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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

/**
 * Unit tests for {@link Ending}: label format, span-width geometry, split-element search,
 * and bracket-range computation.
 */
class EndingTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Row 10 — BracketRange.label()
    // -------------------------------------------------------------------------

    @Nested
    class BracketRangeLabel {

        @Test
        void testLabelForNumberOneReturnsOnePeriod() {
            var bracket = new Ending.BracketRange(0.0, 1.0, 1, true);
            assertThat(bracket.label()).isEqualTo("1.");
        }

        @Test
        void testLabelForNumberTwoReturnsTwoPeriod() {
            var bracket = new Ending.BracketRange(0.0, 1.0, 2, true);
            assertThat(bracket.label()).isEqualTo("2.");
        }
    }

    // -------------------------------------------------------------------------
    // Row 12 — getSpanWidthSs(double anchorXSs, double endXSs)
    // -------------------------------------------------------------------------

    @Nested
    class GetSpanWidthSs {

        private Ending ending;

        @BeforeEach
        void setUp() {
            var song = new Song();
            var line = song.getLine(0);
            var anchor = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            ending = new Ending(anchor, end);
            song.withoutMutationTracking(() -> {
                line.addElement(anchor);
                line.addElement(end);
                line.addRangeElement(ending);
            });
        }

        @Test
        void testZeroSpanReturnsNoteHeadWidth() {
            double anchorX = 10.0;
            assertThat(ending.getSpanWidthSs(anchorX, anchorX))
                .isEqualTo(SMuFLConstants.NOTE_HEAD_WIDTH_SS);
        }

        @Test
        void testPositiveSpanReturnsDifferencesPlusNoteHeadWidth() {
            double anchorX = 10.0;
            double span = 10.0;
            assertThat(ending.getSpanWidthSs(anchorX, anchorX + span))
                .isEqualTo(span + SMuFLConstants.NOTE_HEAD_WIDTH_SS);
        }

        /** Builds an ending, on its own line, whose end element has the given type. */
        private static Ending endingEndingOn(ElementType endType) {
            var song = new Song();
            var line = song.getLine(0);
            var anchor = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(endType);
            var built = new Ending(anchor, end);
            song.withoutMutationTracking(() -> {
                line.addElement(anchor);
                line.addElement(end);
                line.addRangeElement(built);
            });

            return built;
        }

        // The span covers the end note's head, so an ending that stops on a whole note stretches
        // further than one that stops on a quarter note at the same X (#694). The expected width is
        // read from the font metadata so this fails if a whole note is measured as a black one again.
        @Test
        void testWiderEndNoteheadStretchesTheSpan() {
            double anchorX = 10.0;
            double span = 10.0;
            var wholeNoteWidthSs = SMuFLMetadata.requireBBox(SMuFLGlyph.NOTEHEAD_WHOLE).right();

            assertThat(endingEndingOn(ElementType.SEMIBREVE).getSpanWidthSs(anchorX, anchorX + span))
                .as("a whole note's head is wider, so the bracket must reach further")
                .isGreaterThan(ending.getSpanWidthSs(anchorX, anchorX + span))
                .isEqualTo(span + wholeNoteWidthSs);
        }

        // The lower bound is a generic minimum bracket width, not the end note's head: a grace head
        // is narrower than the floor, and a zero-length span on one still yields the floor.
        @Test
        void testMinimumSpanFloorDoesNotTrackTheEndNoteheadWidth() {
            double anchorX = 10.0;
            var graceEnding = endingEndingOn(ElementType.GRACE_QUAVER);

            assertThat(ElementType.GRACE_QUAVER.getElementWidthSs())
                .as("precondition: the grace head must be narrower than the floor to exercise it")
                .isLessThan(SMuFLConstants.NOTE_HEAD_WIDTH_SS);
            assertThat(graceEnding.getSpanWidthSs(anchorX, anchorX))
                .isEqualTo(SMuFLConstants.NOTE_HEAD_WIDTH_SS);
        }
    }

    // -------------------------------------------------------------------------
    // Row 13 — findRepeatSplitElement(Line line)
    // -------------------------------------------------------------------------

    @Nested
    class FindRepeatSplitElement {

        @Test
        void testNoRepeatInSpanReturnsNull() {
            var song = new Song();
            var line = song.getLine(0);
            var anchor = new StaffElement(ElementType.CROTCHET);
            var mid = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var ending = new Ending(anchor, end);
            song.withoutMutationTracking(() -> {
                line.addElement(anchor);
                line.addElement(mid);
                line.addElement(end);
                line.addRangeElement(ending);
            });

            assertThat(ending.findRepeatSplitElement(line)).isNull();
        }

        @Test
        void testRepeatRightInSpanReturnsElement() {
            var fixture = EndingLineFixture.primary();
            assertThat(fixture.ending().findRepeatSplitElement(fixture.line()))
                .isSameAs(fixture.split());
        }

        @Test
        void testRepeatLeftRightInSpanReturnsElement() {
            var fixture = EndingLineFixture.secondary();
            assertThat(fixture.ending().findRepeatSplitElement(fixture.line()))
                .isSameAs(fixture.split());
        }

        @Test
        void testRepeatAtEndIndexExcludedFromScanReturnsNull() {
            // Scan is range(anchorIndex + 1, endIndex) — endIndex is exclusive.
            // A REPEAT_RIGHT exactly at the end index must NOT be found.
            var song = new Song();
            var line = song.getLine(0);
            var anchor = new StaffElement(ElementType.CROTCHET);
            var mid = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.REPEAT_RIGHT);
            var ending = new Ending(anchor, end);
            song.withoutMutationTracking(() -> {
                line.addElement(anchor);
                line.addElement(mid);
                line.addElement(end);
                line.addRangeElement(ending);
            });

            assertThat(ending.findRepeatSplitElement(line)).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // Row 14 — getSplitIndex(Line line)
    // -------------------------------------------------------------------------

    @Nested
    class GetSplitIndex {

        @Test
        void testSplitLessEndingThrows() {
            // Every ending must have a REPEAT splitting its two brackets (issue #306).
            // A span with no interior REPEAT is corrupt state and must throw rather than
            // silently degrade — the contract StructuralStacker/MidiSequenceBuilder rely on.
            var song = new Song();
            var line = song.getLine(0);
            var anchor = new StaffElement(ElementType.CROTCHET);
            var mid = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var ending = new Ending(anchor, end);
            song.withoutMutationTracking(() -> {
                line.addElement(anchor);
                line.addElement(mid);
                line.addElement(end);
                line.addRangeElement(ending);
            });

            assertThatThrownBy(() -> ending.getSplitIndex(line))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no split element");
        }

        @Test
        void testEndingWithSplitReturnsSplitIndex() {
            var fixture = EndingLineFixture.primary();

            assertThat(fixture.ending().getSplitIndex(fixture.line()))
                .isEqualTo(fixture.line().getElementIndex(fixture.split()));
        }
    }

    // -------------------------------------------------------------------------
    // Row 15 — computeCollisionRegions(BracketRange, double)
    // -------------------------------------------------------------------------

    @Nested
    class ComputeCollisionRegions {

        @Test
        void testNoClosingStrokeProducesThreeRegions() {
            // No closing stroke → bar, left-tick, label = 3 regions
            double xBase = 5.0;
            double span = 10.0;
            var bracket = new Ending.BracketRange(xBase, xBase + span, 1, false);
            var ending = minimalEnding();

            var regions = ending.computeCollisionRegions(bracket, xBase);

            assertThat(regions).hasSize(3);
            assertThat(regions.get(0).xOffsetSs()).isEqualTo(xBase);
            assertThat(regions.get(1).xOffsetSs()).isEqualTo(xBase);
            assertThat(regions.get(2).xOffsetSs()).isEqualTo(xBase + Ending.LABEL_X_INSET_SS);
        }

        @Test
        void testClosingStrokeProducesFourRegionsWithRightTick() {
            // Closing stroke → bar, left-tick, right-tick, label = 4 regions
            double xBase = 5.0;
            double span = 10.0;
            var bracket = new Ending.BracketRange(xBase, xBase + span, 1, true);
            var ending = minimalEnding();

            var regions = ending.computeCollisionRegions(bracket, xBase);

            assertThat(regions).hasSize(4);
            double expectedRightTickX = xBase + span - LineThickness.VOLTA_BRACKET_SS;
            assertThat(regions.get(2).xOffsetSs()).isEqualTo(expectedRightTickX);
            assertThat(regions.get(3).xOffsetSs()).isEqualTo(xBase + Ending.LABEL_X_INSET_SS);
        }

        private Ending minimalEnding() {
            var song = new Song();
            var line = song.getLine(0);
            var anchor = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var ending = new Ending(anchor, end);
            song.withoutMutationTracking(() -> {
                line.addElement(anchor);
                line.addElement(end);
                line.addRangeElement(ending);
            });
            return ending;
        }
    }

    // -------------------------------------------------------------------------
    // Row 14 — computeBracketRanges(Line, Function<StaffElement, ElementColumn>)
    // -------------------------------------------------------------------------

    @Nested
    class ComputeBracketRanges {

        // Arbitrary extents used to stand in for "has an accidental" (left extent negative,
        // extending further left than the glyph origin) and "has an augmentation dot" (right
        // extent wider than a bare notehead) in the note-anchor geometry tests below.
        private static final double ACCIDENTAL_LEFT_EXTENT_SS = -2.5;
        private static final double DOTTED_RIGHT_EXTENT_SS = SMuFLConstants.NOTE_HEAD_WIDTH_SS + 1.0;

        /** Maps elements to columns at their assigned X positions, with bare-notehead extents. */
        private Function<StaffElement, ElementColumn> columnMap(
            StaffElement[] elements, double[] xs
        ) {
            return columnMap(elements, xs, null, null);
        }

        /**
         * Maps elements to columns at their assigned X positions. {@code leftExtents} and
         * {@code rightExtents} may be {@code null} (bare-notehead extents: 0.0 left,
         * {@code NOTE_HEAD_WIDTH_SS} right) or parallel arrays overriding specific elements.
         */
        private Function<StaffElement, ElementColumn> columnMap(
            StaffElement[] elements, double[] xs,
            double @Nullable [] leftExtents, double @Nullable [] rightExtents
        ) {
            return el -> {
                for (int i = 0; i < elements.length; i++) {
                    if (elements[i] == el) {
                        var leftExtentSs = leftExtents == null ? 0.0 : leftExtents[i];
                        var rightExtentSs = rightExtents == null
                            ? SMuFLConstants.NOTE_HEAD_WIDTH_SS
                            : rightExtents[i];
                        var column = new ElementColumn(
                            el, List.of(), leftExtentSs, rightExtentSs, rightExtentSs,
                            0, 0, null, 0, false);
                        column.setXSs(xs[i]);
                        return column;
                    }
                }
                throw new IllegalArgumentException("No column for element: " + el);
            };
        }

        @Test
        void testSplitProducesTwoBracketsWithGapAtRepeat() {
            // Primary fixture: anchor=SINGLE_BARLINE(0), note1(1), note2(2),
            //                  split=REPEAT_RIGHT(3), note4(4), note5(5), end=SINGLE_BARLINE(6)
            var fixture = EndingLineFixture.primary();
            var line = fixture.line();
            var ending = fixture.ending();

            double anchorX = 10.0;
            double note1X = 20.0;
            double note2X = 30.0;
            double splitX = 40.0;
            double note4X = 50.0;
            double note5X = 60.0;
            double endX = 70.0;
            var elements = new StaffElement[]{
                fixture.anchor(), fixture.note1(), fixture.note2(),
                fixture.split(), fixture.note4(), fixture.note5(), fixture.end()
            };
            var xs = new double[]{anchorX, note1X, note2X, splitX, note4X, note5X, endX};

            var ranges = ending.computeBracketRanges(line, columnMap(elements, xs));

            assertThat(ranges).hasSize(2);

            // First bracket: anchor=SINGLE_BARLINE at idx 0 (no prev), isBarLine → offset applied
            double expectedX1 = anchorX + ElementType.SINGLE_BARLINE.endingAnchorXOffsetSs();
            double expectedX2First = splitX + LineThickness.REPEAT_RIGHT_THIN_BARLINE_CENTER_X_SS;
            var bracket1 = ranges.get(0);
            assertThat(bracket1.number()).isEqualTo(1);
            assertThat(bracket1.x1Ss()).isEqualTo(expectedX1);
            assertThat(bracket1.x2Ss()).isEqualTo(expectedX2First);

            // Second bracket: starts after repeat thick barline, end=SINGLE_BARLINE → no closing stroke
            double expectedX1Second = splitX + LineThickness.REPEAT_RIGHT_AFTER_THICK_X_SS - LineThickness.VOLTA_BRACKET_SS / 2;
            double expectedX2Second = endX + LineThickness.THIN_BARLINE_SS / 2;
            var bracket2 = ranges.get(1);
            assertThat(bracket2.number()).isEqualTo(2);
            assertThat(bracket2.x1Ss()).isEqualTo(expectedX1Second);
            assertThat(bracket2.x2Ss()).isEqualTo(expectedX2Second);
            assertThat(bracket2.hasClosingStroke()).isFalse();
        }

        @Test
        void testStartAdjustPullsBracketLeftwardToBarline() {
            // Line: [SINGLE_BARLINE(prev,0), CROTCHET(anchor,1), REPEAT_RIGHT(split,2),
            //        CROTCHET(end,3), FINAL_DOUBLE_BARLINE(4)]
            // Previous element of anchor is a barline → start adjusted leftward to idx 0.
            // A REPEAT_RIGHT splits the two brackets so this is a valid ending.
            var song = new Song();
            var line = song.getLine(0);
            var prev = new StaffElement(ElementType.SINGLE_BARLINE);
            var anchor = new StaffElement(ElementType.CROTCHET);
            var split = new StaffElement(ElementType.REPEAT_RIGHT);
            var end = new StaffElement(ElementType.CROTCHET);
            var ending = new Ending(anchor, end);
            song.withoutMutationTracking(() -> {
                line.addElement(prev);
                line.addElement(anchor);
                line.addElement(split);
                line.addElement(end);
                line.addRangeElement(ending);
            });
            // FINAL_DOUBLE_BARLINE at last index (added by Song)
            var terminal = line.getElement(line.elementCount() - 1);

            double prevX = 5.0;
            double anchorX = 15.0;
            double splitX = 25.0;
            double endX = 35.0;
            double terminalX = 45.0;
            var elements = new StaffElement[]{prev, anchor, split, end, terminal};
            var xs = new double[]{prevX, anchorX, splitX, endX, terminalX};

            var ranges = ending.computeBracketRanges(line, columnMap(elements, xs));

            assertThat(ranges).hasSize(2);
            // First bracket x1 anchored to the prev barline, not the anchor note
            double expectedX1 = prevX + ElementType.SINGLE_BARLINE.endingAnchorXOffsetSs();
            assertThat(ranges.get(0).x1Ss()).isEqualTo(expectedX1);
            // and it is left of the anchor — confirming the leftward pull
            assertThat(ranges.get(0).x1Ss()).isLessThan(anchorX);
        }

        @Test
        void testRepeatRightEndGivesClosingStrokeOnSecondBracket() {
            // Secondary fixture: split=REPEAT_LEFT_RIGHT(3), end=REPEAT_RIGHT(6)
            // REPEAT_RIGHT end → hasClosingStroke=true on the second bracket
            var fixture = EndingLineFixture.secondary();
            var line = fixture.line();
            var ending = fixture.ending();

            double anchorX = 10.0;
            double note1X = 20.0;
            double note2X = 30.0;
            double splitX = 40.0;
            double note4X = 50.0;
            double note5X = 60.0;
            double endX = 70.0;
            var elements = new StaffElement[]{
                fixture.anchor(), fixture.note1(), fixture.note2(),
                fixture.split(), fixture.note4(), fixture.note5(), fixture.end()
            };
            var xs = new double[]{anchorX, note1X, note2X, splitX, note4X, note5X, endX};

            var ranges = ending.computeBracketRanges(line, columnMap(elements, xs));

            assertThat(ranges).hasSize(2);
            assertThat(ranges.get(1).hasClosingStroke()).isTrue();
        }

        @Test
        void testSplitLessEndingThrows() {
            // computeBracketRanges enforces the same "every ending has a split" invariant as
            // getSplitIndex (issue #306): a span with no interior REPEAT throws rather than
            // producing a degenerate single bracket. StructuralStacker calls this per ending.
            var line = detachedLine();
            var anchor = new StaffElement(ElementType.CROTCHET);
            var mid = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var ending = new Ending(anchor, end);
            line.addElement(anchor);
            line.addElement(mid);
            line.addElement(end);
            line.addRangeElement(ending);

            var elements = new StaffElement[]{anchor, mid, end};
            var xs = new double[]{10.0, 20.0, 30.0};

            assertThatThrownBy(() -> ending.computeBracketRanges(line, columnMap(elements, xs)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no split element");
        }

        // -------------------------------------------------------------------------
        // #306 — note-anchored outer edges
        // -------------------------------------------------------------------------

        @Test
        void testFirstBracketStartingOnNoteWithAccidentalUsesLeftExtentFormula() {
            // Line: [CROTCHET(anchor,0, has accidental), CROTCHET(note2,1),
            //        REPEAT_RIGHT(split,2), CROTCHET(note4,3), CROTCHET(end,4)]
            // Detached line (no auto-appended terminal) — no element precedes the anchor, so it
            // is not pulled onto a barline — the 1st bracket's left edge anchors directly to the
            // note via the left-extent formula.
            var line = detachedLine();
            var anchor = new StaffElement(ElementType.CROTCHET);
            var note2 = new StaffElement(ElementType.CROTCHET);
            var split = new StaffElement(ElementType.REPEAT_RIGHT);
            var note4 = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var ending = new Ending(anchor, end);
            line.addElement(anchor);
            line.addElement(note2);
            line.addElement(split);
            line.addElement(note4);
            line.addElement(end);
            line.addRangeElement(ending);

            double anchorX = 10.0;
            var elements = new StaffElement[]{anchor, note2, split, note4, end};
            var xs = new double[]{
                anchorX, anchorX + 10.0, anchorX + 20.0, anchorX + 30.0, anchorX + 40.0
            };
            // Only the anchor has an accidental-inclusive left extent; the rest are bare noteheads.
            var leftExtents = new double[]{ACCIDENTAL_LEFT_EXTENT_SS, 0.0, 0.0, 0.0, 0.0};

            var ranges = ending.computeBracketRanges(
                line, columnMap(elements, xs, leftExtents, null));

            assertThat(ranges).hasSize(2);
            var expectedX1 = anchorX + ACCIDENTAL_LEFT_EXTENT_SS - NoteGeometry.ACCIDENTAL_PADDING_SS;
            assertThat(ranges.get(0).x1Ss()).isEqualTo(expectedX1);
        }

        @Test
        void testFirstBracketStartingOnBareNoteheadUsesLeftExtentFormula() {
            // Same as the accidental case but the anchor is a bare notehead (left extent 0.0),
            // so x1 collapses to anchorX - ACCIDENTAL_PADDING_SS — the plan flagged this exact
            // bare-head case as one to confirm keeps a uniform gap.
            var line = detachedLine();
            var anchor = new StaffElement(ElementType.CROTCHET);
            var note2 = new StaffElement(ElementType.CROTCHET);
            var split = new StaffElement(ElementType.REPEAT_RIGHT);
            var note4 = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var ending = new Ending(anchor, end);
            line.addElement(anchor);
            line.addElement(note2);
            line.addElement(split);
            line.addElement(note4);
            line.addElement(end);
            line.addRangeElement(ending);

            double anchorX = 10.0;
            var elements = new StaffElement[]{anchor, note2, split, note4, end};
            var xs = new double[]{
                anchorX, anchorX + 10.0, anchorX + 20.0, anchorX + 30.0, anchorX + 40.0
            };

            // All bare noteheads (left extent 0.0) — no accidental override.
            var ranges = ending.computeBracketRanges(line, columnMap(elements, xs));

            assertThat(ranges).hasSize(2);
            var expectedX1 = anchorX - NoteGeometry.ACCIDENTAL_PADDING_SS;
            assertThat(ranges.get(0).x1Ss()).isEqualTo(expectedX1);
        }

        @Test
        void testSecondBracketEndingOnNoteWithAugmentationDotUsesRightExtentFormulaAndNoClosingStroke() {
            // Line: [SINGLE_BARLINE(anchor,0), CROTCHET(note2,1), REPEAT_RIGHT(split,2),
            //        CROTCHET(note4,3), CROTCHET(end,4, has augmentation dot)]
            // Detached line (no auto-appended terminal) — no element follows the end note, so it
            // is not pulled onto a trailing barline — the 2nd bracket's right edge anchors
            // directly to the note via the right-extent formula, with no closing stroke.
            var line = detachedLine();
            var anchor = new StaffElement(ElementType.SINGLE_BARLINE);
            var note2 = new StaffElement(ElementType.CROTCHET);
            var split = new StaffElement(ElementType.REPEAT_RIGHT);
            var note4 = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var ending = new Ending(anchor, end);
            line.addElement(anchor);
            line.addElement(note2);
            line.addElement(split);
            line.addElement(note4);
            line.addElement(end);
            line.addRangeElement(ending);

            double anchorX = 10.0;
            var elements = new StaffElement[]{anchor, note2, split, note4, end};
            var xs = new double[]{
                anchorX, anchorX + 10.0, anchorX + 20.0, anchorX + 30.0, anchorX + 40.0
            };
            var endX = xs[4];
            // Only the end note has a dot-inclusive right extent; the rest are bare noteheads.
            var rightExtents = new double[]{
                SMuFLConstants.NOTE_HEAD_WIDTH_SS, SMuFLConstants.NOTE_HEAD_WIDTH_SS,
                SMuFLConstants.NOTE_HEAD_WIDTH_SS, SMuFLConstants.NOTE_HEAD_WIDTH_SS,
                DOTTED_RIGHT_EXTENT_SS
            };

            var ranges = ending.computeBracketRanges(
                line, columnMap(elements, xs, null, rightExtents));

            assertThat(ranges).hasSize(2);
            var bracket2 = ranges.get(1);
            var expectedX2 = endX + DOTTED_RIGHT_EXTENT_SS + SMuFLConstants.AUGMENTATION_DOT_WIDTH_SS;
            assertThat(bracket2.x2Ss()).isEqualTo(expectedX2);
            assertThat(bracket2.hasClosingStroke()).isFalse();
        }
    }
}
