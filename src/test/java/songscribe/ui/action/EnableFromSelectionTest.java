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
import static songscribe.dom.StaffElementFactory.crotchet;

import module java.desktop;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.Song;
import songscribe.ui.Mode;
import songscribe.ui.selection.SelectionCoordinator;

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

    // -- enableFromSelection: terminal selected (issue #713) --
    //
    // These drive a real SelectionCoordinator holding a real auto-maintained terminal rather
    // than stubbing selectionHasDurations(), because that stub is what the terminal case would
    // have to get wrong: the predicate has no terminal-specific branch, so hand-feeding it
    // "false" would pass identically for any barline and prove nothing about the terminal.
    //
    // BeatChangeAction and TempoChangeAction carry DISABLE_WHEN_BAR_SELECTED, so they gate on
    // selectionHasDurations(). AnnotationAction carries no such flag and stays enabled: an
    // annotation on the terminal is the entire point of issue #713.

    @Test
    void testAnnotationActionStaysEnabledWhileTheTerminalIsSelected() {
        var coordinator = selectTerminal();

        var action = AnnotationAction.createAction(mainFrame());

        assertThat(action.enableFromSelection(
            coordinator.hasActiveSelection(), mockEnv().score())).isTrue();
    }

    @Test
    void testBeatChangeActionIsDisabledWhileTheTerminalIsSelected() {
        var coordinator = selectTerminal();

        var action = BeatChangeAction.createAction(mainFrame());

        assertThat(action.enableFromSelection(
            coordinator.hasActiveSelection(), mockEnv().score())).isFalse();
    }

    @Test
    void testTempoChangeActionIsDisabledWhileTheTerminalIsSelected() {
        var coordinator = selectTerminal();

        var action = TempoChangeAction.createAction(mainFrame());

        assertThat(action.enableFromSelection(
            coordinator.hasActiveSelection(), mockEnv().score())).isFalse();
    }

    // -- helpers --

    /**
     * Selects the song's real auto-maintained terminal behind a real
     * {@link SelectionCoordinator}, so the actions above compute their enabled state against
     * the actual terminal element instead of a hand-fed boolean.
     */
    private SelectionCoordinator selectTerminal() {
        var song = new Song();
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(crotchet()));

        when(mockEnv().score().getMode()).thenReturn(Mode.SELECT);

        var coordinator = new SelectionCoordinator(mockEnv().score());
        when(mockEnv().score().getSelectionCoordinator()).thenReturn(coordinator);
        when(mockEnv().score().getSelectionSize()).thenReturn(1);

        coordinator.registerLine(0, line);
        coordinator.activateLine(0);
        coordinator.selectSingleElement(0, line.elementCount() - 1);

        assertThat(coordinator.isTerminalSelected())
            .as("the fixture must actually select the terminal, or these tests prove nothing")
            .isTrue();

        return coordinator;
    }

    private UIAction createNonReflectableWithBarFlag() {
        var action = new UIAction(mainFrame(), "Test", null, 0, "test", "Test");
        action.setFlags(UIAction.Flag.DISABLE_WHEN_BAR_SELECTED);
        return action;
    }
}
