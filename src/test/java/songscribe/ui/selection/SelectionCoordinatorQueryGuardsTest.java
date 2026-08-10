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

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Ending;
import songscribe.hit.HitTarget;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for the cross-line guard logic in {@link SelectionCoordinator#isElementSelected},
 * {@link SelectionCoordinator#isLineSelected} and {@link SelectionCoordinator#isSelected}.
 *
 * <p>These methods all share the same shape:
 * <ol>
 *   <li>Return false immediately when {@code lineIndex != activeLineIndex}.</li>
 *   <li>Otherwise answer from the selected {@link Selection.Range}, or
 *       from the coordinator's own single selected target.</li>
 * </ol>
 * Both branches are exercised for each method. The guard is what it always was; what changed
 * is that the target half of the answer is now one field here rather than one per line.
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
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));

        var lineA = new Line(song);
        lineA.addElement(crotchet());
        coordinator.registerLine(LINE_0, lineA);

        var lineB = new Line(song);
        lineB.addElement(crotchet());
        coordinator.registerLine(LINE_1, lineB);

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
        coordinator.selectSingleElement(LINE_0, ELEMENT_0);

        // Query against line 1, which is not active.
        assertThat(coordinator.isElementSelected(ELEMENT_0, LINE_1))
            .as("isElementSelected(0, line 1) when line 0 is active")
            .isFalse();
    }

    /**
     * Row 22: isElementSelected returns false when nothing is selected on the active line.
     * Since an empty range can no longer be stored, "no selection" is a null selection and
     * the query answers false for every index.
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
     * Row 23: isElementSelected answers from the range when lineIndex matches the active
     * line and a range is selected — true for the selected element.
     */
    @Test
    void testIsElementSelectedAnswersFromTheRangeOnTheActiveLine() {
        var coordinator = twoLineCoordinator();

        // Select element 0 on the active line (line 0).
        coordinator.selectSingleElement(LINE_0, ELEMENT_0);

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

        coordinator.select(new HitTarget.StaffLine());

        // Query against line 1, which is not active.
        assertThat(coordinator.isLineSelected(LINE_1))
            .as("isLineSelected(line 1) when line 0 is active and line-selected")
            .isFalse();
    }

    /**
     * Row 25: isLineSelected answers from the coordinator's own target for the active line
     * and returns true when that target is the staff line.
     */
    @Test
    void testIsLineSelectedAnswersForTheActiveLine() {
        var coordinator = twoLineCoordinator();

        coordinator.select(new HitTarget.StaffLine());

        assertThat(coordinator.isLineSelected(LINE_0))
            .as("isLineSelected(line 0) when the staff line is the selected target")
            .isTrue();
    }

    // -------------------------------------------------------------------------
    // isSelected: a slide
    // -------------------------------------------------------------------------

    /**
     * Row 26 (cross-line guard): a selected slide is not reported on a line other than the
     * active one, even though it is the score's one selected target.
     */
    @Test
    void testIsSlideSelectedReturnsFalseForInactiveLine() {
        var coordinator = twoLineCoordinator();

        // Select the glissando on element 0 of the active line (line 0).
        ReflectionTestHelper.selectGlissando(coordinator, ELEMENT_0);

        var slide = selectedSlide(coordinator);

        // Query against line 1, which is not active.
        assertThat(coordinator.isSelected(slide, LINE_1))
            .as("isSelected(slide, line 1) when line 0 is active and has that slide selected")
            .isFalse();
    }

    /**
     * Row 26 (answer): the selected slide is reported on the active line.
     */
    @Test
    void testIsSlideSelectedAnswersForTheActiveLine() {
        var coordinator = twoLineCoordinator();

        // Select the glissando on element 0 of the active line (line 0).
        ReflectionTestHelper.selectGlissando(coordinator, ELEMENT_0);

        assertThat(coordinator.isSelected(selectedSlide(coordinator), LINE_0))
            .as("isSelected(slide, line 0) when the slide at element 0 is selected")
            .isTrue();
    }

    /**
     * Returns the slide target the coordinator currently holds, so a query can name the same
     * thing the helper selected without the test rebuilding it from the line.
     */
    private static HitTarget selectedSlide(SelectionCoordinator coordinator) {
        var target = coordinator.getSelectedTarget();
        assertThat(target).as("the selected target").isInstanceOf(HitTarget.Slide.class);
        return target;
    }

    // -------------------------------------------------------------------------
    // isSelected: an ending / hasDecorationSelection
    // -------------------------------------------------------------------------

    /**
     * Builds a standalone {@link Ending}. Ending selection is tracked by reference
     * identity, so the ending does not need to be attached to a line for these tests.
     */
    private static Ending newEnding() {
        return new Ending(crotchet(), crotchet());
    }

    /**
     * Cross-line guard: a selected ending is not reported on a line other than the active one.
     */
    @Test
    void testIsEndingSelectedReturnsFalseForInactiveLine() {
        var coordinator = twoLineCoordinator();
        var ending = newEnding();

        ReflectionTestHelper.selectEnding(coordinator, ending);

        assertThat(coordinator.isSelected(new HitTarget.Ending(ending), LINE_1))
            .as("isSelected(ending, line 1) when line 0 is active and has that ending selected")
            .isFalse();
    }

    /**
     * The selected ending is reported on the active line.
     */
    @Test
    void testIsEndingSelectedAnswersForTheActiveLine() {
        var coordinator = twoLineCoordinator();
        var ending = newEnding();

        ReflectionTestHelper.selectEnding(coordinator, ending);

        assertThat(coordinator.isSelected(new HitTarget.Ending(ending), LINE_0))
            .as("isSelected(ending, line 0) when that ending is selected")
            .isTrue();
    }

    /**
     * A different ending on the active line is not reported as selected.
     */
    @Test
    void testIsEndingSelectedReturnsFalseForDifferentEnding() {
        var coordinator = twoLineCoordinator();

        ReflectionTestHelper.selectEnding(coordinator, newEnding());

        assertThat(coordinator.isSelected(new HitTarget.Ending(newEnding()), LINE_0))
            .as("isSelected(other ending, line 0) when a different ending is selected")
            .isFalse();
    }

    @Test
    void testHasDecorationSelectionReflectsActiveLineState() {
        var coordinator = twoLineCoordinator();

        assertThat(coordinator.hasDecorationSelection())
            .as("hasDecorationSelection() before anything is selected")
            .isFalse();

        ReflectionTestHelper.selectEnding(coordinator, newEnding());

        assertThat(coordinator.hasDecorationSelection())
            .as("hasDecorationSelection() after selecting an ending on the active line")
            .isTrue();
    }
}
