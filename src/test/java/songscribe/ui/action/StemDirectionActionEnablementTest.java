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
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.AutoStemDirectionCommand;
import songscribe.message.command.FlipStemDirectionCommand;
import songscribe.message.notification.MusicSelectionDidChangeNotification;

/**
 * The flip and auto actions are two instances of the same class, so enablement — which
 * they share — is verified once. Each factory is then checked for the command it posts,
 * the only behavior that distinguishes them.
 */
class StemDirectionActionEnablementTest extends MainFrameMockTest {

    // > 0 so REQUIRES_SELECTION passes, letting updateEnabledState() return true.
    private static final int SELECTION_SIZE = 1;

    private StemDirectionAction flipAction;
    private StemDirectionAction autoAction;

    @BeforeEach
    void setUp() {
        // PlaybackController's static initializer creates UIAction instances with keyboard
        // shortcuts that call mainFrame.getRootPane(), so we need a JRootPane mock.
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(mockEnv().frame().getRootPane()).thenReturn(mockRootPane);

        // Override defaults so that the general updateEnabledState() checks pass,
        // leaving canModifyStemDirection() in charge of the final enabled state.
        when(mockEnv().score().getSelectionSize()).thenReturn(SELECTION_SIZE);
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
        when(mockEnv().coordinator().selectionHasDurations()).thenReturn(true);

        flipAction = StemDirectionAction.createFlipAction(mainFrame());
        autoAction = StemDirectionAction.createAutoAction(mainFrame());
    }

    // Row 38: disables when canModifyStemDirection() returns false (e.g. all-rest selection)
    @Test
    void testDisablesWhenCannotModifyStemDirection() {
        when(mockEnv().ctrl().canModifyStemDirection()).thenReturn(false);
        var notification = new MusicSelectionDidChangeNotification(mockEnv().score());

        flipAction.musicSelectionDidChange(notification);
        autoAction.musicSelectionDidChange(notification);

        assertThat(flipAction.isEnabled()).isFalse();
        assertThat(autoAction.isEnabled()).isFalse();
    }

    // Row 39: enables when canModifyStemDirection() returns true (selection has ≥1 note)
    @Test
    void testEnablesWhenCanModifyStemDirection() {
        when(mockEnv().ctrl().canModifyStemDirection()).thenReturn(true);
        var notification = new MusicSelectionDidChangeNotification(mockEnv().score());

        flipAction.musicSelectionDidChange(notification);
        autoAction.musicSelectionDidChange(notification);

        assertThat(flipAction.isEnabled()).isTrue();
        assertThat(autoAction.isEnabled()).isTrue();
    }

    // Row 40: each action posts its own command on the message bus
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

        private Message capturePostedMessage(StemDirectionAction action) {
            var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "");
            action.actionPerformed(e);
            var captor = ArgumentCaptor.forClass(Message.class);
            messageMock.verify(() -> MessageCenter.post(captor.capture()));
            return captor.getValue();
        }

        @Test
        void testFlipActionPostsFlipStemDirectionCommand() {
            assertThat(capturePostedMessage(flipAction)).isInstanceOf(FlipStemDirectionCommand.class);
        }

        @Test
        void testAutoActionPostsAutoStemDirectionCommand() {
            assertThat(capturePostedMessage(autoAction)).isInstanceOf(AutoStemDirectionCommand.class);
        }
    }
}
