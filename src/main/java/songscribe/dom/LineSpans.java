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

import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.Mutation;

/**
 * The spans drawn over one line's elements — beams, ties, trills, hairpins, tuplets and
 * endings — and everything that concerns them as a collection: the queries of
 * {@link SpanLookup}, the sweep that re-judges every span when an element changes, and the
 * merge rules that decide what two same-kind spans become. How the sweep, the per-kind
 * policies and the merge divide the work is in {@code docs/span-invalidation.md}.
 * <p>
 * One per {@link Line}, reached through {@link Line#spanRegistry()}, and the only writer of
 * the list it holds: a span enters or leaves only through {@link Span#addTo} and
 * {@link Span#removeFrom}, which end in {@link #add} and {@link #remove} here.
 * <p>
 * A span is never attached to a line: {@link Span#isIn} derives parentage from where its
 * endpoints sit, so a tie whose endpoints straddle a line boundary is in both lines, and
 * {@link #appendChild}/{@link #removeChild} keep both lines' registries holding it so every
 * sweep over a line's spans sees the half that belongs to it. The invariant is
 * one-directional on purpose — {@code span.isIn(L) ⟹ L.spanRegistry().getSpans()} contains the
 * span — because a span with both endpoints detached is in no line by the derived answer, yet
 * is still in the registry it was added to, which is exactly where it sat before.
 */
public final class LineSpans implements SpanLookup {

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

    /** Spans (beams, ties, trills, crescendo, diminuendo, tuplets, endings). */
    private final List<Span> spans = new ArrayList<>();

    /**
     * Live unmodifiable view of {@link #spans}, held once rather than wrapped per call:
     * every {@link SpanLookup} query iterates {@link #getSpans()}, and the wrapper tracks
     * later mutations of {@code spans} anyway.
     */
    private final List<Span> spansView = Collections.unmodifiableList(spans);

    LineSpans(Line line) {
        this.line = line;
    }

    /**
     * Returns an unmodifiable view of the spans in this line.
     */
    @Override
    public List<Span> getSpans() {
        return spansView;
    }

    @Override
    public SpanBound anchorIndexOf(Span span) {
        return boundOf(span.getAnchorElement());
    }

    @Override
    public SpanBound endIndexOf(Span span) {
        return boundOf(span.getEndElement());
    }

    /**
     * Returns the elements {@code span} covers in this line, from its anchor's position to its
     * end's position, resolved the same way {@link #anchorIndexOf} and {@link #endIndexOf}
     * resolve each endpoint. This is the query for a caller that needs the extent as a range —
     * to widen it, to test what overlaps it, or to index the elements under it; a caller that
     * has to tell an endpoint in a neighboring line from an absent one asks the two bound
     * queries instead.
     *
     * @param span the span to place, whether or not it is in this registry
     * @return the inclusive range under {@code span}, or {@code null} when either endpoint
     *     does not sit in this line — a cross-line tie's far endpoint, a detached endpoint, or
     *     one in a line the song no longer holds
     */
    public @Nullable ElementRange rangeOf(Span span) {
        if (anchorIndexOf(span) instanceof SpanBound.At(var anchorIndex)
                && endIndexOf(span) instanceof SpanBound.At(var endIndex)) {
            return new ElementRange(anchorIndex, endIndex);
        }

        return null;
    }

    /**
     * As {@link #rangeOf}, for a caller whose contract already places both of {@code span}'s
     * endpoints in this line.
     *
     * @param span the span to place; both endpoints in this line
     * @return the inclusive range under {@code span}
     * @throws IllegalArgumentException if either endpoint does not sit in this line
     */
    ElementRange requireRangeOf(Span span) {
        var range = rangeOf(span);

        if (range == null) {
            throw new IllegalArgumentException("Both endpoints of " + span + " must be in this line");
        }

        return range;
    }

