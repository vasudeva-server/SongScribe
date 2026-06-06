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

package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.message.notification.GraceModeStateDidChangeNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.ui.action.Actions;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.playback.PlaybackController;
import songscribe.util.UIUtils;

/**
 * Unit tests for {@link ModeCycleButton} covering:
 * <ul>
 *   <li>Row 19 — {@code modeDidChange}: button NOT updated when {@code isAdjustmentMode()} is true</li>
 *   <li>Row 20 — {@code modeDidChange}: button IS updated when {@code isAdjustmentMode()} is false</li>
 *   <li>Row 21 — {@code playbackStateDidChange}: button disabled when playing, enabled otherwise</li>
 *   <li>Row 22 — {@code graceModeStateDidChange}: button disabled when grace mode active</li>
 * </ul>
 */
class ModeCycleButtonTest extends MainFrameMockTest {

    private ModeCycleButton button;

    @BeforeEach
    void createButton() {
        button = new ModeCycleButton();
    }

    // -----------------------------------------------------------------------
    // Row 19: modeDidChange skips updateButton when isAdjustmentMode() is true
    // -----------------------------------------------------------------------

    @Test
    void testModeDidChangeInAdjustmentModeSkipsUpdateButton() {
        // ADJUST_MUSIC_MODE_ACTION has command "adjust-note-mode" → isAdjustmentMode() == true
        var notification = new ModeDidChangeNotification(Actions.ADJUST_MUSIC_MODE_ACTION);
        assertThat(notification.isAdjustmentMode()).isTrue();

        try (var uiUtilsMock = mockStatic(UIUtils.class)) {
            button.modeDidChange(notification);
            // configureButtonFromAction must not be called when isAdjustmentMode() is true
            uiUtilsMock.verify(() -> UIUtils.configureButtonFromAction(any(), any()), never());
        }
    }

    // -----------------------------------------------------------------------
    // Row 20: modeDidChange calls updateButton when isAdjustmentMode() is false
    // -----------------------------------------------------------------------

    @Test
    void testModeDidChangeInNonAdjustmentModeCallsUpdateButton() {
        // EDIT_MODE_ACTION has command "edit-mode" → isAdjustmentMode() == false
        var notification = new ModeDidChangeNotification(Actions.EDIT_MODE_ACTION);
        assertThat(notification.isAdjustmentMode()).isFalse();

        try (var uiUtilsMock = mockStatic(UIUtils.class)) {
            button.modeDidChange(notification);
            // configureButtonFromAction must be called exactly once (inside updateButton)
            uiUtilsMock.verify(() -> UIUtils.configureButtonFromAction(button, Actions.EDIT_MODE_ACTION));
        }
    }

    // -----------------------------------------------------------------------
    // Row 21: playbackStateDidChange disables button when playing, enables otherwise
    // -----------------------------------------------------------------------

    @Test
    void testPlaybackStateDidChangeDisablesButtonWhenPlaying() {
        var notification = new PlaybackStateDidChangeNotification(PlaybackController.PlaybackState.PLAYING);

        try (var pcMock = mockStatic(PlaybackController.class)) {
            pcMock.when(PlaybackController::isPlaying).thenReturn(true);
            button.playbackStateDidChange(notification);
            assertThat(button.isEnabled()).isFalse();
        }
    }

    @Test
    void testPlaybackStateDidChangeEnablesButtonWhenNotPlaying() {
        // First disable the button to ensure the handler actually changes state
        button.setEnabled(false);

        var notification = new PlaybackStateDidChangeNotification(PlaybackController.PlaybackState.STOPPED);

        try (var pcMock = mockStatic(PlaybackController.class)) {
            pcMock.when(PlaybackController::isPlaying).thenReturn(false);
            button.playbackStateDidChange(notification);
            assertThat(button.isEnabled()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Row 22: graceModeStateDidChange disables button when grace mode is active
    // -----------------------------------------------------------------------

    @Test
    void testGraceModeStateDidChangeDisablesButtonWhenGraceModeActive() {
        var notification = new GraceModeStateDidChangeNotification(true);

        try (var gmMock = mockStatic(GraceModeManager.class)) {
            gmMock.when(GraceModeManager::isActive).thenReturn(true);
            button.graceModeStateDidChange(notification);
            assertThat(button.isEnabled()).isFalse();
        }
    }

    @Test
    void testGraceModeStateDidChangeEnablesButtonWhenGraceModeInactive() {
        button.setEnabled(false);

        var notification = new GraceModeStateDidChangeNotification(false);

        try (var gmMock = mockStatic(GraceModeManager.class)) {
            gmMock.when(GraceModeManager::isActive).thenReturn(false);
            button.graceModeStateDidChange(notification);
            assertThat(button.isEnabled()).isTrue();
        }
    }
}
