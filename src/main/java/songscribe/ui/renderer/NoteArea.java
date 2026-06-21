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

package songscribe.ui.renderer;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

/**
 * A note's geometry for glissando endpoint search: the base ink {@code area} (notehead,
 * stem, flags, …) used to find where the ray exits the note, and the {@code offsetArea}
 * (ink pre-expanded by the gap) used to clamp the endpoint away from the stem, with the
 * offset area's bounding box bounding the inward search.
 */
record NoteArea(Area area, Area offsetArea, Rectangle2D offsetBounds) {}
