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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LineLayoutChange;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;

/**
 * A note and the note a tie from it ends on, whether or not the note is tied.
 *
 * <p>Both notes pass the <b>pitched-note check</b>, {@link NoteElement#canCarryTie}, and no tie
 * exists for a pair that fails it.
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
     * @return {@code null} when {@code anchor} or {@code end} cannot carry a tie
     *     ({@link NoteElement#canCarryTie})
     */
    private static @Nullable Tie between(NoteElement anchor, StaffElement end) {
        return anchor.canCarryTie() && end instanceof NoteElement endNote && endNote.canCarryTie()
            ? new Tie(anchor, endNote)
            : null;
    }

    /**
     * The end of the result may be in the line after the line of {@code anchor}, and is in no
     * line after that.
     *
     * @return {@code null} when {@code anchor} is in no line, or no note that a tie can end on
     *     follows it
     * @invariant a non-null result's {@link #anchor()} is {@code anchor}, and
     *     {@code endingOn(result.end())} equals the result
     */
    public static @Nullable Tie startingOn(NoteElement anchor) {
        var line = anchor.getParentLine();

        if (line == null) {
            return null;
        }

        var reached = endAfter(line, anchor);

        return reached == null ? null : between(anchor, reached);
    }

    /**
     * The anchor of the result may be in the line before the line of {@code end}, and is in no
     * line before that.
     *
     * @return {@code null} when {@code end} is in no line, or no note before it starts a tie
     *     that ends on it
     * @invariant a non-null result's {@link #end()} is {@code end}, and
     *     {@code startingOn(result.anchor())} equals the result
     */
    public static @Nullable Tie endingOn(NoteElement end) {
        var line = end.getParentLine();

        if (line == null) {
            return null;
        }

        if (!(anchorBefore(line, end) instanceof NoteElement anchor)) {
            return null;
        }

        var tie = startingOn(anchor);

        //noinspection ObjectEquality
        return tie != null && tie.end() == end ? tie : null;
    }

    /**
     * @param range a range of the anchor's line that contains the anchor
     */
    boolean bothNotesIn(ElementRange range) {
        var line = anchor.requireParentLine();

        //noinspection ObjectEquality
        return end.getParentLine() == line && range.contains(line.getElementIndex(end));
    }

    public @Nullable Beam beamHoldingBoth() {
        var line = anchor.requireParentLine();
        var beam = line.spanRegistry().findBeamAt(line.getElementIndex(anchor));

        return beam != null && bothNotesIn(beam.range()) ? beam : null;
    }

    public NoteElement anchor() {
        return anchor;
    }

    public NoteElement end() {
        return end;
    }

    boolean holds() {
        return anchor.getStaffPosition().equals(end.getStaffPosition())
            && anchor.accidentalState().sounding() == end.accidentalState().sounding();
    }

    /**
     * Whether a tie stays.
     *
     * <p>An edit, a load and the user's request for a tie get this one judgment. A request
     * judged by any other rule can offer a tie that the edit's own judgment then removes.
     */
    public sealed interface Judgment {

        record Holds(Tie tie) implements Judgment { }

        /**
         * @param where the anchor's line, and the line of the element the tie's walk reaches when
         *              it reaches one
         */
        record Broken(Reason reason, ChangeLines where) implements Judgment {

            RecordedChange entry() {
                return new RecordedChange(ChangeHeading.TIES_REMOVED, Strings.get(reason.key), where);
            }
        }

        /** Why a tie does not stay. */
        enum Reason {

            /** No note a tie can end on follows the anchor. */
            NO_FOLLOWING_NOTE(Strings.REPORT_REASON_TIE_NO_FOLLOWING_NOTE),

            /** The two notes no longer sit at one staff position sounding one alteration. */
            NOT_HELD(Strings.REPORT_REASON_TIE_NOT_HELD),

            /** A file ties an element that cannot carry a tie. */
            DAMAGED_ANCHOR(Strings.REPORT_REASON_DAMAGED_WRONG_ELEMENT);

            private final String key;

            Reason(String key) {
                this.key = key;
            }
        }
    }

    /**
     * Must be asked only after every restamp of the edit has landed: until then, a note's
     * sounding alteration can be stale, and the judgment compares it.
     *
     * @param anchor in a line of its song; it need not be tied
     * @return {@link Judgment.Holds} with the tie from {@code anchor}, or {@link Judgment.Broken}
     *     with {@link Judgment.Reason#NO_FOLLOWING_NOTE} or {@link Judgment.Reason#NOT_HELD};
     *     never {@link Judgment.Reason#DAMAGED_ANCHOR}
     */
    public static Judgment judge(NoteElement anchor) {
        var line = anchor.requireParentLine();
        var reached = endAfter(line, anchor);

        return reached == null
            ? new Judgment.Broken(Judgment.Reason.NO_FOLLOWING_NOTE, ChangeLines.on(line))
            : judge(anchor, reached);
    }

    private static Judgment judge(NoteElement anchor, StaffElement end) {
        var tie = between(anchor, end);

        if (tie != null && tie.holds()) {
            return new Judgment.Holds(tie);
        }

        var reason = tie == null ? Judgment.Reason.NO_FOLLOWING_NOTE : Judgment.Reason.NOT_HELD;

        return new Judgment.Broken(reason, new ChangeLines(anchor.requireParentLine(), end.requireParentLine()));
    }

    /**
     * @param line the line that holds {@code anchor}
     */
    private static @Nullable StaffElement endAfter(Line line, NoteElement anchor) {
        var index = line.firstNonSeparatorIndexFrom(line.getElementIndex(anchor) + 1);

        if (index != null) {
            return line.getElement(index);
        }

        var following = line.nextLine();

        if (following == null) {
            return null;
        }

        var followingIndex = following.firstNonSeparatorIndexFrom(0);

        return followingIndex == null ? null : following.getElement(followingIndex);
    }

    /**
     * @param line the line that holds {@code end}
     */
    private static @Nullable StaffElement anchorBefore(Line line, NoteElement end) {
        var anchorIndex = line.lastNonSeparatorIndexBefore(line.getElementIndex(end));

        if (anchorIndex != null) {
            return line.getElement(anchorIndex);
        }

        var preceding = line.previousLine();

        if (preceding == null) {
            return null;
        }

        var precedingIndex = preceding.lastNonSeparatorIndexBefore(preceding.elementCount());

        return precedingIndex == null ? null : preceding.getElement(precedingIndex);
    }

    /**
     * Returns every line, other than the one {@code mutation} names, whose drawn ties
     * {@code mutation} can change. A line draws the half of a tie that crosses into it from the
     * line before it, so an edit in one line can change what the line beside it draws.
     *
     * <p>Asked only after {@code mutation} has landed: each position it names is read in its line
     * as that line is then.
     *
     * @return the lines, in no promised order
     */
    public static List<Line> reach(Mutation mutation) {
        var origin = reachOrigin(mutation);

        if (origin == null) {
            return List.of();
        }

        var line = origin.line();
        var firstNonSeparator = line.firstNonSeparatorIndexFrom(0);
        var lastNonSeparator = line.lastNonSeparatorIndexBefore(line.elementCount());
        var reach = new ArrayList<Line>();

        if (!line.isLast() && (lastNonSeparator == null || origin.last() >= lastNonSeparator)) {
            reach.add(line.nextLine());
        }

        if (!line.isFirst() && (firstNonSeparator == null || origin.first() <= firstNonSeparator)) {
            reach.add(line.previousLine());
        }

        return List.copyOf(reach);
    }

    private static @Nullable ReachOrigin reachOrigin(Mutation mutation) {
        return switch (mutation) {
            case ElementInsertion insertion -> elementOrigin(insertion.line(), insertion.index());
            case ElementReplacement replacement -> elementOrigin(replacement.line(), replacement.index());
            case ElementDeletion deletion -> elementOrigin(deletion.line(), deletion.range().begin());
            case ElementModification modification -> elementOrigin(modification.line(), modification.index());
            case TieAddition(var line, var anchor) -> tieAnchorOrigin(line, anchor);
            case TieRemoval(var line, var anchor) -> tieAnchorOrigin(line, anchor);
            case SpanAddition(var line, var span) -> beamOrigin(line, span);
            case SpanRemoval(var line, var span) -> beamOrigin(line, span);
            case LineInsertion _, LineDeletion _, LineKeyChange _, LineLayoutChange _, MetadataChange _,
                 FontChange _, LayoutChange _, LyricsChange _ -> null;
        };
    }

    private static ReachOrigin elementOrigin(Line line, int position) {
        var beam = line.spanRegistry().findBeamAt(position);

        if (beam == null) {
            return new ReachOrigin(line, position, position);
        }

        var range = beam.range();

        return new ReachOrigin(line, range.begin(), range.end());
    }

    private static @Nullable ReachOrigin tieAnchorOrigin(Line line, NoteElement tieAnchor) {
        if (!line.contains(tieAnchor)) {
            return null;
        }

        var index = line.getElementIndex(tieAnchor);

        return new ReachOrigin(line, index, index);
    }

    private static @Nullable ReachOrigin beamOrigin(Line line, Span<?> span) {
        if (!(span instanceof Beam beam)) {
            return null;
        }

        var beamAnchor = beam.getAnchorElement();
        var beamEnd = beam.getEndElement();

        if (!line.contains(beamAnchor) || !line.contains(beamEnd)) {
            return null;
        }

        return new ReachOrigin(line, line.getElementIndex(beamAnchor), line.getElementIndex(beamEnd));
    }

    private record ReachOrigin(Line line, int first, int last) {
    }

    public boolean isAbove() {
        return arcSign() < 0;
    }

    /**
     * The arc's sign in Y-down screen space: {@code +1} bulges downward (tie below the notes),
     * {@code -1} upward (tie above).
     *
     * <p>Every reader of a tie's side takes it from here or from {@link #isAbove()}. A side
     * computed a second time can put the drawn tie and the exported tie on different sides.
     */
    public int arcSign() {
        // The one inversion from musical "above" to the arc sign. Do not derive ±1 in each
        // branch of tieArcDirection instead.
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
