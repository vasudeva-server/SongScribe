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
import static org.assertj.swing.core.MouseButton.LEFT_BUTTON;
import static org.junit.jupiter.api.Assertions.assertAll;

import module java.desktop;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.Test;

import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.music.StaffElement.Accidental;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.score.InsertionElementManager;
import songscribe.ui.layout2.ScaleContext;

/**
 * Consolidated E2E test for selection mechanics, toolbar reflection,
 * barline hit detection, action application, drag, and replacement.
 * Replaces selection/SelectionTest, selection/SelectionApplyTest,
 * ToolbarReflectionTest, BarlineHitTest, and parts of TieTest and
 * ElementInsertionTest.
 */
class SelectionTest extends E2ETest {

    // Element indices for selection1.mssw (ordinals match fixture order)
    private enum Sel1 {
        QUARTER_TEMPO,
        WHOLE,
        HALF,
        QUARTER,
        EIGHTH,
        SIXTEENTH,
        THIRTY_SECOND,
        FLAT,
        DOUBLE_FLAT,
        NATURAL_FLAT,
        NATURAL,
        SHARP,
        DOUBLE_SHARP,
        NATURAL_SHARP,
        FLAT_IN_PARENS,
        DOTTED,
        DOUBLE_DOTTED,
    }

    // Element indices for selection2.mssw (ordinals match fixture order)
    private enum Sel2 {
        STACCATO,
        ACCENT,
        FERMATA,
        TIED_1,
        TIED_2,
        SEMIBREVE_REST,
        MINIM_REST,
        CROTCHET_REST,
        QUAVER_REST,
        SEMIQUAVER_REST,
        DEMI_SEMIQUAVER_REST,
        NOTE,
        SINGLE_BARLINE,
        DOUBLE_BARLINE,
        FINAL_DOUBLE_BARLINE,
        REPEAT_LEFT,
        REPEAT_RIGHT,
        REPEAT_LEFT_RIGHT,
        BREATH_MARK,
    }

    // Duration actions in toolbar order (for iteration)
    private static final UIAction[] DURATION_ACTIONS = new UIAction[]{
        Actions.WHOLE_NOTE_ACTION,
        Actions.HALF_NOTE_ACTION,
        Actions.QUARTER_NOTE_ACTION,
        Actions.EIGHTH_NOTE_ACTION,
        Actions.SIXTEENTH_NOTE_ACTION,
        Actions.THIRTY_SECOND_NOTE_ACTION,
    };

