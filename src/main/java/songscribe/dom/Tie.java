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
 * <p>Both notes pass the <b>pitched-note check</b>, {@link #canAnchor}, and {@link #between} makes
 * no tie for a pair that fails it.
 *
 * <p>No beam holds both notes of a tie; the tie wins over the beam on every route.
 *
 * <p>A value, not a document element: nothing stores or registers a tie, and two ties made from
 * the same two notes are equal.
 */
public final class Tie {

    private final NoteElement anchor;
    private final NoteElement end;

    private Tie(NoteElement anchor, NoteElement end) {
        this.anchor = anchor;
        this.end = end;
    }

    /**
     * Does not check that a tie from {@code anchor} would end on {@code end}, or that the two
     * share a pitch ({@link #holds()}).
     *
     * @param end {@code null} when the tie's walk reaches no element
     * @return {@code null} when {@code anchor} cannot carry a tie, or {@code end} is
     *     {@code null} or cannot carry one ({@link #canAnchor})
     */
    public static @Nullable Tie between(NoteElement anchor, @Nullable StaffElement end) {
        return canAnchor(anchor) && end instanceof NoteElement endNote && canAnchor(endNote)
            ? new Tie(anchor, endNote)
            : null;
    }

    public static boolean canAnchor(StaffElement element) {
        return element instanceof NoteElement && element.getType().isPitchedNote();
    }

    /**
     * The anchor need not be tied yet.
     *
     * @param line a line of its song
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
     * @param line        a line of its song
     * @param anchorIndex the anchor need not be tied yet
     */
    public static @Nullable Beam beamHoldingBoth(Line line, int anchorIndex) {
        var beam = line.spanRegistry().findBeamAt(anchorIndex);

        return beam != null && bothNotesIn(line, anchorIndex, beam.range()) ? beam : null;
    }

    public NoteElement anchor() {
        return anchor;
    }

    public NoteElement end() {
        return end;
    }

    /**
     * Non-duration elements take no time, except a final double barline (nothing may sound across
     * it) and every key change (it can alter the pitch class, and the rule excludes all of them
     * so that whether a tie survives does not depend on where its notes sit).
     *
     * <p>{@code RangeQueries.canToggleTie} and the walk behind {@link NoteElement#tie()} both read
     * this, and must not disagree: otherwise a tie would end somewhere other than where it was
     * drawn. They share the type rule only, not a count.
     */
    public static boolean isLegalSeparator(ElementType type) {
        return type.isNonDuration()
            && type != ElementType.FINAL_DOUBLE_BARLINE
            && !type.isKeyChange();
    }

    /**
     * Holds only when the two notes sit at the same staff position and sound the same
     * alteration; the written duration plays no part. Ask it only after every restamp of the
     * edit has landed, so both notes have a sounding alteration to compare.
     */
    boolean holds() {
        return anchor.getStaffPosition().equals(end.getStaffPosition())
            && anchor.soundingAccidental() == end.soundingAccidental();
    }

    record Reached(Line line, int index) {

        StaffElement element() {
            return line.getElement(index);
        }
    }

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
     * @param from at or above 0
     * @return {@code null} when no non-separator element remains from {@code from} on
     */
    public static @Nullable Integer firstNonSeparatorIndexFrom(List<? extends StaffElement> elements, int from) {
        return StaffElementRun.firstIndexFrom(elements, from, type -> !isLegalSeparator(type));
    }

    /**
     * @param before a position in {@code 0..elements.size()}; the element there is not examined
     * @return {@code null} when every element before {@code before} is a legal separator
     */
    public static @Nullable Integer lastNonSeparatorIndexBefore(List<? extends StaffElement> elements, int before) {
        return StaffElementRun.lastIndexBefore(elements, before, type -> !isLegalSeparator(type));
    }

    public boolean isAbove() {
        // isAbove and arcSign share one convention: arcSign < 0 == arc bulges up == tie above.
        return arcSign() < 0;
    }

    /**
     * The arc's sign in Y-down screen space: {@code +1} bulges downward (tie below the notes),
     * {@code -1} upward (tie above).
     *
     * <p>The single source of truth for tie direction: the renderer, the skyline seeder and the
     * MusicXML export ({@link #isAbove()}) must agree on the side.
     *
     * <p>The single inversion from musical "above" to the arc sign lives in the
     * {@code .opposite()} call below and nowhere else; do not re-derive {@code ±1} per branch.
     */
    public int arcSign() {
        return tieArcDirection().opposite().sign();
    }

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
