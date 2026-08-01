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
package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

/**
 * Unit tests for {@link LineEndingSupport}: findEndings, findEndingAt, isInsideAnyEnding,
 * isStartOfAnyEnding, isEndOfAnyEnding, and findEndingReplacementEffect.
 *
 * <p>Primary canonical line layout (from {@link EndingLineFixture}):
 * <pre>
 *  idx:  0             1        2        3             4        5        6
 *        SINGLE_BAR    CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BAR
 *        (anchor)                        (split)                          (end)
 * </pre>
 * Ending spans [0, 6] inclusive.
 */
class LineEndingSupportTest extends UnitTest {

    // -----------------------------------------------------------------------
    // Row 25 — findEndings() extracts Ending range elements
    // -----------------------------------------------------------------------

    @Nested
    class FindEndings {

        @Test
        void testNoEndingsOnLineReturnsEmptyList() {
            var song = new Song();
            var line = song.getLine(0);
            song.withoutMutationTracking(() -> {
                line.addElement(new StaffElement(ElementType.CROTCHET));
            });

            var result = LineEndingSupport.findEndings(line);

            assertThat(result).isEmpty();
        }

        @Test
        void testOneEndingOnLineReturnsThatEnding() {
            var fixture = EndingLineFixture.primary();

            var result = LineEndingSupport.findEndings(fixture.line());

            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isSameAs(fixture.ending());
        }

        @Test
        void testTwoEndingsOnLineReturnsBothEndingsInOrder() {
            // Build a line with two non-overlapping endings:
            //   idx: 0       1       2       3       4       5
            //        BAR     NOTE    BAR     NOTE    NOTE    BAR
            //        (a1)            (e1)    (a2)            (e2)
            var song = new Song();
            var line = song.getLine(0);
            var anchor1 = new StaffElement(ElementType.SINGLE_BARLINE);
            var mid1    = new StaffElement(ElementType.CROTCHET);
            var end1    = new StaffElement(ElementType.SINGLE_BARLINE);
            var anchor2 = new StaffElement(ElementType.CROTCHET);
            var mid2    = new StaffElement(ElementType.CROTCHET);
            var end2    = new StaffElement(ElementType.SINGLE_BARLINE);
            var ending1 = new Ending(anchor1, end1);
            var ending2 = new Ending(anchor2, end2);
            song.withoutMutationTracking(() -> {
                line.addElement(anchor1);
                line.addElement(mid1);
                line.addElement(end1);
                line.addElement(anchor2);
                line.addElement(mid2);
                line.addElement(end2);
                line.addRangeElement(ending1);
                line.addRangeElement(ending2);
            });

            var result = LineEndingSupport.findEndings(line);

            assertThat(result).hasSize(2);
            assertThat(result).containsExactlyInAnyOrder(ending1, ending2);
        }
    }

    // -----------------------------------------------------------------------
    // Row 26 — findEndingAt(List, int) span inclusion and boundary comparators
    // -----------------------------------------------------------------------

    @Nested
    class FindEndingAtList {

        // Ending spans [3, 7] in an 8-element line (anchor index 3, end index 7).
        // Indices chosen to be distinct from 0/1 to expose off-by-one errors at both edges.
        private static final int ANCHOR_IDX = 3;
        private static final int END_IDX    = 7;
        private static final int BEFORE_START  = ANCHOR_IDX - 1;   // 2 — outside left
        private static final int AT_START      = ANCHOR_IDX;       // 3 — inclusive start
        private static final int INSIDE        = ANCHOR_IDX + 1;   // 4 — interior
        private static final int AT_END        = END_IDX;          // 7 — inclusive end
        private static final int AFTER_END     = END_IDX + 1;      // 8 — outside right

