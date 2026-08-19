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
import songscribe.ui.selection.ElementSelection;

/**
 * <h2>Lifecycle</h2>
 * {@link #initialize(MainFrame)} establishes the four playback action constants
 * against one owner frame; each subscribes itself to the message bus.
 * {@link #deinitialize()} removes them. Re-initialization is permitted and retires the
 * previous four first.
 *
 * <p>Playback state — the registered score, the transport state, the sequencer —
 * is established by {@link #register} and {@link #play}, not by
 * {@code initialize}, and is therefore not {@code deinitialize}'s to undo. Stopping
 * playback is {@link #stop()}.
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

    public enum PlaybackState {
        PLAYING,

        /**
         * Playback ended with the music selection left intact, so the next {@link #play}
         * resumes at the beginning of that selection.
         */
        STOPPED,

        /**
         * Playback ended and the music selection was dropped, so the next {@link #play}
         * starts at the beginning of the song. Distinct from {@link #STOPPED} only in
         * that the selection is gone — the score clears its own selection when it sees
         * this state, rather than being reached into from here.
         */
        REWOUND,
    }

    // The MIDI velocity values for normal and accented notes
    public static final int NOTE_VELOCITY = 100;
    public static final int ACCENTED_NOTE_VELOCITY = 127;

    // Fraction of NOTE_VELOCITY used when previewing a selected note — the "Soft" stop (75%) on the Prefs volume slider
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
    private static int previousPlayingLine = -1;

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

    // Tracks whether the four action constants above are live, so initialize() knows
    // whether a generation exists to retire and deinitialize() knows whether it has
    // anything to do. Null until initialize() is called; null again after deinitialize().
    @Nullable
    private static MainFrame mainFrame;

    private PlaybackController() {
    }

    /**
     * Populates the playback action constants using {@code mainFrame} as the owner, so the
     * actions no longer call {@link MainFrame#getInstance()} themselves.
     *
     * <p>Must be called once at the top of {@link MainFrame#initFrame()} — adjacent to
     * {@code Actions.initialize(this)} — before any constant in this class is first
     * referenced. Calling it again (e.g. in tests) retires the previous four via
     * {@link #deinitialize()} first, then replaces them with freshly constructed instances.
     */
    public static void initialize(MainFrame mainFrame) {
        deinitialize();

        PlaybackController.mainFrame = mainFrame;
        PLAY_STOP_ACTION = PlayStopAction.createAction(mainFrame);
        REWIND_ACTION = RewindAction.createAction(mainFrame);
        PLAY_WITH_REPEATS_ACTION = PlayWithRepeatsAction.createAction(mainFrame);
        LOOP_PLAYBACK_ACTION = LoopPlaybackAction.createAction(mainFrame);
    }

    /**
     * Retires the current four playback action constants and clears the owner. Idempotent:
     * calling it without a preceding {@link #initialize} is a no-op.
     */
    public static void deinitialize() {
        if (mainFrame == null) {
            return;
        }

        PLAY_STOP_ACTION.dispose();
        REWIND_ACTION.dispose();
        PLAY_WITH_REPEATS_ACTION.dispose();
        LOOP_PLAYBACK_ACTION.dispose();
        mainFrame = null;
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

    public static int getPreviousPlayingLine() {
        return previousPlayingLine;
    }

    public static void setPreviousPlayingLine(int line) {
        previousPlayingLine = line;
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

        // Clear previous line if different
        if (previousPlayingLine != -1 && previousPlayingLine != lineIndex) {
            var prevLineComponent = score.getLineComponent(previousPlayingLine);
            if (prevLineComponent != null) {
                prevLineComponent.setPlayingIndices(-1, -1);
            }
        }

        // Set new playing note and its preceding grace note (if any)
        var lineComponent = score.getLineComponent(lineIndex);
        if (lineComponent != null) {
            var line = score.getSong().getLine(lineIndex);
            lineComponent.setPlayingIndices(noteIndex, line.precedingGraceNoteIndex(noteIndex));
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

    /**
     * Ends playback and returns to the top of the song. Rewinding is {@link #stop} plus
     * dropping the music selection, so the next {@link #play} starts at the beginning of
     * the song rather than at the beginning of whatever was selected.
     */
    public static void rewindToBeginning() {
        // Already at the top with nothing selected, so there is nothing left to rewind.
        // This also absorbs the auto-repeat of the keyboard shortcut, which would
        // otherwise re-post the notification several times a second while the key is held.
        if (state == PlaybackState.REWOUND) {
            return;
        }

        endPlayback(PlaybackState.REWOUND);
    }

    /**
     * Reclassifies a rewind as an ordinary stop once the user selects music again: the
     * next Play should start at that new selection, not at the top of the song. Posts
     * nothing, because the two states are indistinguishable to every subscriber — they
     * differ only in what Play does next.
     */
    public static void selectionDidChange() {
        if (state == PlaybackState.REWOUND) {
            state = PlaybackState.STOPPED;
        }
    }

    private static void clearPlayingHighlight() {
        if (previousPlayingLine != -1) {
            var lineComponent = getLineComponent(previousPlayingLine);
            if (lineComponent != null) {
                lineComponent.setPlayingIndices(-1, -1);
            }
        }
        previousPlayingLine = -1;
    }

    /**
     * Halts the sequencer and parks it at the start. No wait for the asynchronous stop to
     * settle, unlike {@link #reloadSequenceDuringPlayback}: nothing reads the position
     * again until the next {@link #play}, which loads a fresh sequence and rewinds it
     * anyway. Callers that drive the sequencer themselves straight afterwards want
     * {@link #stopAndAwaitSequencer} instead.
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
     * Ends playback, leaving the music selection alone so the next {@link #play} resumes
     * at the beginning of it.
     */
    public static void stop() {
        endPlayback(PlaybackState.STOPPED);
    }

    /**
     * Ends playback and does not return until the sequencer has actually gone quiet, for
     * callers that immediately load and start a sequence of their own on it.
     */
    public static void stopAndAwaitSequencer() {
        stop();

        var sequencer = MidiController.sequencer;

        if (sequencer != null) {
            awaitSequencerStopped(sequencer);
        }
    }

    /**
     * The shared tail of {@link #stop} and {@link #rewindToBeginning} — the two differ
     * only in the state they land in, and hence in what the next {@link #play} starts from.
     */
    private static void endPlayback(PlaybackState newState) {
        state = newState;
        activeSelection = null;
        stopSequencer();
        clearPlayingHighlight();

        MessageCenter.post(new PlaybackStateDidChangeNotification(state));
    }

    public static void applyVolumeFromPrefs() {
        MidiController.setPlaybackVolume(Prefs.getInt(PrefsKey.PLAYBACK_VOLUME));
    }

    /**
     * Rebuilds and reloads the MIDI sequence during active playback so that
     * changes to tempo, note duration, or instrument take effect immediately.
     * Does nothing if playback is not in the PLAYING state.
     */
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

    /**
     * Loads {@code sequence} into an already-playing {@code sequencer} and resumes it at
     * {@code tickPosition}, without the channel reinitialization that a fresh {@link #play}
     * performs.
     */
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
     * Spins until the sequencer's internal thread is fully stopped.
     * <p>
     * {@link Sequencer#stop()} is asynchronous — without this gate, loading and starting a
     * sequence can race with the play thread that is still winding down, causing missed
     * note-offs or silent playback. The wait is typically under a millisecond, so the EDT
     * impact is negligible; the deadline only exists so a wedged sequencer degrades to a
     * warning instead of freezing the EDT forever. Proceeding after the deadline reopens
     * the race this guards against, which is the better of the two failures.
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
                (noteSelection.begin() != noteSelection.end())) &&
                Prefs.getBoolean(PrefsKey.LOOP_PLAYBACK);

        // If a single note is selected, do not loop playback

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
            .buildFromNoteToEnd(lineIndex, selection.begin());
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
