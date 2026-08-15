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
package songscribe.ui.playback;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.ShortMessage;

import org.jspecify.annotations.Nullable;

public class PlayThread implements Runnable {

    private static final int NOTE_DURATION_MS = 700;

    private final int pitch;
    private final boolean playNoteOn;
    private @Nullable Thread thread;

    public PlayThread(int pitch) {
        this(pitch, true);
    }

    /**
     * @param pitch     the MIDI pitch to play
     * @param playNoteOn if true, sends NOTE_ON before the delay; if false, only
     *                   schedules the NOTE_OFF (useful when a NOTE_ON is already sounding)
     */
    public PlayThread(int pitch, boolean playNoteOn) {
        this.pitch = pitch;
        this.playNoteOn = playNoteOn;
    }

    public void start() {
        thread = new Thread(this);
        thread.start();
    }

    public void join(long millis) throws InterruptedException {
        if (thread != null) {
            thread.join(millis);
        }
    }

    public boolean isAlive() {
        return thread != null && thread.isAlive();
    }

    @Override
    public void run() {
        if (playNoteOn) {
            sendNoteOn(pitch);
        }

        try {
            Thread.sleep(NOTE_DURATION_MS);
        } catch (InterruptedException e) {
            // okay
        }

        sendNoteOff(pitch);
    }

    /** Sends bank-select and program-change messages to configure the instrument. */
    private static void setupInstrument() throws InvalidMidiDataException {
        var receiver = MidiController.midiReceiver;

        if (receiver == null) {
            return;
        }

        var bankMsb = new ShortMessage();
        bankMsb.setMessage(ShortMessage.CONTROL_CHANGE, 0, 0, 0);
        receiver.send(bankMsb, -1);

        var bankLsb = new ShortMessage();
        bankLsb.setMessage(ShortMessage.CONTROL_CHANGE, 0, 32, 0);
        receiver.send(bankLsb, -1);

        var programChange = new ShortMessage();
        var instrument = PlaybackController.getPlaybackSettings().instrument();
        programChange.setMessage(ShortMessage.PROGRAM_CHANGE, 0, instrument, 0);
        receiver.send(programChange, -1);
    }

    /**
     * Sends a NOTE_ON message for the given pitch (after instrument setup).
     * No-op if {@code midiReceiver} is null.
     */
    public static void sendNoteOn(int pitch) {
        if (MidiController.midiReceiver == null) {
            return;
        }

        try {
            setupInstrument();
            var on = new ShortMessage();
            on.setMessage(ShortMessage.NOTE_ON, 0, pitch, PlaybackController.SELECTED_NOTE_VELOCITY);
            MidiController.midiReceiver.send(on, -1);
        } catch (InvalidMidiDataException e) {
            // Ignore
        }
    }

    /**
     * Sends a NOTE_OFF message for the given pitch.
     * No-op if {@code midiReceiver} is null.
     */
    public static void sendNoteOff(int pitch) {
        if (MidiController.midiReceiver == null) {
            return;
        }

        try {
            var off = new ShortMessage();
            off.setMessage(ShortMessage.NOTE_OFF, 0, pitch, 0);
            MidiController.midiReceiver.send(off, -1);
        } catch (InvalidMidiDataException e) {
            // Ignore
        }
    }
}
