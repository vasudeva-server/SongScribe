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
import songscribe.hit.HitTarget;

/**
 * The one thing selected in the score, in whichever of its two shapes.
 *
 * <pre>
 *   RANGE                                    TARGET
 *   Selection.Range(line, begin, end, …)     Selection.Target(HitTarget)
 *
 *   ● ● ● ● ● ● ● ●                          ● ● ● ♯● ● ● ●
 *     └───────┘                                    ↑
 *      3 notes                                  one accidental
 *
 *   spans many elements, names none           names exactly one thing,
 *   of them individually                      spans nothing — including the
 *                                             staff line itself, as StaffLine
 *
 *   what the tie / beam / tuplet              what a click resolves to: an
 *   toggles are built from                    accidental, one articulation,
 *                                             a tie curve, a hairpin, a lyric…
 * </pre>
 *
 * A range cannot name an accidental or a single articulation, and a target cannot express a
 * multi-element span, so neither shape can be expressed in the other's terms. What they can
 * share is the <em>field</em>: {@link SelectionCoordinator} holds one
 * {@code @Nullable Selection}, so "a range displaces a target" and "a target displaces a
 * range" stop being rules anyone enforces and become what one field means.
 * <p>
 * <b>Why {@code HitTarget} is wrapped rather than extended.</b> {@link HitTarget}'s contract is
 * "a thing a click resolves to", and {@link HitTarget#owner()} answers for every variant of it.
 * A {@code Range} is neither: {@code HitRegistry.hitTest} can never produce one, and it owns no
 * single element. Adding it there would put an unreachable arm in every switch on the registry
 * and renderer path, so it is composed in here instead.
 * <p>
 * <b>Why the variants are immutable records.</b> With immutable variants, "the selection
 * changed" and "the field was assigned" are the same event, which is the entire payoff — there
 * is exactly one place to observe. A mutable range sitting in the field would let code change
 * what is selected without assigning anything, leaving repaint, notification and cache
 * invalidation nowhere single to hang.
 */
public sealed interface Selection {

    /** An index range on one line, anchored at the end the user extended from. */
    record Range(Line line, int begin, int end, int anchor) implements Selection {

        /**
         * @throws IllegalArgumentException if the range is empty or reversed. A range that
         *     selects nothing is not stored at all — the coordinator holds null for that —
         *     so constructing one is a caller bug rather than a state to represent.
         */
        public Range {
            if (begin < 0 || end < begin) {
                throw new IllegalArgumentException(
                    "Selection.Range must select at least one element, got [" + begin + ", " + end + "]");
            }
        }

        /** A range over the single element at {@code elementIndex}, anchored on it. */
        public static Range single(Line line, int elementIndex) {
            return new Range(line, elementIndex, elementIndex, elementIndex);
        }

        /** The number of elements the range covers, never zero. */
        public int size() {
            return (end - begin) + 1;
        }

        /**
         * Returns whether the element at {@code elementIndex} reads as selected.
         *
         * <p>Wider than {@code begin..end} by design: a breath mark immediately after the
         * range counts as selected. It is owned by the element before it and goes wherever
         * that element goes — a deletion or a copy of the range carries it along
         * ({@link Line#effectiveDeleteEnd}) — so it has to read as selected too, or deleting
         * the range would take away an element the user never saw highlighted (refs #698).
         *
         * <p>This is the one query that disagrees with the raw range, and the disagreement is
         * deliberate: every other query, and everything the tie/beam/tuplet toggles are built
         * from, reports {@code begin..end} itself.
         */
        public boolean contains(int elementIndex) {
            return elementIndex >= begin && elementIndex <= line.effectiveDeleteEnd(end);
        }

        /** The single selected element, or null if the range covers more than one. */
        public @Nullable StaffElement singleElement() {
            return (begin == end) ? line.getElement(begin) : null;
        }

        /** This range as the immutable snapshot the selection caches are keyed by. */
        public ElementSelection toElementSelection() {
            return new ElementSelection(line, begin, end);
        }

        /**
         * Returns whether the range still fits the line — false once a mutation has shrunk the
         * line past its end, as an undo that removed a selected element does.
         *
         * <p>Bounded by {@link Line#elementCount()} rather than
         * {@link Line#effectiveElementCount()}: what makes a range unusable is that it can no
         * longer be indexed at all. Whether a selection may reach the song-owned terminal is a
         * separate question, decided where the selection is made.
         *
         * <p>Only the end is checked, because every caller that builds a range leaves
         * {@code begin <= end} — the constructor rejects anything else.
         */
        public boolean fitsLine() {
            return end < line.elementCount();
        }
    }

    /**
     * One thing, named by identity: an accidental, an articulation, a lyric, the staff line.
     * <p>
     * Carries no line of its own. Which line a target sits on is answered by
     * {@link SelectionCoordinator#getActiveLine()}, so that the range and the target shapes
     * have one source of truth for it rather than two that can disagree — and because
     * {@link HitTarget.StaffLine} names the line as a whole and has no owner to ask.
     */
    record Target(HitTarget target) implements Selection { }
}
