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
        if (composition != null) {
            composition.setModified(true);
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
}
