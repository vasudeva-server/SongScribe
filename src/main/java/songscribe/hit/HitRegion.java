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
 * One clickable area on a staff line: a shape, what clicking it selects, and how it
 * resolves against the other areas it overlaps.
 *
 * <p>The shape is a {@link Shape} rather than a rectangle because two kinds tilt with
 * the ink they cover: a glissando's hit strip is a rotated four-point {@code Path2D},
 * and a bracketed tuplet's is the sloped bracket quad together with its number's box.
 * Everything else registered today is a {@code Rectangle2D.Double}.
 *
 * <p><b>Coordinates are layout space:</b> X in line-local staff spaces, Y in staff
 * spaces relative to the staff midline, positive downward. A click point is converted
 * into that space once, at query time.
 *
 * <p>How the region resolves against the ones it overlaps, and whether the mouse-move query
 * scans it, both follow from what it addresses rather than from anything chosen where it is
 * registered — so two regions addressing the same kind can never disagree about either.
 *
 * @param shapeSs the clickable area, in layout space (staff spaces)
 * @param target  what a click inside {@code shapeSs} addresses
 */
public record HitRegion(Shape shapeSs, HitTarget target) {

    /**
     * @return the resolution rank of the kind this region addresses; see {@link HitPriority}
     */
    public HitPriority priority() {
        return target.priority();
    }

    /**
     * @return {@code true} when this region takes part in the mouse-move query,
     *         {@link HitRegistry#hitTestHover}
     */
    public boolean hoverTestable() {
        return target.hoverTestable();
    }
}
