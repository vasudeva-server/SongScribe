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
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
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

public class Line {

    private static final int[][] FLAT_SHARP_ORDINAL = new int[][]{
        new int[]{0, 3, 6, 2, 5, 1, 4},
        new int[]{4, 1, 5, 2, 6, 3, 0},
    };

    /** Range elements (beams, ties, trills, crescendo, diminuendo, tuplets, endings). */
    private final List<RangeElement> rangeElements = new ArrayList<>();

    private final Song song;
    private int keys = 0;
    @Nullable
    private KeyType keyType = null;
    private final List<StaffElement> elements = new ArrayList<>();

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
     * Executes {@code body} inside a modification bracket on the parent song that
     * declares {@code label} as its op-name (Tier B).
     */
    public void withModification(String label, Runnable body) {
        song.withModification(label, body);
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
            && song.isAutoMaintainedTerminal(elements.get(lastIdx), this);
        var index = insertBeforeFinal ? lastIdx : elements.size();

        applyChange(
            new ElementInsertion(this, index, element),
            () -> {
                elements.add(index, element);
            }
        );
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
            var endingsToRemove = rangeElements.stream()
                .filter(r -> r.isInvalidatedByInsertion(index, insertedType, this))
                .toList();
            endingsToRemove.forEach(this::removeInvalidatedRangeElement);

            // When prepending to a non-empty first line, the previous first element
            // carried the initial tempo — move it to the new first element. The
            // removal is a tracked modification; the attachment on the incoming
            // element needs no record because the element is not yet in the document,
            // so the ElementInsertion below captures its attached state.
            if (index == 0 && !elements.isEmpty() && song.indexOfLine(this) == 0) {
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
            () -> elements.add(index, element)
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

        // Skipped during replay: the recorded batch already carries the removals.
        // The anchor re-pointing in the mutator below still runs — it is
        // self-inverting and required for span references to stay valid.
        if (!song.isReplaying()) {
            // Pre-compute before the mutator so findRepeatSplitElement sees the pre-replacement line.
            // Companion removals precede the primary replacement so reverse-order undo
            // restores the primary element before re-adding dependent spans.
            var endingsToRemove = rangeElements.stream()
                .filter(r -> r.isInvalidatedByReplacement(oldElement, element, this))
                .toList();
            endingsToRemove.forEach(this::removeInvalidatedRangeElement);
        }

        applyChange(
            new ElementReplacement(this, index, oldElement, element),
            () -> {
                element.setLine(this);
                element.setParentLine(this);
                elements.set(index, element);

                // Update stale anchor/end references in surviving range elements so that
                // getAnchorElementIndex()/getEndElementIndex() remain valid after the swap.
                for (var r : rangeElements) {
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
     * Must be called inside a modification bracket, after {@link #addElement(int, StaffElement)}.
     */
    public void adjustSyllablesForSuccessorAfterInsertion(int insertionIndex) {
        var successorIndex = insertionIndex + 1;

        if (successorIndex >= effectiveElementCount()) {
            return;
        }

        var successor = elements.get(successorIndex);
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
     * {@link #addElement(int, StaffElement)} while the pre-insertion indices are still valid.
     *
     * <ul>
     *   <li>{@link Lyric.Extend#START}: predecessor cleared to {@code NONE}; cascade-clear
     *       {@code CONTINUE}/{@code STOP} from {@code insertionIndex} onward.</li>
     *   <li>{@link Lyric.Extend#CONTINUE}: predecessor promoted to {@code STOP}; cascade-clear
     *       {@code CONTINUE}/{@code STOP} from {@code insertionIndex} onward.</li>
     *   <li>{@link Lyric.Extend#STOP}/{@link Lyric.Extend#NONE}: no action.</li>
     * </ul>
     */
    public void adjustExtendsForInsertion(int insertionIndex) {
        var predecessorIndex = insertionIndex - 1;

        if (predecessorIndex < 0 || predecessorIndex >= effectiveElementCount()) {
            return;
        }

        var predecessorElement = elements.get(predecessorIndex);
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
            cascadeClearExtend(insertionIndex, fix.verse());
        }
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
            var element = elements.get(i);
            var lyricIndex = findLyricIndexForVerse(element, verse);

            if (lyricIndex < 0) {
                break;
            }

            var lyric = element.lyrics.get(lyricIndex);

            if (lyric.extend() == Lyric.Extend.NONE) {
                break;
            }

            var isStop = lyric.extend() == Lyric.Extend.STOP;

            modifyElement(i, ElementField.LYRIC, () ->
                element.lyrics.set(lyricIndex,
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

        var lyric = precedingElement.lyrics.get(lyricIndex);
        var existingExtend = lyric.extend();

        if (existingExtend != Lyric.Extend.CONTINUE && existingExtend != Lyric.Extend.START) {
            return;
        }

        // CONTINUE → STOP: preceding note becomes the new terminus
        // START → NONE: 2-note chain collapses (a single note cannot carry a melisma)
        var newExtend = existingExtend == Lyric.Extend.CONTINUE ? Lyric.Extend.STOP : Lyric.Extend.NONE;

        modifyElement(precedingIndex, ElementField.LYRIC, () ->
            precedingElement.lyrics.set(lyricIndex,
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
            var lyrics = elements.get(index).lyrics;

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
        var lyrics = elements.get(index).lyrics;
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
                prevContinues = Lyric.syllabicContinues(prevSyllabic);
                break;
            }
        }

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
                "setSyllableBoundary cannot run on carrier lyric (extend=" + lyric.extend() + ')');
        }

        var prevContinues = previousLyricContinues(index, verse);
        var newSyllabic = deriveSyllabic(prevContinues, !isWordEnd);

        var newCompound = !isWordEnd && isCompound;

        if (newSyllabic != lyric.syllabic() || newCompound != lyric.compound()) {
            modifyElement(index, ElementField.LYRIC, () ->
                replaceSyllabicAndCompound(index, verse, newSyllabic, newCompound));
        }

        fixSuccessorSyllabic(index, verse, !isWordEnd);
    }

    private void fixSuccessorSyllabic(int index, int verse, boolean predecessorContinues) {
        var nextIndex = nextLyricBearingIndex(index, verse);

        if (nextIndex < 0) {
            return;
        }

        var nextLyric = elements.get(nextIndex).getLyricForVerse(verse);

        if (nextLyric == null || nextLyric.syllabic() == null) {
            return;
        }

        var newSyllabic = deriveSyllabic(predecessorContinues, Lyric.syllabicContinues(nextLyric.syllabic()));

        if (newSyllabic != nextLyric.syllabic()) {
            modifyElement(nextIndex, ElementField.LYRIC, () ->
                replaceSyllabicAndCompound(nextIndex, verse, newSyllabic, nextLyric.compound()));
        }
    }

    private void replaceSyllabicAndCompound(int index, int verse,
            Lyric.@Nullable Syllabic syllabic, boolean compound) {
        var lyrics = elements.get(index).lyrics;
        var lyricIndex = findLyricIndexForVerse(elements.get(index), verse);
        var lyric = lyrics.get(lyricIndex);
        lyrics.set(lyricIndex, new Lyric(
            lyric.verse(), lyric.text(), lyric.extend(), syllabic, compound));
    }

    private boolean previousLyricContinues(int index, int verse) {
        for (var i = index - 1; i >= 0; i--) {
            var prevLyric = elements.get(i).getLyricForVerse(verse);

            if (prevLyric != null && prevLyric.syllabic() != null) {
                return Lyric.syllabicContinues(prevLyric.syllabic());
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

    private int previousLyricBearingIndex(int fromIndex, int verse) {
        for (var i = fromIndex - 1; i >= 0; i--) {
            if (elements.get(i).getLyricForVerse(verse) != null) {
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

    private boolean hasFollowingTextBearingLyric(int fromIndex, int verse) {
        var forwardIndex = nextLyricBearingIndex(fromIndex, verse);

        if (forwardIndex < 0) {
            return false;
        }

        var forwardLyric = elements.get(forwardIndex).getLyricForVerse(verse);
        return forwardLyric != null && forwardLyric.syllabic() != null;
    }

    /**
     * Repairs syllabic chain markers on the neighbors of a just-deleted verse lyric.
     * Must be called inside a modification bracket, after the lyric at {@code index}
     * has already been cleared.
     */
    public void adjustNeighborsForLyricDeletion(int index, int verse) {
        var backIndex = previousLyricBearingIndex(index, verse);

        if (backIndex >= 0) {
            var backLyric = elements.get(backIndex).getLyricForVerse(verse);

            if (backLyric == null) {
                return;
            }

            if (backLyric.extend() == Lyric.Extend.CONTINUE) {
                var backElement = elements.get(backIndex);
                modifyElement(backIndex, ElementField.LYRIC, () ->
                    backElement.setLyricForVerse(verse,
                        backLyric.syllabic(), backLyric.compound(), backLyric.text(),
                        Lyric.Extend.STOP));
                return;
            }

            if (isWordContinuingLyric(backLyric) && !hasFollowingTextBearingLyric(backIndex, verse)) {
                // setSyllableBoundary also adjusts the next lyric-bearing element.
                setSyllableBoundary(backIndex, verse, true, false);
            }

            return;
        }

        // No predecessor — fix the successor if it now lacks a continuing predecessor.
        fixSuccessorSyllabic(index, verse, false);
    }

    private static int findLyricIndexForVerse(StaffElement element, int verse) {
        var lyrics = element.lyrics;

        for (var i = 0; i < lyrics.size(); i++) {
            if (lyrics.get(i).verse() == verse) {
                return i;
            }
        }

        return -1;
    }

    private static Lyric.Syllabic deriveSyllabic(boolean prevContinues, boolean thisContinues) {
        if (prevContinues) {
            return thisContinues ? Lyric.Syllabic.MIDDLE : Lyric.Syllabic.END;
        }

        return thisContinues ? Lyric.Syllabic.BEGIN : Lyric.Syllabic.SINGLE;
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

    /** Attaches the song's initial tempo to the first element of this line if not already set. */
    void attachInitialTempoIfNeeded() {
        if (elements.isEmpty()) {
            return;
        }

        var element = elements.getFirst();

        if (element.findAttachment(TempoChangeAttachment.class) == null) {
            var initialTempo = song.getTempo();

            if (initialTempo != null) {
                element.addAttachment(new TempoChangeAttachment(element, initialTempo));
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

        var deleted = elements.get(index);

        // During replay the recorded batch already carries the companion
        // removals — re-deriving them would double-apply.
        if (!song.isReplaying()) {
            removeOverlappingTuplets(index, index);
            var deletedList = List.of(deleted);

            // Companion removals precede the primary deletion so reverse-order undo
            // re-inserts the element before re-adding the spans anchored to it.
            var invalidated = rangeElements.stream()
                .filter(r -> r.isInvalidatedBy(deletedList)
                    || r.isInvalidatedByDeletion(deletedList, this))
                .toList();
            invalidated.forEach(this::removeInvalidatedRangeElement);
        }

        applyChange(
            new ElementDeletion(this, index, deleted),
            () -> elements.remove(index)
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

        var deletedElements = List.copyOf(elements.subList(from, to + 1));

        // During replay the recorded batch already carries the companion
        // removals — re-deriving them would double-apply.
        if (!song.isReplaying()) {
            removeOverlappingTuplets(from, to);

            // Companion removals precede the primary deletion so reverse-order undo
            // re-inserts the elements before re-adding the spans anchored to them.
            var invalidated = rangeElements.stream()
                .filter(r -> r.isInvalidatedBy(deletedElements)
                    || r.isInvalidatedByDeletion(deletedElements, this))
                .toList();
            invalidated.forEach(this::removeInvalidatedRangeElement);
        }

        applyChange(
            new ElementRangeDeletion(this, from, to, deletedElements),
            () -> elements.subList(from, to + 1).clear()
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

        if (count > 0 && song.isAutoMaintainedTerminal(elements.get(count - 1), this)) {
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

    public boolean isPairedGraceNote(int index) {
        if (index < 0 || index >= elements.size()) {
            return false;
        }

        var element = elements.get(index);

        if (!element.getType().isGraceNote()) {
            return false;
        }

        return element.hasGlissando();
    }

    /** Returns true when the element at {@code index} is the host of a paired grace note. */
    public boolean isHostOfPairedGraceNote(int index) {
        return index >= 1 && isPairedGraceNote(index - 1);
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
     * Returns all {@link Beam} range elements whose span overlaps [begin, end] inclusive.
     */
    public List<Beam> findBeamsOverlapping(int begin, int end) {
        var result = new ArrayList<Beam>();

        for (var re : rangeElements) {
            if (re instanceof Beam b) {
                var anchor = b.getAnchorElementIndex();
                var endIdx = b.getEndElementIndex();

                if (anchor <= end && endIdx >= begin) {
                    result.add(b);
                }
            }
        }

        return result;
    }

    /**
     * Returns true if {@code elementIndex} is the anchor of any {@link Beam} range element.
     */
    public boolean isStartOfAnyBeam(int elementIndex) {
        for (var re : rangeElements) {
            if (re instanceof Beam b && b.getAnchorElementIndex() == elementIndex) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if {@code elementIndex} is the end of any {@link Beam} range element.
     */
    public boolean isEndOfAnyBeam(int elementIndex) {
        for (var re : rangeElements) {
            if (re instanceof Beam b && b.getEndElementIndex() == elementIndex) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the first {@link Tie} range element whose anchor-to-end range includes
     * {@code elementIndex}, or {@code null} if the element is not part of any tie.
     */
    @Nullable
    public Tie findTieAt(int elementIndex) {
        for (var re : rangeElements) {
            if (re instanceof Tie t) {
                var anchor = t.getAnchorElementIndex();
                var end = t.getEndElementIndex();

                if (anchor >= 0 && end >= 0 && anchor <= elementIndex && elementIndex <= end) {
                    return t;
                }
            }
        }

        return null;
    }

    /**
     * Returns the {@link Tie} range element whose anchor is exactly {@code anchorIndex}
     * and end is exactly {@code endIndex}, or {@code null} if no tie spans that exact
     * range. Unlike {@link #findTieAt(int)}, this disambiguates chained ties that share
     * an endpoint note — {@code findTieAt} would return whichever overlapping tie comes
     * first, not necessarily the one matching a specific selection.
     */
    @Nullable
    public Tie findExactTie(int anchorIndex, int endIndex) {
        for (var re : rangeElements) {
            if (re instanceof Tie t
                && t.getAnchorElementIndex() == anchorIndex
                && t.getEndElementIndex() == endIndex) {
                return t;
            }
        }

        return null;
    }

    /**
     * Adds a tie range element. Tie ranges never coalesce — a chain of tied notes is
     * represented as one {@link Tie} per adjacent pair, each rendered as its own arc,
     * even when two ties share an endpoint note.
     */
    public void addTie(Tie tie) {
        tie.setParentLine(this);
        applyChange(new TieAddition(this, tie), () -> rangeElements.add(tie));
    }

    /**
     * Removes a tie range element that was previously added via {@link #addTie(Tie)}.
     */
    public void removeTie(Tie tie) {
        var index = rangeElements.indexOf(tie);

        if (index < 0) {
            return;
        }

        applyChange(
            new TieRemoval(this, tie),
            () -> {
                rangeElements.remove(index);
                tie.setParentLine(null);
            }
        );
    }

    /**
     * Returns an unmodifiable snapshot of all {@link Tie} range elements in this line.
     */
    public List<Tie> findTies() {
        return findRangeElements(Tie.class);
    }

    /**
     * Returns the first {@link Tuplet} range element whose anchor-to-end range includes
     * {@code elementIndex}, or {@code null} if the element is not part of any tuplet.
     */
    @Nullable
    public Tuplet findTupletAt(int elementIndex) {
        for (var re : rangeElements) {
            if (re instanceof Tuplet t) {
                var anchor = t.getAnchorElementIndex();
                var end = t.getEndElementIndex();

                if (anchor >= 0 && end >= 0 && anchor <= elementIndex && elementIndex <= end) {
                    return t;
                }
            }
        }

        return null;
    }

    /**
     * Returns all {@link Tuplet} range elements whose span overlaps [begin, end] inclusive.
     */
    public List<Tuplet> findTupletsOverlapping(int begin, int end) {
        var result = new ArrayList<Tuplet>();

        for (var re : rangeElements) {
            if (re instanceof Tuplet t) {
                var anchor = t.getAnchorElementIndex();
                var endIdx = t.getEndElementIndex();

                if (anchor <= end && endIdx >= begin) {
                    result.add(t);
                }
            }
        }

        return result;
    }

    public List<Crescendo> getCrescendos() {
        return findRangeElements(Crescendo.class);
    }

    public List<Diminuendo> getDiminuendos() {
        return findRangeElements(Diminuendo.class);
    }

    /**
     * Returns the first {@link Beam} range element whose anchor-to-end range includes
     * {@code elementIndex}, or {@code null} if the element is not part of any beam.
     */
    @Nullable
    public Beam findBeamAt(int elementIndex) {
        for (var re : rangeElements) {
            if (re instanceof Beam b) {
                var anchor = b.getAnchorElementIndex();
                var end = b.getEndElementIndex();

                if (anchor >= 0 && end >= 0 && anchor <= elementIndex && elementIndex <= end) {
                    return b;
                }
            }
        }

        return null;
    }

    /**
     * Adds a beam range element, merging with any existing beams that share endpoints.
     * <p>
     * If an existing beam ends at the new beam's start, or starts at the new beam's end,
     * the spans are merged into a single wider beam. Any beam whose range is fully
     * covered by the merged result is removed.
     */
    public void addBeaming(Beam beam) {
        beam.setParentLine(this);

        // During replay the recorded BeamingAddition already carries the merged
        // span and the batch carries the subsumed-beam removals — just add.
        if (!song.isReplaying()) {
            mergeOverlappingSpans(beam, Beam.class, this::removeBeaming);
        }

        applyChange(new BeamingAddition(this, beam), () -> rangeElements.add(beam));
    }

    /**
     * Absorbs same-type spans overlapping {@code span}'s endpoints: widens the span to
     * cover them, then removes every {@code type} span fully subsumed by the widened
     * range via {@code remover}. The tracked removals are emitted before the widened
     * span's addition so undo restores the original spans after removing the merged
     * one.
     */
    private <R extends RangeElement> void mergeOverlappingSpans(
        R span,
        Class<R> type,
        Consumer<? super R> remover
    ) {
        var anchorIdx = elements.indexOf(span.getAnchorElement());
        var endIdx = elements.indexOf(span.getEndElement());

        // Expand bounds to absorb adjacent/overlapping spans.
        var mergedAnchorIdx = anchorIdx;
        var mergedEndIdx = endIdx;

        for (var re : rangeElements) {
            if (type.isInstance(re)) {
                var existing = type.cast(re);
                var existingAnchor = existing.getAnchorElementIndex();
                var existingEnd = existing.getEndElementIndex();

                if (existingAnchor <= anchorIdx && anchorIdx <= existingEnd) {
                    mergedAnchorIdx = Math.min(mergedAnchorIdx, existingAnchor);
                }

                if (existingAnchor <= endIdx && endIdx <= existingEnd) {
                    mergedEndIdx = Math.max(mergedEndIdx, existingEnd);
                }
            }
        }

        if (mergedAnchorIdx != anchorIdx) {
            span.setAnchorElement(elements.get(mergedAnchorIdx));
        }

        if (mergedEndIdx != endIdx) {
            span.setEndElement(elements.get(mergedEndIdx));
        }

        final int finalMergedAnchor = mergedAnchorIdx;
        final int finalMergedEnd = mergedEndIdx;
        var subsumedSpans = rangeElements.stream()
            .filter(re -> type.isInstance(re)
                && type.cast(re).getAnchorElementIndex() >= finalMergedAnchor
                && type.cast(re).getEndElementIndex() <= finalMergedEnd)
            .map(type::cast)
            .toList();
        subsumedSpans.forEach(remover);
    }

    /**
     * Removes a beam range element that was previously added via {@link #addBeaming(Beam)}.
     */
    public void removeBeaming(Beam beam) {
        var index = rangeElements.indexOf(beam);

        if (index < 0) {
            return;
        }

        applyChange(
            new BeamingRemoval(this, beam),
            () -> {
                rangeElements.remove(index);
                beam.setParentLine(null);
            }
        );
    }

    /**
     * Adds a tuplet range element, replacing any existing tuplet that overlaps the new one.
     * <p>
     * Any existing tuplet whose range overlaps [anchor, end] is removed before the new tuplet
     * is added.
     */
    public void addTuplet(Tuplet tuplet) {
        tuplet.setParentLine(this);

        // During replay the recorded batch already carries the overlapping-tuplet
        // removals — just add.
        if (!song.isReplaying()) {
            var anchorIndex = elements.indexOf(tuplet.getAnchorElement());
            var endIndex = elements.indexOf(tuplet.getEndElement());

            // Remove any existing tuplets that overlap the new range — tracked
            // removals emitted before the addition so undo restores the originals.
            removeOverlappingTuplets(anchorIndex, endIndex);
        }

        applyChange(new TupletAddition(this, tuplet), () -> rangeElements.add(tuplet));
    }

    /**
     * Removes a tuplet range element that was previously added via {@link #addTuplet(Tuplet)}.
     */
    public void removeTuplet(Tuplet tuplet) {
        var index = rangeElements.indexOf(tuplet);

        if (index < 0) {
            return;
        }

        applyChange(
            new TupletRemoval(this, tuplet),
            () -> {
                rangeElements.remove(index);
                tuplet.setParentLine(null);
            }
        );
    }

    /**
     * Adds a crescendo hairpin range element.
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
     * Adds a diminuendo hairpin range element.
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
        BiFunction<Line, H, ? extends Mutation> mutationFactory,
        Class<H> type
    ) {
        hairpin.setParentLine(this);

        // During replay the recorded addition already carries the merged span
        // and the batch carries the absorbed-hairpin removals — just add.
        if (!song.isReplaying()) {
            mergeOverlappingSpans(hairpin, type, this::removeInvalidatedRangeElement);
        }

        applyChange(mutationFactory.apply(this, hairpin), () -> rangeElements.add(hairpin));
    }

    /**
     * Shared remove logic for crescendo and diminuendo hairpins.
     */
    private <H extends Hairpin> void removeHairpin(
        H hairpin,
        BiFunction<Line, H, ? extends Mutation> mutationFactory
    ) {
        var index = rangeElements.indexOf(hairpin);

        if (index < 0) {
            return;
        }

        applyChange(
            mutationFactory.apply(this, hairpin),
            () -> {
                rangeElements.remove(index);
                hairpin.setParentLine(null);
            }
        );
    }

    /**
     * Returns true if the given note index falls within any hairpin (crescendo or diminuendo) range.
     */
    public boolean isInHairpinRange(int noteIndex) {
        for (var re : rangeElements) {
            if (re instanceof Hairpin) {
                var anchorIdx = re.getAnchorElementIndex();
                var endIdx = re.getEndElementIndex();

                if (anchorIdx >= 0 && endIdx >= 0 && anchorIdx <= noteIndex && noteIndex <= endIdx) {
                    return true;
                }
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
        // (see attachInitialTempoIfNeeded), so the tempo marking — and its vertical
        // adjustment handle — belongs to that element.
        if ((song.indexOfLine(this) == 0) && (elementCount() > 0)) {
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
        return findRangeElements(Trill.class).stream()
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
     * Adds a trill range element, first removing any existing trill that overlaps the new one.
     * Each removal is recorded as its own mutation so replacing a displaced trill is undoable.
     */
    public void addTrill(Trill trill) {
        trill.setParentLine(this);

        for (var overlapping : findTrillsOverlapping(trill.getAnchorElementIndex(), trill.getEndElementIndex())) {
            removeRangeElement(overlapping);
        }

        addRangeElement(trill);
    }

    /**
     * Removes every trill range element overlapping {@code [beginIndex, endIndex]}.
     */
    public void removeTrillsOverlapping(int beginIndex, int endIndex) {
        for (var trill : findTrillsOverlapping(beginIndex, endIndex)) {
            removeRangeElement(trill);
        }
    }

    /**
     * Finds every trill range element overlapping {@code [beginIndex, endIndex]}.
     */
    public List<Trill> findTrillsOverlapping(int beginIndex, int endIndex) {
        return findRangeElements(Trill.class).stream()
            .filter(trill -> trill.overlaps(beginIndex, endIndex))
            .toList();
    }

    /**
     * Returns whether any trill range element overlaps {@code [beginIndex, endIndex]}.
     * Cheaper than {@link #findTrillsOverlapping} when only presence matters: it
     * short-circuits and allocates no intermediate lists.
     */
    public boolean hasTrillOverlapping(int beginIndex, int endIndex) {
        for (var element : rangeElements) {
            if (element instanceof Trill trill && trill.overlaps(beginIndex, endIndex)) {
                return true;
            }
        }

        return false;
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
     * Removes a range element displaced by another change (invalidated by an
     * element edit, or subsumed by a span merge) via its typed tracked removal,
     * so the removal emits its proper mutation. A raw
     * {@code rangeElements.removeIf} would drop the span with no record, making
     * undo of the enclosing operation lossy.
     */
    private void removeInvalidatedRangeElement(RangeElement rangeElement) {
        switch (rangeElement) {
            case Beam beam -> removeBeaming(beam);
            case Tie tie -> removeTie(tie);
            case Tuplet tuplet -> removeTuplet(tuplet);
            case Crescendo crescendo -> removeCrescendo(crescendo);
            case Diminuendo diminuendo -> removeDiminuendo(diminuendo);
            default -> removeRangeElement(rangeElement);
        }
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
            var start = element.getAnchorElementIndex();
            var end = element.getEndElementIndex();

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
     * Returns true if deleting {@code deletedElements} would remove any Ending in this line.
     * Checks both {@link RangeElement#isInvalidatedBy} (anchor/end deleted) and
     * {@link RangeElement#isInvalidatedByDeletion} (subclass-specific conditions).
     * <p>
     * {@code deletedElements} must reflect the pre-deletion line state.
     */
    public boolean hasEndingInvalidatedByDeletion(List<StaffElement> deletedElements) {
        return rangeElements.stream()
            .filter(RangeElement::requiresInvalidationConfirm)
            .anyMatch(r -> r.isInvalidatedBy(deletedElements) ||
                           r.isInvalidatedByDeletion(deletedElements, this));
    }

    /**
     * Returns true if inserting an element of {@code insertedType} at {@code insertedIndex}
     * would remove any Ending in this line.
     * <p>
     * Call before {@link #addElement(int, StaffElement)}.
     */
    public boolean hasEndingInvalidatedByInsertion(int insertedIndex, ElementType insertedType) {
        return rangeElements.stream()
            .anyMatch(r -> r.isInvalidatedByInsertion(insertedIndex, insertedType, this));
    }

    private record ExtendFix(int lyricIndex, int verse, Lyric.Extend newExtend) {}

    // =========================================================================
    // Beam Group Management (Phase 4+)
    // =========================================================================

}
