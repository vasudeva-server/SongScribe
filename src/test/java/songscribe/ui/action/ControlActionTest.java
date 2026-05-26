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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.MainFrameMockTest;
import songscribe.message.MessageCenter;
import songscribe.message.notification.ControlDidChangeNotification;
import songscribe.ui.Control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class ControlActionTest extends MainFrameMockTest {

    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUpMessageCenter() {
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDownMessageCenter() {
        messageCenterMock.close();
    }

    // Row 48: actionPerformed posts ControlDidChangeNotification with correct control

    @Test
    void testActionPerformedPostsControlDidChangeNotificationWithCorrectControl() {
        var action = ControlAction.createMouseControlAction(mainFrame());
        var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "MOUSE");

        action.actionPerformed(e);

        var captor = ArgumentCaptor.forClass(ControlDidChangeNotification.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        assertThat(captor.getValue().getControl()).isEqualTo(Control.MOUSE);
    }

    // Row 49: factory methods bind correct Control enum values

    @Test
    void testCreateMouseControlActionBindsMouseControl() {
        var action = ControlAction.createMouseControlAction(mainFrame());
        assertThat(action.control).isEqualTo(Control.MOUSE);
    }

    @Test
    void testCreateKeyboardControlActionBindsKeyboardControl() {
        var action = ControlAction.createKeyboardControlAction(mainFrame());
        assertThat(action.control).isEqualTo(Control.KEYBOARD);
    }
}
