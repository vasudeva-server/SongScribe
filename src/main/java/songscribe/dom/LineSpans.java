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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;

/**
 * The spans drawn over one line's elements — beams, trills, hairpins, tuplets and endings —
 * and everything that concerns them as a collection: the typed span queries, each stated in
 * terms of an index through a {@link Span.IndexPredicate} and answered by
 * {@link #findSpans(Class, Span.IndexPredicate)}, {@link #findFirstSpan(Class, Span.IndexPredicate)},
 * {@link #findSpans(Class)} or, for a caller that judges every kind in one walk,
 * {@link #findSpans(Span.IndexPredicate)}; the decision and the sweep that re-judge every span
 * when an element changes; and the merge rules that decide what two same-kind spans become.
 * How the sweep, the per-kind policies and the merge divide the work is in
 * {@code docs/span-invalidation.md}. A tie is not
 * a span; it is a note's own state (see {@link NoteElement}).
 * <p>
 * One per {@link Line}, reached through {@link Line#spanRegistry()}, and the only writer of
 * the list it holds: a span enters or leaves only through {@link Span#addTo} and
 * {@link Span#removeFrom}, which end in {@link #add} and {@link #remove} here.
 * <p>
 * Every span lies in one line and sits in that line's registry only. A span is never attached
 * to a line: its lines are derived from where its endpoints sit. Every endpoint of
 * a span in this registry sits in this line: a deletion sweeps first and removes every span
 * whose endpoint it takes, a replacement re-points, a merge moves endpoints only within the
 * line, and replay undoes in reverse order. So {@link Span#range} and every
 * {@link Span.IndexPredicate} are total over this registry.
 */
public final class LineSpans {

    /**
     * How far past a span's endpoint a same-type span may sit and still count as
     * touching it, in elements. Two spans this close describe one uninterrupted
     * gesture, so adding or reshaping either one merges them.
     * <p>
     * Every place that decides "are these two spans adjacent?" must read this, or
     * the menu would offer to extend a hairpin the model then declines to merge.
     */
    public static final int SPAN_ADJACENCY_REACH = 1;

    private final Line line;

    /**
     * This line's spans (beams, trills, crescendo, diminuendo, tuplets, endings), each with both
     * endpoints in this line, and held in no other line's registry.
     */
    private final List<Span<?>> spans = new ArrayList<>();

    /**
     * Live unmodifiable view of {@link #spans}, held once rather than wrapped per call of
     * {@link #getSpans()}: the wrapper tracks later mutations of {@code spans}.
     */
    private final List<Span<?>> spansView = Collections.unmodifiableList(spans);

    LineSpans(Line line) {
        this.line = line;
    }

    /**
     * The spans drawn over this line.
     * <p>
     * A live view, not a copy: a span added or removed while a caller iterates it gives that
     * caller {@link java.util.ConcurrentModificationException}, so a caller that removes spans as
     * it walks them copies the list first.
     *
     * @return an unmodifiable live view of this line's spans, in no defined order
     */
    public List<Span<?>> getSpans() {
        return spansView;
    }

    // ========================================================================
    // List writes
    // ========================================================================

    /**
     * Appends {@code element} to {@code spans}. Call from inside an applyChange mutator lambda,
     * so the list write moves with the recorded change.
     */
    private void appendChild(Span<?> element) {
        spans.add(element);
    }

    /**
     * Removes the span at {@code index} of {@code spans}, the index its caller already located,
     * rather than scanning the list a second time. Call from inside an applyChange mutator
     * lambda, so the list write moves with the recorded change.
     */
    private void removeChild(int index) {
        spans.remove(index);
    }

    /**
     * Points every span endpoint that is {@code oldElement} at {@code newElement}, so
     * {@link Span#requireAnchorElementIndex()}/{@link Span#requireEndElementIndex()} remain valid
     * after {@link Line#replace} swaps the element out. Runs inside that mutator, on
     * replay too: it is self-inverting, and the recorded batch carries no record of it.
     */
    void repoint(StaffElement oldElement, StaffElement newElement) {
        for (var span : spans) {
            repointEndpoints(span, oldElement, newElement);
        }
    }

