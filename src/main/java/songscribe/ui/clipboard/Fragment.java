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

package songscribe.ui.clipboard;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.DetachedLyricRun;
import songscribe.dom.ElementType;
import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.LyricRun;
import songscribe.dom.NoteElement;
import songscribe.dom.SongStart;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;

/**
 * An immutable, self-contained copy of a run of {@link StaffElement}s (and the
 * {@link Span} spans fully contained within it), independent of the
 * {@link Line} it was captured from.
 *
 * <p>This is the one place "clone + re-anchor" is defined, used by both capture
 * and every paste. The stored {@code Fragment} is never itself inserted, so repeated pastes
 * are independent.
 */
public record Fragment(List<StaffElement> elements, List<Span<?>> spans) {

    // Defensive copies — the class contract is immutability, and both factories
    // build their lists incrementally before handing them over.
    public Fragment {
        elements = List.copyOf(elements);
        spans = List.copyOf(spans);
    }

    /**
     * Captures the elements {@code range} names in {@code line}, along with every {@link Span} fully contained within
     * that range.
     *
     * <p>The captured range is first widened at both ends — past a trailing breath mark or a key
     * change standing behind a barline ({@link Line#effectiveEnd}), and back over the barline
     * in front of a key change ({@link Line#beginIncludingKeyChangeBarline}) — and then
     * trimmed of an orphan paired grace note at the tail. When the entire range is that one
     * orphan grace note (a one-element {@code range}), the trim drops it entirely and
     * capture returns an empty {@code Fragment}.
     *
     * <p><b>Widening the head is what keeps a pasted key change legal.</b> A key change is
     * never the first element on a line and always follows a barline or a repeat
     * ({@link KeyChangeElement}'s position invariant); capturing one without the
     * barline in front of it would put a fragment on the clipboard that violates that invariant
     * wherever it lands. The deletion side already takes both, so a cut that widened only its
     * deletion would also disagree with its own copy. A captured {@code FINAL_DOUBLE_BARLINE}
     * is normalized to {@code DOUBLE_BARLINE} so pasted content can never violate
     * the song-owned invariant. Repeats are copied verbatim, with no balance
     * validation.
     *
     * <p>The captured lyrics are then repaired as a run of their own
     * ({@link LyricRun#endDanglingChains}): a word hyphenated across the
     * boundary ends at the boundary, and a melisma that crosses it is closed or dropped, so
     * a paste can never join the copied syllables to whatever they land next to.
     *
     * <p>A span is captured only when the range contains <b>both</b> its endpoints; one with
     * an endpoint outside is dropped from the fragment. A {@link songscribe.dom.Tie} is held
     * the same way: a copied note keeps its tie only when the note the tie ends on is copied
     * too. A copied note whose tie end was not copied is untied
     * ({@link NoteElement#untieDetached}), so a paste never ties it to whatever it lands in
     * front of.
     *
     * @param line  The line to capture from
     * @param range The elements to capture, as selected — capture widens it itself
     * @return A new {@code Fragment} of clones, independent of {@code line}
     */
    public static Fragment capture(Line line, StaffElementRun.ElementRange range) {
        // Not effectiveRange: that also reaches back over a paired grace note, which a copy must
        // not do. A grace note cannot outlive its host, so a capture starting at a host leaves it
        // behind; a key change cannot exist without its barline, so a capture starting at one
        // takes the barline too.
        var effectiveBegin = line.beginIncludingKeyChangeBarline(range.begin());
        var effectiveEnd = line.effectiveEnd(range.end());

        // The host of a paired grace note sits at the very next index, which lies
        // outside the captured range when the grace note is the last included
        // element. Dropping the orphan grace note here also excludes every span
        // touching it, since neither of that span's endpoints can be a clone-map key.
        if (line.isPairedGraceNote(effectiveEnd)) {
            effectiveEnd--;
        }

        var originalToClone = new IdentityHashMap<StaffElement, StaffElement>();
        var elements = new ArrayList<StaffElement>();

        for (var i = effectiveBegin; i <= effectiveEnd; i++) {
            var original = line.getElement(i);
            var clone = cloneForCapture(original);

            originalToClone.put(original, clone);
            elements.add(clone);
        }

        untieClippedTies(originalToClone);

        // A lyric chain says "the syllable next to me continues this word" by position, so a
        // chain crossing either end of the capture would, once pasted, say it about whatever
        // element sits there. The clones are a run of their own, and this ends every
        // chain inside it — the same repair an edit runs, applied to the same lyrics.
        new DetachedLyricRun(elements).endDanglingChains();

        return new Fragment(elements, cloneSpans(line.spanRegistry().getSpans(), originalToClone));
    }

