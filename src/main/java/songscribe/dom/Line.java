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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;

import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.BeamingRemoval;
import songscribe.message.mutation.CrescendoAddition;
import songscribe.message.mutation.CrescendoRemoval;
import songscribe.message.mutation.DiminuendoAddition;
import songscribe.message.mutation.DiminuendoRemoval;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementRangeDeletion;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.KeyField;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LineLayoutChange;
import songscribe.message.mutation.LineLayoutField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;

public class Line implements LyricRun, SpanLookup {

    private static final int[][] FLAT_SHARP_ORDINAL = new int[][]{
        new int[]{0, 3, 6, 2, 5, 1, 4},
        new int[]{4, 1, 5, 2, 6, 3, 0},
    };

    private static final String TERMINAL_NOT_REMOVABLE =
        "The auto-maintained terminal may not be removed";

    /** Spans (beams, ties, trills, crescendo, diminuendo, tuplets, endings). */
    private final List<Span> spans = new ArrayList<>();

    /**
     * Live unmodifiable view of {@link #spans}, held once rather than wrapped per call:
     * every {@link SpanLookup} query iterates {@link #getSpans()}, and the wrapper tracks
     * later mutations of {@code spans} anyway.
     */
    private final List<Span> spansView = Collections.unmodifiableList(spans);

    private final Song song;
    private int keys = 0;
    @Nullable
    private KeyType keyType = null;
    private final List<StaffElement> elements = new ArrayList<>();

    /**
     * Live unmodifiable view of {@link #elements}, held once rather than wrapped per call:
     * {@link #getElements()} is on the repaint path, and the wrapper tracks later mutations
     * of {@code elements} anyway.
     */
    private final List<StaffElement> elementsView = Collections.unmodifiableList(elements);

    /**
     * Lazily-built position index over {@link #elements}, or null when it must be rebuilt.
     * <p>
     * Identity-keyed, matching {@code ArrayList.indexOf}'s semantics: no {@code StaffElement}
     * overrides {@code equals}/{@code hashCode}, and an override added later must not silently
     * change what an element's position means.
     * <p>
     * {@code volatile} is cheap insurance, not a concurrency guarantee. Nothing reads a
     * {@code Line} off the event thread today; were something to, this would keep a reader
     * from seeing a non-null reference to a map whose contents are not yet visible, since
     * {@code IdentityHashMap}'s internal table is not {@code final}. It would not make such a
     * reader safe — {@link #elements} is a plain {@code ArrayList}, so a rebuild racing a
     * structural change was never safe and this field does not change that.
     *
     * <pre>
     *   elements written  ──► attach/detach ──► elementIndexMap = null
     *        addElement ×2, setElement, removeElement, removeRange
     *
     *   getElementIndex() ──► null? rebuild : cached lookup
     * </pre>
     * Writes to {@link #spans} are absent from that picture because they cannot reach it:
     * a span holds no position in {@code elements}, and {@link #appendChild} /
     * {@link #removeChild} touch the span list alone.
     */
    private volatile @Nullable Map<StaffElement, Integer> elementIndexMap = null;

    /**
     * Default beat change Y position (-3 staff spaces above middle)
     */
    public static final double BEAT_CHANGE_DEFAULT_Y_SS = -3.0;  // -24px

    /**
     * Default lyrics Y position (below staff)
     */
    public static final double LYRICS_DEFAULT_Y_SS = 6.25;  // 50px
    /**
     * Y offset for lyrics display (default: 50, below staff).
     * <p>
     * Note: This field is still in active use for line-level lyrics positioning.
     * Per-instance lyrics offsets are not yet implemented.
     */
    private double lyricsYPosSs = LYRICS_DEFAULT_Y_SS;

    /**
     * Default first/second ending Y position (above staff)
     */
    public static final double ENDING_DEFAULT_Y_SS = -3.125;  // -25px

    /**
     * Default trill Y position (above staff)
     */
    public static final double TRILL_DEFAULT_Y_SS = -3.375;  // -27px

    /**
     * How far past a span's endpoint a same-type span may sit and still count as
     * touching it, in elements. Two spans this close describe one uninterrupted
     * gesture, so adding or reshaping either one merges them.
     * <p>
     * Every place that decides "are these two spans adjacent?" must read this, or
     * the menu would offer to extend a hairpin the model then declines to merge.
     */
    public static final int SPAN_ADJACENCY_REACH = 1;

    /** Ratio multiplier for horizontal element spacing (default: 1.0, user-adjustable). */
    private float elementSpacingRatio = 1f;

    public Line(Song song) {
        this.song = song;
    }

    public Song getSong() {
        return song;
    }

    public int getKeyAccidentalCount() {
        return keys;
    }

    /**
     * Applies a single mutation, delegating to the parent song's bracket.
     *
     * <p>A modification bracket must be open — the mutation is recorded and the
     * mutator runs inside it. If the song has suspended tracking via
     * {@link Song#withoutMutationTracking(Runnable)}, the mutator runs directly
     * without tracking.
     *
     * @throws IllegalStateException if the song has neither an open modification
     *     bracket nor suspended tracking
     */
    public void applyChange(Mutation mutation, Runnable mutator) {
        if (song.isMutationTrackingSuspended()) {
            mutator.run();
            return;
        }

        if (!song.isModifying()) {
            throw new IllegalStateException(
                "Line.applyChange called outside a modification bracket for " + mutation
            );
        }

        song.applyChange(mutation, mutator);
    }

    /**
     * Executes {@code body} inside a modification bracket on the parent song.
     */
    public void withModification(Runnable body) {
        song.withModification(body);
    }

    /**
     * The value-returning form of {@link #withModification(Runnable)}, for a body
     * whose outcome the caller must inspect after the bracket closes.
     *
     * @param body The modification to run
     * @return Whatever {@code body} returns
     */
    public <T> T withModificationResult(Supplier<T> body) {
        return song.withModificationResult(body);
    }

    /**
     * Executes {@code body} inside a modification bracket on the parent song that
     * declares {@code label} as its op-name (Tier B).
     */
    public void withModification(String label, Runnable body) {
        song.withModification(label, body);
    }

    /**
     * Executes {@code body} inside a modification bracket on the parent song, naming the undo
     * step {@code label} when there is one and letting the Tier-A pending op-name stand when
     * there is not.
     * <p>
     * For a caller that computes a label conditionally and would otherwise have to branch on it
     * to pick between the two overloads above. The two overloads cannot be collapsed into one
     * {@code @Nullable} parameter: that would be the same signature as the labeled overload, and
     * routing every unlabeled bracket through it would change which method every existing caller
     * — and every test that stubs one of them — actually invokes.
     */
    public void withOptionallyNamedModification(@Nullable String label, Runnable body) {
        if (label != null) {
            withModification(label, body);
        } else {
            withModification(body);
        }
    }

    public void setKeyAccidentalCount(int keys) {
        if (this.keys == keys) {
            return;
        }

        var old = this.keys;
        applyChange(
            new LineKeyChange(this, KeyField.ACCIDENTAL_COUNT, old, keys),
            () -> this.keys = keys
        );
    }

    public @Nullable KeyType getKeyType() {
        return keyType;
    }

    public void setKeyType(@Nullable KeyType keyType) {
        if (this.keyType == keyType) {
            return;
        }

        var old = this.keyType;
        applyChange(
            new LineKeyChange(this, KeyField.KEY_TYPE, old, keyType),
            () -> this.keyType = keyType
        );
    }

