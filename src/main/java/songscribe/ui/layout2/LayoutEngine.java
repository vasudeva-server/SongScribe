/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.layout2;

import java.awt.*;
import java.awt.geom.*;
import java.util.HashMap;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.layout.Bounds;

/**
 * Orchestrates the complete layout pipeline for a staff line.
 * <p>
 * The LayoutEngine coordinates all layout calculators to produce a final {@link LayoutResult}
 * containing positioned elements ready for rendering. The pipeline executes in this order:
 * <ol>
 *   <li>{@link NoteColumnBuilder} - Creates note columns from the line's notes</li>
 *   <li>{@link HorizontalSpacingCalculator} - Positions columns horizontally (lyric-driven)</li>
 *   <li>{@link VerticalStackingCalculator} - Positions elements vertically (layer-by-layer)</li>
 *   <li>{@link LineJustificationCalculator} - Compresses spacing if line exceeds margin</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>{@code
 * var engine = new LayoutEngine(g2, lyricsFont, staffRightMarginSs);
 * LayoutResult result = engine.layout(line);
 *
 * if (result == null) {
 *     // Layout failed (line justification error)
 *     String error = engine.getLastError();
 *     // Display error to user
 * } else {
 *     // Use result for rendering
 * }
 * }</pre>
 */
public class LayoutEngine {

    // Staff height in staff-space units (from LayoutConstants)
    private static final double STAFF_HEIGHT_SS = LayoutConstants.STAFF_HEIGHT_SS;

    // Beam geometry constants (staff-space units unless noted)
    private static final double BEAM_DEPTH_SS = 0.4;        // beam thickness
    private static final double BEAM_SHIFT_SS = 0.625;      // gap between stacked beam levels
    private static final double BEAM_STUB_SS = 1.0;         // partial beam stub length
    private static final double BEAM_SLOPE_MAX = 0.4;    // hyperbolic saturation limit (dimensionless)
    private static final double MIN_STEM_SS = 3.5;       // minimum stem length (Gould/Ross 4.2)

    private final Graphics2D g2;
    private final Font lyricsFont;
    private final double staffRightMarginSs;

    // Calculators
    private final NoteColumnBuilder columnBuilder;
    private final HorizontalSpacingCalculator horizontalCalculator;
    private final VerticalStackingCalculator verticalCalculator;
    private final LineJustificationCalculator justificationCalculator;

    // Error tracking
    private String lastError;

    /**
     * Creates a new LayoutEngine.
     *
     * @param g2               Graphics context for text measurement
     * @param lyricsFont       Font to use for lyrics (for measuring syllable widths)
     * @param staffRightMarginSs Right margin of the staff in staff-space units
     */
    public LayoutEngine(
        @NotNull Graphics2D g2,
        @NotNull Font lyricsFont,
        double staffRightMarginSs) {
        this.g2 = g2;
        this.lyricsFont = lyricsFont;
        this.staffRightMarginSs = staffRightMarginSs;

        // Initialize calculators
        this.columnBuilder = new NoteColumnBuilder(g2, lyricsFont);
        this.horizontalCalculator = new HorizontalSpacingCalculator();
        this.verticalCalculator = new VerticalStackingCalculator();
        this.justificationCalculator = new LineJustificationCalculator();

        this.lastError = null;
    }

