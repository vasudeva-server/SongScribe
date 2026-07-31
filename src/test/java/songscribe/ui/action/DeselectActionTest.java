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
import songscribe.message.command.DeselectCommand;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class DeselectActionTest extends MainFrameMockTest {

    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    // Row 5: actionPerformed dispatches DeselectCommand

    @Test
    void testActionPerformedPostsDeselectCommand() {
        var action = DeselectAction.createAction(mainFrame());
        action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "deselect"));

        messageCenterMock.verify(
            () -> MessageCenter.post(any(DeselectCommand.class)));
    }
}
