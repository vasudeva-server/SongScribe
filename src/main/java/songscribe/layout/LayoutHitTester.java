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

import org.jspecify.annotations.Nullable;

import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;

/**
 * Answers "what is under the mouse?" and where an inserted preview element is drawn, against a
 * finished {@link LayoutResult}. Call the delegating methods on the result rather than
 * constructing this directly.
 */
public final class LayoutHitTester {

    private static final double PREVIEW_BEFORE_FIRST_OFFSET_SS = 1.875;  // 15px

    private final LayoutResult layoutResult;

    LayoutHitTester(LayoutResult layoutResult) {
        this.layoutResult = layoutResult;
    }

    // ==========================================================================
    // Key Edit Hit Targets
    // ==========================================================================

    /**
     * Every line's header is a target, whether or not the line holds a key of its own.
     *
     * @return {@code null} when {@code mouseXSs} falls outside the header
     */
    public @Nullable Line hitTestHeaderKeyEdit(double mouseXSs, Line line) {
        return HorizontalSpacingCalculator.isWithinHeaderXSs(mouseXSs, line) ? line : null;
    }

    /**
     * Returns the line the cautionary key change at the end of {@code line} would edit — the line
     * <em>following</em> {@code line}, not {@code line} itself.
     * <p>
     * The target is the run of accidentals and not the barline ahead of it, so clicking the
     * line's own barline still selects that element.
     *
     * @return {@code null} when no cautionary is drawn or {@code mouseXSs} falls outside its
     *         drawn run of accidentals
     */
    public @Nullable Line hitTestCautionaryKeyEdit(double mouseXSs, Line line) {
        var cautionary = CautionaryKeySignature.of(line);

        if (cautionary == null) {
            return null;
        }

        var song = line.getSong();
        var startXSs = cautionary.placeIn(
            layoutResult.overflowsStaffWidth(), layoutResult.contentRightEdgeSs(), song.getLineWidthSs().value()
        ).accidentalsXSs();

        if (mouseXSs < startXSs || mouseXSs > startXSs + cautionary.accidentalsWidthSs()) {
            return null;
        }

        return song.getLine(line.index() + 1);
    }

    /**
     * @return {@code null} when {@code mouseXSs} does not fall within a mid-line key signature's
     *         column
     */
    public @Nullable KeyChangeElement hitTestMidLineKeyEdit(double mouseXSs, Line line) {
        var index = findElementAtXSs(mouseXSs, line);

        if (index == null) {
            return null;
        }

        var element = line.getElement(index);
        return element instanceof KeyChangeElement keyChange ? keyChange : null;
    }

    // ==========================================================================
    // Preview Element Positioning (Edit Mode)
    // ==========================================================================

    /**
     * Only X is checked. The hit zone is the glyph body, {@link ElementColumn#getXSs()} to
     * {@link ElementColumn#getRightEdgeXSs()}, so a leading accidental is not part of its element.
     * <p>
     * On an overlap the lower index wins: a grace note's column can overlap its host's, and the
     * grace note precedes its host.
     *
     * @return {@code null} if mouseXSs is not within any element column's bounds
     */
    public @Nullable Integer findElementAtXSs(double mouseXSs, Line line) {
        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);
            var column = layoutResult.getElementColumn(element);

            if (column == null) {
                continue;
            }

            if (mouseXSs < column.getXSs()) {
                break;
            }

            if (mouseXSs <= column.getRightEdgeXSs()) {
                return i;
            }
        }

        return null;
    }

    /**
     * Indexes 0 to elementCount-1 are over an existing element (replacement); elementCount is
     * after the last element (append).
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
        var elementAtX = findElementAtXSs(mouseXSs, line);

        if (elementAtX != null && elementAtX < elementCount) {
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
     * @param mouseXSs            ignored when {@code betweenElementsOnly} is true
     * @param betweenElementsOnly for callers (e.g. the paste-mode insertion marker) that never
     *                            merge onto an existing element's own position
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
                    if (line.getSong().isTerminal(element, line)) {
                        var previewRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                                previewElement, Beaming.UNBEAMED);
                        return elementX + column.getRightExtentSs() - previewRightExtentSs;
                    }

                    // Center the preview's glyph on the hovered element's glyph rather than
                    // aligning the two origins: a preview narrower than what it would replace —
                    // a thin barline over a notehead — otherwise sits against that element's
                    // left edge instead of on it. Both widths are measured the same
                    // way, so a preview of the hovered element's own type still lands exactly
                    // on it.
                    return centeredInRoomSs(
                        elementX, elementX + element.getGlyphWidthSs(), previewElement);
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
            var insertionColumn = ElementColumnBuilder.buildLightweightColumn(previewElement);

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
            // the end of the line, rather than pinned flush against the boundary.
            var boundarySs = song.getLineWidthSs().value();
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
                    lastColumn.getXSs() + lastElement.getGlyphWidthSs(), boundarySs, previewElement);
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
        // notehead rather than in the space beside it. The width is the element's, not
        // its type's: a mid-line key change's type width is only a one-accidental floor, so the
        // room would otherwise start inside the signature and the marker land on its accidentals.
        var roomStartSs = prevColumn.getXSs() + prevElement.getGlyphWidthSs();

        return centeredInRoomSs(roomStartSs, currColumn.getXSs(), previewElement);
    }

    /**
     * A preview wider than the room straddles it equally. The width centered is the element's own
     * drawn width, not its column's spacing footprint.
     */
    private static double centeredInRoomSs(double roomStartSs, double roomEndSs, StaffElement previewElement) {
        var previewWidthSs = previewElement.getGlyphWidthSs();
        return roomStartSs + (roomEndSs - roomStartSs - previewWidthSs) / 2;
    }
}