    /**
     * A {@code FINAL_DOUBLE_BARLINE} becomes a plain {@code DOUBLE_BARLINE}, so pasted
     * content can never violate the song-owned invariant that only the last line ends the
     * song.
     */
    private static StaffElement cloneForCapture(StaffElement original) {
        if (original.getType() == ElementType.FINAL_DOUBLE_BARLINE) {
            return ElementType.DOUBLE_BARLINE.newInstance();
        }

        return original.clone();
    }

    private static void untieClippedTies(Map<StaffElement, StaffElement> originalToClone) {
        for (var entry : originalToClone.entrySet()) {
            if (!(entry.getKey() instanceof NoteElement original)
                || !(entry.getValue() instanceof NoteElement clone)
                || !clone.isTiedToNext()) {
                continue;
            }

            var tie = original.tie();

            if (tie == null || !originalToClone.containsKey(tie.end())) {
                clone.untieDetached();
            }
        }
    }

    private static List<Span<?>> cloneSpans(
        List<Span<?>> source, Map<StaffElement, ? extends StaffElement> originalToClone) {
        var clonedSpans = new ArrayList<Span<?>>();

        for (var span : source) {
            var clone = cloneOnto(span, originalToClone);

            if (clone != null) {
                clonedSpans.add(clone);
            }
        }

        return clonedSpans;
    }

    /** @return {@code null} when either endpoint has no clone in {@code originalToClone} */
    private static <E extends StaffElement> @Nullable Span<E> cloneOnto(
        Span<E> span, Map<StaffElement, ? extends StaffElement> originalToClone) {
        var clonedAnchor = originalToClone.get(span.getAnchorElement());
        var clonedEnd = originalToClone.get(span.getEndElement());

        if (clonedAnchor == null || clonedEnd == null) {
            return null;
        }

        return span.copy(span.narrowEndpoint(clonedAnchor), span.narrowEndpoint(clonedEnd));
    }

    /**
     * Clones every element and re-anchors every span onto the fresh clones,
     * leaving this {@code Fragment} untouched. Since the stored fragment is
     * never itself inserted, calling this repeatedly yields independent
     * results each time.
     *
     * @return A fresh {@code Fragment} of clones, ready to insert
     */
    public Fragment instantiate() {
        var originalToClone = new IdentityHashMap<StaffElement, StaffElement>();
        var clonedElements = new ArrayList<StaffElement>(elements.size());

        for (var element : elements) {
            var clone = element.clone();
            originalToClone.put(element, clone);
            clonedElements.add(clone);
        }

        return new Fragment(clonedElements, cloneSpans(spans, originalToClone));
    }

    private StaffElementRun run() {
        return new DetachedLyricRun(elements);
    }

    public Key keyAtEndUnder(Key keyAtInsertion) {
        return run().keyAtEndUnder(keyAtInsertion);
    }

    /**
     * Returns this fragment with every key change that would land restating the key already
     * running there removed, each together with the element it is paired with.
     *
     * <p>A fragment carries the key it was copied under, not the key it is pasted into, so the
     * same clipboard content is stranded in one destination and meaningful in another. Removing
     * such a key change <em>before</em> the clones are inserted rather than after is what keeps a
     * paste one mutation: the chain walk, the fit measurement and the span
     * reconciliation all read the element list, and each of them must see the run that actually
     * lands.
     *
     * <p>What goes with the key change is {@link StaffElementRun}'s to say, asked of this
     * fragment as the run it is — so a pasted pair is resolved by the rule that resolves a
     * deleted one, and neither has to be kept in step with the other by hand. In practice that is
     * the barline in front of it, which {@link #capture} widens a captured range back over
     * precisely so this fragment holds both.
     *
     * <p>A span with an endpoint on a removed element cannot be drawn, so it goes with it. The
     * user did not ask to lose it, so the reduction names it in
     * {@link Reduction#strandedSpans}, for the paste to report. An ending can be hit,
     * and so can a tuplet that ends on a barline; a beam and a hairpin cannot, because their
     * endpoints are notes.
     *
     * @param keyAtInsertion the key in effect immediately before the insertion point
     * @return the reduction: this fragment and no stranded span when it strands no key change;
     *     otherwise a reduced fragment — which may hold no elements at all, when the whole
     *     fragment was one such pair — and every span it left out
     * @invariant every one of this fragment's spans is either in the reduced fragment's spans or
     *     in {@link Reduction#strandedSpans}, never both
     */
    public Reduction withoutRedundantKeyChanges(Key keyAtInsertion) {
        var stranded = run().redundantKeyChangeRanges(keyAtInsertion);

        if (stranded.isEmpty()) {
            return new Reduction(this, List.of());
        }

        return without(stranded);
    }

