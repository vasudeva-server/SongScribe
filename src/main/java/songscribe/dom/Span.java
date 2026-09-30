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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;

/**
 * Abstract base class for elements that span multiple elements.
 * <p>
 * Spans include beams, trills, crescendo/diminuendo hairpins,
 * tuplet brackets, and first/second endings.
 * <p>
 * Each span has:
 * <ul>
 *   <li>An anchor element (first element in the range)</li>
 *   <li>Methods to determine the range extent</li>
 * </ul>
 *
 * <p><b>Endpoint typing.</b> A kind states its endpoint type as the type argument {@code E} and
 * passes that type's class to the constructor. The constructor, the two getters and
 * {@link #copy} are typed by {@code E}, so the compiler keeps every other endpoint out. Three
 * places turn a bare {@link StaffElement} into an endpoint — {@code LineSpans.repoint},
 * {@code LineSpans.mergeOverlapping} and {@code Fragment.cloneSpans} — and each narrows it
 * through {@link #narrowEndpoint}, which checks it against the class the kind passed.
 * {@link Beam} ({@code Span<NoteElement>}) is the one kind whose endpoint type is narrower than
 * {@link StaffElement}.
 *
 * @param <E> the type every endpoint of this kind has
 */
public abstract sealed class Span<E extends StaffElement> extends LineElement implements LineContent
    permits Beam, Hairpin, Tuplet, Trill, Ending {

    /** The class of {@code E}, which {@link #narrowEndpoint} checks an element against. */
    private final Class<E> endpointType;

    /** The first element in this range. */
    private E anchorElement;

    /** The last element in this range. */
    private E endElement;

    /**
     * Creates a span reaching from the anchor element to the end element.
     *
     * @param endpointType  The class of this kind's endpoint type
     * @param anchorElement The first element in the range
     * @param endElement    The last element in the range
     */
    protected Span(Class<E> endpointType, E anchorElement, E endElement) {
        this.endpointType = endpointType;
        this.anchorElement = anchorElement;
        this.endElement = endElement;
    }

    /**
     * Returns the first element in this range.
     *
     * @return the anchor element
     */
    public final E getAnchorElement() {
        return anchorElement;
    }

    /**
     * Sets the first element in this range.
     */
    void setAnchorElement(E anchorElement) {
        this.anchorElement = anchorElement;
    }

    /**
     * Returns the last element in this range.
     *
     * @return the end element
     */
    public final E getEndElement() {
        return endElement;
    }

    /**
     * Sets the last element in this range.
     */
    void setEndElement(E endElement) {
        this.endElement = endElement;
    }

    /**
     * Returns {@code element} as this kind's endpoint type, for a caller holding a bare
     * {@link StaffElement} it is about to make an endpoint of this span.
     *
     * @param element the element to become an endpoint
     * @return {@code element}, typed as this kind's endpoint type
     * @throws IllegalArgumentException if {@code element} is not an instance of this kind's
     *     endpoint type; the message names this span's kind and {@code element}
     */
    public final E narrowEndpoint(StaffElement element) {
        if (!endpointType.isInstance(element)) {
            throw new IllegalArgumentException(
                getClass().getSimpleName() + " cannot end on " + element);
        }

        return endpointType.cast(element);
    }

    /**
     * The line the anchor element belongs to, or {@code null} when the anchor sits in no line.
     */
    private @Nullable Line getAnchorLine() {
        return anchorElement.getParentLine();
    }

    /**
     * The line this span lies in: its anchor's line. A span never crosses a line, so the
     * anchor's line is the end's too.
     *
     * @return the anchor's line
     * @throws IllegalStateException if the anchor sits in no line, which is the case only for a
     *     span outside every registry, such as a clipboard span over cloned elements
     */
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
     * Adds this span to {@code line}'s span registry, first displacing whatever same-kind spans
     * this kind's {@link #displacement()} says it may not coexist with.
     * <p>
     * Each displacement is its own recorded mutation, emitted before this span's addition so
     * undo restores the displaced spans after removing this one. Displacement is skipped while
     * {@link Song#isReplaying()}: the recorded batch already carries the companion mutations,
     * and re-deriving them would apply them twice. An absorbing displacement may widen this
     * span's endpoints before it is added, which is what a merge is.
     *
     * @param line the line this span is being added to; both of this span's endpoints sit in it
     * @effects posts one {@link SpanRemoval} per displaced span, then a {@link SpanAddition} for
     *     this span, through the line's song, and appends this span to the registry; what is
     *     recorded, and what is thrown outside a bracket, is {@link Song#applyChange}'s promise
     */
    public final void addTo(Line line) {
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

    /**
     * What adding a span of this kind does to the same-kind spans already on its line. One
     * constant per kind; {@link #addTo} is the one reader.
     *
     * @return this kind's displacement policy
     */
    protected abstract Displacement displacement();

    /**
     * Whether {@code other} is a span of the same kind as this one, the test every
     * {@link Displacement} applies. Here the test is the same concrete class; a subclass may
     * override it to narrow the test further, and an override must still require the same
     * concrete class.
     *
     * @return {@code true} when adding this span may displace {@code other}
     */
    boolean isSameKindAs(Span<?> other) {
        return other.getClass() == getClass();
    }

    /**
     * Removes this span from {@code line}'s span registry if it is there, recording its
     * removal, and is a no-op otherwise.
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
     * @param line the line this span is being removed from
     * @effects when this span is in the registry, posts a {@link SpanRemoval} through the line's
     *     song and removes the span; otherwise nothing. What is recorded, and what is thrown
     *     outside a bracket, is {@link Song#applyChange}'s promise
     */
    public final void removeFrom(Line line) {
        line.spanRegistry().remove(this);
    }

    /**
     * Decides what should happen to this span when {@code change} lands on its line,
     * {@code change.line()}. This is the one hook the sweep ({@link LineSpans#sweep}) puts a
     * change to; a kind with a rule of its own overrides it, and states that rule as one switch
     * over the change.
     * <p>
     * <b>The decision is made before the change lands.</b> {@code change.line()} is the line
     * being changed, still in its pre-change state; every judgement must be made against the
     * change's own terms or against {@link ElementChange#projectedElements}, which is what the
     * line will look like afterwards, and never by reading the line as though the change had
     * landed. Undo replays a step's mutations in reverse, so the companion span
     * mutation this answer produces has to be recorded <em>before</em> the primary element
     * mutation — deciding after the fact would record a span already re-pointed at the new
     * element, and undo would restore it broken.
     * <p>
     * <b>A replacement re-points, it does not delete.</b> {@link Line#replace} points any
     * span whose anchor or end was a replaced element at that element's replacement. So under
     * {@link ElementChange.Replacement}, a span whose endpoint is a replaced element must be
     * read as having that endpoint become its replacement
     * ({@link ElementChange.Replacement#replacementOf}) — <em>not</em> as having lost an
     * endpoint. {@link ElementChange#projectedEndpointIndexOf} reads an endpoint this way.
     * Reading the replaced element as deleted would silently remove every span whose endpoint
     * is edited.
     * <p>
     * The default is the rule every kind shares and the whole of {@link Trill}'s:
     * a deletion that takes an endpoint ({@link #isEndpointDeletedBy}) removes the span, for the
     * reason {@link Strings#DIALOG_REPORT_REASON_ENDPOINT_DELETED}, and every other change keeps
     * it.
     * <p>
     * A span either survives a change or it does not, which is all {@link SpanOutcome} can say.
     * A hairpin is the one kind that can survive in a different shape, and the sweep asks it
     * that wider question through {@link Hairpin#hairpinOutcomeFor(ElementChange)} instead.
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return a {@link SpanOutcome.Remove} carrying the key of the reason this span's rule gives
     *     when this span cannot survive {@code change}, otherwise {@link SpanOutcome.Keep#INSTANCE}
     */
    public SpanOutcome outcomeFor(ElementChange change) {
        return removeWhen(
            change instanceof ElementChange.Deletion deletion && isEndpointDeletedBy(deletion),
            Strings.DIALOG_REPORT_REASON_ENDPOINT_DELETED);
    }

    /**
     * Whether {@code deletion} takes this span's anchor or its end — the rule every kind
     * shares, since a span cannot be drawn without both endpoints.
     *
     * @return {@code true} when the deleted elements include the anchor or the end
     */
    protected final boolean isEndpointDeletedBy(ElementChange.Deletion deletion) {
        var deletedElements = deletion.deletedElements();

        return deletedElements.contains(anchorElement) || deletedElements.contains(endElement);
    }

    /**
     * The {@link #outcomeFor} answer for a verdict stated as a boolean.
     *
     * @param invalidated whether the change breaks this span
     * @param reasonKey   the {@code Strings} key of the reason the removal is reported with;
     *                    unused when {@code invalidated} is {@code false}
     * @return a {@link SpanOutcome.Remove} carrying {@code reasonKey} when {@code invalidated},
     *     otherwise {@link SpanOutcome.Keep#INSTANCE}
     */
    protected static SpanOutcome removeWhen(boolean invalidated, String reasonKey) {
        return invalidated ? new SpanOutcome.Remove(reasonKey) : SpanOutcome.Keep.INSTANCE;
    }

    /**
     * The {@code Strings} key of the heading a removed span of this kind is reported under, such
     * as "beams were removed". Its value is a MessageFormat pattern taking the count of entries
     * under the heading as {@code {0}}. One constant per kind; {@link #reportedAs} is the reader.
     *
     * @return this kind's removal heading key
     */
    protected abstract String reportHeadingKey();

    /**
     * The elements this span covers, as locations in the song: from the anchor to the end, both
     * inclusive, resolved through the anchor's line and that line's index in its song. A span
     * never crosses a line, so both locations name the same line.
     * <p>
     * The span need not be in a registry. It answers for any span whose endpoints are elements
     * of a line in the song, which is the case for a pasted fragment's span once its elements
     * are inserted and before the span itself is added.
     *
     * @return the range from the anchor's location to the end's, as they stand when this is called
     * @throws IllegalStateException when the anchor or the end sits in no line, or the anchor's
     *     line is not one of its song's lines — the span's own state, not an argument
     */
    private ElementLocationRange location() {
        var line = line();
        var lineIndex = line.getSong().indexOfLine(line);

        if (lineIndex == null) {
            throw new IllegalStateException(getClass().getSimpleName() + " anchor line is not in its song: " + this);
        }

        return ElementLocationRange.onLine(lineIndex, range());
    }

    /**
     * The report entry for removing this span for the reason {@code reasonKey} names.
     *
     * @param reasonKey the {@code Strings} key of the reason, whose value is plain text
     * @return an entry under {@link #reportHeadingKey}, whose description is the reason's text
     *     and whose location is {@link #location}
     * @throws IllegalStateException under the conditions {@link #location} throws
     */
    public ReportedChange reportedAs(String reasonKey) {
        return new ReportedChange(reportHeadingKey(), Strings.get(reasonKey), location());
    }

    /**
     * Removes this span from {@code line}'s span registry and tells the user why.
     * <p>
     * Every removal the user did not directly request — one a rule, a sweep, a replacing
     * displacement, a paste or a load makes on its own — goes through this method, so none is silent.
     * {@link #removeFrom} is for a removal the user asked for, and for a span a merge absorbs,
     * which the merged span covers; neither needs a report.
     * <p>
     * Unlike {@link #removeFrom}, a second call is not a no-op: it reports again. The caller
     * holds a span that is in {@code line}'s registry.
     *
     * @param line      the line this span is being removed from; this span is in its registry
     * @param reasonKey the {@code Strings} key of the reason the removal is reported with
     * @effects records {@link #reportedAs reportedAs(reasonKey)} through {@link Song#report},
     *     which records nothing during replay or while tracking is suspended, then does what
     *     {@link #removeFrom} does
     * @throws IllegalStateException under the conditions {@link #location} throws, or when
     *     tracking is on and no modification bracket is open
     */
    public final void removeReported(Line line, String reasonKey) {
        line.getSong().report(reportedAs(reasonKey));
        removeFrom(line);
    }

    /**
     * The position of {@code element} within the line it is in, or {@code null} when it
     * sits in no line — an endpoint of a span outside every registry, such as a clipboard span
     * over cloned elements.
     */
    private static @Nullable Integer indexInLine(StaffElement element) {
        var line = element.getParentLine();

        if (line == null) {
            return null;
        }

        return line.getElementIndex(element);
    }

    /**
     * The index of {@code element} within its line, for a caller that assumes it is in one.
     *
     * @throws IllegalStateException when {@code element} sits in no line
     */
    private int requireIndexInLine(StaffElement element) {
        var index = indexInLine(element);

        if (index == null) {
            throw new IllegalStateException(
                getClass().getSimpleName() + " endpoint " + element + " is in no line");
        }

        return index;
    }

    /**
     * The anchor element's index within its own line. Every span in a line's registry has its
     * anchor in that line; a span outside every registry, such as a clipboard span over cloned
     * elements, has it in none.
     *
     * @return the anchor's index within {@link #getAnchorLine}
     * @throws IllegalStateException when the anchor sits in no line, which is the case only for
     *     a span outside every registry — a fault in the span's own state rather than in any
     *     argument, which is why it is not {@code IllegalArgumentException}
     */
    public int requireAnchorElementIndex() {
        return requireIndexInLine(anchorElement);
    }

    /**
     * The end element's index within its own line. See {@link #requireAnchorElementIndex} for
     * when an endpoint sits in no line.
     *
     * @return the end's index within {@link #line}
     * @throws IllegalStateException when the end sits in no line, which is the case only for a
     *     span outside every registry — the span's own state, not an argument
     */
    public int requireEndElementIndex() {
        return requireIndexInLine(endElement);
    }

    /**
     * The elements this span covers, as a range of indices in its line: from the anchor's index
     * to the end's index, both inclusive. A span never crosses a line, so both indices are in
     * {@link #line}, the anchor's line, and the range names positions in that line's element
     * list as it stands when this is called. The range is computed on each call, so an edit to
     * the line between two calls can give two different ranges.
     *
     * @return a range whose {@link ElementRange#begin} is {@link #requireAnchorElementIndex}
     *     and whose {@link ElementRange#end} is {@link #requireEndElementIndex}
     * @throws IllegalStateException when the anchor or the end sits in no line, which is the case
     *     only for a span outside every registry, such as a clipboard span over cloned elements
     *     — the span's own state, not an argument
     */
    public final ElementRange range() {
        return new ElementRange(requireAnchorElementIndex(), requireEndElementIndex());
    }

    /**
     * A test on a span's inclusive range, from its anchor's index to its end's.
     */
    @FunctionalInterface
    public interface IndexPredicate {
        boolean test(ElementRange spanRange);
    }

    /**
     * A predicate matching a span whose anchor-to-end range includes {@code elementIndex}.
     *
     * @return a predicate that is {@code true} for a range containing {@code elementIndex}
     */
    public static IndexPredicate containing(int elementIndex) {
        return spanRange -> spanRange.contains(elementIndex);
    }

    /**
     * A predicate matching a span sharing at least one element with {@code range}: its anchor at
     * or before {@code range}'s last element and its end at or after {@code range}'s first.
     *
     * @return a predicate that is {@code true} for a range sharing an element with {@code range}
     */
    public static IndexPredicate overlapping(ElementRange range) {
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
     * @return a predicate that is {@code true} for a range overlapping {@code range} by more than
     *     one shared endpoint
     */
    public static IndexPredicate overlappingBeyondEndpoint(ElementRange range) {
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
     * @return a predicate that is {@code true} for a range with {@code elementIndex} strictly
     *     between its bounds
     */
    public static IndexPredicate strictlyContaining(int elementIndex) {
        return spanRange -> spanRange.containsExcludingBounds(elementIndex);
    }

    /**
     * A predicate matching a span whose anchor is exactly {@code range}'s first element and
     * whose end is exactly its last.
     *
     * @return a predicate that is {@code true} for a range equal to {@code range}
     */
    public static IndexPredicate exactly(ElementRange range) {
        return spanRange -> spanRange.equals(range);
    }

    /**
     * A predicate matching a span whose anchor is the element at {@code elementIndex}.
     *
     * @return a predicate that is {@code true} for a range beginning at {@code elementIndex}
     */
    public static IndexPredicate anchoredAt(int elementIndex) {
        return spanRange -> spanRange.begin() == elementIndex;
    }

    /**
     * A predicate matching a span whose end is the element at {@code elementIndex}.
     *
     * @return a predicate that is {@code true} for a range ending at {@code elementIndex}
     */
    public static IndexPredicate endingAt(int elementIndex) {
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
    public static IndexPredicate straddling(StaffElementRun.InsertionTarget target) {
        var insertIndex = target.insertIndex();
        var deleteRange = target.deleteRange();
        var firstIndexAfter = deleteRange == null ? insertIndex : deleteRange.range().end() + 1;

        return spanRange -> spanRange.begin() < insertIndex && spanRange.end() >= firstIndexAfter;
    }

    /**
     * Creates a copy of this span anchored to the given elements, carrying all of this span's
     * user state through {@link #copyUserStateFrom}. Does not copy derived caches.
     *
     * @param newAnchor The anchor element for the copy
     * @param newEnd    The end element for the copy
     * @return A new span of the same concrete type as this one, with this span's user state
     */
    public final Span<E> copy(E newAnchor, E newEnd) {
        var copy = createCopy(newAnchor, newEnd);
        copy.copyUserStateFrom(this);
        return copy;
    }

    /**
     * Copies all of {@code source}'s user state onto this span: the base state
     * {@link LineElement#copyBaseStateFrom} copies, then the span kind's own through
     * {@link #copyOwnStateFrom}.
     * <p>
     * This is the one route by which a span takes another's user state, so a field added to
     * either level is carried by every caller at once.
     *
     * @param source the span whose user state to take
     * @throws IllegalArgumentException if {@code source} is not of this span's concrete class
     * @effects replaces this span's user state; leaves its endpoints, its kind-defining state
     *          and its place in the document alone
     */
    public final void copyUserStateFrom(Span<E> source) {
        if (source.getClass() != getClass()) {
            throw new IllegalArgumentException(
                getClass().getSimpleName() + " cannot take the user state of " + source);
        }

        copyBaseStateFrom(source);
        copyOwnStateFrom(source);
    }

    /**
     * Copies the user state this span kind adds to the base state from {@code source} onto this
     * span. Called only by {@link #copyUserStateFrom}, which guarantees {@code source} is of this
     * span's concrete class. A kind with no user state of its own copies nothing.
     *
     * @param source the span whose own user state to take, of this span's concrete class
     * @effects replaces this span kind's own user state
     */
    protected abstract void copyOwnStateFrom(Span<E> source);

    /**
     * Creates a new instance of this span's concrete subclass over the given elements, with the
     * state that defines its kind and no user state. Called only by {@link #copy}, which then
     * copies the user state through {@link #copyUserStateFrom}.
     *
     * @param newAnchor The anchor element for the copy
     * @param newEnd    The end element for the copy
     * @return a new span of this span's concrete class over {@code newAnchor} and {@code newEnd}
     */
    protected abstract Span<E> createCopy(E newAnchor, E newEnd);
}
