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

package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.*;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;

/**
 * E2E tests for toolbar button reflection when notes are selected.
 * Verifies that selecting notes in select mode causes the toolbar buttons
 * to reflect the attributes of the selected notes.
 */
class ToolbarReflectionTest extends E2ETest {

    @Nested
    class SingleElementReflection {

        @Test
        void testSingleNoteSelection() {
            buildNotes(Actions.QUARTER_NOTE_ACTION, 0);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));

            assertActionSelected(Actions.QUARTER_NOTE_ACTION, true);
            assertActionSelected(Actions.HALF_NOTE_ACTION, false);
            assertActionSelected(Actions.EIGHTH_NOTE_ACTION, false);
            assertActionSelected(Actions.FLAT_ACTION, false);
            assertActionSelected(Actions.SHARP_ACTION, false);
            assertActionSelected(Actions.DOT_ACTION, false);

            // Verify toolbar button model mirrors the action state
            assertButtonSelected(Actions.QUARTER_NOTE_ACTION, true);
            assertButtonSelected(Actions.FLAT_ACTION, false);
            assertButtonSelected(Actions.DOT_ACTION, false);
        }

        @Test
        void testSingleRestSelection() {
            // Insert a quarter rest
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            enableRestMode();
            clickAt(insertionPoint(0, 0));
            performLayout(0);
            deselectRestMode();

            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));

            // DURATION actions: appliesTo=true but matchesNote=false (CROTCHET_REST != CROTCHET)
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, false);
            assertActionSelected(Actions.HALF_NOTE_ACTION, false);
            assertActionSelected(Actions.EIGHTH_NOTE_ACTION, false);

            // NoteOnlyAction subclasses: appliesTo=false for rests
            assertActionSelected(Actions.FLAT_ACTION, false);
            assertActionSelected(Actions.FERMATA_ACTION, false);
            assertActionSelected(Actions.STACCATO_ACTION, false);
        }
    }

    @Nested
    class MultiElementReflection {

        @Test
        void testMultipleIdenticalNotes() {
            buildNotes(Actions.QUARTER_NOTE_ACTION, 0, -2);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            assertThat(score().getSelectionSize()).isEqualTo(2);
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, true);
            assertActionSelected(Actions.FLAT_ACTION, false);
            assertActionSelected(Actions.DOT_ACTION, false);
        }

        @Test
        void testMultipleDifferentDurations() {
            // Insert a crotchet then a minim
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            selectDuration(Actions.HALF_NOTE_ACTION);
            clickAt(insertionPoint(0, -2));
            performLayout(0);

            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            assertThat(score().getSelectionSize()).isEqualTo(2);
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, false);
            assertActionSelected(Actions.HALF_NOTE_ACTION, false);
        }

        @Test
        void testMultipleDifferentAccidentals() {
            // Insert crotchet with flat
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickToolbarButton(Actions.FLAT_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            // Insert crotchet with double-flat (ActionGroup auto-deselects flat)
            clickToolbarButton(Actions.DOUBLE_FLAT_ACTION);
            clickAt(insertionPoint(0, -2));
            performLayout(0);

            // Deselect accidental
            clickToolbarButton(Actions.DOUBLE_FLAT_ACTION);

            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            assertThat(score().getSelectionSize()).isEqualTo(2);
            assertActionSelected(Actions.FLAT_ACTION, false);
            assertActionSelected(Actions.DOUBLE_FLAT_ACTION, false);
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, true);
        }
    }

    @Nested
    class MixedTypeReflection {

        @Test
        void testNoteAndRestSelection() {
            // Insert a crotchet then a crotchet rest
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            enableRestMode();
            clickAt(insertionPoint(0, 0));
            performLayout(0);
            deselectRestMode();

            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            assertThat(score().getSelectionSize()).isEqualTo(2);

            // CROTCHET action: applicable to both (DURATION appliesTo=true for all),
            // but matchesNote=false for the rest (CROTCHET_REST != CROTCHET)
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, false);
            assertActionSelected(Actions.FLAT_ACTION, false);
        }

        @Test
        void testDifferentDurationsAndRest() {
            // Insert a crotchet, a minim, and a crotchet rest
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            selectDuration(Actions.HALF_NOTE_ACTION);
            clickAt(insertionPoint(0, -2));
            performLayout(0);

            selectDuration(Actions.QUARTER_NOTE_ACTION);
            enableRestMode();
            clickAt(insertionPoint(0, 0));
            performLayout(0);
            deselectRestMode();

            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 2));

            assertThat(score().getSelectionSize()).isEqualTo(3);
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, false);
            assertActionSelected(Actions.HALF_NOTE_ACTION, false);
            assertActionSelected(Actions.FLAT_ACTION, false);
        }
    }

    @Test
    void testSelectionClearedRestoresState() {
        // Insert a minim with flat
        selectDuration(Actions.HALF_NOTE_ACTION);
        clickToolbarButton(Actions.FLAT_ACTION);
        clickAt(insertionPoint(0, 0));
        performLayout(0);
        clickToolbarButton(Actions.FLAT_ACTION);

        enterSelectMode();

        // The flat action should be deselected before any selection
        assertActionSelected(Actions.FLAT_ACTION, false);

        // Select the note — reflection should select the flat action
        clickAt(noteScreenPosition(0, 0));
        assertActionSelected(Actions.FLAT_ACTION, true);

        // Deselect all via Cmd+D — flat should be restored to deselected
        robot.pressAndReleaseKey(KeyEvent.VK_D, InputEvent.META_DOWN_MASK);
        pause();

        assertActionSelected(Actions.FLAT_ACTION, false);
    }


    // -- Assertion helpers --

    /**
     * Asserts the action's selected state. Works for all actions,
     * including those that only exist in menus.
     */
    private void assertActionSelected(UIAction action, boolean expected) {
        var selectable = (UIAction.Selectable) action;
        var isSelected = GuiActionRunner.execute(() -> selectable.isSelected());
        assertThat(isSelected)
            .as("Action '%s' selected state", action.getActionCommand())
            .isEqualTo(expected);
    }

    /**
     * Asserts the toolbar button model's selected state. Only works for
     * actions that have a corresponding toolbar button.
     */
    private void assertButtonSelected(UIAction action, boolean expected) {
        var button = findButtonByName(action.getActionCommand());
        var isSelected = GuiActionRunner.execute(() -> button.getModel().isSelected());
        assertThat(isSelected)
            .as("Button '%s' selected state", action.getActionCommand())
            .isEqualTo(expected);
    }
}