    /**
     * Executes the complete layout pipeline for a line.
     * <p>
     * This is the main entry point for layout. It orchestrates all calculators
     * and produces a final LayoutResult ready for rendering.
     *
     * @param line The line to lay out
     * @return LayoutResult with all positioned elements, or null if layout fails
     */
    public @Nullable LayoutResult layout(@NotNull Line line) {
        lastError = null;

        // Step 1: Build note columns
        List<NoteColumn> columns = columnBuilder.buildColumns(line);

        if (columns.isEmpty()) {
            // Empty line - return empty result
            return LayoutResult.builder()
                .setLineHeightSs(STAFF_HEIGHT_SS)
                .setStaffGeometrySs(0, STAFF_HEIGHT_SS)
                .setLyricBaselineYSs(0)
                .build();
        }

        // Step 2: Calculate horizontal positions
        horizontalCalculator.calculatePositions(columns, line);

        // Step 3: Apply line justification (compression if needed)
        var justificationResult = justificationCalculator.justifyLine(columns, staffRightMarginSs);

        if (!justificationResult.isSuccess()) {
            // Line cannot fit within margin while maintaining minimum spacing
            lastError = justificationResult.getErrorMessage();
            return null;
        }

        // Step 4: Calculate vertical positions
        var verticalResult = verticalCalculator.calculateVerticalPositions(columns, line, g2);

        var builder = LayoutResult.builder();

        // Step 5: Calculate beam layouts for beamed note groups
        calculateBeams(line, columns, builder);

        // Step 6: Calculate stem layouts for unbeamed notes
        calculateUnbeamedStems(line, columns, builder);

        // Step 7: Build final LayoutResult
        return buildLayoutResult(columns, verticalResult, line, builder);
    }

    /**
     * Returns the last error message from a failed layout attempt.
     *
     * @return Error message, or null if no error
     */
    public @Nullable String getLastError() {
        return lastError;
    }

    /**
     * Builds the final LayoutResult from calculated positions.
     * Populates the given builder with note columns, element bounds, and staff geometry,
     * then returns the built result.
     */
    private LayoutResult buildLayoutResult(
        @NotNull List<NoteColumn> columns,
        @NotNull VerticalStackingResult verticalResult,
        @NotNull Line line,
        @NotNull LayoutResult.Builder builder) {

        // Add note columns
        for (var column : columns) {
            builder.putNoteColumn(column.getNote(), column);
        }

        // Add element bounds from vertical stacking result
        var elementPositions = verticalResult.getElementPositions();

        for (var noteEntry : elementPositions.entrySet()) {
            var note = noteEntry.getKey();
            var elementMap = noteEntry.getValue();

            for (var elementEntry : elementMap.entrySet()) {
                var element = elementEntry.getKey();
                var position = elementEntry.getValue();

                // Create bounds for this element
                // For now, use simple rectangular bounds based on element content size
                var contentBounds = new Rectangle2D.Double(
                    position.getX(),
                    position.getY(),
                    element.getContentWidth(),
                    element.getContentHeight()
                );

                var bounds = element.getBounds();

                // Update bounds position to match calculated position
                bounds = new Bounds(
                    contentBounds,
                    new Rectangle2D.Double(
                        position.getX() - element.getMarginLeft(),
                        position.getY() - element.getMarginTop(),
                        element.getContentWidth() + element.getMarginLeft() + element.getMarginRight(),
                        element.getContentHeight() + element.getMarginTop() + element.getMarginBottom()
                    )
                );

                builder.putElementBounds(element, bounds);
            }
        }

        // Set staff geometry
        double staffTopYSs = 0;
        double staffBottomYSs = STAFF_HEIGHT_SS;

        builder.setStaffGeometrySs(staffTopYSs, staffBottomYSs);

        // Set line height and lyrics baseline from vertical result
        builder.setLineHeightSs(verticalResult.getLineHeightPx());
        builder.setLyricBaselineYSs(verticalResult.getLyricsBaselineYPx());

        return builder.build();
    }

