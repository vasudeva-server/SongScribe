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

package songscribe.layout.stacking;

import songscribe.layout.Footprint;
import songscribe.layout.StaffExtents;

/**
 * An element's footprint together with the shapes of its two vertical edges: the inner one, which
 * decides where the element may sit, and the outer one, which it reserves for whatever stacks
 * outside it.
 *
 * @param footprint the element's horizontal footprint
 * @param profiles  the element's inner and outer edges, each measured from the footprint's left
 *                  edge
 */
record Outline(Footprint footprint, StaffExtents.Profiles profiles) {

    /**
     * Returns the outline of an element a neighbor may treat as a rectangle: both edges flat
     * across the whole footprint.
     *
     * @param footprint the element's horizontal footprint
     * @return the outline with {@link StaffExtents.Profiles#flat} edges as wide as {@code footprint}
     */
    static Outline flat(Footprint footprint) {
        return new Outline(footprint, StaffExtents.Profiles.flat(footprint.widthSs()));
    }
}
