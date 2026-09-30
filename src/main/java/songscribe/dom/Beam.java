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
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.engraving.BeamMetrics;
import songscribe.engraving.StaffPosition;
import songscribe.engraving.StemMetrics;

/**
 * A beam group connecting a contiguous run of notes.
 *
 * <p>Carries only the anchor/end pair inherited from {@link Span}; everything else it answers —
 * the group's stem direction, how many beam levels it stacks, which of its members a level
 * reaches, which way a partial-beam stub points, how far French beaming pulls a stem back — is
 * derived from its members' types and staff positions. None of it needs geometry, which is
 * what lets a note ask its beam for a direction before any column exists. The computed beam
 * geometry lives in {@code LayoutResult.BeamLayout}.
 *
 * <p>A beam's endpoints are notes, which the type argument states: the constructor, the
 * endpoint getters and {@link Span#copy} are typed to {@link NoteElement}, so every reader of a
 * beam's endpoint gets a note without re-proving it.
 *
 * <p>{@link #members} hands out each joined note as a {@link Member}, which answers the
 * per-note questions — which levels it carries, which way its stub points, how far French
 * beaming shortens its stem — from its place among the members. A note that is not a member
 * has no {@link Member}, so none of those questions can be put about it.
 *
 * <p>Invariant: every element inside a beam's span is a note, both endpoints are beamable, and
 * every non-grace element strictly inside it is beamable. {@link #outcomeFor} keeps it through
 * an element replacement by removing the beam whenever the replacement would break it, which is
 * why {@link #members} and the query for the grace notes it passes over may narrow without testing. A broken beam is
 * always removed, never shortened to the members that survive.
 *
 * <p>A grace note inside the span is not a member: it sits between two beamed notes without
 * joining their beam, keeping its own short upward stem and its own flag, and neither its pitch
 * nor its stem may decide where the beam goes. {@link #members} leaves it out, so every walk
 * over a beam's members is a walk over the notes the beam joins; the notes it passes over
 * are a separate query. A grace note is always strictly inside the span,
 * because both endpoints are beamable, so its nearest members on either side are both members
 * of this beam.
 */
public final class Beam extends Span<NoteElement> {

    /**
     * Vertical gap, in staff spaces, a grace note's stem tip must keep below an upward beam
     * before the beam is pushed to the other side of the staff instead
     * ({@link #graceStemTouchesBeamAbove}).
     * <p>
     * It also absorbs the error in {@link BeamMetrics#upwardStackBottomYSs}, which places the
     * beam a natural stem above the group's highest head while the scorer routinely settles a
     * few tenths of a space lower. Erring low here only means a beam flips that would have been
     * a near miss; erring high means the grace note is buried, which is the bug this guards.
     */
    private static final double GRACE_STEM_BEAM_CLEARANCE_SS = 0.5;

    public Beam(NoteElement anchorElement, NoteElement endElement) {
        super(NoteElement.class, anchorElement, endElement);
    }

    @Override
    protected Beam createCopy(NoteElement newAnchor, NoteElement newEnd) {
        return new Beam(newAnchor, newEnd);
    }

    @Override
    protected void copyOwnStateFrom(Span<NoteElement> source) {
    }

    /**
     * Returns the notes this beam joins, each with its place among them: every non-grace note
     * from the anchor to the end, in line order.
     *
     * <p>The list is built afresh on each call and reflects the line as it is then; a
     * {@link Member} answers from the members as they were when it was built.
     *
     * @return this beam's members, a new unmodifiable list, never empty
     * @throws IllegalStateException if this beam's anchor or its end is in no line
     * @invariant the first member's note is {@link #getAnchorElement()} and the last's is
     *     {@link #getEndElement()}
     * @invariant no member's note is a grace note
     */
    public List<Member> members() {
        var notes = notesInSpan().filter(note -> !note.getType().isGraceNote()).toList();

        return IntStream.range(0, notes.size())
            .mapToObj(position -> new Member(notes, position))
            .toList();
    }

    /**
     * Returns the grace notes this beam passes over without joining, in line order.
     *
     * @return the grace notes strictly inside this beam's span, a new unmodifiable list; empty
     *     when there are none
     * @throws IllegalStateException if this beam's anchor or its end is in no line
     */
    private List<NoteElement> graceNotes() {
        return notesInSpan().filter(note -> note.getType().isGraceNote()).toList();
    }

