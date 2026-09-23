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
 * <p><b>Endpoint typing.</b> The base holds {@link StaffElement} endpoints, because most span
 * types legitimately reach over any staff element. A subtype whose endpoints are always of one
 * kind narrows them: it declares a constructor taking the narrower type, overrides the two
 * getters covariantly, and overrides {@link #acceptsEndpoint} to admit only that type. The
 * override is what makes the narrowing a guarantee rather than a convention: every route by
 * which an endpoint enters through a base reference — {@link #setAnchorElement},
 * {@link #setEndElement} and {@link #copy} — consults it and refuses what it rejects, so a
 * narrowed getter can never hand back anything but the narrower type. {@link Beam} is the
 * one narrowing in the codebase.
 */
public abstract sealed class Span extends LineElement implements LineContent permits Beam, Hairpin, Tuplet, Trill, Ending {

    /**
     * Creates a span reaching from the anchor element to the end element.
     *
     * @param anchorElement The first element in the range
     * @param endElement    The last element in the range
     */
    protected Span(StaffElement anchorElement, StaffElement endElement) {
        this.anchorElement = anchorElement;
        this.endElement = endElement;
    }

    /** The first element in this range. */
    private StaffElement anchorElement;

    /** The last element in this range. */
    private StaffElement endElement;

    /**
     * Returns the first element in this range.
     */
    public StaffElement getAnchorElement() {
        return anchorElement;
    }

    /**
     * Sets the first element in this range.
     *
     * @throws IllegalArgumentException if this span's type does not
     *     {@link #acceptsEndpoint accept} {@code anchorElement} as an endpoint
     */
    void setAnchorElement(StaffElement anchorElement) {
        this.anchorElement = requireAccepted(anchorElement);
    }

    /**
     * Returns the last element in this range.
     */
    public StaffElement getEndElement() {
        return endElement;
    }

    /**
     * Sets the last element in this range.
     *
     * @throws IllegalArgumentException if this span's type does not
     *     {@link #acceptsEndpoint accept} {@code endElement} as an endpoint
     */
    void setEndElement(StaffElement endElement) {
        this.endElement = requireAccepted(endElement);
    }

    /**
     * Whether {@code element} may be one of this span's endpoints. The base admits every staff
     * element; a subtype that narrows its endpoint type overrides this to admit only that type,
     * and the setters and {@link #copy} then refuse anything else. See the class Javadoc.
     */
    protected boolean acceptsEndpoint(StaffElement element) {
        return true;
    }

    /**
     * {@code element}, once {@link #acceptsEndpoint} has admitted it.
     *
     * @throws IllegalArgumentException if this span's type rejects {@code element}
     */
    private StaffElement requireAccepted(StaffElement element) {
        if (!acceptsEndpoint(element)) {
            throw new IllegalArgumentException(
                getClass().getSimpleName() + " cannot end on " + element);
        }

        return element;
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
                case ABSORB_OVERLAPPING, ABSORB_ADJACENT ->
                    spans.mergeOverlapping(this, displacement.absorbReach());
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
    final boolean isSameKindAs(Span other) {
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
     * Decides what should happen to this span when {@code change} lands on {@code line}. This is
     * the one hook the sweep ({@link LineSpans#sweep}) and
     * {@link ElementChange#removesConfirmableSpan} put a change to; a kind with a rule of its
     * own overrides it, and states that rule as one switch over the change.
     * <p>
     * <b>The decision is made before the change lands.</b> {@code line} still holds its
     * pre-change elements; every judgement must be made against the change's own terms or
     * against {@link ElementChange#projectedElements}, which is what the line will look like
     * afterwards, and never by reading the line as though the change had landed. Undo replays a step's mutations in reverse, so the companion span
     * mutation this answer produces has to be recorded <em>before</em> the primary element
     * mutation — deciding after the fact would record a span already re-pointed at the new
     * element, and undo would restore it broken. {@code line} is passed so an implementor can
     * ask the line questions the change cannot answer on its own, such as which spans it
     * carries.
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
     * {@link Hairpin#hairpinOutcomeFor(ElementChange, Line)} instead.
     * <p>
     * Both questions are put only to {@link LineSpans#judgeableSpans}, so an implementor may
     * rely on {@code line} placing both of this span's ends.
     *
     * @param change the pending change, not yet applied to {@code line}
     * @param line   the line being changed, still in its pre-change state
     * @return {@link SpanOutcome.Simple#REMOVE} when this span cannot survive {@code change},
     *     otherwise {@link SpanOutcome.Simple#KEEP}
     */
    public SpanOutcome.Simple outcomeFor(ElementChange change, Line line) {
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
     * sits in no line because a removal detached it ({@link Line#removeElement}).
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
     * The anchor element's index within its own line, for a caller that assumes the anchor
     * is in one: the edit, layout and render paths, which act only on spans whose endpoints
     * are attached and feed the index into a range, a loop bound or arithmetic.
     *
     * @return the anchor's index within {@link #getAnchorLine}
     * @throws IllegalStateException when the anchor sits in no line because a removal
     *     ({@link Line#removeElement}) detached it — a fault in the span's own state rather
     *     than in any argument, which is why it is not {@code IllegalArgumentException}. A
     *     caller for which absence is a legitimate answer asks
     *     {@link #getAnchorElementIndex} instead
     */
    public int requireAnchorElementIndex() {
        return requireIndexInLine(anchorElement);
    }

    /**
     * The end element's index within its own line, for a caller that assumes the end is in
     * one. See {@link #requireAnchorElementIndex} for which callers those are.
     *
     * @return the end's index within {@link #getEndLine}
     * @throws IllegalStateException when the end sits in no line because a removal
     *     ({@link Line#removeElement}) detached it — the span's own state, not an argument.
     *     A caller for which absence is a legitimate answer asks
     *     {@link #getEndElementIndex} instead
     */
    public int requireEndElementIndex() {
        return requireIndexInLine(endElement);
    }

    /**
     * The anchor element's index within its own line, or {@code null} when the anchor sits
     * in no line. For the load and validation paths, where a span may legitimately arrive
     * with an endpoint that a removal ({@link Line#removeElement}) has detached and the
     * caller decides what to do about it — {@link Hairpin#outcomeFor} is the model.
     * <p>
     * A caller that assumes the anchor is attached asks {@link #requireAnchorElementIndex},
     * which states that assumption in its name and fails loudly when it is wrong.
     *
     * @return the anchor's index within {@link #getAnchorLine}, or {@code null} when
     *     {@link #getAnchorLine} is null
     */
    public @Nullable Integer getAnchorElementIndex() {
        return indexInLine(anchorElement);
    }

    /**
     * The end element's index within its own line, or {@code null} when the end sits in no
     * line. See {@link #getAnchorElementIndex} for which callers this pair is for; a caller
     * that assumes the end is attached asks {@link #requireEndElementIndex}.
     *
     * @return the end's index within {@link #getEndLine}, or {@code null} when
     *     {@link #getEndLine} is null
     */
    public @Nullable Integer getEndElementIndex() {
        return indexInLine(endElement);
    }

    /**
     * A test on a span's two endpoint indices, each {@code null} when that endpoint sits in
     * no line — a detached endpoint, or one in a line the song no longer holds.
     */
    @FunctionalInterface
    public interface IndexPredicate {
        boolean test(@Nullable Integer anchorIndex, @Nullable Integer endIndex);
    }

    /**
     * A predicate matching a span whose anchor-to-end range includes {@code elementIndex}.
     * <p>
     * A {@code null} endpoint bounds nothing, so a half-detached span is never reported as
     * covering an element.
     */
    public static IndexPredicate containing(int elementIndex) {
        return (anchorIndex, endIndex) ->
            anchorIndex != null && endIndex != null && anchorIndex <= elementIndex && elementIndex <= endIndex;
    }

    /**
     * A predicate matching a span overlapping {@code range}.
     * <p>
     * A {@code null} anchor is deliberately <em>not</em> rejected: the replacing displacement
     * ({@link Displacement#REPLACE_OVERLAPPING}) and {@link LineSpans#removeOverlapping} rely on
     * a half-detached span still being found so it can be cleaned up. A {@code null} end is
     * rejected, since nothing bounds the range from below without it.
     */
    public static IndexPredicate overlapping(ElementRange range) {
        return (anchorIndex, endIndex) ->
            (anchorIndex == null || anchorIndex <= range.end())
                && endIndex != null && endIndex >= range.begin();
    }

    /**
     * A predicate matching a span that overlaps {@code range} by more than a single shared
     * endpoint: its end falls past the range's first element and its anchor falls before
     * the range's last.
     * <p>
     * Two hairpins may meet — one ends on the element the next begins on, and the
     * wedges back away from that shared column (LilyPond's back-to-back rule). More
     * than that one element in common is a genuine collision.
     * <p>
     * A {@code null} bound equals nothing, so a half-detached span matches and the caller
     * treats it as a collision: a bound with no position cannot be shown to share only an
     * endpoint.
     */
    public static IndexPredicate overlappingBeyondEndpoint(ElementRange range) {
        // Built once here rather than inside the lambda: this predicate is tested
        // against every span on the line, on every selection change.
        var overlaps = overlapping(range);

        return (anchorIndex, endIndex) ->
            overlaps.test(anchorIndex, endIndex)
                && !isAt(endIndex, range.begin())
                && !isAt(anchorIndex, range.end());
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
        // Built once here rather than inside the lambda: this predicate is tested
        // against every span on the line, on every selection change.
        var contains = containing(elementIndex);

        return (anchorIndex, endIndex) ->
            contains.test(anchorIndex, endIndex) && !isAt(anchorIndex, elementIndex) && !isAt(endIndex, elementIndex);
    }

    /**
     * A predicate matching a span whose anchor is exactly {@code range}'s first element and
     * whose end is exactly its last.
     */
    public static IndexPredicate exactly(ElementRange range) {
        return (anchorIndex, endIndex) -> isAt(anchorIndex, range.begin()) && isAt(endIndex, range.end());
    }

    /**
     * A predicate matching a span whose anchor is the element at {@code elementIndex}.
     */
    public static IndexPredicate anchoredAt(int elementIndex) {
        return (anchorIndex, endIndex) -> isAt(anchorIndex, elementIndex);
    }

    /**
     * A predicate matching a span whose end is the element at {@code elementIndex}.
     */
    public static IndexPredicate endingAt(int elementIndex) {
        return (anchorIndex, endIndex) -> isAt(endIndex, elementIndex);
    }

    /**
     * Whether an endpoint index names {@code elementIndex}. A {@code null} endpoint sits in no
     * line, so it names no element.
     */
    private static boolean isAt(@Nullable Integer endpointIndex, int elementIndex) {
        return endpointIndex != null && endpointIndex == elementIndex;
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
     * @throws IllegalArgumentException if this span's type does not
     *     {@link #acceptsEndpoint accept} either element as an endpoint
     */
    public final Span copy(StaffElement newAnchor, StaffElement newEnd) {
        var copy = createCopy(requireAccepted(newAnchor), requireAccepted(newEnd));
        copy.copyBaseStateFrom(this);
        return copy;
    }

    /**
     * Creates a new instance of this span's concrete subclass, anchored to the
     * given elements and carrying over any subclass-specific state. Called only by
     * {@link #copy}, which layers on the shared {@link LineElement}-level state and has
     * already put both elements past {@link #acceptsEndpoint}.
     *
     * @param newAnchor The anchor element for the copy
     * @param newEnd    The end element for the copy
     */
    protected abstract Span createCopy(StaffElement newAnchor, StaffElement newEnd);
}
