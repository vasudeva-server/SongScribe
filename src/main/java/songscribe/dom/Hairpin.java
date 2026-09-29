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

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;

/**
 * A hairpin dynamic marking: a crescendo or a diminuendo, as its {@link Kind} says.
 * <p>
 * A hairpin is a wedge-shaped marking placed above the staff that indicates
 * a gradual change in volume. The user can adjust the horizontal endpoints
 * and vertical position.
 */
public final class Hairpin extends Span<StaffElement> implements IntrinsicHeight {

    /**
     * Height of the hairpin opening in staff-space units. LilyPond's {@code Hairpin.height}
     * grob property ({@code scm/define-grobs.scm}) is {@code 0.6666}, a <em>half</em>-height:
     * {@code Hairpin::print} ({@code lily/hairpin.cc:335-336}) draws the two wedge segments at
     * {@code Offset(x, starth)} and {@code Offset(x, -starth)} where {@code starth == height},
     * so the full mouth opening is twice the property value.
     */
    public static final double HAIRPIN_OPENING_HEIGHT_SS = 1.3332;

    /**
     * The number of columns a hairpin needs to slope across. A pitched note is one
     * column; so is a rest closing the span, which bounds a wedge in its own right.
     * A grace note is not — it shares its host's column — so a grace note and its
     * host are two elements but a single column.
     */
    public static final int MIN_COLUMNS = 2;

    /** Whether this is a crescendo or a diminuendo. */
    private final Kind kind;

    /** User-controlled left-endpoint horizontal shift in staff-space units. */
    private double x1ShiftSs;

    /** User-controlled right-endpoint horizontal shift in staff-space units. */
    private double x2ShiftSs;

    /**
     * Creates a hairpin of {@code kind} spanning from anchor to end element.
     *
     * @param kind          Whether the hairpin is a crescendo or a diminuendo
     * @param anchorElement The starting element of the hairpin
     * @param endElement    The ending element of the hairpin
     */
    public Hairpin(Kind kind, StaffElement anchorElement, StaffElement endElement) {
        super(StaffElement.class, anchorElement, endElement);
        this.kind = kind;
    }

    /**
     * Which of the two hairpins this is.
     *
     * @return the kind this hairpin was created with
     */
    public Kind getKind() {
        return kind;
    }

    /**
     * {@link Displacement#ABSORB_ADJACENT}: a hairpin drawn flush against a same-kind one
     * continues it rather than starting a second — one uninterrupted dynamic gesture, one
     * hairpin.
     */
    @Override
    protected Displacement displacement() {
        return Displacement.ABSORB_ADJACENT;
    }

    /**
     * Narrows the kind test to a hairpin of the same {@link Kind}, so a crescendo never
     * displaces a diminuendo, nor a diminuendo a crescendo.
     *
     * @return {@code true} when {@code other} is a hairpin whose {@link Kind} is this one's
     */
    @Override
    boolean isSameKindAs(Span<?> other) {
        return super.isSameKindAs(other)
            && other instanceof Hairpin otherHairpin
            && otherHairpin.kind == kind;
    }

    /**
     * The {@link SpanOutcome} projection of {@link #hairpinOutcomeFor}: a reshaped
     * hairpin is one that survives, so a {@link SpanOutcome.Reshape} reads as
     * {@link SpanOutcome.Keep#INSTANCE}. The sweep is the one reader that acts on the reshaped
     * range and asks {@link #hairpinOutcomeFor} directly; every other reader asks only whether
     * the hairpin survives, which this answers.
     *
     * @return a {@link SpanOutcome.Remove} when this hairpin cannot survive
     *     {@code change}, otherwise {@link SpanOutcome.Keep#INSTANCE}
     */
    @Override
    public final SpanOutcome outcomeFor(ElementChange change) {
        return switch (hairpinOutcomeFor(change)) {
            case SpanOutcome.Remove remove -> remove;
            case SpanOutcome.Keep _, SpanOutcome.Reshape _ -> SpanOutcome.Keep.INSTANCE;
        };
    }

    @Override
    protected String reportHeadingKey() {
        return Strings.DIALOG_REPORT_HAIRPINS_REMOVED;
    }