    /**
     * Every note from the anchor to the end, in line order. The class invariant says every
     * element inside the span is a note, so this narrows rather than tests.
     */
    private Stream<NoteElement> notesInSpan() {
        return line().getElements(range()).stream().map(NoteElement.class::cast);
    }

    /**
     * Decides what happens to this beam when {@code change} lands: a deletion that takes an
     * endpoint removes it ({@link #isEndpointDeletedBy}), a replacement that would break the
     * class invariant removes it ({@link #replacementBreaksInvariant}), and an insertion or an
     * in-place modification keeps it.
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return a {@link SpanOutcome.Remove} whose reason is
     *     {@link Strings#DIALOG_REPORT_REASON_ENDPOINT_DELETED} or
     *     {@link Strings#DIALOG_REPORT_REASON_BEAM_NOT_BEAMABLE} when {@code change} invalidates
     *     the beam, otherwise {@link SpanOutcome.Keep#INSTANCE}
     */
    @Override
    public SpanOutcome outcomeFor(ElementChange change) {
        return switch (change) {
            case ElementChange.Deletion deletion -> removeWhen(
                isEndpointDeletedBy(deletion), Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED);
            case ElementChange.Replacement replacement -> removeWhen(
                replacementBreaksInvariant(replacement), Strings.DIALOG_REPORT_REASON_BEAM_NOT_BEAMABLE);
            case ElementChange.Insertion _, ElementChange.Modification _ -> SpanOutcome.Keep.INSTANCE;
        };
    }

    @Override
    protected String reportHeadingKey() {
        return Strings.DIALOG_REPORT_BEAMS_REMOVED;
    }

    /**
     * Returns true if {@code replacement} would break the class invariant, in which case the
     * beam is removed rather than shortened to the members that remain. A replacement outside
     * the span leaves the beam alone.
     *
     * <p>Removal is the whole answer: this is the only rule that decides a replaced beam's fate,
     * so a beam whose last note becomes a rest is removed, not trimmed back to the notes before
     * it. Every replacement path reaches it through {@link Line#replace}, which is what lets
     * the rule live in one place.
     *
     * @param replacement the pending replacement, judged on the pre-replacement line
     * @return {@code true} when any of {@code replacement}'s pairs breaks the invariant
     */
    private boolean replacementBreaksInvariant(ElementChange.Replacement replacement) {
        var beamRange = range();

        return replacement.newElements().entrySet().stream()
            .anyMatch(pair -> pairBreaksInvariant(beamRange, pair.getKey(), pair.getValue()));
    }

    /**
     * Whether putting {@code newElement} at {@code replacedIndex} breaks the class invariant
     * — see {@link #replacementBreaksInvariant}. Any element that is not beamable breaks it,
     * whatever its type.
     *
     * @param beamRange     this beam's range in the pre-replacement line
     * @param replacedIndex the position being replaced
     * @param newElement    the element taking its place
     * @return {@code true} when the position is in the beam and {@code newElement} is not
     *     beamable
     */
    private static boolean pairBreaksInvariant(ElementRange beamRange, int replacedIndex, StaffElement newElement) {
        return beamRange.contains(replacedIndex) && !newElement.getType().isBeamable();
    }

    // -------------------------------------------------------------------------
    // Direction
    // -------------------------------------------------------------------------

    /**
     * Returns the direction every auto stem in this group points.
     *
     * <p>The first member with a forced stem decides, and the group follows it. Otherwise the
     * direction follows the members' staff-position contour: the sum of the lowest and highest
     * member positions is compared against 0 rather than halved, to keep the arithmetic
     * integral, and a sum at or above 0 (Y-down, so heads at or above the middle on average)
     * points the stems down. An upward contour that would trap a grace stem under the beam
     * ({@link #graceStemTouchesBeamAbove}) yields down instead: a grace stem always points up,
     * so an upward beam is the only one it can be buried under.
     *
     * <p>Grace notes are not members: neither their pitch nor a direction forced on them decides
     * the group. Every auto member's {@link NoteElement#direction()} reads this, so the whole
     * group agrees by construction rather than by a write-back.
     *
     * @return the group direction, never null
     * @throws IllegalStateException if this beam's anchor or its end is in no line
     */
    public Direction direction() {
        var minStaffPos = Integer.MAX_VALUE;
        var maxStaffPos = Integer.MIN_VALUE;

        for (var member : members()) {
            var note = member.note();

            if (note.stem() instanceof Stem.Forced forced) {
                return forced.direction();
            }

            var pos = note.getStaffPosition().value();
            minStaffPos = Math.min(minStaffPos, pos);
            maxStaffPos = Math.max(maxStaffPos, pos);
        }

        if ((minStaffPos + maxStaffPos) <= 0) {
            return Direction.DOWN;
        }

        return graceStemTouchesBeamAbove(new StaffPosition(minStaffPos)) ? Direction.DOWN : Direction.UP;
    }

