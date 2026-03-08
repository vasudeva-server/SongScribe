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
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.smufl.StaffSpaces;
import songscribe.ui.layout.Articulation;
import songscribe.ui.layout.FermataAttachment;
import songscribe.ui.layout.LineElement;
import songscribe.ui.renderer.ArticulationRenderer;

/**
 * Calculates vertical positions for all elements above and below the staff.
 * <p>
 * Implements layer-by-layer stacking with proper collision detection using bounding areas.
 * The stacking order (bottom to top, above staff) is:
 * <ol>
 *   <li>Note (head, stem, flag, ledger lines)</li>
 *   <li>Articulations (staccato, accent)</li>
 *   <li>Trill</li>
 *   <li>Fermata</li>
 *   <li>Dynamics (point only)</li>
 *   <li>Tempo/beat change</li>
 *   <li>Text annotations</li>
 * </ol>
 * <p>
 * Each layer is positioned with a specific margin from the layer below.
 * Elements cannot collide - if an element would intersect the accumulated bounding area,
 * it is moved upward until clear.
 */
public class VerticalStackingCalculator {

    // Note head dimensions from SMuFL noteheadBlack bounding box
    private static final double NOTE_HEAD_WIDTH_PX =
        StaffSpaces.toPixels(
            SMuFLMetadata.getInstance().getBBox(SMuFLGlyph.NOTEHEAD_BLACK).width());

    private static final double NOTE_HEAD_HEIGHT_PX =
        StaffSpaces.toPixels(
            SMuFLMetadata.getInstance().getBBox(SMuFLGlyph.NOTEHEAD_BLACK).height());

    /**
     * Creates a new vertical stacking calculator.
     */
    public VerticalStackingCalculator() {
    }

    /**
     * Calculates vertical positions for all elements in the given note columns.
     * <p>
     * This is the main entry point for vertical layout. It processes each column
     * in order, stacking elements layer by layer above the staff.
     *
     * @param columns List of note columns with X positions already set
     * @param line    The line being laid out
     * @param g2      Graphics context for text measurement (may be null if no text elements)
     * @return VerticalStackingResult containing all calculated positions
     */
    public @NotNull VerticalStackingResult calculateVerticalPositions(
        @NotNull List<NoteColumn> columns,
        @NotNull Line line,
        Graphics2D g2) {

        var elementPositions = new HashMap<Note, Map<LineElement, Point2D>>();
        var maxHeightAboveStaffPx = 0.0;

        // Process each column
        for (var column : columns) {
            var note = column.getNote();
            var noteElementPositions = new HashMap<LineElement, Point2D>();
            elementPositions.put(note, noteElementPositions);

            // Start with note bounds as the initial bounding area
            var accumulated = getNoteBoundingArea(column);

            // Track the highest point reached for this column
            var columnMaxYPx = accumulated.getBounds2D().getMinY();

            // Process each layer in order (bottom to top)
            var articulationsMaxYPx = stackArticulations(column, accumulated, noteElementPositions);
            if (articulationsMaxYPx < columnMaxYPx) {
                columnMaxYPx = articulationsMaxYPx;
            }

            var trillMaxYPx = stackTrill(column, accumulated, noteElementPositions);
            if (trillMaxYPx < columnMaxYPx) {
                columnMaxYPx = trillMaxYPx;
            }

            var fermataMaxYPx = stackFermata(column, accumulated, noteElementPositions);
            if (fermataMaxYPx < columnMaxYPx) {
                columnMaxYPx = fermataMaxYPx;
            }

            var dynamicsMaxYPx = stackDynamics(column, accumulated, noteElementPositions);
            if (dynamicsMaxYPx < columnMaxYPx) {
                columnMaxYPx = dynamicsMaxYPx;
            }

            var tempoMaxYPx = stackTempo(column, accumulated, noteElementPositions);
            if (tempoMaxYPx < columnMaxYPx) {
                columnMaxYPx = tempoMaxYPx;
            }

            var annotationsMaxYPx = stackAnnotations(column, accumulated, noteElementPositions);
            if (annotationsMaxYPx < columnMaxYPx) {
                columnMaxYPx = annotationsMaxYPx;
            }

            // Update global maximum height
            if (columnMaxYPx < maxHeightAboveStaffPx) {
                maxHeightAboveStaffPx = columnMaxYPx;
            }
        }

        // Calculate lyrics baseline
        var lowestNoteYPx = findLowestNoteBoundingYPx(columns);
        var lyricsBaselineYPx = lowestNoteYPx + ScaleContext.getInstance().toPixels(LayoutConstants.LYRICS_BASELINE_OFFSET_SS);

        // Calculate line height
        var hasLyrics = columns.stream().anyMatch(NoteColumn::hasSyllable);
        var lyricsHeightPx = hasLyrics ? 20.0 : 0.0; // TODO: Measure actual lyric height
        var interLineMarginPx = 10.0; // TODO: Get from LayoutConstants

        var lineHeightPx = ScaleContext.getInstance().toPixels(LayoutConstants.STAFF_HEIGHT_SS) +
            Math.abs(maxHeightAboveStaffPx) +
            lyricsHeightPx +
            interLineMarginPx;

        return new VerticalStackingResult(
            elementPositions,
            maxHeightAboveStaffPx,
            lyricsBaselineYPx,
            lineHeightPx
        );
    }

