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

package songscribe.ui.component.score;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.SlideZone;
import songscribe.dom.StaffElement;
import songscribe.layout.LayoutResult;
import songscribe.ui.action.Actions;
import songscribe.ui.action.ElementTypeAction;

/**
 * Decides which slide a slide tool would draw at a given insertion point, and whether the source
 * note already carries it.
 * <p>
 * Pure with respect to the preview subsystem's tracking state: everything it needs arrives as
 * arguments (or from the selected toolbar action), so {@link PreviewElementManager} and the slide
 * overlays can both ask the same questions without either owning the answers.
 */
final class SlideZoneResolver {

    private SlideZoneResolver() {
    }

    /**
     * Returns whether the given element is a slide placeholder (the insertion
     * element created when a slide tool is selected).
     */
    static boolean isSlidePlaceholder(@Nullable StaffElement element) {
        return element != null && element.getType() == ElementType.SLIDE;
    }

    /**
     * Returns whether the source note at {@code sourceIndex} on {@code line} already carries the
     * slide {@code zone} represents. When it does, no preview slide is drawn (and thus no preview
     * highlight) — the note already has what the tool would add. A null {@code zone} (no zone)
     * trivially matches nothing.
     */
    static boolean sourceAlreadyHasSlide(
        Line line, int sourceIndex, @Nullable SlideZone zone
    ) {
        return zone != null && zone.matches(line.getElement(sourceIndex));
    }

    /**
     * Returns the slide zone the mouse is currently over, or null when the active tool is not a
     * slide tool or the position admits no slide.
     *
     * @param previewElement The active preview element; only a slide placeholder previews a slide
     * @param line           The line the mouse is over
     * @param xIndex         Insertion index from {@link LayoutResult#findInsertionIndex}
     * @param elementAtX     Index of the element head under the mouse, or -1 when in a gap
     */
    static @Nullable SlideZone zoneUnderMouse(
        @Nullable StaffElement previewElement, Line line, int xIndex, int elementAtX
    ) {
        // Hovering over an element head means there is no valid slide target to the left.
        if (!isSlidePlaceholder(previewElement) || elementAtX >= 0) {
            return null;
        }

        return computeSlideZone(line, xIndex, getSelectedSlideZone());
    }

    /**
     * Returns the slide zone corresponding to the currently selected action,
     * or null if neither slide action is selected.
     */
    private static @Nullable SlideZone getSelectedSlideZone() {
        var selected = Actions.DURATION_ACTION_GROUP.getSelected();

        if (selected instanceof ElementTypeAction eta) {
            return eta.getSlideZone();
        }

        return null;
    }

    /**
     * Computes the slide zone type for the given intended type.
     * <p>
     * Returns null if the mouse is to the left of the first note (no source note),
     * or if {@code intendedZone} is null. Otherwise validates whether the given zone
     * can be inserted at the given index: {@code GLISSANDO} requires a pitched note to the
     * right with a different pitch, {@code FALL} only requires a pitched source note.
     * Only pitched notes can be a slide source or target — bar lines, grace
     * notes, rests, and other non-pitched elements are rejected.
     *
     * @param line         The line containing the notes
     * @param xIndex       Insertion index from {@link LayoutResult#findInsertionIndex}
     * @param intendedZone The slide zone to validate, or null
     * @return The zone, or null if no valid zone
     */
    static @Nullable SlideZone computeSlideZone(
        Line line,
        int xIndex,
        @Nullable SlideZone intendedZone) {
        // xIndex=0 means to the left of the first note — no source note to draw from
        if (xIndex <= 0 || line.elementCount() == 0) {
            return null;
        }

        if (intendedZone == null) {
            return null;
        }

        // Only pitched notes can be a slide source or target
        var sourceElement = line.getElement(xIndex - 1);

        if (!sourceElement.getType().isPitchedNote()) {
            return null;
        }

        if (intendedZone == SlideZone.GLISSANDO) {
            // A connecting glissando requires a note to the right
            if (xIndex >= line.elementCount()) {
                return null;
            }

            var targetElement = line.getElement(xIndex);

            // Target must be a pitched note (bar lines, grace notes, rests, etc. are invalid)
            if (!targetElement.getType().isPitchedNote()) {
                return null;
            }

            // A glissando between two notes at one pitch has nothing to traverse
            if (line.isSamePitchAsFollower(xIndex - 1)) {
                return null;
            }
        }

        return intendedZone;
    }
}
