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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.message.mutation.ElementField;
import songscribe.util.LogUtils;

/**
 * A run of {@link StaffElement}s carrying lyric chains, and every rule for keeping those
 * chains consistent as the run is edited.
 *
 * <p>Two chains run through a verse's lyrics, and both are stored on the individual
 * syllables rather than as spans, so both have to be repaired element by element whenever
 * an edit changes who a syllable's neighbors are:
 *
 * <ul>
 *   <li>the <b>syllabic chain</b> — {@link Lyric.Syllabic} plus {@link Lyric#compound()},
 *       which say whether a syllable continues into the next one (and so whether a hyphen
 *       is drawn between them);</li>
 *   <li>the <b>melisma chain</b> — {@link Lyric.Extend}, a {@code START} followed by
 *       text-less {@code CONTINUE} carriers and closed by a {@code STOP}.</li>
 * </ul>
 *
 * <p>A chain member points at its neighbors implicitly, by position, so a chain that leaves
 * the run is a chain pointing at nothing: the syllable still says "hyphen to the next one"
 * and finds whatever happens to sit there.
 *
 * <h2>Why this is an interface rather than part of {@link Line}</h2>
 *
 * <p>These rules were {@code Line}'s, and a line is still the run they mostly operate on.
 * But a clipboard {@link songscribe.ui.clipboard.Fragment} is a run of elements with lyrics
 * too — the same syllables, carrying the same two chains, needing the same repair when the
 * run's ends stop being where they were. It simply has no line, and no undo history to
 * record into.
 *
 * <p>Reaching an element and counting the run are {@link StaffElementRun}'s, which this
 * extends: a run carrying lyric chains is a run of elements first. One thing more differs
 * between the two, so it is all that is abstract here — what to do with a repair once it has
 * been applied. {@link Line} answers it by recording an {@code ElementModification} so the
 * repair can be undone; {@link DetachedLyricRun} answers it by doing nothing, because a
 * detached run is not part of any document. Everything else is shared, so the repair a
 * fragment gets is the same code an edit gets, not a second implementation of the same rules.
 *
 * <p><b>Mutation contract:</b> every repair here goes through
 * {@link #modifyElement(int, ElementField, Runnable)}, and on a {@code Line} that method
 * must be called inside an open modification bracket. The per-method "must be called inside
 * a modification bracket" notes below are that requirement, and they bind every caller
 * holding a {@code Line}. A {@link DetachedLyricRun} has no bracket to be inside.
 */
public interface LyricRun extends StaffElementRun {

    Logger LOG = LoggerFactory.getLogger(LyricRun.class);

    // -------------------------------------------------------------------------
    // What a run must answer for itself
    // -------------------------------------------------------------------------

    /**
     * Applies {@code mutator} to the element at {@code index} and does whatever the run
     * requires to make that change recoverable — for a {@link Line}, recording an
     * {@code ElementModification} in the open modification bracket.
     */
    void modifyElement(int index, ElementField field, Runnable mutator);

    // -------------------------------------------------------------------------
    // Chain repair
    // -------------------------------------------------------------------------

    /**
     * Settles, in every verse, the word and the melisma that reach the gap at {@code gapIndex}:
     * the position between what stands before it and what stands at or after it.
     *
     * <p>A chain may cross the gap only where the caller says so. Where it may not, the word
     * before the gap ends there, the syllable after it begins a word, the melisma before it
     * closes on its last member, and the carriers after it go. Where it may, a chain whose two
     * sides both still stand is left as it is, and a side left without its other side is settled
     * as above. An element that holds no lyric for a verse is no neighbor in that verse, so the
     * sides are found past any rest, barline, breath mark or key change.
     *
     * <p>Must be called inside a modification bracket.
     *
     * @param gapIndex the index of the first element after the gap
     * @param versesThatMayCross the verses whose chains may run across the gap
     */
    private void settleChainsAcross(int gapIndex, Predicate<Integer> versesThatMayCross) {
        for (var verse : verseNumbers()) {
            var mayCross = versesThatMayCross.test(verse);

            settleMelismaAcross(gapIndex, verse, mayCross);
            settleWordAcross(gapIndex, verse, mayCross);
        }
    }

    private void settleMelismaAcross(int gapIndex, int verse, boolean mayCross) {
        var beforeIndex = previousLyricBearingIndex(gapIndex, verse);
        var afterIndex = lyricBearingIndexAtOrAfter(gapIndex, verse);
        var beforeLyric = beforeIndex == null ? null : getElement(beforeIndex).getLyricForVerse(verse);
        var afterLyric = afterIndex == null ? null : getElement(afterIndex).getLyricForVerse(verse);
        var isOpenBefore = beforeLyric != null
            && (beforeLyric.extend() == Lyric.Extend.START || beforeLyric.extend() == Lyric.Extend.CONTINUE);
        var isCarriedAfter = afterLyric != null && afterLyric.isCarrier();
        var crosses = mayCross && isOpenBefore && isCarriedAfter;

        if (crosses) {
            return;
        }

        if (isOpenBefore && beforeIndex != null) {
            closeMelismaAt(beforeIndex, verse);
        }

        if (isCarriedAfter && afterIndex != null) {
            removeCarriersFrom(afterIndex, verse);
        }
    }

    private void settleWordAcross(int gapIndex, int verse, boolean mayCross) {
        var beforeIndex = previousTextBearingIndex(gapIndex, verse);

        if (beforeIndex != null) {
            var beforeLyric = getElement(beforeIndex).getLyricForVerse(verse);
            var crosses = mayCross && hasFollowingTextBearingLyric(beforeIndex, verse);

            if (beforeLyric != null && isWordContinuingLyric(beforeLyric) && !crosses) {
                setSyllableBoundary(beforeIndex, verse, true, false);
            }
        }

        fixSuccessorSyllabic(gapIndex - 1, verse, previousLyricContinues(gapIndex, verse));
    }

