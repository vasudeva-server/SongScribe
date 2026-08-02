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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import songscribe.dom.DetachedLyricRun;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.LyricRun;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;

/**
 * An immutable, self-contained copy of a run of {@link StaffElement}s (and the
 * {@link Span} spans fully contained within it), independent of the
 * {@link Line} it was captured from.
 *
 * <p>This is the one place "clone + re-anchor" is defined, used by both capture
 * and every paste:
 *
 * <pre>
 *   copy:   Line ──capture(line,begin,end)──> Fragment{elements[], priorAccidentals[], spans[]}  ──> ClipboardManager.fragment
 *                     ├─ effectiveDeleteEnd() extends past trailing breath mark
 *                     ├─ drop orphan paired grace note at the tail
 *                     ├─ clone elements → IdentityHashMap&lt;orig,clone&gt;
 *                     ├─ resolve each element's effective accidental against the ORIGINAL
 *                     ├─ FINAL_DOUBLE_BARLINE → DOUBLE_BARLINE
 *                     ├─ drop the tempo at the initial-tempo anchor (song's first element)
 *                     ├─ DetachedLyricRun.endDanglingChains — every syllabic or melisma
 *                     │  chain leaving the run ends inside it
 *                     └─ span kept iff BOTH endpoints ∈ map keys
 *                           └─ span.copy(map[anchor], map[end])
 *
 *   paste:  ClipboardManager.fragment ──instantiate()──> Fragment{fresh clones, priorAccidentals[] unchanged, fresh spans}
 *                                                             ├─ each clone's xOffset zeroed — a fragment
 *                                                             │  carries semantic content, not layout corrections
 *                                                             │
 *                                                             ├─ PasteSpanReconciliation.reconcile — BEFORE any
 *                                                             │  mutation: straddled destination spans removed,
 *                                                             │  fragment spans a straddle invalidates dropped
 *                                                             ├─ clones inserted into the destination line
 *                                                             ├─ .dropTupletsRejectedByTarget — pasted tuplets the
 *                                                             │  destination's beat context rejects dropped; the
 *                                                             │  notes stay, only the bracket goes
 *                                                             └─ surviving spans added
 *
 *           (the stored Fragment is NEVER inserted, so paste N times ⇒ N independent results)
 * </pre>
 *
 * <p>Because the stored {@code Fragment} is never itself inserted, repeated pastes
 * are independent by construction.
 *
 * @param elements         The captured elements, cloned from the source line
 * @param priorAccidentals The effective accidental each element had on its source line,
 *                          parallel to {@code elements}; an element carrying an explicit
 *                          accidental of its own records that accidental, and one that
 *                          inherits records what it inherited. {@code null} only where the
 *                          element is unpitched (a barline, a breath mark, and so on)
 * @param spans            The {@link Span} spans fully contained within {@code elements}
 */
