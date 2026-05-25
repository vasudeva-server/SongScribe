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
package songscribe.dom;

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
import songscribe.layout.Ending;
import songscribe.layout.EndingLineFixture;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementRangeDeletion;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.KeyField;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LineLayoutChange;
import songscribe.message.mutation.LineLayoutField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.SongDidChangeNotification;

class LineMutationTest extends UnitTest {

    private static final int VERSE = 1;

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

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LineMutations {

        @Test
        void testAddLineFiresLineInsertion() {
            var newLine = new Line(song);
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
            var deletion = (LineDeletion) notification.getMutations().getFirst();
            assertThat(deletion.lineIndex()).isEqualTo(0);
            assertThat(deletion.deletedLine()).isSameAs(line);
        }
    }

    // -----------------------------------------------------------------------
    // modifyElement clones before mutation
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ModifyElementCloneBeforeMutation {

        @Test
        void testBeforeElementIsPreMutationSnapshot() {
            // Set up an element with a known dot count, then modify it. The beforeElement
            // in the resulting ElementModification must reflect the pre-mutation state.
            var element = new StaffElement(ElementType.QUAVER);
            var preMutationDotCount = 0;
            var postMutationDotCount = 1;
            song.withoutMutationTracking(() -> line.addElement(element));

            song.withModification(() ->
                line.modifyElement(0, ElementField.DOT_COUNT,
                    () -> element.setDotCount(postMutationDotCount)));

            var notification = captureSingleDidChange();
            var modification = findSingleMutationOfType(notification, ElementModification.class);

            // beforeElement is the clone captured before the mutator ran.
            assertThat(modification.beforeElement().getDotCount())
                .as("beforeElement.dotCount should reflect pre-mutation state")
                .isEqualTo(preMutationDotCount);

            // The live element on the line now holds the post-mutation value.
            assertThat(line.getElement(0).getDotCount())
                .as("element on line should reflect post-mutation state")
                .isEqualTo(postMutationDotCount);
        }
    }

    // -----------------------------------------------------------------------
    // modifyElement with DURATION_AFFECTING field removes overlapping tuplets
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ModifyElementDurationAffectingRemovesTuplet {

        @Test
        void testModifyDurationAffectingFieldRemovesOverlappingTuplet() {
            // DOT_COUNT is in ElementField.DURATION_AFFECTING. Modifying it must trigger
            // removeOverlappingTuplets, which removes any tuplet whose span covers the
            // modified element's index.
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            var e2 = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
                line.addElement(e2);
            });

            var tripletGrade = 3;
            var tuplet = new Tuplet(e0, e2, tripletGrade);
            song.withoutMutationTracking(() -> line.addTuplet(tuplet));

            // Modify e1 (index 1) using a DURATION_AFFECTING field (DOT_COUNT).
            song.withModification(() ->
                line.modifyElement(1, ElementField.DOT_COUNT, () -> e1.setDotCount(1)));

            var notification = captureSingleDidChange();

            // The TupletRemoval mutation must be present alongside the ElementModification.
            var removal = findSingleMutationOfType(notification, TupletRemoval.class);
            assertThat(removal.tuplet()).isSameAs(tuplet);

