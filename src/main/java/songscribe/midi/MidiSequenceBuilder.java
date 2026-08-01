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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Tempo;
import songscribe.dom.Ending;
import songscribe.layout.LineEndingSupport;
import songscribe.ui.playback.MidiMetaMessageTypes;


/**
 * Coordinates MIDI sequence building for a song.
 * Delegates note-level MIDI generation to Line objects.
 * <p>
 * Phase 1 of the ScoreView Cleanup refactoring (Step 5).
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
                var endings = LineEndingSupport.findEndings(line);
                TrackPosition result;

                if (endings.isEmpty()) {
                    result = (lineStart > 0 || lineEnd < line.effectiveElementCount() - 1)
                        ? builder.addToTrack(track, i, ticks, currentTempo, settings,
                            lineStart, lineEnd, velocityMap)
                        : builder.addToTrack(track, i, ticks, currentTempo, settings,
                            velocityMap);
                } else {
                    // With repeats off, skip each first-ending span and play only the
                    // common section plus the second ending.
                    result = addLineSkippingFirstEndings(
                        track, builder, line, i, lineStart, lineEnd,
                        new TrackPosition(ticks, currentTempo), endings, velocityMap);
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
     * This implements the same logic as the old ScoreView.addLineToTrack method.
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
        var slideHelper = new SlideMidiHelper();

        var lineIndex = startLine;
        while (lineIndex < lines.size()) {
            var line = lines.get(lineIndex);
            var noteCount = line.elementCount();
            var builder = new LineTrackBuilder(line);
            var endings = LineEndingSupport.findEndings(line);

            for (var noteIndex = (lineIndex == startLine ? startNote : 0); noteIndex < noteCount; noteIndex++) {
                var note = line.getElement(noteIndex);
                var noteType = note.getType();

                // Resolve the ending covering this element once; reused below.
                var ending = LineEndingSupport.findEndingAt(endings, noteIndex);

                // The closing REPEAT_RIGHT of a secondary (REPEAT_LEFT_RIGHT-split) ending
                // must not start a spurious third pass, so exclude it from the repeat-marker
                // handler below.
                var isEndingClosingMarker = ending != null
                    && noteIndex == ending.getEndElementIndex()
                    && (noteType == ElementType.REPEAT_RIGHT || noteType == ElementType.REPEAT_LEFT_RIGHT);

                // Handle repeat markers
                if (!isEndingClosingMarker
                    && (noteType == ElementType.REPEAT_RIGHT || noteType == ElementType.REPEAT_LEFT_RIGHT)) {
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

                // Handle first-second endings. On the replay pass, skip the entire first
                // ending and resume at the second ending without repeating again. On the
                // first pass the split marker triggers the backward jump above before the
                // second ending is reached, so no early-skip handling is needed there.
                if (repeating && ending != null) {
                    // splitIndex names the split marker itself, already handled above as the
                    // repeat trigger. Jump there so the loop's noteIndex++ lands on the first
                    // element of the second ending. Clear repeating so we don't jump back again.
                    noteIndex = ending.getSplitIndex(line);
                    repeating = false;
                    continue;
                }

                // Add the note to the track (one note at a time), sharing the
                // slide helper so grace note state survives across calls.
                var result = builder.addToTrack(
                    track, lineIndex, ticks, currentTempo, settings,
                    noteIndex, noteIndex, slideHelper, velocityMap
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
        slideHelper.createPendingResets(track, ticks, 0);

        return new TrackPosition(ticks, currentTempo);
    }

    /**
     * Adds a line to the track with repeats off, skipping each first-ending span so only
     * the common section and the second ending are played.
     *
     * <pre>
     *   line:     common | [anchor .. split] (1st ending) | 2nd ending
     *   emitted:  common |          (skipped)              | 2nd ending
     * </pre>
     *
     * A single {@link SlideMidiHelper} is shared across all emitted segments so
     * grace-note/slide state survives the skipped gaps; pending resets are flushed
     * once at the end. Every ending has a split, so each contributes exactly one
     * first-ending skip span.
     *
     * @param track The MIDI track to add to
     * @param builder The track builder for this line
     * @param line The line being emitted
     * @param lineIndex This line's index in the song
     * @param lineStart Index of the first element to consider
     * @param lineEnd Index of the last element to consider
     * @param startPosition Tick position and tempo at the start of this line
     * @param endings The endings on this line
     * @param velocityMap Dynamic-aware velocities
     * @return Pair of (ending tick position, ending tempo)
     * @throws InvalidMidiDataException if MIDI data is invalid
     */
    private TrackPosition addLineSkippingFirstEndings(
        Track track,
        LineTrackBuilder builder,
        Line line,
        int lineIndex,
        int lineStart,
        int lineEnd,
        TrackPosition startPosition,
        List<? extends Ending> endings,
        VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var skipSpans = new ArrayList<SkipSpan>();

        for (var ending : endings) {
            var splitIndex = ending.getSplitIndex(line);
            var spanStart = Math.max(ending.getAnchorElementIndex(), lineStart);
            var spanEnd = Math.min(splitIndex, lineEnd);

            if (spanStart <= spanEnd) {
                skipSpans.add(new SkipSpan(spanStart, spanEnd));
            }
        }

        skipSpans.sort(Comparator.comparingInt(SkipSpan::start));

        var ticks = startPosition.ticks();
        var currentTempo = startPosition.tempo();
        var slideHelper = new SlideMidiHelper();
        var segmentStart = lineStart;

        for (var span : skipSpans) {
            if (span.start() > segmentStart) {
                var result = builder.addToTrack(
                    track, lineIndex, ticks, currentTempo, settings,
                    segmentStart, span.start() - 1, slideHelper, velocityMap
                );
                ticks = result.ticks();
                currentTempo = result.tempo();
            }

            segmentStart = Math.max(segmentStart, span.end() + 1);
        }

        if (segmentStart <= lineEnd) {
            var result = builder.addToTrack(
                track, lineIndex, ticks, currentTempo, settings,
                segmentStart, lineEnd, slideHelper, velocityMap
            );
            ticks = result.ticks();
            currentTempo = result.tempo();
        }

        slideHelper.createPendingResets(track, ticks, 0);

        return new TrackPosition(ticks, currentTempo);
    }

    /**
     * A contiguous element-index span of a first ending to skip during repeats-off playback,
     * inclusive of both endpoints.
     */
    private record SkipSpan(int start, int end) {}

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
