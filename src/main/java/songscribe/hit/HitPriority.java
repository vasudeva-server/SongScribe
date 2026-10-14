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

/**
 * How the regions in a {@link HitRegistry} resolve against one another: the highest
 * {@link #rank()} wins, and equal ranks fall to the smallest bounding-box area. Each
 * {@link HitTarget} kind names its own constant.
 *
 * <p>Changing the order of {@link #LYRIC}, {@link #ELEMENT}, {@link #SLIDE}, {@link #HAIRPIN},
 * {@link #ENDING} and {@link #STAFF_LINE} changes what the user selects when two overlap.
 * {@link #ARTICULATION}, {@link #ATTACHMENT} and {@link #TRILL} must outrank {@link #TIE},
 * whose hit shape over-covers what it spans.
 */
public enum HitPriority {

    LYRIC(100),

    ARTICULATION(90),

    ATTACHMENT(90),

    TRILL(90),

    ELEMENT(80),

    SLIDE(70),

    HAIRPIN(60),

    /** Below {@link #ELEMENT}, so notes inside it stay clickable. */
    ENDING(50),

    /** Below {@link #ELEMENT}, so the notes it spans stay clickable. */
    TUPLET(45),

    TIE(40),

    BEAM(30),

    /** Outranks only {@link #STAFF_LINE}, so any overlap resolves to the more precise target. */
    ATTRIBUTION(20),

    STAFF_LINE(10);

    private final int rank;

    HitPriority(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
