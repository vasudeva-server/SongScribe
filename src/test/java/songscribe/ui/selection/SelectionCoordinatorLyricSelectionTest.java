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
import static songscribe.dom.StaffElementFactory.crotchet;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.hit.HitTarget;

class SelectionCoordinatorLyricSelectionTest extends UnitTest {

    @Test
    void testSelectLyricClearsElementSelection() {
        var line = detachedLine();
        var first = crotchet();
        var second = crotchet();
        line.addElement(first);
        line.addElement(second);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        coordinator.selectSingleElement(0, 0);

        coordinator.selectLyric(second, 2);

        assertThat(coordinator.hasActiveSelection()).isFalse();
        assertThat(coordinator.getSelectedTarget()).isEqualTo(new HitTarget.Lyric(second, 2));
    }

    @Test
    void testElementSelectionClearsLyricSelection() {
        var line = detachedLine();
        var first = crotchet();
        var second = crotchet();
        line.addElement(first);
        line.addElement(second);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        coordinator.selectLyric(first, 1);

        coordinator.selectSingleElement(0, 1);

        assertThat(coordinator.getSelectedTarget()).isNull();
        assertThat(coordinator.hasActiveSelection()).isTrue();
        assertThat(coordinator.getSingleSelectedElement()).isSameAs(second);
    }

    /**
     * Row 6: activateLine clears lyric selection.
     */
    @Test
    void testActivateLineClearsLyricSelection() {
        var line = detachedLine();
        var element = crotchet();
        line.addElement(element);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        coordinator.selectLyric(element, 1);
        assertThat(coordinator.getSelectedTarget()).as("lyric selection before activateLine").isNotNull();

        coordinator.activateLine(0);

        assertThat(coordinator.getSelectedTarget())
            .as("lyric selection cleared after activateLine")
            .isNull();
    }

    /**
     * Row 7: clearSelection clears lyric selection.
     */
    @Test
    void testClearSelectionClearsLyricSelection() {
        var line = detachedLine();
        var element = crotchet();
        line.addElement(element);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        coordinator.selectLyric(element, 1);
        assertThat(coordinator.getSelectedTarget()).as("lyric selection before clearSelection").isNotNull();

        coordinator.clearSelection();

        assertThat(coordinator.getSelectedTarget())
            .as("lyric selection cleared after clearSelection")
            .isNull();
    }

    /**
     * Row 15: selectLyric on an element whose line is not registered leaves
     * activeLineIndex at -1 (findLineIndex returns -1 for unknown lines), and no query
     * reports the lyric as selected — every one of them is guarded on the active line, and
     * -1 is not a line index any caller asks about.
     */
    @Test
    void testSelectLyricOnUnregisteredLineYieldsNegativeOneActiveLineIndex() {
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(detachedLine());

        // Build a fresh, unregistered line with an element.
        var unregisteredLine = detachedLine();
        var element = crotchet();
        unregisteredLine.addElement(element);

        coordinator.selectLyric(element, 1);

        assertThat(coordinator.getActiveLineIndex())
            .as("activeLineIndex after selectLyric on unregistered line")
            .isEqualTo(-1);
        assertThat(coordinator.isSelected(new HitTarget.Lyric(element, 1), 0))
            .as("isSelected(lyric) on line 0 after selecting a lyric on an unregistered line")
            .isFalse();
    }

    /**
     * An element removed from its line is in no line at all, so there is no lyric to select
     * and no line index to activate. The guard has to run before the existing selection is
     * cleared: placed after, it would leave the user staring at a selection that vanished
     * for no visible reason.
     */
    @Test
    void testSelectLyricOnAnElementInNoLineLeavesTheExistingSelectionAlone() {
        var line = detachedLine();
        var kept = crotchet();
        var removed = crotchet();
        line.addElement(kept);
        line.addElement(removed);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        coordinator.selectSingleElement(0, 0);

        line.removeElement(line.getElementIndex(removed));
        coordinator.selectLyric(removed, 1);

        assertThat(coordinator.hasActiveSelection())
            .as("the element selection made before the doomed selectLyric call")
            .isTrue();
        assertThat(coordinator.getSelectedTarget())
            .as("no lyric target was selected on an element that is in no line")
            .isNotEqualTo(new HitTarget.Lyric(removed, 1));
    }
}