    /**
     * Points each endpoint of {@code span} that is {@code oldElement} at {@code newElement},
     * narrowed to {@code span}'s endpoint type.
     *
     * @throws IllegalArgumentException if an endpoint is {@code oldElement} and
     *     {@code newElement} is not of {@code span}'s endpoint type
     */
    private static <E extends StaffElement> void repointEndpoints(
        Span<E> span,
        StaffElement oldElement,
        StaffElement newElement
    ) {
        if (span.getAnchorElement() == oldElement) {
            span.setAnchorElement(span.narrowEndpoint(newElement));
        }

        if (span.getEndElement() == oldElement) {
            span.setEndElement(span.narrowEndpoint(newElement));
        }
    }

    /**
     * Records a {@link SpanAddition} of {@code span} and appends it inside that record. The
     * primitive {@link Span#addTo} ends in, after the span's displacement.
     */
    void add(Span<?> span) {
        line.applyChange(new SpanAddition(line, span), () -> appendChild(span));
    }

    /**
     * Records a {@link SpanRemoval} of {@code span} and removes it inside that record, or does
     * nothing at all when {@code span} is not in this registry. The primitive
     * {@link Span#removeFrom} ends in, and the one place the absent-span early return exists,
     * so a second removal of the same span finds nothing left to remove and records nothing.
     */
    void remove(Span<?> span) {
        var index = spans.indexOf(span);

        if (index < 0) {
            return;
        }

        line.applyChange(new SpanRemoval(line, span), () -> removeChild(index));
    }

    // ========================================================================
    // The sweep behind every element change
    // ========================================================================

    /**
     * A span paired with a range over it, so a caller that has already resolved the range does
     * not resolve it again from the span's live endpoints.
     */
    private record SpanAt<S extends Span<?>>(S span, ElementRange range) {
    }

    /**
     * A span the sweep removes, with the reason its rule gave for removing it.
     *
     * @param span      the span to remove
     * @param reasonKey the {@code Strings} key of the reason, as {@link Span#removeReported} takes it
     */
    private record Removal(Span<?> span, String reasonKey) {
    }

    /**
     * What a pending change does to this line's spans, decided before any of it is carried
     * out: the spans it deletes along with their elements, the spans it removes, and the
     * hairpins it reshapes.
     *
     * @param deleted        the spans a deletion takes whole, each once; their removal is the one
     *     the user asked for, so none is reported
     * @param removed        the spans the change removes, each once, with its reason; a span in
     *     {@code deleted} is not here
     * @param reshapedByKind the hairpins the change reshapes, grouped by kind, each paired with
     *     its range over the change's projection; a hairpin in {@code deleted} or
     *     {@code removed} is not here
     */
    record SweepDecision(
        List<Span<?>> deleted,
        List<Removal> removed,
        Map<Hairpin.Kind, List<SpanAt<Hairpin>>> reshapedByKind
    ) {
    }

