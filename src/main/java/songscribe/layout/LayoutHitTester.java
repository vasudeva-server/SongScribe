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

package songscribe.layout;

import module java.desktop;

import java.util.Collections;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.engraving.StaffHeaderMetrics;
import songscribe.layout.LayoutResult.LyricHit;

/**
 * Answers "what is under the mouse?" against a finished {@link LayoutResult}.
 * <p>
 * Two kinds of question live here: which existing element or lyric a point lands on, and where a
 * preview element being inserted in edit mode should be drawn. Both read the result's committed
 * geometry and neither changes it, so they sit beside the layout data rather than inside it.
 * <p>
 * Every {@link LayoutResult} owns one of these; call the delegating methods on the result rather
 * than constructing this directly.
 */
public final class LayoutHitTester {

    /**
     * Offset for positioning a preview element before the first element in the line (ss).
     */
    private static final double PREVIEW_BEFORE_FIRST_OFFSET_SS = 1.875;  // 15px

    private final LayoutResult layoutResult;

    LayoutHitTester(LayoutResult layoutResult) {
        this.layoutResult = layoutResult;
    }

    // ==========================================================================
    // Lyric Hit Testing
    // ==========================================================================

    public @Nullable LyricHit hitTestLyric(LyricRenderMetrics lyricRenderMetrics, Line line, Point2D pointPx) {
        var rowHeightSs = lyricRenderMetrics.lyricBoxHeightSs();
        var rowTopYSs = layoutResult.lyricAreaBaseYSs();
        var pointXSs = ScaleContext.pxToSs(pointPx.getX());
        var pointYSs = ScaleContext.pxToSs(pointPx.getY());

        for (var element : line.getElements()) {
            for (var box : layoutResult.getLyricBoxes(element)) {
                // Matches the ending and note-head hit tests, which also test containment
                // with Rectangle2D: left and top edges inclusive, right and bottom exclusive.
                var hitRect = new Rectangle2D.Double(box.xSs(), rowTopYSs, box.widthSs(), rowHeightSs);

                if (hitRect.contains(pointXSs, pointYSs)) {
                    return new LyricHit(element, box.verseIndex());
                }
            }
        }

        return null;
    }

    // ==========================================================================
    // Key Edit Hit Targets
    // ==========================================================================

    /**
     * Returns {@code line} when {@code mouseXSs} falls within its staff header — the clef and key
     * signature — the header's double-click target for editing that line's own key.
     * <p>
     * Every line's header is a target, whether or not the line holds a key of its own: a header
     * that merely restates an inherited key is drawn identically to one that changes it, so a click
     * cannot tell them apart and both accept it.
     *
     * @param mouseXSs mouse X coordinate, in staff-space units
     * @param line     the line whose header is being tested
     * @return {@code line} itself, or {@code null} when {@code mouseXSs} falls outside the header
     */
    public @Nullable Line hitTestHeaderKeyEdit(double mouseXSs, Line line) {
        return HorizontalSpacingCalculator.isWithinHeaderXSs(mouseXSs, line) ? line : null;
    }

    /**
     * Returns the line whose key the cautionary key change at the end of {@code line} would edit
     * — the line <em>following</em> {@code line}, not {@code line} itself — when {@code mouseXSs}
     * falls within the cautionary's drawn run of accidentals.
     * <p>
     * The cautionary warns the performer about the key the next line begins in, so double-clicking
     * it edits that line's key where the change is visible, rather than reopening {@code line}'s
     * own key a second time. This returns {@code null}, and nothing is drawn to click on, when
     * {@code line} is the song's last line or the two lines' running keys agree —
     * {@link Key#widthSsFrom} answers zero in both cases.
     * <p>
     * The rect tested is the one rendering draws, including its
     * {@link LayoutResult#overflowsStaffWidth() overflow} placement: on a line whose content
     * overflows the staff, the run sits one line rest past the rightmost solved column rather than
     * pinned to the margin, and this target follows it there.
     *
     * @param mouseXSs mouse X coordinate, in staff-space units
     * @param line     the line whose end is being tested for its cautionary
     * @return the following line, or {@code null} when no cautionary is drawn or {@code mouseXSs}
     *         falls outside its drawn run
     */
    public @Nullable Line hitTestCautionaryKeyEdit(double mouseXSs, Line line) {
        var nextKey = line.nextLineRunningKey();

        if (nextKey == null) {
            return null;
        }

        var widthSs = nextKey.widthSsFrom(line.keyAtEndOfLine());

        if (widthSs == 0) {
            return null;
        }

        var song = line.getSong();
        var startXSs = layoutResult.overflowsStaffWidth()
            ? layoutResult.contentRightEdgeSs() + song.getDefaultRestLengthSs()
            : song.getLineWidthSs() - StaffHeaderMetrics.CAUTIONARY_RIGHT_MARGIN_SS - widthSs;

        if (mouseXSs < startXSs || mouseXSs > startXSs + widthSs) {
            return null;
        }

        return song.getLine(song.indexOfLine(line) + 1);
    }

