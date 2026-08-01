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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import songscribe.util.LogUtils;

public class Line {

    private static final Logger LOG = LoggerFactory.getLogger(Line.class);

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
            () -> elements.add(index, element)
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
            //
            // Not routed through Song.withBeatDefiningEdit: the displacement is the one
            // beat-defining write that cannot change the beat anywhere. The tempo leaves
            // element 0 of line 0 and lands back on element 0 of line 0 — the same
            // document position, carrying the same Tempo — so resolveBeatAt returns the
            // same beat for every position in the song and no tuplet can be invalidated.
            if (isInitialTempoAnchor(index) && !elements.isEmpty()) {
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
            cascadeClearExtend(cascadeStartIndex, fix.verse());
        }
    }

    /**
     * Breaks the lyric chains that a bare element about to be inserted at
     * {@code insertionIndex} interrupts — the syllabic chain on the predecessor and the
     * melisma chain running through it. Must be called inside a modification bracket,
     * <em>before</em> {@link #addElement(int, StaffElement)}, while the pre-insertion
     * indices still hold.
     *
     * <p>Only the predecessor half of the repair belongs here. The rest runs after the
     * insertion — see {@link #repairNeighborsAfterInsertion} — so an insertion path calls
     * this, inserts, then calls that. A path that inserts first and records later wants
     * {@link #repairNeighborsAfterUntrackedInsertion} instead, which does both halves in
     * post-insertion indices.
     */
    public void repairNeighborsBeforeInsertion(int insertionIndex) {
        adjustSyllablesForNeighborChange(insertionIndex - 1, null);
        adjustExtendsForInsertion(insertionIndex);
    }

    /**
     * Repairs what the element just inserted at {@code insertedIndex} could only disturb once
     * it was in the list: the successor's syllabic chain, a connecting glissando on the
     * predecessor left with no valid target, and the grace-host melisma of a pair the
     * insertion landed inside. The predecessor half of the repair runs before the insertion —
     * see {@link #repairNeighborsBeforeInsertion}.
     *
     * <p>Must be called inside a modification bracket, <em>after</em>
     * {@link #addElement(int, StaffElement)}.
     */
    public void repairNeighborsAfterInsertion(int insertedIndex) {
        adjustSyllablesForSuccessorAfterInsertion(insertedIndex);

        // A connecting glissando joins a note to the note that immediately follows it.
        // Inserting another pitched note simply re-targets it, but inserting anything else
        // (rest, breath mark, grace note) leaves it with no valid target, so remove it from
        // the preceding note.
        if (!getElement(insertedIndex).getType().isPitchedNote() && insertedIndex > 0) {
            var precedingElement = getElement(insertedIndex - 1);

            if (precedingElement.hasGlissando()) {
                modifyElement(insertedIndex - 1, ElementField.SLIDE, precedingElement::removeSlide);
            }
        }

        // A pitched insertion between a grace note and its host re-targets the glissando, so
        // the inserted element is the new host. The extend adjustment made before the insertion
        // removed the melisma that pointed at the old host — carrier and all, leaving it an
        // ordinary note free to take a syllable of its own — so re-establish the melisma against
        // the new host. The non-pitched case fell through the glissando strip above and no
        // longer reads as paired.
        if (isPairedGraceNote(insertedIndex - 1)) {
            syncGraceHostMelisma(insertedIndex - 1);
        }
    }

    /**
     * Repairs the neighbors of the element already inserted at {@code insertedIndex}, doing
     * entirely in post-insertion indices what an insertion path does on both sides of
     * {@link #addElement(int, StaffElement)}, so a caller that inserted the element earlier can
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
    public void repairNeighborsAfterUntrackedInsertion(int insertedIndex) {
        adjustSyllablesForNeighborChange(insertedIndex - 1, null);
        adjustExtends(insertedIndex - 1, insertedIndex + 1);
        repairNeighborsAfterInsertion(insertedIndex);
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

            // A carrier has no text of its own, so clearing its extend would strand an empty
            // lyric — one that still counts as lyric-bearing for lyric navigation. Drop the
            // entry instead. The cascade only ever walks the members of a chain downstream of
            // its START, which are carriers by construction; the else branch is for safety.
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

        var precedingElement = elements.get(precedingIndex);
        var lyricIndex = findLyricIndexForVerse(precedingElement, verse);

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
     * <p>Must be called from inside a {@link #withModification(Runnable)} bracket.
     *
     * @param index      the element index on this line
     * @param verse      the verse number (typically 1)
     * @param text       the syllable text; blank for a carrier or an erased syllable
     * @param extend     melisma extender state for the lyric
     * @param isWordEnd  {@code true} when this syllable terminates the word (no continuation)
     * @param isCompound {@code true} when this syllable joins the next via a compound-word
     *                   boundary; ignored when {@code isWordEnd} is {@code true}
     */
    public void writeLyricForVerse(int index, int verse, String text, Lyric.Extend extend,
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

        var element = elements.get(index);

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

        var nextLyric = elements.get(nextIndex).getLyricForVerse(verse);

        if (nextLyric == null || nextLyric.syllabic() == null) {
            trace("fixSuccessorSyllabic({}): successor {} is a carrier, nothing to fix",
                index, nextIndex);
            return;
        }

        var newSyllabic = deriveSyllabic(predecessorContinues, Lyric.syllabicContinues(nextLyric.syllabic()));

        trace("fixSuccessorSyllabic({}): successor {} {} -> {}, predecessorContinues={}",
            index, nextIndex, nextLyric.syllabic(), newSyllabic, predecessorContinues);

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

    /**
     * Walks backward from {@code fromIndex - 1} and returns the index of the first element whose
     * {@code verse} lyric is non-null, or {@code -1} if there is none.
     */
    public int previousLyricBearingIndex(int fromIndex, int verse) {
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

    /**
     * Whether the first element after {@code fromIndex} that carries a {@code verse} lyric
     * carries a syllable of its own, rather than being a melisma carrier.
     */
    public boolean hasFollowingTextBearingLyric(int fromIndex, int verse) {
        var forwardIndex = nextLyricBearingIndex(fromIndex, verse);

        if (forwardIndex < 0) {
            return false;
        }

        var forwardLyric = elements.get(forwardIndex).getLyricForVerse(verse);
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
    public void adjustSuccessorAfterMelismaCarrier(int index, int verse) {
        for (var i = index + 1; i < elements.size(); i++) {
            var lyric = elements.get(i).getLyricForVerse(verse);

            if (lyric == null || lyric.syllabic() == null) {
                continue;
            }

            var newSyllabic = deriveSyllabic(false, Lyric.syllabicContinues(lyric.syllabic()));

            trace("adjustSuccessorAfterMelismaCarrier({}): successor {} {} -> {}",
                index, i, lyric.syllabic(), newSyllabic);

            if (newSyllabic != lyric.syllabic()) {
                var successorIndex = i;
                modifyElement(successorIndex, ElementField.LYRIC, () ->
                    replaceSyllabicAndCompound(successorIndex, verse, newSyllabic, lyric.compound()));
            }

            return;
        }
    }

    /**
     * Repairs syllabic chain markers on the neighbors of a just-deleted verse lyric.
     * Must be called inside a modification bracket, after the lyric at {@code index}
     * has already been cleared.
     */
    public void adjustNeighborsForLyricDeletion(int index, int verse) {
        var backIndex = previousLyricBearingIndex(index, verse);
        trace("adjustNeighborsForLyricDeletion({}): predecessor={}", index, backIndex);

        if (backIndex >= 0) {
            var backLyric = elements.get(backIndex).getLyricForVerse(verse);

            if (backLyric == null) {
                return;
            }

            if (backLyric.extend() == Lyric.Extend.CONTINUE) {
                trace("adjustNeighborsForLyricDeletion({}): closing chain, {} CONTINUE -> STOP",
                    index, backIndex);
                var backElement = elements.get(backIndex);
                modifyElement(backIndex, ElementField.LYRIC, () ->
                    backElement.setLyricForVerse(verse,
                        backLyric.syllabic(), backLyric.compound(), backLyric.text(),
                        Lyric.Extend.STOP));
                return;
            }

            if (isWordContinuingLyric(backLyric) && !hasFollowingTextBearingLyric(backIndex, verse)) {
                trace("adjustNeighborsForLyricDeletion({}): ending dangling word at {}",
                    index, backIndex);
                // setSyllableBoundary also adjusts the next lyric-bearing element.
                setSyllableBoundary(backIndex, verse, true, false);
            }

            return;
        }

        // No predecessor — fix the successor if it now lacks a continuing predecessor.
        fixSuccessorSyllabic(index, verse, false);
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
    public void removeLyricForVerse(int index, int verse) {
        if (index < 0 || index >= effectiveElementCount()) {
            return;
        }

        var element = elements.get(index);

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
    public void transferLyricForVerse(int fromIndex, int toIndex, int verse) {
        var elementCount = effectiveElementCount();

        if (fromIndex == toIndex
                || fromIndex < 0 || fromIndex >= elementCount
                || toIndex < 0 || toIndex >= elementCount) {
            return;
        }

        var sourceLyric = elements.get(fromIndex).getLyricForVerse(verse);

        if (sourceLyric == null || sourceLyric.isCarrier()) {
            return;
        }

        // Read off the source before anything is written: setLyricForVerse drops the
        // target's existing verse entry before adding the incoming syllable.
        var targetElement = elements.get(toIndex);
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
    public void transferLyrics(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= effectiveElementCount()) {
            return;
        }

        // Snapshot the verses first: transferLyricForVerse mutates the source's lyric list.
        for (var verse : verseNumbersOf(elements.get(fromIndex))) {
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
    public void syncGraceHostMelisma(int graceIndex) {
        var hostIndex = graceIndex + 1;

        if (graceIndex < 0 || hostIndex >= effectiveElementCount()) {
            return;
        }

        var isPaired = isPairedGraceNote(graceIndex);
        var verses = new LinkedHashSet<>(verseNumbersOf(elements.get(graceIndex)));
        verses.addAll(verseNumbersOf(elements.get(hostIndex)));

        for (var verse : verses) {
            syncGraceHostMelismaForVerse(graceIndex, verse, isPaired);
        }
    }

    /**
     * Normalizes every grace-host pair on this line to the automatic grace-host melisma, for
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
    public void repairGraceHostMelismas() {
        for (var index = 0; index < elements.size(); index++) {
            if (!isPairedGraceNote(index) || index + 1 >= elements.size()) {
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
        var graceElement = elements.get(graceIndex);

        // Snapshot the verses first: transferLyricForVerse mutates the host's lyric list.
        for (var verse : verseNumbersOf(elements.get(hostIndex))) {
            // transferLyricForVerse ignores a text-less carrier, so a host STOP stays put.
            if (graceElement.getLyricForVerse(verse) == null) {
                transferLyricForVerse(hostIndex, graceIndex, verse);
            }
        }
    }

    private void syncGraceHostMelismaForVerse(int graceIndex, int verse, boolean isPaired) {
        var hostIndex = graceIndex + 1;
        var hostElement = elements.get(hostIndex);
        var graceLyric = elements.get(graceIndex).getLyricForVerse(verse);
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
        var element = elements.get(index);
        var lyricIndex = findLyricIndexForVerse(element, verse);

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

    /**
     * Whether {@code elementIndex} of this line is where the song's initial tempo is
     * anchored — the first element of the first line, the position
     * {@link #attachInitialTempoIfNeeded} mirrors it onto.
     *
     * <p>A {@link TempoChangeAttachment} at that position is the song's own tempo, never
     * an independent per-note tempo change. Nothing in the model distinguishes the two —
     * the mirrored attachment carries no mark identifying it as such, and the tempo dialog
     * edits it in place like any other — so every reader of that position must treat what
     * it finds there as the song's tempo.
     */
    public boolean isInitialTempoAnchor(int elementIndex) {
        return elementIndex == 0 && song.indexOfLine(this) == 0;
    }

    /**
     * Attaches the song's initial tempo to the first element of this line if not already set.
     *
     * <p>Routed through {@link Song#withBeatDefiningEdit} because attaching a tempo defines a
     * beat. In practice this runs only while mutation tracking is suspended (the load path),
     * where the helper validates nothing and the load pass judges the tuplets instead — but
     * the routing keeps the chokepoint whole if that ever changes.
     */
    void attachInitialTempoIfNeeded() {
        if (elements.isEmpty()) {
            return;
        }

        var element = elements.getFirst();

        if (element.findAttachment(TempoChangeAttachment.class) == null) {
            var initialTempo = song.getTempo();

            if (initialTempo != null) {
                Song.withBeatDefiningEditOn(element,
                    () -> element.addAttachment(new TempoChangeAttachment(element, initialTempo)));
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
            adjustHairpinsForDeletion(deletedList);

            // Companion removals precede the primary deletion so reverse-order undo
            // re-inserts the element before re-adding the spans anchored to it.
            var invalidated = rangeElements.stream()
                .filter(r -> !(r instanceof Hairpin))
                .filter(r -> r.isInvalidatedBy(deletedList)
                    || r.isInvalidatedByDeletion(deletedList, this))
                .toList();
            invalidated.forEach(this::removeInvalidatedRangeElement);
        }

        var displacedTempo = initialTempoBeingRemoved(index);

        applyChange(
            new ElementDeletion(this, index, deleted),
            () -> elements.remove(index)
        );

        reanchorInitialTempo(displacedTempo);
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
            adjustHairpinsForDeletion(deletedElements);

            // Companion removals precede the primary deletion so reverse-order undo
            // re-inserts the elements before re-adding the spans anchored to them.
            var invalidated = rangeElements.stream()
                .filter(r -> !(r instanceof Hairpin))
                .filter(r -> r.isInvalidatedBy(deletedElements)
                    || r.isInvalidatedByDeletion(deletedElements, this))
                .toList();
            invalidated.forEach(this::removeInvalidatedRangeElement);
        }

        var displacedTempo = initialTempoBeingRemoved(from);

        applyChange(
            new ElementRangeDeletion(this, from, to, deletedElements),
            () -> elements.subList(from, to + 1).clear()
        );

        reanchorInitialTempo(displacedTempo);
    }

    /**
     * The song's initial tempo when removing from {@code from} would take the anchor
     * element with it, else null. Read before the removal, while the anchor is still in
     * place. Suppressed during replay: the recorded batch already carries the companion
     * modification.
     */
    private @Nullable TempoChangeAttachment initialTempoBeingRemoved(int from) {
        if (song.isReplaying() || !isInitialTempoAnchor(from)) {
            return null;
        }

        return elements.get(from).findAttachment(TempoChangeAttachment.class);
    }

    /**
     * Moves the song's initial tempo onto the new first element after a removal took the
     * anchor away. The tempo describes the song, not the note it happened to sit on (see
     * {@link #isInitialTempoAnchor}), so it must not disappear along with that note — the
     * mirror image of the displacement {@link #addElement(int, StaffElement)} performs when
     * an insertion pushes the anchor aside.
     *
     * <p>Emitted after the primary deletion, since the new first element only reaches index
     * 0 once the removal has happened. Reverse-order undo therefore strips this tempo again
     * before re-inserting the element that owned it.
     */
    private void reanchorInitialTempo(@Nullable TempoChangeAttachment displacedTempo) {
        if (displacedTempo == null || elements.isEmpty()) {
            return;
        }

        var newFirstElement = elements.getFirst();

        // A tempo already on the new first element is the song's tempo in its own right —
        // attachInitialTempoIfNeeded defers to an existing attachment, and so does this.
        if (newFirstElement.findAttachment(TempoChangeAttachment.class) != null) {
            return;
        }

        modifyElement(0, ElementField.TEMPO_CHANGE,
            () -> newFirstElement.addAttachment(displacedTempo.copy(newFirstElement)));
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

        return element.getType().isGraceNote() && element.hasGlissando();
    }

    /** Returns true when the element at {@code index} is the host of a paired grace note. */
    public boolean isHostOfPairedGraceNote(int index) {
        return index >= 1 && isPairedGraceNote(index - 1);
    }

    /**
     * A breath mark immediately after {@code end} is positionally attached to the
     * last selected element, so it must be included in a deletion or copy range that
     * ends at {@code end}. Returns {@code end} extended past that trailing breath
     * mark, or {@code end} unchanged if there is none. Pure query — mutates nothing.
     */
    public int effectiveDeleteEnd(int end) {
        if (end + 1 < effectiveElementCount() && getElement(end + 1).getType().isBreathMark()) {
            return end + 1;
        }

        return end;
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
     * Returns true if one and the same {@link Tie} covers both {@code firstIndex} and
     * {@code secondIndex}.
     */
    public boolean sameTieAt(int firstIndex, int secondIndex) {
        var tieAtFirst = findTieAt(firstIndex);

        //noinspection ObjectEquality
        return tieAtFirst != null && tieAtFirst == findTieAt(secondIndex);
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
     * Returns true if one and the same {@link Beam} covers both {@code firstIndex} and
     * {@code secondIndex}.
     */
    public boolean sameBeamAt(int firstIndex, int secondIndex) {
        var beamAtFirst = findBeamAt(firstIndex);

        //noinspection ObjectEquality
        return beamAtFirst != null && beamAtFirst == findBeamAt(secondIndex);
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
        for (var i = fromIndex + step; i >= 0 && i < elementCount(); i += step) {
            if (!getElement(i).getType().isGraceNote()) {
                return i;
            }
        }

        return -1;
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
            // Beams do not absorb an adjacent beam: two beam groups written back to
            // back are two deliberate groupings, not one interrupted by an accident.
            mergeOverlappingSpans(beam, Beam.class, this::removeBeaming, false);
        }

        applyChange(new BeamingAddition(this, beam), () -> rangeElements.add(beam));
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
    private <R extends RangeElement> void mergeOverlappingSpans(
        R span,
        Class<? extends R> type,
        Consumer<? super R> remover,
        boolean absorbAdjacent
    ) {
        var anchorIdx = elements.indexOf(span.getAnchorElement());
        var endIdx = elements.indexOf(span.getEndElement());

        // How far past an endpoint an existing span may sit and still be absorbed.
        var reach = absorbAdjacent ? SPAN_ADJACENCY_REACH : 0;

        // Expand bounds to absorb adjacent/overlapping spans.
        var mergedAnchorIdx = anchorIdx;
        var mergedEndIdx = endIdx;

        for (var re : rangeElements) {
            if (type.isInstance(re)) {
                var existing = type.cast(re);
                var existingAnchor = existing.getAnchorElementIndex();
                var existingEnd = existing.getEndElementIndex();

                if (existingAnchor <= anchorIdx && anchorIdx <= existingEnd + reach) {
                    mergedAnchorIdx = Math.min(mergedAnchorIdx, existingAnchor);
                }

                if (existingAnchor - reach <= endIdx && endIdx <= existingEnd) {
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

        var finalMergedAnchor = mergedAnchorIdx;
        var finalMergedEnd = mergedEndIdx;
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
        BiFunction<? super Line, ? super H, ? extends Mutation> mutationFactory,
        Class<? extends H> type
    ) {
        hairpin.setParentLine(this);

        // During replay the recorded addition already carries the merged span
        // and the batch carries the absorbed-hairpin removals — just add.
        if (!song.isReplaying()) {
            // A hairpin drawn flush against a same-type one continues it rather than
            // starting a second: one uninterrupted dynamic gesture, one hairpin.
            mergeOverlappingSpans(hairpin, type, this::removeInvalidatedRangeElement, true);
        }

        applyChange(mutationFactory.apply(this, hairpin), () -> rangeElements.add(hairpin));
    }

    /**
     * Shared remove logic for crescendo and diminuendo hairpins.
     */
    private <H extends Hairpin> void removeHairpin(
        H hairpin,
        BiFunction<? super Line, H, ? extends Mutation> mutationFactory
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
     * A hairpin paired with the span it will occupy once a pending deletion is applied,
     * as inclusive indices into the elements that survive that deletion.
     */
    private record HairpinSpan(Hairpin hairpin, int begin, int end) {
    }

    /**
     * Reshapes this line's hairpins around the pending deletion of {@code deletedElements},
     * which must still be present in the line.
     * <p>
     * A hairpin whose endpoint is deleted pulls in to the nearest surviving element rather
     * than being dropped, and same-type hairpins the deletion leaves with nothing between
     * them become one — the same one-gesture-one-hairpin rule {@link #addHairpin} applies
     * when the user draws a hairpin flush against another. Only a hairpin left with fewer
     * than two elements is removed, having no gesture left to show.
     * <p>
     * A reshaped hairpin is expressed as a tracked removal plus a tracked addition of a
     * copy, since a range element has no modification mutation of its own. Hairpins are
     * therefore excluded from the invalidation pass in {@link #removeElement} and
     * {@link #removeRange}: this method is the whole of their response to a deletion.
     */
    private void adjustHairpinsForDeletion(List<StaffElement> deletedElements) {
        var hairpins = findRangeElements(Hairpin.class);

        // Every deletion in the app reaches this method, but most songs hold no hairpin
        // at all. Leave before building the survivor bookkeeping, which is a full pass
        // over the line and would be discarded unused.
        if (hairpins.isEmpty()) {
            return;
        }

        var doomed = new HashSet<>(deletedElements);

        // The post-deletion element order, computed while the doomed elements are still
        // in place so each hairpin's surviving endpoints can be resolved against it.
        // survivorIndices records each survivor's new position as the list is built, so
        // resolving an endpoint later is a lookup rather than another scan.
        var survivors = new ArrayList<StaffElement>();
        var survivorIndices = new HashMap<StaffElement, Integer>();

        for (var element : elements) {
            if (!doomed.contains(element)) {
                survivorIndices.computeIfAbsent(element, k -> survivors.size());
                survivors.add(element);
            }
        }

        var spansByType = new LinkedHashMap<Class<?>, List<HairpinSpan>>();

        for (var hairpin : hairpins) {
            var anchorIndex = hairpin.getAnchorElementIndex();
            var endIndex = hairpin.getEndElementIndex();

            // A hairpin not anchored in this line is not this deletion's to reshape.
            if (anchorIndex < 0 || endIndex < 0) {
                continue;
            }

            var span = survivingSpanOf(hairpin, anchorIndex, endIndex, survivors, survivorIndices);

            if (span == null) {
                removeInvalidatedRangeElement(hairpin);
                continue;
            }

            spansByType.computeIfAbsent(hairpin.getClass(), type -> new ArrayList<>()).add(span);
        }

        for (var spans : spansByType.values()) {
            mergeAdjacentSpans(spans, survivors);
        }
    }

    /**
     * Returns the span {@code hairpin} occupies among {@code survivors} once the pending
     * deletion is applied, or null when what survives cannot carry a hairpin — a wedge
     * needs a note to start on and another to end on.
     *
     * @param anchorIndex     the hairpin's anchor index in the pre-deletion line
     * @param endIndex        the hairpin's end index in the pre-deletion line
     * @param survivorIndices each surviving element's post-deletion position
     */
    private @Nullable HairpinSpan survivingSpanOf(
        Hairpin hairpin,
        int anchorIndex,
        int endIndex,
        List<StaffElement> survivors,
        Map<StaffElement, Integer> survivorIndices
    ) {
        // Nothing outside the range can fall between two elements inside it, so what
        // survives of the range is still described by its first and last position.
        var first = -1;
        var last = -1;

        for (var i = anchorIndex; i <= endIndex; i++) {
            // Absent means the element is one of the doomed ones.
            var survivorIndex = survivorIndices.get(elements.get(i));

            if (survivorIndex == null) {
                continue;
            }

            if (first < 0) {
                first = survivorIndex;
            }

            last = survivorIndex;
        }

        if (first < 0) {
            return null;
        }

        var begin = resolveBeginIndex(hairpin, first, last, survivors);
        var end = resolveEndIndex(hairpin, first, last, survivors);

        // One note, or none, leaves no gesture to draw.
        if (begin < 0 || end < 0 || begin >= end) {
            return null;
        }

        return new HairpinSpan(hairpin, begin, end);
    }

    /**
     * Returns the post-deletion position of {@code hairpin}'s anchor, given that its
     * elements survive from {@code first} through {@code last}, or -1 when none of them
     * can begin a hairpin.
     * <p>
     * A surviving anchor stays where it is — a hairpin an older build left anchored on a
     * rest is not this deletion's to correct. A deleted one moves in to the first element
     * that can begin one: a pitched note, or a grace note whose host is one, matching what
     * the app allows when the hairpin is drawn.
     */
    private static int resolveBeginIndex(
        Hairpin hairpin,
        int first,
        int last,
        List<? extends StaffElement> survivors
    ) {
        if (survivors.get(first) == hairpin.getAnchorElement()) {
            return first;
        }

        for (var i = first; i <= last; i++) {
            if (canAnchorHairpin(survivors, i, last)) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns whether the element at {@code index} can begin a hairpin: a pitched note,
     * or a grace note whose host is one.
     *
     * @param lastIndex the last index a hairpin may reach, bounding the host lookahead
     */
    public boolean canAnchorHairpin(int index, int lastIndex) {
        return canAnchorHairpin(elements, index, lastIndex);
    }

    /**
     * Returns whether the element at {@code index} in {@code candidates} can begin a
     * hairpin.
     * <p>
     * A pitched note always can. A grace note belongs to the note it precedes, so it
     * can begin one when that note is pitched — anchoring on the grace note rather than
     * its host keeps the hairpin over what the user actually selected. Anything else,
     * including a grace note with no host within reach, cannot.
     * <p>
     * Both the menu's eligibility test and the post-deletion reshaping read this, so a
     * deletion can never leave a hairpin anchored somewhere the user could not have
     * placed one.
     *
     * @param candidates the elements to test against, in document order
     * @param lastIndex  the last usable index, bounding the host lookahead
     */
    private static boolean canAnchorHairpin(List<? extends StaffElement> candidates, int index, int lastIndex) {
        var type = candidates.get(index).getType();

        return type.isPitchedNote()
            || (type.isGraceNote()
                && index < lastIndex
                && candidates.get(index + 1).getType().isPitchedNote());
    }

    /**
     * Returns the post-deletion position of {@code hairpin}'s end, given that its elements
     * survive from {@code first} through {@code last}, or -1 when none of them can end a
     * hairpin. A surviving end stays where it is; a deleted one moves in to the last
     * surviving pitched note, which is the only thing a hairpin may end on.
     */
    private static int resolveEndIndex(
        Hairpin hairpin,
        int first,
        int last,
        List<? extends StaffElement> survivors
    ) {
        if (survivors.get(last) == hairpin.getEndElement()) {
            return last;
        }

        for (var i = last; i >= first; i--) {
            if (survivors.get(i).getType().isPitchedNote()) {
                return i;
            }
        }

        return -1;
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
        doomed.sort(Comparator.<Hairpin>comparingInt(rangeElements::indexOf).reversed());
        doomed.forEach(this::removeInvalidatedRangeElement);

        var reshaped = (Hairpin) first.copy(anchorElement, endElement);

        switch (reshaped) {
            case Crescendo crescendo -> addCrescendo(crescendo);
            case Diminuendo diminuendo -> addDiminuendo(diminuendo);
        }
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


    public int getFirstTempoChange() {
        // On the first line the base tempo is anchored on the first element
        // (see isInitialTempoAnchor), so the tempo marking belongs to that element.
        if (isInitialTempoAnchor(0) && elementCount() > 0) {
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
    @SuppressWarnings("UnusedReturnValue")
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
     * <p>
     * Every branch is a no-op when the span is no longer in this line, so callers
     * that cannot cheaply tell whether an earlier step already removed it (the paste
     * reconciliation in {@code ScoreViewController.tryInsertFragment}) may call it
     * unconditionally.
     */
    public void removeInvalidatedRangeElement(RangeElement rangeElement) {
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
     * Adds a span that a paste is inserting, routing hairpins through the merge-aware
     * {@link #addCrescendo}/{@link #addDiminuendo} so a pasted hairpin landing flush
     * against a same-type one already on this line becomes a single hairpin, exactly
     * as if the user had drawn it there.
     * <p>
     * Every other kind is added verbatim: {@code PasteSpanReconciliation} has already
     * guaranteed no same-kind overlap survives the paste, so the other adders'
     * displacement logic has nothing left to resolve.
     */
    public void addPastedRangeElement(RangeElement rangeElement) {
        switch (rangeElement) {
            case Crescendo crescendo -> addCrescendo(crescendo);
            case Diminuendo diminuendo -> addDiminuendo(diminuendo);
            default -> addRangeElement(rangeElement);
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

    private record ExtendFix(int lyricIndex, int verse, Lyric.Extend newExtend) {}

    // =========================================================================
    // Beam Group Management (Phase 4+)
    // =========================================================================

}
