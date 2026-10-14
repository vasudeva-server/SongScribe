/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;

/**
 * Abstract base class for elements that span multiple elements.
 *
 * <p>A bare {@link StaffElement} becomes an endpoint only through {@link #narrowEndpoint}, which
 * checks it against the class the kind passed to the constructor.
 */
public abstract sealed class Span<E extends StaffElement> extends LineElement implements LineContent
    permits Beam, Hairpin, Tuplet, Trill, Ending {

    static final int MIN_PITCHED_NOTES = 2;

    private final Class<E> endpointType;

    private E anchorElement;

    private E endElement;

    protected Span(Class<E> endpointType, E anchorElement, E endElement) {
        this.endpointType = endpointType;
        this.anchorElement = anchorElement;
        this.endElement = endElement;
    }

    public final E getAnchorElement() {
        return anchorElement;
    }

    /**
     * @param anchorElement an element of this span's line
     */
    void setAnchorElement(E anchorElement) {
        this.anchorElement = anchorElement;
    }

    public final E getEndElement() {
        return endElement;
    }

    /**
     * @param endElement an element of this span's line
     */
    void setEndElement(E endElement) {
        this.endElement = endElement;
    }

    public final E narrowEndpoint(StaffElement element) {
        if (!endpointType.isInstance(element)) {
            throw new IllegalArgumentException(
                getClass().getSimpleName() + " cannot end on " + element);
        }

        return endpointType.cast(element);
    }

    private @Nullable Line getAnchorLine() {
        return anchorElement.getParentLine();
    }

    protected final Line line() {
        var line = getAnchorLine();

        if (line == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " anchor is in no line: " + this);
        }

        return line;
    }

    // ========================================================================
    // Entering and leaving a line's span registry
    // ========================================================================

    /**
     * Adds this span to its line's span registry, first displacing whatever same-kind spans
     * this kind's {@link #displacement()} says it may not coexist with.
     * <p>
     * Each displacement is its own recorded mutation, emitted before this span's addition so
     * undo restores the displaced spans after removing this one. Displacement is skipped while
     * {@link Song#isReplaying()}: the recorded batch already carries the companion mutations,
     * and re-deriving them would apply them twice.
     * <p>
     * An absorbing displacement widens this span's endpoints over the same-kind spans it absorbs
     * before it is added, which is what a merge is. A hairpin from {@link Hairpin#spanning},
     * added before any other span joins its line, already covers the range its absorption
     * reaches, so the displacement moves neither of its endpoints and only removes the hairpins
     * it absorbs.
     *
     * @effects posts one {@link SpanRemoval} per displaced span, then a {@link SpanAddition} for
     *     this span, through the line's song, and appends this span to the registry; under
     *     {@link Displacement#REPLACE_OVERLAPPING}, each displaced span is also reported, through
     *     {@link #removeReported} with {@link Strings#REPORT_REASON_REPLACED}; what is
     *     recorded, and what is thrown outside a bracket, is {@link Song#applyChange}'s promise
     * @throws IllegalStateException under the conditions {@link #line} throws
     */
    public final void addTo() {
        var line = line();
        var spans = line.spanRegistry();

        if (!line.getSong().isReplaying()) {
            var displacement = displacement();

            switch (displacement) {
                case COEXIST -> { }
                case REPLACE_OVERLAPPING -> spans.removeOverlappingSameKind(this);
                case ABSORB_OVERLAPPING -> spans.mergeOverlapping(this, 0);
                case ABSORB_ADJACENT -> spans.mergeOverlapping(this, LineSpans.SPAN_ADJACENCY_REACH);
            }
        }

        spans.add(this);
    }

    protected abstract Displacement displacement();

    /**
     * An override may narrow the test but must still require the same concrete class.
     */
    boolean isSameKindAs(Span<?> other) {
        return other.getClass() == getClass();
    }

    /**
     * Removes this span from its line's span registry if it is there, recording its
     * removal, and is a no-op otherwise. Requires that the span's anchor is in a line.
     * <p>
     * The no-op is a promise, not an accident: a second call finds nothing left to remove and
     * records nothing. A caller that cannot cheaply tell whether an earlier step
     * already removed the span — a sweep that doomed it, a merge that subsumed it, the paste
     * reconciliation — may therefore call this unconditionally. A raw list removal would drop
     * the span with no record and make undo of the enclosing operation lossy, which is why
     * this is the only route out.
     * <p>
     * This is for a removal the user asked for. A removal the user did not directly request
     * goes through {@link #removeReported}, which reports it and then calls this.
     *
     * @effects when this span is in the registry, posts a {@link SpanRemoval} through the line's
     *     song and removes the span; otherwise nothing. What is recorded, and what is thrown
     *     outside a bracket, is {@link Song#applyChange}'s promise
     */
    public final void removeFrom() {
        line().spanRegistry().remove(this);
    }

    /**
     * @param change not yet applied to {@code change.line()}, so judge against the change's own
     *     terms or {@link ElementChange#projectedElements}, never the line as though it landed
     */
    public SpanOutcome outcomeFor(ElementChange change) {
        return outcomeWhen(
            change instanceof ElementChange.Deletion deletion && isEndpointDeletedBy(deletion),
            Strings.REPORT_REASON_ENDPOINT_DELETED);
    }

    protected final boolean isEndpointDeletedBy(ElementChange.Deletion deletion) {
        var deletedElements = deletion.deletedElements();

        return deletedElements.contains(anchorElement) || deletedElements.contains(endElement);
    }

    protected static SpanOutcome outcomeWhen(boolean invalidated, String reasonKey) {
        return invalidated ? new SpanOutcome.Remove(reasonKey) : SpanOutcome.Keep.INSTANCE;
    }

    /**
     * The key's value is a MessageFormat pattern taking the entry count as {@code {0}}.
     */
    protected abstract String reportHeadingKey();

    private ChangeLines changeLines() {
        return ChangeLines.on(line());
    }

    public RecordedChange reportedAs(String reasonKey) {
        return reportedAt(reasonKey, changeLines());
    }

    /**
     * For a span whose anchor is not in a line, where {@link #reportedAs} cannot name its line.
     *
     * @param where {@code null} for an entry with no place
     */
    public RecordedChange reportedAt(String reasonKey, @Nullable ChangeLines where) {
        return new RecordedChange(reportHeadingKey(), Strings.get(reasonKey), where);
    }

    /**
     * Every removal the user did not directly request goes through this method, so none is
     * silent. {@link #removeFrom} is for a removal the user asked for, and for a span a merge
     * absorbs.
     * <p>
     * Unlike {@link #removeFrom}, a second call is not a no-op: it reports again. The caller
     * holds a span that is in its line's registry.
     */
    public final void removeReported(String reasonKey) {
        line().getSong().report(reportedAs(reasonKey));
        removeFrom();
    }

    private int requireIndexInLine(StaffElement element) {
        var line = element.getParentLine();

        if (line == null) {
            throw new IllegalStateException(
                getClass().getSimpleName() + " endpoint " + element + " is in no line");
        }

        return line.getElementIndex(element);
    }

    public int requireAnchorElementIndex() {
        return requireIndexInLine(anchorElement);
    }

    public int requireEndElementIndex() {
        return requireIndexInLine(endElement);
    }

    /**
     * Computed on each call, so an edit to the line between two calls can give two different
     * ranges.
     */
    public final ElementRange range() {
        return new ElementRange(requireAnchorElementIndex(), requireEndElementIndex());
    }

    /**
     * {@value #MIN_PITCHED_NOTES} pitched notes are the fewest a {@link Hairpin} or a
     * {@link Tuplet} may hold. A grace note does not count: it and its host count once.
     */
    static boolean holdsEnoughPitchedNotes(List<? extends StaffElement> elements, ElementRange range) {
        var count = 0;

        for (var index = range.begin(); index <= range.end(); index++) {
            if (elements.get(index).getType().isPitchedNote()) {
                count++;
            }
        }

        return count >= MIN_PITCHED_NOTES;
    }

    @FunctionalInterface
    public interface RangePredicate {

        boolean test(ElementRange spanRange);
    }

    /**
     * A predicate matching a span whose anchor-to-end range includes {@code elementIndex}.
     *
     * @param elementIndex an index in the span's line
     * @return a predicate that is {@code true} for a range containing {@code elementIndex}
     */
    public static RangePredicate containing(int elementIndex) {
        return spanRange -> spanRange.contains(elementIndex);
    }

    /**
     * A predicate matching a span sharing at least one element with {@code range}: its anchor at
     * or before {@code range}'s last element and its end at or after {@code range}'s first.
     *
     * @param range the elements to test against, as indices in the span's line
     * @return a predicate that is {@code true} for a range sharing an element with {@code range}
     */
    public static RangePredicate overlapping(ElementRange range) {
        return spanRange -> spanRange.overlaps(range);
    }

    /**
     * A predicate matching a span that overlaps {@code range} by more than a single shared
     * endpoint: its end falls past the range's first element and its anchor falls before
     * the range's last.
     * <p>
     * Two hairpins may meet — one ends on the element the next begins on, and the
     * wedges back away from that shared column (LilyPond's back-to-back rule). More
     * than that one element in common is a genuine collision.
     *
     * @param range the elements to test against, as indices in the span's line
     * @return a predicate that is {@code true} for a range overlapping {@code range} by more than
     *     one shared endpoint
     */
    public static RangePredicate overlappingBeyondEndpoint(ElementRange range) {
        return spanRange ->
            spanRange.overlaps(range)
                && spanRange.end() != range.begin()
                && spanRange.begin() != range.end();
    }

    /**
     * A predicate matching a span that covers {@code elementIndex} strictly inside its
     * range — the bound elements themselves do not count.
     * <p>
     * A text dynamic may sit on a hairpin's bound, where the wedge pads away from it, but
     * never under the wedge, so the editor asks about the strict interior rather than the
     * inclusive range.
     *
     * @param elementIndex an index in the span's line
     * @return a predicate that is {@code true} for a range with {@code elementIndex} strictly
     *     between its bounds
     */
    public static RangePredicate strictlyContaining(int elementIndex) {
        return spanRange -> spanRange.containsExcludingBounds(elementIndex);
    }

    /**
     * A predicate matching a span whose anchor is exactly {@code range}'s first element and
     * whose end is exactly its last.
     *
     * @param range the range to match, as indices in the span's line
     * @return a predicate that is {@code true} for a range equal to {@code range}
     */
    public static RangePredicate exactly(ElementRange range) {
        return spanRange -> spanRange.equals(range);
    }

    /**
     * A predicate matching a span whose anchor is the element at {@code elementIndex}.
     *
     * @param elementIndex an index in the span's line
     * @return a predicate that is {@code true} for a range beginning at {@code elementIndex}
     */
    public static RangePredicate anchoredAt(int elementIndex) {
        return spanRange -> spanRange.begin() == elementIndex;
    }

    /**
     * A predicate matching a span whose end is the element at {@code elementIndex}.
     *
     * @param elementIndex an index in the span's line
     * @return a predicate that is {@code true} for a range ending at {@code elementIndex}
     */
    public static RangePredicate endingAt(int elementIndex) {
        return spanRange -> spanRange.end() == elementIndex;
    }

    /**
     * A predicate matching a span that an insertion at {@code target} lands inside: its anchor
     * lies before the insertion index, and its end lies at or after the first element the
     * insertion leaves standing after itself.
     * <p>
     * A span wholly inside {@code target}'s deleted range does not match, since the deletion
     * takes its endpoints; neither does a span clear of the insertion.
     *
     * @param target where a run is inserted, on the line as it stands before the insertion
     * @return a predicate that is {@code true} for a range that begins before
     *     {@code target.insertIndex()} and ends at or after the index after
     *     {@code target}'s deleted range, or at or after {@code target.insertIndex()} when it
     *     deletes nothing
     */
    public static RangePredicate straddling(StaffElementRun.InsertionTarget target) {
        var insertIndex = target.insertIndex();
        var deleteRange = target.deleteRange();
        var firstIndexAfter = deleteRange == null ? insertIndex : deleteRange.range().end() + 1;

        return spanRange -> spanRange.begin() < insertIndex && spanRange.end() >= firstIndexAfter;
    }

    /**
     * Creates a copy of this span anchored to the given elements, carrying all of this span's
     * user state through {@link LineElement#copyBaseStateFrom}. Does not copy derived caches.
     *
     * @param newAnchor The anchor element for the copy
     * @param newEnd    The end element for the copy
     * @return A new span of the same concrete type as this one, with this span's user state
     */
    public final Span<E> copy(E newAnchor, E newEnd) {
        var copy = createCopy(newAnchor, newEnd);
        copy.copyBaseStateFrom(this);
        return copy;
    }

    /**
     * The new instance has the state that defines its kind and no user state, which
     * {@link #copy} then copies through {@link LineElement#copyBaseStateFrom}.
     */
    protected abstract Span<E> createCopy(E newAnchor, E newEnd);
}
