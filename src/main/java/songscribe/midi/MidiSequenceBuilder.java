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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.jspecify.annotations.Nullable;

import songscribe.dom.ElementLocation;
import songscribe.dom.Ending;
import songscribe.dom.Line;
import songscribe.dom.LineSpans;
import songscribe.dom.RepeatSections;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.ui.playback.MidiMetaMessageTypes;

/**
 * Builds the MIDI sequence that plays a song, whole or from a given note to its end. The
 * sequence holds one track at {@value #PPQ} ticks per quarter note.
 *
 * <p>A tie chain sounds as one note. Its first note strikes, the notes tied into it strike
 * nothing, and the chain releases once, where its last note releases: at the end of that note's
 * sounding duration, after its articulation and {@link PlaybackSettings#noteDurationPercent()}
 * shorten it. Every note of the chain still advances time by its own written duration.
 */
public class MidiSequenceBuilder {

    public static final int PPQ = 96;

    private static final int BANK_SELECT_MSB_CC = 0;

    private static final int BANK_SELECT_LSB_CC = 32;

    private final Song song;
    private final PlaybackSettings settings;

    public MidiSequenceBuilder(Song song, PlaybackSettings settings) {
        this.song = song;
        this.settings = settings;
    }

    /**
     * Starts at the song's own tempo scaled by {@link PlaybackSettings#tempoChangePercent()} and
     * ends at the end of the last played element's written duration.
     */
    public Sequence buildFullSequence() throws InvalidMidiDataException {
        return buildSequence(0, 0, song.getTempo());
    }

    public Sequence buildFromNoteToEnd(int lineIndex, int startNote) throws InvalidMidiDataException {
        var startTempo = song.getTempoAt(lineIndex, startNote);
        return buildSequence(lineIndex, startNote, startTempo);
    }

    private Sequence buildSequence(
        int startLine,
        int startNote,
        Tempo initialTempo
    ) throws InvalidMidiDataException {
        var sequence = new Sequence(Sequence.PPQ, PPQ, 0);
        var track = sequence.createTrack();

        // Force GM melodic bank before program change to avoid sticky variation banks.
        addBankSelect(track, 0, 0, 0);
        addProgramChange(track, settings.instrument());

        MidiEventFactory.addTempoEvent(track, 0, initialTempo, settings.tempoChangePercent());

        var velocityMap = VelocityMap.build(song, VelocityMap.MAX_VELOCITY);

        var ticks = 0;
        var currentTempo = initialTempo;
        var lines = song.getLines();

        if (!settings.playWithRepeats()) {
            for (var i = startLine; i < lines.size(); i++) {
                var line = lines.get(i);

                var lineStart = (i == startLine && startNote > 0) ? startNote : 0;
                var lineEnd = line.effectiveElementCount() - 1;

                var builder = new LineTrackBuilder(line);
                var endings = line.spanRegistry().findEndings();
                TrackPosition result;

                if (endings.isEmpty()) {
                    result = (lineStart > 0)
                        ? builder.addToTrack(track, i, ticks, currentTempo, settings,
                            lineStart, lineEnd, velocityMap)
                        : builder.addToTrack(track, i, ticks, currentTempo, settings,
                            velocityMap);
                } else {
                    result = addLineSkippingFirstEndings(
                        track, builder, i, lineStart, lineEnd,
                        new TrackPosition(ticks, currentTempo), endings, velocityMap);
                }

                ticks = result.ticks();
                currentTempo = result.tempo();
            }
        } else {
            var result = buildSequenceWithRepeats(
                track, startLine, startNote, initialTempo, velocityMap);
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
     * Returns null when none covers {@code elementIndex}. {@code endings} is already narrowed to
     * one line's endings.
     */
    private static @Nullable Ending endingCovering(List<Ending> endings, int elementIndex) {
        for (var ending : endings) {
            if (ending.range().contains(elementIndex)) {
                return ending;
            }
        }

        return null;
    }

    /**
     * A {@code :|} or {@code :|:} plays the section it closes twice, jumping back to
     * {@link RepeatSections#sectionStart}, which may lie on an earlier line. A {@code :|} with
     * no open section plays as an ordinary bar. On the replay pass a first ending is skipped.
     */
    private TrackPosition buildSequenceWithRepeats(
        Track track,
        int startLine,
        int startNote,
        Tempo initialTempo,
        VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var ticks = 0;
        var currentTempo = initialTempo;
        var lines = song.getLines();
        var repeatSections = RepeatSections.of(song);
        var repeating = false;
        var slideHelper = new SlideMidiHelper();

        var lineIndex = startLine;
        var noteIndex = startNote;

        // Every jump back to a section start re-enters this loop, so the line, its builder
        // and its endings are always those of the line the jump lands on.
        lineLoop:
        while (lineIndex < lines.size()) {
            var line = lines.get(lineIndex);
            var noteCount = line.elementCount();
            var builder = new LineTrackBuilder(line);

            // Hoisted out of the note loop: asking the line per note would re-filter its
            // whole span list — every beam, tie, tuplet, trill and hairpin included — once
            // for every note in the song.
            var endings = line.spanRegistry().findEndings();

            for (; noteIndex < noteCount; noteIndex++) {
                var closesRepeat = line.getElement(noteIndex).getType().closesRepeat();

                var ending = endingCovering(endings, noteIndex);

                // The closing REPEAT_RIGHT of a secondary (REPEAT_LEFT_RIGHT-split) ending
                // must not start a spurious third pass, so exclude it from the repeat-marker
                // handler below.
                var isEndingClosingMarker = ending != null
                    && noteIndex == ending.requireEndElementIndex()
                    && closesRepeat;

                if (!isEndingClosingMarker && closesRepeat) {
                    if (repeating) {
                        repeating = false;
                    } else {
                        var sectionStart = repeatSections.sectionStart(
                            new ElementLocation(lineIndex, noteIndex));

                        // An unbalanced marker, with no open section, plays as an ordinary bar.
                        if (sectionStart != null) {
                            repeating = true;
                            lineIndex = sectionStart.lineIndex();
                            noteIndex = sectionStart.elementIndex();
                            continue lineLoop;
                        }
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
                    noteIndex = ending.getSplitIndex();
                    repeating = false;
                    continue;
                }

                var result = builder.addToTrack(
                    track, lineIndex, ticks, currentTempo, settings,
                    noteIndex, noteIndex, slideHelper, velocityMap
                );
                ticks = result.ticks();
                currentTempo = result.tempo();
            }

            lineIndex++;
            noteIndex = 0;
        }

        slideHelper.createPendingResets(track, ticks, 0);

        return new TrackPosition(ticks, currentTempo);
    }

    /**
     * With repeats off, each first-ending span is skipped so only the common section and the
     * second ending are played. One {@link SlideMidiHelper} is shared across the segments so
     * grace-note/slide state survives the skipped gaps.
     */
    private TrackPosition addLineSkippingFirstEndings(
        Track track,
        LineTrackBuilder builder,
        int lineIndex,
        int lineStart,
        int lineEnd,
        TrackPosition startPosition,
        List<? extends Ending> endings,
        VelocityMap velocityMap
    ) throws InvalidMidiDataException {
        var skipSpans = new ArrayList<SkipSpan>();

        for (var ending : endings) {
            var splitIndex = ending.getSplitIndex();
            var spanStart = Math.max(ending.firstBracket().begin(), lineStart);
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
     * Inclusive of both endpoints.
     */
    private record SkipSpan(int start, int end) {}

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
