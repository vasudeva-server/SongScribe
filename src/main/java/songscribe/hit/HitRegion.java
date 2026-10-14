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

package songscribe.hit;

import java.awt.Shape;

/**
 * One clickable area on a staff line: a shape and what clicking it addresses. Its priority
 * follows from the target, never from the registration site.
 *
 * <p>Coordinates are layout space: X in line-local staff spaces, Y in staff spaces relative to
 * the staff midline, positive downward.
 */
public record HitRegion(Shape shapeSs, HitTarget target) {

    public HitPriority priority() {
        return target.priority();
    }
}
