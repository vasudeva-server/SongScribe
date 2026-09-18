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
import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementField;

/**
 * A pending change to a {@link Line}'s elements: the line has not been mutated yet, and
 * {@link #projectedElements} describes what it will look like once the change lands. The four
 * shapes are the four element primitives — an insertion, a replacement, a deletion, and a
 * modification of one element's fields in place.
 * <p>
 * A change has two readers. The span sweep ({@link LineSpans#sweep}) hands it to every
 * {@link Span} via {@link Span#outcomeFor(ElementChange, Line)}, and the projection is the
 * single thing a span judges itself against — decisions must always be made against the
 * projection, never against the mutated line, so that companion span mutations can be
 * recorded before the primary element mutation for correct undo replay. The confirmation
 * prompts ask the change itself, through {@link #removesConfirmableSpan} and
 * {@link Replacement#endingEffect}, what the edit will cost before it is made; those
 * predictions put the same question to the same spans, so a prompt and the edit it
 * precedes cannot disagree.
 * <p>
 * <b>The projection is a view, not a copy.</b> Each shape maps projected positions onto
 * the line it holds, so building a change allocates nothing proportional to the line and
 * a line with no hairpin on it — the only span type that reads the projection at all —
 * never pays for one. The view is therefore live: it is valid only until the line's
 * elements are mutated, which is exactly the window every caller uses it in. See
 * {@link ProjectedElements}.
 */
public sealed interface ElementChange {

    /**
     * The line this change applies to.
     *
     * @return the line being changed, still in its pre-change state
     */
    Line line();

    /**
     * The line's elements as they will be once this change is applied.
     * <p>
     * A fresh view per call, cheap to build and cheap to index — but never to be held
     * across the mutation this change describes.
     *
     * @return a live, unmodifiable view of the projected elements, valid until the line's
     *     elements are next mutated
     */
    List<StaffElement> projectedElements();

    /**
     * The position of {@code element} in {@link #projectedElements}.
     *
     * @param element an element of {@link #line()}
     * @return the projected position, or {@code null} when {@code element} is not present
     *     after the change — it is one this change removes
     * @throws IllegalArgumentException if {@code element} is not on {@link #line()}
     */
    @Nullable Integer projectedIndexOf(StaffElement element);

    /**
     * Whether this change would remove any span whose loss warrants a confirmation prompt.
     * <p>
     * Asks those spans the same question {@link LineSpans#sweep} does, so the answer is the
     * one the sweep will act on and a prompt and the edit it precedes cannot disagree about
     * what the edit costs. Only spans that ask to be confirmed
     * ({@link Span#requiresInvalidationConfirm}) are consulted: a tie an insertion
     * displaces goes silently, so it must not raise the ending confirm.
     *
     * @return false when the line carries no such span; otherwise true exactly when one of
     *     them answers {@link SpanOutcome.Simple#REMOVE} for this change
     */
    default boolean removesConfirmableSpan() {
        var line = line();

        return line.spanRegistry().getSpans().stream()
            .filter(Span::requiresInvalidationConfirm)
            .anyMatch(span -> span.outcomeFor(this, line) == SpanOutcome.Simple.REMOVE);
    }

    /**
     * A single element inserted at {@code index}.
     *
     * @param line     the line being changed, still in its pre-insertion state
     * @param index    the position {@code inserted} will occupy, {@code 0..}{@link
     *                 Line#elementCount()}
     * @param inserted the element being inserted
     */
    record Insertion(Line line, int index, StaffElement inserted) implements ElementChange {

        @Override
        public List<StaffElement> projectedElements() {
            return new ProjectedElements(line.elementCount() + 1, projectedIndex -> {
                if (projectedIndex == index) {
                    return inserted;
                }

                // Everything from the insertion point on has been pushed one along.
                return line.getElement(projectedIndex < index ? projectedIndex : projectedIndex - 1);
            });
        }

        @Override
        public @Nullable Integer projectedIndexOf(StaffElement element) {
            if (element == inserted) {
                return index;
            }

            var current = line.getElementIndex(element);
            return current < index ? current : current + 1;
        }
    }

