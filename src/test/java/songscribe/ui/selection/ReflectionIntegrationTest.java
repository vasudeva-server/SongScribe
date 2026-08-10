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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.minim;
import static songscribe.dom.StaffElementFactory.singleBarline;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.AccidentalInParensAction;
import songscribe.ui.action.ArticulationAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.FinalDoubleBarlineAction;
import songscribe.ui.action.UIAction;
import songscribe.dom.FermataAttachment;
import songscribe.ui.component.MainFrame;

class ReflectionIntegrationTest extends UnitTest {

    private static final MainFrame MOCK_FRAME = mock(MainFrame.class, RETURNS_DEEP_STUBS);

    private ElementTypeAction crotchetAction;
    private ElementTypeAction minimAction;
    private ElementTypeAction barlineAction;
    private ElementTypeAction rightRepeatAction;
    private FinalDoubleBarlineAction finalDoubleBarlineAction;
    private AccidentalAction sharpAction;
    private AccidentalAction flatAction;
    private DotAction dotAction;
    private DotAction doubleDotAction;
    private FermataAction fermataAction;
    private ArticulationAction staccatoAction;
    private AccidentalInParensAction accidentalInParensAction;

    @BeforeEach
    void setUp() {
        crotchetAction = ElementTypeAction.createQuarterNoteAction(MOCK_FRAME);
        minimAction = ElementTypeAction.createHalfNoteAction(MOCK_FRAME);
        barlineAction = ElementTypeAction.createSingleBarlineAction(MOCK_FRAME);
        rightRepeatAction = ElementTypeAction.createRightRepeatAction(MOCK_FRAME);
        finalDoubleBarlineAction = FinalDoubleBarlineAction.createAction(MOCK_FRAME);
        sharpAction = AccidentalAction.createSharpAction(MOCK_FRAME);
        flatAction = AccidentalAction.createFlatAction(MOCK_FRAME);
        dotAction = DotAction.createDotAction(MOCK_FRAME);
        doubleDotAction = DotAction.createDoubleDotAction(MOCK_FRAME);
        fermataAction = FermataAction.createAction(MOCK_FRAME);
        staccatoAction = ArticulationAction.createStaccatoAction(MOCK_FRAME);
        accidentalInParensAction = AccidentalInParensAction.createAction(MOCK_FRAME);
    }

    private List<UIAction.Reflectable> allActions() {
        return List.of(
            crotchetAction, minimAction, barlineAction,
            sharpAction, flatAction,
            dotAction, doubleDotAction,
            fermataAction, staccatoAction,
            accidentalInParensAction
        );
    }

    private void assertSelected(UIAction.Reflectable action, boolean expected) {
        assertThat(action.isSelected()).isEqualTo(expected);
    }

