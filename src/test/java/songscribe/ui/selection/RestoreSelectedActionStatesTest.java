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

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SelectionCoordinator#restoreSelectedActionStates()}.
 * <p>
 * This is the restore used after a delete. It exists because the previous behavior —
 * discarding the saved states outright — permanently latched both duration action groups
 * empty: selection reflection deselects every duration button for a non-uniform selection,
 * and nothing else ever reselects one. That left edit mode with no preview element and no
 * way to recreate it, so the preview element never reappeared for the rest of the session.
 * <p>
 * The enabled states must NOT be restored — the song has changed, so each action re-derives
 * its own enablement from the new content.
 */
class RestoreSelectedActionStatesTest extends MainFrameMockTest {

    private SelectionCoordinator coordinatorManaging(UIAction... managedActions) {
        return ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of(),
            List.of(managedActions)
        );
    }

    /**
     * The regression test for the latched-toolbar bug: a duration button that reflection
     * deselected must be selected again after the restore, so the action group reports a
     * selection and edit mode can rebuild its preview element.
     */
    @Test
    void testRestoreSelectedActionStatesReselectsDeselectedDurationAction() {
        var quarterNoteAction = Actions.QUARTER_NOTE_ACTION;
        var durationGroup = Actions.DURATION_ACTION_GROUP;
        var coordinator = coordinatorManaging(quarterNoteAction);

        durationGroup.select(quarterNoteAction, quarterNoteAction);
        coordinator.saveActionStates();

        // Reflection deselects every duration button for a non-uniform selection, which
        // drives ActionGroup to null out its selection.
        quarterNoteAction.setSelected(false);

        assertThat(durationGroup.getSelected())
            .as("precondition: reflection must have left the duration group with no selection")
            .isNull();

        coordinator.restoreSelectedActionStates();

        assertThat(durationGroup.getSelected())
            .as("the user's chosen duration button must survive the delete")
            .isSameAs(quarterNoteAction);
    }

    @Test
    void testRestoreSelectedActionStatesDoesNotRestoreEnabledState() {
        var quarterNoteAction = Actions.QUARTER_NOTE_ACTION;
        var coordinator = coordinatorManaging(quarterNoteAction);

        quarterNoteAction.setEnabled(true);
        coordinator.saveActionStates();
        quarterNoteAction.setEnabled(false);

        coordinator.restoreSelectedActionStates();

        assertThat(quarterNoteAction.isEnabled())
            .as("enabled state is stale after a mutation and must be left for the action to re-derive")
            .isFalse();
    }

    @Test
    void testRestoreSelectedActionStatesClearsSavedStates() {
        var coordinator = coordinatorManaging(Actions.QUARTER_NOTE_ACTION);

        coordinator.saveActionStates();

        assertThat(coordinator.hasSavedActionStates())
            .as("precondition: states must be saved before the restore")
            .isTrue();

        coordinator.restoreSelectedActionStates();

        assertThat(coordinator.hasSavedActionStates())
            .as("the restore must consume the saved states")
            .isFalse();
    }

    /**
     * A delete with no prior selection save must not clobber the current toolbar state.
     */
    @Test
    void testRestoreSelectedActionStatesIsNoOpWhenNothingSaved() {
        var quarterNoteAction = Actions.QUARTER_NOTE_ACTION;
        var durationGroup = Actions.DURATION_ACTION_GROUP;
        var coordinator = coordinatorManaging(quarterNoteAction);

        durationGroup.select(quarterNoteAction, quarterNoteAction);

        coordinator.restoreSelectedActionStates();

        assertThat(durationGroup.getSelected())
            .as("a restore with nothing saved must leave the current selection alone")
            .isSameAs(quarterNoteAction);
    }
}
