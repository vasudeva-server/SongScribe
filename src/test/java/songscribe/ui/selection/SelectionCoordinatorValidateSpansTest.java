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
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.model.BeamSpan;
import songscribe.model.Song;
import songscribe.model.ElementType;
import songscribe.model.StaffElement;
import songscribe.model.TupletSpan;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.UIAction;

/**
 * Coverage for the beam-repair and tuplet-invalidation behavior of
 * {@link SelectionCoordinator#applyActionToSelection}'s span validation
 * step. Each test exercises a single beam or tuplet scenario through a real
 * {@code ElementReplaceable} action and asserts on the exact mutation records
 * emitted into the open modification bracket.
 */
class SelectionCoordinatorValidateSpansTest extends UnitTest {

    private static final ElementTypeAction QUARTER_ACTION =
        ElementTypeAction.createQuarterNoteAction();

    private static final ElementTypeAction SIXTEENTH_ACTION =
        ElementTypeAction.createSixteenthNoteAction();

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

    private <T extends Mutation> List<T> mutationsOfType(Class<? extends T> type) {
        var list = new ArrayList<T>();

        for (var mutation : capturedMutations) {
            if (type.isInstance(mutation)) {
                list.add(type.cast(mutation));
            }
        }

        return list;
    }

    // ---- Beam repair ----

    @Test
    void testBeamUnchangedWhenAllElementsRemainBeamable() {
        // Three eighths with a beam over [0..2]; replacing the middle element
        // with a sixteenth keeps every element beamable, so the beam is
        // untouched.
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(SIXTEENTH_ACTION));
        var line = Objects.requireNonNull(coordinator.getActiveSelection()).getLine();
        line.getBeamings().addSpan(new BeamSpan(0, 2));

        ReflectionTestHelper.selectNote(coordinator, 1);
        coordinator.applyActionToSelection(SIXTEENTH_ACTION, true);