    /**
     * Stacks articulations for the given column.
     * <p>
     * Unlike other elements, articulations have privileged positioning: they anchor
     * to the staff edge or note head at a fixed distance, on the opposite side from
     * the stem. They are the only note elements that may go below the staff.
     * All other elements stack above articulations.
     *
     * @param column              The note column
     * @param accumulated         Accumulated bounding area
     * @param noteElementPositions Map to store element positions
     * @return Minimum Y position reached (most negative)
     */
    private double stackArticulations(
        @NotNull NoteColumn column,
        @NotNull Area accumulated,
        @NotNull Map<LineElement, Point2D> noteElementPositions) {

        var note = column.getNote();
        var articulations = note.getArticulations();

        if (articulations.isEmpty()) {
            return accumulated.getBounds2D().getMinY();
        }

        var minYPx = accumulated.getBounds2D().getMinY();
        boolean isUpper = note.isUpper();
        double halfContentHeightPx = articulations.getFirst().getContentHeight() / 2.0;

        // Identify articulation types
        Articulation staccatoArticulation = null;
        Articulation accentArticulation = null;

        for (var a : articulations) {
            if (a.isStaccato()) {
                staccatoArticulation = a;
            } else if (a.isAccent()) {
                accentArticulation = a;
            }
        }

        // Compute center Y positions in layout space (middleLineY=0)
        // using the same logic as the renderer's fallback path.
        boolean hasStaccato = staccatoArticulation != null;
        int staccatoCenterY = hasStaccato
            ? ArticulationRenderer.calculateStaccatoYPx(note, 0)
            : 0;

        // Position staccato
        if (staccatoArticulation != null) {
            double topYPx = staccatoCenterY - halfContentHeightPx;
            positionElement(staccatoArticulation, column.getXSs(), topYPx, noteElementPositions);

            // Only add to accumulated area if above the note (stem down),
            // so that elements stacking above will clear it.
            if (!isUpper) {
                addToAccumulated(staccatoArticulation, column.getXSs(), topYPx, accumulated);

                if (topYPx < minYPx) {
                    minYPx = topYPx;
                }
            }
        }

        // Position accent
        if (accentArticulation != null) {
            int accentCenterY = ArticulationRenderer.calculateAccentYPx(
                note, 0, staccatoCenterY, hasStaccato);
            double topYPx = accentCenterY - halfContentHeightPx;
            positionElement(accentArticulation, column.getXSs(), topYPx, noteElementPositions);

            if (!isUpper) {
                addToAccumulated(accentArticulation, column.getXSs(), topYPx, accumulated);

                if (topYPx < minYPx) {
                    minYPx = topYPx;
                }
            }
        }

        return minYPx;
    }

    /**
     * Stacks trill for the given column (legacy flag support).
     *
     * @param column               The note column
     * @param accumulated          Accumulated bounding area
     * @param noteElementPositions Map to store element positions
     * @return Minimum Y position reached (most negative)
     */
    private double stackTrill(
        @NotNull NoteColumn column,
        @NotNull Area accumulated,
        @NotNull Map<LineElement, Point2D> noteElementPositions) {

        var note = column.getNote();

        // TODO: In future phases, check for TrillAttachment in new hierarchy
        if (!note.isTrill()) {
            return accumulated.getBounds2D().getMinY();
        }

        // For now, just reserve space for legacy trill
        // Actual positioning will be done in rendering phase
        var trillHeightPx = 12.0;
        var trillWidthPx = 20.0;

        var yPx = accumulated.getBounds2D().getMinY() -
            ScaleContext.getInstance().toPixels(LayoutConstants.TRILL_MARGIN_SS) -
            trillHeightPx;

        var trillBounds = new Rectangle2D.Double(
            column.getXSs() - trillWidthPx / 2,
            yPx,
            trillWidthPx,
            trillHeightPx
        );

        accumulated.add(new Area(trillBounds));

        return yPx;
    }