public record Fragment(
    List<StaffElement> elements, List<StaffElement.@Nullable Accidental> priorAccidentals,
    List<Span> spans) {

    // Defensive copies — the class contract is immutability, and both factories
    // build their lists incrementally before handing them over. priorAccidentals
    // can hold nulls (an unpitched or unaccidented element), and List.copyOf
    // rejects those, so it gets an ArrayList-backed copy instead.
    public Fragment {
        elements = List.copyOf(elements);
        priorAccidentals = Collections.unmodifiableList(new ArrayList<>(priorAccidentals));
        spans = List.copyOf(spans);

        if (priorAccidentals.size() != elements.size()) {
            throw new IllegalArgumentException(
                "priorAccidentals must be parallel to elements: expected "
                    + elements.size() + " but got " + priorAccidentals.size());
        }
    }

    /**
     * Captures the elements in {@code line} from {@code begin} to {@code end}
     * (inclusive), along with every {@link Span} fully contained within
     * that range.
     *
     * <p>The captured range is first extended past a trailing breath mark
     * ({@link Line#effectiveDeleteEnd}) and then trimmed of an
     * orphan paired grace note at the tail. When the entire range is that one
     * orphan grace note ({@code begin == end}), the trim drops it entirely and
     * capture returns an empty {@code Fragment}. A captured {@code FINAL_DOUBLE_BARLINE}
     * is normalized to {@code DOUBLE_BARLINE} so pasted content can never violate
     * the song-owned invariant. The song's initial tempo is dropped when the capture
     * starts at the song's first element. Repeats are copied verbatim, with no balance
     * validation.
     *
     * <p>The captured lyrics are then repaired as a run of their own
     * ({@link LyricRun#endDanglingChains}): a word hyphenated across the
     * boundary ends at the boundary, and a melisma that crosses it is closed or dropped, so
     * a paste can never join the copied syllables to whatever they land next to.
     *
     * @param line  The line to capture from
     * @param begin The index of the first element to capture
     * @param end   The index of the last element to capture
     * @return A new {@code Fragment} of clones, independent of {@code line}
     */
    public static Fragment capture(Line line, int begin, int end) {
        var effectiveEnd = line.effectiveDeleteEnd(end);

        // The host of a paired grace note sits at the very next index, which lies
        // outside the captured range when the grace note is the last included
        // element. Dropping the orphan grace note here also excludes every span
        // touching it, since neither of that span's endpoints can be a clone-map key.
        if (line.isPairedGraceNote(effectiveEnd)) {
            effectiveEnd--;
        }

        var originalToClone = new IdentityHashMap<StaffElement, StaffElement>();
        var elements = new ArrayList<StaffElement>();
        var priorAccidentals = new ArrayList<StaffElement.@Nullable Accidental>();

        for (var i = begin; i <= effectiveEnd; i++) {
            var original = line.getElement(i);
            var clone = cloneForCapture(line, original, i);

            originalToClone.put(original, clone);
            elements.add(clone);
            priorAccidentals.add(priorAccidentalOf(line, original, i));
        }

        // A lyric chain says "the syllable next to me continues this word" by position, so a
        // chain crossing either end of the capture would, once pasted, say it about whatever
        // element now sits there. The clones are a run of their own now, and this ends every
        // chain inside it — the same repair an edit runs, applied to the same lyrics.
        new DetachedLyricRun(elements).endDanglingChains();

        return new Fragment(
            elements, priorAccidentals, cloneSpans(line.getSpans(), originalToClone));
    }

    /**
     * Clones {@code original} for capture, stripping the two things an element may not
     * carry out of the song it belongs to.
     *
     * <p>A {@code FINAL_DOUBLE_BARLINE} becomes a plain {@code DOUBLE_BARLINE}, so pasted
     * content can never violate the song-owned invariant that only the last line ends the
     * song. (Cloning is skipped entirely in that case — the replacement is a fresh element,
     * so nothing of the original survives to be copied.)
     *
     * <p>A tempo attachment at the initial-tempo anchor is dropped. That tempo is the
     * song's, not the note's — see {@link Line#isInitialTempoAnchor}, which explains why a
     * tempo at that one position can never be an independent per-note tempo change — so
     * pasting it elsewhere would plant a spurious tempo change. A tempo on any other
     * element is a real tempo change and is kept.
     */
    private static StaffElement cloneForCapture(Line line, StaffElement original, int index) {
        if (original.getType() == ElementType.FINAL_DOUBLE_BARLINE) {
            return ElementType.DOUBLE_BARLINE.newInstance();
        }

        var clone = original.clone();

        if (line.isInitialTempoAnchor(index)) {
            var initialTempo = clone.findAttachment(TempoChangeAttachment.class);

            if (initialTempo != null) {
                clone.removeAttachment(initialTempo);
            }
        }

        return clone;
    }

    /**
     * The accidental {@code original} sounded with at index {@code index} of its own line,
     * or null when it is unpitched.
     *
     * <p>Resolved against {@code original}, never against its clone: a clone is born
     * detached, so {@code clone.findLastAccidental()} finds no line to scan and returns
     * null — a wrong answer with no error, and one that says "no accidental" rather than
     * "unknown".
     */
    private static StaffElement.@Nullable Accidental priorAccidentalOf(
        Line line, StaffElement original, int index) {
        var ownAccidental = original.getAccidental();

        if (ownAccidental != null) {
            return ownAccidental;
        }

        if (original.getType().isPitchedNote()) {
            return original.findEffectiveAccidental(line, index);
        }

        return null;
    }

    /**
     * Copies every span in {@code source} onto the clones in {@code originalToClone},
     * keeping only those whose anchor and end are both present in the map — a span
     * with an endpoint outside the captured run cannot be re-anchored and is dropped.
     */
    private static List<Span> cloneSpans(
        List<? extends Span> source, Map<StaffElement, ? extends StaffElement> originalToClone) {
        var clonedSpans = new ArrayList<Span>();

        for (var span : source) {
            var anchor = span.getAnchorElement();
            var end = span.getEndElement();

            if (anchor == null || end == null) {
                continue;
            }

            var clonedAnchor = originalToClone.get(anchor);
            var clonedEnd = originalToClone.get(end);

            if (clonedAnchor != null && clonedEnd != null) {
                clonedSpans.add(span.copy(clonedAnchor, clonedEnd));
            }
        }

        return clonedSpans;
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

            // A fragment carries semantic content, not layout corrections: xOffset is a
            // nudge from the computed position under one specific spring solve, with
            // specific neighbours, under a specific header width. Pasted elsewhere it is
            // meaningless at best and at worst recreates the collision it was made to fix.
            clone.setXOffsetPx(0);

            originalToClone.put(element, clone);
            clonedElements.add(clone);
        }

        return new Fragment(clonedElements, priorAccidentals, cloneSpans(spans, originalToClone));
    }
}
