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

import java.util.function.ToDoubleFunction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.engraving.LineThickness;
import songscribe.engraving.SMuFLConstants;

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
    // Row 14 — computeBracketRanges(Line, ToDoubleFunction)
    // -------------------------------------------------------------------------

    @Nested
    class ComputeBracketRanges {

        /** Maps elements to their assigned X positions. */
        private ToDoubleFunction<StaffElement> positionMap(
            StaffElement[] elements, double[] xs
        ) {
            return el -> {
                for (int i = 0; i < elements.length; i++) {
                    if (elements[i] == el) {
                        return xs[i];
                    }
                }
                throw new IllegalArgumentException("No X for element: " + el);
            };
        }

        @Test
        void testNoSplitProducesSingleBracketWithExpectedCoords() {
            // Line: [CROTCHET(anchor,0), CROTCHET(end,1), FINAL_DOUBLE_BARLINE(2)]
            // Song always appends a terminal FINAL_DOUBLE_BARLINE; the next-element midpoint path fires.
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
            // FINAL_DOUBLE_BARLINE was added by Song; it is at index 2
            var terminal = line.getElement(line.elementCount() - 1);

            double anchorX = 10.0;
            double endX = 20.0;
            double terminalX = 30.0;
            var elements = new StaffElement[]{anchor, end, terminal};
            var xs = new double[]{anchorX, endX, terminalX};

            var ranges = ending.computeBracketRanges(line, positionMap(elements, xs));

            // anchor=CROTCHET at index 0, not barline, no previous → x1 = anchorX
            // end has a next element (terminal) → x2 = endX + (terminalX - endX) / 2
            assertThat(ranges).hasSize(1);
            var bracket = ranges.get(0);
            assertThat(bracket.number()).isEqualTo(1);
            assertThat(bracket.x1Ss()).isEqualTo(anchorX);
            assertThat(bracket.x2Ss()).isEqualTo(endX + (terminalX - endX) / 2.0);
            assertThat(bracket.hasClosingStroke()).isTrue();
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

            var ranges = ending.computeBracketRanges(line, positionMap(elements, xs));

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
            // Line: [SINGLE_BARLINE(prev,0), CROTCHET(anchor,1), CROTCHET(mid,2),
            //        CROTCHET(end,3), FINAL_DOUBLE_BARLINE(4)]
            // Previous element of anchor is a barline → start adjusted leftward to idx 0
            var song = new Song();
            var line = song.getLine(0);
            var prev = new StaffElement(ElementType.SINGLE_BARLINE);
            var anchor = new StaffElement(ElementType.CROTCHET);
            var mid = new StaffElement(ElementType.CROTCHET);
            var end = new StaffElement(ElementType.CROTCHET);
            var ending = new Ending(anchor, end);
            song.withoutMutationTracking(() -> {
                line.addElement(prev);
                line.addElement(anchor);
                line.addElement(mid);
                line.addElement(end);
                line.addRangeElement(ending);
            });
            // FINAL_DOUBLE_BARLINE at last index (added by Song)
            var terminal = line.getElement(line.elementCount() - 1);

            double prevX = 5.0;
            double anchorX = 15.0;
            double midX = 25.0;
            double endX = 35.0;
            double terminalX = 45.0;
            var elements = new StaffElement[]{prev, anchor, mid, end, terminal};
            var xs = new double[]{prevX, anchorX, midX, endX, terminalX};

            var ranges = ending.computeBracketRanges(line, positionMap(elements, xs));

            assertThat(ranges).hasSize(1);
            // x1 anchored to the prev barline, not the anchor note
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

            var ranges = ending.computeBracketRanges(line, positionMap(elements, xs));

            assertThat(ranges).hasSize(2);
            assertThat(ranges.get(1).hasClosingStroke()).isTrue();
        }
    }
}
