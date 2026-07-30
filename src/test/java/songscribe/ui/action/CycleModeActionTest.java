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

    // Row 21: actionPerformed toggles the current mode; first call → SELECT mode, second → EDIT mode

    @Test
    void testFirstActionPerformedActivatesSelectMode() {
        var action = CycleModeAction.createAction(mainFrame());
        assertThat(action.getCurrentAction()).isEqualTo(Actions.EDIT_MODE_ACTION);

        try (var messageCenterMock = mockStatic(MessageCenter.class)) {
            // Mock MessageCenter so the perform() call does not propagate to real handlers
            action.actionPerformed(
                new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "cycle-mode"));
        }

        assertThat(action.getCurrentAction()).isEqualTo(Actions.SELECT_MODE_ACTION);
    }

    @Test
    void testSecondActionPerformedWrapsBackToEditMode() {
        var action = CycleModeAction.createAction(mainFrame());

        try (var messageCenterMock = mockStatic(MessageCenter.class)) {
            // First call: EDIT → SELECT
            action.actionPerformed(
                new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "cycle-mode"));
            // Second call: SELECT → EDIT
            action.actionPerformed(
                new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "cycle-mode"));
        }

        assertThat(action.getCurrentAction()).isEqualTo(Actions.EDIT_MODE_ACTION);
    }

    // Row 22: modeDidChange syncs the current mode from the ModeAction in the notification

    @Test
    void testModeDidChangeSyncsToSelectMode() {
        var action = CycleModeAction.createAction(mainFrame());
        // Start at EDIT, send a SELECT notification → current action should become SELECT
        action.modeDidChange(new ModeDidChangeNotification(Actions.SELECT_MODE_ACTION));

        assertThat(action.getCurrentAction()).isEqualTo(Actions.SELECT_MODE_ACTION);
    }

    @Test
    void testModeDidChangeSyncsToEditMode() {
        var action = CycleModeAction.createAction(mainFrame());
        // Move to SELECT first
        action.modeDidChange(new ModeDidChangeNotification(Actions.SELECT_MODE_ACTION));
        // Now send an EDIT notification → current action should become EDIT
        action.modeDidChange(new ModeDidChangeNotification(Actions.EDIT_MODE_ACTION));

        assertThat(action.getCurrentAction()).isEqualTo(Actions.EDIT_MODE_ACTION);
    }

}
