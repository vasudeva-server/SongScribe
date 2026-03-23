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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.midi.MidiSequenceBuilder;
import songscribe.midi.PlaybackSettings;
import songscribe.music.Composition;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.Score;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.selection.ElementSelection;

public final class PlaybackController {

    public enum PlaybackState {
        PLAYING,
        PAUSED,
        STOPPED,
    }

    // The number of pulses per quarter note (ticks per beat), used to calculate the duration of notes
    // when playing back the score or generating a MIDI file.
    public static final int PPQ = 96;

    // The MIDI velocity values for normal and accented notes
    public static final int NOTE_VELOCITY = 100;
    public static final int ACCENTED_NOTE_VELOCITY = 127;

    private static final Logger LOG = LoggerFactory.getLogger(PlaybackController.class);

    @Nullable
    private static Score registeredScore;

    private static PlaybackState state = PlaybackState.STOPPED;
    private static int previousPlayingLine = -1;
    private static long pausedTickPosition = 0;

    private static int instrument = 0;
    private static int tempoChangePercent = 100;
    private static int noteDurationPercent = 100;
    private static boolean playWithRepeats = false;

    @Nullable
    private static ElementSelection activeSelection;

    public static final PlayPauseAction PLAY_PAUSE_ACTION =
        PlayPauseAction.createAction();

    public static final RewindAction REWIND_ACTION = RewindAction.createAction();

    public static final PlayWithRepeatsAction PLAY_WITH_REPEATS_ACTION =
        PlayWithRepeatsAction.createAction();

    public static final LoopPlaybackAction LOOP_PLAYBACK_ACTION =
        LoopPlaybackAction.createAction();

    private PlaybackController() {
    }

    public static void register(Score score) {
        registeredScore = score;
    }

