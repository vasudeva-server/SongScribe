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
 *
 * <p><b>Elements.</b> The five primitives — insertion, replacement, modification of one element's
 * fields, single removal and range removal — each hand their pending change to the span sweep,
 * post one mutation, and repair nothing around the element; an edit goes through the run
 * operations {@link EditableRun} states. The four that place or take away an element first ask
 * the song whether they may touch the terminal position, because the last element of the last
 * line is the song's to maintain: see {@link TerminalMaintainer}.
 *
 * <p><b>Ties.</b> A tie is a note's own state; the line is where it is recorded
 * ({@link #addTie}, {@link #removeTie}).
 *
 * <p><b>Key invariant.</b> A line's own {@link #getKey() key} is non-null only where a key change
 * actually takes effect at the start of the line; null means the line inherits the key in effect
 * at the end of the previous line. Line 0 of a song is always non-null, and {@link Song}
 * maintains that, both when a mutation promotes a different line to index 0 and when a document
 * is loaded. A line that belongs to no song is subject to the same rule. What a line
 * <em>inherits</em> is held by {@link Song} ({@link Song#inheritedKeyOf}), so a line cannot carry
 * a stale inherited key out of the song it was removed from.
 */
public final class Line implements EditableRun {

    private final LineSpans spans = new LineSpans(this);

    private final Song song;

    /**
     * Null when this line inherits its key.
     */
    private @Nullable Key key = null;

    private final List<StaffElement> elements = new ArrayList<>();

    private final List<StaffElement> elementsView = Collections.unmodifiableList(elements);

    /**
     * Lazily-built position index over {@link #elements}, or null when it must be rebuilt.
     * <p>
     * Identity-keyed, matching {@code ArrayList.indexOf}'s semantics: no {@code StaffElement}
     * overrides {@code equals}/{@code hashCode}, and an override added later must not silently
     * change what an element's position means.
     * <p>
     * Every write to {@link #elements} goes through {@code attach} or {@code detach}, which null
     * this field out.
     */
    private volatile @Nullable Map<StaffElement, Integer> elementIndexMap = null;

    private static final double LYRICS_DEFAULT_Y_SS = 6.25;

    /**
     * Shared by every script on the line.
     */
    private double lyricsYPosSs = LYRICS_DEFAULT_Y_SS;

    public Line(Song song) {
        this.song = song;
    }

    public Song getSong() {
        return song;
    }

    /**
     * The same instance for the line's whole life.
     */
    public LineSpans spanRegistry() {
        return spans;
    }

    public int index() {
        var result = song.indexOfLine(this);

        if (result == null) {
            throw new IllegalStateException("Line is not one of its song's lines");
        }

        return result;
    }

    public boolean isFirst() {
        return index() == 0;
    }

    public boolean isLast() {
        return index() == song.lineCount() - 1;
    }

    public void applyChange(Mutation mutation, Runnable mutator) {
        song.applyChange(mutation, mutator);
    }

    /**
     * @return null when this line establishes no key of its own and inherits the key in effect
     *         at the end of the previous line
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
     * Total, deliberately: a line that establishes no key and has nothing to inherit from — line 0
     * before it is keyed, a line removed from its song, or any line while a file is loading — is
     * in {@link Key#DEFAULT}.
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
     * — {@code KeyChangeStranding.ofMidLineEdit} takes this key as its starting point. A deletion
     * computes that reach whether or not it removes a key change, because one that removes none
     * leaves the end key where it was and the reach is one line, on the same path.
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
     * Null is the "there is nothing to warn about" answer: no cautionary is drawn or reserved for.
     *
     * @return null when this line is the song's last line or is not one of the song's lines at
     *         all — a detached line, or one being measured before it is added
     */
    public @Nullable Key nextLineRunningKey() {
        var next = nextLine();

        return next == null ? null : next.getRunningKey();
    }

    /**
     * @return {@code null} when this line is its song's last line or is not
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
     * @return {@code null} when this line is its song's first line or is
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
    // A span has no line pointer: its line is derived from where its endpoints sit. What hangs on a staff element — attachments, articulations — has none
    // either: its line is its host's, read through the host, so attaching or detaching the
    // host carries them along with nothing to push down.
    //
    // Invariant:
    //   staff element — element.getParentLine() == L ⟺ L.elements contains the element,
    //   and null ⟺ the element is in no line at all.
    // ========================================================================

    private void attach(StaffElement element) {
        elementIndexMap = null;
        element.setParentLine(this);
    }

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
     * A span is judged against the line with every pair applied, never against a line with only
     * some of them applied, so no span answers for an intermediate state the edit never shows.
     * Every span whose anchor or end was a replaced element is re-pointed at its replacement.
     *
     * @param replacement the pairs to apply, built against this line in its current state
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
     * The posted {@link ElementModification} holds the element's state from before and after
     * {@code mutator}, so undo and redo restore either one without the caller taking a copy.
     *
     * @param mutator it rewrites nothing but {@code field}
     */
    @Override
    public void modifyElement(int index, ElementField field, Runnable mutator) {
        modifyElement(index, EnumSet.of(field), mutator);
    }

    /**
     * @param mutator it rewrites nothing outside {@code fields}
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
     * A live view, not a copy: iterating it across a structural change to the line throws
     * {@link java.util.ConcurrentModificationException}, so an edit that walks the elements and
     * changes them copies the list first. The terminal is included.
     */
    public List<StaffElement> getElements() {
        return elementsView;
    }

    public List<StaffElement> effectiveElements() {
        return getElements().subList(0, effectiveElementCount());
    }

    /**
     * A live view over this line's own list, meaningful only until the line's elements next
     * change; holding it across a structural change throws
     * {@link java.util.ConcurrentModificationException}.
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
     * The deletion and its companion tie and span changes post through {@link #applyChange},
     * under {@link Song#applyChange}'s bracket rule. A tie that ends on an element of
     * {@code range} is untied first ({@link #untieEndingIn}).
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

        // Companion tie and span changes precede the primary deletion so reverse-order undo
        // re-inserts the elements before re-tying the anchor and re-adding the spans anchored
        // to them.
        untieEndingIn(range);
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
     * Nothing here judges the tie: the tie rule ({@link TieRule}) does.
     *
     * @param anchor a note of this line that can carry a tie ({@link Tie#canAnchor}); a file
     *     reader asks that first and drops a tie on any other note; while the song is replaying,
     *     the note the replayed record names, whichever line holds it at that point of the replay
     */
    public void addTie(NoteElement anchor) {
        if (!Tie.canAnchor(anchor)) {
            throw new IllegalArgumentException("only a note of kind NOTE can carry a tie: " + anchor);
        }

        if (anchor.isTiedToNext()) {
            return;
        }

        applyChange(new TieAddition(this, anchor), anchor::tieToNext);
    }

    /**
     * @param anchor while the song is replaying, the note the replayed
     *     record names, whichever line holds it at that point of the replay
     */
    public void removeTie(NoteElement anchor) {
        if (!anchor.isTiedToNext()) {
            return;
        }

        applyChange(new TieRemoval(this, anchor), anchor::untie);
    }

    /**
     * The untie is not reported: the user deleted the note the tie ended on. Without it the
     * anchor would stay tied to whatever its walk reaches once {@code range} is gone.
     *
     * <p>Does nothing while the song is replaying: the replayed batch already holds the
     * removal.
     */
    void untieEndingIn(ElementRange range) {
        if (song.isReplaying()) {
            return;
        }

        var endIndex = Tie.firstNonSeparatorIndexFrom(elements, range.begin());

        if (endIndex == null || endIndex > range.end() || !(elements.get(endIndex) instanceof NoteElement end)) {
            return;
        }

        var incoming = end.incomingTie();

        if (incoming != null) {
            var anchor = incoming.anchor();
            anchor.requireParentLine().removeTie(anchor);
        }
    }

    /**
     * The ties this line draws, each with its place in this line.
     * <p>
     * A line owns the tie that starts on each of its notes: whole when the end is in this line,
     * and as the half running off the right edge when the end is in the next line. It also owns
     * the half entering from its left edge of a tie whose anchor is in the previous line, which
     * ends on this line's first note. So an intra-line tie is listed once, from its anchor, and
     * a tie across a line break is listed once in each of its two lines.
     * <p>
     * The one statement of that ownership rule. Layout calls it once per line and lays out each
     * entry; everything downstream reads the laid-out ties rather than asking again.
     *
     * @return the ties, in the order of the notes they were found on: the half entering from the
     *     left edge first, when there is one
     */
    public List<TiePlacement> ownedTies() {
        var ties = new ArrayList<TiePlacement>();
        var entering = enteringTie();

        if (entering != null) {
            ties.add(placement(entering));
        }

        for (var element : elements) {
            if (element instanceof NoteElement note) {
                var outgoing = note.tie();

                if (outgoing != null) {
                    ties.add(placement(outgoing));
                }
            }
        }

        return ties;
    }

    /**
     * @return {@code null} when this line holds neither of its notes
     *     and so draws no part of it
     */
    public @Nullable TiePlacement placementOf(Tie tie) {
        return contains(tie.anchor()) || contains(tie.end()) ? placement(tie) : null;
    }

    private @Nullable Tie enteringTie() {
        var firstIndex = Tie.firstNonSeparatorIndexFrom(elements, 0);

        if (firstIndex == null || !(elements.get(firstIndex) instanceof NoteElement first)) {
            return null;
        }

        var incoming = first.incomingTie();

        return incoming != null && !contains(incoming.anchor()) ? incoming : null;
    }

    private TiePlacement placement(Tie tie) {
        var anchor = tie.anchor();
        var end = tie.end();
        var holdsAnchor = contains(anchor);
        var holdsEnd = contains(end);
        var begin = holdsAnchor ? getElementIndex(anchor) : 0;
        var last = holdsEnd ? getElementIndex(end) : elementCount() - 1;
        @Nullable Edge openEdge = null;

        if (!holdsAnchor) {
            openEdge = Edge.START;
        } else if (!holdsEnd) {
            openEdge = Edge.END;
        }

        return new TiePlacement(tie, new ElementRange(begin, last), openEdge);
    }

    /**
     * An edge of a line that a tie half runs off, because the tie's other note is in the
     * neighboring line.
     */
    public enum Edge {

        START,

        END
    }

    /**
     * A tie's place in one line that holds at least one of its notes: the elements it is drawn
     * over, and the edge it runs off when only one of its notes is here. Made only by
     * {@link #ownedTies} and {@link #placementOf}. A value read off the line at the moment it was
     * made; an edit to the line does not update it.
     *
     * @invariant {@code range().begin()} is the anchor's index unless {@code openEdge()} is
     *     {@link Edge#START}, when it is {@code 0}; {@code range().end()} is the end's index
     *     unless {@code openEdge()} is {@link Edge#END}, when it is the line's last index
     */
    public static final class TiePlacement {

        private final Tie tie;
        private final ElementRange range;
        private final @Nullable Edge openEdge;

        private TiePlacement(Tie tie, ElementRange range, @Nullable Edge openEdge) {
            this.tie = tie;
            this.range = range;
            this.openEdge = openEdge;
        }

        public Tie tie() {
            return tie;
        }

        public ElementRange range() {
            return range;
        }

        /**
         * @return {@code null} when both of its notes are in the line
         */
        public @Nullable Edge openEdge() {
            return openEdge;
        }

        public boolean holdsAnchor() {
            return openEdge != Edge.START;
        }

        public boolean holdsEnd() {
            return openEdge != Edge.END;
        }

        @Override
        public String toString() {
            return "TiePlacement{" + tie + ", " + range + ", openEdge=" + openEdge + '}';
        }
    }

    @Override
    public int elementCount() {
        return elements.size();
    }

    /**
     * The element count with a trailing auto-maintained terminal left out, for a computation
     * that treats the song-owned terminal as if it were not there.
     */
    @Override
    public int effectiveElementCount() {
        return isTerminalAt(elementCount() - 1) ? elementCount() - 1 : elementCount();
    }

    /**
     * @param index element index; out of range yields {@code false}
     */
    public boolean isTerminalAt(int index) {
        return hasIndex(index) && song.isTerminal(getElement(index), this);
    }

    /**
     * @return {@code false} when this line is not one of its song's lines
     */
    @Override
    public boolean isSongStartAt(int index) {
        return SongStart.isSlot(this, index);
    }

    /**
     * Asked when the caller does not know whether the element is on this line;
     * {@link #getElementIndex} assumes it is.
     */
    public boolean contains(StaffElement element) {
        return element.getParentLine() == this;
    }

    /**
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

    public void setLyricsYPosSs(double lyricsYPosSs) {
        var old = this.lyricsYPosSs;
        applyChange(
            new LineLayoutChange(this, LineLayoutField.LYRICS_Y_POS_SS, old, lyricsYPosSs),
            () -> this.lyricsYPosSs = lyricsYPosSs
        );
    }
}
