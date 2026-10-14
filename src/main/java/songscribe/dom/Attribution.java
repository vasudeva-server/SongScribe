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

package songscribe.dom;

/**
 * Block element for the song's attribution above the first staff line.
 * <p>
 * Always right-aligned to the staff right edge; the user cannot shift it horizontally, so
 * {@link #getUserXOffsetSs()} returns {@code 0.0}.
 */
public class Attribution extends LineElement {

    public static final double ATTRIBUTION_MARGIN_BOTTOM_SS = 2.0;

    public static final double ATTRIBUTION_RIGHT_MARGIN_SS = 0.5;

    @Override
    public double getUserXOffsetSs() {
        return 0.0;
    }
}
