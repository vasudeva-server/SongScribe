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
package songscribe.ui.layout;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.Song;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;

/**
 * Unit tests for the Ending invalidation and classification methods:
 * {@link Ending#checkReplacement}, {@link Ending#isInvalidatedByDeletion},
 * {@link Ending#isInvalidatedByReplacement}, and {@link Ending#isInvalidatedByInsertion}.
 *
 * <p>Primary canonical line layout used across most tests:
 * <pre>
 *  idx:  0             1        2        3             4        5        6
 *        SINGLE_BAR    CROTCHET CROTCHET REPEAT_RIGHT  CROTCHET CROTCHET SINGLE_BAR
 *        (anchor)                        (split)                          (end)
 * </pre>
 *
 * <p>Secondary canonical line layout used for REPEAT_LEFT_RIGHT-split cases:
 * <pre>
 *  idx:  0            1        2        3                 4        5        6
 *        REPEAT_LEFT  CROTCHET CROTCHET REPEAT_LEFT_RIGHT CROTCHET CROTCHET REPEAT_RIGHT
 *        (anchor)                       (split)                              (end)
 * </pre>
 */
class EndingInvalidationTest extends UnitTest {

    private Line line;
    private Ending ending;

    // Elements at their respective canonical indices
    private StaffElement anchor;  // idx 0 — SINGLE_BARLINE (anchor)
    private StaffElement note1;   // idx 1 — CROTCHET (first-span content)
    private StaffElement note2;   // idx 2 — CROTCHET (first-span content)
    private StaffElement split;   // idx 3 — REPEAT_RIGHT (split)
    private StaffElement note4;   // idx 4 — CROTCHET (second-span content)
    private StaffElement note5;   // idx 5 — CROTCHET (second-span content)
    private StaffElement end;     // idx 6 — SINGLE_BARLINE (end)

    @BeforeEach
    void setUp() {
        var fixture = EndingLineFixture.primary();
        Song song = fixture.song();
        line        = fixture.line();
        anchor      = fixture.anchor();
        note1       = fixture.note1();
        note2       = fixture.note2();
        split       = fixture.split();
        note4       = fixture.note4();
        note5       = fixture.note5();
        end         = fixture.end();
        ending      = fixture.ending();
    }

    // -----------------------------------------------------------------------
    // isInvalidatedByDeletion
    // -----------------------------------------------------------------------

    @Nested
    class IsInvalidatedByDeletion {

        @Test
        void testAllFirstSpanContentDeletedReturnsTrue() {
            // Condition 4: deleting all content in the first sub-span (note1, note2)
            assertThat(ending.isInvalidatedByDeletion(List.of(note1, note2), line)).isTrue();
        }

        @Test
        void testAllSecondSpanContentDeletedReturnsTrue() {
            // Condition 4: deleting all content in the second sub-span (note4, note5)
            assertThat(ending.isInvalidatedByDeletion(List.of(note4, note5), line)).isTrue();
        }

        @Test
        void testAnchorDeletedReturnsFalse() {
            // Anchor deletion is handled by the base isInvalidatedBy; not re-checked here
            assertThat(ending.isInvalidatedByDeletion(List.of(anchor), line)).isFalse();
        }

        @Test
        void testOneFirstSpanNoteDeletedReturnsFalse() {
            // Condition 4 requires ALL content in a sub-span to be deleted; one note remains
            assertThat(ending.isInvalidatedByDeletion(List.of(note1), line)).isFalse();
        }

        @Test
        void testOneSecondSpanNoteDeletedReturnsFalse() {
            // Same as above for the second sub-span
            assertThat(ending.isInvalidatedByDeletion(List.of(note4), line)).isFalse();
        }

