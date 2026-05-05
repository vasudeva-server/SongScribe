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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.BeamSpan;
import songscribe.music.TieSpan;
import songscribe.music.TupletSpan;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;

class BatchMutationTest extends UnitTest {

    private static final FermataAction FERMATA_ACTION = FermataAction.createAction();

    private static final AccidentalAction SHARP_ACTION =
        AccidentalAction.createSharpAction();

    private static final ElementTypeAction QUARTER_ACTION =
        ElementTypeAction.createQuarterNoteAction();

    /**
     * Creates a coordinator with a song mock on the line. The mock is
     * stubbed so {@code withModification} runs its runnable and {@code applyChange}
     * runs its mutator; {@code isModifying} returns true so {@code Line.applyChange}
     * accepts mutations as if a real bracket were open.
     */
    private SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions
    ) {
        return ReflectionTestHelper.createCoordinator(notes, actions, createSongMock());
    }

    private static Song createSongMock() {
        var songMock = mock(Song.class);
        when(songMock.isModifying()).thenReturn(true);
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(0);
            runnable.run();
            return null;
        }).when(songMock).withModification(any());
        doAnswer(inv -> {
            Runnable mutator = inv.getArgument(1);
            mutator.run();
            return null;
        }).when(songMock).applyChange(any(), any());
        return songMock;
    }

    private Line getLine(SelectionCoordinator coordinator) {
        return Objects.requireNonNull(coordinator.getActiveSelection()).getLine();
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
        assertThat(line.getElement(1).getAccidental()).isNull();
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

        for (var i = 0; i <= 2; i++) {
            assertThat(line.getElement(i).isFermata())
                .as("note %d should have fermata", i)
                .isTrue();
        }
    }

    // -- Beam span validation --

    @Test
    void testBeamDissolvedWhenAllNonBeamable() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getBeamings().addSpan(new BeamSpan(0, 2));

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
        line.getBeamings().addSpan(new BeamSpan(0, 1));

        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getBeamings().findSpan(0)).isNull();
        assertThat(line.getBeamings().findSpan(1)).isNull();
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
        line.getBeamings().addSpan(new BeamSpan(0, 3));

        ReflectionTestHelper.selectNote(coordinator, 3);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var beam = Objects.requireNonNull(line.getBeamings().findSpan(0));
        assertThat(beam.start).isEqualTo(0);
        assertThat(beam.end).isEqualTo(2);
        assertThat(line.getBeamings().findSpan(3)).isNull();
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
        line.getBeamings().addSpan(new BeamSpan(0, 3));

        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var beam = Objects.requireNonNull(line.getBeamings().findSpan(1));
        assertThat(beam.start).isEqualTo(1);
        assertThat(beam.end).isEqualTo(3);
        assertThat(line.getBeamings().findSpan(0)).isNull();
    }

    @Test
    void testBeamKilledWhenInteriorElementBecomesNonBeamable() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getBeamings().addSpan(new BeamSpan(0, 4));

        ReflectionTestHelper.selectNote(coordinator, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.CROTCHET);
        assertThat(line.getBeamings().isEmpty()).isTrue();
    }

    // -- Song mutation bracket is opened --

    @Test
    void testSongBracketOpened() {
        var notes = List.of(ElementType.CROTCHET.newInstance());
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectNote(coordinator, 0);

        coordinator.applyActionToSelection(FERMATA_ACTION, true);

        var song = getLine(coordinator).getSong();
        verify(song).withModification(any());
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

        for (var i = 0; i <= 2; i++) {
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

        var selection = Objects.requireNonNull(coordinator.getSelection());
        assertThat(selection.begin()).isEqualTo(0);
        assertThat(selection.end()).isEqualTo(1);
    }

    // -- Tie span validation --
    //
    // Tie repair was deleted: under the invariants enforced by
    // applyActionToSelection (pitch and rest-ness preserved, grace notes
    // disabled in select mode, type-preserving modifiable actions), no
    // reachable replacement can invalidate an existing tie. Tests assert
    // that ties are left untouched even when their range overlaps a
    // duration-change selection.

    @Test
    void testTieUntouchedByDurationChange() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        var tie = new TieSpan(0, 1);
        line.getTies().addSpan(tie);

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var preserved = Objects.requireNonNull(line.getTies().findSpan(0));
        assertThat(preserved.start).isEqualTo(0);
        assertThat(preserved.end).isEqualTo(1);
    }

    // -- Tuplet span validation --
    //
    // Tuplets are flat-removed on overlap under the immutability policy:
    // any change other than pitch invalidates a tuplet. There is no
    // repair-by-splitting.

    @Test
    void testTupletDissolvedWhenContainsNonDuration() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.SINGLE_BARLINE.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getTuplets().addSpan(new TupletSpan(0, 2, 3));

        ReflectionTestHelper.selectRange(coordinator, 0, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getTuplets().isEmpty()).isTrue();
    }

    @Test
    void testOverlappingTupletFlatRemovedNoSubSpans() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getTuplets().addSpan(new TupletSpan(0, 4, 3));

        ReflectionTestHelper.selectNote(coordinator, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(line.getTuplets().isEmpty()).isTrue();
    }

    @Test
    void testNonOverlappingTupletPreserved() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getTuplets().addSpan(new TupletSpan(0, 1, 3));

        ReflectionTestHelper.selectRange(coordinator, 3, 4);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var preserved = Objects.requireNonNull(line.getTuplets().findSpan(0));
        assertThat(preserved.start).isEqualTo(0);
        assertThat(preserved.end).isEqualTo(1);
    }
}