        /** Builds a list containing a single ending whose anchor/end indices are ANCHOR_IDX..END_IDX. */
        private List<Ending> oneEndingList() {
            // Build: [BAR, NOTE, NOTE, BAR(anchor), NOTE, NOTE, NOTE, BAR(end)]
            var song = new Song();
            var line = song.getLine(0);
            var anchorEl = new StaffElement(ElementType.SINGLE_BARLINE);
            var endEl    = new StaffElement(ElementType.SINGLE_BARLINE);
            var ending   = new Ending(anchorEl, endEl);
            song.withoutMutationTracking(() -> {
                // Pad 3 elements before anchor so anchor lands at index ANCHOR_IDX
                for (var i = 0; i < ANCHOR_IDX; i++) {
                    line.addElement(new StaffElement(ElementType.CROTCHET));
                }
                line.addElement(anchorEl);
                // Fill interior: need END_IDX - ANCHOR_IDX - 1 = 3 notes
                var interiorCount = END_IDX - ANCHOR_IDX - 1;
                for (var i = 0; i < interiorCount; i++) {
                    line.addElement(new StaffElement(ElementType.CROTCHET));
                }
                line.addElement(endEl);
                line.addRangeElement(ending);
            });
            return List.of(ending);
        }

        @Test
        void testEmptyListReturnsNull() {
            assertThat(LineEndingSupport.findEndingAt(List.of(), AT_START)).isNull();
        }

        @Test
        void testIndexBeforeStartReturnsNull() {
            assertThat(LineEndingSupport.findEndingAt(oneEndingList(), BEFORE_START)).isNull();
        }

        @Test
        void testIndexAtStartReturnsEnding() {
            var endings = oneEndingList();
            assertThat(LineEndingSupport.findEndingAt(endings, AT_START)).isSameAs(endings.get(0));
        }

        @Test
        void testIndexInsideReturnsEnding() {
            var endings = oneEndingList();
            assertThat(LineEndingSupport.findEndingAt(endings, INSIDE)).isSameAs(endings.get(0));
        }

        @Test
        void testIndexAtEndReturnsEnding() {
            var endings = oneEndingList();
            assertThat(LineEndingSupport.findEndingAt(endings, AT_END)).isSameAs(endings.get(0));
        }

        @Test
        void testIndexAfterEndReturnsNull() {
            assertThat(LineEndingSupport.findEndingAt(oneEndingList(), AFTER_END)).isNull();
        }
    }

    // -----------------------------------------------------------------------
    // Row 28 — isInsideAnyEnding null-safe
    // -----------------------------------------------------------------------

    @Nested
    class IsInsideAnyEnding {

        // Primary fixture: ending spans [0, 6].
        private static final int INSIDE_IDX  = 3;   // interior of [0, 6]
        private static final int OUTSIDE_IDX = 8;   // beyond end index 6

        @Test
        void testIndexInsideEndingReturnsTrue() {
            var fixture = EndingLineFixture.primary();
            var endings = LineEndingSupport.findEndings(fixture.line());

            assertThat(LineEndingSupport.isInsideAnyEnding(endings, INSIDE_IDX)).isTrue();
        }

        @Test
        void testIndexOutsideEndingReturnsFalse() {
            var fixture = EndingLineFixture.primary();
            var endings = LineEndingSupport.findEndings(fixture.line());

            assertThat(LineEndingSupport.isInsideAnyEnding(endings, OUTSIDE_IDX)).isFalse();
        }

