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
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;

/**
 * The spans drawn over one line's elements — beams, trills, hairpins, tuplets and endings —
 * and everything that concerns them as a collection.
 * <p>
 * The only writer of the list it holds: a span enters or leaves only through {@link #add} and
 * {@link #remove} here.
 * <p>
 * Every span lies in one line and sits in that line's registry only. Every endpoint of a span in
 * this registry sits in this line, so {@link Span#range} and every {@link Span.RangePredicate}
 * are total over this registry.
 */
public final class LineSpans {

    /**
     * How far past a span's endpoint a same-type span may sit and still count as touching it,
     * in elements; adding or reshaping either one merges them.
     * <p>
     * Every place that decides "are these two spans adjacent?" must read this, or the menu would
     * offer to extend a hairpin the model then declines to merge.
     */
    public static final int SPAN_ADJACENCY_REACH = 1;

    private final Line line;

    private final List<Span<?>> spans = new ArrayList<>();

    private final List<Span<?>> spansView = Collections.unmodifiableList(spans);

    LineSpans(Line line) {
        this.line = line;
    }

    /**
     * A live view, not a copy: a span added or removed while a caller iterates it throws
     * {@link java.util.ConcurrentModificationException}, so a caller that removes spans as it
     * walks them copies the list first.
     *
     * @return in no defined order
     */
    public List<Span<?>> getSpans() {
        return spansView;
    }

    /**
     * Call from inside an applyChange mutator lambda,
     * so the list write moves with the recorded change.
     */
    private void appendChild(Span<?> element) {
        spans.add(element);
    }

    /**
     * Call from inside an applyChange mutator lambda, so the list write moves with the recorded
     * change.
     */
    private void removeChild(int index) {
        spans.remove(index);
    }

    /**
     * Runs inside the {@link Line#replace} mutator, on replay too: it is self-inverting, and the
     * recorded batch carries no record of it.
     */
    void repoint(StaffElement oldElement, StaffElement newElement) {
        for (var span : spans) {
            repointEndpoints(span, oldElement, newElement);
        }
    }

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

    void add(Span<?> span) {
        line.applyChange(new SpanAddition(line, span), () -> appendChild(span));
    }

    /**
     * A second removal of the same span finds nothing left to remove and records nothing.
     */
    void remove(Span<?> span) {
        var index = spans.indexOf(span);

        if (index < 0) {
            return;
        }

        line.applyChange(new SpanRemoval(line, span), () -> removeChild(index));
    }

    private record SpanAt<S extends Span<?>>(S span, ElementRange range) {
    }

    private record Removal(Span<?> span, String reasonKey) {
    }