    /**
     * Returns the {@link KeyChangeElement} at {@code mouseXSs} on {@code line} — the rect
     * bounded by that element's own rendered accidentals is the mid-line double-click target for
     * editing it.
     * <p>
     * Delegates to {@link #findElementAtXSs}, so the same column bounds spacing solved apply; a
     * key signature carries no leading accidental of its own, so {@link ColumnSpan#HEAD} and
     * {@link ColumnSpan#FULL_INK} agree for it and the choice between them is immaterial here.
     *
     * @param mouseXSs mouse X coordinate, in staff-space units
     * @param line     the line to search
     * @return the {@link KeyChangeElement} at that position, or {@code null} when
     *         {@code mouseXSs} does not fall within a mid-line key signature's column
     */
    public @Nullable KeyChangeElement hitTestMidLineKeyEdit(double mouseXSs, Line line) {
        var index = findElementAtXSs(mouseXSs, line, ColumnSpan.FULL_INK);

        if (index < 0) {
            return null;
        }

        var element = line.getElement(index);
        return element instanceof KeyChangeElement keySignature ? keySignature : null;
    }

    // ==========================================================================
    // Preview Element Positioning (Edit Mode)
    // ==========================================================================

    /**
     * Returns the index of the element whose column contains {@code mouseXSs}, or {@code -1} if
     * none.
     * <p>
     * Only the horizontal (X) dimension is checked; Y position is ignored. The left bound of the
     * hit zone depends on {@code span}: {@link ColumnSpan#HEAD} starts at the glyph body
     * ({@link ElementColumn#getXSs()}), excluding a leading accidental; {@link ColumnSpan#FULL_INK}
     * starts at the column's full drawn ink ({@link ElementColumn#getLeftEdgeXSs()}), enclosing the
     * accidental. Both spans share the same right bound, {@link ElementColumn#getRightEdgeXSs()}.
     * <p>
     * Columns are checked in element order and the first match wins, so on an overlap — a grace
     * note's column can overlap its host's when the host's flag is discounted (refs #560) — the
     * lower index (the grace note, which always precedes its host) is returned.
     * <p>
     * The spacing solver assigns each column's X as a running sum of non-negative gaps, so left
     * bounds never decrease as the index grows. Once one is past {@code mouseXSs}, no later
     * column can contain it and the scan stops.
     *
     * @param mouseXSs Mouse X coordinate in staff-space units
     * @param line     The line containing the elements
     * @param span     Which horizontal slice of each column counts as a hit
     * @return Element index, or {@code -1} if mouseXSs is not within any element column's bounds
     */
    public int findElementAtXSs(double mouseXSs, Line line, ColumnSpan span) {
        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);
            var column = layoutResult.getElementColumn(element);

            if (column == null) {
                continue;
            }

            var leftBoundSs = span == ColumnSpan.HEAD ? column.getXSs() : column.getLeftEdgeXSs();

            if (mouseXSs < leftBoundSs) {
                break;
            }

