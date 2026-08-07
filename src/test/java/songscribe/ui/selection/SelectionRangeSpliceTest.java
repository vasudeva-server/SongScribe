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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementRangeDeletion;
import songscribe.message.mutation.ElementReplacement;

/**
 * Unit tests for {@link Selection.Range#splicedFor} and the anchor contract its results rely on.
 * <p>
 * The splice is pure index arithmetic — it never reads the line's contents — so every fixture is
 * an empty detached line plus a hand-built mutation record. One test per boundary, named for that
 * boundary, so a failure says which edge of the rule broke rather than "the splice is wrong".
 */
class SelectionRangeSpliceTest extends UnitTest {

    /** The range under test, held fixed so every case reads as a move relative to it. */
    private static final int RANGE_BEGIN = 4;
    private static final int RANGE_END = 8;

    /** An anchor strictly between the two endpoints, for the cases where it must ride along. */
    private static final int INTERIOR_ANCHOR = 6;

    // -- Insertion --

    @Test
    void testInsertionAtBeginSlidesTheWholeRangeRight() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);

        var spliced = range.splicedFor(insertionAt(line, RANGE_BEGIN));

        assertSplicedTo(spliced, RANGE_BEGIN + 1, RANGE_END + 1, RANGE_BEGIN + 1);
    }

    @Test
    void testInsertionStrictlyInsideGrowsTheEndAndLeavesBeginAndAnchorAlone() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);
        var insideIndex = RANGE_BEGIN + 1;

        var spliced = range.splicedFor(insertionAt(line, insideIndex));

        assertSplicedTo(spliced, RANGE_BEGIN, RANGE_END + 1, RANGE_BEGIN);
    }

    @Test
    void testInsertionPastTheEndLeavesTheRangeUntouched() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);

        var spliced = range.splicedFor(insertionAt(line, RANGE_END + 1));

        assertSplicedTo(spliced, RANGE_BEGIN, RANGE_END, RANGE_BEGIN);
    }

    // -- Deletion --

    @Test
    void testDeletionStrictlyBeforeBeginSlidesTheWholeRangeLeft() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);
        var beforeIndex = RANGE_BEGIN - 1;

        var spliced = range.splicedFor(deletionAt(line, beforeIndex));

        assertSplicedTo(spliced, RANGE_BEGIN - 1, RANGE_END - 1, RANGE_BEGIN - 1);
    }

    /**
     * The head of the range is cut away, so {@code begin} clamps forward onto the element that
     * slid into the hole — the deletion's own {@code from} — and the surviving tail lands there
     * behind it.
     */
    @Test
    void testDeletionOverlappingTheHeadHoldsBeginAtFromAndSlidesTheEndLeft() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);
        var headDeleteFrom = 2;
        var headDeleteTo = 5;
        var expectedBegin = 2;
        var expectedEnd = 4;

        var spliced = range.splicedFor(rangeDeletion(line, headDeleteFrom, headDeleteTo));

        assertSplicedTo(spliced, expectedBegin, expectedEnd, expectedBegin);
    }

    @Test
    void testDeletionStrictlyInsideHoldsBeginAndSlidesTheEndLeft() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);
        var interiorFrom = 5;
        var interiorTo = 6;
        var expectedEnd = 6;

        var spliced = range.splicedFor(rangeDeletion(line, interiorFrom, interiorTo));

        assertSplicedTo(spliced, RANGE_BEGIN, expectedEnd, RANGE_BEGIN);
    }

    /**
     * The tail is cut away with nothing behind it to slide down, so {@code end} clamps back onto
     * the last element before the hole rather than forward the way {@code begin} does.
     */
    @Test
    void testDeletionOverlappingTheTailClampsTheEndBackBeforeTheHole() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);
        var tailDeleteFrom = 7;
        var tailDeleteTo = 10;
        var expectedEnd = tailDeleteFrom - 1;

        var spliced = range.splicedFor(rangeDeletion(line, tailDeleteFrom, tailDeleteTo));

        assertSplicedTo(spliced, RANGE_BEGIN, expectedEnd, RANGE_BEGIN);
    }

    @Test
    void testDeletionCoveringTheWholeRangeReturnsNull() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);
        var coveringFrom = RANGE_BEGIN - 1;
        var coveringTo = RANGE_END + 1;

        assertThat(range.splicedFor(rangeDeletion(line, coveringFrom, coveringTo))).isNull();
    }

    @Test
    void testDeletionEntirelyAfterTheEndLeavesTheRangeUntouched() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);
        var afterFrom = RANGE_END + 1;
        var afterTo = RANGE_END + 2;

        var spliced = range.splicedFor(rangeDeletion(line, afterFrom, afterTo));

        assertSplicedTo(spliced, RANGE_BEGIN, RANGE_END, RANGE_BEGIN);
    }

    // -- Anchor --

    @Test
    void testAnchorFollowsItsElementWhenTheSpliceMovesTheRange() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, INTERIOR_ANCHOR);
        var leadingDeleteFrom = 0;
        var leadingDeleteTo = 1;
        var removedCount = 2;

        var spliced = range.splicedFor(rangeDeletion(line, leadingDeleteFrom, leadingDeleteTo));

        assertSplicedTo(
            spliced, RANGE_BEGIN - removedCount, RANGE_END - removedCount, INTERIOR_ANCHOR - removedCount);
    }

    /**
     * The anchored element itself is deleted, so the anchor has no element of its own to land on.
     * Without the clamp in {@code splicedFor} it would sit one past the surviving end and the
     * constructor would throw.
     */
    @Test
    void testAnchorClampsIntoTheRangeWhenTheAnchoredElementIsDeleted() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_END);
        var expectedEnd = RANGE_END - 1;

        var spliced = range.splicedFor(deletionAt(line, RANGE_END));

        assertSplicedTo(spliced, RANGE_BEGIN, expectedEnd, expectedEnd);
    }

    @Test
    void testAnchorAtTheEndClampsDownToTheNewEndWhenTheTailIsDeleted() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_END);
        var tailDeleteFrom = 6;
        var tailDeleteTo = 9;
        var expectedEnd = tailDeleteFrom - 1;

        var spliced = range.splicedFor(rangeDeletion(line, tailDeleteFrom, tailDeleteTo));

        assertSplicedTo(spliced, RANGE_BEGIN, expectedEnd, expectedEnd);
    }

    // -- Mutations that move no index --

    @Test
    void testMutationOnAnotherLineReturnsTheSameRange() {
        var line = detachedLine();
        var otherLine = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);

        assertThat(range.splicedFor(insertionAt(otherLine, RANGE_BEGIN))).isSameAs(range);
        assertThat(range.splicedFor(deletionAt(otherLine, RANGE_BEGIN))).isSameAs(range);
        assertThat(range.splicedFor(rangeDeletion(otherLine, RANGE_BEGIN, RANGE_END))).isSameAs(range);
    }

    @Test
    void testReplacementAndModificationReturnTheSameRange() {
        var line = detachedLine();
        var range = rangeAnchoredAt(line, RANGE_BEGIN);
        var replacement =
            new ElementReplacement(line, RANGE_BEGIN, newElement(), newElement());
        var modification = new ElementModification(
            line, RANGE_BEGIN, EnumSet.of(ElementField.PITCH), newElement(), newElement());

        assertThat(range.splicedFor(replacement)).as("replacement").isSameAs(range);
        assertThat(range.splicedFor(modification)).as("modification").isSameAs(range);
    }

    // -- Anchor validation --

    @Test
    void testRangeRejectsAnAnchorBeforeBegin() {
        var line = detachedLine();

        assertThatThrownBy(() -> new Selection.Range(line, RANGE_BEGIN, RANGE_END, RANGE_BEGIN - 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testRangeRejectsAnAnchorAfterEnd() {
        var line = detachedLine();

        assertThatThrownBy(() -> new Selection.Range(line, RANGE_BEGIN, RANGE_END, RANGE_END + 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -- Fixtures --

    private static Selection.Range rangeAnchoredAt(Line line, int anchor) {
        return new Selection.Range(line, RANGE_BEGIN, RANGE_END, anchor);
    }

    private static StaffElement newElement() {
        return ElementType.CROTCHET.newInstance();
    }

    private static ElementInsertion insertionAt(Line line, int index) {
        return new ElementInsertion(line, index, newElement());
    }

    private static ElementDeletion deletionAt(Line line, int index) {
        return new ElementDeletion(line, index, newElement());
    }

    private static ElementRangeDeletion rangeDeletion(Line line, int from, int to) {
        return new ElementRangeDeletion(line, from, to, List.of(newElement()));
    }

    /** Names the endpoint that broke, so the test name and the failure together locate the bug. */
    private static void assertSplicedTo(
        Selection.@Nullable Range spliced, int expectedBegin, int expectedEnd, int expectedAnchor) {
        assertThat(spliced).isNotNull();
        assertThat(spliced.begin()).as("begin").isEqualTo(expectedBegin);
        assertThat(spliced.end()).as("end").isEqualTo(expectedEnd);
        assertThat(spliced.anchor()).as("anchor").isEqualTo(expectedAnchor);
    }
}
