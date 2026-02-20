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

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.jetbrains.annotations.NotNull;

import kotlin.Pair;
import songscribe.data.BeamInterval;
import songscribe.data.DynamicsInterval;
import songscribe.data.EndingInterval;
import songscribe.data.Interval;
import songscribe.data.IntervalSet;
import songscribe.data.TupletInterval;
import songscribe.midi.PlaybackSettings;
import songscribe.ui.layout.BeamGroup;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.RangeElement;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.playback.MidiMetaMessageTypes;

public class Line {

    private static final int[][] FLAT_SHARP_ORDINAL = new int[][]{
        new int[]{0, 3, 6, 2, 5, 1, 4},
        new int[]{4, 1, 5, 2, 6, 3, 0},
    };

    // MIDI constants for playback
    private static final int PPQ = 96;
    private static final int GRACE_QUAVER_DURATION = PPQ / 8;
    private static final int NOTE_VELOCITY = 98;
    private static final int ACCENTED_NOTE_VELOCITY = 127;
    private final IntervalSet<BeamInterval> beamings = new IntervalSet<>();
    private final IntervalSet<Interval> ties = new IntervalSet<>();
    private final IntervalSet<TupletInterval> tuplets = new IntervalSet<>();
    private final IntervalSet<EndingInterval> firstSecondEndings = new IntervalSet<>();
    private final IntervalSet<DynamicsInterval> crescendo = new IntervalSet<>();
    private final IntervalSet<DynamicsInterval> diminuendo = new IntervalSet<>();
    @SuppressWarnings("rawtypes")
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
    private int beatChangeYPos = LayoutStylesheet.toPixels(LayoutStylesheet.BEAT_CHANGE_DEFAULT_Y);

    /**
     * Y offset for lyrics display (default: 50, below staff).
     * <p>
     * Note: This field is still in active use for line-level lyrics positioning.
     * Per-instance lyrics offsets are not yet implemented.
     */
    private double lyricsYPos = LayoutStylesheet.LYRICS_DEFAULT_Y;