    // ========================================================================
    // Line element parentage
    //
    // For the staff elements held in `elements`, attach/detach are the only writers of
    // parentLine. Every mutation of `elements` funnels through one of them from inside
    // the applyChange mutator lambda, so parentage moves with the recorded change:
    //
    //     addElement(StaffElement) ──┐
    //     addElement(int, …) ────────┼──► attach
    //     setElement ────────────────┘
    //
    //     setElement ────────────────┐
    //     removeElement ─────────────┼──► detach
    //     removeRange ───────────────┘
    //
    // A span is never attached to a line at all. Its inherited parentLine stays null for
    // its whole life, and Span.isIn(Line) derives parentage from where its endpoints sit,
    // so a span whose endpoints straddle a line boundary is in both of those lines.
    // appendChild/removeChild — still the only writers of `spans` — therefore write the
    // lists alone, and keep both lines of such a pair holding the span so every sweep
    // over a line's spans sees the half that belongs to it.
    //
    // Attachments (articulations, fermatas) live in neither list, so none of this
    // covers them: LineElement.addChild/removeChild own their parentLine, and
    // propagateParentLine is what carries a host element's line down to them.
    //
    // Invariants:
    //   staff element — element.getParentLine() == L ⟺ L.elements contains the element,
    //   and null ⟺ the element is in no line at all.
    //
    //   span — span.isIn(L) ⟹ L.spans contains the span. One-directional on purpose:
    //   a span with both endpoints detached is in no line by the derived answer, yet is
    //   still in the list it was added to, which is exactly where it sat before.
    // ========================================================================

    /** Points {@code element} and everything below it at this line. */
    private void attach(StaffElement element) {
        elementIndexMap = null;
        element.setParentLine(this);
        element.propagateParentLine(this);
    }

    /** Clears {@code element}'s line pointer, and those of everything below it. */
    private void detach(StaffElement element) {
        // Invalidate above the early return below, so a re-parent cannot skip it.
        elementIndexMap = null;

        // A re-parent that ran attach() first already owns this element — don't
        // clear a pointer that now names a different line.
        //
        // No caller reaches this return today: every re-parent detaches before it attaches,
        // setElement deliberately so. It is kept because the cost of being wrong here
        // is a live element with no line, and untestable — a test cannot reach it either.
        if (element.getParentLine() != this) {
            return;
        }

        element.setParentLine(null);
        element.propagateParentLine(null);
    }

    /**
     * Returns the line holding an endpoint of {@code span} that is not this line, or
     * {@code null} when neither endpoint is in another line.
     * <p>
     * A span reaches {@link #appendChild} either with both endpoints in this line, with
     * both detached, or — for a cross-line tie — with one endpoint here and one in the
     * adjacent line. This answers the third case, so that tie lands in both lines' lists.
     */
    private @Nullable Line otherLineOf(Span span) {
        var anchorLine = span.getAnchorLine();

        if (anchorLine != null && anchorLine != this) {
            return anchorLine;
        }

        var endLine = span.getEndLine();

        if (endLine != null && endLine != this) {
            return endLine;
        }

        return null;
    }

    /**
     * Appends {@code element} to {@code spans} — this line's, and the other line of the
     * pair when the span's endpoints straddle a boundary. Call from inside an applyChange
     * mutator lambda, so the list write moves with the recorded change. The {@code elements}
     * list has no counterpart because every one of its mutations inserts at an index or
     * replaces in place.
     * <p>
     * The other line only gains the span if it is not already holding it. A span whose two
     * endpoints share a line, added to a <em>different</em> line, names its own line as the
     * other one and would otherwise land there twice — one copy from wherever it was first
     * added, one from here — leaving {@link #removeChild}, which takes out a single copy per
     * list, unable to get rid of it.
     */
    private void appendChild(Span element) {
        spans.add(element);

        var otherLine = otherLineOf(element);

        if (otherLine != null && !otherLine.spans.contains(element)) {
            otherLine.spans.add(element);
        }
    }

    /**
     * Removes {@code element} from {@code spans} — this line's, and the other line of the
     * pair, so a cross-line span leaves both halves together. Takes the index its caller
     * already located in this line, rather than scanning the list a second time.
     */
    private void removeChild(Span element, int index) {
        spans.remove(index);

        var otherLine = otherLineOf(element);

        if (otherLine != null) {
            otherLine.spans.remove(element);
        }
    }

    /**
     * Appends {@code element}, or inserts it before the auto-maintained terminal when this
     * line carries one, so the terminal remains last.
     * <p>
     * Resolves the position and hands off to {@link #addElement(int, StaffElement)} rather
     * than inserting here: appending is an insertion at the end, and the companion work that
     * overload does — dropping the spans the new element invalidates above all — applies to it
     * just the same. It looked as though it did not while every span ended somewhere inside
     * its own line, because nothing appended past the last element could land between a span's
     * endpoints. A tie whose end note is in the <em>next</em> line breaks that: everything
     * after its anchor is between the two notes, and appending is exactly how something gets
     * there (#493).
     */
    public void addElement(StaffElement element) {
        var lastIdx = elements.size() - 1;
        var insertBeforeFinal = lastIdx >= 0
            && song.isAutoMaintainedTerminal(elements.get(lastIdx), this);

        addElement(insertBeforeFinal ? lastIdx : elements.size(), element);
    }

    /**
     * Returns {@code true} when the terminal mutation guards in this class should
     * be bypassed: mutation tracking is suspended (test setup or file load), the
     * song is currently auto-maintaining the invariant, or undo/redo is replaying
     * a recorded batch (whose intermediate states legitimately violate the
     * invariant, e.g. undoing terminal maintenance before the line op).
     */
    private boolean isTerminalGuardBypassed() {
        return song.isMutationTrackingSuspended()
            || song.isInAutoMaintenance()
            || song.isReplaying();
    }

    /**
     * True when the terminal guard is live and the element at {@code index} is the song's
     * auto-maintained terminal. Uses the two-argument instance predicate, not the static
     * single-argument form — a guard must not treat an unparented element as safe.
     */
    private boolean guardsTerminalAt(int index) {
        return !isTerminalGuardBypassed() && song.isAutoMaintainedTerminal(elements.get(index), this);
    }

    public void addElement(int index, StaffElement element) {
        if (!isTerminalGuardBypassed()
                && element.getType() == ElementType.FINAL_DOUBLE_BARLINE
                && (song.indexOfLine(this) != song.lineCount() - 1
                    || index != elementCount())) {
            throw new IllegalStateException(
                "FINAL_DOUBLE_BARLINE may only be appended to the last line");
        }

        // During replay the recorded batch already carries the companion
        // mutations below — re-deriving them would double-apply.
        if (!song.isReplaying()) {
            var tuplet = findTupletAt(index);

            if (tuplet != null && index > tuplet.getAnchorElementIndex()) {
                removeTuplet(tuplet);
            }

            var insertedType = element.getType();
            // Companion removals precede the primary insertion so reverse-order undo
            // restores the primary element before re-adding dependent spans.
            var invalidatedSpans = spans.stream()
                .filter(r -> r.isInvalidatedByInsertion(index, insertedType, this))
                .toList();
            invalidatedSpans.forEach(this::removeInvalidatedSpan);

            // When prepending to a non-empty first line, the previous first element
            // carried the initial tempo — move it to the new first element. The
            // removal is a tracked modification; the attachment on the incoming
            // element needs no record because the element is not yet in the document,
            // so the ElementInsertion below captures its attached state.
            //
            // Not routed through Song.withBeatDefiningEdit: the displacement is the one
            // beat-defining write that cannot change the beat anywhere. The tempo leaves
            // element 0 of line 0 and lands back on element 0 of line 0 — the same
            // document position, carrying the same Tempo — so resolveBeatAt returns the
            // same beat for every position in the song and no tuplet can be invalidated.
            if (isInitialTempoAnchor(index) && !elements.isEmpty()) {
                var displacedFirstElement = elements.getFirst();
                var displacedTempo =
                    displacedFirstElement.findAttachment(TempoChangeAttachment.class);

                if (displacedTempo != null) {
                    modifyElement(0, ElementField.TEMPO_CHANGE,
                        () -> displacedFirstElement.removeAttachment(displacedTempo));
                    element.addAttachment(displacedTempo.copy(element));
                }
            }
        }

        applyChange(
            new ElementInsertion(this, index, element),
            () -> {
                attach(element);
                elements.add(index, element);
            }
        );
    }