        @Test
        void testSplitDeletedReturnsTrue() {
            // Condition 2: the REPEAT_RIGHT that separates first/second sub-spans is deleted
            assertThat(ending.isInvalidatedByDeletion(List.of(split), line)).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // isInvalidatedByInsertion
    // -----------------------------------------------------------------------

    @Nested
    class IsInvalidatedByInsertion {

        @Test
        void testBarlineAfterEndReturnsFalse() {
            // Index 7 is >= endIndex (6), so outside the span
            assertThat(ending.isInvalidatedByInsertion(7, ElementType.SINGLE_BARLINE, line)).isFalse();
        }

        @Test
        void testBarlineAtAnchorReturnsFalse() {
            // Index 0 is <= anchorIndex (0), so not interior
            assertThat(ending.isInvalidatedByInsertion(0, ElementType.SINGLE_BARLINE, line)).isFalse();
        }

        @Test
        void testBarlineInFirstSpanInteriorReturnsTrue() {
            // Condition 5: inserting a barline at index 2 (interior of first sub-span)
            assertThat(ending.isInvalidatedByInsertion(2, ElementType.SINGLE_BARLINE, line)).isTrue();
        }

        @Test
        void testBarlineInSecondSpanInteriorReturnsTrue() {
            // Condition 5: inserting a repeat barline at index 5 (interior of second sub-span)
            assertThat(ending.isInvalidatedByInsertion(5, ElementType.REPEAT_LEFT, line)).isTrue();
        }

        @Test
        void testNoteInFirstSpanInteriorReturnsFalse() {
            // Non-barline/non-repeat insertions never invalidate the ending
            assertThat(ending.isInvalidatedByInsertion(2, ElementType.CROTCHET, line)).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // isInvalidatedByReplacement
    // -----------------------------------------------------------------------

    @Nested
    class IsInvalidatedByReplacement {

        @Test
        void testAnchorReplacedWithDoubleBarlineReturnsTrue() {
            // Condition 1: DOUBLE_BARLINE is not an allowed anchor type
            var newElement = new StaffElement(ElementType.DOUBLE_BARLINE);
            assertThat(ending.isInvalidatedByReplacement(anchor, newElement, line)).isTrue();
        }

        @Test
        void testAnchorReplacedWithRepeatLeftReturnsFalse() {
            // Condition 1: REPEAT_LEFT is an allowed anchor type
            var newElement = new StaffElement(ElementType.REPEAT_LEFT);
            assertThat(ending.isInvalidatedByReplacement(anchor, newElement, line)).isFalse();
        }

        @Test
        void testAnchorReplacedWithRepeatRightReturnsTrue() {
            // Condition 1: REPEAT_RIGHT is not an allowed anchor type
            var newElement = new StaffElement(ElementType.REPEAT_RIGHT);
            assertThat(ending.isInvalidatedByReplacement(anchor, newElement, line)).isTrue();
        }

        @Test
        void testAnchorReplacedWithSingleBarlineReturnsFalse() {
            // Condition 1: SINGLE_BARLINE is an allowed anchor type (same as existing)
            var newElement = new StaffElement(ElementType.SINGLE_BARLINE);
            assertThat(ending.isInvalidatedByReplacement(anchor, newElement, line)).isFalse();
        }

        @Test
        void testEndReplacedWithDoubleBarlineReturnsFalse() {
            // Condition 3: DOUBLE_BARLINE is a barline — end replacement is allowed
            var newElement = new StaffElement(ElementType.DOUBLE_BARLINE);
            assertThat(ending.isInvalidatedByReplacement(end, newElement, line)).isFalse();
        }

        @Test
        void testEndReplacedWithNoteReturnsTrue() {
            // Condition 3: replacing the end element with a non-barline/non-repeat invalidates
            var newElement = new StaffElement(ElementType.CROTCHET);
            assertThat(ending.isInvalidatedByReplacement(end, newElement, line)).isTrue();
        }

        @Test
        void testEndReplacedWithRepeatLeftReturnsFalse() {
            // Condition 3: REPEAT_LEFT is a repeat — end replacement is allowed
            var newElement = new StaffElement(ElementType.REPEAT_LEFT);
            assertThat(ending.isInvalidatedByReplacement(end, newElement, line)).isFalse();
        }

        @Test
        void testMiddleNoteReplacedWithBarlineReturnsFalse() {
            // A middle content element is not the anchor, split, or end — no condition applies
            var newElement = new StaffElement(ElementType.SINGLE_BARLINE);
            assertThat(ending.isInvalidatedByReplacement(note1, newElement, line)).isFalse();
        }

        @Test
        void testSplitReplacedWithRepeatLeftReturnsTrue() {
            // Condition 2: REPEAT_LEFT is not an allowed split type
            var newElement = new StaffElement(ElementType.REPEAT_LEFT);
            assertThat(ending.isInvalidatedByReplacement(split, newElement, line)).isTrue();
        }

        @Test
        void testSplitReplacedWithRepeatLeftRightEndNotRightRepeatReturnsFalse() {
            // Condition 2: REPEAT_RIGHT → REPEAT_LEFT_RIGHT returns CompensateEnd, not Invalidate,
            // so isInvalidatedByReplacement returns false. The UI layer handles the compensating change.
            var newElement = new StaffElement(ElementType.REPEAT_LEFT_RIGHT);
            assertThat(ending.isInvalidatedByReplacement(split, newElement, line)).isFalse();
        }

        @Test
        void testSplitReplacedWithRepeatLeftRightEndIsRightRepeatReturnsFalse() {
            // Condition 2: REPEAT_LEFT_RIGHT is valid as split when end is a right repeat
            var comp2 = new Song();
            var line2 = comp2.getLine(0);
            var anchor2 = new StaffElement(ElementType.SINGLE_BARLINE);
            var split2 = new StaffElement(ElementType.REPEAT_RIGHT);
            var end2 = new StaffElement(ElementType.REPEAT_RIGHT);
            comp2.withoutMutationTracking(() -> {
                line2.addElement(anchor2);
                line2.addElement(new StaffElement(ElementType.CROTCHET));
                line2.addElement(split2);
                line2.addElement(new StaffElement(ElementType.CROTCHET));
                line2.addElement(end2);
            });
            var ending2 = new Ending(anchor2, end2, Ending.Type.FIRST);
            assertThat(ending2.isInvalidatedByReplacement(split2, new StaffElement(ElementType.REPEAT_LEFT_RIGHT), line2)).isFalse();
        }

        @Nested
        class WhenSplitIsRepeatLeftRight {

            private Line line2;
            private StaffElement anchor2;
            private StaffElement end2;
            private Ending ending2;

            @BeforeEach
            void setUp() {
                Song comp2 = new Song();
                line2 = comp2.getLine(0);
                anchor2 = new StaffElement(ElementType.SINGLE_BARLINE);
                var lrSplit = new StaffElement(ElementType.REPEAT_LEFT_RIGHT);
                end2 = new StaffElement(ElementType.REPEAT_RIGHT);
                comp2.withoutMutationTracking(() -> {
                    line2.addElement(anchor2);
                    line2.addElement(new StaffElement(ElementType.CROTCHET));
                    line2.addElement(lrSplit);
                    line2.addElement(new StaffElement(ElementType.CROTCHET));
                    line2.addElement(end2);
                });
                ending2 = new Ending(anchor2, end2, Ending.Type.FIRST);
            }

            @Test
            void testEndReplacedWithSingleBarlineReturnsFalse() {
                // Condition 3: SINGLE_BARLINE end with REPEAT_LEFT_RIGHT split returns CompensateSplit,
                // not Invalidate, so isInvalidatedByReplacement returns false. UI handles the compensating change.
                assertThat(ending2.isInvalidatedByReplacement(end2, new StaffElement(ElementType.SINGLE_BARLINE), line2)).isFalse();
            }

            @Test
            void testEndReplacedWithRepeatRightReturnsFalse() {
                // Condition 3: REPEAT_RIGHT is allowed when split is REPEAT_LEFT_RIGHT
                assertThat(ending2.isInvalidatedByReplacement(end2, new StaffElement(ElementType.REPEAT_RIGHT), line2)).isFalse();
            }

            @Test
            void testEndReplacedWithRepeatLeftRightReturnsFalse() {
                // Condition 3: REPEAT_LEFT_RIGHT is allowed when split is REPEAT_LEFT_RIGHT
                assertThat(ending2.isInvalidatedByReplacement(end2, new StaffElement(ElementType.REPEAT_LEFT_RIGHT), line2)).isFalse();
            }
        }

        @Test
        void testSplitReplacedWithRepeatRightReturnsFalse() {
            // Condition 2: REPEAT_RIGHT is an allowed split type (same as existing)
            var newElement = new StaffElement(ElementType.REPEAT_RIGHT);
            assertThat(ending.isInvalidatedByReplacement(split, newElement, line)).isFalse();
        }

        @Test
        void testSplitReplacedWithSingleBarlineReturnsTrue() {
            // Condition 2: SINGLE_BARLINE is not an allowed split type
            var newElement = new StaffElement(ElementType.SINGLE_BARLINE);
            assertThat(ending.isInvalidatedByReplacement(split, newElement, line)).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // checkReplacement
    // -----------------------------------------------------------------------

    @Nested
    class CheckReplacement {

        // --- Invalidate ---

        @Test
        void testAnchorReplacedWithDoubleBarlineReturnsInvalidate() {
            var effect = ending.checkReplacement(anchor, new StaffElement(ElementType.DOUBLE_BARLINE), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.Invalidate(ending));
        }

        @Test
        void testAnchorReplacedWithRepeatRightReturnsInvalidate() {
            var effect = ending.checkReplacement(anchor, new StaffElement(ElementType.REPEAT_RIGHT), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.Invalidate(ending));
        }

        @Test
        void testSplitRepeatRightToSingleBarlineReturnsInvalidate() {
            // Any non-repeat type for the split invalidates the ending
            var effect = ending.checkReplacement(split, new StaffElement(ElementType.SINGLE_BARLINE), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.Invalidate(ending));
        }

        @Test
        void testSplitRepeatRightToRepeatLeftReturnsInvalidate() {
            // REPEAT_LEFT is not an allowed split type
            var effect = ending.checkReplacement(split, new StaffElement(ElementType.REPEAT_LEFT), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.Invalidate(ending));
        }

        @Test
        void testEndSingleBarlineToNoteReturnsInvalidate() {
            // Non-barline, non-repeat replacement of the end element invalidates
            var effect = ending.checkReplacement(end, new StaffElement(ElementType.CROTCHET), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.Invalidate(ending));
        }

        @Test
        void testEndSingleBarlineToBreathMarkReturnsInvalidate() {
            // BREATH_MARK is neither a barline nor a repeat
            var effect = ending.checkReplacement(end, new StaffElement(ElementType.BREATH_MARK), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.Invalidate(ending));
        }

        // --- None ---

        @Test
        void testAnchorReplacedWithSingleBarlineReturnsNone() {
            var effect = ending.checkReplacement(anchor, new StaffElement(ElementType.SINGLE_BARLINE), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
        }

        @Test
        void testAnchorReplacedWithRepeatLeftReturnsNone() {
            var effect = ending.checkReplacement(anchor, new StaffElement(ElementType.REPEAT_LEFT), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
        }

        @Test
        void testSplitRepeatRightToRepeatRightReturnsNone() {
            // Same type: no structural change needed
            var effect = ending.checkReplacement(split, new StaffElement(ElementType.REPEAT_RIGHT), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
        }

        @Test
        void testEndReplacedWithDoubleBarlineReturnsNone() {
            // DOUBLE_BARLINE isTerminal() and split is REPEAT_RIGHT
            var effect = ending.checkReplacement(end, new StaffElement(ElementType.DOUBLE_BARLINE), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
        }

        @Test
        void testEndReplacedWithRepeatLeftReturnsNone() {
            // REPEAT_LEFT isTerminal() and split is REPEAT_RIGHT
            var effect = ending.checkReplacement(end, new StaffElement(ElementType.REPEAT_LEFT), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
        }

        @Test
        void testInteriorNoteReplacedWithBarlineReturnsNone() {
            // Interior content elements are unrelated to the ending structure
            var effect = ending.checkReplacement(note1, new StaffElement(ElementType.SINGLE_BARLINE), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
        }

        // --- CompensateEnd ---

        @Test
        void testSplitRepeatRightToRepeatLeftRightReturnsCompensateEnd() {
            // Split REPEAT_RIGHT → REPEAT_LEFT_RIGHT: end must become REPEAT_RIGHT
            var effect = ending.checkReplacement(split, new StaffElement(ElementType.REPEAT_LEFT_RIGHT), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.CompensateEnd(ending, ElementType.REPEAT_RIGHT));
        }

        // --- CompensateSplit ---

        @Test
        void testEndSingleBarlineToRepeatRightReturnsCompensateSplit() {
            // End → REPEAT_RIGHT with split REPEAT_RIGHT: split must become REPEAT_LEFT_RIGHT
            var effect = ending.checkReplacement(end, new StaffElement(ElementType.REPEAT_RIGHT), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.CompensateSplit(ending, ElementType.REPEAT_LEFT_RIGHT));
        }

        @Test
        void testEndSingleBarlineToRepeatLeftRightReturnsCompensateSplit() {
            // End → REPEAT_LEFT_RIGHT with split REPEAT_RIGHT: split must become REPEAT_LEFT_RIGHT
            var effect = ending.checkReplacement(end, new StaffElement(ElementType.REPEAT_LEFT_RIGHT), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.CompensateSplit(ending, ElementType.REPEAT_LEFT_RIGHT));
        }

        // -----------------------------------------------------------------------
        // Secondary canonical line: split = REPEAT_LEFT_RIGHT
        // -----------------------------------------------------------------------

        @Nested
        class WhenSplitIsRepeatLeftRight {

            private Line line2;
            private StaffElement lrSplit;
            private StaffElement end2;
            private Ending ending2;

            @BeforeEach
            void setUp() {
                var fixture = EndingLineFixture.secondary();
                Song comp2 = fixture.song();
                line2   = fixture.line();
                StaffElement anchor2 = fixture.anchor();
                lrSplit = fixture.split();
                end2    = fixture.end();
                ending2 = fixture.ending();
            }

            // --- Invalidate ---

            @Test
            void testEndRepeatRightToNoteReturnsInvalidate() {
                // Non-barline, non-repeat replacement of the end element invalidates
                var effect = ending2.checkReplacement(end2, new StaffElement(ElementType.CROTCHET), line2);
                assertThat(effect).isEqualTo(new Ending.EndingEffect.Invalidate(ending2));
            }

            // --- None ---

            @Test
            void testSplitRepeatLeftRightToRepeatLeftRightReturnsNone() {
                // Same type: no structural change needed
                var effect = ending2.checkReplacement(lrSplit, new StaffElement(ElementType.REPEAT_LEFT_RIGHT), line2);
                assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
            }

            @Test
            void testEndRepeatRightToRepeatLeftRightReturnsNone() {
                // REPEAT_LEFT_RIGHT is valid when split is REPEAT_LEFT_RIGHT
                var effect = ending2.checkReplacement(end2, new StaffElement(ElementType.REPEAT_LEFT_RIGHT), line2);
                assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
            }

            // --- CompensateEnd ---

            @Test
            void testSplitRepeatLeftRightToRepeatRightReturnsCompensateEnd() {
                // Split REPEAT_LEFT_RIGHT → REPEAT_RIGHT: end must become SINGLE_BARLINE
                var effect = ending2.checkReplacement(lrSplit, new StaffElement(ElementType.REPEAT_RIGHT), line2);
                assertThat(effect).isEqualTo(new Ending.EndingEffect.CompensateEnd(ending2, ElementType.SINGLE_BARLINE));
            }

            // --- CompensateSplit ---

            @Test
            void testEndRepeatRightToSingleBarlineReturnsCompensateSplit() {
                // End → SINGLE_BARLINE with split REPEAT_LEFT_RIGHT: split must become REPEAT_RIGHT
                var effect = ending2.checkReplacement(end2, new StaffElement(ElementType.SINGLE_BARLINE), line2);
                assertThat(effect).isEqualTo(new Ending.EndingEffect.CompensateSplit(ending2, ElementType.REPEAT_RIGHT));
            }

            @Test
            void testEndRepeatRightToRepeatLeftReturnsCompensateSplit() {
                // End → REPEAT_LEFT with split REPEAT_LEFT_RIGHT: split must become REPEAT_RIGHT
                var effect = ending2.checkReplacement(end2, new StaffElement(ElementType.REPEAT_LEFT), line2);
                assertThat(effect).isEqualTo(new Ending.EndingEffect.CompensateSplit(ending2, ElementType.REPEAT_RIGHT));
            }
        }
    }
}
