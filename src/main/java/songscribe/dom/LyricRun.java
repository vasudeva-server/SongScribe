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
import java.util.LinkedHashSet;
import java.util.List;

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
     * Adjusts the syllabic value on the element at {@code prevIndex} when the neighbor
     * to its right is inserted (blank note) or deleted.
     *
     * <p>For insertion ({@code deletedElement} is null): always breaks the syllabic chain on
     * the predecessor, since the new blank note interrupts it.
     *
     * <p>For deletion ({@code deletedElement} is the element being removed): breaks the
     * predecessor's chain only in verses where the deleted element did not continue
     * the chain (i.e., it has no lyric for that verse or its syllabic is not BEGIN/MIDDLE).
     *
     * <p>Must be called inside a modification bracket.
     */
    default void adjustSyllablesForNeighborChange(int prevIndex, @Nullable StaffElement deletedElement) {
        if (!hasIndex(prevIndex)) {
            return;
        }

        var prevElement = getElement(prevIndex);
        var lyrics = prevElement.lyrics;
        var indicesToClear = new ArrayList<Integer>();

        for (var j = 0; j < lyrics.size(); j++) {
            var lyric = lyrics.get(j);
            var syllabic = lyric.syllabic();

            if (!Lyric.syllabicContinues(syllabic)) {
                continue;
            }

            if (deletedElement == null) {
                indicesToClear.add(j);
            } else {
                var matchingDeletedLyric = deletedElement.lyrics.stream()
                    .filter(deletedLyric -> deletedLyric.verse() == lyric.verse())
                    .findFirst()
                    .orElse(null);

                var deletedSyllabic = matchingDeletedLyric != null ? matchingDeletedLyric.syllabic() : null;

                if (!Lyric.syllabicContinues(deletedSyllabic)) {
                    indicesToClear.add(j);
                }
            }
        }

        if (indicesToClear.isEmpty()) {
            return;
        }

        modifyElement(prevIndex, ElementField.LYRIC, () -> {
            for (var lyricIndex : indicesToClear) {
                var lyric = lyrics.get(lyricIndex);
                var prevOfPrevLyric = prevIndex > 0
                    ? getElement(prevIndex - 1).getLyricForVerse(lyric.verse()) : null;
                var prevOfPrevSyllabic = prevOfPrevLyric != null ? prevOfPrevLyric.syllabic() : null;
                var prevContinues = prevOfPrevSyllabic == Lyric.Syllabic.BEGIN
                    || prevOfPrevSyllabic == Lyric.Syllabic.MIDDLE;
                var newSyllabic = prevContinues ? Lyric.Syllabic.END : Lyric.Syllabic.SINGLE;
                lyrics.set(lyricIndex,
                    new Lyric(lyric.verse(), lyric.text(), lyric.extend(), newSyllabic, false));
            }
        });
    }

    /**
     * Adjusts the syllabic value on the element at {@code insertionIndex + 1} after a bare
     * note has been inserted at {@code insertionIndex}. The inserted note carries no lyric,
     * so a successor that was mid-word or end-of-word now has no preceding syllable:
     *
     * <ul>
     *   <li>{@link Lyric.Syllabic#MIDDLE} → {@link Lyric.Syllabic#BEGIN}</li>
     *   <li>{@link Lyric.Syllabic#END} → {@link Lyric.Syllabic#SINGLE}</li>
     * </ul>
     *
     * <p>Carrier lyrics ({@code syllabic == null}) are unaffected.
     * Must be called inside a modification bracket, after {@link Line#addElement(int, StaffElement)}.
     */
    default void adjustSyllablesForSuccessorAfterInsertion(int insertionIndex) {
        var successorIndex = insertionIndex + 1;

        if (successorIndex >= effectiveElementCount()) {
            return;
        }

        var successor = getElement(successorIndex);
        var lyrics = successor.lyrics;
        var indicesToAdjust = new ArrayList<Integer>();

        for (var j = 0; j < lyrics.size(); j++) {
            var syllabic = lyrics.get(j).syllabic();

            if (syllabic == Lyric.Syllabic.MIDDLE || syllabic == Lyric.Syllabic.END) {
                indicesToAdjust.add(j);
            }
        }

        if (indicesToAdjust.isEmpty()) {
            return;
        }

        modifyElement(successorIndex, ElementField.LYRIC, () -> {
            for (var lyricIndex : indicesToAdjust) {
                var lyric = lyrics.get(lyricIndex);
                var newSyllabic = lyric.syllabic() == Lyric.Syllabic.MIDDLE
                    ? Lyric.Syllabic.BEGIN
                    : Lyric.Syllabic.SINGLE;
                lyrics.set(lyricIndex,
                    new Lyric(lyric.verse(), lyric.text(), lyric.extend(), newSyllabic, lyric.compound()));
            }
        });
    }

    /**
     * Adjusts the melisma extend chain when a bare note is inserted at {@code insertionIndex}.
     * The predecessor at {@code insertionIndex - 1} may be part of a melisma chain; inserting
     * a note with no extend breaks the chain there. Must be called <em>before</em>
     * {@link Line#addElement(int, StaffElement)} while the pre-insertion indices are still valid.
     *
     * <ul>
     *   <li>{@link Lyric.Extend#START}: predecessor cleared to {@code NONE}; cascade-clear
     *       {@code CONTINUE}/{@code STOP} from {@code insertionIndex} onward.</li>
     *   <li>{@link Lyric.Extend#CONTINUE}: predecessor promoted to {@code STOP}; cascade-clear
     *       {@code CONTINUE}/{@code STOP} from {@code insertionIndex} onward.</li>
     *   <li>{@link Lyric.Extend#STOP}/{@link Lyric.Extend#NONE}: no action.</li>
     * </ul>
     */
    default void adjustExtendsForInsertion(int insertionIndex) {
        adjustExtends(insertionIndex - 1, insertionIndex);
    }

    /**
     * The body of {@link #adjustExtendsForInsertion}, with the two indices it derives from the
     * insertion point passed in: the chain member that loses its successor
     * ({@code predecessorIndex}) and the first surviving member of the broken chain
     * ({@code cascadeStartIndex}). They differ by the inserted element, which sits between
     * them once it is in the list — hence the two callers, one before the insertion and one
     * after it.
     */
    private void adjustExtends(int predecessorIndex, int cascadeStartIndex) {
        // One pending rewrite of the predecessor's melisma extend, in the one method that
        // makes them: the list is built before the modification bracket opens and applied
        // inside it, so the before/after clones bracket every verse's fix together.
        record ExtendFix(int lyricIndex, int verse, Lyric.Extend newExtend) {}

        if (predecessorIndex < 0 || predecessorIndex >= effectiveElementCount()) {
            return;
        }

        var predecessorElement = getElement(predecessorIndex);
        var lyrics = predecessorElement.lyrics;
        var fixes = new ArrayList<ExtendFix>();

        for (var i = 0; i < lyrics.size(); i++) {
            var lyric = lyrics.get(i);

            switch (lyric.extend()) {
                case START -> fixes.add(new ExtendFix(i, lyric.verse(), Lyric.Extend.NONE));
                case CONTINUE -> fixes.add(new ExtendFix(i, lyric.verse(), Lyric.Extend.STOP));
                default -> { }
            }
        }

        if (fixes.isEmpty()) {
            return;
        }

        modifyElement(predecessorIndex, ElementField.LYRIC, () -> {
            for (var fix : fixes) {
                var lyricIndex = fix.lyricIndex();
                var lyric = lyrics.get(lyricIndex);
                lyrics.set(lyricIndex,
                    new Lyric(lyric.verse(), lyric.text(), fix.newExtend(), lyric.syllabic(), lyric.compound()));
            }
        });

        for (var fix : fixes) {
            cascadeClearExtend(cascadeStartIndex, fix.verse());
        }
    }

    /**
     * Breaks the lyric chains that the run about to be inserted at {@code insertionIndex}
     * interrupts — the syllabic chain on the predecessor and the melisma chain running through
     * it. Must be called inside a modification bracket, <em>before</em>
     * {@link Line#addElement(int, StaffElement)}, while the pre-insertion indices still hold.
     *
     * <p>A run every element of which carries the chains onward without ever taking a syllable —
     * a barline, a breath mark, a key change, a rest — breaks nothing and is left alone. That is
     * {@link ElementType#interruptsLyricChain()}'s answer, and the run is judged by it as a
     * whole: one interrupting element in it breaks the chains for the run.
     *
     * <p>Only the predecessor half of the repair belongs here. The rest runs after the
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

        adjustSyllablesForNeighborChange(insertionIndex - 1, null);
        adjustExtendsForInsertion(insertionIndex);
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
     * disturb once it was in the list: the successor's syllabic chain, a connecting glissando on
     * the predecessor left with no valid target, and the grace-host melisma of a pair the
     * insertion landed inside. The predecessor half of the repair runs before the insertion —
     * see {@link #repairNeighborsBeforeInsertion}.
     *
     * <p><b>The two halves key off opposite ends of the run.</b> The successor's chain is broken
     * by the <em>last</em> element inserted, because that is the one now standing in front of it.
     * The glissando and the melisma are broken by the <em>first</em>, because that is the one now
     * standing behind the predecessor. A path that inserts more than one element and passes a
     * single index therefore repairs one half against the wrong element, which is why the whole
     * run is a parameter rather than each caller's own arithmetic.
     *
     * <p>The successor's chain, like the predecessor's, survives a run that interrupts nothing —
     * see {@link #repairNeighborsBeforeInsertion}. The glissando and the melisma do not read that
     * rule: they are about which element a pairing points at, not about who a syllable's
     * neighbors are, and a barline standing between a note and its glissando target orphans it
     * however transparent it is to a word.
     *
     * <p>Must be called inside a modification bracket, <em>after</em> every
     * {@link Line#addElement(int, StaffElement)} of the run.
     *
     * @param firstInsertedIndex the index the first element of the run landed at
     * @param insertedRun the run that landed there, in the order it landed; must not be empty
     */
    default void repairNeighborsAfterInsertion(int firstInsertedIndex, List<? extends StaffElement> insertedRun) {
        if (interruptsLyricChains(insertedRun)) {
            adjustSyllablesForSuccessorAfterInsertion(firstInsertedIndex + insertedRun.size() - 1);
        }

        // A connecting glissando joins a note to the note that immediately follows it.
        // Inserting another pitched note simply re-targets it, but inserting anything else
        // (rest, breath mark, grace note, barline, key change) leaves it with no valid
        // target, so remove it from the preceding note.
        if (!getElement(firstInsertedIndex).getType().isPitchedNote() && firstInsertedIndex > 0) {
            var precedingElement = getElement(firstInsertedIndex - 1);

            if (precedingElement.hasGlissando()) {
                modifyElement(firstInsertedIndex - 1, ElementField.SLIDE, precedingElement::removeSlide);
            }
        }

        // A pitched insertion between a grace note and its host re-targets the glissando, so
        // the inserted element is the new host. The extend adjustment made before the insertion
        // removed the melisma that pointed at the old host — carrier and all, leaving it an
        // ordinary note free to take a syllable of its own — so re-establish the melisma against
        // the new host. The non-pitched case fell through the glissando strip above and no
        // longer reads as paired.
        if (isPairedGraceNote(firstInsertedIndex - 1)) {
            syncGraceHostMelisma(firstInsertedIndex - 1);
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
            adjustSyllablesForNeighborChange(insertedIndex - 1, null);
            adjustExtends(insertedIndex - 1, insertedIndex + 1);
        }

        repairNeighborsAfterInsertion(insertedIndex, insertedRun);
    }

    /**
     * Adjusts the melisma extends of neighboring elements when the element at
     * {@code deletedIndex} is about to be deleted. Must be called inside a modification
     * bracket while the element is still in the list.
     *
     * <ul>
     *   <li>{@link Lyric.Extend#START} deleted: kills the chain — cascades {@code NONE} to
     *       all following elements through the {@code STOP}.</li>
     *   <li>{@link Lyric.Extend#CONTINUE} deleted: chain heals naturally, no adjustment needed.</li>
     *   <li>{@link Lyric.Extend#STOP} deleted: the immediately preceding {@code CONTINUE} is
     *       promoted to {@code STOP}; if the preceding element has {@code START} (2-element
     *       chain), it is cleared to {@code NONE}.</li>
     * </ul>
     */
    default void adjustExtendsForDeletion(int deletedIndex) {
        if (deletedIndex < 0 || deletedIndex >= effectiveElementCount()) {
            return;
        }

        var deletedElement = getElement(deletedIndex);

        for (var lyric : deletedElement.lyrics) {
            switch (lyric.extend()) {
                case START -> cascadeClearExtend(deletedIndex + 1, lyric.verse());
                case STOP -> adjustPrecedingForStopDeletion(deletedIndex - 1, lyric.verse());
                default -> { }
            }
        }
    }

    private void cascadeClearExtend(int startIndex, int verse) {
        for (var i = startIndex; i < effectiveElementCount(); i++) {
            var element = getElement(i);
            var lyricIndex = element.indexOfLyricForVerse(verse);

            if (lyricIndex < 0) {
                break;
            }

            var lyric = element.lyrics.get(lyricIndex);

            if (lyric.extend() == Lyric.Extend.NONE) {
                break;
            }

            var isStop = lyric.extend() == Lyric.Extend.STOP;

            // A carrier has no text of its own, so clearing its extend would strand an empty
            // lyric — one that still counts as lyric-bearing for lyric navigation. Drop the
            // entry instead. The cascade only ever walks the members of a chain downstream of
            // its START, which are carriers by construction, so the else branch is unreachable
            // as things stand; it is there to keep a malformed chain from losing its text.
            if (lyric.isCarrier()) {
                modifyElement(i, ElementField.LYRIC, () -> element.lyrics.remove(lyricIndex));
            } else {
                modifyElement(i, ElementField.LYRIC, () ->
                    element.lyrics.set(lyricIndex,
                        new Lyric(lyric.verse(), lyric.text(), Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false)));
            }

            if (isStop) {
                break;
            }
        }
    }

    private void adjustPrecedingForStopDeletion(int precedingIndex, int verse) {
        if (precedingIndex < 0) {
            return;
        }

        var precedingElement = getElement(precedingIndex);
        var lyricIndex = precedingElement.indexOfLyricForVerse(verse);

        if (lyricIndex < 0) {
            return;
        }

        var existingExtend = precedingElement.lyrics.get(lyricIndex).extend();

        if (existingExtend != Lyric.Extend.CONTINUE && existingExtend != Lyric.Extend.START) {
            return;
        }

        // CONTINUE → STOP: preceding note becomes the new terminus
        // START → NONE: 2-note chain collapses (a single note cannot carry a melisma)
        var newExtend = existingExtend == Lyric.Extend.CONTINUE ? Lyric.Extend.STOP : Lyric.Extend.NONE;

        setExtendForVerse(precedingIndex, verse, newExtend);
    }

    /**
     * Normalizes every {@link Lyric} in this run so that {@link Lyric#syllabic()} is
     * chain-consistent (SINGLE/BEGIN/MIDDLE/END match the word boundary implied by neighbors).
     * Idempotent. Used after document load for legacy files where {@code <syllabic>} may be
     * absent or only a best-guess value.
     */
    default void backfillSyllabic() {
        for (var index = 0; index < elementCount(); index++) {
            var lyrics = getElement(index).lyrics;

            for (var lyric : lyrics) {
                backfillSyllabicAt(index, lyric.verse());
            }
        }
    }

    /**
     * Updates the lyric at {@code (index, verse)} so its {@link Lyric#syllabic()} is consistent
     * with the syllabic chain. BEGIN/MIDDLE on the predecessor means this element is in the middle
     * or end of a word; BEGIN on this element (from an initial best-guess load) means it continues
     * to the next. No-op when the element has no lyric for the verse, the lyric is a carrier, or
     * the stored value already matches. Mutates the lyric list in place — the caller is
     * responsible for any required modification bracket.
     */
    private void backfillSyllabicAt(int index, int verse) {
        var element = getElement(index);
        var lyrics = element.lyrics;
        var lyricIndex = element.indexOfLyricForVerse(verse);

        if (lyricIndex < 0) {
            return;
        }

        var lyric = lyrics.get(lyricIndex);
        var extend = lyric.extend();

        // Carriers never need backfill — their syllabic is always null.
        if (extend == Lyric.Extend.STOP || extend == Lyric.Extend.CONTINUE) {
            return;
        }

        // The nearest earlier lyric of any kind, carrier included: a carrier's syllabic is
        // null, and syllabicContinues reads that as "does not continue", which is the answer
        // a melisma running into this syllable calls for.
        var prevIndex = previousLyricBearingIndex(index, verse);
        var prevLyric = prevIndex < 0 ? null : getElement(prevIndex).getLyricForVerse(verse);
        var prevContinues = prevLyric != null && Lyric.syllabicContinues(prevLyric.syllabic());

        // BEGIN was assigned by best-guess load to signal "this syllable continues to the next".
        var derived = deriveSyllabic(prevContinues, Lyric.syllabicContinues(lyric.syllabic()));

        if (derived == lyric.syllabic()) {
            return;
        }

        lyrics.set(lyricIndex, new Lyric(lyric.verse(), lyric.text(), lyric.extend(), derived, lyric.compound()));
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
     * <p>Must be called from inside a {@link Line#withModification(Runnable)} bracket.
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
                replaceSyllabicAndCompound(index, verse, newSyllabic, newCompound));
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
     * has no syllable of its own), and the chain-derived value for a text-bearing syllable. For
     * an erased syllable it is {@link Lyric.Syllabic#SINGLE}, but that value never reaches
     * storage — blank text with {@link Lyric.Extend#NONE} makes
     * {@link StaffElement#setLyricForVerse} drop the verse entry outright, so the syllabic is
     * discarded along with it.
     *
     * <p>Must be called from inside a {@link Line#withModification(Runnable)} bracket.
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

        // isBlank, not isEmpty, to match StaffElement.setLyricForVerse: it drops the entry for
        // any blank text, so whitespace-only text must not be treated as a syllable here or the
        // successor would be re-lettered as though one followed it.
        var letters = !isCarrier && !text.isBlank();

        // Stricter than isWordEnd alone: a lyric with no text of its own continues nothing,
        // whatever the caller asked for. Callers that pre-compute a commit's continues-status
        // from isWordEnd by itself (LyricEditor.CommitIntent) must therefore never pass a
        // continuing request with blank text, or their answer and this one diverge.
        var continuesIntoNext = letters && !isWordEnd;

        Lyric.@Nullable Syllabic syllabic;

        if (isCarrier) {
            syllabic = null;
        } else if (letters) {
            syllabic = deriveSyllabic(previousLyricContinues(index, verse), continuesIntoNext);
        } else {
            syllabic = Lyric.Syllabic.SINGLE;
        }

        var compound = continuesIntoNext && isCompound;

        trace("writeLyricForVerse({}, '{}', {}): syllabic={}, compound={}",
            index, text, extend, syllabic, compound);

        var element = getElement(index);

        // Skip a write that would store what is already there, as setSyllableBoundary and
        // fixSuccessorSyllabic do: an ElementModification costs two element clones plus an undo
        // entry the user could step back into for no visible change.
        if (!storesSameLyric(element.getLyricForVerse(verse), text, extend, syllabic, compound)) {
            modifyElement(index, ElementField.LYRIC, () ->
                element.setLyricForVerse(verse, syllabic, compound, text, extend));
        }

        if (letters) {
            fixSuccessorSyllabic(index, verse, continuesIntoNext);
        }
    }

    /**
     * Whether {@code existing} already holds exactly what {@link #writeLyricForVerse} would store
     * for these values, making the write a no-op. Mirrors {@link StaffElement#setLyricForVerse}'s
     * truth table, and deliberately reports "different" for the combinations that method rejects
     * (text on a carrier, blank text with {@link Lyric.Extend#START}) so the write still runs and
     * still throws.
     */
    private static boolean storesSameLyric(@Nullable Lyric existing, String text,
            Lyric.Extend extend, Lyric.@Nullable Syllabic syllabic, boolean compound) {
        var isBlank = text.isBlank();

        if (Lyric.isCarrier(extend)) {
            // A carrier's stored state is fixed by its extend alone: empty text, null syllabic,
            // not compound.
            return isBlank && existing != null && existing.extend() == extend;
        }

        if (isBlank) {
            // Blank text with NONE removes the verse entry rather than storing anything.
            return extend == Lyric.Extend.NONE && existing == null;
        }

        return existing != null
            && existing.text().equals(text)
            && existing.extend() == extend
            && existing.syllabic() == syllabic
            && existing.compound() == compound;
    }

    private void fixSuccessorSyllabic(int index, int verse, boolean predecessorContinues) {
        var nextIndex = nextLyricBearingIndex(index, verse);

        if (nextIndex < 0) {
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
                replaceSyllabicAndCompound(index, verse, newSyllabic, lyric.compound()));
        }
    }

    private void replaceSyllabicAndCompound(int index, int verse,
            Lyric.@Nullable Syllabic syllabic, boolean compound) {
        var element = getElement(index);
        var lyrics = element.lyrics;
        var lyricIndex = element.indexOfLyricForVerse(verse);
        var lyric = lyrics.get(lyricIndex);
        lyrics.set(lyricIndex, new Lyric(
            lyric.verse(), lyric.text(), lyric.extend(), syllabic, compound));
    }

    private boolean previousLyricContinues(int index, int verse) {
        var prevIndex = previousTextBearingIndex(index, verse);

        if (prevIndex < 0) {
            return false;
        }

        var prevLyric = getElement(prevIndex).getLyricForVerse(verse);
        return prevLyric != null && Lyric.syllabicContinues(prevLyric.syllabic());
    }

    private int nextLyricBearingIndex(int index, int verse) {
        for (var i = index + 1; i < elementCount(); i++) {
            if (getElement(i).getLyricForVerse(verse) != null) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Walks backward from {@code fromIndex - 1} and returns the index of the first element whose
     * {@code verse} lyric is non-null, or {@code -1} if there is none.
     */
    default int previousLyricBearingIndex(int fromIndex, int verse) {
        for (var i = fromIndex - 1; i >= 0; i--) {
            if (getElement(i).getLyricForVerse(verse) != null) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Walks backward from {@code fromIndex - 1} and returns the index of the first element
     * carrying a text-bearing {@code verse} syllable, or {@code -1} if there is none.
     * Melisma carriers are skipped: they hold no syllable, and so no syllabic chain
     * position, of their own.
     */
    private int previousTextBearingIndex(int fromIndex, int verse) {
        for (var i = fromIndex - 1; i >= 0; i--) {
            var lyric = getElement(i).getLyricForVerse(verse);

            if (lyric != null && lyric.syllabic() != null) {
                return i;
            }
        }

        return -1;
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

        if (forwardIndex < 0) {
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

        if (backIndex >= 0) {
            var backLyric = getElement(backIndex).getLyricForVerse(verse);

            if (backLyric == null) {
                return;
            }

            if (backLyric.extend() == Lyric.Extend.CONTINUE) {
                trace("adjustNeighborsForLyricDeletion({}): closing chain, {} CONTINUE -> STOP",
                    index, backIndex);
                var backElement = getElement(backIndex);
                modifyElement(backIndex, ElementField.LYRIC, () ->
                    backElement.setLyricForVerse(verse,
                        backLyric.syllabic(), backLyric.compound(), backLyric.text(),
                        Lyric.Extend.STOP));
                return;
            }

            endDanglingWord(backIndex, verse);
            return;
        }

        // No predecessor — fix the successor if it now lacks a continuing predecessor.
        fixSuccessorSyllabic(index, verse, false);
    }

    /**
     * Ends the word at {@code index} when the syllable there continues into a next one that
     * is not there — no text-bearing lyric follows it in this verse. A melisma carrier is
     * not that successor: it holds no syllable of its own for a hyphen to reach.
     *
     * <p>No-op when {@code index} is negative, when the element carries no {@code verse}
     * lyric, or when the syllable continues into a syllable that does exist. Must be called
     * inside a modification bracket.
     */
    private void endDanglingWord(int index, int verse) {
        if (index < 0) {
            return;
        }

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
        modifyElement(index, ElementField.LYRIC, () ->
            element.setLyricForVerse(verse, null, false, null, Lyric.Extend.NONE));
    }

    /** The verse number of every lyric on {@code element}, in list order. */
    private static List<Integer> verseNumbersOf(StaffElement element) {
        var verses = new ArrayList<Integer>();

        for (var lyric : element.lyrics) {
            verses.add(lyric.verse());
        }

        return verses;
    }

    /**
     * Moves the {@code verse} syllable from {@code fromIndex} to {@code toIndex}, replacing
     * whatever lyric the target held. Used in both directions by the automatic grace-host
     * melisma: host→grace when a pair is formed (the syllable belongs to the grace), and
     * grace→host when the grace alone is deleted (the host is an ordinary note again).
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

        // Read off the source before anything is written: setLyricForVerse drops the
        // target's existing verse entry before adding the incoming syllable.
        var targetElement = getElement(toIndex);
        modifyElement(toIndex, ElementField.LYRIC, () ->
            targetElement.setLyricForVerse(verse, sourceLyric.syllabic(), sourceLyric.compound(),
                sourceLyric.text(), Lyric.Extend.NONE));
        removeLyricForVerse(fromIndex, verse);
    }

    /**
     * Moves every verse's syllable from {@code fromIndex} to {@code toIndex} via
     * {@link #transferLyricForVerse}. Verse enumeration lives here because a caller
     * outside this package cannot see an element's lyric list.
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
                hostElement.setLyricForVerse(verse, null, false, null, Lyric.Extend.STOP));

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
        var lyricIndex = element.indexOfLyricForVerse(verse);

        if (lyricIndex < 0) {
            return;
        }

        var lyric = element.lyrics.get(lyricIndex);

        if (lyric.extend() == extend) {
            return;
        }

        modifyElement(index, ElementField.LYRIC, () ->
            element.lyrics.set(lyricIndex,
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
     * {@link #cascadeClearExtend} for the orphaned carriers at the head,
     * {@link #adjustPrecedingForStopDeletion} for the chain left open at the tail, and
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
        var firstIndex = nextLyricBearingIndex(-1, verse);

        // Unreachable from endDanglingChains, which only ever passes a verse it found on
        // this run's own elements. Kept so the method stays safe to call with any verse.
        if (firstIndex < 0) {
            return;
        }

        var firstLyric = getElement(firstIndex).getLyricForVerse(verse);

        // A carrier at the head sustains a melisma that began outside the run, so its
        // START is gone and it extends nothing — exactly what deleting that START leaves
        // behind. A text-bearing head is left alone: a melisma it starts is the run's own.
        if (firstLyric != null && firstLyric.isCarrier()) {
            cascadeClearExtend(firstIndex, verse);
        }

        // The head has no predecessor at all, so its syllable cannot be continuing one.
        // -1 is that missing predecessor, the same argument adjustNeighborsForLyricDeletion
        // passes when the deleted lyric had none.
        fixSuccessorSyllabic(-1, verse, false);

        var effectiveCount = effectiveElementCount();

        // The tail's chain never reaches its STOP, which is the deleted-STOP case.
        adjustPrecedingForStopDeletion(previousLyricBearingIndex(effectiveCount, verse), verse);

        endDanglingWord(previousTextBearingIndex(effectiveCount, verse), verse);
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
