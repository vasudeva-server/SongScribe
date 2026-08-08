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
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Ending;
import songscribe.hit.HitTarget;
import songscribe.message.MessageCenter;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;

/**
 * Test utility for setting up a {@link SelectionCoordinator} with a {@link Line}
 * containing specific notes and a custom set of reflectable actions.
 * This bypasses the {@link songscribe.ui.action.Actions} reflection scan
 * that pulls in the full UI singleton graph.
 */
public final class ReflectionTestHelper {

    private ReflectionTestHelper() {
    }

    /**
     * A score-view stub reporting EDIT mode, which is what
     * {@link SelectionCoordinator#isInSelectMode()} derives its answer from. Tests that need
     * select-mode behavior build their own stub rather than using these helpers.
     */
    private static ScoreView editModeScoreView() {
        var scoreView = mock(ScoreView.class);
        when(scoreView.getMode()).thenReturn(Mode.EDIT);
        return scoreView;
    }

    /**
     * A mocked {@link SelectionCoordinator} whose collaborators answer as mocks rather than
     * null, for tests that only need the coordinator to stand there.
     * <p>
     * A coordinator hands out an {@link ActionReflector} and a {@link SelectionDragTracker},
     * and production code calls straight through them — {@code
     * coordinator.getActionReflector().saveActionStates()}. A bare {@code
     * mock(SelectionCoordinator.class)} answers null for both, so every such call NPEs. Tests
     * that want to observe those calls keep their own handle on the returned mock via
     * {@link SelectionCoordinator#getActionReflector()}.
     */
    public static SelectionCoordinator mockCoordinator() {
        var coordinator = mock(SelectionCoordinator.class);
        when(coordinator.getActionReflector()).thenReturn(mock(ActionReflector.class));
        when(coordinator.getDragTracker()).thenReturn(mock(SelectionDragTracker.class));
        return coordinator;
    }

    /**
     * Creates a SelectionCoordinator for an existing Line (e.g. from a fixture),
     * registered and activated at line index 0, with no reflectable actions.
     */
    public static SelectionCoordinator createCoordinatorForLine(Line line) {
        return createCoordinatorForLine(line, List.of());
    }

    /**
     * Creates a SelectionCoordinator for an existing Line, registered and activated at line
     * index 0, with the given actions injected as the reflectable actions list.
     * <p>
     * Unlike the {@code createCoordinator} overloads, this adopts a line the caller already
     * owns rather than building a detached one — which is what a test needs when the line must
     * be a song's real last line, the only place the auto-maintained terminal exists.
     */
    public static SelectionCoordinator createCoordinatorForLine(
        Line line,
        List<UIAction.Reflectable> actions
    ) {
        var coordinator = new SelectionCoordinator(editModeScoreView());
        coordinator.registerLine(0, line);
        coordinator.activateLine(0);
        return createCoordinator(coordinator, actions, List.of());
    }

