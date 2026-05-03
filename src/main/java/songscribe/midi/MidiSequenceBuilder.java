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

import module java.desktop;


import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Tempo;
import songscribe.ui.playback.MidiMetaMessageTypes;


/**
 * Coordinates MIDI sequence building for a song.
 * Delegates note-level MIDI generation to Line objects.
 * <p>
 * Phase 1 of the Score Cleanup refactoring (Step 5).
 */
public class MidiSequenceBuilder {

    // The number of pulses per quarter note (ticks per beat), used to calculate the duration of notes
    // when playing back the score or generating a MIDI file.
    public static final int PPQ = 96;

    /** MIDI CC number for Bank Select MSB. */
    private static final int BANK_SELECT_MSB_CC = 0;

    /** MIDI CC number for Bank Select LSB. */
    private static final int BANK_SELECT_LSB_CC = 32;

    private final Song song;
    private final PlaybackSettings settings;

    /**
     * Creates a new MIDI sequence builder.
     *
     * @param song The song to build a sequence from
     * @param settings Playback settings (instrument, tempo adjustment, note duration, colorize)
     */
    public MidiSequenceBuilder(Song song, PlaybackSettings settings) {
        this.song = song;
        this.settings = settings;
    }

    /**
     * Builds a MIDI sequence for the entire song.
     *
     * @return The complete MIDI sequence
     * @throws InvalidMidiDataException if MIDI data is invalid
     */
    public Sequence buildFullSequence() throws InvalidMidiDataException {
        return buildSequence(0, 0, -1, -1, song.getEffectiveTempo());
    }

    /**
     * Builds a MIDI sequence starting from a specific note and continuing to the end
     * of the song.
     *
     * @param lineIndex The line containing the starting note
     * @param startNote The index of the first note to include
     * @return The MIDI sequence from startNote to the end of the song
     * @throws InvalidMidiDataException if MIDI data is invalid
     */
    public Sequence buildFromNoteToEnd(int lineIndex, int startNote) throws InvalidMidiDataException {
        var startTempo = song.getTempoAt(lineIndex, startNote);
        return buildSequence(lineIndex, startNote, -1, -1, startTempo);
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
        MidiEventFactory.addTempoEvent(track, 0, initialTempo, settings.tempoChangePercent());

        // Pre-compute dynamic-aware velocities for all notes
        var velocityMap = VelocityMap.build(song, VelocityMap.MAX_VELOCITY);

        var ticks = 0;
        var currentTempo = initialTempo;
        var lines = song.getLines();

        // If repeats are disabled or we have a hard end boundary, use simple linear processing
        if (!settings.playWithRepeats() || endNote >= 0) {
            for (var i = startLine; i < lines.size(); i++) {
                var line = lines.get(i);

                var lineStart = (i == startLine && startNote > 0) ? startNote : 0;
                var lineEnd = (i == endLine && endNote >= 0) ? endNote : line.effectiveElementCount() - 1;

                var builder = new LineTrackBuilder(line);
                TrackPosition result;

                if (lineStart > 0 || lineEnd < line.effectiveElementCount() - 1) {
                    result = builder.addToTrack(track, i, ticks, currentTempo, settings,
                        lineStart, lineEnd, velocityMap);
                } else {
                    result = builder.addToTrack(track, i, ticks, currentTempo, settings,
                        velocityMap);
                }

                ticks = result.ticks();
                currentTempo = result.tempo();

                if (i == endLine) {
                    break;
                }
            }
        } else {
            // Handle repeats
            var result = buildSequenceWithRepeats(
                track, startLine, startNote, endLine, initialTempo, velocityMap);
            ticks = result.ticks();
            currentTempo = result.tempo();
        }

        // Place END_OF_TRACK at the end of the last note's full written duration.
        // Without this, Java's Track auto-places it at the last event (the note-off),
        // which is earlier than the duration end due to staccato/articulation.
        var endOfTrack = new MetaMessage(MidiMetaMessageTypes.END_OF_TRACK, new byte[0], 0);
        track.add(new MidiEvent(endOfTrack, ticks));

        return sequence;
    }