    /**
     * Breaks the lyric chains that the run about to be inserted at {@code insertionIndex}
     * interrupts: in every verse, the word and the melisma that run across the position. The
     * word before it ends there and the syllable after it begins a word; the melisma closes on
     * its last member before it and the carriers after it go. Must be called inside a
     * modification bracket, <em>before</em> {@link Line#addElement(int, StaffElement)}, while the
     * pre-insertion indices still hold.
     *
     * <p>A run every element of which carries the chains onward without ever taking a syllable —
     * a barline, a breath mark, a key change, a rest — breaks nothing and is left alone. That is
     * {@link ElementType#interruptsLyricChain()}'s answer, and the run is judged by it as a
     * whole: one interrupting element in it breaks the chains for the run.
     *
     * <p>Only the lyric half of the repair belongs here. The glissando half runs after the
     * insertion — see {@link #repairNeighborsAfterInsertion} — so an insertion path calls
     * this, inserts, then calls that. A path that inserts first and records later wants
     * {@link #repairNeighborsAfterUntrackedInsertion} instead, which does both halves in
     * post-insertion indices.
     *
     * @param insertionIndex the index the first element of the run will land at
     * @param insertedRun the run about to be inserted, in the order it lands; must not be empty
     */
    default void repairNeighborsBeforeInsertion(int insertionIndex, List<? extends StaffElement> insertedRun) {
        if (!interruptsLyricChains(insertedRun)) {
            return;
        }

        settleChainsAcross(insertionIndex, verse -> false);
    }

    /**
     * Repairs the neighbors of a single element just inserted at {@code insertedIndex}.
     *
     * @param insertedIndex the index the element landed at
     * @param inserted the element that landed there
     */
    default void repairNeighborsAfterInsertion(int insertedIndex, StaffElement inserted) {
        repairNeighborsAfterInsertion(insertedIndex, List.of(inserted));
    }

    /**
     * Strips the glissando that reaches the element at {@code index} from the note in front of
     * it, when there is one — the repair {@link #hasGlissandoInto} is the question for. Does
     * nothing when no glissando arrives at {@code index}.
     *
     * <p>Must be called from inside a {@link Song#withModification(Runnable)} bracket.
     *
     * @param index the index of the element the glissando arrives at; any value
     *     {@link #hasGlissandoInto} accepts
     * @effects when {@code hasGlissandoInto(index)}, removes the slide from the note at
     *     {@code index - 1} through one {@link #modifyElement}; an implementor that records gets
     *     one record
     */
    default void removeGlissandoInto(int index) {
        if (hasGlissandoInto(index) && getElement(index - 1) instanceof NoteElement note) {
            modifyElement(index - 1, ElementField.SLIDE, note::removeSlide);
        }
    }

    /**
     * Whether inserting {@code run} breaks the lyric chains that ran across the position it
     * lands at. One interrupting element is enough: the chains cannot pass through a run that
     * holds a syllable slot arriving empty, or a repeat.
     *
     * @param run the elements being inserted, in any order; must not be empty
     * @return {@code true} when the run breaks a word or a melisma spanning its position
     */
    private static boolean interruptsLyricChains(List<? extends StaffElement> run) {
        return run.stream().anyMatch(element -> element.getType().interruptsLyricChain());
    }

    /**
     * Repairs what the run of elements just inserted at {@code firstInsertedIndex} could only
     * disturb once it was in the list: a connecting glissando on the predecessor left with no
     * valid target. The lyric half of the repair runs before the insertion — see
     * {@link #repairNeighborsBeforeInsertion}.
     *
     * <p>The glissando is broken by the <em>first</em> element of the run, because that is the
     * one now standing behind the predecessor. It does not read the rule the lyric chains read:
     * it is about which element a pairing points at, not about who a syllable's neighbors are,
     * and a barline standing between a note and its glissando target orphans it however
     * transparent it is to a word.
     *
     * <p>Must be called inside a modification bracket, <em>after</em> every
     * {@link Line#addElement(int, StaffElement)} of the run.
     *
     * @param firstInsertedIndex the index the first element of the run landed at
     * @param insertedRun the run that landed there, in the order it landed; must not be empty
     */
    default void repairNeighborsAfterInsertion(int firstInsertedIndex, List<? extends StaffElement> insertedRun) {
        // A connecting glissando joins a note to the note that immediately follows it.
        // Inserting another pitched note simply re-targets it, but inserting anything else
        // (rest, breath mark, grace note, barline, key change) leaves it with no valid
        // target, so remove it from the preceding note.
        if (!getElement(firstInsertedIndex).getType().isPitchedNote()) {
            removeGlissandoInto(firstInsertedIndex);
        }
    }

    /**
     * Repairs the neighbors of the element already inserted at {@code insertedIndex}, doing
     * entirely in post-insertion indices what an insertion path does on both sides of
     * {@link Line#addElement(int, StaffElement)}, so a caller that inserted the element earlier can
     * record the repairs later.
     *
     * <p>Grace mode is that caller: it inserts its grace note with mutation tracking suspended
     * (a lone grace note is not undoable), where a repair would be applied but never recorded,
     * leaving undo unable to put back the broken chain or the stripped glissando. It defers the
     * repairs to the bracket that makes the grace-host pairing undoable and calls this from
     * there.
     *
     * <p>Must be called inside a modification bracket. The repairs are recorded after the
     * insertion they belong to, which is what reverse-order undo needs: their indices only
     * hold while the inserted element is in the list.
     */
    default void repairNeighborsAfterUntrackedInsertion(int insertedIndex) {
        var insertedRun = List.of(getElement(insertedIndex));

        if (interruptsLyricChains(insertedRun)) {
            // The inserted element holds no lyric yet, so the gap it opened is found on either
            // side of it from its own index.
            settleChainsAcross(insertedIndex, verse -> false);
        }

        repairNeighborsAfterInsertion(insertedIndex, insertedRun);
    }