    /**
     * Resolves one endpoint of a span against this line, for both accessors above.
     * <p>
     * Direction for an endpoint in another line comes from where the two lines actually sit in
     * the song, not from which endpoint is being asked about: an anchor is not necessarily
     * behind us just because it is the earlier of the pair.
     * <p>
     * Only the two neighbours get a direction. A span reaching further than that describes a
     * jump nothing can draw — the two halves would run off opposite edges of lines with a
     * whole line of unrelated music between them, each pointing at nothing — so it resolves
     * to {@link SpanBound#ABSENT}, which stops this half drawing at all.
     * {@link Song#removeSpansBetweenNonAdjacentLines} drops such a span when an insertion or
     * deletion is what broke adjacency, but nothing checks adjacency when a span is created:
     * the MusicXML reader carries one pending tie start across a whole part, so a file whose
     * {@code <tied>} start and stop are separated by a complete line produces one. This is the
     * single place every query, layout, export and render path resolves an endpoint through,
     * so it is the one place that has to say no.
     * <p>
     * {@link Song#removeLine} leaves a deleted line's elements pointing at it, so an endpoint
     * can also name a line the song no longer holds; that has no position at all and is
     * likewise {@link SpanBound#ABSENT}.
     * <p>
     * Only a cross-line endpoint pays for the two {@link Line#index} scans, and only
     * ties are ever cross-line.
     */
    private SpanBound boundOf(@Nullable StaffElement endpoint) {
        if (endpoint == null) {
            return SpanBound.ABSENT;
        }

        var endpointLine = endpoint.getParentLine();

        if (endpointLine == null) {
            return SpanBound.ABSENT;
        }

        if (endpointLine == line) {
            // The stored-parentage invariant guarantees this line's elements contain it.
            return new SpanBound.At(line.getElementIndex(endpoint));
        }

        // Guarded before the distance is taken: a line the song no longer holds must not
        // read as one away from line 0 and be treated as an adjacent neighbour.
        var song = line.getSong();

        if (!song.contains(line) || !song.contains(endpointLine)) {
            return SpanBound.ABSENT;
        }

        var thisLineIndex = line.index();
        var endpointLineIndex = endpointLine.index();

        return switch (endpointLineIndex - thisLineIndex) {
            case -1 -> SpanBound.BEFORE_LINE;
            case 1 -> SpanBound.AFTER_LINE;
            default -> SpanBound.ABSENT;
        };
    }

    // ========================================================================
    // List writes
    // ========================================================================

    /**
     * Returns the line holding an endpoint of {@code span} that is not this line, or
     * {@code null} when neither endpoint is in another line.
     * <p>
     * A span reaches {@link #appendChild} either with both endpoints in this line, with
     * both detached, or — for a cross-line tie — with one endpoint here and one in the
     * adjacent line. This answers the third case, so that tie lands in both lines' lists.
     */
    private @Nullable Line otherLineOf(Span span) {
        var anchorLine = span.getAnchorLine();

        if (anchorLine != null && anchorLine != line) {
            return anchorLine;
        }

        var endLine = span.getEndLine();

        if (endLine != null && endLine != line) {
            return endLine;
        }

        return null;
    }

    /**
     * Appends {@code element} to {@code spans} — this line's, and the other line of the
     * pair when the span's endpoints straddle a boundary. Call from inside an applyChange
     * mutator lambda, so the list write moves with the recorded change.
     * <p>
     * The other line only gains the span if it is not already holding it. A span whose two
     * endpoints share a line, added to a <em>different</em> line, names its own line as the
     * other one and would otherwise land there twice — one copy from wherever it was first
     * added, one from here — leaving {@link #removeChild}, which takes out a single copy per
     * list, unable to get rid of it.
     */
    private void appendChild(Span element) {
        spans.add(element);

        var otherLine = otherLineOf(element);

        if (otherLine != null && !otherLine.spanRegistry().spans.contains(element)) {
            otherLine.spanRegistry().spans.add(element);
        }
    }

    /**
     * Removes {@code element} from {@code spans} — this line's, and the other line of the
     * pair, so a cross-line span leaves both halves together. Takes the index its caller
     * already located in this line, rather than scanning the list a second time.
     */
    private void removeChild(Span element, int index) {
        spans.remove(index);

        var otherLine = otherLineOf(element);

        if (otherLine != null) {
            otherLine.spanRegistry().spans.remove(element);
        }
    }

    /**
     * Points every span endpoint that is {@code oldElement} at {@code newElement}, so
     * {@link Span#getAnchorElementIndex()}/{@link Span#getEndElementIndex()} remain valid
     * after {@link Line#setElement} swaps the element out. Runs inside that mutator, on
     * replay too: it is self-inverting, and the recorded batch carries no record of it.
     */
    void repoint(StaffElement oldElement, StaffElement newElement) {
        for (var span : spans) {
            if (span.getAnchorElement() == oldElement) {
                span.setAnchorElement(newElement);
            }

            if (span.getEndElement() == oldElement) {
                span.setEndElement(newElement);
            }
        }
    }

    /**
     * Records {@code addition} and appends {@code span} inside it. The generic primitive
     * every kind's {@link Span#addTo} ends in, after applying its own policy.
     */
    void add(Span span, Mutation addition) {
        line.applyChange(addition, () -> appendChild(span));
    }

