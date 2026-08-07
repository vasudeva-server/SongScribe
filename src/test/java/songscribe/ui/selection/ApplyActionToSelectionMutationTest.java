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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.BeamingRemoval;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.layout.NoteGeometry;
import songscribe.dom.Beam;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.Tuplet;
import songscribe.dom.Tie;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;
import songscribe.ui.OptionDialogs;

/**
 * Mutation-emission tests for {@link SelectionActionApplier#apply}.
 * Verifies that the method emits the correct mutation records for both
 * {@link UIAction.ElementReplaceable} and {@link UIAction.ElementModifiable}
 * action paths, including downstream span-validation mutations (beam repair
 * and tuplet invalidation) and the absence of any tie mutations.
 */
class ApplyActionToSelectionMutationTest extends MainFrameMockTest {

    private ElementTypeAction QUARTER_ACTION;
    private AccidentalAction SHARP_ACTION;
    private FermataAction FERMATA_ACTION;
    private DotAction DOT_ACTION;

    // Applying a width-changing action measures the projected line, which reads accidental widths
    // out of a static table that has to be built first. Without this the class passes only when
    // some earlier test class happens to have built it, and fails whenever it runs alone.
    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    @BeforeEach
    void setUp() {
        var mainFrame = mainFrame();
        QUARTER_ACTION = ElementTypeAction.createQuarterNoteAction(mainFrame);
        SHARP_ACTION = AccidentalAction.createSharpAction(mainFrame);
        FERMATA_ACTION = FermataAction.createAction(mainFrame);
        DOT_ACTION = DotAction.createDotAction(mainFrame);
    }

    private final List<Mutation> capturedMutations = new ArrayList<>();

