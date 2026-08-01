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
 * Priorities for the regions registered in a {@link HitRegistry}. Higher wins: of all
 * regions whose shape contains the click point, the one with the greatest priority is
 * the hit, and equal priorities are broken by smallest bounding-box area.
 *
 * <p>Two parts of this ordering are deliberate and must not be reordered:
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
 * <p>Values are spaced so a kind can be slotted between two existing ones without
 * renumbering.
 */
public final class HitPriority {

    /** A lyric syllable box. */
    public static final int LYRIC = 100;

    /** A staccato, accent, tenuto and the like. Above {@link #TIE} by design. */
    public static final int ARTICULATION = 90;

    /** A fermata, dynamic, metronome or similar attached marking. Above {@link #TIE} by design. */
    public static final int ATTACHMENT = 90;

    /**
     * A trill and its wavy-line extension. Ranks with the other note-attached markings: the
     * stacker gives it a reservation of its own, so it overlaps neither of them.
     */
    public static final int TRILL = 90;

    /** A note's accidental — above the note so the sub-element stays reachable. */
    public static final int ACCIDENTAL = 85;

    /** A note head. */
    public static final int ELEMENT = 80;

    /** A glissando or fall. */
    public static final int SLIDE = 70;

    /** A crescendo or diminuendo. */
    public static final int HAIRPIN = 60;

    /** A volta / ending bracket. Below {@link #ELEMENT}, so notes inside it stay clickable. */
    public static final int ENDING = 50;

    /**
     * A tuplet bracket and number. Above {@link #TIE} — the bracket's box is a real
     * reservation rather than an over-covering hull — and below {@link #ELEMENT}, so the
     * notes it spans stay clickable, for the same reason as {@link #ENDING}.
     */
    public static final int TUPLET = 45;

    /** A tie or slur, whose hit shape is a bounding box that over-covers what it spans. */
    public static final int TIE = 40;

    /** A beam group. */
    public static final int BEAM = 30;

    /** The staff line itself — the fallback when nothing on the line was hit. */
    public static final int STAFF_LINE = 10;

    private HitPriority() {
    }
}
