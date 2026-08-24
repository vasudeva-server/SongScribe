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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

/**
 * One system of the score: an ordered list of staff elements, the spans drawn over them, and the
 * key that is in effect across them.
 *
 * <p><b>Key invariant.</b> A line's own {@link #getKey() key} is non-null only where a key change
 * actually takes effect at the start of the line; null means the line inherits the key in effect
 * at the end of the previous line. Line 0 of a song is always non-null — there is nothing before
 * it to inherit from — and {@link Song} is what maintains that, both when a mutation promotes a
 * different line to index 0 and when a document is loaded. A line that belongs to no song is
 * subject to the same rule for the same reason: nothing precedes it, so it must carry its own key
 * before anything asks what key it is in.
 *
 * <p>What a line <em>inherits</em> is not held here. {@link Song} owns it, keyed by line, because
 * it is a fact about a line's position in the song rather than about the line — see
 * {@link Song#runningKeyAt}. A line therefore cannot carry a stale inherited key out of the song
 * it was removed from.
 *
 * <p><b>Pairing rule.</b> Some elements cannot outlive their partner: a paired grace note goes
 * with its host, a breath mark with the element it hangs off, and a key signature with the
 * barline it sits behind. A range that names one half of such a pair therefore widens to cover
 * the other, in whichever direction the partner lies — {@link #effectiveBegin},
 * {@link #effectiveEnd} and {@link #effectiveRange} are that widening, and each states only the
 * pairs it resolves rather than restating this rule. The widening is a query, never a mutation:
 * it is what a deletion or a copy really carries away, and therefore also what the selection
 * highlight has to show.
 */
public class Line implements LyricRun, SpanLookup {

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

