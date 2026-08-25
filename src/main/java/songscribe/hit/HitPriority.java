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
 * How the regions registered in a {@link HitRegistry} resolve against one another. Higher
 * {@link #rank()} wins: of all regions whose shape contains the click point, the one with the
 * greatest rank is the hit, and equal ranks are broken by smallest bounding-box area.
 *
 * <p>Each {@link HitTarget} kind names its own constant, so a region's rank follows from what
 * it is and is never chosen at a registration site.
 *
 * <p>Constants are declared highest rank first, and two parts of that order are deliberate:
 *
 * <ol>
 *   <li>{@link #LYRIC} &gt; {@link #ELEMENT} &gt; {@link #SLIDE} &gt; {@link #HAIRPIN}
 *       &gt; {@link #ENDING} &gt; {@link #STAFF_LINE} reproduces, verbatim, the order of
 *       the hit-tester cascade this registry replaced. Changing it changes which thing
 *       the user selects when two of them overlap.</li>
 *   <li>{@link #ARTICULATION}, {@link #ATTACHMENT} and {@link #TRILL} outrank {@link #TIE}, because a
 *       tie's hit shape is its bounding box and therefore deliberately over-covers the
 *       notes and markings it spans.</li>
 * </ol>
 *
 * <p>Rank rather than declaration order carries the comparison, because three kinds share a
 * rank on purpose and fall to the area tiebreak. Ranks are spaced so a kind can be slotted
 * between two existing ones without renumbering.
 */
public enum HitPriority {

    /** A lyric syllable box. */
    LYRIC(100),

    /** A staccato, accent, tenuto and the like. Above {@link #TIE} by design. */
    ARTICULATION(90),

    /** A fermata, dynamic, metronome or similar attached marking. Above {@link #TIE} by design. */
    ATTACHMENT(90),

    /**
     * A trill and its wavy-line extension. Ranks with the other note-attached markings: the
     * stacker gives it a reservation of its own, so it overlaps neither of them.
     */
    TRILL(90),

    /** A note's accidental — above the note so the sub-element stays reachable. */
    ACCIDENTAL(85),

    /** A note head. */
    ELEMENT(80),

    /** A glissando or fall. */
    SLIDE(70),

    /** A crescendo or diminuendo. */
    HAIRPIN(60),

    /** A volta / ending bracket. Below {@link #ELEMENT}, so notes inside it stay clickable. */
    ENDING(50),

    /**
     * A tuplet bracket and number. Above {@link #TIE} — the bracket's box is a real
     * reservation rather than an over-covering hull — and below {@link #ELEMENT}, so the
     * notes it spans stay clickable, for the same reason as {@link #ENDING}.
     */
    TUPLET(45),

    /** A tie or slur, whose hit shape is a bounding box that over-covers what it spans. */
    TIE(40),

    /** A beam group. */
    BEAM(30),

    /**
     * The attribution block above the first staff line. It outranks only {@link #STAFF_LINE},
     * the fallback: the stacker places the block clear of the system extents, so nothing
     * overlaps it today, and ranking it below every kind that names a specific piece of
     * notation means any overlap that ever arises resolves to the more precise target rather
     * than to the block.
     */
    ATTRIBUTION(20),

    /** The staff line itself — the fallback when nothing on the line was hit. */
    STAFF_LINE(10);

    private final int rank;

    HitPriority(int rank) {
        this.rank = rank;
    }

    /**
     * @return this kind's resolution rank, greater beating lesser
     */
    public int rank() {
        return rank;
    }
}