    /**
     * Decides what happens to this hairpin when {@code change} lands, judged entirely
     * against {@link ElementChange#projectedElements} — the line as it will be.
     * <p>
     * An insertion or a replacement removes no element, so there is nothing to pull an
     * endpoint in to: both only ask whether this hairpin's two endpoints are still
     * endpoints the user could have placed, and answer {@link SpanOutcome.Remove}
     * or {@link SpanOutcome.Keep#INSTANCE}. Under {@link ElementChange.Replacement} an
     * endpoint that is a replaced element is read as having <em>become</em> its
     * replacement — see {@link ElementChange#projectedEndpointIndexOf}.
     * <p>
     * A deletion can pull an endpoint in, so it answers
     * {@link SpanOutcome.Reshape} — <b>even when the hairpin's span is unchanged</b>,
     * rather than {@link SpanOutcome.Keep#INSTANCE}. This is the one place a hairpin departs
     * from the "{@code Keep} means untouched" reading: the caller's merge pass needs every
     * surviving hairpin's projected span, not only the moved ones, and a run of one whose
     * endpoints both survive already emits no mutation.
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return {@link SpanOutcome.Keep#INSTANCE} or a {@link SpanOutcome.Remove} for a
     *     change that removes no element; for a deletion this hairpin survives, the
     *     {@link SpanOutcome.Reshape} over its projected span, and otherwise
     *     a {@link SpanOutcome.Remove}
     */
    public SpanOutcome.HairpinOutcome hairpinOutcomeFor(ElementChange change) {
        var range = range();

        if (change instanceof ElementChange.Deletion deletion) {
            return outcomeForDeletion(deletion, range);
        }

        // Every other shape removes no element, so none of them can pull an endpoint in and
        // all are answered alike — one call rather than an arm apiece saying the same thing.
        // The exhaustive switches in the other kinds' outcomeFor are what force a new shape
        // to be considered, and this is where a hairpin considers it.
        var line = change.line();
        return outcomeForRepointing(change, line.getElement(range.begin()), line.getElement(range.end()));
    }

    /**
     * Answers a change that removes no element: the hairpin keeps both of its endpoints,
     * so what is at stake is whether those endpoints are still legal in the projection,
     * and whether what lies between them is still wide enough to slope across — a
     * replacement can turn a column into a grace note that shares the next one.
     *
     * @param anchor this hairpin's anchor element, as found in the pre-change line
     * @param end    this hairpin's end element, as found in the pre-change line
     */
    private SpanOutcome.HairpinOutcome outcomeForRepointing(ElementChange change, StaffElement anchor, StaffElement end) {
        var projected = change.projectedElements();
        var anchorIndex = change.projectedEndpointIndexOf(anchor);
        var endIndex = change.projectedEndpointIndexOf(end);

        if (anchorIndex == null || endIndex == null || endIndex <= anchorIndex) {
            return new SpanOutcome.Remove(Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_INVALID);
        }

        var span = new ElementRange(anchorIndex, endIndex);

        // Both predicates read the neighbours of the index they are given — the host after
        // an anchor, the run of durations before an end — so they are asked about the line
        // as it will be, rather than having their rules restated over the change alone.
        if (canAnchorAt(projected, span)
            && canEndAt(projected, endIndex)
            && hasEnoughColumns(projected, span)) {
            return SpanOutcome.Keep.INSTANCE;
        }

        return new SpanOutcome.Remove(Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_INVALID);
    }