    /**
     * Stacks fermata for the given column (legacy flag + new attachments).
     *
     * @param column               The note column
     * @param accumulated          Accumulated bounding area
     * @param noteElementPositions Map to store element positions
     * @return Minimum Y position reached (most negative)
     */
    private double stackFermata(
        @NotNull NoteColumn column,
        @NotNull Area accumulated,
        @NotNull Map<LineElement, Point2D> noteElementPositions) {

        var note = column.getNote();
        var minYPx = accumulated.getBounds2D().getMinY();

        // Check new attachment hierarchy first
        var fermata = note.findAttachment(FermataAttachment.class);

        if (fermata != null) {
            var yPx = findClearYPositionPx(
                fermata,
                column.getXSs(),
                accumulated,
                ScaleContext.getInstance().toPixels(LayoutConstants.FERMATA_MARGIN_SS)
            );

            positionElement(fermata, column.getXSs(), yPx, noteElementPositions);
            addToAccumulated(fermata, column.getXSs(), yPx, accumulated);

            if (yPx < minYPx) {
                minYPx = yPx;
            }

            return minYPx;
        }

        // Fall back to legacy flag
        if (note.isFermata()) {
            var fermataHeightPx = 16.0;
            var fermataWidthPx = 16.0;

            var yPx = accumulated.getBounds2D().getMinY() -
                ScaleContext.getInstance().toPixels(LayoutConstants.FERMATA_MARGIN_SS) -
                fermataHeightPx;

            var fermataBounds = new Rectangle2D.Double(
                column.getXSs() - fermataWidthPx / 2,
                yPx,
                fermataWidthPx,
                fermataHeightPx
            );

            accumulated.add(new Area(fermataBounds));

            if (yPx < minYPx) {
                minYPx = yPx;
            }
        }

        return minYPx;
    }

    /**
     * Stacks dynamics for the given column (point dynamics only).
     *
     * @param column               The note column
     * @param accumulated          Accumulated bounding area
     * @param noteElementPositions Map to store element positions
     * @return Minimum Y position reached (most negative)
     */
    private double stackDynamics(
        @NotNull NoteColumn column,
        @NotNull Area accumulated,
        @NotNull Map<LineElement, Point2D> noteElementPositions) {

        // TODO: Implement when DynamicAttachment is ready
        // For now, dynamics are deferred
        return accumulated.getBounds2D().getMinY();
    }

    /**
     * Stacks tempo/beat change for the given column (legacy properties + new attachments).
     *
     * @param column               The note column
     * @param accumulated          Accumulated bounding area
     * @param noteElementPositions Map to store element positions
     * @return Minimum Y position reached (most negative)
     */
    private double stackTempo(
        @NotNull NoteColumn column,
        @NotNull Area accumulated,
        @NotNull Map<LineElement, Point2D> noteElementPositions) {

        var note = column.getNote();
        var minYPx = accumulated.getBounds2D().getMinY();

        // Check for tempo change (legacy property)
        if (note.getTempoChange() != null) {
            var tempoHeightPx = 20.0;
            var tempoWidthPx = 60.0;

            var yPx = accumulated.getBounds2D().getMinY() -
                ScaleContext.getInstance().toPixels(LayoutConstants.TEMPO_MARGIN_SS) -
                tempoHeightPx;

            var tempoBounds = new Rectangle2D.Double(
                column.getXSs() - tempoWidthPx / 2,
                yPx,
                tempoWidthPx,
                tempoHeightPx
            );

            accumulated.add(new Area(tempoBounds));

            if (yPx < minYPx) {
                minYPx = yPx;
            }
        }

        // Check for beat change (legacy property)
        if (note.getBeatChange() != null) {
            var beatChangeHeightPx = 20.0;
            var beatChangeWidthPx = 40.0;

            var yPx = accumulated.getBounds2D().getMinY() -
                ScaleContext.getInstance().toPixels(LayoutConstants.TEMPO_MARGIN_SS) -
                beatChangeHeightPx;

            var beatChangeBounds = new Rectangle2D.Double(
                column.getXSs() - beatChangeWidthPx / 2,
                yPx,
                beatChangeWidthPx,
                beatChangeHeightPx
            );

            accumulated.add(new Area(beatChangeBounds));

            if (yPx < minYPx) {
                minYPx = yPx;
            }
        }

        // TODO: Also check new TempoAttachment and BeatChangeAttachment hierarchy

        return minYPx;
    }