    /**
     * Calculates beam geometry for all beamed note groups in the line.
     * Populates {@code builder} with a {@link LayoutResult.BeamLayout} for each beam interval.
     */
    private void calculateBeams(
        @NotNull Line line,
        @NotNull List<NoteColumn> columns,
        @NotNull LayoutResult.Builder builder) {
        var beamings = line.getBeamings();

        // Build a note→column map for fast X lookups inside the loop.
        var noteToColumn = new HashMap<Note, NoteColumn>(columns.size() * 2);

        for (var column : columns) {
            noteToColumn.put(column.getNote(), column);
        }

        var it = beamings.listIterator();

        while (it.hasNext()) {
            var interval = it.next();

            // Determine stem direction from the pitch contour of the group.
            // Staff position 0 = middle line; positive = below midpoint (Y-down) → stems up.
            // We compare (min + max) to 0 rather than dividing to keep integer arithmetic.
            int minStaffPos = Integer.MAX_VALUE;
            int maxStaffPos = Integer.MIN_VALUE;

            for (int i = interval.getStart(); i <= interval.getEnd(); i++) {
                int pos = line.getNote(i).getStaffPosition();

                if (pos < minStaffPos) {
                    minStaffPos = pos;
                }

                if (pos > maxStaffPos) {
                    maxStaffPos = pos;
                }
            }

            // Scan for any manual override in the group; first one wins.
            Boolean manualDirection = null;

            for (int i = interval.getStart(); i <= interval.getEnd(); i++) {
                var n = line.getNote(i);

                if (!n.isStemDirectionAuto()) {
                    manualDirection = n.isUpper();
                    break;
                }
            }

            boolean stemsUp = (manualDirection != null)
                ? manualDirection
                : (minStaffPos + maxStaffPos) > 0;

            // Normalize auto-direction notes to the group stem direction.
            // Manual overrides are left untouched.
            for (int i = interval.getStart(); i <= interval.getEnd(); i++) {
                var n = line.getNote(i);

                if (n.isStemDirectionAuto()) {
                    n.setUpper(stemsUp);
                }
            }

            // Compute beam slope (abc2svg algorithm with hyperbolic dampening).
            // Staff positions are in half-staff-spaces; ×0.5 converts to staff-space units.
            var firstNote = line.getNote(interval.getStart());
            var lastNote = line.getNote(interval.getEnd());
            var firstColumn = noteToColumn.get(firstNote);
            var lastColumn = noteToColumn.get(lastNote);

            double slope = 0.0;

            if (firstColumn != null && lastColumn != null) {
                double dxSs = lastColumn.getXSs() - firstColumn.getXSs();

                if (dxSs != 0.0) {
                    double rawSlope =
                        (lastNote.getStaffPosition() - firstNote.getStaffPosition()) * 0.5 / dxSs;

                    // Hyperbolic dampening saturates extreme slopes without hard clamping.
                    slope = BEAM_SLOPE_MAX * rawSlope / (BEAM_SLOPE_MAX + Math.abs(rawSlope));
                }
            }

            // Compute y-intercept so beam passes through the anchor note's stem tip at MIN_STEM_SS.
            // The anchor is the note whose stem would be shortest — i.e., closest to the beam.
            //   stemsUp  → note with min staffPosition (highest pitch in Y-down, closest to beam above)
            //   stemsDown → note with max staffPosition (lowest pitch in Y-down, closest to beam below)
            // All Y values are in staff-space with Y-down positive (positive staffPos = below center).
            double startYSs = 0.0;

            if (firstColumn != null) {
                double firstXSs = firstColumn.getXSs();

                int anchorIdx = interval.getStart();
                int anchorStaffPos = firstNote.getStaffPosition();

                for (int i = interval.getStart() + 1; i <= interval.getEnd(); i++) {
                    int pos = line.getNote(i).getStaffPosition();

                    if (stemsUp ? pos < anchorStaffPos : pos > anchorStaffPos) {
                        anchorStaffPos = pos;
                        anchorIdx = i;
                    }
                }

                var anchorNote = line.getNote(anchorIdx);
                var anchorColumn = noteToColumn.get(anchorNote);
                double anchorXSs = (anchorColumn != null) ? anchorColumn.getXSs() : firstXSs;
                double anchorNoteYSs = anchorNote.getStaffPosition() * 0.5;

                // Place beam exactly MIN_STEM_SS from the anchor notehead.
                // Y-down: beam above notehead = smaller Y (subtract); beam below = larger Y (add).
                double beamYAtAnchorSs = stemsUp
                    ? anchorNoteYSs - MIN_STEM_SS
                    : anchorNoteYSs + MIN_STEM_SS;

                startYSs = beamYAtAnchorSs - slope * (anchorXSs - firstXSs);

                // Iteratively reduce slope until all stems are at least MIN_STEM_SS, or give up
                // after 20 iterations.
                for (int iter = 0; iter < 20; iter++) {
                    boolean allOk = true;

                    for (int i = interval.getStart(); i <= interval.getEnd(); i++) {
                        var note = line.getNote(i);
                        var col = noteToColumn.get(note);

                        if (col == null) {
                            continue;
                        }

                        double noteYSs = note.getStaffPosition() * 0.5;
                        double beamYSs = slope * (col.getXSs() - firstXSs) + startYSs;
                        double stemLenSs = stemsUp ? (noteYSs - beamYSs) : (beamYSs - noteYSs);

                        if (stemLenSs < MIN_STEM_SS - 1e-9) {
                            allOk = false;
                            break;
                        }
                    }

                    if (allOk) {
                        break;
                    }

                    // Reduce slope and reanchor so the anchor note still has exactly MIN_STEM_SS.
                    slope *= 0.85;
                    beamYAtAnchorSs = stemsUp
                        ? anchorNoteYSs - MIN_STEM_SS
                        : anchorNoteYSs + MIN_STEM_SS;
                    startYSs = beamYAtAnchorSs - slope * (anchorXSs - firstXSs);
                }

                // After slope reduction, shift beam vertically to cover any remaining deficit.
                double maxDeficitSs = 0.0;

                for (int i = interval.getStart(); i <= interval.getEnd(); i++) {
                    var note = line.getNote(i);
                    var col = noteToColumn.get(note);

                    if (col == null) {
                        continue;
                    }

                    double noteYSs = note.getStaffPosition() * 0.5;
                    double beamYSs = slope * (col.getXSs() - firstXSs) + startYSs;
                    double stemLenSs = stemsUp ? (noteYSs - beamYSs) : (beamYSs - noteYSs);
                    double deficitSs = MIN_STEM_SS - stemLenSs;

                    if (deficitSs > maxDeficitSs) {
                        maxDeficitSs = deficitSs;
                    }
                }

                if (maxDeficitSs > 0.0) {
                    startYSs += stemsUp ? -maxDeficitSs : maxDeficitSs;
                }
            }

            // Flat beam snapping: when slope is near zero, snap startYSs to the nearest
            // staff line or space boundary so the beam sits cleanly on the grid.
            // Grid points are at multiples of 0.5 ss (each staff line/space = 0.5 ss).
            // The formula maps startYSs into the nearest 0.5 ss slot, offset by the
            // staff's half-line-space (0.25 ss from center = 1.5 half-spaces).
            if (Math.abs(slope) < 0.05) {
                startYSs = Math.round((startYSs + 1.5) / 0.75) * 0.75 - 1.5;
            }

            // Beam thickening: angled beams appear thinner due to raster aliasing.
            // Compensate by increasing BEAM_DEPTH proportionally to 1/cos(angle),
            // clamped to a 3.3–8.8% increase over the nominal beam depth.
            double angle = Math.atan(slope);
            double factor = Math.clamp(1.0 / Math.cos(angle), 1.033, 1.088);
            double thickeningSs = BEAM_DEPTH_SS * (factor - 1.0);

            // Build StemLayout for each note in the beam group and accumulate into a map
            // for the BeamLayout.  All Y values are in staff-space with Y-down positive.
            //   stemsUp:   topYSs = beamYSs (above notehead, smaller Y),  bottomYSs = noteAnchorYSs
            //   stemsDown: topYSs = noteAnchorYSs,                        bottomYSs = beamYSs (below notehead, larger Y)
            var stemLayouts = new HashMap<Note, LayoutResult.StemLayout>();

            if (firstColumn != null) {
                double firstXSs = firstColumn.getXSs();

                for (int i = interval.getStart(); i <= interval.getEnd(); i++) {
                    var note = line.getNote(i);
                    var col = noteToColumn.get(note);

                    if (col == null) {
                        continue;
                    }

                    double noteYSs = note.getStaffPosition() * 0.5;
                    double beamYSs = slope * (col.getXSs() - firstXSs) + startYSs;
                    double stemLenSs = stemsUp ? (noteYSs - beamYSs) : (beamYSs - noteYSs);
                    double lengtheningSs = stemLenSs - MIN_STEM_SS;

                    double topYSs = stemsUp ? beamYSs : noteYSs;
                    double bottomYSs = stemsUp ? noteYSs : beamYSs;

                    // Determine stub direction for partial-beam notes.
                    // A stub is needed at beam level L when neither neighbour shares level L.
                    int myBeams = beamCount(note);
                    int leftBeams = i > interval.getStart() ? beamCount(line.getNote(i - 1)) : 0;
                    int rightBeams = i < interval.getEnd() ? beamCount(line.getNote(i + 1)) : 0;

                    boolean hasStub = false;

                    for (int level = 2; level <= myBeams; level++) {
                        if (leftBeams < level && rightBeams < level) {
                            hasStub = true;
                            break;
                        }
                    }

                    boolean stubRight = false;

                    if (hasStub) {
                        if (i == interval.getStart()) {
                            stubRight = true;                   // first note → stub right
                        } else if (i == interval.getEnd()) {
                            stubRight = false;                  // last note → stub left
                        } else if (rightBeams < myBeams) {
                            stubRight = false;                  // note before a beam break → left
                        } else if (leftBeams < myBeams) {
                            stubRight = true;                   // note at a beam break → right
                        } else {
                            stubRight = rightBeams >= leftBeams; // toward neighbour with more beams
                        }
                    }

                    stemLayouts.put(note, new LayoutResult.StemLayout(topYSs, bottomYSs, lengtheningSs, stubRight));
                }
            }

            var beamLayout = new LayoutResult.BeamLayout(slope, startYSs, stemsUp, thickeningSs, stemLayouts);
            builder.putBeamLayout(interval, beamLayout);
        }
    }

