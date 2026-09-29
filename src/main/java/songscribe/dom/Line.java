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
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.error.RuntimeError;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LineLayoutChange;
import songscribe.message.mutation.LineLayoutField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;

/**
 * One system of the score: an {@link EditableRun element run} that belongs to a {@link Song} and
 * posts every change to its elements as a mutation through that song's modification bracket.
 * It holds four things — the key it establishes, its ordered staff elements, the vertical offset
 * of its lyric rows, and the registry of the spans drawn over it.
 *
 * <p><b>Elements.</b> The five primitives below — insertion, replacement, modification of one
 * element's fields, single removal and range removal — each hand their pending change to the span
 * sweep, post one mutation, and repair nothing around the element; an edit goes through the run
 * operations {@link EditableRun} states, which run the repairs. There is one primitive for each
 * shape of {@link ElementChange}, so every change to the elements passes through one sweep. The
 * four that place or take away an element first ask the song whether they may touch the terminal
 * position, because the last element of the last line is the song's to maintain, not the line's:
 * the policy is stated once, on the song's {@link TerminalMaintainer}.
 *
 * <p><b>Spans.</b> The spans are not held here but in the line's {@link LineSpans}, reached
 * through {@link #spanRegistry()}: every query over them, the sweep that re-judges them when an
 * element changes, and the merge rules belong to that registry, and a span enters or leaves it
 * through its own {@link Span#addTo}/{@link Span#removeFrom}. The element primitives only hand
 * each pending change to the registry before they apply it.
 *
 * <p><b>Ties.</b> A tie is not a span: it is a note's own state, and its end is derived (see
 * {@link NoteElement}). The line is where a tie is recorded — {@link #addTie} and
 * {@link #removeTie} — and where the tie rule judges its ties, {@link #applyTieRule}, which
 * {@link TieRule} runs when the outermost modification bracket closes.
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
 * {@link Song#inheritedKeyOf}. A line therefore cannot carry a stale inherited key out of the song
 * it was removed from.
 *
 * <p><b>Pairing.</b> Which elements cannot outlive their partner, and how a range widens to cover
 * both, is {@link StaffElementRun}'s — a line is one such run, and a clipboard fragment is
 * another, so the rule is stated once for both.
 */
public final class Line implements EditableRun {

    /** The spans drawn over this line's elements. See the class Javadoc. */
    private final LineSpans spans = new LineSpans(this);

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
     * <p>Writes to the span registry are absent from that picture because they cannot reach
     * it: a span holds no position in {@code elements}, and {@link LineSpans} writes its own
     * list alone.
     */
    private volatile @Nullable Map<StaffElement, Integer> elementIndexMap = null;

    /**
     * Default lyrics Y position (below staff)
     */
    private static final double LYRICS_DEFAULT_Y_SS = 6.25;

    /**
     * The vertical position of this line's lyric rows below the staff, in staff spaces, shared
     * by every verse on the line.
     */
    private double lyricsYPosSs = LYRICS_DEFAULT_Y_SS;

    public Line(Song song) {
        this.song = song;
    }

    public Song getSong() {
        return song;
    }

    /**
     * The registry of the spans drawn over this line's elements.
     *
     * @return this line's span registry, the same instance for the line's whole life
     */
    public LineSpans spanRegistry() {
        return spans;
    }

    /**
     * This line's position among its song's lines.
     *
     * @return the index {@link Song#getLine} would return this line for
     * @throws IllegalStateException if this line is not one of its song's lines
     */
    public int index() {
        var result = song.indexOfLine(this);

        if (result == null) {
            throw new IllegalStateException("Line is not one of its song's lines");
        }

        return result;
    }

    /**
     * Whether this line is its song's first line.
     *
     * @return {@code true} when this line stands at index 0 of its song
     * @throws IllegalStateException if this line is not one of its song's lines
     */
    public boolean isFirst() {
        return index() == 0;
    }

