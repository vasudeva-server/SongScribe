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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.command.ToggleTrillCommand;
import songscribe.message.notification.MusicSelectionDidChangeNotification;

class ToggleTrillActionEnablementTest extends MainFrameMockTest {

    // > 0 so REQUIRES_SELECTION passes, letting updateEnabledState() return true.
    private static final int SELECTION_SIZE = 1;

    private ToggleTrillAction action;

    @BeforeEach
    void setUp() {
        // PlaybackController's static initializer creates UIAction instances with keyboard
        // shortcuts that call mainFrame.getRootPane(), so we need a JRootPane mock.
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(mockEnv().frame().getRootPane()).thenReturn(mockRootPane);

        // Override default so that updateEnabledState() checks pass,
        // leaving canToggleTrill() in charge of the final enabled state.
        when(mockEnv().score().getSelectionSize()).thenReturn(SELECTION_SIZE);

        action = ToggleTrillAction.createAction(mainFrame());
    }

    // Row 48: disables when canToggleTrill() returns false
    @Test
    void testDisablesWhenCannotToggleTrill() {
        when(mockEnv().ctrl().canToggleTrill()).thenReturn(false);
        var notification = new MusicSelectionDidChangeNotification(mockEnv().score());
        action.musicSelectionDidChange(notification);
        assertThat(action.isEnabled()).isFalse();
    }

    // Row 49: enables when canToggleTrill() returns true
    @Test
    void testEnablesWhenCanToggleTrill() {
        when(mockEnv().ctrl().canToggleTrill()).thenReturn(true);
        var notification = new MusicSelectionDidChangeNotification(mockEnv().score());
        action.musicSelectionDidChange(notification);
        assertThat(action.isEnabled()).isTrue();
    }

    // Row 50: actionPerformed posts ToggleTrillCommand on the message bus
    @Nested
    class ActionPerformed {

        private MockedStatic<MessageCenter> messageMock;

        @BeforeEach
        void setUpMessageCenter() {
            messageMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDownMessageCenter() {
            messageMock.close();
        }

        @Test
        void testActionPerformedPostsToggleTrillCommand() {
            var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "");
            action.actionPerformed(e);
            var captor = ArgumentCaptor.forClass(ToggleTrillCommand.class);
            messageMock.verify(() -> MessageCenter.post(captor.capture()));
            assertThat(captor.getValue()).isInstanceOf(ToggleTrillCommand.class);
        }
    }
}
