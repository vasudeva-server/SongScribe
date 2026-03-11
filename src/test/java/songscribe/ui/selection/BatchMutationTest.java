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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static songscribe.ui.action.ElementTypeAction.Kind;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.data.BeamInterval;
import songscribe.data.TieInterval;
import songscribe.data.TupletInterval;
import songscribe.music.Composition;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;

class BatchMutationTest extends UnitTest {

    private static final FermataAction FERMATA_ACTION = new FermataAction();

    private static final AccidentalAction SHARP_ACTION =
        new AccidentalAction(StaffElement.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");

    private static final ElementTypeAction QUARTER_ACTION = new ElementTypeAction(
        Kind.DURATION, ElementType.CROTCHET, "Quarter", null, 0, "quarter", "Quarter note", 0, 0
    );

    /**
     * Creates a coordinator with a composition mock on the line,
     * so applyActionToSelection can call line.getComposition().setModified().
     */
    private SelectionCoordinator createCoordinator(
        List<StaffElement> notes,
        List<UIAction.Reflectable> actions
    ) {
        var coordinator = ReflectionTestHelper.createCoordinator(notes, actions);
        var line = coordinator.getActiveSelection().getLine();
        line.setComposition(mock(Composition.class));
        return coordinator;
    }

    private Line getLine(SelectionCoordinator coordinator) {
        return coordinator.getActiveSelection().getLine();
    }

    // -- Inapplicable notes are skipped --

    @Test
    void testAccidentalSkipsRests() {
        var notes = List.of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET_REST.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(SHARP_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(SHARP_ACTION, true);

        var line = getLine(coordinator);
        assertThat(line.getElement(0).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
        assertThat(line.getElement(1).getAccidental()).isEqualTo(StaffElement.Accidental.NONE);
        assertThat(line.getElement(2).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }

    // -- Apply attribute (in-place mutation) --

    @Test
    void testApplyFermataToSelection() {
        var notes = List.of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(FERMATA_ACTION, true);

        var line = getLine(coordinator);

        for (int i = 0; i <= 2; i++) {
            assertThat(line.getElement(i).isFermata())
                .as("note %d should have fermata", i)
                .isTrue();
        }
    }

    // -- Beam interval validation --

    @Test
    void testBeamDissolvedWhenAllNonBeamable() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getBeamings().addInterval(new BeamInterval(0, 2));

        ReflectionTestHelper.selectRange(coordinator, 0, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getBeamings().isEmpty()).isTrue();
    }

    @Test
    void testBeamDissolvedWhenSubgroupTooSmall() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getBeamings().addInterval(new BeamInterval(0, 1));

        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getBeamings().findInterval(0)).isNull();
        assertThat(line.getBeamings().findInterval(1)).isNull();
    }

    @Test
    void testBeamShrunkFromEnd() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getBeamings().addInterval(new BeamInterval(0, 3));

        ReflectionTestHelper.selectNote(coordinator, 3);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var beam = line.getBeamings().findInterval(0);
        assertThat(beam).isNotNull();
        assertThat(beam.start).isEqualTo(0);
        assertThat(beam.end).isEqualTo(2);
        assertThat(line.getBeamings().findInterval(3)).isNull();
    }