    /**
     * The verses in which a word ends inside {@code range}: some syllable there is the last of
     * its word. Across such a range, what stands before it and what stands after it are
     * different words, so no word may close up over it once it is gone.
     *
     * @param range the elements about to be deleted
     * @return the verse numbers, in no particular order
     */
    default Set<Integer> versesWithWordEndIn(ElementRange range) {
        var verses = new HashSet<Integer>();

        for (var index = range.begin(); index <= range.end(); index++) {
            for (var lyric : getElement(index).getLyrics()) {
                if (!lyric.isCarrier() && !isWordContinuingLyric(lyric)) {
                    verses.add(lyric.verse());
                }
            }
        }

        return verses;
    }

    /**
     * Repairs the lyric chains around a range that has just been removed, so that in every verse
     * the syllables that remain form whole words and whole melismas:
     *
     * <ul>
     *   <li>a word that lost syllables closes up around the gap, and a word left without its
     *       beginning or its end begins or ends on the syllable that remains;</li>
     *   <li>a melisma that lost its syllable loses its carriers too; one that lost only carriers
     *       closes on the last member that remains, and one left with no carrier is an ordinary
     *       syllable.</li>
     * </ul>
     *
     * <p>Must be called inside a modification bracket, <em>after</em> the removal. The repairs
     * are recorded after the removal they belong to, which is what reverse-order undo needs:
     * their indices hold only while the range is out of the list.
     *
     * @param gapIndex the index the removed range began at
     * @param versesWithWordEnd what {@link #versesWithWordEndIn} answered for the range before
     *     it was removed
     */
    default void repairChainsAfterDeletion(int gapIndex, Set<Integer> versesWithWordEnd) {
        settleChainsAcross(gapIndex, verse -> !versesWithWordEnd.contains(verse));
    }

    /**
     * Removes the melisma carriers found from {@code startIndex} on, through the close of their
     * chain. An element holding no {@code verse} lyric is passed over, since a melisma runs
     * through a rest or a barline; the first syllable ends the walk.
     */
    private void removeCarriersFrom(int startIndex, int verse) {
        for (var i = startIndex; i < effectiveElementCount(); i++) {
            var element = getElement(i);
            var lyric = element.getLyricForVerse(verse);

            if (lyric == null) {
                continue;
            }

            if (!lyric.isCarrier()) {
                break;
            }

            // A carrier has no text of its own, so clearing its extend would strand an empty
            // lyric — one that still counts as lyric-bearing for lyric navigation. Drop the
            // entry instead.
            modifyElement(i, ElementField.LYRIC, () -> element.removeLyricForVerse(verse));

            if (lyric.extend() == Lyric.Extend.STOP) {
                break;
            }
        }
    }

    /**
     * Makes the melisma member at {@code index} the last of its chain: a carrier there becomes
     * the close, and a syllable there loses its extender, since one note carries no melisma.
     * No-op when the {@code verse} lyric at {@code index} is absent or is not an open member.
     */
    private void closeMelismaAt(int index, int verse) {
        var lyric = getElement(index).getLyricForVerse(verse);

        if (lyric == null) {
            return;
        }

        var existingExtend = lyric.extend();

        if (existingExtend != Lyric.Extend.CONTINUE && existingExtend != Lyric.Extend.START) {
            return;
        }

        var newExtend = existingExtend == Lyric.Extend.CONTINUE ? Lyric.Extend.STOP : Lyric.Extend.NONE;

        setExtendForVerse(index, verse, newExtend);
    }

    /**
     * Normalizes every {@link Lyric} in this run so that {@link Lyric#syllabic()} is
     * chain-consistent (SINGLE/BEGIN/MIDDLE/END match the word boundary implied by neighbors).
     * Idempotent. Used after document load for legacy files where {@code <syllabic>} may be
     * absent or only a best-guess value.
     */
    default void backfillSyllabic() {
        for (var index = 0; index < elementCount(); index++) {
            // Snapshot the verses first: backfillSyllabicAt writes the element's lyrics.
            for (var verse : verseNumbersOf(getElement(index))) {
                backfillSyllabicAt(index, verse);
            }
        }
    }

    /**
     * Updates the lyric at {@code (index, verse)} so its {@link Lyric#syllabic()} is consistent
     * with the syllabic chain. BEGIN/MIDDLE on the predecessor means this element is in the middle
     * or end of a word; BEGIN on this element (from an initial best-guess load) means it continues
     * to the next. No-op when the element has no lyric for the verse, the lyric is a carrier, or
     * the stored value already matches. Writes the element directly, outside any bracket — the
     * caller is responsible for any required modification bracket.
     */
    private void backfillSyllabicAt(int index, int verse) {
        var element = getElement(index);
        var lyric = element.getLyricForVerse(verse);

        if (lyric == null) {
            return;
        }

        var extend = lyric.extend();

        // Carriers never need backfill — their syllabic is always null.
        if (extend == Lyric.Extend.STOP || extend == Lyric.Extend.CONTINUE) {
            return;
        }

        // The nearest earlier lyric of any kind, carrier included: a carrier's syllabic is
        // null, and syllabicContinues reads that as "does not continue", which is the answer
        // a melisma running into this syllable calls for.
        var prevIndex = previousLyricBearingIndex(index, verse);
        var prevLyric = prevIndex == null ? null : getElement(prevIndex).getLyricForVerse(verse);
        var prevContinues = prevLyric != null && Lyric.syllabicContinues(prevLyric.syllabic());

        // BEGIN was assigned by best-guess load to signal "this syllable continues to the next".
        var derived = deriveSyllabic(prevContinues, Lyric.syllabicContinues(lyric.syllabic()));

        if (derived == lyric.syllabic()) {
            return;
        }

        element.putLyric(new Lyric(lyric.verse(), lyric.text(), lyric.extend(), derived, lyric.compound()));
    }