    private List<StaffElementRun.EffectiveRange> leadingBarRanges() {
        var run = run();
        var ranges = new ArrayList<StaffElementRun.EffectiveRange>();
        var index = 0;

        while (index < elements.size() && elements.get(index).getType().isBar()) {
            var effectiveRange = run.effectiveRange(StaffElementRun.ElementRange.single(index));
            ranges.add(effectiveRange);
            index = effectiveRange.range().end() + 1;
        }

        return ranges;
    }

    /** @param removed pairwise disjoint */
    private Reduction without(List<StaffElementRun.EffectiveRange> removed) {
        var removedIndices = new HashSet<Integer>();

        for (var effectiveRange : removed) {
            var range = effectiveRange.range();

            for (var index = range.begin(); index <= range.end(); index++) {
                removedIndices.add(index);
            }
        }

        var keptElements = new ArrayList<StaffElement>();

        for (var index = 0; index < elements.size(); index++) {
            if (!removedIndices.contains(index)) {
                keptElements.add(elements.get(index));
            }
        }

        var keptSpans = new ArrayList<Span<?>>();
        var strandedSpans = new ArrayList<StrandedSpan>();

        for (var span : spans) {
            // StaffElement overrides neither equals nor hashCode, so this compares by identity.
            var anchorIndex = elements.indexOf(span.getAnchorElement());
            var endIndex = elements.indexOf(span.getEndElement());

            if (removedIndices.contains(anchorIndex) || removedIndices.contains(endIndex)) {
                strandedSpans.add(new StrandedSpan(span, elements.subList(anchorIndex, endIndex + 1)));
            } else {
                keptSpans.add(span);
            }
        }

        return new Reduction(new Fragment(keptElements, keptSpans), strandedSpans);
    }

    /**
     * What {@link #withoutRedundantKeyChanges}, and then {@link #withoutLeadingBars}, leave of a
     * fragment.
     *
     * @param strandedSpans in the order the reductions ran, and within one reduction in the
     *     original fragment's order; empty when nothing was removed
     */
    public record Reduction(Fragment fragment, List<StrandedSpan> strandedSpans) {

        public Reduction {
            strandedSpans = List.copyOf(strandedSpans);
        }

        /**
         * For a paste that lands at the start of the song, where no bar line may stand
         * ({@link SongStart}). The drop names the key of the last key change it left out, for
         * the paste to move onto the destination line ({@link SongStart#keyBehind}).
         */
        public LeadingBarDrop withoutLeadingBars() {
            var leadingBars = fragment.leadingBarRanges();

            if (leadingBars.isEmpty()) {
                return new LeadingBarDrop(this, null);
            }

            var run = fragment.run();
            @Nullable Key movedKey = null;

            for (var leadingBar : leadingBars) {
                var key = SongStart.keyBehind(run, leadingBar);

                if (key != null) {
                    movedKey = key;
                }
            }

            var reduced = fragment.without(leadingBars);
            var stranded = new ArrayList<>(strandedSpans);
            stranded.addAll(reduced.strandedSpans());

            return new LeadingBarDrop(new Reduction(reduced.fragment(), stranded), movedKey);
        }
    }

    /**
     * What {@link Reduction#withoutLeadingBars} leaves of a fragment, and the key it moves.
     *
     * @param movedKey the key the paste makes the destination line's own; {@code null} when the
     *     drop left out no key change
     */
    public record LeadingBarDrop(Reduction reduction, @Nullable Key movedKey) {
    }

    /**
     * A span a {@link Reduction} left out because an endpoint of it was removed.
     *
     * @param span never added to any line
     * @param covered the elements it covered in the original fragment, anchor to end inclusive
     */
    public record StrandedSpan(Span<?> span, List<StaffElement> covered) {

        public StrandedSpan {
            covered = List.copyOf(covered);
        }

        /** The same reason a deletion that takes a span's endpoint gives. */
        public String reasonKey() {
            return Strings.REPORT_REASON_ENDPOINT_DELETED;
        }
    }
}
