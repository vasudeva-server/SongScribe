/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.action;

import module java.desktop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Trill;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.RangeElementAddition;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link TrillAction}: checked-state overlap detection, G1
 * enablement (reusing {@link LineSelectionState#canToggleTrill()}), and the
 * check/uncheck {@code actionPerformed} paths (spanning-trill addition,
 * outermost-pitched-note clamping, and multi-range removal batched into a
 * single {@link SongDidChangeNotification}).
 */
class TrillActionTest extends UnitTest {

    private MainFrame mockFrame;
    private ScoreView mockScore;
    private SelectionCoordinator mockCoordinator;
    private TrillAction action;

    @BeforeEach
    void setUp() {
        mockFrame = mock(MainFrame.class);
        mockScore = mock(ScoreView.class);
        mockCoordinator = mock(SelectionCoordinator.class);

        when(mockFrame.getScoreView()).thenReturn(mockScore);
        when(mockFrame.requireScoreView()).thenReturn(mockScore);
        when(mockScore.getSelectionCoordinator()).thenReturn(mockCoordinator);

        action = TrillAction.createAction(mockFrame);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Line lineOf(ElementType... types) {
        var line = detachedLine();

        for (var type : types) {
            line.addElement(type.newInstance());
        }

        return line;
    }

    /** Wires the coordinator/score mocks so the given selection state is "active". */
    private void wireSelection(LineSelectionState state, boolean hasActiveSelection) {
        when(mockCoordinator.getActiveSelection()).thenReturn(state);
        when(mockCoordinator.hasActiveSelection()).thenReturn(hasActiveSelection);
        when(mockScore.getSelectionSize()).thenReturn(state.getSelectionSize());
    }

    @SuppressWarnings("UnusedReturnValue")
    private LineSelectionState selectRange(Line line, int begin, int end) {
        var state = new LineSelectionState(line);
        state.setSelectionFromClick(begin);
        state.extendSelectionTo(end);
        wireSelection(state, true);
        return state;
    }

    @SuppressWarnings("UnusedReturnValue")
    private LineSelectionState noActiveSelection(Line line) {
        var state = new LineSelectionState(line);
        wireSelection(state, false);
        return state;
    }

    private void fireSelectionChange() {
        action.musicSelectionDidChange(new MusicSelectionDidChangeNotification(mockScore));
    }

    private ActionEvent clickEvent() {
        return new ActionEvent(new JButton(), ActionEvent.ACTION_PERFORMED, "");
    }

    // -----------------------------------------------------------------------
    // Checked-state: musicSelectionDidChange overlap detection
    // -----------------------------------------------------------------------

    @Nested
    class CheckedState {

        @Test
        void testSelectionOverlappingTrillIsChecked() {
            final var trillAnchorIndex = 3;
            final var trillEndIndex = 7;
            final var selectionBeginIndex = 5;
            final var selectionEndIndex = 10;
            var line = lineOf(notesUpTo(selectionEndIndex));
            line.addRangeElement(
                new Trill(line.getElement(trillAnchorIndex), line.getElement(trillEndIndex))
            );
            selectRange(line, selectionBeginIndex, selectionEndIndex);

            fireSelectionChange();

            assertThat(action.isSelected()).isTrue();
        }

        @Test
        void testSelectionNotOverlappingTrillIsUnchecked() {
            final var trillAnchorIndex = 3;
            final var trillEndIndex = 7;
            final var selectionBeginIndex = 8;
            final var selectionEndIndex = 10;
            var line = lineOf(notesUpTo(selectionEndIndex));
            line.addRangeElement(
                new Trill(line.getElement(trillAnchorIndex), line.getElement(trillEndIndex))
            );
            selectRange(line, selectionBeginIndex, selectionEndIndex);

            fireSelectionChange();

            assertThat(action.isSelected()).isFalse();
        }

        @Test
        void testNoTrillsIsUnchecked() {
            final var selectionEndIndex = 2;
            var line = lineOf(notesUpTo(selectionEndIndex));
            selectRange(line, 0, selectionEndIndex);

            fireSelectionChange();

            assertThat(action.isSelected()).isFalse();
        }

        @Test
        void testSingleNoteSelectionOverlappingSingleNoteTrillIsChecked() {
            final var trillIndex = 5;
            var line = lineOf(notesUpTo(trillIndex));
            line.addRangeElement(new Trill(line.getElement(trillIndex)));
            selectRange(line, trillIndex, trillIndex);

            fireSelectionChange();

            assertThat(action.isSelected()).isTrue();
        }

        // G2 boundary: a trill ending exactly one element before the selection
        // start must NOT count as overlapping (guards the end-index >= begin
        // comparison against an off-by-one).
        @Test
        void testTrillEndingJustBeforeSelectionIsUnchecked() {
            final var trillAnchorIndex = 3;
            final var trillEndIndex = 4;
            final var selectionBeginIndex = 5;
            final var selectionEndIndex = 10;
            var line = lineOf(notesUpTo(selectionEndIndex));
            line.addRangeElement(
                new Trill(line.getElement(trillAnchorIndex), line.getElement(trillEndIndex))
            );
            selectRange(line, selectionBeginIndex, selectionEndIndex);

            fireSelectionChange();

            assertThat(action.isSelected()).isFalse();
        }

        // G2 boundary: a trill ending exactly at the selection start DOES overlap.
        @Test
        void testTrillEndingExactlyAtSelectionStartIsChecked() {
            final var trillAnchorIndex = 3;
            final var trillEndIndex = 5;
            final var selectionBeginIndex = 5;
            final var selectionEndIndex = 10;
            var line = lineOf(notesUpTo(selectionEndIndex));
            line.addRangeElement(
                new Trill(line.getElement(trillAnchorIndex), line.getElement(trillEndIndex))
            );
            selectRange(line, selectionBeginIndex, selectionEndIndex);

            fireSelectionChange();

            assertThat(action.isSelected()).isTrue();
        }

        // Both edges overlap simultaneously: the trill's bounds exactly match the selection's.
        @Test
        void testTrillExactlyMatchingSelectionBoundsIsChecked() {
            final var selectionBeginIndex = 3;
            final var selectionEndIndex = 7;
            var line = lineOf(notesUpTo(selectionEndIndex));
            line.addRangeElement(
                new Trill(line.getElement(selectionBeginIndex), line.getElement(selectionEndIndex))
            );
            selectRange(line, selectionBeginIndex, selectionEndIndex);

            fireSelectionChange();

            assertThat(action.isSelected()).isTrue();
        }

        /** Returns {@code lastIndex + 1} CROTCHET types, enough to populate indices {@code 0..lastIndex}. */
        private ElementType[] notesUpTo(int lastIndex) {
            var types = new ElementType[lastIndex + 1];

            for (var i = 0; i <= lastIndex; i++) {
                types[i] = ElementType.CROTCHET;
            }

            return types;
        }
    }

    // -----------------------------------------------------------------------
    // G1 enablement: replaces the deleted ToggleTrillActionEnablementTest
    // -----------------------------------------------------------------------

    @Nested
    class Enablement {

        @Test
        void testPitchedSelectionIsEnabled() {
            var line = lineOf(ElementType.CROTCHET, ElementType.CROTCHET);
            selectRange(line, 0, 1);

            fireSelectionChange();

            assertThat(action.isEnabled()).isTrue();
        }

        @Test
        void testAllRestsSelectionIsDisabled() {
            var line = lineOf(ElementType.CROTCHET_REST, ElementType.CROTCHET_REST);
            selectRange(line, 0, 1);

            fireSelectionChange();

            assertThat(action.isEnabled()).isFalse();
        }

        @Test
        void testNoSelectionIsDisabledAndUnchecked() {
            var line = lineOf(ElementType.CROTCHET);
            noActiveSelection(line);

            fireSelectionChange();

            assertThat(action.isEnabled()).isFalse();
            assertThat(action.isSelected()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Null guards: musicSelectionDidChange's defensive checks for a torn-down
    // ScoreView or a selection cleared between enablement and notification.
    // -----------------------------------------------------------------------

    @Nested
    class NullGuards {

        @Test
        void testNoScoreViewResetsCheckedState() {
            var line = lineOf(ElementType.CROTCHET, ElementType.CROTCHET);
            selectRange(line, 0, 1);
            fireSelectionChange();
            assertThat(action.isSelected())
                .as("sanity check: selection has no trill, so this starts unchecked")
                .isFalse();
            action.setSelected(true);

            when(mockFrame.getScoreView()).thenReturn(null);
            fireSelectionChange();

            assertThat(action.isSelected()).isFalse();
        }

        @Test
        void testNoActiveSelectionResetsCheckedState() {
            var line = lineOf(ElementType.CROTCHET, ElementType.CROTCHET);
            selectRange(line, 0, 1);
            fireSelectionChange();
            action.setSelected(true);

            when(mockCoordinator.getActiveSelection()).thenReturn(null);
            fireSelectionChange();

            assertThat(action.isSelected()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Check-action: actionPerformed while isSelected() == true (add)
    // -----------------------------------------------------------------------

    @Nested
    class CheckAction {

        @Test
        void testCheckingAddsOneSpanningTrill() {
            final var selectionBeginIndex = 1;
            final var selectionEndIndex = 3;
            var line = lineOf(
                ElementType.CROTCHET, ElementType.CROTCHET,
                ElementType.CROTCHET, ElementType.CROTCHET
            );
            selectRange(line, selectionBeginIndex, selectionEndIndex);
            action.setSelected(true);

            action.actionPerformed(clickEvent());

            var trills = line.findRangeElements(Trill.class);
            assertThat(trills).hasSize(1);
            var trill = trills.getFirst();
            assertThat(trill.getAnchorElementIndex()).isEqualTo(selectionBeginIndex);
            assertThat(trill.getEndElementIndex()).isEqualTo(selectionEndIndex);
        }

        @Test
        void testCheckingClampsToOutermostPitchedNotesWhenRestsAreOnTheBoundary() {
            final var firstPitchedIndex = 1;
            final var lastPitchedIndex = 2;
            var line = lineOf(
                ElementType.CROTCHET_REST, ElementType.CROTCHET,
                ElementType.CROTCHET, ElementType.CROTCHET_REST
            );
            selectRange(line, 0, 3);
            action.setSelected(true);

            action.actionPerformed(clickEvent());

            var trills = line.findRangeElements(Trill.class);
            assertThat(trills).hasSize(1);
            var trill = trills.getFirst();
            assertThat(trill.getAnchorElementIndex()).isEqualTo(firstPitchedIndex);
            assertThat(trill.getEndElementIndex()).isEqualTo(lastPitchedIndex);
        }

        @Test
        void testCheckingWithAllRestSelectionIsNoOp() {
            var line = lineOf(
                ElementType.CROTCHET_REST, ElementType.CROTCHET_REST, ElementType.CROTCHET_REST
            );
            selectRange(line, 0, 2);
            action.setSelected(true);

            action.actionPerformed(clickEvent());

            assertThat(line.findRangeElements(Trill.class)).isEmpty();
        }

        // Adding a trill must fire exactly one SongDidChangeNotification carrying a
        // single RangeElementAddition, so a check is one undo step. Uses a real Song
        // (the mock-Song CheckAction tests above suspend mutation tracking and cannot
        // observe notifications).
        @Test
        void testCheckingFiresOneSongDidChangeWithRangeElementAddition() {
            final var selectionBeginIndex = 1;
            final var selectionEndIndex = 3;
            var elementCount = selectionEndIndex + 1;
            var song = new Song();
            var line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                for (var i = 0; i < elementCount; i++) {
                    line.addElement(ElementType.CROTCHET.newInstance());
                }
            });

            var state = new LineSelectionState(line);
            state.setSelectionFromClick(selectionBeginIndex);
            state.extendSelectionTo(selectionEndIndex);
            wireSelection(state, true);
            action.setSelected(true);

            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                action.actionPerformed(clickEvent());

                var captor = ArgumentCaptor.forClass(Message.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));

                assertThat(captor.getValue())
                    .as("the single post must be a SongDidChangeNotification")
                    .isInstanceOf(SongDidChangeNotification.class);
                assertThat(((SongDidChangeNotification) captor.getValue()).getMutations())
                    .as("a check must record exactly one RangeElementAddition")
                    .singleElement()
                    .isInstanceOf(RangeElementAddition.class);
            }

            assertThat(line.findRangeElements(Trill.class)).hasSize(1);
        }
    }

    // -----------------------------------------------------------------------
    // Uncheck-action: actionPerformed while isSelected() == false (remove)
    // -----------------------------------------------------------------------

    @Nested
    class UncheckAction {

        private final int trill1AnchorIndex = 3;
        private final int trill1EndIndex = 5;
        private final int trill2AnchorIndex = 7;
        private final int trill2EndIndex = 9;
        private final int selectionBeginIndex = 4;
        private final int selectionEndIndex = 8;

        @Test
        void testUncheckingRemovesAllOverlappingTrills() {
            var elementCount = trill2EndIndex + 1;
            var types = new ElementType[elementCount];

            for (var i = 0; i < elementCount; i++) {
                types[i] = ElementType.CROTCHET;
            }

            var line = lineOf(types);
            line.addRangeElement(
                new Trill(line.getElement(trill1AnchorIndex), line.getElement(trill1EndIndex))
            );
            line.addRangeElement(
                new Trill(line.getElement(trill2AnchorIndex), line.getElement(trill2EndIndex))
            );
            selectRange(line, selectionBeginIndex, selectionEndIndex);
            action.setSelected(false);

            action.actionPerformed(clickEvent());

            assertThat(line.findRangeElements(Trill.class)).isEmpty();
        }

        // Guards against an overly-greedy predicate that removes every trill on the
        // line instead of only the ones overlapping the selection.
        @Test
        void testUncheckingLeavesNonOverlappingTrillIntact() {
            final var nonOverlappingAnchorIndex = 11;
            final var nonOverlappingEndIndex = 12;
            var elementCount = nonOverlappingEndIndex + 1;
            var types = new ElementType[elementCount];

            for (var i = 0; i < elementCount; i++) {
                types[i] = ElementType.CROTCHET;
            }

            var line = lineOf(types);
            line.addRangeElement(
                new Trill(line.getElement(trill1AnchorIndex), line.getElement(trill1EndIndex))
            );
            var nonOverlappingTrill = new Trill(
                line.getElement(nonOverlappingAnchorIndex), line.getElement(nonOverlappingEndIndex)
            );
            line.addRangeElement(nonOverlappingTrill);
            selectRange(line, selectionBeginIndex, selectionEndIndex);
            action.setSelected(false);

            action.actionPerformed(clickEvent());

            assertThat(line.findRangeElements(Trill.class)).containsExactly(nonOverlappingTrill);
        }

        // Confirms the multi-range removal is batched into a single
        // SongDidChangeNotification (one undo step) rather than one notification
        // per removed Trill.
        @Test
        void testUncheckingMultiRangeRemovalFiresExactlyOneSongDidChangeNotification() {
            var elementCount = trill2EndIndex + 1;
            var song = new Song();
            var line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                for (var i = 0; i < elementCount; i++) {
                    line.addElement(ElementType.CROTCHET.newInstance());
                }

                line.addRangeElement(
                    new Trill(line.getElement(trill1AnchorIndex), line.getElement(trill1EndIndex))
                );
                line.addRangeElement(
                    new Trill(line.getElement(trill2AnchorIndex), line.getElement(trill2EndIndex))
                );
            });

            var state = new LineSelectionState(line);
            state.setSelectionFromClick(selectionBeginIndex);
            state.extendSelectionTo(selectionEndIndex);
            wireSelection(state, true);
            action.setSelected(false);

            try (var messageCenterMock = mockStatic(MessageCenter.class)) {
                action.actionPerformed(clickEvent());

                // verify() with the implicit times(1) already asserts exactly one post
                // occurred; this checks that single post is the batched notification.
                var captor = ArgumentCaptor.forClass(Message.class);
                messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));

                assertThat(captor.getValue())
                    .as("the single batched post must be a SongDidChangeNotification")
                    .isInstanceOf(SongDidChangeNotification.class);
            }

            assertThat(line.findRangeElements(Trill.class)).isEmpty();
        }
    }
}
