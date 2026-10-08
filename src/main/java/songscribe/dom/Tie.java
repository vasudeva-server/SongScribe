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
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElementRun.ElementRange;

/**
 * A tie from a note to the next note, as {@link NoteElement#tie()} and
 * {@link NoteElement#incomingTie()} derive it: the anchor stores that it is tied, and the end is
 * the first element after it that is not a {@link #isLegalSeparator legal separator}, which may
 * sit at the start of the next line.
 *
 * <p>A tie joins two notes whose {@link ElementType#kind()} is {@link ElementType.Kind#NOTE}:
 * that is the <b>pitched-note check</b>, {@link #canAnchor}. No other kind — a grace note among
 * them — can be its anchor or its end. {@link #between} is the only way to make a tie, and it
 * makes none for a pair that fails the pitched-note check, so no tie that fails it can exist.
 *
 * <p>No beam holds both notes of a tie: tied notes are never beamed together. A note that ties
 * out of a beam to a note outside it is legal. {@link #bothNotesIn} is the one test of this
 * rule. {@link Beam#spanning} refuses a beam that would hold both notes of a tie, and
 * {@link TieRule} removes a beam that an edit leaves holding both, so the tie wins over the beam
 * on every route.
 *
 * <p>A value, not a document element. Nothing stores a tie and nothing registers one; each call
 * to {@code tie()} makes a new one, so a caller never holds a tie that outlives the notes it was
 * made from. Two ties made from the same two notes are equal, because {@link NoteElement}
 * compares by identity, so a caller that asks whether two ties are the same tie asks
 * {@link #equals}.
 *
 * <p>Ties are rendered as a curved arc, above or below the notes: by the stems of the two notes,
 * or by their staff position when neither has a stem ({@link #arcSign()}).
 */
public final class Tie {

    private final NoteElement anchor;
    private final NoteElement end;

    private Tie(NoteElement anchor, NoteElement end) {
        this.anchor = anchor;
        this.end = end;
    }

    /**
     * Returns the tie from {@code anchor} to {@code end}, when the two pass the pitched-note
     * check. Says nothing about whether a tie from {@code anchor} would end on {@code end}, which
     * is the caller's to know, or whether the two share a pitch, which the tie rule judges
     * ({@link #holds()}).
     *
     * @param anchor the note that stores the tie
     * @param end    the element a tie from {@code anchor} reaches, or {@code null} when it reaches
     *     none
     * @return the tie, or {@code null} when {@code anchor} cannot carry a tie, or {@code end} is
     *     {@code null} or cannot carry one ({@link #canAnchor})
     */
    public static @Nullable Tie between(NoteElement anchor, @Nullable StaffElement end) {
        return canAnchor(anchor) && end instanceof NoteElement endNote && canAnchor(endNote)
            ? new Tie(anchor, endNote)
            : null;
    }

    /**
     * The pitched-note check: whether {@code element} can carry a tie, as its anchor or as its
     * end. The same check governs both, so a reader asks it of a note before it ties the note.
     *
     * @param element the element to ask about
     * @return {@code true} when {@code element} is a note whose {@link ElementType#kind()} is
     *     {@link ElementType.Kind#NOTE}
     */
    public static boolean canAnchor(StaffElement element) {
        return element instanceof NoteElement && element.getType().isPitchedNote();
    }

    /**
     * Whether {@code range} of {@code line} holds both notes of a tie from the element at
     * {@code anchorIndex}: the anchor, and the element the tie's walk reaches ({@link #endAfter}).
     * The anchor need not be tied yet, so a caller can ask about a tie it is about to make.
     *
     * @param line        a line of its song
     * @param anchorIndex the anchor's position in {@code line}
     * @param range       a range of {@code line}'s indices
     * @return {@code true} when {@code range} holds {@code anchorIndex} and the walk reaches an
     *     element of {@code line} whose index {@code range} holds; {@code false} when the walk
     *     reaches no element or crosses into another line
     */
    static boolean bothNotesIn(Line line, int anchorIndex, ElementRange range) {
        if (!range.contains(anchorIndex)) {
            return false;
        }

        var reached = endAfter(line, anchorIndex);

        //noinspection ObjectEquality
        return reached != null && reached.line() == line && range.contains(reached.index());
    }

