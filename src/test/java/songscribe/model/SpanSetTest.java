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

package songscribe.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

/**
 * Unit tests for {@link SpanSet#shiftValues(int, int)}. The deletion paths
 * regression-test issue #397: deleting the first note of a beam span used to
 * corrupt the span boundaries, leaking the beam onto an unrelated preceding
 * note.
 */
class SpanSetTest extends UnitTest {

    private static SpanSet<BeamSpan> beamSet(int[]... ranges) {
        var set = new SpanSet<BeamSpan>();

        for (var r : ranges) {
            set.addSpan(new BeamSpan(r[0], r[1]));
        }

        return set;
    }

    private static List<int[]> snapshot(SpanSet<BeamSpan> set) {
        var result = new ArrayList<int[]>();

        for (var it = set.listIterator(); it.hasNext(); ) {
            var span = it.next();
            result.add(new int[] { span.start, span.end });
        }

        return result;
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Deletion {

        @Test
        void testDeletingFirstNoteOfTwoNoteSpanRemovesSpan() {
            // Regression for #397: a 2-note beam losing its first note must
            // disappear (only one note left).
            var set = beamSet(new int[] { 3, 4 });
            set.shiftValues(3, -1);
            assertThat(snapshot(set)).isEmpty();
        }

        @Test
        void testDeletingFirstNoteOfSecondSpanDoesNotLeakIntoGap() {
            // Regression for #397: with two separate beam spans, deleting the
            // first note of the second span must not pull the span backwards
            // into the unbeamed gap note.
            var set = beamSet(new int[] { 0, 1 }, new int[] { 3, 4 });
            set.shiftValues(3, -1);
            // Span A unaffected; span B had only 2 notes and now collapses.
            assertThat(snapshot(set))
                .containsExactly(new int[] { 0, 1 });
        }

        @Test
        void testDeletingFirstNoteOfMultiNoteSpanPreservesRemainder() {
            // Span [3,5] (three notes). Deleting note 3 leaves [3,4].
            var set = beamSet(new int[] { 3, 5 });
            set.shiftValues(3, -1);
            assertThat(snapshot(set))
                .containsExactly(new int[] { 3, 4 });
        }

        @Test
        void testDeletingLastNoteOfSpanShrinksEnd() {
            // Span [2,5], delete note 5 → [2,4].
            var set = beamSet(new int[] { 2, 5 });
            set.shiftValues(5, -1);
            assertThat(snapshot(set))
                .containsExactly(new int[] { 2, 4 });
        }

        @Test
        void testDeletingInteriorNoteShrinksSpan() {
            // Span [2,5], delete note 3 → [2,4].
            var set = beamSet(new int[] { 2, 5 });
            set.shiftValues(3, -1);
            assertThat(snapshot(set))
                .containsExactly(new int[] { 2, 4 });
        }

        @Test
        void testDeletingBeforeSpanShiftsBothBoundsDown() {
            // Span [4,6], delete note 1 → [3,5].
            var set = beamSet(new int[] { 4, 6 });
            set.shiftValues(1, -1);
            assertThat(snapshot(set))
                .containsExactly(new int[] { 3, 5 });
        }

        @Test
        void testDeletingAfterSpanLeavesItUnchanged() {
            var set = beamSet(new int[] { 0, 2 });
            set.shiftValues(5, -1);
            assertThat(snapshot(set))
                .containsExactly(new int[] { 0, 2 });
        }

        @Test
        void testRangeDeletionEntirelyContainingSpanRemovesSpan() {
            // Span [3,5], delete range [2,6] → span removed.
            var set = beamSet(new int[] { 3, 5 });
            set.shiftValues(2, -5);
            assertThat(snapshot(set)).isEmpty();
        }

        @Test
        void testRangeDeletionTrimsLeadingOverlap() {
            // Span [3,7], delete range [3,4] (2 notes) → span clamps to [3,5]
            // (the surviving suffix shifted down by 2).
            var set = beamSet(new int[] { 3, 7 });
            set.shiftValues(3, -2);
            assertThat(snapshot(set))
                .containsExactly(new int[] { 3, 5 });
        }

        @Test
        void testRangeDeletionTrimsTrailingOverlap() {
            // Span [2,7], delete range [5,7] (3 notes) → span trims to [2,4].
            var set = beamSet(new int[] { 2, 7 });
            set.shiftValues(5, -3);
            assertThat(snapshot(set))
                .containsExactly(new int[] { 2, 4 });
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class Insertion {

        @Test
        void testInsertingBeforeSpanShiftsBothBoundsUp() {
            var set = beamSet(new int[] { 3, 5 });
            set.shiftValues(0, 1);
            assertThat(snapshot(set))
                .containsExactly(new int[] { 4, 6 });
        }

        @Test
        void testInsertingAtSpanStartPushesSpanForward() {
            // Inserting a new note at the span's start index pushes the
            // existing span outward — the new note is NOT part of the span.
            var set = beamSet(new int[] { 3, 5 });
            set.shiftValues(3, 1);
            assertThat(snapshot(set))
                .containsExactly(new int[] { 4, 6 });
        }

        @Test
        void testInsertingAfterSpanLeavesItUnchanged() {
            var set = beamSet(new int[] { 0, 2 });
            set.shiftValues(5, 1);
            assertThat(snapshot(set))
                .containsExactly(new int[] { 0, 2 });
        }
    }
}
