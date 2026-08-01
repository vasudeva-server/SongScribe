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

import java.util.List;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PlayThreadTest extends UnitTest {

    // Thread join timeout — well above NOTE_DURATION_MS to avoid flakiness on slow CI
    private static final long JOIN_TIMEOUT_MS = 3_000L;

    // MIDI channel used by PlayThread (always channel 0)
    private static final int MIDI_CHANNEL = 0;

    // CC controller numbers used in setupInstrument()
    private static final int BANK_MSB_CONTROLLER = 0;
    private static final int BANK_LSB_CONTROLLER = 32;

    @AfterEach
    void tearDown() {
        MidiController.midiReceiver = null;
        // Reset PlaybackController fields that affect instrument selection
        PlaybackController.setInstrument(0);
    }

    // -------------------------------------------------------------------------
    // Helper: collect all ShortMessages sent to a mock receiver
    // -------------------------------------------------------------------------

    private static List<ShortMessage> captureMessages(Receiver receiver) {
        var captor = ArgumentCaptor.forClass(MidiMessage.class);
        verify(receiver, atLeastOnce()).send(captor.capture(), anyLong());
        return captor.getAllValues().stream()
            .map(m -> (ShortMessage) m)
            .toList();
    }

    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Run {

        /**
         * Starts the given thread and waits for it to finish.
         * Fails the test if the join times out.
         */
        private static void runAndJoin(PlayThread thread) throws InterruptedException {
            thread.start();
            thread.join(JOIN_TIMEOUT_MS);
            assertThat(thread.isAlive())
                .as("PlayThread should finish within %d ms", JOIN_TIMEOUT_MS)
                .isFalse();
        }

        @Test
        void testWhenPlayNoteOnTrueSendsNoteOnThenNoteOff() throws InterruptedException {
            var mockReceiver = mock(Receiver.class);
            MidiController.midiReceiver = mockReceiver;
            final var pitch = 60;
            // setupInstrument sends 3 messages; then NOTE_ON; then NOTE_OFF = 5 total
            final var expectedMessageCount = 5;

            runAndJoin(new PlayThread(pitch, true));

            // Collect all sent messages
            var messages = captureMessages(mockReceiver);

            assertThat(messages).hasSize(expectedMessageCount);
            var noteOn = messages.get(expectedMessageCount - 2);
            var noteOff = messages.get(expectedMessageCount - 1);

            assertThat(noteOn.getCommand()).isEqualTo(ShortMessage.NOTE_ON);
            assertThat(noteOn.getData1()).isEqualTo(pitch);
            assertThat(noteOn.getData2()).isEqualTo(PlaybackController.SELECTED_NOTE_VELOCITY);

            assertThat(noteOff.getCommand()).isEqualTo(ShortMessage.NOTE_OFF);
            assertThat(noteOff.getData1()).isEqualTo(pitch);
        }

        @Test
        void testDefaultConstructorSetsPlayNoteOnTrue() throws InterruptedException {
            var mockReceiver = mock(Receiver.class);
            MidiController.midiReceiver = mockReceiver;
            final var pitch = 60;
            // setupInstrument sends 3 messages; then NOTE_ON; then NOTE_OFF = 5 total
            final var expectedMessageCount = 5;

            // The one-arg constructor defaults playNoteOn to true
            runAndJoin(new PlayThread(pitch));

            var messages = captureMessages(mockReceiver);
            assertThat(messages).hasSize(expectedMessageCount);
            var noteOn = messages.get(expectedMessageCount - 2);
            assertThat(noteOn.getCommand()).isEqualTo(ShortMessage.NOTE_ON);
            assertThat(noteOn.getData1()).isEqualTo(pitch);
        }

        @Test
        void testWhenPlayNoteOnFalseSkipsNoteOnButSendsNoteOff() throws InterruptedException {
            var mockReceiver = mock(Receiver.class);
            MidiController.midiReceiver = mockReceiver;
            final var pitch = 64;

            runAndJoin(new PlayThread(pitch, false));

            // With playNoteOn=false, sendNoteOn is not called so setupInstrument is not
            // called either; only sendNoteOff runs — exactly one message
            var captor = ArgumentCaptor.forClass(MidiMessage.class);
            verify(mockReceiver).send(captor.capture(), anyLong());
            var msg = (ShortMessage) captor.getValue();

            assertThat(msg.getCommand()).isEqualTo(ShortMessage.NOTE_OFF);
            assertThat(msg.getData1()).isEqualTo(pitch);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SendNoteOn {

        @Test
        void testNoOpWhenMidiReceiverIsNull() {
            // midiReceiver is null from tearDown — no exception should be thrown
            assertThatCode(() -> PlayThread.sendNoteOn(60))
                .doesNotThrowAnyException();
        }

        @Test
        void testSendsBankSelectProgramChangeAndNoteOn() throws Exception {
            final var pitch = 69;
            final var instrument = 5;
            PlaybackController.setInstrument(instrument);
            var mockReceiver = mock(Receiver.class);
            MidiController.midiReceiver = mockReceiver;

            PlayThread.sendNoteOn(pitch);

            var messages = captureMessages(mockReceiver);

            // Expected sequence: bank MSB CC, bank LSB CC, PROGRAM_CHANGE, NOTE_ON
            assertThat(messages).hasSize(4);

            // Bank MSB: CC 0 on channel 0 with value 0
            var bankMsb = messages.getFirst();
            assertThat(bankMsb.getCommand()).isEqualTo(ShortMessage.CONTROL_CHANGE);
            assertThat(bankMsb.getChannel()).isEqualTo(MIDI_CHANNEL);
            assertThat(bankMsb.getData1()).isEqualTo(BANK_MSB_CONTROLLER);
            assertThat(bankMsb.getData2()).isZero();

            // Bank LSB: CC 32 on channel 0 with value 0
            var bankLsb = messages.get(1);
            assertThat(bankLsb.getCommand()).isEqualTo(ShortMessage.CONTROL_CHANGE);
            assertThat(bankLsb.getChannel()).isEqualTo(MIDI_CHANNEL);
            assertThat(bankLsb.getData1()).isEqualTo(BANK_LSB_CONTROLLER);
            assertThat(bankLsb.getData2()).isZero();

            // Program change: instrument number on channel 0
            var programChange = messages.get(2);
            assertThat(programChange.getCommand()).isEqualTo(ShortMessage.PROGRAM_CHANGE);
            assertThat(programChange.getChannel()).isEqualTo(MIDI_CHANNEL);
            assertThat(programChange.getData1()).isEqualTo(instrument);

            // NOTE_ON: correct pitch and velocity
            var noteOn = messages.get(3);
            assertThat(noteOn.getCommand()).isEqualTo(ShortMessage.NOTE_ON);
            assertThat(noteOn.getChannel()).isEqualTo(MIDI_CHANNEL);
            assertThat(noteOn.getData1()).isEqualTo(pitch);
            assertThat(noteOn.getData2()).isEqualTo(PlaybackController.SELECTED_NOTE_VELOCITY);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SendNoteOff {

        @Test
        void testNoOpWhenMidiReceiverIsNull() {
            // midiReceiver is null from tearDown — no exception should be thrown
            assertThatCode(() -> PlayThread.sendNoteOff(60))
                .doesNotThrowAnyException();
        }

        @Test
        void testSendsNoteOffWithCorrectPitch() throws Exception {
            final var pitch = 72;
            var mockReceiver = mock(Receiver.class);
            MidiController.midiReceiver = mockReceiver;

            PlayThread.sendNoteOff(pitch);

            var captor = ArgumentCaptor.forClass(MidiMessage.class);
            verify(mockReceiver).send(captor.capture(), anyLong());
            var msg = (ShortMessage) captor.getValue();

            assertThat(msg.getCommand()).isEqualTo(ShortMessage.NOTE_OFF);
            assertThat(msg.getChannel()).isEqualTo(MIDI_CHANNEL);
            assertThat(msg.getData1()).isEqualTo(pitch);
        }
    }
}
