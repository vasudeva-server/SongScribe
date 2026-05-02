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
package songscribe.music;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
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
import songscribe.message.mutation.RangeElementAddition;
import songscribe.message.mutation.RangeElementRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.layout.ScaleContext;

public class Line {

    private static final int[][] FLAT_SHARP_ORDINAL = new int[][]{
        new int[]{0, 3, 6, 2, 5, 1, 4},
        new int[]{4, 1, 5, 2, 6, 3, 0},
    };

    private final SpanSet<BeamSpan> beamings = new SpanSet<>();
    private final SpanSet<TieSpan> ties = new SpanSet<>();
    private final SpanSet<TupletSpan> tuplets = new SpanSet<>();
    private final SpanSet<DynamicsSpan> crescendo = new SpanSet<>();
    private final SpanSet<DynamicsSpan> diminuendo = new SpanSet<>();
    @SuppressWarnings("rawtypes")
    private final SpanSet[] spanSets = new SpanSet[]{
        beamings,
        ties,
        tuplets,
        crescendo,
        diminuendo,
    };

    // =========================================================================
    // New storage for Phase 4+ layout redesign
    // These will replace SpanSets after Phase 7 (IO) migration
    // =========================================================================

    /** Range elements (ties, trills, crescendo, diminuendo, tuplets, endings). */
    private final List<RangeElement> rangeElements = new ArrayList<>();

    @SuppressWarnings("NullAway") // set by setSong() before the Line is used
    private Song song;
    private int keys = 0;
    @Nullable
    private KeyType keyType = null;
    private final List<StaffElement> elements = new ArrayList<>();

    // ---------------------------------------------------------------------
    // Legacy View Properties (Y positions relative to middleLineY)
    // ---------------------------------------------------------------------
    // DEPRECATED: These line-level Y position fields are retained for backward
    // compatibility with legacy documents (pre-Phase 11). New code should use
    // per-instance offsets on the element objects themselves:
    //   - Tempo/BeatChange: use Attachment.getUserYOffset()
    //   - Endings: use Ending.getYPosition()
    //   - Trills: use Trill.getYPosition()
    //   - Annotations: use Annotation.getUserYOffset()
    //
    // When loading legacy documents, FormatMigrator converts these line-level
    // offsets to per-instance offsets. LineIO no longer writes these fields
    // to new documents.
    // ---------------------------------------------------------------------

    /**
     * Y offset for tempo change display. Line 0 default: -40, others: -24.
     *
     * @deprecated Use per-instance userYOffset on TempoChangeAttachment instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int tempoChangeYPosPx = 0;

    /**
     * Default beat change Y position (-3 staff spaces above middle)
     */
    public static final double BEAT_CHANGE_DEFAULT_Y_SS = -3.0;  // -24px
    /**
     * Y offset for beat change display (default: -24, above staff).
     *
     * @deprecated Use per-instance userYOffset on BeatChangeAttachment instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int beatChangeYPosPx = ScaleContext.getInstance().toRoundedPixels(BEAT_CHANGE_DEFAULT_Y_SS);

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
     * Y offset for first/second ending display (default: -25, above staff).
     *
     * @deprecated Use per-instance yPosition on Ending objects instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int firstSecondEndingYPosPx = ScaleContext.getInstance().toRoundedPixels(ENDING_DEFAULT_Y_SS);

    /**
     * Default trill Y position (above staff)
     */
    public static final double TRILL_DEFAULT_Y_SS = -3.375;  // -27px
    /**
     * Y offset for trill display (default: -27, above staff).
     *
     * @deprecated Use per-instance yPosition on Trill objects instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int trillYPosPx = ScaleContext.getInstance().toRoundedPixels(TRILL_DEFAULT_Y_SS);

    /** Ratio multiplier for horizontal element spacing (default: 1.0, user-adjustable). */
    private float elementSpacingRatio = 1f;

    public Song getSong() {
        return song;
    }

    public void setSong(Song song) {
        this.song = song;
    }

    public int getKeyAccidentalCount() {
        return keys;
    }

