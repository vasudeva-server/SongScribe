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
 * A note and the note a tie from it ends on, whether or not the note is tied: the end is the
 * first element after the anchor that is not a {@link #isLegalSeparator legal separator}, which
 * may sit at the start of the next line. {@link NoteElement#tie()} and
 * {@link NoteElement#incomingTie()} give the ties a note stores; {@link #startingOn} and
 * {@link #endingOn} give the tie a note could start or end.
 *
 * <p>Both notes pass the <b>pitched-note check</b>, {@link #canAnchor}, and no tie exists for a
 * pair that fails it.
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
     * @return {@code null} when {@code anchor} or {@code end} cannot carry a tie
     *     ({@link #canAnchor})
     */
    private static @Nullable Tie between(NoteElement anchor, StaffElement end) {
        return canAnchor(anchor) && end instanceof NoteElement endNote && canAnchor(endNote)
            ? new Tie(anchor, endNote)
            : null;
    }

    /**
     * Returns the tie from {@code anchor}, whether or not {@code anchor} is tied.
     *
     * @return {@code null} when {@code anchor} is in no line, or the walk from it reaches no note
     *     a tie can end on
     */
    public static @Nullable Tie startingOn(NoteElement anchor) {
        var line = anchor.getParentLine();

        if (line == null) {
            return null;
        }

        var reached = endAfter(line, line.getElementIndex(anchor));

        return reached == null ? null : between(anchor, reached);
    }

    /**
     * Returns the tie that ends on {@code end}, whether or not its anchor is tied.
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

        if (!(anchorBefore(line, line.getElementIndex(end)) instanceof NoteElement anchor)) {
            return null;
        }

        var tie = startingOn(anchor);

        //noinspection ObjectEquality
        return tie != null && tie.end() == end ? tie : null;
    }

    public static boolean canAnchor(StaffElement element) {
        return element instanceof NoteElement && element.getType().isPitchedNote();
    }

    /**
     * The anchor need not be tied yet.
     *
     * @param line  a line of its song
     * @param range contains {@code anchorIndex}
     */
    static boolean bothNotesIn(Line line, int anchorIndex, ElementRange range) {
        var reached = endAfter(line, anchorIndex);

        //noinspection ObjectEquality
        return reached != null && reached.getParentLine() == line && range.contains(line.getElementIndex(reached));
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
            && anchor.accidentalState().sounding() == end.accidentalState().sounding();
    }

    /**
     * Whether a tie stays: the one judgment of a tie that an edit leaves, of a tie a file holds,
     * and of a tie the user asks for.
     */
    public sealed interface Judgment {

        record Holds() implements Judgment { }

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

            /**
             * A file ties an element that cannot carry a tie. Only {@link TieLoadPass} gives this
             * reason; {@link Tie#judge} never does.
             */
            DAMAGED_ANCHOR(Strings.REPORT_REASON_DAMAGED_WRONG_ELEMENT);

            private final String key;

            Reason(String key) {
                this.key = key;
            }
        }
    }

    /**
     * Must be asked only after every restamp has landed, so that both notes have a sounding
     * alteration to compare ({@link #holds()}).
     *
     * @param anchor in a line of its song; it need not be tied
     * @return {@link Judgment.Holds} when the element the tie's walk reaches from {@code anchor}
     *     is a note a tie can end on and the tie {@link #holds() holds}; otherwise
     *     {@link Judgment.Broken} with {@link Judgment.Reason#NO_FOLLOWING_NOTE} when no tie can
     *     end on what the walk reaches, or {@link Judgment.Reason#NOT_HELD} when the tie does not
     *     hold
     */
    public static Judgment judge(NoteElement anchor) {
        var line = anchor.requireParentLine();
        var reached = endAfter(line, line.getElementIndex(anchor));

        return reached == null
            ? new Judgment.Broken(Judgment.Reason.NO_FOLLOWING_NOTE, ChangeLines.on(line))
            : judge(anchor, reached);
    }

    private static Judgment judge(NoteElement anchor, StaffElement end) {
        var tie = between(anchor, end);

        if (tie != null && tie.holds()) {
            return new Judgment.Holds();
        }

        var reason = tie == null ? Judgment.Reason.NO_FOLLOWING_NOTE : Judgment.Reason.NOT_HELD;

        return new Judgment.Broken(reason, new ChangeLines(anchor.requireParentLine(), end.requireParentLine()));
    }

    static @Nullable StaffElement endAfter(Line line, int anchorIndex) {
        var elements = line.getElements();
        var index = firstNonSeparatorIndexFrom(elements, anchorIndex + 1);

        if (index != null) {
            return elements.get(index);
        }

        var following = line.nextLine();

        if (following == null) {
            return null;
        }

        var followingElements = following.getElements();
        var followingIndex = firstNonSeparatorIndexFrom(followingElements, 0);

        return followingIndex == null ? null : followingElements.get(followingIndex);
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

    /**
     * Returns the lines, other than the one {@code mutation} names, whose drawn ties it can
     * change.
     *
     * <p>A line draws the half of a tie that crosses into it from the line before it, so an edit
     * near one edge of a line can change what the line beside it draws while the mutation's own
     * record names only its own line. Deleting a tied last note, inserting a same-pitch note
     * after an anchor, and flipping the stem of a note a crossing tie ends on are each felt this
     * way.
     *
     * <p>A mutation names an origin in its line L: a run of positions from {@code first} to
     * {@code last}. An insertion's, a replacement's or a modification's index, or a deletion's
     * first index, is the origin when no beam of L contains it; when one does, the origin is
     * that beam's whole range, because a beamed note's stem follows its beam, so a change to
     * one member can turn the stem of the member at L's edge. A {@link TieAddition}'s or
     * {@link TieRemoval}'s anchor index is the origin of a tie record, and a {@link Beam}'s range
     * is the origin of a {@link SpanAddition} or {@link SpanRemoval} of that beam. It reaches:
     * <ul>
     *   <li>L's next line, when there is one and {@code last} is at or after L's last element
     *       that is not a legal separator ({@link #isLegalSeparator}), or L has no such
     *       element;</li>
     *   <li>L's previous line, when there is one and {@code first} is at or before L's first
     *       such element, or L has none.</li>
     * </ul>
     *
     * @param mutation the mutation to ask about
     * @return the lines, in no promised order, or empty when {@code mutation} names no origin —
     *     any other kind of mutation, a span record of a span that is not a beam, a beam record
     *     whose endpoints are no longer both in the line it names, or a tie record whose anchor
     *     is no longer in that line — or names one too far from either edge to reach a
     *     neighboring line
     */
    public static List<Line> reach(Mutation mutation) {
        var origin = reachOrigin(mutation);

        if (origin == null) {
            return List.of();
        }

        var line = origin.line();
        var elements = line.getElements();
        var firstNonSeparator = firstNonSeparatorIndexFrom(elements, 0);
        var lastNonSeparator = lastNonSeparatorIndexBefore(elements, elements.size());
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
