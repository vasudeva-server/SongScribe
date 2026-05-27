/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.midi;

import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.layout.Ending;
import songscribe.ui.playback.MidiMetaMessageTypes;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.midi.MidiSequenceBuilder.PPQ;

@SuppressWarnings("OverlyBroadThrowsClause")
class MidiSequenceBuilderTest extends UnitTest {

    private static final int INSTRUMENT_PIANO = 0;
    private static final int MIDI_INSTRUMENT_VIOLIN = 40;
    private static final int TEMPO_PERCENT_NORMAL = 100;
    private static final int DURATION_PERCENT_NORMAL = 100;
    private static final int BPM_FAST = 180;
    private static final int MICROSECONDS_PER_MINUTE = 60_000_000;
    private static final int MIDI_CC_BANK_SELECT_MSB = 0;
    private static final int MIDI_CC_BANK_SELECT_LSB = 32;

    // Staff positions for distinct pitched notes in repeat tests
    private static final int STAFF_POS_A = -2;
    private static final int STAFF_POS_B = -4;
    private static final int STAFF_POS_C = -6;

    // A note inside a simple repeat plays once on first pass and once on second pass.
    private static final int REPEAT_PLAY_COUNT = 2;

    // first/second ending: noteCommon × REPEAT_PLAY_COUNT + note1st × 1 + note2nd × 1
    private static final int FIRST_SECOND_ENDING_TOTAL_NOTE_ONS = REPEAT_PLAY_COUNT + 1 + 1;

    private static final PlaybackSettings SETTINGS_NO_REPEATS = new PlaybackSettings(
        INSTRUMENT_PIANO, TEMPO_PERCENT_NORMAL, DURATION_PERCENT_NORMAL, false
    );

    private static final PlaybackSettings SETTINGS_WITH_REPEATS = new PlaybackSettings(
        INSTRUMENT_PIANO, TEMPO_PERCENT_NORMAL, DURATION_PERCENT_NORMAL, true
    );

    /** Creates a Song whose getLines() returns the given lines in order. */
    private static Song songWith(Line... lines) {
        var song = new Song();
        song.getLines().clear();
        for (var line : lines) {
            song.getLines().add(line);
        }
        return song;
    }

    /** Builds a full sequence from the given song and returns the first (and only) track. */
    private static Track buildFirstTrack(Song song, PlaybackSettings settings)
        throws InvalidMidiDataException {
        return new MidiSequenceBuilder(song, settings).buildFullSequence().getTracks()[0];
    }

    /** One-crotchet, one-line song at default tempo. */
    private static Song singleNoteSong() {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(STAFF_POS_A);
        var line = detachedLine();
        line.addElement(note);
        return songWith(line);
    }

    private static List<MidiEvent> programChangeEvents(Track track) {
        var list = new ArrayList<MidiEvent>();
        for (var i = 0; i < track.size(); i++) {
            var ev = track.get(i);
            if (ev.getMessage() instanceof ShortMessage sm
                && sm.getCommand() == ShortMessage.PROGRAM_CHANGE) {
                list.add(ev);
            }
        }
        return list;
    }

    private static List<MidiEvent> controlChangeEvents(Track track, int cc) {
        var list = new ArrayList<MidiEvent>();
        for (var i = 0; i < track.size(); i++) {
            var ev = track.get(i);
            if (ev.getMessage() instanceof ShortMessage sm
                && sm.getCommand() == ShortMessage.CONTROL_CHANGE
                && sm.getData1() == cc) {
                list.add(ev);
            }
        }
        return list;
    }

    private static List<MidiEvent> setTempoEvents(Track track) {
        var list = new ArrayList<MidiEvent>();
        for (var i = 0; i < track.size(); i++) {
            var ev = track.get(i);
            if (ev.getMessage() instanceof MetaMessage mm
                && mm.getType() == MidiMetaMessageTypes.SET_TEMPO) {
                list.add(ev);
            }
        }
        return list;
    }

    private static List<MidiEvent> endOfTrackEvents(Track track) {
        var list = new ArrayList<MidiEvent>();
        for (var i = 0; i < track.size(); i++) {
            var ev = track.get(i);
            if (ev.getMessage() instanceof MetaMessage mm
                && mm.getType() == MidiMetaMessageTypes.END_OF_TRACK) {
                list.add(ev);
            }
        }
        return list;
    }

    /** NOTE_ON events with non-zero velocity (zero-velocity NOTE_ON is NOTE_OFF in MIDI). */
    private static List<MidiEvent> noteOnEvents(Track track) {
        var list = new ArrayList<MidiEvent>();
        for (var i = 0; i < track.size(); i++) {
            var ev = track.get(i);
            if (ev.getMessage() instanceof ShortMessage sm
                && sm.getCommand() == ShortMessage.NOTE_ON
                && sm.getData2() > 0) {
                list.add(ev);
            }
        }
        return list;
    }

    /** Decodes the microseconds-per-beat from a SET_TEMPO meta-message (3-byte big-endian). */
    private static int decodeMicroseconds(MetaMessage message) {
        var data = message.getData();
        return ((data[0] & 0xFF) << 16)
             | ((data[1] & 0xFF) << 8)
             | (data[2] & 0xFF);
    }