    /**
     * One element replaced by another at the same position. A replacement <b>re-points</b>
     * a span's endpoint rather than deleting it: {@link #oldElement()} is absent from
     * {@code projectedElements} and {@code newElement} sits at {@code index}. A span whose
     * anchor or end was the old element is to be read as having that endpoint become
     * {@code newElement} — not as having lost an endpoint.
     * <p>
     * The old element is read off the line rather than held, so a replacement is valid only
     * until its line changes — the window every reader uses it in, like every other shape.
     *
     * @param line       the line being changed, still in its pre-replacement state
     * @param index      the position of both the element being replaced and {@code newElement}
     * @param newElement the element taking its place
     */
    record Replacement(Line line, int index, StaffElement newElement) implements ElementChange {

        /**
         * The element being replaced.
         *
         * @return the element standing at {@link #index} in {@link #line}
         * @throws IndexOutOfBoundsException if no element stands at {@link #index}
         */
        public StaffElement oldElement() {
            return line.getElement(index);
        }

        @Override
        public List<StaffElement> projectedElements() {
            return new ProjectedElements(
                line.elementCount(),
                projectedIndex ->
                    projectedIndex == index ? newElement : line.getElement(projectedIndex));
        }

        @Override
        public @Nullable Integer projectedIndexOf(StaffElement element) {
            // Asked first, so a self-replace resolves to the position it keeps rather
            // than reading as a replaced element that is now absent.
            if (element == newElement) {
                return index;
            }

            var current = line.getElementIndex(element);

            // Nothing moves; only the replaced element leaves.
            return current == index ? null : current;
        }

        /**
         * The effect this replacement has on an ending in the line, as the ending itself
         * judges it via {@link Ending#checkReplacement}. The first ending reporting an
         * effect decides.
         * <p>
         * Ask before {@link Line#setElement} to learn whether a confirmation dialog is needed.
         *
         * @return the first ending's non-{@link Ending.EndingEffect.None None} effect, or
         *     {@link Ending.EndingEffect.None} when no ending is affected
         * @throws IndexOutOfBoundsException if no element stands at {@link #index}
         */
        public Ending.EndingEffect endingEffect() {
            var oldElement = oldElement();

            return line.spanRegistry().findEndings().stream()
                .map(ending -> ending.checkReplacement(oldElement, newElement, line))
                .filter(effect -> !(effect instanceof Ending.EndingEffect.None))
                .findFirst()
                .orElse(Ending.EndingEffect.None.INSTANCE);
        }
    }

    /**
     * The contiguous run of elements in {@code range} removed.
     * <p>
     * A range rather than a set of elements because a range is all a deletion can be: the
     * editor has no non-contiguous selection to delete from, and both primitives that delete
     * — {@link Line#removeElement} and {@link Line#removeRange} — are handed a position or a
     * range. Saying so here is what makes the projection three comparisons in each direction
     * instead of a search, and it puts the invariant in the type rather than in a comment.
     *
     * @param line  the line being changed, still in its pre-deletion state
     * @param range the elements being removed, both ends included
     */
    record Deletion(Line line, ElementRange range) implements ElementChange {

        /**
         * The elements being removed.
         *
         * @return a view of the line's own list over {@link #range}, valid only until the
         *     deletion lands, like everything else this change reads
         */
        public List<StaffElement> deletedElements() {
            return line.getElements(range);
        }

        /** How many elements leave the line. */
        private int deletedCount() {
            return range.size();
        }

        @Override
        public List<StaffElement> projectedElements() {
            var deletedCount = deletedCount();
            var from = range.begin();

            return new ProjectedElements(
                line.elementCount() - deletedCount,
                projectedIndex -> line.getElement(
                    projectedIndex < from ? projectedIndex : projectedIndex + deletedCount));
        }

        @Override
        public @Nullable Integer projectedIndexOf(StaffElement element) {
            var current = line.getElementIndex(element);

            if (range.contains(current)) {
                return null;
            }

            return current < range.begin() ? current : current - deletedCount();
        }
    }

    /**
     * The element at {@code index} changed in place: {@code fields} are rewritten on it and
     * nothing moves. The projection is the line as it stands, since a field change alters no
     * element's position, and a span judges it on which fields change — a duration-affecting
     * change unbalances a tuplet the element sits in, an accidental restamp touches nothing.
     *
     * @param line   the line being changed, still in its pre-modification state
     * @param index  the position of the element being modified
     * @param fields the fields the modification rewrites
     */
    record Modification(Line line, int index, Set<ElementField> fields) implements ElementChange {

        @Override
        public List<StaffElement> projectedElements() {
            return new ProjectedElements(line.elementCount(), line::getElement);
        }

        @Override
        public @Nullable Integer projectedIndexOf(StaffElement element) {
            return line.getElementIndex(element);
        }
    }

}
