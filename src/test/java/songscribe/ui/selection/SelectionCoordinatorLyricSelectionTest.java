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

import java.util.Objects;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;

class SelectionCoordinatorLyricSelectionTest extends UnitTest {

    @Test
    void testSelectLyricClearsElementSelection() {
        var line = detachedLine();
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.CROTCHET.newInstance();
        line.addElement(first);
        line.addElement(second);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        var state = Objects.requireNonNull(coordinator.getLineState(0));
        state.setSelectionFromClick(0);

        coordinator.selectLyric(second, 2);

        assertThat(coordinator.hasActiveSelection()).isFalse();
        assertThat(coordinator.hasLyricSelection()).isTrue();
        assertThat(coordinator.getLyricSelection()).isEqualTo(
            new SelectionCoordinator.LyricSelection(second, 2));
    }

    @Test
    void testElementSelectionClearsLyricSelection() {
        var line = detachedLine();
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.CROTCHET.newInstance();
        line.addElement(first);
        line.addElement(second);
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(line);
        var state = Objects.requireNonNull(coordinator.getLineState(0));
        coordinator.selectLyric(first, 1);

        state.setSelectionFromClick(1);

        assertThat(coordinator.hasLyricSelection()).isFalse();
        assertThat(coordinator.hasActiveSelection()).isTrue();
        assertThat(coordinator.getSingleSelectedElement()).isSameAs(second);
    }
}
