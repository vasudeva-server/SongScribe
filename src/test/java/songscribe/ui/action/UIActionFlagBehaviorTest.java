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

package songscribe.ui.action;

import module java.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.Song;
import songscribe.message.notification.DialogVisibilityDidChangeNotification;
import songscribe.ui.component.ScoreView;
import songscribe.ui.dialog.BaseDialog;
import songscribe.ui.action.DialogOpenActionTest.StubDialog;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.edit.PasteModeManager;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class UIActionFlagBehaviorTest extends MainFrameMockTest {

    // -- OPENS_DIALOG flag enables/disables based on dialog visibility --

    @Test
    void testOpensDialogFlagDisablesActionWhenAnyDialogIsVisible() {
        try (var ignored = mockStatic(BaseDialog.class)) {
            ignored.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(true);

            var action = createActionWithFlag(UIAction.Flag.OPENS_DIALOG);
            action.updateEnabledState();

            assertThat(action.isEnabled()).isFalse();
        }
    }

    @Test
    void testOpensDialogFlagReenablesActionWhenAllDialogsClose() {
        try (var ignored = mockStatic(BaseDialog.class)) {
            ignored.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(false);

            var action = createActionWithFlag(UIAction.Flag.OPENS_DIALOG);
            action.updateEnabledState();

            assertThat(action.isEnabled()).isTrue();
        }
    }

    // -- action without OPENS_DIALOG is unaffected by dialog visibility --

    @Test
    void testActionWithoutOpensDialogFlagUnaffectedByDialogVisibility() {
        try (var ignored = mockStatic(BaseDialog.class)) {
            ignored.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(true);

            var action = createActionWithFlag();
            action.updateEnabledState();

            assertThat(action.isEnabled()).isTrue();
        }
    }

    // -- combined flags: OPENS_DIALOG + other disabling flags --

    @Test
    void testOpensDialogWithOtherDisablingFlagRemainsDisabledAfterDialogCloses() {
        try (var baseDialogMock = mockStatic(BaseDialog.class);
             var graceMock = mockStatic(GraceModeManager.class)) {
            baseDialogMock.when(BaseDialog::isAnyBlockingDialogVisible).thenReturn(false);
            graceMock.when(GraceModeManager::isActive).thenReturn(true);

            var action = createActionWithFlag(
                UIAction.Flag.OPENS_DIALOG,
                UIAction.Flag.DISABLE_IN_GRACE_MODE
            );
            action.updateEnabledState();

            assertThat(action.isEnabled()).isFalse();
        }
    }

    // -- DialogOpenAction does NOT auto-set OPENS_DIALOG --

    @Test
    void testDialogOpenActionAutoSetsOpensDialogFlag() {
        var action = new DialogOpenAction<>(mainFrame(), "Test Dialog", StubDialog::new);

        assertThat(action.hasFlag(UIAction.Flag.OPENS_DIALOG)).isTrue();
    }

    // -- setFlags disables on construction for selection/song-state flags --

    @Test
    void testSetFlagsDisablesOnConstructionWithRequiresSelection() {
        var action = createActionWithFlag(UIAction.Flag.REQUIRES_SELECTION);
        assertThat(action.isEnabled()).isFalse();
    }

    @Test
    void testSetFlagsDisablesOnConstructionWithRequiresSingleSelection() {
        var action = createActionWithFlag(UIAction.Flag.REQUIRES_SINGLE_SELECTION);
        assertThat(action.isEnabled()).isFalse();
    }

    @Test
    void testSetFlagsDisablesOnConstructionWithRequiresMultipleSelection() {
        var action = createActionWithFlag(UIAction.Flag.REQUIRES_MULTIPLE_SELECTION);
        assertThat(action.isEnabled()).isFalse();
    }

    @Test
    void testSetFlagsDisablesOnConstructionWithDisableWhenSongEmpty() {
        var action = createActionWithFlag(UIAction.Flag.DISABLE_WHEN_SONG_EMPTY);
        assertThat(action.isEnabled()).isFalse();
    }

    // -- updateEnabledState returns false and disables when getScoreView() is null --

    @Test
    void testUpdateEnabledStateReturnsFalseAndDisablesWhenScoreViewIsNull() {
        when(mainFrame().getScoreView()).thenReturn(null);

        var action = createActionWithFlag();
        action.setEnabled(true);
        var result = action.updateEnabledState();

        assertAll(
            () -> assertThat(result).isFalse(),
            () -> assertThat(action.isEnabled()).isFalse()
        );
    }

    // -- enableInSelectMode: DISABLE_IN_SELECT_MODE + isInSelectMode --

    @Test
    void testDisableInSelectModeFlagDisablesWhenInSelectMode() {
        when(mockEnv().coordinator().isInSelectMode()).thenReturn(true);

        var action = createActionWithFlag(UIAction.Flag.DISABLE_IN_SELECT_MODE);
        var result = action.updateEnabledState();

        assertAll(
            () -> assertThat(result).isFalse(),
            () -> assertThat(action.isEnabled()).isFalse()
        );
    }

    @Test
    void testDisableInSelectModeFlagEnablesWhenNotInSelectMode() {
        when(mockEnv().coordinator().isInSelectMode()).thenReturn(false);

        var action = createActionWithFlag(UIAction.Flag.DISABLE_IN_SELECT_MODE);
        var result = action.updateEnabledState();

        assertAll(
            () -> assertThat(result).isTrue(),
            () -> assertThat(action.isEnabled()).isTrue()
        );
    }

    // -- enableFromSelectionSize --

    @Nested
    class EnableFromSelectionSize {

        private boolean enable(int size, UIAction.Flag flag) {
            var mockScore = mock(ScoreView.class);
            when(mockScore.getSelectionSize()).thenReturn(size);
            var action = createActionWithFlag(flag);
            return action.enableFromSelectionSize(mockScore);
        }

        // REQUIRES_SELECTION: size 0 → false, size > 0 → true

        @Test
        void testRequiresSelectionReturnsFalseWhenSizeIsZero() {
            assertThat(enable(0, UIAction.Flag.REQUIRES_SELECTION)).isFalse();
        }

        @Test
        void testRequiresSelectionReturnsTrueWhenSizeIsPositive() {
            assertThat(enable(1, UIAction.Flag.REQUIRES_SELECTION)).isTrue();
        }

        // REQUIRES_EMPTY_SELECTION: size 0 → true, size > 0 → false

        @Test
        void testRequiresEmptySelectionReturnsTrueWhenSizeIsZero() {
            assertThat(enable(0, UIAction.Flag.REQUIRES_EMPTY_SELECTION)).isTrue();
        }

        @Test
        void testRequiresEmptySelectionReturnsFalseWhenSizeIsPositive() {
            assertThat(enable(1, UIAction.Flag.REQUIRES_EMPTY_SELECTION)).isFalse();
        }

        // REQUIRES_SINGLE_SELECTION: size 1 → true, size ≠ 1 → false

        @Test
        void testRequiresSingleSelectionReturnsTrueWhenSizeIsOne() {
            assertThat(enable(1, UIAction.Flag.REQUIRES_SINGLE_SELECTION)).isTrue();
        }

        @Test
        void testRequiresSingleSelectionReturnsFalseWhenSizeIsZero() {
            assertThat(enable(0, UIAction.Flag.REQUIRES_SINGLE_SELECTION)).isFalse();
        }

        @Test
        void testRequiresSingleSelectionReturnsFalseWhenSizeIsGreaterThanOne() {
            assertThat(enable(2, UIAction.Flag.REQUIRES_SINGLE_SELECTION)).isFalse();
        }

        // REQUIRES_OPTIONAL_SINGLE_SELECTION: size 0 or 1 → true, size > 1 → false

        @Test
        void testRequiresOptionalSingleSelectionReturnsTrueWhenSizeIsZero() {
            assertThat(enable(0, UIAction.Flag.REQUIRES_OPTIONAL_SINGLE_SELECTION)).isTrue();
        }

        @Test
        void testRequiresOptionalSingleSelectionReturnsTrueWhenSizeIsOne() {
            assertThat(enable(1, UIAction.Flag.REQUIRES_OPTIONAL_SINGLE_SELECTION)).isTrue();
        }

        @Test
        void testRequiresOptionalSingleSelectionReturnsFalseWhenSizeIsGreaterThanOne() {
            assertThat(enable(2, UIAction.Flag.REQUIRES_OPTIONAL_SINGLE_SELECTION)).isFalse();
        }

        // REQUIRES_MULTIPLE_SELECTION: size > 1 → true, size ≤ 1 → false

        @Test
        void testRequiresMultipleSelectionReturnsTrueWhenSizeIsGreaterThanOne() {
            assertThat(enable(2, UIAction.Flag.REQUIRES_MULTIPLE_SELECTION)).isTrue();
        }

        @Test
        void testRequiresMultipleSelectionReturnsFalseWhenSizeIsOne() {
            assertThat(enable(1, UIAction.Flag.REQUIRES_MULTIPLE_SELECTION)).isFalse();
        }

        @Test
        void testRequiresMultipleSelectionReturnsFalseWhenSizeIsZero() {
            assertThat(enable(0, UIAction.Flag.REQUIRES_MULTIPLE_SELECTION)).isFalse();
        }

        // REQUIRES_OPTIONAL_MULTIPLE_SELECTION: size 0 or > 1 → true, size 1 → false

        @Test
        void testRequiresOptionalMultipleSelectionReturnsTrueWhenSizeIsZero() {
            assertThat(enable(0, UIAction.Flag.REQUIRES_OPTIONAL_MULTIPLE_SELECTION)).isTrue();
        }

        @Test
        void testRequiresOptionalMultipleSelectionReturnsTrueWhenSizeIsGreaterThanOne() {
            assertThat(enable(2, UIAction.Flag.REQUIRES_OPTIONAL_MULTIPLE_SELECTION)).isTrue();
        }

        @Test
        void testRequiresOptionalMultipleSelectionReturnsFalseWhenSizeIsOne() {
            assertThat(enable(1, UIAction.Flag.REQUIRES_OPTIONAL_MULTIPLE_SELECTION)).isFalse();
        }
    }

    // -- enableInRestMode --

    @Nested
    class EnableInRestMode {

        @AfterEach
        void resetRestAction() {
            Actions.REST_ACTION.setSelected(false);
        }

        private boolean enable(UIAction.Flag... flags) {
            var action = createActionWithFlag(flags);
            return action.enableInRestMode();
        }

        // DISABLE_IN_REST_MODE + REST_ACTION.isSelected() → false

        @Test
        void testDisableInRestModeReturnsFalseWhenRestActionIsSelected() {
            Actions.REST_ACTION.setSelected(true);
            assertThat(enable(UIAction.Flag.DISABLE_IN_REST_MODE)).isFalse();
        }

        // DISABLE_IN_REST_MODE + selectionHasRests → false

        @Test
        void testDisableInRestModeReturnsFalseWhenSelectionHasRests() {
            when(mockEnv().coordinator().selectionHasRests()).thenReturn(true);
            assertThat(enable(UIAction.Flag.DISABLE_IN_REST_MODE)).isFalse();
        }

        // no DISABLE_IN_REST_MODE flag → always true

        @Test
        void testWithoutDisableInRestModeFlagAlwaysReturnsTrue() {
            Actions.REST_ACTION.setSelected(true);
            when(mockEnv().coordinator().selectionHasRests()).thenReturn(true);
            assertThat(enable()).isTrue();
        }
    }

    // -- enableFromPlaybackState --

    @Test
    void testDisableWhenPlayingFlagReturnsFalseWhenPlaying() {
        try (var playbackMock = mockStatic(PlaybackController.class)) {
            playbackMock.when(PlaybackController::isPlaying).thenReturn(true);

            var action = createActionWithFlag(UIAction.Flag.DISABLE_WHEN_PLAYING);
            assertThat(action.enableFromPlaybackState()).isFalse();
        }
    }

    @Test
    void testWithoutDisableWhenPlayingFlagReturnsTrueWhenPlaying() {
        try (var playbackMock = mockStatic(PlaybackController.class)) {
            playbackMock.when(PlaybackController::isPlaying).thenReturn(true);

            var action = createActionWithFlag();
            assertThat(action.enableFromPlaybackState()).isTrue();
        }
    }

    // -- enableFromMidiState --

    @Test
    void testDisableWhenMidiUnavailableFlagReturnsFalseWhenMidiUnavailable() {
        try (var midiMock = mockStatic(MidiController.class)) {
            midiMock.when(MidiController::isAvailable).thenReturn(false);

            var action = createActionWithFlag(UIAction.Flag.DISABLE_WHEN_MIDI_UNAVAILABLE);
            assertThat(action.enableFromMidiState()).isFalse();
        }
    }

    @Test
    void testDisableWhenMidiUnavailableFlagReturnsTrueWhenMidiAvailable() {
        try (var midiMock = mockStatic(MidiController.class)) {
            midiMock.when(MidiController::isAvailable).thenReturn(true);

            var action = createActionWithFlag(UIAction.Flag.DISABLE_WHEN_MIDI_UNAVAILABLE);
            assertThat(action.enableFromMidiState()).isTrue();
        }
    }

    @Test
    void testWithoutMidiFlagReturnsTrueWhenMidiUnavailable() {
        // No DISABLE_WHEN_MIDI_UNAVAILABLE flag: always true regardless of MIDI availability.
        try (var midiMock = mockStatic(MidiController.class)) {
            midiMock.when(MidiController::isAvailable).thenReturn(false);

            var action = createActionWithFlag();
            assertThat(action.enableFromMidiState()).isTrue();
        }
    }

    // -- enableFromGraceModeState --

    @Test
    void testDisableInGraceModeFlagReturnsFalseWhenGraceModeIsActive() {
        try (var graceMock = mockStatic(GraceModeManager.class)) {
            graceMock.when(GraceModeManager::isActive).thenReturn(true);

            var action = createActionWithFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE);
            assertThat(action.enableFromGraceModeState()).isFalse();
        }
    }

    // -- enableFromPasteMode: blanket disable, no flag required --

    @Test
    void testEnableFromPasteModeReturnsFalseForAnyActionWhilePasteModeIsActive() {
        try (var pasteModeMock = mockStatic(PasteModeManager.class)) {
            pasteModeMock.when(PasteModeManager::isActive).thenReturn(true);

            // No flags at all — enableFromPasteMode is unconditional, unlike the
            // per-action DISABLE_IN_GRACE_MODE / DISABLE_WHEN_EDITING_TEXT flags.
            var action = createActionWithFlag();
            assertThat(action.enableFromPasteMode()).isFalse();
        }
    }

    @Test
    void testEnableFromPasteModeReturnsTrueWhenPasteModeIsInactive() {
        try (var pasteModeMock = mockStatic(PasteModeManager.class)) {
            pasteModeMock.when(PasteModeManager::isActive).thenReturn(false);

            var action = createActionWithFlag();
            assertThat(action.enableFromPasteMode()).isTrue();
        }
    }

    // -- enableFromSongState: DISABLE_WHEN_SONG_EMPTY flag --

    @Test
    void testDisableWhenSongEmptyFlagReturnsFalseWhenSongIsEmpty() {
        var mockSong = mock(Song.class);
        when(mockEnv().score().isInitialized()).thenReturn(true);
        when(mockEnv().score().getSong()).thenReturn(mockSong);
        when(mockSong.isEmpty()).thenReturn(true);

        var action = createActionWithFlag(UIAction.Flag.DISABLE_WHEN_SONG_EMPTY);
        assertThat(action.enableFromSongState()).isFalse();
    }

    @Test
    void testDisableWhenSongEmptyFlagReturnsTrueWhenSongIsNotEmpty() {
        var mockSong = mock(Song.class);
        when(mockEnv().score().isInitialized()).thenReturn(true);
        when(mockEnv().score().getSong()).thenReturn(mockSong);
        when(mockSong.isEmpty()).thenReturn(false);

        var action = createActionWithFlag(UIAction.Flag.DISABLE_WHEN_SONG_EMPTY);
        assertThat(action.enableFromSongState()).isTrue();
    }

    @Test
    void testEnableFromSongStateReturnsTrueWithoutFlag() {
        // No DISABLE_WHEN_SONG_EMPTY flag: always true regardless of song state.
        var action = createActionWithFlag();
        assertThat(action.enableFromSongState()).isTrue();
    }

    // -- perform: delegates to actionPerformed with correct ActionEvent source --

    @Test
    void testPerformDelegatesToActionPerformedWithCorrectSource() {
        var source = new Object();
        var capturedSource = new Object[1];

        var action = new UIAction(mainFrame(), "Test", "test-cmd") {
            @Override
            protected void performAction(ActionEvent e) {
                capturedSource[0] = e.getSource();
            }
        };

        action.perform(source);

        assertThat(capturedSource[0]).isSameAs(source);
    }

    @Test
    void testPerformUsesActionItselfAsSourceWhenNullPassed() {
        var capturedSource = new Object[1];

        var action = new UIAction(mainFrame(), "Test", "test-cmd") {
            @Override
            protected void performAction(ActionEvent e) {
                capturedSource[0] = e.getSource();
            }
        };

        action.perform(null);

        assertThat(capturedSource[0]).isSameAs(action);
    }

    // -- dialogVisibilityDidChange: only calls updateEnabledState when OPENS_DIALOG flag present --

    @Test
    void testDialogVisibilityDidChangeDoesNotUpdateEnabledStateWithoutFlag() {
        // An action without OPENS_DIALOG starts enabled; calling dialogVisibilityDidChange
        // must not trigger updateEnabledState (which would disable it if scoreView is null).
        when(mainFrame().getScoreView()).thenReturn(null);

        var action = createActionWithFlag();
        action.setEnabled(true);

        action.dialogVisibilityDidChange(
            new DialogVisibilityDidChangeNotification(true));

        // enabled state must remain true since updateEnabledState was not invoked
        assertThat(action.isEnabled()).isTrue();
    }

    // -- helpers --

    private UIAction createActionWithFlag(UIAction.Flag... flags) {
        return new UIAction(mainFrame(), "Test", "test-cmd", flags);
    }
}
