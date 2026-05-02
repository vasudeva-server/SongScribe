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

package songscribe.ui.layout;

import module java.desktop;

import java.util.HashMap;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.music.ElementType;
import songscribe.music.KeyType;
import songscribe.smufl.Engraving;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.layout.stacking.VerticalStackingCalculator;
import songscribe.ui.renderer.NoteRenderer;

/**
 * Orchestrates the complete layout pipeline for a staff line.
 * <p>
 * The LayoutEngine coordinates all layout calculators to produce a final {@link LayoutResult}
 * containing positioned elements ready for rendering. The pipeline executes in this order:
 * <ol>
 *   <li>{@link ElementColumnBuilder} - Creates note columns from the line's notes</li>
 *   <li>{@link HorizontalSpacingCalculator} - Positions columns horizontally (lyric-driven)</li>
 *   <li>{@link VerticalStackingCalculator} - Positions elements vertically (layer-by-layer)</li>
 *   <li>{@link LineJustificationCalculator} - Compresses spacing if line exceeds margin</li>
 * </ol>
 * <p>
 * Usage:
 * <pre>{@code
 * var engine = new LayoutEngine(lyricRenderMetrics, staffRightMarginSs);
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

    // Staff height in staff-space units
    private static final double STAFF_HEIGHT_SS = StaffExtents.STAFF_HEIGHT_SS;

    private static final double CLEF_X_POSITION_SS = 0.625;  // 5px

    // Beam geometry constants (staff-space units unless noted)
    private static final double BEAM_DEPTH_SS = 0.4;        // beam thickness
    private static final double BEAM_SHIFT_SS = 0.625;      // gap between stacked beam levels
    private static final double BEAM_SLOPE_MAX = 0.4;    // hyperbolic saturation limit (dimensionless)
    private static final double MIN_STEM_SS = NoteRenderer.STEM_LENGTH_SS;

    // Tie geometry constants (MuseScore port, staff-space units unless noted)
    private static final double TIE_SHOULDER_W = 0.6;                // shoulder width fraction of tie span
    private static final double TIE_MIN_SHOULDER_HEIGHT_SS = 0.3;   // minimum arc height
    private static final double TIE_MAX_SHOULDER_HEIGHT_SS = 2.0;   // maximum arc height
    private static final double TIE_SHOULDER_HEIGHT_SCALE = 0.3;    // sqrt scaling factor for arc height
    private static final double TIE_MID_THICKNESS_SS = Engraving.TIE_MIDPOINT_THICKNESS_SS; // midpoint half-thickness (midWidth - endWidth)
    private static final double TIE_COLLISION_FACTOR = 0.65;        // interior deflection scaling
    private static final double TIE_COLLISION_PUSH = 0.45;          // midpoint push-up ratio on collision
    private static final double TIE_NOTEHEAD_HALF_WIDTH_SS = 0.6;   // visual half-width of notehead
    private static final double TIE_ENDPOINT_Y_OFFSET_SS = 0.7;    // y offset from note center (noteHeight/2 + 0.2)

    private final LyricRenderMetrics lyricRenderMetrics;
    private final double staffRightMarginSs;

    // Calculators
    private final ElementColumnBuilder columnBuilder;
    private final HorizontalSpacingCalculator horizontalCalculator;
    private final VerticalStackingCalculator verticalCalculator;
    private final LineJustificationCalculator justificationCalculator;

    // Error tracking
    @Nullable
    private String lastError;

    /**
     * Creates a new LayoutEngine.
     *
     * @param lyricRenderMetrics Song-wide lyric render metrics (font + glyph widths)
     * @param staffRightMarginSs Right margin of the staff in staff-space units
     */
    public LayoutEngine(LyricRenderMetrics lyricRenderMetrics, double staffRightMarginSs) {
        this.lyricRenderMetrics = lyricRenderMetrics;
        this.staffRightMarginSs = staffRightMarginSs;

        // Initialize calculators
        this.columnBuilder = new ElementColumnBuilder(lyricRenderMetrics);
        this.horizontalCalculator = new HorizontalSpacingCalculator();
        this.verticalCalculator = new VerticalStackingCalculator();
        this.justificationCalculator = new LineJustificationCalculator();

        this.lastError = null;
    }

    /**
     * Executes the complete layout pipeline for a line.
     * Equivalent to {@code layout(line, false, false)}.
     *
     * @param line The line to lay out
     * @return LayoutResult with all positioned elements, or null if layout fails
     */
    public @Nullable LayoutResult layout(Line line) {
        return layout(line, false, false);
    }

    /**
     * Executes the complete layout pipeline for a line.
     * Equivalent to {@code layout(line, isLastLine, false)}.
     *
     * @param line       The line to lay out
     * @param isLastLine Whether this line is the last line of the song
     * @return LayoutResult with all positioned elements, or null if layout fails
     */
    public @Nullable LayoutResult layout(Line line, boolean isLastLine) {
        return layout(line, isLastLine, false);
    }

    /**
     * Executes the complete layout pipeline for a line.
     * <p>
     * This is the main entry point for layout. It orchestrates all calculators
     * and produces a final LayoutResult ready for rendering.
     *
     * @param line                      The line to lay out
     * @param isLastLine                Whether this line is the last line of the song.
     *                                  When true, the final double barline (if present) is
     *                                  pinned flush with the right edge of the line.
     * @param hasLeadingLyricContinuation True when the previous line ended with an active
     *                                    melisma extender that should continue from x = 0
     *                                    on this line until the first syllable or rest that
     *                                    breaks it.
     * @return LayoutResult with all positioned elements, or null if layout fails
     */
    public @Nullable LayoutResult layout(Line line, boolean isLastLine, boolean hasLeadingLyricContinuation) {
        lastError = null;

        // Step 1: Build note columns
        List<ElementColumn> columns = columnBuilder.buildColumns(line);

        if (columns.isEmpty()) {
            // Empty line — mirror the minimum content-fitted height from
            // VerticalStackingCalculator so header elements (clef, key signature)
            // have MIN_ABOVE_STAFF_SS room above the staff.
            double emptyLineHeightSs = SongLayoutMetricsBuilder.MIN_LINE_HEIGHT_SS;
            var emptyBuilder = LayoutResult.builder()
                .setLineHeightSs(emptyLineHeightSs)
                .setAboveStaffSs(StaffExtents.MIN_ABOVE_STAFF_SS);
            createHeaderElements(line, emptyBuilder);
            return emptyBuilder.build();
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

        // Step 3b: Pin the terminal flush-right on the last line.
        // Layout is the sole writer of the terminal's x position.
        if (isLastLine) {
            positionTerminalFlushRight(columns);
        }

        var builder = LayoutResult.builder();

        // Step 4: Create header elements (clef and key signature)
        createHeaderElements(line, builder);

        // Step 5: Calculate beam layouts for beamed note groups
        calculateBeams(line, columns, builder);

        // Step 5b: Calculate stem layouts for unbeamed notes
        calculateUnbeamedStems(line, columns, builder);

        // Step 6: Calculate tie geometry for all tie spans
        calculateTies(line, columns, builder);

        // Step 7: Calculate vertical positions (requires stem layouts from steps 5/5b)
        // Use the song's staff width for consistent StaffExtents discretization,
        // not the content width which varies with column count.
        verticalCalculator.calculate(columns, line, builder, staffRightMarginSs);

        // Step 7b: Compute lyric box and connector geometry.
        buildLyricLayout(columns, builder, hasLeadingLyricContinuation);

        // Step 8: Build final LayoutResult
        return buildLayoutResult(columns, line, builder);
    }

    private void buildLyricLayout(
        List<ElementColumn> columns,
        LayoutResult.Builder builder,
        boolean hasLeadingLyricContinuation) {

        var lyricResult = LyricLayoutBuilder.build(
            columns, lyricRenderMetrics, hasLeadingLyricContinuation, staffRightMarginSs);

        for (var entry : lyricResult.boxes().entrySet()) {
            for (var box : entry.getValue()) {
                builder.addLyricBox(entry.getKey(), box);
            }
        }

        for (var connector : lyricResult.connectors()) {
            builder.addLyricConnector(connector);
        }

        builder.setVerseCount(lyricResult.verseCount());
        builder.setHasTrailingLyricContinuation(lyricResult.hasTrailingContinuation());
    }

    private void positionTerminalFlushRight(List<ElementColumn> columns) {
        if (columns.isEmpty()) {
            return;
        }

        // On the last line, the terminal is always the last element. Only the last
        // column is snapped flush-right — interior REPEAT_RIGHTs are left in place.
        var lastColumn = columns.getLast();
        var lastType = lastColumn.getElement().getType();

        if (lastType.isValidTerminal()) {
            lastColumn.setXSs(ElementType.terminalFlushRightXSs(staffRightMarginSs, lastType));
        }
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
     * <p>
     * Populates the given builder with element columns and staff geometry,
     * then returns the built result. Vertical stacking results (decoration layouts,
     * line height, lyrics baseline) are already in the builder from the
     * {@link VerticalStackingCalculator#calculate} call.
     */
    private LayoutResult buildLayoutResult(
        List<ElementColumn> columns,
        Line line,
        LayoutResult.Builder builder) {

        // Add element columns
        for (var column : columns) {
            builder.putElementColumn(column.getElement(), column);
        }

        return builder.build();
    }

    /**
     * Calculates beam geometry for all beamed note groups in the line.
     * Populates {@code builder} with a {@link LayoutResult.BeamLayout} for each beam span.
     */
    private void calculateBeams(
        Line line,
        List<ElementColumn> columns,
        LayoutResult.Builder builder) {
        var beamings = line.getBeamings();

        // Build an element→column map for fast X lookups inside the loop.
        var elementToColumn = new HashMap<StaffElement, ElementColumn>(columns.size() * 2);

        for (var column : columns) {
            elementToColumn.put(column.getElement(), column);
        }

        var it = beamings.listIterator();

        while (it.hasNext()) {
            var span = it.next();

            // Determine stem direction from the pitch contour of the group.
            // Staff position 0 = middle line; positive = below midpoint (Y-down) → stems up.
            // We compare (min + max) to 0 rather than dividing to keep integer arithmetic.
            int minStaffPos = Integer.MAX_VALUE;
            int maxStaffPos = Integer.MIN_VALUE;

            for (int i = span.getStart(); i <= span.getEnd(); i++) {
                int pos = line.getElement(i).getStaffPosition();

                if (pos < minStaffPos) {
                    minStaffPos = pos;
                }

                if (pos > maxStaffPos) {
                    maxStaffPos = pos;
                }
            }

            // Scan for any manual override in the group; first one wins.
            Boolean manualDirection = null;

            for (int i = span.getStart(); i <= span.getEnd(); i++) {
                var n = line.getElement(i);

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
            for (int i = span.getStart(); i <= span.getEnd(); i++) {
                var n = line.getElement(i);

                if (n.isStemDirectionAuto()) {
                    n.setUpper(stemsUp);
                }
            }

            // Compute beam slope (abc2svg algorithm with hyperbolic dampening).
            // Staff positions are in half-staff-spaces; ×0.5 converts to staff-space units.
            var firstElement = line.getElement(span.getStart());
            var lastElement = line.getElement(span.getEnd());
            var firstColumn = elementToColumn.get(firstElement);
            var lastColumn = elementToColumn.get(lastElement);

            double slope = 0.0;

            if (firstColumn != null && lastColumn != null) {
                double dxSs = lastColumn.getXSs() - firstColumn.getXSs();

                if (dxSs != 0.0) {
                    double rawSlope =
                        StaffExtents.spToSs(lastElement.getStaffPosition() - firstElement.getStaffPosition()) / dxSs;

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

                int anchorIdx = span.getStart();
                int anchorStaffPos = firstElement.getStaffPosition();

                for (int i = span.getStart() + 1; i <= span.getEnd(); i++) {
                    int pos = line.getElement(i).getStaffPosition();

                    if (stemsUp ? pos < anchorStaffPos : pos > anchorStaffPos) {
                        anchorStaffPos = pos;
                        anchorIdx = i;
                    }
                }

                var anchorElement = line.getElement(anchorIdx);
                var anchorColumn = elementToColumn.get(anchorElement);
                double anchorXSs = (anchorColumn != null) ? anchorColumn.getXSs() : firstXSs;
                double anchorElementYSs = StaffExtents.spToSs(anchorElement.getStaffPosition());

                // Place beam exactly MIN_STEM_SS from the anchor notehead.
                // Y-down: beam above notehead = smaller Y (subtract); beam below = larger Y (add).
                double beamYAtAnchorSs = stemsUp
                    ? anchorElementYSs - MIN_STEM_SS
                    : anchorElementYSs + MIN_STEM_SS;

                startYSs = beamYAtAnchorSs - slope * (anchorXSs - firstXSs);

                // Iteratively reduce slope until all stems are at least MIN_STEM_SS, or give up
                // after 20 iterations.
                for (int iter = 0; iter < 20; iter++) {
                    boolean allOk = true;

                    for (int i = span.getStart(); i <= span.getEnd(); i++) {
                        var element = line.getElement(i);
                        var col = elementToColumn.get(element);

                        if (col == null) {
                            continue;
                        }

                        double elementYSs = StaffExtents.spToSs(element.getStaffPosition());
                        double beamYSs = slope * (col.getXSs() - firstXSs) + startYSs;
                        double stemLenSs = stemsUp ? (elementYSs - beamYSs) : (beamYSs - elementYSs);

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
                        ? anchorElementYSs - MIN_STEM_SS
                        : anchorElementYSs + MIN_STEM_SS;
                    startYSs = beamYAtAnchorSs - slope * (anchorXSs - firstXSs);
                }

                // After slope reduction, shift beam vertically to cover any remaining deficit.
                double maxDeficitSs = 0.0;

                for (int i = span.getStart(); i <= span.getEnd(); i++) {
                    var element = line.getElement(i);
                    var col = elementToColumn.get(element);

                    if (col == null) {
                        continue;
                    }

                    double elementYSs = StaffExtents.spToSs(element.getStaffPosition());
                    double beamYSs = slope * (col.getXSs() - firstXSs) + startYSs;
                    double stemLenSs = stemsUp ? (elementYSs - beamYSs) : (beamYSs - elementYSs);
                    double deficitSs = MIN_STEM_SS - stemLenSs;

                    if (deficitSs > maxDeficitSs) {
                        maxDeficitSs = deficitSs;
                    }
                }

                if (maxDeficitSs > 0.0) {
                    startYSs += stemsUp ? -maxDeficitSs : maxDeficitSs;
                }
            }

            // Flat beam snapping: when slope is near zero, snap the outer beam edge to
            // the nearest staff line or space boundary (0.5 ss grid) so the beam sits
            // cleanly on the grid. startYSs is the outer edge (top for stems-up,
            // bottom for stems-down), so rounding to the nearest 0.5 ss aligns it
            // with a line or space center.
            if (Math.abs(slope) < 0.05) {
                startYSs = Math.round(startYSs * 2.0) / 2.0;
            }

            // Beam thickening: angled beams appear thinner due to raster aliasing.
            // Compensate by increasing BEAM_DEPTH proportionally to 1/cos(angle),
            // clamped to a 3.3–8.8% increase over the nominal beam depth.
            double angle = Math.atan(slope);
            double factor = Math.clamp(1.0 / Math.cos(angle), 1.033, 1.088);
            double thickeningSs = BEAM_DEPTH_SS * (factor - 1.0);

            // Build StemLayout for each element in the beam group and accumulate into a map
            // for the BeamLayout.  All Y values are in staff-space with Y-down positive.
            //   stemsUp:   topYSs = beamYSs (above notehead, smaller Y),  bottomYSs = elementAnchorYSs
            //   stemsDown: topYSs = elementAnchorYSs,                     bottomYSs = beamYSs (below notehead, larger Y)
            var stemLayouts = new HashMap<StaffElement, LayoutResult.StemLayout>();

            if (firstColumn != null) {
                double firstXSs = firstColumn.getXSs();

                for (int i = span.getStart(); i <= span.getEnd(); i++) {
                    var element = line.getElement(i);
                    var col = elementToColumn.get(element);

                    if (col == null) {
                        continue;
                    }

                    double elementYSs = StaffExtents.spToSs(element.getStaffPosition());
                    double beamYSs = slope * (col.getXSs() - firstXSs) + startYSs;
                    double stemLenSs = stemsUp ? (elementYSs - beamYSs) : (beamYSs - elementYSs);
                    double lengtheningSs = stemLenSs - MIN_STEM_SS;

                    double topYSs = stemsUp ? beamYSs : elementYSs;
                    double bottomYSs = stemsUp ? elementYSs : beamYSs;

                    // Determine stub direction for partial-beam elements.
                    // A stub is needed at beam level L when neither neighbour shares level L.
                    int myBeams = beamCount(element);
                    int leftBeams = i > span.getStart() ? beamCount(line.getElement(i - 1)) : 0;
                    int rightBeams = i < span.getEnd() ? beamCount(line.getElement(i + 1)) : 0;

                    boolean hasStub = false;

                    for (int level = 2; level <= myBeams; level++) {
                        if (leftBeams < level && rightBeams < level) {
                            hasStub = true;
                            break;
                        }
                    }

                    boolean stubRight = false;

                    if (hasStub) {
                        if (i == span.getStart()) {
                            stubRight = true;                   // first element → stub right
                        } else if (i == span.getEnd()) {
                            stubRight = false;                  // last element → stub left
                        } else if (rightBeams < myBeams) {
                            stubRight = false;                  // element before a beam break → left
                        } else if (leftBeams < myBeams) {
                            stubRight = true;                   // element at a beam break → right
                        } else {
                            stubRight = rightBeams >= leftBeams; // toward neighbour with more beams
                        }
                    }

                    stemLayouts.put(element, new LayoutResult.StemLayout(topYSs, bottomYSs, lengtheningSs, stubRight));
                }
            }

            var beamLayout = new LayoutResult.BeamLayout(slope, startYSs, stemsUp, thickeningSs, stemLayouts);
            builder.putBeamLayout(span, beamLayout);
        }
    }

    /**
     * Calculates stem geometry for all elements not covered by a beam group.
     * Populates {@code builder} with a {@link LayoutResult.StemLayout} for each such element.
     */
    private void calculateUnbeamedStems(
        Line line,
        List<ElementColumn> columns,
        LayoutResult.Builder builder) {
        for (var col : columns) {
            var element = col.getElement();

            if (col.isBeamed() || !element.getType().isNoteWithStem()) {
                continue;
            }

            // Set auto stem direction: elements below the middle line (staffPosition > 0) get stems up.
            // This matches Score.defaultUpperNote: upper=true means stem up.
            if (element.isStemDirectionAuto()) {
                element.setUpper(element.getType().isGraceNote() || element.getStaffPosition() > 0);
            }

            // isUpper() → stem up (upper=true means stem goes up)
            boolean stemsUp = element.isUpper();
            double elementYSs = StaffExtents.spToSs(element.getStaffPosition());
            double stemLenSs = element.getType().isGraceNote()
                ? NoteRenderer.GRACE_NOTE_STEM_LENGTH_SS
                : MIN_STEM_SS;

            // Y increases downward: stem-up tip has smaller Y; stem-down tip has larger Y.
            double topYSs = stemsUp ? elementYSs - stemLenSs : elementYSs;
            double bottomYSs = stemsUp ? elementYSs : elementYSs + stemLenSs;

            builder.putStemLayout(element, new LayoutResult.StemLayout(topYSs, bottomYSs, 0.0, false));
        }
    }

    /**
     * Calculates tie geometry for all tie spans in the line.
     * <p>
     * Ports MuseScore's tie layout algorithm to SongScribe's staff-space coordinate system.
     * Each tie produces a filled lens shape defined by an outer and an inner cubic Bezier curve
     * that share start/end points, creating natural tapering at the endpoints.
     * Populates {@code builder} with a {@link LayoutResult.TieLayout} for each tie span.
     */
    private void calculateTies(
        Line line,
        List<ElementColumn> columns,
        LayoutResult.Builder builder) {

        var ties = line.getTies();

        if (ties.isEmpty()) {
            return;
        }

        // Build an element → column map for fast X lookups.
        var elementToColumn = new HashMap<StaffElement, ElementColumn>(columns.size() * 2);

        for (var column : columns) {
            elementToColumn.put(column.getElement(), column);
        }

        var it = ties.listIterator();

        while (it.hasNext()) {
            var span = it.next();

            var startElement = line.getElement(span.getStart());
            var endElement = line.getElement(span.getEnd());
            var startColumn = elementToColumn.get(startElement);
            var endColumn = elementToColumn.get(endElement);

            if (startColumn == null || endColumn == null) {
                continue;
            }

            // Tie direction: stem-up elements tie below (+1), stem-down elements tie above (-1).
            // Y increases downward, so direction = +1 → arc bulges downward.
            int direction = startElement.isUpper() ? 1 : -1;

            // Tie attachment points: centered on notehead horizontally.
            double startXSs = startColumn.getXSs() + TIE_NOTEHEAD_HALF_WIDTH_SS;
            double endXSs = endColumn.getXSs() + TIE_NOTEHEAD_HALF_WIDTH_SS;
            double startYSs = StaffExtents.spToSs(startElement.getStaffPosition()) + direction * TIE_ENDPOINT_Y_OFFSET_SS;
            double endYSs = StaffExtents.spToSs(endElement.getStaffPosition()) + direction * TIE_ENDPOINT_Y_OFFSET_SS;

            double tieWidthSs = endXSs - startXSs;

            // Shoulder height: sqrt growth curve, short ties are relatively tall, long ties flatten.
            double shoulderHSs = TIE_MIN_SHOULDER_HEIGHT_SS
                + TIE_SHOULDER_HEIGHT_SCALE * Math.sqrt(Math.max(tieWidthSs - 1, 0));
            shoulderHSs = Math.clamp(shoulderHSs, TIE_MIN_SHOULDER_HEIGHT_SS, TIE_MAX_SHOULDER_HEIGHT_SS);

            // Control point X positions: at 20% and 80% of tie width (shoulderW = 0.6).
            double marginFraction = (1.0 - TIE_SHOULDER_W) * 0.5;
            double cp1XSs = startXSs + tieWidthSs * marginFraction;
            double cp2XSs = startXSs + tieWidthSs * (marginFraction + TIE_SHOULDER_W);

            // Control point Y: both at shoulder height from the baseline.
            // Baseline is the midpoint Y of the two endpoints (identical for ties).
            double baseYSs = (startYSs + endYSs) * 0.5;
            double shoulderYSs = baseYSs + direction * shoulderHSs;

            // Outer curve control points: offset away from notes by midThickness.
            double outerCpYSs = shoulderYSs + direction * TIE_MID_THICKNESS_SS;

            // Inner curve control points: offset toward notes by midThickness.
            double innerCpYSs = shoulderYSs - direction * TIE_MID_THICKNESS_SS;

            // Interior note collision avoidance: only for ties spanning 3+ notes.
            if (span.getEnd() - span.getStart() >= 2) {
                double maxDeflection = 0.0;

                for (int i = span.getStart() + 1; i < span.getEnd(); i++) {
                    var interiorElement = line.getElement(i);
                    var interiorColumn = elementToColumn.get(interiorElement);

                    if (interiorColumn == null) {
                        continue;
                    }

                    double elementXSs = interiorColumn.getXSs();
                    double elementYSs = StaffExtents.spToSs(interiorElement.getStaffPosition());

                    // Evaluate outer cubic Bezier at approximate t (linear X interpolation).
                    double t = tieWidthSs > 0
                        ? Math.clamp((elementXSs - startXSs) / tieWidthSs, 0.0, 1.0) : 0.5;
                    double mt = 1.0 - t;
                    double tieYAtElementSs =
                        mt * mt * mt * startYSs +
                            3 * mt * mt * t * outerCpYSs +
                            3 * mt * t * t * outerCpYSs +
                            t * t * t * endYSs;

                    // Deflection: how much the element protrudes into the tie arc.
                    double deflection = (tieYAtElementSs - elementYSs) * direction;

                    if (deflection > maxDeflection) {
                        maxDeflection = deflection;
                    }
                }

                if (maxDeflection > 0.0) {
                    double push = TIE_COLLISION_PUSH * TIE_COLLISION_FACTOR * maxDeflection;
                    shoulderHSs += push;
                    shoulderYSs = baseYSs + direction * shoulderHSs;
                    outerCpYSs = shoulderYSs + direction * TIE_MID_THICKNESS_SS;
                    innerCpYSs = shoulderYSs - direction * TIE_MID_THICKNESS_SS;
                }
            }

            builder.putTieLayout(span, new LayoutResult.TieLayout(
                startXSs, startYSs,
                endXSs, endYSs,
                cp1XSs, outerCpYSs,
                cp2XSs, outerCpYSs,
                cp1XSs, innerCpYSs,
                cp2XSs, innerCpYSs
            ));
        }
    }

    /**
     * Creates the clef and key signature header elements and stores them in the builder.
     * <p>
     * The clef is placed at {@link #CLEF_X_POSITION_SS}. The key signature
     * is placed immediately to the right of the clef, accounting for the clef's advance width
     * and right margin.
     */
    private static void createHeaderElements(Line line, LayoutResult.Builder builder) {
        var clef = new Clef();
        clef.setPosition(CLEF_X_POSITION_SS, 0);
        builder.setClef(clef);
        var rawKeyType = line.getKeyType();
        var keyType = rawKeyType != null ? rawKeyType : KeyType.NONE;
        var keySig = new KeySignature(keyType, line.getKeyAccidentalCount());
        double keySigXSs = CLEF_X_POSITION_SS
            + Engraving.G_CLEF_WIDTH_SS
            + clef.getMarginRightSs();
        keySig.setPosition(keySigXSs, 0);
        builder.setKeySignature(keySig);
    }

    /**
     * Returns the number of beams (flag levels) for a note type.
     * QUAVER = 1, SEMIQUAVER = 2, DEMI_SEMIQUAVER = 3.
     */
    private static int beamCount(StaffElement note) {
        return switch (note.getType()) {
            case SEMIQUAVER -> 2;
            case DEMI_SEMIQUAVER -> 3;
            default -> 1;
        };
    }

    /**
     * Returns the staff right margin used by this engine.
     */
    public double getStaffRightMarginSs() {
        return staffRightMarginSs;
    }
}
