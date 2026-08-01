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

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;

/**
 * Unit tests for {@link SelectionCoordinator#getSelectedLine} and
 * {@link SelectionCoordinator#getSelectedElements}.
 *
 * <p>Rows 40–42 of the selection coordinator test matrix.
 */
class GetSelectedLineAndElementsTest extends UnitTest {

    private static final int LINE_0 = 0;
    private static final int ELEMENT_0 = 0;
    private static final int ELEMENT_1 = 1;
    private static final int ELEMENT_2 = 2;

    // -------------------------------------------------------------------------
    // getSelectedLine — row 40
    // -------------------------------------------------------------------------

    /**
     * Row 40 (positive): getSelectedLine returns activeLineIndex when the active
     * line has a line-selection.
     */
    @Test
    void testGetSelectedLineReturnsActiveLineIndexWhenLineSelected() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );
        var lineState = coordinator.getLineState(LINE_0);
        assertThat(lineState).isNotNull();

        lineState.setLineSelected(true);

        assertThat(coordinator.getSelectedLine())
            .as("getSelectedLine when active line has line-selection")
            .isEqualTo(LINE_0);
    }

    /**
     * Row 40 (negative): getSelectedLine returns -1 when the active line does
     * not have a line-selection.
     */
    @Test
    void testGetSelectedLineReturnsMinusOneWhenLineNotLineSelected() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );
        // Active line is registered but not line-selected.

        assertThat(coordinator.getSelectedLine())
            .as("getSelectedLine when active line has no line-selection")
            .isEqualTo(-1);
    }

    // -------------------------------------------------------------------------
    // getSelectedElements — row 41
    // -------------------------------------------------------------------------

    /**
     * Row 41: getSelectedElements returns an empty list when there is no selection.
     */
    @Test
    void testGetSelectedElementsReturnsEmptyListWhenNoSelection() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );
        // Nothing selected.

        assertThat(coordinator.getSelectedElements())
            .as("getSelectedElements with no selection")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // getSelectedElements — row 42
    // -------------------------------------------------------------------------

    /**
     * Row 42: getSelectedElements returns the correct elements in order for a
     * multi-element selection range. Asserts element identity (same object
     * references) and order.
     */
    @Test
    void testGetSelectedElementsReturnsCorrectElementsInOrder() {
        var first = ElementType.CROTCHET.newInstance();
        var second = ElementType.QUAVER.newInstance();
        var third = ElementType.MINIM.newInstance();

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(first, second, third),
            List.of()
        );
        ReflectionTestHelper.selectRange(coordinator, ELEMENT_0, ELEMENT_2);

        var elements = coordinator.getSelectedElements();

        assertThat(elements)
            .as("getSelectedElements element count for range [0..2]")
            .hasSize(3);
        assertThat(elements.get(ELEMENT_0))
            .as("getSelectedElements element at index 0")
            .isSameAs(first);
        assertThat(elements.get(ELEMENT_1))
            .as("getSelectedElements element at index 1")
            .isSameAs(second);
        assertThat(elements.get(ELEMENT_2))
            .as("getSelectedElements element at index 2")
            .isSameAs(third);
    }

    /**
     * Row 42 (single-element subcase): getSelectedElements returns a single-element
     * list when exactly one element is selected.
     */
    @Test
    void testGetSelectedElementsReturnsSingletonListForSingleElementSelection() {
        var note = ElementType.CROTCHET.newInstance();

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note, ElementType.QUAVER.newInstance()),
            List.of()
        );
        ReflectionTestHelper.selectNote(coordinator, ELEMENT_0);

        var elements = coordinator.getSelectedElements();

        assertThat(elements)
            .as("getSelectedElements element count for single-element selection")
            .hasSize(1);
        assertThat(elements.get(ELEMENT_0))
            .as("getSelectedElements sole element identity")
            .isSameAs(note);
    }
}
