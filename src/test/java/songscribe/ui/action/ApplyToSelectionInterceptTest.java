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

import java.awt.event.ActionEvent;

import songscribe.UnitTest;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.Mode;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.selection.NoteSelection;
import songscribe.ui.selection.SelectionCoordinator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplyToSelectionInterceptTest extends UnitTest {

    // -- applyToSelectionIfActive: reflectable with active selection --

    @Test
    void testReflectableWithSelectionReturnsTrue() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            var selection = new NoteSelection(mock(Line.class), 0, 2);
            when(env.coordinator.getSelection()).thenReturn(selection);

            var action = new FermataAction();
            action.setSelected(true);

            assertThat(action.applyToSelectionIfActive()).isTrue();
            verify(env.coordinator).applyActionToSelection(action, true);
        }
    }

    @Test
    void testReflectableWithSelectionPassesSelectedFalse() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            var selection = new NoteSelection(mock(Line.class), 0, 2);
            when(env.coordinator.getSelection()).thenReturn(selection);

            var action = new FermataAction();
            action.setSelected(false);

            assertThat(action.applyToSelectionIfActive()).isTrue();
            verify(env.coordinator).applyActionToSelection(action, false);
        }
    }

    // -- applyToSelectionIfActive: no selection --

    @Test
    void testReflectableWithNoSelectionReturnsFalse() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);
            when(env.coordinator.getSelection()).thenReturn(null);

            var action = new FermataAction();

            assertThat(action.applyToSelectionIfActive()).isFalse();
            verify(env.coordinator, never()).applyActionToSelection(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean()
            );
        }
    }

    // -- applyToSelectionIfActive: non-reflectable --

    @Test
    void testNonReflectableReturnsFalse() {
        try (var mainFrameMock = mockStatic(MainFrame.class)) {
            var env = setupMockEnv(mainFrameMock);

            var action = new UIAction("Test", null, 0, "test", "Test") {
                @Override
                public void actionPerformed(ActionEvent e) {}
            };

            assertThat(action.applyToSelectionIfActive()).isFalse();
        }
    }

    // -- helpers --

    private record MockEnv(
        MainFrame frame,
        Score score,
        SelectionCoordinator coordinator
    ) {}

    private MockEnv setupMockEnv(
        org.mockito.MockedStatic<MainFrame> mainFrameMock
    ) {
        var mockFrame = mock(MainFrame.class);
        var mockScore = mock(Score.class);
        var mockCoordinator = mock(SelectionCoordinator.class);

        mainFrameMock.when(MainFrame::getInstance).thenReturn(mockFrame);
        when(mockFrame.getScore()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);
        when(mockScore.getMode()).thenReturn(Mode.NOTE_EDIT);
        when(mockScore.getSelectionSize()).thenReturn(0);

        return new MockEnv(mockFrame, mockScore, mockCoordinator);
    }
}
