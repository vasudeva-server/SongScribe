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
 * <p>Nothing a beam answers needs geometry, so a note can ask its beam for a direction before
 * any column exists.
 *
 * <p>Invariant: every element inside a beam's span is a note, both endpoints are beamable, and
 * every non-grace element strictly inside it is beamable; {@link #members} and the query for
 * the grace notes it passes over narrow without testing. A broken beam is always removed, never
 * shortened to the members that survive.
 *
 * <p>Invariant: a beam never holds both notes of a tie ({@link Tie}).
 *
 * <p>A grace note inside the span is not a member: neither its pitch nor its stem decides where
 * the beam goes. It is always strictly inside the span, so its nearest members on either side
 * are both members of this beam.
 */
public final class Beam extends Span<NoteElement> {

    /**
     * Absorbs the error in {@link BeamMetrics#upwardStackBottomYSs}. Erring high buries the grace
     * note; erring low only flips a near miss.
     */
    private static final double GRACE_STEM_BEAM_CLEARANCE_SS = 0.5;

    private static final int MIN_MEMBERS = 2;

    private static final String REPORT_HEADING_KEY = Strings.REPORT_BEAMS_REMOVED;

    /** This beam's direction while a {@link DirectionHold} is open on it; null otherwise. */
    private @Nullable Direction heldDirection = null;

    private Beam(NoteElement anchorElement, NoteElement endElement) {
        super(NoteElement.class, anchorElement, endElement);
    }

    /**
     * Returns a beam over {@code range} of {@code line} when the class invariant allows one
     * there, or the reason it does not. With {@link #createCopy}, this is the one way to make a
     * beam: the editor, the paste and both file readers ask it, so a range holds a beam by the
     * same rule whichever route it arrives by. It is the counterpart of {@link Hairpin#spanning}
     * and {@link Ending#spanning}.
     * <p>
     * A grace note at either end of {@code range} is left outside the beam: the beam is judged
     * over the range from the first non-grace element to the last
     * ({@link StaffElementRun#nonGraceRange}), since a grace note never joins a beam. Every
     * element of that range must be beamable or a grace note.
     * <p>
     * Adding a beam absorbs every beam it overlaps ({@link Displacement#ABSORB_OVERLAPPING}),
     * so the beam is built over the range it will hold once added: the judged range widened
     * over every beam on {@code line} that overlaps it, again and again until nothing more
     * overlaps. A beam already on the line meets the class invariant, so the widened range does
     * too, and is not judged again. A beam back to back with the range is not absorbed.
     * <p>
     * The beam is built, not added: {@code line}'s span registry is unchanged. Added before any
     * other beam joins the line, {@link Span#addTo} removes the absorbed beams and moves neither
     * endpoint.
     *
     * @param line  the line the beam would be on
     * @param range the range the beam would cover; both of its indices are indices of
     *              {@code line}
     * @return {@link Spanning.Spanned} with a new beam over the widened range, anchored on its
     *     first element and ending on its last, and with every beam the widened range absorbs,
     *     empty when it absorbs none; or {@link Spanning.Refused} with
     *     {@link Spanning.Cause#RANGE}, under {@link Strings#REPORT_BEAMS_REMOVED}, and
     *     <ul>
     *       <li>{@link Strings#REPORT_REASON_BEAM_TOO_FEW_NOTES} when {@code range} holds
     *           fewer than {@value #MIN_MEMBERS} elements that are not grace notes;</li>
     *       <li>{@link Strings#REPORT_REASON_BEAM_UNBEAMABLE_ELEMENT} when an element
     *           from its first non-grace element to its last is neither beamable nor a grace
     *           note;</li>
     *       <li>{@link Strings#REPORT_REASON_BEAM_TIED_NOTES} when the widened range holds
     *           both notes of a tie ({@link Tie#bothNotesIn}).</li>
     *     </ul>
     *     The reasons are judged in that order.
     */
    public static Spanning<Beam> spanning(Line line, ElementRange range) {
        var joined = line.nonGraceRange(range);

        if (joined == null || joined.size() < MIN_MEMBERS) {
            return refused(Strings.REPORT_REASON_BEAM_TOO_FEW_NOTES);
        }

        for (var index = joined.begin(); index <= joined.end(); index++) {
            var type = line.getElement(index).getType();

            if (!type.isBeamable() && !type.isGraceNote()) {
                return refused(Strings.REPORT_REASON_BEAM_UNBEAMABLE_ELEMENT);
            }
        }

        // The reach and the same-kind test are the ones the displacement applies, so the range
        // answered here is the one adding the beam produces.
        var absorption = line.spanRegistry().absorption(joined, Beam.class::isInstance, 0);
        var widened = absorption.range();

        if (holdsTiedPair(line, widened)) {
            return refused(Strings.REPORT_REASON_BEAM_TIED_NOTES);
        }

        var absorbed = absorption.absorbedAs(Beam.class);
        var beam = new Beam(line.noteAt(widened.begin()), line.noteAt(widened.end()));

        return new Spanning.Spanned<>(beam, absorbed);
    }

    private static boolean holdsTiedPair(Line line, ElementRange range) {
        for (var index = range.begin(); index <= range.end(); index++) {
            if (line.getElement(index) instanceof NoteElement note
                && note.isTiedToNext()
                && Tie.bothNotesIn(line, index, range)) {
                return true;
            }
        }

        return false;
    }

    private static Spanning<Beam> refused(String reasonKey) {
        return new Spanning.Refused<>(reasonKey, Spanning.Cause.RANGE, REPORT_HEADING_KEY);
    }

    @Override
    protected Beam createCopy(NoteElement newAnchor, NoteElement newEnd) {
        return new Beam(newAnchor, newEnd);
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
        var notes = notesInSpan().filter(Beam::joinsWithinSpan).toList();

        return IntStream.range(0, notes.size())
            .mapToObj(position -> new Member(notes, position))
            .toList();
    }

    private List<NoteElement> graceNotes() {
        return notesInSpan().filter(note -> !joinsWithinSpan(note)).toList();
    }

    /**
     * The one membership rule, shared by {@link #members()} and
     * {@link LineSpans#findBeamJoining} so they cannot disagree.
     *
     * @param element an element inside some beam's span
     */
    static boolean joinsWithinSpan(StaffElement element) {
        return !element.getType().isGraceNote();
    }

    private Stream<NoteElement> notesInSpan() {
        return line().getElements(range()).stream().map(NoteElement.class::cast);
    }

    /**
     * Decides what happens to this beam when {@code change} lands: a deletion that takes an
     * endpoint removes it ({@link #isEndpointDeletedBy}), a replacement that would break the
     * class invariant removes it ({@link #replacementBreaksInvariant}), an insertion that would
     * break it removes it ({@link #insertionBreaksInvariant}), and an in-place modification
     * keeps it.
     * <p>
     * An insertion lands inside the beam when its index is after the anchor's and no later than
     * the end's: the inserted element then sits between two notes the beam joins. It breaks the
     * invariant unless it is beamable or a grace note, which the beam passes over without
     * joining. An insertion at the anchor's own index or just past the end lands outside the
     * beam and keeps it.
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return a {@link SpanOutcome.Remove} whose reason is
     *     {@link Strings#REPORT_REASON_ENDPOINT_DELETED} for a deletion,
     *     {@link Strings#REPORT_REASON_BEAM_NOT_BEAMABLE} for a replacement, or
     *     {@link Strings#REPORT_REASON_BEAM_INSERTION_NOT_BEAMABLE} for an insertion,
     *     when {@code change} invalidates the beam; otherwise {@link SpanOutcome.Keep#INSTANCE}
     */
    @Override
    public SpanOutcome outcomeFor(ElementChange change) {
        return switch (change) {
            case ElementChange.Deletion deletion -> outcomeWhen(
                isEndpointDeletedBy(deletion), Strings.REPORT_REASON_ENDPOINT_DELETED);
            case ElementChange.Replacement replacement -> outcomeWhen(
                replacementBreaksInvariant(replacement), Strings.REPORT_REASON_BEAM_NOT_BEAMABLE);
            case ElementChange.Insertion insertion -> outcomeWhen(
                insertionBreaksInvariant(insertion), Strings.REPORT_REASON_BEAM_INSERTION_NOT_BEAMABLE);
            case ElementChange.Modification _ -> SpanOutcome.Keep.INSTANCE;
        };
    }

    private boolean insertionBreaksInvariant(ElementChange.Insertion insertion) {
        var beamRange = range();
        var index = insertion.index();
        var insertedType = insertion.inserted().getType();

        return beamRange.begin() < index
            && index <= beamRange.end()
            && !insertedType.isBeamable()
            && !insertedType.isGraceNote();
    }

    @Override
    protected String reportHeadingKey() {
        return REPORT_HEADING_KEY;
    }

    /**
     * A beam whose last note becomes a rest is removed, not trimmed back to the notes before it.
     */
    private boolean replacementBreaksInvariant(ElementChange.Replacement replacement) {
        var beamRange = range();

        return replacement.newElements().entrySet().stream()
            .anyMatch(pair -> pairBreaksInvariant(beamRange, pair.getKey(), pair.getValue()));
    }

    private static boolean pairBreaksInvariant(ElementRange beamRange, int replacedIndex, StaffElement newElement) {
        return beamRange.contains(replacedIndex) && !newElement.getType().isBeamable();
    }

    /**
     * Returns the direction every auto stem in this group points.
     *
     * <p>The first member with a forced stem decides, and the group follows it. Otherwise the
     * direction follows the members' staff-position contour: the sum of the lowest and highest
     * member positions is compared against 0 rather than halved, to keep the arithmetic
     * integral, and a sum at or below 0 (Y-down, so heads at or above the middle on average)
     * points the stems down. An upward contour that would trap a grace stem under the beam
     * ({@link #graceStemTouchesBeamAbove}) yields down instead: a grace stem always points up,
     * so an upward beam is the only one it can be buried under.
     *
     * <p>Grace notes are not members: neither their pitch nor a direction forced on them decides
     * the group. Every auto member's {@link NoteElement#direction()} reads this, so the whole
     * group agrees by construction rather than by a write-back.
     *
     * @return the group direction, never null
     * @throws IllegalStateException if this beam's anchor or its end is in no line and no
     *     {@link DirectionHold} is open on it
     * @invariant while a {@link DirectionHold} is open on this beam, every call answers the
     *     direction computed when the hold opened
     */
    public Direction direction() {
        var held = heldDirection;

        return held != null ? held : computeDirection();
    }

    /**
     * The caller must not change a held beam's line while the hold is open: no element
     * inserted, removed or replaced inside it, and no member's stem, type or staff position
     * written. Holds do not nest: closing one releases every beam it holds.
     */
    public static DirectionHold holdDirections(List<Beam> beams) {
        for (var beam : beams) {
            beam.heldDirection = beam.computeDirection();
        }

        return new DirectionHold(beams);
    }

    private Direction computeDirection() {
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

    public int beamStackCount() {
        var maxBeamCount = 0;

        for (var member : members()) {
            maxBeamCount = Math.max(maxBeamCount, member.note().getType().beamCount());
        }

        return maxBeamCount;
    }

    /**
     * A beam merges with any beam it overlaps, but not with an <em>adjacent</em> one: two beam
     * groups written back to back are two deliberate groupings.
     */
    @Override
    protected Displacement displacement() {
        return Displacement.ABSORB_OVERLAPPING;
    }

    /**
     * A note a beam joins; only {@link Beam#members} creates one.
     *
     * <p>Neighbours are members, so a grace note is never a neighbour. A neighbour past either
     * end of the group counts as carrying no beams.
     */
    public static final class Member {

        private final List<NoteElement> notes;

        private final int position;

        private final NoteElement note;

        private Member(List<NoteElement> notes, int position) {
            this.notes = notes;
            this.position = position;
            this.note = notes.get(position);
        }

        public NoteElement note() {
            return note;
        }

        public int position() {
            return position;
        }

        /**
         * Every member carries {@link BeamLevel#EIGHTH}.
         */
        public boolean carries(BeamLevel level) {
            return level.isCarriedBy(note.getType());
        }

        /**
         * @return {@code null} when this member does not carry {@code level}
         */
        public @Nullable MemberRun runAt(BeamLevel level) {
            if (!carries(level)) {
                return null;
            }

            var first = position;

            while (memberCarries(first - 1, level)) {
                first--;
            }

            var last = position;

            while (memberCarries(last + 1, level)) {
                last++;
            }

            return new MemberRun(first, last);
        }

        /**
         * Returns the side this member's partial-beam stub is drawn on.
         *
         * <p>A member has a stub when its run at some level ({@link #runAt}) is this member
         * alone. The side is:
         * <ul>
         *   <li>the first member of the group → right;</li>
         *   <li>the last member → left;</li>
         *   <li>any other member → toward the neighbour with more beams, and right when both
         *       have the same number.</li>
         * </ul>
         * Every stub on one member points the same way, whatever level it is at.
         *
         * @return {@link Stub#NONE} when no run of this member's is a single member; otherwise
         *     the side its stubs are drawn on
         */
        public Stub stub() {
            if (!hasStub()) {
                return Stub.NONE;
            }

            if (position == 0) {
                return Stub.RIGHT;
            }

            if (position == notes.size() - 1) {
                return Stub.LEFT;
            }

            return beamCountAt(position + 1) >= beamCountAt(position - 1) ? Stub.RIGHT : Stub.LEFT;
        }

        private boolean hasStub() {
            for (@Nullable BeamLevel level = BeamLevel.EIGHTH; level != null; level = level.next()) {
                var run = runAt(level);

                if (run != null && run.isSingle()) {
                    return true;
                }
            }

            return false;
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

        private int beamCountAt(int memberPosition) {
            return isMemberPosition(memberPosition) ? notes.get(memberPosition).getType().beamCount() : 0;
        }

        private boolean memberCarries(int memberPosition, BeamLevel level) {
            return isMemberPosition(memberPosition) && level.isCarriedBy(notes.get(memberPosition).getType());
        }

        private boolean isMemberPosition(int memberPosition) {
            return memberPosition >= 0 && memberPosition < notes.size();
        }
    }

    /**
     * @param last the position of the run's last member, no less than {@code first}
     */
    public record MemberRun(int first, int last) {

        public boolean isSingle() {
            return first == last;
        }
    }

    public enum Stub {
        NONE,

        LEFT,

        RIGHT
    }

    /**
     * An open hold on the directions of a set of beams ({@link #holdDirections}); closing it
     * releases them.
     */
    public static final class DirectionHold implements AutoCloseable {

        private final List<Beam> beams;

        private DirectionHold(List<Beam> beams) {
            this.beams = List.copyOf(beams);
        }

        @Override
        public void close() {
            for (var beam : beams) {
                beam.heldDirection = null;
            }
        }
    }
}