    /**
     * Decides what {@code change} does to this line's spans, without doing any of it.
     * {@link #sweep} makes the whole decision before it carries out any of it, because carrying
     * one out while deciding would mutate the {@code spans} list being walked and would change
     * what a later span is judged against.
     * <p>
     * A deletion that takes every element a span covers takes the span with them, and no span
     * kind is asked about it: the user deleted the whole span, so its removal is the one the user
     * asked for. Every other span is asked its own outcome ({@link Span#outcomeFor}; a hairpin is
     * asked {@link Hairpin#hairpinOutcomeFor}, the one outcome that can reshape).
     *
     * @param change a pending change to this line, not yet applied
     * @return the spans {@code change} deletes whole, the spans it removes, and the hairpins it
     *     reshapes
     */
    private SweepDecision decide(ElementChange change) {
        var deleted = new ArrayList<Span<?>>();
        var removed = new ArrayList<Removal>();

        // Each hairpin's range is over the projection: change.projectedElements(), not this
        // line as it stands now.
        var reshapedByKind = new LinkedHashMap<Hairpin.Kind, List<SpanAt<Hairpin>>>();

        for (var span : spans) {
            if (change instanceof ElementChange.Deletion deletion && deletion.range().contains(span.range())) {
                deleted.add(span);
                continue;
            }

            // Only a hairpin can reshape, so only a hairpin is asked the wider question: the
            // merge that consumes a Reshape replaces a run with a copy of a hairpin, and
            // there is nothing it could do with any other kind of span.
            if (span instanceof Hairpin hairpin) {
                switch (hairpin.hairpinOutcomeFor(change)) {
                    case SpanOutcome.Keep _ -> { }
                    case SpanOutcome.Remove remove -> removed.add(new Removal(hairpin, remove.reasonKey()));
                    case SpanOutcome.Reshape reshape -> collectReshape(reshapedByKind, hairpin, reshape);
                }
            } else {
                switch (span.outcomeFor(change)) {
                    case SpanOutcome.Keep _ -> { }
                    case SpanOutcome.Remove remove -> removed.add(new Removal(span, remove.reasonKey()));
                }
            }
        }

        return new SweepDecision(deleted, removed, reshapedByKind);
    }

    /**
     * Decides what {@code change} does to every span on this line ({@link #decide}), and
     * carries out that decision — the single sweep behind every element insertion,
     * replacement, deletion and in-place modification.
     * <p>
     * Called <b>before</b> the change is applied, so each span is judged against the
     * projection rather than the mutated line, and so the companion span mutations are
     * recorded before the primary element mutation: undo replays a step in reverse, and
     * has to restore the element before the spans that point at it.
     * <p>
     * Does nothing while the song is replaying undo or redo, for two reasons. The recorded
     * batch already carries every companion span mutation, and re-deriving them would apply
     * them twice. And replay passes through states no edit made, which the rules would judge:
     * undo of a deletion of more than one element puts the elements back one at a time through
     * {@link Line#addElement(int, StaffElement)}, and each call sweeps. On {@code [Y, ||, X, N …]} with an ending
     * anchored on note {@code N}, the user deletes {@code ||} and {@code X} and the ending
     * stays. Undo puts {@code ||} back first, giving {@code [Y, ||, N …]}; the ending's rules
     * ({@code Ending.hasOpeningPredecessor}) refuse a {@code ||} in front of a note anchor, so a
     * sweep there would remove the ending in the middle of the undo.
     * <p>
     * The whole decision is made first and acted on afterwards. Acting while deciding would
     * both mutate the {@code spans} list being walked and let one span's removal change what
     * a later span is judged against.
     * <p>
     * Two answers are properties of the collection rather than of one span, since no span
     * knows its siblings. Same-type hairpins the change leaves with nothing between them
     * become one, unless joining them would make the joined hairpin share more than one
     * element with a surviving hairpin of the opposite kind: the reshaped spans are grouped by
     * hairpin kind and handed to {@link #mergeAdjacentSpans} with the projected ranges of the
     * other kind, which expresses each reshape as a tracked removal plus a tracked addition of
     * a copy, since a span has no modification mutation of its own.
     * Each span a deletion takes whole is removed with {@link Span#removeFrom}, since the user
     * deleted it; each other span the decision removes is removed with
     * {@link Span#removeReported}, so the user is told why.
     *
     * @param change the pending change, not yet applied to this line
     */
    void sweep(ElementChange change) {
        if (spans.isEmpty() || line.getSong().isReplaying()) {
            return;
        }

        var decision = decide(change);

        decision.deleted().forEach(span -> span.removeFrom(line));
        decision.removed().forEach(removal -> removal.span().removeReported(line, removal.reasonKey()));

        var reshapedByKind = decision.reshapedByKind();

        // The projection is asked for per kind rather than hoisted: there are at most two
        // kinds, and a change nothing reshaped must not build one at all.
        for (var entry : reshapedByKind.entrySet()) {
            // A deletion answers Reshape for every hairpin it keeps, so the other kind's
            // reshaped hairpins are every one of that kind that survives the change.
            var oppositeRanges = reshapedByKind.getOrDefault(entry.getKey().opposite(), List.of()).stream()
                .map(SpanAt::range)
                .toList();

            mergeAdjacentSpans(entry.getValue(), oppositeRanges, change.projectedElements());
        }
    }

