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
package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementRangeDeletion;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.EndingLineFixture;
import songscribe.ui.layout.Tie;

class LineMutationTest extends UnitTest {

    private Song song;
    private Line line;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        // Construct before mocking so constructor's bus interactions go to the real bus.
        song = new Song();
        line = song.getLine(0);
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    // -----------------------------------------------------------------------
    // Line insertion / deletion
    // -----------------------------------------------------------------------

    @Nested
    class LineMutations {

        @Test
        void testAddLineFiresLineInsertion() {
            var newLine = new Line();
            song.addLine(1, newLine);

            // addLine also fires LineKeyChange and LineLayoutChange mutations
            // for the new line's key and tempo defaults; filter for the LineInsertion.
            var notification = captureSingleDidChange();
            var insertion = findSingleMutationOfType(notification, LineInsertion.class);
            assertThat(insertion.lineIndex()).isEqualTo(1);
            assertThat(insertion.line()).isSameAs(newLine);
        }

        @Test
        void testRemoveLineFiresLineDeletion() {
            song.removeLine(0);

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);
            var deletion = (LineDeletion) notification.getMutations().get(0);
            assertThat(deletion.lineIndex()).isEqualTo(0);
            assertThat(deletion.deletedLine()).isSameAs(line);
        }
    }

    // -----------------------------------------------------------------------
    // Element deletion
    // -----------------------------------------------------------------------

    @Nested
    class RemoveElement {

        private StaffElement e0;
        private StaffElement e1;
        private StaffElement e2;

        @BeforeEach
        void addElements() {
            // Populate via withoutMutationTracking so Line.applyChange bypasses the
            // strict bracket check and the setup produces no notification.
            e0 = new StaffElement(ElementType.QUAVER);
            e1 = new StaffElement(ElementType.QUAVER);
            e2 = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
                line.addElement(e2);
            });
        }

        @Test
        void testFiresSingleElementDeletion() {
            song.withModification(() -> line.removeElement(1));

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);
            var deletion = (ElementDeletion) notification.getMutations().get(0);
            assertThat(deletion.line()).isSameAs(line);
            assertThat(deletion.index()).isEqualTo(1);
            assertThat(deletion.deletedElement()).isSameAs(e1);
        }

        @Test
        void testInvalidatedRangeElementIsRemoved() {
            // Tie spans e0 → e2; deleting the anchor (e0) must remove the tie.
            var tie = new Tie(e0, e2);
            song.withoutMutationTracking(() -> line.addRangeElement(tie));

            song.withModification(() -> line.removeElement(0));

            assertThat(line.getRangeElements()).doesNotContain(tie);
        }

        @Test
        void testUnaffectedRangeElementIsPreserved() {
            // Tie spans e0 → e2; deleting the middle element (e1) must not remove the tie.
            var tie = new Tie(e0, e2);
            song.withoutMutationTracking(() -> line.addRangeElement(tie));

            song.withModification(() -> line.removeElement(1));

            assertThat(line.getRangeElements()).contains(tie);
        }
    }

    // -----------------------------------------------------------------------
    // Element range deletion
    // -----------------------------------------------------------------------

    @Nested
    class RemoveRange {

        private List<StaffElement> elements;

        @BeforeEach
        void addElements() {
            // Populate via withoutMutationTracking so Line.applyChange bypasses the
            // strict bracket check and the setup produces no notification.
            var list = new ArrayList<StaffElement>();
            song.withoutMutationTracking(() -> {
                for (var i = 0; i < 10; i++) {
                    var element = new StaffElement(ElementType.QUAVER);
                    line.addElement(element);
                    list.add(element);
                }
            });

            elements = List.copyOf(list);
        }

        @Test
        void testFiresSingleElementRangeDeletion() {
            song.withModification(() -> line.removeRange(2, 5));

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);
            var deletion = (ElementRangeDeletion) notification.getMutations().get(0);
            assertThat(deletion.line()).isSameAs(line);
            assertThat(deletion.from()).isEqualTo(2);
            assertThat(deletion.to()).isEqualTo(5);
            assertThat(deletion.deletedElements()).containsExactly(
                elements.get(2), elements.get(3), elements.get(4), elements.get(5));
        }

        @Test
        void testElementListShrunkenByRangeWidth() {
            song.withModification(() -> line.removeRange(2, 5));

            // 10 elements + 1 auto-maintained final barline − 4 removed (indices 2, 3, 4, 5) = 7 remain.
            assertThat(line.elementCount()).isEqualTo(7);
        }

        @Test
        void testInvalidatedRangeElementIsRemoved() {
            // Ending anchored at e3 (index 3), which falls inside the deleted range [2, 5].
            var ending = new Ending(elements.get(3), elements.get(7), Ending.Type.FIRST);
            song.withoutMutationTracking(() -> line.addRangeElement(ending));

            song.withModification(() -> line.removeRange(2, 5));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        @Test
        void testUnaffectedRangeElementIsPreserved() {
            // Ending spans e0 → e1; deleted range [5, 8] is entirely disjoint.
            var ending = new Ending(elements.get(0), elements.get(1), Ending.Type.FIRST);
            song.withoutMutationTracking(() -> line.addRangeElement(ending));

            song.withModification(() -> line.removeRange(5, 8));

            assertThat(line.getRangeElements()).contains(ending);
        }
    }

    // -----------------------------------------------------------------------
    // Coordinator-equivalent range deletion
    // -----------------------------------------------------------------------

    @Nested
    class CoordinatorEquivalentDelete {

        @Test
        void testXPosShiftOutsideBracketProducesNoNotification() {
            // Verify that mutating element positions directly (outside any bracket)
            // does not trigger a SongDidChangeNotification. This mirrors
            // the pre-bracket xpos adjustment in handleDelete's contiguous path.
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            e0.setXOffsetPx(0);
            e1.setXOffsetPx(10);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
            });

            e1.setXOffsetPx(e1.getXOffsetPx() + 5);

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }

        @Test
        void testContiguousRangeDeleteFiresSingleNotificationWithOneRangeDeletion() {
            // Mirrors the production path in handleDelete for the contiguous range case:
            //   1. Shift trailing elements outside the bracket (direct field mutation, no notification).
            //   2. Call song.withModification(() -> line.removeRange(begin, end)).
            // Exactly one SongDidChangeNotification must be posted, carrying
            // exactly one ElementRangeDeletion (not one per deleted element).
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            var e2 = new StaffElement(ElementType.QUAVER);
            var e3 = new StaffElement(ElementType.QUAVER);
            e0.setXOffsetPx(0);
            e1.setXOffsetPx(10);
            e2.setXOffsetPx(20);
            e3.setXOffsetPx(30);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
                line.addElement(e2);
                line.addElement(e3);
            });

            var begin = 1;
            var end = 2;

            // Step 1: shift trailing elements (handleDelete's pre-bracket adjustment)
            var shift = line.getElement(begin).getXOffsetPx() - line.getElement(end + 1).getXOffsetPx();

            for (var i = end + 1; i < line.elementCount(); i++) {
                line.getElement(i).setXOffsetPx(line.getElement(i).getXOffsetPx() + shift);
            }

            // Step 2: bracket-wrapped range removal
            song.withModification(() -> line.removeRange(begin, end));

            var notification = captureSingleDidChange();
            assertThat(notification.getMutations()).hasSize(1);

            var deletion = (ElementRangeDeletion) notification.getMutations().get(0);
            assertThat(deletion.line()).isSameAs(line);
            assertThat(deletion.from()).isEqualTo(begin);
            assertThat(deletion.to()).isEqualTo(end);
            assertThat(deletion.deletedElements()).containsExactly(e1, e2);
        }
    }

    // -----------------------------------------------------------------------
    // Ending invalidation wiring
    // -----------------------------------------------------------------------

    /**
     * Integration tests verifying that the {@link Line} mutation methods
     * ({@code setElement}, {@code addElement(int,…)}, {@code removeElement},
     * {@code removeRange}) remove an {@link Ending} from
     * {@link Line#getRangeElements()} whenever the corresponding invalidation
     * predicate returns {@code true}.
     *
     * <p>Canonical line layout (same as {@code EndingInvalidationTest}):
     * <pre>
     *  idx:  0             1        2        3             4        5        6
     *        SINGLE_BAR    CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BAR
     *        (anchor)                        (split)                          (end)
     * </pre>
     */
    @Nested
    class EndingInvalidationConditions {

        private StaffElement anchor;
        private StaffElement note1;
        private StaffElement note2;
        private StaffElement split;
        private StaffElement note4;
        private StaffElement note5;
        private StaffElement end;
        private Ending ending;

        @BeforeEach
        void setUpEnding() {
            var fixture = EndingLineFixture.primary(song);
            anchor = fixture.anchor();
            note1  = fixture.note1();
            note2  = fixture.note2();
            split  = fixture.split();
            note4  = fixture.note4();
            note5  = fixture.note5();
            end    = fixture.end();
            ending = fixture.ending();
        }

        // -------------------------------------------------------------------
        // setElement wiring (conditions 1, 2, 3)
        // -------------------------------------------------------------------

        @Test
        void testSetElementAnchorWithDoubleBarlineRemovesEnding() {
            // Condition 1: DOUBLE_BARLINE is not an allowed anchor type
            song.withModification(() ->
                line.setElement(0, new StaffElement(ElementType.DOUBLE_BARLINE)));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        @Test
        void testSetElementAnchorWithRepeatLeftRetainsEnding() {
            // Condition 1: REPEAT_LEFT is an allowed anchor type
            song.withModification(() ->
                line.setElement(0, new StaffElement(ElementType.REPEAT_LEFT)));

            assertThat(line.getRangeElements()).contains(ending);
        }

        @Test
        void testSetElementSplitWithSingleBarlineRemovesEnding() {
            // Condition 2: SINGLE_BARLINE is not an allowed split type
            song.withModification(() ->
                line.setElement(3, new StaffElement(ElementType.SINGLE_BARLINE)));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        @Test
        void testSetElementSplitWithRepeatLeftRightEndNotRightRepeatRetainsEnding() {
            // Condition 2: replacing split REPEAT_RIGHT → REPEAT_LEFT_RIGHT now returns
            // CompensateEnd, not Invalidate, so isInvalidatedByReplacement returns false and
            // the ending is retained. The UI layer handles the confirm and compensating change.
            song.withModification(() ->
                line.setElement(3, new StaffElement(ElementType.REPEAT_LEFT_RIGHT)));

            assertThat(line.getRangeElements()).contains(ending);
        }

        @Test
        void testSetElementSplitWithRepeatLeftRightEndIsRightRepeatRetainsEnding() {
            // Condition 2: REPEAT_LEFT_RIGHT is valid as split when end is a right repeat
            var comp2 = new Song();
            var line2 = comp2.getLine(0);
            var anchor2 = new StaffElement(ElementType.SINGLE_BARLINE);
            var split2  = new StaffElement(ElementType.REPEAT_RIGHT);
            var end2    = new StaffElement(ElementType.REPEAT_RIGHT);
            comp2.withoutMutationTracking(() -> {
                line2.addElement(anchor2);
                line2.addElement(new StaffElement(ElementType.CROTCHET));
                line2.addElement(new StaffElement(ElementType.CROTCHET));
                line2.addElement(split2);
                line2.addElement(new StaffElement(ElementType.CROTCHET));
                line2.addElement(new StaffElement(ElementType.CROTCHET));
                line2.addElement(end2);
            });
            var ending2 = new Ending(anchor2, end2, Ending.Type.FIRST);
            comp2.withoutMutationTracking(() -> line2.addRangeElement(ending2));

            comp2.withModification(() ->
                line2.setElement(3, new StaffElement(ElementType.REPEAT_LEFT_RIGHT)));

            assertThat(line2.getRangeElements()).contains(ending2);
        }

        @Test
        void testSetElementEndWithNoteRemovesEnding() {
            // Condition 3: a content element is not an allowed end type
            song.withModification(() ->
                line.setElement(6, new StaffElement(ElementType.CROTCHET)));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        @Test
        void testSetElementEndWithDoubleBarlineRetainsEnding() {
            // Condition 3: any barline type is allowed as end
            song.withModification(() ->
                line.setElement(6, new StaffElement(ElementType.DOUBLE_BARLINE)));

            assertThat(line.getRangeElements()).contains(ending);
        }

        // -------------------------------------------------------------------
        // addElement(int, StaffElement) wiring (condition 5)
        // -------------------------------------------------------------------

        @Test
        void testInsertBarlineInFirstSpanInteriorRemovesEnding() {
            // Condition 5: barline inserted at interior of first sub-span (index 2)
            song.withModification(() ->
                line.addElement(2, new StaffElement(ElementType.SINGLE_BARLINE)));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        @Test
        void testInsertNoteInFirstSpanInteriorRetainsEnding() {
            // Non-barline/non-repeat insertions never invalidate the ending
            song.withModification(() ->
                line.addElement(2, new StaffElement(ElementType.CROTCHET)));

            assertThat(line.getRangeElements()).contains(ending);
        }

        // -------------------------------------------------------------------
        // removeElement wiring — sequential deletion (condition 4)
        // -------------------------------------------------------------------

        @Test
        void testSequentialDeleteFirstSpanContentRemovesEndingOnLastNote() {
            // After removing note1 the ending is still present (note2 remains in span).
            song.withModification(() -> line.removeElement(1));
            assertThat(line.getRangeElements()).contains(ending);

            // note2 has shifted to index 1; removing it empties the first span → ending gone.
            song.withModification(() -> line.removeElement(1));
            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        // -------------------------------------------------------------------
        // removeRange wiring (condition 4)
        // -------------------------------------------------------------------

        @Test
        void testRemoveRangeAllFirstSpanContentRemovesEnding() {
            // Deleting both first-span notes (indices 1–2) at once empties the sub-span
            song.withModification(() -> line.removeRange(1, 2));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        // -------------------------------------------------------------------
        // removeElement wiring — split deletion (condition 2)
        // -------------------------------------------------------------------

        @Test
        void testRemoveSplitElementRemovesEnding() {
            // Condition 2: deleting the REPEAT_RIGHT that separates first/second sub-spans
            song.withModification(() -> line.removeElement(3));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }
    }

    // -----------------------------------------------------------------------
    // Final-barline mutation guards
    // -----------------------------------------------------------------------

    @Nested
    class TerminalGuards {

        // --- addElement guards ---

        @Test
        void testAddFinalBarlineOnNonLastLineThrows() {
            var secondLine = new Line();
            song.addLine(1, secondLine);
            // line is now index 0, no longer the last line
            assertThatIllegalStateException().isThrownBy(() ->
                song.withModification(() ->
                    line.addElement(0, Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))));
        }

        @Test
        void testAddFinalBarlineAtNonEndOfLastLineThrows() {
            // Pre-load an element so that index 0 != elementCount() (2)
            song.withoutMutationTracking(() ->
                line.addElement(new StaffElement(ElementType.QUAVER)));
            assertThatIllegalStateException().isThrownBy(() ->
                song.withModification(() ->
                    line.addElement(0, Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))));
        }

        @Test
        void testAddFinalBarlineAtEndOfLastLineSucceeds() {
            assertThatNoException().isThrownBy(() ->
                song.withModification(() ->
                    line.addElement(line.elementCount(),
                        Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))));
        }

        // --- setElement guards ---

        @Test
        void testSetFinalBarlineOnNonLastLineThrows() {
            var secondLine = new Line();
            song.addLine(1, secondLine);
            song.withoutMutationTracking(() ->
                line.addElement(new StaffElement(ElementType.QUAVER)));
            // line is index 0, not the last line
            assertThatIllegalStateException().isThrownBy(() ->
                song.withModification(() ->
                    line.setElement(0, Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))));
        }

        @Test
        void testSetFinalBarlineAtNonLastPositionOfLastLineThrows() {
            song.withoutMutationTracking(() -> {
                line.addElement(new StaffElement(ElementType.QUAVER));
                line.addElement(new StaffElement(ElementType.QUAVER));
            });
            // index 0 != elementCount()-1 (2)
            assertThatIllegalStateException().isThrownBy(() ->
                song.withModification(() ->
                    line.setElement(0, Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))));
        }

        @Test
        void testSetFinalBarlineAtLastPositionOfLastLineSucceeds() {
            song.withoutMutationTracking(() ->
                line.addElement(new StaffElement(ElementType.QUAVER)));
            // index 1 == elementCount()-1 (1), and this is the last line
            assertThatNoException().isThrownBy(() ->
                song.withModification(() ->
                    line.setElement(line.elementCount() - 1,
                        Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))));
        }

        // --- removeElement guards ---

        @Test
        void testRemoveFinalBarlineOnLastLineThrows() {
            song.withoutMutationTracking(() ->
                line.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE)));
            assertThatIllegalStateException().isThrownBy(() ->
                song.withModification(() ->
                    line.removeElement(line.elementCount() - 1)));
        }

        @Test
        void testRemoveNonFinalElementOnLastLineSucceeds() {
            song.withoutMutationTracking(() ->
                line.addElement(new StaffElement(ElementType.QUAVER)));
            assertThatNoException().isThrownBy(() ->
                song.withModification(() -> line.removeElement(0)));
        }

        // --- removeRange guards ---

        @Test
        void testRemoveRangeIncludingFinalBarlineOnLastLineThrows() {
            song.withoutMutationTracking(() -> {
                line.addElement(new StaffElement(ElementType.QUAVER));
                line.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
            });
            assertThatIllegalStateException().isThrownBy(() ->
                song.withModification(() ->
                    line.removeRange(0, line.elementCount() - 1)));
        }

        @Test
        void testRemoveRangeExcludingFinalBarlineOnLastLineSucceeds() {
            song.withoutMutationTracking(() -> {
                line.addElement(new StaffElement(ElementType.QUAVER));
                line.addElement(new StaffElement(ElementType.QUAVER));
            });
            assertThatNoException().isThrownBy(() ->
                song.withModification(() -> line.removeRange(0, 0)));
        }

        // --- guard bypass ---

        @Test
        void testGuardsAreBypasedWhenMutationTrackingSuspended() {
            assertThatNoException().isThrownBy(() ->
                song.withoutMutationTracking(() ->
                    line.addElement(0, Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))));
        }
    }

    // -----------------------------------------------------------------------
    // Selectability predicate
    // -----------------------------------------------------------------------

    @Nested
    class SelectabilityPredicate {

        @Test
        void testFinalBarlineOnLastLineIsNotInteractable() {
            var terminal = line.getElement(line.elementCount() - 1);
            assertThat(song.isInteractable(terminal, line)).isFalse();
        }

        @Test
        void testDoubleBarlineOnLastLineIsInteractable() {
            assertThat(song.isInteractable(
                new StaffElement(ElementType.DOUBLE_BARLINE), line)).isTrue();
        }

        @Test
        void testFinalBarlineOnNonLastLineIsInteractable() {
            song.addLine(1, new Line());
            // line is now index 0, not the last line
            assertThat(song.isInteractable(
                Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE), line)).isTrue();
        }

        @Test
        void testNoteOnLastLineIsInteractable() {
            assertThat(song.isInteractable(
                new StaffElement(ElementType.QUAVER), line)).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Syllable adjustment
    // -----------------------------------------------------------------------

    @Nested
    class SyllableAdjustment {

        private StaffElement predecessor;
        private StaffElement neighbor;

        @BeforeEach
        void addElements() {
            predecessor = new StaffElement(ElementType.QUAVER);
            neighbor = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(predecessor);
                line.addElement(neighbor);
            });
        }

        private void setLyric(StaffElement element, Lyric.Syllabic syllabic, boolean compound) {
            element.properties.lyrics.add(new Lyric(1, "x", Lyric.Extend.NONE, syllabic, compound));
        }

        @Test
        void testInsertionBreaksSyllableRelation() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, false);
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, null));
            assertThat(predecessor.properties.lyrics.get(0).syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testInsertionBreaksCompoundWordRelation() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, true);
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, null));
            assertThat(predecessor.properties.lyrics.get(0).syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testInsertionLeavesNoneRelationUnchanged() {
            setLyric(predecessor, Lyric.Syllabic.SINGLE, false);
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, null));
            assertThat(predecessor.properties.lyrics.get(0).syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testInsertionOnNegativeIndexIsNoOp() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, false);
            assertThatNoException().isThrownBy(() ->
                song.withModification(() -> line.adjustSyllablesForNeighborChange(-1, null)));
            assertThat(predecessor.properties.lyrics.get(0).syllabic())
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }

        @Test
        void testDeletionOfTerminusBreaksPredecessorRelation() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, false);
            setLyric(neighbor, Lyric.Syllabic.SINGLE, false);
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, neighbor));
            assertThat(predecessor.properties.lyrics.get(0).syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testDeletionOfChainMemberPreservesPredecessorRelation() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, false);
            setLyric(neighbor, Lyric.Syllabic.BEGIN, false);
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, neighbor));
            assertThat(predecessor.properties.lyrics.get(0).syllabic())
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }

        @Test
        void testDeletionOfElementWithNoLyricBreaksPredecessorRelation() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, false);
            // neighbor has no lyric
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, neighbor));
            assertThat(predecessor.properties.lyrics.get(0).syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testMultiVerseAdjustsPerVerse() {
            predecessor.properties.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            predecessor.properties.lyrics.add(new Lyric(2, "un", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            neighbor.properties.lyrics.add(new Lyric(1, "re", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            // verse 2 has no lyric on neighbor — verse 2 predecessor should break, verse 1 should keep

            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, neighbor));

            assertThat(predecessor.properties.lyrics.get(0).syllabic())
                .as("verse 1: chain continues via neighbor's BEGIN")
                .isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(predecessor.properties.lyrics.get(1).syllabic())
                .as("verse 2: neighbor has no lyric, chain broken")
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }
    }

    // -----------------------------------------------------------------------
    // Extend adjustment
    // -----------------------------------------------------------------------

    @Nested
    class ExtendAdjustment {

        private static final int VERSE = 1;

        private StaffElement makeElement(Lyric.Extend extend) {
            var element = new StaffElement(ElementType.QUAVER);
            var text = extend == Lyric.Extend.START ? "x" : "";
            var syllabic = (extend == Lyric.Extend.STOP || extend == Lyric.Extend.CONTINUE) ? null : Lyric.Syllabic.SINGLE;
            element.properties.lyrics.add(new Lyric(VERSE, text, extend, syllabic, false));
            return element;
        }

        private Lyric.Extend extendOf(StaffElement element) {
            return element.properties.lyrics.get(0).extend();
        }

        private void addChain(StaffElement... elements) {
            song.withoutMutationTracking(() -> {
                for (var element : elements) {
                    line.addElement(element);
                }
                line.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
            });
        }

        private void deleteAt(int index) {
            song.withModification(() -> {
                line.adjustExtendsForDeletion(index);
                line.removeElement(index);
            });
        }

        // ---- 2-element chain [1.START, 2.STOP] ----

        @Test
        void testDeleteFirstOfTwoElementChain() {
            var start = makeElement(Lyric.Extend.START);
            var stop = makeElement(Lyric.Extend.STOP);
            addChain(start, stop);
            deleteAt(0);
            // Deleting START kills the chain: [2.NONE]
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.NONE);
        }

        @Test
        void testDeleteLastOfTwoElementChain() {
            var start = makeElement(Lyric.Extend.START);
            var stop = makeElement(Lyric.Extend.STOP);
            addChain(start, stop);
            deleteAt(1);
            // Deleting STOP from a 2-element chain collapses it: [1.NONE]
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.NONE);
        }

        // ---- 3-element chain [1.START, 2.CONTINUE, 3.STOP] ----

        @Test
        void testDeleteFirstOfThreeElementChain() {
            var start = makeElement(Lyric.Extend.START);
            var continueElement = makeElement(Lyric.Extend.CONTINUE);
            var stop = makeElement(Lyric.Extend.STOP);
            addChain(start, continueElement, stop);
            deleteAt(0);
            // Deleting START kills the chain: [2.NONE, 3.NONE]
            assertThat(extendOf(continueElement)).isEqualTo(Lyric.Extend.NONE);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.NONE);
        }

        @Test
        void testDeleteSecondOfThreeElementChain() {
            var start = makeElement(Lyric.Extend.START);
            var continueElement = makeElement(Lyric.Extend.CONTINUE);
            var stop = makeElement(Lyric.Extend.STOP);
            addChain(start, continueElement, stop);
            deleteAt(1);
            // Deleting CONTINUE heals the chain: [1.START, 3.STOP]
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testDeleteLastOfThreeElementChain() {
            var start = makeElement(Lyric.Extend.START);
            var continueElement = makeElement(Lyric.Extend.CONTINUE);
            var stop = makeElement(Lyric.Extend.STOP);
            addChain(start, continueElement, stop);
            deleteAt(2);
            // Deleting STOP promotes the preceding CONTINUE: [1.START, 2.STOP]
            assertThat(extendOf(continueElement)).isEqualTo(Lyric.Extend.STOP);
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
        }

        // ---- 4-element chain [1.START, 2.CONTINUE, 3.CONTINUE, 4.STOP] ----

        @Test
        void testDeleteFirstOfFourElementChain() {
            var start = makeElement(Lyric.Extend.START);
            var firstContinue = makeElement(Lyric.Extend.CONTINUE);
            var secondContinue = makeElement(Lyric.Extend.CONTINUE);
            var stop = makeElement(Lyric.Extend.STOP);
            addChain(start, firstContinue, secondContinue, stop);
            deleteAt(0);
            // Deleting START kills the chain: [2.NONE, 3.NONE, 4.NONE]
            assertThat(extendOf(firstContinue)).isEqualTo(Lyric.Extend.NONE);
            assertThat(extendOf(secondContinue)).isEqualTo(Lyric.Extend.NONE);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.NONE);
        }

        @Test
        void testDeleteSecondOfFourElementChain() {
            var start = makeElement(Lyric.Extend.START);
            var firstContinue = makeElement(Lyric.Extend.CONTINUE);
            var secondContinue = makeElement(Lyric.Extend.CONTINUE);
            var stop = makeElement(Lyric.Extend.STOP);
            addChain(start, firstContinue, secondContinue, stop);
            deleteAt(1);
            // Deleting CONTINUE heals the chain: [1.START, 3.CONTINUE, 4.STOP]
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
            assertThat(extendOf(secondContinue)).isEqualTo(Lyric.Extend.CONTINUE);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testDeleteSecondAndThirdOfFourElementChain() {
            var start = makeElement(Lyric.Extend.START);
            var firstContinue = makeElement(Lyric.Extend.CONTINUE);
            var secondContinue = makeElement(Lyric.Extend.CONTINUE);
            var stop = makeElement(Lyric.Extend.STOP);
            addChain(start, firstContinue, secondContinue, stop);
            deleteAt(1);
            deleteAt(1);
            // Each CONTINUE deletion heals the chain; remaining: [1.START, 4.STOP]
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testDeleteLastOfFourElementChain() {
            var start = makeElement(Lyric.Extend.START);
            var firstContinue = makeElement(Lyric.Extend.CONTINUE);
            var secondContinue = makeElement(Lyric.Extend.CONTINUE);
            var stop = makeElement(Lyric.Extend.STOP);
            addChain(start, firstContinue, secondContinue, stop);
            deleteAt(3);
            // Deleting STOP promotes the preceding CONTINUE: [1.START, 2.CONTINUE, 3.STOP]
            assertThat(extendOf(secondContinue)).isEqualTo(Lyric.Extend.STOP);
            assertThat(extendOf(firstContinue)).isEqualTo(Lyric.Extend.CONTINUE);
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SongDidChangeNotification captureSingleDidChange() {
        var captor = ArgumentCaptor.forClass(Message.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        var didChanges = captor.getAllValues().stream()
            .filter(m -> m instanceof SongDidChangeNotification)
            .map(m -> (SongDidChangeNotification) m)
            .toList();

        assertThat(didChanges)
            .as("expected exactly one SongDidChangeNotification, got: %s", didChanges)
            .hasSize(1);

        return didChanges.get(0);
    }

    private <T extends Mutation> T findSingleMutationOfType(
        SongDidChangeNotification notification, Class<T> type
    ) {
        var matches = notification.getMutations().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .toList();

        assertThat(matches)
            .as("expected exactly one %s mutation, got: %s", type.getSimpleName(), matches)
            .hasSize(1);

        return matches.get(0);
    }
}