    /**
     * The beam that holds both notes of a tie from the element at {@code anchorIndex} of
     * {@code line}, as {@link #bothNotesIn} judges it.
     *
     * @param line        a line of its song
     * @param anchorIndex the anchor's position in {@code line}; the anchor need not be tied yet
     * @return that beam, or {@code null} when no beam holds both notes
     */
    public static @Nullable Beam beamHoldingBoth(Line line, int anchorIndex) {
        var beam = line.spanRegistry().findBeamAt(anchorIndex);

        return beam != null && bothNotesIn(line, anchorIndex, beam.range()) ? beam : null;
    }

    /**
     * @return the note that stores the tie
     */
    public NoteElement anchor() {
        return anchor;
    }

    /**
     * @return the note the tie ends on
     */
    public NoteElement end() {
        return end;
    }

    /**
     * Returns whether {@code type} may sit between two tied notes.
     *
     * <p>Non-duration elements take no time, so the notes on either side stay adjacent in
     * the music even though an element separates them on the staff. Two are excluded:
     *
     * <ul>
     *   <li>a final double barline ends the piece, so nothing may sound across it
     *;</li>
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
     *
     * @param type the type of the element between the two notes
     * @return {@code true} when an element of {@code type} may sit between two tied notes
     */
    public static boolean isLegalSeparator(ElementType type) {
        return type.isNonDuration()
            && type != ElementType.FINAL_DOUBLE_BARLINE
            && !type.isKeyChange();
    }

    /**
     * The tie rule's test: whether this tie holds. It holds only when its two notes sit at the
     * same staff position and sound the same alteration. The written duration plays no part.
     *
     * <p>The rule runs when the outermost modification bracket closes, after every change and
     * every chain-walk restamp in it has landed, so both notes are stamped and each has a
     * sounding alteration to compare. {@link TieRule} is its only caller.
     *
     * @return {@code true} when the tie holds
     */
    boolean holds() {
        return anchor.getStaffPosition().equals(end.getStaffPosition())
            && anchor.soundingAccidental() == end.soundingAccidental();
    }

    /**
     * Where the walk from a tie's anchor stopped: the element at {@code index} in {@code line}.
     *
     * @param line  the line the walk stopped in: the anchor's own line, or the line after it
     * @param index the position of the element the walk stopped at, in {@code line}
     */
    record Reached(Line line, int index) {

        /**
         * @return the element the walk stopped at
         */
        StaffElement element() {
            return line.getElement(index);
        }
    }

    /**
     * Where a tie from the element at {@code anchorIndex} in {@code line} ends: the first element
     * after it that is not a legal separator, and when {@code line} runs out first, the first
     * such element of the line after it. The walk crosses into that line and no further.
     *
     * @param line        a line of its song
     * @param anchorIndex the anchor's position in {@code line}
     * @return where the walk stopped, or {@code null} when it reaches no element: {@code line}
     *     runs out and it is its song's last line, or the line after it runs out too
     */
    static @Nullable Reached endAfter(Line line, int anchorIndex) {
        var index = firstNonSeparatorIndexFrom(line.getElements(), anchorIndex + 1);

        if (index != null) {
            return new Reached(line, index);
        }

        var following = line.nextLine();

        if (following == null) {
            return null;
        }

        var followingIndex = firstNonSeparatorIndexFrom(following.getElements(), 0);

        return followingIndex == null ? null : new Reached(following, followingIndex);
    }

