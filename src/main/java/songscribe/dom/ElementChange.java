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

import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.message.mutation.ElementField;

/**
 * A pending change to a {@link Line}'s elements: the line has not been mutated yet, and
 * {@link #projectedElements} describes what it will look like once the change lands.
 * <p>
 * A span judges itself against the projection ({@link Span#outcomeFor(ElementChange)}), never
 * against the mutated line, so that companion span mutations can be recorded before the primary
 * element mutation for correct undo replay.
 * <p>
 * The projection is a live view, valid only until the line's elements are mutated. See
 * {@link ProjectedElements}.
 */
public sealed interface ElementChange {

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
     * The position a span endpoint takes in {@link #projectedElements}.
     * <p>
     * <b>A replacement re-points, it does not delete.</b> {@link Line#replace} points any span
     * whose anchor or end was a replaced element at that element's replacement, so a replaced
     * endpoint is read as its replacement ({@link Replacement#replacementOf}) and answers the
     * replacement's position — not as an endpoint that is gone. Reading it as deleted would
     * remove every span whose endpoint is edited. Under every other shape an endpoint is an
     * element like any other, and this answers {@link #projectedIndexOf}.
     *
     * @param endpoint an endpoint of a span on {@link #line()}
     * @return the endpoint's projected position, or {@code null} when it is not present after
     *     the change — it is one a deletion removes
     * @throws IllegalArgumentException if {@code endpoint} is not on {@link #line()}
     */
    default @Nullable Integer projectedEndpointIndexOf(StaffElement endpoint) {
        return projectedIndexOf(endpoint);
    }

    /**
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
     * A set of {@code (index, newElement)} pairs at distinct indices. A replaced element is absent
     * from {@code projectedElements}; a span endpoint on it is read through
     * {@link ElementChange#projectedEndpointIndexOf}.
     *
     * @param line        the line being changed, still in its pre-replacement state
     * @param newElements each replaced position, mapped to the element taking its place
     * @invariant {@code newElements} is non-empty, unmodifiable, and every key is an index of
     *     {@code line}
     */
    record Replacement(Line line, NavigableMap<Integer, StaffElement> newElements) implements ElementChange {

        public Replacement {
            if (newElements.isEmpty()) {
                throw new IllegalArgumentException("a replacement replaces at least one element");
            }

            var elementCount = line.elementCount();

            for (var index : newElements.keySet()) {
                Objects.checkIndex(index, elementCount);
            }

            newElements = Collections.unmodifiableNavigableMap(new TreeMap<>(newElements));
        }

        public static Replacement single(Line line, int index, StaffElement newElement) {
            var newElements = new TreeMap<Integer, StaffElement>();
            newElements.put(index, newElement);
            return new Replacement(line, newElements);
        }

        /**
         * @return {@code null} when this replacement does not replace {@code element}
         */
        public @Nullable StaffElement replacementOf(StaffElement element) {
            return newElements.get(line.getElementIndex(element));
        }

        @Override
        public List<StaffElement> projectedElements() {
            return new ProjectedElements(line.elementCount(), projectedIndex -> {
                var newElement = newElements.get(projectedIndex);
                return newElement != null ? newElement : line.getElement(projectedIndex);
            });
        }

        @Override
        public @Nullable Integer projectedIndexOf(StaffElement element) {
            // Asked first, so a self-replace resolves to the position it keeps rather
            // than reading as a replaced element that is now absent.
            for (var pair : newElements.entrySet()) {
                if (pair.getValue() == element) {
                    return pair.getKey();
                }
            }

            var current = line.getElementIndex(element);

            // Nothing moves; only the replaced elements leave.
            return newElements.containsKey(current) ? null : current;
        }

        @Override
        public @Nullable Integer projectedEndpointIndexOf(StaffElement endpoint) {
            var replacement = replacementOf(endpoint);
            return projectedIndexOf(replacement != null ? replacement : endpoint);
        }

    }

    /**
     * @param line  the line being changed, still in its pre-deletion state
     * @param range the elements being removed, both ends included
     */
    record Deletion(Line line, ElementRange range) implements ElementChange {

        public List<StaffElement> deletedElements() {
            return line.getElements(range);
        }

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
     * A span judges a modification on which fields change: a duration-affecting change
     * unbalances a tuplet the element sits in, an accidental restamp touches nothing.
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
