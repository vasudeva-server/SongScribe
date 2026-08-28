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

package songscribe.smufl;

/**
 * Where a stem meets a notehead, for each of the two stem directions.
 *
 * <p>Both anchors are present because a notehead the font gives one to, it gives both to,
 * which is why neither is nullable.
 *
 * @param stemUpSE   where an upward stem meets the notehead, at its south-east corner
 * @param stemDownNW where a downward stem meets the notehead, at its north-west corner
 */
public record StemAnchors(Anchor stemUpSE, Anchor stemDownNW) {}
