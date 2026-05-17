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

import javax.swing.JRootPane;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.when;

import org.mockito.MockedStatic;

import songscribe.ui.Mode;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Shared test helper for setting up a mock MainFrame environment
 * used by action-level unit tests.
 */
public final class MockEnvHelper {

    private MockEnvHelper() {
    }

    public record MockEnv(
        MainFrame frame,
        ScoreView score,
        SelectionCoordinator coordinator,
        ScoreViewController ctrl
    ) {}

    public static MockEnv setupMockEnv(MockedStatic<MainFrame> mainFrameMock) {
        var mockFrame = mock(MainFrame.class);
        var mockScore = mock(ScoreView.class);
        var mockCoordinator = mock(SelectionCoordinator.class);
        var mockCtrl = mock(ScoreViewController.class);

        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
        when(mockFrame.getRootPane()).thenReturn(mock(JRootPane.class, RETURNS_DEEP_STUBS));
        when(mockFrame.getScoreView()).thenReturn(mockScore);
        when(mockFrame.requireScoreView()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockScore.getMode()).thenReturn(Mode.EDIT);
        when(mockScore.getSelectionSize()).thenReturn(0);
        when(mockScore.getController()).thenReturn(mockCtrl);

        return new MockEnv(mockFrame, mockScore, mockCoordinator, mockCtrl);
    }
}
