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
 * Tuplet bracket geometry constants ported from LilyPond, in staff spaces.
 */
public final class TupletMetrics {

    /** Outward extension of the bracket arm at each endpoint (LilyPond: 0.2ss). */
    public static final double ARM_EXTENSION_SS = 0.2;

    /** Vertical arm height of bracket endpoints (LilyPond: 0.7ss). */
    public static final double BRACKET_ARM_HEIGHT_SS = 0.7;

    /**
     * The gap from the worst note tip to the bracket <em>line</em>, from LilyPond's
     * {@code TupletBracket.padding} (measured tip → line in {@code calc_position_and_height},
     * {@code *offset += padding * dir}). The vertical arms hang {@link #BRACKET_ARM_HEIGHT_SS} down
     * from the line into this gap; they do not push the line up.
     */
    public static final double BRACKET_PADDING_SS = 1.1;

    /** Maximum bracket slope, as a fraction of rise over run (LilyPond: 0.5). */
    public static final double MAX_SLOPE_FACTOR = 0.5;

    private TupletMetrics() {}
}
