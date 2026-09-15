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

import songscribe.engraving.BeamMetrics;
import songscribe.engraving.Staff;
import songscribe.engraving.StaffPosition;
import songscribe.engraving.StemMetrics;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.BeamingRemoval;
import songscribe.message.mutation.Mutation;

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
 * <p>Invariant: every element inside a beam's span is a note, both endpoints are beamable, and
 * every non-grace element strictly inside it is beamable. {@link #isInvalidatedByReplacement}
 * keeps it through an element replacement by removing the beam whenever the replacement would
 * break it, which is why {@link #noteAt} may narrow without testing. A broken beam is always
 * removed, never shortened to the members that survive.
 *
 * <p>A grace note inside the span is not a member: it sits between two beamed notes without
 * joining their beam, keeping its own short upward stem and its own flag, and neither its pitch
 * nor its stem may decide where the beam goes. Every walk over the span below steps over it
 * rather than counting it as a member.
 */
public final class Beam extends NoteSpan {

    /**
     * Number of distinct beam levels: level 0 = 8th, level 1 = 16th, level 2 = 32nd. The
     * MusicXML writer sizes its per-note beam-value arrays by it.
     */
    public static final int LEVEL_COUNT = NoteValue.THIRTY_SECOND.flagCount();

    /**
     * Vertical gap, in staff spaces, a grace note's stem tip must keep below an upward beam
     * before the beam is pushed to the other side of the staff instead
     * ({@link #graceStemTouchesBeamAbove}).
     * <p>
     * It also absorbs the error in {@link #estimatedBeamBottomAboveYSs}, which places the beam
     * a natural stem above the group's highest head while the scorer routinely settles a few
     * tenths of a space lower. Erring low here only means a beam flips that would have been a
     * near miss; erring high means the grace note is buried, which is the bug this guards.
     */
    private static final double GRACE_STEM_BEAM_CLEARANCE_SS = 0.5;

    public Beam(NoteElement anchorElement, NoteElement endElement) {
        super(anchorElement, endElement);
    }

    @Override
    protected NoteSpan createNoteCopy(NoteElement newAnchor, NoteElement newEnd) {
        return new Beam(newAnchor, newEnd);
    }

    /**
     * The line this beam's members are in. A beam never crosses a line, so the anchor's line is
     * the whole group's.
     *
     * @throws IllegalStateException if the anchor is in no line, which a removal that detached
     *     it leaves behind until the span is cleaned up
     */
    private Line line() {
        var line = getAnchorLine();

        if (line == null) {
            throw new IllegalStateException("beam anchor is in no line: " + this);
        }

        return line;
    }

    /**
     * Returns the note at line index {@code index}, which lies inside this beam's span. The one
     * narrowing every walk over the span uses: the class invariant says every element inside the
     * span is a note, so a walker asks for the note rather than testing for it.
     *
     * @param index a line index between this beam's anchor and end, inclusive
     * @return the element at {@code index}, as a note; a grace note is returned like any other
     *     and is the walker's to step over
     * @throws IllegalStateException if the element at {@code index} is not a note, which the
     *     invariant rules out
     */
    public NoteElement noteAt(int index) {
        return line().noteAt(index);
    }

    /**
     * Returns true if replacing {@code oldElement} with {@code newElement} would break the class
     * invariant, in which case the beam is removed rather than shortened to the members that
     * remain. A replacement outside the span leaves the beam alone.
     *
     * <p>Removal is the whole answer: this is the only rule that decides a replaced beam's fate,
     * so a beam whose last note becomes a rest is removed, not trimmed back to the notes before
     * it. Every replacement path reaches it through {@link Line#setElement}, which is what lets
     * the rule live in one place.
     *
     * <p>Must be called on the <em>pre-replacement</em> line state, while {@code oldElement} is
     * still in the line.
     *
     * @param oldElement the element being replaced (still in the line at call time)
     * @param newElement the element that will replace it
     * @param line       the line receiving the replacement (pre-replacement state)
     */
    @Override
    protected boolean isInvalidatedByReplacement(
        StaffElement oldElement, StaffElement newElement, Line line
    ) {
        var replacedIndex = line.getElementIndex(oldElement);
        var beamStart = getAnchorElementIndex();
        var beamEnd = getEndElementIndex();

        // A beam never crosses a line, so an element of another line has index -1 here and an
        // index inside the span is inside the beam.
        if (replacedIndex < beamStart || replacedIndex > beamEnd) {
            return false;
        }

        if (!(newElement instanceof NoteElement newNote)) {
            return true;
        }

        var newType = newNote.getType();

        if (newType.isBeamable()) {
            return false;
        }

        // A beam legitimately passes over a grace note in its interior (refs #592), but its
        // endpoints are members of the beam, and a grace note cannot be one.
        return !newType.isGraceNote() || replacedIndex == beamStart || replacedIndex == beamEnd;
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
     * @throws IllegalStateException if this beam's anchor is in no line
     */
    public Direction direction() {
        var beamStart = getAnchorElementIndex();
        var beamEnd = getEndElementIndex();
        var minStaffPos = Integer.MAX_VALUE;
        var maxStaffPos = Integer.MIN_VALUE;

        for (var i = beamStart; i <= beamEnd; i++) {
            var member = noteAt(i);

            if (member.getType().isGraceNote()) {
                continue;
            }

            if (member.stem() instanceof Stem.Forced forced) {
                return forced.direction();
            }

            var pos = member.getStaffPosition().value();
            minStaffPos = Math.min(minStaffPos, pos);
            maxStaffPos = Math.max(maxStaffPos, pos);
        }

        if ((minStaffPos + maxStaffPos) <= 0) {
            return Direction.DOWN;
        }

        return graceStemTouchesBeamAbove(minStaffPos) ? Direction.DOWN : Direction.UP;
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
    private boolean graceStemTouchesBeamAbove(int topStaffPosition) {
        var beamEnd = getEndElementIndex();
        var beamBottomYSs = estimatedBeamBottomAboveYSs(topStaffPosition);

        for (var i = getAnchorElementIndex(); i <= beamEnd; i++) {
            var graceNote = noteAt(i);

            if (!graceNote.getType().isGraceNote()) {
                continue;
            }

            var stemTopYSs =
                graceNote.getStaffPosition().toSs() - StemMetrics.GRACE_NOTE_STEM_LENGTH_SS;

            if (stemTopYSs < beamBottomYSs + GRACE_STEM_BEAM_CLEARANCE_SS) {
                return true;
            }
        }

        return false;
    }

    /**
     * Estimates the Y of the lowest edge of the beam stack an upward beam would draw over this
     * group, in layout staff spaces (Y down) from the middle line. {@code topStaffPosition} is
     * the staff position of the group's highest member.
     *
     * <p>An estimate is all that is available: the direction has to be settled before columns
     * exist, and the beam scorer cannot place the beam without their X positions. So the beam
     * is assumed to sit a natural stem above the highest head — its outer edge — with the stack
     * of {@link #beamLevel} beams hanging below it at the renderer's spacing.
     */
    private double estimatedBeamBottomAboveYSs(int topStaffPosition) {
        var beamStackCount = beamLevel() + 1;

        return topStaffPosition * Staff.HALF_SPACE_SS
            - StemMetrics.STEM_LENGTH_SS
            + BeamMetrics.beamStackHeightSs(beamStackCount);
    }

    // -------------------------------------------------------------------------
    // Beam level (highest level spanned by the shortest note in the group)
    // -------------------------------------------------------------------------

    /**
     * Returns the beam level of this group, where level 0 = 8th, level 1 = 16th, level 2 =
     * 32nd. The level is the shortest note's within the span.
     *
     * @return the highest {@link ElementType#beamCount()} among this beam's members, minus one
     */
    public int beamLevel() {
        var line = line();
        var beamEnd = getEndElementIndex();
        var maxBeamCount = 0;

        for (var i = getAnchorElementIndex(); i <= beamEnd; i++) {
            maxBeamCount = Math.max(maxBeamCount, line.getElement(i).getType().beamCount());
        }

        return maxBeamCount - 1;
    }

    // -------------------------------------------------------------------------
    // Note-type-in-level predicate
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the note at {@code noteIndex} is beamable at the given
     * {@code level} (0 = 8th, 1 = 16th, 2 = 32nd).
     *
     * <p>Grace notes are resolved by inspecting the nearest non-grace neighbours: a grace note
     * is in level L if both neighbours are in level L. The neighbours are looked for in the
     * whole line, so a grace note at the edge of the span reads past it.
     *
     * @param noteIndex the line index of the note to test
     * @param level     the beam level to test against (0 = 8th, 1 = 16th, 2 = 32nd)
     * @return {@code true} if the note at {@code noteIndex} carries a beam at {@code level}
     */
    public boolean noteTypeInLevel(int noteIndex, int level) {
        var line = line();
        var type = line.getElement(noteIndex).getType();

        if (!type.isGraceNote()) {
            return type.beamCount() > level;
        }

        // Grace notes: check surrounding non-grace notes.
        var begin = noteIndex - 1;
        var end = noteIndex + 1;

        while (begin > 0 && line.getElement(begin).getType().isGraceNote()) {
            begin--;
        }

        while (end < line.elementCount() && line.getElement(end).getType().isGraceNote()) {
            end++;
        }

        return begin >= 0 && noteTypeInLevel(begin, level)
            && end < line.elementCount() && noteTypeInLevel(end, level);
    }

    // -------------------------------------------------------------------------
    // Stub direction
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the partial-beam stub at position {@code i} within this group
     * should be drawn to the right; {@code false} if it should be drawn to the left or if no
     * stub is needed at this position.
     *
     * <p>A stub is needed at beam level L when neither the left nor the right neighbour shares
     * level L. The direction rule is:
     * <ul>
     *   <li>first element in group → right</li>
     *   <li>last element, or element immediately after a beam break → left</li>
     *   <li>element immediately before a beam break → right</li>
     *   <li>otherwise → toward the neighbour with more beams</li>
     * </ul>
     *
     * @param i the line index of the element to test
     * @return {@code true} for a stub drawn to the right; {@code false} for a stub drawn to the
     *     left, or for no stub at all
     */
    public boolean stubRight(int i) {
        var line = line();
        var beamStart = getAnchorElementIndex();
        var beamEnd = getEndElementIndex();
        var myBeams = line.getElement(i).getType().beamCount();
        var leftBeams = i > beamStart ? neighborBeamCount(i, -1) : 0;
        var rightBeams = i < beamEnd ? neighborBeamCount(i, 1) : 0;

        var hasStub = false;

        for (var level = 2; level <= myBeams; level++) {
            if (leftBeams < level && rightBeams < level) {
                hasStub = true;
                break;
            }
        }

        if (!hasStub) {
            return false;
        }

        if (i == beamStart) {
            return true;                          // first element → stub right
        }

        if (i == beamEnd || rightBeams < myBeams) {
            return false;                         // last element or after break → left
        }

        //noinspection SimplifiableIfStatement
        if (leftBeams < myBeams) {
            return true;                          // element at a beam break → right
        }

        return rightBeams >= leftBeams;       // toward neighbour with more beams
    }

    // -------------------------------------------------------------------------
    // French beaming
    // -------------------------------------------------------------------------

    /**
     * Returns how many beam levels the stem at index {@code i} within this group is shortened
     * by under French beaming — i.e. the number of beam translations between the outer beam
     * and the innermost beam that passes straight <em>through</em> this stem.
     *
     * <p>A stem is shortened only for beams that pass through it, so it still terminates on a
     * beam rather than in mid-air. Following LilyPond ({@code Beam::set_stem_lengths}, which
     * intersects each stem's left and right beaming slices), the count is the number of beam
     * levels shared with <em>both</em> neighbours, less one for the level the stem must still
     * reach.
     *
     * <p>So a stem returns 0 — and runs out to the outer beam as usual — exactly when it shares
     * no more than the outer beam with a neighbour: the first and last stem of a group, every
     * stem carrying a single beam, and every stem whose <em>secondary</em> beam breaks. A break
     * at a deeper level still shortens: in a 16th/32nd/32nd/16th group the second stem is where
     * the 32nd sub-beam starts, yet it stops one level short, on the 16th beam. A stub is not a
     * level the stem must reach either — it sits nearer the notehead and is covered on the way.
     *
     * <p>This is a drawing-time correction only: the logical stem tip stays at the outer beam,
     * which is what beam placement and the stacker read.
     *
     * @param i the line index of the stem to test
     * @return the number of beam levels shared with both neighbours, less one for the level the
     *     stem must still reach; 0 for the first and last stem of the group
     */
    public int frenchBeamShortening(int i) {
        if (i <= getAnchorElementIndex() || i >= getEndElementIndex()) {
            return 0;
        }

        // A note's beams always run 0..beamCount-1, so LilyPond's intersection of the
        // stem's left and right beaming slices is simply the smallest of the three
        // counts — the beams that reach this stem from both sides pass through it.
        var leftBeams = neighborBeamCount(i, -1);
        var ownBeams = line().getElement(i).getType().beamCount();
        var rightBeams = neighborBeamCount(i, 1);
        var beamsThrough = Math.min(ownBeams, Math.min(leftBeams, rightBeams));

        return Math.max(0, beamsThrough - 1);
    }

    /**
     * Returns {@link ElementType#beamCount()} of the nearest element to one side of
     * {@code index} that takes part in beaming, stepping by {@code step} (-1 for the left
     * neighbour, 1 for the right); 0 if the line runs out first.
     *
     * <p>Grace notes are stepped over. One can be inserted between two beamed notes without
     * joining their beams, so the beam that decides this stem's length is the one continuing
     * to the note on the far side of it, not the grace note's own flag.
     *
     * @param index the line index to search from
     * @param step  -1 to search left, 1 to search right
     * @return the neighbour's beam count, or 0 if the line runs out first
     */
    private int neighborBeamCount(int index, int step) {
        var neighborIndex = line().nearestNonGraceIndex(index, step);

        return neighborIndex < 0 ? 0 : line().getElement(neighborIndex).getType().beamCount();
    }

    /**
     * Merges this beam with any existing beam that overlaps it: widens this beam to cover
     * them and removes every beam the widened range subsumes. Beams do not absorb an
     * <em>adjacent</em> beam — two beam groups written back to back are two deliberate
     * groupings, not one interrupted by an accident — so the reach is zero.
     */
    @Override
    protected void applyDisplacementPolicy(Line line) {
        line.spanRegistry().mergeOverlapping(this, Beam.class, 0);
    }

    @Override
    protected Mutation additionRecordFor(Line line) {
        return new BeamingAddition(line, this);
    }

    @Override
    protected Mutation removalRecordFor(Line line) {
        return new BeamingRemoval(line, this);
    }
}
