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
import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;

/**
 * Abstract base class for elements that span multiple elements.
 * <p>
 * Spans include ties, trills, crescendo/diminuendo hairpins,
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
 * narrowed getter can never hand back anything but the narrower type. {@link NoteSpan} is the
 * one narrowing in the codebase.
 */
public abstract sealed class Span extends LineElement permits NoteSpan, Hairpin, Tuplet, Trill, Ending {

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
    public void setAnchorElement(StaffElement anchorElement) {
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
    public void setEndElement(StaffElement endElement) {
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
     * A span is never attached to a line: its inherited {@code parentLine} stays
     * {@code null} for its whole life, and this derives parentage from where the endpoints
     * sit instead. A span whose endpoints straddle a line boundary is therefore in both
     * lines, which is what no single stored pointer could say.
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
     * line. Null-safe so callers deciding which lines a span reaches — including
     * {@link LineSpans#appendChild} and {@link Song#removeSpansBetweenNonAdjacentLines} —
     * express that in one call rather than each repeating the unwrap.
     * <p>
     * This is the endpoint's <em>own</em> line, which is not the same question as where the
     * endpoint sits relative to some other line; for that, ask that line's registry via
     * {@link LineSpans#anchorIndexOf}.
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
     * Adds this span to {@code line}'s span registry, applying this kind's own displacement
     * policy and recording this kind's own mutation.
     * <p>
     * The policy — what happens to the same-kind spans already there that this one may not
     * coexist with — is {@link #applyDisplacementPolicy}'s, and each displacement is its own
     * recorded mutation, emitted before this span's addition so undo restores the displaced
     * spans after removing this one. A kind with a policy skips it while
     * {@link Song#isReplaying()}: the recorded batch already carries the companion mutations,
     * and re-deriving them would apply them twice. The policy may widen this span's endpoints
     * before it is added, which is what a merge is.
     *
     * @param line the line this span is being added to
     * @effects posts {@link #additionRecordFor} through the line's song, after the
     *     displacements the policy posts, and appends this span to the registry; what is
     *     recorded, and what is thrown outside a bracket, is {@link Song#applyChange}'s promise
     */
    public final void addTo(Line line) {
        if (!line.getSong().isReplaying()) {
            applyDisplacementPolicy(line);
        }

        line.spanRegistry().add(this, additionRecordFor(line));
    }

    /**
     * Removes this span from {@code line}'s span registry if it is there, recording this
     * kind's own mutation, and is a no-op otherwise.
     * <p>
     * The no-op is a promise, not an accident: a removal takes a cross-line span out of both
     * lines' registries together, so a second call on either line finds nothing left to
     * remove and records nothing. A caller that cannot cheaply tell whether an earlier step
     * already removed the span — a sweep that doomed it, a merge that subsumed it, the paste
     * reconciliation — may therefore call this unconditionally. A raw list removal would drop
     * the span with no record and make undo of the enclosing operation lossy, which is why
     * this is the only route out.
     *
     * @param line the line this span is being removed from
     * @effects when this span is in the registry, posts {@link #removalRecordFor} through the
     *     line's song and removes the span; otherwise nothing. What is recorded, and what is
     *     thrown outside a bracket, is {@link Song#applyChange}'s promise
     */
    public final void removeFrom(Line line) {
        line.spanRegistry().remove(this, removalRecordFor(line));
    }

    /**
     * Makes room for this span among the same-kind spans already on {@code line}: removes or
     * absorbs whatever this kind may not coexist with, each removal through that span's own
     * {@link #removeFrom}. The base kind coexists with everything and does nothing. Called by
     * {@link #addTo} before the addition, and never while replaying.
     */
    protected void applyDisplacementPolicy(Line line) {
    }

    /**
     * The mutation {@link #addTo} records for this kind on {@code line}. The base records a
     * {@link SpanAddition}; a kind with a record of its own returns that instead.
     *
     * @return the addition record naming {@code line} and this span
     */
    protected Mutation additionRecordFor(Line line) {
        return new SpanAddition(line, this);
    }

    /**
     * The mutation {@link #removeFrom} records for this kind on {@code line}. The base records
     * a {@link SpanRemoval}; a kind with a record of its own returns that instead.
     *
     * @return the removal record naming {@code line} and this span
     */
    protected Mutation removalRecordFor(Line line) {
        return new SpanRemoval(line, this);
    }

    /**
     * Returns whether this span is invalidated by the given deletion.
     * <p>
     * A span is invalidated when its anchor or end element is among the deleted elements,
     * because the range can no longer be rendered without both endpoints. Subclasses may override
     * this method if their invalidation condition is more nuanced.
     * <p>
     * This is an implementation detail of the default {@link #outcomeFor}, which is the only
     * caller and the only entry point the sweep ({@link LineSpans#sweep}) uses. A new span
     * type should override
     * {@code outcomeFor} instead — a boolean predicate can only ever say "remove me", so a
     * type that implements this one silently forfeits {@link SpanOutcome.Reshape}.
     *
     * @param deletedElements the elements that were removed from the line
     * @return {@code true} if this span should be removed as a result of the deletion
     */
    protected boolean isInvalidatedBy(List<StaffElement> deletedElements) {
        return deletedElements.contains(anchorElement) || deletedElements.contains(endElement);
    }

    /**
     * Returns true if inserting an element of {@code insertedType} at {@code insertedIndex}
     * invalidates this span. Default is false; subclasses may override.
     * <p>
     * An implementation detail of the default {@link #outcomeFor} — see {@link #isInvalidatedBy}
     * for why a new span type should override {@code outcomeFor} rather than this.
     */
    protected boolean isInvalidatedByInsertion(int insertedIndex, ElementType insertedType, Line line) {
        return false;
    }

    /**
     * Returns true if deleting the given elements invalidates this span beyond what
     * {@link #isInvalidatedBy} already detects. Default is false; subclasses may override.
     * <p>
     * An implementation detail of the default {@link #outcomeFor} — see {@link #isInvalidatedBy}
     * for why a new span type should override {@code outcomeFor} rather than this.
     */
    protected boolean isInvalidatedByDeletion(List<StaffElement> deletedElements, Line line) {
        return false;
    }

    /**
     * Returns true if rewriting {@code fields} of the element at {@code index} in place
     * invalidates this span. Default is false; subclasses may override.
     * <p>
     * An implementation detail of the default {@link #outcomeFor} — see {@link #isInvalidatedBy}
     * for why a new span type should override {@code outcomeFor} rather than this.
     */
    protected boolean isInvalidatedByModification(int index, Set<ElementField> fields, Line line) {
        return false;
    }

    /**
     * Returns true if replacing {@code oldElement} with {@code newElement} invalidates
     * this span. Default is false; subclasses may override.
     * <p>
     * An implementation detail of the default {@link #outcomeFor} — see {@link #isInvalidatedBy}
     * for why a new span type should override {@code outcomeFor} rather than this.
     */
    protected boolean isInvalidatedByReplacement(StaffElement oldElement, StaffElement newElement, Line line) {
        return false;
    }

    /**
     * Decides what should happen to this span when {@code change} lands on {@code line}.
     * <p>
     * <b>The decision is made before the change lands.</b> {@code line} still holds its
     * pre-change elements; every judgement must be made against
     * {@link ElementChange#projectedElements}, which is what the line will look like
     * afterwards, and never against the line's current elements. Undo replays a step's
     * mutations in reverse, so the companion span mutation this answer produces has to be
     * recorded <em>before</em> the primary element mutation — deciding after the fact would
     * record a span already re-pointed at the new element, and undo would restore it broken.
     * {@code line} is passed only so an implementor can ask the line questions the projection
     * cannot answer on its own, such as which spans it carries.
     * <p>
     * <b>A replacement re-points, it does not delete.</b> {@link Line#setElement} points any
     * span whose anchor or end was the replaced element at the replacement. So under
     * {@link ElementChange.Replacement}, a span whose endpoint is
     * {@link ElementChange.Replacement#oldElement} must be read as having that endpoint
     * become {@link ElementChange.Replacement#newElement} — <em>not</em> as having lost an
     * endpoint. Reading the old element as deleted would silently remove every span whose
     * endpoint is edited.
     * <p>
     * The default is a bridge over the five per-shape predicates — {@link #isInvalidatedBy},
     * {@link #isInvalidatedByInsertion}, {@link #isInvalidatedByDeletion},
     * {@link #isInvalidatedByReplacement} and {@link #isInvalidatedByModification} — routing
     * each change shape to exactly the predicate the sweep consults for it, so a subclass
     * that overrides only those keeps behaving as it always has. A subclass overriding
     * {@code outcomeFor} need not implement any of them.
     * <p>
     * A span either survives a change or it does not, which is all {@link SpanOutcome.Simple}
     * can say. A hairpin is the one kind that can survive in a different shape, and the
     * sweep asks it that wider question through
     * {@link Hairpin#hairpinOutcomeFor(ElementChange, Line)} instead.
     *
     * @param change the pending change, not yet applied to {@code line}
     * @param line   the line being changed, still in its pre-change state
     * @return {@link SpanOutcome.Simple#REMOVE} when this span cannot survive {@code change},
     *     otherwise {@link SpanOutcome.Simple#KEEP}
     */
    public SpanOutcome.Simple outcomeFor(ElementChange change, Line line) {
        return isInvalidatedByChange(change, line) ? SpanOutcome.Simple.REMOVE : SpanOutcome.Simple.KEEP;
    }

    /**
     * Routes {@code change} to the one per-shape predicate {@link Line} consulted for it
     * before {@link #outcomeFor} existed. Separated from {@code outcomeFor} so the
     * boolean-to-outcome mapping is written once rather than once per shape.
     */
    private boolean isInvalidatedByChange(ElementChange change, Line line) {
        return switch (change) {
            case ElementChange.Insertion insertion ->
                isInvalidatedByInsertion(insertion.index(), insertion.inserted().getType(), line);

            case ElementChange.Replacement replacement ->
                isInvalidatedByReplacement(replacement.oldElement(), replacement.newElement(), line);

            case ElementChange.Deletion deletion -> {
                var deletedElements = deletion.deletedElements();

                yield isInvalidatedBy(deletedElements) || isInvalidatedByDeletion(deletedElements, line);
            }

            case ElementChange.Modification modification ->
                isInvalidatedByModification(modification.index(), modification.fields(), line);
        };
    }

    /**
     * Returns whether removing this span as a side effect of an edit warrants a
     * user confirmation prompt. Default is false: spans such as beams, ties, and
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
     * <p>
     * This is the endpoint's <em>own</em> line, which is not the same question as where the
     * anchor sits relative to some other line; for that, ask that line's registry via
     * {@link LineSpans#anchorIndexOf}.
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
     * one. See {@link #requireAnchorElementIndex} for which callers those are and for the
     * relationship to {@link LineSpans}.
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
     * caller decides what to do about it — {@link Tuplet#hasValidSpan} and
     * {@link Hairpin#outcomeFor} are the model.
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
     * A test on a span's endpoints as the asking line resolved them.
     * <p>
     * The bounds are {@link SpanBound}s, not indices, because an endpoint may sit off one of
     * that line's edges or nowhere at all; the queried positions stay {@code int} because a
     * query is always a real in-line position. That asymmetry is what keeps a cross-line
     * half from matching a query for a span genuinely anchored at this line's first or last
     * element.
     */
    @FunctionalInterface
    public interface IndexPredicate {
        boolean test(SpanBound anchorBound, SpanBound endBound);
    }

    /**
     * A predicate matching a span whose anchor-to-end range includes {@code elementIndex}.
     * <p>
     * A half whose far endpoint is off an edge covers every element it passes over on the way
     * there, so a cross-line tie's line-A half contains everything from its anchor to the last
     * element. {@link SpanBound#ABSENT} is rejected outright: an endpoint with no position
     * bounds nothing, so a half-detached span is never reported as covering an element.
     */
    public static IndexPredicate containing(int elementIndex) {
        return (anchorBound, endBound) -> anchorBound.atOrBefore(elementIndex) && endBound.atOrAfter(elementIndex);
    }

    /**
     * A predicate matching a span overlapping {@code range}.
     * <p>
     * Off-edge bounds read as unbounded in their direction, exactly as in {@link #containing}.
     * An {@link SpanBound#ABSENT} anchor is deliberately <em>not</em> rejected: the trill
     * sweeps in {@link Trill#applyDisplacementPolicy} and {@link LineSpans#removeOverlapping} rely on a
     * half-detached span still being found so it can be cleaned up.
     */
    public static IndexPredicate overlapping(ElementRange range) {
        return (anchorBound, endBound) ->
            (anchorBound.isAbsent() || anchorBound.atOrBefore(range.end()))
                && endBound.atOrAfter(range.begin());
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
     * Off-edge and absent bounds read exactly as in {@link #overlapping}, and
     * {@link SpanBound#isAt} is false for all of them — so a half-detached span
     * matches, and the caller treats it as a collision. That is deliberate: a bound
     * with no position cannot be shown to share only an endpoint.
     */
    public static IndexPredicate overlappingBeyondEndpoint(ElementRange range) {
        // Built once here rather than inside the lambda: this predicate is tested
        // against every span on the line, on every selection change.
        var overlaps = overlapping(range);

        return (anchorBound, endBound) ->
            overlaps.test(anchorBound, endBound)
                && !endBound.isAt(range.begin())
                && !anchorBound.isAt(range.end());
    }

    /**
     * A predicate matching a span that covers {@code elementIndex} strictly inside its
     * range — the bound elements themselves do not count.
     * <p>
     * A text dynamic may sit on a hairpin's bound, where the wedge pads away from it, but
     * never under the wedge, so the editor asks about the strict interior rather than the
     * inclusive range.
     * <p>
     * Off-edge and absent bounds read exactly as in {@link #containing}, which rejects
     * {@link SpanBound#ABSENT} outright, and {@link SpanBound#isAt} is false for
     * {@link SpanBound#BEFORE_LINE} / {@link SpanBound#AFTER_LINE} — so a bound with no
     * position in the asking line cannot exempt an element, and every element the half
     * covers reads as interior.
     */
    public static IndexPredicate strictlyContaining(int elementIndex) {
        // Built once here rather than inside the lambda: this predicate is tested
        // against every span on the line, on every selection change.
        var contains = containing(elementIndex);

        return (anchorBound, endBound) ->
            contains.test(anchorBound, endBound)
                && !anchorBound.isAt(elementIndex)
                && !endBound.isAt(elementIndex);
    }

    /**
     * A predicate matching a span whose anchor is exactly {@code range}'s first element and
     * whose end is exactly its last.
     * <p>
     * Only a resolved position can equal a queried one — an off-edge bound is never coerced
     * to index 0 or to the last index, so a cross-line half never answers an exact-range query.
     */
    public static IndexPredicate exactly(ElementRange range) {
        return (anchorBound, endBound) ->
            anchorBound instanceof SpanBound.At(var spanAnchorIndex) && spanAnchorIndex == range.begin() &&
            endBound instanceof SpanBound.At(var spanEndIndex) && spanEndIndex == range.end();
    }

    /**
     * Creates a copy of this span anchored to the given elements, carrying over
     * all {@link LineElement}-level user state (the two user offsets).
     * <p>
     * Base state travels through {@link LineElement#copyBaseStateFrom}, so a new subclass cannot
     * forget it and a new base field needs no change here. Subclasses carry their own
     * subclass-specific state in {@link #createCopy}. Does not set {@code parentLine} — no span
     * ever has one; see {@link #isIn} — and does not copy derived caches.
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