    private SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions
    ) {
        return ReflectionTestHelper.createCoordinator(notes, actions, createSongMock());
    }

    @SuppressWarnings("ReturnOfNull")
    private Song createSongMock() {
        var songMock = mock(Song.class);
        when(songMock.isModifying()).thenReturn(true);
        when(songMock.getLineWidthSs()).thenReturn(UNCONSTRAINED_LINE_WIDTH_SS);
        doAnswer(answerVoid(Runnable::run)).when(songMock).withModification(any());
        doAnswer(answerVoid((Mutation mutation, Runnable mutator) -> {
            capturedMutations.add(mutation);
            mutator.run();
        })).when(songMock).applyChange(any(), any());
        return songMock;
    }

    private Line getLine(SelectionCoordinator coordinator) {
        var selection = coordinator.getActiveLine();
        assertThat(selection).isNotNull();

        return selection;
    }

    private <T extends Mutation> List<T> mutationsOfType(Class<? extends T> type) {
        var list = new ArrayList<T>();

        for (var mutation : capturedMutations) {
            if (type.isInstance(mutation)) {
                list.add(type.cast(mutation));
            }
        }

        return list;
    }

    private void assertNoTieMutations() {
        assertThat(mutationsOfType(TieAddition.class))
            .as("no TieAddition mutations should be emitted")
            .isEmpty();
        assertThat(mutationsOfType(TieRemoval.class))
            .as("no TieRemoval mutations should be emitted")
            .isEmpty();
    }

    private void assertNoSpanMutations() {
        assertThat(mutationsOfType(BeamingAddition.class))
            .as("no BeamingAddition mutations should be emitted")
            .isEmpty();
        assertThat(mutationsOfType(BeamingRemoval.class))
            .as("no BeamingRemoval mutations should be emitted")
            .isEmpty();
        assertThat(mutationsOfType(TupletAddition.class))
            .as("no TupletAddition mutations should be emitted")
            .isEmpty();
        assertThat(mutationsOfType(TupletRemoval.class))
            .as("no TupletRemoval mutations should be emitted")
            .isEmpty();
        assertNoTieMutations();
    }

    // ---- Restatement prompt on an accidental toggle (#681) ----

    private static final int F_STAFF_POSITION = 3;

    private static StaffElement sharpNote() {
        var note = ElementType.CROTCHET.newInstance();
        note.setStaffPosition(F_STAFF_POSITION);
        note.setAccidental(StaffElement.Accidental.SHARP);
        return note;
    }

    /**
     * Two sharps at the same staff position, with only the first one selected. Toggling that first
     * sharp off takes an accidental away, so the second one is offered as a restatement of it.
     */
    private SelectionCoordinator coordinatorWithARestatement() {
        var songMock = createSongMock();
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(sharpNote(), sharpNote()), List.of(SHARP_ACTION), songMock);
        var line = getLine(coordinator);

        // Teach the song mock the line list the forward scan walks.
        when(songMock.lineCount()).thenReturn(1);
        when(songMock.getLine(0)).thenReturn(line);
        when(songMock.indexOfLine(line)).thenReturn(0);

        ReflectionTestHelper.selectRange(coordinator, 0, 0);

        // Building the line emitted insertions of its own; only what the toggle does matters here.
        capturedMutations.clear();
        return coordinator;
    }

    private void toggleTheFirstSharpOffAnswering(SelectionCoordinator coordinator, int answer) {
        try (var optionDialogs = mockStatic(OptionDialogs.class)) {
            optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
                any(), any(), any(), anyInt(), anyInt())).thenReturn(answer);

            SelectionActionApplier.apply(coordinator, SHARP_ACTION, false, null);
        }
    }

    @Test
    void testAccidentalToggleOffCancelledLeavesTheLineUntouched() {
        var coordinator = coordinatorWithARestatement();
        var line = getLine(coordinator);

        toggleTheFirstSharpOffAnswering(coordinator, JOptionPane.CANCEL_OPTION);

        assertThat(line.getElement(0).getAccidental())
            .as("the toggle itself was abandoned, not just the restatement removal")
            .isEqualTo(StaffElement.Accidental.SHARP);
        assertThat(line.getElement(1).getAccidental()).isEqualTo(StaffElement.Accidental.SHARP);
        assertThat(capturedMutations).as("no undo step was created").isEmpty();
    }

    @Test
    void testAccidentalToggleOffAcceptedClearsTheRestatementToo() {
        var coordinator = coordinatorWithARestatement();
        var line = getLine(coordinator);

        toggleTheFirstSharpOffAnswering(coordinator, JOptionPane.YES_OPTION);

        assertThat(line.getElement(0).getAccidental()).isNull();
        assertThat(line.getElement(1).getAccidental())
            .as("the accepted restatement went with it")
            .isNull();
    }

    @Test
    void testAccidentalToggleOffDeclinedLeavesTheRestatementInPlace() {
        var coordinator = coordinatorWithARestatement();
        var line = getLine(coordinator);

        toggleTheFirstSharpOffAnswering(coordinator, JOptionPane.NO_OPTION);

        assertThat(line.getElement(0).getAccidental()).isNull();
        assertThat(line.getElement(1).getAccidental())
            .as("declining leaves every restatement alone")
            .isEqualTo(StaffElement.Accidental.SHARP);
    }

    // ---- ElementReplaceable path: duration change ----

    @Test
    void testElementReplaceableEmitsOneReplacementPerSelectedElement() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        var replacements = mutationsOfType(ElementReplacement.class);
        assertThat(replacements).hasSize(3);

        var line = getLine(coordinator);

        for (var i = 0; i <= 2; i++) {
            var replacement = replacements.get(i);
            assertThat(replacement.line()).isSameAs(line);
            assertThat(replacement.index()).isEqualTo(i);
            assertThat(replacement.oldElement().getType()).isEqualTo(ElementType.QUAVER);
            assertThat(replacement.newElement().getType()).isEqualTo(ElementType.CROTCHET);
        }

        assertNoSpanMutations();
    }

    @Test
    void testElementReplaceableSkipsInapplicableElements() {
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.SINGLE_BARLINE.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        var replacements = mutationsOfType(ElementReplacement.class);
        assertThat(replacements).hasSize(2);
        assertThat(replacements.get(0).index()).isEqualTo(0);
        assertThat(replacements.get(1).index()).isEqualTo(2);

        assertNoSpanMutations();
    }

    @Test
    void testElementReplaceableEmitsBeamMutationsOnBeamRepair() {
        // Four eighths with a beam over [0..3]; replacing the tail eighth with
        // a quarter trims the beam from the right end, producing one
        // BeamingRemoval + one BeamingAddition for the truncated [0..2] span.
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.addBeaming(new Beam(line.getElement(0), line.getElement(3)));
        capturedMutations.clear();

        ReflectionTestHelper.selectNote(coordinator, 3);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        var replacements = mutationsOfType(ElementReplacement.class);
        assertThat(replacements).hasSize(1);
        assertThat(replacements.getFirst().index()).isEqualTo(3);

        var beamRemovals = mutationsOfType(BeamingRemoval.class);
        assertThat(beamRemovals).hasSize(1);
        assertThat(beamRemovals.getFirst().beam()).isNotNull();

        var beamAdditions = mutationsOfType(BeamingAddition.class);
        assertThat(beamAdditions).hasSize(1);
        assertThat(beamAdditions.getFirst().beam().getAnchorElementIndex()).isEqualTo(0);
        assertThat(beamAdditions.getFirst().beam().getEndElementIndex()).isEqualTo(2);

        assertNoTieMutations();
    }

    @Test
    void testElementReplaceableEmitsTupletRemovalOnOverlap() {
        // Five eighths with a tuplet over [0..4]; replacing any interior
        // element with a quarter removes the tuplet flatly (no split).
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        var tuplet = Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(4), 3);
        line.addTuplet(tuplet);
        capturedMutations.clear();

        ReflectionTestHelper.selectNote(coordinator, 2);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        var replacements = mutationsOfType(ElementReplacement.class);
        assertThat(replacements).hasSize(1);
        assertThat(replacements.getFirst().index()).isEqualTo(2);

        var tupletRemovals = mutationsOfType(TupletRemoval.class);
        assertThat(tupletRemovals).hasSize(1);
        assertThat(tupletRemovals.getFirst().tuplet()).isSameAs(tuplet);

        assertThat(mutationsOfType(TupletAddition.class))
            .as("tuplet invalidation never re-adds a split tuplet")
            .isEmpty();
        assertNoTieMutations();
    }

    @Test
    void testElementReplaceableNeverEmitsTieMutations() {
        // A tie over [0..1] with a duration change on both endpoints: the
        // dead tie-repair branch must stay dead — zero tie mutations.
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getSong().withoutMutationTracking(
            () -> line.addSpan(new Tie(line.getElement(0), line.getElement(1))));

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        SelectionActionApplier.apply(coordinator, QUARTER_ACTION, true, null);

        assertThat(mutationsOfType(ElementReplacement.class)).hasSize(2);
        assertNoTieMutations();
    }

    // ---- ElementModifiable path: accidental / fermata / dot ----

    @Test
    void testElementModifiableEmitsOneModificationPerAffectedElement() {
        var notes = List.of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(SHARP_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        SelectionActionApplier.apply(coordinator, SHARP_ACTION, true, null);

        var modifications = mutationsOfType(ElementModification.class);
        assertThat(modifications).hasSize(3);

        var line = getLine(coordinator);

        for (var i = 0; i <= 2; i++) {
            var modification = modifications.get(i);
            assertThat(modification.line()).isSameAs(line);
            assertThat(modification.index()).isEqualTo(i);
            assertThat(modification.fields()).containsExactly(ElementField.ACCIDENTAL);
            assertThat(modification.beforeElement().getAccidental()).isNull();
        }

        assertNoSpanMutations();
    }

    @Test
    void testElementModifiableSkipsInapplicableElements() {
        // Accidentals only apply to notes — rests and barlines are skipped.
        var notes = List.of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET_REST.newInstance(),
            ElementType.SINGLE_BARLINE.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(SHARP_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 3);

        SelectionActionApplier.apply(coordinator, SHARP_ACTION, true, null);

        var modifications = mutationsOfType(ElementModification.class);
        assertThat(modifications).hasSize(2);
        assertThat(modifications.get(0).index()).isEqualTo(0);
        assertThat(modifications.get(1).index()).isEqualTo(3);

        assertNoSpanMutations();
    }

    @Test
    void testElementModifiableFermataUsesFermataField() {
        var notes = List.of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        SelectionActionApplier.apply(coordinator, FERMATA_ACTION, true, null);

        var modifications = mutationsOfType(ElementModification.class);
        assertThat(modifications).hasSize(2);

        for (var modification : modifications) {
            assertThat(modification.fields()).containsExactly(ElementField.FERMATA);
        }

        assertNoSpanMutations();
    }

    @Test
    void testElementModifiableDotUsesDotCountField() {
        var notes = List.of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(DOT_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        SelectionActionApplier.apply(coordinator, DOT_ACTION, true, null);

        var modifications = mutationsOfType(ElementModification.class);
        assertThat(modifications).hasSize(2);

        for (var modification : modifications) {
            assertThat(modification.fields()).containsExactly(ElementField.DOT_COUNT);
        }

        assertNoSpanMutations();
    }

    @Test
    void testElementModifiableBypassesSpanValidation() {
        // Even with a beam and tuplet spanning the selection, an
        // ElementModifiable action never triggers beam repair or tuplet
        // invalidation — it doesn't touch element type.
        var notes = List.of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(SHARP_ACTION));
        var line = getLine(coordinator);
        line.addBeaming(new Beam(line.getElement(0), line.getElement(2)));
        line.addTuplet(Tuplet.withUnresolvedRatio(line.getElement(0), line.getElement(2), 3));
        capturedMutations.clear();
        line.getSong().withoutMutationTracking(
            () -> line.addSpan(new Tie(line.getElement(0), line.getElement(1))));

        ReflectionTestHelper.selectRange(coordinator, 0, 2);
        SelectionActionApplier.apply(coordinator, SHARP_ACTION, true, null);

        assertThat(mutationsOfType(ElementModification.class)).hasSize(3);
        assertNoSpanMutations();
    }
}