    /**
     * Replaces the element at {@code index}, guarded on two fronts: the incoming element may
     * not be a {@code FINAL_DOUBLE_BARLINE} unless it lands in the terminal position, and the
     * element being displaced may not be the auto-maintained terminal unless the replacement is
     * itself a valid terminal type (the exemption {@link TerminalMaintainer#replaceTerminal}
     * relies on to swap terminal types without raising the auto-maintenance flag).
     */
    public void setElement(int index, StaffElement element) {
        if (!isTerminalGuardBypassed()
                && element.getType() == ElementType.FINAL_DOUBLE_BARLINE
                && (song.indexOfLine(this) != song.lineCount() - 1
                    || index != elementCount() - 1)) {
            throw new IllegalStateException(
                "FINAL_DOUBLE_BARLINE may only replace the last element on the last line");
        }

        if (guardsTerminalAt(index) && !element.getType().isValidSongTerminal()) {
            throw new IllegalStateException(
                "The auto-maintained terminal may only be replaced by a valid terminal");
        }

        var oldElement = elements.get(index);

        // Skipped during replay: the recorded batch already carries the removals.
        // The anchor re-pointing in the mutator below still runs — it is
        // self-inverting and required for span references to stay valid.
        if (!song.isReplaying()) {
            // Pre-compute before the mutator so findRepeatSplitElement sees the pre-replacement line.
            // Companion removals precede the primary replacement so reverse-order undo
            // restores the primary element before re-adding dependent spans.
            var invalidatedSpans = spans.stream()
                .filter(r -> r.isInvalidatedByReplacement(oldElement, element, this))
                .toList();
            invalidatedSpans.forEach(this::removeInvalidatedSpan);
        }

        applyChange(
            new ElementReplacement(this, index, oldElement, element),
            () -> {
                // Detach first: a self-replace (element == oldElement) would otherwise
                // end with a live element holding a null parentLine, because detach's
                // `!= this` guard would see the pointer attach just wrote.
                detach(oldElement);
                elements.set(index, element);
                attach(element);

                // Update stale anchor/end references in surviving spans so that
                // getAnchorElementIndex()/getEndElementIndex() remain valid after the swap.
                for (var r : spans) {
                    if (r.getAnchorElement() == oldElement) {
                        r.setAnchorElement(element);
                    }

                    if (r.getEndElement() == oldElement) {
                        r.setEndElement(element);
                    }
                }
            }
        );
    }

    /**
     * Applies a field change to the element at {@code index}. Clones the element
     * before running {@code mutator} so the resulting {@link ElementModification}
     * carries a stable pre-mutation snapshot for undo — centralizing the
     * clone-before-mutate contract that would otherwise have to be repeated at
     * every emission site.
     */
    @Override
    public void modifyElement(int index, ElementField field, Runnable mutator) {
        modifyElement(index, EnumSet.of(field), mutator);
    }

    public void modifyElement(int index, EnumSet<ElementField> fields, Runnable mutator) {
        // The replayer never calls modifyElement, but the gate keeps the
        // suppression contract symmetric with the other helpers.
        if (!song.isReplaying()
                && !Collections.disjoint(fields, ElementField.DURATION_AFFECTING)) {
            removeOverlappingTuplets(index, index);
        }

        // Run the mutator up front so the record can carry both the before and
        // after clones; applyChange then only appends the record. Equivalent
        // under withoutMutationTracking: the mutator has run, nothing is recorded.
        var element = elements.get(index);
        var beforeClone = element.clone();
        mutator.run();
        var afterClone = element.clone();
        applyChange(
            new ElementModification(this, index, fields, beforeClone, afterClone),
            () -> {}
        );
    }

    /**
     * Returns the first pitched note on this line, or null if it has none. Used to
     * defer the initial-tempo dialog until a host note exists: when the first note is
     * a grace note (an ornament), the dialog waits until the following pitched host
     * note is placed, even though the tempo itself anchors on the first element.
     */
    public @Nullable StaffElement firstPitchedElement() {
        for (var element : elements) {
            if (element.getType().isPitchedNote()) {
                return element;
            }
        }

        return null;
    }

    /**
     * Whether {@code elementIndex} of this line is where the song's initial tempo is
     * anchored — the first element of the first line, the position
     * {@link #attachInitialTempoIfNeeded} mirrors it onto.
     *
     * <p>A {@link TempoChangeAttachment} at that position is the song's own tempo, never
     * an independent per-note tempo change. Nothing in the model distinguishes the two —
     * the mirrored attachment carries no mark identifying it as such, and the tempo dialog
     * edits it in place like any other — so every reader of that position must treat what
     * it finds there as the song's tempo.
     */
    public boolean isInitialTempoAnchor(int elementIndex) {
        return elementIndex == 0 && song.indexOfLine(this) == 0;
    }

    /**
     * Attaches the song's initial tempo to the first element of this line if not already set.
     *
     * <p>Routed through {@link Song#withBeatDefiningEdit} because attaching a tempo defines a
     * beat. In practice this runs only while mutation tracking is suspended (the load path),
     * where the helper validates nothing and the load pass judges the tuplets instead — but
     * the routing keeps the chokepoint whole if that ever changes.
     */
    void attachInitialTempoIfNeeded() {
        if (elements.isEmpty()) {
            return;
        }

        var element = elements.getFirst();

        if (element.findAttachment(TempoChangeAttachment.class) == null) {
            var initialTempo = song.getTempo();

            if (initialTempo != null) {
                Song.withBeatDefiningEditOn(element,
                    () -> element.addAttachment(new TempoChangeAttachment(element, initialTempo)));
            }
        }
    }

    @Override
    public StaffElement getElement(int index) {
        return elements.get(index);
    }

    public List<StaffElement> getElements() {
        return elementsView;
    }

    // Returns a sublist of elements from start to end inclusive
    public List<StaffElement> getElements(int start, int end) {
        // subList is exclusive of the end index, so we add 1.
        // Wrapped per call because the bounds vary: a caller that could reorder or
        // clear the sublist would move element positions behind the index's back.
        return Collections.unmodifiableList(elements.subList(start, end + 1));
    }

    public void removeElement(int index) {
        if (guardsTerminalAt(index)) {
            throw new IllegalStateException(TERMINAL_NOT_REMOVABLE);
        }

        var deleted = elements.get(index);

        // During replay the recorded batch already carries the companion
        // removals — re-deriving them would double-apply.
        if (!song.isReplaying()) {
            removeOverlappingTuplets(index, index);
            var deletedList = List.of(deleted);
            adjustHairpinsForDeletion(deletedList);

            // Companion removals precede the primary deletion so reverse-order undo
            // re-inserts the element before re-adding the spans anchored to it.
            var invalidated = spans.stream()
                .filter(r -> !(r instanceof Hairpin))
                .filter(r -> r.isInvalidatedBy(deletedList)
                    || r.isInvalidatedByDeletion(deletedList, this))
                .toList();
            invalidated.forEach(this::removeInvalidatedSpan);
        }

        var displacedTempo = initialTempoBeingRemoved(index);

        applyChange(
            new ElementDeletion(this, index, deleted),
            () -> {
                detach(deleted);
                elements.remove(index);
            }
        );

        reanchorInitialTempo(displacedTempo);
    }

