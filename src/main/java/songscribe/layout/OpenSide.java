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

package songscribe.layout;

/**
 * Which end of a tie's arc terminates at the staff edge instead of at a notehead.
 * <p>
 * A tie whose two notes sit in different lines is laid out once per line, and each line
 * draws only its own half: the half with the anchor note runs off the right edge, the
 * half with the end note enters from the left edge. Following LilyPond
 * ({@code tie-configuration.cc get_untransformed_bezier}), an open half is still a
 * complete arc over its own width — it rises from the baseline and returns to it at the
 * open end — so {@link LayoutResult.TieLayout}'s geometry describes it in full and nothing
 * downstream has to reconstruct the missing side. What the open side does say is that this
 * arc's termination is a staff edge, which is why it is recorded rather than inferred from
 * the geometry.
 * <p>
 * Top level rather than nested in {@link LayoutResult.TieLayout}, the final geometry it ends
 * up on: {@link ElementColumn} decides the open side while pairing a tie's endpoints with
 * columns, well before any geometry exists, and an earlier stage of the layout pipeline
 * should not have to name a later stage's output to describe its own result.
 */
public enum OpenSide {

    /** Both ends meet a notehead in this line: an ordinary, whole tie. */
    NONE,

    /** The start end runs to this line's left edge; the anchor note is in an earlier line. */
    START,

    /** The end end runs to this line's right edge; the end note is in a later line. */
    END
}
