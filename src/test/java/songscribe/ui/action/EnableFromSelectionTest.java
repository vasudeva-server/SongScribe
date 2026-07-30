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
import static org.mockito.Mockito.when;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;

class EnableFromSelectionTest extends MainFrameMockTest {

    // -- enableFromBarSelection: defer during active selection --

    @Test
    void testEnableFromBarSelectionDefersWithActiveSelection() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);

        var action = createNonReflectableWithBarFlag();

        // Even though the action has DISABLE_WHEN_BAR_SELECTED, the method
        // defers (returns true) when a selection is active.
        assertThat(action.enableFromBarSelection(
            mockEnv().coordinator().hasActiveSelection())).isTrue();
    }

    @Test
    void testEnableFromBarSelectionRunsNormallyWithoutSelection() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(false);

        // Without the flag, the method returns true regardless of selection state.
        var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");

        assertThat(action.enableFromBarSelection(
            mockEnv().coordinator().hasActiveSelection())).isTrue();
    }

    // -- enableFromDurationSelection: defer during active selection --

    @Test
    void testEnableFromDurationSelectionDefersWithActiveSelection() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);

        var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
        action.setFlags(UIAction.Flag.ENABLE_WHEN_DURATION_SELECTED);

        // Even with the flag, the method defers during active selection.
        assertThat(action.enableFromDurationSelection(
            mockEnv().coordinator().hasActiveSelection())).isTrue();
    }

    @Test
    void testEnableFromDurationSelectionRunsNormallyWithoutSelection() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(false);

        var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
        // No ENABLE_WHEN_DURATION_SELECTED flag -> returns true (no check needed)
        assertThat(action.enableFromDurationSelection(
            mockEnv().coordinator().hasActiveSelection())).isTrue();
    }

    // -- enableFromDurationSelection: ENABLE_WHEN_DURATION_SELECTED + no selection + excluded durations → false --

    // The grace duration disables an action only when it opts in with
    // DISABLE_WHEN_GRACE_DURATION_SELECTED. Without that flag the action stays enabled, so a
    // grace note can be given accidentals and articulations before it is placed.

    @Test
    void testEnableFromDurationSelectionReturnsTrueWhenGraceEighthNoteSelectedWithoutOptOut() {
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.GRACE_EIGHTH_NOTE_ACTION, true);
        try {
            var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
            action.setFlags(UIAction.Flag.ENABLE_WHEN_DURATION_SELECTED);

            assertThat(action.enableFromDurationSelection(false)).isTrue();
        } finally {
            Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);
        }
    }

    @Test
    void testEnableFromDurationSelectionReturnsFalseWhenGraceEighthNoteSelectedWithOptOut() {
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.GRACE_EIGHTH_NOTE_ACTION, true);
        try {
            var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
            action.setFlags(
                UIAction.Flag.ENABLE_WHEN_DURATION_SELECTED,
                UIAction.Flag.DISABLE_WHEN_GRACE_DURATION_SELECTED
            );

            assertThat(action.enableFromDurationSelection(false)).isFalse();
        } finally {
            Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);
        }
    }

    // Pins the two actions that opt out. DotActionTest has no Actions environment, so the
    // dot wiring is asserted here alongside the mechanism it depends on.

    @Test
    void testDotActionIsDisabledWhileTheGraceDurationIsSelected() {
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.GRACE_EIGHTH_NOTE_ACTION, true);
        try {
            var dotAction = DotAction.createDotAction(mainFrame());
            assertThat(dotAction.enableFromDurationSelection(false)).isFalse();
        } finally {
            Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);
        }
    }

    @Test
    void testDotActionIsEnabledForOrdinaryDurations() {
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);
        var dotAction = DotAction.createDotAction(mainFrame());
        assertThat(dotAction.enableFromDurationSelection(false)).isTrue();
    }

    @Test
    void testGraceOptOutDoesNotDisableForOrdinaryDurations() {
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);
        var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
        action.setFlags(
            UIAction.Flag.ENABLE_WHEN_DURATION_SELECTED,
            UIAction.Flag.DISABLE_WHEN_GRACE_DURATION_SELECTED
        );

        assertThat(action.enableFromDurationSelection(false)).isTrue();
    }

    // The decoration actions deliberately do NOT opt out with
    // DISABLE_WHEN_GRACE_DURATION_SELECTED: a grace note can carry accidentals and
    // articulations, so they must stay enabled while the grace duration is selected.
    // Pinning the real actions guards against the opt-out flag being added by mistake.

    @Test
    void testAccidentalActionStaysEnabledWhileTheGraceDurationIsSelected() {
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.GRACE_EIGHTH_NOTE_ACTION, true);
        try {
            var accidentalAction = AccidentalAction.createSharpAction(mainFrame());
            assertThat(accidentalAction.enableFromDurationSelection(false)).isTrue();
        } finally {
            Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);
        }
    }

    @Test
    void testArticulationActionStaysEnabledWhileTheGraceDurationIsSelected() {
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.GRACE_EIGHTH_NOTE_ACTION, true);
        try {
            var articulationAction = ArticulationAction.createStaccatoAction(mainFrame());
            assertThat(articulationAction.enableFromDurationSelection(false)).isTrue();
        } finally {
            Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);
        }
    }

    @Test
    void testEnableFromDurationSelectionReturnsFalseWhenGlissandoSelected() {
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.GLISSANDO_ACTION, true);
        try {
            var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
            action.setFlags(UIAction.Flag.ENABLE_WHEN_DURATION_SELECTED);

            assertThat(action.enableFromDurationSelection(false)).isFalse();
        } finally {
            Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);
        }
    }

    @Test
    void testEnableFromDurationSelectionReturnsFalseWhenFallSelected() {
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.FALL_ACTION, true);
        try {
            var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
            action.setFlags(UIAction.Flag.ENABLE_WHEN_DURATION_SELECTED);

            assertThat(action.enableFromDurationSelection(false)).isFalse();
        } finally {
            Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);
        }
    }

    // -- enableFromDurationSelection: ENABLE_WHEN_DURATION_SELECTED + no selection + normal duration → true --

    @Test
    void testEnableFromDurationSelectionReturnsTrueWhenQuarterNoteSelected() {
        Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);

        var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
        action.setFlags(UIAction.Flag.ENABLE_WHEN_DURATION_SELECTED);

        assertThat(action.enableFromDurationSelection(false)).isTrue();
    }

    // -- enableFromSelection: no active selection --

    @Test
    void testNoSelectionReturnsTrue() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(false);

        var action = FermataAction.createAction(mainFrame());

        assertThat(action.enableFromSelection(
            mockEnv().coordinator().hasActiveSelection(), mockEnv().score())).isTrue();
    }

    // -- enableFromSelection: non-reflectable with DISABLE_WHEN_BAR_SELECTED --

    @Test
    void testNonReflectableWithFlagAndDurationsReturnsTrue() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
        when(mockEnv().coordinator().selectionHasDurations()).thenReturn(true);

        var action = createNonReflectableWithBarFlag();

        assertThat(action.enableFromSelection(
            mockEnv().coordinator().hasActiveSelection(), mockEnv().score())).isTrue();
    }

    @Test
    void testNonReflectableWithFlagAndNoDurationsReturnsFalse() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);
        when(mockEnv().coordinator().selectionHasDurations()).thenReturn(false);

        var action = createNonReflectableWithBarFlag();

        assertThat(action.enableFromSelection(
            mockEnv().coordinator().hasActiveSelection(), mockEnv().score())).isFalse();
    }

    @Test
    void testNonReflectableWithoutFlagReturnsTrue() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);

        var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");

        assertThat(action.enableFromSelection(
            mockEnv().coordinator().hasActiveSelection(), mockEnv().score())).isTrue();
    }

    // -- enableFromSelection: reflectable actions --

    @Test
    void testReflectableApplicableReturnsTrue() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);

        var action = FermataAction.createAction(mainFrame());
        when(mockEnv().coordinator().isApplicableToSelection(action)).thenReturn(true);

        assertThat(action.enableFromSelection(
            mockEnv().coordinator().hasActiveSelection(), mockEnv().score())).isTrue();
    }

    @Test
    void testReflectableInapplicableReturnsFalse() {
        when(mockEnv().coordinator().hasActiveSelection()).thenReturn(true);

        var action = AccidentalAction.createSharpAction(mainFrame());
        when(mockEnv().coordinator().isApplicableToSelection(action)).thenReturn(false);

        assertThat(action.enableFromSelection(
            mockEnv().coordinator().hasActiveSelection(), mockEnv().score())).isFalse();
    }

    // -- helpers --

    private UIAction createNonReflectableWithBarFlag() {
        var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
        action.setFlags(UIAction.Flag.DISABLE_WHEN_BAR_SELECTED);
        return action;
    }
}
