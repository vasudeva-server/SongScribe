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

package songscribe.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

/**
 * A run of {@link StaffElement}s in order, and the rules that decide which of them mean nothing
 * apart.
 *
 * <p>Some elements cannot stand without a neighbor, so a range naming one of them really covers
 * both: a <b>paired grace note</b> goes with its host, a <b>key change</b> with the barline or
 * repeat in front of it ({@link KeyChangeElement}'s position invariant), and a <b>breath mark</b>
 * with the element before it. The pairs are not symmetric. {@link #effectiveRange} is the one
 * definition of the pairing; a caller must not reimplement it.
 *
 * <p>A clipboard {@link songscribe.ui.clipboard.Fragment} is a run too, so it gets the same
 * answers as a {@link Line}.
 */
public interface StaffElementRun {

    int FIRST_LEGAL_KEY_CHANGE_INDEX = 1;

    // -------------------------------------------------------------------------
    // What a run must answer for itself
    // -------------------------------------------------------------------------

    StaffElement getElement(int index);

    int elementCount();

    int effectiveElementCount();

    default boolean hasIndex(int index) {
        return index >= 0 && index < elementCount();
    }

    /**
     * @return always {@code false} for a run that belongs to no song
     */
    boolean isSongStartAt(int index);

    default NoteElement noteAt(int index) {
        var element = getElement(index);

        if (element instanceof NoteElement note) {
            return note;
        }

        throw new IllegalStateException("expected a note at index " + index + " but found " + element);
    }

    // -------------------------------------------------------------------------
    // Pairing
    // -------------------------------------------------------------------------

    /**
     * Answers without checking that the following note is present, so that
     * {@link songscribe.ui.clipboard.Fragment#capture} can recognize the orphan it has to trim.
     */
    default boolean isPairedGraceNote(int index) {
        return hasIndex(index) && getElement(index) instanceof NoteElement note && note.isPairedGraceNote();
    }

    default boolean isPairedGraceNoteTarget(int index) {
        return isPairedGraceNote(index - 1);
    }

    private boolean isKeyChangeBehindBarline(int index) {
        return isKeyChangeAt(index) && isBarAt(index - 1);
    }

    /**
     * {@code begin} extended back over the barline a key change at {@code begin} sits behind,
     * or {@code begin} unchanged when no key change stands there. Pure query — mutates
     * nothing.
     *
     * <p>This is the barline half of {@link #effectiveBegin} on its own, for the caller that owes
     * that widening but not the grace-note one: a <b>copy</b>. A key change captured without
     * the barline in front of it is a clipboard fragment that violates
     * {@link KeyChangeElement}'s position invariant wherever it lands, so a copy must take
     * both. A paired grace note is the opposite case — it cannot outlive its host, so a copy
     * beginning at a host simply leaves the grace note behind rather than reaching back for it.
     *
     * @param begin the first element the caller named
     * @return {@code begin - 1} when a key change at {@code begin} sits behind a barline,
     *     otherwise {@code begin}; never greater than {@code begin}
     */
    default int beginIncludingKeyChangeBarline(int begin) {
        return isKeyChangeBehindBarline(begin) ? begin - 1 : begin;
    }

    /**
     * The barline case is a decision, not a symmetry: a barline can outlive its key, but the pair
     * goes whole so a barline added only to host a key does not linger. A barline the user placed
     * goes with the key that follows it; the notator is not prompted, because
     * {@code Selection.Range.contains} widens over the same pairs and already shows both selected.
     *
     * @invariant no deletion of the range this begins leaves a key change at index 0
     */
    default int effectiveBegin(int begin) {
        if (isPairedGraceNoteTarget(begin)) {
            return begin - 1;
        }

        return beginIncludingKeyChangeBarline(begin);
    }

    default int effectiveEnd(int end) {
        if (isBreathMarkAt(end + 1)) {
            return end + 1;
        }

        if (isKeyChangeBehindBarline(end + 1)) {
            return end + 1;
        }

        return end;
    }

    /**
     * The range a deletion or copy of {@code range} actually covers, widened at both ends by
     * {@link #effectiveBegin} and {@link #effectiveEnd}. Every query that asks about a deletion
     * must ask about this range, not the caller's raw selection — and the ones that must are
     * typed to take the {@link EffectiveRange} only this method creates.
     *
     * @param range the elements the caller named
     * @return the widened range, whose {@link EffectiveRange#range()} always contains
     *     {@code range}
     */
    default EffectiveRange effectiveRange(ElementRange range) {
        return new EffectiveRange(
            new ElementRange(effectiveBegin(range.begin()), effectiveEnd(range.end())));
    }

