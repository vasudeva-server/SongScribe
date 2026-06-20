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

import java.awt.event.ActionEvent;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.action.AccidentalAction;
import songscribe.ui.action.DotAction;
import songscribe.ui.action.FermataAction;
import songscribe.ui.action.StickyUIAction;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;

class SelectionContentTest extends MainFrameMockTest {

    // -- isApplicableToSelection --

    @Test
    void testActionNotApplicableToBarlines() {
        // AccidentalAction does not apply to barlines
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.SINGLE_BARLINE.newInstance()),
            List.of(action)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.isApplicableToSelection(action)).isFalse();
    }

    @Test
    void testApplicableActionWithApplicableNotesReturnsTrue() {
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of(action)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.isApplicableToSelection(action)).isTrue();
    }

    @Test
    void testApplicableActionWithMixedNotesReturnsTrue() {
        // AccidentalAction applies to the note but not the rest
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET_REST.newInstance()),
            List.of(action)
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(coordinator.isApplicableToSelection(action)).isTrue();
    }

    @Test
    void testApplicableActionWithNoApplicableNotesReturnsFalse() {
        // AccidentalAction applies only to notes, not rests
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET_REST.newInstance()),
            List.of(action)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.isApplicableToSelection(action)).isFalse();
    }

    @Test
    void testDotActionAppliesToDurations() {
        // DotAction applies to both notes and rests (durations)
        var action = DotAction.createDotAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET_REST.newInstance()),
            List.of(action)
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.isApplicableToSelection(action)).isTrue();
    }

    @Test
    void testNoSelectionIsNotApplicable() {
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of(action)
        );

        assertThat(coordinator.isApplicableToSelection(action)).isFalse();
    }

    // -- hasActiveSelection --

    @Test
    void testNoSelectionReturnsNoActiveSelection() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );

        assertThat(coordinator.hasActiveSelection()).isFalse();
    }

    // -- selectionHasDurations --

    @Test
    void testNoSelectionHasDurationsReturnsFalse() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );

        assertThat(coordinator.selectionHasDurations()).isFalse();
    }

    @Test
    void testSelectionWithMixedContentHasDurations() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance(), ElementType.SINGLE_BARLINE.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(coordinator.selectionHasDurations()).isTrue();
    }

    @Test
    void testSelectionWithOnlyDurationsHasDurations() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance(), ElementType.QUAVER_REST.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(coordinator.selectionHasDurations()).isTrue();
    }

    @Test
    void testSelectionWithOnlyNonDurationsHasNoDurations() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.SINGLE_BARLINE.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.selectionHasDurations()).isFalse();
    }

    @Test
    void testWithSelectionReturnsActiveSelection() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        assertThat(coordinator.hasActiveSelection()).isTrue();
    }

    // -- selectionHasRests --

    @Test
    void testNoSelectionHasRestsReturnsFalse() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET_REST.newInstance()),
            List.of()
        );

        assertThat(coordinator.selectionHasRests()).isFalse();
    }

    @Test
    void testSelectionContainingRestHasRests() {
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance(), ElementType.CROTCHET_REST.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(coordinator.selectionHasRests()).isTrue();
    }

    // -- row 49: selectionHasRests false for note-only selection --

    @Test
    void testNoteOnlySelectionHasNoRests() {
        // AccidentalAction does not matter here; the key is that only notes are selected
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance(), ElementType.QUAVER.newInstance()),
            List.of()
        );

        ReflectionTestHelper.selectRange(coordinator, 0, 1);

        assertThat(coordinator.selectionHasRests()).isFalse();
    }

    // -- row 53: applicability cache is invalidated when the selection changes --

    @Test
    void testApplicabilityCacheInvalidatedOnSelectionChange() {
        // AccidentalAction applies to notes but NOT to barlines.
        // Range A (index 0) = barline → isApplicableToSelection must be false (cached).
        // Range B (index 1) = note  → after selection change, cache must be invalidated
        // so isApplicableToSelection returns true (not the stale false from range A).
        var action = AccidentalAction.createSharpAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.SINGLE_BARLINE.newInstance(), ElementType.CROTCHET.newInstance()),
            List.of(action)
        );

        // Range A: barline only — populate the applicability cache with false
        ReflectionTestHelper.selectNote(coordinator, 0);
        assertThat(coordinator.isApplicableToSelection(action))
            .as("barline-only selection: applicability must be false")
            .isFalse();

        // Range B: note only — cache must be invalidated; result must reflect the new range
        ReflectionTestHelper.selectNote(coordinator, 1);
        assertThat(coordinator.isApplicableToSelection(action))
            .as("note-only selection after selection change: applicability must be true (not stale false)")
            .isTrue();
    }

    // -- row 54: content cache is invalidated after applyActionToSelection --

    @Test
    void testContentCacheInvalidatedAfterApply() {
        // NoteToRestAction replaces every note with CROTCHET_REST so that
        // selectionHasRests changes from false (before apply) to true (after apply).
        // A buggy implementation that skips contentCacheSelection = null in
        // applyActionToSelection would return the stale false instead of recomputed true.
        var action = new NoteToRestAction(mainFrame());
        var coordinator = ReflectionTestHelper.createCoordinator(
            List.of(ElementType.CROTCHET.newInstance()),
            List.of(action),
            createSongMockForApply()
        );

        ReflectionTestHelper.selectNote(coordinator, 0);

        // Prime the content cache: hasRests = false (only a note selected)
        assertThat(coordinator.selectionHasRests())
            .as("note-only selection before apply: selectionHasRests must be false")
            .isFalse();

        // Apply the action: the note is replaced with a rest and the cache is cleared
        coordinator.applyActionToSelection(action, true, null);

        // Cache must have been invalidated: recomputed result must be true
        assertThat(coordinator.selectionHasRests())
            .as("rest-only selection after apply: selectionHasRests must be true (cache invalidated)")
            .isTrue();
    }

    @SuppressWarnings("ReturnOfNull")
    private static Song createSongMockForApply() {
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

    /**
     * Test-only ElementReplaceable that replaces any duration element with a
     * CROTCHET_REST. Used to verify content-cache invalidation in row 54.
     */
    private static final class NoteToRestAction extends StickyUIAction
        implements UIAction.ElementReplaceable {

        NoteToRestAction(MainFrame mainFrame) {
            super(mainFrame, "NoteToRest", null, 0, "note-to-rest", "", 0, 0);
        }

        @Override
        public boolean appliesTo(StaffElement element) {
            return element.getType().isDuration();
        }

        @Override
        public boolean matchesElement(StaffElement element) {
            return element.getType().isRest();
        }

        @Override
        public StaffElement createReplacement(StaffElement element, boolean selected) {
            return new StaffElement(ElementType.CROTCHET_REST, element);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // Not used in tests
        }
    }
}
