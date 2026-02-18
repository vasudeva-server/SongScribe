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
import java.util.List;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;

import songscribe.data.IntervalSet;
import songscribe.ui.layout.BeamGroup;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.MessageCenter;

public class Line {

    private static final int[][] FLAT_SHARP_ORDINAL = new int[][]{
        new int[]{0, 3, 6, 2, 5, 1, 4},
        new int[]{4, 1, 5, 2, 6, 3, 0},
    };
    private final IntervalSet beamings = new IntervalSet();
    private final IntervalSet ties = new IntervalSet();
    private final IntervalSet tuplets = new IntervalSet();
    private final IntervalSet firstSecondEndings = new IntervalSet();
    private final IntervalSet crescendo = new IntervalSet();
    private final IntervalSet diminuendo = new IntervalSet();
    private final IntervalSet[] intervalSets = new IntervalSet[]{
        beamings,
        ties,
        tuplets,
        firstSecondEndings,
        crescendo,
        diminuendo,
    };

    // =========================================================================
    // New storage for Phase 4+ layout redesign
    // These will replace IntervalSets after Phase 7 (IO) migration
    // =========================================================================

    /** Range elements (ties, trills, crescendo, diminuendo, tuplets, endings). */
    private final List<RangeElement> rangeElements = new ArrayList<>();

    /** Beam groups coordinating note beaming. */
    private final List<BeamGroup> beamGroups = new ArrayList<>();

    // acceleration
    public Note.SyllableRelation beginRelation = Note.SyllableRelation.NO;
    private Composition composition = null;
    private int keys = 0;
    private KeyType keyType = null;
    private final List<Note> notes = new ArrayList<>();

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
     * @deprecated Use per-instance userYOffset on TempoAttachment instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int tempoChangeYPos = 0;

    /**
     * Y offset for beat change display (default: -24, above staff).
     *
     * @deprecated Use per-instance userYOffset on BeatChangeAttachment instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int beatChangeYPos = LayoutStylesheet.BEAT_CHANGE_DEFAULT_Y;

    /**
     * Y offset for lyrics display (default: 50, below staff).
     * <p>
     * Note: This field is still in active use for line-level lyrics positioning.
     * Per-instance lyrics offsets are not yet implemented.
     */
    private int lyricsYPos = LayoutStylesheet.LYRICS_DEFAULT_Y;

    /**
     * Y offset for first/second ending display (default: -25, above staff).
     *
     * @deprecated Use per-instance yPosition on Ending objects instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int firstSecondEndingYPos = LayoutStylesheet.ENDING_DEFAULT_Y;

    /**
     * Y offset for trill display (default: -27, above staff).
     *
     * @deprecated Use per-instance yPosition on Trill objects instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int trillYPos = LayoutStylesheet.TRILL_DEFAULT_Y;

    /** Ratio multiplier for horizontal note spacing (default: 1.0, user-adjustable). */
    private float noteDistChangeRatio = 1f;


    public Composition getComposition() {
        return composition;
    }

    public void setComposition(Composition composition) {
        this.composition = composition;
    }

    public int getKeyAccidentalCount() {
        return keys;
    }

    public void setKeyAccidentalCount(int keys) {
        modifiedComposition();
        this.keys = keys;
    }

    public KeyType getKeyType() {
        return keyType;
    }

    public void setKeyType(KeyType keyType) {
        modifiedComposition();
        this.keyType = keyType;
    }

    public void addNote(Note note) {
        modifiedComposition();
        note.setLine(this);
        notes.add(note);
        attachInitialTempoIfNeeded(note);
    }

    public void addNote(int index, Note note) {
        modifiedComposition();
        note.setLine(this);
        notes.add(index, note);
        shiftIntervals(intervalSets, index, 1);
        attachInitialTempoIfNeeded(note);
    }

    public void setNote(int index, Note note) {
        modifiedComposition();
        note.setLine(this);
        notes.set(index, note);
        attachInitialTempoIfNeeded(note);
    }

    /**
     * Attaches the composition's initial tempo to the first note of the first line
     * if it doesn't already have a tempo change.
     */
    private void attachInitialTempoIfNeeded(Note note) {
        if (composition == null) {
            return;
        }

        // Check if this is the first note of the first line
        boolean isFirstLine = composition.indexOfLine(this) == 0;
        boolean isFirstNote = notes.size() == 1 && notes.get(0) == note;

        if (isFirstLine && isFirstNote && note.getTempoChange() == null) {
            var initialTempo = composition.getTempo();

            if (initialTempo != null) {
                note.setTempoChange(initialTempo);
            }
        }
    }

