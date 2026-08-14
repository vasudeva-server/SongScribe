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

/**
 * An immutable, self-contained copy of a run of {@link StaffElement}s (and the
 * {@link Span} spans fully contained within it), independent of the
 * {@link Line} it was captured from.
 *
 * <p>This is the one place "clone + re-anchor" is defined, used by both capture
 * and every paste:
 *
 * <p>{@code capture} extends the run past a trailing breath mark, drops an orphan paired grace note
 * at the tail, clones the elements into an identity map from original to clone, resolves each
 * element's effective accidental against the <em>original</em> line, demotes a final double barline
 * to a plain one, and ends every syllabic or melisma chain that would otherwise leave the run. A
 * span survives only when both its endpoints are in the map, and is copied onto the corresponding
 * clones.
 *
 * <p>{@code instantiate} produces fresh clones and fresh spans, leaving the recorded prior
 * accidentals as they are. Each clone's {@code xOffset} is zeroed — a fragment carries semantic
 * content, not layout corrections. {@code PasteSpanReconciliation.reconcile} then runs <em>before
 * any mutation</em>, removing straddled destination spans and invalidating fragment spans that
 * straddle a dropped one. The clones are inserted into the destination line, tuplets the
 * destination's beat context rejects have their brackets dropped (the notes stay), and the
 * surviving spans are added.
 *
 * <p>Because the stored {@code Fragment} is never itself inserted, repeated pastes
 * are independent by construction. See section 1 of {@code docs/clipboard.md} for the full
 * diagram.
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
     * <p>The captured range is first widened at both ends — past a trailing breath mark or a key
     * signature standing behind a barline ({@link Line#effectiveEnd}), and back over the barline
     * in front of a key signature ({@link Line#beginIncludingKeySignatureBarline}) — and then
     * trimmed of an orphan paired grace note at the tail. When the entire range is that one
     * orphan grace note ({@code begin == end}), the trim drops it entirely and
     * capture returns an empty {@code Fragment}.
     *
     * <p><b>Widening the head is what keeps a pasted key signature legal.</b> A key signature is
     * never the first element on a line and always follows a barline or a repeat
     * ({@link songscribe.dom.KeySignatureElement}'s position invariant); capturing one without the
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
     * an endpoint outside is dropped from the fragment, and that applies to every span type
     * including a {@link songscribe.dom.Tie}. Copying a range that clips a tie therefore
     * copies the elements the user selected and nothing else — the capture is never refused
     * and the clipboard is never left holding a span it cannot paste whole.
     *
     * @param line  The line to capture from
     * @param begin The index of the first element to capture
     * @param end   The index of the last element to capture
     * @return A new {@code Fragment} of clones, independent of {@code line}
     */
    public static Fragment capture(Line line, int begin, int end) {
        // Not effectiveRange: that also reaches back over a paired grace note, which a copy must
        // not do. A grace note cannot outlive its host, so a capture starting at a host leaves it
        // behind; a key signature cannot exist without its barline, so a capture starting at one
        // takes the barline too.
        var effectiveBegin = line.beginIncludingKeySignatureBarline(begin);
        var effectiveEnd = line.effectiveEnd(end);

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

        for (var i = effectiveBegin; i <= effectiveEnd; i++) {
            var original = line.getElement(i);
            var clone = cloneForCapture(original);

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
     * Clones {@code original} for capture, stripping the one thing an element may not carry
     * out of the song it belongs to.
     *
     * <p>A {@code FINAL_DOUBLE_BARLINE} becomes a plain {@code DOUBLE_BARLINE}, so pasted
     * content can never violate the song-owned invariant that only the last line ends the
     * song. (Cloning is skipped entirely in that case — the replacement is a fresh element,
     * so nothing of the original survives to be copied.)
     *
     * <p>A {@code TempoChangeAttachment} is always an ordinary tempo change now that the
     * song's own tempo lives on the {@code Song}, so it is always kept.
     */
    private static StaffElement cloneForCapture(StaffElement original) {
        if (original.getType() == ElementType.FINAL_DOUBLE_BARLINE) {
            return ElementType.DOUBLE_BARLINE.newInstance();
        }

        return original.clone();
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
