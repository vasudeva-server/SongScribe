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


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.*;

/**
 * Unit tests for the Ending invalidation and classification methods:
 * {@link Ending#checkReplacement} and {@link Ending#outcomeFor}, which is the single hook
 * {@link Line} consults for every shape of element change.
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
        var song = fixture.song();
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

    /**
     * The outcome {@code ending} reports for deleting the contiguous run from {@code first}
     * through {@code last} of {@code line}, judged against the projected line.
     * <p>
     * Takes the endpoints as elements and resolves their positions here, so each test still
     * names what it deletes rather than counting indices.
     */
    private static SpanOutcome outcomeForDeleting(
        Ending ending, Line line, StaffElement first, StaffElement last
    ) {
        return ending.outcomeFor(
            ElementChange.forDeletion(line, line.getElementIndex(first), line.getElementIndex(last)),
            line);
    }

    /** The outcome {@code ending} reports for deleting {@code only} from {@code line}. */
    private static SpanOutcome outcomeForDeleting(Ending ending, Line line, StaffElement only) {
        return outcomeForDeleting(ending, line, only, only);
    }

    /**
     * The outcome {@code ending} reports for inserting a fresh {@code insertedType} at
     * {@code index} of {@code line}, judged against the projected line.
     */
    private static SpanOutcome outcomeForInserting(
        Ending ending, Line line, int index, ElementType insertedType
    ) {
        return ending.outcomeFor(
            ElementChange.forInsertion(line, index, insertedType.newInstance()), line);
    }

    /**
     * The outcome {@code ending} reports for replacing {@code oldElement} with
     * {@code newElement}, judged against the projected line.
     */
    private static SpanOutcome outcomeForReplacing(
        Ending ending, Line line, StaffElement oldElement, StaffElement newElement
    ) {
        return ending.outcomeFor(
            ElementChange.forReplacement(
                line, line.getElementIndex(oldElement), newElement),
            line);
    }

    // -----------------------------------------------------------------------
    // outcomeFor, deletion
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenElementsAreDeleted {

        @Test
        void testAllFirstSpanContentDeletedReturnsTrue() {
            // Condition 4: deleting all content in the first sub-span (note1, note2)
            assertThat(outcomeForDeleting(ending, line, note1, note2))
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testAllSecondSpanContentDeletedReturnsTrue() {
            // Condition 4: deleting all content in the second sub-span (note4, note5)
            assertThat(outcomeForDeleting(ending, line, note4, note5))
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testAnchorDeletedRemovesTheEnding() {
            // Not the ending's own rule but the base endpoint rule, which outcomeFor asks first
            assertThat(outcomeForDeleting(ending, line, anchor))
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testOneFirstSpanNoteDeletedReturnsFalse() {
            // Condition 4 requires ALL content in a sub-span to be deleted; one note remains
            assertThat(outcomeForDeleting(ending, line, note1))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testOneSecondSpanNoteDeletedReturnsFalse() {
            // Same as above for the second sub-span
            assertThat(outcomeForDeleting(ending, line, note4))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testSplitDeletedReturnsTrue() {
            // Condition 2: the REPEAT_RIGHT that separates first/second sub-spans is deleted
            assertThat(outcomeForDeleting(ending, line, split))
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }
    }

    // -----------------------------------------------------------------------
    // outcomeFor, insertion
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenAnElementIsInserted {

        @Test
        void testBarlineAfterEndReturnsFalse() {
            // Index 7 is >= endIndex (6), so outside the span
            assertThat(outcomeForInserting(ending, line, 7, ElementType.SINGLE_BARLINE))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testBarlineAtAnchorReturnsFalse() {
            // Index 0 is <= anchorIndex (0), so not interior
            assertThat(outcomeForInserting(ending, line, 0, ElementType.SINGLE_BARLINE))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testBarlineInFirstSpanInteriorReturnsTrue() {
            // Condition 5: inserting a barline at index 2 (interior of first sub-span)
            assertThat(outcomeForInserting(ending, line, 2, ElementType.SINGLE_BARLINE))
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testBarlineInSecondSpanInteriorReturnsTrue() {
            // Condition 5: inserting a repeat barline at index 5 (interior of second sub-span)
            assertThat(outcomeForInserting(ending, line, 5, ElementType.REPEAT_LEFT))
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testNoteInFirstSpanInteriorReturnsFalse() {
            // Non-barline/non-repeat insertions never invalidate the ending
            assertThat(outcomeForInserting(ending, line, 2, ElementType.CROTCHET))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testBarlineAtSplitIndexReturnsTrue() {
            // The split boundary is not exempt: inserting at splitIndex (3) puts the new
            // barline *before* the existing split, leaving the first sub-span with two.
            assertThat(outcomeForInserting(ending, line, 3, ElementType.SINGLE_BARLINE))
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }
    }

    // -----------------------------------------------------------------------
    // outcomeFor, replacement
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class WhenAnElementIsReplaced {

        @Test
        void testAnchorReplacedWithDoubleBarlineReturnsFalse() {
            // #306: Condition 1 — DOUBLE_BARLINE is a barline, an allowed anchor type
            var newElement = new StaffElement(ElementType.DOUBLE_BARLINE);
            assertThat(outcomeForReplacing(ending, line, anchor, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testAnchorReplacedWithRepeatLeftReturnsFalse() {
            // Condition 1: REPEAT_LEFT is an allowed anchor type
            var newElement = new StaffElement(ElementType.REPEAT_LEFT);
            assertThat(outcomeForReplacing(ending, line, anchor, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testAnchorReplacedWithRepeatRightReturnsFalse() {
            // #306: Condition 1 — REPEAT_RIGHT is a repeat, an allowed anchor type
            var newElement = new StaffElement(ElementType.REPEAT_RIGHT);
            assertThat(outcomeForReplacing(ending, line, anchor, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testAnchorReplacedWithGraceNoteReturnsTrue() {
            // Condition 1: GRACE_QUAVER is neither content, barline, nor repeat — invalidates
            var newElement = new StaffElement(ElementType.GRACE_QUAVER);
            assertThat(outcomeForReplacing(ending, line, anchor, newElement))
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testAnchorReplacedWithSingleBarlineReturnsFalse() {
            // Condition 1: SINGLE_BARLINE is an allowed anchor type (same as existing)
            var newElement = new StaffElement(ElementType.SINGLE_BARLINE);
            assertThat(outcomeForReplacing(ending, line, anchor, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testAnchorReplacedWithNoteReturnsFalse() {
            // #306: Condition 1 — a content element is now a valid anchor type
            var newElement = new StaffElement(ElementType.CROTCHET);
            assertThat(outcomeForReplacing(ending, line, anchor, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testAnchorReplacedWithRepeatLeftRightReturnsFalse() {
            // #306: Condition 1 — REPEAT_LEFT_RIGHT is now a valid anchor type (isRepeat())
            var newElement = new StaffElement(ElementType.REPEAT_LEFT_RIGHT);
            assertThat(outcomeForReplacing(ending, line, anchor, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testEndReplacedWithDoubleBarlineReturnsFalse() {
            // Condition 3: DOUBLE_BARLINE is a barline — end replacement is allowed
            var newElement = new StaffElement(ElementType.DOUBLE_BARLINE);
            assertThat(outcomeForReplacing(ending, line, end, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testEndReplacedWithNoteReturnsFalse() {
            // #306: Condition 3 — a note end needs no split compensation; a content
            // replacement is a valid end regardless of split type.
            var newElement = new StaffElement(ElementType.CROTCHET);
            assertThat(outcomeForReplacing(ending, line, end, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testEndReplacedWithRepeatLeftReturnsFalse() {
            // Condition 3: REPEAT_LEFT is a repeat — end replacement is allowed
            var newElement = new StaffElement(ElementType.REPEAT_LEFT);
            assertThat(outcomeForReplacing(ending, line, end, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testMiddleNoteReplacedWithBarlineReturnsFalse() {
            // A middle content element is not the anchor, split, or end — no condition applies
            var newElement = new StaffElement(ElementType.SINGLE_BARLINE);
            assertThat(outcomeForReplacing(ending, line, note1, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testSplitReplacedWithRepeatLeftReturnsTrue() {
            // Condition 2: REPEAT_LEFT is not an allowed split type
            var newElement = new StaffElement(ElementType.REPEAT_LEFT);
            assertThat(outcomeForReplacing(ending, line, split, newElement))
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }

        @Test
        void testSplitReplacedWithRepeatLeftRightEndNotRightRepeatReturnsFalse() {
            // Condition 2: REPEAT_RIGHT → REPEAT_LEFT_RIGHT returns CompensateEnd, not Invalidate,
            // so the ending is kept. The UI layer handles the compensating change.
            var newElement = new StaffElement(ElementType.REPEAT_LEFT_RIGHT);
            assertThat(outcomeForReplacing(ending, line, split, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
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
            var ending2 = new Ending(anchor2, end2);
            assertThat(outcomeForReplacing(ending2, line2, split2, new StaffElement(ElementType.REPEAT_LEFT_RIGHT)))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @SuppressWarnings("PackageVisibleInnerClass")
        @Nested
        class WhenSplitIsRepeatLeftRight {

            private Line line2;
            private StaffElement anchor2;
            private StaffElement end2;
            private Ending ending2;

            @BeforeEach
            void setUp() {
                var comp2 = new Song();
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
                ending2 = new Ending(anchor2, end2);
            }

            @Test
            void testEndReplacedWithSingleBarlineReturnsFalse() {
                // Condition 3: SINGLE_BARLINE end with REPEAT_LEFT_RIGHT split returns CompensateSplit,
                // not Invalidate, so the ending is kept. UI handles the compensating change.
                assertThat(outcomeForReplacing(ending2, line2, end2, new StaffElement(ElementType.SINGLE_BARLINE)))
                .isSameAs(SpanOutcome.Simple.KEEP);
            }

            @Test
            void testEndReplacedWithRepeatRightReturnsFalse() {
                // Condition 3: REPEAT_RIGHT is allowed when split is REPEAT_LEFT_RIGHT
                assertThat(outcomeForReplacing(ending2, line2, end2, new StaffElement(ElementType.REPEAT_RIGHT)))
                .isSameAs(SpanOutcome.Simple.KEEP);
            }

            @Test
            void testEndReplacedWithRepeatLeftRightReturnsFalse() {
                // Condition 3: REPEAT_LEFT_RIGHT is allowed when split is REPEAT_LEFT_RIGHT
                assertThat(outcomeForReplacing(ending2, line2, end2, new StaffElement(ElementType.REPEAT_LEFT_RIGHT)))
                .isSameAs(SpanOutcome.Simple.KEEP);
            }
        }

        @Test
        void testSplitReplacedWithRepeatRightReturnsFalse() {
            // Condition 2: REPEAT_RIGHT is an allowed split type (same as existing)
            var newElement = new StaffElement(ElementType.REPEAT_RIGHT);
            assertThat(outcomeForReplacing(ending, line, split, newElement))
                .isSameAs(SpanOutcome.Simple.KEEP);
        }

        @Test
        void testSplitReplacedWithSingleBarlineReturnsTrue() {
            // Condition 2: SINGLE_BARLINE is not an allowed split type
            var newElement = new StaffElement(ElementType.SINGLE_BARLINE);
            assertThat(outcomeForReplacing(ending, line, split, newElement))
                .isSameAs(SpanOutcome.Simple.REMOVE);
        }
    }

    // -----------------------------------------------------------------------
    // checkReplacement
    // -----------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CheckReplacement {

        // --- Invalidate ---

        @Test
        void testAnchorReplacedWithGraceNoteReturnsInvalidate() {
            // #306: GRACE_QUAVER is neither content, barline, nor repeat — invalidates
            var effect = ending.checkReplacement(anchor, new StaffElement(ElementType.GRACE_QUAVER), line);
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
        void testEndSingleBarlineToGraceNoteReturnsInvalidate() {
            // #306: Condition 3 — GRACE_QUAVER is non-content, non-barline, non-repeat
            var effect = ending.checkReplacement(end, new StaffElement(ElementType.GRACE_QUAVER), line);
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
            // DOUBLE_BARLINE isEndingTerminal() and split is REPEAT_RIGHT
            var effect = ending.checkReplacement(end, new StaffElement(ElementType.DOUBLE_BARLINE), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
        }

        @Test
        void testEndReplacedWithRepeatLeftReturnsNone() {
            // REPEAT_LEFT isEndingTerminal() and split is REPEAT_RIGHT
            var effect = ending.checkReplacement(end, new StaffElement(ElementType.REPEAT_LEFT), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
        }

        @Test
        void testEndReplacedWithNoteReturnsNone() {
            // #306: Condition 3 — a note end needs no split compensation, regardless of split type
            var effect = ending.checkReplacement(end, new StaffElement(ElementType.CROTCHET), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
        }

        @Test
        void testAnchorReplacedWithNoteReturnsNone() {
            // #306: Condition 1 — a content element is now a valid anchor type
            var effect = ending.checkReplacement(anchor, new StaffElement(ElementType.CROTCHET), line);
            assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
        }

        @Test
        void testAnchorReplacedWithRepeatLeftRightReturnsNone() {
            // #306: Condition 1 — REPEAT_LEFT_RIGHT is now a valid anchor type (isRepeat())
            var effect = ending.checkReplacement(anchor, new StaffElement(ElementType.REPEAT_LEFT_RIGHT), line);
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

        @SuppressWarnings("PackageVisibleInnerClass")
        @Nested
        class WhenSplitIsRepeatLeftRight {

            private Line line2;
            private StaffElement lrSplit;
            private StaffElement end2;
            private Ending ending2;

            @BeforeEach
            void setUp() {
                var fixture = EndingLineFixture.secondary();
                var comp2 = fixture.song();
                line2   = fixture.line();
                var anchor2 = fixture.anchor();
                lrSplit = fixture.split();
                end2    = fixture.end();
                ending2 = fixture.ending();
            }

            // --- Invalidate ---

            @Test
            void testEndRepeatRightToGraceNoteReturnsInvalidate() {
                // #306: Condition 3 — GRACE_QUAVER is non-content, non-barline, non-repeat
                var effect = ending2.checkReplacement(end2, new StaffElement(ElementType.GRACE_QUAVER), line2);
                assertThat(effect).isEqualTo(new Ending.EndingEffect.Invalidate(ending2));
            }

            // --- None ---

            @Test
            void testEndRepeatRightToNoteReturnsNone() {
                // #306: Condition 3 — a note end needs no split compensation, regardless of
                // split type, including REPEAT_LEFT_RIGHT
                var effect = ending2.checkReplacement(end2, new StaffElement(ElementType.CROTCHET), line2);
                assertThat(effect).isEqualTo(new Ending.EndingEffect.None());
            }

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
