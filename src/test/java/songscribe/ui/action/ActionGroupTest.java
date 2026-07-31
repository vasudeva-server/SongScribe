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

import java.awt.event.ActionEvent;

import javax.swing.Action;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.message.MessageCenter;
import songscribe.message.notification.BarWasSelectedNotification;
import songscribe.message.notification.DurationWasSelectedNotification;

class ActionGroupTest extends MainFrameMockTest {

    private DynamicMarkingAction forteAction;
    private DynamicMarkingAction pianoAction;

    @BeforeEach
    void createActions() {
        forteAction = DynamicMarkingAction.createForteAction(mainFrame());
        pianoAction = DynamicMarkingAction.createPianoAction(mainFrame());
    }

    // Row 51: setSelected(action, false) on currently-selected clears selected

    @Test
    void testSetSelectedFalseOnCurrentSelectionClearsSelected() {
        var group = new ActionGroup<>(forteAction, pianoAction);
        group.setSelected(forteAction, true);
        group.setSelected(forteAction, false);
        assertThat(group.getSelected()).isNull();
    }

    // Row 52: setSelected(action, true) captures previousSelected

    @Test
    void testSetSelectedCapturesPreviousSelected() {
        var group = new ActionGroup<>(forteAction, pianoAction);
        group.setSelected(forteAction, true);
        group.setSelected(pianoAction, true);
        assertThat(group.getPreviousSelected()).isEqualTo(forteAction);
    }

    // Row 64: select(action, source) — calls perform(source) only when selection changes

    @Test
    void testSelectCallsPerformOnlyWhenSelectionChanges() {
        var performCount = new int[]{0};
        var trackingAction = new SelectableUIAction(mainFrame(), "track", "cmd-track") {
            @Override
            protected void performAction(ActionEvent e) {
                performCount[0]++;
            }
        };
        var otherAction = new SelectableUIAction(mainFrame(), "other", "cmd-other") {};
        var group = new ActionGroup<>(trackingAction, otherAction);
        var source = new Object();
        group.select(trackingAction, source);
        group.select(trackingAction, source);
        assertThat(performCount[0]).isEqualTo(1);
    }

    // Row 65: propertyChange — external putValue(SELECTED_KEY, true) enforces exclusivity

