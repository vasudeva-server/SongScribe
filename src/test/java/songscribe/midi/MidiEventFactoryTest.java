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
import java.util.stream.Stream;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.Key;
import songscribe.dom.KeySignatureElement;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tempo;
import songscribe.io.musicxml.KeySignatureMapping;
import songscribe.ui.playback.MidiMetaMessageTypes;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;
import static songscribe.midi.MidiSequenceBuilder.PPQ;

@SuppressWarnings("OverlyBroadThrowsClause")
class MidiEventFactoryTest extends UnitTest {

    private static final int BPM_60 = 60;
    private static final int BPM_120 = 120;
    private static final int BPM_240 = 240;
    private static final int FULL_TEMPO_PERCENT = 100;
    private static final int DOUBLE_TEMPO_PERCENT = 200;
    private static final int TEMPO_DATA_LENGTH = 3;

    // Precomputed expected bytes for common BPM values — derived, not magic
    private static final int MICROSECONDS_AT_120_BPM = MidiEventFactory.MICROSECONDS_PER_MINUTE / BPM_120;
    private static final byte TEMPO_BYTE_HIGH_120 = (byte) (MICROSECONDS_AT_120_BPM >> 16);
    private static final byte TEMPO_BYTE_MID_120 = (byte) (MICROSECONDS_AT_120_BPM >> 8);
    private static final byte TEMPO_BYTE_LOW_120 = (byte) MICROSECONDS_AT_120_BPM;

    private static final int MICROSECONDS_AT_60_BPM = MidiEventFactory.MICROSECONDS_PER_MINUTE / BPM_60;
    private static final byte TEMPO_BYTE_HIGH_60 = (byte) (MICROSECONDS_AT_60_BPM >> 16);
    private static final byte TEMPO_BYTE_MID_60 = (byte) (MICROSECONDS_AT_60_BPM >> 8);
    private static final byte TEMPO_BYTE_LOW_60 = (byte) MICROSECONDS_AT_60_BPM;

    private static final int MICROSECONDS_AT_240_BPM = MidiEventFactory.MICROSECONDS_PER_MINUTE / BPM_240;
    private static final byte TEMPO_BYTE_HIGH_240 = (byte) (MICROSECONDS_AT_240_BPM >> 16);
    private static final byte TEMPO_BYTE_MID_240 = (byte) (MICROSECONDS_AT_240_BPM >> 8);
    private static final byte TEMPO_BYTE_LOW_240 = (byte) MICROSECONDS_AT_240_BPM;

    private static Track newTrack() throws InvalidMidiDataException {
        var sequence = new Sequence(Sequence.PPQ, PPQ);
        return sequence.createTrack();
    }

    private static MetaMessage setTempoEventFrom(Track track) {
        for (var i = 0; i < track.size(); i++) {
            var ev = track.get(i);

            if (ev.getMessage() instanceof MetaMessage mm
                && mm.getType() == MidiMetaMessageTypes.SET_TEMPO) {
                return mm;
            }
        }
        throw new AssertionError("No SET_TEMPO meta event found in track");
    }

    /** Every valid key, driven from the domain rather than a hand-written list. */
    static Stream<Key> allSignatures() {
        return Key.allSignatures().stream();
    }

    private static List<MidiEvent> keySignatureEventsFrom(Track track) {
        var list = new ArrayList<MidiEvent>();

        for (var i = 0; i < track.size(); i++) {
            var ev = track.get(i);

            if (ev.getMessage() instanceof MetaMessage mm
                && mm.getType() == MidiMetaMessageTypes.KEY_SIGNATURE) {
                list.add(ev);
            }
        }

        return list;
    }

    @Nested
    class AddTempoEventWithBpm {

        @Test
        void testBytesEncode120Bpm() throws Exception {
            var track = newTrack();
            MidiEventFactory.addTempoEvent(track, 0, BPM_120);

            var data = setTempoEventFrom(track).getData();

            assertThat(data).hasSize(TEMPO_DATA_LENGTH);
            assertThat(data[0]).isEqualTo(TEMPO_BYTE_HIGH_120);
            assertThat(data[1]).isEqualTo(TEMPO_BYTE_MID_120);
            assertThat(data[2]).isEqualTo(TEMPO_BYTE_LOW_120);
        }

        @Test
        void testBytesEncode60Bpm() throws Exception {
            var track = newTrack();
            MidiEventFactory.addTempoEvent(track, 0, BPM_60);

            var data = setTempoEventFrom(track).getData();

            assertThat(data).hasSize(TEMPO_DATA_LENGTH);
            assertThat(data[0]).isEqualTo(TEMPO_BYTE_HIGH_60);
            assertThat(data[1]).isEqualTo(TEMPO_BYTE_MID_60);
            assertThat(data[2]).isEqualTo(TEMPO_BYTE_LOW_60);
        }

