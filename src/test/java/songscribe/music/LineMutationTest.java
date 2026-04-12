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

            // 10 elements − 4 removed (indices 2, 3, 4, 5) = 6 remain.
            assertThat(line.elementCount()).isEqualTo(6);
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
            e0.setXPosSs(0);
            e1.setXPosSs(10);
            composition.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
            });

            e1.setXPosSs(e1.getXPosSs() + 5);

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
            e0.setXPosSs(0);
            e1.setXPosSs(10);
            e2.setXPosSs(20);
            e3.setXPosSs(30);
            composition.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
                line.addElement(e2);
                line.addElement(e3);
            });

            var begin = 1;
            var end = 2;

            // Step 1: shift trailing elements (handleDelete's pre-bracket adjustment)
            var shift = line.getElement(begin).getXPosSs() - line.getElement(end + 1).getXPosSs();

            for (var i = end + 1; i < line.elementCount(); i++) {
                line.getElement(i).setXPosSs(line.getElement(i).getXPosSs() + shift);
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