    /**
     * Adjusts the syllable boundary on the lyric at {@code (index, verse)} and propagates the
     * change to the next lyric-bearing element so its {@link Lyric.Syllabic} stays consistent
     * with the chain. The lyric at {@code index} must already exist and must not be a carrier
     * ({@link Lyric.Extend#STOP} / {@link Lyric.Extend#CONTINUE}); typical use is right after a
     * text write to fix up the syllabic chain.
     *
     * <p>The new {@link Lyric#syllabic()} on this element is derived from {@code isWordEnd} and
     * the previous lyric-bearing element's syllabic position:
     * <ul>
     *   <li>{@code isWordEnd} → {@link Lyric.Syllabic#END} when the predecessor continues
     *       (BEGIN/MIDDLE), else {@link Lyric.Syllabic#SINGLE}.</li>
     *   <li>{@code !isWordEnd} → {@link Lyric.Syllabic#MIDDLE} when the predecessor continues,
     *       else {@link Lyric.Syllabic#BEGIN}. {@link Lyric#compound()} is set to
     *       {@code isCompound}.</li>
     * </ul>
     *
     * <p>The next lyric-bearing element (if it has a non-null syllabic) is rewritten to whichever
     * of BEGIN/MIDDLE/END/SINGLE matches its own continues-status combined with this element's
     * new continues-status. Element rewrites that would not actually change the stored value are
     * skipped to avoid emitting redundant {@code ElementModification}s.
     *
     * <p>Must be called from inside a {@link Song#withModification(Runnable)} bracket.
     *
     * @param index      the element index in this run
     * @param verse      the verse number (typically 1)
     * @param isWordEnd  {@code true} when this syllable terminates the word (no continuation)
     * @param isCompound {@code true} when this syllable joins the next via a compound-word
     *                   boundary; ignored when {@code isWordEnd} is {@code true}
     */
    default void setSyllableBoundary(int index, int verse, boolean isWordEnd, boolean isCompound) {
        var lyric = getElement(index).getLyricForVerse(verse);

        if (lyric == null) {
            throw new IllegalStateException(
                "setSyllableBoundary requires existing lyric at index " + index + " verse " + verse);
        }

        if (lyric.syllabic() == null) {
            throw new IllegalStateException(
                "setSyllableBoundary cannot run on carrier lyric (extend=" + lyric.extend() + ')');
        }

        var prevContinues = previousLyricContinues(index, verse);
        var newSyllabic = deriveSyllabic(prevContinues, !isWordEnd);

        var newCompound = !isWordEnd && isCompound;

        trace("setSyllableBoundary({}, wordEnd={}, compound={}): {} -> {}, prevContinues={}",
            index, isWordEnd, isCompound, lyric.syllabic(), newSyllabic, prevContinues);

        if (newSyllabic != lyric.syllabic() || newCompound != lyric.compound()) {
            modifyElement(index, ElementField.LYRIC, () ->
                replaceSyllabicAndCompound(index, lyric, newSyllabic, newCompound));
        }

        fixSuccessorSyllabic(index, verse, !isWordEnd);
    }

    /**
     * Writes the {@code verse} lyric at {@code index} with its syllabic already derived from
     * chain context, then re-letters the following syllable if this write changed what that
     * one must be.
     *
     * <p>Use this rather than {@link #setSyllableBoundary} whenever the text itself is being
     * written: deriving the syllabic here means one {@link ElementField#LYRIC} modification for
     * this element. Writing a placeholder syllabic and letting the boundary pass correct it
     * costs a second one whenever the derived value differs from the placeholder.
     * {@code setSyllableBoundary} remains the entry point for re-lettering a lyric whose text is
     * <em>not</em> changing.
     *
     * <p>The syllabic follows the lyric's own kind: {@code null} for a melisma carrier (which
     * has no syllable of its own), and the chain-derived value for a text-bearing syllable.
     * Blank text on a non-carrier is an erasure rather than a syllable — {@link Lyric}'s
     * constructor rejects blank text on anything but a carrier — so this removes the verse
     * entry outright and returns before any {@link Lyric} is derived or constructed.
     *
     * <p>Must be called from inside a {@link Song#withModification(Runnable)} bracket.
     *
     * @param index      the element index in this run
     * @param verse      the verse number (typically 1)
     * @param text       the syllable text; blank for a carrier or an erased syllable
     * @param extend     melisma extender state for the lyric
     * @param isWordEnd  {@code true} when this syllable terminates the word (no continuation)
     * @param isCompound {@code true} when this syllable joins the next via a compound-word
     *                   boundary; ignored when {@code isWordEnd} is {@code true}
     */
    default void writeLyricForVerse(int index, int verse, String text, Lyric.Extend extend,
            boolean isWordEnd, boolean isCompound) {
        var isCarrier = Lyric.isCarrier(extend);

        // isBlank, not isEmpty: whitespace-only text erases the syllable exactly as an empty
        // string does. Decided before any Lyric is derived or constructed, since Lyric's
        // constructor now rejects blank text on a non-carrier outright.
        if (!isCarrier && text.isBlank()) {
            trace("writeLyricForVerse({}, '{}', {}): blank, erasing", index, text, extend);
            removeLyricForVerse(index, verse);
            return;
        }

        // Stricter than isWordEnd alone: a lyric with no text of its own continues nothing,
        // whatever the caller asked for. Callers that pre-compute a commit's continues-status
        // from isWordEnd by itself (LyricEditor.CommitIntent) must therefore never pass a
        // continuing request with blank text, or their answer and this one diverge.
        var continuesIntoNext = !isCarrier && !isWordEnd;

        Lyric.@Nullable Syllabic syllabic = isCarrier
            ? null
            : deriveSyllabic(previousLyricContinues(index, verse), continuesIntoNext);

        var compound = continuesIntoNext && isCompound;

        trace("writeLyricForVerse({}, '{}', {}): syllabic={}, compound={}",
            index, text, extend, syllabic, compound);

        var element = getElement(index);

        // Skip a write that would store what is already there, as setSyllableBoundary and
        // fixSuccessorSyllabic do: an ElementModification costs two element clones plus an undo
        // entry the user could step back into for no visible change.
        if (!storesSameLyric(element.getLyricForVerse(verse), text, extend, syllabic, compound)) {
            var lyric = isCarrier
                ? Lyric.carrier(verse, extend)
                : new Lyric(verse, text, extend, syllabic, compound);
            modifyElement(index, ElementField.LYRIC, () -> element.putLyric(lyric));
        }

        if (!isCarrier) {
            fixSuccessorSyllabic(index, verse, continuesIntoNext);
        }
    }

