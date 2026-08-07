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
import static org.mockito.AdditionalAnswers.answerVoid;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.message.mutation.Mutation;
import songscribe.dom.Beam;
import songscribe.dom.Tie;
import songscribe.dom.Tuplet;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;
import songscribe.dom.FermataAttachment;

class BatchMutationTest extends MainFrameMockTest {

    private FermataAction FERMATA_ACTION;
    private AccidentalAction SHARP_ACTION;
    private ElementTypeAction QUARTER_ACTION;

    @BeforeEach
    void setUp() {
        var mainFrame = mainFrame();
        FERMATA_ACTION = FermataAction.createAction(mainFrame);
        SHARP_ACTION = AccidentalAction.createSharpAction(mainFrame);
        QUARTER_ACTION = ElementTypeAction.createQuarterNoteAction(mainFrame);
    }

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

    @SuppressWarnings("ReturnOfNull")
    private static Song createSongMock() {
        var songMock = mock(Song.class);
        when(songMock.isModifying()).thenReturn(true);
        when(songMock.getLineWidthSs()).thenReturn(UNCONSTRAINED_LINE_WIDTH_SS);
        doAnswer(answerVoid(Runnable::run)).when(songMock).withModification(any());
        doAnswer(answerVoid((Mutation mutation, Runnable mutator) -> mutator.run())).when(songMock).applyChange(any(), any());
        return songMock;
    }

