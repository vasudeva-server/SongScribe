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
import songscribe.message.command.InsertLineCommand;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class InsertLineActionTest extends MainFrameMockTest {

    // Row 7: getActionCommand branches — ADD→"add-line", 0→"insert-line-before", 1→"insert-line-after"

    @Test
    void testAddLineActionCommandIsAddLine() {
        var action = InsertLineAction.createAddLineAction(mainFrame());
        assertThat(action.getActionCommand()).isEqualTo("add-line");
    }

    @Test
    void testInsertLineBeforeActionCommandIsInsertLineBefore() {
        var action = InsertLineAction.createInsertLineBeforeAction(mainFrame());
        assertThat(action.getActionCommand()).isEqualTo("insert-line-before");
    }

    @Test
    void testInsertLineAfterActionCommandIsInsertLineAfter() {
        var action = InsertLineAction.createInsertLineAfterAction(mainFrame());
        assertThat(action.getActionCommand()).isEqualTo("insert-line-after");
    }

    // Accelerators — all three are Return with the menu shortcut key, distinguished
    // by Shift (before) and Alt (after)

    @Test
    void testAddLineAcceleratorIsMenuShortcutReturn() {
        var action = InsertLineAction.createAddLineAction(mainFrame());
        assertThat(action.getAccelerator())
            .isEqualTo(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, UIUtils.MENU_SHORTCUT_MASK));
    }

    @Test
    void testInsertLineBeforeAcceleratorAddsShift() {
        var action = InsertLineAction.createInsertLineBeforeAction(mainFrame());
        assertThat(action.getAccelerator()).isEqualTo(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_ENTER,
                UIUtils.MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK));
    }

    @Test
    void testInsertLineAfterAcceleratorAddsAlt() {
        var action = InsertLineAction.createInsertLineAfterAction(mainFrame());
        assertThat(action.getAccelerator()).isEqualTo(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_ENTER,
                UIUtils.MENU_SHORTCUT_MASK | InputEvent.ALT_DOWN_MASK));
    }

    // Row 15: actionPerformed dispatches InsertLineCommand(type) for all three variants

    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUpMessageCenter() {
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDownMessageCenter() {
        messageCenterMock.close();
    }

    @Test
    void testAddLineActionPerformedPostsInsertLineCommandWithAddAtEnd() {
        var action = InsertLineAction.createAddLineAction(mainFrame());
        action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "add-line"));

        var captor = ArgumentCaptor.forClass(InsertLineCommand.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        assertThat(captor.getValue().getType()).isEqualTo(InsertLineAction.Type.ADD_AT_END);
    }

    @Test
    void testInsertLineBeforeActionPerformedPostsInsertLineCommandWithInsertBefore() {
        var action = InsertLineAction.createInsertLineBeforeAction(mainFrame());
        action.actionPerformed(
            new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "insert-line-before"));

        var captor = ArgumentCaptor.forClass(InsertLineCommand.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        assertThat(captor.getValue().getType()).isEqualTo(InsertLineAction.Type.INSERT_BEFORE);
    }

    @Test
    void testInsertLineAfterActionPerformedPostsInsertLineCommandWithInsertAfter() {
        var action = InsertLineAction.createInsertLineAfterAction(mainFrame());
        action.actionPerformed(
            new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "insert-line-after"));

        var captor = ArgumentCaptor.forClass(InsertLineCommand.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        assertThat(captor.getValue().getType()).isEqualTo(InsertLineAction.Type.INSERT_AFTER);
    }

    // updateEnabledState — add-line is always enabled; before/after require a line selection

    @Test
    void testAddLineEnabledWithoutLineSelection() {
        when(mockEnv().coordinator().hasLineSelection()).thenReturn(false);

        var action = InsertLineAction.createAddLineAction(mainFrame());
        action.setEnabled(false);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isTrue();
    }

    @Test
    void testInsertLineBeforeEnabledWhenLineSelected() {
        when(mockEnv().coordinator().hasLineSelection()).thenReturn(true);

        var action = InsertLineAction.createInsertLineBeforeAction(mainFrame());
        action.setEnabled(false);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isTrue();
    }

    @Test
    void testInsertLineBeforeDisabledWithoutLineSelection() {
        when(mockEnv().coordinator().hasLineSelection()).thenReturn(false);

        var action = InsertLineAction.createInsertLineBeforeAction(mainFrame());
        action.setEnabled(true);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isFalse();
    }

    @Test
    void testInsertLineAfterEnabledWhenLineSelected() {
        when(mockEnv().coordinator().hasLineSelection()).thenReturn(true);

        var action = InsertLineAction.createInsertLineAfterAction(mainFrame());
        action.setEnabled(false);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isTrue();
    }

    @Test
    void testInsertLineAfterDisabledWithoutLineSelection() {
        when(mockEnv().coordinator().hasLineSelection()).thenReturn(false);

        var action = InsertLineAction.createInsertLineAfterAction(mainFrame());
        action.setEnabled(true);

        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockEnv().score()));

        assertThat(action.isEnabled()).isFalse();
    }
}
