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

import org.jspecify.annotations.Nullable;

/**
 * A run of {@link StaffElement}s in order, and the rules that decide which of them mean nothing
 * apart.
 *
 * <h2>Pairing</h2>
 *
 * <p>Some elements cannot stand without a neighbor, so a range naming one of them really covers
 * both. There are three pairs, and they are not symmetric:
 *
 * <ul>
 *   <li>a <b>paired grace note</b> cannot outlive the note it decorates, so a range beginning at
 *       a host reaches back over it;</li>
 *   <li>a <b>key change</b> cannot stand without the barline or repeat in front of it — see
 *       {@link KeyChangeElement}'s position invariant — so a range reaches back over that barline,
 *       and forward over a key change whose barline the range already ends at;</li>
 *   <li>a <b>breath mark</b> is positionally attached to the element before it, so a range reaches
 *       forward over one.</li>
 * </ul>
 *
 * <p>Every query that asks what a deletion or a copy really covers asks {@link #effectiveRange},
 * so the pairing has exactly one definition. A caller that reimplements it — dropping "the index
 * before" on the assumption it holds a barline, say — is a second definition that will not be
 * updated when a fourth pair is added.
 *
 * <h2>Why this is an interface rather than part of {@link Line}</h2>
 *
 * <p>These rules were {@code Line}'s, and a line is still the run they mostly operate on. But a
 * clipboard {@link songscribe.ui.clipboard.Fragment} is a run of elements too, holding the same
 * pairs and needing the same answer about them — a fragment that dropped a key change and kept
 * its barline, or dropped a barline that was never one, would be reasoning about pairs by a rule
 * of its own. It simply has no line, and no undo history to record into.
 *
 * <p>{@link LyricRun} extends this because a run carrying lyric chains is a run of elements
 * first, so {@link Line} and {@link DetachedLyricRun} answer for both without implementing
 * anything twice.
 *
 * <h2>Keys along a run</h2>
 *
 * <p>A run also answers where its key changes stand and what they leave in effect, because both
 * questions are asked of a fragment that has not landed on a line as well as of a committed line.
 * The two differ only in what each index takes with it when it goes, which is the pairing above.
 */
public interface StaffElementRun {

    /**
     * The lowest element index a {@link KeyChangeElement} can occupy. Index 0 is forbidden by that
     * class's position invariant — a key change always follows a barline or repeat — so every scan
     * for one stops here rather than at 0.
     */
    int FIRST_LEGAL_KEY_CHANGE_INDEX = 1;

    // -------------------------------------------------------------------------
    // What a run must answer for itself
    // -------------------------------------------------------------------------

    /** The element at {@code index}. */
    StaffElement getElement(int index);

    /** The number of elements in this run. */
    int elementCount();

    /**
     * The number of elements the run's own content occupies — {@link #elementCount()} less
     * any trailing element the run maintains for itself rather than holding as content
     * (a {@link Line}'s auto-maintained terminal barline).
     */
    int effectiveElementCount();

    /**
     * Whether {@code index} addresses an element of this run, and so may be passed to
     * {@link #getElement}.
     */
    default boolean hasIndex(int index) {
        return index >= 0 && index < elementCount();
    }