    /**
     * Records {@code removal} and removes {@code span} inside it, or does nothing at all when
     * {@code span} is not in this registry. The generic primitive every kind's
     * {@link Span#removeFrom} ends in, and the one place the absent-span early return exists:
     * a removal takes a cross-line span out of both lines' lists together, so a second call
     * on either line finds nothing left to remove and records nothing.
     */
    void remove(Span span, Mutation removal) {
        var index = spans.indexOf(span);

        if (index < 0) {
            return;
        }

        line.applyChange(removal, () -> removeChild(span, index));
    }

    // ========================================================================
    // The sweep behind every element change
    // ========================================================================

    /**
     * A hairpin paired with the span it will occupy once a pending deletion is applied,
     * as a range over the elements that survive that deletion.
     */
    private record HairpinSpan(Hairpin hairpin, ElementRange range) {
    }

    /**
     * Asks every span on this line what should happen to it when {@code change} lands, and
     * carries out those answers — the single sweep behind every element insertion,
     * replacement, deletion and in-place modification.
     * <p>
     * Called <b>before</b> the change is applied, so each span judges itself against the
     * projection rather than the mutated line, and so the companion span mutations are
     * recorded before the primary element mutation: undo replays a step in reverse, and
     * has to restore the element before the spans that point at it.
     * <p>
     * Does nothing while the song is replaying undo or redo: the recorded batch already
     * carries every companion span mutation, and re-deriving them would apply them twice.
     * <p>
     * Every span is asked first and acted on afterwards. Acting inside the loop would both
     * mutate the {@code spans} list being walked and let one span's removal change what a
     * later span is judged against.
     * <p>
     * A {@link SpanOutcome.Reshape} is the one answer that cannot be acted on span by span:
     * same-type hairpins the change leaves with nothing between them become one, which no
     * span can decide on its own because none of them knows its siblings. The reshaped
     * spans are therefore grouped by hairpin kind and handed to {@link #mergeAdjacentSpans},
     * which expresses each reshape as a tracked removal plus a tracked addition of a copy,
     * since a span has no modification mutation of its own.
     *
     * @param change the pending change, not yet applied to this line
     */
    void sweep(ElementChange change) {
        if (spans.isEmpty() || line.getSong().isReplaying()) {
            return;
        }

        var doomed = new ArrayList<Span>();
        var reshapedByKind = new LinkedHashMap<Hairpin.Kind, List<HairpinSpan>>();

        for (var span : spans) {
            switch (span.outcomeFor(change, line)) {
                case SpanOutcome.Simple.KEEP -> { }
                case SpanOutcome.Simple.REMOVE -> doomed.add(span);
                case SpanOutcome.Reshape reshape -> collectReshape(reshapedByKind, span, reshape);
            }
        }

        doomed.forEach(span -> span.removeFrom(line));

        // The projection is asked for per kind rather than hoisted: there are at most two
        // kinds, and a change nothing reshaped must not build one at all.
        for (var hairpinSpans : reshapedByKind.values()) {
            mergeAdjacentSpans(hairpinSpans, change.projectedElements());
        }
    }

    /**
     * Files {@code reshape} under {@code span}'s kind, ready for the merge pass.
     * <p>
     * Only a hairpin can reshape: the merge that consumes these answers replaces a run with
     * a copy of a hairpin, and there is nothing it could do with any other kind of span.
     */
    private static void collectReshape(
        Map<Hairpin.Kind, List<HairpinSpan>> reshapedByKind,
        Span span,
        SpanOutcome.Reshape reshape
    ) {
        if (!(span instanceof Hairpin hairpin)) {
            throw new IllegalStateException(
                "Only a hairpin may answer Reshape, but " + span.getClass().getSimpleName() + " did");
        }

        reshapedByKind
            .computeIfAbsent(hairpin.getKind(), kind -> new ArrayList<>())
            .add(new HairpinSpan(hairpin, reshape.range()));
    }

    /**
     * Replaces each run of same-type hairpin {@code hairpinSpans} that the deletion leaves
     * touching with a single hairpin covering the run. Runs of one whose endpoints both
     * survive are left untouched, so a deletion that misses a hairpin emits no hairpin
     * mutations.
     *
     * @param hairpinSpans all post-deletion spans of one hairpin type, in any order
     */
    private void mergeAdjacentSpans(List<HairpinSpan> hairpinSpans, List<? extends StaffElement> survivors) {
        hairpinSpans.sort(Comparator.comparingInt((HairpinSpan span) -> span.range().begin()));

        var run = new ArrayList<HairpinSpan>();
        var runEnd = -1;

        for (var span : hairpinSpans) {
            var range = span.range();

            // One surviving element between two hairpins is still a break in the gesture.
            if (!run.isEmpty() && range.begin() > runEnd + SPAN_ADJACENCY_REACH) {
                applySpanRun(run, runEnd, survivors);
                run.clear();
                runEnd = -1;
            }

            run.add(span);
            runEnd = Math.max(runEnd, range.end());
        }

        applySpanRun(run, runEnd, survivors);
    }

