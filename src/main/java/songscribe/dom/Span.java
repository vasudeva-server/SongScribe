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
     * Returns whether this span is in {@code line}, meaning either of its endpoints is.
     * <p>
     * Two reference comparisons and nothing else: it allocates nothing and is safe to ask
     * on any path.
     * <p>
     * It answers where the endpoints are, which is not the same question as whether the
     * span is still in the document — a removed span whose endpoints survive still reports
     * {@code true}. A caller that means "is this span still live on that line" must ask
     * {@code line.spanRegistry().getSpans()}, as {@code SelectionCoordinator.isOnLine} does.
     */
    public boolean isIn(Line line) {
        return getAnchorLine() == line || getEndLine() == line;
    }

    /**
     * The line the anchor element belongs to, or {@code null} when the anchor sits in no
     * line. Null-safe so callers deciding which lines a span reaches express that in one
     * call rather than each repeating the unwrap.
     */
    public @Nullable Line getAnchorLine() {
        return anchorElement.getParentLine();
    }

    /** The line the end element belongs to. See {@link #getAnchorLine}. */
    public @Nullable Line getEndLine() {
        return endElement.getParentLine();
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
     * {@link Displacement} applies: the same concrete class, and for a hairpin the same
     * {@link Hairpin.Kind}, so a crescendo never displaces a diminuendo.
     *
     * @return {@code true} when adding this span may displace {@code other}
     */
    final boolean isSameKindAs(Span<?> other) {
        if (other.getClass() != getClass()) {
            return false;
        }

        return !(this instanceof Hairpin hairpin && other instanceof Hairpin otherHairpin)
            || hairpin.getKind() == otherHairpin.getKind();
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
     * {@code change.line()}. This is the one hook the sweep ({@link LineSpans#sweep}) and
     * {@link ElementChange#removesConfirmableSpan} put a change to; a kind with a rule of its
     * own overrides it, and states that rule as one switch over the change.
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
     * <b>A replacement re-points, it does not delete.</b> {@link Line#setElement} points any
     * span whose anchor or end was the replaced element at the replacement. So under
     * {@link ElementChange.Replacement}, a span whose endpoint is
     * {@link ElementChange.Replacement#oldElement} must be read as having that endpoint
     * become {@link ElementChange.Replacement#newElement} — <em>not</em> as having lost an
     * endpoint. Reading the old element as deleted would silently remove every span whose
     * endpoint is edited.
     * <p>
     * The default is the rule every kind shares and the whole of {@link Trill}'s:
     * a deletion that takes an endpoint ({@link #isEndpointDeletedBy}) removes the span, and
     * every other change keeps it.
     * <p>
     * A span either survives a change or it does not, which is all {@link SpanOutcome.Simple}
     * can say. A hairpin is the one kind that can survive in a different shape, and the
     * sweep asks it that wider question through
     * {@link Hairpin#hairpinOutcomeFor(ElementChange)} instead.
     *
     * @param change the pending change, not yet applied to {@code change.line()}
     * @return {@link SpanOutcome.Simple#REMOVE} when this span cannot survive {@code change},
     *     otherwise {@link SpanOutcome.Simple#KEEP}
     */
    public SpanOutcome.Simple outcomeFor(ElementChange change) {
        return removeWhen(change instanceof ElementChange.Deletion deletion && isEndpointDeletedBy(deletion));
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
     * @return {@link SpanOutcome.Simple#REMOVE} when {@code invalidated}, otherwise
     *     {@link SpanOutcome.Simple#KEEP}
     */
    protected static SpanOutcome.Simple removeWhen(boolean invalidated) {
        return invalidated ? SpanOutcome.Simple.REMOVE : SpanOutcome.Simple.KEEP;
    }

    /**
     * Returns whether removing this span as a side effect of an edit warrants a
     * user confirmation prompt. Default is false: spans such as beams and
     * tuplets are removed silently. Subclasses may override (endings do, since their loss
     * is significant enough to confirm first).
     */
    public boolean requiresInvalidationConfirm() {
        return false;
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
     * @return the end's index within {@link #getEndLine}
     * @throws IllegalStateException when the end sits in no line, which is the case only for a
     *     span outside every registry — the span's own state, not an argument
     */
    public int requireEndElementIndex() {
        return requireIndexInLine(endElement);
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
     */
    public static IndexPredicate containing(int elementIndex) {
        return spanRange -> spanRange.contains(elementIndex);
    }

    /**
     * A predicate matching a span sharing at least one element with {@code range}: its anchor at
     * or before {@code range}'s last element and its end at or after {@code range}'s first.
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
     */
    public static IndexPredicate strictlyContaining(int elementIndex) {
        return spanRange ->
            spanRange.contains(elementIndex)
                && spanRange.begin() != elementIndex
                && spanRange.end() != elementIndex;
    }

    /**
     * A predicate matching a span whose anchor is exactly {@code range}'s first element and
     * whose end is exactly its last.
     */
    public static IndexPredicate exactly(ElementRange range) {
        return spanRange -> spanRange.equals(range);
    }

    /**
     * A predicate matching a span whose anchor is the element at {@code elementIndex}.
     */
    public static IndexPredicate anchoredAt(int elementIndex) {
        return spanRange -> spanRange.begin() == elementIndex;
    }

    /**
     * A predicate matching a span whose end is the element at {@code elementIndex}.
     */
    public static IndexPredicate endingAt(int elementIndex) {
        return spanRange -> spanRange.end() == elementIndex;
    }

    /**
     * Creates a copy of this span anchored to the given elements, carrying over
     * all {@link LineElement}-level user state (the two user offsets).
     * <p>
     * Base state travels through {@link LineElement#copyBaseStateFrom}, so a new subclass cannot
     * forget it and a new base field needs no change here. Subclasses carry their own
     * subclass-specific state in {@link #createCopy}. Does not copy derived caches.
     *
     * @param newAnchor The anchor element for the copy
     * @param newEnd    The end element for the copy
     * @return A new span of the same concrete type as this one
     */
    public final Span<E> copy(E newAnchor, E newEnd) {
        var copy = createCopy(newAnchor, newEnd);
        copy.copyBaseStateFrom(this);
        return copy;
    }

    /**
     * Creates a new instance of this span's concrete subclass, anchored to the
     * given elements and carrying over any subclass-specific state. Called only by
     * {@link #copy}, which layers on the shared {@link LineElement}-level state.
     *
     * @param newAnchor The anchor element for the copy
     * @param newEnd    The end element for the copy
     */
    protected abstract Span<E> createCopy(E newAnchor, E newEnd);
}