    /** Files {@code reshape} under {@code hairpin}'s kind, ready for the merge pass. */
    private static void collectReshape(
        Map<Hairpin.Kind, List<SpanAt<Hairpin>>> reshapedByKind,
        Hairpin hairpin,
        SpanOutcome.Reshape reshape
    ) {
        reshapedByKind
            .computeIfAbsent(hairpin.getKind(), kind -> new ArrayList<>())
            .add(new SpanAt<>(hairpin, reshape.range()));
    }

    /**
     * Replaces each run of same-type hairpin {@code hairpinSpans} that the deletion leaves
     * touching with a single hairpin covering the run. Runs of one whose endpoints both
     * survive are left untouched, so a deletion that misses a hairpin emits no hairpin
     * mutations.
     * <p>
     * A run ends before a hairpin that would make the joined range share more than one element
     * with a range in {@code oppositeRanges} ({@link Span#overlappingBeyondEndpoint}, the test
     * {@link Hairpin#spanning} applies for the opposite-kind rule of {@link Hairpin}'s class
     * invariant). Both hairpins then stay, each reshaped on its own. A crescendo on
     * {@code [0, 2]}, a diminuendo on {@code [2, 3]} and a crescendo on {@code [3, 5]} stay three
     * hairpins, since the joined crescendo {@code [0, 5]} would hold the whole diminuendo.
     *
     * @param hairpinSpans   all post-deletion spans of one hairpin type, in any order, each
     *     paired with its range over the projection
     * @param oppositeRanges the range over the projection of every hairpin of the opposite kind
     *     that survives the deletion, reshaped or not
     * @param survivors      the line's elements as the deletion leaves them
     */
    private void mergeAdjacentSpans(
        List<SpanAt<Hairpin>> hairpinSpans,
        List<ElementRange> oppositeRanges,
        List<? extends StaffElement> survivors
    ) {
        hairpinSpans.sort(Comparator.comparingInt((SpanAt<Hairpin> span) -> span.range().begin()));

        var run = new ArrayList<SpanAt<Hairpin>>();

        // The run's post-deletion range; null exactly when the run is empty.
        @Nullable ElementRange runRange = null;

        for (var span : hairpinSpans) {
            var range = span.range();

            if (runRange != null && !joinsRun(runRange, range, oppositeRanges)) {
                applySpanRun(run, runRange, survivors);
                run.clear();
                runRange = null;
            }

            run.add(span);
            runRange = runRange == null ? range : joined(runRange, range);
        }

        if (runRange != null) {
            applySpanRun(run, runRange, survivors);
        }
    }

    /**
     * Whether a same-kind hairpin over {@code range} joins the run over {@code runRange}.
     *
     * @param runRange       the run's post-deletion range
     * @param range          the hairpin's post-deletion range; it begins at or after
     *     {@code runRange}'s begin
     * @param oppositeRanges the post-deletion range of every surviving hairpin of the opposite
     *     kind
     * @return {@code true} when {@code range} begins within {@link #SPAN_ADJACENCY_REACH} of the
     *     run's end and the joined range shares at most one element, an endpoint, with each of
     *     {@code oppositeRanges}
     */
    private static boolean joinsRun(ElementRange runRange, ElementRange range, List<ElementRange> oppositeRanges) {
        // One surviving element between two hairpins is still a break in the gesture.
        if (range.begin() > runRange.end() + SPAN_ADJACENCY_REACH) {
            return false;
        }

        var overlapsBeyondEndpoint = Span.overlappingBeyondEndpoint(joined(runRange, range));

        return oppositeRanges.stream().noneMatch(overlapsBeyondEndpoint::test);
    }