    @Test
    void testBeamShrunkFromStart() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getBeamings().addInterval(new BeamInterval(0, 3));

        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var beam = line.getBeamings().findInterval(1);
        assertThat(beam).isNotNull();
        assertThat(beam.start).isEqualTo(1);
        assertThat(beam.end).isEqualTo(3);
        assertThat(line.getBeamings().findInterval(0)).isNull();
    }

    @Test
    void testBeamSplitAroundNonBeamable() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getBeamings().addInterval(new BeamInterval(0, 4));

        ReflectionTestHelper.selectNote(coordinator, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.CROTCHET);

        var beam0 = line.getBeamings().findInterval(0);
        assertThat(beam0).isNotNull();
        assertThat(beam0.start).isEqualTo(0);
        assertThat(beam0.end).isEqualTo(1);

        var beam3 = line.getBeamings().findInterval(3);
        assertThat(beam3).isNotNull();
        assertThat(beam3.start).isEqualTo(3);
        assertThat(beam3.end).isEqualTo(4);

        assertThat(line.getBeamings().findInterval(2)).isNull();
    }

    // -- Composition is marked modified --

    @Test
    void testCompositionMarkedModified() {
        var notes = List.of(ElementType.CROTCHET.newInstance());
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectNote(coordinator, 0);

        coordinator.applyActionToSelection(FERMATA_ACTION, true);

        var composition = getLine(coordinator).getComposition();
        verify(composition).setModified(true);
    }

    // -- Duration change replaces notes --

    @Test
    void testDurationChangePreservesAttributes() {
        var note = ElementType.QUAVER.newInstance();
        note.setFermata(true);
        note.setAccidental(StaffElement.Accidental.SHARP);

        var coordinator = createCoordinator(List.of(note), List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectNote(coordinator, 0);

        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var replaced = getLine(coordinator).getElement(0);
        assertThat(replaced.getType()).isEqualTo(ElementType.CROTCHET);
        assertThat(replaced.isFermata()).isTrue();
        assertThat(replaced.getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
    }

    @Test
    void testDurationChangePreservesNoteRestKind() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER_REST.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var line = getLine(coordinator);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
        assertThat(line.getElement(1).getType()).isEqualTo(ElementType.CROTCHET_REST);
        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.CROTCHET);
    }

    @Test
    void testDurationChangeReplacesNotes() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var line = getLine(coordinator);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
        assertThat(line.getElement(1).getType()).isEqualTo(ElementType.CROTCHET);
    }

    // -- Duration change skips barlines --

    @Test
    void testDurationChangeSkipsBarlines() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.SINGLE_BARLINE.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var line = getLine(coordinator);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
        assertThat(line.getElement(1).getType()).isEqualTo(ElementType.SINGLE_BARLINE);
        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.CROTCHET);
    }

    // -- Duration un-apply is a no-op --

    @Test
    void testDurationUnApplyIsNoOp() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        coordinator.applyActionToSelection(QUARTER_ACTION, false);

        var line = getLine(coordinator);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.QUAVER);
        assertThat(line.getElement(1).getType()).isEqualTo(ElementType.QUAVER);
    }

    // -- No selection is a no-op --

    @Test
    void testNoSelectionIsNoOp() {
        var notes = List.of(ElementType.CROTCHET.newInstance());
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));

        // No selection set -- should not throw
        coordinator.applyActionToSelection(FERMATA_ACTION, true);

        assertThat(getLine(coordinator).getElement(0).isFermata()).isFalse();
    }

    @Test
    void testRemoveFermataFromSelection() {
        var notes = List.of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance()
        );

        for (var note : notes) {
            note.setFermata(true);
        }

        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(FERMATA_ACTION, false);

        var line = getLine(coordinator);

        for (int i = 0; i <= 2; i++) {
            assertThat(line.getElement(i).isFermata())
                .as("note %d should not have fermata", i)
                .isFalse();
        }
    }

    // -- Selection remains active after mutation --

    @Test
    void testSelectionRemainsActiveAfterMutation() {
        var notes = List.of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        coordinator.applyActionToSelection(FERMATA_ACTION, true);

        assertThat(coordinator.getSelection()).isNotNull();
        assertThat(coordinator.getSelection().begin()).isEqualTo(0);
        assertThat(coordinator.getSelection().end()).isEqualTo(1);
    }

    // -- Tie interval validation --

    @Test
    void testTieDissolvedWhenContainsRest() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER_REST.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getTies().addInterval(new TieInterval(0, 1));

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getTies().isEmpty()).isTrue();
    }

    @Test
    void testTieSplitAroundRest() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER_REST.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getTies().addInterval(new TieInterval(0, 4));

        ReflectionTestHelper.selectRange(coordinator, 0, 4);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var tie0 = line.getTies().findInterval(0);
        assertThat(tie0).isNotNull();
        assertThat(tie0.start).isEqualTo(0);
        assertThat(tie0.end).isEqualTo(1);

        var tie3 = line.getTies().findInterval(3);
        assertThat(tie3).isNotNull();
        assertThat(tie3.start).isEqualTo(3);
        assertThat(tie3.end).isEqualTo(4);

        assertThat(line.getTies().findInterval(2)).isNull();
    }

    // -- Tuplet interval validation --

    @Test
    void testTupletDissolvedWhenContainsNonDuration() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.SINGLE_BARLINE.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getTuplets().addInterval(new TupletInterval(0, 2, 3));

        ReflectionTestHelper.selectRange(coordinator, 0, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getTuplets().isEmpty()).isTrue();
    }

    @Test
    void testTupletSplitAroundNonDuration() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.SINGLE_BARLINE.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getTuplets().addInterval(new TupletInterval(0, 4, 3));

        ReflectionTestHelper.selectRange(coordinator, 0, 4);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var tuplet0 = line.getTuplets().findInterval(0);
        assertThat(tuplet0).isNotNull();
        assertThat(tuplet0.start).isEqualTo(0);
        assertThat(tuplet0.end).isEqualTo(1);

        var tuplet3 = line.getTuplets().findInterval(3);
        assertThat(tuplet3).isNotNull();
        assertThat(tuplet3.start).isEqualTo(3);
        assertThat(tuplet3.end).isEqualTo(4);

        assertThat(line.getTuplets().findInterval(2)).isNull();
    }
}
