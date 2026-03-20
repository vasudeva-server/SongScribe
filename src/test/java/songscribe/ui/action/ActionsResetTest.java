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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.MessageCenter;
import songscribe.message.notification.CompositionDidChangeNotification;
import songscribe.music.Composition;
import songscribe.ui.Mode;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Integration tests verifying that posting a FULL CompositionDidChangeNotification
 * resets all action states to defaults via the Actions.ResetHandler.
 */
class ActionsResetTest extends UnitTest {

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

    @BeforeEach
    void setNonDefaultStates() {
        // Set all actions to non-default states so we can verify they get reset
        Actions.MODE_ACTION_GROUP.setSelected(Actions.SELECT_MODE_ACTION, true);
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.HALF_NOTE_ACTION, true);
        Actions.ACCIDENTAL_ACTION_GROUP.setSelected(Actions.SHARP_ACTION, true);
        Actions.ARTICULATION_ACTION_GROUP.setSelected(Actions.STACCATO_ACTION, true);
        Actions.DOT_ACTION_GROUP.setSelected(Actions.DOT_ACTION, true);
        Actions.NON_DURATION_ACTION_GROUP.setSelected(Actions.BARLINE_ACTIONS[0], true);
        Actions.ACCENT_ACTION.setSelected(true);
        Actions.REST_ACTION.setSelected(true);
        Actions.FERMATA_ACTION.setSelected(true);
        Actions.ACCIDENTAL_IN_PARENS_ACTION.setSelected(true);

        // Set CONTROL_ACTION_GROUP to keyboard — this should NOT be reset
        Actions.CONTROL_ACTION_GROUP.setSelected(Actions.KEYBOARD_CONTROL_ACTION, true);
    }

    @Test
    void testControlGroupNotReset() {
        postFullNotification();

        assertThat(Actions.CONTROL_ACTION_GROUP.getSelected())
            .as("CONTROL_ACTION_GROUP should not be reset")
            .isSameAs(Actions.KEYBOARD_CONTROL_ACTION);
    }

    @Test
    void testNewCompositionResetsActions() {
        postFullNotification();
        verifyAllDefaults();
    }

    @Test
    void testRepeatedResetIsIdempotent() {
        postFullNotification();
        postFullNotification();
        verifyAllDefaults();
    }

    // -- helpers --

    private void postFullNotification() {
        MessageCenter.post(new CompositionDidChangeNotification(
            CompositionDidChangeNotification.ChangeType.FULL,
            new Composition()
        ));
    }

    private void verifyAllDefaults() {
        assertThat(Actions.MODE_ACTION_GROUP.getSelected())
            .as("mode should reset to EDIT")
            .isSameAs(Actions.EDIT_MODE_ACTION);

        assertThat(Actions.DURATION_ACTION_GROUP.getSelected())
            .as("duration should reset to QUARTER_NOTE")
            .isSameAs(Actions.QUARTER_NOTE_ACTION);

        assertThat(Actions.ACCIDENTAL_ACTION_GROUP.getSelected())
            .as("accidental group should be cleared")
            .isNull();

        assertThat(Actions.ARTICULATION_ACTION_GROUP.getSelected())
            .as("articulation group should be cleared")
            .isNull();

        assertThat(Actions.DOT_ACTION_GROUP.getSelected())
            .as("dot group should be cleared")
            .isNull();

        assertThat(Actions.NON_DURATION_ACTION_GROUP.getSelected())
            .as("non-duration group should be cleared")
            .isNull();

        assertThat(Actions.ACCENT_ACTION.isSelected())
            .as("accent toggle should be off")
            .isFalse();

        assertThat(Actions.REST_ACTION.isSelected())
            .as("rest toggle should be off")
            .isFalse();

        assertThat(Actions.FERMATA_ACTION.isSelected())
            .as("fermata toggle should be off")
            .isFalse();

        assertThat(Actions.ACCIDENTAL_IN_PARENS_ACTION.isSelected())
            .as("accidental-in-parens toggle should be off")
            .isFalse();
    }
}