    /**
     * Removes all elements in the contiguous range {@code [from, to]} (inclusive)
     * and posts a single {@link ElementRangeDeletion} mutation.
     *
     * @param from the index of the first element to remove
     * @param to   the index of the last element to remove (inclusive)
     */
    public void removeRange(int from, int to) {
        if (IntStream.rangeClosed(from, to).anyMatch(this::guardsTerminalAt)) {
            throw new IllegalStateException(TERMINAL_NOT_REMOVABLE);
        }

        var deletedElements = List.copyOf(elements.subList(from, to + 1));

        // During replay the recorded batch already carries the companion
        // removals — re-deriving them would double-apply.
        if (!song.isReplaying()) {
            removeOverlappingTuplets(from, to);
            adjustHairpinsForDeletion(deletedElements);

            // Companion removals precede the primary deletion so reverse-order undo
            // re-inserts the elements before re-adding the spans anchored to them.
            var invalidated = spans.stream()
                .filter(r -> !(r instanceof Hairpin))
                .filter(r -> r.isInvalidatedBy(deletedElements)
                    || r.isInvalidatedByDeletion(deletedElements, this))
                .toList();
            invalidated.forEach(this::removeInvalidatedSpan);
        }

        var displacedTempo = initialTempoBeingRemoved(from);

        applyChange(
            new ElementRangeDeletion(this, from, to, deletedElements),
            () -> {
                deletedElements.forEach(this::detach);
                elements.subList(from, to + 1).clear();
            }
        );

        reanchorInitialTempo(displacedTempo);
    }

    /**
     * The song's initial tempo when removing from {@code from} would take the anchor
     * element with it, else null. Read before the removal, while the anchor is still in
     * place. Suppressed during replay: the recorded batch already carries the companion
     * modification.
     */
    private @Nullable TempoChangeAttachment initialTempoBeingRemoved(int from) {
        if (song.isReplaying() || !isInitialTempoAnchor(from)) {
            return null;
        }

        return elements.get(from).findAttachment(TempoChangeAttachment.class);
    }

    /**
     * Moves the song's initial tempo onto the new first element after a removal took the
     * anchor away. The tempo describes the song, not the note it happened to sit on (see
     * {@link #isInitialTempoAnchor}), so it must not disappear along with that note — the
     * mirror image of the displacement {@link #addElement(int, StaffElement)} performs when
     * an insertion pushes the anchor aside.
     *
     * <p>Emitted after the primary deletion, since the new first element only reaches index
     * 0 once the removal has happened. Reverse-order undo therefore strips this tempo again
     * before re-inserting the element that owned it.
     */
    private void reanchorInitialTempo(@Nullable TempoChangeAttachment displacedTempo) {
        if (displacedTempo == null || elements.isEmpty()) {
            return;
        }

        var newFirstElement = elements.getFirst();

        // A tempo already on the new first element is the song's tempo in its own right —
        // attachInitialTempoIfNeeded defers to an existing attachment, and so does this.
        if (newFirstElement.findAttachment(TempoChangeAttachment.class) != null) {
            return;
        }

        modifyElement(0, ElementField.TEMPO_CHANGE,
            () -> newFirstElement.addAttachment(displacedTempo.copy(newFirstElement)));
    }

    @Override
    public int elementCount() {
        return elements.size();
    }

