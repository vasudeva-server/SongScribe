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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import songscribe.MainFrameMockTest;
import songscribe.dom.Line;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionActionApplier;

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

        try (var applier = mockStatic(SelectionActionApplier.class)) {
            assertThat(action.applyToSelectionIfActive()).isFalse();
            applier.verify(
                () -> SelectionActionApplier.apply(
                    ArgumentMatchers.any(),
                    ArgumentMatchers.any(),
                    ArgumentMatchers.anyBoolean(),
                    ArgumentMatchers.any()
                ),
                never()
            );
        }
    }

    // -- applyToSelectionIfActive: reflectable with active selection --

    /**
     * Asserts that an action carrying the given selected state routes to the applier with
     * exactly that state, and reports having handled the selection itself.
     * <p>
     * The applier is stubbed out rather than run: its mutation pass would reach through the
     * mocked {@link Line} the selection names, and what is under test here is only that the
     * action hands off to it.
     */
    private void assertRoutesToApplier(boolean selected) {
        var selection = new ElementSelection(mock(Line.class), 0, 2);
        when(mockEnv().coordinator().getSelection()).thenReturn(selection);

        var action = FermataAction.createAction(mainFrame());
        action.setSelected(selected);

        try (var applier = mockStatic(SelectionActionApplier.class)) {
            assertThat(action.applyToSelectionIfActive()).isTrue();
            applier.verify(() -> SelectionActionApplier.apply(
                mockEnv().coordinator(), action, selected, mockEnv().score()));
        }
    }

    @Test
    void testReflectableWithSelectionPassesSelectedFalse() {
        assertRoutesToApplier(false);
    }

    @Test
    void testReflectableWithSelectionReturnsTrue() {
        assertRoutesToApplier(true);
    }
}
