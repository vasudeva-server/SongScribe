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

package songscribe.midi;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.jetbrains.annotations.NotNull;

import songscribe.music.Composition;
import songscribe.music.NoteType;
import songscribe.music.Tempo;
import songscribe.ui.playback.MidiMetaMessageTypes;

/**
 * Coordinates MIDI sequence building for a composition.
 * Delegates note-level MIDI generation to Line objects.
 * <p>
 * Phase 1 of the Score Cleanup refactoring (Step 5).
 */
public class MidiSequenceBuilder {

    private static final int PPQ = 96;

    private final Composition composition;
    private final PlaybackSettings settings;

    /**
     * Creates a new MIDI sequence builder.
     *
     * @param composition The composition to build a sequence from
     * @param settings Playback settings (instrument, tempo adjustment, note duration, colorize)
     */
    public MidiSequenceBuilder(@NotNull Composition composition, @NotNull PlaybackSettings settings) {
        this.composition = composition;
        this.settings = settings;
    }

    /**
     * Builds a MIDI sequence for the entire composition.
     *
     * @return The complete MIDI sequence
     * @throws InvalidMidiDataException if MIDI data is invalid
     */
    public Sequence buildFullSequence() throws InvalidMidiDataException {
        return buildSequence(0, 0, -1, -1, composition.getTempo());
    }

    /**
     * Builds a MIDI sequence for a selection within a single line.
     *
     * @param lineIndex The line containing the selection
     * @param startNote The first note index in the selection
     * @param endNote The last note index in the selection
     * @return The MIDI sequence for the selection
     * @throws InvalidMidiDataException if MIDI data is invalid
     */
    public Sequence buildSelectionSequence(int lineIndex, int startNote, int endNote)
            throws InvalidMidiDataException {
        var startTempo = composition.getTempoAt(lineIndex, startNote);
        return buildSequence(lineIndex, startNote, lineIndex, endNote, startTempo);
    }

    /**
     * Builds a MIDI sequence for a range of notes.
     *
     * @param startLine The starting line index
     * @param startNote The starting note index within the start line
     * @param endLine The ending line index (-1 for all remaining lines)
     * @param endNote The ending note index within the end line (-1 for all notes)
     * @param initialTempo The tempo at the start of the sequence
     * @return The MIDI sequence
     * @throws InvalidMidiDataException if MIDI data is invalid
     */
    private Sequence buildSequence(
        int startLine,
        int startNote,
        int endLine,
        int endNote,
        Tempo initialTempo
    ) throws InvalidMidiDataException {
        var sequence = new Sequence(Sequence.PPQ, PPQ, 0);
        var track = sequence.createTrack();

        // Force GM melodic bank before program change to avoid sticky variation banks.
        addBankSelect(track, 0, 0, 0);
        addProgramChange(track, settings.instrument());

        // Add initial tempo
        addTempoChange(track, 0, initialTempo);

        var ticks = 0;
        var currentTempo = initialTempo;
        var lines = composition.getLines();

        // If repeats are disabled or this is a selection, use simple linear processing
        if (!settings.playWithRepeats() || (startNote > 0 || endNote >= 0)) {
            for (var i = startLine; i < lines.size(); i++) {
                var line = lines.get(i);

                // For single-line selections (endLine == startLine), use range-aware processing
                if (i == startLine && i == endLine && (startNote > 0 || endNote >= 0)) {
                    var result = line.addToTrack(track, i, ticks, currentTempo, settings, startNote, endNote);
                    ticks = result.getFirst();
                    currentTempo = result.getSecond();
                } else {
                    // Full line: delegate to Line to add its notes
                    var result = line.addToTrack(track, i, ticks, currentTempo, settings);
                    ticks = result.getFirst();
                    currentTempo = result.getSecond();
                }

                if (i == endLine) {
                    break;
                }
            }
        } else {
            // Handle repeats
            var result = buildSequenceWithRepeats(track, startLine, endLine, initialTempo);
            ticks = result.getFirst();
            currentTempo = result.getSecond();
        }

        return sequence;
    }

