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

import java.awt.Graphics2D;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.layout.Articulation;
import songscribe.ui.layout.Attachment;
import songscribe.ui.layout.FermataAttachment;
import songscribe.ui.layout.LineElement;

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
        var maxHeightAboveStaff = 0.0;

        // Process each column
        for (var column : columns) {
            var note = column.getNote();
            var noteElementPositions = new HashMap<LineElement, Point2D>();
            elementPositions.put(note, noteElementPositions);

            // Start with note bounds as the initial bounding area
            var accumulated = getNoteBoundingArea(column);

            // Track the highest point reached for this column
            var columnMaxY = accumulated.getBounds2D().getMinY();

            // Process each layer in order (bottom to top)
            var articulationsMaxY = stackArticulations(column, accumulated, noteElementPositions);
            if (articulationsMaxY < columnMaxY) {
                columnMaxY = articulationsMaxY;
            }

            var trillMaxY = stackTrill(column, accumulated, noteElementPositions);
            if (trillMaxY < columnMaxY) {
                columnMaxY = trillMaxY;
            }

            var fermataMaxY = stackFermata(column, accumulated, noteElementPositions);
            if (fermataMaxY < columnMaxY) {
                columnMaxY = fermataMaxY;
            }

            var dynamicsMaxY = stackDynamics(column, accumulated, noteElementPositions);
            if (dynamicsMaxY < columnMaxY) {
                columnMaxY = dynamicsMaxY;
            }

            var tempoMaxY = stackTempo(column, accumulated, noteElementPositions);
            if (tempoMaxY < columnMaxY) {
                columnMaxY = tempoMaxY;
            }

            var annotationsMaxY = stackAnnotations(column, accumulated, noteElementPositions);
            if (annotationsMaxY < columnMaxY) {
                columnMaxY = annotationsMaxY;
            }

            // Update global maximum height
            if (columnMaxY < maxHeightAboveStaff) {
                maxHeightAboveStaff = columnMaxY;
            }
        }

        // Calculate lyrics baseline
        var lowestNoteY = findLowestNoteBoundingY(columns);
        var lyricsBaselineY = lowestNoteY + LayoutConstants.px(LayoutConstants.LYRICS_BASELINE_OFFSET);

        // Calculate line height
        var hasLyrics = columns.stream().anyMatch(NoteColumn::hasSyllable);
        var lyricsHeight = hasLyrics ? 20.0 : 0.0; // TODO: Measure actual lyric height
        var interLineMargin = 10.0; // TODO: Get from LayoutConstants

        var lineHeight = LayoutConstants.STAFF_HEIGHT +
                         Math.abs(maxHeightAboveStaff) +
                         lyricsHeight +
                         interLineMargin;

        return new VerticalStackingResult(
                elementPositions,
                maxHeightAboveStaff,
                lyricsBaselineY,
                lineHeight
        );
    }

    /**
     * Stacks articulations for the given column.
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

        var minY = accumulated.getBounds2D().getMinY();

        for (var articulation : articulations) {
            var y = findClearYPosition(
                    articulation,
                    column.getX(),
                    accumulated,
                    LayoutConstants.px(LayoutConstants.ARTICULATION_MARGIN)
            );

            positionElement(articulation, column.getX(), y, noteElementPositions);
            addToAccumulated(articulation, column.getX(), y, accumulated);

            if (y < minY) {
                minY = y;
            }
        }

        return minY;
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
        var trillHeight = 12.0;
        var trillWidth = 20.0;

        var y = accumulated.getBounds2D().getMinY() -
                LayoutConstants.px(LayoutConstants.TRILL_MARGIN) -
                trillHeight;

        var trillBounds = new Rectangle2D.Double(
                column.getX() - trillWidth / 2,
                y,
                trillWidth,
                trillHeight
        );

        accumulated.add(new Area(trillBounds));

        return y;
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
        var minY = accumulated.getBounds2D().getMinY();

        // Check new attachment hierarchy first
        var fermata = note.findAttachment(FermataAttachment.class);

        if (fermata != null) {
            var y = findClearYPosition(
                    fermata,
                    column.getX(),
                    accumulated,
                    LayoutConstants.px(LayoutConstants.FERMATA_MARGIN)
            );

            positionElement(fermata, column.getX(), y, noteElementPositions);
            addToAccumulated(fermata, column.getX(), y, accumulated);

            if (y < minY) {
                minY = y;
            }

            return minY;
        }

        // Fall back to legacy flag
        if (note.isFermata()) {
            var fermataHeight = 16.0;
            var fermataWidth = 16.0;

            var y = accumulated.getBounds2D().getMinY() -
                    LayoutConstants.px(LayoutConstants.FERMATA_MARGIN) -
                    fermataHeight;

            var fermataBounds = new Rectangle2D.Double(
                    column.getX() - fermataWidth / 2,
                    y,
                    fermataWidth,
                    fermataHeight
            );

            accumulated.add(new Area(fermataBounds));

            if (y < minY) {
                minY = y;
            }
        }

        return minY;
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
        var minY = accumulated.getBounds2D().getMinY();

        // Check for tempo change (legacy property)
        if (note.getTempoChange() != null) {
            var tempoHeight = 20.0;
            var tempoWidth = 60.0;

            var y = accumulated.getBounds2D().getMinY() -
                    LayoutConstants.px(LayoutConstants.TEMPO_MARGIN) -
                    tempoHeight;

            var tempoBounds = new Rectangle2D.Double(
                    column.getX() - tempoWidth / 2,
                    y,
                    tempoWidth,
                    tempoHeight
            );

            accumulated.add(new Area(tempoBounds));

            if (y < minY) {
                minY = y;
            }
        }

        // Check for beat change (legacy property)
        if (note.getBeatChange() != null) {
            var beatChangeHeight = 20.0;
            var beatChangeWidth = 40.0;

            var y = accumulated.getBounds2D().getMinY() -
                    LayoutConstants.px(LayoutConstants.TEMPO_MARGIN) -
                    beatChangeHeight;

            var beatChangeBounds = new Rectangle2D.Double(
                    column.getX() - beatChangeWidth / 2,
                    y,
                    beatChangeWidth,
                    beatChangeHeight
            );

            accumulated.add(new Area(beatChangeBounds));

            if (y < minY) {
                minY = y;
            }
        }

        // TODO: Also check new TempoAttachment and BeatChangeAttachment hierarchy

        return minY;
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
        var minY = accumulated.getBounds2D().getMinY();

        // Check for annotation (legacy property)
        if (note.getAnnotation() != null) {
            var annotationHeight = 14.0;
            var annotationWidth = 40.0; // TODO: Measure actual text width

            var y = accumulated.getBounds2D().getMinY() -
                    LayoutConstants.px(LayoutConstants.ANNOTATION_MARGIN) -
                    annotationHeight;

            var annotationBounds = new Rectangle2D.Double(
                    column.getX() - annotationWidth / 2,
                    y,
                    annotationWidth,
                    annotationHeight
            );

            accumulated.add(new Area(annotationBounds));

            if (y < minY) {
                minY = y;
            }
        }

        // TODO: Also check new AnnotationAttachment hierarchy

        return minY;
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
        var stemTop = column.getStemTop();
        var stemBottom = column.getStemBottom();

        // Note head is typically 8px wide, centered on X
        var noteHeadWidth = 8.0;
        var noteHeadHeight = 6.0;

        // Create bounding area from stem top to stem bottom
        var bounds = new Rectangle2D.Double(
                column.getX() - noteHeadWidth / 2,
                stemTop,
                noteHeadWidth,
                stemBottom - stemTop
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
     * @param margin      Margin from accumulated area (in pixels)
     * @return Y position for the element (top-left corner)
     */
    private double findClearYPosition(
            @NotNull LineElement element,
            double x,
            @NotNull Area accumulated,
            double margin) {

        // Start from top of accumulated bounding area
        var accBounds = accumulated.getBounds2D();
        var candidateY = accBounds.getMinY() - margin - element.getContentHeight();

        // Create element bounds at candidate position
        var elementBounds = new Rectangle2D.Double(
                x - element.getContentWidth() / 2,
                candidateY,
                element.getContentWidth(),
                element.getContentHeight()
        );

        var elementArea = new Area(elementBounds);

        // Check for intersection
        var testArea = new Area(accumulated);
        testArea.intersect(elementArea);

        // If intersects, move up until clear
        while (!testArea.isEmpty()) {
            candidateY -= 1.0;

            elementBounds.setRect(
                    x - element.getContentWidth() / 2,
                    candidateY,
                    element.getContentWidth(),
                    element.getContentHeight()
            );

            elementArea = new Area(elementBounds);
            testArea = new Area(accumulated);
            testArea.intersect(elementArea);
        }

        return candidateY;
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
    private double findLowestNoteBoundingY(@NotNull List<NoteColumn> columns) {
        var lowestY = 0.0;

        for (var column : columns) {
            var stemBottom = column.getStemBottom();

            if (stemBottom > lowestY) {
                lowestY = stemBottom;
            }
        }

        return lowestY;
    }
}
