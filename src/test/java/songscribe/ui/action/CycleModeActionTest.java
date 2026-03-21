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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.ui.Mode;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.selection.SelectionCoordinator;

class CycleModeActionTest extends UnitTest {

    private static MockedStatic<MainFrame> mainFrameMock;

    @BeforeAll
    static void setUpMocks() {
        mainFrameMock = mockStatic(MainFrame.class);
        var mockFrame = mock(MainFrame.class);
        var mockScore = mock(Score.class);
        var mockCoordinator = mock(SelectionCoordinator.class);
        var mockRootPane = mock(JRootPane.class);

        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
        when(mockFrame.getScore()).thenReturn(mockScore);
        when(mockFrame.getRootPane()).thenReturn(mockRootPane);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockScore.getMode()).thenReturn(Mode.EDIT);
        when(mockScore.getSelectionSize()).thenReturn(0);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());

        // Force Actions class loading within the mock scope
        var _ = Actions.EDIT_MODE_ACTION;
    }

    @AfterAll
    static void tearDownMocks() {
        mainFrameMock.close();
    }

    @Test
    void testCycleFromEditToSelect() {
        // Default state is edit mode (index 0)
        Actions.MODE_ACTION_GROUP.setSelected(Actions.EDIT_MODE_ACTION, true);
        var event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "cycle-mode");

        Actions.CYCLE_MODE_ACTION.actionPerformed(event);

        assertThat(Actions.CYCLE_MODE_ACTION.getCurrentAction())
            .as("should cycle to select mode")
            .isSameAs(Actions.SELECT_MODE_ACTION);
        assertThat(Actions.CYCLE_MODE_ACTION.getCurrentAction().getMode())
            .isEqualTo(Mode.SELECT);
    }

    @Test
    void testCycleFromSelectToEdit() {
        // Set to select mode first
        Actions.MODE_ACTION_GROUP.setSelected(Actions.SELECT_MODE_ACTION, true);
        var event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "cycle-mode");

        // Cycle once to get to select, then again to get to edit
        Actions.CYCLE_MODE_ACTION.actionPerformed(event);

        assertThat(Actions.CYCLE_MODE_ACTION.getCurrentAction())
            .as("should cycle to edit mode")
            .isSameAs(Actions.EDIT_MODE_ACTION);
        assertThat(Actions.CYCLE_MODE_ACTION.getCurrentAction().getMode())
            .isEqualTo(Mode.EDIT);
    }

    @Test
    void testFullCycleReturnsToStart() {
        var event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "cycle-mode");
        var startAction = Actions.CYCLE_MODE_ACTION.getCurrentAction();

        // Cycle through all modes
        for (var i = 0; i < CycleModeAction.MODES.length; i++) {
            Actions.CYCLE_MODE_ACTION.actionPerformed(event);
        }

        assertThat(Actions.CYCLE_MODE_ACTION.getCurrentAction())
            .as("full cycle returns to start")
            .isSameAs(startAction);
    }
}
