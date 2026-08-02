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
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.dom.Ending;
import songscribe.ui.playback.MidiMetaMessageTypes;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.midi.MidiSequenceBuilder.PPQ;

class MidiSequenceBuilderTest extends UnitTest {

    private static final int INSTRUMENT_PIANO = 0;
    private static final int MIDI_INSTRUMENT_VIOLIN = 40;
    private static final int TEMPO_PERCENT_NORMAL = 100;
    private static final int DURATION_PERCENT_NORMAL = 100;
    private static final int BPM_FAST = 180;
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

    // repeats ON, REPEAT_LEFT_RIGHT split (no common): note1st + note2nd
    private static final int REPEATS_ON_LEFT_RIGHT_NOTE_ONS = 1 + 1;

    // repeats OFF, primary shape: common + note2nd (first ending skipped)
    private static final int REPEATS_OFF_PRIMARY_NOTE_ONS = 1 + 1;

    // repeats OFF, REPEAT_LEFT_RIGHT split (no common): note2nd only
    private static final int REPEATS_OFF_LEFT_RIGHT_NOTE_ONS = 1;

    // Multi-note endings: two notes in the first ending, three in the second. The counts
    // are deliberately unequal so the repeats-OFF test fails if production skips the wrong
    // ending — skipping the second instead of the first would emit common + 2 notes, not
    // common + 3.
    private static final int MULTI_FIRST_ENDING_NOTES = 1 + 1;
    private static final int MULTI_SECOND_ENDING_NOTES = 1 + 1 + 1;

    // repeats ON: common × REPEAT_PLAY_COUNT + every first- and second-ending note once
    private static final int MULTI_NOTE_REPEATS_ON_NOTE_ONS =
        REPEAT_PLAY_COUNT + MULTI_FIRST_ENDING_NOTES + MULTI_SECOND_ENDING_NOTES;

    // repeats OFF: common once + second-ending notes (first ending skipped)
    private static final int MULTI_NOTE_REPEATS_OFF_NOTE_ONS = 1 + MULTI_SECOND_ENDING_NOTES;

    // In the repeats-OFF primary line the common crotchet (PPQ ticks) precedes the second
    // ending, so the second-ending note-on lands exactly one crotchet in.
    private static final int SECOND_ENDING_NOTE_ON_TICK = PPQ;

    // Partial build (buildFromNoteToEnd) starting inside the first ending of the multi-note
    // line: the common section is before the start and is excluded, the partial first ending
    // is skipped, leaving only the second-ending notes.
    private static final int MULTI_NOTE_PARTIAL_START_NOTE = 3;
    private static final int MULTI_NOTE_PARTIAL_NOTE_ONS = MULTI_SECOND_ENDING_NOTES;

    // Two endings on one line, repeats OFF: common + each second ending (both first endings
    // skipped). One common note precedes the first ending and one separates the two.
    private static final int TWO_ENDINGS_COMMON_NOTES = 1 + 1;
    private static final int TWO_ENDINGS_REPEATS_OFF_NOTE_ONS =
        TWO_ENDINGS_COMMON_NOTES + 1 + 1;

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

    /**
     * Builds a partial sequence starting from the given note to the end of the song and
     * returns the first (and only) track.
     */
    private static Track buildFirstTrackFromNote(
        Song song, PlaybackSettings settings, int lineIndex, int startNote
    ) throws InvalidMidiDataException {
        return new MidiSequenceBuilder(song, settings)
            .buildFromNoteToEnd(lineIndex, startNote).getTracks()[0];
    }

