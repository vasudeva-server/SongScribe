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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import songscribe.MainFrameMockTest;
import songscribe.dom.Line;
import songscribe.ui.selection.ElementSelection;

class ApplyToSelectionInterceptTest extends MainFrameMockTest {

    // -- applyToSelectionIfActive: non-reflectable --

    @Test
    void testNonReflectableReturnsFalse() {
        var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
        assertThat(action.applyToSelectionIfActive()).isFalse();
    }

    // -- applyToSelectionIfActive: no selection --

    @Test
    void testReflectableWithNoSelectionReturnsFalse() {
        when(mockEnv().coordinator().getSelection()).thenReturn(null);

        var action = FermataAction.createAction(mainFrame());

        assertThat(action.applyToSelectionIfActive()).isFalse();
        verify(mockEnv().coordinator(), never()).applyActionToSelection(
            ArgumentMatchers.any(),
            ArgumentMatchers.anyBoolean(),
            ArgumentMatchers.any()
        );
    }

    // -- applyToSelectionIfActive: reflectable with active selection --

    @Test
    void testReflectableWithSelectionPassesSelectedFalse() {
        var selection = new ElementSelection(mock(Line.class), 0, 2);
        when(mockEnv().coordinator().getSelection()).thenReturn(selection);

        var action = FermataAction.createAction(mainFrame());
        action.setSelected(false);

        assertThat(action.applyToSelectionIfActive()).isTrue();
        verify(mockEnv().coordinator()).applyActionToSelection(action, false, mockEnv().score());
    }

    @Test
    void testReflectableWithSelectionReturnsTrue() {
        var selection = new ElementSelection(mock(Line.class), 0, 2);
        when(mockEnv().coordinator().getSelection()).thenReturn(selection);

        var action = FermataAction.createAction(mainFrame());
        action.setSelected(true);

        assertThat(action.applyToSelectionIfActive()).isTrue();
        verify(mockEnv().coordinator()).applyActionToSelection(action, true, mockEnv().score());
    }
}
