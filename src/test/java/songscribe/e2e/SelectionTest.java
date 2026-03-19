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
import org.junit.jupiter.api.Test;

import songscribe.music.ArticulationType;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.music.StaffElement.Accidental;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.score.InsertionElementManager;
import songscribe.ui.layout.ScaleContext;

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
        int stepCounter = 0;

        debugStep(++stepCounter, "Mode toggle", (step) -> {
            enterSelectMode();
            assertThat(score().getMode()).as(step + ": select mode").isEqualTo(Mode.SELECT);

            enterEditMode();
            assertThat(score().getMode()).as(step + ": edit mode").isEqualTo(Mode.EDIT);
        });

        debugStep(++stepCounter, "Click empty space deselects", (step) -> {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel1.WHOLE.ordinal()));
            var emptyPoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                var loc = lc.getLocationOnScreen();
                return new Point(loc.x + lc.getWidth() - 10, loc.y + lc.getHeight() - 5);
            }));
            clickAt(emptyPoint);
            assertThat(score().getSelectionSize()).as(step + ": click empty deselects").isEqualTo(0);
        });

        debugStep(++stepCounter, "Shift-click extends selection", (step) -> {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel1.WHOLE.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel1.QUARTER.ordinal()));
            assertThat(score().getSelectionSize()).as(step + ": shift-click range").isEqualTo(3);
        });

        debugStep(++stepCounter, "Shift-click shrinks selection", (step) -> {
            shiftClickAt(noteScreenPosition(0, Sel1.HALF.ordinal()));
            assertAll(
                () -> assertThat(score().getSelectionSize()).as(step + ": shrunk range size").isEqualTo(2),
                () -> assertThat(score().isElementSelected(Sel1.QUARTER.ordinal(), 0))
                    .as(step + ": index 3 not selected").isFalse()
            );
        });

        debugStep(++stepCounter, "Drag-select", (step) -> {
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
            assertThat(score().getSelectionSize()).as(step + ": drag-select").isGreaterThanOrEqualTo(3);
        });

        debugStep(++stepCounter, "Cmd+D deselects", (step) -> {
            clickMenuItem(Actions.DESELECT_ACTION);
            assertThat(score().getSelectionSize()).as(step + ": Cmd+D deselects").isEqualTo(0);
        });

        debugStep(++stepCounter, "Click past elements selects line", (step) -> {
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
            assertThat(score().isLineSelected(0)).as(step + ": line selected").isTrue();
        });

        debugStep(++stepCounter, "Duration reflection", (step) -> {
            assertDurationReflected(step, Sel1.WHOLE.ordinal(), Actions.WHOLE_NOTE_ACTION);
            assertDurationReflected(step, Sel1.HALF.ordinal(), Actions.HALF_NOTE_ACTION);
            assertDurationReflected(step, Sel1.QUARTER.ordinal(), Actions.QUARTER_NOTE_ACTION);
            assertDurationReflected(step, Sel1.EIGHTH.ordinal(), Actions.EIGHTH_NOTE_ACTION);
            assertDurationReflected(step, Sel1.SIXTEENTH.ordinal(), Actions.SIXTEENTH_NOTE_ACTION);
            assertDurationReflected(step, Sel1.THIRTY_SECOND.ordinal(), Actions.THIRTY_SECOND_NOTE_ACTION);
        });

        debugStep(++stepCounter, "Accidental reflection", (step) -> {
            assertAccidentalReflected(step, Sel1.FLAT.ordinal(), Actions.FLAT_ACTION);
            assertAccidentalReflected(step, Sel1.DOUBLE_FLAT.ordinal(), Actions.DOUBLE_FLAT_ACTION);
            assertAccidentalReflected(step, Sel1.NATURAL_FLAT.ordinal(), Actions.NATURAL_FLAT_ACTION);
            assertAccidentalReflected(step, Sel1.NATURAL.ordinal(), Actions.NATURAL_ACTION);
            assertAccidentalReflected(step, Sel1.SHARP.ordinal(), Actions.SHARP_ACTION);
            assertAccidentalReflected(step, Sel1.DOUBLE_SHARP.ordinal(), Actions.DOUBLE_SHARP_ACTION);
            assertAccidentalReflected(step, Sel1.NATURAL_SHARP.ordinal(), Actions.NATURAL_SHARP_ACTION);
        });

        debugStep(++stepCounter, "Accidental-in-parens reflection", (step) -> {
            clickAt(noteScreenPosition(0, Sel1.FLAT_IN_PARENS.ordinal()));
            assertActionSelected(Actions.FLAT_ACTION, true, step + ": underlying flat selected");
            assertActionSelected(Actions.ACCIDENTAL_IN_PARENS_ACTION, true, step + ": in-parens selected");
        });

        debugStep(++stepCounter, "Dot reflection", (step) -> {
            clickAt(noteScreenPosition(0, Sel1.DOTTED.ordinal()));
            assertActionSelected(Actions.DOT_ACTION, true, step + ": dot selected");
            assertActionSelected(Actions.DOUBLE_DOT_ACTION, false, step + ": double-dot not selected");
        });

        debugStep(++stepCounter, "Double-dot reflection", (step) -> {
            clickAt(noteScreenPosition(0, Sel1.DOUBLE_DOTTED.ordinal()));
            assertActionSelected(Actions.DOUBLE_DOT_ACTION, true, step + ": double-dot selected");
            assertActionSelected(Actions.DOT_ACTION, false, step + ": dot not selected");
        });

        debugStep(++stepCounter, "Single quarter elements reflect quarter", (step) -> {
            clickAt(noteScreenPosition(0, Sel1.QUARTER_TEMPO.ordinal()));
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, true, step + "a: index 0 is quarter");
            clickAt(noteScreenPosition(0, Sel1.QUARTER.ordinal()));
            assertActionSelected(Actions.QUARTER_NOTE_ACTION, true, step + "b: index 3 is quarter");
        });

        debugStep(++stepCounter, "Mixed durations deselect both", (step) -> {
            clickAt(noteScreenPosition(0, Sel1.QUARTER.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel1.HALF.ordinal()));
            assertAll(
                () -> assertActionSelected(Actions.QUARTER_NOTE_ACTION, false, step + ": quarter not selected"),
                () -> assertActionSelected(Actions.HALF_NOTE_ACTION, false, step + ": half not selected")
            );
        });

        debugStep(++stepCounter, "Mixed accidentals deselect both", (step) -> {
            clickAt(noteScreenPosition(0, Sel1.FLAT.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel1.DOUBLE_FLAT.ordinal()));
            assertAll(
                () -> assertActionSelected(Actions.FLAT_ACTION, false, step + ": flat not selected"),
                () -> assertActionSelected(Actions.DOUBLE_FLAT_ACTION, false, step + ": double-flat not selected")
            );
        });

        loadFixture("selection2");

        debugStep(++stepCounter, "Rest reflects duration + rest mode", (step) -> {
            enterSelectMode();

            // Select crotchet rest — large enough to click reliably
            clickAt(noteScreenPosition(0, Sel2.CROTCHET_REST.ordinal()));
            assertDurationReflected(step, Sel2.CROTCHET_REST.ordinal(), Actions.QUARTER_NOTE_ACTION);
            assertActionSelected(Actions.REST_ACTION, true, step + ": rest mode selected");
        });

        debugStep(++stepCounter, "Articulation reflection", (step) -> {
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            assertActionSelected(Actions.STACCATO_ACTION, true, step + ": staccato selected");

            clickAt(noteScreenPosition(0, Sel2.ACCENT.ordinal()));
            assertActionSelected(Actions.ACCENT_ACTION, true, step + ": accent selected");

            clickAt(noteScreenPosition(0, Sel2.FERMATA.ordinal()));
            assertActionSelected(Actions.FERMATA_ACTION, true, step + ": fermata selected");
        });

        debugStep(++stepCounter, "Single barline disables durations", (step) -> {
            clickAt(noteScreenPosition(0, Sel2.SINGLE_BARLINE.ordinal()));
            assertAll(
                () -> verifyDurationsDisabled(step + ""),
                () -> assertActionEnabled(Actions.BARLINE_ACTIONS[2], true, step + ": barline action enabled")
            );
        });

        debugStep(++stepCounter, "Other barlines/repeats disable durations", (step) -> {
            clickAt(noteScreenPosition(0, Sel2.DOUBLE_BARLINE.ordinal()));
            verifyDurationsDisabled(step + ": double");

            clickAt(noteScreenPosition(0, Sel2.FINAL_DOUBLE_BARLINE.ordinal()));
            verifyDurationsDisabled(step + ": final double");

            clickAt(noteScreenPosition(0, Sel2.REPEAT_LEFT.ordinal()));
            verifyDurationsDisabled(step + ": repeat left");

            clickAt(noteScreenPosition(0, Sel2.REPEAT_RIGHT.ordinal()));
            verifyDurationsDisabled(step + ": repeat right");

            clickAt(noteScreenPosition(0, Sel2.REPEAT_LEFT_RIGHT.ordinal()));
            verifyDurationsDisabled(step + ": repeat left-right");
        });

        debugStep(++stepCounter, "Breath mark disables durations and barlines", (step) -> {
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
            assertAll(
                () -> verifyDurationsDisabled(step + ""),
                () -> assertActionEnabled(Actions.BARLINE_ACTIONS[2], false, step + ": barline disabled"),
                () -> assertActionEnabled(Actions.BREATH_MARK_ACTION, true, step + ": breath mark enabled")
            );
        });

        debugStep(++stepCounter, "Note + rest enables durations", (step) -> {
            clickAt(noteScreenPosition(0, Sel2.CROTCHET_REST.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.NOTE.ordinal()));
            assertActionEnabled(Actions.QUARTER_NOTE_ACTION, true, step + ": duration enabled for note+rest");
        });

        debugStep(++stepCounter, "Note + barline enables both", (step) -> {
            clickAt(noteScreenPosition(0, Sel2.NOTE.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.SINGLE_BARLINE.ordinal()));
            assertAll(
                () -> assertActionEnabled(Actions.QUARTER_NOTE_ACTION, true, step + ": duration enabled"),
                () -> assertActionEnabled(Actions.BARLINE_ACTIONS[2], true, step + ": barline enabled")
            );
        });

        debugStep(++stepCounter, "Barlines only disables durations", (step) -> {
            clickAt(noteScreenPosition(0, Sel2.SINGLE_BARLINE.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.DOUBLE_BARLINE.ordinal()));
            assertAll(
                () -> assertActionEnabled(Actions.QUARTER_NOTE_ACTION, false, step + ": duration disabled"),
                () -> assertActionEnabled(Actions.DOT_ACTION, false, step + ": dot disabled"),
                () -> assertActionEnabled(Actions.BARLINE_ACTIONS[2], true, step + ": barline enabled")
            );
        });

        debugStep(++stepCounter, "Deselect clears selection", (step) -> {
            clickMenuItem(Actions.DESELECT_ACTION);
            assertThat(score().getSelectionSize()).as(step + ": selection cleared").isEqualTo(0);
        });

        debugStep(++stepCounter, "Glissando suppressed when target is rest", (step) -> {
            enterEditMode();
            clickToolbarButton(Actions.GLISSANDO_ACTION);
            hoverBetween(0, Sel2.TIED_2.ordinal(), Sel2.SEMIBREVE_REST.ordinal());
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as(step + ": target is rest").isFalse();
        });

        debugStep(++stepCounter, "Glissando suppressed when source is rest", (step) -> {
            hoverBetween(0, Sel2.DEMI_SEMIQUAVER_REST.ordinal(), Sel2.NOTE.ordinal());
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as(step + ": source is rest").isFalse();
        });

        debugStep(++stepCounter, "Glissando suppressed when both are rests", (step) -> {
            hoverBetween(0, Sel2.SEMIBREVE_REST.ordinal(), Sel2.MINIM_REST.ordinal());
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as(step + ": both rests").isFalse();
        });

        debugStep(++stepCounter, "Slide-out suppressed when source is rest", (step) -> {
            clickToolbarButton(Actions.SLIDE_OUT_ACTION);
            hoverBetween(0, Sel2.DEMI_SEMIQUAVER_REST.ordinal(), Sel2.NOTE.ordinal());
            assertThat(GuiActionRunner.execute(() -> InsertionElementManager.shouldShowGlissandoPreview()))
                .as(step + ": source is rest").isFalse();
            selectDuration(Actions.QUARTER_NOTE_ACTION);
        });

        debugStep(++stepCounter, "Apply eighth preserves decorations", (step) -> {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.FERMATA.ordinal()));
            clickToolbarButton(Actions.EIGHTH_NOTE_ACTION);
            assertAll(
                () -> verifyNoteType(0, Sel2.STACCATO.ordinal(), ElementType.QUAVER, step + "a: type"),
                () -> verifyNoteType(0, Sel2.ACCENT.ordinal(), ElementType.QUAVER, step + "b: type"),
                () -> verifyNoteType(0, Sel2.FERMATA.ordinal(), ElementType.QUAVER, step + "c: type"),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.STACCATO.ordinal())
                        .hasArticulation(ArticulationType.STACCATO)))
                    .as(step + "a: staccato preserved").isTrue(),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.ACCENT.ordinal())
                        .hasArticulation(ArticulationType.ACCENT)))
                    .as(step + "b: accent preserved").isTrue(),
                () -> verifyFermata(0, Sel2.FERMATA.ordinal(), true, step + "c: fermata preserved"),
                () -> assertThat(score().getSelectionSize()).as(step + ": selection preserved").isEqualTo(3)
            );
        });

        debugStep(++stepCounter, "Apply half preserves note/rest kind", (step) -> {
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.CROTCHET_REST.ordinal()));
            clickToolbarButton(Actions.HALF_NOTE_ACTION);

            var staccatoType = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.STACCATO.ordinal()).getType()));
            var accentType = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.ACCENT.ordinal()).getType()));
            var fermataType = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.FERMATA.ordinal()).getType()));
            var restType = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.CROTCHET_REST.ordinal()).getType()));

            assertAll(
                () -> assertThat(staccatoType).as(step + "a: staccato is minim").isEqualTo(ElementType.MINIM),
                () -> assertThat(accentType).as(step + "b: accent is minim").isEqualTo(ElementType.MINIM),
                () -> assertThat(fermataType).as(step + "c: fermata is minim").isEqualTo(ElementType.MINIM),
                () -> assertThat(restType).as(step + ": rest is minim rest").isEqualTo(ElementType.MINIM_REST),
                () -> assertThat(staccatoType.isRest()).as(step + ": staccato is still a note").isFalse(),
                () -> assertThat(restType.isRest()).as(step + ": rest is still a rest").isTrue()
            );
        });

        debugStep(++stepCounter, "Apply flat to selection", (step) -> {
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.ACCENT.ordinal()));
            clickToolbarButton(Actions.FLAT_ACTION);
            assertAll(
                () -> verifyAccidental(0, Sel2.STACCATO.ordinal(), Accidental.FLAT, step + "a"),
                () -> verifyAccidental(0, Sel2.ACCENT.ordinal(), Accidental.FLAT, step + "b"),
                () -> assertThat(score().getSelectionSize()).as(step + ": selection preserved").isEqualTo(2)
            );
        });

        debugStep(++stepCounter, "Apply natural to same selection", (step) -> {
            clickToolbarButton(Actions.NATURAL_ACTION);
            assertAll(
                () -> verifyAccidental(0, Sel2.STACCATO.ordinal(), Accidental.NATURAL, step + "a"),
                () -> verifyAccidental(0, Sel2.ACCENT.ordinal(), Accidental.NATURAL, step + "b")
            );
        });

        debugStep(++stepCounter, "Apply dot to notes and rest", (step) -> {
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.CROTCHET_REST.ordinal()));
            clickToolbarButton(Actions.DOT_ACTION);
            assertAll(
                () -> verifyDotCount(0, Sel2.STACCATO.ordinal(), 1, step + "a"),
                () -> verifyDotCount(0, Sel2.ACCENT.ordinal(), 1, step + "b"),
                () -> verifyDotCount(0, Sel2.FERMATA.ordinal(), 1, step + "c"),
                () -> verifyDotCount(0, Sel2.CROTCHET_REST.ordinal(), 1, step + "d")
            );
        });

        debugStep(++stepCounter, "Apply fermata", (step) -> {
            clickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            shiftClickAt(noteScreenPosition(0, Sel2.ACCENT.ordinal()));
            clickMenuItem(Actions.FERMATA_ACTION);
            assertAll(
                () -> verifyFermata(0, Sel2.STACCATO.ordinal(), true, step + "a"),
                () -> verifyFermata(0, Sel2.ACCENT.ordinal(), true, step + "b")
            );
        });

        debugStep(++stepCounter, "Toggle fermata off", (step) -> {
            clickMenuItem(Actions.FERMATA_ACTION);
            assertAll(
                () -> verifyFermata(0, Sel2.STACCATO.ordinal(), false, step + "a"),
                () -> verifyFermata(0, Sel2.ACCENT.ordinal(), false, step + "b")
            );
        });

        debugStep(++stepCounter, "Alt+drag tied note from EDIT mode", (step) -> {
            enterEditMode();
            var originalSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.TIED_1.ordinal()).getStaffPosition()
            ));
            var targetSp = originalSp - 4;

            var startPoint = noteScreenPosition(0, Sel2.TIED_1.ordinal());
            var endPoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                var endYPx = lc.staffPositionToYPx(targetSp);
                var locationOnScreen = lc.getLocationOnScreen();
                return new Point(startPoint.x, locationOnScreen.y + endYPx);
            }));

            altDrag(startPoint, endPoint);
            performLayout(0);

            assertAll(
                () -> assertThat(score().getMode()).as(step + ": mode is SELECT").isEqualTo(Mode.SELECT),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.TIED_1.ordinal()).getStaffPosition()
                )).as(step + ": tied note source moved").isEqualTo(targetSp),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.TIED_2.ordinal()).getStaffPosition()
                )).as(step + ": tied note partner moved").isEqualTo(targetSp)
            );
        });

        debugStep(++stepCounter, "Drag note in SELECT mode", (step) -> {
            var countBefore = Objects.requireNonNull(GuiActionRunner.execute(() -> composition().getLine(0).elementCount()));
            var originalSp = Objects.requireNonNull(GuiActionRunner.execute(
                () -> composition().getLine(0).getElement(Sel2.NOTE.ordinal()).getStaffPosition()
            ));
            var targetSp = originalSp - 4;

            dragNote(0, Sel2.NOTE.ordinal(), targetSp);
            performLayout(0);

            assertAll(
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).getElement(Sel2.NOTE.ordinal()).getStaffPosition()
                )).as(step + ": staffPosition updated").isEqualTo(targetSp),
                () -> assertThat(GuiActionRunner.execute(
                    () -> composition().getLine(0).elementCount()
                )).as(step + ": elementCount unchanged").isEqualTo(countBefore)
            );
        });

        debugStep(++stepCounter, "Alt+click on empty space enters SELECT, no selection", (step) -> {
            enterEditMode();
            var emptyPoint = Objects.requireNonNull(GuiActionRunner.execute(() -> {
                var lc = Objects.requireNonNull(score().getLineComponent(0));
                var loc = lc.getLocationOnScreen();
                return new Point(loc.x + lc.getWidth() - 10, loc.y + lc.getHeight() / 2);
            }));
            altClickAt(emptyPoint);
            assertAll(
                () -> assertThat(score().getMode()).as(step + ": mode is SELECT").isEqualTo(Mode.SELECT),
                () -> assertThat(score().getSelectionSize()).as(step + ": no selection").isEqualTo(0)
            );
        });

        debugStep(++stepCounter, "Click note in SELECT mode selects it", (step) -> {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel2.NOTE.ordinal()));
            assertAll(
                () -> assertThat(score().getSingleSelectedElement())
                    .as(step + ": note selected")
                    .isEqualTo(composition().getLine(0).getElement(Sel2.NOTE.ordinal())),
                () -> assertThat(score().getMode()).as(step + ": mode stays SELECT").isEqualTo(Mode.SELECT)
            );
        });

        debugStep(++stepCounter, "Alt+click in SELECT mode same as click", (step) -> {
            enterSelectMode();
            altClickAt(noteScreenPosition(0, Sel2.STACCATO.ordinal()));
            assertThat(score().getSingleSelectedElement())
                .as(step + ": note selected via alt+click")
                .isEqualTo(composition().getLine(0).getElement(Sel2.STACCATO.ordinal()));
        });

        debugStep(++stepCounter, "Release without drag keeps selection", (step) -> {
            enterSelectMode();
            clickAt(noteScreenPosition(0, Sel2.NOTE.ordinal()));
            assertAll(
                () -> assertThat(score().getSingleSelectedElement())
                    .as(step + ": note stays selected")
                    .isEqualTo(composition().getLine(0).getElement(Sel2.NOTE.ordinal())),
                () -> assertThat(score().getMode()).as(step + ": mode stays SELECT").isEqualTo(Mode.SELECT)
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
    private void assertDurationReflected(int step, int elementIndex, UIAction expectedAction) {
        clickAt(noteScreenPosition(0, elementIndex));

        for (var action : DURATION_ACTIONS) {
            boolean expected = (action == expectedAction);
            assertActionSelected(action, expected,
                step + ": duration reflection for index " + elementIndex);
        }
    }

    /**
     * Selects a single element and verifies the given accidental action is selected
     * while all other accidental actions are deselected.
     */
    private void assertAccidentalReflected(int step, int elementIndex, UIAction expectedAction) {
        clickAt(noteScreenPosition(0, elementIndex));

        for (var action : accidentalActions()) {
            boolean expected = (action == expectedAction);
            assertActionSelected(action, expected,
                step + ": accidental reflection for index " + elementIndex);
        }
    }

    /**
     * Verifies all duration actions are deselected.
     */
    private void verifyNoDurationSelected(String label) {
        for (var action : DURATION_ACTIONS) {
            assertActionSelected(action, false, label + ": no duration selected");
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