    /** Creates a crotchet at the given staff position. */
    private static StaffElement crotchetAt(int staffPosition) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPosition);
        return note;
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
                .isEqualTo(MidiEventFactory.MICROSECONDS_PER_MINUTE / BPM_FAST);
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

        @Test
        void testFirstSecondEndingsRepeatsOffSkipsFirstEnding() throws InvalidMidiDataException {
            // Repeats OFF, primary shape: the first ending [anchor..split] is skipped and
            // only common + the second ending play, with no repetition.
            var line = primaryEndingLine();

            var track = buildFirstTrack(songWith(line), SETTINGS_NO_REPEATS);
            var noteOns = noteOnEvents(track);
            assertThat(noteOns)
                .as("repeats OFF, primary: common + 2nd ending (first ending skipped)")
                .hasSize(REPEATS_OFF_PRIMARY_NOTE_ONS);

            // The second-ending note must start where it would if the first ending were
            // simply absent (one common crotchet in), proving ticks are threaded across the
            // skipped gap rather than left as if the first ending had been played.
            assertThat(noteOns.get(1).getTick())
                .as("second-ending note-on threaded across the skipped first ending")
                .isEqualTo(SECOND_ENDING_NOTE_ON_TICK);
        }

        @Test
        void testFirstSecondEndingsRepeatsOffLeftRightSplit() throws InvalidMidiDataException {
            // Repeats OFF, REPEAT_LEFT_RIGHT split: only the second ending plays.
            var line = leftRightSplitLine();

            var track = buildFirstTrack(songWith(line), SETTINGS_NO_REPEATS);
            assertThat(noteOnEvents(track))
                .as("repeats OFF, REPEAT_LEFT_RIGHT split: 2nd ending only")
                .hasSize(REPEATS_OFF_LEFT_RIGHT_NOTE_ONS);
        }

        @Test
        void testMultiNoteEndingsRepeatsOff() throws InvalidMidiDataException {
            var line = multiNoteEndingLine();

            var track = buildFirstTrack(songWith(line), SETTINGS_NO_REPEATS);
            assertThat(noteOnEvents(track))
                .as("multi-note endings, repeats OFF: common + second-ending notes")
                .hasSize(MULTI_NOTE_REPEATS_OFF_NOTE_ONS);
        }

        @Test
        void testFirstEndingPartialBuildSkipsRemainderOfFirstEnding()
            throws InvalidMidiDataException {
            // Partial build starting inside the first ending (buildFromNoteToEnd with a
            // non-zero startNote) exercises the lineStart > 0 path through
            // addLineSkippingFirstEndings: the common section before the start is excluded
            // and the partial first ending is still skipped, leaving only the second ending.
            var line = multiNoteEndingLine();

            var track = buildFirstTrackFromNote(
                songWith(line), SETTINGS_NO_REPEATS, 0, MULTI_NOTE_PARTIAL_START_NOTE);
            assertThat(noteOnEvents(track))
                .as("partial build from inside first ending: only second-ending notes play")
                .hasSize(MULTI_NOTE_PARTIAL_NOTE_ONS);
        }

        @Test
        void testTwoEndingsOnOneLineRepeatsOff() throws InvalidMidiDataException {
            // Two endings on one line: addLineSkippingFirstEndings must sort both skip spans
            // and thread the emitted segments across each gap, so each common section and
            // each second ending plays while both first endings are skipped.
            var line = twoEndingsLine();

            var track = buildFirstTrack(songWith(line), SETTINGS_NO_REPEATS);
            assertThat(noteOnEvents(track))
                .as("two endings, repeats OFF: both common sections + both second endings")
                .hasSize(TWO_ENDINGS_REPEATS_OFF_NOTE_ONS);
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
            // Real primary shape (REPEAT_RIGHT split):
            //   [noteCommon, SINGLE_BARLINE, note1st, REPEAT_RIGHT, note2nd, SINGLE_BARLINE]
            //   Ending(anchor=barline@1, end=barline@5), split = REPEAT_RIGHT@3
            //
            // Pass 1: noteCommon + note1st → REPEAT_RIGHT (split) jumps back to song start.
            // Pass 2: noteCommon → barline → skip first ending to the split → note2nd plays.
            //
            // Expected: noteCommon × REPEAT_PLAY_COUNT + note1st × 1 + note2nd × 1
            //         = FIRST_SECOND_ENDING_TOTAL_NOTE_ONS
            var line = primaryEndingLine();

            var track = buildFirstTrack(songWith(line), SETTINGS_WITH_REPEATS);
            assertThat(noteOnEvents(track))
                .as("first/second endings: " + FIRST_SECOND_ENDING_TOTAL_NOTE_ONS + " NOTE_ONs "
                    + "(common×" + REPEAT_PLAY_COUNT + " + 1st-ending×1 + 2nd-ending×1)")
                .hasSize(FIRST_SECOND_ENDING_TOTAL_NOTE_ONS);
        }

        @Test
        void testFirstSecondEndingsRepeatLeftRightSplit() throws InvalidMidiDataException {
            // Secondary shape (REPEAT_LEFT_RIGHT split), repeats ON:
            //   [REPEAT_LEFT, note1st, REPEAT_LEFT_RIGHT, note2nd, REPEAT_RIGHT]
            //   Ending(anchor=0, end=4), split = REPEAT_LEFT_RIGHT@2
            //
            // Pass 1: note1st → REPEAT_LEFT_RIGHT (split) jumps back to REPEAT_LEFT.
            // Pass 2: skip first ending to the split → note2nd plays. The closing
            //   REPEAT_RIGHT@4 is the ending end, so it does not start a third pass.
            //
            // Expected: note1st × 1 + note2nd × 1 (no common section).
            var line = leftRightSplitLine();

            var track = buildFirstTrack(songWith(line), SETTINGS_WITH_REPEATS);
            assertThat(noteOnEvents(track))
                .as("REPEAT_LEFT_RIGHT split, repeats ON: 1st-ending + 2nd-ending")
                .hasSize(REPEATS_ON_LEFT_RIGHT_NOTE_ONS);
        }

        @Test
        void testMultiNoteEndingsRepeatsOn() throws InvalidMidiDataException {
            var line = multiNoteEndingLine();

            var track = buildFirstTrack(songWith(line), SETTINGS_WITH_REPEATS);
            assertThat(noteOnEvents(track))
                .as("multi-note endings, repeats ON: common×" + REPEAT_PLAY_COUNT
                    + " + all first- and second-ending notes")
                .hasSize(MULTI_NOTE_REPEATS_ON_NOTE_ONS);
        }

    }

    // -------------------------------------------------------------------------
    // Shared fixtures for ending tests exercised under both toggle states
    // -------------------------------------------------------------------------

    /**
     * Real primary shape (REPEAT_RIGHT split):
     * <pre>
     *   [common, SINGLE_BARLINE, note1st, REPEAT_RIGHT, note2nd, SINGLE_BARLINE]
     *   Ending(anchor=barline@1, end=barline@5), split = REPEAT_RIGHT@3
     * </pre>
     */
    private static Line primaryEndingLine() {
        var noteCommon = crotchetAt(STAFF_POS_A);
        var startBarline = ElementType.SINGLE_BARLINE.newInstance();
        var note1st = crotchetAt(STAFF_POS_B);
        var repeatRight = ElementType.REPEAT_RIGHT.newInstance();
        var note2nd = crotchetAt(STAFF_POS_C);
        var endBarline = ElementType.SINGLE_BARLINE.newInstance();

        var line = detachedLine();
        line.addElement(noteCommon);   // index 0
        line.addElement(startBarline); // index 1 (anchor)
        line.addElement(note1st);      // index 2
        line.addElement(repeatRight);  // index 3 (split)
        line.addElement(note2nd);      // index 4
        line.addElement(endBarline);   // index 5 (end)

        line.addSpan(new Ending(startBarline, endBarline));
        return line;
    }

    /**
     * Secondary shape (REPEAT_LEFT_RIGHT split):
     * <pre>
     *   [REPEAT_LEFT, note1st, REPEAT_LEFT_RIGHT, note2nd, REPEAT_RIGHT]
     *   Ending(anchor=0, end=4), split = REPEAT_LEFT_RIGHT@2, end is itself a REPEAT_RIGHT
     * </pre>
     */
    private static Line leftRightSplitLine() {
        var repeatLeft = ElementType.REPEAT_LEFT.newInstance();
        var note1st = crotchetAt(STAFF_POS_A);
        var repeatLeftRight = ElementType.REPEAT_LEFT_RIGHT.newInstance();
        var note2nd = crotchetAt(STAFF_POS_B);
        var repeatRight = ElementType.REPEAT_RIGHT.newInstance();

        var line = detachedLine();
        line.addElement(repeatLeft);      // index 0 (anchor)
        line.addElement(note1st);         // index 1
        line.addElement(repeatLeftRight); // index 2 (split)
        line.addElement(note2nd);         // index 3
        line.addElement(repeatRight);     // index 4 (end)

        line.addSpan(new Ending(repeatLeft, repeatRight));
        return line;
    }

    /**
     * Primary shape with asymmetric multi-note endings (2 first, 3 second):
     * <pre>
     *   [common, SINGLE_BARLINE, note1stA, note1stB, REPEAT_RIGHT,
     *    note2ndA, note2ndB, note2ndC, SINGLE_BARLINE]
     *   Ending(anchor=barline@1, end=barline@8), split = REPEAT_RIGHT@4
     * </pre>
     */
    private static Line multiNoteEndingLine() {
        var common = crotchetAt(STAFF_POS_A);
        var startBarline = ElementType.SINGLE_BARLINE.newInstance();
        var note1stA = crotchetAt(STAFF_POS_B);
        var note1stB = crotchetAt(STAFF_POS_C);
        var repeatRight = ElementType.REPEAT_RIGHT.newInstance();
        var note2ndA = crotchetAt(STAFF_POS_B);
        var note2ndB = crotchetAt(STAFF_POS_C);
        var note2ndC = crotchetAt(STAFF_POS_A);
        var endBarline = ElementType.SINGLE_BARLINE.newInstance();

        var line = detachedLine();
        line.addElement(common);        // index 0
        line.addElement(startBarline);  // index 1 (anchor)
        line.addElement(note1stA);      // index 2
        line.addElement(note1stB);      // index 3
        line.addElement(repeatRight);   // index 4 (split)
        line.addElement(note2ndA);      // index 5
        line.addElement(note2ndB);      // index 6
        line.addElement(note2ndC);      // index 7
        line.addElement(endBarline);    // index 8 (end)

        line.addSpan(new Ending(startBarline, endBarline));
        return line;
    }

    /**
     * Two independent first/second endings on one line, each a primary (REPEAT_RIGHT split)
     * shape, with a common note before the first and another between the two:
     * <pre>
     *   [commonStart, BARLINE, note1stA, REPEAT_RIGHT, note2ndA, BARLINE,
     *    commonMid, BARLINE, note1stB, REPEAT_RIGHT, note2ndB, BARLINE]
     *   EndingA(anchor=barline@1, end=barline@5), split = REPEAT_RIGHT@3
     *   EndingB(anchor=barline@7, end=barline@11), split = REPEAT_RIGHT@9
     * </pre>
     */
    private static Line twoEndingsLine() {
        var commonStart = crotchetAt(STAFF_POS_A);
        var barlineA = ElementType.SINGLE_BARLINE.newInstance();
        var note1stA = crotchetAt(STAFF_POS_B);
        var repeatRightA = ElementType.REPEAT_RIGHT.newInstance();
        var note2ndA = crotchetAt(STAFF_POS_C);
        var barlineAEnd = ElementType.SINGLE_BARLINE.newInstance();
        var commonMid = crotchetAt(STAFF_POS_A);
        var barlineB = ElementType.SINGLE_BARLINE.newInstance();
        var note1stB = crotchetAt(STAFF_POS_B);
        var repeatRightB = ElementType.REPEAT_RIGHT.newInstance();
        var note2ndB = crotchetAt(STAFF_POS_C);
        var barlineBEnd = ElementType.SINGLE_BARLINE.newInstance();

        var line = detachedLine();
        line.addElement(commonStart);  // index 0
        line.addElement(barlineA);     // index 1 (anchor A)
        line.addElement(note1stA);     // index 2
        line.addElement(repeatRightA); // index 3 (split A)
        line.addElement(note2ndA);     // index 4
        line.addElement(barlineAEnd);  // index 5 (end A)
        line.addElement(commonMid);    // index 6
        line.addElement(barlineB);     // index 7 (anchor B)
        line.addElement(note1stB);     // index 8
        line.addElement(repeatRightB); // index 9 (split B)
        line.addElement(note2ndB);     // index 10
        line.addElement(barlineBEnd);  // index 11 (end B)

        line.addSpan(new Ending(barlineA, barlineAEnd));
        line.addSpan(new Ending(barlineB, barlineBEnd));
        return line;
    }

}