    /**
     * The range of the run over {@code runRange} once the hairpin over {@code range} joins it.
     *
     * @param range a range that begins at or after {@code runRange}'s begin
     */
    private static ElementRange joined(ElementRange runRange, ElementRange range) {
        return new ElementRange(runRange.begin(), Math.max(runRange.end(), range.end()));
    }

    /**
     * Reshapes the hairpins in {@code run} into one hairpin spanning the run's surviving
     * endpoints, carrying over the first one's user offsets.
     *
     * @param run      the hairpins to replace, ordered by their post-deletion start, each paired
     *     with its range over the projection
     * @param runRange the run's post-deletion range
     */
    private void applySpanRun(List<SpanAt<Hairpin>> run, ElementRange runRange, List<? extends StaffElement> survivors) {
        var first = run.getFirst().span();
        var anchorElement = survivors.get(runRange.begin());
        var endElement = survivors.get(runRange.end());

        if (run.size() == 1
                && first.getAnchorElement() == anchorElement
                && first.getEndElement() == endElement) {
            return;
        }

        // Removals precede the addition so reverse-order undo drops the reshaped hairpin
        // before restoring the originals. The first hairpin is reshaped and every other one in
        // the run is absorbed into it; the reshaped hairpin covers them all, so no removal is
        // reported.
        first.removeFrom(line);

        for (var absorbed : run.subList(1, run.size())) {
            absorbed.span().removeFrom(line);
        }

        var reshaped = first.copy(anchorElement, endElement);
        reshaped.addTo(line);
    }

    // ========================================================================
    // The displacements Span.addTo applies
    // ========================================================================

    /**
     * The {@link Displacement#ABSORB_OVERLAPPING} and {@link Displacement#ABSORB_ADJACENT}
     * displacements: widens {@code span} to the range {@link #absorption} answers, then removes
     * every span that absorption took in through its own {@link Span#removeFrom}. The widened
     * span covers every span it absorbs, so a merge loses nothing and reports nothing. The
     * tracked removals are emitted before the widened span's addition so undo restores the
     * original spans after removing the merged one.
     *
     * @param span  the span about to be added, whose endpoints both sit in this line
     * @param reach how far past an endpoint, in elements, a same-kind span may sit and still be
     *              absorbed; {@code 0} absorbs only what {@code span} overlaps
     */
    <E extends StaffElement> void mergeOverlapping(Span<E> span, int reach) {
        var own = span.range();

        // Decided in full before the setters below move the very indices it compares.
        var absorption = absorption(span, reach);
        var merged = absorption.range();

        if (merged.begin() != own.begin()) {
            span.setAnchorElement(span.narrowEndpoint(line.getElement(merged.begin())));
        }

        if (merged.end() != own.end()) {
            span.setEndElement(span.narrowEndpoint(line.getElement(merged.end())));
        }

        absorption.absorbed()
            .forEach(absorbed -> absorbed.removeFrom(line));
    }

    /**
     * What an absorbing displacement makes of a span: the range it widens to, and the spans it
     * takes in.
     *
     * @param range    the widened range, which holds the span's own range
     * @param absorbed every same-kind span whose range lies inside {@code range}, in no defined
     *                 order; empty when none does
     */
    record Absorption(ElementRange range, List<Span<?>> absorbed) {
    }

