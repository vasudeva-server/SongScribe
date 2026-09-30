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
 * <p>
 * Every attached hairpin meets every invariant below. The rules for where a hairpin may start,
 * end and reach are in {@code docs/hairpin-editing.md}. What keeps each rule on every path that
 * puts a hairpin on a line:
 * <ul>
 *   <li>{@link #spanning} judges every new hairpin against all five rules, and it and
 *       {@link #createCopy} are the only ways to make one. The editor
 *       ({@code MusicEditOperations.addHairpinToSelection}), the paste
 *       ({@code ScoreViewController.tryInsertFragment}) and the load ({@code HairpinLoadPass},
 *       which both file readers reach) each add only a hairpin that {@link #spanning}
 *       returned.</li>
 *   <li>{@link #outcomeFor} judges every element change. An insertion or a replacement removes
 *       a hairpin that the change leaves breaking rule 1, 2, 3 or 4. A deletion pulls a lost
 *       endpoint in to the nearest element that meets rule 1 or 2, and removes a hairpin left
 *       too narrow for rule 3; it cannot bring a bar inside a range, so rule 4 holds. No
 *       insertion or replacement changes which elements two hairpins share, and a deletion only
 *       takes shared elements away, so a change never breaks rule 5 for a hairpin it keeps.</li>
 *   <li>The merge that joins the same-kind hairpins a deletion leaves adjacent
 *       ({@link LineSpans}) keeps all five rules. It keeps rules 1 to 4 without asking
 *       {@link #spanning}: its anchor and end are endpoints of hairpins the deletion reshaped,
 *       which meet rules 1 and 2; a joined range holds at least the columns of its parts, so
 *       rule 3 holds; and it joins only hairpins with no element between them, while a deletion
 *       cannot put a bar inside a range, so rule 4 holds. It keeps rule 5 by ending a run before
 *       a hairpin whose joining would share more than one element with a surviving hairpin of
 *       the opposite kind, judged by {@link Span#overlappingBeyondEndpoint}, the test
 *       {@link #spanning} applies.</li>
 *   <li>Undo and redo put back only a hairpin one of these paths made, against the line it was
 *       judged on.</li>
 * </ul>
 *
 * @invariant it starts on a pitched note, or on a grace note whose host is a pitched note
 * @invariant it ends on a pitched note, never on a rest
 * @invariant it holds at least {@value #MIN_COLUMNS} columns; a grace note and its host are one
 *            column, and a rest is not a column
 * @invariant its range holds no repeat and no bar other than {@link ElementType#SINGLE_BARLINE}
 * @invariant it shares at most one element with a hairpin of the opposite {@link Kind}, where
 *            one ends and the other starts
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
     * column. A grace note is not — it shares its host's column — so a grace note and its
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
     * Creates a hairpin of {@code kind} over two elements the caller has already checked span a
     * hairpin the class invariant allows.
     *
     * @param kind          Whether the hairpin is a crescendo or a diminuendo
     * @param anchorElement The starting element of the hairpin
     * @param endElement    The ending element of the hairpin
     */
    private Hairpin(Kind kind, StaffElement anchorElement, StaffElement endElement) {
        super(StaffElement.class, anchorElement, endElement);
        this.kind = kind;
    }

    /**
     * Returns a hairpin of {@code kind} over {@code span} of {@code line} when the class
     * invariant allows one there, or the reason it does not. With {@link #createCopy}, this is
     * the one way to make a hairpin: the editor, the paste and the load all ask it, so a range
     * holds a hairpin by the same rule whichever route it arrives by. It is the counterpart of
     * {@link Ending#spanning}.
     * <p>
     * It judges {@code converge(line, kind, span).range()} ({@link #converge}), not
     * {@code span}: adding the hairpin absorbs the same-kind hairpins in reach, and the range
     * that results is the one the line will hold. Absorption can reach an opposite-kind hairpin
     * that {@code span} alone does not. A crescendo on {@code [0, 3]}, a diminuendo on
     * {@code [3, 4]} and a new crescendo on {@code [4, 6]}: the new one absorbs {@code [0, 3]},
     * becomes {@code [0, 6]}, and covers the whole diminuendo.
     * <p>
     * An absorbed hairpin already meets rules 1 and 2 of the class invariant, so when the
     * converged range fails either, the failing endpoint is one {@code span} supplied. Rule 4
     * is judged over {@code span} alone: convergence absorbs only a hairpin that overlaps the
     * range or sits directly next to it, so the converged range is {@code span} and the
     * absorbed hairpins with no element between them, and an absorbed hairpin holds no
     * structural bar.
     * <p>
     * The hairpin is built, not added: {@code line}'s span registry is unchanged.
     *
     * @param line the line the hairpin would be on
     * @param kind whether the hairpin would be a crescendo or a diminuendo
     * @param span the range the hairpin would cover; both of its indices are indices of
     *     {@code line}
     * @return {@link Spanning.Spanned} with a new hairpin of {@code kind} anchored on the element
     *     at {@code span.begin()} and ending on the element at {@code span.end()}, with zero
     *     shifts, which {@link Span#addTo} widens to the converged range; or
     *     {@link Spanning.Refused}, judged over the converged range, with
     *     {@link Spanning.Cause#RANGE} and
     *     <ul>
     *       <li>{@link Strings#DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE} when the anchor
     *           fails {@link #canAnchorAt} or the end fails {@link #canEndAt};</li>
     *       <li>{@link Strings#DIALOG_REPORT_REASON_HAIRPIN_TOO_FEW_COLUMNS} when it fails
     *           {@link #hasEnoughColumns};</li>
     *       <li>{@link Strings#DIALOG_REPORT_REASON_HAIRPIN_STRUCTURAL_BOUNDARY} when
     *           {@code span} holds a repeat or a bar other than a single barline;</li>
     *     </ul>
     *     or with {@link Spanning.Cause#OTHER_SPAN} and
     *     {@link Strings#DIALOG_REPORT_REASON_HAIRPIN_OPPOSITE_OVERLAP} when the range shares
     *     more than one element with a hairpin of the opposite kind
     *     ({@link Span#overlappingBeyondEndpoint}). Every {@link Spanning.Cause#RANGE} refusal
     *     is judged before the {@link Spanning.Cause#OTHER_SPAN} one.
     */
    public static Spanning<Hairpin> spanning(Line line, Kind kind, ElementRange span) {
        var elements = line.getElements();
        var range = converge(line, kind, span).range();

        if (!canAnchorAt(elements, range) || !canEndAt(elements, range.end())) {
            return new Spanning.Refused<>(Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE, Spanning.Cause.RANGE);
        }

        if (!hasEnoughColumns(elements, range)) {
            return new Spanning.Refused<>(Strings.DIALOG_REPORT_REASON_HAIRPIN_TOO_FEW_COLUMNS, Spanning.Cause.RANGE);
        }

        if (line.spansStructuralBoundary(span)) {
            return new Spanning.Refused<>(
                Strings.DIALOG_REPORT_REASON_HAIRPIN_STRUCTURAL_BOUNDARY, Spanning.Cause.RANGE);
        }

        if (line.spanRegistry().hasHairpin(kind.opposite(), Span.overlappingBeyondEndpoint(range))) {
            return new Spanning.Refused<>(
                Strings.DIALOG_REPORT_REASON_HAIRPIN_OPPOSITE_OVERLAP, Spanning.Cause.OTHER_SPAN);
        }

        return new Spanning.Spanned<>(new Hairpin(kind, elements.get(span.begin()), elements.get(span.end())));
    }

    /**
     * Returns the range a hairpin of {@code kind} over {@code span} of {@code line} holds once
     * {@link Span#addTo} has applied {@link Displacement#ABSORB_ADJACENT}, and the hairpins it
     * absorbs on the way. Nothing on the line changes.
     * <p>
     * The range widens over every hairpin of {@code kind} that overlaps it or sits within
     * {@link LineSpans#SPAN_ADJACENCY_REACH} of either end, and repeats until the range stops
     * growing, since absorbing one hairpin can bring a further one into reach. The displacement
     * that {@link Span#addTo} applies computes the same widening, so the range this answers is
     * the range adding the hairpin produces.
     *
     * @param line the line the hairpin would be on
     * @param kind whether the hairpin would be a crescendo or a diminuendo
     * @param span the range the hairpin would cover; both of its indices are indices of
     *     {@code line}
     * @return the converged range, which holds {@code span}, and every hairpin of {@code kind}
     *     inside it; the range is {@code span} and the list is empty when no hairpin of
     *     {@code kind} is in reach
     */
    public static Convergence converge(Line line, Kind kind, ElementRange span) {
        var elements = line.getElements();

        // A hairpin not added to the line, asked of the registry exactly as the one addTo
        // displaces for, so the reach and the same-kind test are the displacement's own.
        var probe = new Hairpin(kind, elements.get(span.begin()), elements.get(span.end()));
        var absorption = line.spanRegistry().absorption(probe, LineSpans.SPAN_ADJACENCY_REACH);
        var absorbed = absorption.absorbed().stream()
            .map(Hairpin.class::cast)
            .toList();

        return new Convergence(absorption.range(), absorbed);
    }

    /**
     * What adding a hairpin over a range makes of it: the range it holds once it has absorbed
     * the same-kind hairpins in reach, and those hairpins. {@link #converge} answers it.
     *
     * @param range    the converged range, which holds the requested one
     * @param absorbed every hairpin the widening took in; empty when nothing was in reach
     */
    public record Convergence(ElementRange range, List<Hairpin> absorbed) {
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
     * endpoint in to: both only ask whether this hairpin still meets rules 1 to 4 of the
     * class invariant in the projection, and answer {@link SpanOutcome.Remove}
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
     *     a {@link SpanOutcome.Remove}. A {@code Remove} gives the reason
     *     {@link Strings#DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_INVALID} when the change leaves
     *     the anchor or the end breaking rule 1 or 2,
     *     {@link Strings#DIALOG_REPORT_REASON_HAIRPIN_TOO_FEW_COLUMNS} for rule 3,
     *     {@link Strings#DIALOG_REPORT_REASON_HAIRPIN_STRUCTURAL_BOUNDARY} for rule 4, and
     *     {@link Strings#DIALOG_REPORT_REASON_ENDPOINT_DELETED} for a deletion that leaves no
     *     element able to anchor or end it, or fewer than {@value #MIN_COLUMNS} columns
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
     * whether what lies between them is still wide enough to slope across — a
     * replacement can turn a column into a grace note that shares the next one — and whether
     * the range now holds a repeat or a bar other than a single barline, which an insertion or
     * a replacement can put there.
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

        // The anchor predicate reads the host after an anchor, so each is asked about the line
        // as it will be, rather than having its rule restated over the change alone.
        if (!canAnchorAt(projected, span) || !canEndAt(projected, endIndex)) {
            return new SpanOutcome.Remove(Strings.DIALOG_REPORT_REASON_HAIRPIN_ENDPOINT_INVALID);
        }

        if (!hasEnoughColumns(projected, span)) {
            return new SpanOutcome.Remove(Strings.DIALOG_REPORT_REASON_HAIRPIN_TOO_FEW_COLUMNS);
        }

        if (StaffElementRun.spansStructuralBoundary(projected, span)) {
            return new SpanOutcome.Remove(Strings.DIALOG_REPORT_REASON_HAIRPIN_STRUCTURAL_BOUNDARY);
        }

        return SpanOutcome.Keep.INSTANCE;
    }

    /**
     * Answers a deletion, pulling each endpoint in to the nearest survivor that can hold
     * it, or removing the hairpin when what survives is too narrow to slope across.
     * <p>
     * A deletion never judges the structural-boundary rule: it removes elements and adds none,
     * so it cannot put a repeat or a bar inside a range that held none.
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
     * hairpin over {@code span}: rule 1 of the class invariant.
     * <p>
     * A pitched note always can. A grace note belongs to the note it precedes, so it
     * can begin one when that note is pitched — anchoring on the grace note rather than
     * its host keeps the hairpin over what the user actually selected. Anything else,
     * including a grace note with no host within reach, cannot.
     * <p>
     * {@link #spanning}, {@link #outcomeFor} and the deletion reshape all read this, so no path
     * that makes or keeps a hairpin can anchor it somewhere a new one could not start.
     *
     * @param candidates the elements to test against, in document order
     * @param span       the hairpin's span in {@code candidates}; its end bounds the host lookahead
     * @return {@code true} when the element at {@code span.begin()} is a pitched note, or a grace
     *     note followed, within {@code span}, by a pitched note
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
     * hairpin: rule 2 of the class invariant. Only a pitched note can; a rest, a grace note
     * and every other element cannot.
     * <p>
     * {@link #spanning}, {@link #outcomeFor} and the deletion reshape all read this, so no path
     * that makes or keeps a hairpin can end it somewhere a new one could not end.
     *
     * @param candidates the elements to test against, in document order
     * @param index      the index in {@code candidates} of the element to test
     * @return {@code true} when the element at {@code index} is a pitched note
     */
    public static boolean canEndAt(List<? extends StaffElement> candidates, int index) {
        return candidates.get(index).getType().isPitchedNote();
    }

    /**
     * Returns whether {@code span} holds the {@value #MIN_COLUMNS} columns a hairpin needs
     * to slope across: rule 3 of the class invariant.
     * <p>
     * The endpoint rules alone are not enough: a grace note and its host both pass
     * {@link #canAnchorAt} and {@link #canEndAt} respectively, yet occupy one column
     * between them, and a wedge cannot slope from a column to itself. {@link #spanning},
     * {@link #outcomeFor} and the deletion reshape all read this, so no path that makes or
     * keeps a hairpin can leave it over a span too narrow for a new one.
     *
     * @param candidates the elements to test against, in document order
     * @param span       the hairpin's span in {@code candidates}
     * @return {@code true} when {@code span} holds at least {@value #MIN_COLUMNS} pitched notes;
     *     grace notes and rests do not count
     */
    public static boolean hasEnoughColumns(List<? extends StaffElement> candidates, ElementRange span) {
        return countColumns(candidates, span) >= MIN_COLUMNS;
    }

    /**
     * Counts what a hairpin has to slope across in {@code span}: every pitched note. Grace notes
     * and rests do not count.
     */
    private static int countColumns(List<? extends StaffElement> candidates, ElementRange span) {
        var count = 0;

        for (var index = span.begin(); index <= span.end(); index++) {
            if (candidates.get(index).getType().isPitchedNote()) {
                count++;
            }
        }

        return count;
    }

    /**
     * Returns the post-deletion position of this hairpin's anchor, given that its
     * elements survive over {@code survivors}.
     * <p>
     * A surviving anchor stays where it is. A deleted one moves in to the first element
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
     * in to the last surviving pitched note, so a deletion never pulls an end onto a rest.
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
        return new Hairpin(kind, newAnchor, newEnd);
    }

    @Override
    protected void copyOwnStateFrom(Span<StaffElement> source) {
        var hairpin = (Hairpin) source;
        x1ShiftSs = hairpin.x1ShiftSs;
        x2ShiftSs = hairpin.x2ShiftSs;
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

        /**
         * The other kind — the one this may share an endpoint with.
         *
         * @return {@link #DIMINUENDO} for {@link #CRESCENDO}, and {@link #CRESCENDO} for
         *     {@link #DIMINUENDO}
         */
        public Kind opposite() {
            return this == CRESCENDO ? DIMINUENDO : CRESCENDO;
        }
    }
}
