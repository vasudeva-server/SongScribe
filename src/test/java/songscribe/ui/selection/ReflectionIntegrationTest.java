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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.DurationArticulation;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.DurationArticulationAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.ElementTypeAction.Kind;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;

class ReflectionIntegrationTest extends UnitTest {

    private ElementTypeAction crotchetAction;
    private ElementTypeAction minimAction;
    private ElementTypeAction barlineAction;
    private AccidentalAction sharpAction;
    private AccidentalAction flatAction;
    private DotAction dotAction;
    private DotAction doubleDotAction;
    private FermataAction fermataAction;
    private DurationArticulationAction staccatoAction;

    @BeforeEach
    void setUp() {
        crotchetAction = new ElementTypeAction(Kind.DURATION, ElementType.CROTCHET, "Quarter", null, 0, "quarter", "Quarter note", 0, 0);
        minimAction = new ElementTypeAction(Kind.DURATION, ElementType.MINIM, "Half", null, 0, "half", "Half note", 0, 0);
        barlineAction = new ElementTypeAction(Kind.NON_DURATION, ElementType.SINGLE_BARLINE, "Barline", null, 0, "barline", "Single barline", 0, 0);
        sharpAction = new AccidentalAction(StaffElement.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        flatAction = new AccidentalAction(StaffElement.Accidental.FLAT, "Flat", null, 0, "flat", "Flat");
        dotAction = new DotAction(DotAction.DotLevel.SINGLE, "Dot", null, 0, "dot", "Add dot", 0, 0);
        doubleDotAction = new DotAction(DotAction.DotLevel.DOUBLE, "Double Dot", null, 0, "double-dot", "Add double dot", 0, 0);
        fermataAction = new FermataAction();
        staccatoAction = new DurationArticulationAction(DurationArticulation.STACCATO, "Staccato", null, 0, "staccato", "Add staccato");
    }

    private List<UIAction.Reflectable> allActions() {
        return List.of(
            crotchetAction, minimAction, barlineAction,
            sharpAction, flatAction,
            dotAction, doubleDotAction,
            fermataAction, staccatoAction
        );
    }

    private void assertSelected(UIAction.Reflectable action, boolean expected) {
        assertThat(((UIAction.Selectable) action).isSelected()).isEqualTo(expected);
    }

    @Test
    void testTwoCrotchetsSharpNoDot() {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setAccidental(StaffElement.Accidental.SHARP);
        var note2 = ElementType.CROTCHET.newInstance();
        note2.setAccidental(StaffElement.Accidental.SHARP);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2), allActions()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertSelected(crotchetAction, true);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, false);
        assertSelected(sharpAction, true);
        assertSelected(flatAction, false);
        assertSelected(dotAction, false);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, false);
        assertSelected(staccatoAction, false);
    }

    @Test
    void testCrotchetAndMinimBothSharp() {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setAccidental(StaffElement.Accidental.SHARP);
        var note2 = ElementType.MINIM.newInstance();
        note2.setAccidental(StaffElement.Accidental.SHARP);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, note2), allActions()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertSelected(crotchetAction, false);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, false);
        assertSelected(sharpAction, true);
        assertSelected(flatAction, false);
        assertSelected(dotAction, false);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, false);
        assertSelected(staccatoAction, false);
    }

    @Test
    void testCrotchetAndCrotchetRest() {
        var note1 = ElementType.CROTCHET.newInstance();
        note1.setAccidental(StaffElement.Accidental.SHARP);
        var rest = ElementType.CROTCHET_REST.newInstance();

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note1, rest), allActions()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertSelected(crotchetAction, false);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, false);
        assertSelected(sharpAction, true);
        assertSelected(flatAction, false);
        assertSelected(dotAction, false);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, false);
        assertSelected(staccatoAction, false);
    }

    @Test
    void testTwoCrotchetRests() {
        var rest1 = ElementType.CROTCHET_REST.newInstance();
        var rest2 = ElementType.CROTCHET_REST.newInstance();

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(rest1, rest2), allActions()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertSelected(crotchetAction, false);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, false);
        assertSelected(sharpAction, false);
        assertSelected(flatAction, false);
        assertSelected(dotAction, false);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, false);
        assertSelected(staccatoAction, false);
    }

    @Test
    void testTwoSingleBarlines() {
        var barline1 = ElementType.SINGLE_BARLINE.newInstance();
        var barline2 = ElementType.SINGLE_BARLINE.newInstance();

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(barline1, barline2), allActions()
        );
        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.reflectSelection(null);

        assertSelected(crotchetAction, false);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, true);
        assertSelected(sharpAction, false);
        assertSelected(flatAction, false);
        assertSelected(dotAction, false);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, false);
        assertSelected(staccatoAction, false);
    }

    @Test
    void testSingleCrotchetSharpDottedFermata() {
        var note = ElementType.CROTCHET.newInstance();
        note.setAccidental(StaffElement.Accidental.SHARP);
        note.setDotCount(1);
        note.setFermata(true);

        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(note), allActions()
        );
        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.reflectSelection(null);

        assertSelected(crotchetAction, true);
        assertSelected(minimAction, false);
        assertSelected(barlineAction, false);
        assertSelected(sharpAction, true);
        assertSelected(flatAction, false);
        assertSelected(dotAction, true);
        assertSelected(doubleDotAction, false);
        assertSelected(fermataAction, true);
        assertSelected(staccatoAction, false);
    }
}