    /**
     * Decides what an absorbing displacement makes of {@code span} on this line, without
     * changing anything. The range starts as {@code span}'s own and widens over every same-kind
     * span ({@link Span#isSameKindAs}) that overlaps it or sits within {@code reach} of either
     * end, again and again until a pass finds nothing that widens it, since absorbing one span
     * can bring a further one into reach.
     * <p>
     * {@link #mergeOverlapping} carries this out when a span is added, and
     * {@link Hairpin#converge} asks it before a hairpin exists, so the range a hairpin is judged
     * over is the range adding it produces.
     *
     * @param span  a span whose endpoints both sit in this line, not in this registry
     * @param reach how far past an end, in elements, a same-kind span may sit and still be
     *              absorbed; {@code 0} absorbs only what overlaps
     * @return the widened range and the same-kind spans inside it; the range equals
     *     {@code span}'s own when no same-kind span overlaps it or sits within {@code reach}
     */
    Absorption absorption(Span<?> span, int reach) {
        var placed = sameKindSpans(span).stream()
            .map(existing -> new SpanAt<Span<?>>(existing, existing.range()))
            .toList();
        var range = span.range();

        while (true) {
            var window = new ElementRange(Math.max(0, range.begin() - reach), range.end() + reach);
            var begin = range.begin();
            var end = range.end();

            for (var candidate : placed) {
                if (candidate.range().overlaps(window)) {
                    begin = Math.min(begin, candidate.range().begin());
                    end = Math.max(end, candidate.range().end());
                }
            }

            if (begin == range.begin() && end == range.end()) {
                break;
            }

            range = new ElementRange(begin, end);
        }

        var widened = range;
        var absorbed = placed.stream()
            .filter(candidate -> widened.contains(candidate.range()))
            .map(SpanAt::span)
            .toList();

        return new Absorption(widened, absorbed);
    }

    /**
     * The spans in this registry of {@code span}'s kind ({@link Span#isSameKindAs}), in no
     * defined order.
     *
     * @return a copy, safe to hold while spans are removed
     */
    private List<Span<?>> sameKindSpans(Span<?> span) {
        return spans.stream()
            .filter(span::isSameKindAs)
            .toList();
    }

    /**
     * The {@link Displacement#REPLACE_OVERLAPPING} displacement: removes every same-kind span
     * ({@link Span#isSameKindAs}) overlapping {@code span}'s range, as {@link Span#overlapping}
     * judges it.
     *
     * @param span the span about to be added, whose endpoints both sit in this line
     * @effects one {@link Span#removeFrom} per span removed, so each removal is its own
     *     mutation, recorded under the rule that method states
     */
    void removeOverlappingSameKind(Span<?> span) {
        var overlapsSpan = Span.overlapping(span.range());

        sameKindSpans(span).stream()
            .filter(existing -> overlapsSpan.test(existing.range()))
            .forEach(existing -> existing.removeReported(line, Strings.DIALOG_REPORT_REASON_REPLACED));
    }

    /**
     * Removes every {@code type} span overlapping {@code range}.
     *
     * @param type  the kind of span to remove; a span of any other kind stays
     * @param range the elements a removed span overlaps, as {@link Span#overlapping} judges it
     * @effects one {@link Span#removeFrom} per span found, so each removal is its own mutation,
     *     recorded under the rule that method states
     */
    public void removeOverlapping(Class<? extends Span<?>> type, ElementRange range) {
        for (var span : findSpans(type, Span.overlapping(range))) {
            span.removeFrom(line);
        }
    }

    // ========================================================================
    // Span queries
    // ========================================================================

    /**
     * Returns every span of {@code type} whose range satisfies {@code matches}.
     *
     * @return the matching spans, in registry order; empty when none match
     */
    public <T extends Span<?>> List<T> findSpans(Class<T> type, Span.IndexPredicate matches) {
        var result = new ArrayList<T>();

        for (var span : spans) {
            if (isMatch(span, type, matches)) {
                result.add(type.cast(span));
            }
        }

        return result;
    }

    /**
     * Returns a span of {@code type} whose range satisfies {@code matches}, or {@code null} if
     * there is none. When several spans match, it returns one of them, and which one is not
     * defined. Short-circuits on the match it finds.
     */
    public <T extends Span<?>> @Nullable T findFirstSpan(Class<T> type, Span.IndexPredicate matches) {
        for (var span : spans) {
            if (isMatch(span, type, matches)) {
                return type.cast(span);
            }
        }

        return null;
    }

