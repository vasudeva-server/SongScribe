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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.mutation.Mutation;
import songscribe.undo.UndoTestSupport;

/**
 * Unit tests for {@link Line#getElementIndex}, which answers from a lazily-built position
 * index invalidated when an element is attached to or detached from the line.
 *
 * <p>The one expected result every case here asserts: after any mutation,
 * {@code getElementIndex(e)} equals {@code e}'s true position in {@code getElements()} for
 * every element in the line, and -1 for every element that is not. A cache that is never
 * invalidated fails these; a cache that is invalidated too eagerly still passes, because
 * over-invalidation costs only a rebuild.
 *
 * <p>Every test mutates a line whose index has already been built — {@link #setUp} queries
 * it — so a missing invalidation shows up as a stale answer rather than being masked by a
 * first-query rebuild.
 *
 * <p>The line under test is {@code new Song()}'s initial line, which already carries the
 * song's auto-maintained terminal element. The terminal stays last: the {@code NOTE_COUNT}
 * crotchets added below occupy indices {@code [0, NOTE_COUNT)} and the terminal sits at
 * {@code NOTE_COUNT}.
 */
class LineElementIndexTest extends UnitTest {

    private static final int NOTE_COUNT = 6;
    private static final int TERMINAL_IDX = NOTE_COUNT;
    private static final int INSERT_IDX = 1;
    private static final int REPLACE_IDX = 2;
    private static final int REMOVE_IDX = 2;
    private static final int RANGE_FROM = 1;
    private static final int RANGE_TO = 3;
    private static final int NOT_IN_LINE = -1;

    private Song song;
    private Line line;

    @BeforeEach
    void setUp() {
        song = new Song();
        line = song.getLine(0);

        song.withoutMutationTracking(() -> {
            for (var i = 0; i < NOTE_COUNT; i++) {
                line.addElement(new StaffElement(ElementType.CROTCHET));
            }
        });

        // Builds the index, so the mutations under test run against a populated one.
        assertIndicesConsistent(line);
    }

    /** The cache never lies: every element resolves to its true list position. */
    private void assertIndicesConsistent(Line checkedLine) {
        var elements = checkedLine.getElements();

        for (var i = 0; i < elements.size(); i++) {
            assertThat(checkedLine.getElementIndex(elements.get(i)))
                .as("element at list position %d must resolve to that position", i)
                .isEqualTo(i);
        }
    }

    // -----------------------------------------------------------------------
    // The five element-mutation paths
    // -----------------------------------------------------------------------

    @Test
    void testAppendPlacesTheNewElementBeforeTheTerminal() {
        var terminal = line.getElement(TERMINAL_IDX);
        var appended = new StaffElement(ElementType.CROTCHET);

        song.withoutMutationTracking(() -> line.addElement(appended));

        assertAll(
            () -> assertThat(line.getElementIndex(appended))
                .as("the appended element takes the terminal's former position")
                .isEqualTo(TERMINAL_IDX),
            () -> assertThat(line.getElementIndex(terminal))
                .as("the terminal shifted one position right")
                .isEqualTo(TERMINAL_IDX + 1)
        );
        assertIndicesConsistent(line);
    }

    @Test
    void testInsertShiftsLaterIndices() {
        var shifted = line.getElement(INSERT_IDX);
        var inserted = new StaffElement(ElementType.CROTCHET);

        song.withoutMutationTracking(() -> line.addElement(INSERT_IDX, inserted));

        assertAll(
            () -> assertThat(line.getElementIndex(inserted))
                .as("the inserted element lands at the insertion index")
                .isEqualTo(INSERT_IDX),
            () -> assertThat(line.getElementIndex(shifted))
                .as("the element it displaced shifted one position right")
                .isEqualTo(INSERT_IDX + 1)
        );
        assertIndicesConsistent(line);
    }