    /**
     * Answers a deletion, pulling each endpoint in to the nearest survivor that can hold
     * it, or removing the hairpin when what survives is too narrow to slope across.
     *
     * @param span this hairpin's span in the pre-deletion line
     */
    private SpanOutcome.HairpinOutcome outcomeForDeletion(
        ElementChange.Deletion deletion,
        ElementRange span
    ) {
        var projected = deletion.projectedElements();

        // Nothing outside the range can fall between two elements inside it, so what
        // survives of the range is still described by its first and last position.
        @Nullable Integer first = null;
        @Nullable Integer last = null;

        for (var element : deletion.line().getElements(span)) {
            // Absent means the element is one of the doomed ones.
            var projectedIndex = deletion.projectedIndexOf(element);

            if (projectedIndex == null) {
                continue;
            }

            if (first == null) {
                first = projectedIndex;
            }

            last = projectedIndex;
        }

        if (first == null || last == null) {
            return new SpanOutcome.Remove(Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED);
        }

        var survivors = new ElementRange(first, last);
        var begin = resolveBeginIndex(survivors, projected);
        var end = resolveEndIndex(survivors, projected);

        if (begin == null || end == null || end <= begin) {
            return new SpanOutcome.Remove(Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED);
        }

        // A span the user could not have drawn leaves no gesture worth keeping. The column
        // count also rejects a surviving grace note with its host, which is one column.
        var reshaped = new ElementRange(begin, end);

        if (!hasEnoughColumns(projected, reshaped)) {
            return new SpanOutcome.Remove(Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED);
        }

        return new SpanOutcome.Reshape(reshaped);
    }

    /**
     * Returns whether the first element of {@code span} in {@code candidates} can begin a
     * hairpin over {@code span}.
     * <p>
     * A pitched note always can. A grace note belongs to the note it precedes, so it
     * can begin one when that note is pitched — anchoring on the grace note rather than
     * its host keeps the hairpin over what the user actually selected. Anything else,
     * including a grace note with no host within reach, cannot.
     * <p>
     * Both the menu's eligibility test and the post-deletion reshaping read this, so a
     * deletion can never leave a hairpin anchored somewhere the user could not have
     * placed one.
     *
     * @param candidates the elements to test against, in document order
     * @param span       the hairpin's span; its end bounds the host lookahead
     */
    public static boolean canAnchorAt(List<? extends StaffElement> candidates, ElementRange span) {
        var index = span.begin();
        var type = candidates.get(index).getType();

        return type.isPitchedNote()
            || (type.isGraceNote()
                && index < span.end()
                && candidates.get(index + 1).getType().isPitchedNote());
    }

    /**
     * Returns whether the element at {@code index} in {@code candidates} can end a
     * hairpin.
     * <p>
     * A pitched note always can. A rest can when it is the first one after a pitched
     * note, so a hairpin ends on at most one rest — a wedge running on across a second
     * has nothing left to slope over. Interior rests are unaffected; this is a rule
     * about where a hairpin stops, not what it may cross.
     * <p>
     * Both the menu's eligibility test and the post-deletion reshaping read this, so the
     * two can never disagree about what a hairpin may end on.
     *
     * @param candidates the elements to test against, in document order
     */
    public static boolean canEndAt(List<? extends StaffElement> candidates, int index) {
        var type = candidates.get(index).getType();

        if (type.isPitchedNote()) {
            return true;
        }

        // LilyPond ends a hairpin at a rest's left edge rather than past its glyph
        // (hairpin.cc:268-271), so a rest bounds a wedge as legitimately as a note —
        // but only the one that closes the run of notes.
        return type.isRest() && precedingDurationIsPitchedNote(candidates, index);
    }

    /**
     * Returns whether the nearest duration element before {@code index} is a pitched
     * note, false when there is none.
     * <p>
     * Non-durations are skipped rather than treated as separators: a grace note sits
     * between a rest and whatever precedes it without making that rest the first of its
     * run.
     */
    private static boolean precedingDurationIsPitchedNote(
        List<? extends StaffElement> candidates,
        int index
    ) {
        for (var i = index - 1; i >= 0; i--) {
            var type = candidates.get(i).getType();

            if (type.isDuration()) {
                return type.isPitchedNote();
            }
        }

        return false;
    }

    /**
     * Returns whether {@code span} holds the {@value #MIN_COLUMNS} columns a hairpin needs
     * to slope across.
     * <p>
     * The endpoint rules alone are not enough: a grace note and its host both pass
     * {@link #canAnchorAt} and {@link #canEndAt} respectively, yet occupy one column
     * between them, and a wedge cannot slope from a column to itself. The menu reads
     * this before offering to add a hairpin and {@link #outcomeFor} reads it after every
     * element change, so an edit can never leave a hairpin over a span too narrow for
     * the user to have drawn it there.
     *
     * @param candidates the elements to test against, in document order
     */
    public static boolean hasEnoughColumns(List<? extends StaffElement> candidates, ElementRange span) {
        return countColumns(candidates, span) >= MIN_COLUMNS;
    }