        assertThat(mutationsOfType(BeamingRemoval.class)).isEmpty();
        assertThat(mutationsOfType(BeamingAddition.class)).isEmpty();
    }

    @Test
    void testBeamTrimmedWhenStartElementBecomesNonBeamable() {
        // Three eighths with a beam over [0..2]; replacing the first element
        // with a quarter trims the beam from the left end, producing one
        // BeamingRemoval + one BeamingAddition for the truncated [1..2] span.
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = Objects.requireNonNull(coordinator.getActiveSelection()).getLine();
        var originalBeam = new BeamSpan(0, 2);
        line.getBeamings().addSpan(originalBeam);

        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var removals = mutationsOfType(BeamingRemoval.class);
        assertThat(removals).hasSize(1);
        assertThat(removals.getFirst().span()).isSameAs(originalBeam);

        var additions = mutationsOfType(BeamingAddition.class);
        assertThat(additions).hasSize(1);
        assertThat(additions.getFirst().span().start).isEqualTo(1);
        assertThat(additions.getFirst().span().end).isEqualTo(2);
    }

    @Test
    void testBeamTrimmedWhenEndElementBecomesNonBeamable() {
        // Three eighths with a beam over [0..2]; replacing the last element
        // with a quarter trims the beam from the right end, producing one
        // BeamingRemoval + one BeamingAddition for the truncated [0..1] span.
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = Objects.requireNonNull(coordinator.getActiveSelection()).getLine();
        var originalBeam = new BeamSpan(0, 2);
        line.getBeamings().addSpan(originalBeam);

        ReflectionTestHelper.selectNote(coordinator, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var removals = mutationsOfType(BeamingRemoval.class);
        assertThat(removals).hasSize(1);
        assertThat(removals.getFirst().span()).isSameAs(originalBeam);

        var additions = mutationsOfType(BeamingAddition.class);
        assertThat(additions).hasSize(1);
        assertThat(additions.getFirst().span().start).isEqualTo(0);
        assertThat(additions.getFirst().span().end).isEqualTo(1);
    }

    @Test
    void testBeamKilledWhenInteriorElementBecomesNonBeamable() {
        // Five eighths with a beam over [0..4]; replacing the middle element
        // with a quarter punctures the interior — the beam is killed outright,
        // not split, per the Phase 4 trim-and-kill rule.
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = Objects.requireNonNull(coordinator.getActiveSelection()).getLine();
        var originalBeam = new BeamSpan(0, 4);
        line.getBeamings().addSpan(originalBeam);

        ReflectionTestHelper.selectNote(coordinator, 2);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var removals = mutationsOfType(BeamingRemoval.class);
        assertThat(removals).hasSize(1);
        assertThat(removals.getFirst().span()).isSameAs(originalBeam);

        assertThat(mutationsOfType(BeamingAddition.class))
            .as("interior puncture kills the beam without a replacement")
            .isEmpty();
    }

    @Test
    void testBeamKilledWhenTrimLeavesFewerThanTwoElements() {
        // Two eighths with a beam over [0..1]; replacing one element with a
        // quarter leaves a single beamable element, which cannot form a beam
        // on its own — the beam is killed with no addition.
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = Objects.requireNonNull(coordinator.getActiveSelection()).getLine();
        var originalBeam = new BeamSpan(0, 1);
        line.getBeamings().addSpan(originalBeam);

        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var removals = mutationsOfType(BeamingRemoval.class);
        assertThat(removals).hasSize(1);
        assertThat(removals.getFirst().span()).isSameAs(originalBeam);

        assertThat(mutationsOfType(BeamingAddition.class))
            .as("single remaining beamable element cannot form a beam")
            .isEmpty();
        assertThat(mutationsOfType(TupletAddition.class)).isEmpty();
        assertThat(mutationsOfType(TupletRemoval.class)).isEmpty();
    }

    // ---- Tuplet invalidation ----

    @Test
    void testOverlappingTupletsRemovedOnReplacement() {
        // Five eighths with two disjoint tuplets: [0..1] and [3..4]. Selecting
        // the full range [0..4] overlaps both, so both trigger a TupletRemoval
        // when the replaceable action is applied.
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = Objects.requireNonNull(coordinator.getActiveSelection()).getLine();
        var firstTuplet = new TupletSpan(0, 1, 3);
        var secondTuplet = new TupletSpan(3, 4, 3);
        line.getTuplets().addSpan(firstTuplet);
        line.getTuplets().addSpan(secondTuplet);

        ReflectionTestHelper.selectRange(coordinator, 0, 4);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        var removals = mutationsOfType(TupletRemoval.class);
        assertThat(removals).hasSize(2);
        var removedSpans = removals.stream().map(TupletRemoval::span).toList();
        assertThat(removedSpans).containsExactlyInAnyOrder(firstTuplet, secondTuplet);
    }

    @Test
    void testNonOverlappingTupletsUntouched() {
        // Five eighths with a tuplet at [3..4] — entirely outside the selected
        // index 0. The replacement emits no TupletRemoval records.
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(QUARTER_ACTION));
        var line = Objects.requireNonNull(coordinator.getActiveSelection()).getLine();
        line.getTuplets().addSpan(new TupletSpan(3, 4, 3));

        ReflectionTestHelper.selectNote(coordinator, 0);
        coordinator.applyActionToSelection(QUARTER_ACTION, true);

        assertThat(mutationsOfType(TupletRemoval.class)).isEmpty();
    }

    @Test
    void testDotCountChangeRemovesTuplet() {
        // Three eighths with a tuplet spanning [0..2]. Applying a dot to the
        // middle element changes its duration, which invalidates the tuplet.
        // Line.modifyElement detects DOT_COUNT in the modified fields and emits
        // a TupletRemoval directly — independent of validateSpans.
        var dotAction = DotAction.createDotAction();
        var notes = List.<StaffElement>of(
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance(),
            ElementType.QUAVER.newInstance()
        );
        var coordinator = createCoordinator(notes, List.of(dotAction));
        var line = Objects.requireNonNull(coordinator.getActiveSelection()).getLine();
        var tuplet = new TupletSpan(0, 2, 3);
        line.getTuplets().addSpan(tuplet);

        ReflectionTestHelper.selectNote(coordinator, 1);
        coordinator.applyActionToSelection(dotAction, true);

        var removals = mutationsOfType(TupletRemoval.class);
        assertThat(removals).hasSize(1);
        assertThat(removals.getFirst().span()).isSameAs(tuplet);
    }
}
