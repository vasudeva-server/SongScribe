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

import java.awt.EventQueue;
import java.util.concurrent.TimeUnit;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.midi.MidiSequenceBuilder;
import songscribe.midi.PlaybackSettings;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.renderer.LineInvariants;
import songscribe.ui.selection.ElementSelection;

/**
 * <h2>Lifecycle</h2>
 * {@link #initialize(MainFrame)} constructs the four playback action constants against one
 * owner frame; each subscribes itself to the message bus and lives for the process. A caller
 * that replaces them must first have retired the bus they joined.
 */
// The playback action constants are @NonNull but populated lazily by initialize() (which
// needs the MainFrame, unavailable at class-load), mirroring the Actions holder. NullAway.Init
// suppresses the "uninitialized field" check; keeping them non-null avoids poisoning call sites.
@SuppressWarnings({
    "NullAway.Init",
    "StaticVariableUsedBeforeInitialization",
    "StaticVariableMayNotBeInitialized",
    "StaticNonFinalField"
})
public final class PlaybackController {

    public static final int NOTE_VELOCITY = 100;
    public static final int ACCENTED_NOTE_VELOCITY = 127;

    // Matches the "Soft" stop on the Prefs volume slider.
    private static final double SELECTED_NOTE_VOLUME_FRACTION = 0.75;
    public static final int SELECTED_NOTE_VELOCITY =
        (int) Math.round(NOTE_VELOCITY * SELECTED_NOTE_VOLUME_FRACTION);

    // Bound on the spin in awaitSequencerStopped(), generous relative to the
    // sub-millisecond wait actually observed.
    private static final long SEQUENCER_STOP_TIMEOUT_MILLIS = 250;

    private static final Logger LOG = LoggerFactory.getLogger(PlaybackController.class);

    @Nullable
    private static ScoreView registeredScore = null;

    private static PlaybackState state = PlaybackState.STOPPED;
    private static @Nullable Integer previousPlayingLine;

    private static int instrument = 0;
    private static int tempoChangePercent = 100;
    private static int noteDurationPercent = 100;
    private static boolean playWithRepeats = false;

    @Nullable
    private static ElementSelection activeSelection = null;

    public static PlayStopAction PLAY_STOP_ACTION;
    public static RewindAction REWIND_ACTION;
    public static PlayWithRepeatsAction PLAY_WITH_REPEATS_ACTION;
    public static LoopPlaybackAction LOOP_PLAYBACK_ACTION;

    private PlaybackController() {
    }

    /**
     * Called once per process, at the top of {@link MainFrame#initFrame()}, before any constant
     * in this class is first referenced. The constants this replaces keep their bus
     * registrations, so a second call must first retire the bus they joined.
     */
    public static void initialize(MainFrame mainFrame) {
        PLAY_STOP_ACTION = PlayStopAction.createAction(mainFrame);
        REWIND_ACTION = RewindAction.createAction(mainFrame);
        PLAY_WITH_REPEATS_ACTION = PlayWithRepeatsAction.createAction(mainFrame);
        LOOP_PLAYBACK_ACTION = LoopPlaybackAction.createAction(mainFrame);
    }

    public static void register(ScoreView score) {
        registeredScore = score;
    }

    public static PlaybackState getState() {
        return state;
    }

    public static void setState(PlaybackState newState) {
        state = newState;
    }

    @Nullable
    public static ScoreView getRegisteredScore() {
        return registeredScore;
    }

    public static void setRegisteredScore(@Nullable ScoreView score) {
        registeredScore = score;
    }

    @Nullable
    public static ElementSelection getActiveSelection() {
        return activeSelection;
    }

    public static void setActiveSelection(@Nullable ElementSelection selection) {
        activeSelection = selection;
    }

    public static boolean isPlaying() {
        return state == PlaybackState.PLAYING;
    }

    public static void playbackDidStart() {
        state = PlaybackState.PLAYING;
        registerMetaListener();
        MessageCenter.post(new PlaybackStateDidChangeNotification(state));
    }

    private static void registerMetaListener() {
        if (MidiController.sequencer != null) {
            MidiController.sequencer.addMetaEventListener(PlaybackController::handleMetaMessage);
        }
    }

    private static void handleMetaMessage(MetaMessage meta) {
        if (meta.getType() == MidiMetaMessageTypes.SEQUENCE_NUMBER) {
            var data = meta.getData();
            var lineIndex = (data[0] << 8) | data[1];
            var noteIndex = (data[2] << 8) | data[3];
            updatePlayingNote(lineIndex, noteIndex);
        } else if (meta.getType() == MidiMetaMessageTypes.END_OF_TRACK) {
            // The MIDI system calls this back on the sequencer's own thread. stop() posts
            // synchronously, so its subscribers — including the Play/Stop button, which
            // derives its whole label from that notification — would otherwise mutate
            // Swing state off the EDT.
            EventQueue.invokeLater(PlaybackController::stop);
        }
    }