    /**
     * Builds a MIDI sequence with repeat handling.
     * This implements the same logic as the old Score.addLineToTrack method.
     *
     * @param track The MIDI track to add to
     * @param startLine The starting line index
     * @param startNote The index of the first note to include on the start line
     * @param endLine The ending line index (-1 for all remaining lines)
     * @param initialTempo The tempo at the start of the sequence
     * @return Pair of (ending tick position, ending tempo)
     * @throws InvalidMidiDataException if MIDI data is invalid
     */
    private TrackPosition buildSequenceWithRepeats(
        Track track,
        int startLine,
        int startNote,
        int endLine,
        Tempo initialTempo,
        VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var ticks = 0;
        var currentTempo = initialTempo;
        var lines = song.getLines();
        var repeating = false;
        var glissandoHelper = new GlissandoMidiHelper();

        var lineIndex = startLine;
        while (lineIndex < lines.size()) {
            var line = lines.get(lineIndex);
            var noteCount = line.elementCount();
            var builder = new LineTrackBuilder(line);

            for (var noteIndex = (lineIndex == startLine ? startNote : 0); noteIndex < noteCount; noteIndex++) {
                var note = line.getElement(noteIndex);
                var noteType = note.getType();

                // Handle repeat markers
                if (noteType == ElementType.REPEAT_RIGHT || noteType == ElementType.REPEAT_LEFT_RIGHT) {
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
                                var searchNote = lines.get(searchLineIndex).getElement(searchNoteIndex);
                                if (searchNote.getType().isRepeat()) {
                                    // Found a repeat marker, continue from here
                                    lineIndex = searchLineIndex;
                                    noteIndex = searchNoteIndex;
                                    break repeatSearchLoop;
                                }
                                searchNoteIndex--;
                            }

                            searchLineIndex--;
                            if (searchLineIndex >= 0) {
                                searchNoteIndex = lines.get(searchLineIndex).elementCount() - 1;
                            }
                        }

                        // If we didn't find a repeat start, begin from the start of the song
                        if (searchLineIndex < 0) {
                            lineIndex = 0;
                            noteIndex = -1; // Will be incremented to 0
                        }

                        continue;
                    }
                }

                // Handle first-second endings
                var ending = line.findEndingAt(noteIndex);

                if (repeating && ending != null) {
                    // During repeat: skip first ending, play only on second pass
                    var foundRepeatRight = false;
                    for (var i = noteIndex; i <= ending.getEndElementIndex() && i < noteCount; i++) {
                        if (line.getElement(i).getType() == ElementType.REPEAT_RIGHT) {
                            foundRepeatRight = true;
                            noteIndex = i - 1; // Will be incremented in the loop
                            break;
                        }
                    }
                    if (foundRepeatRight) {
                        continue;
                    }
                }

                if (!repeating && ending != null && noteType == ElementType.REPEAT_RIGHT) {
                    // Not repeating and at end of first-second ending: skip to end
                    noteIndex = ending.getEndElementIndex();
                    continue;
                }

                // Add the note to the track (one note at a time), sharing the
                // glissando helper so grace note state survives across calls.
                var result = builder.addToTrack(
                    track, lineIndex, ticks, currentTempo, settings,
                    noteIndex, noteIndex, glissandoHelper, velocityMap
                );
                ticks = result.ticks();
                currentTempo = result.tempo();
            }

            if (lineIndex == endLine) {
                break;
            }

            lineIndex++;
        }

        // Flush any pending pitch bend/expression resets at the end
        glissandoHelper.createPendingResets(track, ticks, 0);

        return new TrackPosition(ticks, currentTempo);
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
        bankMsb.setMessage(ShortMessage.CONTROL_CHANGE, channel, BANK_SELECT_MSB_CC, msb);
        track.add(new MidiEvent(bankMsb, 0));

        var bankLsb = new ShortMessage();
        bankLsb.setMessage(ShortMessage.CONTROL_CHANGE, channel, BANK_SELECT_LSB_CC, lsb);
        track.add(new MidiEvent(bankLsb, 0));
    }

}