    /**
     * Returns whether any grace note inside this beam's span would have its stem run into — or
     * come within {@link #GRACE_STEM_BEAM_CLEARANCE_SS} of — a beam drawn above the group, whose
     * highest member sits at {@code topStaffPosition}.
     *
     * <p>A grace note keeps its own short, always-upward stem instead of running out to the
     * beam, so an upward beam over a grace note higher than its neighbours crosses the grace
     * stem and swallows the notehead. The stem tip is the grace note's topmost ink: its flag
     * hangs back down from there.
     */
    private boolean graceStemTouchesBeamAbove(StaffPosition topStaffPosition) {
        var beamBottomYSs = BeamMetrics.upwardStackBottomYSs(topStaffPosition, beamStackCount());

        for (var graceNote : graceNotes()) {
            var stemTopYSs = StemMetrics.graceStemTopYSs(graceNote.getStaffPosition());

            if (stemTopYSs < beamBottomYSs + GRACE_STEM_BEAM_CLEARANCE_SS) {
                return true;
            }
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Beam stack count (beams carried by the shortest note in the group)
    // -------------------------------------------------------------------------

    /**
     * Returns the number of beams stacked over this group: 1 for an 8th, 2 for a 16th, 3 for
     * a 32nd. The count is the shortest note's within the span.
     *
     * @return the highest {@link ElementType#beamCount()} among this beam's members
     */
    public int beamStackCount() {
        var maxBeamCount = 0;

        for (var member : members()) {
            maxBeamCount = Math.max(maxBeamCount, member.note().getType().beamCount());
        }

        return maxBeamCount;
    }

    /**
     * {@link Displacement#ABSORB_OVERLAPPING}: a beam merges with any beam it overlaps, but not
     * with an <em>adjacent</em> one — two beam groups written back to back are two deliberate
     * groupings, not one interrupted by an accident.
     */
    @Override
    protected Displacement displacement() {
        return Displacement.ABSORB_OVERLAPPING;
    }

    // -------------------------------------------------------------------------
    // Members
    // -------------------------------------------------------------------------

    /**
     * A note a beam joins, together with its place among the beam's members. Only
     * {@link Beam#members} creates one, so every member is a note its beam joins, and its
     * neighbours are the members either side of it.
     *
     * <p>Neighbours are members, so a grace note between two beamed notes is never the
     * neighbour: the beam that decides a stem's length or stub is the one continuing to the
     * note on the far side of the grace note, not the grace note's own flag. A neighbour past
     * either end of the group counts as carrying no beams.
     */
    public static final class Member {

        /** The notes of every member of the beam, in line order. */
        private final List<NoteElement> notes;

        /** This member's position in {@link #notes}. */
        private final int position;

        private final NoteElement note;

        private Member(List<NoteElement> notes, int position) {
            this.notes = notes;
            this.position = position;
            this.note = notes.get(position);
        }

        /**
         * Returns the note this member is.
         *
         * @return the member's note, never a grace note
         */
        public NoteElement note() {
            return note;
        }

        /**
         * Returns whether this member carries a beam at {@code level}. A sub-beam at a level
         * joins each run of consecutive members that all carry it.
         *
         * @param level the beam level to test
         * @return {@code true} if this member's note carries a beam at {@code level}
         * @invariant every member carries {@link BeamLevel#EIGHTH}
         */
        public boolean carries(BeamLevel level) {
            return level.isCarriedBy(note.getType());
        }

        /**
         * Returns {@code true} if the partial-beam stub on this member should be drawn to the
         * right; {@code false} if it should be drawn to the left or if no stub is needed on it.
         *
         * <p>A stub is needed at a level past the primary one when this member carries it and
         * neither neighbouring member does. The direction rule is:
         * <ul>
         *   <li>first member in group → right</li>
         *   <li>last member, or member immediately after a beam break → left</li>
         *   <li>member immediately before a beam break → right</li>
         *   <li>otherwise → toward the neighbour with more beams</li>
         * </ul>
         *
         * @return {@code true} for a stub drawn to the right; {@code false} for a stub drawn to
         *     the left, or for no stub at all
         */
        public boolean stubRight() {
            var lastPosition = notes.size() - 1;
            var myBeams = note.getType().beamCount();
            var leftBeams = beamCountAt(position - 1);
            var rightBeams = beamCountAt(position + 1);

            var hasStub = false;

            for (@Nullable BeamLevel level = BeamLevel.SIXTEENTH;
                 level != null;
                 level = level.next()) {
                if (carries(level) && !neighborCarries(-1, level) && !neighborCarries(1, level)) {
                    hasStub = true;
                    break;
                }
            }

            if (!hasStub) {
                return false;
            }

            if (position == 0) {
                return true;                          // first element → stub right
            }

            if (position == lastPosition || rightBeams < myBeams) {
                return false;                         // last element or after break → left
            }

            //noinspection SimplifiableIfStatement
            if (leftBeams < myBeams) {
                return true;                          // element at a beam break → right
            }

            return rightBeams >= leftBeams;       // toward neighbour with more beams
        }

        /**
         * Returns how many beam levels this member's stem is shortened by under French beaming
         * — i.e. the number of beam translations between the outer beam and the innermost beam
         * that passes straight <em>through</em> this stem.
         *
         * <p>A stem is shortened only for beams that pass through it, so it still terminates on
         * a beam rather than in mid-air. Following LilyPond ({@code Beam::set_stem_lengths},
         * which intersects each stem's left and right beaming slices), the count is the number
         * of beam levels shared with <em>both</em> neighbours, less one for the level the stem
         * must still reach.
         *
         * <p>So a stem returns 0 — and runs out to the outer beam as usual — exactly when it
         * shares no more than the outer beam with a neighbour: the first and last stem of a
         * group, every stem carrying a single beam, and every stem whose <em>secondary</em> beam
         * breaks. A break at a deeper level still shortens: in a 16th/32nd/32nd/16th group the
         * second stem is where the 32nd sub-beam starts, yet it stops one level short, on the
         * 16th beam. A stub is not a level the stem must reach either — it sits nearer the
         * notehead and is covered on the way.
         *
         * <p>This is a drawing-time correction only: the logical stem tip stays at the outer
         * beam, which is what beam placement and the stacker read.
         *
         * @return the number of beam levels shared with both neighbours, less one for the level
         *     the stem must still reach; 0 for the first and last stem of the group
         */
        public int frenchShortening() {
            // A note's beams always run from the primary level inward, so LilyPond's
            // intersection of the stem's left and right beaming slices is simply the smallest
            // of the three counts — the beams that reach this stem from both sides pass
            // through it. The first and last stem have a missing neighbour, which counts as no
            // beams.
            var leftBeams = beamCountAt(position - 1);
            var ownBeams = note.getType().beamCount();
            var rightBeams = beamCountAt(position + 1);
            var beamsThrough = Math.min(ownBeams, Math.min(leftBeams, rightBeams));

            return Math.max(0, beamsThrough - 1);
        }

        /**
         * Returns {@link ElementType#beamCount()} of the member at {@code memberPosition}, or 0
         * when {@code memberPosition} is past either end of the group.
         */
        private int beamCountAt(int memberPosition) {
            if (memberPosition < 0 || memberPosition >= notes.size()) {
                return 0;
            }

            return notes.get(memberPosition).getType().beamCount();
        }

        /**
         * Returns whether the member at {@code position + offset} carries a beam at
         * {@code level} ({@link BeamLevel#isCarriedBy}), or {@code false} when that position is
         * past either end of the group.
         */
        private boolean neighborCarries(int offset, BeamLevel level) {
            var neighborPosition = position + offset;

            if (neighborPosition < 0 || neighborPosition >= notes.size()) {
                return false;
            }

            return level.isCarriedBy(notes.get(neighborPosition).getType());
        }
    }
}
