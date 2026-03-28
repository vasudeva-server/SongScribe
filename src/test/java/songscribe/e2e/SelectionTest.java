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

import java.util.Objects;

import module java.desktop;

import org.assertj.swing.edt.GuiActionRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.score.InsertionElementManager;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.ScaleContext;

/**
 * Consolidated E2E test for selection mechanics, toolbar reflection,
 * barline hit detection, action application, drag, and replacement.
 * Replaces selection/SelectionTest, selection/SelectionApplyTest,
 * ToolbarReflectionTest, BarlineHitTest, and parts of TieTest and
 * ElementInsertionTest.
 */
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class SelectionTest extends E2ETest {

    @BeforeAll
    void loadSelection1Fixture() throws Exception {
        resetComposition();
        loadFixture("selection1");
    }

    // Element indices for selection1.mssw
    private enum Sel1 {
        QUARTER_TEMPO(0),
        WHOLE(1),
        HALF(2),
        QUARTER(3),
        EIGHTH(4),
        SIXTEENTH(5),
        THIRTY_SECOND(6),
        FLAT(7),
        DOUBLE_FLAT(8),
        NATURAL_FLAT(9),
        NATURAL(10),
        SHARP(11),
        DOUBLE_SHARP(12),
        NATURAL_SHARP(13),
        FLAT_IN_PARENS(14),
        DOTTED(15),
        DOUBLE_DOTTED(16),
        ;

        final int index;

        Sel1(int index) {
            this.index = index;
        }
    }

    // Element indices for selection2.mssw
    private enum Sel2 {
        STACCATO(0),
        ACCENT(1),
        FERMATA(2),
        TIED_1(3),
        TIED_2(4),
        SEMIBREVE_REST(5),
        MINIM_REST(6),
        CROTCHET_REST(7),
        QUAVER_REST(8),
        SEMIQUAVER_REST(9),
        DEMI_SEMIQUAVER_REST(10),
        NOTE(11),
        SINGLE_BARLINE(12),
        DOUBLE_BARLINE(13),
        FINAL_DOUBLE_BARLINE(14),
        REPEAT_LEFT(15),
        REPEAT_RIGHT(16),
        REPEAT_LEFT_RIGHT(17),
        BREATH_MARK(18),
        ;

        final int index;

        Sel2(int index) {
            this.index = index;
        }
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


    @Nested
    @Order(1)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class BasicSelection {

        @BeforeEach
        void resetState() {
            deselectSelection();
        }

        @Test
        void testClickEmptySpaceDeselects() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel1.WHOLE.index));
            var emptyPoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                var loc = lc.getLocationOnScreen();
                return new Point(loc.x + lc.getWidth() - 10, loc.y + lc.getHeight() - 5);
            }));
            clickAt(emptyPoint);
            assertThat(score().getSelectionSize()).as("click empty deselects").isEqualTo(0);
        }
    }


    @Nested
    @Order(2)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class RangeSelection {

        @Order(1)
        @Test
        void testShiftClickExtendsSelection() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel1.WHOLE.index));
            shiftClickAt(noteScreenPosition(0, Sel1.QUARTER.index));
            assertThat(score().getSelectionSize()).as("shift-click range").isEqualTo(3);
        }

        @Order(2)
        @Test
        void testShiftClickShrinksSelection() {
            shiftClickAt(noteScreenPosition(0, Sel1.HALF.index));
            assertAll(
                () -> assertThat(score().getSelectionSize()).as("shrunk range size").isEqualTo(2),
                () -> assertThat(score().isElementSelected(Sel1.QUARTER.index, 0))
                    .as("index 3 not selected").isFalse()
            );
        }

        @Order(3)
        @Test
        void testDragSelect() {
            enterSelectMode();
            var note1Pos = noteScreenPosition(0, Sel1.WHOLE.index);
            var note3Pos = noteScreenPosition(0, Sel1.QUARTER.index);
            var dragStart = new Point(note1Pos.x - 20, note1Pos.y - 20);
            var dragEnd = new Point(note3Pos.x + 20, note3Pos.y + 20);
            robot.pressMouse(dragStart, LEFT_BUTTON);
            pause();
            robot.moveMouse(dragEnd);
            pause();
            robot.releaseMouseButtons();
            pause();
            assertThat(score().getSelectionSize()).as("drag-select").isGreaterThanOrEqualTo(3);
        }

    }


    @Nested
    @Order(3)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class LineSelection {

        @BeforeEach
        void resetState() {
            deselectSelection();
        }

        @Test
        void testClickInStaffHeaderSelectsLine() {
            enterSelectMode();
            var lineClickPoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                var loc = lc.getLocationOnScreen();
                // Click at the midpoint of the clef — squarely inside the header region
                int clefMidXPx = (int) Math.round(
                    ScaleContext.getInstance().toPixels(LayoutStylesheet.CLEF_WIDTH_SS / 2.0));
                var yPx = lc.staffPositionToYPx(0);
                return new Point(loc.x + clefMidXPx, loc.y + yPx);
            }));
            clickAt(lineClickPoint);
            assertThat(score().isLineSelected(0)).as("line selected").isTrue();
        }

        @Test
        void testClickPastElementsDoesNotSelectLine() {
            enterSelectMode();
            var lineClickPoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                var line = Objects.requireNonNull(lc.getLine());
                var layoutResult = lc.getLayoutResult();
                var lastElement = line.getElement(line.elementCount() - 1);
                var lastXSs = layoutResult != null ? layoutResult.getElementXSs(lastElement) : 0.0;
                int pastLastXPx = (int) Math.round(ScaleContext.getInstance().toPixels(lastXSs)) + 40;
                var loc = lc.getLocationOnScreen();
                var yPx = lc.staffPositionToYPx(0);
                return new Point(loc.x + pastLastXPx, loc.y + yPx);
            }));
            clickAt(lineClickPoint);
            assertThat(score().isLineSelected(0)).as("line not selected past music").isFalse();
        }
    }


    @Nested
    @Order(4)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class BarlineSelection {

        @BeforeAll
        void loadSelection2Fixture() throws Exception {
            resetComposition();
            loadFixture("selection2");
        }

        @BeforeEach
        void resetState() {
            deselectSelection();
        }

        @Test
        void testActionStatesResetOnNewComposition() {
            // Set non-default states via toolbar clicks
            enterSelectMode();
            selectDuration(Actions.HALF_NOTE_ACTION);
            clickAction(Actions.SHARP_ACTION);
            clickAction(Actions.DOT_ACTION);
            clickAction(Actions.FERMATA_ACTION);
            clickAction(Actions.ACCENT_ACTION);
            enableRestMode();
            clickAction(Actions.ACCIDENTAL_IN_PARENS_ACTION);
            clickAction(Actions.STACCATO_ACTION);

            // Loading a fixture triggers Score.setComposition() → resetToDefaults()
            resetComposition();

            assertAll(
                () -> assertThat(GuiActionRunner.execute(() -> Actions.MODE_ACTION_GROUP.getSelected()))
                    .as("mode reset to EDIT").isSameAs(Actions.EDIT_MODE_ACTION),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.DURATION_ACTION_GROUP.getSelected()))
                    .as("duration reset to QUARTER_NOTE").isSameAs(Actions.QUARTER_NOTE_ACTION),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.ACCIDENTAL_ACTION_GROUP.getSelected()))
                    .as("accidental group cleared").isNull(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.ARTICULATION_ACTION_GROUP.getSelected()))
                    .as("articulation group cleared").isNull(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.DOT_ACTION_GROUP.getSelected()))
                    .as("dot group cleared").isNull(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.NON_DURATION_ACTION_GROUP.getSelected()))
                    .as("non-duration group cleared").isNull(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.ACCENT_ACTION.isSelected()))
                    .as("accent off").isFalse(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.REST_ACTION.isSelected()))
                    .as("rest off").isFalse(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.FERMATA_ACTION.isSelected()))
                    .as("fermata off").isFalse(),
                () -> assertThat(GuiActionRunner.execute(() -> Actions.ACCIDENTAL_IN_PARENS_ACTION.isSelected()))
                    .as("accidental-in-parens off").isFalse()
            );

            // Reload fixture for subsequent tests in this class
            resetComposition();
            try {
                loadFixture("selection2");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        void testBreathMarkDisablesDurationsAndBarlines() {
            enterSelectMode();
            // Breath marks are very small — use drag-select to reliably select
            var bmPos = noteScreenPosition(0, Sel2.BREATH_MARK.index);
            var dragStart = new Point(bmPos.x - 10, bmPos.y - 10);
            var dragEnd = new Point(bmPos.x + 10, bmPos.y + 10);
            robot.pressMouse(dragStart, LEFT_BUTTON);
            pause();
            robot.moveMouse(dragEnd);
            pause();
            robot.releaseMouseButtons();
            pause();
            assertAll(
                () -> verifyDurationsDisabled("breath mark"),
                () -> assertActionEnabled(Actions.BARLINE_ACTIONS[2], false, "barline disabled"),
                () -> assertActionEnabled(Actions.BREATH_MARK_ACTION, true, "breath mark enabled")
            );
        }
    }


    @Nested
    @Order(5)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class GlissandoSuppression {

        @BeforeEach
        void resetState() {
            deselectSelection();
        }

        @Test
        void testGlissandoSuppressedWhenTargetIsRest() {
            clickAction(Actions.GLISSANDO_ACTION);
            hoverBetween(0, Sel2.TIED_2.index, Sel2.SEMIBREVE_REST.index);
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as("target is rest").isFalse();
        }

        @Test
        void testGlissandoSuppressedWhenSourceIsRest() {
            clickAction(Actions.GLISSANDO_ACTION);
            hoverBetween(0, Sel2.DEMI_SEMIQUAVER_REST.index, Sel2.NOTE.index);
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as("source is rest").isFalse();
        }

        @Test
        void testGlissandoSuppressedWhenBothAreRests() {
            clickAction(Actions.GLISSANDO_ACTION);
            hoverBetween(0, Sel2.SEMIBREVE_REST.index, Sel2.MINIM_REST.index);
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as("both rests").isFalse();
        }

        @Test
        void testSlideOutSuppressedWhenSourceIsRest() {
            clickAction(Actions.SLIDE_OUT_ACTION);
            hoverBetween(0, Sel2.DEMI_SEMIQUAVER_REST.index, Sel2.NOTE.index);
            assertThat(GuiActionRunner.execute(InsertionElementManager::shouldShowGlissandoPreview))
                .as("source is rest").isFalse();
        }
    }


    @Nested
    @Order(6)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ClickAndMode {

        @BeforeEach
        void resetState() {
            deselectSelection();
        }

        @Test
        void testAltClickEmptySpaceEntersSelectWithNoSelection() {
            enterEditMode();
            var emptyPoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                var loc = lc.getLocationOnScreen();
                return new Point(loc.x + lc.getWidth() - 10, loc.y + lc.getHeight() / 2);
            }));
            altClickAt(emptyPoint);
            assertAll(
                () -> assertThat(score().getMode()).as("mode is SELECT").isEqualTo(Mode.SELECT),
                () -> assertThat(score().getSelectionSize()).as("no selection").isEqualTo(0)
            );
        }

        @Test
        void testClickNoteInSelectModeSelectsIt() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel2.NOTE.index));
            assertAll(
                () -> assertThat(score().getSingleSelectedElement())
                    .as("note selected")
                    .isEqualTo(composition().getLine(0).getElement(Sel2.NOTE.index)),
                () -> assertThat(score().getMode()).as("mode stays SELECT").isEqualTo(Mode.SELECT)
            );
        }

        @Test
        void testAltClickInSelectModeSameAsClick() {
            enterSelectMode();
            altClickAt(noteScreenPosition(0, Sel2.STACCATO.index));
            assertThat(score().getSingleSelectedElement())
                .as("note selected via alt+click")
                .isEqualTo(composition().getLine(0).getElement(Sel2.STACCATO.index));
        }

        @Test
        void testReleaseWithoutDragKeepsSelection() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel2.NOTE.index));
            assertAll(
                () -> assertThat(score().getSingleSelectedElement())
                    .as("note stays selected")
                    .isEqualTo(composition().getLine(0).getElement(Sel2.NOTE.index)),
                () -> assertThat(score().getMode()).as("mode stays SELECT").isEqualTo(Mode.SELECT)
            );
        }
    }


    @Nested
    @Order(7)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class Drag {

        @BeforeEach
        void resetState() {
            deselectSelection();
        }

        @Order(1)
        @Test
        void testAltDragTiedNoteFromEditMode() {
            enterEditMode();
            var originalSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.TIED_1.index).getStaffPosition()
            ));
            var targetSp = originalSp - 4;

            var startPoint = noteScreenPosition(0, Sel2.TIED_1.index);
            var endPoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                var endYPx = lc.staffPositionToYPx(targetSp);
                var locationOnScreen = lc.getLocationOnScreen();
                return new Point(startPoint.x, locationOnScreen.y + endYPx);
            }));

            altDrag(startPoint, endPoint);
            performLayout(0);

            assertAll(
                () -> assertThat(score().getMode()).as("mode is SELECT").isEqualTo(Mode.SELECT),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.TIED_1.index).getStaffPosition()
                )).as("tied note source moved").isEqualTo(targetSp),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.TIED_2.index).getStaffPosition()
                )).as("tied note partner moved").isEqualTo(targetSp)
            );
        }

        @Order(2)
        @Test
        void testMultiNoteShiftSelectAndDrag() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel2.STACCATO.index));
            shiftClickAt(noteScreenPosition(0, Sel2.FERMATA.index));

            var originalStaccatoSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.STACCATO.index).getStaffPosition()));
            var originalAccentSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.ACCENT.index).getStaffPosition()));
            var originalFermataSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.FERMATA.index).getStaffPosition()));

            var targetSp = originalAccentSp - 2;
            dragNote(0, Sel2.ACCENT.index, targetSp);
            performLayout(0);

            assertAll(
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.STACCATO.index).getStaffPosition()
                )).as("staccato moved by -2").isEqualTo(originalStaccatoSp - 2),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.ACCENT.index).getStaffPosition()
                )).as("accent moved by -2").isEqualTo(originalAccentSp - 2),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.FERMATA.index).getStaffPosition()
                )).as("fermata moved by -2").isEqualTo(originalFermataSp - 2)
            );
        }

        @Order(3)
        @Test
        void testTieChainExpansionDuringMultiNoteDrag() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel2.FERMATA.index));
            shiftClickAt(noteScreenPosition(0, Sel2.TIED_1.index));

            var currentFermataSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.FERMATA.index).getStaffPosition()));
            var currentTied1Sp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.TIED_1.index).getStaffPosition()));
            var currentTied2Sp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.TIED_2.index).getStaffPosition()));

            var targetSp = currentTied1Sp + 2;
            dragNote(0, Sel2.TIED_1.index, targetSp);
            performLayout(0);

            assertAll(
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.FERMATA.index).getStaffPosition()
                )).as("fermata moved by +2").isEqualTo(currentFermataSp + 2),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.TIED_1.index).getStaffPosition()
                )).as("tied_1 moved by +2").isEqualTo(currentTied1Sp + 2),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.TIED_2.index).getStaffPosition()
                )).as("tied_2 moved by +2 (tie expansion)").isEqualTo(currentTied2Sp + 2)
            );
        }

        @Order(4)
        @Test
        void testDragWithNoteAndRestSelectionMovesOnlyNote() {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel2.DEMI_SEMIQUAVER_REST.index));
            shiftClickAt(noteScreenPosition(0, Sel2.NOTE.index));

            var currentRestSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.DEMI_SEMIQUAVER_REST.index).getStaffPosition()));
            var currentNoteSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.NOTE.index).getStaffPosition()));

            var targetSp = currentNoteSp - 2;
            dragNote(0, Sel2.NOTE.index, targetSp);
            performLayout(0);

            assertAll(
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.NOTE.index).getStaffPosition()
                )).as("note moved by -2").isEqualTo(currentNoteSp - 2),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.DEMI_SEMIQUAVER_REST.index).getStaffPosition()
                )).as("rest unchanged").isEqualTo(currentRestSp),
                () -> assertThat(score().getSelectionSize())
                    .as("both remain selected").isEqualTo(2)
            );
        }
    }


    // -- Assertion helpers --

    private void assertActionEnabled(UIAction action, boolean expected, String label) {
        var isEnabled = GuiActionRunner.execute(() -> action.isEnabled());
        assertThat(isEnabled).as("%s: action '%s' enabled", label, action.getActionCommand()).isEqualTo(expected);
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
        var midpoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
            var lc = Objects.requireNonNull(score().getLineComponent(lineIdx));
            var line = Objects.requireNonNull(lc.getLine());
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
        }));

        robot.moveMouse(midpoint);
        pause();
    }
}