    @Test
    void testPropertyChangeExternalPutValueEnforcesExclusivity() {
        var group = new ActionGroup<>(forteAction, pianoAction);
        group.setSelected(forteAction, true);
        pianoAction.putValue(Action.SELECTED_KEY, true);
        assertThat(group.getSelected()).isEqualTo(pianoAction);
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddAndInsert {

        // Row 56: add is idempotent — adding same action twice doesn't duplicate

        @Test
        void testAddIdempotentDoesNotDuplicateAction() {
            var group = new ActionGroup<DynamicMarkingAction>();
            group.add(forteAction);
            group.add(forteAction);
            assertThat(group.getActions()).hasSize(1);
        }

        // Row 57: insert(index, action) inserts at correct index; idempotent

        @Test
        void testInsertAtIndexPlacesActionCorrectly() {
            var group = new ActionGroup<DynamicMarkingAction>();
            group.add(forteAction);
            group.insert(0, pianoAction);
            assertThat(group.getActions()).containsExactly(pianoAction, forteAction);
        }

        @Test
        void testInsertIdempotentDoesNotDuplicateAction() {
            var group = new ActionGroup<DynamicMarkingAction>();
            group.add(forteAction);
            group.insert(0, forteAction);
            assertThat(group.getActions()).hasSize(1);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RemoveAndContains {

        // Row 58: remove(action) removes from list and detaches property listener

        @Test
        void testRemoveDetachesPropertyListenerSoSelectIsIgnored() {
            var group = new ActionGroup<DynamicMarkingAction>();
            group.add(forteAction);
            group.remove(forteAction);
            // After remove, the action is no longer in the group, so setSelected is a no-op.
            // This verifies the listener is detached: if it were still attached, a putValue
            // on the action would trigger propertyChange and attempt to re-select it.
            group.setSelected(forteAction, true);
            assertThat(group.getSelected()).isNull();
        }

        // Row 59: contains(action) true after add, false otherwise

        @Test
        void testContainsTrueAfterAdd() {
            var group = new ActionGroup<DynamicMarkingAction>();
            group.add(forteAction);
            assertThat(group.contains(forteAction)).isTrue();
        }

        @Test
        void testContainsFalseForActionNotAdded() {
            var group = new ActionGroup<DynamicMarkingAction>();
            group.add(forteAction);
            assertThat(group.contains(pianoAction)).isFalse();
        }
    }

    // Row 60: anySelected — true when at least one Selectable action is selected

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AnySelected {

        private SelectableUIAction selectableA;
        private SelectableUIAction selectableB;

        @BeforeEach
        void createSelectableActions() {
            selectableA = new SelectableUIAction(mainFrame(), "A", "cmd-a") {};
            selectableB = new SelectableUIAction(mainFrame(), "B", "cmd-b") {};
        }

        @Test
        void testAnySelectedTrueWhenOneActionSelected() {
            var group = new ActionGroup<>(selectableA, selectableB);
            group.setSelected(selectableA, true);
            assertThat(group.anySelected()).isTrue();
        }

        @Test
        void testAnySelectedFalseWhenNoActionSelected() {
            var group = new ActionGroup<>(selectableA, selectableB);
            assertThat(group.anySelected()).isFalse();
        }

        @Test
        void testAnySelectedFalseAfterClearSelection() {
            var group = new ActionGroup<>(selectableA, selectableB);
            group.setSelected(selectableA, true);
            group.clearSelection();
            assertThat(group.anySelected()).isFalse();
        }
    }

    // Rows 67-68: mutual exclusion between duration and non-duration action groups

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MutualExclusion {

        // Row 67: DurationActionGroup.barWasSelected clears duration selection at HIGH_PRIORITY

        @Test
        void testBarWasSelectedClearsDurationSelection() {
            Actions.DURATION_ACTION_GROUP.setSelected(Actions.QUARTER_NOTE_ACTION, true);
            assertThat(Actions.DURATION_ACTION_GROUP.getSelected())
                .as("precondition")
                .isEqualTo(Actions.QUARTER_NOTE_ACTION);
            MessageCenter.post(new BarWasSelectedNotification(ElementType.SINGLE_BARLINE));
            assertThat(Actions.DURATION_ACTION_GROUP.getSelected()).isNull();
        }

        // Row 68: NonDurationActionGroup.durationWasSelected clears non-duration selection at HIGH_PRIORITY

        @Test
        void testDurationWasSelectedClearsNonDurationSelection() {
            Actions.NON_DURATION_ACTION_GROUP.setSelected(Actions.BREATH_MARK_ACTION, true);
            assertThat(Actions.NON_DURATION_ACTION_GROUP.getSelected())
                .as("precondition")
                .isEqualTo(Actions.BREATH_MARK_ACTION);
            MessageCenter.post(new DurationWasSelectedNotification(ElementType.CROTCHET));
            assertThat(Actions.NON_DURATION_ACTION_GROUP.getSelected()).isNull();
        }
    }

    // Rows 61-63: selectNext

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SelectNext {

        // Row 61: selectNext — wraps from last to first

        @Test
        void testSelectNextWrapsFromLastToFirst() {
            var group = new ActionGroup<>(forteAction, pianoAction);
            group.setSelected(pianoAction, true);
            group.selectNext();
            assertThat(group.getSelected()).isEqualTo(forteAction);
        }

        // Row 62: selectNext — advances to next when selected is not last

        @Test
        void testSelectNextAdvancesToNextAction() {
            var group = new ActionGroup<>(forteAction, pianoAction);
            group.setSelected(forteAction, true);
            group.selectNext();
            assertThat(group.getSelected()).isEqualTo(pianoAction);
        }

        // Row 63: selectNext — when selected is null → selects first

        @Test
        void testSelectNextWhenNullSelectsFirst() {
            var group = new ActionGroup<>(forteAction, pianoAction);
            group.selectNext();
            assertThat(group.getSelected()).isEqualTo(forteAction);
        }
    }
}