    /**
     * Returns the note at {@code index}, for a caller whose index is known to name one: a beam
     * member, the owner of a slide, the anchor of a pitch shift. The question a caller asks when
     * it already knows the answer's kind and wants the element narrowed rather than tested.
     *
     * @param index an index of this run
     * @return the element at {@code index}, as a note
     * @throws IllegalStateException if the element at {@code index} is not a note
     */
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
     * Whether the element at {@code index} is a grace note connected to the note that
     * follows it, {@code false} when {@code index} is outside the run.
     *
     * <p>Says nothing about whether the following note is present: a pair whose host lies
     * past the end of the run still reads as paired, which is what lets
     * {@link songscribe.ui.clipboard.Fragment#capture} recognize the orphan it has to trim.
     */
    default boolean isPairedGraceNote(int index) {
        return hasIndex(index) && getElement(index) instanceof NoteElement note && note.isPairedGraceNote();
    }

    /** Whether the element at {@code index} is the host of a paired grace note. */
    default boolean isHostOfPairedGraceNote(int index) {
        return index >= 1 && isPairedGraceNote(index - 1);
    }

    /**
     * Whether the element at {@code index} is a key change sitting behind the barline or
     * repeat immediately before it — the one bidirectional pair in a run, since a key change
     * belongs at the head of a measure (see {@link KeyChangeElement}'s position invariant).
     * <p>
     * The barline is tested rather than assumed. The invariant makes the test hold for every
     * key change a document of this program's own making can contain, and re-testing it
     * here is what keeps the widening from reaching past a key change that arrived any
     * other way.
     *
     * @param index element index; out of range, or 0, yields false — index 0 has nothing
     *     before it to pair with
     * @return {@code true} when {@code index} and {@code index - 1} are such a pair
     */
    default boolean isKeyChangeBehindBarline(int index) {
        if (index < 1 || index >= effectiveElementCount()) {
            return false;
        }

        if (!getElement(index).getType().isKeyChange()) {
            return false;
        }

        var precedingType = getElement(index - 1).getType();

        return precedingType.isBar();
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
     * The first element a range beginning at {@code begin} really covers — see the pairing rule
     * in this interface's Javadoc. Two elements before {@code begin} belong to it: a paired grace
     * note, which cannot outlive its host, and the barline a key change at {@code begin}
     * sits behind. Pure query — mutates nothing.
     * <p>
     * <b>The barline case is a decision, not a symmetry.</b> A key change cannot outlive its
     * barline, but a barline can outlive its key: deleting the key alone would leave a valid
     * run. The pair goes whole so that a barline the insertion flow added only to host a key
     * does not linger once the key is gone. The cost is the other case — a barline the user
     * placed themselves goes with the key that happened to follow it, merging two measures.
     * The notator is not prompted about that: {@code Selection.Range.contains} widens over the
     * same pairs, so selecting either half draws both as selected and the deletion takes what
     * was already shown going. Neither half of that reasoning is redundant; the backward
     * extension is not derivable from the forward one.
     * <p>
     * This is also why no deletion can leave a key change at index 0: reaching index 0 takes
     * deleting the barline in front of it, which carries the key change along. A separate
     * index-0 guard would be dead code.
     *
     * @param begin the first element the caller named
     * @return {@code begin} extended back over the element paired with it, or {@code begin}
     *     unchanged when nothing before it is paired with it; never greater than {@code begin}
     */
    default int effectiveBegin(int begin) {
        if (isHostOfPairedGraceNote(begin)) {
            return begin - 1;
        }

        return beginIncludingKeyChangeBarline(begin);
    }

    /**
     * The last element a range ending at {@code end} really covers — see the pairing rule in
     * this interface's Javadoc. Two elements after {@code end} belong to it: a breath mark, which
     * is positionally attached to the element before it, and a key change whose barline is
     * the element at {@code end}. Pure query — mutates nothing.
     *
     * @param end the last element the caller named
     * @return {@code end} extended past the element paired with it, or {@code end} unchanged
     *     when nothing after it is paired with it; never less than {@code end}
     */
    default int effectiveEnd(int end) {
        if (end + 1 < effectiveElementCount() && getElement(end + 1).getType().isBreathMark()) {
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
     * must ask about this range, not the caller's raw selection.
     *
     * @param range the elements the caller named
     * @return the widened range, which always contains {@code range}
     */
    default ElementRange effectiveRange(ElementRange range) {
        return new ElementRange(effectiveBegin(range.begin()), effectiveEnd(range.end()));
    }

    /**
     * Returns whether inserting at the given index would conflict with a paired grace note.
     * A grace note is paired when it has a connecting {@link Slide#GLISSANDO}
     * linking it to the following note.
     * <p>
     * This returns {@code true} in two cases:
     * <ul>
     *   <li>The element at {@code index} is itself a paired grace note (clicking on it)</li>
     *   <li>The element at {@code index - 1} is a paired grace note (clicking between it and its host)</li>
     * </ul>
     */
    default boolean isInsideGraceHostPair(int index) {
        return isPairedGraceNote(index) || isPairedGraceNote(index - 1);
    }

    /**
     * If the element immediately before {@code index} is a grace note, returns its index.
     * Returns -1 otherwise.
     */
    default int precedingGraceNoteIndex(int index) {
        var candidateIndex = index - 1;

        if (candidateIndex < 0) {
            return -1;
        }

        return getElement(candidateIndex).getType().isGraceNote() ? candidateIndex : -1;
    }

    /**
     * Whether the element at {@code index} is a mid-line key change.
     *
     * <p>The barline in front of it is not tested. This reports what stands at {@code index}, so a
     * key change that reached the line by any route answers {@code true} — testing for the
     * barline would make a refusal depend on the pair rule in {@code docs/key-changes.md}
     * rather than help keep it.
     *
     * @param index element index; out of range yields false
     * @return {@code true} when a {@link KeyChangeElement} stands at {@code index}
     */
    default boolean isKeyChangeAt(int index) {
        return hasIndex(index) && getElement(index).getType().isKeyChange();
    }

    /**
     * Whether the element at {@code index} is the barline or repeat a key change sits behind —
     * the same pair {@link #isKeyChangeAt} names from the key change's side, seen from the
     * barline's.
     *
     * @param index element index; out of range yields false
     * @return {@code true} when a key change stands at {@code index + 1} and {@code index}
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
     * <b>key change and the barline it sits behind</b> — the pair rule in
     * {@code docs/key-changes.md}, which {@link #effectiveBegin} deletes whole for the same
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
    default boolean canReplaceElementAt(int index) {
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
        return !isKeyChangeAt(index) && !isHostOfPairedGraceNote(index);
    }

    // -------------------------------------------------------------------------
    // Element queries
    // -------------------------------------------------------------------------

    /** Whether this run holds no elements. */
    default boolean isEmpty() {
        return elementCount() == 0;
    }

    /**
     * The index of the last note on this line.
     *
     * <p>Answers how far along a line an edit can still govern a pitch. A key change past this
     * index reaches no note, so it says nothing the next line's own key does not say, and the
     * cautionary already draws that key at this line's end. See {@code docs/key-changes.md}.
     *
     * <p>The scan needs no bound against {@link #effectiveElementCount()}: an auto-maintained
     * terminal is a barline or a repeat, so a scan for a note stops before it either way.
     *
     * @return the last note's element index, or -1 when this line holds no note
     * @invariant the result is always below {@link #effectiveElementCount()}
     */
    default int lastNoteIndex() {
        for (var scanIndex = elementCount() - 1; scanIndex >= 0; scanIndex--) {
            if (getElement(scanIndex).getType().isNote()) {
                return scanIndex;
            }
        }

        return -1;
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
    default boolean isSamePitchAsFollower(int index) {
        if (index < 0 || index + 1 >= elementCount()) {
            return false;
        }

        return getElement(index) instanceof NoteElement note
            && getElement(index + 1) instanceof NoteElement follower
            && note.getPitch() == follower.getPitch();
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
    default boolean canBearSyllableAt(int index) {
        return getElement(index).canBearSyllable(index >= 1 ? getElement(index - 1) : null);
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
    default int nearestNonGraceIndex(int fromIndex, int step) {
        for (var i = fromIndex + step; hasIndex(i); i += step) {
            if (!getElement(i).getType().isGraceNote()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns true if any element in {@code range} is a repeat or a barline other than
     * {@link ElementType#SINGLE_BARLINE}.
     * <p>
     * No bounds check is performed; callers own their indices.
     */
    default boolean spansStructuralBoundary(ElementRange range) {
        for (var i = range.begin(); i <= range.end(); i++) {
            var type = getElement(i).getType();

            if (type.isBar() && type != ElementType.SINGLE_BARLINE) {
                return true;
            }
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Keys along the run
    // -------------------------------------------------------------------------

    /**
     * Returns the index of every key change at or after {@code fromIndex} that restates the key
     * already in effect where it stands — a stranded key change, which cancels nothing and so
     * draws no signature at all.
     *
     * <p>The tracked key is never advanced past a stranded key change, because it already equals
     * it. One forward pass therefore suffices, and removing one of them can neither create nor
     * hide another: a key change that restates the key in effect cannot change the key in effect
     * at any position.
     *
     * @param fromIndex   the lowest index to count a key change at
     * @param keyInEffect the key in effect at {@code fromIndex}
     * @return the indices, ascending
     */
    default List<Integer> strandedKeyChangeIndices(int fromIndex, Key keyInEffect) {
        var indices = new ArrayList<Integer>();
        var trackedKey = keyInEffect;

        for (var index = Math.max(fromIndex, FIRST_LEGAL_KEY_CHANGE_INDEX);
             index < effectiveElementCount(); index++) {

            if (getElement(index) instanceof KeyChangeElement keyChange) {
                if (keyChange.getKey() == trackedKey) {
                    indices.add(index);
                } else {
                    trackedKey = keyChange.getKey();
                }
            }
        }

        return indices;
    }

    /**
     * Returns the {@link ElementRange} of every key change at or after {@code fromIndex} that
     * restates the key already in effect immediately before it, each widened to the pair it
     * belongs to, so the barline it sits behind goes with it.
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
    default List<ElementRange> redundantKeyChangeRanges(int fromIndex, Key keyInEffect) {
        return strandedKeyChangeIndices(fromIndex, keyInEffect).stream()
            .map(index -> effectiveRange(ElementRange.single(index)))
            .toList();
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
    default List<ElementRange> redundantKeyChangeRanges(Key keyInEffect) {
        return redundantKeyChangeRanges(FIRST_LEGAL_KEY_CHANGE_INDEX, keyInEffect);
    }

    /**
     * Returns the key the last key change at or after {@code fromIndex} establishes — which is
     * what this run leaves in effect, whenever it holds a key change of its own.
     *
     * <p>A stranded key change restates the key running into it, so it gives the same answer as
     * whatever precedes it: this needs no knowledge of {@link #strandedKeyChangeIndices}, and the
     * two may be asked in either order.
     *
     * <p>The bounded form is what an edit inserting a key change at {@code fromIndex} needs: the
     * key the run leaves off in afterwards is the inserted one <em>only if</em> no key change
     * already stands after the insertion point, and this is the question that decides it.
     *
     * <p>Indices below {@link #FIRST_LEGAL_KEY_CHANGE_INDEX} are treated as that index rather
     * than rejected, because no key change can stand before it.
     *
     * @param fromIndex the lowest index to count a key change at
     * @return the last key change's key, or {@code null} when no key change stands at or after
     *     {@code fromIndex}, in which case the run leaves in effect whatever it started in
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
     * Returns the key the last key change anywhere in this run establishes.
     *
     * <p>Null says the run changes key nowhere along its length, so the key it leaves off in is
     * whatever it started in. That distinction is what a caller projecting an edit needs: under a
     * <em>hypothetical</em> starting key, a run with a key change still ends in that change's key,
     * and a run without one ends in the hypothetical.
     *
     * @return the last key change's key, or {@code null} when this run holds no key change
     */
    default @Nullable Key lastKeyChangeKey() {
        return lastKeyChangeKeyFrom(FIRST_LEGAL_KEY_CHANGE_INDEX);
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
     * The inclusive element range {@code [begin, end]} on a run: the one type for a contiguous
     * stretch of elements named by index, so no caller passes the pair as two ints it can
     * transpose.
     *
     * <p>It says nothing about pairing. A range read off a selection may cut a grace note from
     * its host or a key change from its barline; {@link #effectiveRange} is what turns it into
     * the range a deletion or a copy really covers, and a method that needs the widened range
     * says so in its own contract.
     *
     * @param begin the index of the first element in the range
     * @param end   the index of the last element in the range, inclusive
     * @invariant {@code begin <= end}, so every range holds at least one element
     */
    record ElementRange(int begin, int end) {

        /**
         * @throws IllegalArgumentException if {@code begin > end}
         */
        public ElementRange {
            if (begin > end) {
                throw new IllegalArgumentException(
                    "ElementRange begin " + begin + " is after end " + end);
            }
        }

        /**
         * Returns the range holding only the element at {@code index}.
         *
         * @param index the index of the one element
         * @return the range {@code [index, index]}
         */
        public static ElementRange single(int index) {
            return new ElementRange(index, index);
        }

        /**
         * Returns how many elements the range holds.
         *
         * @return {@code end - begin + 1}, never less than 1
         */
        public int size() {
            return end - begin + 1;
        }

        /**
         * Returns whether {@code index} falls inside the range.
         *
         * @param index the element index to test
         * @return {@code true} when {@code begin <= index <= end}
         */
        public boolean contains(int index) {
            return index >= begin && index <= end;
        }

        /**
         * Returns whether this range and {@code other} share at least one element.
         *
         * @param other the range to test against
         * @return {@code true} when {@code begin <= other.end() && other.begin() <= end}
         */
        public boolean overlaps(ElementRange other) {
            return begin <= other.end() && other.begin() <= end;
        }

        /**
         * Returns this range moved {@code offset} places along the run — what a range read off
         * the run before an edit becomes once that edit has inserted or removed {@code offset}
         * elements ahead of it.
         *
         * @param offset how far the elements in this range moved; negative when they moved back
         * @return the range at their new indices
         */
        public ElementRange shiftedBy(int offset) {
            return new ElementRange(begin + offset, end + offset);
        }
    }
}