    // -------------------------------------------------------------------------
    // Row 33: buildFullSequence
    // -------------------------------------------------------------------------

    @Nested
    class BuildFullSequence {

        @Test
        void testReturnsNonNullSequence() throws InvalidMidiDataException {
            var sequence = new MidiSequenceBuilder(singleNoteSong(), SETTINGS_NO_REPEATS)
                .buildFullSequence();
            assertThat(sequence).isNotNull();
        }

        @Test
        void testResolutionEqualsPpq() throws InvalidMidiDataException {
            var sequence = new MidiSequenceBuilder(singleNoteSong(), SETTINGS_NO_REPEATS)
                .buildFullSequence();
            assertThat(sequence.getResolution()).as("sequence PPQ resolution").isEqualTo(PPQ);
        }

        @Test
        void testHasAtLeastOneMidiTrack() throws InvalidMidiDataException {
            var sequence = new MidiSequenceBuilder(singleNoteSong(), SETTINGS_NO_REPEATS)
                .buildFullSequence();
            assertThat(sequence.getTracks().length)
                .as("at least one MIDI track")
                .isGreaterThanOrEqualTo(1);
        }

        @Test
        void testProgramChangeEventPresentAtTickZero() throws InvalidMidiDataException {
            var track = buildFirstTrack(singleNoteSong(), SETTINGS_NO_REPEATS);
            var programChanges = programChangeEvents(track);
            assertThat(programChanges).as("program-change event present").isNotEmpty();
            assertThat(programChanges.getFirst().getTick())
                .as("program-change at tick 0")
                .isEqualTo(0);
        }
    }

    // -------------------------------------------------------------------------
    // Row 34: buildFromNoteToEnd
    // -------------------------------------------------------------------------

    @Nested
    class BuildFromNoteToEnd {

        @Test
        void testInitialTempoMatchesMidLineTempoAtStartNote() throws InvalidMidiDataException {
            // Song default: 120 BPM. A TempoChangeAttachment on note 1 carries 180 BPM.
            // buildFromNoteToEnd(0, 1) must use the 180 BPM tempo for the initial SET_TEMPO
            // event, not the song's 120 BPM default.
            var fastTempo = new Tempo(BPM_FAST, Duration.CROTCHET, "", false);
            var noteAtStart = ElementType.CROTCHET.newInstance();
            noteAtStart.setStaffPosition(STAFF_POS_A);
            var noteWithTempo = ElementType.CROTCHET.newInstance();
            noteWithTempo.setStaffPosition(STAFF_POS_B);
            noteWithTempo.addAttachment(new TempoChangeAttachment(fastTempo));
            var line = detachedLine();
            line.addElement(noteAtStart);
            line.addElement(noteWithTempo);
            var song = songWith(line);

            var builder = new MidiSequenceBuilder(song, SETTINGS_NO_REPEATS);
            var track = builder.buildFromNoteToEnd(0, 1).getTracks()[0];

            var tempoEvents = setTempoEvents(track);
            assertThat(tempoEvents).as("initial SET_TEMPO event present").isNotEmpty();
            var encodedMicroseconds = decodeMicroseconds(
                (MetaMessage) tempoEvents.getFirst().getMessage()
            );
            assertThat(encodedMicroseconds)
                .as("initial SET_TEMPO encodes the mid-line tempo, not the song default")
                .isEqualTo(MICROSECONDS_PER_MINUTE / BPM_FAST);
        }
    }

    // -------------------------------------------------------------------------
    // Rows 35, 37, 38: buildSequence — linear path, bank select, program change,
    //                  initial tempo, END_OF_TRACK
    // -------------------------------------------------------------------------

    @Nested
    class BuildSequenceLinear {

        @Test
        void testBankSelectMsbEmittedAtTickZero() throws InvalidMidiDataException {
            var track = buildFirstTrack(singleNoteSong(), SETTINGS_NO_REPEATS);
            var events = controlChangeEvents(track, MIDI_CC_BANK_SELECT_MSB);
            assertThat(events).as("bank select MSB (CC 0) emitted once").hasSize(1);
            assertThat(events.getFirst().getTick())
                .as("bank select MSB at tick 0")
                .isEqualTo(0);
        }

        @Test
        void testBankSelectLsbEmittedAtTickZero() throws InvalidMidiDataException {
            var track = buildFirstTrack(singleNoteSong(), SETTINGS_NO_REPEATS);
            var events = controlChangeEvents(track, MIDI_CC_BANK_SELECT_LSB);
            assertThat(events).as("bank select LSB (CC 32) emitted once").hasSize(1);
            assertThat(events.getFirst().getTick())
                .as("bank select LSB at tick 0")
                .isEqualTo(0);
        }

