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
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
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
import songscribe.music.BeamSpan;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.music.TieSpan;
import songscribe.music.TupletSpan;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.UIAction;

/**
 * Mutation-emission tests for {@link SelectionCoordinator#applyActionToSelection}.
 * Verifies that the method emits the correct mutation records for both
 * {@link UIAction.ElementReplaceable} and {@link UIAction.ElementModifiable}
 * action paths, including downstream span-validation mutations (beam repair
 * and tuplet invalidation) and the absence of any tie mutations.
 */
class ApplyActionToSelectionMutationTest extends UnitTest {

    private static final ElementTypeAction QUARTER_ACTION =
        ElementTypeAction.createQuarterNoteAction();

    private static final AccidentalAction SHARP_ACTION =
        AccidentalAction.createSharpAction();

    private static final FermataAction FERMATA_ACTION = FermataAction.createAction();

    private static final DotAction DOT_ACTION = DotAction.createDotAction();

    private final List<Mutation> capturedMutations = new ArrayList<>();

    private SelectionCoordinator createCoordinator(
        List<? extends StaffElement> notes,
        List<UIAction.Reflectable> actions
    ) {
        return ReflectionTestHelper.createCoordinator(notes, actions, createSongMock());
    }

    private Song createSongMock() {
        var songMock = mock(Song.class);
        when(songMock.isModifying()).thenReturn(true);
        doAnswer(inv -> {
            Runnable runnable = inv.getArgument(0);
            runnable.run();
            return null;
        }).when(songMock).withModification(any());
        doAnswer(inv -> {
            Mutation mutation = inv.getArgument(0);
            Runnable mutator = inv.getArgument(1);
            capturedMutations.add(mutation);
            mutator.run();
            return null;
        }).when(songMock).applyChange(any(), any());
        return songMock;
    }

    private Line getLine(SelectionCoordinator coordinator) {
        return Objects.requireNonNull(coordinator.getActiveSelection()).getLine();
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

    // ---- ElementReplaceable path: duration change ----

    @Test
    void testElementReplaceableEmitsOneReplacementPerSelectedElement() {
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(QUARTER_ACTION, true);

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
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.SINGLE_BARLINE.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(QUARTER_ACTION, true);

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
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        var originalBeam = new BeamSpan(0, 3);
        line.getBeamings().addSpan(originalBeam);

        ReflectionTestHelper.selectNote(coordinator, 3);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var replacements = mutationsOfType(ElementReplacement.class);
        assertThat(replacements).hasSize(1);
        assertThat(replacements.getFirst().index()).isEqualTo(3);

        var beamRemovals = mutationsOfType(BeamingRemoval.class);
        assertThat(beamRemovals).hasSize(1);
        assertThat(beamRemovals.getFirst().span()).isSameAs(originalBeam);

        var beamAdditions = mutationsOfType(BeamingAddition.class);
        assertThat(beamAdditions).hasSize(1);
        assertThat(beamAdditions.getFirst().span().start).isEqualTo(0);
        assertThat(beamAdditions.getFirst().span().end).isEqualTo(2);

        assertNoTieMutations();
    }

    @Test
    void testElementReplaceableEmitsTupletRemovalOnOverlap() {
        // Five eighths with a tuplet over [0..4]; replacing any interior
        // element with a quarter removes the tuplet flatly (no split).
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        var tuplet = new TupletSpan(0, 4, 3);
        line.getTuplets().addSpan(tuplet);

        ReflectionTestHelper.selectNote(coordinator, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var replacements = mutationsOfType(ElementReplacement.class);
        assertThat(replacements).hasSize(1);
        assertThat(replacements.getFirst().index()).isEqualTo(2);

        var tupletRemovals = mutationsOfType(TupletRemoval.class);
        assertThat(tupletRemovals).hasSize(1);
        assertThat(tupletRemovals.getFirst().span()).isSameAs(tuplet);

        assertThat(mutationsOfType(TupletAddition.class))
            .as("tuplet invalidation never re-adds a split tuplet")
            .isEmpty();
        assertNoTieMutations();
    }

    @Test
    void testElementReplaceableNeverEmitsTieMutations() {
        // A tie over [0..1] with a duration change on both endpoints: the
        // dead tie-repair branch must stay dead — zero tie mutations.
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = getLine(coordinator);
        line.getTies().addSpan(new TieSpan(0, 1));

        ReflectionTestHelper.selectRange(coordinator, 0, 1);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(mutationsOfType(ElementReplacement.class)).hasSize(2);
        assertNoTieMutations();
    }

    // ---- ElementModifiable path: accidental / fermata / dot ----

    @Test
    void testElementModifiableEmitsOneModificationPerAffectedElement() {
        var notes = List.<StaffElement>of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(SHARP_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 2);

        coordinator.applyActionToSelection(SHARP_ACTION, true);

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
        var notes = List.<StaffElement>of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET_REST.newInstance(),
            ElementType.SINGLE_BARLINE.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(SHARP_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 3);

        coordinator.applyActionToSelection(SHARP_ACTION, true);

        var modifications = mutationsOfType(ElementModification.class);
        assertThat(modifications).hasSize(2);
        assertThat(modifications.get(0).index()).isEqualTo(0);
        assertThat(modifications.get(1).index()).isEqualTo(3);

        assertNoSpanMutations();
    }

    @Test
    void testElementModifiableFermataUsesFermataField() {
        var notes = List.<StaffElement>of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(FERMATA_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        coordinator.applyActionToSelection(FERMATA_ACTION, true);

        var modifications = mutationsOfType(ElementModification.class);
        assertThat(modifications).hasSize(2);

        for (var modification : modifications) {
            assertThat(modification.fields()).containsExactly(ElementField.FERMATA);
        }

        assertNoSpanMutations();
    }

    @Test
    void testElementModifiableDotUsesDotCountField() {
        var notes = List.<StaffElement>of(
            ElementType.CROTCHET.newInstance(),
            ElementType.CROTCHET.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(DOT_ACTION));
        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        coordinator.applyActionToSelection(DOT_ACTION, true);

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
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(SHARP_ACTION));
        var line = getLine(coordinator);
        line.getBeamings().addSpan(new BeamSpan(0, 2));
        line.getTuplets().addSpan(new TupletSpan(0, 2, 3));
        line.getTies().addSpan(new TieSpan(0, 1));

        ReflectionTestHelper.selectRange(coordinator, 0, 2);
        coordinator.applyActionToSelection(SHARP_ACTION, true);

        assertThat(mutationsOfType(ElementModification.class)).hasSize(3);
        assertNoSpanMutations();
    }
}
