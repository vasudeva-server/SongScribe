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
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementRangeDeletion;
import songscribe.message.mutation.Mutation;

/**
 * The one thing selected in the score, in whichever of its two shapes.
 *
 * <p>A {@code Selection.Range(line, begin, end, …)} is a contiguous run of elements: it spans many
 * of them and names none individually, and it is what the tie, beam and tuplet toggles are built
 * from. A {@code Selection.Target(HitTarget)} is the opposite — it names exactly one thing and
 * spans nothing, including the staff line itself, as {@code StaffLine}. A target is what a click
 * resolves to: an accidental, one articulation, a tie curve, a hairpin, a lyric, and so on.
 *
 * <p>A range cannot name an accidental or a single articulation, and a target cannot express a
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

    /**
     * An index range on one line, anchored at the element the selection was made from.
     * <p>
     * The anchor is the element a plain click or a drag selected from — {@code begin} in both
     * cases — and it stays on that element until the next plain click or drag, however many
     * times Shift+click or Shift+arrow extends the range around it (issue #748). It is
     * therefore {@code begin} of the range it anchors until an extension crosses it, after
     * which it is {@code end}: extend leftward past the anchor and the range grows to its
     * left while the anchor stays where the user put it.
     * <p>
     * A range names elements by index, so any mutation that inserts or removes elements at or
     * below it retargets it unless the indices are spliced to follow their elements. That is
     * what {@link #splicedFor} does, one mutation at a time:
     *
     * <p>For a range {@code [b, e]} and an insertion at {@code i}: {@code i <= b} slides the whole
     * range to {@code [b+1, e+1]}; {@code b < i <= e} widens it to {@code [b, e+1]}, so the new
     * element joins the selection; and {@code i > e} leaves it untouched.
     *
     * <p>For a deletion of {@code [f, t]}, where {@code k = t − f + 1}: a deletion entirely before
     * the range shifts it to {@code [b−k, e−k]}; one entirely after leaves it untouched; and an
     * overlapping one moves {@code b} to the first survivor at or after {@code f} and {@code e} to
     * the last survivor before {@code f}, clearing the selection if nothing survives.
     *
     * {@code begin} and {@code end} do <b>not</b> use the same boundary predicate. An insertion
     * at {@code i == begin} slides the whole range right; a deletion at {@code i == begin} is an
     * interior shrink. Any attempt to collapse the two into a single signed-delta helper produces
     * an off-by-one on every insertion at {@code begin}. Keep them as two functions.
     */
    record Range(Line line, int begin, int end, int anchor) implements Selection {

        /**
         * @throws IllegalArgumentException if the range is empty or reversed, or if the anchor
         *     lies outside it. A range that selects nothing is not stored at all — the
         *     coordinator holds null for that — so constructing one is a caller bug rather than
         *     a state to represent. The anchor holds inside the range for two separate
         *     reasons, neither of which is this check: an extension derives both ends from the
         *     anchor itself, so it cannot place the anchor outside them; a splice can, every
         *     time a mutation deletes the anchored element, and {@link #spliced} clamps it
         *     back before constructing anything. So reaching this throw means a caller invented
         *     an anchor of its own, not that a routine path overshot.
         */
        public Range {
            if (begin < 0 || end < begin) {
                throw new IllegalArgumentException(
                    "Selection.Range must select at least one element, got [" + begin + ", " + end + "]");
            }

            if (anchor < begin || anchor > end) {
                throw new IllegalArgumentException(
                    "Selection.Range anchor " + anchor + " must lie within [" + begin + ", " + end + "]");
            }
        }

        /** A range over the single element at {@code elementIndex}, anchored on it. */
        public static Range single(Line line, int elementIndex) {
            return new Range(line, elementIndex, elementIndex, elementIndex);
        }

        /** The number of elements the range covers, never zero. */
        public int size() {
            return toElementSelection().size();
        }

        /**
         * Returns whether the element at {@code elementIndex} reads as selected.
         *
         * <p>Wider than {@code begin..end} by design, at both ends: an element paired with one
         * the range names goes wherever its partner goes, so a deletion or a copy of the range
         * carries it along (see {@link Line}'s pairing rule and {@link Line#effectiveRange}).
         * It therefore has to read as selected too, or an operation would take away an element
         * the user never saw highlighted. Both directions matter — the pair a key signature
         * makes with the barline before it lies backward.
         *
         * <p>The expansion happens here, where a range becomes highlighted columns, and not in
         * the stored range: what the user selected is what they chose, and the highlight shows
         * what an operation would reach. Nothing that validates an edit against the selection
         * may read it through this query.
         *
         * <p>This is the one query that disagrees with the raw range, and the disagreement is
         * deliberate: every other query, and everything the tie/beam/tuplet toggles are built
         * from, reports {@code begin..end} itself.
         *
         * @param elementIndex index of the element to test
         * @return {@code true} when the element is inside the range or paired with an element
         *     that is
         */
        public boolean contains(int elementIndex) {
            var effective = line.effectiveRange(begin, end);

            return elementIndex >= effective.begin() && elementIndex <= effective.end();
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
         * This range spliced for {@code mutation}, or null if nothing of it survives.
         * Returns {@code this} unchanged for a mutation on another line, for a replacement
         * or a modification (neither moves an index), and for song-scoped mutations.
         *
         * <p><b>That last case includes a {@link songscribe.message.mutation.LineDeletion} of
         * this range's own line, which comes back unchanged like any other song-scoped
         * mutation — a range still naming a line the song no longer holds.</b> Only element
         * indices are spliced here; whether the line itself survived is a question this method
         * does not ask. {@link SelectionCoordinator#revalidateElementSelection} answers it by
         * intercepting that mutation before it ever reaches here, and any other caller folding
         * a whole batch has to do the same.
         *
         * <p>A deletion that removes the anchored element leaves the anchor with no element of
         * its own, so the result is clamped back into {@code [begin, end]} here. That clamp is
         * not optional: this method establishes the anchor invariant and the constructor
         * enforces it, so skipping it would throw on the EDT from inside a message handler.
         * The anchor clamps in the same direction {@code begin} does — forward, onto the
         * element that slid into the hole — so a deletion swallowing the anchor from strictly
         * inside the range lands it on the first survivor after the hole, not the last one
         * before it. Either side would satisfy the invariant; forward is chosen so one rule
         * covers the anchor wherever it sits, rather than the landing changing with it.
         */
        public @Nullable Range splicedFor(Mutation mutation) {
            return switch (mutation) {
                //noinspection ObjectEquality
                case ElementInsertion insertion when insertion.line() == line -> spliced(
                    afterInsertion(begin, insertion.index()),
                    afterInsertion(end, insertion.index()),
                    afterInsertion(anchor, insertion.index()));

                //noinspection ObjectEquality
                case ElementDeletion deletion when deletion.line() == line ->
                    splicedForDeletion(deletion.index(), deletion.index());

                //noinspection ObjectEquality
                case ElementRangeDeletion deletion when deletion.line() == line ->
                    splicedForDeletion(deletion.from(), deletion.to());

                default -> this;
            };
        }

        /** Where index {@code x} lands after one element is inserted at {@code at}. */
        private static int afterInsertion(int x, int at) {
            return x >= at ? x + 1 : x;
        }

        /**
         * Where index {@code x} lands after elements {@code [from..to]} are removed.
         * A removed index has nowhere of its own to land, so it clamps to a survivor:
         * {@code isRangeStart} clamps forward onto the element that slid into {@code from},
         * otherwise back onto the last element before the hole.
         */
        private static int afterDeletion(int x, int from, int to, boolean isRangeStart) {
            if (x < from) {
                return x;
            }

            if (x > to) {
                return x - ((to - from) + 1);
            }

            return isRangeStart ? from : from - 1;
        }

        /** This range spliced for the removal of elements {@code [from..to]} of its own line. */
        private @Nullable Range splicedForDeletion(int from, int to) {
            return spliced(
                afterDeletion(begin, from, to, true),
                afterDeletion(end, from, to, false),
                afterDeletion(anchor, from, to, true));
        }

        /**
         * The spliced endpoints as a range, or null if the splice left nothing selected.
         * The anchor is clamped rather than validated — see {@link #splicedFor}.
         */
        private @Nullable Range spliced(int splicedBegin, int splicedEnd, int splicedAnchor) {
            if (splicedEnd < splicedBegin) {
                return null;
            }

            return new Range(line, splicedBegin, splicedEnd, Math.clamp(splicedAnchor, splicedBegin, splicedEnd));
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