    @Test
    void testSetElementMovesThePositionToTheReplacement() {
        var replaced = line.getElement(REPLACE_IDX);
        var replacement = new StaffElement(ElementType.CROTCHET);

        song.withoutMutationTracking(() -> line.setElement(REPLACE_IDX, replacement));

        assertAll(
            () -> assertThat(line.getElementIndex(replacement))
                .as("the replacement holds the replaced element's position")
                .isEqualTo(REPLACE_IDX),
            () -> assertThat(line.getElementIndex(replaced))
                .as("the replaced element is no longer in the line")
                .isEqualTo(NOT_IN_LINE)
        );
        assertIndicesConsistent(line);
    }

    @Test
    void testRemoveElementShiftsLaterIndices() {
        var removed = line.getElement(REMOVE_IDX);
        var following = line.getElement(REMOVE_IDX + 1);

        song.withoutMutationTracking(() -> line.removeElement(REMOVE_IDX));

        assertAll(
            () -> assertThat(line.getElementIndex(removed))
                .as("the removed element is no longer in the line")
                .isEqualTo(NOT_IN_LINE),
            () -> assertThat(line.getElementIndex(following))
                .as("the element after it moved into the vacated position")
                .isEqualTo(REMOVE_IDX)
        );
        assertIndicesConsistent(line);
    }

    @Test
    void testRemoveRangeShiftsLaterIndices() {
        // removeRange writes through a sublist view, which a naive invalidation misses.
        var removedElements = List.copyOf(line.getElements(RANGE_FROM, RANGE_TO));
        var following = line.getElement(RANGE_TO + 1);

        song.withoutMutationTracking(() -> line.removeRange(RANGE_FROM, RANGE_TO));

        assertAll(
            () -> assertThat(removedElements.stream().map(line::getElementIndex))
                .as("no removed element is still in the line")
                .allMatch(index -> index == NOT_IN_LINE),
            () -> assertThat(line.getElementIndex(following))
                .as("the first survivor moved down by the size of the removed range")
                .isEqualTo(RANGE_FROM)
        );
        assertIndicesConsistent(line);
    }

    @Test
    void testDuplicatedElementResolvesToItsEarliestPosition() {
        // The index is built with putIfAbsent so a repeated element answers with its first
        // position, matching the list scan this replaced; a forward put would answer with its
        // last. Ordinary editing cannot produce a duplicate, which is why nothing else here
        // covers it — and why one word could silently reverse the rule.
        //
        // Deliberately not followed by assertIndicesConsistent: the whole point is that the
        // later occurrence does not resolve to its own position.
        var repeated = line.getElement(REPLACE_IDX);

        song.withoutMutationTracking(() -> line.addElement(repeated));

        assertAll(
            () -> assertThat(line.getElements().get(TERMINAL_IDX))
                .as("the duplicate really is in the line a second time")
                .isSameAs(repeated),
            () -> assertThat(line.getElementIndex(repeated))
                .as("a repeated element resolves to its earliest position, not its latest")
                .isEqualTo(REPLACE_IDX)
        );
    }