    /**
     * Builds a MIDI sequence with repeat handling.
     * This implements the same logic as the old Score.addLineToTrack method.
     *
     * @param track The MIDI track to add to
     * @param startLine The starting line index
     * @param endLine The ending line index (-1 for all remaining lines)
     * @param initialTempo The tempo at the start of the sequence
     * @return Pair of (ending tick position, ending tempo)
     * @throws InvalidMidiDataException if MIDI data is invalid
     */
    private kotlin.Pair<Integer, Tempo> buildSequenceWithRepeats(
        Track track,
        int startLine,
        int endLine,
        Tempo initialTempo
    ) throws InvalidMidiDataException {
        var ticks = 0;
        var currentTempo = initialTempo;
        var lines = composition.getLines();
        var repeating = false;

        var lineIndex = startLine;
        while (lineIndex < lines.size()) {
            var line = lines.get(lineIndex);
            var noteCount = line.noteCount();

            for (var noteIndex = 0; noteIndex < noteCount; noteIndex++) {
                var note = line.getNote(noteIndex);
                var noteType = note.getNoteType();

                // Handle repeat markers
                if (noteType == NoteType.REPEAT_RIGHT || noteType == NoteType.REPEAT_LEFT_RIGHT) {
                    if (repeating) {
                        // Second time through: exit the repeat
                        repeating = false;
                    } else {
                        // First time: jump back to find the repeat start
                        repeating = true;

                        // Search backward for the repeat start
                        var searchLineIndex = lineIndex;
                        var searchNoteIndex = noteIndex - 1;

                        repeatSearchLoop:
                        while (searchLineIndex >= 0) {
                            while (searchNoteIndex >= 0) {
                                var searchNote = lines.get(searchLineIndex).getNote(searchNoteIndex);
                                if (searchNote.getNoteType().isRepeat()) {
                                    // Found a repeat marker, continue from here
                                    lineIndex = searchLineIndex;
                                    noteIndex = searchNoteIndex;
                                    break repeatSearchLoop;
                                }
                                searchNoteIndex--;
                            }

                            searchLineIndex--;
                            if (searchLineIndex >= 0) {
                                searchNoteIndex = lines.get(searchLineIndex).noteCount() - 1;
                            }
                        }

                        // If we didn't find a repeat start, start from the beginning
                        if (searchLineIndex < 0) {
                            lineIndex = 0;
                            noteIndex = -1; // Will be incremented to 0
                        }

                        continue;
                    }
                }

                // Handle first-second endings
                var firstSecondInterval = line.getFirstSecondEndings().findInterval(noteIndex);

                if (repeating && firstSecondInterval != null) {
                    // During repeat: skip first ending, play only on second pass
                    var foundRepeatRight = false;
                    for (var i = noteIndex; i <= firstSecondInterval.getEnd() && i < noteCount; i++) {
                        if (line.getNote(i).getNoteType() == NoteType.REPEAT_RIGHT) {
                            foundRepeatRight = true;
                            noteIndex = i - 1; // Will be incremented in the loop
                            break;
                        }
                    }
                    if (foundRepeatRight) {
                        continue;
                    }
                }

                if (!repeating && firstSecondInterval != null && noteType == NoteType.REPEAT_RIGHT) {
                    // Not repeating and at end of first-second ending: skip to end
                    noteIndex = firstSecondInterval.getEnd();
                    continue;
                }

                // Add the note to the track (one note at a time)
                var result = line.addToTrack(track, lineIndex, ticks, currentTempo, settings, noteIndex, noteIndex);
                ticks = result.getFirst();
                currentTempo = result.getSecond();
            }

            if (lineIndex == endLine) {
                break;
            }

            lineIndex++;
        }

        return new kotlin.Pair<>(ticks, currentTempo);
    }

    /**
     * Adds a program change (instrument selection) to the track.
     *
     * @param track The MIDI track
     * @param instrument The MIDI instrument number (0-127)
     * @throws InvalidMidiDataException If MIDI data creation fails
     */
    private void addProgramChange(Track track, int instrument) throws InvalidMidiDataException {
        var programChange = new ShortMessage();
        programChange.setMessage(ShortMessage.PROGRAM_CHANGE, 0, instrument, 0);
        track.add(new MidiEvent(programChange, 0));
    }

    private void addBankSelect(Track track, int channel, int msb, int lsb)
            throws InvalidMidiDataException {
        var bankMsb = new ShortMessage();
        bankMsb.setMessage(ShortMessage.CONTROL_CHANGE, channel, 0, msb);
        track.add(new MidiEvent(bankMsb, 0));

        var bankLsb = new ShortMessage();
        bankLsb.setMessage(ShortMessage.CONTROL_CHANGE, channel, 32, lsb);
        track.add(new MidiEvent(bankLsb, 0));
    }

    /**
     * Adds a tempo change meta message to the track.
     *
     * @param track The MIDI track
     * @param ticks The tick position for the tempo change
     * @param tempo The tempo to set
     * @throws InvalidMidiDataException If MIDI data creation fails
     */
    private void addTempoChange(Track track, int ticks, Tempo tempo)
        throws InvalidMidiDataException {
        var realTempo = tempo.getRealTempo();
        var midiTempo = 60000000 / ((realTempo * settings.tempoChangePercent()) / 100);
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
}
