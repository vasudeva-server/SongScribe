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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.ui.playback.PlaybackController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

class CycleModeActionTest extends MainFrameMockTest {

    private MockedStatic<PlaybackController> playbackControllerMock;

    @BeforeEach
    void setUp() {
        // modeDidChange() → super.modeDidChange() → updateEnabledState() → PlaybackController.isPlaying()
        playbackControllerMock = mockStatic(PlaybackController.class);
        playbackControllerMock.when(PlaybackController::isPlaying).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        playbackControllerMock.close();
    }

    // Row 21: actionPerformed increments currentIndex mod 2; first call → SELECT mode, second → EDIT mode

    @Test
    void testFirstActionPerformedActivatesSelectMode() {
        // MODES[0] = EDIT, MODES[1] = SELECT; first call increments index from 0 to 1
        var action = CycleModeAction.createAction(mainFrame());
        assertThat(action.getCurrentAction()).isEqualTo(CycleModeAction.MODES[0]);

        try (var messageCenterMock = mockStatic(MessageCenter.class)) {
            // Mock MessageCenter so the perform() call does not propagate to real handlers
            action.actionPerformed(
                new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "cycle-mode"));
        }

        assertThat(action.getCurrentAction()).isEqualTo(CycleModeAction.MODES[1]);
    }

    @Test
    void testSecondActionPerformedWrapsBackToEditMode() {
        var action = CycleModeAction.createAction(mainFrame());

        try (var messageCenterMock = mockStatic(MessageCenter.class)) {
            // First call: index 0 → 1 (SELECT)
            action.actionPerformed(
                new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "cycle-mode"));
            // Second call: index 1 → 0 (EDIT)
            action.actionPerformed(
                new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "cycle-mode"));
        }

        assertThat(action.getCurrentAction()).isEqualTo(CycleModeAction.MODES[0]);
    }

    // Row 22: modeDidChange syncs currentIndex from the ModeAction in the notification;
    //          adjustment modes are skipped (index unchanged)

    @Test
    void testModeDidChangeSyncsToSelectMode() {
        var action = CycleModeAction.createAction(mainFrame());
        // Start at EDIT (index 0), send SELECT notification → currentIndex should become 1
        action.modeDidChange(new ModeDidChangeNotification(CycleModeAction.MODES[1]));

        assertThat(action.getCurrentAction()).isEqualTo(CycleModeAction.MODES[1]);
    }

    @Test
    void testModeDidChangeSyncsToEditMode() {
        var action = CycleModeAction.createAction(mainFrame());
        // Put currentIndex at 1 (SELECT) first
        action.modeDidChange(new ModeDidChangeNotification(CycleModeAction.MODES[1]));
        // Now send EDIT notification → currentIndex should become 0
        action.modeDidChange(new ModeDidChangeNotification(CycleModeAction.MODES[0]));

        assertThat(action.getCurrentAction()).isEqualTo(CycleModeAction.MODES[0]);
    }

    @Test
    void testModeDidChangeIgnoresAdjustmentMode() {
        var action = CycleModeAction.createAction(mainFrame());
        // Put currentIndex at 1 (SELECT) first
        action.modeDidChange(new ModeDidChangeNotification(CycleModeAction.MODES[1]));
        assertThat(action.getCurrentAction()).isEqualTo(CycleModeAction.MODES[1]);

        // Send an adjustment-mode notification — currentIndex must not change
        var adjustAction = ModeAction.createAdjustMusicModeAction(mainFrame());
        action.modeDidChange(new ModeDidChangeNotification(adjustAction));

        assertThat(action.getCurrentAction()).isEqualTo(CycleModeAction.MODES[1]);
    }
}
