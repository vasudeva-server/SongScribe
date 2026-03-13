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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.Mode;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.selection.SelectionCoordinator;

class UIActionReflectableGuardTest extends UnitTest {

    @Test
    void testNonReflectableWithSelectionRunsNormalLogic() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(Score.class);
            var mockCoordinator = mock(SelectionCoordinator.class);

            mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
            when(mockFrame.getScore()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
            when(mockCoordinator.getSelectionSize()).thenReturn(2);

            // Make enableInAdjustmentMode return false so the flag chain
            // short-circuits to false, proving normal logic ran despite selection > 0.
            when(mockScore.getMode()).thenReturn(Mode.ADJUSTMENT);
            when(mockScore.getSelectionSize()).thenReturn(2);

            var nonReflectable = new UIAction("Test", null, 0, "test", "Test") {
                @Override
                public void actionPerformed(ActionEvent e) {
                }
            };
            nonReflectable.setFlags(UIAction.Flag.DISABLE_IN_ADJUSTMENT_MODE);
            nonReflectable.setEnabled(true);

            var result = nonReflectable.updateEnabledState();

            assertThat(result).isFalse();
            assertThat(nonReflectable.isEnabled()).isFalse();
        }
    }

    @Test
    void testReflectableWithNoSelectionRunsNormalLogic() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(Score.class);
            var mockCoordinator = mock(SelectionCoordinator.class);

            mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
            when(mockFrame.getScore()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
            when(mockCoordinator.getSelectionSize()).thenReturn(0);

            // Make enableInAdjustmentMode return false so the flag chain
            // short-circuits to false, proving normal logic ran.
            when(mockScore.getMode()).thenReturn(Mode.ADJUSTMENT);
            when(mockScore.getSelectionSize()).thenReturn(0);

            var action = new FermataAction();
            action.setEnabled(true);

            var result = action.updateEnabledState();

            assertThat(result).isFalse();
            assertThat(action.isEnabled()).isFalse();
        }
    }

    @Test
    void testReflectableWithSelectionStillRunsFlagLogic() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var mockFrame = mock(MainFrame.class);
            var mockScore = mock(Score.class);
            var mockCoordinator = mock(SelectionCoordinator.class);

            mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
            when(mockFrame.getScore()).thenReturn(mockScore);
            when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
            when(mockCoordinator.getSelectionSize()).thenReturn(2);

            // FermataAction has DISABLE_IN_ADJUSTMENT_MODE, so adjustment mode
            // should disable it even when a selection is active.
            when(mockScore.getMode()).thenReturn(Mode.ADJUSTMENT);
            when(mockScore.getSelectionSize()).thenReturn(2);

            var action = new FermataAction();
            action.setEnabled(true);

            var result = action.updateEnabledState();

            assertThat(result).isFalse();
            assertThat(action.isEnabled()).isFalse();
        }
    }
}
