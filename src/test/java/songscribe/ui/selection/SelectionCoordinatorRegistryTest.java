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
import static songscribe.dom.StaffElementFactory.crotchet;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link SelectionCoordinator} line-state registry and active-line
 * lifecycle: registerLine, unregisterLine, clearLines,
 * getActiveLineIndex, and activateLine.
 */
class SelectionCoordinatorRegistryTest extends UnitTest {

    // -------------------------------------------------------------------------
    // registerLine
    // -------------------------------------------------------------------------

    /**
     * Row 1: selecting a range displaces a target with no callback in the picture. The two
     * shapes are one field, so the assignment is the whole of the exclusion — this is what
     * the range-change callback used to have to arrange across an object boundary.
     */
    @Test
    void testSelectingARangeDisplacesTheTargetSelection() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(crotchet()),
            List.of()
        );

        var line = coordinator.getActiveLine();
        assertThat(line).isNotNull();
        coordinator.selectLyric(line.getElement(0), 1);
        assertThat(coordinator.getSelectedTarget()).as("target before the range is set").isNotNull();

        coordinator.selectSingleElement(0, 0);

        assertThat(coordinator.getSelectedTarget())
            .as("target displaced by the range")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // unregisterLine
    // -------------------------------------------------------------------------

    /**
     * Row 2: unregisterLine removes the registration so getLine returns null.
     */
    @Test
    void testUnregisterLineRemovesItFromTheRegistry() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(crotchet()),
            List.of()
        );

        assertThat(coordinator.getLine(0))
            .as("state present before unregister")
            .isNotNull();

        coordinator.unregisterLine(0);

        assertThat(coordinator.getLine(0))
            .as("state absent after unregister")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // clearLines
    // -------------------------------------------------------------------------

    /**
     * Row 3: clearLines removes all registrations and resets
     * activeLineIndex to -1.
     */
    @Test
    void testClearLineStatesClearsAllStatesAndResetsActiveLineIndex() {
        // Register two states at indices 0 and 1.
        var song = minimalSongMock();
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));

        var lineA = new Line(song);
        var lineB = new Line(song);
        coordinator.registerLine(0, lineA);
        coordinator.registerLine(1, lineB);
        coordinator.activateLine(0);

        coordinator.clearLines();

        assertThat(coordinator.getLine(0)).as("line 0 absent after clearLines").isNull();
        assertThat(coordinator.getLine(1)).as("line 1 absent after clearLines").isNull();
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
        coordinator.registerLine(0, new Line(song));
        coordinator.registerLine(1, new Line(song));

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
        lineA.addElement(crotchet());

        var lineB = new Line(song);
        lineB.addElement(crotchet());

        coordinator.registerLine(0, lineA);
        coordinator.registerLine(1, lineB);

        // Activate line 0 and select its element.
        coordinator.selectSingleElement(0, 0);
        assertThat(coordinator.isElementSelected(0, 0))
            .as("line 0 has a selection before activating line 1")
            .isTrue();

        // Activating line 1 should clear line 0's selection.
        coordinator.activateLine(1);

        assertThat(coordinator.getActiveLineIndex())
            .as("activeLineIndex is 1 after activateLine(1)")
            .isEqualTo(1);
        assertThat(coordinator.getRange())
            .as("line 0 selection cleared after activating line 1")
            .isNull();
    }
}
