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
import songscribe.dom.Line;
import songscribe.layout.Ending;

/**
 * Unit tests for the cross-line guard logic and per-state delegation in
 * {@link SelectionCoordinator#isElementSelected}, {@link SelectionCoordinator#isLineSelected},
 * and {@link SelectionCoordinator#isSlideSelected}.
 *
 * <p>These methods all share the same shape:
 * <ol>
 *   <li>Return false immediately when {@code lineIndex != activeLineIndex}.</li>
 *   <li>Otherwise delegate to the {@link LineSelectionState} for the active line.</li>
 * </ol>
 * Both branches are exercised for each method.
 */
class SelectionCoordinatorQueryGuardsTest extends UnitTest {

    // Index constants used across tests — avoids raw integer literals in logic.
    private static final int LINE_0 = 0;
    private static final int LINE_1 = 1;
    private static final int ELEMENT_0 = 0;

    // -------------------------------------------------------------------------
    // Shared fixture builder
    // -------------------------------------------------------------------------

    /**
     * Returns a coordinator with two registered lines, each containing one
     * quarter note, with line 0 activated but nothing selected yet.
     */
    private SelectionCoordinator twoLineCoordinator() {
        var song = minimalSongMock();
        var coordinator = new SelectionCoordinator();

        var lineA = new Line(song);
        lineA.addElement(ElementType.CROTCHET.newInstance());
        coordinator.registerLineState(LINE_0, new LineSelectionState(lineA));

        var lineB = new Line(song);
        lineB.addElement(ElementType.CROTCHET.newInstance());
        coordinator.registerLineState(LINE_1, new LineSelectionState(lineB));

        coordinator.activateLine(LINE_0);
        return coordinator;
    }

    // -------------------------------------------------------------------------
    // isElementSelected
    // -------------------------------------------------------------------------

    /**
     * Row 21: isElementSelected returns false when the queried lineIndex does not
     * match the active line — even though the active line has a selection.
     */
    @Test
    void testIsElementSelectedReturnsFalseForInactiveLine() {
        var coordinator = twoLineCoordinator();

        // Select element 0 on the active line (line 0).
        Objects.requireNonNull(coordinator.getActiveSelection()).setSelectionFromClick(ELEMENT_0);

        // Query against line 1, which is not active.
        assertThat(coordinator.isElementSelected(ELEMENT_0, LINE_1))
            .as("isElementSelected(0, line 1) when line 0 is active")
            .isFalse();
    }

    /**
     * Row 22: isElementSelected returns false when the active line has no element
     * selection (state.hasElementSelection() is false).
     */
    @Test
    void testIsElementSelectedReturnsFalseWhenNoElementSelection() {
        var coordinator = twoLineCoordinator();
        // Line 0 is active but nothing is selected.

        assertThat(coordinator.isElementSelected(ELEMENT_0, LINE_0))
            .as("isElementSelected(0, line 0) with no selection on active line")
            .isFalse();
    }

