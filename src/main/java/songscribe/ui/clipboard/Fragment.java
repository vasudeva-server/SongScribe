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
import java.util.IdentityHashMap;
import java.util.List;

import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.RangeElement;
import songscribe.dom.StaffElement;
import songscribe.ui.component.ScoreViewController;

/**
 * An immutable, self-contained copy of a run of {@link StaffElement}s (and the
 * {@link RangeElement} spans fully contained within it), independent of the
 * {@link Line} it was captured from.
 *
 * <p>This is the one place "clone + re-anchor" is defined, used by both capture
 * and every paste:
 *
 * <pre>
 *   copy:   Line ──capture(line,begin,end)──> Fragment{elements[], spans[]}  ──> ClipboardManager.fragment
 *                     ├─ effectiveDeleteEnd() extends past trailing breath mark
 *                     ├─ drop orphan paired grace note at the tail
 *                     ├─ clone elements → IdentityHashMap&lt;orig,clone&gt;
 *                     ├─ FINAL_DOUBLE_BARLINE → DOUBLE_BARLINE
 *                     └─ span kept iff BOTH endpoints ∈ map keys
 *                           └─ span.copy(map[anchor], map[end])
 *
 *   paste:  ClipboardManager.fragment ──instantiate()──> Fragment{fresh clones, fresh spans}
 *                                                             │
 *           (the stored Fragment is NEVER inserted, so paste N times ⇒ N independent results)
 * </pre>
 *
 * <p>Because the stored {@code Fragment} is never itself inserted, repeated pastes
 * are independent by construction.
 */
public record Fragment(List<StaffElement> elements, List<RangeElement> spans) {

    /**
     * Captures the elements in {@code line} from {@code begin} to {@code end}
     * (inclusive), along with every {@link RangeElement} fully contained within
     * that range.
     *
     * <p>The captured range is first extended past a trailing breath mark
     * ({@link ScoreViewController#effectiveDeleteEnd}) and then trimmed of an
     * orphan paired grace note at the tail. A captured {@code FINAL_DOUBLE_BARLINE}
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
        var effectiveEnd = ScoreViewController.effectiveDeleteEnd(line, begin, end);

        // The host of a paired grace note sits at the very next index, which lies
        // outside the captured range when the grace note is the last included
        // element. Dropping the orphan grace note here also excludes every span
        // touching it, since neither of that span's endpoints can be a clone-map key.
        if (line.isPairedGraceNote(effectiveEnd)) {
            effectiveEnd--;
        }

        var originalToClone = new IdentityHashMap<StaffElement, StaffElement>();
        var elements = new ArrayList<StaffElement>();

        for (var i = begin; i <= effectiveEnd; i++) {
            var original = line.getElement(i);
            var clone = original.clone();

            if (clone.getType() == ElementType.FINAL_DOUBLE_BARLINE) {
                clone = ElementType.DOUBLE_BARLINE.newInstance();
            }

            originalToClone.put(original, clone);
            elements.add(clone);
        }

        var spans = new ArrayList<RangeElement>();

        for (var rangeElement : line.getRangeElements()) {
            var anchor = rangeElement.getAnchorElement();
            var rangeEnd = rangeElement.getEndElement();

            if (anchor == null || rangeEnd == null) {
                continue;
            }

            var clonedAnchor = originalToClone.get(anchor);
            var clonedEnd = originalToClone.get(rangeEnd);

            if (clonedAnchor != null && clonedEnd != null) {
                spans.add(rangeElement.copy(clonedAnchor, clonedEnd));
            }
        }

        return new Fragment(elements, spans);
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

        var clonedSpans = new ArrayList<RangeElement>(spans.size());

        for (var span : spans) {
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

        return new Fragment(clonedElements, clonedSpans);
    }
}