            if (mouseXSs <= column.getRightEdgeXSs()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Finds which insertion slot a mouse X coordinate falls into.
     * <p>
     * Insertion slots are the positions where an element can be inserted or replaced:
     * <ul>
     *   <li>Index 0 to elementCount-1: over an existing element (for replacement)</li>
     *   <li>Index elementCount: after the last element (for appending)</li>
     * </ul>
     * <p>
     * If the mouse is within the horizontal bounds of an element head, returns that element's index
     * to indicate replacement. Otherwise, returns the insertion slot between elements.
     *
     * @param mouseXSs Mouse X coordinate in staff-space units
     * @param line     The line containing the elements
     * @return Insertion index (0 to elementCount inclusive)
     */
    public int findInsertionIndex(double mouseXSs, Line line) {
        // Exclude the auto-maintained terminal: insertions always occur before it,
        // and the gap between the last real element and the barline should behave as
        // "after the last element" rather than a between-elements midpoint.
        var elementCount = line.effectiveElementCount();

        if (elementCount == 0) {
            return 0;
        }

        // Check each element to see if mouse is within its head bounds
        var elementAtX = findElementAtXSs(mouseXSs, line, ColumnSpan.HEAD);

        if (elementAtX >= 0 && elementAtX < elementCount) {
            return elementAtX;
        }

        // Mouse is not over any element head - find insertion slot between elements

        // Check if before first element
        var firstElement = line.getElement(0);
        var firstColumn = layoutResult.getElementColumn(firstElement);

        if (firstColumn == null) {
            return 0;
        }

        if (mouseXSs < firstColumn.getXSs()) {
            return 0;
        }

        // Check if after last element
        var lastElement = line.getElement(elementCount - 1);
        var lastColumn = layoutResult.getElementColumn(lastElement);

        if (lastColumn == null) {
            return elementCount;
        }

        if (mouseXSs > lastColumn.getRightEdgeXSs()) {
            return elementCount;
        }

        // Find the slot between elements (excluding element head bounds)
        for (var i = 0; i < elementCount - 1; i++) {
            var currentElement = line.getElement(i);
            var nextElement = line.getElement(i + 1);

            var currentColumn = layoutResult.getElementColumn(currentElement);
            var nextColumn = layoutResult.getElementColumn(nextElement);

            if (currentColumn == null || nextColumn == null) {
                continue;
            }

            var currentRight = currentColumn.getRightEdgeXSs();
            var nextLeft = nextColumn.getXSs();

            // Check if mouseXSs is in the gap between element heads
            if (mouseXSs > currentRight && mouseXSs < nextLeft) {
                return i + 1;
            }
        }

        // Fallback: return position after last element
        return elementCount;
    }

    /**
     * Calculates the X position for rendering a preview element at a given index.
     * <p>
     * If the mouse is within the horizontal bounds of an element head, snaps to that element,
     * centering the preview's glyph on it. Otherwise, centers the preview in the room the
     * neighbouring glyphs leave, or between the last element and the line's right boundary.
     *
     * @param insertionIndex      The insertion index (0 to elementCount inclusive)
     * @param mouseXSs            Mouse X coordinate in staff-space units (used to detect if over an
     *                            element head); ignored when {@code betweenElementsOnly} is true
     * @param previewElement      The element to be inserted (used to calculate extents for after-last
     *                            positioning)
     * @param line                The line containing the elements
     * @param betweenElementsOnly When true, skips the element-head snap check below and always
     *                            returns a between-elements/before-first/after-last position — for
     *                            callers (e.g. the paste-mode insertion marker) that never merge onto
     *                            an existing element's own position
     * @return X position in staff-space units for rendering the preview element
     */
    public double calculateInsertionXSs(
        int insertionIndex,
        double mouseXSs,
        StaffElement previewElement,
        Line line,
        boolean betweenElementsOnly) {

        // Exclude the auto-maintained terminal from positioning decisions —
        // it sits at the line's right edge and must not be treated as a real
        // neighbour for spacing the preview.
        var elementCount = line.effectiveElementCount();

        // Empty line - use first element position (clef + key signature + offset)
        if (elementCount == 0) {
            return HorizontalSpacingCalculator.calculateFirstElementXSs(line);
        }

        // Check if mouse is over any element head - if so, snap to that element's position.
        // Include the terminal so hovering over it snaps the preview to its position.
        if (!betweenElementsOnly) {
            for (var i = 0; i < line.elementCount(); i++) {
                var element = line.getElement(i);
                var column = layoutResult.getElementColumn(element);

                if (column == null) {
                    continue;
                }

                var elementX = column.getXSs();

                if (mouseXSs >= elementX && mouseXSs <= elementX + column.getRightExtentSs()) {
                    // Mouse is over this element head - snap to its position.
                    // For the terminal, right-align the preview to the terminal's right edge
                    // so a wider replacement (e.g. REPEAT_RIGHT replacing FINAL_DOUBLE_BARLINE)
                    // doesn't overflow the staff boundary.
                    if (line.getSong().isAutoMaintainedTerminal(element, line)) {
                        var previewRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                                previewElement, false, StaffElement.Direction.UP);
                        return elementX + column.getRightExtentSs() - previewRightExtentSs;
                    }

                    // Center the preview's glyph on the hovered element's glyph rather than
                    // aligning the two origins: a preview narrower than what it would replace —
                    // a thin barline over a notehead — otherwise sits against that element's
                    // left edge instead of on it (refs #689). Both widths are measured the same
                    // way, so a preview of the hovered element's own type still lands exactly
                    // on it.
                    return centeredInRoomSs(
                        elementX, elementX + element.getType().getElementWidthSs(), previewElement);
                }
            }
        }

        // Mouse is not over an element head - handle insertion

        // Before first element - position to the left
        if (insertionIndex == 0) {
            var firstElement = line.getElement(0);
            var firstColumn = layoutResult.getElementColumn(firstElement);

            if (firstColumn == null) {
                return HorizontalSpacingCalculator.calculateFirstElementXSs(line);
            }

            return firstColumn.getXSs() - PREVIEW_BEFORE_FIRST_OFFSET_SS;
        }

        // After last element - use same spacing logic as layout engine
        if (insertionIndex >= elementCount) {
            var lastElement = line.getElement(elementCount - 1);
            var lastColumn = layoutResult.getElementColumn(lastElement);

            if (lastColumn == null) {
                return HorizontalSpacingCalculator.calculateFirstElementXSs(line);
            }

            // Build a temporary column for the preview element to calculate proper spacing
            var insertionColumn = new ElementColumn(
                previewElement,
                Collections.emptyList(),
                ElementColumnBuilder.calculateLeftExtentSs(previewElement),
                ElementColumnBuilder.calculateRightExtentSs(previewElement, false, StaffElement.Direction.UP),
                ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(previewElement, false, StaffElement.Direction.UP),
                0,
                0,
                null,  // a preview element carries no lyric
                0,
                false
            );

            var song = line.getSong();

            // Space the pair through the spring engine the committed layout uses, so the preview
            // honours the song's line rest instead of a fixed default gap.
            var naturalXSs = lastColumn.getXSs()
                + HorizontalSpacingCalculator.buildSpring(
                    lastColumn, insertionColumn, song.getDefaultRestLengthSs()).naturalLengthSs();

            // The line can already be visually full — its committed elements compressed to fit —
            // so the preview's default spacing can overrun what room is actually left. The nearest
            // hard boundary is the auto-maintained terminal (excluded from elementCount above) when
            // the line has one, otherwise the staff margin itself. Splitting the remaining room
            // between the last real element and that boundary reads as the preview floating past
            // the end of the line, rather than pinned flush against the boundary (refs #608).
            var boundarySs = song.getLineWidthSs();
            var terminalColumn = line.elementCount() > elementCount
                ? layoutResult.getElementColumn(line.getElement(elementCount))
                : null;

            if (terminalColumn != null) {
                boundarySs = terminalColumn.getXSs();
            }

            if (naturalXSs + insertionColumn.getRightExtentSs() > boundarySs) {
                // The room starts at the last element's drawn right edge, measured the same way as
                // the between-elements case below: past the glyph, but not past its flag or dots.
                return centeredInRoomSs(
                    lastColumn.getXSs() + lastElement.getType().getElementWidthSs(), boundarySs, previewElement);
            }

            return naturalXSs;
        }

        // Between elements - center the preview in the room the two neighbouring glyphs leave
        var prevElement = line.getElement(insertionIndex - 1);
        var currElement = line.getElement(insertionIndex);

        var prevColumn = layoutResult.getElementColumn(prevElement);
        var currColumn = layoutResult.getElementColumn(currElement);

        if (prevColumn == null || currColumn == null) {
            return HorizontalSpacingCalculator.calculateFirstElementXSs(line);
        }

        // The room runs from the previous element's drawn right edge to the next element's origin,
        // which is that element's drawn left edge. Extents are deliberately not consulted: a flag,
        // dot or accidental does not shrink the room, but the glyphs themselves do — starting the
        // room at the previous element's own origin would center the preview over that element's
        // notehead rather than in the space beside it (refs #689).
        var roomStartSs = prevColumn.getXSs() + prevElement.getType().getElementWidthSs();

        return centeredInRoomSs(roomStartSs, currColumn.getXSs(), previewElement);
    }

    /**
     * Returns the X at which {@code previewElement}'s glyph sits centered in the room between
     * {@code roomStartSs} and {@code roomEndSs}.
     * <p>
     * Centering is symmetric in both directions: a preview wider than the room straddles it,
     * overlapping each side equally, rather than being pinned to either edge.
     * <p>
     * The width centered is the element's own drawn width, not its column's spacing footprint —
     * a flag, accidental or dot would otherwise leave the glyph the eye actually reads as the
     * element sitting well off center.
     */
    private static double centeredInRoomSs(double roomStartSs, double roomEndSs, StaffElement previewElement) {
        var previewWidthSs = previewElement.getType().getElementWidthSs();
        return roomStartSs + (roomEndSs - roomStartSs - previewWidthSs) / 2;
    }
}
