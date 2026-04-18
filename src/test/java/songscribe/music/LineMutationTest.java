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
import songscribe.message.notification.CompositionDidChangeNotification;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.EndingLineFixture;
import songscribe.ui.layout.Tie;

class LineMutationTest extends UnitTest {

    private Composition composition;
    private Line line;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        // Construct before mocking so constructor's bus interactions go to the real bus.
        composition = new Composition();
        line = composition.getLine(0);
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
            composition.addLine(1, newLine);

            // addLine also fires LineKeyChange and LineLayoutChange mutations
            // for the new line's key and tempo defaults; filter for the LineInsertion.
            var notification = captureSingleDidChange();
            var insertion = findSingleMutationOfType(notification, LineInsertion.class);
            assertThat(insertion.lineIndex()).isEqualTo(1);
            assertThat(insertion.line()).isSameAs(newLine);
        }

        @Test
        void testRemoveLineFiresLineDeletion() {
            composition.removeLine(0);

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
            composition.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
                line.addElement(e2);
            });
        }

        @Test
        void testFiresSingleElementDeletion() {
            composition.withModification(() -> line.removeElement(1));

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
            composition.withoutMutationTracking(() -> line.addRangeElement(tie));

            composition.withModification(() -> line.removeElement(0));

            assertThat(line.getRangeElements()).doesNotContain(tie);
        }

        @Test
        void testUnaffectedRangeElementIsPreserved() {
            // Tie spans e0 → e2; deleting the middle element (e1) must not remove the tie.
            var tie = new Tie(e0, e2);
            composition.withoutMutationTracking(() -> line.addRangeElement(tie));

            composition.withModification(() -> line.removeElement(1));

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
            composition.withoutMutationTracking(() -> {
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
            composition.withModification(() -> line.removeRange(2, 5));

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
            composition.withModification(() -> line.removeRange(2, 5));

            // 10 elements + 1 auto-maintained final barline − 4 removed (indices 2, 3, 4, 5) = 7 remain.
            assertThat(line.elementCount()).isEqualTo(7);
        }

        @Test
        void testInvalidatedRangeElementIsRemoved() {
            // Ending anchored at e3 (index 3), which falls inside the deleted range [2, 5].
            var ending = new Ending(elements.get(3), elements.get(7), Ending.Type.FIRST);
            composition.withoutMutationTracking(() -> line.addRangeElement(ending));

            composition.withModification(() -> line.removeRange(2, 5));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        @Test
        void testUnaffectedRangeElementIsPreserved() {
            // Ending spans e0 → e1; deleted range [5, 8] is entirely disjoint.
            var ending = new Ending(elements.get(0), elements.get(1), Ending.Type.FIRST);
            composition.withoutMutationTracking(() -> line.addRangeElement(ending));

            composition.withModification(() -> line.removeRange(5, 8));

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
            // does not trigger a CompositionDidChangeNotification. This mirrors
            // the pre-bracket xpos adjustment in handleDelete's contiguous path.
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            e0.setXOffsetPx(0);
            e1.setXOffsetPx(10);
            composition.withoutMutationTracking(() -> {
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
            //   2. Call composition.withModification(() -> line.removeRange(begin, end)).
            // Exactly one CompositionDidChangeNotification must be posted, carrying
            // exactly one ElementRangeDeletion (not one per deleted element).
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            var e2 = new StaffElement(ElementType.QUAVER);
            var e3 = new StaffElement(ElementType.QUAVER);
            e0.setXOffsetPx(0);
            e1.setXOffsetPx(10);
            e2.setXOffsetPx(20);
            e3.setXOffsetPx(30);
            composition.withoutMutationTracking(() -> {
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
            composition.withModification(() -> line.removeRange(begin, end));

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
            var fixture = EndingLineFixture.primary(composition);
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
            composition.withModification(() ->
                line.setElement(0, new StaffElement(ElementType.DOUBLE_BARLINE)));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        @Test
        void testSetElementAnchorWithRepeatLeftRetainsEnding() {
            // Condition 1: REPEAT_LEFT is an allowed anchor type
            composition.withModification(() ->
                line.setElement(0, new StaffElement(ElementType.REPEAT_LEFT)));

            assertThat(line.getRangeElements()).contains(ending);
        }

        @Test
        void testSetElementSplitWithSingleBarlineRemovesEnding() {
            // Condition 2: SINGLE_BARLINE is not an allowed split type
            composition.withModification(() ->
                line.setElement(3, new StaffElement(ElementType.SINGLE_BARLINE)));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        @Test
        void testSetElementSplitWithRepeatLeftRightEndNotRightRepeatRetainsEnding() {
            // Condition 2: replacing split REPEAT_RIGHT → REPEAT_LEFT_RIGHT now returns
            // CompensateEnd, not Invalidate, so isInvalidatedByReplacement returns false and
            // the ending is retained. The UI layer handles the confirm and compensating change.
            composition.withModification(() ->
                line.setElement(3, new StaffElement(ElementType.REPEAT_LEFT_RIGHT)));

            assertThat(line.getRangeElements()).contains(ending);
        }

        @Test
        void testSetElementSplitWithRepeatLeftRightEndIsRightRepeatRetainsEnding() {
            // Condition 2: REPEAT_LEFT_RIGHT is valid as split when end is a right repeat
            var comp2 = new Composition();
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
            composition.withModification(() ->
                line.setElement(6, new StaffElement(ElementType.CROTCHET)));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        @Test
        void testSetElementEndWithDoubleBarlineRetainsEnding() {
            // Condition 3: any barline type is allowed as end
            composition.withModification(() ->
                line.setElement(6, new StaffElement(ElementType.DOUBLE_BARLINE)));

            assertThat(line.getRangeElements()).contains(ending);
        }

        // -------------------------------------------------------------------
        // addElement(int, StaffElement) wiring (condition 5)
        // -------------------------------------------------------------------

        @Test
        void testInsertBarlineInFirstSpanInteriorRemovesEnding() {
            // Condition 5: barline inserted at interior of first sub-span (index 2)
            composition.withModification(() ->
                line.addElement(2, new StaffElement(ElementType.SINGLE_BARLINE)));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        @Test
        void testInsertNoteInFirstSpanInteriorRetainsEnding() {
            // Non-barline/non-repeat insertions never invalidate the ending
            composition.withModification(() ->
                line.addElement(2, new StaffElement(ElementType.CROTCHET)));

            assertThat(line.getRangeElements()).contains(ending);
        }

        // -------------------------------------------------------------------
        // removeElement wiring — sequential deletion (condition 4)
        // -------------------------------------------------------------------

        @Test
        void testSequentialDeleteFirstSpanContentRemovesEndingOnLastNote() {
            // After removing note1 the ending is still present (note2 remains in span).
            composition.withModification(() -> line.removeElement(1));
            assertThat(line.getRangeElements()).contains(ending);

            // note2 has shifted to index 1; removing it empties the first span → ending gone.
            composition.withModification(() -> line.removeElement(1));
            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        // -------------------------------------------------------------------
        // removeRange wiring (condition 4)
        // -------------------------------------------------------------------

        @Test
        void testRemoveRangeAllFirstSpanContentRemovesEnding() {
            // Deleting both first-span notes (indices 1–2) at once empties the sub-span
            composition.withModification(() -> line.removeRange(1, 2));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }

        // -------------------------------------------------------------------
        // removeElement wiring — split deletion (condition 2)
        // -------------------------------------------------------------------

        @Test
        void testRemoveSplitElementRemovesEnding() {
            // Condition 2: deleting the REPEAT_RIGHT that separates first/second sub-spans
            composition.withModification(() -> line.removeElement(3));

            assertThat(line.getRangeElements()).doesNotContain(ending);
        }
    }

    // -----------------------------------------------------------------------
    // Final-barline mutation guards
    // -----------------------------------------------------------------------

    @Nested
    class FinalBarlineGuards {

        // --- addElement guards ---

        @Test
        void testAddFinalBarlineOnNonLastLineThrows() {
            var secondLine = new Line();
            composition.addLine(1, secondLine);
            // line is now index 0, no longer the last line
            assertThatIllegalStateException().isThrownBy(() ->
                composition.withModification(() ->
                    line.addElement(0, Composition.createFinalBarlineElement())));
        }

        @Test
        void testAddFinalBarlineAtNonEndOfLastLineThrows() {
            // Pre-load an element so that index 0 != elementCount() (2)
            composition.withoutMutationTracking(() ->
                line.addElement(new StaffElement(ElementType.QUAVER)));
            assertThatIllegalStateException().isThrownBy(() ->
                composition.withModification(() ->
                    line.addElement(0, Composition.createFinalBarlineElement())));
        }

        @Test
        void testAddFinalBarlineAtEndOfLastLineSucceeds() {
            assertThatNoException().isThrownBy(() ->
                composition.withModification(() ->
                    line.addElement(line.elementCount(),
                        Composition.createFinalBarlineElement())));
        }

        // --- setElement guards ---

        @Test
        void testSetFinalBarlineOnNonLastLineThrows() {
            var secondLine = new Line();
            composition.addLine(1, secondLine);
            composition.withoutMutationTracking(() ->
                line.addElement(new StaffElement(ElementType.QUAVER)));
            // line is index 0, not the last line
            assertThatIllegalStateException().isThrownBy(() ->
                composition.withModification(() ->
                    line.setElement(0, Composition.createFinalBarlineElement())));
        }

        @Test
        void testSetFinalBarlineAtNonLastPositionOfLastLineThrows() {
            composition.withoutMutationTracking(() -> {
                line.addElement(new StaffElement(ElementType.QUAVER));
                line.addElement(new StaffElement(ElementType.QUAVER));
            });
            // index 0 != elementCount()-1 (2)
            assertThatIllegalStateException().isThrownBy(() ->
                composition.withModification(() ->
                    line.setElement(0, Composition.createFinalBarlineElement())));
        }

        @Test
        void testSetFinalBarlineAtLastPositionOfLastLineSucceeds() {
            composition.withoutMutationTracking(() ->
                line.addElement(new StaffElement(ElementType.QUAVER)));
            // index 1 == elementCount()-1 (1), and this is the last line
            assertThatNoException().isThrownBy(() ->
                composition.withModification(() ->
                    line.setElement(line.elementCount() - 1,
                        Composition.createFinalBarlineElement())));
        }

        // --- removeElement guards ---

        @Test
        void testRemoveFinalBarlineOnLastLineThrows() {
            composition.withoutMutationTracking(() ->
                line.addElement(Composition.createFinalBarlineElement()));
            assertThatIllegalStateException().isThrownBy(() ->
                composition.withModification(() ->
                    line.removeElement(line.elementCount() - 1)));
        }

        @Test
        void testRemoveNonFinalElementOnLastLineSucceeds() {
            composition.withoutMutationTracking(() ->
                line.addElement(new StaffElement(ElementType.QUAVER)));
            assertThatNoException().isThrownBy(() ->
                composition.withModification(() -> line.removeElement(0)));
        }

        // --- removeRange guards ---

        @Test
        void testRemoveRangeIncludingFinalBarlineOnLastLineThrows() {
            composition.withoutMutationTracking(() -> {
                line.addElement(new StaffElement(ElementType.QUAVER));
                line.addElement(Composition.createFinalBarlineElement());
            });
            assertThatIllegalStateException().isThrownBy(() ->
                composition.withModification(() ->
                    line.removeRange(0, line.elementCount() - 1)));
        }

        @Test
        void testRemoveRangeExcludingFinalBarlineOnLastLineSucceeds() {
            composition.withoutMutationTracking(() -> {
                line.addElement(new StaffElement(ElementType.QUAVER));
                line.addElement(new StaffElement(ElementType.QUAVER));
            });
            assertThatNoException().isThrownBy(() ->
                composition.withModification(() -> line.removeRange(0, 0)));
        }

        // --- guard bypass ---

        @Test
        void testGuardsAreBypasedWhenMutationTrackingSuspended() {
            assertThatNoException().isThrownBy(() ->
                composition.withoutMutationTracking(() ->
                    line.addElement(0, Composition.createFinalBarlineElement())));
        }
    }

    // -----------------------------------------------------------------------
    // Selectability predicate
    // -----------------------------------------------------------------------

    @Nested
    class SelectabilityPredicate {

        @Test
        void testFinalBarlineOnLastLineIsNotInteractable() {
            assertThat(composition.isInteractable(
                Composition.createFinalBarlineElement(), line)).isFalse();
        }

        @Test
        void testDoubleBarlineOnLastLineIsInteractable() {
            assertThat(composition.isInteractable(
                new StaffElement(ElementType.DOUBLE_BARLINE), line)).isTrue();
        }

        @Test
        void testFinalBarlineOnNonLastLineIsInteractable() {
            composition.addLine(1, new Line());
            // line is now index 0, not the last line
            assertThat(composition.isInteractable(
                Composition.createFinalBarlineElement(), line)).isTrue();
        }

        @Test
        void testNoteOnLastLineIsInteractable() {
            assertThat(composition.isInteractable(
                new StaffElement(ElementType.QUAVER), line)).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private CompositionDidChangeNotification captureSingleDidChange() {
        var captor = ArgumentCaptor.forClass(Message.class);
        messageCenterMock.verify(() -> MessageCenter.post(captor.capture()));
        var didChanges = captor.getAllValues().stream()
            .filter(m -> m instanceof CompositionDidChangeNotification)
            .map(m -> (CompositionDidChangeNotification) m)
            .toList();

        assertThat(didChanges)
            .as("expected exactly one CompositionDidChangeNotification, got: %s", didChanges)
            .hasSize(1);

        return didChanges.get(0);
    }

    private <T extends Mutation> T findSingleMutationOfType(
        CompositionDidChangeNotification notification, Class<T> type
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
