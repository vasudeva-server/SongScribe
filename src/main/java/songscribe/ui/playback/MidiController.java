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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Soundbank;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.SysexMessage;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.lifecycle.Shutdown;
import songscribe.ui.component.MainFrame;

@SuppressWarnings("StaticNonFinalField")
public final class MidiController {

    private static final Logger LOG = LoggerFactory.getLogger(MidiController.class);

    public static volatile @Nullable Sequencer sequencer = null;
    public static volatile @Nullable Receiver midiReceiver = null;
    public static volatile @Nullable Synthesizer synthesizer = null;
    private static boolean closed = false;

    static final long NO_TIMESTAMP = -1;

    private MidiController() {}

    private static final String SOUNDFONT_RESOURCE = "/soundfonts/FluidR3_GM.sf2";

    private static final int MIDI_CHANNEL_COUNT = 16;
    private static final int MIDI_DATA_MAX = 127;
    private static final int GENERAL_MIDI_BANK = 0;
    private static final int DEFAULT_VOLUME = 100;
    private static final int CENTER_PAN = 64;
    private static final int DEFAULT_EXPRESSION = 110;
    private static final int DEFAULT_REVERB_SEND = 35;
    private static final int DEFAULT_CHORUS_SEND = 10;
    private static final float FULL_PERCENT = 100f;

    // General MIDI System On, a universal non-real-time SysEx message from the MIDI 1.0 specification.
    private static final byte[] GENERAL_MIDI_SYSTEM_ON =
            {(byte) 0xF0, 0x7E, 0x7F, 0x09, 0x01, (byte) 0xF7};

    /**
     * The controller numbers of the MIDI 1.0 Control Change message, as General MIDI uses them.
     */
    private enum ControlChange {
        BANK_SELECT_MSB(0),
        BANK_SELECT_LSB(32),
        VOLUME(7),
        PAN(10),
        EXPRESSION(11),
        REVERB_SEND(91),
        CHORUS_SEND(93);

        private final int number;

        ControlChange(int number) {
            this.number = number;
        }
    }

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

    /** @return a latch that reaches zero when initialization is complete, in success or failure */
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

    /** Loading from a File is more reliable than from an InputStream. */
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

    private static void initChannels(Receiver receiver) {
        try {
            var gmOn = new SysexMessage();
            gmOn.setMessage(GENERAL_MIDI_SYSTEM_ON, GENERAL_MIDI_SYSTEM_ON.length);
            receiver.send(gmOn, NO_TIMESTAMP);

            for (var channel = 0; channel < MIDI_CHANNEL_COUNT; channel++) {
                initChannel(receiver, channel);
            }
        } catch (InvalidMidiDataException e) {
            LOG.warn("Failed to initialize MIDI channels", e);
        }
    }

    private static void initChannel(Receiver receiver, int channel)
            throws InvalidMidiDataException {
        cc(receiver, channel, ControlChange.BANK_SELECT_MSB, GENERAL_MIDI_BANK);
        cc(receiver, channel, ControlChange.BANK_SELECT_LSB, GENERAL_MIDI_BANK);
        cc(receiver, channel, ControlChange.VOLUME, DEFAULT_VOLUME);
        cc(receiver, channel, ControlChange.PAN, CENTER_PAN);
        cc(receiver, channel, ControlChange.EXPRESSION, DEFAULT_EXPRESSION);
        cc(receiver, channel, ControlChange.REVERB_SEND, DEFAULT_REVERB_SEND);
        cc(receiver, channel, ControlChange.CHORUS_SEND, DEFAULT_CHORUS_SEND);
    }

    private static void cc(
            Receiver receiver, int channel, ControlChange controller, int value
    ) throws InvalidMidiDataException {
        var msg = new ShortMessage();
        msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, controller.number, value);
        receiver.send(msg, NO_TIMESTAMP);
    }

    /**
     * Call before each playback to counteract the sequencer's
     * automatic CC 121 (Reset All Controllers) sent when it stops.
     */
    public static void reinitChannels() {
        if (midiReceiver != null) {
            initChannels(midiReceiver);
        }
    }

    public static void setPlaybackVolume(PlaybackVolume volume) {
        if (midiReceiver == null) {
            return;
        }

        var midiValue = Math.round(volume.percent() / FULL_PERCENT * MIDI_DATA_MAX);

        try {
            for (var channel = 0; channel < MIDI_CHANNEL_COUNT; channel++) {
                cc(midiReceiver, channel, ControlChange.VOLUME, midiValue);
            }
        } catch (InvalidMidiDataException e) {
            LOG.warn("Failed to set playback volume", e);
        }
    }

    /**
     * Sent so the correct instrument sounds even when the sequencer resumes past the Program
     * Change event at tick 0.
     */
    public static void setPlaybackInstrument(int program) {
        if (midiReceiver == null) {
            return;
        }

        try {
            var msg = new ShortMessage();
            msg.setMessage(ShortMessage.PROGRAM_CHANGE, 0, Math.clamp(program, 0, MIDI_DATA_MAX), 0);
            midiReceiver.send(msg, NO_TIMESTAMP);
        } catch (InvalidMidiDataException e) {
            LOG.warn("Failed to set playback instrument", e);
        }
    }

    static void closeMidi() {
        if (closed) {
            return;
        }

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
