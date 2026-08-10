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
import static songscribe.dom.StaffElementFactory.minim;
import static songscribe.dom.StaffElementFactory.quaver;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class DeselectTest extends UnitTest {

    @Test
    void testClearSelectionRemovesAllSelectedElements() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(
                crotchet(),
                quaver(),
                minim()
            ),
            List.of()
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 2);
        assertThat(coordinator.getSelectionSize())
            .as("selection before deselect")
            .isEqualTo(3);

        coordinator.clearSelection();
        assertThat(coordinator.getSelectionSize())
            .as("selection after deselect")
            .isEqualTo(0);
    }

    @Test
    void testClearSelectionOnSingleElement() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(crotchet()),
            List.of()
        );

        ReflectionTestHelper.selectNote(coordinator, 0);
        assertThat(coordinator.getSelectionSize()).isEqualTo(1);

        coordinator.clearSelection();
        assertThat(coordinator.getSelectionSize()).isEqualTo(0);
    }

    @Test
    void testClearSelectionWhenNothingSelected() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(crotchet()),
            List.of()
        );

        // Should not throw when nothing is selected
        coordinator.clearSelection();
        assertThat(coordinator.getSelectionSize()).isEqualTo(0);
    }
}
