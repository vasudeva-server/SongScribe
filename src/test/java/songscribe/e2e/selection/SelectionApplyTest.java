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

package songscribe.e2e.selection;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.e2e.E2ETest;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;

/**
 * E2E tests for applying toolbar actions to a selection.
 * Verifies that clicking toolbar buttons in select mode modifies the
 * selected notes rather than changing insertion state.
 */
class SelectionApplyTest extends E2ETest {

    @Nested
    class DurationChanges {

        @Test
        void testSelectNotesClickDurationVerifyChanged() {
            buildNotes(Actions.EIGHTH_NOTE_ACTION, 0, -2, -4);
            enterSelectMode();

            // Select all three notes
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 2));
            assertThat(score().getSelectionSize()).isEqualTo(3);

            // Click the quarter note toolbar button
            clickToolbarButton(Actions.QUARTER_NOTE_ACTION);

            // All notes should now be crotchets
            verifyNoteType(0, 0, ElementType.CROTCHET);
            verifyNoteType(0, 1, ElementType.CROTCHET);
            verifyNoteType(0, 2, ElementType.CROTCHET);

            // Selection should remain active
            assertThat(score().getSelectionSize()).isEqualTo(3);
        }

        @Test
        void testDurationChangePreservesNoteRestKind() {
            // Quaver, quaver rest, quaver
            selectDuration(Actions.EIGHTH_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            enableRestMode();
            clickAt(insertionPoint(0, 0));
            performLayout(0);
            deselectRestMode();

            clickAt(insertionPoint(0, -4));
            performLayout(0);

            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 2));

            // Change all to half notes
            clickToolbarButton(Actions.HALF_NOTE_ACTION);

            verifyNoteType(0, 0, ElementType.MINIM);
            verifyNoteType(0, 1, ElementType.MINIM_REST);
            verifyNoteType(0, 2, ElementType.MINIM);
        }
    }

    @Nested
    class AccidentalChanges {

        @Test
        void testSelectNotesClickAccidentalVerifyApplied() {
            buildNotes(Actions.QUARTER_NOTE_ACTION, 0, -2);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));
            assertThat(score().getSelectionSize()).isEqualTo(2);

            // Click the flat toolbar button
            clickToolbarButton(Actions.FLAT_ACTION);

            verifyAccidental(0, 0, StaffElement.Accidental.FLAT);
            verifyAccidental(0, 1, StaffElement.Accidental.FLAT);

            // Selection should remain active
            assertThat(score().getSelectionSize()).isEqualTo(2);
        }

        @Test
        void testSelectNotesClickNaturalToolbarVerifyApplied() {
            buildNotes(Actions.QUARTER_NOTE_ACTION, 0, -2);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));
            assertThat(score().getSelectionSize()).isEqualTo(2);

            // Click the natural toolbar button
            clickToolbarButton(Actions.NATURAL_ACTION);

            verifyAccidental(0, 0, StaffElement.Accidental.NATURAL);
            verifyAccidental(0, 1, StaffElement.Accidental.NATURAL);

            // Selection should remain active
            assertThat(score().getSelectionSize()).isEqualTo(2);
        }
    }

    @Nested
    class ArticulationChanges {

        @Test
        void testSelectNotesAndRestsClickDotVerifyBothGetDots() {
            // Crotchet, crotchet rest, crotchet
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            enableRestMode();
            clickAt(insertionPoint(0, 0));
            performLayout(0);
            deselectRestMode();

            clickAt(insertionPoint(0, -4));
            performLayout(0);

            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 2));
            assertThat(score().getSelectionSize()).isEqualTo(3);

            // Click the dot toolbar button
            clickToolbarButton(Actions.DOT_ACTION);

            // Both notes and rest should have dots (DotAction applies to all durations)
            verifyDotCount(0, 0, 1);
            verifyDotCount(0, 1, 1);
            verifyDotCount(0, 2, 1);
        }

        @Test
        void testApplyFermataThenRemove() {
            buildNotes(Actions.QUARTER_NOTE_ACTION, 0, -2);
            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            // Apply fermata via Insert menu
            clickMenuItem(Actions.FERMATA_ACTION);
            verifyFermata(0, 0, true);
            verifyFermata(0, 1, true);

            // Remove fermata (clicking again toggles it off)
            clickMenuItem(Actions.FERMATA_ACTION);
            verifyFermata(0, 0, false);
            verifyFermata(0, 1, false);
        }
    }

    @Nested
    class MixedElementSelection {

        @Test
        void testSelectNotesAndBarlineVerifyMutualExclusivity() {
            // Note, barline, note
            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            selectDuration(Actions.BARLINE_ACTIONS[2]);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPoint(0, -4));
            performLayout(0);

            enterSelectMode();

            // Select the note, then shift-click the barline
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));
            assertThat(score().getSelectionSize()).isEqualTo(2);

            // Duration actions should be enabled (notes exist in selection)
            assertActionEnabled(Actions.QUARTER_NOTE_ACTION, true);

            // Non-duration barline actions should also be enabled (barlines exist)
            assertActionEnabled(Actions.BARLINE_ACTIONS[2], true);
        }

        @Test
        void testSelectOnlyBarlinesDisablesDurationActions() {
            // Two barlines with a note after (so we have clickable positions)
            selectDuration(Actions.BARLINE_ACTIONS[2]);
            clickAt(insertionPoint(0, 0));
            performLayout(0);
            clickAt(insertionPoint(0, 0));
            performLayout(0);

            selectDuration(Actions.QUARTER_NOTE_ACTION);
            clickAt(insertionPoint(0, -4));
            performLayout(0);

            enterSelectMode();

            // Select only the two barlines
            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));
            assertThat(score().getSelectionSize()).isEqualTo(2);

            // Duration actions should be disabled (no durations in selection)
            assertActionEnabled(Actions.QUARTER_NOTE_ACTION, false);
            assertActionEnabled(Actions.HALF_NOTE_ACTION, false);

            // Dot action should be disabled (DotAction.appliesTo requires isDuration)
            assertActionEnabled(Actions.DOT_ACTION, false);

            // Barline actions should be enabled
            assertActionEnabled(Actions.BARLINE_ACTIONS[2], true);
        }
    }


    // -- Assertion helpers --

    private void verifyNoteType(int lineIndex, int noteIndex, ElementType expected) {
        var actual = GuiActionRunner.execute(
            () -> composition().getLine(lineIndex).getElement(noteIndex).getType()
        );
        assertThat(actual)
            .as("note[%d][%d] type", lineIndex, noteIndex)
            .isEqualTo(expected);
    }

    private void verifyAccidental(int lineIndex, int noteIndex, StaffElement.Accidental expected) {
        var actual = GuiActionRunner.execute(
            () -> composition().getLine(lineIndex).getElement(noteIndex).getAccidental()
        );
        assertThat(actual)
            .as("note[%d][%d] accidental", lineIndex, noteIndex)
            .isEqualTo(expected);
    }

    private void verifyDotCount(int lineIndex, int noteIndex, int expected) {
        var actual = GuiActionRunner.execute(
            () -> composition().getLine(lineIndex).getElement(noteIndex).getDotCount()
        );
        assertThat(actual)
            .as("note[%d][%d] dot count", lineIndex, noteIndex)
            .isEqualTo(expected);
    }

    private void verifyFermata(int lineIndex, int noteIndex, boolean expected) {
        var actual = GuiActionRunner.execute(
            () -> composition().getLine(lineIndex).getElement(noteIndex).isFermata()
        );
        assertThat(actual)
            .as("note[%d][%d] fermata", lineIndex, noteIndex)
            .isEqualTo(expected);
    }

    private void assertActionEnabled(UIAction action, boolean expected) {
        var isEnabled = GuiActionRunner.execute(() -> action.isEnabled());
        assertThat(isEnabled)
            .as("Action '%s' enabled state", action.getActionCommand())
            .isEqualTo(expected);
    }
}
