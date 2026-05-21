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
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.ui.Mode;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.SelectionCoordinator;

class UIActionReflectableGuardTest extends MainFrameMockTest {

    @Test
    void testNonReflectableWithSelectionRunsNormalLogic() {
        var mockScore = mock(ScoreView.class);
        var mockCoordinator = mock(SelectionCoordinator.class);

        when(mainFrame().getScoreView()).thenReturn(mockScore);
        when(mainFrame().requireScoreView()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockCoordinator.getSelectionSize()).thenReturn(2);

        // Make enableInAdjustmentMode return false so the flag chain
        // short-circuits to false, proving normal logic ran despite selection > 0.
        when(mockScore.getMode()).thenReturn(Mode.ADJUSTMENT);
        when(mockScore.getSelectionSize()).thenReturn(2);

        var nonReflectable = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
        nonReflectable.setFlags(UIAction.Flag.DISABLE_IN_ADJUSTMENT_MODE);
        nonReflectable.setEnabled(true);

        var result = nonReflectable.updateEnabledState();

        assertThat(result).isFalse();
        assertThat(nonReflectable.isEnabled()).isFalse();
    }

    @Test
    void testReflectableWithNoSelectionRunsNormalLogic() {
        var mockScore = mock(ScoreView.class);
        var mockCoordinator = mock(SelectionCoordinator.class);

        when(mainFrame().getScoreView()).thenReturn(mockScore);
        when(mainFrame().requireScoreView()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockCoordinator.getSelectionSize()).thenReturn(0);

        // Make enableInAdjustmentMode return false so the flag chain
        // short-circuits to false, proving normal logic ran.
        when(mockScore.getMode()).thenReturn(Mode.ADJUSTMENT);
        when(mockScore.getSelectionSize()).thenReturn(0);

        var action = FermataAction.createAction(mainFrame());
        action.setEnabled(true);

        var result = action.updateEnabledState();

        assertThat(result).isFalse();
        assertThat(action.isEnabled()).isFalse();
    }

    @Test
    void testReflectableWithSelectionStillRunsFlagLogic() {
        var mockScore = mock(ScoreView.class);
        var mockCoordinator = mock(SelectionCoordinator.class);

        when(mainFrame().getScoreView()).thenReturn(mockScore);
        when(mainFrame().requireScoreView()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockCoordinator.getSelectionSize()).thenReturn(2);

        // FermataAction has DISABLE_IN_ADJUSTMENT_MODE, so adjustment mode
        // should disable it even when a selection is active.
        when(mockScore.getMode()).thenReturn(Mode.ADJUSTMENT);
        when(mockScore.getSelectionSize()).thenReturn(2);

        var action = FermataAction.createAction(mainFrame());
        action.setEnabled(true);

        var result = action.updateEnabledState();

        assertThat(result).isFalse();
        assertThat(action.isEnabled()).isFalse();
    }
}