    /**
     * Reshapes the hairpins in {@code run} into one hairpin spanning the run's surviving
     * endpoints, carrying over the first one's user offsets.
     *
     * @param run    the hairpins to replace, ordered by their post-deletion start
     * @param runEnd the run's last post-deletion index
     */
    private void applySpanRun(List<HairpinSpan> run, int runEnd, List<? extends StaffElement> survivors) {
        var first = run.getFirst().hairpin();
        var anchorElement = survivors.get(run.getFirst().range().begin());
        var endElement = survivors.get(runEnd);

        if (run.size() == 1
                && first.getAnchorElement() == anchorElement
                && first.getEndElement() == endElement) {
            return;
        }

        // Removals precede the addition so reverse-order undo drops the reshaped hairpin
        // before restoring the originals, and run in reverse list order so undo restores
        // them in the order they were in, leaving the document identical to before.
        var doomed = new ArrayList<>(run.stream().map(HairpinSpan::hairpin).toList());
        doomed.sort(Comparator.<Hairpin>comparingInt(spans::indexOf).reversed());
        doomed.forEach(hairpin -> hairpin.removeFrom(line));

        var reshaped = first.copy(anchorElement, endElement);
        reshaped.addTo(line);
    }

    // ========================================================================
    // Displacement policies the span kinds call from addTo
    // ========================================================================

    /**
     * Absorbs same-type spans overlapping {@code span}'s endpoints: widens the span to
     * cover them, then removes every {@code type} span fully subsumed by the widened
     * range through its own {@link Span#removeFrom}. The tracked removals are emitted
     * before the widened span's addition so undo restores the original spans after
     * removing the merged one.
     *
     * @param span  the span about to be added, whose endpoints both sit in this line
     * @param type  the kind of span that may be absorbed — the concrete class, so a
     *              crescendo never absorbs a diminuendo
     * @param reach how far past an endpoint, in elements, an existing span may sit and
     *              still be absorbed: {@link #SPAN_ADJACENCY_REACH} for a kind whose two
     *              touching spans are one gesture, {@code 0} for a kind that absorbs only
     *              what it overlaps
     */
    <R extends Span> void mergeOverlapping(R span, Class<? extends R> type, int reach) {
        var own = requireRangeOf(span);

        // Only a span with both endpoints in this line is a merge candidate: the widening
        // below is arithmetic on these positions, then an index into this line's elements,
        // and neither is meaningful for an endpoint that sits elsewhere — letting one
        // through would drag the widened span out to a neighbor's position, or past the end
        // of the list. Resolved once, before the setters below move the very indices they
        // are compared against.
        var placed = new ArrayList<PlacedSpan<R>>();

        for (var existing : findSpans(type)) {
            var range = rangeOf(existing);

            if (range != null) {
                placed.add(new PlacedSpan<>(existing, range));
            }
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
            span.setAnchorElement(line.getElement(mergedAnchorIdx));
        }

        if (mergedEndIdx != own.end()) {
            span.setEndElement(line.getElement(mergedEndIdx));
        }

        placed.stream()
            .filter(candidate -> candidate.range().begin() >= mergedAnchorIdx
                && candidate.range().end() <= mergedEndIdx)
            .forEach(candidate -> candidate.span().removeFrom(line));
    }

    /** A span of this line paired with the range it covers in it. */
    private record PlacedSpan<R extends Span>(R span, ElementRange range) {
    }

    /**
     * Removes every tuplet overlapping {@code range}, each through its own
     * {@link Span#removeFrom}, so each removal is that tuplet's own recorded mutation and
     * posts under the same bracket rule.
     *
     * @param range the elements a tuplet must share at least one of to be removed
     */
    public void removeOverlappingTuplets(ElementRange range) {
        removeOverlapping(Tuplet.class, range);
    }

    /**
     * Removes every trill overlapping {@code range}, each through its own
     * {@link Span#removeFrom}, so each removal is that trill's own recorded mutation and
     * posts under the same bracket rule. A trill whose anchor is detached is still found and
     * removed, so a half-detached trill can be cleaned up.
     *
     * @param range the elements a trill must share at least one of to be removed
     */
    public void removeTrillsOverlapping(ElementRange range) {
        removeOverlapping(Trill.class, range);
    }

    /**
     * Removes every {@code type} span overlapping {@code range}, each through its own
     * {@link Span#removeFrom} so each removal is its own recorded mutation.
     */
    private void removeOverlapping(Class<? extends Span> type, ElementRange range) {
        for (var span : findSpans(type, Span.overlapping(range))) {
            span.removeFrom(line);
        }
    }
}
