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

import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.swing.*;

import songscribe.ui.Constants;

@SuppressWarnings("StaticNonFinalField")
public final class MidiController {

    // MIDI
    public static Sequencer sequencer = null;
    public static Receiver midiReceiver = null;
    public static Synthesizer synthesizer = null;

    private MidiController() {}

    // Set up MIDI to play back music
    public static void openMidi() {
        try {
            synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();
            synthesizer.loadAllInstruments(synthesizer.getDefaultSoundbank());
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            midiReceiver = MidiSystem.getReceiver();
        } catch (MidiUnavailableException e) {
            JOptionPane.showMessageDialog(
                null,
                "You may already be running " + Constants.PACKAGE_NAME +
                " or another application that uses sound. " +
                "Please try to quit them and restart " +
                Constants.PACKAGE_NAME + ". " +
                "In this session playback will be disabled.",
                Constants.PACKAGE_NAME,
                JOptionPane.WARNING_MESSAGE
            );
        }
    }

    // Close MIDI resources so other applications can use them
    public static void closeMidi() {
        if (midiReceiver != null) {
            midiReceiver.close();
        }

        if (sequencer != null) {
            sequencer.close();
        }

        if (synthesizer != null) {
            synthesizer.close();
        }
    }

    public static boolean isPlaying() {
        return ((sequencer != null) && sequencer.isRunning());
    }
}