    private boolean isMatch(Span<?> span, Class<? extends Span<?>> type, Span.IndexPredicate matches) {
        return type.isInstance(span) && matches.test(span.range());
    }

    /**
     * Returns every span of {@code type}, whatever its endpoints resolve to.
     * <p>
     * Filters by type only and resolves no endpoint positions — unlike routing through
     * {@link #findSpans(Class, Span.IndexPredicate)} with an always-true predicate, which
     * would still resolve both endpoints of every matching span because Java evaluates
     * arguments eagerly.
     *
     * @return the spans of {@code type}, in registry order; empty when there are none
     */
    public <T extends Span<?>> List<T> findSpans(Class<T> type) {
        var result = new ArrayList<T>();

        for (var span : spans) {
            if (type.isInstance(span)) {
                result.add(type.cast(span));
            }
        }

        return result;
    }

    /**
     * Returns every span, of any kind, whose range satisfies {@code matches}. For a caller that
     * judges every kind in one walk.
     *
     * @return the matching spans, in registry order; empty when none match
     */
    public List<Span<?>> findSpans(Span.IndexPredicate matches) {
        return spans.stream().filter(span -> matches.test(span.range())).toList();
    }

    /**
     * Returns whether any span of {@code type} satisfies {@code matches}.
     *
     * @return {@code true} when at least one span of {@code type} satisfies {@code matches}
     */
    public boolean hasSpan(Class<? extends Span<?>> type, Span.IndexPredicate matches) {
        return findFirstSpan(type, matches) != null;
    }

    /**
     * Returns the first {@link Beam} span whose anchor-to-end range includes
     * {@code elementIndex}, or {@code null} if the element is not part of any beam.
     *
     * @return the beam covering {@code elementIndex}, or {@code null} when no beam in this line
     *     does
     */
    public @Nullable Beam findBeamAt(int elementIndex) {
        return findFirstSpan(Beam.class, Span.containing(elementIndex));
    }

    public boolean sameBeamAt(int firstIndex, int secondIndex) {
        var beamAtFirst = findBeamAt(firstIndex);

        //noinspection ObjectEquality
        return beamAtFirst != null && beamAtFirst == findBeamAt(secondIndex);
    }

    /**
     * Returns the first {@link Tuplet} span whose anchor-to-end range includes
     * {@code elementIndex}, or {@code null} if the element is not part of any tuplet.
     *
     * @return the tuplet covering {@code elementIndex}, or {@code null} when no tuplet in this
     *     line does
     */
    public @Nullable Tuplet findTupletAt(int elementIndex) {
        return findFirstSpan(Tuplet.class, Span.containing(elementIndex));
    }

    /**
     * Returns true if the given note index falls strictly inside any hairpin (crescendo or
     * diminuendo), its bound elements excluded.
     * <p>
     * This is the editor's rule for where a text dynamic may go: a dynamic may sit on a
     * hairpin's anchor or end element, but never under the wedge. See
     * {@code docs/hairpin-editing.md}.
     *
     * @return {@code true} when a hairpin covers {@code noteIndex} strictly between its bounds
     */
    public boolean isInsideHairpin(int noteIndex) {
        return hasSpan(Hairpin.class, Span.strictlyContaining(noteIndex));
    }

    public List<Hairpin> findHairpins(Hairpin.Kind kind, Span.IndexPredicate matches) {
        return findSpans(Hairpin.class, matches).stream()
            .filter(hairpin -> hairpin.getKind() == kind)
            .toList();
    }

    public boolean hasHairpin(Hairpin.Kind kind, Span.IndexPredicate matches) {
        return !findHairpins(kind, matches).isEmpty();
    }

    public boolean hasTrillOverlapping(ElementRange range) {
        return hasSpan(Trill.class, Span.overlapping(range));
    }

    public List<Ending> findEndings() {
        return findSpans(Ending.class);
    }
}