    /**
     * Stacks annotations for the given column (legacy property + new attachments).
     *
     * @param column               The note column
     * @param accumulated          Accumulated bounding area
     * @param noteElementPositions Map to store element positions
     * @return Minimum Y position reached (most negative)
     */
    private double stackAnnotations(
        @NotNull NoteColumn column,
        @NotNull Area accumulated,
        @NotNull Map<LineElement, Point2D> noteElementPositions) {

        var note = column.getNote();
        var minYPx = accumulated.getBounds2D().getMinY();

        // Check for annotation (legacy property)
        if (note.getAnnotation() != null) {
            var annotationHeightPx = 14.0;
            var annotationWidthPx = 40.0; // TODO: Measure actual text width

            var yPx = accumulated.getBounds2D().getMinY() -
                ScaleContext.getInstance().toPixels(LayoutConstants.ANNOTATION_MARGIN_SS) -
                annotationHeightPx;

            var annotationBounds = new Rectangle2D.Double(
                column.getXSs() - annotationWidthPx / 2,
                yPx,
                annotationWidthPx,
                annotationHeightPx
            );

            accumulated.add(new Area(annotationBounds));

            if (yPx < minYPx) {
                minYPx = yPx;
            }
        }

        // TODO: Also check new AnnotationAttachment hierarchy

        return minYPx;
    }

    /**
     * Returns the bounding area for the note in this column.
     * This includes the note head, stem, flag, and ledger lines.
     *
     * @param column The note column
     * @return Bounding area for the note
     */
    private @NotNull Area getNoteBoundingArea(@NotNull NoteColumn column) {
        var note = column.getNote();

        // Get stem bounds
        var stemTopPx = column.getStemTopSs();
        var stemBottomPx = column.getStemBottomSs();

        // Create bounding area from stem top to stem bottom
        var bounds = new Rectangle2D.Double(
            column.getXSs() - NOTE_HEAD_WIDTH_PX / 2,
            stemTopPx,
            NOTE_HEAD_WIDTH_PX,
            stemBottomPx - stemTopPx
        );

        return new Area(bounds);
    }

    /**
     * Finds a Y position for the element that clears the accumulated bounding area.
     * <p>
     * The element is positioned above the accumulated area with the specified margin.
     * If the initial position would cause intersection, the element is moved upward
     * until clear.
     *
     * @param element     The element to position
     * @param x           X position of the element
     * @param accumulated Accumulated bounding area
     * @param marginPx    Margin from accumulated area in pixels
     * @return Y position for the element (top-left corner) in pixels
     */
    private double findClearYPositionPx(
        @NotNull LineElement element,
        double x,
        @NotNull Area accumulated,
        double marginPx) {

        // Start from top of accumulated bounding area
        var accBounds = accumulated.getBounds2D();
        var candidateYPx = accBounds.getMinY() - marginPx - element.getContentHeight();

        // Create element bounds at candidate position
        var elementBounds = new Rectangle2D.Double(
            x - element.getContentWidth() / 2,
            candidateYPx,
            element.getContentWidth(),
            element.getContentHeight()
        );

        var elementArea = new Area(elementBounds);

        // Check for intersection
        var testArea = new Area(accumulated);
        testArea.intersect(elementArea);

        // If intersects, move up until clear
        while (!testArea.isEmpty()) {
            candidateYPx -= 1.0;

            elementBounds.setRect(
                x - element.getContentWidth() / 2,
                candidateYPx,
                element.getContentWidth(),
                element.getContentHeight()
            );

            elementArea = new Area(elementBounds);
            testArea = new Area(accumulated);
            testArea.intersect(elementArea);
        }

        return candidateYPx;
    }

    /**
     * Positions an element at the given X, Y coordinates.
     *
     * @param element              The element to position
     * @param x                    X position
     * @param y                    Y position
     * @param noteElementPositions Map to store the position
     */
    private void positionElement(
        @NotNull LineElement element,
        double x,
        double y,
        @NotNull Map<LineElement, Point2D> noteElementPositions) {

        var position = new Point2D.Double(x, y);
        element.setPosition(position);
        noteElementPositions.put(element, position);
    }

    /**
     * Adds an element's bounds to the accumulated bounding area.
     *
     * @param element     The element
     * @param x           X position
     * @param y           Y position
     * @param accumulated Accumulated bounding area to update
     */
    private void addToAccumulated(
        @NotNull LineElement element,
        double x,
        double y,
        @NotNull Area accumulated) {

        var bounds = new Rectangle2D.Double(
            x - element.getContentWidth() / 2,
            y,
            element.getContentWidth(),
            element.getContentHeight()
        );

        accumulated.add(new Area(bounds));
    }

    /**
     * Finds the lowest Y position of any note bounding area across all columns.
     * This is used to calculate the lyrics baseline position.
     *
     * @param columns List of note columns
     * @return Lowest Y position (maximum Y value)
     */
    private double findLowestNoteBoundingYPx(@NotNull List<NoteColumn> columns) {
        var lowestYPx = 0.0;

        for (var column : columns) {
            var stemBottomPx = column.getStemBottomSs();

            if (stemBottomPx > lowestYPx) {
                lowestYPx = stemBottomPx;
            }
        }

        return lowestYPx;
    }
}
