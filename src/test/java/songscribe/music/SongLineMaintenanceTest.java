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
package songscribe.music;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.notification.SongDidChangeNotification;

/**
 * Verifies that {@link Song#addLine} and {@link Song#removeLine} auto-maintain
 * the terminal invariant: whenever the last line of the song changes, the new last
 * line ends in a valid terminal ({@code FINAL_DOUBLE_BARLINE} or {@code REPEAT_RIGHT}) and
 * no other line does. All maintenance mutations
 * coalesce with the triggering {@link LineInsertion} / {@link LineDeletion} into a single
 * {@link SongDidChangeNotification}.
 */
class SongLineMaintenanceTest extends UnitTest {

    private Song song;
    private Line initialLine;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        song = new Song();
        initialLine = song.getLine(0);
        messageCenterMock = mockStatic(MessageCenter.class);
    }

    @AfterEach
    void tearDown() {
        messageCenterMock.close();
    }

    // -----------------------------------------------------------------------
    // addLine — appending as the new last line transfers the final barline
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class AppendAsNewLast {

        @Test
        void testAppendEmptyLineRemovesPrevFinalAndInsertsOnNewLast() {
            var newLast = new Line(song);
            song.addLine(1, newLast);

            var notification = captureSingleDidChange();

            // Prev last (initialLine) had a FINAL — expect one ElementDeletion targeting it.
            var deletion = singleMutationOfType(notification, ElementDeletion.class);
            assertThat(deletion.line()).isSameAs(initialLine);
            assertThat(deletion.deletedElement().getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

            // New last is empty — FINAL is appended via ElementInsertion.
            var insertion = singleMutationOfType(notification, ElementInsertion.class);
            assertThat(insertion.line()).isSameAs(newLast);
            assertThat(insertion.element().getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

            // Invariant holds post-hoc.
            assertThat(initialLine.elementCount()).isZero();
            assertThat(newLast.elementCount()).isEqualTo(1);
            assertThat(newLast.getElement(0).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        @Test
        void testAppendLineEndingInOtherBarlineReplacesWithFinal() {
            var newLast = new Line(song);
            var trailingBarline = new StaffElement(ElementType.DOUBLE_BARLINE);
            song.withoutMutationTracking(() -> {
                newLast.addElement(new StaffElement(ElementType.CROTCHET));
                newLast.addElement(trailingBarline);
            });

            song.addLine(1, newLast);

            var notification = captureSingleDidChange();

            // Prev last's FINAL is deleted.
            var deletion = singleMutationOfType(notification, ElementDeletion.class);
            assertThat(deletion.line()).isSameAs(initialLine);

            // New last's trailing DOUBLE_BARLINE is replaced with FINAL.
            var replacement = singleMutationOfType(notification, ElementReplacement.class);
            assertThat(replacement.line()).isSameAs(newLast);
            assertThat(replacement.oldElement()).isSameAs(trailingBarline);
            assertThat(replacement.newElement().getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

            // No ElementInsertion for the FINAL — replacement, not append.
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementInsertion ei
                && ei.element().getType() == ElementType.FINAL_DOUBLE_BARLINE);

            assertThat(newLast.getElement(newLast.elementCount() - 1).getType())
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        @Test
        void testAppendLineEndingInNoteAppendsFinal() {
            var newLast = new Line(song);
            var trailingNote = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> newLast.addElement(trailingNote));

            song.addLine(1, newLast);

            var notification = captureSingleDidChange();

            // FINAL appended via ElementInsertion targeting newLast at index 1.
            var insertions = notification.getMutations().stream()
                .filter(m -> m instanceof ElementInsertion ei
                    && ei.element().getType() == ElementType.FINAL_DOUBLE_BARLINE)
                .map(m -> (ElementInsertion) m)
                .toList();
            assertThat(insertions).hasSize(1);
            assertThat(insertions.getFirst().line()).isSameAs(newLast);
            assertThat(insertions.getFirst().index()).isEqualTo(1);

            // Trailing note is still there, followed by FINAL.
            assertThat(newLast.elementCount()).isEqualTo(2);
            assertThat(newLast.getElement(0)).isSameAs(trailingNote);
            assertThat(newLast.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        @Test
        void testAppendLineAlreadyEndingInFinalTransfersWithoutDuplication() {
            var newLast = new Line(song);
            song.withoutMutationTracking(
                () -> newLast.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))
            );

            song.addLine(1, newLast);

            var notification = captureSingleDidChange();

            // Prev last's FINAL is still deleted.
            var deletion = singleMutationOfType(notification, ElementDeletion.class);
            assertThat(deletion.line()).isSameAs(initialLine);

            // New last already ends in FINAL — no ElementInsertion / ElementReplacement for it.
            assertThat(notification.getMutations())
                .noneMatch(m -> m instanceof ElementReplacement);
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementInsertion ei
                && ei.element().getType() == ElementType.FINAL_DOUBLE_BARLINE);

            assertThat(newLast.elementCount()).isEqualTo(1);
            assertThat(newLast.getElement(0).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }
    }

    // -----------------------------------------------------------------------
    // addLine — inserting before the current last runs no maintenance
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertBeforeLast {

        @Test
        void testInsertBeforeLastDoesNotTouchInitialLine() {
            // Populate the initial (last) line beyond its final barline so we can assert
            // the final barline remains as the last element.
            song.withoutMutationTracking(() -> {
                // Put a note BEFORE the final barline. addElement(index, element) would
                // trip the guard check for FINAL's position, so insert at index 0 and
                // rely on the existing FINAL staying last by index shift.
                initialLine.addElement(0, new StaffElement(ElementType.CROTCHET));
            });
            var finalElement = initialLine.getElement(initialLine.elementCount() - 1);

            var inserted = new Line(song);
            song.addLine(0, inserted);

            var notification = captureSingleDidChange();

            // Exactly one LineInsertion for the triggering action.
            var lineInsertion = singleMutationOfType(notification, LineInsertion.class);
            assertThat(lineInsertion.line()).isSameAs(inserted);
            assertThat(lineInsertion.lineIndex()).isZero();

            // No element-level maintenance — initialLine still has its final barline
            // in the exact same slot.
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementDeletion);
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementReplacement);
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementInsertion);

            assertThat(song.getLine(song.lineCount() - 1)).isSameAs(initialLine);
            assertThat(initialLine.getElement(initialLine.elementCount() - 1)).isSameAs(finalElement);
        }
    }

    // -----------------------------------------------------------------------
    // removeLine — removing the last line migrates the barline to the penult
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RemoveLastLine {

        @Test
        void testRemovingLastWhenPenultEndsInNoteAppendsFinal() {
            // Configure initialLine to end in a note so the removeLine maintenance
            // exercises the append branch.
            var newLast = new Line(song);
            song.withoutMutationTracking(() -> {
                initialLine.removeElement(initialLine.elementCount() - 1);
                initialLine.addElement(new StaffElement(ElementType.CROTCHET));
                newLast.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
            });
            song.addLine(1, newLast);
            // Drain the addLine notification so the removeLine assertion below sees
            // only the removal's mutations.
            messageCenterMock.reset();

            song.removeLine(1);

            var notification = captureSingleDidChange();

            singleMutationOfType(notification, LineDeletion.class);

            var insertion = singleMutationOfType(notification, ElementInsertion.class);
            assertThat(insertion.line()).isSameAs(initialLine);
            assertThat(insertion.index()).isEqualTo(1);
            assertThat(insertion.element().getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

            assertThat(initialLine.elementCount()).isEqualTo(2);
            assertThat(initialLine.getElement(0).getType()).isEqualTo(ElementType.CROTCHET);
            assertThat(initialLine.getElement(1).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        @Test
        void testRemovingLastWhenPenultEndsInOtherBarlineReplacesWithFinal() {
            var newLast = new Line(song);
            song.withoutMutationTracking(() -> {
                // Replace initialLine's FINAL with CROTCHET + DOUBLE_BARLINE so the
                // removeLine maintenance exercises the replace branch.
                initialLine.removeElement(initialLine.elementCount() - 1);
                initialLine.addElement(new StaffElement(ElementType.CROTCHET));
                initialLine.addElement(new StaffElement(ElementType.DOUBLE_BARLINE));
                newLast.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE));
            });
            song.addLine(1, newLast);
            messageCenterMock.reset();

            song.removeLine(1);

            var notification = captureSingleDidChange();

            var replacement = singleMutationOfType(notification, ElementReplacement.class);
            assertThat(replacement.line()).isSameAs(initialLine);
            assertThat(replacement.oldElement().getType()).isEqualTo(ElementType.DOUBLE_BARLINE);
            assertThat(replacement.newElement().getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

            assertThat(initialLine.getElement(initialLine.elementCount() - 1).getType())
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }

        @Test
        void testRemovingLastWhenPenultAlreadyEndsInFinalIsNoOp() {
            // Arrange: two lines, both ending in FINAL. addLine(1, newLast) would strip
            // initialLine's FINAL during maintenance, so re-seed it under
            // withoutMutationTracking so the post-addLine state has FINAL on both lines.
            var newLast = new Line(song);
            song.withoutMutationTracking(
                () -> newLast.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))
            );
            song.addLine(1, newLast);
            song.withoutMutationTracking(
                () -> initialLine.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))
            );
            messageCenterMock.reset();

            song.removeLine(1);

            var notification = captureSingleDidChange();

            singleMutationOfType(notification, LineDeletion.class);
            // No element-level maintenance — initialLine already ends in FINAL.
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementInsertion);
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementReplacement);
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementDeletion);
        }
    }

    // -----------------------------------------------------------------------
    // removeLine — removing a non-last line runs no maintenance
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RemoveNonLastLine {

        @Test
        void testRemovingNonLastLineRunsNoMaintenance() {
            var newLast = new Line(song);
            song.withoutMutationTracking(
                () -> newLast.addElement(Song.newTerminalElement(ElementType.FINAL_DOUBLE_BARLINE))
            );
            song.addLine(1, newLast);
            messageCenterMock.reset();

            // Remove the first (non-last) line.
            song.removeLine(0);

            var notification = captureSingleDidChange();

            singleMutationOfType(notification, LineDeletion.class);
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementInsertion);
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementReplacement);
            assertThat(notification.getMutations()).noneMatch(m -> m instanceof ElementDeletion);

            // newLast is now the only line and still ends in FINAL.
            assertThat(song.lineCount()).isEqualTo(1);
            assertThat(song.getLine(0)).isSameAs(newLast);
            assertThat(newLast.getElement(newLast.elementCount() - 1).getType())
                .isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        }
    }

    // -----------------------------------------------------------------------
    // Guard bypass: maintenance-driven replacement must not throw.
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class GuardBypass {

        @Test
        void testAddLineRequiringReplacementDoesNotThrow() {
            // New line whose last element is DOUBLE_BARLINE — maintenance must issue
            // a setElement(…, FINAL_DOUBLE_BARLINE) that would otherwise trip the
            // Phase 1 guards. isInAutoMaintenance gates them so this succeeds.
            var newLast = new Line(song);
            song.withoutMutationTracking(() -> {
                newLast.addElement(new StaffElement(ElementType.CROTCHET));
                newLast.addElement(new StaffElement(ElementType.DOUBLE_BARLINE));
            });

            assertThatNoException().isThrownBy(() -> song.addLine(1, newLast));
        }
    }

    // -----------------------------------------------------------------------
    // Modified flag: user-driven line mutations dirty the document.
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ModifiedFlag {

        @Test
        void testAddLineDirtiesDocument() {
            assertThat(song.isModified()).isFalse();
            song.addLine(1, new Line(song));
            assertThat(song.isModified()).isTrue();
        }

        @Test
        void testRemoveLineDirtiesDocument() {
            song.addLine(1, new Line(song));
            song.setModified(false);

            song.removeLine(1);
            assertThat(song.isModified()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Single-step undo is pending issue #14 (undo grouping).
    // -----------------------------------------------------------------------

    @Test
    @Disabled("pending #14 — undo grouping collapses trigger + auto-maintenance into one step")
    void testCoalescedMutationsRevertInSingleUndoStep() {
        // Will exercise undo once Song.beginModification snapshots for undo grouping
        // (see TODO at Song.java beginModification).
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

    private <T> T singleMutationOfType(
        SongDidChangeNotification notification, Class<T> type
    ) {
        var matches = notification.getMutations().stream()
            .filter(type::isInstance)
            .map(type::cast)
            .toList();

        assertThat(matches)
            .as("expected exactly one %s mutation, got: %s",
                type.getSimpleName(), notification.getMutations())
            .hasSize(1);

        return matches.getFirst();
    }
}