    default @Nullable Integer precedingGraceNoteIndex(int index) {
        var candidateIndex = index - 1;

        if (candidateIndex < 0) {
            return null;
        }

        return getElement(candidateIndex).getType().isGraceNote() ? candidateIndex : null;
    }

    /**
     * Does not test the barline in front, so a key change that reached the line by any route
     * answers {@code true}.
     */
    default boolean isKeyChangeAt(int index) {
        return hasIndex(index) && getElement(index).getType().isKeyChange();
    }

    default boolean isBreathMarkAt(int index) {
        return hasIndex(index) && getElement(index).getType().isBreathMark();
    }

    private boolean isBarAt(int index) {
        return hasIndex(index) && getElement(index).getType().isBar();
    }

    default boolean isDurationAt(int index) {
        return hasIndex(index) && getElement(index).getType().isDuration();
    }

    default boolean hasGlissandoInto(int index) {
        return hasIndex(index - 1) && getElement(index - 1) instanceof NoteElement note
            && note.hasGlissando();
    }

    /**
     * The fit pre-check and the commit must agree on this: a disagreement accepts an edit that
     * does not fit, or reserves room for a barline that never arrives.
     *
     * @param insertionIndex the index the key change would land at, at least
     *     {@link #FIRST_LEGAL_KEY_CHANGE_INDEX}
     * @return {@code true} when the element already at {@code insertionIndex - 1} is neither a
     *     barline nor a repeat, so a {@link ElementType#SINGLE_BARLINE} is owed in front
     */
    default boolean needsBarlineBeforeKeyChangeAt(int insertionIndex) {
        return !isBarAt(insertionIndex - 1);
    }

    /**
     * Returns {@code range} narrowed to the elements that are not grace notes: the same range
     * with its ends drawn inward to the first and last non-grace element.
     *
     * <p>Grace notes are transparent to the operations that span a range of notes — a beam or a
     * tuplet reads over them without joining them — so a range read off a selection is narrowed
     * through this before its ends are examined, and a selection holding nothing but grace
     * notes has no range for those operations to act on at all.
     *
     * @param range the range to narrow; every index in it satisfies {@link #hasIndex}
     * @return the sub-range of {@code range} from its first non-grace element to its last, or
     *     {@code null} when every element in {@code range} is a grace note
     * @invariant a non-null result lies within {@code range} and holds at least one element
     */
    default @Nullable ElementRange nonGraceRange(ElementRange range) {
        var first = range.begin();

        while (first <= range.end() && getElement(first).getType().isGraceNote()) {
            first++;
        }

        if (first > range.end()) {
            return null;
        }

        var last = range.end();

        while (getElement(last).getType().isGraceNote()) {
            last--;
        }

        return new ElementRange(first, last);
    }

    private boolean isKeyChangeBarlineAt(int index) {
        return isKeyChangeBehindBarline(index + 1);
    }

    /**
     * Whether the element at {@code index} may be replaced in place by an element of type
     * {@code incoming} — clicked through with another element in note entry, which swaps the new
     * element in and keeps the index.
     *
     * <p>Two things are refused whatever {@code incoming} is, both because they are half of a
     * pair that only means anything whole: a <b>grace note</b>, which belongs to the host that
     * follows it, and either half of a <b>key change and the barline it sits behind</b> — the pair
     * rule in {@code docs/key-changes.md}, which {@link #effectiveBegin} deletes whole for the
     * same reason.
     *
     * <p>A <b>bar line</b> — an {@code incoming} whose {@link ElementType#isBar()} is true — is
     * also refused where it would stand first in the song ({@link SongStart}): at the song-start
     * slot, or on the host of a paired grace note at that slot. A replacement by anything but a
     * pitched note takes the paired grace note in front of it along, so the bar line would then
     * stand where the grace note stood; {@link #effectiveBegin} is that reach.
     *
     * <p>Says nothing about what may be <em>inserted</em> at {@code index}: a replacement takes
     * the element's place, while an insertion lands in front of it.
     * {@link #canInsertElementAt} is the insertion-side question.
     *
     * @param index    element index; out of range yields true, since nothing stands there to
     *                 protect and the caller's own bounds decide what happens next
     * @param incoming the type of the element that would take the place of the one at
     *                 {@code index}
     * @return {@code true} when note entry may write an element of type {@code incoming} over the
     *     element at {@code index}
     */
    default boolean canReplaceElementAt(int index, ElementType incoming) {
        if (!hasIndex(index)) {
            return true;
        }

        return !getElement(index).getType().isGraceNote()
            && !isKeyChangeAt(index)
            && !isKeyChangeBarlineAt(index)
            && !(incoming.isBar() && isSongStartAt(effectiveBegin(index)));
    }