    /**
     * Row 23: isElementSelected delegates to the state when lineIndex matches the
     * active line and the state has a selection — returns true for the selected
     * element.
     */
    @Test
    void testIsElementSelectedDelegatesToStateForActiveLineWithSelection() {
        var coordinator = twoLineCoordinator();

        // Select element 0 on the active line (line 0).
        Objects.requireNonNull(coordinator.getActiveSelection()).setSelectionFromClick(ELEMENT_0);

        assertThat(coordinator.isElementSelected(ELEMENT_0, LINE_0))
            .as("isElementSelected(0, line 0) when element 0 is selected on active line")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // isLineSelected
    // -------------------------------------------------------------------------

    /**
     * Row 24: isLineSelected returns false when the queried lineIndex does not
     * match the active line — even though the active line has a line selection.
     */
    @Test
    void testIsLineSelectedReturnsFalseForInactiveLine() {
        var coordinator = twoLineCoordinator();

        // Activate line 0 and mark it as line-selected.
        Objects.requireNonNull(coordinator.getLineState(LINE_0)).setLineSelected(true);

        // Query against line 1, which is not active.
        assertThat(coordinator.isLineSelected(LINE_1))
            .as("isLineSelected(line 1) when line 0 is active and line-selected")
            .isFalse();
    }

    /**
     * Row 25: isLineSelected delegates to state.isLineSelected for the active line
     * and returns true when that state has been line-selected.
     */
    @Test
    void testIsLineSelectedDelegatesToStateForActiveLine() {
        var coordinator = twoLineCoordinator();

        // Mark line 0 as line-selected via the state's own setter.
        Objects.requireNonNull(coordinator.getLineState(LINE_0)).setLineSelected(true);

        assertThat(coordinator.isLineSelected(LINE_0))
            .as("isLineSelected(line 0) when state has lineSelected=true")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // isGlissandoSelected
    // -------------------------------------------------------------------------

    /**
     * Row 26 (cross-line guard): isGlissandoSelected returns false when the
     * queried lineIndex does not match the active line — even though the active
     * line has a glissando selection.
     */
    @Test
    void testIsGlissandoSelectedReturnsFalseForInactiveLine() {
        var coordinator = twoLineCoordinator();

        // Select the glissando on element 0 of the active line (line 0).
        ReflectionTestHelper.selectGlissando(coordinator, ELEMENT_0);

        // Query against line 1, which is not active.
        assertThat(coordinator.isSlideSelected(ELEMENT_0, LINE_1))
            .as("isGlissandoSelected(0, line 1) when line 0 is active and has glissando selection")
            .isFalse();
    }

    /**
     * Row 26 (delegation): isGlissandoSelected delegates to the state for the
     * active line and returns true when that state has the glissando selected.
     */
    @Test
    void testIsGlissandoSelectedDelegatesToStateForActiveLine() {
        var coordinator = twoLineCoordinator();

        // Select the glissando on element 0 of the active line (line 0).
        ReflectionTestHelper.selectGlissando(coordinator, ELEMENT_0);

        assertThat(coordinator.isSlideSelected(ELEMENT_0, LINE_0))
            .as("isGlissandoSelected(0, line 0) when glissando at element 0 is selected")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // isDecorationSelected / hasEndingSelection
    // -------------------------------------------------------------------------

    /**
     * Builds a standalone {@link Ending}. Ending selection is tracked by reference
     * identity, so the ending does not need to be attached to a line for these tests.
     */
    private static Ending newEnding() {
        return new Ending(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET.newInstance());
    }

    /**
     * Cross-line guard: isDecorationSelected returns false when the queried lineIndex
     * does not match the active line — even though the active line has an ending selected.
     */
    @Test
    void testIsEndingSelectedReturnsFalseForInactiveLine() {
        var coordinator = twoLineCoordinator();
        var ending = newEnding();

        ReflectionTestHelper.selectEnding(coordinator, ending);

        assertThat(coordinator.isDecorationSelected(ending, LINE_1))
            .as("isDecorationSelected(ending, line 1) when line 0 is active and has that ending selected")
            .isFalse();
    }

    /**
     * Delegation: isDecorationSelected delegates to the state for the active line and
     * returns true when that state has the ending selected.
     */
    @Test
    void testIsEndingSelectedDelegatesToStateForActiveLine() {
        var coordinator = twoLineCoordinator();
        var ending = newEnding();

        ReflectionTestHelper.selectEnding(coordinator, ending);

        assertThat(coordinator.isDecorationSelected(ending, LINE_0))
            .as("isDecorationSelected(ending, line 0) when that ending is selected")
            .isTrue();
    }

    /**
     * A different ending on the active line is not reported as selected.
     */
    @Test
    void testIsEndingSelectedReturnsFalseForDifferentEnding() {
        var coordinator = twoLineCoordinator();

        ReflectionTestHelper.selectEnding(coordinator, newEnding());

        assertThat(coordinator.isDecorationSelected(newEnding(), LINE_0))
            .as("isDecorationSelected(other ending, line 0) when a different ending is selected")
            .isFalse();
    }

    @Test
    void testHasEndingSelectionReflectsActiveLineState() {
        var coordinator = twoLineCoordinator();

        assertThat(coordinator.hasEndingSelection())
            .as("hasEndingSelection() before anything is selected")
            .isFalse();

        ReflectionTestHelper.selectEnding(coordinator, newEnding());

        assertThat(coordinator.hasEndingSelection())
            .as("hasEndingSelection() after selecting an ending on the active line")
            .isTrue();
    }
}