    private Line getLine(SelectionCoordinator coordinator) {
        var selection = coordinator.getActiveLine();
        assertThat(selection).isNotNull();

        return selection;
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

        SelectionActionApplier.apply(coordinator, SHARP_ACTION, true, null);

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

        SelectionActionApplier.apply(coordinator, FERMATA_ACTION, true, null);

        var line = getLine(coordinator);

        for (var i = 0; i <= 2; i++) {
            assertThat(line.getElement(i).findAttachment(FermataAttachment.class))
                .as("note %d should have fermata", i)
                .isNotNull();
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
        line.addBeaming(new Beam(line.getElement(0), line.getElement(2)));

        ReflectionTestHelper.selectRange(coordinator, 0, 2);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        assertThat(line.findSpans(Beam.class).isEmpty()).isTrue();
    }

    @Test
    void testBeamDissolvedWhenSubgroupTooSmall() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.addBeaming(new Beam(line.getElement(0), line.getElement(1)));

        ReflectionTestHelper.selectNote(coordinator, 0);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        assertThat(line.findBeamAt(0)).isNull();
        assertThat(line.findBeamAt(1)).isNull();
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
        line.addBeaming(new Beam(line.getElement(0), line.getElement(3)));

        ReflectionTestHelper.selectNote(coordinator, 3);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        var beam = line.findBeamAt(0);
        assertThat(beam).isNotNull();
        assertThat(beam.getAnchorElementIndex()).isEqualTo(0);
        assertThat(beam.getEndElementIndex()).isEqualTo(2);
        assertThat(line.findBeamAt(3)).isNull();
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
        line.addBeaming(new Beam(line.getElement(0), line.getElement(3)));

        ReflectionTestHelper.selectNote(coordinator, 0);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        var beam = line.findBeamAt(1);
        assertThat(beam).isNotNull();
        assertThat(beam.getAnchorElementIndex()).isEqualTo(1);
        assertThat(beam.getEndElementIndex()).isEqualTo(3);
        assertThat(line.findBeamAt(0)).isNull();
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
        line.addBeaming(new Beam(line.getElement(0), line.getElement(4)));

        ReflectionTestHelper.selectNote(coordinator, 2);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.CROTCHET);
        assertThat(line.findSpans(Beam.class).isEmpty()).isTrue();
    }

    // -- Song mutation bracket is opened --

    @Test
    void testSongBracketOpened() {
        var notes = List.of(ElementType.CROTCHET.newInstance());
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectNote(coordinator, 0);

        SelectionActionApplier.apply(coordinator, FERMATA_ACTION, true, null);

        var song = getLine(coordinator).getSong();
        verify(song).withModification(any());
    }

    // -- Duration change replaces notes --

    @Test
    void testDurationChangePreservesAttributes() {
        var note = ElementType.QUAVER.newInstance();
        note.addAttachment(new FermataAttachment(note));
        note.setAccidental(StaffElement.Accidental.SHARP);

        var coordinator = createCoordinator(List.of(note), List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectNote(coordinator, 0);

        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        var replaced = getLine(coordinator).getElement(0);
        assertThat(replaced.getType()).isEqualTo(ElementType.CROTCHET);
        assertThat(replaced.findAttachment(FermataAttachment.class)).isNotNull();
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

        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

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

        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

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

        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

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

        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, false, null);

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
        SelectionActionApplier.apply(coordinator, FERMATA_ACTION, true, null);

        assertThat(getLine(coordinator).getElement(0).findAttachment(FermataAttachment.class)).isNull();
    }

    // -- row 55: no modification bracket opened when no selection is active --

    @Test
    void testNoSelectionDoesNotOpenModificationBracket() {
        // applyActionToSelection returns immediately when getSelection() == null,
        // so withModification must never be called at all.
        var notes = List.of(ElementType.CROTCHET.newInstance());
        var songMock = createSongMock();
        var coordinator = ReflectionTestHelper.createCoordinator(notes, List.of(FERMATA_ACTION), songMock);

        // No selection — must not invoke the modification bracket
        SelectionActionApplier.apply(coordinator, FERMATA_ACTION, true, null);

        verify(songMock, never()).withModification(any());
    }

    // -- row 58: ElementReplaceable with selected=false skips all elements (continue guard) --

    @Test
    void testElementReplaceableWithSelectedFalseSkipsAllElements() {
        // The ElementReplaceable branch has an explicit `if (!selected) { continue; }` guard.
        // Applying QUARTER_ACTION (ElementReplaceable) with selected=false must leave every
        // element unchanged — contrast with the ElementModifiable path that DOES process false.
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, false, null);

        for (var i = 0; i <= 2; i++) {
            assertThat(line.getElement(i).getType())
                .as("element %d type must be unchanged (QUAVER)", i)
                .isEqualTo(ElementType.QUAVER);
        }
    }

    @Test
    void testRemoveFermataFromSelection() {
        var notes = List.of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance()
        );

        for (var note : notes) {
            note.addAttachment(new FermataAttachment(note));
        }

        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        SelectionActionApplier.apply(coordinator, FERMATA_ACTION, false, null);

        var line = getLine(coordinator);

        //noinspection ConstantValue
        for (var i = 0; i <= 2; i++) {
            assertThat(line.getElement(i).findAttachment(FermataAttachment.class))
                .as("note %d should not have fermata", i)
                .isNull();
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

        SelectionActionApplier.apply(coordinator, FERMATA_ACTION, true, null);

        var selection = coordinator.getSelection();
        assertThat(selection).isNotNull();
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
        var tie = new Tie(line.getElement(0), line.getElement(1));
        line.addSpan(tie);

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        var preserved = line.findTieAt(0);
        assertThat(preserved).isNotNull();
        assertThat(preserved.getAnchorElementIndex()).isEqualTo(0);
        assertThat(preserved.getEndElementIndex()).isEqualTo(1);
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
        line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(2), 3));

        ReflectionTestHelper.selectRange(coordinator, 0, 2);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        assertThat(line.findSpans(Tuplet.class)).isEmpty();
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
        line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(4), 3));

        ReflectionTestHelper.selectNote(coordinator, 2);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        assertThat(line.findSpans(Tuplet.class)).isEmpty();
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
        line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(1), 3));

        ReflectionTestHelper.selectRange(coordinator, 3, 4);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        var preserved = line.findTupletAt(0);
        assertThat(preserved).isNotNull();
        assertThat(preserved.getAnchorElementIndex()).isEqualTo(0);
        assertThat(preserved.getEndElementIndex()).isEqualTo(1);
    }
}
