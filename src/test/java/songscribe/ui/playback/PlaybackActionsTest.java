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
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PlayStopAction, RewindAction, LoopPlaybackAction, and PlayWithRepeatsAction.
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
    // PlayStopAction
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PlayStop {

        /**
         * Row 45 — actionPerformed delegates to PlaybackController.togglePlayStop()
         * without moving the label itself.
         */
        @Test
        void testActionPerformedCallsTogglePlayStop() {
            var action = PlayStopAction.createAction(mainFrame());
            var playName = Strings.get(Strings.ACTION_PLAY_PLAY);

            // Initial state: action has the play name
            assertThat(action.getName()).isEqualTo(playName);

            try (var playbackMock = mockStatic(PlaybackController.class)) {
                action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, null));

                // PlaybackController.togglePlayStop() must have been called
                playbackMock.verify(PlaybackController::togglePlayStop);
            }
        }

        /**
         * Row 46 — playbackStateDidChange is the sole driver of the action's label:
         * PLAYING switches it to the stop name, and anything else switches it back
         * to the play name.
         * <p>
         * The icon and tooltip are asserted alongside the name because all three move
         * together; checking only the name would let a swapped glyph or a tooltip wired
         * to the wrong branch through, leaving a button that reads "Stop" but shows the
         * play triangle.
         */
        @Test
        void testPlaybackStateDidChangeDrivesActionNameIconAndTooltip() {
            var action = PlayStopAction.createAction(mainFrame());

            assertPlayAppearance(action);

            action.playbackStateDidChange(
                new PlaybackStateDidChangeNotification(PlaybackState.PLAYING)
            );

            assertThat(action.getName())
                .as("PLAYING must present the button as Stop")
                .isEqualTo(Strings.get(Strings.ACTION_PLAY_STOP));
            assertThat(action.getValue(UIAction.FONT_ICON_KEY))
                .isEqualTo(UIUtils.getTaggedString(PlayStopAction.STOP_ICON).text());
            assertThat(action.getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo(Strings.get(Strings.ACTION_PLAY_STOP_TOOLTIP));

            action.playbackStateDidChange(
                new PlaybackStateDidChangeNotification(PlaybackState.STOPPED)
            );

            assertPlayAppearance(action);
        }

        /**
         * A rewind ends playback, so it must present the button as Play — and never leave
         * it reading "Stop" with nothing playing.
         */
        @Test
        void testPlaybackStateDidChangePresentsPlayAfterRewind() {
            var action = PlayStopAction.createAction(mainFrame());

            action.playbackStateDidChange(
                new PlaybackStateDidChangeNotification(PlaybackState.PLAYING)
            );
            action.playbackStateDidChange(
                new PlaybackStateDidChangeNotification(PlaybackState.REWOUND)
            );

            assertPlayAppearance(action);
        }

        private void assertPlayAppearance(PlayStopAction action) {
            assertThat(action.getName())
                .as("the button must read Play whenever nothing is playing")
                .isEqualTo(Strings.get(Strings.ACTION_PLAY_PLAY));
            assertThat(action.getValue(UIAction.FONT_ICON_KEY))
                .as("the button must show the play glyph whenever nothing is playing")
                .isEqualTo(UIUtils.getTaggedString(PlayStopAction.PLAY_ICON).text());
            assertThat(action.getValue(Action.SHORT_DESCRIPTION))
                .isEqualTo(Strings.get(Strings.ACTION_PLAY_PLAY_TOOLTIP));
        }

        /**
         * Regression guard for the desync that motivated removing toggleAction():
         * performAction must not move the label itself — only
         * playbackStateDidChange may do that.
         */
        @Test
        void testPerformActionDoesNotMoveTheLabel() {
            var action = PlayStopAction.createAction(mainFrame());
            var playName = Strings.get(Strings.ACTION_PLAY_PLAY);

            try (var playbackMock = mockStatic(PlaybackController.class)) {
                action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, null));

                assertThat(action.getName()).isEqualTo(playName);
            }
        }

        /**
         * Row 48 — PlayStopAction does NOT carry DISABLE_WHEN_PLAYING, so it
         * remains enabled while playback is in the PLAYING state. (The existing
         * audit test checks DISABLE_WHEN_EDITING_TEXT but not the absence of
         * DISABLE_WHEN_PLAYING.)
         */
        @Test
        void testActionRemainsEnabledDuringPlaybackBecauseDisableWhenPlayingFlagIsAbsent() {
            var action = PlayStopAction.createAction(mainFrame());

            // The flag must NOT be set — that is the design intent
            assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING))
                .as("PlayStopAction must not carry DISABLE_WHEN_PLAYING so it can act as the stop button")
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
            // PlayStopAction has DISABLE_WHEN_SONG_EMPTY, so requires the song to be non-empty
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

            PlaybackController.PLAY_STOP_ACTION.updateEnabledState();

            assertThat(PlaybackController.PLAY_STOP_ACTION.isEnabled())
                .as("PLAY_STOP_ACTION must be disabled when MIDI is unavailable")
                .isFalse();
        }

        /**
         * When sequencer is non-null (MIDI available), updateEnabledState() must enable
         * the action (all other conditions satisfied by the mock score view setup).
         */
        @Test
        void testActionEnabledWhenSequencerIsSet() {
            MidiController.sequencer = mock(Sequencer.class);

            PlaybackController.PLAY_STOP_ACTION.updateEnabledState();

            assertThat(PlaybackController.PLAY_STOP_ACTION.isEnabled())
                .as("PLAY_STOP_ACTION must be enabled when MIDI is available")
                .isTrue();
        }
    }
}
