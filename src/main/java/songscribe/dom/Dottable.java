/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.dom;

/**
 * An element that takes augmentation dots: a note or a rest. Each leaf holds its own count;
 * this is the one name for the question "how many dots is it written with", so a site that
 * reads or writes dots on an element it has not narrowed narrows to this rather than to each
 * leaf in turn. A breath mark, a barline and a key change take no dots and are not this.
 */
public sealed interface Dottable permits NoteElement, RestElement {

    /**
     * Returns the dots {@code source} is written with: its own when it takes dots, and
     * {@link Dots#NONE} when it does not, since an element that takes no dots has none to
     * give. This is the answer for an element that has not been narrowed, whether a conversion
     * is carrying dots over or a reader is asking how long it is.
     *
     * @param source the element, of any class
     * @return the source's dots, or {@link Dots#NONE} when it takes none
     */
    static Dots dotsOf(StaffElement source) {
        return source instanceof Dottable dottable ? dottable.dots() : Dots.NONE;
    }

    /**
     * Returns the augmentation dots this element is written with.
     *
     * @return the dots: {@link Dots#NONE} for an undotted element
     */
    Dots dots();

    /**
     * Sets the augmentation dots this element is written with. The value is written as
     * given: the boundaries that read a count from a file convert it through
     * {@link Dots#of(int)} before it reaches here.
     *
     * @param dots the dots to write with
     * @effects changes what {@link #dots()} and
     *     {@link StaffElement#writtenDuration()} answer
     */
    void setDots(Dots dots);
}