        @Test
        void testEventPlacedAtSpecifiedTick() throws Exception {
            var track = newTrack();
            MidiEventFactory.addTempoEvent(track, PPQ, BPM_120);

            for (var i = 0; i < track.size(); i++) {
                var ev = track.get(i);

                if (ev.getMessage() instanceof MetaMessage mm
                    && mm.getType() == MidiMetaMessageTypes.SET_TEMPO) {
                    assertThat(ev.getTick()).isEqualTo(PPQ);
                    return;
                }
            }

            throw new AssertionError("No SET_TEMPO meta event found");
        }
    }

    @Nested
    class AddTempoEventWithTempoRecord {

        @Test
        void testBytesMatchDirectBpmAtFullPercent() throws Exception {
            // CROTCHET tempo → getRealTempo() = (bpm * PPQ) / PPQ = bpm, so 120 BPM passes through
            var tempo = new Tempo(BPM_120, Duration.CROTCHET, "", false);
            var track = newTrack();
            MidiEventFactory.addTempoEvent(track, 0, tempo, FULL_TEMPO_PERCENT);

            var data = setTempoEventFrom(track).getData();

            assertThat(data).hasSize(TEMPO_DATA_LENGTH);
            assertThat(data[0]).isEqualTo(TEMPO_BYTE_HIGH_120);
            assertThat(data[1]).isEqualTo(TEMPO_BYTE_MID_120);
            assertThat(data[2]).isEqualTo(TEMPO_BYTE_LOW_120);
        }

        @Test
        void testDoubledTempoAtTwoHundredPercent() throws Exception {
            // 200% tempo doubles BPM from 120 → 240, halving μs/beat
            var tempo = new Tempo(BPM_120, Duration.CROTCHET, "", false);
            var track = newTrack();
            MidiEventFactory.addTempoEvent(track, 0, tempo, DOUBLE_TEMPO_PERCENT);

            var data = setTempoEventFrom(track).getData();

            assertThat(data).hasSize(TEMPO_DATA_LENGTH);
            assertThat(data[0]).isEqualTo(TEMPO_BYTE_HIGH_240);
            assertThat(data[1]).isEqualTo(TEMPO_BYTE_MID_240);
            assertThat(data[2]).isEqualTo(TEMPO_BYTE_LOW_240);
        }
    }

    @Nested
    class AddKeySignatureEvent {

        private static final int KEY_SIGNATURE_DATA_LENGTH = 2;
        private static final byte MAJOR_MODE_BYTE = 0;

        @ParameterizedTest
        @MethodSource("songscribe.midi.MidiEventFactoryTest#allSignatures")
        void testSfByteMatchesTheMusicXmlFifthsEncoding(Key key) throws Exception {
            var track = newTrack();
            MidiEventFactory.addKeySignatureEvent(track, 0, key);

            var data = ((MetaMessage) keySignatureEventsFrom(track).getFirst().getMessage()).getData();

            assertThat(data).hasSize(KEY_SIGNATURE_DATA_LENGTH);
            assertThat(data[0]).isEqualTo((byte) KeySignatureMapping.toFifths(key));
        }

        @ParameterizedTest
        @MethodSource("songscribe.midi.MidiEventFactoryTest#allSignatures")
        void testModeByteIsAlwaysMajor(Key key) throws Exception {
            var track = newTrack();
            MidiEventFactory.addKeySignatureEvent(track, 0, key);

            var data = ((MetaMessage) keySignatureEventsFrom(track).getFirst().getMessage()).getData();

            assertThat(data[1]).as("mi byte is always major").isEqualTo(MAJOR_MODE_BYTE);
        }

        @ParameterizedTest
        @MethodSource("songscribe.midi.MidiEventFactoryTest#allSignatures")
        void testSfByteSignMatchesAccidentalType(Key key) throws Exception {
            var track = newTrack();
            MidiEventFactory.addKeySignatureEvent(track, 0, key);

            var sf = ((MetaMessage) keySignatureEventsFrom(track).getFirst().getMessage()).getData()[0];

            if (key.keyType() == KeyType.FLATS) {
                assertThat(sf).as("flats encode as a negative sf byte").isLessThan((byte) 0);
            } else if (key.keyType() == KeyType.SHARPS) {
                assertThat(sf).as("sharps encode as a positive sf byte").isGreaterThan((byte) 0);
            } else {
                assertThat(sf).as("no accidentals encodes as zero").isEqualTo((byte) 0);
            }
        }

