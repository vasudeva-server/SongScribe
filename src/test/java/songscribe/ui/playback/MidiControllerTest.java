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

package songscribe.ui.playback;

import module java.desktop;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.ui.component.MainFrame;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MidiControllerTest extends UnitTest {

    @AfterEach
    void tearDown() {
        MidiController.sequencer = null;
        MidiController.midiReceiver = null;
        MidiController.synthesizer = null;
        MidiController.closed = false;
        MidiController.failForTesting = false;
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetPlaybackVolume {

        // Boundary values from the formula: Math.round(Math.clamp(percent, 50, 100) / 100f * 127)
        private static final int VOLUME_PERCENT_MIN = 50;
        private static final int VOLUME_PERCENT_MAX = 100;
        private static final int MIDI_CC7_AT_MIN_PERCENT = 64;
        private static final int MIDI_CC7_AT_MAX_PERCENT = 127;
        private static final int VOLUME_PERCENT_MID = 75;
        // Math.round(75 / 100f * 127) = Math.round(95.25) = 95
        private static final int MIDI_CC7_AT_MID_PERCENT = 95;
        private static final int MIDI_CC7_CONTROLLER = 7;
        private static final int MIDI_CHANNELS = 16;

        /**
         * Calls {@code setPlaybackVolume(percent)} with a mock receiver wired in,
         * verifies exactly {@value #MIDI_CHANNELS} sends, and returns the first
         * captured {@link ShortMessage}.
         */
        private ShortMessage captureFirstVolumeMessage(int percent) throws Exception {
            var mockReceiver = mock(Receiver.class);
            MidiController.midiReceiver = mockReceiver;

            MidiController.setPlaybackVolume(percent);

            var captor = ArgumentCaptor.forClass(MidiMessage.class);
            verify(mockReceiver, times(MIDI_CHANNELS)).send(captor.capture(), anyLong());
            return (ShortMessage) captor.getAllValues().get(0);
        }

        @Test
        void testBoundaryValuesProduceCorrectMidiCc7Values() throws Exception {
            // Volume at minimum percent (50) must map to MIDI CC7 value 64
            var msg = captureFirstVolumeMessage(VOLUME_PERCENT_MIN);
            assertThat(msg.getCommand()).isEqualTo(ShortMessage.CONTROL_CHANGE);
            assertThat(msg.getData1()).isEqualTo(MIDI_CC7_CONTROLLER);
            assertThat(msg.getData2()).isEqualTo(MIDI_CC7_AT_MIN_PERCENT);
        }

        @Test
        void testMaxPercentProducesFullMidiCc7Value() throws Exception {
            var msg = captureFirstVolumeMessage(VOLUME_PERCENT_MAX);
            assertThat(msg.getData1()).isEqualTo(MIDI_CC7_CONTROLLER);
            assertThat(msg.getData2()).isEqualTo(MIDI_CC7_AT_MAX_PERCENT);
        }

        @Test
        void testMidpointPercentProducesCorrectMidiCc7Value() throws Exception {
            var msg = captureFirstVolumeMessage(VOLUME_PERCENT_MID);
            assertThat(msg.getData1()).isEqualTo(MIDI_CC7_CONTROLLER);
            assertThat(msg.getData2()).isEqualTo(MIDI_CC7_AT_MID_PERCENT);
        }

        @Test
        void testPercentBelowMinimumClampsToMinimum() throws Exception {
            // Below 50 clamps to 50 → same MIDI value as VOLUME_PERCENT_MIN
            var msg = captureFirstVolumeMessage(VOLUME_PERCENT_MIN - 1);
            assertThat(msg.getData1()).isEqualTo(MIDI_CC7_CONTROLLER);
            assertThat(msg.getData2()).isEqualTo(MIDI_CC7_AT_MIN_PERCENT);
        }

        @Test
        void testPercentAboveMaximumClampsToMaximum() throws Exception {
            // Above 100 clamps to 100 → same MIDI value as VOLUME_PERCENT_MAX
            var msg = captureFirstVolumeMessage(VOLUME_PERCENT_MAX + 1);
            assertThat(msg.getData1()).isEqualTo(MIDI_CC7_CONTROLLER);
            assertThat(msg.getData2()).isEqualTo(MIDI_CC7_AT_MAX_PERCENT);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetPlaybackInstrument {

        private static final int MIDI_PROGRAM_CHANNEL = 0;
        private static final int MIDI_PROGRAM_MIN = 0;
        private static final int MIDI_PROGRAM_MAX = 127;

        @Test
        void testSendsProgramChangeOnChannel0WithCorrectProgram() throws Exception {
            var mockReceiver = mock(Receiver.class);
            MidiController.midiReceiver = mockReceiver;
            final int program = 42;

            MidiController.setPlaybackInstrument(program);

            var captor = ArgumentCaptor.forClass(MidiMessage.class);
            verify(mockReceiver).send(captor.capture(), anyLong());
            var msg = (ShortMessage) captor.getValue();
            assertThat(msg.getCommand()).isEqualTo(ShortMessage.PROGRAM_CHANGE);
            assertThat(msg.getChannel()).isEqualTo(MIDI_PROGRAM_CHANNEL);
            assertThat(msg.getData1()).isEqualTo(program);
        }

        @Test
        void testProgramBelowZeroClampedToMinimum() throws Exception {
            var mockReceiver = mock(Receiver.class);
            MidiController.midiReceiver = mockReceiver;

            MidiController.setPlaybackInstrument(MIDI_PROGRAM_MIN - 1);

            var captor = ArgumentCaptor.forClass(MidiMessage.class);
            verify(mockReceiver).send(captor.capture(), anyLong());
            var msg = (ShortMessage) captor.getValue();
            assertThat(msg.getData1()).isEqualTo(MIDI_PROGRAM_MIN);
        }

        @Test
        void testProgramAbove127ClampedToMaximum() throws Exception {
            var mockReceiver = mock(Receiver.class);
            MidiController.midiReceiver = mockReceiver;

            MidiController.setPlaybackInstrument(MIDI_PROGRAM_MAX + 1);

            var captor = ArgumentCaptor.forClass(MidiMessage.class);
            verify(mockReceiver).send(captor.capture(), anyLong());
            var msg = (ShortMessage) captor.getValue();
            assertThat(msg.getData1()).isEqualTo(MIDI_PROGRAM_MAX);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class IsPlaying {

        @Test
        void testReturnsFalseWhenSequencerIsNull() {
            // sequencer is null from tearDown; no setup needed
            assertThat(MidiController.isPlaying()).isFalse();
        }

        @Test
        void testDelegatesToSequencerIsRunning() {
            var mockSequencer = mock(Sequencer.class);
            MidiController.sequencer = mockSequencer;
            when(mockSequencer.isRunning()).thenReturn(true);

            assertThat(MidiController.isPlaying()).isTrue();
        }

        @Test
        void testReturnsFalseWhenSequencerIsNotRunning() {
            var mockSequencer = mock(Sequencer.class);
            MidiController.sequencer = mockSequencer;
            when(mockSequencer.isRunning()).thenReturn(false);

            assertThat(MidiController.isPlaying()).isFalse();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CloseMidi {

        @Test
        void testSecondCallDoesNotCloseReceiverAgain() {
            var mockReceiver = mock(Receiver.class);
            MidiController.midiReceiver = mockReceiver;

            MidiController.closeMidi();
            // midiReceiver is not nulled out by closeMidi(); the closed flag guards the second call
            MidiController.closeMidi();

            // Despite two calls, midiReceiver.close() is invoked exactly once
            verify(mockReceiver, times(1)).close();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class IsAvailable {

        @Test
        void testReturnsFalseWhenSequencerIsNull() {
            // sequencer is null from tearDown; no setup needed
            assertThat(MidiController.isAvailable())
                .as("isAvailable() must be false when sequencer is null")
                .isFalse();
        }

        @Test
        void testReturnsTrueWhenSequencerIsSet() {
            MidiController.sequencer = mock(Sequencer.class);

            assertThat(MidiController.isAvailable())
                .as("isAvailable() must be true when sequencer is set")
                .isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class OpenMidiAsync {

        private static final long LATCH_TIMEOUT_SECONDS = 10;

        @AfterEach
        void clearStartupErrors() {
            MainFrame.clearStartupErrorsForTest();
        }

        /**
         * The latch must reach zero even when MIDI init throws. The finally block in
         * openMidiAsync() guarantees countdown on any outcome.
         *
         * The success path is not unit-tested here because it depends on a real MIDI
         * system and the bundled soundfont being present, which fails on headless CI.
         * This forced-failure test already proves the {@code finally} countdown holds.
         */
        @Test
        void testLatchReachesZeroOnForcedFailure() throws InterruptedException {
            // Force openMidi() into the failure path via the package-private test flag.
            MidiController.failForTesting = true;

            var latch = MidiController.openMidiAsync();

            assertThat(latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("latch must reach zero even after openMidi() fails")
                .isTrue();
            assertThat(MidiController.sequencer)
                .as("sequencer must remain null after failed MIDI init")
                .isNull();
        }
    }
}
