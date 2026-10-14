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

package songscribe.engraving;

/**
 * Hairpin wedge length and padding constants ported from LilyPond, in staff spaces.
 */
public final class HairpinMetrics {

    /**
     * From LilyPond's {@code Hairpin.minimum-length}. Spacing pushes the notes apart rather than
     * squashing the wedge.
     */
    public static final double MINIMUM_LENGTH_SS = 2.0;

    /**
     * From LilyPond's {@code Hairpin.bound-padding}.
     */
    public static final double BOUND_PADDING_SS = 1.0;

    private static final double BACK_TO_BACK_PADDING_DIVISOR = 3;

    public static final double BACK_TO_BACK_PADDING_SS =
        BOUND_PADDING_SS / BACK_TO_BACK_PADDING_DIVISOR;

    private HairpinMetrics() {}
}
