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

import songscribe.music.DurationArticulation;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.DurationArticulationAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.NoteTypeAction;
import songscribe.ui.action.NoteTypeAction.Kind;
import songscribe.ui.action.UIAction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectionIntegrationTest {

    private NoteTypeAction crotchetAction;
    private NoteTypeAction minimAction;
    private NoteTypeAction barlineAction;
    private AccidentalAction sharpAction;
    private AccidentalAction flatAction;
    private DotAction dotAction;
    private DotAction doubleDotAction;
    private FermataAction fermataAction;
    private DurationArticulationAction staccatoAction;

    @BeforeEach
    void setUp() {
        crotchetAction = new NoteTypeAction(Kind.DURATION, NoteType.CROTCHET, "Quarter", null, 0, "quarter", "Quarter note", 0, 0);
        minimAction = new NoteTypeAction(Kind.DURATION, NoteType.MINIM, "Half", null, 0, "half", "Half note", 0, 0);
        barlineAction = new NoteTypeAction(Kind.NON_DURATION, NoteType.SINGLE_BARLINE, "Barline", null, 0, "barline", "Single barline", 0, 0);
        sharpAction = new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");
        flatAction = new AccidentalAction(Note.Accidental.FLAT, "Flat", null, 0, "flat", "Flat");
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
        assertThat(((UIAction) action).isSelected()).isEqualTo(expected);
    }

    @Test
    void testTwoCrotchetsSharpNoDot() {
        var note1 = NoteType.CROTCHET.newInstance();
        note1.setAccidental(Note.Accidental.SHARP);
        var note2 = NoteType.CROTCHET.newInstance();
        note2.setAccidental(Note.Accidental.SHARP);

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
        var note1 = NoteType.CROTCHET.newInstance();
        note1.setAccidental(Note.Accidental.SHARP);
        var note2 = NoteType.MINIM.newInstance();
        note2.setAccidental(Note.Accidental.SHARP);

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
        var note1 = NoteType.CROTCHET.newInstance();
        note1.setAccidental(Note.Accidental.SHARP);
        var rest = NoteType.CROTCHET_REST.newInstance();

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
        var rest1 = NoteType.CROTCHET_REST.newInstance();
        var rest2 = NoteType.CROTCHET_REST.newInstance();

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
        var barline1 = NoteType.SINGLE_BARLINE.newInstance();
        var barline2 = NoteType.SINGLE_BARLINE.newInstance();

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
        var note = NoteType.CROTCHET.newInstance();
        note.setAccidental(Note.Accidental.SHARP);
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