    /**
     * Counts what a hairpin has to slope across in {@code span}: every pitched note, plus a
     * rest at its end. Grace notes and interior rests do not count.
     */
    private static int countColumns(List<? extends StaffElement> candidates, ElementRange span) {
        var count = 0;

        for (var index = span.begin(); index <= span.end(); index++) {
            if (candidates.get(index).getType().isPitchedNote()) {
                count++;
            }
        }

        if (candidates.get(span.end()).getType().isRest()) {
            count++;
        }

        return count;
    }

    /**
     * Returns the post-deletion position of this hairpin's anchor, given that its
     * elements survive over {@code survivors}.
     * <p>
     * A surviving anchor stays where it is — a hairpin an older build left anchored on a
     * rest is not this deletion's to correct. A deleted one moves in to the first element
     * that can begin one: a pitched note, or a grace note whose host is one, matching what
     * the app allows when the hairpin is drawn.
     *
     * @return the anchor's position in {@code candidates}, or {@code null} when no survivor
     *     can begin a hairpin
     */
    private @Nullable Integer resolveBeginIndex(ElementRange survivors, List<? extends StaffElement> candidates) {
        var first = survivors.begin();
        var last = survivors.end();

        if (candidates.get(first) == getAnchorElement()) {
            return first;
        }

        for (var i = first; i <= last; i++) {
            if (canAnchorAt(candidates, new ElementRange(i, last))) {
                return i;
            }
        }

        return null;
    }

    /**
     * Returns the post-deletion position of this hairpin's end, given that its elements
     * survive over {@code survivors}. A surviving end stays where it is; a deleted one moves
     * in to the last surviving note or rest.
     *
     * @return the end's position in {@code candidates}, or {@code null} when no survivor can
     *     end a hairpin
     */
    private @Nullable Integer resolveEndIndex(ElementRange survivors, List<? extends StaffElement> candidates) {
        var first = survivors.begin();
        var last = survivors.end();

        if (candidates.get(last) == getEndElement()) {
            return last;
        }

        for (var i = last; i >= first; i--) {
            if (canEndAt(candidates, i)) {
                return i;
            }
        }

        return null;
    }

    @Override
    protected Span<StaffElement> createCopy(StaffElement newAnchor, StaffElement newEnd) {
        var copy = new Hairpin(kind, newAnchor, newEnd);
        copy.x1ShiftSs = x1ShiftSs;
        copy.x2ShiftSs = x2ShiftSs;
        return copy;
    }

    public double getX1ShiftSs() {
        return x1ShiftSs;
    }

    public void setX1ShiftSs(double x1ShiftSs) {
        this.x1ShiftSs = x1ShiftSs;
    }

    public double getX2ShiftSs() {
        return x2ShiftSs;
    }

    public void setX2ShiftSs(double x2ShiftSs) {
        this.x2ShiftSs = x2ShiftSs;
    }

    @Override
    public double intrinsicHeightSs() {
        return HAIRPIN_OPENING_HEIGHT_SS;
    }

    /**
     * Which of the two hairpins one is: a crescendo, getting louder and opening to the right
     * ({@literal <}), or a diminuendo, getting softer and opening to the left ({@literal >}).
     * <p>
     * It names the kind before there is an instance to ask, which is what both the
     * MusicXML reader (holding a wedge open while its end note is awaited) and the
     * hairpin menu (resolving what one item would do) need. Where an instance is in
     * hand, ask it: {@link Hairpin#getKind()}.
     */
    public enum Kind {
        CRESCENDO,
        DIMINUENDO;

        /** The other kind — the one this may share an endpoint with. */
        public Kind opposite() {
            return this == CRESCENDO ? DIMINUENDO : CRESCENDO;
        }
    }
}