    /**
     * Creates a SelectionCoordinator with a Line containing the given notes,
     * registered and activated at line index 0, with the given actions
     * injected as the reflectable actions list. The line is backed by a minimal
     * song mock with mutation tracking suspended.
     */
    public static SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions
    ) {
        return createCoordinator(notes, actions, UnitTest.minimalSongMock());
    }

    /**
     * Creates a SelectionCoordinator with a Line backed by the given {@code song},
     * registered and activated at line index 0, with the given actions
     * injected as the reflectable actions list.
     */
    public static SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions,
        Song song
    ) {
        var managedActions = new ArrayList<UIAction>();

        for (var action : actions) {
            managedActions.add((UIAction) action);
        }

        return createCoordinator(notes, actions, managedActions, song);
    }

    /**
     * Creates a SelectionCoordinator with a Line containing the given notes,
     * registered and activated at line index 0, with the given reflectable and
     * managed actions injected. The line is backed by a minimal song mock with
     * mutation tracking suspended.
     */
    public static SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions,
        List<UIAction> managedActions
    ) {
        return createCoordinator(notes, actions, managedActions, UnitTest.minimalSongMock());
    }

    /**
     * Creates a SelectionCoordinator with a Line backed by the given {@code song},
     * registered and activated at line index 0, with the given reflectable and
     * managed actions injected.
     */
    public static SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions,
        List<UIAction> managedActions,
        Song song
    ) {
        var line = new Line(song);

        for (var note : notes) {
            line.addElement(note);
        }

        var coordinator = new SelectionCoordinator(editModeScoreView());
        coordinator.registerLine(0, line);
        coordinator.activateLine(0);

        return createCoordinator(coordinator, actions, managedActions);
    }

    /**
     * Stands up {@link Actions#GRACE_EIGHTH_NOTE_ACTION} if nothing has yet.
     * <p>
     * This helper deliberately bypasses the {@code Actions} singleton graph, but the reflector
     * it hands back does not: every reflection pass ends by enabling or disabling the grace-note
     * action, reading that one static field directly. A test class that never calls
     * {@code Actions.initialize} therefore hits a null there the moment a selection is live and
     * the song changes — and because the failure surfaces inside a message handler, MBassador
     * swallows it and aborts delivery to the post's remaining subscribers, so it reads as an
     * unrelated test failing rather than as missing setup. Which classes happened to run first
     * in the same JVM decided whether a suite passed.
     */
    private static void ensureGraceNoteActionExists() {
        if (Actions.GRACE_EIGHTH_NOTE_ACTION != null) {
            return;
        }

        var action = ElementTypeAction.createGraceEighthNoteAction(
            mock(MainFrame.class, RETURNS_DEEP_STUBS));

        // Every UIAction subscribes itself to the message bus on construction. The reflector
        // only ever calls setEnabled on this one, so leaving it subscribed would buy nothing
        // and cost plenty: its own song-change handler would then run against the deep-stub
        // frame stood up above and throw, which is the very failure this method exists to stop.
        MessageCenter.unsubscribe(action);

        Actions.GRACE_EIGHTH_NOTE_ACTION = action;
    }

    private static SelectionCoordinator createCoordinator(
        SelectionCoordinator coordinator,
        List<UIAction.Reflectable> actions,
        List<UIAction> managedActions
    ) {
        ensureGraceNoteActionExists();

        var reflector = coordinator.getActionReflector();

        try {
            var reflField = ActionReflector.class.getDeclaredField("reflectableActions");
            reflField.setAccessible(true);
            reflField.set(reflector, new ArrayList<>(actions));

            var managedField = ActionReflector.class.getDeclaredField("managedActions");
            managedField.setAccessible(true);
            managedField.set(reflector, new ArrayList<>(managedActions));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to inject test actions", e);
        }

        return coordinator;
    }

    /**
     * Returns the coordinator's active line, failing with a message that names the problem
     * rather than letting the caller trip over a bare NullPointerException when the
     * coordinator was never given an active line.
     */
    private static Line activeLine(SelectionCoordinator coordinator) {
        var line = coordinator.getActiveLine();

        assertThat(line).as("the test coordinator has no active line").isNotNull();

        return line;
    }

    /**
     * Fails unless the coordinator has an active line, so that a target selected below has a
     * line to belong to.
     */
    private static void requireActiveLine(SelectionCoordinator coordinator) {
        activeLine(coordinator);
    }

    /**
     * Selects notes [fromIndex..toIndex] inclusive on the coordinator's active line.
     */
    public static void selectRange(SelectionCoordinator coordinator, int fromIndex, int toIndex) {
        coordinator.getActionReflector().saveActionStates();
        requireActiveLine(coordinator);

        // Anchored at fromIndex, so a backwards range reads as a drag that started at its end.
        coordinator.selectRange(
            Math.min(fromIndex, toIndex), Math.max(fromIndex, toIndex), fromIndex);
    }

    /**
     * Selects a single note on the coordinator's active line.
     */
    public static void selectNote(SelectionCoordinator coordinator, int noteIndex) {
        selectRange(coordinator, noteIndex, noteIndex);
    }

    /**
     * Selects {@code target} on the coordinator's active line, snapshotting the action states
     * first as the production selection path does.
     * <p>
     * The one entry point for selecting anything a click can address; the named helpers below
     * are conveniences over it for the kinds tests reach for most.
     */
    public static void selectTarget(SelectionCoordinator coordinator, HitTarget target) {
        coordinator.getActionReflector().saveActionStates();
        requireActiveLine(coordinator);
        coordinator.select(target);
    }

    /**
     * Selects the glissando owned by the element at the given index.
     */
    public static void selectGlissando(SelectionCoordinator coordinator, int elementIndex) {
        selectTarget(
            coordinator, new HitTarget.Slide(activeLine(coordinator).getElement(elementIndex)));
    }

    /**
     * Selects the given ending on the coordinator's active line.
     */
    public static void selectEnding(SelectionCoordinator coordinator, Ending ending) {
        selectTarget(coordinator, new HitTarget.Ending(ending));
    }

    /**
     * Selects the given hairpin on the coordinator's active line.
     */
    public static void selectHairpin(SelectionCoordinator coordinator, Hairpin hairpin) {
        selectTarget(coordinator, new HitTarget.Hairpin(hairpin));
    }

    /**
     * Clears the selection on the coordinator's active line.
     */
    public static void clearSelection(SelectionCoordinator coordinator) {
        requireActiveLine(coordinator);
        coordinator.clearActiveSelection();
        coordinator.getActionReflector().restoreActionStates();
    }

    /**
     * The {@link Actions#BARLINE_ACTIONS} entry that draws {@code type}.
     */
    public static ElementTypeAction barlineActionFor(ElementType type) {
        for (var action : Actions.BARLINE_ACTIONS) {
            if (action.getType() == type) {
                return action;
            }
        }

        throw new AssertionError("no barline action draws " + type);
    }
}
