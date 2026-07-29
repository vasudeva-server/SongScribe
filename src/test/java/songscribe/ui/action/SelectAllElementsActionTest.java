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
import songscribe.dom.Song;
import songscribe.message.MessageCenter;
import songscribe.message.command.SelectAllElementsCommand;
import songscribe.ui.selection.ElementSelection;
import songscribe.util.UIUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SelectAllElementsActionTest extends MainFrameMockTest {

    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    // Row 6: actionPerformed dispatches SelectAllElementsCommand

    @Test
    void testActionPerformedPostsSelectAllElementsCommand() {
        var action = SelectAllElementsAction.createAction(mainFrame());
        action.actionPerformed(new ActionEvent(action, ActionEvent.ACTION_PERFORMED, "select-all-elements"));

        messageCenterMock.verify(
            () -> MessageCenter.post(org.mockito.ArgumentMatchers.any(SelectAllElementsCommand.class)));
    }

    @Test
    void testAcceleratorIsTheMenuShortcutWithA() {
        var action = SelectAllElementsAction.createAction(mainFrame());

        assertThat(action.getAccelerator()).isEqualTo(
            KeyStroke.getKeyStroke(KeyEvent.VK_A, UIUtils.MENU_SHORTCUT_MASK));
    }

    @Test
    void testCarriesDisableWhenEditingTextSoTextFieldsKeepTheirOwnSelectAll() {
        var action = SelectAllElementsAction.createAction(mainFrame());

        assertThat(action.hasFlag(UIAction.Flag.DISABLE_WHEN_EDITING_TEXT)).isTrue();
    }

    // Enablement: a line selection reports a selection size of 0, so
    // Flag.ENABLE_WHEN_LINE_SELECTED must carry it past the REQUIRES_SELECTION size check —
    // except when the selected line has no elements to swap the line selection for.

    @Test
    void testEnabledWhenTheSelectedLineHasElements() {
        var action = SelectAllElementsAction.createAction(mainFrame());
        var coordinator = mockEnv().coordinator();
        var line = new Song().getLine(0);
        when(coordinator.hasLineSelection()).thenReturn(true);
        when(coordinator.getSelection()).thenReturn(new ElementSelection(line, 0, 1));

        assertThat(action.enableFromSelectionSize(mockEnv().score())).isTrue();
    }

    @Test
    void testDisabledWhenTheSelectedLineHasNoElements() {
        var action = SelectAllElementsAction.createAction(mainFrame());
        var coordinator = mockEnv().coordinator();
        when(coordinator.hasLineSelection()).thenReturn(true);
        when(coordinator.getSelection()).thenReturn(null);

        assertThat(action.enableFromSelectionSize(mockEnv().score())).isFalse();
    }

    @Test
    void testEnabledWhenElementsAreSelectedWithoutALineSelection() {
        var action = SelectAllElementsAction.createAction(mainFrame());
        when(mockEnv().coordinator().hasLineSelection()).thenReturn(false);
        when(mockEnv().score().getSelectionSize()).thenReturn(1);

        assertThat(action.enableFromSelectionSize(mockEnv().score())).isTrue();
    }

    @Test
    void testDisabledWhenNothingIsSelected() {
        var action = SelectAllElementsAction.createAction(mainFrame());
        when(mockEnv().coordinator().hasLineSelection()).thenReturn(false);
        when(mockEnv().score().getSelectionSize()).thenReturn(0);

        assertThat(action.enableFromSelectionSize(mockEnv().score())).isFalse();
    }
}
