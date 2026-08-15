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

/**
 * Base class for hairpin dynamic markings (crescendo and diminuendo).
 * <p>
 * A hairpin is a wedge-shaped marking placed above the staff that indicates
 * a gradual change in volume. The user can adjust the horizontal endpoints
 * and vertical position.
 */
public abstract sealed class Hairpin extends Span
        permits Crescendo, Diminuendo {

    /**
     * Height of the hairpin opening in staff-space units. LilyPond's {@code Hairpin.height}
     * grob property ({@code scm/define-grobs.scm}) is {@code 0.6666}, a <em>half</em>-height:
     * {@code Hairpin::print} ({@code lily/hairpin.cc:335-336}) draws the two wedge segments at
     * {@code Offset(x, starth)} and {@code Offset(x, -starth)} where {@code starth == height},
     * so the full mouth opening is twice the property value.
     */
    public static final double HAIRPIN_OPENING_HEIGHT_SS = 1.3332;

    /**
     * Minimum drawn length of a hairpin in staff spaces, from LilyPond's
     * {@code Hairpin.minimum-length} (scm/define-grobs.scm). LilyPond applies this
     * as a spacing rod via {@code ly:spanner::set-spacing-rods}, so the notes are
     * pushed apart rather than the wedge being squashed.
     */
    public static final double MINIMUM_LENGTH_SS = 2.0;

    /**
     * The gap a hairpin endpoint keeps from an adjacent text dynamic or barline,
     * from LilyPond's {@code Hairpin.bound-padding} (scm/define-grobs.scm).
     */
    public static final double BOUND_PADDING_SS = 1.0;

    /**
     * The gap each of two back-to-back hairpins keeps from their shared column
     * center. {@code Hairpin::print} uses {@code padding / 3} for this case
     * ("we're not adjacent to a text-dynamic, and we may move closer"), so it is
     * derived from {@link #BOUND_PADDING_SS} rather than written as a literal.
     */
    public static final double BACK_TO_BACK_PADDING_SS = BOUND_PADDING_SS / 3;

    /**
     * The number of columns a hairpin needs to slope across. A pitched note is one
     * column; so is a rest closing the span, which bounds a wedge in its own right.
     * A grace note is not — it shares its host's column — so a grace note and its
     * host are two elements but a single column.
     */
    public static final int MIN_COLUMNS = 2;

    /** User-controlled left-endpoint horizontal shift in staff-space units. */
    private double x1ShiftSs;

    /** User-controlled right-endpoint horizontal shift in staff-space units. */
    private double x2ShiftSs;

    /** User-controlled vertical shift in staff-space units. */
    private double yShiftSs;

    /**
     * Creates a hairpin spanning from anchor to end element.
     *
     * @param anchorElement The starting element of the hairpin
     * @param endElement    The ending element of the hairpin
     */
    protected Hairpin(StaffElement anchorElement, StaffElement endElement) {
        super(anchorElement, endElement);
    }

    /**
     * Creates a new instance of the concrete hairpin subtype (Crescendo or Diminuendo),
     * anchored to the given elements. Called by {@link #createCopy}, which then carries
     * over the Hairpin fields shared by both subtypes in one place.
     */
    protected abstract Hairpin createHairpin(StaffElement newAnchor, StaffElement newEnd);

    /**
     * Which of the two hairpins this is. The single answer to that question — use it
     * rather than {@code instanceof} or a {@code getClass()} comparison, so a caller
     * that only needs the type never has to know the class hierarchy.
     */
    public abstract Kind getKind();

    /**
     * Decides what happens to this hairpin when {@code change} lands, judged entirely
     * against {@link ElementChange#projectedElements} — the line as it will be.
     * <p>
     * An insertion or a replacement removes no element, so there is nothing to pull an
     * endpoint in to: both only ask whether this hairpin's two endpoints are still
     * endpoints the user could have placed, and answer {@link SpanOutcome.Simple#REMOVE}
     * or {@link SpanOutcome.Simple#KEEP}. Under {@link ElementChange.Replacement} an
     * endpoint that is the replaced element is read as having <em>become</em> the
     * replacement, since {@link Line#setElement} re-points the span rather than dropping it.
     * <p>
     * A deletion can pull an endpoint in, so it answers
     * {@link SpanOutcome.Reshape} — <b>even when the hairpin's span is unchanged</b>,
     * rather than {@link SpanOutcome.Simple#KEEP}. This is the one place a hairpin departs
     * from the "{@code KEEP} means untouched" reading: the caller's merge pass needs every
     * surviving hairpin's projected span, not only the moved ones, and a run of one whose
     * endpoints both survive already emits no mutation.
     *
     * @param change the pending change, not yet applied to {@code line}
     * @param line   the line being changed, still in its pre-change state
     */
    @Override
    public SpanOutcome outcomeFor(ElementChange change, Line line) {
        var anchorIndex = getAnchorElementIndex();
        var endIndex = getEndElementIndex();

        // A hairpin whose endpoints are not both in this line has nothing here to judge.
        if (anchorIndex < 0 || endIndex < 0) {
            return SpanOutcome.Simple.KEEP;
        }

        if (change instanceof ElementChange.Deletion deletion) {
            return outcomeForDeletion(deletion, line, anchorIndex, endIndex);
        }

        // Every other shape removes no element, so none of them can pull an endpoint in and
        // all are answered alike — one call rather than an arm apiece saying the same thing.
        // The exhaustive switch in Span.outcomeFor is what forces a new shape to be
        // considered here too.
        //
        // The endpoints are read out of the line rather than off the span: both indices
        // are known good here, which the nullable accessors cannot express.
        return outcomeForRepointing(change, line.getElement(anchorIndex), line.getElement(endIndex));
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
    private SpanOutcome outcomeForRepointing(ElementChange change, StaffElement anchor, StaffElement end) {
        var projected = change.projectedElements();
        var anchorIndex = projectedEndpointIndexOf(change, anchor);
        var endIndex = projectedEndpointIndexOf(change, end);

        if (anchorIndex < 0 || endIndex < 0) {
            return SpanOutcome.Simple.REMOVE;
        }

        // Both predicates read the neighbours of the index they are given — the host after
        // an anchor, the run of durations before an end — so they are asked about the line
        // as it will be, rather than having their rules restated over the change alone.
        if (canAnchorAt(projected, anchorIndex, endIndex)
            && canEndAt(projected, endIndex)
            && hasEnoughColumns(projected, anchorIndex, endIndex)) {
            return SpanOutcome.Simple.KEEP;
        }

        return SpanOutcome.Simple.REMOVE;
    }

    /**
     * Returns the projected position of {@code endpoint}, following the replacement
     * re-pointing rule: a replaced endpoint becomes the replacement rather than vanishing.
     */
    private static int projectedEndpointIndexOf(ElementChange change, StaffElement endpoint) {
        if (change instanceof ElementChange.Replacement replacement && endpoint == replacement.oldElement()) {
            return change.projectedIndexOf(replacement.newElement());
        }

        return change.projectedIndexOf(endpoint);
    }

    /**
     * Answers a deletion, pulling each endpoint in to the nearest survivor that can hold
     * it, or removing the hairpin when what survives is too narrow to slope across.
     *
     * @param anchorIndex this hairpin's anchor index in the pre-deletion line
     * @param endIndex    this hairpin's end index in the pre-deletion line
     */
    private SpanOutcome outcomeForDeletion(
        ElementChange.Deletion deletion,
        Line line,
        int anchorIndex,
        int endIndex
    ) {
        var elements = line.getElements();
        var projected = deletion.projectedElements();

        // Nothing outside the range can fall between two elements inside it, so what
        // survives of the range is still described by its first and last position.
        var first = -1;
        var last = -1;

        for (var i = anchorIndex; i <= endIndex; i++) {
            // Absent means the element is one of the doomed ones.
            var projectedIndex = deletion.projectedIndexOf(elements.get(i));

            if (projectedIndex < 0) {
                continue;
            }

            if (first < 0) {
                first = projectedIndex;
            }

            last = projectedIndex;
        }

        if (first < 0) {
            return SpanOutcome.Simple.REMOVE;
        }

        var begin = resolveBeginIndex(first, last, projected);
        var end = resolveEndIndex(first, last, projected);

        // A span the user could not have drawn leaves no gesture worth keeping. The
        // column count subsumes begin >= end: a single element is at most one column,
        // and so is a surviving grace note with its host.
        if (begin < 0 || end < 0 || !hasEnoughColumns(projected, begin, end)) {
            return SpanOutcome.Simple.REMOVE;
        }

        return new SpanOutcome.Reshape(begin, end);
    }

    /**
     * Returns whether the element at {@code index} in {@code candidates} can begin a
     * hairpin.
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
     * @param lastIndex  the last usable index, bounding the host lookahead
     */
    public static boolean canAnchorAt(List<? extends StaffElement> candidates, int index, int lastIndex) {
        var type = candidates.get(index).getType();

        return type.isPitchedNote()
            || (type.isGraceNote()
                && index < lastIndex
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
     * Returns whether {@code [begin, end]} holds the {@value #MIN_COLUMNS} columns a
     * hairpin needs to slope across.
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
    public static boolean hasEnoughColumns(List<? extends StaffElement> candidates, int begin, int end) {
        return countColumns(candidates, begin, end) >= MIN_COLUMNS;
    }

    /**
     * Counts what a hairpin has to slope across in the inclusive range
     * {@code [begin, end]}: every pitched note, plus a rest at {@code end}. Grace notes
     * and interior rests do not count.
     */
    private static int countColumns(List<? extends StaffElement> candidates, int begin, int end) {
        var count = 0;

        for (var index = begin; index <= end; index++) {
            if (candidates.get(index).getType().isPitchedNote()) {
                count++;
            }
        }

        if (candidates.get(end).getType().isRest()) {
            count++;
        }

        return count;
    }

    /**
     * Returns the post-deletion position of this hairpin's anchor, given that its
     * elements survive from {@code first} through {@code last}, or -1 when none of them
     * can begin a hairpin.
     * <p>
     * A surviving anchor stays where it is — a hairpin an older build left anchored on a
     * rest is not this deletion's to correct. A deleted one moves in to the first element
     * that can begin one: a pitched note, or a grace note whose host is one, matching what
     * the app allows when the hairpin is drawn.
     */
    private int resolveBeginIndex(int first, int last, List<? extends StaffElement> candidates) {
        if (candidates.get(first) == getAnchorElement()) {
            return first;
        }

        for (var i = first; i <= last; i++) {
            if (canAnchorAt(candidates, i, last)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns the post-deletion position of this hairpin's end, given that its elements
     * survive from {@code first} through {@code last}, or -1 when none of them can end a
     * hairpin. A surviving end stays where it is; a deleted one moves in to the last
     * surviving note or rest.
     */
    private int resolveEndIndex(int first, int last, List<? extends StaffElement> candidates) {
        if (candidates.get(last) == getEndElement()) {
            return last;
        }

        for (var i = last; i >= first; i--) {
            if (canEndAt(candidates, i)) {
                return i;
            }
        }

        return -1;
    }

    @Override
    protected final Span createCopy(StaffElement newAnchor, StaffElement newEnd) {
        var copy = createHairpin(newAnchor, newEnd);
        copy.x1ShiftSs = x1ShiftSs;
        copy.x2ShiftSs = x2ShiftSs;
        copy.yShiftSs = yShiftSs;
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

    public double getYShiftSs() {
        return yShiftSs;
    }

    public void setYShiftSs(double yShiftSs) {
        this.yShiftSs = yShiftSs;
    }

    /**
     * Returns the height of the hairpin opening in staff-space units.
     */
    @Override
    public double getContentHeightSs() {
        return HAIRPIN_OPENING_HEIGHT_SS;
    }

    /**
     * Returns the horizontal span width for collision detection. The span runs past the end
     * element's head, so it is measured with that element's own width
     * ({@link #getEndElementWidthSs()}).
     * <p>
     * No longer used to place a hairpin — {@code HairpinEndpoints} resolves both tips from the
     * neighbouring columns instead — but {@link Span#getSpanWidthSs} is abstract, so every span
     * must answer it.
     *
     * @param anchorXSs X position of the anchor element in staff-space units
     * @param endXSs    X position of the end element in staff-space units
     * @return span width in staff-space units
     */
    @Override
    public double getSpanWidthSs(double anchorXSs, double endXSs) {
        return Math.max(HAIRPIN_OPENING_HEIGHT_SS, endXSs - anchorXSs + getEndElementWidthSs());
    }

    @Override
    public String toIndexString() {
        var base = getAnchorElementIndex() + "," + getEndElementIndex();
        if (x1ShiftSs != 0 || x2ShiftSs != 0 || yShiftSs != 0) {
            return base + ',' + x1ShiftSs + ',' + x2ShiftSs + ',' + yShiftSs + ';';
        }
        return base + ';';
    }

    /**
     * Which of the two hairpins a crescendo or diminuendo is — a plain two-value tag
     * rather than a {@code Class} token, since a hairpin is always constructed
     * directly ({@code new Crescendo}/{@code new Diminuendo}), never reflectively.
     * <p>
     * It names the type before there is an instance to ask, which is what both the
     * MusicXML reader (holding a wedge open while its end note is awaited) and the
     * hairpin menu (resolving what one item would do) need. Where an instance is in
     * hand, ask it: {@link Hairpin#getKind()}.
     */
    public enum Kind {
        CRESCENDO(Crescendo.class),
        DIMINUENDO(Diminuendo.class);

        private final Class<? extends Hairpin> spanType;

        Kind(Class<? extends Hairpin> spanType) {
            this.spanType = spanType;
        }

        /** The concrete subclass this kind names, for {@code SpanLookup} queries. */
        public Class<? extends Hairpin> spanType() {
            return spanType;
        }

        /** The other kind — the one this may share an endpoint with. */
        public Kind opposite() {
            return this == CRESCENDO ? DIMINUENDO : CRESCENDO;
        }
    }
}
