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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;
import javax.swing.*;

import org.jetbrains.annotations.Nullable;

import songscribe.ui.Constants;
import songscribe.util.Log;

@SuppressWarnings("StaticNonFinalField")
public final class MidiController {

    // MIDI
    public static Sequencer sequencer = null;
    public static Receiver midiReceiver = null;
    public static Synthesizer synthesizer = null;

    private MidiController() {}

    private static final String SOUNDFONT_RESOURCE = "/soundfonts/FluidR3_GM.sf2";

    // Set up MIDI to play back music
    public static void openMidi() {
        try {
            var soundbank = loadBundledSoundbank();
            synthesizer = openSynthesizerWithSoundbank(soundbank);
            midiReceiver = synthesizer.getReceiver();
            initChannels(midiReceiver);

            // Use an unconnected sequencer and wire it to our synthesizer,
            // so both note preview and playback use the same soundfont.
            sequencer = MidiSystem.getSequencer(false);
            sequencer.getTransmitter().setReceiver(midiReceiver);
            sequencer.open();
        } catch (Exception e) {
            Log.warning("MIDI initialization failed: " + e.getMessage());
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

    /**
     * Opens the first synthesizer that can load the given soundbank.
     * Tries the default synthesizer first, then probes all available ones.
     */
    private static Synthesizer openSynthesizerWithSoundbank(
            @Nullable Soundbank soundbank
    ) throws MidiUnavailableException {
        var candidates = new ArrayList<Synthesizer>();
        candidates.add(MidiSystem.getSynthesizer());
        candidates.addAll(findAllSynthesizers());

        Exception lastError = null;

        for (var synth : candidates) {
            if (synth == null) {
                continue;
            }

            try {
                synth.open();

                if (soundbank != null) {
                    // Unload default instruments to avoid mapping conflicts
                    var defaultSb = synth.getDefaultSoundbank();

                    if (defaultSb != null) {
                        try {
                            synth.unloadAllInstruments(defaultSb);
                        } catch (Exception ignored) {}
                    }

                    if (!synth.loadAllInstruments(soundbank)) {
                        throw new IllegalStateException("loadAllInstruments returned false");
                    }

                    Log.info("Loaded soundfont: " + soundbank.getName() +
                            " into synth: " + synth.getDeviceInfo().getName());
                }

                return synth;
            } catch (Exception e) {
                lastError = e;

                try {
                    synth.close();
                } catch (Exception ignored) {}
            }
        }

        throw new MidiUnavailableException(
                "No synthesizer could load the soundbank" +
                (lastError != null ? ": " + lastError.getMessage() : "")
        );
    }

    private static List<Synthesizer> findAllSynthesizers() {
        var result = new ArrayList<Synthesizer>();

        for (var info : MidiSystem.getMidiDeviceInfo()) {
            try {
                var device = MidiSystem.getMidiDevice(info);

                if (device instanceof Synthesizer synth) {
                    result.add(synth);
                }
            } catch (Exception ignored) {}
        }

        return result;
    }

    /**
     * Extracts the bundled SF2 resource to a temp file and loads it.
     * Loading from a File is more reliable than from an InputStream.
     */
    private static @Nullable Soundbank loadBundledSoundbank() {
        try {
            var sf2File = extractSoundfontToTempFile(SOUNDFONT_RESOURCE);
            var soundbank = MidiSystem.getSoundbank(sf2File);

            if (soundbank == null) {
                Log.warning("MidiSystem could not parse bundled soundfont");
                return null;
            }

            return soundbank;
        } catch (FileNotFoundException e) {
            Log.warning("Bundled soundfont not found: " + SOUNDFONT_RESOURCE);
        } catch (InvalidMidiDataException | IOException e) {
            Log.warning("Failed to load bundled soundfont: " + e.getMessage());
        }

        return null;
    }

    private static File extractSoundfontToTempFile(String resourcePath)
            throws IOException {
        try (InputStream in = MidiController.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new FileNotFoundException(
                        "Resource not found on classpath: " + resourcePath
                );
            }

            var tmp = Files.createTempFile("soundfont-", ".sf2");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp.toFile();
        }
    }

    /**
     * Sends a GM System On reset and sets sensible CC defaults on all channels.
     */
    private static void initChannels(Receiver receiver) {
        try {
            // GM System On reset
            var gmOn = new SysexMessage();
            gmOn.setMessage(
                    new byte[]{(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7},
                    6
            );
            receiver.send(gmOn, -1);

            for (int ch = 0; ch < 16; ch++) {
                if (ch == 9) {
                    // Drums: drier, no chorus
                    initChannel(receiver, ch, 100, 64, 127, 15, 0);
                } else {
                    initChannel(receiver, ch, 100, 64, 110, 35, 10);
                }
            }
        } catch (InvalidMidiDataException e) {
            Log.warning("Failed to initialize MIDI channels: " + e.getMessage());
        }
    }

    private static void initChannel(
            Receiver receiver, int ch,
            int volume, int pan, int expression,
            int reverbSend, int chorusSend
    ) throws InvalidMidiDataException {
        cc(receiver, ch, 0, 0);           // Bank select MSB (GM bank 0)
        cc(receiver, ch, 32, 0);          // Bank select LSB
        cc(receiver, ch, 7, volume);       // Volume
        cc(receiver, ch, 10, pan);         // Pan
        cc(receiver, ch, 11, expression);  // Expression
        cc(receiver, ch, 91, reverbSend);  // Reverb send
        cc(receiver, ch, 93, chorusSend);  // Chorus send
    }

    private static void cc(Receiver receiver, int ch, int controller, int value)
            throws InvalidMidiDataException {
        var msg = new ShortMessage();
        msg.setMessage(ShortMessage.CONTROL_CHANGE, ch, controller,
                Math.clamp(value, 0, 127));
        receiver.send(msg, -1);
    }

    /**
     * Re-sends GM reset + CC defaults on all channels.
     * Call before each playback to counteract the sequencer's
     * automatic CC 121 (Reset All Controllers) sent when it stops.
     */
    public static void reinitChannels() {
        if (midiReceiver != null) {
            initChannels(midiReceiver);
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
