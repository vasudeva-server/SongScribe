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

import songscribe.UnitTest;
import songscribe.data.BeamInterval;
import songscribe.data.TieInterval;
import songscribe.data.TupletInterval;
import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.NoteTypeAction;
import songscribe.ui.action.UIAction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static songscribe.ui.action.NoteTypeAction.Kind;

class BatchMutationTest extends UnitTest {

    private static final FermataAction FERMATA_ACTION = new FermataAction();

    private static final AccidentalAction SHARP_ACTION =
        new AccidentalAction(Note.Accidental.SHARP, "Sharp", null, 0, "sharp", "Sharp");

    private static final NoteTypeAction QUARTER_ACTION = new NoteTypeAction(
        Kind.DURATION, NoteType.CROTCHET, "Quarter", null, 0, "quarter", "Quarter note", 0, 0
    );

    /**
     * Creates a coordinator with a composition mock on the line,
     * so applyActionToSelection can call line.getComposition().setModified().
     */
    private SelectionCoordinator createCoordinator(
            List<Note> notes,
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

    // -- Apply attribute (in-place mutation) --

    @Test
    void testApplyFermataToSelection() {
        var notes = List.of(
            NoteType.CROTCHET.newInstance(),
            NoteType.CROTCHET.newInstance(),
            NoteType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(FERMATA_ACTION, true);

        var line = getLine(coordinator);

        for (int i = 0; i <= 2; i++) {
            assertThat(line.getNote(i).isFermata())
                .as("note %d should have fermata", i)
                .isTrue();
        }
    }

    @Test
    void testRemoveFermataFromSelection() {
        var notes = List.of(
            NoteType.CROTCHET.newInstance(),
            NoteType.CROTCHET.newInstance(),
            NoteType.CROTCHET.newInstance()
        );

        for (var note : notes) {
            note.setFermata(true);
        }

        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(FERMATA_ACTION, false);

        var line = getLine(coordinator);

        for (int i = 0; i <= 2; i++) {
            assertThat(line.getNote(i).isFermata())
                .as("note %d should not have fermata", i)
                .isFalse();
        }
    }

    // -- Inapplicable notes are skipped --

    @Test
    void testAccidentalSkipsRests() {
        var notes = List.of(
            NoteType.CROTCHET.newInstance(),
            NoteType.CROTCHET_REST.newInstance(),
            NoteType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(SHARP_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(SHARP_ACTION, true);

        var line = getLine(coordinator);
        assertThat(line.getNote(0).getAccidental()).isEqualTo(Note.Accidental.SHARP);
        assertThat(line.getNote(1).getAccidental()).isEqualTo(Note.Accidental.NONE);
        assertThat(line.getNote(2).getAccidental()).isEqualTo(Note.Accidental.SHARP);
    }

    // -- Duration change replaces notes --

    @Test
    void testDurationChangeReplacesNotes() {
        var notes = List.of(
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var line = getLine(coordinator);
        assertThat(line.getNote(0).getNoteType()).isEqualTo(NoteType.CROTCHET);
        assertThat(line.getNote(1).getNoteType()).isEqualTo(NoteType.CROTCHET);
    }

    @Test
    void testDurationChangePreservesNoteRestKind() {
        var notes = List.of(
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER_REST.newInstance(),
            NoteType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var line = getLine(coordinator);
        assertThat(line.getNote(0).getNoteType()).isEqualTo(NoteType.CROTCHET);
        assertThat(line.getNote(1).getNoteType()).isEqualTo(NoteType.CROTCHET_REST);
        assertThat(line.getNote(2).getNoteType()).isEqualTo(NoteType.CROTCHET);
    }

    @Test
    void testDurationChangePreservesAttributes() {
        var note = NoteType.QUAVER.newInstance();
        note.setFermata(true);
        note.setAccidental(Note.Accidental.SHARP);

        var coordinator = createCoordinator(List.of(note), List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectNote(coordinator, 0);

        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var replaced = getLine(coordinator).getNote(0);
        assertThat(replaced.getNoteType()).isEqualTo(NoteType.CROTCHET);
        assertThat(replaced.isFermata()).isTrue();
        assertThat(replaced.getAccidental()).isEqualTo(Note.Accidental.SHARP);
    }

    // -- Duration un-apply is a no-op --

    @Test
    void testDurationUnApplyIsNoOp() {
        var notes = List.of(
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        coordinator.applyActionToSelection(QUARTER_ACTION, false);

        var line = getLine(coordinator);
        assertThat(line.getNote(0).getNoteType()).isEqualTo(NoteType.QUAVER);
        assertThat(line.getNote(1).getNoteType()).isEqualTo(NoteType.QUAVER);
    }

    // -- Composition is marked modified --

    @Test
    void testCompositionMarkedModified() {
        var notes = List.of(NoteType.CROTCHET.newInstance());
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectNote(coordinator, 0);

        coordinator.applyActionToSelection(FERMATA_ACTION, true);

        var composition = getLine(coordinator).getComposition();
        verify(composition).setModified(true);
    }

    // -- Selection remains active after mutation --

    @Test
    void testSelectionRemainsActiveAfterMutation() {
        var notes = List.of(
            NoteType.CROTCHET.newInstance(),
            NoteType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        coordinator.applyActionToSelection(FERMATA_ACTION, true);

        assertThat(coordinator.getSelection()).isNotNull();
        assertThat(coordinator.getSelection().begin()).isEqualTo(0);
        assertThat(coordinator.getSelection().end()).isEqualTo(1);
    }

    // -- No selection is a no-op --

    @Test
    void testNoSelectionIsNoOp() {
        var notes = List.of(NoteType.CROTCHET.newInstance());
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));

        // No selection set -- should not throw
        coordinator.applyActionToSelection(FERMATA_ACTION, true);

        assertThat(getLine(coordinator).getNote(0).isFermata()).isFalse();
    }

    // -- Duration change skips barlines --

    @Test
    void testDurationChangeSkipsBarlines() {
        var notes = List.of(
            NoteType.QUAVER.newInstance(),
            NoteType.SINGLE_BARLINE.newInstance(),
            NoteType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var line = getLine(coordinator);
        assertThat(line.getNote(0).getNoteType()).isEqualTo(NoteType.CROTCHET);
        assertThat(line.getNote(1).getNoteType()).isEqualTo(NoteType.SINGLE_BARLINE);
        assertThat(line.getNote(2).getNoteType()).isEqualTo(NoteType.CROTCHET);
    }

    // -- Beam interval validation --

    @Test
    void testBeamSplitAroundNonBeamable() {
        var notes = List.of(
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getBeamings().addInterval(new BeamInterval(0, 4));

        ReflectionTestHelper.selectNote(coordinator, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getNote(2).getNoteType()).isEqualTo(NoteType.CROTCHET);

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

    @Test
    void testBeamDissolvedWhenSubgroupTooSmall() {
        var notes = List.of(
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance()
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
    void testBeamDissolvedWhenAllNonBeamable() {
        var notes = List.of(
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getBeamings().addInterval(new BeamInterval(0, 2));

        ReflectionTestHelper.selectRange(coordinator, 0, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getBeamings().isEmpty()).isTrue();
    }

    @Test
    void testBeamShrunkFromStart() {
        var notes = List.of(
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance()
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
    void testBeamShrunkFromEnd() {
        var notes = List.of(
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance()
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

    // -- Tie interval validation --

    @Test
    void testTieDissolvedWhenContainsRest() {
        var notes = List.of(
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER_REST.newInstance()
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
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER_REST.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance()
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
            NoteType.QUAVER.newInstance(),
            NoteType.SINGLE_BARLINE.newInstance(),
            NoteType.QUAVER.newInstance()
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
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.SINGLE_BARLINE.newInstance(),
            NoteType.QUAVER.newInstance(),
            NoteType.QUAVER.newInstance()
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
