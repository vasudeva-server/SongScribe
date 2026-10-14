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

package songscribe.ui.selection;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun.ElementRange;
import songscribe.hit.HitTarget;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.Mutation;

/**
 * The one thing selected in the score, in whichever of its two shapes.
 *
 * <p>A {@code Range} is a contiguous run of elements and names none individually; a
 * {@code Target} names exactly one thing, including the staff line, and spans nothing. Neither
 * shape can express the other.
 *
 * <p>{@link HitTarget} is wrapped rather than extended: a {@code Range} is not something a click
 * resolves to, and adding it there would put an unreachable arm in every switch on the registry
 * and renderer path. A target is narrowed to {@code Selectable}, so a kind a press never selects
 * cannot be stored.
 *
 * <p>The variants are immutable, so "the selection changed" and "the field was assigned" are
 * the same event.
 */
public sealed interface Selection {

    /**
     * An index range on one line. The anchor is the element a plain click or drag selected
     * from; it stays there until the next plain click or drag, however Shift-extension grows the
     * range around it.
     * <p>
     * {@link #splicedFor} treats {@code begin} and {@code end} with different boundary
     * predicates: an insertion at {@code begin} slides the whole range right, while a deletion
     * at {@code begin} is an interior shrink. Keep them as two functions; a single signed-delta
     * helper is off by one on every insertion at {@code begin}.
     */
    record Range(Line line, ElementRange range, int anchor) implements Selection {

        /**
         * The anchor lies within the range, and a range that selects nothing is never constructed
         * (the coordinator holds null for that).
         */
        public Range {
            if (!range.contains(anchor)) {
                throw new IllegalArgumentException(
                    "Selection.Range anchor " + anchor + " must lie within " + range);
            }
        }

        public static Range single(Line line, int elementIndex) {
            return new Range(line, ElementRange.single(elementIndex), elementIndex);
        }

        /**
         * Wider than {@code begin..end} at both ends by design: an element paired with one the
         * range names goes wherever its partner goes (see {@link Line#effectiveRange}), so it
         * must read as selected too. Nothing that validates an edit may read the selection
         * through this query; every other query reports {@code begin..end} itself.
         */
        public boolean contains(int elementIndex) {
            return line.effectiveRange(range).range().contains(elementIndex);
        }

        /** The single selected element, or null if the range covers more than one. */
        public @Nullable StaffElement singleElement() {
            return (range.size() == 1) ? line.getElement(range.begin()) : null;
        }

        public ElementSelection toElementSelection() {
            return new ElementSelection(line, range);
        }

        /**
         * Null if nothing of it survives. Comes back unchanged for song-scoped mutations,
         * including a {@link songscribe.message.mutation.LineDeletion} of this range's own line:
         * {@link SelectionCoordinator#revalidateElementSelection} intercepts that, and any other
         * caller folding a batch must do the same.
         *
         * <p>A deleted anchor is clamped forward onto the first survivor after the hole, as
         * {@code begin} is; the clamp is required because the constructor enforces the anchor
         * invariant.
         */
        public @Nullable Range splicedFor(Mutation mutation) {
            return switch (mutation) {
                //noinspection ObjectEquality
                case ElementInsertion insertion when insertion.line() == line -> spliced(
                    afterInsertion(range.begin(), insertion.index()),
                    afterInsertion(range.end(), insertion.index()),
                    afterInsertion(anchor, insertion.index()));

                //noinspection ObjectEquality
                case ElementDeletion deletion when deletion.line() == line ->
                    splicedForDeletion(deletion.range().begin(), deletion.range().end());

                default -> this;
            };
        }

        private static int afterInsertion(int x, int at) {
            return x >= at ? x + 1 : x;
        }

        private static int afterDeletion(int x, int from, int to, boolean isRangeStart) {
            if (x < from) {
                return x;
            }

            if (x > to) {
                return x - ((to - from) + 1);
            }

            return isRangeStart ? from : from - 1;
        }

        private @Nullable Range splicedForDeletion(int from, int to) {
            return spliced(
                afterDeletion(range.begin(), from, to, true),
                afterDeletion(range.end(), from, to, false),
                afterDeletion(anchor, from, to, true));
        }

        /** Null if the splice left nothing selected. */
        private @Nullable Range spliced(int splicedBegin, int splicedEnd, int splicedAnchor) {
            if (splicedEnd < splicedBegin) {
                return null;
            }

            return new Range(
                line,
                new ElementRange(splicedBegin, splicedEnd),
                Math.clamp(splicedAnchor, splicedBegin, splicedEnd));
        }
    }

    /**
     * Carries no line of its own: {@link SelectionCoordinator#getActiveLine()} is the one source
     * of truth for it, and {@link HitTarget.StaffLine} has no owner to ask.
     */
    record Target(HitTarget.Selectable target) implements Selection { }
}