    /**
     * Calculates stem geometry for all notes not covered by a beam group.
     * Populates {@code builder} with a {@link LayoutResult.StemLayout} for each such note.
     */
    private void calculateUnbeamedStems(
        @NotNull Line line,
        @NotNull List<NoteColumn> columns,
        @NotNull LayoutResult.Builder builder) {
        for (var col : columns) {
            var note = col.getNote();

            if (col.isBeamed() || !note.getNoteType().isNoteWithStem()) {
                continue;
            }

            // Set auto stem direction: notes below the middle line (staffPosition > 0) get stems up.
            // This matches Score.defaultUpperNote: upper=true means stem up.
            if (note.isStemDirectionAuto()) {
                note.setUpper(note.getStaffPosition() > 0);
            }

            // isUpper() → stem up (upper=true means stem goes up)
            boolean stemsUp = note.isUpper();
            double noteYSs = note.getStaffPosition() * 0.5;

            // Y increases downward: stem-up tip has smaller Y; stem-down tip has larger Y.
            double topYSs = stemsUp ? noteYSs - MIN_STEM_SS : noteYSs;
            double bottomYSs = stemsUp ? noteYSs : noteYSs + MIN_STEM_SS;

            builder.putStemLayout(note, new LayoutResult.StemLayout(topYSs, bottomYSs, 0.0, false));
        }
    }

    /**
     * Returns the number of beams (flag levels) for a note type.
     * QUAVER = 1, SEMIQUAVER = 2, DEMI_SEMIQUAVER = 3.
     */
    private static int beamCount(@NotNull Note note) {
        return switch (note.getNoteType()) {
            case SEMIQUAVER -> 2;
            case DEMI_SEMIQUAVER -> 3;
            default -> 1;
        };
    }

    /**
     * Returns the graphics context used by this engine.
     */
    public @NotNull Graphics2D getGraphics() {
        return g2;
    }

    /**
     * Returns the lyrics font used by this engine.
     */
    public @NotNull Font getLyricsFont() {
        return lyricsFont;
    }

    /**
     * Returns the staff right margin used by this engine.
     */
    public double getStaffRightMarginSs() {
        return staffRightMarginSs;
    }
}