    /**
     * Returns the element count excluding a trailing auto-maintained terminal
     * ({@code FINAL_DOUBLE_BARLINE} or {@code REPEAT_RIGHT}). Use this wherever a
     * computation should treat the song-owned terminal as if it were not there
     * (insertion spacing, preview positioning, etc.).
     */
    @Override
    public int effectiveElementCount() {
        var count = elements.size();

        if (count > 0 && song.isAutoMaintainedTerminal(elements.get(count - 1), this)) {
            return count - 1;
        }

        return count;
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /**
     * Returns {@code element}'s position in this line, or -1 when it is not in this line.
     * A null element is not in any line, so it too resolves to -1 — matching
     * {@code ArrayList.indexOf}, which this replaced.
     * <p>
     * Answered from {@link #elementIndexMap}, rebuilt on demand after any element mutation.
     */
    public int getElementIndex(@Nullable StaffElement element) {
        var map = elementIndexMap;

        if (map == null) {
            var rebuilt = new IdentityHashMap<StaffElement, Integer>(elements.size());

            for (var i = 0; i < elements.size(); i++) {
                // First wins, matching ArrayList.indexOf: a duplicated element must
                // resolve to its earliest position, not its latest.
                rebuilt.putIfAbsent(elements.get(i), i);
            }

            // Publish only the fully-built map — never a partially-filled one.
            map = rebuilt;
            elementIndexMap = rebuilt;
        }

        var index = map.get(element);

        return index == null ? -1 : index;
    }

    /**
     * Returns whether inserting at the given index would conflict with a paired grace note.
     * A grace note is paired when it has a connecting {@link StaffElement.Glissando}
     * linking it to the following note.
     * <p>
     * This returns {@code true} in two cases:
     * <ul>
     *   <li>The element at {@code index} is itself a paired grace note (clicking on it)</li>
     *   <li>The element at {@code index - 1} is a paired grace note (clicking between it and its host)</li>
     * </ul>
     */
    public boolean isInsideGraceHostPair(int index) {
        return isPairedGraceNote(index) || isPairedGraceNote(index - 1);
    }

    /**
     * If the element immediately before {@code index} is a grace note, returns its index.
     * Returns -1 otherwise.
     */
    public int precedingGraceNoteIndex(int index) {
        var candidateIndex = index - 1;

        if (candidateIndex < 0) {
            return -1;
        }

        return getElement(candidateIndex).getType().isGraceNote() ? candidateIndex : -1;
    }

    /** Returns true when the element at {@code index} is the host of a paired grace note. */
    public boolean isHostOfPairedGraceNote(int index) {
        return index >= 1 && isPairedGraceNote(index - 1);
    }

    /**
     * Returns whether the element at {@code index} and the one following it are both notes at the
     * same pitch — the span a connecting glissando may never cover, since there is no distance for
     * it to traverse.
     * <p>
     * This is the one definition of that condition. The slide tool asks it before attaching a
     * glissando, a pitch shift asks it to strip one whose two notes have come together, and the
     * layout and render passes ask it so that neither draws nor registers geometry for one that
     * reached the model through an import. Each caller keeps its own further rules — which
     * elements may anchor a glissando at all is a separate question from whether these two
     * share a pitch.
     * <p>
     * Grace notes count on either side, since they carry a pitch like any other note.
     *
     * @param index index of the leading element; out of range, or last in the line, yields false
     */
    public boolean isSamePitchAsFollower(int index) {
        if (index < 0 || index + 1 >= elements.size()) {
            return false;
        }

        var element = elements.get(index);
        var follower = elements.get(index + 1);

        if (!element.getType().isNote() || !follower.getType().isNote()) {
            return false;
        }

        return element.getPitch() == follower.getPitch();
    }

    /**
     * A breath mark immediately after {@code end} is positionally attached to the
     * last selected element, so it must be included in a deletion or copy range that
     * ends at {@code end}. Returns {@code end} extended past that trailing breath
     * mark, or {@code end} unchanged if there is none. Pure query — mutates nothing.
     */
    public int effectiveDeleteEnd(int end) {
        if (end + 1 < effectiveElementCount() && getElement(end + 1).getType().isBreathMark()) {
            return end + 1;
        }

        return end;
    }

    /**
     * @param pitchType 0 for B, 1 for C, 2 for D, ..., 6 for A
     * @return true if there is a leading key for that pitch type
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean keyExists(int pitchType) {
        if (keyType == null) {
            return false;
        }

        var nonNullKeyType = keyType;
        return IntStream.range(0, keys).anyMatch(
            i -> FLAT_SHARP_ORDINAL[nonNullKeyType.ordinal() - 1][i] == pitchType
        );
    }

    public double getLyricsYPosSs() {
        return lyricsYPosSs;
    }

    public void setLyricsYPosSs(double lyricsYPosSs) {
        var old = this.lyricsYPosSs;
        applyChange(
            new LineLayoutChange(this, LineLayoutField.LYRICS_Y_POS_SS, old, lyricsYPosSs),
            () -> this.lyricsYPosSs = lyricsYPosSs
        );
    }

    public void changeElementSpacingRatio(float ratio) {
        var old = elementSpacingRatio;
        var newRatio = old * ratio;
        applyChange(
            new LineLayoutChange(this, LineLayoutField.ELEMENT_SPACING_RATIO, old, newRatio),
            () -> elementSpacingRatio = newRatio
        );
    }

    /**
     * Sets the spacing ratio to an absolute value, emitting the same
     * {@link LineLayoutChange} as {@link #changeElementSpacingRatio(float)}.
     * Exists for undo replay: the multiplying setter would accumulate float
     * error across undo/redo cycles.
     */
    public void setElementSpacingRatioAbsolute(float ratio) {
        var old = elementSpacingRatio;
        applyChange(
            new LineLayoutChange(this, LineLayoutField.ELEMENT_SPACING_RATIO, old, ratio),
            () -> elementSpacingRatio = ratio
        );
    }

    public float getElementSpacingRatio() {
        return elementSpacingRatio;
    }

    /**
     * Adds a tie span. Tie ranges never coalesce — a chain of tied notes is
     * represented as one {@link Tie} per adjacent pair, each rendered as its own arc,
     * even when two ties share an endpoint note.
     */
    public void addTie(Tie tie) {
        applyChange(new TieAddition(this, tie), () -> appendChild(tie));
    }

    /**
     * Removes a tie span that was previously added via {@link #addTie(Tie)}.
     */
    public void removeTie(Tie tie) {
        var index = spans.indexOf(tie);

        if (index < 0) {
            return;
        }

        applyChange(
            new TieRemoval(this, tie),
            () -> removeChild(tie, index)
        );
    }

    /**
     * Returns the index of the nearest element to {@code fromIndex} that is not a grace
     * note, stepping by {@code step} ({@code -1} to search backward, {@code 1} forward),
     * or -1 if the line runs out first. {@code fromIndex} itself is not examined.
     *
     * <p>Grace notes are transparent to callers reasoning about a note's real neighbors:
     * one can sit between two beamed notes without joining their beam, so the neighbor
     * that matters is the one on its far side.
     *
     * @param fromIndex The index to search out from
     * @param step The direction to search in
     * @return The nearest non-grace index, or -1 if there is none
     */
    public int nearestNonGraceIndex(int fromIndex, int step) {
        for (var i = fromIndex + step; i >= 0 && i < elementCount(); i += step) {
            if (!getElement(i).getType().isGraceNote()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Adds a beam span, merging with any existing beams that share endpoints.
     * <p>
     * If an existing beam ends at the new beam's start, or starts at the new beam's end,
     * the spans are merged into a single wider beam. Any beam whose range is fully
     * covered by the merged result is removed.
     */
    public void addBeaming(Beam beam) {
        // During replay the recorded BeamingAddition already carries the merged
        // span and the batch carries the subsumed-beam removals — just add.
        if (!song.isReplaying()) {
            // Beams do not absorb an adjacent beam: two beam groups written back to
            // back are two deliberate groupings, not one interrupted by an accident.
            mergeOverlappingSpans(beam, Beam.class, this::removeBeaming, false);
        }

        applyChange(new BeamingAddition(this, beam), () -> appendChild(beam));
    }

    /**
     * Absorbs same-type spans overlapping {@code span}'s endpoints: widens the span to
     * cover them, then removes every {@code type} span fully subsumed by the widened
     * range via {@code remover}. The tracked removals are emitted before the widened
     * span's addition so undo restores the original spans after removing the merged
     * one.
     *
     * @param absorbAdjacent Whether a same-type span merely <em>touching</em> an endpoint
     *                       — ending one element before it, or starting one element after
     *                       — is absorbed too, as opposed to only one that overlaps it
     */
    private <R extends Span> void mergeOverlappingSpans(
        R span,
        Class<? extends R> type,
        Consumer<? super R> remover,
        boolean absorbAdjacent
    ) {
        var anchorIdx = getElementIndex(span.getAnchorElement());
        var endIdx = getElementIndex(span.getEndElement());

        // How far past an endpoint an existing span may sit and still be absorbed.
        var reach = absorbAdjacent ? SPAN_ADJACENCY_REACH : 0;

        // Expand bounds to absorb adjacent/overlapping spans. Both must be resolved before
        // the setters below, which mutate the very indices these predicates read. Positions
        // come from this line throughout — the same route the predicates above resolve by.
        //
        // bothResolved has already excluded every non-At bound, so indexOr's fallback is
        // unreachable here. It is the un-merged position anyway, so an unreachable case would
        // leave the merge a no-op rather than move an endpoint somewhere arbitrary.
        var mergedAnchorIdx = findSpans(
            type,
            bothResolved((anchor, end) -> anchor <= anchorIdx && anchorIdx <= end + reach))
            .stream()
            .mapToInt(candidate -> anchorIndexOf(candidate).indexOr(anchorIdx))
            .min()
            .orElse(anchorIdx);
        var mergedEndIdx = findSpans(
            type,
            bothResolved((anchor, end) -> anchor - reach <= endIdx && endIdx <= end))
            .stream()
            .mapToInt(candidate -> endIndexOf(candidate).indexOr(endIdx))
            .max()
            .orElse(endIdx);

        if (mergedAnchorIdx != anchorIdx) {
            span.setAnchorElement(elements.get(mergedAnchorIdx));
        }

        if (mergedEndIdx != endIdx) {
            span.setEndElement(elements.get(mergedEndIdx));
        }

        var subsumedSpans = findSpans(
            type,
            bothResolved((anchor, end) -> anchor >= mergedAnchorIdx && end <= mergedEndIdx)
        );
        subsumedSpans.forEach(remover);
    }

    /** A comparison over two endpoint positions both known to be in this line. */
    @FunctionalInterface
    private interface ResolvedIndexPredicate {
        boolean test(int anchorIndex, int endIndex);
    }

    /**
     * Restricts {@code matches} to spans whose endpoints both resolve to a position in this
     * line, so a span carrying an off-line or absent bound is never a merge candidate.
     * <p>
     * {@link #mergeOverlappingSpans} widens by arithmetic on these positions and then indexes
     * {@link #elements} with the result. Neither is meaningful for an endpoint that is not in
     * this line, and letting one through would drag the widened span out to an unrelated
     * neighbor's position — or past the end of the list.
     */
    private static Span.IndexPredicate bothResolved(ResolvedIndexPredicate matches) {
        return (anchorBound, endBound) ->
            anchorBound instanceof SpanBound.At(var anchorIndex) &&
            endBound instanceof SpanBound.At(var endIndex) &&
            matches.test(anchorIndex, endIndex);
    }

    /**
     * Removes a beam span that was previously added via {@link #addBeaming(Beam)}.
     */
    public void removeBeaming(Beam beam) {
        var index = spans.indexOf(beam);

        if (index < 0) {
            return;
        }

        applyChange(
            new BeamingRemoval(this, beam),
            () -> removeChild(beam, index)
        );
    }

    /**
     * Adds a tuplet span, replacing any existing tuplet that overlaps the new one.
     * <p>
     * Any existing tuplet whose range overlaps [anchor, end] is removed before the new tuplet
     * is added.
     */
    public void addTuplet(Tuplet tuplet) {
        // During replay the recorded batch already carries the overlapping-tuplet
        // removals — just add.
        if (!song.isReplaying()) {
            var anchorIndex = getElementIndex(tuplet.getAnchorElement());
            var endIndex = getElementIndex(tuplet.getEndElement());

            // Remove any existing tuplets that overlap the new range — tracked
            // removals emitted before the addition so undo restores the originals.
            removeOverlappingTuplets(anchorIndex, endIndex);
        }

        applyChange(new TupletAddition(this, tuplet), () -> appendChild(tuplet));
    }

    /**
     * Removes a tuplet span that was previously added via {@link #addTuplet(Tuplet)}.
     */
    public void removeTuplet(Tuplet tuplet) {
        var index = spans.indexOf(tuplet);

        if (index < 0) {
            return;
        }

        applyChange(
            new TupletRemoval(this, tuplet),
            () -> removeChild(tuplet, index)
        );
    }

    /**
     * Adds a crescendo hairpin span.
     * <p>
     * Any existing crescendo whose range overlaps or is adjacent to the new one is
     * absorbed into a single wider hairpin.
     */
    public void addCrescendo(Crescendo hairpin) {
        addHairpin(hairpin, CrescendoAddition::new, Crescendo.class);
    }

    /**
     * Removes a crescendo hairpin that was previously added via {@link #addCrescendo(Crescendo)}.
     */
    public void removeCrescendo(Crescendo hairpin) {
        removeHairpin(hairpin, CrescendoRemoval::new);
    }

    /**
     * Adds a diminuendo hairpin span.
     * <p>
     * Overlap-merge semantics mirror {@link #addCrescendo(Crescendo)}.
     */
    public void addDiminuendo(Diminuendo hairpin) {
        addHairpin(hairpin, DiminuendoAddition::new, Diminuendo.class);
    }

    /**
     * Removes a diminuendo hairpin that was previously added via {@link #addDiminuendo(Diminuendo)}.
     */
    public void removeDiminuendo(Diminuendo hairpin) {
        removeHairpin(hairpin, DiminuendoRemoval::new);
    }

    /**
     * Shared add logic for crescendo and diminuendo hairpins.
     * Merges overlapping/adjacent hairpins of the same type into one.
     */
    private <H extends Hairpin> void addHairpin(
        H hairpin,
        BiFunction<? super Line, ? super H, ? extends Mutation> mutationFactory,
        Class<? extends H> type
    ) {
        // During replay the recorded addition already carries the merged span
        // and the batch carries the absorbed-hairpin removals — just add.
        if (!song.isReplaying()) {
            // A hairpin drawn flush against a same-type one continues it rather than
            // starting a second: one uninterrupted dynamic gesture, one hairpin.
            mergeOverlappingSpans(hairpin, type, this::removeInvalidatedSpan, true);
        }

        applyChange(mutationFactory.apply(this, hairpin), () -> appendChild(hairpin));
    }

    /**
     * Shared remove logic for crescendo and diminuendo hairpins.
     */
    private <H extends Hairpin> void removeHairpin(
        H hairpin,
        BiFunction<? super Line, H, ? extends Mutation> mutationFactory
    ) {
        var index = spans.indexOf(hairpin);

        if (index < 0) {
            return;
        }

        applyChange(
            mutationFactory.apply(this, hairpin),
            () -> removeChild(hairpin, index)
        );
    }

    /**
     * A hairpin paired with the span it will occupy once a pending deletion is applied,
     * as inclusive indices into the elements that survive that deletion.
     */
    private record HairpinSpan(Hairpin hairpin, int begin, int end) {
    }

    /**
     * Reshapes this line's hairpins around the pending deletion of {@code deletedElements},
     * which must still be present in the line.
     * <p>
     * A hairpin whose endpoint is deleted pulls in to the nearest surviving element rather
     * than being dropped, and same-type hairpins the deletion leaves with nothing between
     * them become one — the same one-gesture-one-hairpin rule {@link #addHairpin} applies
     * when the user draws a hairpin flush against another. Only a hairpin left with fewer
     * than two elements is removed, having no gesture left to show.
     * <p>
     * A reshaped hairpin is expressed as a tracked removal plus a tracked addition of a
     * copy, since a span has no modification mutation of its own. Hairpins are
     * therefore excluded from the invalidation pass in {@link #removeElement} and
     * {@link #removeRange}: this method is the whole of their response to a deletion.
     */
    private void adjustHairpinsForDeletion(List<StaffElement> deletedElements) {
        var hairpins = findSpans(Hairpin.class);

        // Every deletion in the app reaches this method, but most songs hold no hairpin
        // at all. Leave before building the survivor bookkeeping, which is a full pass
        // over the line and would be discarded unused.
        if (hairpins.isEmpty()) {
            return;
        }

        var doomed = new HashSet<>(deletedElements);

        // The post-deletion element order, computed while the doomed elements are still
        // in place so each hairpin's surviving endpoints can be resolved against it.
        // survivorIndices records each survivor's new position as the list is built, so
        // resolving an endpoint later is a lookup rather than another scan.
        var survivors = new ArrayList<StaffElement>();
        var survivorIndices = new HashMap<StaffElement, Integer>();

        for (var element : elements) {
            if (!doomed.contains(element)) {
                survivorIndices.computeIfAbsent(element, k -> survivors.size());
                survivors.add(element);
            }
        }

        var spansByType = new LinkedHashMap<Class<?>, List<HairpinSpan>>();

        for (var hairpin : hairpins) {
            var anchorIndex = hairpin.getAnchorElementIndex();
            var endIndex = hairpin.getEndElementIndex();

            // A hairpin not anchored in this line is not this deletion's to reshape.
            if (anchorIndex < 0 || endIndex < 0) {
                continue;
            }

            var span = survivingSpanOf(hairpin, anchorIndex, endIndex, survivors, survivorIndices);

            if (span == null) {
                removeInvalidatedSpan(hairpin);
                continue;
            }

            spansByType.computeIfAbsent(hairpin.getClass(), type -> new ArrayList<>()).add(span);
        }

        for (var spans : spansByType.values()) {
            mergeAdjacentSpans(spans, survivors);
        }
    }

    /**
     * Returns the span {@code hairpin} occupies among {@code survivors} once the pending
     * deletion is applied, or null when what survives cannot carry a hairpin — a wedge
     * needs a note to start on and another to end on.
     *
     * @param anchorIndex     the hairpin's anchor index in the pre-deletion line
     * @param endIndex        the hairpin's end index in the pre-deletion line
     * @param survivorIndices each surviving element's post-deletion position
     */
    private @Nullable HairpinSpan survivingSpanOf(
        Hairpin hairpin,
        int anchorIndex,
        int endIndex,
        List<? extends StaffElement> survivors,
        Map<StaffElement, Integer> survivorIndices
    ) {
        // Nothing outside the range can fall between two elements inside it, so what
        // survives of the range is still described by its first and last position.
        var first = -1;
        var last = -1;

        for (var i = anchorIndex; i <= endIndex; i++) {
            // Absent means the element is one of the doomed ones.
            var survivorIndex = survivorIndices.get(elements.get(i));

            if (survivorIndex == null) {
                continue;
            }

            if (first < 0) {
                first = survivorIndex;
            }

            last = survivorIndex;
        }

        if (first < 0) {
            return null;
        }

        var begin = resolveBeginIndex(hairpin, first, last, survivors);
        var end = resolveEndIndex(hairpin, first, last, survivors);

        // One note, or none, leaves no gesture to draw.
        if (begin < 0 || end < 0 || begin >= end) {
            return null;
        }

        return new HairpinSpan(hairpin, begin, end);
    }

    /**
     * Returns the post-deletion position of {@code hairpin}'s anchor, given that its
     * elements survive from {@code first} through {@code last}, or -1 when none of them
     * can begin a hairpin.
     * <p>
     * A surviving anchor stays where it is — a hairpin an older build left anchored on a
     * rest is not this deletion's to correct. A deleted one moves in to the first element
     * that can begin one: a pitched note, or a grace note whose host is one, matching what
     * the app allows when the hairpin is drawn.
     */
    private static int resolveBeginIndex(
        Hairpin hairpin,
        int first,
        int last,
        List<? extends StaffElement> survivors
    ) {
        if (survivors.get(first) == hairpin.getAnchorElement()) {
            return first;
        }

        for (var i = first; i <= last; i++) {
            if (canAnchorHairpin(survivors, i, last)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns whether the element at {@code index} can begin a hairpin: a pitched note,
     * or a grace note whose host is one.
     *
     * @param lastIndex the last index a hairpin may reach, bounding the host lookahead
     */
    public boolean canAnchorHairpin(int index, int lastIndex) {
        return canAnchorHairpin(elements, index, lastIndex);
    }

    /**
     * Returns whether the element at {@code index} in {@code candidates} can begin a
     * hairpin.
     * <p>
     * A pitched note always can. A grace note belongs to the note it precedes, so it
     * can begin one when that note is pitched — anchoring on the grace note rather than
     * its host keeps the hairpin over what the user actually selected. Anything else,
     * including a grace note with no host within reach, cannot.
     * <p>
     * Both the menu's eligibility test and the post-deletion reshaping read this, so a
     * deletion can never leave a hairpin anchored somewhere the user could not have
     * placed one.
     *
     * @param candidates the elements to test against, in document order
     * @param lastIndex  the last usable index, bounding the host lookahead
     */
    private static boolean canAnchorHairpin(List<? extends StaffElement> candidates, int index, int lastIndex) {
        var type = candidates.get(index).getType();

        return type.isPitchedNote()
            || (type.isGraceNote()
                && index < lastIndex
                && candidates.get(index + 1).getType().isPitchedNote());
    }

    /**
     * Returns the post-deletion position of {@code hairpin}'s end, given that its elements
     * survive from {@code first} through {@code last}, or -1 when none of them can end a
     * hairpin. A surviving end stays where it is; a deleted one moves in to the last
     * surviving pitched note, which is the only thing a hairpin may end on.
     */
    private static int resolveEndIndex(
        Hairpin hairpin,
        int first,
        int last,
        List<? extends StaffElement> survivors
    ) {
        if (survivors.get(last) == hairpin.getEndElement()) {
            return last;
        }

        for (var i = last; i >= first; i--) {
            if (survivors.get(i).getType().isPitchedNote()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Replaces each run of same-type hairpin {@code spans} that the deletion leaves touching
     * with a single hairpin covering the run. Runs of one whose endpoints both survive are
     * left untouched, so a deletion that misses a hairpin emits no hairpin mutations.
     *
     * @param spans all post-deletion spans of one hairpin type, in any order
     */
    private void mergeAdjacentSpans(List<HairpinSpan> spans, List<? extends StaffElement> survivors) {
        spans.sort(Comparator.comparingInt(HairpinSpan::begin));

        var run = new ArrayList<HairpinSpan>();
        var runEnd = -1;

        for (var span : spans) {
            // One surviving element between two hairpins is still a break in the gesture.
            if (!run.isEmpty() && span.begin() > runEnd + SPAN_ADJACENCY_REACH) {
                applySpanRun(run, runEnd, survivors);
                run.clear();
                runEnd = -1;
            }

            run.add(span);
            runEnd = Math.max(runEnd, span.end());
        }

        applySpanRun(run, runEnd, survivors);
    }

    /**
     * Reshapes the hairpins in {@code run} into one hairpin spanning the run's surviving
     * endpoints, carrying over the first one's user offsets.
     *
     * @param run    the hairpins to replace, ordered by their post-deletion start
     * @param runEnd the run's last post-deletion index
     */
    private void applySpanRun(List<HairpinSpan> run, int runEnd, List<? extends StaffElement> survivors) {
        if (run.isEmpty()) {
            return;
        }

        var first = run.getFirst().hairpin();
        var anchorElement = survivors.get(run.getFirst().begin());
        var endElement = survivors.get(runEnd);

        if (run.size() == 1
                && first.getAnchorElement() == anchorElement
                && first.getEndElement() == endElement) {
            return;
        }

        // Removals precede the addition so reverse-order undo drops the reshaped hairpin
        // before restoring the originals, and run in reverse list order so undo restores
        // them in the order they were in, leaving the document identical to before.
        var doomed = new ArrayList<>(run.stream().map(HairpinSpan::hairpin).toList());
        doomed.sort(Comparator.<Hairpin>comparingInt(spans::indexOf).reversed());
        doomed.forEach(this::removeInvalidatedSpan);

        var reshaped = (Hairpin) first.copy(anchorElement, endElement);

        switch (reshaped) {
            case Crescendo crescendo -> addCrescendo(crescendo);
            case Diminuendo diminuendo -> addDiminuendo(diminuendo);
        }
    }

    /**
     * Returns true if any element in the inclusive range {@code [begin, end]} is a
     * repeat or a barline other than {@link ElementType#SINGLE_BARLINE}.
     * <p>
     * No bounds check is performed; callers own their indices.
     */
    public boolean spansStructuralBoundary(int begin, int end) {
        for (var i = begin; i <= end; i++) {
            var type = getElement(i).getType();

            if (type.isRepeat() || (type.isBarLine() && type != ElementType.SINGLE_BARLINE)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Removes every tuplet whose span overlaps [begin, end] inclusive, emitting a
     * {@code TupletRemoval} mutation for each. Must be called inside an open
     * modification bracket.
     * <p>
     * Public for use by UI-layer coordinators ({@code SelectionCoordinator},
     * {@code PreviewElementManager}) that perform bulk or replace operations not
     * routed through {@code modifyElement}.
     */
    public void removeOverlappingTuplets(int begin, int end) {
        for (var tuplet : findTupletsOverlapping(begin, end)) {
            removeTuplet(tuplet);
        }
    }


    public int getFirstTempoChange() {
        // On the first line the base tempo is anchored on the first element
        // (see isInitialTempoAnchor), so the tempo marking belongs to that element.
        if (isInitialTempoAnchor(0) && elementCount() > 0) {
            return 0;
        }

        return IntStream.range(0, elementCount())
            .filter(n -> getElement(n).findAttachment(TempoChangeAttachment.class) != null)
            .findFirst()
            .orElse(-1);
    }

    public boolean isAnnotation() {
        return IntStream.range(0, elementCount()).anyMatch(
            n -> getElement(n).findAttachment(AnnotationAttachment.class) != null
        );
    }

    public int getFirstTrill() {
        return findSpans(Trill.class).stream()
            .mapToInt(Trill::getAnchorElementIndex)
            .filter(i -> i >= 0)
            .min()
            .orElse(-1);
    }

    public int getFirstBeatChange() {
        return IntStream.range(0, elementCount())
            .filter(n -> getElement(n).findAttachment(BeatChangeAttachment.class) != null)
            .findFirst()
            .orElse(-1);
    }

    // =========================================================================
    // Range Element Management (Phase 4+)
    // =========================================================================

    /**
     * Adds a span to this line.
     *
     * @param element The span to add
     */
    public void addSpan(Span element) {
        applyChange(
            new SpanAddition(this, element),
            () -> appendChild(element)
        );
    }

    /**
     * Adds a trill span, first removing any existing trill that overlaps the new one.
     * Each removal is recorded as its own mutation so replacing a displaced trill is undoable.
     */
    public void addTrill(Trill trill) {
        for (var overlapping : findTrillsOverlapping(trill.getAnchorElementIndex(), trill.getEndElementIndex())) {
            removeSpan(overlapping);
        }

        addSpan(trill);
    }

    /**
     * Removes every trill span overlapping {@code [beginIndex, endIndex]}.
     */
    public void removeTrillsOverlapping(int beginIndex, int endIndex) {
        for (var trill : findTrillsOverlapping(beginIndex, endIndex)) {
            removeSpan(trill);
        }
    }

    /**
     * Removes a span from this line.
     *
     * @param element The span to remove
     * @return true if the element was removed
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean removeSpan(Span element) {
        var index = spans.indexOf(element);

        if (index < 0) {
            return false;
        }

        applyChange(
            new SpanRemoval(this, element),
            () -> removeChild(element, index)
        );

        return true;
    }

    /**
     * Removes a span displaced by another change (invalidated by an
     * element edit, or subsumed by a span merge) via its typed tracked removal,
     * so the removal emits its proper mutation. A raw
     * {@code spans.removeIf} would drop the span with no record, making
     * undo of the enclosing operation lossy.
     * <p>
     * Every branch is a no-op when the span is no longer in any line it was in — each
     * one looks the span up in this line's {@code spans} and returns when it is absent,
     * and a removal takes a cross-line span out of both lines' lists together, so a
     * second call on either line finds nothing left to remove. Callers that cannot
     * cheaply tell whether an earlier step already removed it (the paste reconciliation
     * in {@code ScoreViewController.tryInsertFragment}) may therefore call it
     * unconditionally.
     */
    public void removeInvalidatedSpan(Span span) {
        switch (span) {
            case Beam beam -> removeBeaming(beam);
            case Tie tie -> removeTie(tie);
            case Tuplet tuplet -> removeTuplet(tuplet);
            case Crescendo crescendo -> removeCrescendo(crescendo);
            case Diminuendo diminuendo -> removeDiminuendo(diminuendo);
            default -> removeSpan(span);
        }
    }

    /**
     * Adds a span that a paste is inserting, routing hairpins through the merge-aware
     * {@link #addCrescendo}/{@link #addDiminuendo} so a pasted hairpin landing flush
     * against a same-type one already on this line becomes a single hairpin, exactly
     * as if the user had drawn it there.
     * <p>
     * Every other kind is added verbatim: {@code PasteSpanReconciliation} has already
     * guaranteed no same-kind overlap survives the paste, so the other adders'
     * displacement logic has nothing left to resolve.
     */
    public void addPastedSpan(Span span) {
        switch (span) {
            case Crescendo crescendo -> addCrescendo(crescendo);
            case Diminuendo diminuendo -> addDiminuendo(diminuendo);
            default -> addSpan(span);
        }
    }

    /**
     * Returns an unmodifiable view of the spans in this line.
     */
    @Override
    public List<Span> getSpans() {
        return spansView;
    }

    @Override
    public SpanBound anchorIndexOf(Span span) {
        return boundOf(span.getAnchorElement());
    }

    @Override
    public SpanBound endIndexOf(Span span) {
        return boundOf(span.getEndElement());
    }

    /**
     * Resolves one endpoint of a span against this line, for both accessors above.
     * <p>
     * Direction for an endpoint in another line comes from where the two lines actually sit in
     * the song, not from which endpoint is being asked about: an anchor is not necessarily
     * behind us just because it is the earlier of the pair.
     * <p>
     * Only the two neighbours get a direction. A span reaching further than that describes a
     * jump nothing can draw — the two halves would run off opposite edges of lines with a
     * whole line of unrelated music between them, each pointing at nothing — so it resolves
     * to {@link SpanBound#ABSENT}, which stops this half drawing at all.
     * {@link Song#removeSpansBetweenNonAdjacentLines} drops such a span when an insertion or
     * deletion is what broke adjacency, but nothing checks adjacency when a span is created:
     * the MusicXML reader carries one pending tie start across a whole part, so a file whose
     * {@code <tied>} start and stop are separated by a complete line produces one. This is the
     * single place every query, layout, export and render path resolves an endpoint through,
     * so it is the one place that has to say no.
     * <p>
     * {@link Song#removeLine} leaves a deleted line's elements pointing at it, so an endpoint
     * can also name a line the song no longer holds; that has no position at all and is
     * likewise {@link SpanBound#ABSENT}.
     * <p>
     * Only a cross-line endpoint pays for the two {@link Song#indexOfLine} scans, and only
     * ties are ever cross-line.
     */
    private SpanBound boundOf(@Nullable StaffElement endpoint) {
        if (endpoint == null) {
            return SpanBound.ABSENT;
        }

        var endpointLine = endpoint.getParentLine();

        if (endpointLine == null) {
            return SpanBound.ABSENT;
        }

        if (endpointLine == this) {
            // The stored-parentage invariant guarantees this line's elements contain it.
            return new SpanBound.At(getElementIndex(endpoint));
        }

        var thisLineIndex = song.indexOfLine(this);
        var endpointLineIndex = song.indexOfLine(endpointLine);

        // Guarded before the distance is taken: a line the song no longer holds reports -1,
        // which would otherwise sit one away from line 0 and read as an adjacent neighbour.
        if (thisLineIndex < 0 || endpointLineIndex < 0) {
            return SpanBound.ABSENT;
        }

        return switch (endpointLineIndex - thisLineIndex) {
            case -1 -> SpanBound.BEFORE_LINE;
            case 1 -> SpanBound.AFTER_LINE;
            default -> SpanBound.ABSENT;
        };
    }

    /**
     * Returns true if deleting {@code deletedElements} would remove any Ending in this line.
     * Checks both {@link Span#isInvalidatedBy} (anchor/end deleted) and
     * {@link Span#isInvalidatedByDeletion} (subclass-specific conditions).
     * <p>
     * {@code deletedElements} must reflect the pre-deletion line state.
     */
    public boolean hasEndingInvalidatedByDeletion(List<StaffElement> deletedElements) {
        return spans.stream()
            .filter(Span::requiresInvalidationConfirm)
            .anyMatch(r -> r.isInvalidatedBy(deletedElements) ||
                           r.isInvalidatedByDeletion(deletedElements, this));
    }

    /**
     * Returns the effect of replacing the element at {@code index} with {@code newElement}
     * on any ending in this line. Returns {@link Ending.EndingEffect.None} if no ending
     * is affected.
     * <p>
     * Call before {@link #setElement} to determine whether a confirmation dialog is needed.
     */
    public Ending.EndingEffect findEndingReplacementEffect(int index, StaffElement newElement) {
        var oldElement = getElement(index);

        return findEndings().stream()
            .map(ending -> ending.checkReplacement(oldElement, newElement, this))
            .filter(effect -> !(effect instanceof Ending.EndingEffect.None))
            .findFirst()
            .orElse(Ending.EndingEffect.None.INSTANCE);
    }

    /**
     * Returns true if inserting an element of {@code insertedType} at {@code insertedIndex}
     * would remove any Ending in this line.
     * <p>
     * Only spans whose loss warrants a prompt are consulted, matching
     * {@link #hasEndingInvalidatedByDeletion}: a tie an insertion displaces goes silently,
     * so it must not raise the ending confirm.
     * <p>
     * Call before {@link #addElement(int, StaffElement)}.
     */
    public boolean hasEndingInvalidatedByInsertion(int insertedIndex, ElementType insertedType) {
        return spans.stream()
            .filter(Span::requiresInvalidationConfirm)
            .anyMatch(r -> r.isInvalidatedByInsertion(insertedIndex, insertedType, this));
    }

    /**
     * Returns true if inserting a contiguous run of elements of {@code insertedTypes} at
     * {@code insertedIndex} would remove any Ending in this line — the paste equivalent of
     * {@link #hasEndingInvalidatedByInsertion(int, ElementType)}.
     * <p>
     * Every type is tested at {@code insertedIndex} rather than at its own eventual
     * position: the run lands contiguously, so each element sits inside the ending
     * (or at its split boundary) exactly when the first one does, and only the
     * pre-insertion index resolves correctly against the pre-insertion line.
     * <p>
     * Call before the first {@link #addElement(int, StaffElement)}.
     */
    public boolean hasEndingInvalidatedByInsertion(int insertedIndex, List<ElementType> insertedTypes) {
        return insertedTypes.stream()
            .anyMatch(type -> hasEndingInvalidatedByInsertion(insertedIndex, type));
    }

    // =========================================================================
    // Beam Group Management (Phase 4+)
    // =========================================================================

}
