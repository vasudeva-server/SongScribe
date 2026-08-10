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

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JRootPane;

import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.SelectionCoordinator;

/** Shared setup for test classes that read the {@link Actions} constants. */
public final class ActionsTestSupport {

    private ActionsTestSupport() {
    }

    /**
     * Populates the {@code Actions.*} constants, which are deferred-init fields left null
     * until the application starts.
     *
     * <p>Call this from a {@code @BeforeEach} in any class that reads one of them, rather than
     * depending on some earlier test class having initialized {@code Actions} in the shared JVM
     * — that hidden ordering coupling breaks the moment the class runs on its own.
     * {@code UnitTest}'s teardown unsubscribes the actions again.
     *
     * <p>The frame is mocked down to a real {@link InputMap}/{@link ActionMap}, because
     * {@code Actions.initialize} registers every action's accelerator on the root pane.
     */
    public static void initializeActions() {
        var mockFrame = mock(MainFrame.class);
        var mockScore = mock(ScoreView.class);
        var mockRootPane = mock(JRootPane.class);
        when(mockRootPane.getInputMap(anyInt())).thenReturn(new InputMap());
        when(mockRootPane.getActionMap()).thenReturn(new ActionMap());
        when(mockFrame.getRootPane()).thenReturn(mockRootPane);
        when(mockFrame.requireScoreView()).thenReturn(mockScore);
        when(mockFrame.getScoreView()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));
        Actions.initialize(mockFrame);
    }
}