    public Note getNote(int index) {
        return notes.get(index);
    }

    public List<Note> getNotes() {
        return notes;
    }

    // Returns a sublist of notes from start to end inclusive
    public List<Note> getNotes(int start, int end) {
        // subList is exclusive of the end index, so we add 1
        return notes.subList(start, end + 1);
    }

    public void removeNote(int index) {
        modifiedComposition();
        notes.remove(index);
        shiftIntervals(intervalSets, index, -1);
    }

    private void modifiedComposition() {
        if (composition != null) {
            composition.setModified(true);

            MessageCenter.post(new LayoutChangeMessage(
                LayoutChangeMessage.Section.SCORE,
                LayoutChangeMessage.ChangeType.CONTENT,
                true
            ));
        }
    }

    public int noteCount() {
        return notes.size();
    }

    public boolean isEmpty() {
        return notes.isEmpty();
    }

    public int getNoteIndex(Note note) {
        return notes.indexOf(note);
    }

    /**
     * @param pitchType 0 for B, 1 for C, 2 for D, ..., 6 for A
     * @return true if there is a leading key for that pitch type
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean keyExists(int pitchType) {
        return IntStream.range(0, keys).anyMatch(
            i -> FLAT_SHARP_ORDINAL[keyType.ordinal() - 1][i] == pitchType
        );
    }

    /**
     * @deprecated Use per-instance userYOffset on TempoAttachment instead.
     */
    @Deprecated
    public int getTempoChangeYPos() {
        return tempoChangeYPos;
    }

    /**
     * @deprecated Use per-instance userYOffset on TempoAttachment instead.
     */
    @Deprecated
    public void setTempoChangeYPos(int tempoChangeYPos) {
        this.tempoChangeYPos = tempoChangeYPos;
        modifiedComposition();
    }

    /**
     * @deprecated Use per-instance userYOffset on BeatChangeAttachment instead.
     */
    @Deprecated
    public int getBeatChangeYPos() {
        return beatChangeYPos;
    }

    /**
     * @deprecated Use per-instance userYOffset on BeatChangeAttachment instead.
     */
    @Deprecated
    public void setBeatChangeYPos(int beatChangeYPos) {
        this.beatChangeYPos = beatChangeYPos;
        modifiedComposition();
    }

    public int getLyricsYPos() {
        return lyricsYPos;
    }

    public void setLyricsYPos(int lyricsYPos) {
        this.lyricsYPos = lyricsYPos;
        modifiedComposition();
    }

    /**
     * @deprecated Use per-instance yPosition on Ending objects instead.
     */
    @Deprecated
    public int getFirstSecondEndingYPos() {
        return firstSecondEndingYPos;
    }

    /**
     * @deprecated Use per-instance yPosition on Ending objects instead.
     */
    @Deprecated
    public void setFirstSecondEndingYPos(int fsEndingYPos) {
        firstSecondEndingYPos = fsEndingYPos;
        modifiedComposition();
    }

    /**
     * @deprecated Use per-instance yPosition on Trill objects instead.
     */
    @Deprecated
    public int getTrillYPos() {
        return trillYPos;
    }

    /**
     * @deprecated Use per-instance yPosition on Trill objects instead.
     */
    @Deprecated
    public void setTrillYPos(int trillYPos) {
        this.trillYPos = trillYPos;
        modifiedComposition();
    }

    public void mulNoteDistChange(float ratio) {
        noteDistChangeRatio *= ratio;
        modifiedComposition();
    }

    public float getNoteDistChangeRatio() {
        return noteDistChangeRatio;
    }

    public IntervalSet getBeamings() {
        return beamings;
    }

    public IntervalSet getTies() {
        return ties;
    }

    public IntervalSet getTuplets() {
        return tuplets;
    }

    public IntervalSet getFirstSecondEndings() {
        return firstSecondEndings;
    }

    public IntervalSet getCrescendos() {
        return crescendo;
    }

    public IntervalSet getDiminuendos() {
        return diminuendo;
    }

    public void removeInterval(int a, int b) {
        for (var is : intervalSets) {
            is.removeInterval(a, b);
        }
    }

    public IntervalSet[] copyIntervals(int a, int b) {
        var retIs = Arrays.stream(intervalSets)
            .map(intervalSet -> intervalSet.copyInterval(a, b))
            .toArray(IntervalSet[]::new);

        shiftIntervals(retIs, 0, -a);
        return retIs;
    }