    private static void updatePlayingNote(int lineIndex, int noteIndex) {
        var score = registeredScore;
        if (score == null) {
            return;
        }

        if (previousPlayingLine != null && previousPlayingLine != lineIndex) {
            var prevLineComponent = score.getLineComponent(previousPlayingLine);
            if (prevLineComponent != null) {
                prevLineComponent.setPlaying(null);
            }
        }

        var lineComponent = score.getLineComponent(lineIndex);
        if (lineComponent != null) {
            var line = score.getSong().getLine(lineIndex);
            lineComponent.setPlaying(new LineInvariants.Playing(noteIndex, line.precedingGraceNoteIndex(noteIndex)));
        }

        previousPlayingLine = lineIndex;
    }

    @Nullable
    private static LineComponent getLineComponent(int lineIndex) {
        if (registeredScore == null) {
            return null;
        }

        return registeredScore.getLineComponent(lineIndex);
    }

    /** Drops the music selection, so the next {@link #play} starts at the beginning of the song. */
    public static void rewindToBeginning() {
        // Already at the top with nothing selected, so there is nothing left to rewind.
        // This also absorbs the auto-repeat of the keyboard shortcut, which would
        // otherwise re-post the notification several times a second while the key is held.
        if (state == PlaybackState.REWOUND) {
            return;
        }

        endPlayback(PlaybackState.REWOUND);
    }

    /** The next Play starts at the new selection, not the top of the song. Posts nothing. */
    public static void selectionDidChange() {
        if (state == PlaybackState.REWOUND) {
            state = PlaybackState.STOPPED;
        }
    }

    private static void clearPlayingHighlight() {
        if (previousPlayingLine != null) {
            var lineComponent = getLineComponent(previousPlayingLine);
            if (lineComponent != null) {
                lineComponent.setPlaying(null);
            }
        }
        previousPlayingLine = null;
    }

    /**
     * Does not wait for the asynchronous stop to settle; callers that drive the sequencer
     * straight afterwards want {@link #stopAndAwaitSequencer}.
     */
    private static void stopSequencer() {
        var sequencer = MidiController.sequencer;

        if (sequencer == null) {
            return;
        }

        sequencer.stop();
        sequencer.setTickPosition(0);
    }

    public static void setInstrument(int value) {
        instrument = value;
    }

    public static void setTempoChangePercent(int value) {
        tempoChangePercent = value;
    }

    public static void setNoteDurationPercent(int value) {
        noteDurationPercent = value;
    }

    public static void setPlayWithRepeats(boolean value) {
        playWithRepeats = value;
    }

    public static PlaybackSettings getPlaybackSettings() {
        return new PlaybackSettings(
            instrument,
            tempoChangePercent,
            noteDurationPercent,
            playWithRepeats
        );
    }

    public static void applySettings(PlaybackSettings settings) {
        instrument = settings.instrument();
        tempoChangePercent = settings.tempoChangePercent();
        noteDurationPercent = settings.noteDurationPercent();
        playWithRepeats = settings.playWithRepeats();
    }

    public static Sequence buildSequence(Song song)
        throws InvalidMidiDataException {
        return new MidiSequenceBuilder(song, getPlaybackSettings()).buildFullSequence();
    }

    public static void togglePlayStop() {
        if (state == PlaybackState.PLAYING) {
            stop();
        } else {
            play();
        }
    }

