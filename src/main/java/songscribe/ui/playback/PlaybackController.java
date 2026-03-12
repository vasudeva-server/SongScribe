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

import java.util.ArrayList;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.Sequencer;

import org.jetbrains.annotations.Nullable;

import songscribe.Strings;
import songscribe.midi.MidiSequenceBuilder;
import songscribe.midi.PlaybackSettings;
import songscribe.music.Composition;
import songscribe.prefs.Prefs;
import songscribe.ui.Dialogs;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.message.MessageCenter;
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

    private static PlaybackState state = PlaybackState.STOPPED;
    private static int previousPlayingLine = -1;
    private static int previousPlayingNote = -1;

    private static final int MINIMUM_PLAYBACK_TEMPO = 20;
    private static final int MAXIMUM_PLAYBACK_TEMPO = 180;

    private static final int PLAYBACK_TEMPO_STEP = 20;

    private static int instrument = 0;
    private static int tempoChangePercent = 100;
    private static int noteDurationPercent = 100;
    private static boolean colorizeNotes = false;
    private static boolean playWithRepeats = false;

    public static final ArrayList<TempoChangeAction> PLAYBACK_TEMPO_ACTIONS =
        new ArrayList<>();

    static {
        for (
            var i = MINIMUM_PLAYBACK_TEMPO;
            i <= MAXIMUM_PLAYBACK_TEMPO;
            i += PLAYBACK_TEMPO_STEP
        ) {
            PLAYBACK_TEMPO_ACTIONS.add(new TempoChangeAction(i));
        }
    }

    public static final PlayPauseAction PLAY_PAUSE_ACTION =
        new PlayPauseAction();

    public static final StopAction STOP_ACTION = new StopAction();

    public static final PlayWithRepeatsAction PLAY_WITH_REPEATS_ACTION =
        new PlayWithRepeatsAction();

    public static final LoopPlaybackAction LOOP_PLAYBACK_ACTION =
        new LoopPlaybackAction();

    private PlaybackController() {
    }

    public static PlaybackState getState() {
        return state;
    }

    public static void playbackDidStart() {
        state = PlaybackState.PLAYING;
        registerMetaListener();
        MessageCenter.post(new PlaybackStateChangedMessage(state));
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
        // Clear previous line if different
        if (previousPlayingLine != -1 && previousPlayingLine != lineIndex) {
            var prevLineComponent = getLineComponent(previousPlayingLine);
            if (prevLineComponent != null) {
                prevLineComponent.setPlayingNoteIndex(-1);
            }
        }

        // Set new playing note
        var lineComponent = getLineComponent(lineIndex);
        if (lineComponent != null) {
            lineComponent.setPlayingNoteIndex(noteIndex);
        }

        previousPlayingLine = lineIndex;
        previousPlayingNote = noteIndex;
    }

    private static LineComponent getLineComponent(int lineIndex) {
        var mainFrame = MainFrame.getInstance();
        if (mainFrame == null) return null;

        var score = mainFrame.getScore();
        if (score == null) return null;

        return score.getLineComponent(lineIndex);
    }

    public static void playbackDidPause() {
        state = PlaybackState.PAUSED;
        stopSequencer();
        MessageCenter.post(new PlaybackStateChangedMessage(state));
    }

    public static void playbackDidStop() {
        state = PlaybackState.STOPPED;
        stopSequencer();
        clearPlayingHighlight();

        MessageCenter.post(new PlaybackStateChangedMessage(state));
    }

    private static void clearPlayingHighlight() {
        if (previousPlayingLine != -1) {
            var lineComponent = getLineComponent(previousPlayingLine);
            if (lineComponent != null) {
                lineComponent.setPlayingNoteIndex(-1);
            }
        }
        previousPlayingLine = -1;
        previousPlayingNote = -1;
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

    public static void setColorizeNotes(boolean value) {
        colorizeNotes = value;
    }

    public static void setPlayWithRepeats(boolean value) {
        playWithRepeats = value;
    }

    public static PlaybackSettings getPlaybackSettings() {
        return new PlaybackSettings(
            instrument,
            tempoChangePercent,
            noteDurationPercent,
            colorizeNotes,
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
        if (state != PlaybackState.PLAYING) {
            play(null);
        } else {
            playbackDidPause();
        }
    }

    public static void play(@Nullable ElementSelection selection) {
        var mainFrame = MainFrame.getInstance();
        var sequencer = MidiController.sequencer;

        try {
            if (sequencer == null) {
                return;
            }

            var score = mainFrame.getScore();
            ElementSelection noteSelection;

            if (selection != null) {
                noteSelection = selection;
            } else {
                noteSelection = score.getSelection();
            }

            setSequenceToPlayFromSelection(noteSelection, score, sequencer);

            setLoopSequence(noteSelection, mainFrame, sequencer);
            MidiController.reinitChannels();
            playbackDidStart();
            sequencer.start();
        } catch (InvalidMidiDataException e1) {
            Dialogs.showErrorMessage(
                null,
                Strings.get(Strings.DIALOG_TITLE_PLAYBACK_ERROR),
                Strings.get(Strings.ERROR_PLAYBACK_UNEXPECTED)
            );
        }
    }

    public static void stop() {
        STOP_ACTION.perform(null);
    }

    private static void setLoopSequence(
        ElementSelection noteSelection,
        MainFrame mainFrame,
        Sequencer sequencer
    ) {
        var loopPlayback =
            ((noteSelection == null) ||
                (noteSelection.begin() != noteSelection.end())) &&
                Prefs.getInstance().getBoolean("loopPlayback");

        // If a single note is selected, do not loop playback

        sequencer.setLoopCount(loopPlayback ? Sequencer.LOOP_CONTINUOUSLY : 0);
    }

    private static void setSequenceToPlayFromSelection(
        ElementSelection noteSelection,
        Score score,
        Sequencer sequencer
    ) throws InvalidMidiDataException {
        var composition = score.getComposition();
        var sequence = (noteSelection == null)
            ? buildSequence(composition)
            : buildSelectionSequence(
            composition,
            score.getComposition().getLines().indexOf(noteSelection.line()),
            noteSelection.begin(),
            noteSelection.end()
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
