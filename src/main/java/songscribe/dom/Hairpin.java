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
 * A hairpin has one horizontal and one vertical user offset, each moving the whole hairpin.
 * <p>
 * Every attached hairpin meets every invariant below. {@link #spanning} and {@link #createCopy}
 * are the only ways to make one. See {@code docs/hairpin-editing.md} and
 * {@code docs/span-invalidation.md}.
 *
 * @invariant it starts on a pitched note, or on a grace note whose host is a pitched note
 * @invariant it ends on a pitched note, never on a rest
 * @invariant it holds at least {@value Span#MIN_PITCHED_NOTES} pitched notes, as
 *            {@link Span#holdsEnoughPitchedNotes} counts them: a grace note and a rest do not
 *            count, so a grace note and its host count once; a wedge cannot slope from a note
 *            to itself
 * @invariant its range holds no repeat and no bar other than {@link ElementType#SINGLE_BARLINE}
 * @invariant it shares at most one element with a hairpin of the opposite {@link Kind}, where
 *            one ends and the other starts
 */
public final class Hairpin extends Span<StaffElement> implements IntrinsicHeight {

    /**
     * Twice LilyPond's {@code Hairpin.height} ({@code 0.6666}), which is a <em>half</em>-height.
     */
    public static final double HAIRPIN_OPENING_HEIGHT_SS = 1.3332;

    private static final String REPORT_HEADING_KEY = Strings.REPORT_HAIRPINS_REMOVED;

    private final Kind kind;

    private Hairpin(Kind kind, StaffElement anchorElement, StaffElement endElement) {
        super(StaffElement.class, anchorElement, endElement);
        this.kind = kind;
    }

    /**
     * Returns a hairpin of {@code kind} over {@code range} of {@code line} when the class
     * invariant allows one there, or the reason it does not. With {@link #createCopy}, this is
     * the one way to make a hairpin: the editor, the paste and the load all ask it, so a range
     * holds a hairpin by the same rule whichever route it arrives by. It is the counterpart of
     * {@link Ending#spanning}.
     * <p>
     * It judges {@code converge(line, kind, range).range()} ({@link #converge}), not
     * {@code range}: adding the hairpin absorbs the same-kind hairpins in reach, and the range
     * that results is the one the line will hold. Absorption can reach an opposite-kind hairpin
     * that {@code range} alone does not. A crescendo on {@code [0, 3]}, a diminuendo on
     * {@code [3, 4]} and a new crescendo on {@code [4, 6]}: the new one absorbs {@code [0, 3]},
     * becomes {@code [0, 6]}, and covers the whole diminuendo.
     * <p>
     * An absorbed hairpin already meets rules 1 and 2 of the class invariant, so when the
     * converged range fails either, the failing endpoint is one {@code range} supplied. Rule 4
     * is judged over {@code range} alone: convergence absorbs only a hairpin that overlaps the
     * range or sits directly next to it, so the converged range is {@code range} and the
     * absorbed hairpins with no element between them, and an absorbed hairpin holds no bar
     * other than a single barline.
     * <p>
     * The hairpin is built over the converged range, so it meets the class invariant as built.
     * It is not added: {@code line}'s span registry is unchanged. Added before any other span
     * joins the line, {@link Span#addTo} removes the absorbed hairpins and moves neither
     * endpoint, since the converged range already holds everything in reach.
     *
     * @param line the line the hairpin would be on
     * @param kind whether the hairpin would be a crescendo or a diminuendo
     * @param range the range the hairpin would cover; both of its indices are indices of
     *     {@code line}
     * @return {@link Spanning.Spanned} with a new hairpin of {@code kind} over the converged
     *     range, anchored on its first element and ending on its last, and with every hairpin of
     *     {@code kind} the converged range absorbs, empty when it absorbs none; or
     *     {@link Spanning.Refused}, judged over the converged range, with
     *     {@link Spanning.Cause#RANGE} and
     *     <ul>
     *       <li>{@link Strings#REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE} when the anchor
     *           fails {@link #canAnchorAt} or the end fails {@link #canEndAt};</li>
     *       <li>{@link Strings#REPORT_REASON_HAIRPIN_TOO_FEW_NOTES} when it fails
     *           {@link Span#holdsEnoughPitchedNotes};</li>
     *       <li>{@link Strings#REPORT_REASON_HAIRPIN_NON_SINGLE_BAR} when
     *           {@code range} fails {@link #holdsNonSingleBar};</li>
     *     </ul>
     *     or with {@link Spanning.Cause#OTHER_SPAN} and
     *     {@link Strings#REPORT_REASON_HAIRPIN_OPPOSITE_OVERLAP} when the range shares
     *     more than one element with a hairpin of the opposite kind
     *     ({@link Span#overlappingBeyondEndpoint}). Every {@link Spanning.Cause#RANGE} refusal
     *     is judged before the {@link Spanning.Cause#OTHER_SPAN} one. Every refusal carries
     *     {@link Strings#REPORT_HAIRPINS_REMOVED} as its heading.
     */
    public static Spanning<Hairpin> spanning(Line line, Kind kind, ElementRange range) {
        var elements = line.getElements();
        var convergence = converge(line, kind, range);
        var convergedRange = convergence.range();

        if (!canAnchorAt(elements, convergedRange) || !canEndAt(elements, convergedRange.end())) {
            return refused(Strings.REPORT_REASON_HAIRPIN_ENDPOINT_UNUSABLE, Spanning.Cause.RANGE);
        }

        if (!holdsEnoughPitchedNotes(elements, convergedRange)) {
            return refused(Strings.REPORT_REASON_HAIRPIN_TOO_FEW_NOTES, Spanning.Cause.RANGE);
        }

        if (holdsNonSingleBar(elements, range)) {
            return refused(Strings.REPORT_REASON_HAIRPIN_NON_SINGLE_BAR, Spanning.Cause.RANGE);
        }

        if (line.spanRegistry().hasHairpin(kind.opposite(), Span.overlappingBeyondEndpoint(convergedRange))) {
            return refused(Strings.REPORT_REASON_HAIRPIN_OPPOSITE_OVERLAP, Spanning.Cause.OTHER_SPAN);
        }

        var hairpin = new Hairpin(kind, elements.get(convergedRange.begin()), elements.get(convergedRange.end()));

        return new Spanning.Spanned<>(hairpin, convergence.absorbed());
    }

    private static Spanning<Hairpin> refused(String reasonKey, Spanning.Cause cause) {
        return new Spanning.Refused<>(reasonKey, cause, REPORT_HEADING_KEY);
    }

    /**
     * Must agree with the widening the displacement in {@link Span#addTo} applies, so the range
     * this answers is the range adding the hairpin produces.
     */
    private static Convergence converge(Line line, Kind kind, ElementRange span) {
        // The reach and the same-kind test are the ones the displacement applies, so the range
        // answered here is the one adding the hairpin produces.
        var absorption = line.spanRegistry().absorption(span, kind::matches, LineSpans.SPAN_ADJACENCY_REACH);
        return new Convergence(absorption.range(), absorption.absorbedAs(Hairpin.class));
    }

    private record Convergence(ElementRange range, List<Hairpin> absorbed) {
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * A hairpin drawn flush against a same-kind one continues it rather than starting a second.
     */
    @Override
    protected Displacement displacement() {
        return Displacement.ABSORB_ADJACENT;
    }

    /**
     * Narrowed to the same {@link Kind}, so a crescendo never displaces a diminuendo.
     */
    @Override
    boolean isSameKindAs(Span<?> other) {
        return super.isSameKindAs(other) && kind.matches(other);
    }

    @Override
    protected String reportHeadingKey() {
        return REPORT_HEADING_KEY;
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
     * <p>
     * A deletion keeps a surviving anchor only while it can still begin the hairpin
     * ({@link #canAnchorAt}). One that cannot, such as a grace note whose host the deletion
     * takes, is passed over like a deleted anchor.
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return {@link SpanOutcome.Keep#INSTANCE} or a {@link SpanOutcome.Remove} for a
     *     change that removes no element; for a deletion this hairpin survives, a
     *     {@link SpanOutcome.Reshape} naming this hairpin and its projected span, and otherwise
     *     a {@link SpanOutcome.Remove}. A {@code Remove} gives the reason
     *     {@link Strings#REPORT_REASON_HAIRPIN_ENDPOINT_INVALID} when the change leaves
     *     the anchor or the end breaking rule 1 or 2,
     *     {@link Strings#REPORT_REASON_HAIRPIN_TOO_FEW_NOTES} for rule 3,
     *     {@link Strings#REPORT_REASON_HAIRPIN_NON_SINGLE_BAR} for rule 4, and
     *     {@link Strings#REPORT_REASON_ENDPOINT_DELETED} for a deletion that leaves no
     *     element able to anchor or end it, or leaves fewer than
     *     {@value Span#MIN_PITCHED_NOTES} pitched notes
     */
    @Override
    public SpanOutcome outcomeFor(ElementChange change) {
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

    private SpanOutcome outcomeForRepointing(ElementChange change, StaffElement anchor, StaffElement end) {
        var projected = change.projectedElements();
        var anchorIndex = change.projectedEndpointIndexOf(anchor);
        var endIndex = change.projectedEndpointIndexOf(end);

        if (anchorIndex == null || endIndex == null || endIndex <= anchorIndex) {
            return new SpanOutcome.Remove(Strings.REPORT_REASON_HAIRPIN_ENDPOINT_INVALID);
        }

        var span = new ElementRange(anchorIndex, endIndex);

        // The anchor predicate reads the host after an anchor, so each is asked about the line
        // as it will be, rather than having its rule restated over the change alone.
        if (!canAnchorAt(projected, span) || !canEndAt(projected, endIndex)) {
            return new SpanOutcome.Remove(Strings.REPORT_REASON_HAIRPIN_ENDPOINT_INVALID);
        }

        if (!holdsEnoughPitchedNotes(projected, span)) {
            return new SpanOutcome.Remove(Strings.REPORT_REASON_HAIRPIN_TOO_FEW_NOTES);
        }

        if (holdsNonSingleBar(projected, span)) {
            return new SpanOutcome.Remove(Strings.REPORT_REASON_HAIRPIN_NON_SINGLE_BAR);
        }

        return SpanOutcome.Keep.INSTANCE;
    }

    /**
     * A deletion never judges rule 4 ({@link #holdsNonSingleBar}): it adds no element.
     */
    private SpanOutcome outcomeForDeletion(
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
            return new SpanOutcome.Remove(Strings.REPORT_REASON_ENDPOINT_DELETED);
        }

        var survivors = new ElementRange(first, last);
        var begin = resolveBeginIndex(survivors, projected);
        var end = resolveEndIndex(survivors, projected);

        if (begin == null || end == null || end <= begin) {
            return new SpanOutcome.Remove(Strings.REPORT_REASON_ENDPOINT_DELETED);
        }

        // A span the user could not have drawn leaves no gesture worth keeping. The count also
        // rejects a surviving grace note with its host, which count as one pitched note.
        var reshaped = new ElementRange(begin, end);

        if (!holdsEnoughPitchedNotes(projected, reshaped)) {
            return new SpanOutcome.Remove(Strings.REPORT_REASON_ENDPOINT_DELETED);
        }

        return new SpanOutcome.Reshape(this, reshaped);
    }

    /**
     * A grace note can begin a hairpin when its host is pitched: anchoring on it keeps the
     * hairpin over what the user selected. {@link #spanning}, {@link #outcomeFor} and the
     * deletion reshape all read this, so no path can anchor a hairpin where a new one could not
     * start.
     */
    private static boolean canAnchorAt(List<? extends StaffElement> candidates, ElementRange span) {
        var index = span.begin();
        var type = candidates.get(index).getType();

        return type.isPitchedNote()
            || (type.isGraceNote()
                && index < span.end()
                && candidates.get(index + 1).getType().isPitchedNote());
    }

    /**
     * Read by every path that makes or keeps a hairpin, so none can end one where a new one
     * could not end.
     */
    private static boolean canEndAt(List<? extends StaffElement> candidates, int index) {
        return candidates.get(index).getType().isPitchedNote();
    }

    /**
     * A single barline only divides measures, and a hairpin may run across it. Read by every
     * path that makes or keeps a hairpin.
     */
    private static boolean holdsNonSingleBar(List<? extends StaffElement> candidates, ElementRange span) {
        for (var index = span.begin(); index <= span.end(); index++) {
            var type = candidates.get(index).getType();

            if (type.isBar() && type != ElementType.SINGLE_BARLINE) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return {@code null} when no survivor can begin a hairpin
     */
    private @Nullable Integer resolveBeginIndex(ElementRange survivors, List<? extends StaffElement> candidates) {
        var first = survivors.begin();
        var last = survivors.end();

        for (var i = first; i <= last; i++) {
            if (canAnchorAt(candidates, new ElementRange(i, last))) {
                return i;
            }
        }

        return null;
    }

    /**
     * A deleted end moves in to the last surviving pitched note, so a deletion never pulls an
     * end onto a rest.
     *
     * @return {@code null} when no survivor can end a hairpin
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
    public double intrinsicHeightSs() {
        return HAIRPIN_OPENING_HEIGHT_SS;
    }

    /**
     * Names the kind before there is an instance to ask, as the MusicXML reader and the hairpin
     * menu need.
     */
    public enum Kind {
        CRESCENDO,
        DIMINUENDO;

        public Kind opposite() {
            return this == CRESCENDO ? DIMINUENDO : CRESCENDO;
        }

        /**
         * {@link Hairpin#isSameKindAs}, stated before a hairpin exists.
         */
        boolean matches(Span<?> span) {
            return span instanceof Hairpin hairpin && hairpin.kind == this;
        }
    }
}