    // -----------------------------------------------------------------------
    // Undo/redo replay through the same paths
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ReplayedMutations {

        @Test
        void testUndoAndRedoOfAnInsertKeepIndicesConsistent() {
            var shifted = line.getElement(INSERT_IDX);
            var inserted = new StaffElement(ElementType.CROTCHET);
            var batch = captureBatch(() -> line.addElement(INSERT_IDX, inserted));

            assertIndicesConsistent(line);

            replayUndo(batch);
            assertAll(
                () -> assertThat(line.getElementIndex(inserted))
                    .as("undo took the inserted element back out of the line")
                    .isEqualTo(NOT_IN_LINE),
                () -> assertThat(line.getElementIndex(shifted))
                    .as("undo returned the displaced element to its original position")
                    .isEqualTo(INSERT_IDX)
            );
            assertIndicesConsistent(line);

            replayRedo(batch);
            assertAll(
                () -> assertThat(line.getElementIndex(inserted))
                    .as("redo put the inserted element back at the insertion index")
                    .isEqualTo(INSERT_IDX),
                () -> assertThat(line.getElementIndex(shifted))
                    .as("redo shifted the displaced element right again")
                    .isEqualTo(INSERT_IDX + 1)
            );
            assertIndicesConsistent(line);
        }

        @Test
        void testUndoAndRedoOfARangeDeletionKeepIndicesConsistent() {
            var removedElements = List.copyOf(line.getElements(RANGE_FROM, RANGE_TO));
            var following = line.getElement(RANGE_TO + 1);
            var batch = captureBatch(() -> line.removeRange(RANGE_FROM, RANGE_TO));

            assertIndicesConsistent(line);

            replayUndo(batch);
            assertAll(
                () -> assertThat(removedElements.stream().map(line::getElementIndex))
                    .as("undo restored the removed elements to their original positions")
                    .containsExactlyElementsOf(
                        IntStream.rangeClosed(RANGE_FROM, RANGE_TO).boxed().toList()),
                () -> assertThat(line.getElementIndex(following))
                    .as("undo pushed the first survivor back out to its original position")
                    .isEqualTo(RANGE_TO + 1)
            );
            assertIndicesConsistent(line);

            replayRedo(batch);
            assertAll(
                () -> assertThat(removedElements.stream().map(line::getElementIndex))
                    .as("redo took the removed elements back out of the line")
                    .allMatch(index -> index == NOT_IN_LINE),
                () -> assertThat(line.getElementIndex(following))
                    .as("redo moved the first survivor back down again")
                    .isEqualTo(RANGE_FROM)
            );
            assertIndicesConsistent(line);
        }
    }

    // -----------------------------------------------------------------------
    // The remaining getElementIndex branches
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class UnresolvableElements {

        @Test
        void testElementOfAnotherLineIsNotFound() {
            var otherLine = detachedLine();
            var stranger = new StaffElement(ElementType.CROTCHET);
            otherLine.addElement(stranger);

            assertAll(
                () -> assertThat(line.getElementIndex(stranger))
                    .as("an element held by a different line has no position in this one")
                    .isEqualTo(NOT_IN_LINE),
                () -> assertThat(otherLine.getElementIndex(stranger))
                    .as("it does have a position in the line that holds it")
                    .isEqualTo(0)
            );
        }

        @Test
        void testNullElementIsNotFound() {
            assertThat(line.getElementIndex(null))
                .as("a null element resolves to no position, as ArrayList.indexOf did")
                .isEqualTo(NOT_IN_LINE);
        }

        @Test
        void testSelfReplaceLeavesTheElementAtItsPosition() {
            // A self-replace detaches and re-attaches the same element. It does not reach
            // detach's early return: setElement detaches first precisely so that guard sees
            // the element still pointing here. Nothing in the codebase reaches that return.
            var element = line.getElement(REPLACE_IDX);

            song.withoutMutationTracking(() -> line.setElement(REPLACE_IDX, element));

            assertThat(line.getElementIndex(element))
                .as("replacing an element with itself leaves it where it was")
                .isEqualTo(REPLACE_IDX);
            assertIndicesConsistent(line);
        }
    }

    // -----------------------------------------------------------------------
    // The views the index depends on being unwritable
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class ElementViews {

        @Test
        void testGetElementsRejectsMutation() {
            var elements = line.getElements();

            assertThatThrownBy(elements::clear)
                .as("a caller that could clear the view would move positions behind the index's back")
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void testGetElementsRangeRejectsMutation() {
            var range = line.getElements(RANGE_FROM, RANGE_TO);

            assertThatThrownBy(range::clear)
                .as("clearing a sublist view is exactly how removeRange deletes elements")
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // -----------------------------------------------------------------------
    // Batch capture and replay — the real undo/redo protocol, from the shared helper
    // -----------------------------------------------------------------------

    private List<Mutation> captureBatch(Runnable edit) {
        return UndoTestSupport.captureBatch(song, edit);
    }

    private void replayUndo(List<? extends Mutation> batch) {
        UndoTestSupport.replayUndo(UndoTestSupport.scoreViewFor(song), batch);
    }

    private void replayRedo(List<? extends Mutation> batch) {
        UndoTestSupport.replayRedo(UndoTestSupport.scoreViewFor(song), batch);
    }
}