        @Test
        void testEventPlacedAtSpecifiedTick() throws Exception {
            var track = newTrack();
            MidiEventFactory.addKeySignatureEvent(track, PPQ, new Key(KeyType.SHARPS, 2));

            assertThat(keySignatureEventsFrom(track).getFirst().getTick()).isEqualTo(PPQ);
        }
    }

    @Nested
    class KeySignatureEmissionDuringTrackBuilding {

        private static final Tempo CROTCHET_TEMPO = new Tempo(120, Duration.CROTCHET, "", false);
        private static final PlaybackSettings SETTINGS_NO_REPEATS =
            new PlaybackSettings(0, 100, 100, false);
        private static final PlaybackSettings SETTINGS_WITH_REPEATS =
            new PlaybackSettings(0, 100, 100, true);

        // A note inside a simple repeat plays once on first pass and once on second pass.
        private static final int REPEAT_PLAY_COUNT = 2;

        private static final Key MID_LINE_KEY = new Key(KeyType.SHARPS, 3);

        private static Song songWith(Line line) {
            var song = new Song();
            song.getLines().clear();
            song.getLines().add(line);
            return song;
        }

        /**
         * Barline, mid-line key change, note — the shape {@link KeySignatureElement}'s position
         * invariant requires.
         */
        private static Line lineWithMidLineKeyChange() {
            var line = detachedLine();
            line.addElement(singleBarline());
            line.addElement(new KeySignatureElement(MID_LINE_KEY));
            line.addElement(crotchet());
            return line;
        }

        @Test
        void testLineOwnKeyEmitsAtTickZero() throws Exception {
            var line = detachedLine();
            line.addElement(crotchet());
            var track = newTrack();

            new LineTrackBuilder(line).addToTrack(track, 0, 0, CROTCHET_TEMPO, SETTINGS_NO_REPEATS);

            var events = keySignatureEventsFrom(track);
            assertThat(events).as("line's own key emitted once").hasSize(1);
            assertThat(events.getFirst().getTick()).as("emitted at tick 0").isEqualTo(0);
        }

        @Test
        void testMidLineKeyChangeEmitsAtItsOwnTickNotOnlyAtTickZero() throws Exception {
            var line = lineWithMidLineKeyChange();
            var track = newTrack();

            new LineTrackBuilder(line).addToTrack(track, 0, 0, CROTCHET_TEMPO, SETTINGS_NO_REPEATS);

            var events = keySignatureEventsFrom(track);
            // One for the line's own (inherited C major) key at tick 0, one for the
            // mid-line change — both at tick 0, since the barline and the key signature
            // element itself carry no duration.
            assertThat(events).as("line key and mid-line change both emitted").hasSize(2);

            var mm = (MetaMessage) events.get(1).getMessage();
            assertThat(mm.getData()[0])
                .as("mid-line event encodes the mid-line key, not the line's own key")
                .isEqualTo((byte) KeySignatureMapping.toFifths(MID_LINE_KEY));
        }

        @Test
        void testRepeatedPassageWithAKeyChangeEmitsTheEventOnEachReplay() throws Exception {
            // Line: [barline], [mid-line key change], [note], [REPEAT_RIGHT].
            // No explicit REPEAT_LEFT → backward search exhausts → jumps to start of line,
            // replaying the key change along with the note.
            var line = lineWithMidLineKeyChange();
            line.addElement(repeatRight());
            var song = songWith(line);

            var track = new MidiSequenceBuilder(song, SETTINGS_WITH_REPEATS)
                .buildFullSequence()
                .getTracks()[0];

            var midLineKeyFifths = (byte) KeySignatureMapping.toFifths(MID_LINE_KEY);
            var midLineEvents = keySignatureEventsFrom(track).stream()
                .filter(ev -> ((MetaMessage) ev.getMessage()).getData()[0] == midLineKeyFifths)
                .toList();

            assertThat(midLineEvents)
                .as("mid-line key change replayed " + REPEAT_PLAY_COUNT + " times")
                .hasSize(REPEAT_PLAY_COUNT);

            var noteOnTicks = new ArrayList<Long>();
            for (var i = 0; i < track.size(); i++) {
                if (track.get(i).getMessage() instanceof ShortMessage sm
                    && sm.getCommand() == ShortMessage.NOTE_ON
                    && sm.getData2() > 0) {
                    noteOnTicks.add(track.get(i).getTick());
                }
            }

            assertThat(noteOnTicks).as("note replayed " + REPEAT_PLAY_COUNT + " times")
                .hasSize(REPEAT_PLAY_COUNT);
            assertThat(midLineEvents.stream().map(MidiEvent::getTick).toList())
                .as("each replay's key change lands at that pass's note-on tick")
                .isEqualTo(noteOnTicks);
        }
    }
}
