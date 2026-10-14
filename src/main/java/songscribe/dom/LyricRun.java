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
import java.util.EnumSet;
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
 * <p>Two chains run through a script's lyrics, both stored on the individual syllables rather
 * than as spans, so both are repaired element by element whenever an edit changes who a
 * syllable's neighbors are: the <b>syllabic chain</b> ({@link Lyric.Syllabic} plus
 * {@link Lyric#compound()}) and the <b>melisma chain</b> ({@link Lyric.Extend}). A chain member
 * points at its neighbors implicitly, by position, so a chain that leaves the run points at
 * whatever happens to sit there.
 *
 * <p>An interface rather than part of {@link Line} because a clipboard
 * {@link songscribe.ui.clipboard.Fragment} is a run with the same chains, but no line and no undo
 * history. The one abstract part is what to do with a repair once applied: {@link Line} records
 * an {@code ElementModification}, {@link DetachedLyricRun} does nothing.
 *
 * <p><b>Mutation contract:</b> every repair goes through
 * {@link #modifyElement(int, ElementField, Runnable)}, and on a {@code Line} that method must be
 * called inside an open modification bracket. The per-method "must be called inside a
 * modification bracket" notes are that requirement, and they bind every caller holding a
 * {@code Line}.
 */
public interface LyricRun extends StaffElementRun {

    Logger LOG = LoggerFactory.getLogger(LyricRun.class);

    /**
     * Makes the change recoverable if the run requires it.
     */
    void modifyElement(int index, ElementField field, Runnable mutator);

    /**
     * A chain may cross the gap only where the caller says so. An element that holds no lyric for
     * a script is no neighbor in that script, so the sides are found past any rest, barline,
     * breath mark or key change.
     *
     * <p>Must be called inside a modification bracket.
     */
    private void settleChainsAcross(int gapIndex, Predicate<Song.Script> scriptsThatMayCross) {
        for (var script : scripts()) {
            var mayCross = scriptsThatMayCross.test(script);

            settleMelismaAcross(gapIndex, script, mayCross);
            settleWordAcross(gapIndex, script, mayCross);
        }
    }

    private void settleMelismaAcross(int gapIndex, Song.Script script, boolean mayCross) {
        var beforeIndex = previousLyricBearingIndex(gapIndex, script);
        var afterIndex = lyricBearingIndexAtOrAfter(gapIndex, script);
        var beforeLyric = beforeIndex == null ? null : getElement(beforeIndex).getLyricForScript(script);
        var afterLyric = afterIndex == null ? null : getElement(afterIndex).getLyricForScript(script);
        var isOpenBefore = beforeLyric != null
            && (beforeLyric.extend() == Lyric.Extend.START || beforeLyric.extend() == Lyric.Extend.CONTINUE);
        var isCarriedAfter = afterLyric != null && afterLyric.isCarrier();
        var crosses = mayCross && isOpenBefore && isCarriedAfter;

        if (crosses) {
            return;
        }

        if (isOpenBefore && beforeIndex != null) {
            closeMelismaAt(beforeIndex, script);
        }

        if (isCarriedAfter && afterIndex != null) {
            removeCarriersFrom(afterIndex, script);
        }
    }

    private void settleWordAcross(int gapIndex, Song.Script script, boolean mayCross) {
        var beforeIndex = previousTextBearingIndex(gapIndex, script);

        if (beforeIndex != null) {
            var beforeLyric = getElement(beforeIndex).getLyricForScript(script);
            var crosses = mayCross && hasFollowingTextBearingLyric(beforeIndex, script);

            if (beforeLyric != null && isWordContinuingLyric(beforeLyric) && !crosses) {
                setSyllableBoundary(beforeIndex, script, true, false);
            }
        }

        fixSuccessorSyllabic(gapIndex - 1, script, previousLyricContinues(gapIndex, script));
    }

    /**
     * Must be called inside a modification bracket, <em>before</em>
     * {@link Line#addElement(int, StaffElement)}, while the pre-insertion indices still hold.
     *
     * <p>The run is judged by {@link ElementType#interruptsLyricChain()} as a whole: one
     * interrupting element in it breaks the chains for the run.
     *
     * <p>Only the lyric half of the repair belongs here; the glissando half runs after the
     * insertion — see {@link #repairNeighborsAfterInsertion}. A path that inserts first and
     * records later wants {@link #repairNeighborsAfterUntrackedInsertion} instead.
     *
     * @param insertedRun must not be empty
     */
    default void repairNeighborsBeforeInsertion(int insertionIndex, List<? extends StaffElement> insertedRun) {
        if (!interruptsLyricChains(insertedRun)) {
            return;
        }

        settleChainsAcross(insertionIndex, script -> false);
    }

    default void repairNeighborsAfterInsertion(int insertedIndex, StaffElement inserted) {
        repairNeighborsAfterInsertion(insertedIndex, List.of(inserted));
    }

    /**
     * Must be called from inside a {@link Song#withModification(Runnable)} bracket.
     *
     * @param index any value {@link #hasGlissandoInto} accepts
     */
    default void removeGlissandoInto(int index) {
        if (hasGlissandoInto(index) && getElement(index - 1) instanceof NoteElement note) {
            modifyElement(index - 1, ElementField.SLIDE, note::removeSlide);
        }
    }

    /**
     * @param run must not be empty
     */
    private static boolean interruptsLyricChains(List<? extends StaffElement> run) {
        return run.stream().anyMatch(element -> element.getType().interruptsLyricChain());
    }

    /**
     * The glissando is broken by the <em>first</em> element of the run, which is now standing
     * behind the predecessor. It does not read the rule the lyric chains read: a barline between
     * a note and its glissando target orphans it however transparent it is to a word.
     *
     * <p>Must be called inside a modification bracket, <em>after</em> every
     * {@link Line#addElement(int, StaffElement)} of the run.
     *
     * @param insertedRun must not be empty
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
     * Grace mode inserts its grace note with mutation tracking suspended, where a repair would be
     * applied but never recorded; it defers the repairs to the bracket that makes the grace-host
     * pairing undoable and calls this from there.
     *
     * <p>Must be called inside a modification bracket. The repairs are recorded after the
     * insertion they belong to, which reverse-order undo needs.
     */
    default void repairNeighborsAfterUntrackedInsertion(int insertedIndex) {
        var insertedRun = List.of(getElement(insertedIndex));

        if (interruptsLyricChains(insertedRun)) {
            // The inserted element holds no lyric yet, so the gap it opened is found on either
            // side of it from its own index.
            settleChainsAcross(insertedIndex, script -> false);
        }

        repairNeighborsAfterInsertion(insertedIndex, insertedRun);
    }

    /**
     * Across a range in which a word ends, what stands before it and what stands after it are
     * different words, so no word may close up over it once it is gone.
     */
    default Set<Song.Script> scriptsWithWordEndIn(ElementRange range) {
        var scripts = EnumSet.noneOf(Song.Script.class);

        for (var index = range.begin(); index <= range.end(); index++) {
            for (var lyric : getElement(index).getLyrics()) {
                if (!lyric.isCarrier() && !isWordContinuingLyric(lyric)) {
                    scripts.add(lyric.script());
                }
            }
        }

        return scripts;
    }

    /**
     * Must be called inside a modification bracket, <em>after</em> the removal. The repairs are
     * recorded after the removal they belong to, which reverse-order undo needs.
     *
     * @param scriptsWithWordEnd what {@link #scriptsWithWordEndIn} answered for the range before
     *     it was removed
     */
    default void repairChainsAfterDeletion(int gapIndex, Set<Song.Script> scriptsWithWordEnd) {
        settleChainsAcross(gapIndex, script -> !scriptsWithWordEnd.contains(script));
    }

    private void removeCarriersFrom(int startIndex, Song.Script script) {
        for (var i = startIndex; i < effectiveElementCount(); i++) {
            var element = getElement(i);
            var lyric = element.getLyricForScript(script);

            if (lyric == null) {
                continue;
            }

            if (!lyric.isCarrier()) {
                break;
            }

            // A carrier has no text of its own, so clearing its extend would strand an empty
            // lyric — one that still counts as lyric-bearing for lyric navigation. Drop the
            // entry instead.
            modifyElement(i, ElementField.LYRIC, () -> element.removeLyricForScript(script));

            if (lyric.extend() == Lyric.Extend.STOP) {
                break;
            }
        }
    }

    private void closeMelismaAt(int index, Song.Script script) {
        var lyric = getElement(index).getLyricForScript(script);

        if (lyric == null) {
            return;
        }

        var existingExtend = lyric.extend();

        if (existingExtend != Lyric.Extend.CONTINUE && existingExtend != Lyric.Extend.START) {
            return;
        }

        var newExtend = existingExtend == Lyric.Extend.CONTINUE ? Lyric.Extend.STOP : Lyric.Extend.NONE;

        setExtendForScript(index, script, newExtend);
    }

    /**
     * Idempotent.
     */
    default void backfillSyllabic() {
        for (var index = 0; index < elementCount(); index++) {
            // Snapshot the scripts first: backfillSyllabicAt writes the element's lyrics.
            for (var script : scriptsOf(getElement(index))) {
                backfillSyllabicAt(index, script);
            }
        }
    }

    /**
     * Writes the element directly, outside any bracket — the caller is responsible for any
     * required modification bracket.
     */
    private void backfillSyllabicAt(int index, Song.Script script) {
        var element = getElement(index);
        var lyric = element.getLyricForScript(script);

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
        var prevIndex = previousLyricBearingIndex(index, script);
        var prevLyric = prevIndex == null ? null : getElement(prevIndex).getLyricForScript(script);
        var prevContinues = prevLyric != null && Lyric.syllabicContinues(prevLyric.syllabic());

        // A best-guess load assigns BEGIN to mean "this syllable continues to the next".
        var derived = deriveSyllabic(prevContinues, Lyric.syllabicContinues(lyric.syllabic()));

        if (derived == lyric.syllabic()) {
            return;
        }

        element.putLyric(new Lyric(lyric.script(), lyric.text(), lyric.extend(), derived, lyric.compound()));
    }

    /**
     * The lyric at {@code index} must already exist and must not be a carrier
     * ({@link Lyric.Extend#STOP} / {@link Lyric.Extend#CONTINUE}). Rewrites that would not change
     * the stored value are skipped, so no redundant {@code ElementModification} is emitted.
     *
     * <p>Must be called from inside a {@link Song#withModification(Runnable)} bracket.
     *
     * @param isCompound ignored when {@code isWordEnd} is {@code true}
     */
    default void setSyllableBoundary(int index, Song.Script script, boolean isWordEnd, boolean isCompound) {
        var lyric = getElement(index).getLyricForScript(script);

        if (lyric == null) {
            throw new IllegalStateException(
                "setSyllableBoundary requires existing lyric at index " + index + " script " + script);
        }

        if (lyric.syllabic() == null) {
            throw new IllegalStateException(
                "setSyllableBoundary cannot run on carrier lyric (extend=" + lyric.extend() + ')');
        }

        var prevContinues = previousLyricContinues(index, script);
        var newSyllabic = deriveSyllabic(prevContinues, !isWordEnd);

        var newCompound = !isWordEnd && isCompound;

        trace("setSyllableBoundary({}, wordEnd={}, compound={}): {} -> {}, prevContinues={}",
            index, isWordEnd, isCompound, lyric.syllabic(), newSyllabic, prevContinues);

        if (newSyllabic != lyric.syllabic() || newCompound != lyric.compound()) {
            modifyElement(index, ElementField.LYRIC, () ->
                replaceSyllabicAndCompound(index, lyric, newSyllabic, newCompound));
        }

        fixSuccessorSyllabic(index, script, !isWordEnd);
    }

    /**
     * Use this rather than {@link #setSyllableBoundary} whenever the text itself is being
     * written: deriving the syllabic here means one {@link ElementField#LYRIC} modification for
     * this element. {@code setSyllableBoundary} remains the entry point for re-lettering a lyric
     * whose text is <em>not</em> changing.
     *
     * <p>Blank text on a non-carrier is an erasure rather than a syllable, so this removes the
     * script's lyric outright.
     *
     * <p>Must be called from inside a {@link Song#withModification(Runnable)} bracket.
     *
     * @param text       blank for a carrier or an erased syllable
     * @param isCompound ignored when {@code isWordEnd} is {@code true}
     */
    default void writeLyricForScript(int index, Song.Script script, String text, Lyric.Extend extend,
            boolean isWordEnd, boolean isCompound) {
        var isCarrier = Lyric.isCarrier(extend);

        // isBlank, not isEmpty: whitespace-only text erases the syllable exactly as an empty
        // string does. Decided before any Lyric is derived or constructed, since Lyric's
        // constructor rejects blank text on a non-carrier outright.
        if (!isCarrier && text.isBlank()) {
            trace("writeLyricForScript({}, '{}', {}): blank, erasing", index, text, extend);
            removeLyricForScript(index, script);
            return;
        }

        // Stricter than isWordEnd alone: a lyric with no text of its own continues nothing,
        // whatever the caller asked for. Callers that pre-compute a commit's continues-status
        // from isWordEnd by itself (LyricEditor.CommitIntent) must therefore never pass a
        // continuing request with blank text, or their answer and this one diverge.
        var continuesIntoNext = !isCarrier && !isWordEnd;

        Lyric.@Nullable Syllabic syllabic = isCarrier
            ? null
            : deriveSyllabic(previousLyricContinues(index, script), continuesIntoNext);

        var compound = continuesIntoNext && isCompound;

        trace("writeLyricForScript({}, '{}', {}): syllabic={}, compound={}",
            index, text, extend, syllabic, compound);

        var element = getElement(index);

        // Skip a write that would store what is already there, as setSyllableBoundary and
        // fixSuccessorSyllabic do: an ElementModification costs two element clones plus an undo
        // entry the user could step back into for no visible change.
        if (!storesSameLyric(element.getLyricForScript(script), text, extend, syllabic, compound)) {
            var lyric = isCarrier
                ? Lyric.carrier(script, extend)
                : new Lyric(script, text, extend, syllabic, compound);
            modifyElement(index, ElementField.LYRIC, () -> element.putLyric(lyric));
        }

        if (!isCarrier) {
            fixSuccessorSyllabic(index, script, continuesIntoNext);
        }
    }

    /**
     * {@code text} is never blank here: {@link #writeLyricForScript} routes a blank non-carrier
     * syllable to {@link #removeLyricForScript} first.
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
     * @param index {@code null} when the successor is the run's first syllable, because the
     *     syllable at issue has no predecessor at all — a deleted head, or the head of a run
     * @param predecessorContinues always {@code false} when {@code index} is {@code null}
     */
    private void fixSuccessorSyllabic(@Nullable Integer index, Song.Script script, boolean predecessorContinues) {
        var nextIndex = textBearingIndexAtOrAfter(index == null ? 0 : index + 1, script);

        if (nextIndex == null) {
            trace("fixSuccessorSyllabic({}): no syllable follows", index);
            return;
        }

        var nextLyric = getElement(nextIndex).getLyricForScript(script);

        if (nextLyric != null) {
            trace("fixSuccessorSyllabic({}): successor {}", index, nextIndex);
            reletterSyllable(nextIndex, script, nextLyric, predecessorContinues);
        }
    }

    /**
     * Skips a write that would store what is already there, which would add an undo entry for
     * no visible change. {@code lyric} must be the element's current {@code script} lyric.
     *
     * <p>Must be called inside a modification bracket.
     */
    private void reletterSyllable(int index, Song.Script script, Lyric lyric, boolean predecessorContinues) {
        var newSyllabic = deriveSyllabic(predecessorContinues, Lyric.syllabicContinues(lyric.syllabic()));

        trace("reletterSyllable({}): {} -> {}, predecessorContinues={}",
            index, lyric.syllabic(), newSyllabic, predecessorContinues);

        if (newSyllabic != lyric.syllabic()) {
            modifyElement(index, ElementField.LYRIC, () ->
                replaceSyllabicAndCompound(index, lyric, newSyllabic, lyric.compound()));
        }
    }

    /**
     * {@code lyric} must be that element's current lyric for its script.
     */
    private void replaceSyllabicAndCompound(int index, Lyric lyric,
            Lyric.@Nullable Syllabic syllabic, boolean compound) {
        getElement(index).putLyric(new Lyric(
            lyric.script(), lyric.text(), lyric.extend(), syllabic, compound));
    }

    private boolean previousLyricContinues(int index, Song.Script script) {
        var prevIndex = previousTextBearingIndex(index, script);

        if (prevIndex == null) {
            return false;
        }

        var prevLyric = getElement(prevIndex).getLyricForScript(script);
        return prevLyric != null && Lyric.syllabicContinues(prevLyric.syllabic());
    }

    /**
     * {@code null} if there is none.
     */
    private @Nullable Integer firstLyricBearingIndex(Song.Script script) {
        return lyricBearingIndexAtOrAfter(0, script);
    }

    private @Nullable Integer lyricBearingIndexAtOrAfter(int start, Song.Script script) {
        for (var i = start; i < elementCount(); i++) {
            if (getElement(i).getLyricForScript(script) != null) {
                return i;
            }
        }

        return null;
    }

    /**
     * {@code null} if there is none.
     */
    default @Nullable Integer previousLyricBearingIndex(int fromIndex, Song.Script script) {
        for (var i = fromIndex - 1; i >= 0; i--) {
            if (getElement(i).getLyricForScript(script) != null) {
                return i;
            }
        }

        return null;
    }

    /**
     * {@code null} if there is none. Melisma carriers are skipped: they hold no syllable, and
     * so no syllabic chain position, of their own.
     */
    private @Nullable Integer previousTextBearingIndex(int fromIndex, Song.Script script) {
        for (var i = fromIndex - 1; i >= 0; i--) {
            var lyric = getElement(i).getLyricForScript(script);

            if (lyric != null && lyric.syllabic() != null) {
                return i;
            }
        }

        return null;
    }

    /**
     * {@code null} if there is none. Melisma carriers
     * are skipped, as {@link #previousTextBearingIndex} skips them going backward.
     */
    private @Nullable Integer textBearingIndexAtOrAfter(int start, Song.Script script) {
        for (var i = start; i < elementCount(); i++) {
            var lyric = getElement(i).getLyricForScript(script);

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
     * Melisma carriers between the two neither count nor hide it: a word runs on past its own
     * melisma.
     */
    default boolean hasFollowingTextBearingLyric(int fromIndex, Song.Script script) {
        return textBearingIndexAtOrAfter(fromIndex + 1, script) != null;
    }

    /**
     * The elements a span covers are sung on the syllable that opened it, so they hold no
     * syllable of their own.
     */
    static boolean opensLyricSpan(Lyric lyric) {
        return lyric.extend() == Lyric.Extend.START || isWordContinuingLyric(lyric);
    }

    /**
     * Where a lyric span that is open in front of {@code start} ends on this run, for
     * {@code script}. The span is open in front of {@code start} when the lyric just before it
     * {@linkplain #opensLyricSpan opens a span}, or when a span ran off the end of the run before
     * this one, as a melisma or a word does across a line break.
     *
     * <p>Walking forward from {@code start}, the span covers every element that holds no
     * {@code script} lyric and every {@link Lyric.Extend#CONTINUE} carrier. It ends at the first of:
     *
     * <ul>
     *   <li>a {@link Lyric.Extend#STOP} carrier, which closes the melisma and is covered;</li>
     *   <li>a text-bearing lyric, which starts a span of its own and is not covered;</li>
     *   <li>a repeat, which nothing runs through, and which is not
     *       covered;</li>
     *   <li>the end of the run, where the span stays open onto whatever follows the run.</li>
     * </ul>
     *
     * @param start the first element the span may cover; {@code elementCount()} for a span
     *     opened by the run's last element
     * @param script the script whose lyrics are read
     * @return the index of the last element the span covers, and whether the span is still open
     *     at the end of the run
     * @invariant {@code start - 1 <= result.lastCoveredIndex() < elementCount()}; it is
     *     {@code start - 1} when the span covers nothing on this run
     * @invariant {@code result.openAtRunEnd()} implies
     *     {@code result.lastCoveredIndex() == elementCount() - 1}
     */
    default LyricSpanEnd lyricSpanEnd(int start, Song.Script script) {
        for (var i = start; i < elementCount(); i++) {
            var element = getElement(i);

            if (!element.getType().bearsLyricChain()) {
                return new LyricSpanEnd(i - 1, false);
            }

            var lyric = element.getLyricForScript(script);

            if (lyric == null || lyric.extend() == Lyric.Extend.CONTINUE) {
                continue;
            }

            var lastCovered = lyric.extend() == Lyric.Extend.STOP ? i : i - 1;
            return new LyricSpanEnd(lastCovered, false);
        }

        return new LyricSpanEnd(elementCount() - 1, true);
    }

    /**
     * The end of a lyric span on one run, as {@link #lyricSpanEnd} finds it.
     *
     * @param lastCoveredIndex the index of the last element the span covers, or one less than
     *     where the walk started when it covers none
     * @param openAtRunEnd     {@code true} when the span reaches the end of the run unclosed, so it
     *     goes on over the leading elements of the run that follows, such as the next line
     */
    record LyricSpanEnd(int lastCoveredIndex, boolean openAtRunEnd) {
    }

    /**
     * Gated on {@code DEBUG_LYRICS} as well as the log level, since these run several times per
     * keystroke.
     */
    private static void trace(String format, @Nullable Object... args) {
        if (LogUtils.isTracingLyrics(LOG)) {
            LOG.debug(format, args);
        }
    }

    /**
     * A melisma is sung on a single non-hyphenated syllable, so the chain always ends the word
     * that ran into it.
     *
     * <p>Must be called inside a modification bracket, after the carrier at {@code index} has
     * been written.
     */
    default void adjustSuccessorAfterMelismaCarrier(int index, Song.Script script) {
        for (var i = index + 1; i < elementCount(); i++) {
            var lyric = getElement(i).getLyricForScript(script);

            if (lyric == null || lyric.syllabic() == null) {
                continue;
            }

            trace("adjustSuccessorAfterMelismaCarrier({}): successor {}", index, i);

            // A melisma is sung on one syllable, so the word running into it always ends at
            // the carrier: nothing before this successor continues into it.
            reletterSyllable(i, script, lyric, false);
            return;
        }
    }

    /**
     * Must be called inside a modification bracket, after the lyric at {@code index}
     * has already been cleared.
     */
    default void adjustNeighborsForLyricDeletion(int index, Song.Script script) {
        var backIndex = previousLyricBearingIndex(index, script);
        trace("adjustNeighborsForLyricDeletion({}): predecessor={}", index, backIndex);

        if (backIndex != null) {
            var backLyric = getElement(backIndex).getLyricForScript(script);

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

            endDanglingWord(backIndex, script);
            return;
        }

        fixSuccessorSyllabic(null, script, false);
    }

    /**
     * Must be called inside a modification bracket.
     */
    private void endDanglingWord(int index, Song.Script script) {
        var lyric = getElement(index).getLyricForScript(script);

        if (lyric == null
                || !isWordContinuingLyric(lyric)
                || hasFollowingTextBearingLyric(index, script)) {
            return;
        }

        trace("endDanglingWord({}): ending dangling word", index);

        setSyllableBoundary(index, script, true, false);
    }

    /**
     * Stronger than clearing a lyric's melisma extend, which keeps an empty-texted lyric that
     * still counts as lyric-bearing for lyric navigation.
     *
     * <p>Must be called inside a modification bracket.
     */
    default void removeLyricForScript(int index, Song.Script script) {
        if (index < 0 || index >= effectiveElementCount()) {
            return;
        }

        var element = getElement(index);

        if (element.getLyricForScript(script) == null) {
            return;
        }

        // Blank text + NONE is setLyricForScript's "remove the entry" case, which keeps the
        // removal inside the API that enforces the carrier/text-bearing invariants.
        modifyElement(index, ElementField.LYRIC, () -> element.removeLyricForScript(script));
    }

    private static List<Song.Script> scriptsOf(StaffElement element) {
        var scripts = new ArrayList<Song.Script>();

        for (var lyric : element.getLyrics()) {
            scripts.add(lyric.script());
        }

        return scripts;
    }

    /**
     * The melisma extend is not carried across: the target receives the syllable at
     * {@link Lyric.Extend#NONE}, and {@link #syncGraceHostMelisma} re-establishes an extender if
     * the pairing still calls for one.
     *
     * <p>No-op when the source has no lyric for the script or only a text-less carrier. Must be
     * called inside a modification bracket.
     */
    default void transferLyricForScript(int fromIndex, int toIndex, Song.Script script) {
        var elementCount = effectiveElementCount();

        if (fromIndex == toIndex
                || fromIndex < 0 || fromIndex >= elementCount
                || toIndex < 0 || toIndex >= elementCount) {
            return;
        }

        var sourceLyric = getElement(fromIndex).getLyricForScript(script);

        if (sourceLyric == null || sourceLyric.isCarrier()) {
            return;
        }

        // Read off the source before anything is written: putLyric replaces the target's
        // existing script entry with the incoming syllable.
        var targetElement = getElement(toIndex);
        modifyElement(toIndex, ElementField.LYRIC, () ->
            targetElement.putLyric(sourceLyric.withExtend(Lyric.Extend.NONE)));
        removeLyricForScript(fromIndex, script);
    }

    /**
     * Must be called inside a modification bracket.
     */
    default void transferLyrics(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= effectiveElementCount()) {
            return;
        }

        // Snapshot the scripts first: transferLyricForScript mutates the source's lyric list.
        for (var script : scriptsOf(getElement(fromIndex))) {
            transferLyricForScript(fromIndex, toIndex, script);
        }
    }

    /**
     * When a grace-host pair carries a lyric the syllable belongs to the grace and the host may
     * not carry one of its own, so a melisma must run from the grace across its host.
     *
     * <p>Idempotent, and derived from live state rather than from a transition: a call site only
     * invokes this inside the bracket that changed the pairing.
     *
     * <p>Melismas that merely pass through the pair are left alone: a host already marked
     * {@link Lyric.Extend#CONTINUE} keeps carrying its chain onward, and a host {@code STOP}
     * whose chain began before the grace is not this pair's to tear down.
     *
     * <p>Must be called inside a modification bracket.
     */
    default void syncGraceHostMelisma(int graceIndex) {
        var hostIndex = graceIndex + 1;

        if (graceIndex < 0 || hostIndex >= effectiveElementCount()) {
            return;
        }

        var isPaired = isPairedGraceNote(graceIndex);
        var scripts = new LinkedHashSet<>(scriptsOf(getElement(graceIndex)));
        scripts.addAll(scriptsOf(getElement(hostIndex)));

        for (var script : scripts) {
            syncGraceHostMelismaForScript(graceIndex, script, isPaired);
        }
    }

    /**
     * Normalizes every grace-host pair in this run to the automatic grace-host melisma.
     *
     * <p>When <em>both</em> elements carry a syllable, the grace's wins and the host's is
     * discarded. The grace is left alone when it holds a text-less carrier, since that carrier
     * belongs to a melisma that began before the pair.
     *
     * <p>Convergence is {@link #syncGraceHostMelisma}'s, so a repaired file lands in exactly the
     * state editing would have produced, and a second load is a no-op.
     *
     * <p>Intended for the load path. Mutation tracking must be suspended so the repair is silent.
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
     * A script the grace already occupies — with a syllable or with a carrier — is left
     * untouched.
     */
    private void moveHostSyllablesToGrace(int graceIndex) {
        var hostIndex = graceIndex + 1;
        var graceElement = getElement(graceIndex);

        // Snapshot the scripts first: transferLyricForScript mutates the host's lyric list.
        for (var script : scriptsOf(getElement(hostIndex))) {
            // transferLyricForScript ignores a text-less carrier, so a host STOP stays put.
            if (graceElement.getLyricForScript(script) == null) {
                transferLyricForScript(hostIndex, graceIndex, script);
            }
        }
    }

    private void syncGraceHostMelismaForScript(int graceIndex, Song.Script script, boolean isPaired) {
        var hostIndex = graceIndex + 1;
        var hostElement = getElement(hostIndex);
        var graceLyric = getElement(graceIndex).getLyricForScript(script);
        var hostLyric = hostElement.getLyricForScript(script);
        var graceCarries = graceLyric != null && graceLyric.isCarrier();
        var graceHasSyllable = graceLyric != null && !graceCarries && !graceLyric.text().isBlank();

        trace("syncGraceHostMelisma(grace={}, host={}): paired={} graceCarries={} graceHasSyllable={} host={}",
            graceIndex, hostIndex, isPaired, graceCarries, graceHasSyllable, hostLyric);

        if (isPaired && graceHasSyllable) {
            setExtendForScript(graceIndex, script, Lyric.Extend.START);

            // A host that already carries the extender onward needs no STOP of its own.
            if (hostLyric != null && hostLyric.isCarrier()) {
                return;
            }

            var hostHadSyllable = hostLyric != null;
            modifyElement(hostIndex, ElementField.LYRIC, () ->
                hostElement.putLyric(Lyric.carrier(script, Lyric.Extend.STOP)));

            // The host's own syllable is gone, so its neighbors' syllabic chain has to heal
            // exactly as it would after a deletion. adjustNeighborsForLyricDeletion documents
            // a cleared lyric as its precondition, and what sits here now is a carrier rather
            // than nothing — that is equivalent for its purposes, since it reads only the
            // neighbors of `index`, never `index` itself.
            if (hostHadSyllable) {
                adjustNeighborsForLyricDeletion(hostIndex, script);
            }

            return;
        }

        // Tear down only this pair's own two-element chain: a STOP reached by a chain that
        // began before the grace belongs to that chain, not to the pair.
        if (graceCarries || hostLyric == null || hostLyric.extend() != Lyric.Extend.STOP) {
            return;
        }

        removeLyricForScript(hostIndex, script);
        setExtendForScript(graceIndex, script, Lyric.Extend.NONE);
    }

    /**
     * Only valid for text-bearing lyrics.
     */
    private void setExtendForScript(int index, Song.Script script, Lyric.Extend extend) {
        var element = getElement(index);
        var lyric = element.getLyricForScript(script);

        if (lyric == null || lyric.extend() == extend) {
            return;
        }

        modifyElement(index, ElementField.LYRIC, () ->
            element.putLyric(
                new Lyric(lyric.script(), lyric.text(), extend, lyric.syllabic(), lyric.compound())));
    }

    private static Lyric.Syllabic deriveSyllabic(boolean prevContinues, boolean thisContinues) {
        if (prevContinues) {
            return thisContinues ? Lyric.Syllabic.MIDDLE : Lyric.Syllabic.END;
        }

        return thisContinues ? Lyric.Syllabic.BEGIN : Lyric.Syllabic.SINGLE;
    }

    /**
     * Ends every lyric chain that runs off either end of this run, so no syllable and no melisma
     * carrier is left pointing at an element the run does not contain. A run lifted out of a
     * longer one, such as a clipboard {@link songscribe.ui.clipboard.Fragment}, would otherwise
     * attach itself to whatever now sits next to it.
     *
     * <p>Idempotent.
     */
    default void endDanglingChains() {
        for (var script : scripts()) {
            endDanglingChainsForScript(script);
        }
    }

    private void endDanglingChainsForScript(Song.Script script) {
        var firstIndex = firstLyricBearingIndex(script);

        if (firstIndex == null) {
            return;
        }

        var firstLyric = getElement(firstIndex).getLyricForScript(script);

        // A carrier at the head sustains a melisma that began outside the run, so its
        // START is gone and it extends nothing — exactly what deleting that START leaves
        // behind. A text-bearing head is left alone: a melisma it starts is the run's own.
        if (firstLyric != null && firstLyric.isCarrier()) {
            removeCarriersFrom(firstIndex, script);
        }

        // The head has no predecessor at all, so its syllable cannot be continuing one.
        fixSuccessorSyllabic(null, script, false);

        var effectiveCount = effectiveElementCount();

        // The tail's chain never reaches its STOP, which is the deleted-STOP case.
        var tailIndex = previousLyricBearingIndex(effectiveCount, script);

        if (tailIndex != null) {
            closeMelismaAt(tailIndex, script);
        }

        var lastTextIndex = previousTextBearingIndex(effectiveCount, script);

        if (lastTextIndex != null) {
            endDanglingWord(lastTextIndex, script);
        }
    }

    /**
     * Each script once, in first-seen order.
     */
    default List<Song.Script> scripts() {
        var scripts = new LinkedHashSet<Song.Script>();

        for (var index = 0; index < effectiveElementCount(); index++) {
            scripts.addAll(scriptsOf(getElement(index)));
        }

        return List.copyOf(scripts);
    }
}