    /**
     * Whether an element may be inserted in front of the element at {@code index} — the sibling
     * of {@link #canReplaceElementAt}, which answers whether the element at {@code index} may be
     * written over instead.
     *
     * <p>Every way content lands on a line by pointing at it asks this, whatever is being placed:
     * note entry, a pasted fragment and a mid-line key change alike. Two slots are refused,
     * both because they sit <em>inside</em> a pair that only means anything adjacent:
     *
     * <ul>
     *   <li>in front of a <b>mid-line key change</b>, which is the gap between it and the
     *       barline it stands behind. Anything landing there leaves the key change preceded by
     *       something other than a barline, breaking {@link KeyChangeElement}'s position
     *       invariant — see {@code docs/key-changes.md}.</li>
     *   <li>in front of the <b>host of a paired grace note</b>, which is the gap between the
     *       grace note and the note it decorates. The two mean nothing apart, so nothing goes
     *       between them.</li>
     * </ul>
     *
     * <p>Says nothing about which of the remaining slots a particular operation wants. That is
     * each operation's own rule, and it is asked separately — a key change is never the first
     * element on a line, for instance, which this does not know or care about.
     *
     * @param index the slot an element would be inserted at, so that it lands in front of the
     *     element currently at {@code index}; out of range — including
     *     {@link #effectiveElementCount()}, the slot past the last element — yields true, since
     *     nothing stands there to be split
     * @return {@code true} when an element may be inserted at {@code index}
     */
    default boolean canInsertElementAt(int index) {
        return !isKeyChangeAt(index) && !isPairedGraceNoteTarget(index);
    }

    /**
     * Whether a run of elements may be inserted in front of the element at {@code index}: the
     * rule {@link #canInsertElementAt} states for a single element, the song-start rule for the
     * run's first element, and the breath-mark placement rule for the run's two ends.
     *
     * <p><b>No bar line stands first in the song</b> ({@link SongStart}), so a run whose first
     * element is a bar line — its {@link ElementType#isBar()} is true — is refused at the
     * song-start slot, {@link #isSongStartAt}.
     *
     * <p><b>A breath mark stands only immediately after a duration element</b> — a pitched note
     * or a rest, the types whose {@link ElementType#isDuration()} is true. {@code canInsertElementAt}
     * refuses the two slots inside a pair; this rule adds the two ways a run can break that
     * invariant at its ends:
     *
     * <ul>
     *   <li>the run's <b>first</b> element is a breath mark, so it needs a duration element in
     *       front of it and no breath mark already at {@code index} — {@link #canLeadRunAt};</li>
     *   <li>a breath mark already stands at {@code index}, so the run's <b>last</b> element,
     *       which will stand in front of it, must be a duration element —
     *       {@link #canTrailRunAt}.</li>
     * </ul>
     *
     * <p>Two routes ask the parts rather than this. A paste asks each breath-mark half
     * separately, because each half has a dialog of its own to raise, and asks
     * {@link #isSongStartAt} itself, because it drops the bar lines that start its fragment
     * there rather than refusing. {@link #canInsertKeyChangeAt} asks only {@link #canTrailRunAt}
     * of the breath-mark halves: its run never begins with a breath mark, and the slot rules it
     * adds are its own — index 0 among them, so its barline never lands in the song-start slot.
     * Every other way a run lands on a line asks this.
     *
     * @param index the slot the run would be inserted at, so that its first element lands in
     *     front of the element currently at {@code index}
     * @param run the elements that would land there, in order; must not be empty
     * @return {@code true} when {@code run} may be inserted at {@code index}
     */
    default boolean canInsertRunAt(int index, List<? extends StaffElement> run) {
        if (!canInsertElementAt(index)) {
            return false;
        }

        var firstType = run.getFirst().getType();

        if (firstType.isBar() && isSongStartAt(index)) {
            return false;
        }

        if (firstType.isBreathMark() && !canLeadRunAt(index)) {
            return false;
        }

        return canTrailRunAt(index, run.getLast().getType());
    }

