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
 * and everything that concerns them as a collection: every span query, expressed over three
 * methods so the "iterate spans, filter by type, resolve both endpoint indices, compare" loop
 * exists in exactly one place ({@link #findSpans(Class, Span.IndexPredicate)},
 * {@link #findFirstSpan(Class, Span.IndexPredicate)} and {@link #findSpans(Class)}, with every
 * typed query stated in terms of an index delegating to one of them via a
 * {@link Span.IndexPredicate}); the decision and the sweep that re-judge every span when an
 * element changes; and the merge rules that decide
 * what two same-kind spans become. How the sweep, the per-kind
 * policies and the merge divide the work is in {@code docs/span-invalidation.md}. A tie is not
 * a span; it is a note's own state (see {@link NoteElement}).
 * <p>
 * One per {@link Line}, reached through {@link Line#spanRegistry()}, and the only writer of
 * the list it holds: a span enters or leaves only through {@link Span#addTo} and
 * {@link Span#removeFrom}, which end in {@link #add} and {@link #remove} here.
 * <p>
 * Every span lies in one line and sits in that line's registry only. A span is never attached
 * to a line: {@link Span#isIn} derives parentage from where its endpoints sit. Every endpoint of
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
     * Live unmodifiable view of {@link #spans}, held once rather than wrapped per call:
     * every query below iterates {@link #getSpans()}, and the wrapper tracks later
     * mutations of {@code spans} anyway.
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
     * @return an unmodifiable live view of this line's spans, in the order they were added
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
     * Does nothing while the song is replaying undo or redo: the recorded batch already
     * carries every companion span mutation, and re-deriving them would apply them twice.
     * <p>
     * The whole decision is made first and acted on afterwards. Acting while deciding would
     * both mutate the {@code spans} list being walked and let one span's removal change what
     * a later span is judged against.
     * <p>
     * Two answers are properties of the collection rather than of one span, since no span
     * knows its siblings. Same-type hairpins the change leaves with nothing between them
     * become one: the reshaped spans are grouped by hairpin kind and handed to
     * {@link #mergeAdjacentSpans}, which expresses each reshape as a tracked removal plus a
     * tracked addition of a copy, since a span has no modification mutation of its own.
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

        // The projection is asked for per kind rather than hoisted: there are at most two
        // kinds, and a change nothing reshaped must not build one at all.
        for (var hairpinSpans : decision.reshapedByKind().values()) {
            mergeAdjacentSpans(hairpinSpans, change.projectedElements());
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
     *
     * @param hairpinSpans all post-deletion spans of one hairpin type, in any order, each
     *     paired with its range over the projection
     */
    private void mergeAdjacentSpans(List<SpanAt<Hairpin>> hairpinSpans, List<? extends StaffElement> survivors) {
        hairpinSpans.sort(Comparator.comparingInt((SpanAt<Hairpin> span) -> span.range().begin()));

        var run = new ArrayList<SpanAt<Hairpin>>();

        // The run's last post-deletion index; null exactly when the run is empty.
        @Nullable Integer runEnd = null;

        for (var span : hairpinSpans) {
            var range = span.range();

            // One surviving element between two hairpins is still a break in the gesture.
            if (runEnd != null && range.begin() > runEnd + SPAN_ADJACENCY_REACH) {
                applySpanRun(run, runEnd, survivors);
                run.clear();
                runEnd = null;
            }

            run.add(span);
            runEnd = runEnd == null ? range.end() : Math.max(runEnd, range.end());
        }

        if (runEnd != null) {
            applySpanRun(run, runEnd, survivors);
        }
    }

    /**
     * Reshapes the hairpins in {@code run} into one hairpin spanning the run's surviving
     * endpoints, carrying over the first one's user offsets.
     *
     * @param run    the hairpins to replace, ordered by their post-deletion start, each paired
     *     with its range over the projection
     * @param runEnd the run's last post-deletion index
     */
    private void applySpanRun(List<SpanAt<Hairpin>> run, int runEnd, List<? extends StaffElement> survivors) {
        var first = run.getFirst().span();
        var anchorElement = survivors.get(run.getFirst().range().begin());
        var endElement = survivors.get(runEnd);

        if (run.size() == 1
                && first.getAnchorElement() == anchorElement
                && first.getEndElement() == endElement) {
            return;
        }

        // Removals precede the addition so reverse-order undo drops the reshaped hairpin
        // before restoring the originals. The first hairpin is reshaped, not lost; every other
        // one in the run is absorbed into it, which the user is told.
        first.removeFrom(line);

        for (var absorbed : run.subList(1, run.size())) {
            absorbed.span().removeReported(line, Strings.DIALOG_REPORT_REASON_MERGED);
        }

        var reshaped = first.copy(anchorElement, endElement);
        reshaped.addTo(line);
    }

    // ========================================================================
    // The displacements Span.addTo applies
    // ========================================================================

    /**
     * The {@link Displacement#ABSORB_OVERLAPPING} and {@link Displacement#ABSORB_ADJACENT}
     * displacements: widens {@code span} over every same-kind span ({@link Span#isSameKindAs})
     * overlapping its endpoints or within {@code reach} of them, then removes every same-kind
     * span fully subsumed by the widened range through its own {@link Span#removeFrom}. The
     * tracked removals are emitted before the widened span's addition so undo restores the
     * original spans after removing the merged one.
     *
     * @param span  the span about to be added, whose endpoints both sit in this line
     * @param reach how far past an endpoint, in elements, a same-kind span may sit and still be
     *              absorbed; {@code 0} absorbs only what {@code span} overlaps
     */
    <E extends StaffElement> void mergeOverlapping(Span<E> span, int reach) {
        var own = span.range();

        // Resolved once, before the setters below move the very indices they are compared
        // against. Each one's range is over this line, as it stands now.
        var placed = new ArrayList<SpanAt<Span<?>>>();

        for (var existing : sameKindSpans(span)) {
            placed.add(new SpanAt<>(existing, existing.range()));
        }

        var mergedAnchorIdx = placed.stream()
            .filter(candidate -> candidate.range().begin() <= own.begin()
                && own.begin() <= candidate.range().end() + reach)
            .mapToInt(candidate -> candidate.range().begin())
            .min()
            .orElse(own.begin());
        var mergedEndIdx = placed.stream()
            .filter(candidate -> candidate.range().begin() - reach <= own.end()
                && own.end() <= candidate.range().end())
            .mapToInt(candidate -> candidate.range().end())
            .max()
            .orElse(own.end());

        if (mergedAnchorIdx != own.begin()) {
            span.setAnchorElement(span.narrowEndpoint(line.getElement(mergedAnchorIdx)));
        }

        if (mergedEndIdx != own.end()) {
            span.setEndElement(span.narrowEndpoint(line.getElement(mergedEndIdx)));
        }

        placed.stream()
            .filter(candidate -> candidate.range().begin() >= mergedAnchorIdx
                && candidate.range().end() <= mergedEndIdx)
            .forEach(candidate -> candidate.span().removeReported(line, Strings.DIALOG_REPORT_REASON_MERGED));
    }

    /**
     * The spans in this registry of {@code span}'s kind ({@link Span#isSameKindAs}), in the
     * order they were added.
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
     * Returns the first span of {@code type} whose range satisfies {@code matches}, or
     * {@code null} if there is none. Short-circuits on the first match.
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
     * Returns whether any span of {@code type} satisfies {@code matches}.
     */
    public boolean hasSpan(Class<? extends Span<?>> type, Span.IndexPredicate matches) {
        return findFirstSpan(type, matches) != null;
    }

    /**
     * Returns the first {@link Beam} span whose anchor-to-end range includes
     * {@code elementIndex}, or {@code null} if the element is not part of any beam.
     */
    public @Nullable Beam findBeamAt(int elementIndex) {
        return findFirstSpan(Beam.class, Span.containing(elementIndex));
    }

    /**
     * Returns true if one and the same {@link Beam} covers both {@code firstIndex} and
     * {@code secondIndex}.
     */
    public boolean sameBeamAt(int firstIndex, int secondIndex) {
        var beamAtFirst = findBeamAt(firstIndex);

        //noinspection ObjectEquality
        return beamAtFirst != null && beamAtFirst == findBeamAt(secondIndex);
    }

    /**
     * Returns the first {@link Tuplet} span whose anchor-to-end range includes
     * {@code elementIndex}, or {@code null} if the element is not part of any tuplet.
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
     */
    public boolean isInsideHairpin(int noteIndex) {
        return hasSpan(Hairpin.class, Span.strictlyContaining(noteIndex));
    }

    /**
     * Returns every hairpin of {@code kind} whose range satisfies {@code matches}.
     */
    public List<Hairpin> findHairpins(Hairpin.Kind kind, Span.IndexPredicate matches) {
        return findSpans(Hairpin.class, matches).stream()
            .filter(hairpin -> hairpin.getKind() == kind)
            .toList();
    }

    /**
     * Returns whether any hairpin of {@code kind} satisfies {@code matches}.
     */
    public boolean hasHairpin(Hairpin.Kind kind, Span.IndexPredicate matches) {
        return !findHairpins(kind, matches).isEmpty();
    }

    /**
     * Returns whether any trill span overlaps {@code range}.
     */
    public boolean hasTrillOverlapping(ElementRange range) {
        return hasSpan(Trill.class, Span.overlapping(range));
    }

    /**
     * Returns all {@link Ending} spans in this line.
     */
    public List<Ending> findEndings() {
        return findSpans(Ending.class);
    }
}
