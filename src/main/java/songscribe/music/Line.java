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
import java.util.List;
import java.util.stream.IntStream;

import songscribe.data.IntervalSet;
import songscribe.ui.component.Score;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.MessageCenter;

public class Line {

    private static final int[][] FLAT_SHARP_ORDINAL = new int[][] {
        new int[] { 0, 3, 6, 2, 5, 1, 4 },
        new int[] { 4, 1, 5, 2, 6, 3, 0 },
    };
    private final IntervalSet beamings = new IntervalSet();
    private final IntervalSet ties = new IntervalSet();
    private final IntervalSet slurs = new IntervalSet();
    private final IntervalSet tuplets = new IntervalSet();
    private final IntervalSet firstSecondEndings = new IntervalSet();
    private final IntervalSet crescendo = new IntervalSet();
    private final IntervalSet diminuendo = new IntervalSet();
    private final IntervalSet[] intervalSets = new IntervalSet[] {
        beamings,
        ties,
        tuplets,
        firstSecondEndings,
        slurs,
        crescendo,
        diminuendo,
    };
    // acceleration
    public Note.SyllableRelation beginRelation = Note.SyllableRelation.NO;
    private Composition composition = null;
    private int keys = 0;
    private KeyType keyType = null;
    private final List<Note> notes = new ArrayList<>();
    // view properties
    private int tempoChangeYPos = 0;
    private int beatChangeYPos = -24;
    private int lyricsYPos = 50;
    private int firstSecondEndingYPos = -25;
    private int trillYPos = -27;
    private float noteDistChangeRatio = 1f;

    // Cached required height for this line; -1 means not calculated
    private int cachedRequiredHeight = -1;

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
    }

    public void addNote(int index, Note note) {
        modifiedComposition();
        note.setLine(this);
        notes.add(index, note);
        shiftIntervals(intervalSets, index, 1);
    }

    public void setNote(int index, Note note) {
        modifiedComposition();
        note.setLine(this);
        notes.set(index, note);
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
        cachedRequiredHeight = -1;

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

    public int getTempoChangeYPos() {
        return tempoChangeYPos;
    }

    public void setTempoChangeYPos(int tempoChangeYPos) {
        this.tempoChangeYPos = tempoChangeYPos;
        modifiedComposition();
    }

    public int getBeatChangeYPos() {
        return beatChangeYPos;
    }

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

    public int getFirstSecondEndingYPos() {
        return firstSecondEndingYPos;
    }

    public void setFirstSecondEndingYPos(int fsEndingYPos) {
        firstSecondEndingYPos = fsEndingYPos;
        modifiedComposition();
    }

    public int getTrillYPos() {
        return trillYPos;
    }

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

    public IntervalSet getSlurs() {
        return slurs;
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
            for (var li = copyIntervalSets[i].listIterator(); li.hasNext();) {
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

    /**
     * Invalidates the cached required height, forcing recalculation on next access.
     */
    public void invalidateHeightCache() {
        cachedRequiredHeight = -1;
    }

    /**
     * Returns the required vertical height for this line based on all its elements.
     * <p>
     * The height is calculated from the topmost element (most negative Y) to the
     * bottommost element (most positive Y), including:
     * <ul>
     *   <li>Staff lines themselves (5 lines spanning yPos -4 to +4)</li>
     *   <li>Notes extending above/below staff (ledger lines)</li>
     *   <li>Tempo change markers (at tempoChangeYPos if present)</li>
     *   <li>Beat change markers (at beatChangeYPos if present)</li>
     *   <li>First/second endings (at firstSecondEndingYPos if present)</li>
     *   <li>Trills (at trillYPos if present)</li>
     *   <li>Note annotations (at their Y positions)</li>
     *   <li>Inline lyrics (at lyricsYPos if notes have syllables)</li>
     * </ul>
     * <p>
     * The result is cached and invalidated when line content changes.
     *
     * @return The required height in pixels
     */
    public int getRequiredHeight() {
        if (cachedRequiredHeight >= 0) {
            return cachedRequiredHeight;
        }

        // Base staff height: 5 lines at yPos -4, -2, 0, 2, 4
        // Staff spans from -4 to +4 in note units = -32 to +32 pixels from middle
        int minY = -4 * Score.STAFF_LINE_Y_OFFSET; // -32 (top of staff)
        int maxY = 4 * Score.STAFF_LINE_Y_OFFSET;  // +32 (bottom of staff)

        // Check for tempo changes (first line always has tempo at note 0)
        if (getFirstTempoChange() >= 0) {
            minY = Math.min(minY, tempoChangeYPos);
        }

        // Check for beat changes
        if (getFirstBeatChange() >= 0) {
            minY = Math.min(minY, beatChangeYPos);
        }

        // Check for first/second endings
        if (!firstSecondEndings.isEmpty()) {
            minY = Math.min(minY, firstSecondEndingYPos);
        }

        // Check for trills
        if (getFirstTrill() >= 0) {
            minY = Math.min(minY, trillYPos);
        }

        // Track if there are syllables (for lyrics positioning)
        boolean hasSyllables = false;

        // Check all notes for annotations and extreme positions
        for (var note : notes) {
            var noteYPos = note.getYPos();

            // Notes extend from ledger lines above to ledger lines below
            // Convert note yPos to pixels (each note step is NOTE_Y_OFFSET = 4 pixels)
            int notePixelY = (int) (noteYPos * Score.NOTE_Y_OFFSET);
            minY = Math.min(minY, notePixelY - Score.STAFF_LINE_Y_OFFSET); // margin above note
            maxY = Math.max(maxY, notePixelY + Score.STAFF_LINE_Y_OFFSET); // margin below note

            // Check annotation position
            var annotation = note.getAnnotation();

            if (annotation != null) {
                int annotationY = annotation.getYPos();

                if (annotationY < 0) {
                    minY = Math.min(minY, annotationY);
                } else {
                    maxY = Math.max(maxY, annotationY);
                }
            }

            // Check for syllables (stored in acceleration.syllable)
            var syllable = note.acceleration.syllable;

            if (syllable != null && !syllable.isEmpty()) {
                hasSyllables = true;
            }
        }

        // Include lyrics position if there are syllables
        if (hasSyllables) {
            maxY = Math.max(maxY, lyricsYPos);
        }

        cachedRequiredHeight = maxY - minY;
        return cachedRequiredHeight;
    }
}