            // The tuplet must no longer be on the line.
            assertThat(line.findTupletAt(1)).isNull();
        }

        @Test
        void testModifyNonDurationAffectingFieldPreservesTuplet() {
            // ARTICULATION is not in DURATION_AFFECTING, so modifying it must NOT remove
            // any tuplet covering the modified element.
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            var e2 = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
                line.addElement(e2);
            });

            var tripletGrade = 3;
            var tuplet = new Tuplet(e0, e2, tripletGrade);
            song.withoutMutationTracking(() -> line.addTuplet(tuplet));

            // Modify e1 using a non-DURATION_AFFECTING field (UPPER is a simple boolean flip).
            song.withModification(() ->
                line.modifyElement(1, ElementField.UPPER, () -> e1.setUpper(true)));

            // The tuplet must still be present.
            assertThat(line.findTupletAt(1)).isSameAs(tuplet);
        }
    }

    // -----------------------------------------------------------------------
    // Element deletion
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
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
            var deletion = (ElementDeletion) notification.getMutations().getFirst();
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

    @SuppressWarnings("PackageVisibleInnerClass")
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
            var deletion = (ElementRangeDeletion) notification.getMutations().getFirst();
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
    // addElement(e) fires ElementInsertion
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddElementFiresElementInsertion {

        @Test
        void testAddElementFiresElementInsertionMutation() {
            // The single-arg addElement(StaffElement) must record an ElementInsertion
            // mutation carrying the inserted element and its resolved index.
            var newElement = new StaffElement(ElementType.QUAVER);
            var expectedIndex = line.elementCount() - 1; // inserts before auto-maintained terminal

            song.withModification(() -> line.addElement(newElement));

            var notification = captureSingleDidChange();
            var insertion = findSingleMutationOfType(notification, ElementInsertion.class);
            assertThat(insertion.line()).isSameAs(line);
            assertThat(insertion.index()).isEqualTo(expectedIndex);
            assertThat(insertion.element()).isSameAs(newElement);
        }
    }

    // -----------------------------------------------------------------------
    // addElement(e) insertion position
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddElementInsertionPosition {

        @Test
        void testAddElementInsertsBeforeAutoMaintainedTerminal() {
            // A fresh Song's last line already has an auto-maintained FINAL_DOUBLE_BARLINE.
            // addElement(e) should insert before it, so the new element lands at count-1.
            var elementCountBefore = line.elementCount();
            var newElement = new StaffElement(ElementType.QUAVER);
            song.withModification(() -> line.addElement(newElement));

            // The terminal is still the last element; new element is second-to-last.
            var expectedIndex = elementCountBefore - 1;
            assertThat(line.getElement(expectedIndex)).isSameAs(newElement);
            // The element that was previously the terminal is now at count-1 (last slot).
            assertThat(line.getElement(line.elementCount() - 1).getType())
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        @Test
        void testAddElementAppendsWhenNoAutoMaintainedTerminal() {
            // Add a second line so line is no longer the last line — the terminal on
            // line is no longer the auto-maintained one and acts as an ordinary element.
            var secondLine = new Line(song);
            song.addLine(1, secondLine);

            var elementCountBefore = line.elementCount();
            var newElement = new StaffElement(ElementType.QUAVER);
            song.withModification(() -> line.addElement(newElement));

            // With no auto-maintained terminal on this line, the element appends at the end.
            assertThat(line.getElement(elementCountBefore)).isSameAs(newElement);
        }
    }

    // -----------------------------------------------------------------------
    // addElement(0, e) tempo migration on line 0
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddElementTempoMigration {

        @Test
        void testInsertAtIndexZeroOnFirstLineMigratesTempoAttachment() {
            // Manually attach a tempo to the current first element (index 0) to simulate
            // the state that exists after a real song is loaded (loadFrom calls
            // attachInitialTempoIfNeeded). We use withoutMutationTracking so the setup
            // itself emits no notification.
            // new Song() always initializes a non-null Tempo; the explicit guard narrows
            // the type so NullAway allows passing it to TempoChangeAttachment's constructor.
            var tempo = song.getTempo();
            if (tempo == null) {
                throw new AssertionError("fresh Song should always have a non-null tempo");
            }
            var originalFirst = line.getElement(0);
            song.withoutMutationTracking(() ->
                originalFirst.addAttachment(new TempoChangeAttachment(tempo)));

            var newFirst = new StaffElement(ElementType.QUAVER);
            song.withModification(() -> line.addElement(0, newFirst));

            // The tempo attachment must have moved to the new first element.
            assertThat(newFirst.findAttachment(TempoChangeAttachment.class))
                .as("new first element should carry the migrated TempoChangeAttachment")
                .isNotNull();
            // The original first element must no longer hold the tempo attachment.
            assertThat(originalFirst.findAttachment(TempoChangeAttachment.class))
                .as("original first element should no longer carry the TempoChangeAttachment")
                .isNull();
        }
    }

    // -----------------------------------------------------------------------
    // addElement(i, e) removes spanning tuplet
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AddElementTupletRemoval {

        @Test
        void testInsertAtIndexInsideTupletSpanRemovesTuplet() {
            // Build: e0 — e1 — e2 — terminal; tuplet spans e0..e2 (indices 0..2).
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            var e2 = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
                line.addElement(e2);
            });

            // Grade 3 = triplet.
            var tripletGrade = 3;
            var tuplet = new Tuplet(e0, e2, tripletGrade);
            song.withoutMutationTracking(() -> line.addTuplet(tuplet));

            // Insert at index 1 — inside the tuplet's span (anchor=0, end=2).
            var inserted = new StaffElement(ElementType.QUAVER);
            song.withModification(() -> line.addElement(1, inserted));

            // The notification must include a TupletRemoval.
            var notification = captureSingleDidChange();
            var removal = findSingleMutationOfType(notification, TupletRemoval.class);
            assertThat(removal.tuplet()).isSameAs(tuplet);

            // The tuplet must be gone from the line.
            assertThat(line.findTupletAt(0)).isNull();
        }

        @Test
        void testInsertAtTupletAnchorIndexDoesNotRemoveTuplet() {
            // Inserting at the anchor index itself (index == anchor) is not strictly
            // inside the span (the guard is index > anchorIndex), so the tuplet survives.
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
            });

            var dupletGrade = 2;
            var tuplet = new Tuplet(e0, e1, dupletGrade);
            song.withoutMutationTracking(() -> line.addTuplet(tuplet));

            var inserted = new StaffElement(ElementType.QUAVER);
            // Insert at the anchor index (0) — not strictly inside, so tuplet is preserved.
            song.withModification(() -> line.addElement(0, inserted));

            assertThat(line.findTupletAt(1)).isSameAs(tuplet);
        }
    }

    // -----------------------------------------------------------------------
    // Coordinator-equivalent range deletion
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
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

            var deletion = (ElementRangeDeletion) notification.getMutations().getFirst();
            assertThat(deletion.line()).isSameAs(line);
            assertThat(deletion.from()).isEqualTo(begin);
            assertThat(deletion.to()).isEqualTo(end);
            assertThat(deletion.deletedElements()).containsExactly(e1, e2);
        }
    }

    // -----------------------------------------------------------------------
    // effectiveElementCount excludes trailing auto-maintained terminal
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EffectiveElementCount {

        @Test
        void testEffectiveElementCountExcludesAutoMaintainedTerminal() {
            // A fresh Song's last line carries an auto-maintained FINAL_DOUBLE_BARLINE
            // as its last element.  effectiveElementCount() must return elementCount()-1
            // for that line, because the terminal must not be treated as a content element.
            var terminalCount = 1;
            assertThat(line.effectiveElementCount())
                .as("effectiveElementCount excludes the auto-maintained terminal")
                .isEqualTo(line.elementCount() - terminalCount);
        }

        @Test
        void testEffectiveElementCountEqualsElementCountWhenNoAutoMaintainedTerminal() {
            // Adding a second line makes line 0 no longer the last line, so its
            // FINAL_DOUBLE_BARLINE is no longer the auto-maintained terminal.
            // effectiveElementCount() must equal elementCount() in that case.
            var secondLine = new Line(song);
            song.addLine(1, secondLine);

            assertThat(line.effectiveElementCount())
                .as("effectiveElementCount equals elementCount when no auto-maintained terminal")
                .isEqualTo(line.elementCount());
        }

        @Test
        void testEffectiveElementCountWithContentElementsBeforeTerminal() {
            // Adding content elements before the terminal must shift both counts up by the
            // same amount — the difference must remain exactly 1 (the terminal).
            var noteCount = 3;
            song.withoutMutationTracking(() -> {
                for (var i = 0; i < noteCount; i++) {
                    line.addElement(new StaffElement(ElementType.QUAVER));
                }
            });

            var terminalCount = 1;
            assertThat(line.effectiveElementCount())
                .as("effectiveElementCount excludes only the terminal regardless of note count")
                .isEqualTo(line.elementCount() - terminalCount);
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
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EndingInvalidationConditions {

        private Ending ending;

        @BeforeEach
        void setUpEnding() {
            var fixture = EndingLineFixture.primary(song);
            var anchor = fixture.anchor();
            var note1 = fixture.note1();
            var note2 = fixture.note2();
            var split = fixture.split();
            var note4 = fixture.note4();
            var note5 = fixture.note5();
            var end = fixture.end();
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

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TerminalGuards {

        // --- addElement guards ---

        @Test
        void testAddFinalBarlineOnNonLastLineThrows() {
            var secondLine = new Line(song);
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
            var secondLine = new Line(song);
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
    // setElement fires ElementReplacement mutation
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetElementFiresReplacement {

        @Test
        void testSetElementFiresSingleElementReplacementWithCorrectOldAndNew() {
            var oldElement = new StaffElement(ElementType.QUAVER);
            var newElement = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> line.addElement(oldElement));

            song.withModification(() -> line.setElement(0, newElement));

            var notification = captureSingleDidChange();
            var replacement = findSingleMutationOfType(notification, ElementReplacement.class);
            assertThat(replacement.line()).isSameAs(line);
            assertThat(replacement.index()).isEqualTo(0);
            assertThat(replacement.oldElement()).isSameAs(oldElement);
            assertThat(replacement.newElement()).isSameAs(newElement);
        }
    }

    // -----------------------------------------------------------------------
    // setElement updates surviving range element anchor/end references
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetElementUpdatesRangeRefs {

        @Test
        void testSetElementUpdatesAnchorReferenceInSurvivingTie() {
            // A tie anchored at index 0; setElement(0, newElement) must update the tie's
            // anchor to newElement so getAnchorElementIndex() remains valid.
            var originalFirst = new StaffElement(ElementType.QUAVER);
            var second = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(originalFirst);
                line.addElement(second);
            });

            var tie = new Tie(originalFirst, second);
            song.withoutMutationTracking(() -> line.addRangeElement(tie));

            var newFirst = new StaffElement(ElementType.CROTCHET);
            song.withModification(() -> line.setElement(0, newFirst));

            // The tie survives (anchor swap does not invalidate it) and its anchor
            // reference is updated to newFirst.
            assertThat(line.getRangeElements()).contains(tie);
            assertThat(tie.getAnchorElement())
                .as("tie anchor should be updated to the replacement element")
                .isSameAs(newFirst);
        }

        @Test
        void testSetElementUpdatesEndReferenceInSurvivingTie() {
            // A tie whose end element is replaced; the end reference must be updated.
            var first = new StaffElement(ElementType.QUAVER);
            var originalEnd = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(first);
                line.addElement(originalEnd);
            });

            var tie = new Tie(first, originalEnd);
            song.withoutMutationTracking(() -> line.addRangeElement(tie));

            var newEnd = new StaffElement(ElementType.CROTCHET);
            song.withModification(() -> line.setElement(1, newEnd));

            assertThat(line.getRangeElements()).contains(tie);
            assertThat(tie.getEndElement())
                .as("tie end reference should be updated to the replacement element")
                .isSameAs(newEnd);
        }
    }

    // -----------------------------------------------------------------------
    // Selectability predicate
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
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
            song.addLine(1, new Line(song));
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

    @SuppressWarnings("PackageVisibleInnerClass")
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
            element.lyrics.add(new Lyric(1, "x", Lyric.Extend.NONE, syllabic, compound));
        }

        @Test
        void testInsertionBreaksSyllableRelation() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, false);
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, null));
            assertThat(predecessor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testInsertionBreaksCompoundWordRelation() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, true);
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, null));
            assertThat(predecessor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testInsertionLeavesNoneRelationUnchanged() {
            setLyric(predecessor, Lyric.Syllabic.SINGLE, false);
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, null));
            assertThat(predecessor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testInsertionOnNegativeIndexIsNoOp() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, false);
            assertThatNoException().isThrownBy(() ->
                song.withModification(() -> line.adjustSyllablesForNeighborChange(-1, null)));
            assertThat(predecessor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }

        @Test
        void testDeletionOfTerminusBreaksPredecessorRelation() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, false);
            setLyric(neighbor, Lyric.Syllabic.SINGLE, false);
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, neighbor));
            assertThat(predecessor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testDeletionOfChainMemberPreservesPredecessorRelation() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, false);
            setLyric(neighbor, Lyric.Syllabic.BEGIN, false);
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, neighbor));
            assertThat(predecessor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }

        @Test
        void testDeletionOfElementWithNoLyricBreaksPredecessorRelation() {
            setLyric(predecessor, Lyric.Syllabic.BEGIN, false);
            // neighbor has no lyric
            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, neighbor));
            assertThat(predecessor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testMultiVerseAdjustsPerVerse() {
            predecessor.lyrics.add(new Lyric(1, "do", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            predecessor.lyrics.add(new Lyric(2, "un", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            neighbor.lyrics.add(new Lyric(1, "re", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            // verse 2 has no lyric on neighbor — verse 2 predecessor should break, verse 1 should keep

            song.withModification(() -> line.adjustSyllablesForNeighborChange(0, neighbor));

            assertThat(predecessor.lyrics.get(0).syllabic())
                .as("verse 1: chain continues via neighbor's BEGIN")
                .isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(predecessor.lyrics.get(1).syllabic())
                .as("verse 2: neighbor has no lyric, chain broken")
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }
    }

    // -----------------------------------------------------------------------
    // Syllable adjustment on insertion
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SyllableAdjustmentOnInsertion {

        private StaffElement successor;

        @BeforeEach
        void addElements() {
            var inserted = new StaffElement(ElementType.QUAVER);
            successor = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(inserted);
                line.addElement(successor);
            });
        }

        private void setLyric(Lyric.Syllabic syllabic) {
            successor.lyrics.add(new Lyric(1, "x", Lyric.Extend.NONE, syllabic, false));
        }

        @Test
        void testInsertionBeforeMiddleSyllablePromotesToBegin() {
            setLyric(Lyric.Syllabic.MIDDLE);
            song.withModification(() -> line.adjustSyllablesForSuccessorAfterInsertion(0));
            assertThat(successor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }

        @Test
        void testInsertionBeforeEndSyllablePromotesToSingle() {
            setLyric(Lyric.Syllabic.END);
            song.withModification(() -> line.adjustSyllablesForSuccessorAfterInsertion(0));
            assertThat(successor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testInsertionBeforeBeginSyllableLeavesUnchanged() {
            setLyric(Lyric.Syllabic.BEGIN);
            song.withModification(() -> line.adjustSyllablesForSuccessorAfterInsertion(0));
            assertThat(successor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }

        @Test
        void testInsertionBeforeSingleSyllableLeavesUnchanged() {
            setLyric(Lyric.Syllabic.SINGLE);
            song.withModification(() -> line.adjustSyllablesForSuccessorAfterInsertion(0));
            assertThat(successor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testInsertionBeforeElementWithNoLyricIsNoOp() {
            song.withModification(() -> line.adjustSyllablesForSuccessorAfterInsertion(0));
            assertThat(successor.lyrics).isEmpty();
        }

        @Test
        void testInsertionPastLastElementIsNoOp() {
            setLyric(Lyric.Syllabic.MIDDLE);
            // insertionIndex 1 means successorIndex 2, out of bounds for a 2-element line
            assertThatNoException().isThrownBy(() ->
                song.withModification(() -> line.adjustSyllablesForSuccessorAfterInsertion(1)));
            assertThat(successor.lyrics.getFirst().syllabic())
                .isEqualTo(Lyric.Syllabic.MIDDLE);
        }
    }

    // -----------------------------------------------------------------------
    // Extend adjustment
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ExtendAdjustment {

        private void deleteAt(int index) {
            song.withModification(() -> {
                line.adjustExtendsForDeletion(index);
                line.removeElement(index);
            });
        }

        // ---- 2-element chain [1.START, 2.STOP] ----

        @Test
        void testDeleteFirstOfTwoElementChain() {
            var start = makeExtendElement(Lyric.Extend.START);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, stop);
            deleteAt(0);
            // Deleting START kills the chain: [2.NONE]
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.NONE);
        }

        @Test
        void testDeleteLastOfTwoElementChain() {
            var start = makeExtendElement(Lyric.Extend.START);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, stop);
            deleteAt(1);
            // Deleting STOP from a 2-element chain collapses it: [1.NONE]
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.NONE);
        }

        // ---- 3-element chain [1.START, 2.CONTINUE, 3.STOP] ----

        @Test
        void testDeleteFirstOfThreeElementChain() {
            var start = makeExtendElement(Lyric.Extend.START);
            var continueElement = makeExtendElement(Lyric.Extend.CONTINUE);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, continueElement, stop);
            deleteAt(0);
            // Deleting START kills the chain: [2.NONE, 3.NONE]
            assertThat(extendOf(continueElement)).isEqualTo(Lyric.Extend.NONE);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.NONE);
        }

        @Test
        void testDeleteSecondOfThreeElementChain() {
            var start = makeExtendElement(Lyric.Extend.START);
            var continueElement = makeExtendElement(Lyric.Extend.CONTINUE);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, continueElement, stop);
            deleteAt(1);
            // Deleting CONTINUE heals the chain: [1.START, 3.STOP]
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testDeleteLastOfThreeElementChain() {
            var start = makeExtendElement(Lyric.Extend.START);
            var continueElement = makeExtendElement(Lyric.Extend.CONTINUE);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, continueElement, stop);
            deleteAt(2);
            // Deleting STOP promotes the preceding CONTINUE: [1.START, 2.STOP]
            assertThat(extendOf(continueElement)).isEqualTo(Lyric.Extend.STOP);
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
        }

        // ---- 4-element chain [1.START, 2.CONTINUE, 3.CONTINUE, 4.STOP] ----

        @Test
        void testDeleteFirstOfFourElementChain() {
            var start = makeExtendElement(Lyric.Extend.START);
            var firstContinue = makeExtendElement(Lyric.Extend.CONTINUE);
            var secondContinue = makeExtendElement(Lyric.Extend.CONTINUE);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, firstContinue, secondContinue, stop);
            deleteAt(0);
            // Deleting START kills the chain: [2.NONE, 3.NONE, 4.NONE]
            assertThat(extendOf(firstContinue)).isEqualTo(Lyric.Extend.NONE);
            assertThat(extendOf(secondContinue)).isEqualTo(Lyric.Extend.NONE);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.NONE);
        }

        @Test
        void testDeleteSecondOfFourElementChain() {
            var start = makeExtendElement(Lyric.Extend.START);
            var firstContinue = makeExtendElement(Lyric.Extend.CONTINUE);
            var secondContinue = makeExtendElement(Lyric.Extend.CONTINUE);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, firstContinue, secondContinue, stop);
            deleteAt(1);
            // Deleting CONTINUE heals the chain: [1.START, 3.CONTINUE, 4.STOP]
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
            assertThat(extendOf(secondContinue)).isEqualTo(Lyric.Extend.CONTINUE);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testDeleteSecondAndThirdOfFourElementChain() {
            var start = makeExtendElement(Lyric.Extend.START);
            var firstContinue = makeExtendElement(Lyric.Extend.CONTINUE);
            var secondContinue = makeExtendElement(Lyric.Extend.CONTINUE);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, firstContinue, secondContinue, stop);
            deleteAt(1);
            deleteAt(1);
            // Each CONTINUE deletion heals the chain; remaining: [1.START, 4.STOP]
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testDeleteLastOfFourElementChain() {
            var start = makeExtendElement(Lyric.Extend.START);
            var firstContinue = makeExtendElement(Lyric.Extend.CONTINUE);
            var secondContinue = makeExtendElement(Lyric.Extend.CONTINUE);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, firstContinue, secondContinue, stop);
            deleteAt(3);
            // Deleting STOP promotes the preceding CONTINUE: [1.START, 2.CONTINUE, 3.STOP]
            assertThat(extendOf(secondContinue)).isEqualTo(Lyric.Extend.STOP);
            assertThat(extendOf(firstContinue)).isEqualTo(Lyric.Extend.CONTINUE);
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
        }
    }

    // -----------------------------------------------------------------------
    // Extend adjustment on insertion
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ExtendAdjustmentOnInsertion {

        private void insertAt(int index) {
            var bareNote = new StaffElement(ElementType.QUAVER);
            song.withModification(() -> {
                line.adjustExtendsForInsertion(index);
                line.addElement(index, bareNote);
            });
        }

        @Test
        void testInsertionAfterStartClearsChain() {
            var start = makeExtendElement(Lyric.Extend.START);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, stop);
            insertAt(1);
            // START broken by insertion: predecessor cleared to NONE, forward chain cascade-cleared
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.NONE);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.NONE);
        }

        @Test
        void testInsertionAfterContinuePromotesToStop() {
            var start = makeExtendElement(Lyric.Extend.START);
            var continueElement = makeExtendElement(Lyric.Extend.CONTINUE);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, continueElement, stop);
            insertAt(2);
            // CONTINUE promoted to STOP; forward chain cascade-cleared
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
            assertThat(extendOf(continueElement)).isEqualTo(Lyric.Extend.STOP);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.NONE);
        }

        @Test
        void testInsertionAfterStopIsNoOp() {
            var start = makeExtendElement(Lyric.Extend.START);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, stop);
            insertAt(2);
            // Inserting after a chain terminus leaves it intact
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testInsertionAfterNoneIsNoOp() {
            var standalone = makeExtendElement(Lyric.Extend.NONE);
            addExtendChain(standalone);
            insertAt(1);
            assertThat(extendOf(standalone)).isEqualTo(Lyric.Extend.NONE);
        }

        @Test
        void testInsertionAtIndexZeroIsNoOp() {
            var start = makeExtendElement(Lyric.Extend.START);
            var stop = makeExtendElement(Lyric.Extend.STOP);
            addExtendChain(start, stop);
            insertAt(0);
            // No predecessor — nothing to repair
            assertThat(extendOf(start)).isEqualTo(Lyric.Extend.START);
            assertThat(extendOf(stop)).isEqualTo(Lyric.Extend.STOP);
        }

        @Test
        void testMultiVerseEachVerseRepairedIndependently() {
            var verse2 = 2;
            var predecessor = new StaffElement(ElementType.QUAVER);
            predecessor.lyrics.add(new Lyric(VERSE, "x", Lyric.Extend.START, Lyric.Syllabic.SINGLE, false));
            predecessor.lyrics.add(new Lyric(verse2, "", Lyric.Extend.CONTINUE, null, false));

            var follower = new StaffElement(ElementType.QUAVER);
            follower.lyrics.add(new Lyric(VERSE, "", Lyric.Extend.STOP, null, false));
            follower.lyrics.add(new Lyric(verse2, "", Lyric.Extend.STOP, null, false));

            song.withoutMutationTracking(() -> {
                line.addElement(predecessor);
                line.addElement(follower);
                line.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
            });

            insertAt(1);

            // Verse 1: START → NONE; verse 2: CONTINUE → STOP
            assertThat(predecessor.lyrics.get(0).extend()).isEqualTo(Lyric.Extend.NONE);
            assertThat(predecessor.lyrics.get(1).extend()).isEqualTo(Lyric.Extend.STOP);
            // Forward chain for both verses cascade-cleared
            assertThat(follower.lyrics.get(0).extend()).isEqualTo(Lyric.Extend.NONE);
            assertThat(follower.lyrics.get(1).extend()).isEqualTo(Lyric.Extend.NONE);
        }
    }

    // -----------------------------------------------------------------------
    // adjustNeighborsForLyricDeletion (Row 55)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AdjustNeighborsForLyricDeletion {

        // Two helper elements: predecessor at index 0, target at index 1.
        private StaffElement predecessor;
        private StaffElement target;

        @BeforeEach
        void addElements() {
            predecessor = new StaffElement(ElementType.QUAVER);
            target = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(predecessor);
                line.addElement(target);
            });
        }

        private void addLyric(StaffElement element, Lyric.Syllabic syllabic, Lyric.Extend extend) {
            var text = (syllabic != null && syllabic != Lyric.Syllabic.END
                        && syllabic != Lyric.Syllabic.MIDDLE) ? "x" : "";
            element.lyrics.add(new Lyric(VERSE, text, extend, syllabic, false));
        }

        @Test
        void testPredecessorWordContinuingBecomesEndWhenNoFollowingTextBearing() {
            // predecessor has BEGIN (word-continuing) and target (at index 1) will have lyric cleared.
            // With no following text-bearing lyric, predecessor must become SINGLE.
            addLyric(predecessor, Lyric.Syllabic.BEGIN, Lyric.Extend.NONE);
            // target gets a lyric that we'll clear before calling adjustNeighbors
            addLyric(target, Lyric.Syllabic.END, Lyric.Extend.NONE);
            // Clear target's lyric (simulate post-deletion state)
            target.lyrics.clear();

            song.withModification(() -> line.adjustNeighborsForLyricDeletion(1, VERSE));

            assertThat(predecessor.lyrics.getFirst().syllabic())
                .as("BEGIN predecessor with no following text-bearing lyric should become SINGLE")
                .isEqualTo(Lyric.Syllabic.SINGLE);
        }

        @Test
        void testNoPredecessorFixesSuccessorWhenLacksContinuingPredecessor() {
            // No predecessor (deleted index 0). The successor at index 1 has MIDDLE;
            // with no continuing predecessor it should become BEGIN.
            addLyric(target, Lyric.Syllabic.MIDDLE, Lyric.Extend.NONE);

            song.withModification(() -> line.adjustNeighborsForLyricDeletion(0, VERSE));

            assertThat(target.lyrics.getFirst().syllabic())
                .as("successor MIDDLE without a continuing predecessor should become BEGIN")
                .isEqualTo(Lyric.Syllabic.BEGIN);
        }

        @Test
        void testPredecessorContinueExtendBecomesStop() {
            // When predecessor's lyric has extend=CONTINUE, it is a carrier whose chain is
            // broken by the deletion; adjustNeighbors must change its extend to STOP.
            predecessor.lyrics.add(new Lyric(VERSE, "", Lyric.Extend.CONTINUE, null, false));
            target.lyrics.add(new Lyric(VERSE, "", Lyric.Extend.STOP, null, false));
            // Clear target lyric to simulate deletion
            target.lyrics.clear();

            song.withModification(() -> line.adjustNeighborsForLyricDeletion(1, VERSE));

            assertThat(predecessor.lyrics.getFirst().extend())
                .as("predecessor CONTINUE should become STOP when the deleted element was its stop")
                .isEqualTo(Lyric.Extend.STOP);
        }
    }

    // -----------------------------------------------------------------------
    // applyChange runs mutator directly when tracking suspended (Row 58)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ApplyChangeSuspended {

        @Test
        void testApplyChangeRunsMutatorWhenTrackingSuspended() {
            // When withoutMutationTracking is active, applyChange must run the mutator
            // directly without requiring or opening a modification bracket, and must
            // post no SongDidChangeNotification.
            var element = new StaffElement(ElementType.QUAVER);
            var modifiedDotCount = 2;
            song.withoutMutationTracking(() -> line.addElement(element));

            song.withoutMutationTracking(() ->
                line.applyChange(
                    new LineDeletion(0, line),   // mutation record is irrelevant — never recorded
                    () -> element.setDotCount(modifiedDotCount)));

            // The mutator ran: dot count must reflect the change.
            assertThat(element.getDotCount())
                .as("mutator should have run even though tracking is suspended")
                .isEqualTo(modifiedDotCount);

            // No notification must have been posted.
            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }
    }

    // -----------------------------------------------------------------------
    // backfillSyllabic (Row 53)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BackfillSyllabic {

        @Test
        void testBackfillCorrectsStaleSingleToEnd() {
            // Legacy load assigned SINGLE to a mid-word syllable whose predecessor has BEGIN.
            // backfillSyllabic must correct it to END (prevContinues=true, thisContinues=false).
            var first = new StaffElement(ElementType.QUAVER);
            var second = new StaffElement(ElementType.QUAVER);
            first.lyrics.add(new Lyric(VERSE, "hel", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            // stale: was SINGLE but the chain says this is the end of a word
            second.lyrics.add(new Lyric(VERSE, "lo", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
            song.withoutMutationTracking(() -> {
                line.addElement(first);
                line.addElement(second);
            });

            line.backfillSyllabic();

            assertThat(second.lyrics.getFirst().syllabic())
                .as("stale SINGLE after a BEGIN predecessor should be corrected to END")
                .isEqualTo(Lyric.Syllabic.END);
        }

        @Test
        void testBackfillIdempotentWhenAlreadyConsistent() {
            // A correctly-tagged two-syllable word: BEGIN + END. backfillSyllabic should leave
            // them unchanged (no mutation).
            var first = new StaffElement(ElementType.QUAVER);
            var second = new StaffElement(ElementType.QUAVER);
            first.lyrics.add(new Lyric(VERSE, "hel", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            second.lyrics.add(new Lyric(VERSE, "lo", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            song.withoutMutationTracking(() -> {
                line.addElement(first);
                line.addElement(second);
            });

            line.backfillSyllabic();

            assertThat(first.lyrics.getFirst().syllabic()).isEqualTo(Lyric.Syllabic.BEGIN);
            assertThat(second.lyrics.getFirst().syllabic()).isEqualTo(Lyric.Syllabic.END);
        }

        @Test
        void testBackfillCorrectsStaleSingleToMiddle() {
            // Three-syllable word: BEGIN — BEGIN (stale) — END. The middle element should
            // be MIDDLE (prevContinues=true, thisContinues=true).
            var first = new StaffElement(ElementType.QUAVER);
            var middle = new StaffElement(ElementType.QUAVER);
            var last = new StaffElement(ElementType.QUAVER);
            first.lyrics.add(new Lyric(VERSE, "a", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            // stale: BEGIN was a best-guess load marker for "continues"
            middle.lyrics.add(new Lyric(VERSE, "b", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            last.lyrics.add(new Lyric(VERSE, "c", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            song.withoutMutationTracking(() -> {
                line.addElement(first);
                line.addElement(middle);
                line.addElement(last);
            });

            line.backfillSyllabic();

            assertThat(middle.lyrics.getFirst().syllabic())
                .as("stale BEGIN in the interior of a word should be corrected to MIDDLE")
                .isEqualTo(Lyric.Syllabic.MIDDLE);
        }
    }

    // -----------------------------------------------------------------------
    // changeElementSpacingRatio fires LineLayoutChange (Row 47)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ChangeElementSpacingRatio {

        private static final float EXPAND_RATIO = 1.5f;

        @Test
        void testChangeElementSpacingRatioFiresLineLayoutChange() {
            var initialRatio = line.getElementSpacingRatio();
            var expectedNewRatio = initialRatio * EXPAND_RATIO;

            song.withModification(() -> line.changeElementSpacingRatio(EXPAND_RATIO));

            var notification = captureSingleDidChange();
            var layoutChange = findSingleMutationOfType(notification, LineLayoutChange.class);
            assertThat(layoutChange.line()).isSameAs(line);
            assertThat(layoutChange.field()).isEqualTo(LineLayoutField.ELEMENT_SPACING_RATIO);
            assertThat((Float) layoutChange.oldValue()).isEqualTo(initialRatio);
            assertThat((Float) layoutChange.newValue()).isEqualTo(expectedNewRatio);
        }

        @Test
        void testChangeElementSpacingRatioAccumulatesWithInitialRatio() {
            // Calling twice: new ratio = initial * EXPAND_RATIO * EXPAND_RATIO
            var initialRatio = line.getElementSpacingRatio();
            var afterFirstChange = initialRatio * EXPAND_RATIO;
            var afterSecondChange = afterFirstChange * EXPAND_RATIO;

            song.withModification(() -> line.changeElementSpacingRatio(EXPAND_RATIO));
            // Reset mock capture so second call's notification is isolated
            messageCenterMock.reset();
            song.withModification(() -> line.changeElementSpacingRatio(EXPAND_RATIO));

            var notification = captureSingleDidChange();
            var layoutChange = findSingleMutationOfType(notification, LineLayoutChange.class);
            assertThat((Float) layoutChange.oldValue()).isEqualTo(afterFirstChange);
            assertThat((Float) layoutChange.newValue()).isEqualTo(afterSecondChange);
        }
    }

    // -----------------------------------------------------------------------
    // findRangeElementsAt (Row 60)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FindRangeElementsAt {

        @Test
        void testFindRangeElementsAtReturnsElementsCoveringIndex() {
            // Build a line with two notes and a tie spanning them.
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
            });
            var tie = new Tie(e0, e1);
            song.withoutMutationTracking(() -> line.addRangeElement(tie));

            // Index 0 is covered by the tie (anchor).
            assertThat(line.findRangeElementsAt(0)).containsExactly(tie);
        }

        @Test
        void testFindRangeElementsAtReturnsEmptyForUncoveredIndex() {
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            var e2 = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
                line.addElement(e2);
            });
            // Tie only spans e0–e1 (indices 0–1); index 2 is outside the span.
            var tie = new Tie(e0, e1);
            song.withoutMutationTracking(() -> line.addRangeElement(tie));

            assertThat(line.findRangeElementsAt(2)).isEmpty();
        }

        @Test
        void testFindRangeElementsAtReturnsMultipleOverlappingElements() {
            // Two ties that both cover index 1: tie1 spans 0–2, tie2 spans 1–2.
            var e0 = new StaffElement(ElementType.QUAVER);
            var e1 = new StaffElement(ElementType.QUAVER);
            var e2 = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> {
                line.addElement(e0);
                line.addElement(e1);
                line.addElement(e2);
            });
            var tie1 = new Tie(e0, e2);
            var tie2 = new Tie(e1, e2);
            song.withoutMutationTracking(() -> {
                line.addRangeElement(tie1);
                line.addRangeElement(tie2);
            });

            assertThat(line.findRangeElementsAt(1))
                .as("index 1 should be covered by both tie1 (0–2) and tie2 (1–2)")
                .containsExactlyInAnyOrder(tie1, tie2);
        }
    }

    // -----------------------------------------------------------------------
    // hasEndingInvalidatedByDeletion pre-flight check (Row 59)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasEndingInvalidatedByDeletion {

        @Test
        void testReturnsTrueWhenAnchorIsDeleted() {
            var fixture = EndingLineFixture.primary(song);
            var anchor = fixture.anchor();
            var ending = fixture.ending();

            // Deleting the anchor element must trigger invalidation.
            assertThat(line.hasEndingInvalidatedByDeletion(List.of(anchor)))
                .as("deleting the anchor element should invalidate the ending")
                .isTrue();
        }

        @Test
        void testReturnsTrueWhenEndIsDeleted() {
            var fixture = EndingLineFixture.primary(song);
            var end = fixture.end();

            assertThat(line.hasEndingInvalidatedByDeletion(List.of(end)))
                .as("deleting the end element should invalidate the ending")
                .isTrue();
        }

        @Test
        void testReturnsFalseWhenNonBoundaryNoteIsDeleted() {
            var fixture = EndingLineFixture.primary(song);
            var note1 = fixture.note1();

            // Deleting a note inside the first sub-span (not all content) must not invalidate.
            assertThat(line.hasEndingInvalidatedByDeletion(List.of(note1)))
                .as("deleting one interior note should not invalidate the ending")
                .isFalse();
        }

        @Test
        void testReturnsTrueWhenAllFirstSubSpanContentIsDeleted() {
            var fixture = EndingLineFixture.primary(song);
            var note1 = fixture.note1();
            var note2 = fixture.note2();

            // Deleting both content notes in the first sub-span empties it — invalidation.
            assertThat(line.hasEndingInvalidatedByDeletion(List.of(note1, note2)))
                .as("deleting all content in the first sub-span should invalidate the ending")
                .isTrue();
        }

        @Test
        void testReturnsFalseForEmptyDeletionList() {
            EndingLineFixture.primary(song);

            assertThat(line.hasEndingInvalidatedByDeletion(List.of()))
                .as("an empty deletion list must not invalidate any ending")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // hasEndingInvalidatedByInsertion pre-flight check (Row 59)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasEndingInvalidatedByInsertion {

        @Test
        void testReturnsTrueWhenBarlineInsertedInInterior() {
            EndingLineFixture.primary(song);
            // Inserting a barline at index 2 (interior of first sub-span) must invalidate.
            assertThat(line.hasEndingInvalidatedByInsertion(2, ElementType.SINGLE_BARLINE))
                .as("inserting a barline inside the first sub-span should invalidate the ending")
                .isTrue();
        }

        @Test
        void testReturnsFalseWhenNoteInsertedInInterior() {
            EndingLineFixture.primary(song);
            // Non-barline insertions never invalidate an ending.
            assertThat(line.hasEndingInvalidatedByInsertion(2, ElementType.CROTCHET))
                .as("inserting a note inside the span should not invalidate the ending")
                .isFalse();
        }

        @Test
        void testReturnsFalseWhenBarlineInsertedOutsideSpan() {
            EndingLineFixture.primary(song);
            // Anchor is at index 0; inserting before it (index 0) is not inside.
            assertThat(line.hasEndingInvalidatedByInsertion(0, ElementType.SINGLE_BARLINE))
                .as("inserting before the anchor should not invalidate the ending")
                .isFalse();
        }

        @Test
        void testReturnsFalseWhenNoEndingsPresent() {
            // A fresh line with no range elements: insertion of any type is fine.
            assertThat(line.hasEndingInvalidatedByInsertion(0, ElementType.SINGLE_BARLINE))
                .as("no endings registered means no invalidation possible")
                .isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // setSyllableBoundary (Row 54)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetSyllableBoundary {

        private StaffElement first;
        private StaffElement second;

        @BeforeEach
        void addElements() {
            first = new StaffElement(ElementType.QUAVER);
            second = new StaffElement(ElementType.QUAVER);
            // first is a word-start; second will be the target element
            first.lyrics.add(new Lyric(VERSE, "hel", Lyric.Extend.NONE, Lyric.Syllabic.BEGIN, false));
            second.lyrics.add(new Lyric(VERSE, "lo", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            song.withoutMutationTracking(() -> {
                line.addElement(first);
                line.addElement(second);
            });
        }

        @Test
        void testWordEndOnPredecessorContinuingProducesEnd() {
            // first=BEGIN (continues), isWordEnd=true → second should become END.
            // The setup already has second=END from the predecessor BEGIN context, so
            // no-op path: verify it is still END after the call.
            song.withModification(() -> line.setSyllableBoundary(1, VERSE, true, false));

            assertThat(second.lyrics.getFirst().syllabic())
                .as("isWordEnd=true with continuing predecessor should yield END")
                .isEqualTo(Lyric.Syllabic.END);
        }

        @Test
        void testNotWordEndOnPredecessorContinuingProducesMiddle() {
            // first=BEGIN (continues), isWordEnd=false → second should become MIDDLE.
            // Change second to have SINGLE (stale) before the call so the mutation fires.
            song.withoutMutationTracking(() ->
                second.lyrics.set(0, new Lyric(VERSE, "lo", Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false)));

            song.withModification(() -> line.setSyllableBoundary(1, VERSE, false, false));

            assertThat(second.lyrics.getFirst().syllabic())
                .as("isWordEnd=false with continuing predecessor should yield MIDDLE")
                .isEqualTo(Lyric.Syllabic.MIDDLE);
        }

        @Test
        void testSuccessorSyllabicAdjustedAfterBoundaryChange() {
            // Arrange a three-element word: first=BEGIN, second=MIDDLE, third=END.
            // Mark index 1 (second) as isWordEnd=true, closing the word there.
            // second becomes END (prevContinues=true, isWordEnd=true).
            // fixSuccessorSyllabic then receives predecessorContinues=false, so
            // third (which was END) → deriveSyllabic(false, false) = SINGLE.
            var third = new StaffElement(ElementType.QUAVER);
            third.lyrics.add(new Lyric(VERSE, "c", Lyric.Extend.NONE, Lyric.Syllabic.END, false));
            song.withoutMutationTracking(() -> line.addElement(third));

            // Set second to MIDDLE (continues), so marking it as word-end fires a real change.
            song.withoutMutationTracking(() ->
                second.lyrics.set(0, new Lyric(VERSE, "lo", Lyric.Extend.NONE, Lyric.Syllabic.MIDDLE, false)));

            song.withModification(() -> line.setSyllableBoundary(1, VERSE, true, false));

            assertThat(second.lyrics.getFirst().syllabic())
                .as("index 1 with isWordEnd=true should become END")
                .isEqualTo(Lyric.Syllabic.END);
            assertThat(third.lyrics.getFirst().syllabic())
                .as("successor END whose predecessor is now non-continuing should become SINGLE")
                .isEqualTo(Lyric.Syllabic.SINGLE);
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

        return didChanges.getFirst();
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

        return matches.getFirst();
    }

    private StaffElement makeExtendElement(Lyric.Extend extend) {
        var element = new StaffElement(ElementType.QUAVER);
        var text = extend == Lyric.Extend.START ? "x" : "";
        var syllabic = (extend == Lyric.Extend.STOP || extend == Lyric.Extend.CONTINUE) ? null : Lyric.Syllabic.SINGLE;
        element.lyrics.add(new Lyric(VERSE, text, extend, syllabic, false));
        return element;
    }

    private Lyric.Extend extendOf(StaffElement element) {
        return element.lyrics.getFirst().extend();
    }

    private void addExtendChain(StaffElement... elements) {
        song.withoutMutationTracking(() -> {
            for (var element : elements) {
                line.addElement(element);
            }
            line.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
        });
    }

    // -----------------------------------------------------------------------
    // keyExists (Row 43)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class KeyExists {

        // FLAT_SHARP_ORDINAL[FLATS][0] = 0 (B flat)
        private static final int FLAT_PITCH_B = 0;
        // FLAT_SHARP_ORDINAL[FLATS][1] = 3 (E flat)
        private static final int FLAT_PITCH_E = 3;
        // FLAT_SHARP_ORDINAL[SHARPS][0] = 4 (F sharp)
        private static final int SHARP_PITCH_F = 4;
        // FLAT_SHARP_ORDINAL[SHARPS][1] = 1 (C sharp)
        private static final int SHARP_PITCH_C = 1;
        // A pitch that is never in any 1-accidental key
        private static final int UNACCIDENTALIZED_PITCH_D = 2;

        @BeforeEach
        void resetKeySignature() {
            // A fresh Song initializes line 0 with 5 flats. Reset to no key so each
            // test starts from a known null/0 state without firing tracked mutations.
            song.withoutMutationTracking(() -> {
                line.setKeyType(null);
                line.setKeyAccidentalCount(0);
            });
        }

        @Test
        void testKeyExistsReturnsFalseWhenKeyTypeIsNull() {
            // keyType is null after reset — no accidental matches any pitch.
            assertThat(line.keyExists(FLAT_PITCH_B)).isFalse();
        }

        @Test
        void testKeyExistsForFlatPitchInFlatKey() {
            // 1-flat key adds a B-flat; pitch 0 (B) must be found.
            song.withoutMutationTracking(() -> {
                line.setKeyType(KeyType.FLATS);
                line.setKeyAccidentalCount(1);
            });
            assertThat(line.keyExists(FLAT_PITCH_B)).isTrue();
        }

        @Test
        void testKeyExistsForAbsentFlatPitchInOneFlatKey() {
            // 1-flat key only contains B-flat; E-flat (ordinal 3) must not be found.
            song.withoutMutationTracking(() -> {
                line.setKeyType(KeyType.FLATS);
                line.setKeyAccidentalCount(1);
            });
            assertThat(line.keyExists(FLAT_PITCH_E)).isFalse();
        }

        @Test
        void testKeyExistsForFlatPitchInTwoFlatKey() {
            // 2-flat key adds B-flat and E-flat; both pitches must be found.
            var twoFlats = 2;
            song.withoutMutationTracking(() -> {
                line.setKeyType(KeyType.FLATS);
                line.setKeyAccidentalCount(twoFlats);
            });
            assertThat(line.keyExists(FLAT_PITCH_B)).isTrue();
            assertThat(line.keyExists(FLAT_PITCH_E)).isTrue();
        }

        @Test
        void testKeyExistsForSharpPitchInSharpKey() {
            // 1-sharp key adds an F-sharp; pitch 4 (F) must be found.
            song.withoutMutationTracking(() -> {
                line.setKeyType(KeyType.SHARPS);
                line.setKeyAccidentalCount(1);
            });
            assertThat(line.keyExists(SHARP_PITCH_F)).isTrue();
        }

        @Test
        void testKeyExistsForAbsentSharpPitchInOneSharpKey() {
            // 1-sharp key only contains F-sharp; C-sharp (ordinal 1) must not be found.
            song.withoutMutationTracking(() -> {
                line.setKeyType(KeyType.SHARPS);
                line.setKeyAccidentalCount(1);
            });
            assertThat(line.keyExists(SHARP_PITCH_C)).isFalse();
        }

        @Test
        void testKeyExistsForSharpPitchInTwoSharpKey() {
            // 2-sharp key adds F-sharp and C-sharp; both pitches must be found.
            var twoSharps = 2;
            song.withoutMutationTracking(() -> {
                line.setKeyType(KeyType.SHARPS);
                line.setKeyAccidentalCount(twoSharps);
            });
            assertThat(line.keyExists(SHARP_PITCH_F)).isTrue();
            assertThat(line.keyExists(SHARP_PITCH_C)).isTrue();
        }

        @Test
        void testKeyExistsReturnsFalseForUnaccidentalizedPitch() {
            // Pitch D (ordinal 2) does not appear in a 1-sharp or 1-flat key.
            song.withoutMutationTracking(() -> {
                line.setKeyType(KeyType.SHARPS);
                line.setKeyAccidentalCount(1);
            });
            assertThat(line.keyExists(UNACCIDENTALIZED_PITCH_D)).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // setKeyAccidentalCount — fires LineKeyChange; no-op when unchanged (Row 44)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetKeyAccidentalCount {

        @BeforeEach
        void resetKeySignature() {
            // Reset to a known 0/null state so tests are independent of Song's defaults.
            song.withoutMutationTracking(() -> {
                line.setKeyType(null);
                line.setKeyAccidentalCount(0);
            });
        }

        @Test
        void testSetKeyAccidentalCountFiresLineKeyChange() {
            var initialCount = 0;
            var newCount = 2;
            song.withModification(() -> line.setKeyAccidentalCount(newCount));

            var notification = captureSingleDidChange();
            var keyChange = findSingleMutationOfType(notification, LineKeyChange.class);
            assertThat(keyChange.line()).isSameAs(line);
            assertThat(keyChange.field()).isEqualTo(KeyField.ACCIDENTAL_COUNT);
            assertThat(keyChange.oldValue()).isEqualTo(initialCount);
            assertThat(keyChange.newValue()).isEqualTo(newCount);
        }

        @Test
        void testSetKeyAccidentalCountIsNoOpWhenUnchanged() {
            // Setting the count to the current value (0) must post no notification.
            var unchanged = 0;
            song.withModification(() -> line.setKeyAccidentalCount(unchanged));

            // No SongDidChangeNotification should have been posted.
            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }

        @Test
        void testSetKeyAccidentalCountUpdatesCount() {
            var newCount = 3;
            song.withModification(() -> line.setKeyAccidentalCount(newCount));
            assertThat(line.getKeyAccidentalCount()).isEqualTo(newCount);
        }
    }

    // -----------------------------------------------------------------------
    // setKeyType — fires LineKeyChange (Row 45)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SetKeyType {

        @BeforeEach
        void resetKeySignature() {
            // Reset to null so tests are independent of Song's 5-flat default.
            song.withoutMutationTracking(() -> line.setKeyType(null));
        }

        @Test
        void testSetKeyTypeFiresLineKeyChange() {
            // Initial keyType is null (after reset); setting to FLATS must fire a LineKeyChange.
            song.withModification(() -> line.setKeyType(KeyType.FLATS));

            var notification = captureSingleDidChange();
            var keyChange = findSingleMutationOfType(notification, LineKeyChange.class);
            assertThat(keyChange.line()).isSameAs(line);
            assertThat(keyChange.field()).isEqualTo(KeyField.KEY_TYPE);
            assertThat(keyChange.oldValue()).isNull();
            assertThat(keyChange.newValue()).isEqualTo(KeyType.FLATS);
        }

        @Test
        void testSetKeyTypeIsNoOpWhenUnchanged() {
            // keyType is null after reset; setting it to null again must post nothing.
            song.withModification(() -> line.setKeyType(null));

            messageCenterMock.verify(() -> MessageCenter.post(any()), times(0));
        }

        @Test
        void testSetKeyTypeUpdatesKeyType() {
            song.withModification(() -> line.setKeyType(KeyType.SHARPS));
            assertThat(line.getKeyType()).isEqualTo(KeyType.SHARPS);
        }

        @Test
        void testSetKeyTypeRecordsOldValue() {
            // Set to FLATS without tracking, then change to SHARPS — old value must be FLATS.
            song.withoutMutationTracking(() -> line.setKeyType(KeyType.FLATS));
            song.withModification(() -> line.setKeyType(KeyType.SHARPS));

            var notification = captureSingleDidChange();
            var keyChange = findSingleMutationOfType(notification, LineKeyChange.class);
            assertThat(keyChange.oldValue()).isEqualTo(KeyType.FLATS);
            assertThat(keyChange.newValue()).isEqualTo(KeyType.SHARPS);
        }
    }

    // -----------------------------------------------------------------------
    // attachInitialTempoIfNeeded (Row 46)
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AttachInitialTempoIfNeeded {

        @Test
        void testAttachInitialTempoAddsTempoToFirstElement() {
            // A line with at least one element and no existing tempo attachment must gain one.
            var element = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> line.addElement(element));

            line.attachInitialTempoIfNeeded();

            assertThat(element.findAttachment(TempoChangeAttachment.class))
                .as("first element should have a TempoChangeAttachment after call")
                .isNotNull();
        }

        @Test
        void testAttachInitialTempoIsNoOpWhenAlreadyAttached() {
            // If the first element already has a TempoChangeAttachment, calling again
            // must not add a second one.
            var tempo = song.getTempo();
            if (tempo == null) {
                throw new AssertionError("fresh Song should always have a non-null tempo");
            }

            var element = new StaffElement(ElementType.QUAVER);
            song.withoutMutationTracking(() -> line.addElement(element));
            element.addAttachment(new TempoChangeAttachment(element, tempo));

            line.attachInitialTempoIfNeeded();

            // Exactly one TempoChangeAttachment must be present — not two.
            var attachments = element.getAttachments().stream()
                .filter(a -> a instanceof TempoChangeAttachment)
                .toList();
            assertThat(attachments)
                .as("must not add a second TempoChangeAttachment when one already exists")
                .hasSize(1);
        }

        @Test
        void testAttachInitialTempoIsNoOpWhenLineIsEmpty() {
            // An empty line must not throw; the early-return guard must fire.
            // A fresh Line has no elements by default (before any addElement call).
            var emptyLine = new Line(song);
            org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(emptyLine::attachInitialTempoIfNeeded);
        }
    }

    // -----------------------------------------------------------------------
    // LineConstructorInvariants
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LineConstructorInvariants {

        @Test
        void testSongIsNonNullImmediatelyAfterConstruction() {
            var testSong = new Song();
            var testLine = new Line(testSong);
            assertThat(testLine.getSong()).isSameAs(testSong);
        }

        @Test
        void testApplyChangeThrowsWhenNotInBracket() {
            assertThatIllegalStateException().isThrownBy(() ->
                line.applyChange(new LineDeletion(0, line), () -> {})
            );
        }
    }
}
