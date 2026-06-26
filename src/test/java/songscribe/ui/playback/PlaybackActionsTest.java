/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.playback;

import module java.desktop;

import java.awt.event.ActionEvent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleLoopPlaybackCommand;
import songscribe.message.command.TogglePlayWithRepeatsCommand;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.ui.action.UIAction;
import songscribe.ui.playback.PlaybackController.PlaybackState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlayPauseAction, RewindAction, LoopPlaybackAction, and PlayWithRepeatsAction.
 */
class PlaybackActionsTest extends MainFrameMockTest {

    private MockedStatic<Prefs> prefsMock;

    @BeforeEach
    void setUpPrefs() {
        // SelectableUIAction constructors read Prefs — stub to avoid real prefs I/O
        prefsMock = mockStatic(Prefs.class);
    }

    @AfterEach
    void tearDownPrefs() {
        prefsMock.close();
        // Reset PlaybackController static state shared across test classes
        PlaybackController.setState(PlaybackState.STOPPED);
    }

    // -------------------------------------------------------------------------
    // PlayPauseAction
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PlayPause {

        /**
         * Row 45 — actionPerformed toggles the action label/icon then delegates
         * to PlaybackController.togglePlayPause().
         */
        @Test
        void testActionPerformedTogglesNameAndCallsTogglePlayPause() {
            var action = PlayPauseAction.createAction(mainFrame());
            var playName = Strings.get(Strings.ACTION_PLAY_PLAY);
            var pauseName = Strings.get(Strings.ACTION_PLAY_PAUSE);

            // Initial state: action has the play name
            assertThat(action.getName()).isEqualTo(playName);

            try (var playbackMock = mockStatic(PlaybackController.class)) {
                action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, null));

                // toggleAction() was called → name switched to pause
                assertThat(action.getName()).isEqualTo(pauseName);

                // PlaybackController.togglePlayPause() must have been called
                playbackMock.verify(PlaybackController::togglePlayPause);
            }
        }

        /**
         * Row 46 — playbackStateDidChange(STOPPED) resets the action label to the
         * play name regardless of the current label.
         */
        @Test
        void testPlaybackStateDidChangeSwitchesToPlayNameOnStopped() {
            var action = PlayPauseAction.createAction(mainFrame());
            var playName = Strings.get(Strings.ACTION_PLAY_PLAY);
            var pauseName = Strings.get(Strings.ACTION_PLAY_PAUSE);

            // Manually switch to pause state so we can observe the revert
            try (var playbackMock = mockStatic(PlaybackController.class)) {
                action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, null));
            }

            assertThat(action.getName()).isEqualTo(pauseName);

            // Now post a STOPPED notification — the action should revert to play
            action.playbackStateDidChange(
                new PlaybackStateDidChangeNotification(PlaybackState.STOPPED)
            );

            assertThat(action.getName()).isEqualTo(playName);
        }

        /**
         * Row 47 — toggleAction is idempotent over a round-trip: calling it twice
         * restores the original name.
         */
        @Test
        void testToggleActionRoundTripRestoresOriginalName() {
            var action = PlayPauseAction.createAction(mainFrame());
            var originalName = action.getName();

            // Two toggles must return to the original state
            action.toggleAction();
            action.toggleAction();

            assertThat(action.getName()).isEqualTo(originalName);
        }

        /**
         * Row 48 — PlayPauseAction does NOT carry DISABLE_WHEN_PLAYING, so it
         * remains enabled while playback is in the PLAYING state. (The existing
         * audit test checks DISABLE_WHEN_EDITING_TEXT but not the absence of
         * DISABLE_WHEN_PLAYING.)
         */
        @Test
        void testActionRemainsEnabledDuringPlaybackBecauseDisableWhenPlayingFlagIsAbsent() {
            var action = PlayPauseAction.createAction(mainFrame());

            // The flag must NOT be set — that is the design intent
            assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING))
                .as("PlayPauseAction must not carry DISABLE_WHEN_PLAYING so it acts as the pause button")
                .isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // RewindAction
    // -------------------------------------------------------------------------

    /**
     * Row 49 — actionPerformed delegates directly to PlaybackController.rewindToBeginning().
     */
    @Test
    void testActionPerformedCallsRewindToBeginning() {
        var action = RewindAction.createAction(mainFrame());

        try (var playbackMock = mockStatic(PlaybackController.class)) {
            action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, null));

            playbackMock.verify(PlaybackController::rewindToBeginning);
        }
    }

    // -------------------------------------------------------------------------
    // LoopPlaybackAction
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LoopPlayback {

        /**
         * Row 50 — actionPerformed posts a ToggleLoopPlaybackCommand whose
         * isSelected() matches the action's selection state at the time of the call.
         */
        @Test
        void testActionPerformedPostsToggleLoopPlaybackCommandWithCorrectState() {
            var action = LoopPlaybackAction.createAction(mainFrame());
            // Explicitly set the selected state so the test is not order-sensitive
            action.setSelected(true);

            try (var messageMock = mockStatic(MessageCenter.class)) {
                action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, null));

                var captor = ArgumentCaptor.forClass(ToggleLoopPlaybackCommand.class);
                messageMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().isSelected()).isTrue();
            }
        }

        @Test
        void testActionPerformedPostsFalseWhenDeselected() {
            var action = LoopPlaybackAction.createAction(mainFrame());
            action.setSelected(false);

            try (var messageMock = mockStatic(MessageCenter.class)) {
                action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, null));

                var captor = ArgumentCaptor.forClass(ToggleLoopPlaybackCommand.class);
                messageMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().isSelected()).isFalse();
            }
        }
    }

    // -------------------------------------------------------------------------
    // PlayWithRepeatsAction
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PlayWithRepeats {

        /**
         * Row 51 — actionPerformed posts a TogglePlayWithRepeatsCommand whose
         * isSelected() matches the action's selection state at the time of the call.
         */
        @Test
        void testActionPerformedPostsTogglePlayWithRepeatsCommandWithCorrectState() {
            var action = PlayWithRepeatsAction.createAction(mainFrame());
            action.setSelected(true);

            try (var messageMock = mockStatic(MessageCenter.class)) {
                action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, null));

                var captor = ArgumentCaptor.forClass(TogglePlayWithRepeatsCommand.class);
                messageMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().isSelected()).isTrue();
            }
        }

        @Test
        void testActionPerformedPostsFalseWhenDeselected() {
            var action = PlayWithRepeatsAction.createAction(mainFrame());
            action.setSelected(false);

            try (var messageMock = mockStatic(MessageCenter.class)) {
                action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, null));

                var captor = ArgumentCaptor.forClass(TogglePlayWithRepeatsCommand.class);
                messageMock.verify(() -> MessageCenter.post(captor.capture()));
                assertThat(captor.getValue().isSelected()).isFalse();
            }
        }
    }

    // -------------------------------------------------------------------------
    // DISABLE_WHEN_MIDI_UNAVAILABLE flag
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DisableWhenMidiUnavailable {

        @BeforeEach
        void setUpScoreView() {
            // PlayPauseAction has DISABLE_WHEN_SONG_EMPTY, so requires the song to be non-empty
            var mockSong = mock(Song.class);
            when(mockSong.isEmpty()).thenReturn(false);
            when(mockEnv().score().isInitialized()).thenReturn(true);
            when(mockEnv().score().getSong()).thenReturn(mockSong);
        }

        @AfterEach
        void resetSequencer() {
            MidiController.sequencer = null;
        }

        /**
         * When sequencer is null (MIDI unavailable) and the action carries
         * DISABLE_WHEN_MIDI_UNAVAILABLE, updateEnabledState() must disable the action.
         */
        @Test
        void testActionDisabledWhenSequencerIsNull() {
            MidiController.sequencer = null;

            PlaybackController.PLAY_PAUSE_ACTION.updateEnabledState();

            assertThat(PlaybackController.PLAY_PAUSE_ACTION.isEnabled())
                .as("PLAY_PAUSE_ACTION must be disabled when MIDI is unavailable")
                .isFalse();
        }

        /**
         * When sequencer is non-null (MIDI available), updateEnabledState() must enable
         * the action (all other conditions satisfied by the mock score view setup).
         */
        @Test
        void testActionEnabledWhenSequencerIsSet() {
            MidiController.sequencer = mock(Sequencer.class);

            PlaybackController.PLAY_PAUSE_ACTION.updateEnabledState();

            assertThat(PlaybackController.PLAY_PAUSE_ACTION.isEnabled())
                .as("PLAY_PAUSE_ACTION must be enabled when MIDI is available")
                .isTrue();
        }
    }
}