    default boolean canLeadRunAt(int index) {
        return isDurationAt(index - 1) && !isBreathMarkAt(index);
    }

    default boolean canTrailRunAt(int index, ElementType last) {
        return !isBreathMarkAt(index) || last.isDuration();
    }

    /**
     * Whether a mid-line key change, with the barline it stands behind, may be inserted in front
     * of the element at {@code index}.
     *
     * <p>The two slots {@link #canInsertElementAt} refuses are refused here too, and four more:
     *
     * <ul>
     *   <li><b>Index 0.</b> A {@link KeyChangeElement} is never the first element of a line —
     *       its position invariant — and a key change there would only restate what the line's
     *       header already says.</li>
     *   <li><b>Every index past the last note.</b> A key change there
     *       governs no note on this line, so it says nothing the next line's own key does not
     *       say, and the cautionary already draws that key at this line's end. The test is the
     *       last note rather than the last element, because what a key change is worth is the
     *       pitches it moves: the index of the last note is taken, and the positions before a
     *       trailing barline, a trailing rest, or the end of the line are refused alike. A run
     *       holding no note at all offers no position.</li>
     *   <li><b>The two indices flanking a key change already on the run</b>: immediately before
     *       the barline it sits behind, and immediately after it. The barline and the key change
     *       are one unit — {@link #effectiveBegin} deletes them together — so the position in
     *       front of the pair goes as well as the one behind it, or a second barline-plus-key
     *       could be dropped directly in front of the first.</li>
     *   <li><b>The index in front of a breath mark.</b> A breath mark stands only directly after
     *       a duration element, and the inserted run ends with the key change —
     *       {@link #canTrailRunAt}.</li>
     * </ul>
     *
     * <p>See {@code docs/key-changes.md} for what a mid-line key change means.
     *
     * @param index the slot the key change would be inserted at, so that it lands in front of the
     *     element currently at {@code index}; out of range yields {@code false}, since no note
     *     follows it
     * @return {@code true} when a key change may be inserted at {@code index}
     */
    default boolean canInsertKeyChangeAt(int index) {
        var lastNoteIndex = lastNoteIndex();

        if (lastNoteIndex == null) {
            return false;
        }

        return index > 0
            && index <= lastNoteIndex
            && canInsertElementAt(index)
            && !isKeyChangeAt(index - 1)
            && !isKeyChangeAt(index + 1)
            && canTrailRunAt(index, ElementType.KEY_CHANGE);
    }

    // -------------------------------------------------------------------------
    // Element queries
    // -------------------------------------------------------------------------

    default boolean isEmpty() {
        return elementCount() == 0;
    }

    private @Nullable Integer lastNoteIndex() {
        return lastIndexBefore(this::getElement, elementCount(), ElementType::isNote);
    }

    /**
     * The one definition of the span a glissando may never cover, for every caller that attaches,
     * strips, lays out or draws one. Grace notes count on either side.
     */
    default boolean isSamePitchAsFollower(int index) {
        if (index < 0 || index + 1 >= elementCount()) {
            return false;
        }

        return getElement(index) instanceof NoteElement note
            && getElement(index + 1) instanceof NoteElement follower
            && note.getPitch() == follower.getPitch();
    }

    /**
     * Does not vary by script. {@link StaffElement#canBearSyllable} states the rule.
     */
    default boolean canBearSyllableAt(int index) {
        return getElement(index).canBearSyllable(index >= 1 ? getElement(index - 1) : null);
    }

    /**
     * Returns the index of the nearest element before {@code fromIndex} that is not a grace
     * note. {@code fromIndex} itself is not examined.
     *
     * <p>Grace notes are transparent to callers reasoning about a note's real neighbors:
     * one can sit between two beamed notes without joining their beam, so the neighbor
     * that matters is the one on its far side.
     *
     * @param fromIndex the index to search back from
     * @return the nearest non-grace index below {@code fromIndex}, or {@code null} when every
     *     element before it is a grace note or nothing stands before it
     */
    default @Nullable Integer previousNonGraceIndex(int fromIndex) {
        return lastIndexBefore(this::getElement, fromIndex, type -> !type.isGraceNote());
    }

