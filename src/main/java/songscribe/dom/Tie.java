/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.dom;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * A tie from a note to the next note, as {@link NoteElement#tie()} and
 * {@link NoteElement#incomingTie()} derive it: the anchor stores that it is tied, and the end is
 * the first element after it that is not a {@link #isLegalSeparator legal separator}, which may
 * sit at the start of the next line.
 *
 * <p>A tie joins two notes whose {@link ElementType#kind()} is {@link ElementType.Kind#NOTE}.
 * No other kind — a grace note among them — can be its anchor or its end, and the constructor
 * refuses one, so no tie that breaks the rule can exist.
 *
 * <p>A value, not a document element. Nothing stores a tie and nothing registers one; each call
 * to {@code tie()} makes a new one, so a caller never holds a tie that outlives the notes it was
 * made from. Two ties made from the same two notes are equal, because {@link NoteElement}
 * compares by identity, so a caller that asks whether two ties are the same tie asks
 * {@link #equals}.
 *
 * <p>Ties are rendered as a curved arc, above or below the notes depending on the stems of the
 * two notes.
 *
 * @param anchor the note that stores the tie
 * @param end    the note the tie ends on
 */
public record Tie(NoteElement anchor, NoteElement end) {

    /**
     * @throws IllegalArgumentException when the kind of {@code anchor} or of {@code end} is not
     *     {@link ElementType.Kind#NOTE}
     */
    public Tie {
        if (!anchor.getType().isPitchedNote() || !end.getType().isPitchedNote()) {
            throw new IllegalArgumentException("A tie joins two notes of kind NOTE: " + anchor + ", " + end);
        }
    }

    /**
     * Returns whether {@code type} may sit between two tied notes.
     *
     * <p>Non-duration elements take no time, so the notes on either side stay adjacent in
     * the music even though an element separates them on the staff. Two are excluded:
     *
     * <ul>
     *   <li>a final double barline ends the piece, so nothing may sound across it
     *       (refs #527);</li>
     *   <li>a key change can alter the pitch class the two notes sit at, so the note
     *       after it may no longer sound what the note before it did — and a tie joins two
     *       notes of the same pitch. The rule excludes every key change rather than only
     *       the ones that happen to touch that pitch class, so that whether a tie survives
     *       does not depend on where its notes sit.</li>
     * </ul>
     *
     * <p>One definition, two readers: {@code RangeQueries.canToggleTie} asks it before letting
     * the user create a tie over a separator, and the walk that derives a tie's end
     * ({@link NoteElement#tie()}) steps over exactly these types. Were they to disagree, the user
     * could tie across an element the walk stops at, and the tie would end somewhere other than
     * where it was drawn.
     *
     * <p>The two readers share the <em>type</em> rule only, not a count. Drawing a tie is
     * limited to two notes with at most one element between them, because that is all a
     * selection can hold; the walk has no such limit and lets separators accumulate, since each
     * one still takes no time and the notes stay adjacent in the music.
     */
    public static boolean isLegalSeparator(ElementType type) {
        return type.isNonDuration()
            && type != ElementType.FINAL_DOUBLE_BARLINE
            && !type.isKeyChange();
    }

    /**
     * The tie rule's test: whether a tie on {@code anchor} holds when its walk reaches
     * {@code end}. It holds only when both are {@link ElementType.Kind#NOTE} notes at the same
     * staff position that sound the same alteration. The written duration plays no part.
     *
     * <p>The rule runs when the outermost modification bracket closes, after every change and
     * every chain-walk restamp in it has landed, so both notes are stamped and each has a
     * sounding alteration to compare. {@link Line#applyTieRule} is its only caller.
     *
     * @param anchor a tied note
     * @param end    the element the walk from {@code anchor} reaches, or {@code null} when it
     *     reaches none
     * @return {@code true} when the tie holds
     */
    static boolean holds(NoteElement anchor, @Nullable StaffElement end) {
        return anchor.getType().isPitchedNote()
            && end instanceof NoteElement endNote
            && endNote.getType().isPitchedNote()
            && anchor.getStaffPosition().equals(endNote.getStaffPosition())
            && anchor.soundingAccidental() == endNote.soundingAccidental();
    }

    /**
     * The element a tie from {@code elements[anchorIndex]} ends on: the first element after it
     * that is not a legal separator, and when {@code elements} runs out first, the first such
     * element of {@code following}. The walk crosses into {@code following} and no further.
     *
     * @param elements    a line's elements, or a projection of them
     * @param anchorIndex the anchor's position in {@code elements}
     * @param following   the line after the one {@code elements} describes, or {@code null} when
     *                    there is none
     * @return the end the walk reaches, or {@code null} when it reaches none
     */
    static @Nullable StaffElement endAfter(
        List<? extends StaffElement> elements, int anchorIndex, @Nullable Line following
    ) {
        var end = firstNonSeparatorFrom(elements, anchorIndex + 1);

        if (end == null && following != null) {
            end = firstNonSeparatorFrom(following.getElements(), 0);
        }

        return end;
    }

    /**
     * The only note whose tie can end on {@code elements[endIndex]}: the last element before it
     * that is not a legal separator, and when there is none, the last such element of
     * {@code preceding}. The mirror of {@link #endAfter}, crossing one line break and no more.
     *
     * @param elements  a line's elements
     * @param endIndex  the candidate end's position in {@code elements}
     * @param preceding the line before the one {@code elements} describes, or {@code null} when
     *                  there is none
     * @return the candidate anchor, or {@code null} when the walk reaches none
     */
    static @Nullable StaffElement anchorBefore(
        List<? extends StaffElement> elements, int endIndex, @Nullable Line preceding
    ) {
        var anchorIndex = lastNonSeparatorIndexBefore(elements, endIndex);

        if (anchorIndex != null) {
            return elements.get(anchorIndex);
        }

        if (preceding == null) {
            return null;
        }

        var precedingElements = preceding.getElements();
        var precedingIndex = lastNonSeparatorIndexBefore(precedingElements, precedingElements.size());

        return precedingIndex == null ? null : precedingElements.get(precedingIndex);
    }

    /**
     * The first element at or after {@code from} that is not a legal separator.
     *
     * @return the element, or {@code null} when every element from {@code from} on is a legal
     *     separator, or {@code from} is past the last element
     */
    static @Nullable StaffElement firstNonSeparatorFrom(List<? extends StaffElement> elements, int from) {
        var index = firstNonSeparatorIndexFrom(elements, from);

        return index == null ? null : elements.get(index);
    }

    /**
     * The position of the first element at or after {@code from} that is not a legal separator.
     *
     * @return the position, or {@code null} when every element from {@code from} on is a legal
     *     separator, or {@code from} is past the last element
     */
    public static @Nullable Integer firstNonSeparatorIndexFrom(List<? extends StaffElement> elements, int from) {
        for (var i = from; i < elements.size(); i++) {
            if (!isLegalSeparator(elements.get(i).getType())) {
                return i;
            }
        }

        return null;
    }

    /**
     * The position of the last element before {@code before} that is not a legal separator.
     *
     * @param before a position in {@code 0..elements.size()}; the element there is not examined
     * @return the position, or {@code null} when every element before {@code before} is a legal
     *     separator
     */
    public static @Nullable Integer lastNonSeparatorIndexBefore(List<? extends StaffElement> elements, int before) {
        for (var i = before - 1; i >= 0; i--) {
            if (!isLegalSeparator(elements.get(i).getType())) {
                return i;
            }
        }

        return null;
    }

    /**
     * Returns whether this tie arcs above its notes rather than below them.
     */
    public boolean isAbove() {
        // isAbove and arcSign share one convention: arcSign < 0 == arc bulges up == tie above.
        return arcSign() < 0;
    }

    /**
     * The tie arc's render/seed sign in Y-down screen space: {@code +1} = arc bulges
     * <em>downward</em> (tie below the notes), {@code -1} = arc bulges upward (tie above).
     *
     * <p>Single source of truth for tie direction, consumed by three live sites:
     * the renderer ({@code LayoutEngine.calculateTies}), the skyline seeder's reserve
     * side ({@code NoteAttachedStacker.seedTieBounds}), and the MusicXML export
     * ({@link #isAbove()} → {@code <tied orientation>}). All three must agree, so a
     * conflicting-stem tie renders, seeds, and exports on the same side.
     *
     * <p>The <em>visual</em> arc direction ({@code tieDir}, a {@link Direction}
     * where UP = arc bulges up = tie above) follows LilyPond's {@code get_default_dir}
     * fallthrough tree, keying off <em>both</em> noteheads' stems. Each rule below is tried in
     * order; a rule that does not apply falls through to the next.
     *
     * <ol>
     *   <li>Both noteheads have stems, and both point UP: DOWN.
     *   <li>Both have stems but disagree: fall through to the final default.
     *   <li>Only the left notehead has a stem: the opposite of that stem's direction.
     *   <li>Only the right notehead has a stem: the opposite of that stem's direction.
     *   <li>Neither has a stem: decide by staff position relative to the middle line — above the
     *       middle gives UP, below gives DOWN, and exactly on the middle line falls through.
     *   <li>Default (NEUTRAL): UP.
     * </ol>
     *
     * <p>The single inversion from musical "above" to Y-down "arc sign" lives in the
     * {@code .opposite()} call below — and nowhere else. {@code tieDir = UP} (tie above)
     * → {@code opposite()} = DOWN → {@code sign()} = {@code -1} (above); {@code tieDir = DOWN}
     * (tie below) → {@code +1}. Do not re-derive {@code ±1} per branch.
     *
     * @return {@code +1} when the arc bulges downward (tie below), {@code -1} when it
     * bulges upward (tie above)
     */
    public int arcSign() {
        return tieArcDirection().opposite().sign();
    }

    /**
     * Computes the tie's <em>visual</em> arc direction (UP = arc bulges up = tie above)
     * via the fallthrough tree documented on {@link #arcSign()}. Reads both notes'
     * {@link ElementType#isNoteWithStem()}, {@link NoteElement#direction()}, and
     * {@link NoteElement#getStaffPosition()}.
     */
    private Direction tieArcDirection() {
        var left = anchor();
        var right = end();
        var leftHasStem = left.getType().isNoteWithStem();
        var rightHasStem = right.getType().isNoteWithStem();

        if (leftHasStem && rightHasStem) {
            // Both stems up → tie below; any other pairing (both down, or conflicting) falls
            // through to the neutral default.
            if (left.direction().isUp() && right.direction().isUp()) {
                return Direction.DOWN;
            }

            return Direction.UP;
        }

        if (leftHasStem) {
            return left.direction().opposite();
        }

        if (rightHasStem) {
            return right.direction().opposite();
        }

        // Neither note has a stem → key off staff position. getStaffPosition() is Y-down
        // (0 = middle line, positive = below), so above the middle line is negative.
        // Same-pitch tie ⇒ both notes share a staff position; read the left.
        var staffPositionSp = left.getStaffPosition().value();

        if (staffPositionSp < 0) {
            return Direction.UP;
        }

        if (staffPositionSp > 0) {
            return Direction.DOWN;
        }

        // Exactly on the middle line → neutral default.
        return Direction.UP;
    }
}