    /**
     * Whether this line is its song's last line.
     *
     * @return {@code true} when no line follows this one in its song
     * @throws IllegalStateException if this line is not one of its song's lines
     */
    public boolean isLast() {
        return index() == song.lineCount() - 1;
    }

    /**
     * Posts a single mutation through the song's modification bracket. What the bracket
     * promises — recording, the suspended-tracking path, the key invariant, the chain walk,
     * and what is thrown outside a bracket — is {@link Song#applyChange}'s contract.
     */
    public void applyChange(Mutation mutation, Runnable mutator) {
        song.applyChange(mutation, mutator);
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
     * @effects posts the {@link LineKeyChange} through {@link #applyChange}, under
     *     {@link Song#applyChange}'s bracket rule
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
     * accounts for any mid-line key change that line carried.
     *
     * <p>Equals {@link #getKey()} whenever that is non-null. Total, and deliberately so — a model
     * getter has no business terminating the application. A line that establishes no key and has
     * nothing to inherit from — line 0 before it is keyed, a line removed from its song, or any line
     * while a file is part-way through loading — is in {@link Key#DEFAULT}, the key a document that
     * names none is in, not a stand-in for a broken invariant.
     *
     * @return the key in effect at the start of this line; never null
     */
    public Key getRunningKey() {
        var ownKey = key;

        if (ownKey != null) {
            return ownKey;
        }

        var inheritedKey = song.inheritedKeyOf(this);
        return (inheritedKey != null) ? inheritedKey : Key.DEFAULT;
    }

    /**
     * Returns the key in force just before {@code index}: {@link #getRunningKey()}, overridden
     * by the last {@link KeyChangeElement} at an index below {@code index}. An element inserted
     * at {@code index} is in this key, and a key change standing at {@code index} is not counted.
     * Index 0 never holds a key change — see {@link KeyChangeElement}'s position invariant — so
     * {@code keyBefore(1)} equals {@code keyBefore(0)}.
     *
     * @param index an insertion index, {@code 0..}{@link #elementCount()}
     * @return the key in force just before that index; {@link #getRunningKey()} at 0
     * @invariant {@code keyBefore(elementCount())} equals {@link #keyAtEndOfLine()}
     */
    public Key keyBefore(int index) {
        for (var scanIndex = index - 1; scanIndex > 0; scanIndex--) {
            if (elements.get(scanIndex) instanceof KeyChangeElement keyChange) {
                return keyChange.getKey();
            }
        }

        return getRunningKey();
    }

    /**
     * Returns the key in effect after this line's last element — the key the next line inherits.
     * Equal to {@code keyBefore(elementCount())}, see that method's {@code @invariant}.
     *
     * @return the key in effect at the end of this line, which is {@link #getRunningKey()} when
     *         the line is empty; never null
     */
    public Key keyAtEndOfLine() {
        return keyAtEndUnder(getRunningKey());
    }

    /**
     * Returns the key this line will leave off in once {@code range} is removed from it: the last
     * key change surviving after the range, and otherwise the key in effect immediately before
     * it.
     *
     * <p>This is what a deletion needs and {@link #keyAtEndUnder(int, Key)} cannot give.
     * Removing a mid-line key change moves the key every following line inherits, so the
     * deletion owes the same cross-line stranding sweep an inserted key change owes
     * — {@code KeyChangeStranding.ofMidLineEdit} takes this key as its starting point. Why a
     * deletion computes that reach unconditionally is in {@code docs/key-changes.md}.
     *
     * @param effectiveRange the elements the deletion removes
     * @return the key in effect after this line's last element once the range is gone; never null
     */
    public Key keyAtEndOfLineAfterRemoving(EffectiveRange effectiveRange) {
        var range = effectiveRange.range();
        var survivingKey = lastKeyChangeKeyFrom(range.end() + 1);

        if (survivingKey != null) {
            return survivingKey;
        }

        return keyBefore(range.begin());
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
        var next = nextLine();

        return next == null ? null : next.getRunningKey();
    }

    /**
     * The line that follows this one in its song.
     *
     * @return the next line, or {@code null} when this line is its song's last line or is not
     *     one of its song's lines
     */
    @Nullable Line nextLine() {
        var lineIndex = song.indexOfLine(this);

        if (lineIndex == null || lineIndex == song.lineCount() - 1) {
            return null;
        }

        return song.getLine(lineIndex + 1);
    }

    /**
     * The line that precedes this one in its song.
     *
     * @return the previous line, or {@code null} when this line is its song's first line or is
     *     not one of its song's lines
     */
    @Nullable Line previousLine() {
        var lineIndex = song.indexOfLine(this);

        if (lineIndex == null || lineIndex == 0) {
            return null;
        }

        return song.getLine(lineIndex - 1);
    }

    // ========================================================================
    // Line element parentage
    //
    // For the staff elements held in `elements`, attach/detach are the only writers of
    // parentLine. Every mutation of `elements` funnels through one of them from inside
    // the applyChange mutator lambda, so parentage moves with the recorded change:
    //
    // Both addElement overloads attach; removeElement and removeRange detach; replace does
    // both, detaching each outgoing element and attaching its incoming one.
    //
    // A span has no line pointer: Span.isIn(Line) derives the lines it is in from where its
    // endpoints sit. What hangs on a staff element — attachments, articulations — has none
    // either: its line is its host's, read through the host, so attaching or detaching the
    // host carries them along with nothing to push down.
    //
    // Invariant:
    //   staff element — element.getParentLine() == L ⟺ L.elements contains the element,
    //   and null ⟺ the element is in no line at all.
    // ========================================================================

    /** Points {@code element} at this line. */
    private void attach(StaffElement element) {
        elementIndexMap = null;
        element.setParentLine(this);
    }

    /** Clears {@code element}'s line pointer. */
    private void detach(StaffElement element) {
        elementIndexMap = null;
        element.setParentLine(null);
    }

    /**
     * Appends {@code element}, or inserts it before the auto-maintained terminal when this
     * line carries one, so the terminal remains last.
     * <p>
     * An append is an insertion at the end, and it goes through the insertion primitive,
     * {@link #addElement(int, StaffElement)}, so everything that primitive promises applies to
     * it: the span sweep, the mutation it posts, and the terminal check.
     *
     * @param element the element to append
     * @throws IllegalStateException if the insertion breaks the song's terminal policy — see
     *     {@link Song#checkTerminalPolicyForInsertion}
     */
    public void addElement(StaffElement element) {
        addElement(effectiveElementCount(), element);
    }

    /**
     * Inserts {@code element} at {@code index} and posts a single {@link ElementInsertion}
     * mutation, after the companion span changes the insertion forces.
     * <p>
     * The primitive: it places the element and repairs nothing around it. An edit inserts
     * through {@link #insertRun} instead, which runs the repairs on either side.
     * <p>
     * The insertion and its companion span changes post through {@link #applyChange}, under
     * {@link Song#applyChange}'s bracket rule.
     *
     * @param index   the position {@code element} will occupy, {@code 0..}{@link #elementCount()}
     * @param element the element to insert
     * @throws IllegalStateException if the insertion breaks the song's terminal policy — see
     *     {@link Song#checkTerminalPolicyForInsertion}
     * @throws IndexOutOfBoundsException if {@code index} is outside
     *     {@code 0..}{@link #elementCount()}
     */
    @Override
    public void addElement(int index, StaffElement element) {
        song.checkTerminalPolicyForInsertion(this, index, element.getType());

        // Companion span changes precede the primary insertion so reverse-order undo restores
        // the primary element before re-adding dependent spans.
        spans.sweep(new ElementChange.Insertion(this, index, element));

        applyChange(
            new ElementInsertion(this, index, element),
            () -> {
                attach(element);
                elements.add(index, element);
            }
        );
    }

    /**
     * Replaces every element {@code replacement} names with its new element, as one change:
     * the spans judge all of its pairs together, once, and then each pair posts its own
     * {@link ElementReplacement}. Every span whose anchor or end was a replaced element is
     * re-pointed at that element's replacement; the spans themselves decide what else the
     * replacement costs them.
     * <p>
     * A span is judged against the line with every pair applied, never against a line with
     * only some of them applied, so no span answers for an intermediate state the edit never
     * shows.
     *
     * @param replacement the pairs to apply, built against this line in its current state
     * @throws IllegalArgumentException if {@code replacement.line()} is not this line
     * @throws IllegalStateException if any pair breaks the song's terminal policy — see
     *     {@link Song#checkTerminalPolicyForReplacement}; nothing is changed
     * @effects posts the companion span changes the sweep decides, then one
     *     {@link ElementReplacement} per pair in ascending index order, all through
     *     {@link #applyChange} under {@link Song#applyChange}'s bracket rule
     */
    public void replace(ElementChange.Replacement replacement) {
        if (replacement.line() != this) {
            throw new IllegalArgumentException("replacement is for another line");
        }

        var newElements = replacement.newElements();

        newElements.forEach((index, element) ->
            song.checkTerminalPolicyForReplacement(this, index, element.getType()));

        // Decided before any mutator so every span judges the pre-replacement line.
        // Companion span changes precede the primary replacements so reverse-order undo
        // restores the primary elements before re-adding dependent spans. The anchor
        // re-pointing in each mutator below runs on replay too — it is self-inverting and
        // required for span references to stay valid.
        spans.sweep(replacement);

        newElements.forEach((index, element) -> {
            var oldElement = elements.get(index);

            applyChange(
                new ElementReplacement(this, index, oldElement, element),
                () -> {
                    // Detach first: a self-replace (element == oldElement) must end with the
                    // element attached, so detaching after attach would leave it detached.
                    detach(oldElement);
                    elements.set(index, element);
                    attach(element);
                    spans.repoint(oldElement, element);
                }
            );
        });
    }

    /**
     * Replaces the element at {@code index} with {@code element}: a {@link #replace} of one
     * pair, under all of its rules.
     *
     * @param index   the position of the element to replace
     * @param element the element taking its place
     * @throws IndexOutOfBoundsException if no element stands at {@code index}
     * @throws IllegalStateException if the replacement breaks the song's terminal policy — see
     *     {@link Song#checkTerminalPolicyForReplacement}
     */
    public void setElement(int index, StaffElement element) {
        replace(ElementChange.Replacement.single(this, index, element));
    }

    /**
     * Runs {@code mutator} to change {@code field} of the element at {@code index}, and posts one
     * {@link ElementModification} that holds the element's state from before and after
     * {@code mutator}, so undo and redo restore either one without the caller taking a copy.
     * <p>
     * The modification and its companion span changes post through {@link #applyChange}, under
     * {@link Song#applyChange}'s bracket rule.
     *
     * @param index   the position of the element to modify
     * @param field   the field {@code mutator} rewrites
     * @param mutator the change to apply to the element; it rewrites nothing but {@code field}
     * @throws IndexOutOfBoundsException if no element stands at {@code index}
     */
    @Override
    public void modifyElement(int index, ElementField field, Runnable mutator) {
        modifyElement(index, EnumSet.of(field), mutator);
    }

    /**
     * As {@link #modifyElement(int, ElementField, Runnable)}, for a change that rewrites
     * several fields at once and records them as one {@link ElementModification}.
     *
     * @param index   the position of the element to modify
     * @param fields  the fields {@code mutator} rewrites
     * @param mutator the change to apply to the element; it rewrites nothing outside
     *     {@code fields}
     * @throws IndexOutOfBoundsException if no element stands at {@code index}
     */
    public void modifyElement(int index, EnumSet<ElementField> fields, Runnable mutator) {
        // Asked before the mutator runs, like every other sweep, so a span judges the
        // element as it stands; only which fields change is what a span reads.
        spans.sweep(new ElementChange.Modification(this, index, fields));

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

    /**
     * This line's elements, in order.
     * <p>
     * A live view, not a copy: it always shows the line as it is now. A caller that iterates it
     * across a structural change to the line gets
     * {@link java.util.ConcurrentModificationException}, so an edit that walks the elements
     * and changes them copies the list first.
     *
     * @return an unmodifiable live view of every element on this line, the terminal included
     */
    public List<StaffElement> getElements() {
        return elementsView;
    }

    /**
     * This line's elements with the song-owned terminal excluded: the list form of
     * {@link #effectiveElementCount()}.
     *
     * @return an unmodifiable live view, on the same terms as {@link #getElements()}, of the
     *     first {@link #effectiveElementCount()} elements
     */
    public List<StaffElement> effectiveElements() {
        return getElements().subList(0, effectiveElementCount());
    }

    /**
     * Returns the elements {@code range} covers, in order.
     * <p>
     * A live view over this line's own list, not a copy: it reflects the line as it is at the
     * moment it is read, and is meaningful only until the line's elements next change. A
     * caller that holds it across an edit reads positions that have moved, and one that holds
     * it across a structural change gets {@link java.util.ConcurrentModificationException}.
     *
     * @param range the elements to view, both ends included
     * @return an unmodifiable live view of exactly those elements
     * @throws IndexOutOfBoundsException if {@code range} reaches past {@link #elementCount()}
     */
    public List<StaffElement> getElements(ElementRange range) {
        // subList is exclusive of the end index, so we add 1.
        // Wrapped per call because the bounds vary: a caller that could reorder or
        // clear the sublist would move element positions behind the index's back.
        return Collections.unmodifiableList(elements.subList(range.begin(), range.end() + 1));
    }

    /**
     * Removes all elements in the contiguous {@code range} and posts a single
     * {@link ElementDeletion} mutation.
     * <p>
     * The primitive beneath {@link #deleteRange}, and what replay calls: it drops the elements
     * and repairs nothing around them. An edit deletes through {@code deleteRange}, which runs
     * the repairs.
     * <p>
     * The deletion and its companion span changes post through {@link #applyChange}, under
     * {@link Song#applyChange}'s bracket rule.
     *
     * @param range the elements to remove
     * @throws IllegalStateException if the range covers the song's auto-maintained terminal —
     *     see {@link Song#checkTerminalPolicyForRemoval}
     * @throws IndexOutOfBoundsException if {@code range} reaches past {@link #elementCount()}
     */
    @Override
    public void removeRange(ElementRange range) {
        song.checkTerminalPolicyForRemoval(this, range);

        var from = range.begin();
        var to = range.end();
        var deletedElements = List.copyOf(elements.subList(from, to + 1));

        // Companion span changes precede the primary deletion so reverse-order undo re-inserts
        // the elements before re-adding the spans anchored to them.
        spans.sweep(new ElementChange.Deletion(this, range));

        applyChange(
            new ElementDeletion(this, range, deletedElements),
            () -> {
                deletedElements.forEach(this::detach);
                elements.subList(from, to + 1).clear();
            }
        );
    }

    // ========================================================================
    // Ties
    // ========================================================================

    /**
     * Ties {@code anchor} to the next note and posts one {@link TieAddition}. The note the tie
     * ends on is derived, not given — see {@link NoteElement#tie()} — so this names only the
     * anchor.
     * <p>
     * Nothing here judges the tie: a caller ties a note where the user or a file placed a tie,
     * and the tie rule (see {@link NoteElement}) judges it when the outermost modification
     * bracket closes, like any other tie in a line the bracket touched.
     *
     * @param anchor a note of this line whose {@link ElementType#kind()} is
     *     {@link ElementType.Kind#NOTE}, which is not checked here: a caller converts at its
     *     boundary, so that only such a note is ever tied; while the song is replaying, the note
     *     the replayed record names, whichever line holds it at that point of the replay
     * @effects when {@code anchor} is not already tied, posts the {@link TieAddition} through
     *     {@link #applyChange}, under {@link Song#applyChange}'s bracket rule, and leaves
     *     {@link NoteElement#isTiedToNext()} {@code true}; when it is already tied, records
     *     nothing
     */
    public void addTie(NoteElement anchor) {
        if (anchor.isTiedToNext()) {
            return;
        }

        applyChange(new TieAddition(this, anchor), anchor::tieToNext);
    }

    /**
     * Unties {@code anchor} from the next note and posts one {@link TieRemoval}.
     *
     * @param anchor a note of this line; while the song is replaying, the note the replayed
     *     record names, whichever line holds it at that point of the replay
     * @effects when {@code anchor} is tied, posts the {@link TieRemoval} through
     *     {@link #applyChange}, under {@link Song#applyChange}'s bracket rule, and leaves
     *     {@link NoteElement#isTiedToNext()} {@code false}; on an untied note, records nothing
     */
    public void removeTie(NoteElement anchor) {
        if (!anchor.isTiedToNext()) {
            return;
        }

        applyChange(new TieRemoval(this, anchor), anchor::untie);
    }

    /**
     * Runs the tie rule (see {@link NoteElement}) over every tied note of this line: each one is
     * judged by {@link Tie#holds} against the element its walk reaches, in this line or at the
     * start of the next, and each tie that does not hold is reported and removed through
     * {@link #removeTie}. {@link TieRule} calls it when the outermost modification bracket
     * closes, once every change in the bracket has landed.
     *
     * @param report the report of the edit being closed
     * @effects for each tie that does not hold, records into {@code report} a change headed
     *     {@link Strings#DIALOG_REPORT_TIES_REMOVED}, located from the anchor to the element the
     *     walk reaches, or at the anchor alone when it reaches none, then posts a
     *     {@link TieRemoval}; nothing else
     */
    void applyTieRule(ChangeReport report) {
        var next = nextLine();

        for (var i = 0; i < elements.size(); i++) {
            if (!(elements.get(i) instanceof NoteElement anchor) || !anchor.isTiedToNext()) {
                continue;
            }

            var end = Tie.endAfter(elements, i, next);

            if (!Tie.holds(anchor, end)) {
                report.record(new ReportedChange(
                    Strings.DIALOG_REPORT_TIES_REMOVED,
                    Strings.get(Strings.DIALOG_REPORT_REASON_TIE_NOT_HELD),
                    tieLocation(i, end)));
                removeTie(anchor);
            }
        }
    }

    /**
     * The elements a tie from this line's element at {@code anchorIndex} to {@code end} covers.
     *
     * @param anchorIndex the anchor's index in this line, which is one of its song's lines
     * @param end         the element the tie's walk reaches, in this line or the next, or
     *                    {@code null} when it reaches none
     * @return the range from the anchor's location to {@code end}'s, or the anchor's location
     *     alone when {@code end} is {@code null}
     */
    private ElementLocationRange tieLocation(int anchorIndex, @Nullable StaffElement end) {
        var start = new ElementLocation(index(), anchorIndex);

        if (end == null) {
            return ElementLocationRange.single(start);
        }

        var endLine = end.getParentLine();

        // Tie.endAfter reads the end from this line's elements or the next line's.
        if (endLine == null) {
            throw RuntimeError.exit("a tie's end is in no line");
        }

        return new ElementLocationRange(
            start, new ElementLocation(endLine.index(), endLine.getElementIndex(end)));
    }

    /**
     * The ties this line draws, one entry per tie or tie half it owns.
     * <p>
     * A line owns the tie that starts on each of its notes: whole when the end is in this line,
     * and as the half running off the right edge when the end is in the next line. It also owns
     * the half entering from its left edge of a tie whose anchor is in the previous line, which
     * ends on this line's first note. So an intra-line tie is listed once, from its anchor, and
     * a tie across a line break is listed once in each of its two lines.
     * <p>
     * The one statement of that ownership rule: layout builds a
     * {@code LayoutResult.TieLayout} for each entry, and the renderer iterates the same list to
     * draw them.
     *
     * @return the ties, in the order of the notes they were found on
     */
    public List<Tie> ownedTies() {
        var ties = new ArrayList<Tie>();

        for (var element : elements) {
            if (!(element instanceof NoteElement note)) {
                continue;
            }

            var incoming = note.incomingTie();

            if (incoming != null && !contains(incoming.anchor())) {
                ties.add(incoming);
            }

            var outgoing = note.tie();

            if (outgoing != null) {
                ties.add(outgoing);
            }
        }

        return ties;
    }

    @Override
    public int elementCount() {
        return elements.size();
    }

    /**
     * The element count with a trailing auto-maintained terminal
     * ({@code FINAL_DOUBLE_BARLINE} or {@code REPEAT_RIGHT}) left out, for a computation that
     * treats the song-owned terminal as if it were not there — insertion spacing and preview
     * positioning among them.
     *
     * @return {@link #elementCount()} less one when the last element is the terminal
     *     {@link #isTerminalAt} recognizes, otherwise {@link #elementCount()}
     */
    @Override
    public int effectiveElementCount() {
        return isTerminalAt(elementCount() - 1) ? elementCount() - 1 : elementCount();
    }

    /**
     * Whether the element at {@code index} is the song's terminal — the auto-maintained
     * {@code FINAL_DOUBLE_BARLINE} or {@code REPEAT_RIGHT} the song owns rather than the user.
     *
     * @param index element index; out of range yields {@code false}
     * @return {@code true} when the element at {@code index} is the terminal
     *     {@link Song#isTerminal} recognizes on this line
     */
    public boolean isTerminalAt(int index) {
        return hasIndex(index) && song.isTerminal(getElement(index), this);
    }

    /**
     * Returns whether {@code element} is one of this line's elements.
     * <p>
     * This is the query a caller asks when it does not know whether the element is on this line;
     * {@link #getElementIndex} assumes it is. The answer is read from the element's parent
     * pointer, which is an exact membership test: a staff element's parent line is written only
     * by {@link #attach} and {@link #detach}.
     *
     * @return {@code true} when {@code element.getParentLine() == this}
     */
    public boolean contains(StaffElement element) {
        return element.getParentLine() == this;
    }

    /**
     * Returns {@code element}'s position in this line.
     * <p>
     * A caller that does not know whether the element is on this line asks {@link #contains}
     * first; this method is total over the line's own elements and rejects every other.
     *
     * @return the element's position, as {@link #getElement} indexes it
     * @throws IllegalArgumentException when {@code element.getParentLine() != this}
     * @invariant the result is a valid index into this line: {@code 0 <= result < elementCount()}
     */
    public int getElementIndex(StaffElement element) {
        if (!contains(element)) {
            throw new IllegalArgumentException("element is not in this line");
        }

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

        if (index == null) {
            throw RuntimeError.exit("element's parent line is this line but it is not in the element list");
        }

        return index;
    }


    public double getLyricsYPosSs() {
        return lyricsYPosSs;
    }

    /**
     * Moves this line's lyric rows to {@code lyricsYPosSs} below the staff, and posts one
     * {@link LineLayoutChange} that holds the old and the new position.
     * <p>
     * The change posts through {@link #applyChange}, under {@link Song#applyChange}'s bracket
     * rule.
     *
     * @param lyricsYPosSs the new vertical position of every verse's row on this line, in staff
     *     spaces below the staff
     */
    public void setLyricsYPosSs(double lyricsYPosSs) {
        var old = this.lyricsYPosSs;
        applyChange(
            new LineLayoutChange(this, LineLayoutField.LYRICS_Y_POS_SS, old, lyricsYPosSs),
            () -> this.lyricsYPosSs = lyricsYPosSs
        );
    }
}