        @Test
        void testProgramChangeOnChannelZeroWithCorrectInstrumentAtTickZero()
            throws InvalidMidiDataException {
            // Use a non-piano instrument so the assertion catches a wrong instrument number.
            var settings = new PlaybackSettings(
                MIDI_INSTRUMENT_VIOLIN, TEMPO_PERCENT_NORMAL, DURATION_PERCENT_NORMAL, false
            );
            var track = buildFirstTrack(singleNoteSong(), settings);
            var events = programChangeEvents(track);
            assertThat(events).as("program-change event present").hasSize(1);
            var pc = (ShortMessage) events.getFirst().getMessage();
            assertThat(pc.getChannel()).as("program-change on channel 0").isEqualTo(0);
            assertThat(pc.getData1())
                .as("program-change instrument number")
                .isEqualTo(MIDI_INSTRUMENT_VIOLIN);
            assertThat(events.getFirst().getTick()).as("program-change at tick 0").isEqualTo(0);
        }

        @Test
        void testInitialSetTempoEmittedAtTickZero() throws InvalidMidiDataException {
            var track = buildFirstTrack(singleNoteSong(), SETTINGS_NO_REPEATS);
            var events = setTempoEvents(track);
            assertThat(events).as("initial SET_TEMPO event emitted").isNotEmpty();
            assertThat(events.getFirst().getTick())
                .as("initial SET_TEMPO at tick 0")
                .isEqualTo(0);
        }

        @Test
        void testEndOfTrackPlacedAtEndOfFinalNoteDuration() throws InvalidMidiDataException {
            // One crotchet at default tempo → written duration is PPQ ticks.
            // buildSequence explicitly places END_OF_TRACK at that tick so that the
            // MIDI file preserves the trailing silence from articulation/staccato shortening.
            var track = buildFirstTrack(singleNoteSong(), SETTINGS_NO_REPEATS);
            var endEvents = endOfTrackEvents(track);
            assertThat(endEvents).as("END_OF_TRACK event present").isNotEmpty();
            var latestEndTick = endEvents.stream().mapToLong(MidiEvent::getTick).max().orElseThrow();
            assertThat(latestEndTick)
                .as("END_OF_TRACK placed at end of one-crotchet duration (PPQ ticks)")
                .isEqualTo(PPQ);
        }
    }

    // -------------------------------------------------------------------------
    // Row 36: buildSequenceWithRepeats
    // -------------------------------------------------------------------------

    @Nested
    class BuildSequenceWithRepeats {

        @Test
        void testSimpleRepeatPlaysNotesTwice() throws InvalidMidiDataException {
            // Line: [note], [REPEAT_RIGHT]
            // No explicit REPEAT_LEFT → backward search exhausts → jumps to start of song.
            // Expected: the single pitched note produces REPEAT_PLAY_COUNT NOTE_ON events.
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(STAFF_POS_A);
            var line = detachedLine();
            line.addElement(note);
            line.addElement(ElementType.REPEAT_RIGHT.newInstance());
            var song = songWith(line);

            var track = buildFirstTrack(song, SETTINGS_WITH_REPEATS);
            assertThat(noteOnEvents(track))
                .as("simple repeat: note plays " + REPEAT_PLAY_COUNT + " times")
                .hasSize(REPEAT_PLAY_COUNT);
        }

        @Test
        void testFirstSecondEndingsSkipsFirstEndingOnSecondPass() throws InvalidMidiDataException {
            // Line: [noteCommon], [note1st], [REPEAT_RIGHT], [note2nd]
            // First ending bracket covers [note1st, REPEAT_RIGHT].
            //
            // First pass:  noteCommon + note1st → REPEAT_RIGHT fires repeat
            // Second pass: noteCommon → 1st-ending found → skip to REPEAT_RIGHT →
            //              REPEAT_RIGHT exits repeat → note2nd plays
            //
            // Expected: noteCommon × REPEAT_PLAY_COUNT + note1st × 1 + note2nd × 1
            //         = FIRST_SECOND_ENDING_TOTAL_NOTE_ONS
            var noteCommon = ElementType.CROTCHET.newInstance();
            noteCommon.setStaffPosition(STAFF_POS_A);
            var note1st = ElementType.CROTCHET.newInstance();
            note1st.setStaffPosition(STAFF_POS_B);
            var repeatRight = ElementType.REPEAT_RIGHT.newInstance();
            var note2nd = ElementType.CROTCHET.newInstance();
            note2nd.setStaffPosition(STAFF_POS_C);

            var line = detachedLine();
            line.addElement(noteCommon);  // index 0
            line.addElement(note1st);     // index 1
            line.addElement(repeatRight); // index 2
            line.addElement(note2nd);     // index 3

            // First ending spans note1st (index 1) through REPEAT_RIGHT (index 2).
            line.addRangeElement(new Ending(note1st, repeatRight));

            var track = buildFirstTrack(songWith(line), SETTINGS_WITH_REPEATS);
            assertThat(noteOnEvents(track))
                .as("first/second endings: " + FIRST_SECOND_ENDING_TOTAL_NOTE_ONS + " NOTE_ONs "
                    + "(common×" + REPEAT_PLAY_COUNT + " + 1st-ending×1 + 2nd-ending×1)")
                .hasSize(FIRST_SECOND_ENDING_TOTAL_NOTE_ONS);
        }
    }
}