    public static PlaybackState getState() {
        return state;
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
            stop();
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
            var line = score.getComposition().getLine(lineIndex);
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

    public static void playbackDidPause() {
        state = PlaybackState.PAUSED;
        pausedTickPosition = MidiController.sequencer != null ? MidiController.sequencer.getTickPosition() : 0;
        stopSequencer();
        MessageCenter.post(new PlaybackStateDidChangeNotification(state));
    }

    public static void playbackDidStop() {
        state = PlaybackState.STOPPED;
        activeSelection = null;
        pausedTickPosition = 0;
        stopSequencer();
        clearPlayingHighlight();

        MessageCenter.post(new PlaybackStateDidChangeNotification(state));
    }

    public static void rewindToBeginning() {
        if (state == PlaybackState.PLAYING) {
            clearPlayingHighlight();

            if (MidiController.sequencer != null) {
                MidiController.sequencer.setTickPosition(0);
            }
        } else if (state == PlaybackState.PAUSED) {
            pausedTickPosition = 0;
            updatePlayingNote(0, 0);
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

    private static void stopSequencer() {
        if (MidiController.sequencer != null) {
            MidiController.sequencer.stop();
        }
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

    public static javax.sound.midi.Sequence buildSequence(Composition composition)
        throws InvalidMidiDataException {
        return new MidiSequenceBuilder(composition, getPlaybackSettings()).buildFullSequence();
    }

    public static javax.sound.midi.Sequence buildSelectionSequence(
        Composition composition,
        int lineIndex,
        int startNote,
        int endNote
    ) throws InvalidMidiDataException {
        return new MidiSequenceBuilder(composition, getPlaybackSettings())
            .buildSelectionSequence(lineIndex, startNote, endNote);
    }

    public static void togglePlayPause() {
        if (state == PlaybackState.PLAYING) {
            playbackDidPause();
        } else if (state == PlaybackState.PAUSED) {
            resume();
        } else {
            play(null);
        }
    }

    private static void resume() {
        var sequencer = MidiController.sequencer;

        if (sequencer == null || registeredScore == null) {
            return;
        }

        try {
            var sequence = buildSequenceForSelection(
                registeredScore.getComposition(), activeSelection);
            sequencer.setSequence(sequence);

            if (pausedTickPosition >= sequencer.getTickLength()) {
                pausedTickPosition = 0;
                OptionDialogs.showWarningMessage(
                    null,
                    Strings.get(Strings.DIALOG_TITLE_RESUME_ERROR),
                    Strings.get(Strings.ERROR_PLAYBACK_RESUME_PAST_END)
                );
                return;
            }

            sequencer.setTickPosition(pausedTickPosition);
            MidiController.reinitChannels();
            MidiController.setPlaybackInstrument(instrument);
            applyVolumeFromPrefs();
            playbackDidStart();
            sequencer.start();
        } catch (InvalidMidiDataException e) {
            LOG.error("Failed to rebuild sequence on resume", e);
            playbackDidStop();
        }
    }

    public static void play(@Nullable ElementSelection selection) {
        var sequencer = MidiController.sequencer;

        try {
            if (sequencer == null) {
                return;
            }

            var score = registeredScore;

            if (score == null) {
                return;
            }

            ElementSelection noteSelection;

            if (selection != null) {
                noteSelection = selection;
            } else {
                noteSelection = score.getSelection();
            }

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
                Strings.get(Strings.DIALOG_TITLE_PLAYBACK_ERROR),
                Strings.get(Strings.ERROR_PLAYBACK_UNEXPECTED)
            );
        }
    }

    public static void stop() {
        REWIND_ACTION.perform(null);
    }

    public static void applyVolumeFromPrefs() {
        MidiController.setPlaybackVolume(Prefs.getInstance().getInt(PrefsKey.PLAYBACK_VOLUME));
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
            sequencer.stop();

            // Spin until the sequencer's internal thread is fully stopped.
            // sequencer.stop() is asynchronous — without this gate,
            // setSequence()/start() can race with the PlayThread that is
            // still winding down, causing missed note-offs or silent playback.
            // The wait is typically < 1 ms so EDT impact is negligible.
            while (sequencer.isRunning()) {
                Thread.onSpinWait();
            }

            var composition = registeredScore.getComposition();
            var sequence = buildSequenceForSelection(composition, activeSelection);
            sequencer.setSequence(sequence);
            sequencer.setTickPosition(Math.min(savedTick, sequencer.getTickLength()));

            // Skip reinitChannels() here — the GM System On reset invalidates the
            // SoftSynthesizer's receiver while the sequencer thread is still sending
            // notes-off. Just restore the instrument and volume directly.
            MidiController.setPlaybackInstrument(instrument);
            applyVolumeFromPrefs();
            sequencer.start();
        } catch (InvalidMidiDataException e) {
            LOG.error("Failed to rebuild sequence during playback", e);
            playbackDidStop();
        }
    }

    private static void setLoopSequence(
        @Nullable ElementSelection noteSelection,
        Sequencer sequencer
    ) {
        var loopPlayback =
            ((noteSelection == null) ||
                (noteSelection.begin() != noteSelection.end())) &&
                Prefs.getInstance().getBoolean(PrefsKey.LOOP_PLAYBACK);

        // If a single note is selected, do not loop playback

        sequencer.setLoopCount(loopPlayback ? Sequencer.LOOP_CONTINUOUSLY : 0);
    }

    private static Sequence buildSequenceForSelection(
        Composition composition,
        @Nullable ElementSelection selection
    ) throws InvalidMidiDataException {
        if (selection == null) {
            return buildSequence(composition);
        }

        return buildSelectionSequence(
            composition,
            composition.getLines().indexOf(selection.line()),
            selection.begin(),
            selection.end()
        );
    }

    private static void setSequenceToPlayFromSelection(
        @Nullable ElementSelection noteSelection,
        Score score,
        Sequencer sequencer
    ) throws InvalidMidiDataException {
        var sequence = buildSequenceForSelection(
            score.getComposition(), noteSelection
        );

        //noinspection ObjectEquality
        if (
            (sequencer.getTickPosition() >= sequencer.getTickLength()) ||
                (sequence != sequencer.getSequence())
        ) {
            sequencer.setTickPosition(0);

            //noinspection ObjectEquality
            if (sequence != sequencer.getSequence()) {
                sequencer.setSequence(sequence);
            }
        }
    }
}