    /**
     * @param from at or above 0
     * @return {@code null} when no element from {@code from} on passes
     */
    static @Nullable Integer firstIndexFrom(
        List<? extends StaffElement> elements, int from, Predicate<ElementType> matches
    ) {
        for (var i = from; i < elements.size(); i++) {
            if (matches.test(elements.get(i).getType())) {
                return i;
            }
        }

        return null;
    }

    /**
     * @param before a position in {@code 0..elements.size()}; the element there is not examined
     * @return {@code null} when no element before {@code before} passes
     */
    static @Nullable Integer lastIndexBefore(
        List<? extends StaffElement> elements, int before, Predicate<ElementType> matches
    ) {
        return lastIndexBefore(elements::get, before, matches);
    }

    private static @Nullable Integer lastIndexBefore(
        IntFunction<? extends StaffElement> elementAt, int before, Predicate<ElementType> matches
    ) {
        for (var i = before - 1; i >= 0; i--) {
            if (matches.test(elementAt.apply(i).getType())) {
                return i;
            }
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Keys along the run
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link EffectiveRange} of every key change at or after {@code fromIndex} that
     * restates the key already in effect immediately before it — a stranded key change, which
     * cancels nothing and so draws no signature at all — each widened to the pair it belongs to,
     * so the barline it sits behind goes with it.
     *
     * <p>{@code keyInEffect} is the key that <em>will</em> be in effect at {@code fromIndex} once
     * the caller's edit commits, not what is in effect now: this query is pure and pre-mutation,
     * like every other reconciliation input, so a caller evaluates it before touching the run.
     *
     * <p>Removing one of these ranges can neither create nor hide another, and cannot change the
     * key in effect at any position — it deletes a step that steps to the value already held. So
     * a caller may compute the whole list once, before anything commits, without its own edit
     * invalidating it.
     *
     * @param fromIndex   the lowest index to count a key change at
     * @param keyInEffect the key to suppose is in effect from {@code fromIndex} onward once the
     *                    caller's edit commits
     * @return the ranges to remove, in ascending order and pairwise disjoint. The first may begin
     *     one index below {@code fromIndex}, when a key change stands at {@code fromIndex} itself
     *     and the barline it sits behind is taken with it
     * @invariant A caller deleting these itself must work from the last range to the first, since
     *     removing an earlier range shifts the indices of every later one.
     *     {@link EditableRun#deleteRanges} discharges that obligation.
     */
    default List<EffectiveRange> redundantKeyChangeRanges(int fromIndex, Key keyInEffect) {
        var ranges = new ArrayList<EffectiveRange>();
        var trackedKey = keyInEffect;

        for (var index = Math.max(fromIndex, FIRST_LEGAL_KEY_CHANGE_INDEX);
             index < effectiveElementCount(); index++) {

            if (getElement(index) instanceof KeyChangeElement keyChange) {
                // The tracked key never advances past a stranded key change, because it already
                // equals it, so one forward pass finds every one of them.
                if (keyChange.getKey() == trackedKey) {
                    ranges.add(effectiveRange(ElementRange.single(index)));
                } else {
                    trackedKey = keyChange.getKey();
                }
            }
        }

        return ranges;
    }

    /**
     * As {@link #redundantKeyChangeRanges(int, Key)} over the whole run, with {@code keyInEffect}
     * the key the run will start in once the caller's edit commits.
     *
     * <p>The bounded form is what a mid-line key edit needs instead: such an edit moves the key
     * from its own index forward and leaves the key in effect at every earlier position exactly as
     * it was, so only the key changes after it can be stranded, and the key the run starts in is
     * not what they are stranded against.
     *
     * @param keyInEffect the key this run will start in once the caller's edit commits
     * @return the ranges to remove, in ascending order and pairwise disjoint
     */
    default List<EffectiveRange> redundantKeyChangeRanges(Key keyInEffect) {
        return redundantKeyChangeRanges(FIRST_LEGAL_KEY_CHANGE_INDEX, keyInEffect);
    }

    /**
     * Indices below {@link #FIRST_LEGAL_KEY_CHANGE_INDEX} are treated as that index rather than
     * rejected.
     *
     * @return {@code null} when no key change stands at or after {@code fromIndex}, in which
     *     case the run leaves in effect whatever it started in
     */
    default @Nullable Key lastKeyChangeKeyFrom(int fromIndex) {
        var lowestIndex = Math.max(fromIndex, FIRST_LEGAL_KEY_CHANGE_INDEX);

        for (var index = elementCount() - 1; index >= lowestIndex; index--) {
            if (getElement(index) instanceof KeyChangeElement keyChange) {
                return keyChange.getKey();
            }
        }

        return null;
    }

    /**
     * Returns the key this run leaves off in when it starts in {@code runningKey}: its last key
     * change's key when it holds one, and {@code runningKey} when it does not.
     *
     * <p>A run holding a key change ends in that change's key however it began, because a key
     * change establishes an absolute key rather than transposing the one before it.
     *
     * @param runningKey the key to suppose this run begins in
     * @return the key in effect after this run's last element under that supposition
     */
    default Key keyAtEndUnder(Key runningKey) {
        return keyAtEndUnder(FIRST_LEGAL_KEY_CHANGE_INDEX, runningKey);
    }

    /**
     * As {@link #keyAtEndUnder(Key)}, counting only key changes at or after {@code fromIndex}.
     *
     * <p>The bounded form is what an edit inserting a key change at {@code fromIndex} needs: the
     * key it establishes reaches the end of the run only when no existing key change already
     * stands after it, and {@code runningKey} is then the inserted key.
     *
     * @param fromIndex  the lowest element index to count a key change at
     * @param runningKey the key to suppose is in effect from {@code fromIndex} onward
     * @return the key in effect after this run's last element under that supposition
     */
    default Key keyAtEndUnder(int fromIndex, Key runningKey) {
        var lastKeyChangeKey = lastKeyChangeKeyFrom(fromIndex);

        return lastKeyChangeKey != null ? lastKeyChangeKey : runningKey;
    }

    /**
     * The inclusive element range {@code [begin, end]} on a run. It says nothing about pairing:
     * {@link #effectiveRange} turns it into the range a deletion or a copy really covers.
     *
     * @invariant {@code 0 <= begin <= end}
     */
    record ElementRange(int begin, int end) {

        public ElementRange {
            if (begin < 0) {
                throw new IllegalArgumentException("ElementRange begin " + begin + " is negative");
            }

            if (begin > end) {
                throw new IllegalArgumentException(
                    "ElementRange begin " + begin + " is after end " + end);
            }
        }

        public static ElementRange single(int index) {
            return new ElementRange(index, index);
        }

        public int size() {
            return end - begin + 1;
        }

        public boolean contains(int index) {
            return index >= begin && index <= end;
        }

        public boolean containsExcludingBounds(int index) {
            return index > begin && index < end;
        }

        public boolean overlaps(ElementRange other) {
            return begin <= other.end() && other.begin() <= end;
        }

        public boolean contains(ElementRange other) {
            return begin <= other.begin() && other.end() <= end;
        }

        public ElementRange shiftedBy(int offset) {
            return new ElementRange(begin + offset, end + offset);
        }
    }

    /**
     * An {@link ElementRange} already widened by {@link #effectiveRange}, with no half of a pair
     * cut off at either end. The methods that delete take this type, so an unwidened range
     * cannot reach them.
     *
     * @invariant {@link #range()} is the value {@link #effectiveRange} returned for some raw
     *     range on the run that produced it, possibly moved along the run by {@link #shiftedBy}
     */
    final class EffectiveRange {

        private final ElementRange range;

        private EffectiveRange(ElementRange range) {
            this.range = range;
        }

        public ElementRange range() {
            return range;
        }

        public EffectiveRange shiftedBy(int offset) {
            return new EffectiveRange(range.shiftedBy(offset));
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof EffectiveRange that && range.equals(that.range);
        }

        @Override
        public int hashCode() {
            return range.hashCode();
        }

        @Override
        public String toString() {
            return "EffectiveRange" + range;
        }
    }

    /**
     * Where a run of elements is inserted: at a plain index, or in place of a range this
     * insertion deletes first.
     */
    sealed interface InsertionTarget {

        int insertIndex();

        /**
         * @return {@code null} for {@link AtIndex}
         */
        @Nullable EffectiveRange deleteRange();

        record AtIndex(int index) implements InsertionTarget {

            @Override
            public int insertIndex() {
                return index;
            }

            @Override
            public @Nullable EffectiveRange deleteRange() {
                return null;
            }
        }

        /**
         * @invariant {@link #insertIndex()} is {@code range.range().begin()}
         */
        record Replacing(EffectiveRange range) implements InsertionTarget {

            @Override
            public int insertIndex() {
                return range.range().begin();
            }

            @Override
            public EffectiveRange deleteRange() {
                return range;
            }
        }
    }
}