    /**
     * @param deleted       the spans a deletion takes whole, each once; their removal is the one
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
     * A deletion that takes every element a span covers takes the span with them without asking
     * it: the user deleted the whole span.
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

            switch (span.outcomeFor(change)) {
                case SpanOutcome.Keep _ -> { }
                case SpanOutcome.Remove remove -> removed.add(new Removal(span, remove.reasonKey()));
                case SpanOutcome.Reshape reshape -> collectReshape(reshapedByKind, reshape);
            }
        }

        return new SweepDecision(deleted, removed, reshapedByKind);
    }

    /**
     * Called <b>before</b> the change is applied, so each span is judged against the projection
     * and the companion span mutations are recorded before the primary element mutation: undo
     * replays a step in reverse and has to restore the element before the spans that point at it.
     * <p>
     * Does nothing while the song is replaying undo or redo, because the recorded step already
     * carries its companion span changes, and because replay passes through states no edit made
     * that a span's rule would wrongly judge: undoing the deletion of several elements puts them
     * back one at a time, and a sweep after the first could remove an ending the step's own
     * later insertions restore.
     * <p>
     * The whole decision is made before any of it is acted on, so one span's removal cannot
     * change what a later span is judged against. A span a deletion takes whole is removed with
     * {@link Span#removeFrom}; each other span the decision removes is removed with
     * {@link Span#removeReported}, so the user is told why.
     */
    void sweep(ElementChange change) {
        if (spans.isEmpty() || line.getSong().isReplaying()) {
            return;
        }

        var decision = decide(change);

        decision.deleted().forEach(span -> span.removeFrom());
        decision.removed().forEach(removal -> removal.span().removeReported(removal.reasonKey()));

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

    private static void collectReshape(
        Map<Hairpin.Kind, List<SpanAt<Hairpin>>> reshapedByKind,
        SpanOutcome.Reshape reshape
    ) {
        var hairpin = reshape.hairpin();

        reshapedByKind
            .computeIfAbsent(hairpin.getKind(), kind -> new ArrayList<>())
            .add(new SpanAt<>(hairpin, reshape.range()));
    }

    /**
     * Runs of one whose endpoints both survive are left untouched, so a deletion that misses a
     * hairpin emits no hairpin mutations.
     * <p>
     * A run ends before a hairpin that would make the joined range share more than one element
     * with a range in {@code oppositeRanges} ({@link Span#overlappingBeyondEndpoint}). A
     * crescendo on {@code [0, 2]}, a diminuendo on {@code [2, 3]} and a crescendo on
     * {@code [3, 5]} stay three hairpins, since the joined crescendo would hold the whole
     * diminuendo.
     */
    private void mergeAdjacentSpans(
        List<SpanAt<Hairpin>> hairpinSpans,
        List<ElementRange> oppositeRanges,
        List<? extends StaffElement> survivors
    ) {
        hairpinSpans.sort(Comparator.comparingInt((SpanAt<Hairpin> span) -> span.range().begin()));

        var run = new ArrayList<SpanAt<Hairpin>>();

        // The run's post-deletion range; null exactly when the run is empty.
        @Nullable RunRange runRange = null;

        for (var span : hairpinSpans) {
            var range = span.range();

            if (runRange != null && !runRange.admits(range, oppositeRanges)) {
                applySpanRun(run, runRange.range(), survivors);
                run.clear();
                runRange = null;
            }

            run.add(span);
            runRange = runRange == null ? new RunRange(range) : runRange.joinedWith(range);
        }

        if (runRange != null) {
            applySpanRun(run, runRange.range(), survivors);
        }
    }

    private record RunRange(ElementRange range) {

        /**
         * @param later it begins at or after this run's begin
         */
        boolean admits(ElementRange later, List<ElementRange> oppositeRanges) {
            // One surviving element between two hairpins is still a break in the gesture.
            if (later.begin() > range.end() + SPAN_ADJACENCY_REACH) {
                return false;
            }

            var overlapsBeyondEndpoint = Span.overlappingBeyondEndpoint(joinedWith(later).range());

            return oppositeRanges.stream().noneMatch(overlapsBeyondEndpoint::test);
        }

        /**
         * @param later a range that begins at or after this run's begin
         */
        RunRange joinedWith(ElementRange later) {
            return new RunRange(new ElementRange(range.begin(), Math.max(range.end(), later.end())));
        }
    }

    /**
     * The joined hairpin is registered through {@link #add}, with no displacement:
     * {@link Span#addTo} would merge again over the line as it stands before the deletion, and
     * could absorb a same-kind hairpin across a hairpin of the opposite kind.
     *
     * @param run the hairpins to replace, ordered by their post-deletion start
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
        first.removeFrom();

        for (var absorbed : run.subList(1, run.size())) {
            absorbed.span().removeFrom();
        }

        add(first.copy(anchorElement, endElement));
    }

    /**
     * The widened span covers every span it absorbs, so a merge loses nothing and reports
     * nothing. The tracked removals are emitted before the widened span's addition so undo
     * restores the original spans after removing the merged one.
     *
     * @param span the span about to be added, whose endpoints both sit in this line
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
            .forEach(absorbed -> absorbed.removeFrom());
    }

    /**
     * @param range   the widened range, which holds the span's own range
     * @param absorbed every same-kind span whose range lies inside {@code range}, in no defined
     *                 order; empty when none does
     */
    record Absorption(ElementRange range, List<Span<?>> absorbed) {

        /**
         * @param kind the class of every absorbed span: the same-kind test admits only spans of
         *             this class
         */
        <S extends Span<?>> List<S> absorbedAs(Class<S> kind) {
            return absorbed.stream().map(kind::cast).toList();
        }
    }

    /**
     * @param span a span whose endpoints both sit in this line, not in this registry
     */
    Absorption absorption(Span<?> span, int reach) {
        return absorption(span.range(), span::isSameKindAs, reach);
    }

    /**
     * {@link Hairpin#spanning} and {@link Beam#spanning} ask this before the span exists, so the
     * range a span is judged over is the range adding it produces.
     *
     * @param sameKind it must agree with {@link Span#isSameKindAs} for the span that would be
     *                 added
     */
    Absorption absorption(ElementRange own, Predicate<Span<?>> sameKind, int reach) {
        var placed = spans.stream()
            .filter(sameKind)
            .map(existing -> new SpanAt<Span<?>>(existing, existing.range()))
            .toList();
        var range = own;

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
            .<Span<?>>map(SpanAt::span)
            .toList();

        return new Absorption(widened, absorbed);
    }

    /**
     * A copy, safe to hold while spans are removed.
     */
    private List<Span<?>> sameKindSpans(Span<?> span) {
        return spans.stream()
            .filter(span::isSameKindAs)
            .toList();
    }

    /**
     * @param span the span about to be added, whose endpoints both sit in this line
     */
    void removeOverlappingSameKind(Span<?> span) {
        var overlapsSpan = Span.overlapping(span.range());

        sameKindSpans(span).stream()
            .filter(existing -> overlapsSpan.test(existing.range()))
            .forEach(existing -> existing.removeReported(Strings.REPORT_REASON_REPLACED));
    }

    public void removeOverlapping(Class<? extends Span<?>> type, ElementRange range) {
        for (var span : findSpans(type, Span.overlapping(range))) {
            span.removeFrom();
        }
    }

    public <T extends Span<?>> List<T> findSpans(Class<T> type, Span.RangePredicate matches) {
        var result = new ArrayList<T>();

        for (var span : spans) {
            if (isMatch(span, type, matches)) {
                result.add(type.cast(span));
            }
        }

        return result;
    }

    /**
     * When several spans match, which one is returned is not defined.
     *
     * @return {@code null} when none matches
     */
    public <T extends Span<?>> @Nullable T findFirstSpan(Class<T> type, Span.RangePredicate matches) {
        for (var span : spans) {
            if (isMatch(span, type, matches)) {
                return type.cast(span);
            }
        }

        return null;
    }

    private boolean isMatch(Span<?> span, Class<? extends Span<?>> type, Span.RangePredicate matches) {
        return type.isInstance(span) && matches.test(span.range());
    }

    /**
     * Resolves no endpoint positions, unlike {@link #findSpans(Class, Span.RangePredicate)} with
     * an always-true predicate.
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

    public List<Span<?>> findSpans(Span.RangePredicate matches) {
        return spans.stream().filter(span -> matches.test(span.range())).toList();
    }

    public boolean hasSpan(Class<? extends Span<?>> type, Span.RangePredicate matches) {
        return findFirstSpan(type, matches) != null;
    }

    /**
     * Containment is not membership: a grace note strictly inside a span is covered by the beam
     * without joining it. To ask which beam an element belongs to, use {@link #findBeamJoining}.
     *
     * @return {@code null} when no beam in this line contains {@code elementIndex}
     */
    public @Nullable Beam findBeamAt(int elementIndex) {
        return findFirstSpan(Beam.class, Span.containing(elementIndex));
    }

    /**
     * {@code null} when the element belongs to no beam. Agrees with {@link Beam#members()}: both
     * apply the same membership rule.
     */
    public @Nullable Beam findBeamJoining(int elementIndex) {
        var beam = findBeamAt(elementIndex);

        if (beam == null || !Beam.joinsWithinSpan(line.getElement(elementIndex))) {
            return null;
        }

        return beam;
    }

    public boolean sameBeamAt(int firstIndex, int secondIndex) {
        var beamAtFirst = findBeamAt(firstIndex);

        //noinspection ObjectEquality
        return beamAtFirst != null && beamAtFirst == findBeamAt(secondIndex);
    }

    /**
     * @return {@code null} when no tuplet in this line contains {@code elementIndex}
     */
    public @Nullable Tuplet findTupletAt(int elementIndex) {
        return findFirstSpan(Tuplet.class, Span.containing(elementIndex));
    }

    /**
     * The editor's rule for where a text dynamic may go: on a hairpin's anchor or end element,
     * never under the wedge. The rule covers the bound, not only an element two hairpins share,
     * so deleting one of two back-to-back hairpins leaves a legal dynamic and nothing needs
     * cleaning up.
     */
    public boolean isInsideHairpin(int noteIndex) {
        return hasSpan(Hairpin.class, Span.strictlyContaining(noteIndex));
    }

    public boolean hasHairpin(Hairpin.Kind kind, Span.RangePredicate matches) {
        for (var span : spans) {
            if (kind.matches(span) && matches.test(span.range())) {
                return true;
            }
        }

        return false;
    }

    public boolean hasTrillOverlapping(ElementRange range) {
        return hasSpan(Trill.class, Span.overlapping(range));
    }

    public List<Ending> findEndings() {
        return findSpans(Ending.class);
    }
}
