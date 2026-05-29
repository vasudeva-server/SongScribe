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

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.DotAction;

class SelectionContentTest extends MainFrameMockTest {

    // -- isApplicableToSelection --

    @Test
    void testActionNotApplicableToBarlines() {
        // AccidentalAction does not apply to barlines
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.SINGLE_BARLINE.newInstance()),
            List.of(action)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.isApplicableToSelection(action)).isFalse();
    }

    @Test
    void testApplicableActionWithApplicableNotesReturnsTrue() {
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of(action)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.isApplicableToSelection(action)).isTrue();
    }

    @Test
    void testApplicableActionWithMixedNotesReturnsTrue() {
        // AccidentalAction applies to the note but not the rest
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET_REST.newInstance()),
            List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(coordinator.isApplicableToSelection(action)).isTrue();
    }

    @Test
    void testApplicableActionWithNoApplicableNotesReturnsFalse() {
        // AccidentalAction applies only to notes, not rests
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET_REST.newInstance()),
            List.of(action)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.isApplicableToSelection(action)).isFalse();
    }

    @Test
    void testDotActionAppliesToDurations() {
        // DotAction applies to both notes and rests (durations)
        var action = DotAction.createDotAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET_REST.newInstance()),
            List.of(action)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.isApplicableToSelection(action)).isTrue();
    }

    @Test
    void testNoSelectionIsNotApplicable() {
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of(action)
        );

        assertThat(coordinator.isApplicableToSelection(action)).isFalse();
    }

    // -- hasActiveSelection --

    @Test
    void testNoSelectionReturnsNoActiveSelection() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );

        assertThat(coordinator.hasActiveSelection()).isFalse();
    }

    // -- selectionHasDurations --

    @Test
    void testNoSelectionHasDurationsReturnsFalse() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );

        assertThat(coordinator.selectionHasDurations()).isFalse();
    }

    @Test
    void testSelectionWithMixedContentHasDurations() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance(), ElementType.SINGLE_BARLINE.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(coordinator.selectionHasDurations()).isTrue();
    }

    @Test
    void testSelectionWithOnlyDurationsHasDurations() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance(), ElementType.QUAVER_REST.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(coordinator.selectionHasDurations()).isTrue();
    }

    @Test
    void testSelectionWithOnlyNonDurationsHasNoDurations() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.SINGLE_BARLINE.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.selectionHasDurations()).isFalse();
    }

    @Test
    void testWithSelectionReturnsActiveSelection() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.hasActiveSelection()).isTrue();
    }

    // -- selectionHasRests --

    @Test
    void testNoSelectionHasRestsReturnsFalse() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET_REST.newInstance()),
            List.of()
        );

        assertThat(coordinator.selectionHasRests()).isFalse();
    }

    @Test
    void testSelectionContainingRestHasRests() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET_REST.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(coordinator.selectionHasRests()).isTrue();
    }
}