    /**
     * Whether {@code existing} already holds exactly what {@link #writeLyricForVerse} would
     * store for these values, making the write a no-op. {@code text} is never blank here:
     * {@link #writeLyricForVerse} routes a blank non-carrier syllable to
     * {@link #removeLyricForVerse} before this is ever reached.
     */
    private static boolean storesSameLyric(@Nullable Lyric existing, String text,
            Lyric.Extend extend, Lyric.@Nullable Syllabic syllabic, boolean compound) {
        if (Lyric.isCarrier(extend)) {
            // A carrier's stored state is fixed by its extend alone: empty text, null syllabic,
            // not compound.
            return existing != null && existing.extend() == extend;
        }

        return existing != null
            && existing.text().equals(text)
            && existing.extend() == extend
            && existing.syllabic() == syllabic
            && existing.compound() == compound;
    }

    /**
     * Reletters the syllable on the first lyric-bearing element after {@code index} so that
     * it agrees with whether its predecessor continues into it. No-op when no lyric-bearing
     * element follows, or when the one that does is a melisma carrier.
     *
     * @param index the position whose successor is relettered, or {@code null} when the
     *     successor is the run's first lyric-bearing element, because the syllable at issue
     *     has no predecessor at all — a deleted head, or the head of a run
     * @param verse the verse whose chain is repaired
     * @param predecessorContinues whether the syllable at {@code index} continues into its
     *     successor; always {@code false} when {@code index} is {@code null}
     */
    private void fixSuccessorSyllabic(@Nullable Integer index, int verse, boolean predecessorContinues) {
        var nextIndex = index == null ? firstLyricBearingIndex(verse) : nextLyricBearingIndex(index, verse);

        if (nextIndex == null) {
            trace("fixSuccessorSyllabic({}): no lyric-bearing successor", index);
            return;
        }

        var nextLyric = getElement(nextIndex).getLyricForVerse(verse);

        if (nextLyric == null || nextLyric.syllabic() == null) {
            trace("fixSuccessorSyllabic({}): successor {} is a carrier, nothing to fix",
                index, nextIndex);
            return;
        }

        trace("fixSuccessorSyllabic({}): successor {}", index, nextIndex);
        reletterSyllable(nextIndex, verse, nextLyric, predecessorContinues);
    }

    /**
     * Rewrites the {@link Lyric.Syllabic} of the text-bearing syllable at {@code index} to the
     * value the chain implies, given whether the syllable before it continues into this one.
     * The compound flag is carried over untouched.
     *
     * <p>Skips a write that would store what is already there: an {@code ElementModification}
     * costs two element clones plus an undo entry the user could step back into for no visible
     * change. {@code lyric} must be the element's current {@code verse} lyric.
     *
     * <p>Must be called inside a modification bracket.
     */
    private void reletterSyllable(int index, int verse, Lyric lyric, boolean predecessorContinues) {
        var newSyllabic = deriveSyllabic(predecessorContinues, Lyric.syllabicContinues(lyric.syllabic()));

        trace("reletterSyllable({}): {} -> {}, predecessorContinues={}",
            index, lyric.syllabic(), newSyllabic, predecessorContinues);

        if (newSyllabic != lyric.syllabic()) {
            modifyElement(index, ElementField.LYRIC, () ->
                replaceSyllabicAndCompound(index, lyric, newSyllabic, lyric.compound()));
        }
    }

    /**
     * Rewrites {@code lyric} on the element at {@code index} with the given syllabic and
     * compound flag, keeping its verse, text and extend. {@code lyric} must be that element's
     * current lyric for its verse.
     */
    private void replaceSyllabicAndCompound(int index, Lyric lyric,
            Lyric.@Nullable Syllabic syllabic, boolean compound) {
        getElement(index).putLyric(new Lyric(
            lyric.verse(), lyric.text(), lyric.extend(), syllabic, compound));
    }

    private boolean previousLyricContinues(int index, int verse) {
        var prevIndex = previousTextBearingIndex(index, verse);

        if (prevIndex == null) {
            return false;
        }

        var prevLyric = getElement(prevIndex).getLyricForVerse(verse);
        return prevLyric != null && Lyric.syllabicContinues(prevLyric.syllabic());
    }

    /**
     * Walks forward from {@code index + 1} and returns the index of the first element whose
     * {@code verse} lyric is non-null, or {@code null} if there is none.
     */
    private @Nullable Integer nextLyricBearingIndex(int index, int verse) {
        return lyricBearingIndexAtOrAfter(index + 1, verse);
    }

    /**
     * Returns the index of the first element on this run whose {@code verse} lyric is
     * non-null, or {@code null} if there is none.
     */
    private @Nullable Integer firstLyricBearingIndex(int verse) {
        return lyricBearingIndexAtOrAfter(0, verse);
    }

    private @Nullable Integer lyricBearingIndexAtOrAfter(int start, int verse) {
        for (var i = start; i < elementCount(); i++) {
            if (getElement(i).getLyricForVerse(verse) != null) {
                return i;
            }
        }

        return null;
    }

    /**
     * Walks backward from {@code fromIndex - 1} and returns the index of the first element whose
     * {@code verse} lyric is non-null, or {@code null} if there is none.
     */
    default @Nullable Integer previousLyricBearingIndex(int fromIndex, int verse) {
        for (var i = fromIndex - 1; i >= 0; i--) {
            if (getElement(i).getLyricForVerse(verse) != null) {
                return i;
            }
        }

        return null;
    }

    /**
     * Walks backward from {@code fromIndex - 1} and returns the index of the first element
     * carrying a text-bearing {@code verse} syllable, or {@code null} if there is none.
     * Melisma carriers are skipped: they hold no syllable, and so no syllabic chain
     * position, of their own.
     */
    private @Nullable Integer previousTextBearingIndex(int fromIndex, int verse) {
        for (var i = fromIndex - 1; i >= 0; i--) {
            var lyric = getElement(i).getLyricForVerse(verse);

            if (lyric != null && lyric.syllabic() != null) {
                return i;
            }
        }

        return null;
    }