    public void pasteIntervals(IntervalSet[] copyIntervalSets, int xIndex) {
        shiftIntervals(copyIntervalSets, 0, xIndex);

        for (var i = 0; i < intervalSets.length; i++) {
            for (var li = copyIntervalSets[i].listIterator(); li.hasNext(); ) {
                var iv = li.next();
                intervalSets[i].addInterval(
                    iv.getStart(),
                    iv.getEnd(),
                    iv.getData()
                );
            }
        }

        shiftIntervals(copyIntervalSets, 0, -xIndex);
    }

    private void shiftIntervals(IntervalSet[] iss, int from, int shift) {
        for (var is : iss) {
            is.shiftValues(from, shift);
            is.removeInterval(Integer.MIN_VALUE, 0);
            is.removeInterval(notes.size() - 1, Integer.MAX_VALUE);
        }
    }

    public int getFirstTempoChange() {
        if ((composition.indexOfLine(this) == 0) && (noteCount() > 0)) {
            return 0;
        }

        return IntStream.range(0, noteCount())
            .filter(n -> getNote(n).getTempoChange() != null)
            .findFirst()
            .orElse(-1);
    }

    public boolean isAnnotation() {
        return IntStream.range(0, noteCount()).anyMatch(
            n -> getNote(n).getAnnotation() != null
        );
    }

    public int getFirstTrill() {
        return IntStream.range(0, noteCount())
            .filter(n -> getNote(n).isTrill())
            .findFirst()
            .orElse(-1);
    }

    public int getFirstBeatChange() {
        return IntStream.range(0, noteCount())
            .filter(n -> getNote(n).getBeatChange() != null)
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
    public void addRangeElement(@NotNull RangeElement element) {
        element.setParentLine(this);
        rangeElements.add(element);
        modifiedComposition();
    }

    /**
     * Removes a range element from this line.
     *
     * @param element The range element to remove
     * @return true if the element was removed
     */
    public boolean removeRangeElement(@NotNull RangeElement element) {
        if (rangeElements.remove(element)) {
            element.setParentLine(null);
            modifiedComposition();

            return true;
        }

        return false;
    }

    /**
     * Returns an unmodifiable view of the range elements in this line.
     */
    public @NotNull List<RangeElement> getRangeElements() {
        return Collections.unmodifiableList(rangeElements);
    }

    /**
     * Finds all range elements that include the specified note index.
     *
     * @param noteIndex The note index to search for
     * @return List of range elements containing the note
     */
    public @NotNull List<RangeElement> findRangeElementsAt(int noteIndex) {
        var result = new ArrayList<RangeElement>();

        for (var element : rangeElements) {
            int start = element.getAnchorNoteIndex();
            int end = element.getEndNoteIndex();

            if (noteIndex >= start && noteIndex <= end) {
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
    public <T extends RangeElement> @NotNull List<T> findRangeElements(@NotNull Class<T> type) {
        var result = new ArrayList<T>();

        for (var element : rangeElements) {
            if (type.isInstance(element)) {
                result.add((T) element);
            }
        }

        return result;
    }

    // =========================================================================
    // Beam Group Management (Phase 4+)
    // =========================================================================

    /**
     * Adds a beam group to this line.
     *
     * @param group The beam group to add
     */
    public void addBeamGroup(@NotNull BeamGroup group) {
        group.setParentLine(this);
        beamGroups.add(group);
        modifiedComposition();
    }

    /**
     * Removes a beam group from this line.
     *
     * @param group The beam group to remove
     * @return true if the group was removed
     */
    public boolean removeBeamGroup(@NotNull BeamGroup group) {
        if (beamGroups.remove(group)) {
            group.setParentLine(null);
            modifiedComposition();

            return true;
        }

        return false;
    }

    /**
     * Returns an unmodifiable view of the beam groups in this line.
     */
    public @NotNull List<BeamGroup> getBeamGroups() {
        return Collections.unmodifiableList(beamGroups);
    }

    /**
     * Finds the beam group containing the specified note.
     *
     * @param note The note to search for
     * @return The beam group containing the note, or null if not found
     */
    public BeamGroup findBeamGroupFor(@NotNull Note note) {
        for (var group : beamGroups) {
            if (group.getBeamedNotes().contains(note)) {
                return group;
            }
        }

        return null;
    }

}
