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

package songscribe.ui.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link SelectionCoordinator} line-state registry and active-line
 * lifecycle: registerLineState, unregisterLineState, clearLineStates,
 * getActiveLineIndex, and activateLine.
 */
class SelectionCoordinatorRegistryTest extends UnitTest {

    // -------------------------------------------------------------------------
    // registerLineState
    // -------------------------------------------------------------------------

    /**
     * Row 1: registerLineState wires the selection-change callback so that when
     * the state fires it (via setSelectionFromClick), the coordinator's lyric
     * selection is cleared.
     */
    @Test
    void testRegisterLineStateWiresSelectionChangeCallbackToClearLyricSelection() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );

        // Establish a lyric selection before triggering the callback.
        var state = coordinator.getActiveSelection();
        assertThat(state).isNotNull();
        var element = state.getLine().getElement(0);
        coordinator.selectLyric(element, 1);
        assertThat(coordinator.hasLyricSelection()).as("lyric selection before callback").isTrue();

        // Firing the state's selection-change callback should clear the lyric selection.
        state.setSelectionFromClick(0);

        assertThat(coordinator.hasLyricSelection())
            .as("lyric selection cleared by selection-change callback")
            .isFalse();
    }

    // -------------------------------------------------------------------------
    // unregisterLineState
    // -------------------------------------------------------------------------

    /**
     * Row 2: unregisterLineState removes the state so getLineState returns null.
     */
    @Test
    void testUnregisterLineStateRemovesStateFromRegistry() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );

        assertThat(coordinator.getLineState(0))
            .as("state present before unregister")
            .isNotNull();

        coordinator.unregisterLineState(0);

        assertThat(coordinator.getLineState(0))
            .as("state absent after unregister")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // clearLineStates
    // -------------------------------------------------------------------------

    /**
     * Row 3: clearLineStates removes all registered states and resets
     * activeLineIndex to -1.
     */
    @Test
    void testClearLineStatesClearsAllStatesAndResetsActiveLineIndex() {
        // Register two states at indices 0 and 1.
        var song = minimalSongMock();
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));

        var lineA = new Line(song);
        var lineB = new Line(song);
        coordinator.registerLineState(0, new LineSelectionState(lineA));
        coordinator.registerLineState(1, new LineSelectionState(lineB));
        coordinator.activateLine(0);

        coordinator.clearLineStates();

        assertThat(coordinator.getLineState(0)).as("line 0 absent after clearLineStates").isNull();
        assertThat(coordinator.getLineState(1)).as("line 1 absent after clearLineStates").isNull();
        assertThat(coordinator.getActiveLineIndex())
            .as("activeLineIndex reset to -1")
            .isEqualTo(-1);
    }

    // -------------------------------------------------------------------------
    // getActiveLineIndex
    // -------------------------------------------------------------------------

    /**
     * Row 4a: getActiveLineIndex returns -1 before any line is activated.
     */
    @Test
    void testGetActiveLineIndexReturnsNegativeOneWhenNoLineActive() {
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));

        assertThat(coordinator.getActiveLineIndex())
            .as("activeLineIndex with no line activated")
            .isEqualTo(-1);
    }

    /**
     * Row 4b: getActiveLineIndex returns the correct index after activateLine.
     */
    @Test
    void testGetActiveLineIndexReturnsCorrectIndexAfterActivation() {
        var song = minimalSongMock();
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));
        coordinator.registerLineState(0, new LineSelectionState(new Line(song)));
        coordinator.registerLineState(1, new LineSelectionState(new Line(song)));

        coordinator.activateLine(1);

        assertThat(coordinator.getActiveLineIndex())
            .as("activeLineIndex after activateLine(1)")
            .isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // activateLine
    // -------------------------------------------------------------------------

    /**
     * Row 5: activateLine sets activeLineIndex and clears the previous line's
     * element selection.
     */
    @Test
    void testActivateLineClearsPreviousLineSelectionAndSetsActiveIndex() {
        var song = minimalSongMock();
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));

        var lineA = new Line(song);
        lineA.addElement(ElementType.CROTCHET.newInstance());
        var stateA = new LineSelectionState(lineA);

        var lineB = new Line(song);
        lineB.addElement(ElementType.CROTCHET.newInstance());
        var stateB = new LineSelectionState(lineB);

        coordinator.registerLineState(0, stateA);
        coordinator.registerLineState(1, stateB);

        // Activate line 0 and select its element.
        coordinator.activateLine(0);
        stateA.setSelectionFromClick(0);
        assertThat(stateA.hasElementSelection())
            .as("line 0 has a selection before activating line 1")
            .isTrue();

        // Activating line 1 should clear line 0's selection.
        coordinator.activateLine(1);

        assertThat(coordinator.getActiveLineIndex())
            .as("activeLineIndex is 1 after activateLine(1)")
            .isEqualTo(1);
        assertThat(stateA.hasElementSelection())
            .as("line 0 selection cleared after activating line 1")
            .isFalse();
    }
}