    private static boolean isWordContinuingLyric(Lyric lyric) {
        return lyric.syllabic() == Lyric.Syllabic.BEGIN
            || lyric.syllabic() == Lyric.Syllabic.MIDDLE
            || lyric.compound();
    }

    /**
     * Whether the first element after {@code fromIndex} that carries a {@code verse} lyric
     * carries a syllable of its own, rather than being a melisma carrier.
     */
    default boolean hasFollowingTextBearingLyric(int fromIndex, int verse) {
        var forwardIndex = nextLyricBearingIndex(fromIndex, verse);

        if (forwardIndex == null) {
            return false;
        }

        var forwardLyric = getElement(forwardIndex).getLyricForVerse(verse);
        return forwardLyric != null && forwardLyric.syllabic() != null;
    }

    /**
     * Records one step of the lyric chain repairs. Gated on {@code DEBUG_LYRICS} as well as the
     * log level, since these run several times per keystroke and would drown any other debug
     * output; see {@code LyricEditor}, which traces the same edits from the editor's side.
     */
    private static void trace(String format, @Nullable Object... args) {
        if (LogUtils.isTracingLyrics(LOG)) {
            LOG.debug(format, args);
        }
    }

    /**
     * Repairs the syllabic of the first text-bearing lyric after {@code index}, given that the
     * element at {@code index} has just become a melisma carrier. A melisma is sung on a single
     * non-hyphenated syllable, so the chain always ends the word that ran into it and that
     * syllable can no longer be a word continuation ({@link Lyric.Syllabic#MIDDLE} becomes
     * {@link Lyric.Syllabic#BEGIN}, {@link Lyric.Syllabic#END} becomes
     * {@link Lyric.Syllabic#SINGLE}). Intervening carriers are skipped — they hold no syllabic
     * of their own.
     *
     * <p>Must be called inside a modification bracket, after the carrier at {@code index} has
     * been written.
     */
    default void adjustSuccessorAfterMelismaCarrier(int index, int verse) {
        for (var i = index + 1; i < elementCount(); i++) {
            var lyric = getElement(i).getLyricForVerse(verse);

            if (lyric == null || lyric.syllabic() == null) {
                continue;
            }

            trace("adjustSuccessorAfterMelismaCarrier({}): successor {}", index, i);

            // A melisma is sung on one syllable, so the word running into it always ends at
            // the carrier: nothing before this successor continues into it.
            reletterSyllable(i, verse, lyric, false);
            return;
        }
    }

    /**
     * Repairs syllabic chain markers on the neighbors of a just-deleted verse lyric.
     * Must be called inside a modification bracket, after the lyric at {@code index}
     * has already been cleared.
     */
    default void adjustNeighborsForLyricDeletion(int index, int verse) {
        var backIndex = previousLyricBearingIndex(index, verse);
        trace("adjustNeighborsForLyricDeletion({}): predecessor={}", index, backIndex);

        if (backIndex != null) {
            var backLyric = getElement(backIndex).getLyricForVerse(verse);

            if (backLyric == null) {
                return;
            }

            if (backLyric.extend() == Lyric.Extend.CONTINUE) {
                trace("adjustNeighborsForLyricDeletion({}): closing chain, {} CONTINUE -> STOP",
                    index, backIndex);
                var backElement = getElement(backIndex);
                modifyElement(backIndex, ElementField.LYRIC, () ->
                    backElement.putLyric(backLyric.withExtend(Lyric.Extend.STOP)));
                return;
            }

            endDanglingWord(backIndex, verse);
            return;
        }

        // No predecessor — fix the successor if it now lacks a continuing predecessor.
        fixSuccessorSyllabic(null, verse, false);
    }

    /**
     * Ends the word at {@code index} when the syllable there continues into a next one that
     * is not there — no text-bearing lyric follows it in this verse. A melisma carrier is
     * not that successor: it holds no syllable of its own for a hyphen to reach.
     *
     * <p>No-op when the element carries no {@code verse} lyric, or when the syllable
     * continues into a syllable that does exist. Must be called inside a modification
     * bracket.
     */
    private void endDanglingWord(int index, int verse) {
        var lyric = getElement(index).getLyricForVerse(verse);

        if (lyric == null
                || !isWordContinuingLyric(lyric)
                || hasFollowingTextBearingLyric(index, verse)) {
            return;
        }

        trace("endDanglingWord({}): ending dangling word", index);

        // setSyllableBoundary also adjusts the next lyric-bearing element.
        setSyllableBoundary(index, verse, true, false);
    }

    /**
     * Removes the {@code verse} lyric at {@code index} outright, leaving no residue.
     *
     * <p>This is deliberately stronger than clearing a lyric's melisma extend, which keeps
     * an (empty-texted) lyric on the element. An empty lyric still counts as lyric-bearing
     * for lyric navigation, so a melisma carrier that is no longer wanted must be removed,
     * not cleared.
     *
     * <p>No-op when the element has no lyric for the verse. Must be called inside a
     * modification bracket.
     */
    default void removeLyricForVerse(int index, int verse) {
        if (index < 0 || index >= effectiveElementCount()) {
            return;
        }

        var element = getElement(index);

        if (element.getLyricForVerse(verse) == null) {
            return;
        }

        // Blank text + NONE is setLyricForVerse's "remove the entry" case, which keeps the
        // removal inside the API that enforces the carrier/text-bearing invariants.
        modifyElement(index, ElementField.LYRIC, () -> element.removeLyricForVerse(verse));
    }

    /** The verse number of every lyric on {@code element}, in list order. */
    private static List<Integer> verseNumbersOf(StaffElement element) {
        var verses = new ArrayList<Integer>();

        for (var lyric : element.getLyrics()) {
            verses.add(lyric.verse());
        }

        return verses;
    }

