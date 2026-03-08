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
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;

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
            buildComposition(quaver(0), quaver(-2), quaver(-4));
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
            buildComposition(quaver(0), rest(ElementType.QUAVER_REST), quaver(-4));
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
            buildComposition(crotchet(0), crotchet(-2));
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
            buildComposition(crotchet(0), crotchet(-2));
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
            buildComposition(crotchet(0), rest(ElementType.CROTCHET_REST), crotchet(-4));
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
            buildComposition(crotchet(0), crotchet(-2));
            enterSelectMode();

            clickAt(noteScreenPosition(0, 0));
            shiftClickAt(noteScreenPosition(0, 1));

            // Apply fermata via Insert menu
            clickMenuItem("Fermata");
            verifyFermata(0, 0, true);
            verifyFermata(0, 1, true);

            // Remove fermata (clicking again toggles it off)
            clickMenuItem("Fermata");
            verifyFermata(0, 0, false);
            verifyFermata(0, 1, false);
        }
    }

    @Nested
    class MixedElementSelection {

        @Test
        void testSelectNotesAndBarlineVerifyMutualExclusivity() {
            // Note, barline, note — select first two elements
            buildComposition(crotchet(0), barline(), crotchet(-4));
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
            buildComposition(barline(), barline(), crotchet(-4));
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


    // -- Note factory helpers --

    private StaffElement crotchet(int staffPositionSp) {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(staffPositionSp);
        return note;
    }

    private StaffElement quaver(int staffPositionSp) {
        var note = ElementType.QUAVER.newInstance();
        note.setStaffPosition(staffPositionSp);
        return note;
    }

    private StaffElement rest(ElementType restType) {
        return restType.newInstance();
    }

    private StaffElement barline() {
        return ElementType.SINGLE_BARLINE.newInstance();
    }


    // -- Composition builder --

    private void buildComposition(StaffElement... notes) {
        GuiActionRunner.execute(() -> {
            var composition = new Composition(MainFrame.getInstance());
            var line = new Line();

            for (var note : notes) {
                line.addElement(note);
            }

            composition.addLine(0, line);
            score().setComposition(composition);
        });

        performLayout(0);
    }


    // -- Action helpers --

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
