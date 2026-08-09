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

/**
 * A pending change to a {@link Line}'s elements: the line has not been mutated yet, and
 * {@link #projectedElements} describes what it will look like once the change lands.
 * This projection is the single thing a {@link Span} is asked to judge itself against
 * via {@link Span#outcomeFor(ElementChange, Line)} — decisions must always be made
 * against the projection, never against the mutated line, so that companion span
 * mutations can be recorded before the primary element mutation for correct undo replay.
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
     * The line's elements as they will be once this change is applied.
     * <p>
     * A fresh view per call, cheap to build and cheap to index — but never to be held
     * across the mutation this change describes.
     */
    List<StaffElement> projectedElements();

    /** The position of {@code element} in {@link #projectedElements}, or -1 if it is not there. */
    int projectedIndexOf(StaffElement element);

    /**
     * A single element inserted at {@code index}.
     *
     * @param line     the line being changed, still in its pre-insertion state
     * @param index    the position {@code inserted} will occupy
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
        public int projectedIndexOf(StaffElement element) {
            if (element == inserted) {
                return index;
            }

            var current = line.getElementIndex(element);

            if (current < 0) {
                return -1;
            }

            return current < index ? current : current + 1;
        }
    }

    /**
     * One element replaced by another at the same position. A replacement <b>re-points</b>
     * a span's endpoint rather than deleting it: {@code oldElement} is absent from
     * {@code projectedElements} and {@code newElement} sits at {@code index}. A span whose
     * anchor or end was {@code oldElement} is to be read as having that endpoint become
     * {@code newElement} — not as having lost an endpoint.
     *
     * @param line       the line being changed, still in its pre-replacement state
     * @param index      the position of both {@code oldElement} and {@code newElement}
     * @param oldElement the element being replaced
     * @param newElement the element taking its place
     */
    record Replacement(Line line, int index, StaffElement oldElement, StaffElement newElement)
        implements ElementChange {

        @Override
        public List<StaffElement> projectedElements() {
            return new ProjectedElements(
                line.elementCount(),
                projectedIndex ->
                    projectedIndex == index ? newElement : line.getElement(projectedIndex));
        }

        @Override
        public int projectedIndexOf(StaffElement element) {
            // Asked first, so a self-replace resolves to the position it keeps rather
            // than reading as a replaced element that is now absent.
            if (element == newElement) {
                return index;
            }

            var current = line.getElementIndex(element);

            // Nothing moves; only the replaced element leaves.
            return current == index ? -1 : current;
        }
    }

    /**
     * The contiguous run of elements {@code [from, to]}, inclusive, removed.
     * <p>
     * A range rather than a set of elements because a range is all a deletion can be: the
     * editor has no non-contiguous selection to delete from, and both primitives that delete
     * — {@link Line#removeElement} and {@link Line#removeRange} — are handed a position or a
     * range. Saying so here is what makes the projection three comparisons in each direction
     * instead of a search, and it puts the invariant in the type rather than in a comment.
     * <p>
     * Not a record: {@link #deletedElements} is derived from the line rather than stored,
     * and a record would have to carry it as a component.
     */
    final class Deletion implements ElementChange {

        private final Line line;
        private final int from;
        private final int to;

        Deletion(Line line, int from, int to) {
            this.line = line;
            this.from = from;
            this.to = to;
        }

        /**
         * The elements being removed — a view of the line's own list, so valid only until
         * the deletion lands, like everything else this change reads.
         */
        public List<StaffElement> deletedElements() {
            return line.getElements(from, to);
        }

        /** How many elements leave the line. */
        private int deletedCount() {
            return to - from + 1;
        }

        @Override
        public List<StaffElement> projectedElements() {
            var deletedCount = deletedCount();

            return new ProjectedElements(
                line.elementCount() - deletedCount,
                projectedIndex -> line.getElement(
                    projectedIndex < from ? projectedIndex : projectedIndex + deletedCount));
        }

        @Override
        public int projectedIndexOf(StaffElement element) {
            var current = line.getElementIndex(element);

            if (current < 0 || (current >= from && current <= to)) {
                return -1;
            }

            return current < from ? current : current - deletedCount();
        }
    }

    /**
     * Builds an {@link Insertion} projecting {@code inserted} into {@code line} at
     * {@code index}.
     */
    static Insertion forInsertion(Line line, int index, StaffElement inserted) {
        return new Insertion(line, index, inserted);
    }

    /**
     * Builds a {@link Replacement} projecting {@code replacement} over the element
     * currently at {@code index} of {@code line}.
     */
    static Replacement forReplacement(Line line, int index, StaffElement replacement) {
        return new Replacement(line, index, line.getElement(index), replacement);
    }

    /**
     * Builds a {@link Deletion} projecting the elements at {@code [from, to]} — inclusive,
     * and the same position twice for a single element — removed from {@code line}.
     */
    static Deletion forDeletion(Line line, int from, int to) {
        return new Deletion(line, from, to);
    }
}