    /**
     * Moves the {@code verse} syllable from {@code fromIndex} to {@code toIndex}, replacing
     * whatever lyric the target held. Used by the automatic grace-host melisma, host→grace
     * when a pair is formed: the syllable belongs to the grace.
     *
     * <p>The melisma extend is not carried across — the target receives the syllable at
     * {@link Lyric.Extend#NONE}, and {@link #syncGraceHostMelisma} re-establishes an
     * extender if the pairing still calls for one. The target's own lyric is dropped as
     * part of the write, so a text-less carrier on the target can never collide with the
     * incoming text.
     *
     * <p>No-op when the source has no lyric for the verse or its lyric is a text-less
     * carrier — there is no syllable to move. Must be called inside a modification bracket.
     */
    default void transferLyricForVerse(int fromIndex, int toIndex, int verse) {
        var elementCount = effectiveElementCount();

        if (fromIndex == toIndex
                || fromIndex < 0 || fromIndex >= elementCount
                || toIndex < 0 || toIndex >= elementCount) {
            return;
        }

        var sourceLyric = getElement(fromIndex).getLyricForVerse(verse);

        if (sourceLyric == null || sourceLyric.isCarrier()) {
            return;
        }

        // Read off the source before anything is written: putLyric replaces the target's
        // existing verse entry with the incoming syllable.
        var targetElement = getElement(toIndex);
        modifyElement(toIndex, ElementField.LYRIC, () ->
            targetElement.putLyric(sourceLyric.withExtend(Lyric.Extend.NONE)));
        removeLyricForVerse(fromIndex, verse);
    }

    /**
     * Moves every verse's syllable from {@code fromIndex} to {@code toIndex} via
     * {@link #transferLyricForVerse}, so a caller moving a whole element's lyrics does not
     * enumerate the verses itself.
     *
     * <p>Must be called inside a modification bracket.
     */
    default void transferLyrics(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= effectiveElementCount()) {
            return;
        }