    /**
     * Starts playback at the beginning of the score's music selection, or at the beginning
     * of the song when nothing is selected — which is also what a rewind leaves behind.
     */
    public static void play() {
        var sequencer = MidiController.sequencer;

        try {
            if (sequencer == null) {
                return;
            }

            var score = registeredScore;

            if (score == null) {
                return;
            }

            var noteSelection = score.getSelection();
            activeSelection = noteSelection;
            setSequenceToPlayFromSelection(noteSelection, score, sequencer);

            setLoopSequence(noteSelection, sequencer);
            MidiController.reinitChannels();
            applyVolumeFromPrefs();
            playbackDidStart();
            sequencer.start();
        } catch (InvalidMidiDataException e1) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_PLAYBACK_ERROR,
                Strings.ERROR_PLAYBACK_UNEXPECTED
            );
        }
    }

    /**
     * Leaves the music selection alone so the next {@link #play} resumes at the beginning of
     * it.
     */
    public static void stop() {
        endPlayback(PlaybackState.STOPPED);
    }

    public static void stopAndAwaitSequencer() {
        stop();

        var sequencer = MidiController.sequencer;

        if (sequencer != null) {
            awaitSequencerStopped(sequencer);
        }
    }

    private static void endPlayback(PlaybackState newState) {
        state = newState;
        activeSelection = null;
        stopSequencer();
        clearPlayingHighlight();

        MessageCenter.post(new PlaybackStateDidChangeNotification(state));
    }

    public static void applyVolumeFromPrefs() {
        MidiController.setPlaybackVolume(Prefs.getChoice(PrefsKey.PLAYBACK_VOLUME, PlaybackVolume.class));
    }

    public static void applyPrefsDuringPlayback() {
        if (state != PlaybackState.PLAYING) {
            return;
        }

        var sequencer = MidiController.sequencer;

        if (sequencer == null || registeredScore == null) {
            return;
        }

        try {
            var savedTick = sequencer.getTickPosition();
            var song = registeredScore.getSong();
            var sequence = buildSequenceForSelection(song, activeSelection);
            reloadSequenceDuringPlayback(sequencer, sequence, savedTick);
        } catch (InvalidMidiDataException e) {
            LOG.error("Failed to rebuild sequence during playback", e);
            stop();
        }
    }

    private static void reloadSequenceDuringPlayback(
        Sequencer sequencer,
        Sequence sequence,
        long tickPosition
    ) throws InvalidMidiDataException {
        sequencer.stop();
        awaitSequencerStopped(sequencer);

        sequencer.setSequence(sequence);
        sequencer.setTickPosition(Math.min(tickPosition, sequencer.getTickLength()));

        // Re-derive the loop count for the selection the new sequence was built from,
        // which a fresh play() would have done. The selection is unchanged across a
        // preferences reload, so this is idempotent.
        setLoopSequence(activeSelection, sequencer);

        // Skip reinitChannels() here — the GM System On reset invalidates the
        // SoftSynthesizer's receiver while the sequencer thread is still sending
        // notes-off. Just restore the instrument and volume directly.
        MidiController.setPlaybackInstrument(instrument);
        applyVolumeFromPrefs();
        sequencer.start();
    }

    /**
     * {@link Sequencer#stop()} is asynchronous: without this gate, loading and starting a
     * sequence can race with the winding-down play thread, causing missed note-offs or silence.
     * The deadline only keeps a wedged sequencer from freezing the EDT; proceeding after it
     * reopens the race.
     */
    private static void awaitSequencerStopped(Sequencer sequencer) {
        var deadlineNanos =
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SEQUENCER_STOP_TIMEOUT_MILLIS);

        while (sequencer.isRunning()) {
            // Subtract rather than compare, so the check survives nanoTime's wraparound.
            if (System.nanoTime() - deadlineNanos >= 0) {
                LOG.warn(
                    "Sequencer still running {} ms after stop(); continuing anyway",
                    SEQUENCER_STOP_TIMEOUT_MILLIS
                );
                return;
            }

            Thread.onSpinWait();
        }
    }

    private static void setLoopSequence(
        @Nullable ElementSelection noteSelection,
        Sequencer sequencer
    ) {
        var loopPlayback =
            ((noteSelection == null) ||
                (noteSelection.range().size() > 1)) &&
                Prefs.getBoolean(PrefsKey.LOOP_PLAYBACK);

        sequencer.setLoopCount(loopPlayback ? Sequencer.LOOP_CONTINUOUSLY : 0);
    }

    private static Sequence buildSequenceForSelection(
        Song song,
        @Nullable ElementSelection selection
    ) throws InvalidMidiDataException {
        if (selection == null) {
            return buildSequence(song);
        }

        var lineIndex = song.getLines().indexOf(selection.line());

        return new MidiSequenceBuilder(song, getPlaybackSettings())
            .buildFromNoteToEnd(lineIndex, selection.range().begin());
    }

    private static void setSequenceToPlayFromSelection(
        @Nullable ElementSelection noteSelection,
        ScoreView score,
        Sequencer sequencer
    ) throws InvalidMidiDataException {
        var sequence = buildSequenceForSelection(
            score.getSong(), noteSelection
        );

        // Play always restarts at the beginning of the selection (or of the song when
        // there is none), so the sequence is loaded and rewound unconditionally.
        // buildSequenceForSelection() returns a freshly built Sequence every call, so
        // there is never an already-loaded instance worth reusing.
        sequencer.setSequence(sequence);
        sequencer.setTickPosition(0);
    }
}
