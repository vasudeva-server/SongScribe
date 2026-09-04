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

import songscribe.dom.Song;
import songscribe.ui.Mode;
import songscribe.ui.ViewScale;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.selection.SelectionCoordinator;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    /**
     * Creates a mock MainFrame environment for code that takes the frame as a constructor or
     * factory parameter. {@code MainFrame.getInstance()} is left unstubbed: code reaching it is a
     * constructor-injection finding, not a case for a wider mock.
     */
    public static MockEnv setupMockEnv() {
        var mockFrame = mock(MainFrame.class);
        var mockScore = mock(ScoreView.class);
        var mockCoordinator = mock(SelectionCoordinator.class);
        var mockCtrl = mock(ScoreViewController.class);

        when(mockFrame.getRootPane()).thenReturn(mock(JRootPane.class, RETURNS_DEEP_STUBS));
        when(mockFrame.getScoreView()).thenReturn(mockScore);
        when(mockFrame.requireScoreView()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        // ZoomController (reached via Actions.initialize → ZoomAction) reads the active
        // view's zoom, so give the mock a real ViewScale rather than a null.
        when(mockScore.getViewScale()).thenReturn(new ViewScale());
        when(mockScore.getMode()).thenReturn(Mode.EDIT);
        when(mockScore.getSelectionSize()).thenReturn(0);
        when(mockScore.getController()).thenReturn(mockCtrl);

        // Actions constructed with this env (e.g. RevertToSavedAction) may compute their
        // initial enabled state from the song, so give it an unmodified default like the
        // real fresh-document state at launch.
        var mockSong = mock(Song.class);
        when(mockSong.isModified()).thenReturn(false);
        when(mockScore.getSong()).thenReturn(mockSong);

        return new MockEnv(mockFrame, mockScore, mockCoordinator, mockCtrl);
    }
}