        // Snapshot the verses first: transferLyricForVerse mutates the source's lyric list.
        for (var verse : verseNumbersOf(getElement(fromIndex))) {
            transferLyricForVerse(fromIndex, toIndex, verse);
        }
    }

    /**
     * Converges the automatic grace-host melisma at {@code graceIndex} to whatever the
     * current state implies. When a grace-host pair carries a lyric the syllable belongs to
     * the grace and the host may not carry one of its own, so a melisma must run from the
     * grace across its host.
     *
     * <p>Idempotent, and derived from live state rather than from a transition: a call site
     * only has to invoke this inside the bracket that changed the pairing, without reasoning
     * about which change occurred.
     *
     * <ul>
     *   <li><b>Establish</b> — {@code graceIndex} is a paired grace note carrying a syllable:
     *       the syllable becomes a melisma {@link Lyric.Extend#START} and the host at
     *       {@code graceIndex + 1} receives a text-less {@link Lyric.Extend#STOP} carrier,
     *       replacing any lyric of its own.</li>
     *   <li><b>Tear down</b> — otherwise: the host's carrier is removed outright and the
     *       grace's {@code START} reverts to {@link Lyric.Extend#NONE}.</li>
     * </ul>
     *
     * <p>Melismas that merely pass through the pair are left alone. A host already marked
     * {@link Lyric.Extend#CONTINUE} keeps carrying its chain onward, and a host {@code STOP}
     * whose chain began before the grace — which shows up as a carrier on the grace itself —
     * is not this pair's to tear down.
     *
     * <p>Must be called inside a modification bracket.
     */
    default void syncGraceHostMelisma(int graceIndex) {
        var hostIndex = graceIndex + 1;

        if (graceIndex < 0 || hostIndex >= effectiveElementCount()) {
            return;
        }

        var isPaired = isPairedGraceNote(graceIndex);
        var verses = new LinkedHashSet<>(verseNumbersOf(getElement(graceIndex)));
        verses.addAll(verseNumbersOf(getElement(hostIndex)));

        for (var verse : verses) {
            syncGraceHostMelismaForVerse(graceIndex, verse, isPaired);
        }
    }

    /**
     * Normalizes every grace-host pair in this run to the automatic grace-host melisma, for
     * files written before the melisma was maintained automatically (or by another program).
     * Two states are repaired, per verse and per pair:
     *
     * <ul>
     *   <li>the host carries a syllable of its own and the grace has no lyric for that verse —
     *       the syllable moves to the grace, which is where a paired grace's syllable belongs,
     *       and the melisma is established across the host;</li>
     *   <li>the grace carries a syllable with no melisma — the melisma is established.</li>
     * </ul>
     *
     * <p>When <em>both</em> elements carry a syllable, the grace's wins and the host's is
     * discarded: with the invariant enforced there is nowhere left to put it. The grace is
     * likewise left alone when it holds a text-less carrier, since that carrier belongs to a
     * melisma that began before the pair rather than to the pair itself.
     *
     * <p>Convergence is {@link #syncGraceHostMelisma}'s, so a repaired file lands in exactly
     * the state editing would have produced, and a second load is a no-op.
     *
     * <p>Intended for the load path. Mutation tracking must be suspended (readers already
     * suspend it) so the repair is silent — no undo entry, no {@code modified} flag.
     */
    default void repairGraceHostMelismas() {
        for (var index = 0; index < elementCount(); index++) {
            if (!isPairedGraceNote(index) || index + 1 >= elementCount()) {
                continue;
            }

            moveHostSyllablesToGrace(index);
            syncGraceHostMelisma(index);
        }
    }

    /**
     * Hands each of the host's own syllables back to the grace, for the verses where the grace
     * has no lyric at all. A verse the grace already occupies — with a syllable or with a
     * carrier — is left untouched; see {@link #repairGraceHostMelismas}.
     */
    private void moveHostSyllablesToGrace(int graceIndex) {
        var hostIndex = graceIndex + 1;
        var graceElement = getElement(graceIndex);

        // Snapshot the verses first: transferLyricForVerse mutates the host's lyric list.
        for (var verse : verseNumbersOf(getElement(hostIndex))) {
            // transferLyricForVerse ignores a text-less carrier, so a host STOP stays put.
            if (graceElement.getLyricForVerse(verse) == null) {
                transferLyricForVerse(hostIndex, graceIndex, verse);
            }
        }
    }

    private void syncGraceHostMelismaForVerse(int graceIndex, int verse, boolean isPaired) {
        var hostIndex = graceIndex + 1;
        var hostElement = getElement(hostIndex);
        var graceLyric = getElement(graceIndex).getLyricForVerse(verse);
        var hostLyric = hostElement.getLyricForVerse(verse);
        var graceCarries = graceLyric != null && graceLyric.isCarrier();
        var graceHasSyllable = graceLyric != null && !graceCarries && !graceLyric.text().isBlank();

        trace("syncGraceHostMelisma(grace={}, host={}): paired={} graceCarries={} graceHasSyllable={} host={}",
            graceIndex, hostIndex, isPaired, graceCarries, graceHasSyllable, hostLyric);

        if (isPaired && graceHasSyllable) {
            setExtendForVerse(graceIndex, verse, Lyric.Extend.START);

            // A host that already carries the extender onward needs no STOP of its own.
            if (hostLyric != null && hostLyric.isCarrier()) {
                return;
            }

            var hostHadSyllable = hostLyric != null;
            modifyElement(hostIndex, ElementField.LYRIC, () ->
                hostElement.putLyric(Lyric.carrier(verse, Lyric.Extend.STOP)));

            // The host's own syllable is gone, so its neighbors' syllabic chain has to heal
            // exactly as it would after a deletion. adjustNeighborsForLyricDeletion documents
            // a cleared lyric as its precondition, and what sits here now is a carrier rather
            // than nothing — that is equivalent for its purposes, since it reads only the
            // neighbors of `index`, never `index` itself.
            if (hostHadSyllable) {
                adjustNeighborsForLyricDeletion(hostIndex, verse);
            }

            return;
        }

        // Tear down only this pair's own two-element chain: a STOP reached by a chain that
        // began before the grace belongs to that chain, not to the pair.
        if (graceCarries || hostLyric == null || hostLyric.extend() != Lyric.Extend.STOP) {
            return;
        }

        removeLyricForVerse(hostIndex, verse);
        setExtendForVerse(graceIndex, verse, Lyric.Extend.NONE);
    }

    /**
     * Replaces the melisma extend of the {@code verse} lyric at {@code index}, leaving its
     * text, syllabic and compound flag untouched. No-op when there is no lyric for the verse
     * or the extend already matches. Only valid for text-bearing lyrics, whose non-null
     * syllabic remains legal for every non-carrier extend.
     */
    private void setExtendForVerse(int index, int verse, Lyric.Extend extend) {
        var element = getElement(index);
        var lyric = element.getLyricForVerse(verse);

        if (lyric == null || lyric.extend() == extend) {
            return;
        }

        modifyElement(index, ElementField.LYRIC, () ->
            element.putLyric(
                new Lyric(lyric.verse(), lyric.text(), extend, lyric.syllabic(), lyric.compound())));
    }

    private static Lyric.Syllabic deriveSyllabic(boolean prevContinues, boolean thisContinues) {
        if (prevContinues) {
            return thisContinues ? Lyric.Syllabic.MIDDLE : Lyric.Syllabic.END;
        }

        return thisContinues ? Lyric.Syllabic.BEGIN : Lyric.Syllabic.SINGLE;
    }

    // -------------------------------------------------------------------------
    // Runs lifted out of a longer one
    // -------------------------------------------------------------------------

    /**
     * Ends every lyric chain that runs off either end of this run, so no syllable and no
     * melisma carrier is left pointing at an element the run does not contain.
     *
     * <p>A run lifted out of a longer one — a clipboard
     * {@link songscribe.ui.clipboard.Fragment} — inherits whatever its first and last
     * syllables said about their neighbors on the line they were taken from. The first may
     * continue a word that began before it, or carry a melisma whose {@code START} was left
     * behind; the last may hyphenate into a syllable that is no longer there, or open a
     * melisma that never closes. Dropped somewhere else, each of those attaches itself to
     * whatever now sits next to it — a syllable the user never copied.
     *
     * <p>Nothing here is a new rule. Everything outside the run is gone, which is the
     * situation a deletion produces, so the repairs are the ones a deletion already runs:
     * {@link #removeCarriersFrom} for the orphaned carriers at the head,
     * {@link #closeMelismaAt} for the chain left open at the tail, and
     * {@link #fixSuccessorSyllabic}/{@link #endDanglingWord} for the words broken at either
     * end.
     *
     * <p>Idempotent — a run with no chain crossing either end is left untouched.
     */
    default void endDanglingChains() {
        for (var verse : verseNumbers()) {
            endDanglingChainsForVerse(verse);
        }
    }

    private void endDanglingChainsForVerse(int verse) {
        var firstIndex = firstLyricBearingIndex(verse);

        if (firstIndex == null) {
            return;
        }

        var firstLyric = getElement(firstIndex).getLyricForVerse(verse);

        // A carrier at the head sustains a melisma that began outside the run, so its
        // START is gone and it extends nothing — exactly what deleting that START leaves
        // behind. A text-bearing head is left alone: a melisma it starts is the run's own.
        if (firstLyric != null && firstLyric.isCarrier()) {
            removeCarriersFrom(firstIndex, verse);
        }

        // The head has no predecessor at all, so its syllable cannot be continuing one.
        fixSuccessorSyllabic(null, verse, false);

        var effectiveCount = effectiveElementCount();

        // The tail's chain never reaches its STOP, which is the deleted-STOP case.
        var tailIndex = previousLyricBearingIndex(effectiveCount, verse);

        if (tailIndex != null) {
            closeMelismaAt(tailIndex, verse);
        }

        var lastTextIndex = previousTextBearingIndex(effectiveCount, verse);

        if (lastTextIndex != null) {
            endDanglingWord(lastTextIndex, verse);
        }
    }

    /** Every verse number carried by any element in this run, in first-seen order. */
    private List<Integer> verseNumbers() {
        var verses = new LinkedHashSet<Integer>();

        for (var index = 0; index < effectiveElementCount(); index++) {
            verses.addAll(verseNumbersOf(getElement(index)));
        }

        return List.copyOf(verses);
    }
}