    /**
     * The only note whose tie can end on the element at {@code endIndex} in {@code line}: the
     * last element before it that is not a legal separator, and when there is none, the last
     * such element of the line before it. The mirror of {@link #endAfter}, crossing one line
     * break and no more.
     *
     * @param line     a line of its song
     * @param endIndex the candidate end's position in {@code line}
     * @return the candidate anchor, or {@code null} when the walk reaches none: {@code line}
     *     runs out and it is its song's first line, or the line before it runs out too
     */
    static @Nullable StaffElement anchorBefore(Line line, int endIndex) {
        var elements = line.getElements();
        var anchorIndex = lastNonSeparatorIndexBefore(elements, endIndex);

        if (anchorIndex != null) {
            return elements.get(anchorIndex);
        }

        var preceding = line.previousLine();

        if (preceding == null) {
            return null;
        }

        var precedingElements = preceding.getElements();
        var precedingIndex = lastNonSeparatorIndexBefore(precedingElements, precedingElements.size());

        return precedingIndex == null ? null : precedingElements.get(precedingIndex);
    }

    /**
     * The position of the first element at or after {@code from} that is not a legal separator.
     *
     * @param elements the elements to scan, in order
     * @param from     the first position examined, at or above 0
     * @return the position, or {@code null} when every element from {@code from} on is a legal
     *     separator, or {@code from} is past the last element
     */
    public static @Nullable Integer firstNonSeparatorIndexFrom(List<? extends StaffElement> elements, int from) {
        return StaffElementRun.firstIndexFrom(elements, from, type -> !isLegalSeparator(type));
    }

    /**
     * The position of the last element before {@code before} that is not a legal separator.
     *
     * @param elements the elements to scan, in order
     * @param before   a position in {@code 0..elements.size()}; the element there is not examined
     * @return the position, or {@code null} when every element before {@code before} is a legal
     *     separator
     */
    public static @Nullable Integer lastNonSeparatorIndexBefore(List<? extends StaffElement> elements, int before) {
        return StaffElementRun.lastIndexBefore(elements, before, type -> !isLegalSeparator(type));
    }

    /**
     * Returns whether this tie arcs above its notes rather than below them, by the rule
     * {@link #arcSign()} states.
     *
     * @return {@code true} when the tie is above its notes, {@code false} when it is below
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
     * <p>The side keys off both notes' stems. Every note except a whole note has a stem, grace
     * notes included. The stem's direction is {@link NoteElement#direction()}: a grace note's
     * stem is always up; a forced stem gives the notator's choice; an auto stem in a beam gives
     * the beam's direction; any other auto stem is up below the middle line, and down on or
     * above it. The tie then sits:
     *
     * <ul>
     *   <li>when both notes have stems: below when both stems are up, and above when both are
     *       down or one is up and one is down;</li>
     *   <li>when only one note has a stem: opposite to that stem;</li>
     *   <li>when neither has a stem: by the left note's staff position only — above when it is
     *       above the middle line, below when it is below it, and above when it is on it.</li>
     * </ul>
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
     * by the rule documented on {@link #arcSign()}. Reads both notes'
     * {@link ElementType#isNoteWithStem()}, {@link NoteElement#direction()}, and
     * {@link NoteElement#getStaffPosition()}.
     */
    private Direction tieArcDirection() {
        var left = anchor();
        var right = end();
        var leftHasStem = left.getType().isNoteWithStem();
        var rightHasStem = right.getType().isNoteWithStem();

        if (leftHasStem && rightHasStem) {
            // Both stems up → tie below; both down, or one each way → tie above.
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

        // Exactly on the middle line → tie above.
        return Direction.UP;
    }

    /**
     * Two ties are equal when they join the same two notes.
     *
     * @return {@code true} when {@code other} is a tie with this tie's anchor and end
     */
    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Tie tie && anchor.equals(tie.anchor) && end.equals(tie.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(anchor, end);
    }

    @Override
    public String toString() {
        return "Tie{anchor=" + anchor + ", end=" + end + '}';
    }
}
