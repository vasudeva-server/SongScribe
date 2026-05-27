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

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Duration;
import songscribe.dom.Tempo;
import songscribe.ui.playback.MidiMetaMessageTypes;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.midi.MidiSequenceBuilder.PPQ;

@SuppressWarnings("OverlyBroadThrowsClause")
class MidiEventFactoryTest extends UnitTest {

    private static final int BPM_60 = 60;
    private static final int BPM_120 = 120;
    private static final int BPM_240 = 240;
    private static final int FULL_TEMPO_PERCENT = 100;
    private static final int DOUBLE_TEMPO_PERCENT = 200;
    private static final int MICROSECONDS_PER_MINUTE = 60_000_000;
    private static final int TEMPO_DATA_LENGTH = 3;

    // Precomputed expected bytes for common BPM values — derived, not magic
    private static final int MICROSECONDS_AT_120_BPM = MICROSECONDS_PER_MINUTE / BPM_120;
    private static final byte TEMPO_BYTE_HIGH_120 = (byte) (MICROSECONDS_AT_120_BPM >> 16);
    private static final byte TEMPO_BYTE_MID_120 = (byte) (MICROSECONDS_AT_120_BPM >> 8);
    private static final byte TEMPO_BYTE_LOW_120 = (byte) MICROSECONDS_AT_120_BPM;

    private static final int MICROSECONDS_AT_60_BPM = MICROSECONDS_PER_MINUTE / BPM_60;
    private static final byte TEMPO_BYTE_HIGH_60 = (byte) (MICROSECONDS_AT_60_BPM >> 16);
    private static final byte TEMPO_BYTE_MID_60 = (byte) (MICROSECONDS_AT_60_BPM >> 8);
    private static final byte TEMPO_BYTE_LOW_60 = (byte) MICROSECONDS_AT_60_BPM;

    private static final int MICROSECONDS_AT_240_BPM = MICROSECONDS_PER_MINUTE / BPM_240;
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
}