    @Test
    void testSelectionOperations() throws Exception {
        loadFixture("selection1");

        debugStep("1-2: Mode toggle", () -> {
            enterSelectMode();
            assertThat(score().getMode()).as("1: select mode").isEqualTo(Mode.SELECT);

            enterEditMode();
            assertThat(score().getMode()).as("2: edit mode").isEqualTo(Mode.EDIT);
        });

        debugStep("3-4: Click note auto-selects", () -> {
            clickAt(noteScreenPosition(0, Sel1.WHOLE.ordinal()));
            assertAll(
                () -> assertThat(score().getMode()).as("3: auto-enters select mode").isEqualTo(Mode.SELECT),
                () -> assertThat(score().getSingleSelectedElement())
                    .as("4: single selected element")
                    .isEqualTo(composition().getLine(0).getElement(Sel1.WHOLE.ordinal()))
            );
        });

        debugStep("5: Click empty space deselects", () -> {
            var emptyPoint = GuiActionRunner.execute(() -> {
                var lc = score().getLineComponent(0);
                var loc = lc.getLocationOnScreen();
                return new Point(loc.x + lc.getWidth() - 10, loc.y + lc.getHeight() - 5);
            });
            clickAt(emptyPoint);
            assertThat(score().getSelectionSize()).as("5: click empty deselects").isEqualTo(0);
        });

        debugStep("6: Shift-click extends selection", () -> {
            clickAt(noteScreenPosition(0, Sel1.WHOLE.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel1.QUARTER.ordinal()));
            assertThat(score().getSelectionSize()).as("6: shift-click range").isEqualTo(3);
        });

        debugStep("7: Shift-click shrinks selection", () -> {
            shiftClickAt(noteScreenPosition(0, Sel1.HALF.ordinal()));
            assertAll(
                () -> assertThat(score().getSelectionSize()).as("7: shrunk range size").isEqualTo(2),
                () -> assertThat(score().isElementSelected(Sel1.QUARTER.ordinal(), 0))
                    .as("7: index 3 not selected").isFalse()
            );
        });

        debugStep("8: Drag-select", () -> {
            enterSelectMode();
            var note1Pos = noteScreenPosition(0, Sel1.WHOLE.ordinal());
            var note3Pos = noteScreenPosition(0, Sel1.QUARTER.ordinal());
            var dragStart = new Point(note1Pos.x - 20, note1Pos.y - 20);
            var dragEnd = new Point(note3Pos.x + 20, note3Pos.y + 20);
            robot.pressMouse(dragStart, LEFT_BUTTON);
            pause();
            robot.moveMouse(dragEnd);
            pause();
            robot.releaseMouseButtons();
            pause();
            assertThat(score().getSelectionSize()).as("8: drag-select").isGreaterThanOrEqualTo(3);
        });

        debugStep("9: Cmd+D deselects", () -> {
            clickMenuItem(Actions.DESELECT_ACTION);
            assertThat(score().getSelectionSize()).as("9: Cmd+D deselects").isEqualTo(0);
        });

        debugStep("10: Click past elements selects line", () -> {
            enterSelectMode();
            var lineClickPoint = GuiActionRunner.execute(() -> {
                var lc = score().getLineComponent(0);
                var line = lc.getLine();
                var layoutResult = lc.getLayoutResult();
                var lastElement = line.getElement(line.elementCount() - 1);
                var lastXSs = layoutResult != null ? layoutResult.getElementXSs(lastElement) : 0.0;
                int pastLastXPx = (int) Math.round(ScaleContext.getInstance().toPixels(lastXSs)) + 40;
                var loc = lc.getLocationOnScreen();
                var yPx = lc.staffPositionToYPx(0);
                return new Point(loc.x + pastLastXPx, loc.y + yPx);
            });
            clickAt(lineClickPoint);
            assertThat(score().isLineSelected(0)).as("10: line selected").isTrue();
        });

        debugStep("11-16: Duration reflection", () -> {
            assertDurationReflected(Sel1.WHOLE.ordinal(), Actions.WHOLE_NOTE_ACTION);
            assertDurationReflected(Sel1.HALF.ordinal(), Actions.HALF_NOTE_ACTION);
            assertDurationReflected(Sel1.QUARTER.ordinal(), Actions.QUARTER_NOTE_ACTION);
            assertDurationReflected(Sel1.EIGHTH.ordinal(), Actions.EIGHTH_NOTE_ACTION);
            assertDurationReflected(Sel1.SIXTEENTH.ordinal(), Actions.SIXTEENTH_NOTE_ACTION);
            assertDurationReflected(Sel1.THIRTY_SECOND.ordinal(), Actions.THIRTY_SECOND_NOTE_ACTION);
        });

        debugStep("17-23: Accidental reflection", () -> {
            assertAccidentalReflected(Sel1.FLAT.ordinal(), Actions.FLAT_ACTION);
            assertAccidentalReflected(Sel1.DOUBLE_FLAT.ordinal(), Actions.DOUBLE_FLAT_ACTION);
            assertAccidentalReflected(Sel1.NATURAL_FLAT.ordinal(), Actions.NATURAL_FLAT_ACTION);
            assertAccidentalReflected(Sel1.NATURAL.ordinal(), Actions.NATURAL_ACTION);
            assertAccidentalReflected(Sel1.SHARP.ordinal(), Actions.SHARP_ACTION);
            assertAccidentalReflected(Sel1.DOUBLE_SHARP.ordinal(), Actions.DOUBLE_SHARP_ACTION);
            assertAccidentalReflected(Sel1.NATURAL_SHARP.ordinal(), Actions.NATURAL_SHARP_ACTION);
        });

        debugStep("24: Accidental-in-parens reflection", () -> {
            clickAt(noteScreenPosition(0, Sel1.FLAT_IN_PARENS.ordinal()));
            assertActionSelected(Actions.FLAT_ACTION, true, "24: underlying flat selected");
            assertActionSelected(Actions.ACCIDENTAL_IN_PARENS_ACTION, true, "24: in-parens selected");
        });

        debugStep("25: Dot reflection", () -> {
            clickAt(noteScreenPosition(0, Sel1.DOTTED.ordinal()));
            assertActionSelected(Actions.DOT_ACTION, true, "25: dot selected");
            assertActionSelected(Actions.DOUBLE_DOT_ACTION, false, "25: double-dot not selected");
        });

        debugStep("26: Double-dot reflection", () -> {
            clickAt(noteScreenPosition(0, Sel1.DOUBLE_DOTTED.ordinal()));
            assertActionSelected(Actions.DOUBLE_DOT_ACTION, true, "26: double-dot selected");
            assertActionSelected(Actions.DOT_ACTION, false, "26: dot not selected");
        });

        debugStep("27: Single quarter elements reflect quarter", () -> {
            clickAt(noteScreenPosition(0, Sel1.QUARTER_TEMPO.ordinal()));
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, true, "27a: index 0 is quarter");
            clickAt(noteScreenPosition(0, Sel1.QUARTER.ordinal()));
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, true, "27b: index 3 is quarter");
        });

        debugStep("28: Mixed durations deselect both", () -> {
            clickAt(noteScreenPosition(0, Sel1.QUARTER.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel1.HALF.ordinal()));
            assertAll(
                () -> assertActionSelected(Actions.QUARTER_NOTE_ACTION, false, "28: quarter not selected"),
                () -> assertActionSelected(Actions.HALF_NOTE_ACTION, false, "28: half not selected")
            );
        });

        debugStep("29: Mixed accidentals deselect both", () -> {
            clickAt(noteScreenPosition(0, Sel1.FLAT.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel1.DOUBLE_FLAT.ordinal()));
            assertAll(
                () -> assertActionSelected(Actions.FLAT_ACTION, false, "29: flat not selected"),
                () -> assertActionSelected(Actions.DOUBLE_FLAT_ACTION, false, "29: double-flat not selected")
            );
        });

        loadFixture("selection2");

        debugStep("30: Rest reflects duration + rest mode", () -> {
            enterSelectMode();

            // Select crotchet rest — large enough to click reliably
            clickAt(noteScreenPosition(0, Sel2.CROTCHET_REST.ordinal()));
            assertDurationReflected(Sel2.CROTCHET_REST.ordinal(), Actions.QUARTER_NOTE_ACTION);
            assertActionSelected(Actions.REST_ACTION, true, "30: rest mode selected");
        });

        debugStep("36-38: Articulation reflection", () -> {
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            assertActionSelected(Actions.STACCATO_ACTION, true, "36: staccato selected");

            clickAt(noteScreenPosition(0, Sel2.ACCENT.ordinal()));
            assertActionSelected(Actions.ACCENT_ACTION, true, "37: accent selected");

            clickAt(noteScreenPosition(0, Sel2.FERMATA.ordinal()));
            assertActionSelected(Actions.FERMATA_ACTION, true, "38: fermata selected");
        });

        debugStep("39: Single barline disables durations", () -> {
            clickAt(noteScreenPosition(0, Sel2.SINGLE_BARLINE.ordinal()));
            assertAll(
                () -> verifyDurationsDisabled("39"),
                () -> assertActionEnabled(Actions.BARLINE_ACTIONS[2], true, "39: barline action enabled")
            );
        });

        debugStep("40-44: Other barlines/repeats disable durations", () -> {
            clickAt(noteScreenPosition(0, Sel2.DOUBLE_BARLINE.ordinal()));
            verifyDurationsDisabled("40");

            clickAt(noteScreenPosition(0, Sel2.FINAL_DOUBLE_BARLINE.ordinal()));
            verifyDurationsDisabled("41");

            clickAt(noteScreenPosition(0, Sel2.REPEAT_LEFT.ordinal()));
            verifyDurationsDisabled("42");

            clickAt(noteScreenPosition(0, Sel2.REPEAT_RIGHT.ordinal()));
            verifyDurationsDisabled("43");

            clickAt(noteScreenPosition(0, Sel2.REPEAT_LEFT_RIGHT.ordinal()));
            verifyDurationsDisabled("44");
        });

        debugStep("45: Breath mark disables durations", () -> {
            // Breath marks are very small — use drag-select to reliably select
            var bmPos = noteScreenPosition(0, Sel2.BREATH_MARK.ordinal());
            var dragStart = new Point(bmPos.x - 10, bmPos.y - 10);
            var dragEnd = new Point(bmPos.x + 10, bmPos.y + 10);
            robot.pressMouse(dragStart, LEFT_BUTTON);
            pause();
            robot.moveMouse(dragEnd);
            pause();
            robot.releaseMouseButtons();
            pause();
            verifyDurationsDisabled("45");
        });

        debugStep("46: Note + rest enables durations", () -> {
            clickAt(noteScreenPosition(0, Sel2.CROTCHET_REST.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.NOTE.ordinal()));
            assertActionEnabled(Actions.QUARTER_NOTE_ACTION, true, "46: duration enabled for note+rest");
        });

        debugStep("47-48: Note + barline enables both", () -> {
            clickAt(noteScreenPosition(0, Sel2.NOTE.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.SINGLE_BARLINE.ordinal()));
            assertAll(
                () -> assertActionEnabled(Actions.QUARTER_NOTE_ACTION, true, "47: duration enabled"),
                () -> assertActionEnabled(Actions.BARLINE_ACTIONS[2], true, "48: barline enabled")
            );
        });

        debugStep("49-51: Barlines only disables durations", () -> {
            clickAt(noteScreenPosition(0, Sel2.SINGLE_BARLINE.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.DOUBLE_BARLINE.ordinal()));
            assertAll(
                () -> assertActionEnabled(Actions.QUARTER_NOTE_ACTION, false, "49: duration disabled"),
                () -> assertActionEnabled(Actions.DOT_ACTION, false, "50: dot disabled"),
                () -> assertActionEnabled(Actions.BARLINE_ACTIONS[2], true, "51: barline enabled")
            );
        });

        debugStep("52: Deselect clears selection", () -> {
            clickMenuItem(Actions.DESELECT_ACTION);
            assertThat(score().getSelectionSize()).as("52: selection cleared").isEqualTo(0);
        });

        debugStep("53: Glissando suppressed when target is rest", () -> {
            enterEditMode();
            clickToolbarButton(Actions.GLISSANDO_ACTION);
            hoverBetween(0, Sel2.TIED_2.ordinal(), Sel2.SEMIBREVE_REST.ordinal());
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as("53: target is rest").isFalse();
        });

        debugStep("54: Glissando suppressed when source is rest", () -> {
            hoverBetween(0, Sel2.DEMI_SEMIQUAVER_REST.ordinal(), Sel2.NOTE.ordinal());
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as("54: source is rest").isFalse();
        });

        debugStep("55: Glissando suppressed when both are rests", () -> {
            hoverBetween(0, Sel2.SEMIBREVE_REST.ordinal(), Sel2.MINIM_REST.ordinal());
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as("55: both rests").isFalse();
        });

        debugStep("56: Slide-out suppressed when source is rest", () -> {
            clickToolbarButton(Actions.SLIDE_OUT_ACTION);
            hoverBetween(0, Sel2.DEMI_SEMIQUAVER_REST.ordinal(), Sel2.NOTE.ordinal());
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as("56: source is rest").isFalse();
            selectDuration(Actions.QUARTER_NOTE_ACTION);
        });

        debugStep("57-58: Apply quarter to selection", () -> {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.FERMATA.ordinal()));
            clickToolbarButton(Actions.QUARTER_NOTE_ACTION);
            assertAll(
                () -> verifyNoteType(0, Sel2.STACCATO.ordinal(), ElementType.CROTCHET, "57a"),
                () -> verifyNoteType(0, Sel2.ACCENT.ordinal(), ElementType.CROTCHET, "57b"),
                () -> verifyNoteType(0, Sel2.FERMATA.ordinal(), ElementType.CROTCHET, "57c"),
                () -> assertThat(score().getSelectionSize()).as("58: selection preserved").isEqualTo(3)
            );
        });

        debugStep("59-61: Apply half preserves note/rest kind", () -> {
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.CROTCHET_REST.ordinal()));
            clickToolbarButton(Actions.HALF_NOTE_ACTION);

            var staccatoType = GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.STACCATO.ordinal()).getType());
            var accentType = GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.ACCENT.ordinal()).getType());
            var fermataType = GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.FERMATA.ordinal()).getType());
            var restType = GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.CROTCHET_REST.ordinal()).getType());

            assertAll(
                () -> assertThat(staccatoType).as("59a: staccato is minim").isEqualTo(ElementType.MINIM),
                () -> assertThat(accentType).as("59b: accent is minim").isEqualTo(ElementType.MINIM),
                () -> assertThat(fermataType).as("59c: fermata is minim").isEqualTo(ElementType.MINIM),
                () -> assertThat(restType).as("60: rest is minim rest").isEqualTo(ElementType.MINIM_REST),
                () -> assertThat(staccatoType.isRest()).as("61a: staccato is still a note").isFalse(),
                () -> assertThat(restType.isRest()).as("61b: rest is still a rest").isTrue()
            );
        });

        debugStep("62-63: Apply flat to selection", () -> {
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.ACCENT.ordinal()));
            clickToolbarButton(Actions.FLAT_ACTION);
            assertAll(
                () -> verifyAccidental(0, Sel2.STACCATO.ordinal(), Accidental.FLAT, "62a"),
                () -> verifyAccidental(0, Sel2.ACCENT.ordinal(), Accidental.FLAT, "62b"),
                () -> assertThat(score().getSelectionSize()).as("63: selection preserved").isEqualTo(2)
            );
        });

        debugStep("64: Apply natural to same selection", () -> {
            clickToolbarButton(Actions.NATURAL_ACTION);
            assertAll(
                () -> verifyAccidental(0, Sel2.STACCATO.ordinal(), Accidental.NATURAL, "64a"),
                () -> verifyAccidental(0, Sel2.ACCENT.ordinal(), Accidental.NATURAL, "64b")
            );
        });

        debugStep("65: Apply dot to notes and rest", () -> {
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.CROTCHET_REST.ordinal()));
            clickToolbarButton(Actions.DOT_ACTION);
            assertAll(
                () -> verifyDotCount(0, Sel2.STACCATO.ordinal(), 1, "65a"),
                () -> verifyDotCount(0, Sel2.ACCENT.ordinal(), 1, "65b"),
                () -> verifyDotCount(0, Sel2.FERMATA.ordinal(), 1, "65c"),
                () -> verifyDotCount(0, Sel2.CROTCHET_REST.ordinal(), 1, "65d")
            );
        });

        debugStep("66: Apply fermata", () -> {
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.ACCENT.ordinal()));
            clickMenuItem(Actions.FERMATA_ACTION);
            assertAll(
                () -> verifyFermata(0, Sel2.STACCATO.ordinal(), true, "66a"),
                () -> verifyFermata(0, Sel2.ACCENT.ordinal(), true, "66b")
            );
        });

        debugStep("67: Toggle fermata off", () -> {
            clickMenuItem(Actions.FERMATA_ACTION);
            assertAll(
                () -> verifyFermata(0, Sel2.STACCATO.ordinal(), false, "67a"),
                () -> verifyFermata(0, Sel2.ACCENT.ordinal(), false, "67b")
            );
        });

        debugStep("68-69: Drag note to new position", () -> {
            var countBefore = GuiActionRunner.execute(() -> composition().getLine(0).elementCount());
            var originalSp = GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.NOTE.ordinal()).getStaffPosition()
            );
            var targetSp = originalSp - 4;

            enterEditMode();
            dragNote(0, Sel2.NOTE.ordinal(), targetSp);
            performLayout(0);

            assertAll(
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.NOTE.ordinal()).getStaffPosition()
                )).as("68: staffPosition updated").isEqualTo(targetSp),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).elementCount()
                )).as("69: elementCount unchanged").isEqualTo(countBefore)
            );
        });

        debugStep("70-71: Drag tied note moves partner", () -> {
            var originalSp = GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.TIED_1.ordinal()).getStaffPosition()
            );
            var targetSp = originalSp - 4;

            dragNote(0, Sel2.TIED_1.ordinal(), targetSp);
            performLayout(0);

            assertAll(
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.TIED_1.ordinal()).getStaffPosition()
                )).as("70: tied note source moved").isEqualTo(targetSp),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.TIED_2.ordinal()).getStaffPosition()
                )).as("71: tied note partner moved").isEqualTo(targetSp)
            );
        });

        debugStep("72-74: Replace via click", () -> {
            var countBeforeReplace = GuiActionRunner.execute(() -> composition().getLine(0).elementCount());
            var originalSp = GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.STACCATO.ordinal()).getStaffPosition()
            );
            var targetSp = originalSp - 4;

            selectDuration(Actions.EIGHTH_NOTE_ACTION);
            performLayout(0);

            // Use noteScreenPosition for X (accurate hit), compute Y for the target staff position
            var existingPos = noteScreenPosition(0, Sel2.STACCATO.ordinal());
            var replacePoint = GuiActionRunner.execute(() -> {
                var lc = score().getLineComponent(0);
                var loc = lc.getLocationOnScreen();
                return new Point(existingPos.x, loc.y + lc.staffPositionToYPx(targetSp));
            });

            clickAt(replacePoint);
            performLayout(0);

            var replacedType = GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.STACCATO.ordinal()).getType());
            var replacedSp = GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.STACCATO.ordinal()).getStaffPosition());
            var countAfterReplace = GuiActionRunner.execute(() -> composition().getLine(0).elementCount());

            assertAll(
                () -> assertThat(countAfterReplace).as("72: elementCount unchanged").isEqualTo(countBeforeReplace),
                () -> assertThat(replacedType).as("73: type changed").isEqualTo(ElementType.QUAVER),
                () -> assertThat(replacedSp).as("74: staffPosition updated").isEqualTo(targetSp)
            );
        });
    }

    // -- Assertion helpers --

    private void verifyNoteType(int lineIndex, int noteIndex, ElementType expected, String label) {
        var actual = GuiActionRunner.execute(
            () -> composition().getLine(lineIndex).getElement(noteIndex).getType()
        );
        assertThat(actual).as("%s: note[%d][%d] type", label, lineIndex, noteIndex).isEqualTo(expected);
    }

    private void verifyAccidental(int lineIndex, int noteIndex, Accidental expected, String label) {
        var actual = GuiActionRunner.execute(
            () -> composition().getLine(lineIndex).getElement(noteIndex).getAccidental()
        );
        assertThat(actual).as("%s: note[%d][%d] accidental", label, lineIndex, noteIndex).isEqualTo(expected);
    }

    private void verifyDotCount(int lineIndex, int noteIndex, int expected, String label) {
        var actual = GuiActionRunner.execute(
            () -> composition().getLine(lineIndex).getElement(noteIndex).getDotCount()
        );
        assertThat(actual).as("%s: note[%d][%d] dot count", label, lineIndex, noteIndex).isEqualTo(expected);
    }

    private void verifyFermata(int lineIndex, int noteIndex, boolean expected, String label) {
        var actual = GuiActionRunner.execute(
            () -> composition().getLine(lineIndex).getElement(noteIndex).isFermata()
        );
        assertThat(actual).as("%s: note[%d][%d] fermata", label, lineIndex, noteIndex).isEqualTo(expected);
    }

    private void assertActionEnabled(UIAction action, boolean expected, String label) {
        var isEnabled = GuiActionRunner.execute(() -> action.isEnabled());
        assertThat(isEnabled).as("%s: action '%s' enabled", label, action.getActionCommand()).isEqualTo(expected);
    }

    private void assertActionSelected(UIAction action, boolean expected, String label) {
        var selectable = (UIAction.Selectable) action;
        var isSelected = GuiActionRunner.execute(() -> selectable.isSelected());
        assertThat(isSelected).as("%s: action '%s' selected", label, action.getActionCommand()).isEqualTo(expected);
    }

    /**
     * Selects a single element and verifies the given duration action is selected
     * while all other duration actions are deselected.
     */
    private void assertDurationReflected(int elementIndex, UIAction expectedAction) {
        clickAt(noteScreenPosition(0, elementIndex));

        for (var action : DURATION_ACTIONS) {
            boolean expected = (action == expectedAction);
            assertActionSelected(action, expected,
                (10 + elementIndex) + ": duration reflection for index " + elementIndex);
        }
    }

    /**
     * Selects a single element and verifies the given accidental action is selected
     * while all other accidental actions are deselected.
     */
    private void assertAccidentalReflected(int elementIndex, UIAction expectedAction) {
        clickAt(noteScreenPosition(0, elementIndex));

        for (var action : accidentalActions()) {
            boolean expected = (action == expectedAction);
            assertActionSelected(action, expected,
                (10 + elementIndex) + ": accidental reflection for index " + elementIndex);
        }
    }

    /**
     * Verifies all duration actions are deselected.
     */
    private void verifyNoDurationSelected(int assertionNum) {
        for (var action : DURATION_ACTIONS) {
            assertActionSelected(action, false, assertionNum + ": no duration selected");
        }
    }

    /**
     * Verifies all duration actions are disabled.
     */
    private void verifyDurationsDisabled(String label) {
        for (var action : DURATION_ACTIONS) {
            assertActionEnabled(action, false, label + ": duration disabled");
        }
    }

    /**
     * Moves the mouse to the midpoint between two elements on the given line.
     */
    private void hoverBetween(int lineIdx, int leftIdx, int rightIdx) {
        var midpoint = GuiActionRunner.execute(() -> {
            var lc = score().getLineComponent(lineIdx);
            var line = lc.getLine();
            var layoutResult = lc.getLayoutResult();
            var leftElement = line.getElement(leftIdx);
            var rightElement = line.getElement(rightIdx);

            var leftXSs = layoutResult != null ? layoutResult.getElementXSs(leftElement) : 0.0;
            var rightXSs = layoutResult != null ? layoutResult.getElementXSs(rightElement) : 0.0;
            var midXSs = (leftXSs + rightXSs) / 2.0;
            int midXPx = (int) Math.round(ScaleContext.getInstance().toPixels(midXSs));
            int yPx = lc.staffPositionToYPx(0);

            var loc = lc.getLocationOnScreen();
            return new Point(loc.x + midXPx, loc.y + yPx);
        });

        robot.moveMouse(midpoint);
        pause();
    }
}
