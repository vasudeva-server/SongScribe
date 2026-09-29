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
     * Minimum drawn length of a hairpin in staff spaces, from LilyPond's
     * {@code Hairpin.minimum-length} (scm/define-grobs.scm). LilyPond applies this
     * as a spacing rod via {@code ly:spanner::set-spacing-rods}, so the notes are
     * pushed apart rather than the wedge being squashed.
     */
    public static final double MINIMUM_LENGTH_SS = 2.0;

    /**
     * The gap a hairpin endpoint keeps from an adjacent text dynamic or barline,
     * from LilyPond's {@code Hairpin.bound-padding} (scm/define-grobs.scm).
     */
    public static final double BOUND_PADDING_SS = 1.0;

    /**
     * The gap each of two back-to-back hairpins keeps from their shared column
     * center. {@code Hairpin::print} uses {@code padding / 3} for this case
     * ("we're not adjacent to a text-dynamic, and we may move closer"), so it is
     * derived from {@link #BOUND_PADDING_SS} rather than written as a literal.
     */
    public static final double BACK_TO_BACK_PADDING_SS = BOUND_PADDING_SS / 3;

    private HairpinMetrics() {}
}