    @Test
    void testCrotchetAndCrotchetRest() {
        var note1 = crotchet();
        note1.setAccidental(StaffElement.Accidental.SHARP);
        var rest = crotchetRest();

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, rest), allActions()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertSelected(crotchetAction, true);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, false);
        assertSelected(sharpAction, true);
        assertSelected(flatAction, false);
        assertSelected(dotAction, false);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, false);
        assertSelected(staccatoAction, false);
        assertSelected(accidentalInParensAction, false);
    }

    @Test
    void testCrotchetAndMinimBothSharp() {
        var note1 = crotchet();
        note1.setAccidental(StaffElement.Accidental.SHARP);
        var note2 = minim();
        note2.setAccidental(StaffElement.Accidental.SHARP);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2), allActions()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertSelected(crotchetAction, false);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, false);
        assertSelected(sharpAction, true);
        assertSelected(flatAction, false);
        assertSelected(dotAction, false);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, false);
        assertSelected(staccatoAction, false);
        assertSelected(accidentalInParensAction, false);
    }

    @Test
    void testSingleCrotchetSharpDottedFermata() {
        var note = crotchet();
        note.setAccidental(StaffElement.Accidental.SHARP);
        note.setDotCount(1);
        note.addAttachment(new FermataAttachment(note));

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note), allActions()
        );
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.getActionReflector().triggerReflection();

        assertSelected(crotchetAction, true);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, false);
        assertSelected(sharpAction, true);
        assertSelected(flatAction, false);
        assertSelected(dotAction, true);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, true);
        assertSelected(staccatoAction, false);
        assertSelected(accidentalInParensAction, false);
    }

    @Test
    void testTwoCrotchetRests() {
        var rest1 = crotchetRest();
        var rest2 = crotchetRest();

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(rest1, rest2), allActions()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertSelected(crotchetAction, true);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, false);
        assertSelected(sharpAction, false);
        assertSelected(flatAction, false);
        assertSelected(dotAction, false);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, false);
        assertSelected(staccatoAction, false);
        assertSelected(accidentalInParensAction, false);
    }

    @Test
    void testTwoCrotchetsSharpNoDot() {
        var note1 = crotchet();
        note1.setAccidental(StaffElement.Accidental.SHARP);
        var note2 = crotchet();
        note2.setAccidental(StaffElement.Accidental.SHARP);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2), allActions()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertSelected(crotchetAction, true);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, false);
        assertSelected(sharpAction, true);
        assertSelected(flatAction, false);
        assertSelected(dotAction, false);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, false);
        assertSelected(staccatoAction, false);
        assertSelected(accidentalInParensAction, false);
    }

    @Test
    void testTwoSingleBarlines() {
        var barline1 = singleBarline();
        var barline2 = singleBarline();

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(barline1, barline2), allActions()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertSelected(crotchetAction, false);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, true);
        assertSelected(sharpAction, false);
        assertSelected(flatAction, false);
        assertSelected(dotAction, false);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, false);
        assertSelected(staccatoAction, false);
        assertSelected(accidentalInParensAction, false);
    }

    // -------------------------------------------------------------------------
    // The song's auto-maintained terminal (issue #713)
    // -------------------------------------------------------------------------
    //
    // Selecting the terminal must check the barline entry standing for its current type and
    // leave every other entry unchecked. This is the check-mark the user sees in the Barline
    // and Repeats menus, and reflection is the only thing that sets it — an action's own
    // updateEnabledState() never touches its checked state. Asserting the predicates in
    // isolation (as TerminalTypeActionTest does) would not catch a reflector that never
    // reaches these entries at all.

    /**
     * A real song whose only line ends in the auto-maintained terminal, preceded by one note.
     * The terminal exists only as the last element of a song's last line, so a detached line
     * cannot stand in here.
     */
    private static Line lineEndingInTerminal(Song song) {
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(crotchet()));
        return line;
    }

    private List<UIAction.Reflectable> terminalActions() {
        return List.of(finalDoubleBarlineAction, rightRepeatAction, barlineAction);
    }

    private void reflectTerminalOf(Song song) {
        var line = lineEndingInTerminal(song);
        var coordinator =
            ReflectionTestHelper.createCoordinatorForLine(line, terminalActions());

        ReflectionTestHelper.selectNote(coordinator, line.elementCount() - 1);
        coordinator.getActionReflector().triggerReflection();
    }

    @Test
    void testFinalDoubleBarlineEntryIsCheckedForAFinalDoubleBarlineTerminal() {
        reflectTerminalOf(new Song());

        assertSelected(finalDoubleBarlineAction, true);
        assertSelected(rightRepeatAction, false);
        assertSelected(barlineAction, false);
    }

    @Test
    void testRightRepeatEntryIsCheckedForARightRepeatTerminal() {
        var song = new Song();
        song.replaceTerminal(ElementType.REPEAT_RIGHT);

        reflectTerminalOf(song);

        assertSelected(rightRepeatAction, true);
        assertSelected(finalDoubleBarlineAction, false);
        assertSelected(barlineAction, false);
    }

    @Test
    void testAccidentalInParensNotSelectedWhenOnlyOneParenthesized() {
        var note1 = crotchet();
        note1.setAccidental(StaffElement.Accidental.SHARP);
        note1.setAccidentalInParentheses(true);
        var note2 = minim();
        note2.setAccidental(StaffElement.Accidental.SHARP);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2), List.of(accidentalInParensAction)
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertSelected(accidentalInParensAction, false);
    }

    @Test
    void testAccidentalInParensSelectedWhenAllParenthesized() {
        var note1 = crotchet();
        note1.setAccidental(StaffElement.Accidental.SHARP);
        note1.setAccidentalInParentheses(true);
        var note2 = minim();
        note2.setAccidental(StaffElement.Accidental.SHARP);
        note2.setAccidentalInParentheses(true);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2), List.of(accidentalInParensAction)
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.getActionReflector().triggerReflection();

        assertSelected(accidentalInParensAction, true);
    }
}
