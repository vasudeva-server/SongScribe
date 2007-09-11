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
import javax.sound.midi.Sequencer;

import org.springframework.lang.Nullable;

import songscribe.ui.Constants;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.message.MessageCenter;

public final class PlaybackController {

    public enum PlaybackState {
        PLAYING,
        PAUSED,
        STOPPED,
    }

    private static PlaybackState state = PlaybackState.STOPPED;
    private static final int MINIMUM_PLAYBACK_TEMPO = 20;
    private static final int MAXIMUM_PLAYBACK_TEMPO = 180;

    private static final int PLAYBACK_TEMPO_STEP = 20;

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

    private PlaybackController() {}

    public static PlaybackState getState() {
        return state;
    }

    public static void playbackDidStart() {
        state = PlaybackState.PLAYING;
        MessageCenter.post(new PlaybackStateChangedMessage(state));
    }

    public static void playbackDidPause() {
        state = PlaybackState.PAUSED;
        stopSequencer();
        MessageCenter.post(new PlaybackStateChangedMessage(state));
    }

    public static void playbackDidStop() {
        state = PlaybackState.STOPPED;
        stopSequencer();
        MessageCenter.post(new PlaybackStateChangedMessage(state));
    }

    private static void stopSequencer() {
        if (MidiController.sequencer != null) {
            MidiController.sequencer.stop();
        }
    }

    public static void togglePlayPause() {
        if (state != PlaybackState.PLAYING) {
            play(null);
        } else {
            playbackDidPause();
        }
    }

    public static void play(@Nullable Score.NoteSelection selection) {
        var mainFrame = MainFrame.getInstance();
        var sequencer = MidiController.sequencer;

        try {
            if (sequencer == null) {
                return;
            }

            var score = mainFrame.getScore();
            Score.NoteSelection noteSelection;

            if (selection != null) {
                noteSelection = selection;
            } else {
                noteSelection = score.getSelection();
            }

            setSequenceToPlayFromSelection(noteSelection, score, sequencer);
            setLoopSequence(noteSelection, mainFrame, sequencer);
            playbackDidStart();
            sequencer.start();
        } catch (InvalidMidiDataException e1) {
            mainFrame.showErrorMessage(
                "Could not play back the song because of an unexpected error."
            );
        }
    }

    public static void stop() {
        STOP_ACTION.perform(null);
    }

    private static void setLoopSequence(
        Score.NoteSelection noteSelection,
        MainFrame mainFrame,
        Sequencer sequencer
    ) {
        var loopPlayback =
            ((noteSelection == null) ||
                (noteSelection.begin() != noteSelection.end())) &&
            mainFrame
                .getProperties()
                .getProperty(Constants.LOOP_PLAYBACK_PROP)
                .equals(Constants.TRUE_VALUE);

        // If a single note is selected, do not loop playback

        sequencer.setLoopCount(loopPlayback ? Sequencer.LOOP_CONTINUOUSLY : 0);
    }

    private static void setSequenceToPlayFromSelection(
        Score.NoteSelection noteSelection,
        Score score,
        Sequencer sequencer
    ) throws InvalidMidiDataException {
        var sequence = (noteSelection == null)
            ? score.getSequence()
            : score.getSelectedSequence(
                noteSelection.line(),
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
