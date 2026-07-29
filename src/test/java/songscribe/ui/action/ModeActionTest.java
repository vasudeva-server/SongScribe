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
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.ui.Mode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mockStatic;

class ModeActionTest extends MainFrameMockTest {

    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUpMessageCenter() {
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDownMessageCenter() {
        messageCenterMock.close();
    }

    // Row 73: Factory methods bind correct Mode value

    @Test
    void testCreateSelectModeActionBindsSelectMode() {
        assertThat(ModeAction.createSelectModeAction(mainFrame()).getMode()).isEqualTo(Mode.SELECT);
    }

    @Test
    void testCreateEditModeActionBindsEditMode() {
        assertThat(ModeAction.createEditModeAction(mainFrame()).getMode()).isEqualTo(Mode.EDIT);
    }

    // Row 74: actionPerformed posts ModeDidChangeNotification with self as source

    @Test
    void testActionPerformedPostsModeDidChangeNotificationWithSelf() {
        var action = ModeAction.createEditModeAction(mainFrame());
        var e = new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "");

        action.actionPerformed(e);

        var captor = ArgumentCaptor.forClass(ModeDidChangeNotification.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        assertThat(captor.getValue().getAction()).isSameAs(action);
    }

    // Row 75: toggleOnKeyboardShortcut toggles selected state when source is JRootPane

    @Test
    void testActionPerformedWithJRootPaneSourceTogglesSelectedState() {
        var action = ModeAction.createSelectModeAction(mainFrame());
        action.setSelected(false);
        var e = new ActionEvent(new JRootPane(), ActionEvent.ACTION_PERFORMED, "");

        action.actionPerformed(e);

        assertThat(action.isSelected()).isTrue();
    }

    // Row 76: Hard-wired flags: DISABLE_WHEN_PLAYING + DISABLE_IN_GRACE_MODE

    @Test
    void testModeActionHasDisableWhenPlayingAndDisableInGraceModeFlags() {
        var action = ModeAction.createEditModeAction(mainFrame());
        assertAll(
            () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_PLAYING)).isTrue(),
            () -> assertThat(action.hasFlag(UIAction.Flag.DISABLE_IN_GRACE_MODE)).isTrue()
        );
    }
}
