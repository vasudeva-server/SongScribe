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

import module java.desktop;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.lifecycle.Shutdown;
import songscribe.ui.component.MainFrame;

@SuppressWarnings("StaticNonFinalField")
public final class MidiController {

    private static final Logger LOG = LoggerFactory.getLogger(MidiController.class);

    // MIDI
    public static volatile @Nullable Sequencer sequencer = null;
    public static volatile @Nullable Receiver midiReceiver = null;
    public static volatile @Nullable Synthesizer synthesizer = null;
    static boolean closed = false;

    // Set to true in unit tests to force openMidi() into the failure path.
    static boolean failForTesting = false;

    private MidiController() {}

    private static final String SOUNDFONT_RESOURCE = "/soundfonts/FluidR3_GM.sf2";

    // Set up MIDI to play back music
    public static void openMidi() {
        var soundbank = loadBundledSoundbank();

        if (soundbank == null) {
            MainFrame.enqueueStartupError(new MainFrame.StartupError(
                Strings.ALERT_TITLE_SOUND,
                Strings.get(Strings.ALERT_SOUND_MISSING),
                true
            ));
            return;
        }

        try {
            if (failForTesting) {
                throw new MidiUnavailableException("forced failure for testing");
            }

            synthesizer = openSynthesizerWithSoundbank(soundbank);
            midiReceiver = synthesizer.getReceiver();
            initChannels(midiReceiver);

            // Use an unconnected sequencer and wire it to our synthesizer,
            // so both note preview and playback use the same soundfont.
            sequencer = MidiSystem.getSequencer(false);
            sequencer.getTransmitter().setReceiver(midiReceiver);
            sequencer.open();
            LOG.info("MIDI initialized");
            Shutdown.registerJVMTask("midi", MidiController::closeMidi);
        } catch (Exception e) {
            LOG.warn("MIDI initialization failed", e);
            MainFrame.enqueueStartupError(new MainFrame.StartupError(
                Strings.ALERT_TITLE_SOUND,
                Strings.ALERT_SOUND_INIT_FAILED,
                false
            ));
        }
    }

    public static boolean isAvailable() {
        return sequencer != null;
    }

    /**
     * Starts MIDI initialization on a background daemon thread.
     *
     * @return a latch that reaches zero when initialization is complete (success or failure)
     */
    public static CountDownLatch openMidiAsync() {
        var ready = new CountDownLatch(1);
        var thread = new Thread(() -> {
            try {
                openMidi();
            } finally {
                ready.countDown();
            }
        }, "midi-init");
        thread.setDaemon(true);
        thread.start();
        return ready;
    }

    /**
     * Opens the first synthesizer that can load the given soundbank.
     * Tries the default synthesizer first, then probes all available ones.
     */
    private static Synthesizer openSynthesizerWithSoundbank(
            Soundbank soundbank
    ) throws MidiUnavailableException {
        var candidates = new ArrayList<Synthesizer>();
        candidates.add(MidiSystem.getSynthesizer());
        candidates.addAll(findAllSynthesizers());

        Exception lastError = null;

        for (var synth : candidates) {
            try {
                synth.open();

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

                LOG.info("Loaded soundfont: {} into synth: {}",
                        soundbank.getName(), synth.getDeviceInfo().getName());

                return synth;
            } catch (Exception e) {
                lastError = e;

                try {
                    synth.close();
                } catch (Exception ignored) {}
            }
        }

        //noinspection ConstantValue -- need for NullAway
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
                LOG.warn("MidiSystem could not parse bundled soundfont");
                return null;
            }

            return soundbank;
        } catch (FileNotFoundException e) {
            LOG.warn("Bundled soundfont not found: {}", SOUNDFONT_RESOURCE);
        } catch (InvalidMidiDataException | IOException e) {
            LOG.warn("Failed to load bundled soundfont", e);
        }

        return null;
    }

    private static File extractSoundfontToTempFile(String resourcePath)
            throws IOException, FileNotFoundException {
        try (var in = MidiController.class.getResourceAsStream(resourcePath)) {
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

            for (var ch = 0; ch < 16; ch++) {
                initChannel(receiver, ch, 100, 64, 110, 35, 10);
            }
        } catch (InvalidMidiDataException e) {
            LOG.warn("Failed to initialize MIDI channels", e);
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

    /**
     * Sets the playback volume on all MIDI channels.
     *
     * @param percent volume percentage in the range 50–100,
     *                linearly scaled to MIDI CC7 values ~64–127
     */
    public static void setPlaybackVolume(int percent) {
        if (midiReceiver == null) {
            return;
        }

        var midiValue = Math.round(Math.clamp(percent, 50, 100) / 100f * 127);

        try {
            for (var ch = 0; ch < 16; ch++) {
                cc(midiReceiver, ch, 7, midiValue);
            }
        } catch (InvalidMidiDataException e) {
            LOG.warn("Failed to set playback volume", e);
        }
    }

    /**
     * Sends a Program Change on channel 0 so the correct instrument sounds
     * even when the sequencer resumes past the Program Change event at tick 0.
     *
     * @param program MIDI program number (0–127)
     */
    public static void setPlaybackInstrument(int program) {
        if (midiReceiver == null) {
            return;
        }

        try {
            var msg = new ShortMessage();
            msg.setMessage(ShortMessage.PROGRAM_CHANGE, 0, Math.clamp(program, 0, 127), 0);
            midiReceiver.send(msg, -1);
        } catch (InvalidMidiDataException e) {
            LOG.warn("Failed to set playback instrument", e);
        }
    }

    // Close MIDI resources so other applications can use them
    static void closeMidi() {
        if (closed) return;
        closed = true;

        if (midiReceiver != null) {
            midiReceiver.close();
        }

        if (sequencer != null) {
            sequencer.close();
        }

        if (synthesizer != null) {
            synthesizer.close();
        }

        LOG.info("MIDI resources released");
    }

    public static boolean isPlaying() {
        return ((sequencer != null) && sequencer.isRunning());
    }
}