    /**
     * Y offset for first/second ending display (default: -25, above staff).
     *
     * @deprecated Use per-instance yPosition on Ending objects instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int firstSecondEndingYPos = LayoutStylesheet.toPixels(LayoutStylesheet.ENDING_DEFAULT_Y);

    /**
     * Y offset for trill display (default: -27, above staff).
     *
     * @deprecated Use per-instance yPosition on Trill objects instead.
     *             Retained for backward compatibility with legacy documents.
     */
    @Deprecated
    private int trillYPos = LayoutStylesheet.toPixels(LayoutStylesheet.TRILL_DEFAULT_Y);

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
        note.setLine(this);
        notes.add(note);
        attachInitialTempoIfNeeded(note);
        modifiedComposition();
    }

    public void addNote(int index, Note note) {
        note.setLine(this);
        notes.add(index, note);
        shiftIntervals(intervalSets, index, 1);
        attachInitialTempoIfNeeded(note);
        modifiedComposition();
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
                true,
                this
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

    public double getLyricsYPos() {
        return lyricsYPos;
    }

    public void setLyricsYPos(double lyricsYPos) {
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

    public IntervalSet<BeamInterval> getBeamings() {
        return beamings;
    }

    public IntervalSet<Interval> getTies() {
        return ties;
    }

    public IntervalSet<TupletInterval> getTuplets() {
        return tuplets;
    }

    public IntervalSet<EndingInterval> getFirstSecondEndings() {
        return firstSecondEndings;
    }

    public IntervalSet<DynamicsInterval> getCrescendos() {
        return crescendo;
    }

    public IntervalSet<DynamicsInterval> getDiminuendos() {
        return diminuendo;
    }

    public void removeInterval(int a, int b) {
        for (var is : intervalSets) {
            is.removeInterval(a, b);
        }
    }

    public IntervalSet<?>[] copyIntervals(int a, int b) {
        var retIs = Arrays.stream(intervalSets)
            .map(intervalSet -> intervalSet.copyInterval(a, b))
            .toArray(IntervalSet[]::new); //noinspection unchecked

        shiftIntervals(retIs, 0, -a);
        return retIs;
    }

    public void pasteIntervals(IntervalSet<?>[] copyIntervalSets, int xIndex) {
        shiftIntervals(copyIntervalSets, 0, xIndex);

        for (var i = 0; i < intervalSets.length; i++) {
            for (var li = copyIntervalSets[i].listIterator(); li.hasNext(); ) {
                var iv = li.next();
                //noinspection unchecked,rawtypes
                ((IntervalSet) intervalSets[i]).addInterval(iv);
            }
        }

        shiftIntervals(copyIntervalSets, 0, -xIndex);
    }

    private void shiftIntervals(IntervalSet<?>[] iss, int from, int shift) {
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

    // =========================================================================
    // MIDI Duration Calculation (Phase 1: Score Cleanup)
    // =========================================================================

    /**
     * Returns the duration of a note adjusted for tuplet membership.
     *
     * @param noteIndex Index of the note
     * @param referenceTempo The tempo providing the reference note duration
     * @return Duration in ticks, adjusted for tuplet if applicable
     */
    public int getNoteDurationWithTuplet(int noteIndex, Tempo referenceTempo) {
        return Math.round(getNote(noteIndex).getDuration() * getTupletFactor(noteIndex, referenceTempo));
    }

    /**
     * Calculates the tuplet scaling factor for a note.
     *
     * @param noteIndex Index of the note
     * @param referenceTempo The tempo providing the reference note duration
     * @return Scaling factor (1.0 if not in a tuplet)
     */
    private float getTupletFactor(int noteIndex, Tempo referenceTempo) {
        var tupletInt = tuplets.findInterval(noteIndex);

        if (tupletInt == null) {
            return 1;
        }

        var tupletDuration = 0f;

        for (var i = tupletInt.getStart(); i <= tupletInt.getEnd(); i++) {
            tupletDuration += getNote(i).getDuration();
        }

        tupletDuration /= referenceTempo.getTempoType().getNote().getDuration();
        float newDuration;

        if (tupletDuration >= 1) {
            newDuration = (float) Math.floor(tupletDuration);

            if ((newDuration == tupletDuration) && (newDuration > 1)) {
                newDuration--;
            }
        } else {
            var log2 = Math.log(2);
            newDuration = (float) Math.pow(
                2,
                Math.floor(Math.log(tupletDuration) / log2)
            );
        }

        return newDuration / tupletDuration;
    }

    /**
     * Adds this line's notes to a MIDI track.
     *
     * @param track The MIDI track to add to
     * @param lineIndex This line's index in the composition (for colorize messages)
     * @param startTicks Starting tick position
     * @param initialTempo Tempo at the start of this line
     * @param settings Playback settings
     * @return Pair of (ending tick position, ending tempo)
     */
    public Pair<Integer, Tempo> addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings
    ) throws InvalidMidiDataException {
        return addToTrack(track, lineIndex, startTicks, initialTempo, settings, 0, noteCount() - 1);
    }

    /**
     * Adds a range of this line's notes to a MIDI track.
     *
     * @param track The MIDI track to add to
     * @param lineIndex This line's index in the composition (for colorize messages)
     * @param startTicks Starting tick position
     * @param initialTempo Tempo at the start of this range
     * @param settings Playback settings
     * @param startNote Index of the first note to add
     * @param endNote Index of the last note to add
     * @return Pair of (ending tick position, ending tempo)
     */
    public Pair<Integer, Tempo> addToTrack(
        Track track,
        int lineIndex,
        int startTicks,
        Tempo initialTempo,
        PlaybackSettings settings,
        int startNote,
        int endNote
    ) throws InvalidMidiDataException {
        var ticks = startTicks;
        var currentTempo = initialTempo;

        var actualEndNote = Math.min(endNote, noteCount() - 1);

        for (var i = startNote; i <= actualEndNote; i++) {
            var note = getNote(i);

            // Add tempo change if present
            if (note.getTempoChange() != null) {
                currentTempo = note.getTempoChange();
                addTempoMetaMessage(track, ticks, currentTempo, settings.tempoChangePercent());
            }

            // Add colorize message if enabled
            if (settings.colorizeNotes()) {
                addColorizeMetaMessage(track, lineIndex, i, ticks);
            }

            // Add note on/off messages and update ticks
            ticks = addNoteMessages(track, i, ticks, currentTempo, settings);
        }

        return new Pair<>(ticks, currentTempo);
    }

    /**
     * Adds a tempo meta message to the track.
     */
    private void addTempoMetaMessage(
        Track track,
        int ticks,
        Tempo tempo,
        int tempoChangePercent
    ) throws InvalidMidiDataException {
        var realTempo = tempo.getRealTempo();
        var midiTempo = 60000000 / ((realTempo * tempoChangePercent) / 100);
        var tempoMessage = new MetaMessage();
        tempoMessage.setMessage(
            MidiMetaMessageTypes.SET_TEMPO,
            new byte[]{
                (byte) (midiTempo >> 16),
                (byte) (midiTempo >> 8),
                (byte) midiTempo,
            },
            3
        );
        track.add(new MidiEvent(tempoMessage, ticks));
    }

    /**
     * Adds a colorize meta message to the track for playback highlighting.
     */
    private void addColorizeMetaMessage(
        Track track,
        int lineIndex,
        int noteIndex,
        int ticks
    ) throws InvalidMidiDataException {
        var playNoteMessage = new MetaMessage();
        playNoteMessage.setMessage(
            MidiMetaMessageTypes.SEQUENCE_NUMBER,
            new byte[]{
                (byte) (lineIndex >> 8),
                (byte) lineIndex,
                (byte) (noteIndex >> 8),
                (byte) noteIndex,
            },
            4
        );
        track.add(new MidiEvent(playNoteMessage, ticks));
    }

    /**
     * Adds note on/off messages to the track and returns the updated tick position.
     */
    private int addNoteMessages(
        Track track,
        int noteIndex,
        int ticks,
        Tempo currentTempo,
        PlaybackSettings settings
    ) throws InvalidMidiDataException {
        var note = getNote(noteIndex);
        var type = note.getNoteType();
        var trackTicks = ticks;

        if (type.isGraceNote()) {
            addNoteOn(track, trackTicks, note);
            trackTicks += GRACE_QUAVER_DURATION;
            addNoteOff(track, trackTicks, note);
        } else if (type.isNote() || type.isRest()) {
            var noteDuration = getNoteDurationWithTuplet(noteIndex, currentTempo);

            if (type.isNote()) {
                var interval = ties.findInterval(noteIndex);

                if ((interval == null) || (interval.getStart() == noteIndex)) {
                    addNoteOn(track, trackTicks, note);
                }

                if ((interval == null) || (interval.getEnd() == noteIndex)) {
                    var midiOverride = note.findMidiDurationOverride();
                    var currDuration = (midiOverride < 0)
                        ? settings.noteDurationPercent()
                        : midiOverride;
                    addNoteOff(
                        track,
                        (int) (trackTicks + ((noteDuration * currDuration) / 100f)),
                        note
                    );
                }
            }

            trackTicks += noteDuration;
        }

        return trackTicks;
    }

    /**
     * Adds a note-on MIDI message to the track.
     */
    private void addNoteOn(Track track, int ticks, Note note) throws InvalidMidiDataException {
        var down = new ShortMessage();
        down.setMessage(
            ShortMessage.NOTE_ON, 0,
            note.getPitch(),
            note.hasArticulation(ArticulationType.ACCENT)
                ? ACCENTED_NOTE_VELOCITY
                : NOTE_VELOCITY
        );
        track.add(new MidiEvent(down, ticks));
    }

    /**
     * Adds a note-off MIDI message to the track.
     */
    private void addNoteOff(Track track, int ticks, Note note) throws InvalidMidiDataException {
        var up = new ShortMessage();
        up.setMessage(ShortMessage.NOTE_OFF, 0, note.getPitch(), 0);
        track.add(new MidiEvent(up, ticks));
    }

}