    /**
     * Applies a single mutation, delegating to the parent song's bracket.
     *
     * <p>When the line is attached to a song, a modification bracket must be
     * open — the mutation is recorded and the mutator runs inside it. If the line is
     * not yet attached (e.g. during file-load bootstrap before {@code setSong}),
     * or the song has suspended tracking via
     * {@link Song#withoutMutationTracking(Runnable)}, the mutator runs directly
     * without tracking.
     *
     * @throws IllegalStateException if the line is attached to a song that has
     *     neither an open modification bracket nor suspended tracking
     */
    public void applyChange(Mutation mutation, Runnable mutator) {
        if (song == null || song.isMutationTrackingSuspended()) {
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
     * If the song is not yet set, {@code body} runs directly.
     */
    public void withModification(Runnable body) {
        if (song != null) {
            song.withModification(body);
        } else {
            body.run();
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

    public void addElement(StaffElement element) {
        element.setLine(this);
        element.setParentLine(this);
        // When the line ends with the auto-maintained terminal, insert the new
        // element before it so the terminal remains the last element.
        var lastIdx = elements.size() - 1;
        var insertBeforeFinal = lastIdx >= 0
            && song != null
            && song.isAutoMaintainedTerminal(elements.get(lastIdx), this);
        var index = insertBeforeFinal ? lastIdx : elements.size();

        applyChange(
            new ElementInsertion(this, index, element),
            () -> {
                elements.add(index, element);
                if (insertBeforeFinal) {
                    shiftSpans(spanSets, index, 1);
                }
            }
        );
    }

    /**
     * Returns {@code true} when the terminal mutation guards in this class should
     * be bypassed: the line has no parent song yet, mutation tracking is suspended
     * (test setup), or the song is currently auto-maintaining the invariant.
     */
    private boolean isTerminalGuardBypassed() {
        return song == null
            || song.isMutationTrackingSuspended()
            || song.isInAutoMaintenance();
    }

    public void addElement(int index, StaffElement element) {
        if (!isTerminalGuardBypassed()
                && element.getType() == ElementType.FINAL_DOUBLE_BARLINE
                && (song.indexOfLine(this) != song.lineCount() - 1
                    || index != elementCount())) {
            throw new IllegalStateException(
                "FINAL_DOUBLE_BARLINE may only be appended to the last line");
        }

        element.setLine(this);
        element.setParentLine(this);
        var tuplet = tuplets.findSpan(index);

        if (tuplet != null && index > tuplet.start) {
            removeTuplet(tuplet);
        }

        var insertedType = element.getType();
        var endingsToRemove = rangeElements.stream()
            .filter(r -> r instanceof Ending e && e.isInvalidatedByInsertion(index, insertedType, this))
            .toList();

        // When prepending to a non-empty first line, the previous first element
        // carried the initial tempo — move it to the new first element.
        var displacedFirstElement = (index == 0
            && !elements.isEmpty()
            && song != null
            && song.indexOfLine(this) == 0)
            ? elements.get(0)
            : null;
        var displacedTempo = displacedFirstElement != null ? displacedFirstElement.getTempoChange() : null;

        applyChange(
            new ElementInsertion(this, index, element),
            () -> {
                elements.add(index, element);
                shiftSpans(spanSets, index, 1);

                if (displacedFirstElement != null) {
                    displacedFirstElement.setTempoChange(null);
                    element.setTempoChange(displacedTempo);
                }

                rangeElements.removeIf(endingsToRemove::contains);
            }
        );
    }

    public void setElement(int index, StaffElement element) {
        if (!isTerminalGuardBypassed()
                && element.getType() == ElementType.FINAL_DOUBLE_BARLINE
                && (song.indexOfLine(this) != song.lineCount() - 1
                    || index != elementCount() - 1)) {
            throw new IllegalStateException(
                "FINAL_DOUBLE_BARLINE may only replace the last element on the last line");
        }

        var oldElement = elements.get(index);
        // Pre-compute before the mutator so findRepeatSplitElement sees the pre-replacement line.
        var endingsToRemove = rangeElements.stream()
            .filter(r -> r instanceof Ending ending &&
                         ending.isInvalidatedByReplacement(oldElement, element, this))
            .toList();

        applyChange(
            new ElementReplacement(this, index, oldElement, element),
            () -> {
                element.setLine(this);
                element.setParentLine(this);
                elements.set(index, element);
                rangeElements.removeIf(endingsToRemove::contains);

                // Update stale anchor/end references in surviving endings so that
                // getAnchorElementIndex()/getEndElementIndex() remain valid after the swap.
                for (var r : rangeElements) {
                    if (r instanceof Ending ending) {
                        if (ending.getAnchorElement() == oldElement) {
                            ending.setAnchorElement(element);
                        }

                        if (ending.getEndElement() == oldElement) {
                            ending.setEndElement(element);
                        }
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
    public void modifyElement(int index, ElementField field, Runnable mutator) {
        modifyElement(index, EnumSet.of(field), mutator);
    }

    public void modifyElement(int index, EnumSet<ElementField> fields, Runnable mutator) {
        if (!Collections.disjoint(fields, ElementField.DURATION_AFFECTING)) {
            removeOverlappingTuplets(index, index);
        }

        var beforeClone = elements.get(index).clone();
        applyChange(new ElementModification(this, index, fields, beforeClone), mutator);
    }

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
    public void adjustSyllablesForNeighborChange(int prevIndex, @Nullable StaffElement deletedElement) {
        if (prevIndex < 0 || prevIndex >= elements.size()) {
            return;
        }

        var prevElement = elements.get(prevIndex);
        var lyrics = prevElement.properties.lyrics;
        var indicesToClear = new ArrayList<Integer>();

        for (var j = 0; j < lyrics.size(); j++) {
            var lyric = lyrics.get(j);
            var syllabic = lyric.syllabic();

            if (syllabic != Lyric.Syllabic.BEGIN && syllabic != Lyric.Syllabic.MIDDLE) {
                continue;
            }

            if (deletedElement == null) {
                indicesToClear.add(j);
            } else {
                var matchingDeletedLyric = deletedElement.properties.lyrics.stream()
                    .filter(deletedLyric -> deletedLyric.verse() == lyric.verse())
                    .findFirst()
                    .orElse(null);

                var deletedSyllabic = matchingDeletedLyric != null ? matchingDeletedLyric.syllabic() : null;

                if (deletedSyllabic != Lyric.Syllabic.BEGIN && deletedSyllabic != Lyric.Syllabic.MIDDLE) {
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
                    ? elements.get(prevIndex - 1).getLyricForVerse(lyric.verse()) : null;
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
    public void adjustExtendsForDeletion(int deletedIndex) {
        if (deletedIndex < 0 || deletedIndex >= effectiveElementCount()) {
            return;
        }

        var deletedElement = elements.get(deletedIndex);

        for (var lyric : deletedElement.properties.lyrics) {
            switch (lyric.extend()) {
                case START -> cascadeClearExtend(deletedIndex + 1, lyric.verse());
                case STOP -> adjustPrecedingForStopDeletion(deletedIndex - 1, lyric.verse());
                default -> { }
            }
        }
    }

    private void cascadeClearExtend(int startIndex, int verse) {
        for (var i = startIndex; i < effectiveElementCount(); i++) {
            var element = elements.get(i);
            var lyricIndex = findLyricIndexForVerse(element, verse);

            if (lyricIndex < 0) {
                break;
            }

            var lyric = element.properties.lyrics.get(lyricIndex);

            if (lyric.extend() == Lyric.Extend.NONE) {
                break;
            }

            var isStop = lyric.extend() == Lyric.Extend.STOP;

            modifyElement(i, ElementField.LYRIC, () ->
                element.properties.lyrics.set(lyricIndex,
                    new Lyric(lyric.verse(), lyric.text(), Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false)));

            if (isStop) {
                break;
            }
        }
    }

    private void adjustPrecedingForStopDeletion(int precedingIndex, int verse) {
        if (precedingIndex < 0) {
            return;
        }

        var precedingElement = elements.get(precedingIndex);
        var lyricIndex = findLyricIndexForVerse(precedingElement, verse);

        if (lyricIndex < 0) {
            return;
        }

        var lyric = precedingElement.properties.lyrics.get(lyricIndex);
        var existingExtend = lyric.extend();

        if (existingExtend != Lyric.Extend.CONTINUE && existingExtend != Lyric.Extend.START) {
            return;
        }

        // CONTINUE → STOP: preceding note becomes the new terminus
        // START → NONE: 2-note chain collapses (a single note cannot carry a melisma)
        var newExtend = existingExtend == Lyric.Extend.CONTINUE ? Lyric.Extend.STOP : Lyric.Extend.NONE;

        modifyElement(precedingIndex, ElementField.LYRIC, () ->
            precedingElement.properties.lyrics.set(lyricIndex,
                new Lyric(lyric.verse(), lyric.text(), newExtend, lyric.syllabic(), lyric.compound())));
    }

    /**
     * Normalizes every {@link Lyric} on this line so that {@link Lyric#syllabic()} is
     * chain-consistent (SINGLE/BEGIN/MIDDLE/END match the word boundary implied by neighbors).
     * Idempotent. Used after document load for legacy files where {@code <syllabic>} may be
     * absent or only a best-guess value.
     */
    public void backfillSyllabic() {
        for (var index = 0; index < elements.size(); index++) {
            var lyrics = elements.get(index).properties.lyrics;

            for (var lyricIndex = 0; lyricIndex < lyrics.size(); lyricIndex++) {
                backfillSyllabicAt(index, lyrics.get(lyricIndex).verse());
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
        var lyrics = elements.get(index).properties.lyrics;
        var lyricIndex = findLyricIndexForVerse(elements.get(index), verse);

        if (lyricIndex < 0) {
            return;
        }

        var lyric = lyrics.get(lyricIndex);
        var extend = lyric.extend();

        // Carriers never need backfill — their syllabic is always null.
        if (extend == Lyric.Extend.STOP || extend == Lyric.Extend.CONTINUE) {
            return;
        }

        var prevContinues = false;

        for (var i = index - 1; i >= 0; i--) {
            var prevLyric = elements.get(i).getLyricForVerse(verse);

            if (prevLyric != null) {
                var prevSyllabic = prevLyric.syllabic();
                prevContinues = prevSyllabic == Lyric.Syllabic.BEGIN || prevSyllabic == Lyric.Syllabic.MIDDLE;
                break;
            }
        }

        // BEGIN was assigned by best-guess load to signal "this syllable continues to the next".
        var thisContinues = lyric.syllabic() == Lyric.Syllabic.BEGIN
                || lyric.syllabic() == Lyric.Syllabic.MIDDLE;
        Lyric.Syllabic derived;

        if (prevContinues) {
            derived = thisContinues ? Lyric.Syllabic.MIDDLE : Lyric.Syllabic.END;
        } else {
            derived = thisContinues ? Lyric.Syllabic.BEGIN : Lyric.Syllabic.SINGLE;
        }

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
     * <p>Must be called from inside a {@link #withModification(Runnable)} bracket.
     *
     * @param index      the element index on this line
     * @param verse      the verse number (typically 1)
     * @param isWordEnd  {@code true} when this syllable terminates the word (no continuation)
     * @param isCompound {@code true} when this syllable joins the next via a compound-word
     *                   boundary; ignored when {@code isWordEnd} is {@code true}
     */
    public void setSyllableBoundary(int index, int verse, boolean isWordEnd, boolean isCompound) {
        var lyric = elements.get(index).getLyricForVerse(verse);

        if (lyric == null) {
            throw new IllegalStateException(
                "setSyllableBoundary requires existing lyric at index " + index + " verse " + verse);
        }

        if (lyric.syllabic() == null) {
            throw new IllegalStateException(
                "setSyllableBoundary cannot run on carrier lyric (extend=" + lyric.extend() + ")");
        }

        var prevContinues = previousLyricContinues(index, verse);
        Lyric.Syllabic newSyllabic;

        if (isWordEnd) {
            newSyllabic = prevContinues ? Lyric.Syllabic.END : Lyric.Syllabic.SINGLE;
        } else {
            newSyllabic = prevContinues ? Lyric.Syllabic.MIDDLE : Lyric.Syllabic.BEGIN;
        }

        var newCompound = !isWordEnd && isCompound;

        if (newSyllabic != lyric.syllabic() || newCompound != lyric.compound()) {
            modifyElement(index, ElementField.LYRIC, () ->
                replaceSyllabicAndCompound(index, verse, newSyllabic, newCompound));
        }

        var nextIndex = nextLyricBearingIndex(index, verse);

        if (nextIndex < 0) {
            return;
        }

        var nextLyric = elements.get(nextIndex).getLyricForVerse(verse);

        if (nextLyric == null || nextLyric.syllabic() == null) {
            return;
        }

        var nextContinues = nextLyric.syllabic() == Lyric.Syllabic.BEGIN
            || nextLyric.syllabic() == Lyric.Syllabic.MIDDLE;
        var thisContinues = !isWordEnd;
        Lyric.Syllabic newNextSyllabic;

        if (thisContinues) {
            newNextSyllabic = nextContinues ? Lyric.Syllabic.MIDDLE : Lyric.Syllabic.END;
        } else {
            newNextSyllabic = nextContinues ? Lyric.Syllabic.BEGIN : Lyric.Syllabic.SINGLE;
        }

        if (newNextSyllabic != nextLyric.syllabic()) {
            modifyElement(nextIndex, ElementField.LYRIC, () ->
                replaceSyllabicAndCompound(nextIndex, verse, newNextSyllabic, nextLyric.compound()));
        }
    }

    private void replaceSyllabicAndCompound(int index, int verse,
            Lyric.@Nullable Syllabic syllabic, boolean compound) {
        var lyrics = elements.get(index).properties.lyrics;
        var lyricIndex = findLyricIndexForVerse(elements.get(index), verse);
        var lyric = lyrics.get(lyricIndex);
        lyrics.set(lyricIndex, new Lyric(
            lyric.verse(), lyric.text(), lyric.extend(), syllabic, compound));
    }

    private boolean previousLyricContinues(int index, int verse) {
        for (var i = index - 1; i >= 0; i--) {
            var prevLyric = elements.get(i).getLyricForVerse(verse);

            if (prevLyric != null && prevLyric.syllabic() != null) {
                return prevLyric.syllabic() == Lyric.Syllabic.BEGIN
                    || prevLyric.syllabic() == Lyric.Syllabic.MIDDLE;
            }
        }

        return false;
    }

    private int nextLyricBearingIndex(int index, int verse) {
        for (var i = index + 1; i < elements.size(); i++) {
            if (elements.get(i).getLyricForVerse(verse) != null) {
                return i;
            }
        }

        return -1;
    }

    private static int findLyricIndexForVerse(StaffElement element, int verse) {
        var lyrics = element.properties.lyrics;

        for (var i = 0; i < lyrics.size(); i++) {
            if (lyrics.get(i).verse() == verse) {
                return i;
            }
        }

        return -1;
    }

    /** Attaches the song's initial tempo to the first element of this line if not already set. */
    void attachInitialTempoIfNeeded() {
        if (elements.isEmpty() || song == null) {
            return;
        }

        var element = elements.get(0);

        if (element.getTempoChange() == null) {
            var initialTempo = song.getTempo();

            if (initialTempo != null) {
                element.setTempoChange(initialTempo);
            }
        }
    }

    public StaffElement getElement(int index) {
        return elements.get(index);
    }

    public List<StaffElement> getElements() {
        return elements;
    }

    // Returns a sublist of elements from start to end inclusive
    public List<StaffElement> getElements(int start, int end) {
        // subList is exclusive of the end index, so we add 1
        return elements.subList(start, end + 1);
    }

    public void removeElement(int index) {
        if (!isTerminalGuardBypassed()
                && song.isAutoMaintainedTerminal(elements.get(index), this)) {
            throw new IllegalStateException(
                "The auto-maintained terminal may not be removed");
        }

        removeOverlappingTuplets(index, index);
        var deleted = elements.get(index);
        var deletedList = List.of(deleted);
        var endingsToRemove = rangeElements.stream()
            .filter(r -> r instanceof Ending e && e.isInvalidatedByDeletion(deletedList, this))
            .toList();

        applyChange(
            new ElementDeletion(this, index, deleted),
            () -> {
                elements.remove(index);
                shiftSpans(spanSets, index, -1);
                rangeElements.removeIf(r ->
                    r.isInvalidatedBy(deletedList) || endingsToRemove.contains(r));
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
        if (!isTerminalGuardBypassed()
                && elements.subList(from, to + 1).stream()
                       .anyMatch(e -> song.isAutoMaintainedTerminal(e, this))) {
            throw new IllegalStateException(
                "The auto-maintained terminal may not be removed");
        }

        removeOverlappingTuplets(from, to);
        var deletedElements = List.copyOf(elements.subList(from, to + 1));
        var endingsToRemove = rangeElements.stream()
            .filter(r -> r instanceof Ending e && e.isInvalidatedByDeletion(deletedElements, this))
            .toList();

        applyChange(
            new ElementRangeDeletion(this, from, to, deletedElements),
            () -> {
                elements.subList(from, to + 1).clear();
                shiftSpans(spanSets, from, -(to - from + 1));
                rangeElements.removeIf(r ->
                    r.isInvalidatedBy(deletedElements) || endingsToRemove.contains(r));
            }
        );
    }

    public int elementCount() {
        return elements.size();
    }

    /**
     * Returns the element count excluding a trailing auto-maintained terminal
     * ({@code FINAL_DOUBLE_BARLINE} or {@code REPEAT_RIGHT}). Use this wherever a
     * computation should treat the song-owned terminal as if it were not there
     * (insertion spacing, preview positioning, etc.).
     */
    public int effectiveElementCount() {
        var count = elements.size();

        if (count > 0 && song != null
                && song.isAutoMaintainedTerminal(elements.get(count - 1), this)) {
            return count - 1;
        }

        return count;
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public int getElementIndex(StaffElement element) {
        return elements.indexOf(element);
    }

    /**
     * Returns whether inserting at the given index would conflict with a paired grace note.
     * A grace note is paired when it has a {@link StaffElement.Glissando.Type#CONNECTED} glissando
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
        int candidateIndex = index - 1;

        if (candidateIndex < 0) {
            return -1;
        }

        return getElement(candidateIndex).getType().isGraceNote() ? candidateIndex : -1;
    }

    public boolean isPairedGraceNote(int index) {
        if (index < 0 || index >= elements.size()) {
            return false;
        }

        var element = elements.get(index);

        if (!element.getType().isGraceNote()) {
            return false;
        }

        var glissando = element.getGlissando();
        return glissando != null
            && glissando.type == StaffElement.Glissando.Type.CONNECTED;
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

        var kt = keyType;
        return IntStream.range(0, keys).anyMatch(
            i -> FLAT_SHARP_ORDINAL[kt.ordinal() - 1][i] == pitchType
        );
    }

    /**
     * @deprecated Use per-instance userYOffset on TempoChangeAttachment instead.
     */
    @Deprecated
    public int getTempoChangeYPosPx() {
        return tempoChangeYPosPx;
    }

    /**
     * @deprecated Use per-instance userYOffset on TempoChangeAttachment instead.
     */
    @Deprecated
    public void setTempoChangeYPosPx(int tempoChangeYPosPx) {
        var old = this.tempoChangeYPosPx;
        applyChange(
            new LineLayoutChange(this, LineLayoutField.TEMPO_CHANGE_Y_POS_PX, old, tempoChangeYPosPx),
            () -> this.tempoChangeYPosPx = tempoChangeYPosPx
        );
    }

    /**
     * @deprecated Use per-instance userYOffset on BeatChangeAttachment instead.
     */
    @Deprecated
    public int getBeatChangeYPosPx() {
        return beatChangeYPosPx;
    }

    /**
     * @deprecated Use per-instance userYOffset on BeatChangeAttachment instead.
     */
    @Deprecated
    public void setBeatChangeYPosPx(int beatChangeYPosPx) {
        var old = this.beatChangeYPosPx;
        applyChange(
            new LineLayoutChange(this, LineLayoutField.BEAT_CHANGE_Y_POS_PX, old, beatChangeYPosPx),
            () -> this.beatChangeYPosPx = beatChangeYPosPx
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

    /**
     * @deprecated Use per-instance yPosition on Ending objects instead.
     */
    @Deprecated
    public int getFirstSecondEndingYPosPx() {
        return firstSecondEndingYPosPx;
    }

    /**
     * @deprecated Use per-instance yPosition on Ending objects instead.
     */
    @Deprecated
    public void setFirstSecondEndingYPosPx(int fsEndingYPosPx) {
        var old = firstSecondEndingYPosPx;
        applyChange(
            new LineLayoutChange(this, LineLayoutField.FIRST_SECOND_ENDING_Y_POS_PX, old, fsEndingYPosPx),
            () -> firstSecondEndingYPosPx = fsEndingYPosPx
        );
    }

    /**
     * @deprecated Use per-instance yPosition on Trill objects instead.
     */
    @Deprecated
    public int getTrillYPosPx() {
        return trillYPosPx;
    }

    /**
     * @deprecated Use per-instance yPosition on Trill objects instead.
     */
    @Deprecated
    public void setTrillYPosPx(int trillYPosPx) {
        var old = this.trillYPosPx;
        applyChange(
            new LineLayoutChange(this, LineLayoutField.TRILL_Y_POS_PX, old, trillYPosPx),
            () -> this.trillYPosPx = trillYPosPx
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

    public float getElementSpacingRatio() {
        return elementSpacingRatio;
    }

    public SpanSet<BeamSpan> getBeamings() {
        return beamings;
    }

    public SpanSet<TieSpan> getTies() {
        return ties;
    }

    public SpanSet<TupletSpan> getTuplets() {
        return tuplets;
    }

    public SpanSet<DynamicsSpan> getCrescendos() {
        return crescendo;
    }

    public SpanSet<DynamicsSpan> getDiminuendos() {
        return diminuendo;
    }

    public void addBeaming(BeamSpan span) {
        applyChange(new BeamingAddition(this, span), () -> beamings.addSpan(span));
    }

    public void removeBeaming(BeamSpan span) {
        applyChange(new BeamingRemoval(this, span), () -> beamings.removeSpan(span));
    }

    public void addTie(TieSpan span) {
        applyChange(new TieAddition(this, span), () -> ties.addSpan(span));
    }

    public void removeTie(TieSpan span) {
        applyChange(new TieRemoval(this, span), () -> ties.removeSpan(span));
    }

    public void addTuplet(TupletSpan span) {
        applyChange(new TupletAddition(this, span), () -> tuplets.addSpan(span));
    }

    public void removeTuplet(TupletSpan span) {
        applyChange(new TupletRemoval(this, span), () -> tuplets.removeSpan(span));
    }

    public void addCrescendo(DynamicsSpan span) {
        applyChange(new CrescendoAddition(this, span), () -> crescendo.addSpan(span));
    }

    public void removeCrescendo(DynamicsSpan span) {
        applyChange(new CrescendoRemoval(this, span), () -> crescendo.removeSpan(span));
    }

    public void addDiminuendo(DynamicsSpan span) {
        applyChange(new DiminuendoAddition(this, span), () -> diminuendo.addSpan(span));
    }

    public void removeDiminuendo(DynamicsSpan span) {
        applyChange(new DiminuendoRemoval(this, span), () -> diminuendo.removeSpan(span));
    }

    /**
     * Returns true if the given note index falls within any hairpin (crescendo or diminuendo) range.
     */
    public boolean isInHairpinRange(int noteIndex) {
        return crescendo.isInsideAnySpan(noteIndex) ||
            diminuendo.isInsideAnySpan(noteIndex);
    }

    public void removeSpan(int a, int b) {
        for (var is : spanSets) {
            is.removeSpan(a, b);
        }
    }

    public SpanSet<?>[] copySpans(int a, int b) {
        var retSpanSets = Arrays.stream(spanSets)
            .map(spanSet -> spanSet.copySpan(a, b))
            .toArray(SpanSet[]::new); //noinspection unchecked

        shiftSpans(retSpanSets, 0, -a);
        return retSpanSets;
    }

    public void pasteSpans(SpanSet<?>[] copySpanSets, int xIndex) {
        shiftSpans(copySpanSets, 0, xIndex);

        for (var i = 0; i < spanSets.length; i++) {
            for (var li = copySpanSets[i].listIterator(); li.hasNext(); ) {
                var span = li.next();
                //noinspection unchecked,rawtypes
                ((SpanSet) spanSets[i]).addSpan(span);
            }
        }

        shiftSpans(copySpanSets, 0, -xIndex);
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
        for (var tuplet : tuplets.findOverlapping(begin, end)) {
            removeTuplet(tuplet);
        }
    }

    private void shiftSpans(SpanSet<?>[] spanSetArray, int from, int shift) {
        for (var spanSet : spanSetArray) {
            spanSet.shiftValues(from, shift);
            spanSet.removeSpan(Integer.MIN_VALUE, 0);
            spanSet.removeSpan(elements.size() - 1, Integer.MAX_VALUE);
        }
    }

    public int getFirstTempoChange() {
        if (song != null && (song.indexOfLine(this) == 0) && (elementCount() > 0)) {
            return 0;
        }

        return IntStream.range(0, elementCount())
            .filter(n -> getElement(n).getTempoChange() != null)
            .findFirst()
            .orElse(-1);
    }

    public boolean isAnnotation() {
        return IntStream.range(0, elementCount()).anyMatch(
            n -> getElement(n).getAnnotation() != null
        );
    }

    public int getFirstTrill() {
        return IntStream.range(0, elementCount())
            .filter(n -> getElement(n).isTrill())
            .findFirst()
            .orElse(-1);
    }

    public int getFirstBeatChange() {
        return IntStream.range(0, elementCount())
            .filter(n -> getElement(n).getBeatChange() != null)
            .findFirst()
            .orElse(-1);
    }

    // =========================================================================
    // Range Element Management (Phase 4+)
    // =========================================================================

    /**
     * Adds a range element to this line.
     *
     * @param element The range element to add
     */
    public void addRangeElement(RangeElement element) {
        element.setParentLine(this);
        applyChange(
            new RangeElementAddition(this, element),
            () -> rangeElements.add(element)
        );
    }

    /**
     * Removes a range element from this line.
     *
     * @param element The range element to remove
     * @return true if the element was removed
     */
    public boolean removeRangeElement(RangeElement element) {
        var index = rangeElements.indexOf(element);

        if (index < 0) {
            return false;
        }

        applyChange(
            new RangeElementRemoval(this, element),
            () -> {
                rangeElements.remove(index);
                element.setParentLine(null);
            }
        );

        return true;
    }

    /**
     * Returns an unmodifiable view of the range elements in this line.
     */
    public List<RangeElement> getRangeElements() {
        return Collections.unmodifiableList(rangeElements);
    }

    /**
     * Finds all range elements that include the specified element index.
     *
     * @param elementIndex The element index to search for
     * @return List of range elements containing the element
     */
    public List<RangeElement> findRangeElementsAt(int elementIndex) {
        var result = new ArrayList<RangeElement>();

        for (var element : rangeElements) {
            int start = element.getAnchorElementIndex();
            int end = element.getEndElementIndex();

            if (elementIndex >= start && elementIndex <= end) {
                result.add(element);
            }
        }

        return result;
    }

    /**
     * Finds range elements of a specific type.
     *
     * @param type The class of range element to find
     * @return List of range elements of the specified type
     */
    @SuppressWarnings("unchecked")
    public <T extends RangeElement> List<T> findRangeElements(Class<T> type) {
        var result = new ArrayList<T>();

        for (var element : rangeElements) {
            if (type.isInstance(element)) {
                result.add((T) element);
            }
        }

        return result;
    }

    /**
     * Returns all Ending range elements on this line.
     */
    public List<Ending> findEndings() {
        return findRangeElements(Ending.class);
    }

    /**
     * Returns the Ending that contains the given element index, or null if none.
     *
     * @param elementIndex The element index to search for
     * @return The containing Ending, or null
     */
    public @Nullable Ending findEndingAt(int elementIndex) {
        for (var ending : findEndings()) {
            int start = ending.getAnchorElementIndex();
            int end = ending.getEndElementIndex();

            if (elementIndex >= start && elementIndex <= end) {
                return ending;
            }
        }

        return null;
    }

    /**
     * Returns whether the given element index is inside any ending.
     */
    public boolean isInsideAnyEnding(int elementIndex) {
        return findEndingAt(elementIndex) != null;
    }

    /**
     * Returns whether the given element index is the start of any ending.
     */
    public boolean isStartOfAnyEnding(int elementIndex) {
        for (var ending : findEndings()) {
            if (ending.getAnchorElementIndex() == elementIndex) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether the given element index is the end of any ending.
     */
    public boolean isEndOfAnyEnding(int elementIndex) {
        for (var ending : findEndings()) {
            if (ending.getEndElementIndex() == elementIndex) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the effect of replacing the element at {@code index} with {@code newElement}
     * on any Ending in this line. Returns {@link Ending.EndingEffect.None} if no ending
     * is affected.
     * <p>
     * Call before {@link #setElement} to determine whether a confirmation dialog is needed.
     */
    public Ending.EndingEffect findEndingReplacementEffect(int index, StaffElement newElement) {
        var oldElement = elements.get(index);

        return rangeElements.stream()
            .filter(r -> r instanceof Ending)
            .map(r -> ((Ending) r).checkReplacement(oldElement, newElement, this))
            .filter(e -> !(e instanceof Ending.EndingEffect.None))
            .findFirst()
            .orElse(new Ending.EndingEffect.None());
    }

    /**
     * Returns true if deleting {@code deletedElements} would remove any Ending in this line.
     * Checks both {@link RangeElement#isInvalidatedBy} (anchor/end deleted) and
     * {@link Ending#isInvalidatedByDeletion} (split deleted, sub-span emptied).
     * <p>
     * {@code deletedElements} must reflect the pre-deletion line state.
     */
    public boolean hasEndingInvalidatedByDeletion(List<StaffElement> deletedElements) {
        return rangeElements.stream()
            .anyMatch(r -> r instanceof Ending e &&
                           (e.isInvalidatedBy(deletedElements) ||
                            e.isInvalidatedByDeletion(deletedElements, this)));
    }

    /**
     * Returns true if inserting an element of {@code insertedType} at {@code insertedIndex}
     * would remove any Ending in this line.
     * <p>
     * Call before {@link #addElement(int, StaffElement)}.
     */
    public boolean hasEndingInvalidatedByInsertion(int insertedIndex, ElementType insertedType) {
        return rangeElements.stream()
            .anyMatch(r -> r instanceof Ending e &&
                           e.isInvalidatedByInsertion(insertedIndex, insertedType, this));
    }

    // =========================================================================
    // Beam Group Management (Phase 4+)
    // =========================================================================

}