    /**
     * The key this line establishes at its own start, or null when it inherits one.
     * See the key invariant in this class's Javadoc.
     */
    private @Nullable Key key = null;

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
     * <p>Every write to {@link #elements} — both {@code addElement} overloads, {@code setElement},
     * {@code removeElement} and {@code removeRange} — goes through {@code attach} or
     * {@code detach}, which null this field out. {@link #getElementIndex} rebuilds the map when it
     * finds it null, and otherwise serves a cached lookup.
     *
     * <p>Writes to {@link #spans} are absent from that picture because they cannot reach it:
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

    /**
     * Applies a single mutation, delegating to the parent song's bracket.
     *
     * <p>A modification bracket must be open — the mutation is recorded and the
     * mutator runs inside it. If the song has suspended tracking via
     * {@link Song#withoutMutationTracking(Runnable)}, the mutator runs without
     * being recorded.
     *
     * <p>Either way the song's key invariant is restored afterwards. The suspended
     * path is not a shortcut past it: a key edit made under suspended tracking moves
     * the same keys as one made inside a bracket, so it owes the same maintenance.
     *
     * @throws IllegalStateException if the song has neither an open modification
     *     bracket nor suspended tracking
     */
    public void applyChange(Mutation mutation, Runnable mutator) {
        if (song.isMutationTrackingSuspended()) {
            mutator.run();

            // Suspending tracking stops the mutation being recorded; it does not stop the edit
            // moving a key. Without this the invariant was skipped on the whole suspended path,
            // leaving every later line's inherited key stale with no error and nothing visible
            // to notice. No line-0 repair can be owed here — that needs a LineInsertion or
            // LineDeletion, and those reach Song.applyChange directly rather than through a line.
            song.maintainKeyInvariant(mutation, null);
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

    /**
     * Returns the key this line establishes at its own start.
     *
     * @return the key that takes effect at the start of this line, or null when this line
     *         establishes no key of its own and inherits the key in effect at the end of the
     *         previous line
     */
    public @Nullable Key getKey() {
        return key;
    }

    /**
     * Establishes {@code key} at the start of this line, or clears the line's own key so that it
     * inherits again.
     *
     * <p><b>A no-op change normalizes to null.</b> When {@code key} equals the key this line would
     * inherit, the line's own key is set to null instead, because a line holds a key only where
     * one actually changes. Keying a line to the value it already inherits looks identical — every
     * header draws its key either way — but it stops inheritance there, so a later change upstream
     * would silently fail to reach this line or anything past it.
     *
     * <p>Records a {@link LineKeyChange} carrying the normalized value, and posts nothing when the
     * normalized value is the one the line already holds. The key in effect on the lines that
     * follow is {@link Song}'s to bring up to date, driven by that record.
     *
     * @param key the key to establish here, or null to inherit
     * @throws IllegalStateException if the song has neither an open modification bracket nor
     *                               suspended tracking
     */
    public void setKey(@Nullable Key key) {
        var normalized = key != null && key.equals(song.inheritedKeyOf(this)) ? null : key;

        if (Objects.equals(this.key, normalized)) {
            return;
        }

        var old = this.key;
        applyChange(
            new LineKeyChange(this, old, normalized),
            () -> this.key = normalized
        );
    }

    /**
     * Returns the key actually in effect at the <em>start</em> of this line: this line's own key
     * when it has one, and otherwise the key in effect at the end of the previous line, which
     * accounts for any mid-line key signature that line carried.
     *
     * <p>Equals {@link #getKey()} whenever that is non-null. Where it is null the answer comes from
     * {@link Song#runningKeyAt}, which is total — a line that establishes no key and has nothing to
     * inherit from is in {@link Key#DEFAULT}, the key a document that names none is in.
     *
     * @return the key in effect at the start of this line; never null
     */
    public Key getRunningKey() {
        var ownKey = key;
        return ownKey != null ? ownKey : song.runningKeyAt(this);
    }

    /**
     * The lowest element index a {@link KeyChangeElement} can occupy. Index 0 is forbidden by
     * that class's position invariant — a key signature always follows a barline or repeat — so
     * every backward scan for one stops here rather than at 0.
     */
    public static final int FIRST_LEGAL_KEY_CHANGE_INDEX = 1;

    /**
     * Returns the key in effect at {@code elementIndex} within this line: {@link #getRunningKey()},
     * overridden by the last {@link KeyChangeElement} at or before that index.
     *
     * <p>The bound is <em>inclusive</em>: a key signature at {@code elementIndex} is in effect at
     * {@code elementIndex}. {@code keyAt(0)} therefore always equals {@link #getRunningKey()},
     * because index 0 can never hold a key signature — see {@link KeyChangeElement}'s position
     * invariant.
     *
     * <p>The domain is an <em>insertion</em> index, so {@link #elementCount()} is valid and means
     * the position just past the last element: a caller resolving what an element about to be
     * appended would be in has an index to ask about. An empty line answers
     * {@link #getRunningKey()} at its one valid index.
     *
     * @param elementIndex the index into this line's elements, {@code 0..}{@link #elementCount()}
     * @return the key in effect at that index; never null
     * @throws IndexOutOfBoundsException if {@code elementIndex} is negative or greater than
     *                                   {@link #elementCount()}
     */
    public Key keyAt(int elementIndex) {
        if (elementIndex < 0 || elementIndex > elements.size()) {
            throw new IndexOutOfBoundsException(
                "element index " + elementIndex + " out of bounds for line of " + elements.size()
            );
        }

        for (var scanIndex = Math.min(elementIndex, elements.size() - 1); scanIndex > 0; scanIndex--) {
            if (elements.get(scanIndex) instanceof KeyChangeElement keySignature) {
                return keySignature.getKey();
            }
        }

        return getRunningKey();
    }

    /**
     * Returns the key in effect after this line's last element — the key the next line inherits.
     *
     * <p>Prefer this to {@code keyAt(elementCount() - 1)}, which is the same answer on a line with
     * elements and an {@link IndexOutOfBoundsException} on one without.
     *
     * @return the key in effect at the end of this line, which is {@link #getRunningKey()} when
     *         the line is empty; never null
     */
    public Key keyAtEndOfLine() {
        return keyAtEndOfLineUnder(getRunningKey());
    }

    /**
     * Returns the key this line would leave off in if it were running in {@code runningKey}: its
     * last mid-line {@link KeyChangeElement}'s key when it holds one, and {@code runningKey}
     * when it does not.
     *
     * <p>This is {@link #keyAtEndOfLine()} under a <em>hypothetical</em> running key, which is what
     * every caller projecting an edit needs — a fit pre-check walking the inheritance chain, an
     * accidental reconciliation deriving that chain's reach, and an insertion asking what its own
     * key changes downstream. A line holding a mid-line change ends in that change's key however
     * it began, and that is the asymmetry each of them would otherwise re-derive.
     *
     * @param runningKey the key to suppose this line begins in
     * @return the key in effect after this line's last element under that supposition; never null
     */
    public Key keyAtEndOfLineUnder(Key runningKey) {
        return keyAtEndOfLineUnder(FIRST_LEGAL_KEY_CHANGE_INDEX, runningKey);
    }

    /**
     * As {@link #keyAtEndOfLineUnder(Key)}, counting only key signatures at or after
     * {@code fromIndex}.
     *
     * <p>The bounded form is what an edit inserting a key signature at {@code fromIndex} needs:
     * the key it establishes reaches the end of the line only when no existing key signature
     * already stands after it, and {@code runningKey} is then the inserted key rather than the
     * line's own.
     *
     * @param fromIndex the lowest element index to count a key signature at
     * @param runningKey the key to suppose is in effect from {@code fromIndex} onward
     * @return the key in effect after this line's last element under that supposition; never null
     */
    public Key keyAtEndOfLineUnder(int fromIndex, Key runningKey) {
        var lastKeyChangeKey = lastKeyChangeKeyFrom(fromIndex);

        return lastKeyChangeKey != null ? lastKeyChangeKey : runningKey;
    }

    /**
     * Returns the key this line will leave off in once {@code [begin, end]} is removed from it:
     * the last key signature surviving after the range, and otherwise the key in effect
     * immediately before it.
     *
     * <p>This is what a deletion needs and {@link #keyAtEndOfLineUnder(int, Key)} cannot give.
     * Removing a mid-line key signature moves the key every following line inherits, so the
     * deletion owes the same cross-line accidental reconciliation an inserted key signature owes
     * — {@code AccidentalReconciliation.linesInheriting} takes this key as its starting point.
     * See {@code docs/key-signatures.md}.
     *
     * <p>A deletion that removes no key signature answers with the key the line already leaves
     * off in, so its caller derives an empty reach and reconciles nothing downstream. That is why
     * the reach is computed unconditionally rather than behind a "does the range hold a key
     * signature" test.
     *
     * @param begin the first element index the deletion removes, already widened to
     *     {@link #effectiveRange}
     * @param end the last element index the deletion removes, inclusive
     * @return the key in effect after this line's last element once the range is gone; never null
     */
    public Key keyAtEndOfLineAfterRemoving(int begin, int end) {
        var survivingKey = lastKeyChangeKeyFrom(end + 1);

        if (survivingKey != null) {
            return survivingKey;
        }

        return begin > 0 ? keyAt(begin - 1) : getRunningKey();
    }

    /**
     * Returns the key the last {@link KeyChangeElement} on this line establishes.
     *
     * <p>Null says the line changes key nowhere along its length, so the key it leaves off in is
     * whatever it started in. That distinction is what a caller projecting an edit needs and
     * {@link #keyAtEndOfLine()} cannot give: under a <em>hypothetical</em> running key, a line with
     * a mid-line change still ends in that change's key, and a line without one ends in the
     * hypothetical.
     *
     * @return the last mid-line key change's key, or null when this line holds no key signature
     */
    public @Nullable Key lastKeyChangeKey() {
        return lastKeyChangeKeyFrom(FIRST_LEGAL_KEY_CHANGE_INDEX);
    }

    /**
     * Returns the key the last {@link KeyChangeElement} at or after {@code fromIndex}
     * establishes, or null when no key signature stands there.
     *
     * <p>This is what an edge inserting a key signature at {@code fromIndex} needs and
     * {@link #lastKeyChangeKey()} cannot give: the key the line will leave off in afterwards is
     * the inserted one <em>only if</em> no existing key signature already stands after the
     * insertion point, and this is the question that decides it.
     *
     * <p>Indices below {@link #FIRST_LEGAL_KEY_CHANGE_INDEX} are treated as that index rather
     * than rejected, because no key signature can stand before it —
     * {@link KeyChangeElement}'s position invariant forbids index 0 — so a lower bound asks
     * about a stretch of the line that cannot hold an answer.
     *
     * @param fromIndex the lowest element index to consider
     * @return the last key signature's key at or after {@code fromIndex}, or null when there is
     *         none
     */
    public @Nullable Key lastKeyChangeKeyFrom(int fromIndex) {
        var lowestIndex = Math.max(fromIndex, FIRST_LEGAL_KEY_CHANGE_INDEX);

        for (var scanIndex = elements.size() - 1; scanIndex >= lowestIndex; scanIndex--) {
            if (elements.get(scanIndex) instanceof KeyChangeElement keySignature) {
                return keySignature.getKey();
            }
        }

        return null;
    }

    /**
     * Returns the key the following line begins in — the key a cautionary key signature at the end
     * of this line warns the performer about. Paired with {@link #keyAtEndOfLine()} it is the whole
     * input to {@link Key#accidentalsFrom}: what the cautionary draws, and how much room layout reserves for
     * it, both come from that pair.
     *
     * <p>Null is the "there is nothing to warn about" answer, and a caller that has one has nothing
     * further to decide: no cautionary is drawn and none is reserved for.
     *
     * @return the following line's {@link #getRunningKey()}, or null when this line is the song's
     *         last line or is not one of the song's lines at all — a detached line, or one being
     *         measured before it is added
     */
    public @Nullable Key nextLineRunningKey() {
        var lineIndex = song.indexOfLine(this);

        if (lineIndex < 0 || lineIndex + 1 >= song.lineCount()) {
            return null;
        }

        return song.getLine(lineIndex + 1).getRunningKey();
    }

    // ========================================================================
    // Line element parentage
    //
    // For the staff elements held in `elements`, attach/detach are the only writers of
    // parentLine. Every mutation of `elements` funnels through one of them from inside
    // the applyChange mutator lambda, so parentage moves with the recorded change:
    //
    // Both addElement overloads attach; removeElement and removeRange detach; setElement does
    // both, detaching the outgoing element and attaching the incoming one.
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

            // Companion span changes precede the primary insertion so reverse-order undo
            // restores the primary element before re-adding dependent spans.
            applySpanOutcomes(ElementChange.forInsertion(this, index, element));
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
     * An element and the X position it lands at, bound together so an insertion cannot pair them
     * up wrongly. Two parallel lists would have to agree in length and in order, and a caller
     * that got either wrong would place every element after the mismatch at the wrong position —
     * a visibly wrong line with nothing near the insertion to explain it.
     *
     * @param element     the element to insert
     * @param xPositionSs where it lands, in staff spaces
     */
    public record PlacedElement(StaffElement element, double xPositionSs) {}

    /**
     * Inserts {@code run} at {@code index} as one run: positions each element, adds them in
     * order, repairs the seams the run breaks on either side, and pushes the tail over to make
     * room.
     *
     * <p><b>This is the whole of a multi-element insertion, and it exists so that no caller
     * assembles one out of parts.</b> The four steps are not independent — {@link
     * LyricRun#repairNeighborsBeforeInsertion} reads pre-insertion indices, so it must run before
     * the first {@link #addElement}, and {@link LyricRun#repairNeighborsAfterInsertion} keys its
     * two halves off opposite ends of the run, so it must run after the last. A caller that
     * open-codes the sequence can leave out a step, and leaving one out shows up as a glissando
     * pointing at a barline or a melisma pointing at an element that is no longer the host,
     * neither of which fails anywhere near the insertion.
     *
     * <p>Must be called inside a modification bracket, so the whole run and its repairs are one
     * undo step.
     *
     * @param index the index the first element lands at
     * @param run the elements to insert with the positions they land at, in the order they land;
     *            must not be empty
     * @param tailShiftPx how far to move every element after the run, in pixels; may be negative
     *                    where the insertion replaces something wider than itself
     * @effects mutates this line and records the insertion, the repairs and the shift into the
     *          open modification bracket
     */
    public void insertRun(int index, List<PlacedElement> run, int tailShiftPx) {
        var elements = run.stream().map(PlacedElement::element).toList();
        repairNeighborsBeforeInsertion(index, elements);

        var insertedCount = run.size();

        for (var i = 0; i < insertedCount; i++) {
            var placed = run.get(i);
            placed.element().setXOffsetPx(ScaleContext.ssToRoundedPx(placed.xPositionSs()));
            addElement(index + i, placed.element());
        }

        repairNeighborsAfterInsertion(index, elements);

        for (var i = index + insertedCount; i < effectiveElementCount(); i++) {
            var element = getElement(i);
            element.setXOffsetPx(element.getXOffsetPx() + tailShiftPx);
        }
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
            // Decided before the mutator so findRepeatSplitElement sees the pre-replacement line.
            // Companion span changes precede the primary replacement so reverse-order undo
            // restores the primary element before re-adding dependent spans.
            applySpanOutcomes(ElementChange.forReplacement(this, index, element));
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

            // Companion span changes precede the primary deletion so reverse-order undo
            // re-inserts the element before re-adding the spans anchored to it.
            applySpanOutcomes(ElementChange.forDeletion(this, index, index));
        }

        applyChange(
            new ElementDeletion(this, index, deleted),
            () -> {
                detach(deleted);
                elements.remove(index);
            }
        );
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

            // Companion span changes precede the primary deletion so reverse-order undo
            // re-inserts the elements before re-adding the spans anchored to them.
            applySpanOutcomes(ElementChange.forDeletion(this, from, to));
        }

        applyChange(
            new ElementRangeDeletion(this, from, to, deletedElements),
            () -> {
                deletedElements.forEach(this::detach);
                elements.subList(from, to + 1).clear();
            }
        );
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
     * The index of the last note on this line.
     *
     * <p>Answers how far along a line an edit can still govern a pitch. A key change past this
     * index reaches no note, so it says nothing the next line's own key does not say, and the
     * cautionary already draws that key at this line's end. See {@code docs/key-signatures.md}.
     *
     * <p>The scan needs no bound against {@link #effectiveElementCount()}: an auto-maintained
     * terminal is a barline or a repeat, so a scan for a note stops before it either way.
     *
     * @return the last note's element index, or -1 when this line holds no note
     * @invariant the result is always below {@link #effectiveElementCount()}
     */
    public int lastNoteIndex() {
        for (var scanIndex = elements.size() - 1; scanIndex >= 0; scanIndex--) {
            if (elements.get(scanIndex).getType().isNote()) {
                return scanIndex;
            }
        }

        return -1;
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
     * Whether the element at {@code index} is a key signature sitting behind the barline or
     * repeat immediately before it — the one bidirectional pair on a line, since a key change
     * belongs at the head of a measure (see {@link KeyChangeElement}'s position invariant).
     * <p>
     * The barline is tested rather than assumed. The invariant makes the test hold for every
     * key signature a document of this program's own making can contain, and re-testing it
     * here is what keeps the widening from reaching past a key signature that arrived any
     * other way.
     *
     * @param index element index; out of range, or 0, yields false — index 0 has nothing
     *     before it to pair with
     * @return {@code true} when {@code index} and {@code index - 1} are such a pair
     */
    private boolean isKeyChangeBehindBarline(int index) {
        if (index < 1 || index >= effectiveElementCount()) {
            return false;
        }

        if (!getElement(index).getType().isKeyChange()) {
            return false;
        }

        var precedingType = getElement(index - 1).getType();

        return precedingType.isBarLine() || precedingType.isRepeat();
    }

    /**
     * Whether the element at {@code index} is a mid-line key signature.
     *
     * <p>The barline in front of it is not tested. This reports what stands at {@code index}, so a
     * key signature that reached the line by any route answers {@code true} — testing for the
     * barline would make a refusal depend on the pair rule in {@code docs/key-signatures.md}
     * rather than help keep it.
     *
     * @param index element index; out of range yields false
     * @return {@code true} when a {@link KeyChangeElement} stands at {@code index}
     */
    public boolean isKeyChangeAt(int index) {
        return hasIndex(index) && getElement(index).getType().isKeyChange();
    }

    /**
     * Whether the element at {@code index} is the barline or repeat a key signature sits behind —
     * the same pair {@link #isKeyChangeAt} names from the key signature's side, seen from the
     * barline's.
     *
     * @param index element index; out of range yields false
     * @return {@code true} when a key signature stands at {@code index + 1} and {@code index}
     *     holds the barline or repeat it sits behind
     */
    private boolean isKeyChangeBarlineAt(int index) {
        return isKeyChangeBehindBarline(index + 1);
    }

    /**
     * Whether the element at {@code index} may be replaced in place — clicked through with
     * another element in note entry, which swaps the new element in and keeps the index.
     *
     * <p>Two things are refused, both because they are half of a pair that only means anything
     * whole: a <b>grace note</b>, which belongs to the host that follows it, and either half of a
     * <b>key signature and the barline it sits behind</b> — the pair rule in
     * {@code docs/key-signatures.md}, which {@link #effectiveBegin} deletes whole for the same
     * reason.
     *
     * <p>Says nothing about what may be <em>inserted</em> at {@code index}: a replacement takes
     * the element's place, while an insertion lands in front of it.
     * {@link #canInsertElementAt} is the insertion-side question.
     *
     * @param index element index; out of range yields true, since nothing stands there to
     *     protect and the caller's own bounds decide what happens next
     * @return {@code true} when note entry may write over the element at {@code index}
     */
    public boolean canReplaceElementAt(int index) {
        if (!hasIndex(index)) {
            return true;
        }

        return !getElement(index).getType().isGraceNote()
            && !isKeyChangeAt(index)
            && !isKeyChangeBarlineAt(index);
    }

    /**
     * Whether an element may be inserted in front of the element at {@code index} — the sibling
     * of {@link #canReplaceElementAt}, which answers whether the element at {@code index} may be
     * written over instead.
     *
     * <p>Every way content lands on a line by pointing at it asks this, whatever is being placed:
     * note entry, a pasted fragment and a mid-line key signature alike. Two slots are refused,
     * both because they sit <em>inside</em> a pair that only means anything adjacent:
     *
     * <ul>
     *   <li>in front of a <b>mid-line key signature</b>, which is the gap between it and the
     *       barline it stands behind. Anything landing there leaves the key signature preceded by
     *       something other than a barline, breaking {@link KeyChangeElement}'s position
     *       invariant — see {@code docs/key-signatures.md}.</li>
     *   <li>in front of the <b>host of a paired grace note</b>, which is the gap between the
     *       grace note and the note it decorates. The two mean nothing apart, so nothing goes
     *       between them.</li>
     * </ul>
     *
     * <p>Says nothing about which of the remaining slots a particular operation wants. That is
     * each operation's own rule, and it is asked separately — a key signature is never the first
     * element on a line, for instance, which this does not know or care about.
     *
     * @param index the slot an element would be inserted at, so that it lands in front of the
     *     element currently at {@code index}; out of range — including
     *     {@link #effectiveElementCount()}, the slot past the last element — yields true, since
     *     nothing stands there to be split
     * @return {@code true} when an element may be inserted at {@code index}
     */
    public boolean canInsertElementAt(int index) {
        return !isKeyChangeAt(index) && !isHostOfPairedGraceNote(index);
    }

    /**
     * Whether a syllable may be written on the element at {@code index} — this line's way of
     * asking {@link StaffElement#canBearSyllable}, which states the rule and which
     * {@code LyricLayoutBuilder} asks of the columns it is laying out instead.
     *
     * <p>Does not vary by verse: an element that can hold a syllable can hold one in every verse.
     *
     * @param index element index
     * @return {@code true} when a syllable may be written on the element at {@code index}
     * @throws IndexOutOfBoundsException if no element stands at {@code index}
     */
    public boolean canBearSyllableAt(int index) {
        return getElement(index).canBearSyllable(index >= 1 ? getElement(index - 1) : null);
    }

    /**
     * The last element a range ending at {@code end} really covers — see the pairing rule in
     * this class's Javadoc. Two elements after {@code end} belong to it: a breath mark, which
     * is positionally attached to the element before it, and a key signature whose barline is
     * the element at {@code end}. Pure query — mutates nothing.
     *
     * @param end the last element the caller named
     * @return {@code end} extended past the element paired with it, or {@code end} unchanged
     *     when nothing after it is paired with it; never less than {@code end}
     */
    public int effectiveEnd(int end) {
        if (end + 1 < effectiveElementCount() && getElement(end + 1).getType().isBreathMark()) {
            return end + 1;
        }

        if (isKeyChangeBehindBarline(end + 1)) {
            return end + 1;
        }

        return end;
    }

    /**
     * The first element a range beginning at {@code begin} really covers — see the pairing rule
     * in this class's Javadoc. Two elements before {@code begin} belong to it: a paired grace
     * note, which cannot outlive its host, and the barline a key signature at {@code begin}
     * sits behind. Pure query — mutates nothing.
     * <p>
     * <b>The barline case is a decision, not a symmetry.</b> A key signature cannot outlive its
     * barline, but a barline can outlive its key: deleting the key alone would leave a valid
     * line. The pair goes whole so that a barline the insertion flow added only to host a key
     * does not linger once the key is gone. The cost is the other case — a barline the user
     * placed themselves goes with the key that happened to follow it, merging two measures.
     * The notator is not prompted about that: {@code Selection.Range.contains} widens over the
     * same pairs, so selecting either half draws both as selected and the deletion takes what
     * was already shown going. Neither half of that reasoning is redundant; the backward
     * extension is not derivable from the forward one.
     * <p>
     * This is also why no deletion can leave a key signature at index 0: reaching index 0 takes
     * deleting the barline in front of it, which carries the key signature along. A separate
     * index-0 guard would be dead code.
     *
     * @param begin the first element the caller named
     * @return {@code begin} extended back over the element paired with it, or {@code begin}
     *     unchanged when nothing before it is paired with it; never greater than {@code begin}
     */
    public int effectiveBegin(int begin) {
        if (isHostOfPairedGraceNote(begin)) {
            return begin - 1;
        }

        return beginIncludingKeyChangeBarline(begin);
    }

    /**
     * {@code begin} extended back over the barline a key signature at {@code begin} sits behind,
     * or {@code begin} unchanged when no key signature stands there. Pure query — mutates
     * nothing.
     *
     * <p>This is the barline half of {@link #effectiveBegin} on its own, for the caller that owes
     * that widening but not the grace-note one: a <b>copy</b>. A key signature captured without
     * the barline in front of it is a clipboard fragment that violates
     * {@link KeyChangeElement}'s position invariant wherever it lands, so a copy must take
     * both. A paired grace note is the opposite case — it cannot outlive its host, so a copy
     * beginning at a host simply leaves the grace note behind rather than reaching back for it.
     *
     * @param begin the first element the caller named
     * @return {@code begin - 1} when a key signature at {@code begin} sits behind a barline,
     *     otherwise {@code begin}; never greater than {@code begin}
     */
    public int beginIncludingKeyChangeBarline(int begin) {
        return isKeyChangeBehindBarline(begin) ? begin - 1 : begin;
    }

    /** The inclusive element range a deletion or a copy actually covers. */
    public record EffectiveRange(int begin, int end) {}

    /**
     * The range a deletion or copy of {@code begin} through {@code end} actually covers, widened
     * at both ends by {@link #effectiveBegin} and {@link #effectiveEnd}. Every query that asks
     * about a deletion must ask about this range, not the caller's raw selection.
     *
     * @param begin the first element the caller named
     * @param end   the last element the caller named
     * @return the widened range, which always contains {@code [begin, end]}
     */
    public EffectiveRange effectiveRange(int begin, int end) {
        return new EffectiveRange(effectiveBegin(begin), effectiveEnd(end));
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
        for (var i = fromIndex + step; hasIndex(i); i += step) {
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
     * Asks every span on this line what should happen to it when {@code change} lands, and
     * carries out those answers — the single sweep behind every element insertion,
     * replacement and deletion.
     * <p>
     * Called <b>before</b> the change is applied, so each span judges itself against the
     * projection rather than the mutated line, and so the companion span mutations are
     * recorded before the primary element mutation: undo replays a step in reverse, and
     * has to restore the element before the spans that point at it.
     * <p>
     * Every span is asked first and acted on afterwards. Acting inside the loop would both
     * mutate the {@code spans} list being walked and let one span's removal change what a
     * later span is judged against.
     * <p>
     * A {@link SpanOutcome.Reshape} is the one answer this line cannot act on span by span:
     * same-type hairpins the change leaves with nothing between them become one, which no
     * span can decide on its own because none of them knows its siblings. The reshaped
     * spans are therefore grouped by hairpin kind and handed to {@link #mergeAdjacentSpans},
     * which expresses each reshape as a tracked removal plus a tracked addition of a copy,
     * since a span has no modification mutation of its own.
     *
     * @param change the pending change, not yet applied to this line
     */
    private void applySpanOutcomes(ElementChange change) {
        if (spans.isEmpty()) {
            return;
        }

        var doomed = new ArrayList<Span>();
        var reshapedByKind = new LinkedHashMap<Hairpin.Kind, List<HairpinSpan>>();

        for (var span : spans) {
            switch (span.outcomeFor(change, this)) {
                case SpanOutcome.Simple.KEEP -> { }
                case SpanOutcome.Simple.REMOVE -> doomed.add(span);
                case SpanOutcome.Reshape reshape -> collectReshape(reshapedByKind, span, reshape);
            }
        }

        doomed.forEach(this::removeInvalidatedSpan);

        // The projection is asked for per kind rather than hoisted: there are at most two
        // kinds, and a change nothing reshaped must not build one at all.
        for (var hairpinSpans : reshapedByKind.values()) {
            mergeAdjacentSpans(hairpinSpans, change.projectedElements());
        }
    }

    /**
     * Files {@code reshape} under {@code span}'s kind, ready for the merge pass.
     * <p>
     * Only a hairpin can reshape: the merge that consumes these answers replaces a run with
     * a copy of a hairpin, and there is nothing it could do with any other kind of span.
     */
    private static void collectReshape(
        Map<Hairpin.Kind, List<HairpinSpan>> reshapedByKind,
        Span span,
        SpanOutcome.Reshape reshape
    ) {
        if (!(span instanceof Hairpin hairpin)) {
            throw new IllegalStateException(
                "Only a hairpin may answer Reshape, but " + span.getClass().getSimpleName() + " did");
        }

        reshapedByKind
            .computeIfAbsent(hairpin.getKind(), kind -> new ArrayList<>())
            .add(new HairpinSpan(hairpin, reshape.begin(), reshape.end()));
    }

    /**
     * Returns whether the element at {@code index} can begin a hairpin: a pitched note,
     * or a grace note whose host is one.
     * <p>
     * The rule lives on {@link Hairpin#canAnchorAt}; this exists only so a caller
     * holding a {@code Line} and an index need not reach for the element list.
     *
     * @param lastIndex the last index a hairpin may reach, bounding the host lookahead
     */
    public boolean canAnchorHairpin(int index, int lastIndex) {
        return Hairpin.canAnchorAt(elements, index, lastIndex);
    }

    /**
     * Returns whether the element at {@code index} can end a hairpin: a pitched note, or
     * the first rest after one.
     * <p>
     * The rule lives on {@link Hairpin#canEndAt}; this exists only so a caller holding a
     * {@code Line} and an index need not reach for the element list.
     */
    public boolean canEndHairpin(int index) {
        return Hairpin.canEndAt(elements, index);
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
     * Returns true if deleting the elements at {@code [from, to]} — inclusive, and the same
     * position twice for a single element — would remove any Ending in this line. See
     * {@link #removesConfirmableSpan} for which spans are consulted and why the answer cannot
     * disagree with the deletion it precedes.
     * <p>
     * The positions are read against the pre-deletion line, so call this before deleting.
     */
    public boolean hasEndingInvalidatedByDeletion(int from, int to) {
        return removesConfirmableSpan(() -> ElementChange.forDeletion(this, from, to));
    }

    /**
     * Whether the change {@code changeSupplier} describes would remove any span whose loss
     * warrants a confirmation prompt.
     * <p>
     * Asks those spans the same question {@link #applySpanOutcomes} does, so a prompt and
     * the edit it precedes can never disagree about what the edit costs. Only spans that
     * ask to be confirmed are consulted: a tie an insertion displaces goes silently, so it
     * must not raise the ending confirm.
     * <p>
     * The supplier is not invoked when this line carries no such span, which is the common
     * case — only an {@link Ending} asks to be confirmed. That guard is what keeps a paste
     * from building one change per pasted element type for a line that has no ending to
     * lose.
     */
    private boolean removesConfirmableSpan(Supplier<ElementChange> changeSupplier) {
        if (spans.stream().noneMatch(Span::requiresInvalidationConfirm)) {
            return false;
        }

        var change = changeSupplier.get();

        return spans.stream()
            .filter(Span::requiresInvalidationConfirm)
            .anyMatch(span -> span.outcomeFor(change, this) == SpanOutcome.Simple.REMOVE);
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
     * would remove any Ending in this line. See {@link #removesConfirmableSpan} for which
     * spans are consulted and why.
     * <p>
     * Call before {@link #addElement(int, StaffElement)}.
     */
    public boolean hasEndingInvalidatedByInsertion(int insertedIndex, ElementType insertedType) {
        // The caller has a type rather than the element it will eventually insert. The
        // endings this consults read no more of an inserted element than its type, so a
        // fresh one stands in.
        return removesConfirmableSpan(
            () -> ElementChange.forInsertion(this, insertedIndex, insertedType.newInstance()));
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