        @Test
        void testEmptyEndingsListReturnsFalse() {
            // Null-safety: empty list must not throw and must return false
            assertThat(LineEndingSupport.isInsideAnyEnding(List.of(), INSIDE_IDX)).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Row 29 — isStartOfAnyEnding anchor equality
    // -----------------------------------------------------------------------

    @Nested
    class IsStartOfAnyEnding {

        // Primary fixture: anchor is at index 0, end is at index 6.
        private static final int ANCHOR_IDX  = 0;
        private static final int INTERIOR_IDX = 3;  // inside but not at start

        @Test
        void testIndexEqualsAnchorReturnsTrue() {
            var fixture = EndingLineFixture.primary();
            var endings = LineEndingSupport.findEndings(fixture.line());

            assertThat(LineEndingSupport.isStartOfAnyEnding(endings, ANCHOR_IDX)).isTrue();
        }

        @Test
        void testIndexInsideButNotStartReturnsFalse() {
            var fixture = EndingLineFixture.primary();
            var endings = LineEndingSupport.findEndings(fixture.line());

            assertThat(LineEndingSupport.isStartOfAnyEnding(endings, INTERIOR_IDX)).isFalse();
        }

        @Test
        void testEmptyEndingsListReturnsFalse() {
            assertThat(LineEndingSupport.isStartOfAnyEnding(List.of(), ANCHOR_IDX)).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Row 30 — isEndOfAnyEnding end equality
    // -----------------------------------------------------------------------

    @Nested
    class IsEndOfAnyEnding {

        // Primary fixture: end element is at index 6, anchor is at index 0.
        private static final int END_IDX      = 6;
        private static final int INTERIOR_IDX = 3;   // inside but not the end

        @Test
        void testIndexEqualsEndElementReturnsTrue() {
            var fixture = EndingLineFixture.primary();
            var endings = LineEndingSupport.findEndings(fixture.line());

            assertThat(LineEndingSupport.isEndOfAnyEnding(endings, END_IDX)).isTrue();
        }

        @Test
        void testIndexInsideButNotEndReturnsFalse() {
            var fixture = EndingLineFixture.primary();
            var endings = LineEndingSupport.findEndings(fixture.line());

            assertThat(LineEndingSupport.isEndOfAnyEnding(endings, INTERIOR_IDX)).isFalse();
        }

        @Test
        void testEmptyEndingsListReturnsFalse() {
            assertThat(LineEndingSupport.isEndOfAnyEnding(List.of(), END_IDX)).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // Row 31 — findEndingReplacementEffect returns first non-None effect
    // -----------------------------------------------------------------------

    @Nested
    class FindEndingReplacementEffect {

        @Test
        void testNoEndingsOnLineReturnsNone() {
            // Line with no endings: any replacement must return None.
            var song = new Song();
            var line = song.getLine(0);
            var note = new StaffElement(ElementType.CROTCHET);
            song.withoutMutationTracking(() -> line.addElement(note));
            var replacement = new StaffElement(ElementType.SINGLE_BARLINE);

            var effect = LineEndingSupport.findEndingReplacementEffect(line, 0, replacement);

            assertThat(effect).isEqualTo(Ending.EndingEffect.None.INSTANCE);
        }

        @Test
        void testOneEndingAffectedReturnsItsEffect() {
            // Primary fixture: replacing the anchor (SINGLE_BARLINE) with GRACE_QUAVER
            // triggers Ending.EndingEffect.Invalidate.
            var fixture = EndingLineFixture.primary();
            var line    = fixture.line();
            var ending  = fixture.ending();
            // Anchor is at index 0; replacing with GRACE_QUAVER is not an allowed anchor type
            // (#306: content, barline, and repeat types are now all allowed anchors).
            var replacement = new StaffElement(ElementType.GRACE_QUAVER);

            var effect = LineEndingSupport.findEndingReplacementEffect(line, 0, replacement);

            assertThat(effect).isEqualTo(new Ending.EndingEffect.Invalidate(ending));
        }

        @Test
        void testTwoEndingsAffectedReturnsFirstNonNoneEffect() {
            // Two endings sharing index 0 as their anchor. Replacing element 0 with GRACE_QUAVER
            // will produce Invalidate for the first ending; the method must return that first effect.
            //
            // Layout:
            //  idx: 0       1       2
            //       BAR     NOTE    BAR
            //  ending1: anchor=idx 0, end=idx 2
            //  ending2: anchor=idx 0, end=idx 2  (same range, different object)
            var song    = new Song();
            var line    = song.getLine(0);
            var anchor  = new StaffElement(ElementType.SINGLE_BARLINE);
            var mid     = new StaffElement(ElementType.CROTCHET);
            var end     = new StaffElement(ElementType.SINGLE_BARLINE);
            var ending1 = new Ending(anchor, end);
            var ending2 = new Ending(anchor, end);
            song.withoutMutationTracking(() -> {
                line.addElement(anchor);
                line.addElement(mid);
                line.addElement(end);
                line.addRangeElement(ending1);
                line.addRangeElement(ending2);
            });
            var replacement = new StaffElement(ElementType.GRACE_QUAVER);

            var effect = LineEndingSupport.findEndingReplacementEffect(line, 0, replacement);

            // Both endings produce Invalidate; we only require the first non-None
            // effect (i.e. the one from ending1, which was added first).
            assertThat(effect).isEqualTo(new Ending.EndingEffect.Invalidate(ending1));
        }
    }
}
