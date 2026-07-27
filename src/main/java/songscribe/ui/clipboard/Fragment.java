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

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.RangeElement;
import songscribe.dom.StaffElement;

/**
 * An immutable, self-contained copy of a run of {@link StaffElement}s (and the
 * {@link RangeElement} spans fully contained within it), independent of the
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
 *                     └─ span kept iff BOTH endpoints ∈ map keys
 *                           └─ span.copy(map[anchor], map[end])
 *
 *   paste:  ClipboardManager.fragment ──instantiate()──> Fragment{fresh clones, priorAccidentals[] unchanged, fresh spans}
 *                                                             ├─ each clone's xOffset zeroed — a fragment
 *                                                             │  carries semantic content, not layout corrections
 *                                                             │
 *           (the stored Fragment is NEVER inserted, so paste N times ⇒ N independent results)
 * </pre>
 *
 * <p>Because the stored {@code Fragment} is never itself inserted, repeated pastes
 * are independent by construction.
 *
 * @param elements         The captured elements, cloned from the source line
 * @param priorAccidentals The effective accidental each element had on its source line,
 *                          parallel to {@code elements}; {@code null} where the element is
 *                          unpitched or already carries an explicit accidental of its own
 * @param spans            The {@link RangeElement} spans fully contained within {@code elements}
 */
public record Fragment(
    List<StaffElement> elements, List<StaffElement.@Nullable Accidental> priorAccidentals,
    List<RangeElement> spans) {

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
     * (inclusive), along with every {@link RangeElement} fully contained within
     * that range.
     *
     * <p>The captured range is first extended past a trailing breath mark
     * ({@link Line#effectiveDeleteEnd}) and then trimmed of an
     * orphan paired grace note at the tail. When the entire range is that one
     * orphan grace note ({@code begin == end}), the trim drops it entirely and
     * capture returns an empty {@code Fragment}. A captured {@code FINAL_DOUBLE_BARLINE}
     * is normalized to {@code DOUBLE_BARLINE} so pasted content can never violate
     * the song-owned invariant. Repeats are copied verbatim, with no balance
     * validation.
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
            var clone = original.clone();

            if (clone.getType() == ElementType.FINAL_DOUBLE_BARLINE) {
                clone = ElementType.DOUBLE_BARLINE.newInstance();
            }

            originalToClone.put(original, clone);
            elements.add(clone);

            // Resolve against `original`, never against `clone`: a clone's `line` field
            // still points at the source line, but `line.getElementIndex(clone)` returns
            // −1 because StaffElement overrides neither equals() nor hashCode(), so
            // `clone.findLastAccidental()` would silently skip the whole scan and return
            // the key signature alone — a wrong answer with no error.
            if (original.getAccidental() != null) {
                priorAccidentals.add(original.getAccidental());
            } else if (original.getType().isPitchedNote()) {
                priorAccidentals.add(original.findEffectiveAccidental(line, i));
            } else {
                priorAccidentals.add(null);
            }
        }

        return new Fragment(
            elements, priorAccidentals, cloneSpans(line.getRangeElements(), originalToClone));
    }

    /**
     * Copies every span in {@code source} onto the clones in {@code originalToClone},
     * keeping only those whose anchor and end are both present in the map — a span
     * with an endpoint outside the captured run cannot be re-anchored and is dropped.
     */
    private static List<RangeElement> cloneSpans(
        List<RangeElement> source, Map<StaffElement, StaffElement> originalToClone) {
        var clonedSpans = new ArrayList<RangeElement>();

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
