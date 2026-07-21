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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.crotchet;

import module java.desktop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.StaffElement;
import songscribe.font.DocumentFonts;
import songscribe.layout.ElementColumn;
import songscribe.layout.ElementColumnBuilder;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.InsertionSpacingCalculator;

class InsertionSpacingCalculatorTest extends UnitTest {

    /** A line width wide enough that any insertion or fall comfortably fits. */
    private static final double WIDE_LINE_SS = 500;

    /** Extra gap, in staff spaces, before the following element so a fall cannot push it. */
    private static final double DISTANT_LAST_ELEMENT_GAP_SS = 50;

    /** Staff-space offset by which a test layout reports an element to the right of its xOffset. */
    private static final double LAYOUT_SHIFT_SS = 10;

    /** Staff spaces either side of a fit boundary, clear of float dust in the solver. */
    private static final double BOUNDARY_SLACK_SS = 1;

    /** Staff spaces by which a compress-to-fit margin undercuts the uncompressed width. */
    private static final double NARROWER_MARGIN_SS = 1;

    /** Right margin for the preview-vs-committed {@link LayoutEngine}, wide enough that a few crotchets never overflow. */
    private static final double PREVIEW_STAFF_RIGHT_MARGIN_SS = 100.0;

    /** Float tolerance for comparing preview and committed X positions (Ss). */
    private static final double PREVIEW_TOLERANCE_SS = 0.001;

    /** Line length for the paste-replace negative-shift fixture: predecessor + wide deleted range + successor. */
    private static final int NEGATIVE_SHIFT_LINE_ELEMENT_COUNT = 6;

    /** Last index of the wide deleted range in the negative-shift fixture. */
    private static final int NEGATIVE_SHIFT_DELETE_RANGE_END_INDEX = 4;

    /** Line length for the multi-element-fragment + deleteRange fixture: predecessor, deleted range, successor. */
    private static final int MULTI_FRAGMENT_DELETE_LINE_ELEMENT_COUNT = 5;

    /** Last index of the deleted range in the multi-element-fragment fixture. */
    private static final int MULTI_FRAGMENT_DELETE_RANGE_END_INDEX = 3;

    /**
     * Line length for the {@code calculateFragmentInsertion} validation fixtures — large enough
     * that a {@link InsertionSpacingCalculator.DeletedRange}'s begin and end can each be made
     * invalid independently of the other.
     */
    private static final int VALIDATION_LINE_ELEMENT_COUNT = 4;

    /**
     * Returns the narrowest margin an insertion can still be laid out within: every projected gap
     * frozen on its strut, anchored at the projected first X, with the last column's right extent
     * inside the margin. This is the boundary {@code fitsWithinLine} solves against.
     */
    private static double fullyCompressedWidthSs(InsertionSpacingCalculator.InsertionResult result) {
        // A rigid gap (grace→host) is pinned to its natural length, not its strut — mirroring
        // SpringSpacer.compress, so this is the solver's true floor rather than an underestimate.
        var floorSpanSs = result.projectedSprings().stream()
            .mapToDouble(spring -> spring.rigid() ? spring.naturalLengthSs() : spring.strutSs())
            .sum();
        return result.projectedFirstXSs() + floorSpanSs + result.projectedLastRightExtentSs();
    }

    /**
     * Real lyric-render metrics for the preview-vs-committed equality test — shared by
     * {@link #layoutEngine} and the direct {@code calculateInsertion} preview call, so both see
     * identical metrics.
     */
    private static LyricRenderMetrics lyricRenderMetrics() {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        var hyphenWidthSs = ScaleContext.textWidthSs(lyricsFont, "-").value();
        var spaceWidthSs = ScaleContext.textWidthSs(lyricsFont, " ").value();
        return new LyricRenderMetrics(
            lyricsFont,
            ScaleContext.scaleFont(lyricsFont),
            hyphenWidthSs,
            spaceWidthSs,
            LineSpacing.LYRICS_ROW_MARGIN_SS + LyricRenderMetrics.fontAboveBaselineSs(lyricsFont));
    }

    /** A {@link LayoutEngine} with real lyric-render metrics, for the preview-vs-committed equality test. */
    private static LayoutEngine layoutEngine() {
        return new LayoutEngine(lyricRenderMetrics(), PREVIEW_STAFF_RIGHT_MARGIN_SS, DocumentFonts.defaultFonts());
    }

    /** Asserts value is not null and returns it non-null for NullAway. */
    @SuppressWarnings("NullAway")
    private static LayoutResult requireLayout(@Nullable LayoutResult result) {
        assertThat(result).isNotNull();
        return result;
    }

    /**
     * A minimal song mock that behaves like a default song for the layout inputs the insertion
     * path reads: mutation tracking suspended and the default line rest (so derived base rests are
     * realistic rather than the {@code 0} a bare mock would return).
     */
    private static Song songMock() {
        var song = mock(Song.class);
        when(song.isMutationTrackingSuspended()).thenReturn(true);
        when(song.getDefaultRestLengthSs()).thenReturn(Song.DEFAULT_REST_LENGTH_SS);
        return song;
    }

    /** Returns a minimal song mock with the given line width stubbed. */
    private static Song songWithLineWidth(double lineWidthSs) {
        var song = songMock();
        when(song.getLineWidthSs()).thenReturn(lineWidthSs);
        return song;
    }

    /**
     * Creates a line with the given number of crotchets, positioned using the
     * standard spacing algorithm.
     */
    private static Line lineWithCrotchets(int count) {
        return lineWithCrotchets(count, detachedLine());
    }

    private static Line lineWithCrotchets(int count, Song song) {
        return lineWithCrotchets(count, new Line(song));
    }

    private static Line lineWithCrotchets(int count, Line line) {
        for (var i = 0; i < count; i++) {
            var element = crotchet();
            var xSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, element, null, null);
            element.setXOffsetPx(ScaleContext.ssToRoundedPx(xSs));
            line.addElement(element);
        }

        return line;
    }

    /**
     * Creates a line with the given number of crotchets followed by one grace note,
     * all positioned using the standard spacing algorithm.
     */
    private static Line lineWithGraceAtIndex(int numCrotchetsBefore) {
        return lineWithGraceAtIndex(numCrotchetsBefore, detachedLine());
    }

    private static Line lineWithGraceAtIndex(int numCrotchetsBefore, Song song) {
        return lineWithGraceAtIndex(numCrotchetsBefore, new Line(song));
    }

    private static Line lineWithGraceAtIndex(int numCrotchetsBefore, Line line) {
        lineWithCrotchets(numCrotchetsBefore, line);
        var grace = ElementType.GRACE_QUAVER.newInstance();
        var xSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, grace, null, null);
        grace.setXOffsetPx(ScaleContext.ssToRoundedPx(xSs));
        line.addElement(grace);
        return line;
    }

    /**
     * The X the spring engine gives {@code curr} when placed after {@code prev} at its current X —
     * the same derivation the production insertion path runs.
     */
    private static double springNextColumnXSs(ElementColumn prev, ElementColumn curr) {
        return prev.getXSs()
            + HorizontalSpacingCalculator.buildSpring(prev, curr, Song.DEFAULT_REST_LENGTH_SS).naturalLengthSs();
    }

    /**
     * Computes the expected shift when inserting {@code insertedElement} at index 0, using
     * the same column construction path that InsertionSpacingCalculator uses internally.
     * <p>
     * Shift = springNextColumnXSs(insertedColumn@insertedXSs, existingColumn) - existingXSs.
     */
    private static double expectedShiftFromInsertAtZero(
        StaffElement insertedElement, double insertedXSs,
        StaffElement existingElement, double existingXSs) {

        var insertedLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(insertedElement);
        var insertedRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
            insertedElement, false, insertedElement.getDirection());
        var insertedColumn = new ElementColumn(
            insertedElement, Collections.emptyList(),
            insertedLeftExtentSs, insertedRightExtentSs, 0, 0, null, 0, false);
        insertedColumn.setXSs(insertedXSs);

        var existingLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(existingElement);
        var existingRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
            existingElement, false, existingElement.getDirection());
        var existingColumn = new ElementColumn(
            existingElement, Collections.emptyList(),
            existingLeftExtentSs, existingRightExtentSs, 0, 0, null, 0, false);

        var insertedToNextSs = springNextColumnXSs(insertedColumn, existingColumn);
        return Math.max(0, insertedToNextSs - existingXSs);
    }

    /**
     * Returns the right edge of the last element on the line.
     */
    private static double lastElementRightEdgeSs(Line line) {
        var last = line.getElement(line.elementCount() - 1);
        var leftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(last);
        var rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(last, false, last.getDirection());
        var column = new ElementColumn(
            last, Collections.emptyList(), leftExtentSs, rightExtentSs, 0, 0, null, 0, false
        );
        column.setXSs(ScaleContext.pxToSs(last.getXOffsetPx()));
        return column.getRightEdgeXSs();
    }

    /**
     * Returns the right edge of {@code element} at {@code xSs} with a fall temporarily applied
     * and then removed — the same fall reservation {@code hasRoomForFall} measures internally.
     */
    private static double rightEdgeWithFallSs(StaffElement element, double xSs) {
        element.setFall();
        var leftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(element);
        var rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(element, false, element.getDirection());
        element.removeSlide();

        var column = new ElementColumn(
            element, Collections.emptyList(), leftExtentSs, rightExtentSs, 0, 0, null, 0, false);
        column.setXSs(xSs);
        return column.getRightEdgeXSs();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateInsertion {

        @Test
        void testNegativeIndexThrowsIllegalArgumentException() {
            var line = lineWithCrotchets(2);
            assertThatIllegalArgumentException()
                .isThrownBy(() -> InsertionSpacingCalculator.calculateInsertion(line, crotchet(), -1, null, null));
        }

        @Test
        void testIndexBeyondCountThrowsIllegalArgumentException() {
            var line = lineWithCrotchets(2);
            var beyondCount = line.elementCount() + 1;
            assertThatIllegalArgumentException()
                .isThrownBy(() -> InsertionSpacingCalculator.calculateInsertion(line, crotchet(), beyondCount, null, null));
        }

        @Test
        void testInsertAtIndexZeroReturnsFirstElementX() {
            // Row 39: inserted element at index 0 must land at calculateFirstElementXSs.
            var line = lineWithCrotchets(1);
            var expectedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());

            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 0, null, null);

            assertThat(result.insertedElementXSs()).isEqualTo(expectedXSs);
        }

        @Test
        void testInsertAtIndexZeroShiftsFollowingElement() {
            // Row 39: the shift applied to the element that was at index 0 must equal
            // (insertedToNextSs - existingFirstXSs), where insertedToNextSs is derived from
            // the exact (un-rounded) first-element X, and existingFirstXSs is the px-rounded
            // X stored when lineWithCrotchets placed the crotchet.
            var line = lineWithCrotchets(1);
            var insertedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
            var existingElement = line.getElement(0);
            var existingXSs = ScaleContext.pxToSs(existingElement.getXOffsetPx());
            // Build a lightweight column for the inserted crotchet at the exact first-element X.
            var insertedCrotchet = crotchet();
            var expectedShiftSs = expectedShiftFromInsertAtZero(insertedCrotchet, insertedXSs, existingElement, existingXSs);

            var result = InsertionSpacingCalculator.calculateInsertion(line, insertedCrotchet, 0, null, null);

            assertThat(result.shiftForSubsequentElementsSs()).isEqualTo(expectedShiftSs);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FitsWithinLine {

        @Test
        void testAppendToEmptyLineReturnsFirstElementX() {
            // Row 35: when the line is empty, calculateAppendPositionSs must equal
            // calculateFirstElementXSs for the line's key-accidental count.
            var line = lineWithCrotchets(0);
            var expectedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
            var actualXSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, crotchet(), null, null);
            assertThat(actualXSs).isEqualTo(expectedXSs);
        }

        @Test
        void testAppendToEmptyLineFitsWithinLargeLine() {
            var line = lineWithCrotchets(0);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 0, null, null);
            assertThat(result.fitsWithinLine(WIDE_LINE_SS)).isTrue();
        }

        @Test
        void testInsertIntoNearlyFullLineCompressesToFit() {
            // Compress-to-fit: a margin narrower than the element's uncompressed projected width
            // is accepted, because the line's gaps still hold slack above their struts. The
            // retired fixed-margin rule rejected exactly this case.
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null, null);
            assertThat(result.fitsWithinLine(result.newLineWidthSs() - NARROWER_MARGIN_SS)).isTrue();
        }

        @Test
        void testInsertFitsJustAboveFullyCompressedBoundary() {
            // The passing boundary is the fully compressed line: every gap at its strut, anchored
            // at the projected first X, with room for the last column's own glyph.
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null, null);
            assertThat(result.fitsWithinLine(fullyCompressedWidthSs(result) + BOUNDARY_SLACK_SS)).isTrue();
        }

        @Test
        void testInsertRejectedJustBelowFullyCompressedBoundary() {
            // One staff space below the fully compressed width, even freezing every gap on its
            // collision floor overflows the margin — the only case that now rejects an insert.
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null, null);
            assertThat(result.fitsWithinLine(fullyCompressedWidthSs(result) - BOUNDARY_SLACK_SS)).isFalse();
        }

        @Test
        void testInsertWithPlentyOfRoom() {
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null, null);
            assertThat(result.fitsWithinLine(WIDE_LINE_SS)).isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasRoomForGraceNote {

        @Test
        void testEmptyLine() {
            var line = lineWithCrotchets(0, songWithLineWidth(WIDE_LINE_SS));
            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(line, 0, null, null)).isTrue();
        }

        @Test
        void testLineExactlyFullCompressesToFitTheGraceNote() {
            // A margin at the line's current right edge leaves no room at rest, but the crotchet
            // gaps still hold slack above their struts, so the grace note is admitted.
            var song = songMock();
            var line = lineWithCrotchets(3, song);
            when(song.getLineWidthSs()).thenReturn(lastElementRightEdgeSs(line));

            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(
                line, line.elementCount(), null, null)).isTrue();
        }

        @Test
        void testLineTooNarrowEvenFullyCompressed() {
            // Below the fully compressed width there is nowhere left to take space from.
            var song = songMock();
            var line = lineWithCrotchets(3, song);
            var graceNote = ElementType.GRACE_QUAVER.getInstance();
            var projection = InsertionSpacingCalculator.calculateInsertion(
                line, graceNote, line.elementCount(), null, null);
            when(song.getLineWidthSs()).thenReturn(fullyCompressedWidthSs(projection) - BOUNDARY_SLACK_SS);

            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(
                line, line.elementCount(), null, null)).isFalse();
        }

        @Test
        void testLineWithPlentyOfRoom() {
            var line = lineWithCrotchets(2, songWithLineWidth(WIDE_LINE_SS));
            assertThat(InsertionSpacingCalculator.hasRoomForGraceNote(line, 1, null, null)).isTrue();
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasRoomForHostNoteAfterGrace {

        @Test
        void testPlentyOfRoom() {
            var line = lineWithGraceAtIndex(0, songWithLineWidth(WIDE_LINE_SS));
            assertThat(InsertionSpacingCalculator.hasRoomForHostNoteAfterGrace(line, 0, null)).isTrue();
        }

        @Test
        void testNoRoomForHost() {
            // Below the fully compressed width the host note cannot be squeezed in at any gap.
            var song = songMock();
            var line = lineWithGraceAtIndex(2, song);
            var graceIndex = line.elementCount() - 1;
            var hostNote = ElementType.CROTCHET.getInstance();
            var projection = InsertionSpacingCalculator.calculateInsertion(
                line, hostNote, graceIndex + 1, null, null);
            when(song.getLineWidthSs()).thenReturn(fullyCompressedWidthSs(projection) - BOUNDARY_SLACK_SS);

            assertThat(InsertionSpacingCalculator.hasRoomForHostNoteAfterGrace(line, graceIndex, null)).isFalse();
        }
    }

    // Row 40: mid-insertion shift is max(0, required), never negative.
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertionShift {

        /**
         * Creates a line where two crotchets are placed far apart so that inserting
         * a third crotchet between them requires zero shift (raw required < 0).
         * <p>
         * The second crotchet is placed {@code extraGapSs} beyond the standard
         * first-element X, creating a gap that dwarfs the space needed by the inserted element.
         */
        private static Line lineWithWidelySpacedCrotchets(double secondElementXSs) {
            var line = detachedLine();
            var first = crotchet();
            var firstXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
            first.setXOffsetPx(ScaleContext.ssToRoundedPx(firstXSs));
            line.addElement(first);

            var second = crotchet();
            second.setXOffsetPx(ScaleContext.ssToRoundedPx(secondElementXSs));
            line.addElement(second);

            return line;
        }

        @Test
        void testMidInsertionShiftIsZeroWhenExistingGapIsAlreadyWide() {
            // Row 40 (negative clamp case): if prevElement and nextElement are far apart,
            // the raw required shift is negative; max(0, required) must clamp it to 0.
            var firstXSs = HorizontalSpacingCalculator
                .calculateFirstElementXSs(0);  // no key accidentals
            // Place the second crotchet very far right — far beyond where any inserted element
            // + its neighbour spacing would land.
            var largeGapSs = 50.0;
            var secondXSs = firstXSs + largeGapSs;
            var line = lineWithWidelySpacedCrotchets(secondXSs);

            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null, null);

            assertThat(result.shiftForSubsequentElementsSs()).isEqualTo(0.0);
        }

        @Test
        void testMidInsertionShiftIsPinnedPositiveValueForTightLine() {
            // Row 40 (positive case): inserting into a tightly-packed line produces a positive
            // shift, pinned to the exact computed value.
            // Line has 3 crotchets at indices 0, 1, 2. Insert at index 1 (between [0] and [1]).
            var line = lineWithCrotchets(3);
            var prevElement = line.getElement(0);
            var nextElement = line.getElement(1);

            // Compute insertedXSs: same algorithm the production code uses.
            var prevLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(prevElement);
            var prevRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                prevElement, false, prevElement.getDirection());
            var prevXSs = ScaleContext.pxToSs(prevElement.getXOffsetPx());
            var prevColumn = new ElementColumn(
                prevElement, Collections.emptyList(),
                prevLeftExtentSs, prevRightExtentSs, 0, 0, null, 0, false);
            prevColumn.setXSs(prevXSs);

            var inserted = crotchet();
            var insertedLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(inserted);
            var insertedRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                inserted, false, inserted.getDirection());
            var insertedColumn = new ElementColumn(
                inserted, Collections.emptyList(),
                insertedLeftExtentSs, insertedRightExtentSs, 0, 0, null, 0, false);

            var insertedXSs = springNextColumnXSs(prevColumn, insertedColumn);
            insertedColumn.setXSs(insertedXSs);

            var nextLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(nextElement);
            var nextRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                nextElement, false, nextElement.getDirection());
            var nextColumn = new ElementColumn(
                nextElement, Collections.emptyList(),
                nextLeftExtentSs, nextRightExtentSs, 0, 0, null, 0, false);

            var insertedToNextSs = springNextColumnXSs(insertedColumn, nextColumn);
            var nextXSs = ScaleContext.pxToSs(nextElement.getXOffsetPx());
            var expectedShiftSs = Math.max(0, insertedToNextSs - nextXSs);

            // Must be positive (elements are tightly packed — inserting requires a real shift)
            assertThat(expectedShiftSs).isGreaterThan(0.0);

            var result = InsertionSpacingCalculator.calculateInsertion(line, inserted, 1, null, null);

            assertThat(result.shiftForSubsequentElementsSs()).isEqualTo(expectedShiftSs);
        }
    }

    // Row 41: calculateNextElementXSs delegates to the spring engine's natural gap.
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateNextElementXSs {

        @Test
        void testDelegatesEquivalentToSpringNextColumnXSs() {
            // Row 41: calculateNextElementXSs(current, next) must equal
            // springNextColumnXSs(equivalentColumn, nextColumn)
            // for the same elements and positions — pinned to the delegate's exact output.
            var line = lineWithCrotchets(2);
            var currentElement = line.getElement(0);
            var nextElement = line.getElement(1);

            // Build equivalent columns exactly as the production code does internally.
            var currentLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(currentElement);
            var currentRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                currentElement, false, currentElement.getDirection());
            var currentColumn = new ElementColumn(
                currentElement, Collections.emptyList(),
                currentLeftExtentSs, currentRightExtentSs, 0, 0, null, 0, false);
            currentColumn.setXSs(ScaleContext.pxToSs(currentElement.getXOffsetPx()));

            var nextLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(nextElement);
            var nextRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                nextElement, false, nextElement.getDirection());
            var nextColumn = new ElementColumn(
                nextElement, Collections.emptyList(),
                nextLeftExtentSs, nextRightExtentSs, 0, 0, null, 0, false);

            var expectedXSs = springNextColumnXSs(currentColumn, nextColumn);

            var actualXSs = InsertionSpacingCalculator.calculateNextElementXSs(currentElement, nextElement);

            assertThat(actualXSs).isEqualTo(expectedXSs);
        }
    }

    // Row 42: InsertionResult.newLineWidthSs equals max(inserted right edge, shifted last right edge).
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class InsertionResultNewLineWidth {

        @Test
        void testNewLineWidthSsIsPinnedMaxOfInsertedAndShiftedLastRightEdge() {
            // Row 42: newLineWidthSs must equal exactly
            //   max(insertedColumn.rightEdgeXSs, lastColumn@(lastXSs + shiftSs).rightEdgeXSs).
            // Use a 3-crotchet line and insert at index 1.
            var line = lineWithCrotchets(3);
            var inserted = crotchet();

            var result = InsertionSpacingCalculator.calculateInsertion(line, inserted, 1, null, null);

            // Recompute the inserted element's column right edge from scratch.
            var insertedLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(inserted);
            var insertedRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                inserted, false, inserted.getDirection());
            var insertedColumn = new ElementColumn(
                inserted, Collections.emptyList(),
                insertedLeftExtentSs, insertedRightExtentSs, 0, 0, null, 0, false);
            insertedColumn.setXSs(result.insertedElementXSs());
            var insertedRightEdgeSs = insertedColumn.getRightEdgeXSs();

            // The effective last element excludes the FINAL_DOUBLE_BARLINE (auto-maintained),
            // so use effectiveElementCount — for a plain crotchet line this is element count.
            var lastIndex = line.effectiveElementCount() - 1;
            var lastElement = line.getElement(lastIndex);
            var lastLeftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(lastElement);
            var lastRightExtentSs = ElementColumnBuilder.calculateRightExtentSs(
                lastElement, false, lastElement.getDirection());
            var lastColumn = new ElementColumn(
                lastElement, Collections.emptyList(),
                lastLeftExtentSs, lastRightExtentSs, 0, 0, null, 0, false);
            var lastOriginalXSs = ScaleContext.pxToSs(lastElement.getXOffsetPx());
            lastColumn.setXSs(lastOriginalXSs + result.shiftForSubsequentElementsSs());
            var shiftedLastRightEdgeSs = lastColumn.getRightEdgeXSs();

            var expectedNewLineWidthSs = Math.max(insertedRightEdgeSs, shiftedLastRightEdgeSs);

            assertThat(result.newLineWidthSs()).isEqualTo(expectedNewLineWidthSs);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class HasRoomForFall {

        @Test
        void testFallAlreadyPresentAlwaysFits() {
            // A line too narrow for any element; the source already carries a fall, so applying
            // one is a no-op change in right extent and must short-circuit to true.
            var line = lineWithCrotchets(2, songWithLineWidth(0));
            line.getElement(0).setFall();

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 0, null))
                .as("fall already present short-circuits to fit even on a zero-width line")
                .isTrue();
        }

        @Test
        void testFallAtLastElementFitsAtExactBoundary() {
            // Source is the last element: the fall extends its own right edge only. Margin set to
            // exactly (fall right edge + required gap) is the passing boundary.
            var song = songMock();
            var line = lineWithCrotchets(3, song);
            var lastIndex = line.elementCount() - 1;
            var lastElement = line.getElement(lastIndex);
            var lastXSs = ScaleContext.pxToSs(lastElement.getXOffsetPx());
            var fallRightEdgeSs = rightEdgeWithFallSs(lastElement, lastXSs);
            var exactBoundarySs = fallRightEdgeSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(exactBoundarySs);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, lastIndex, null))
                .as("fall at last element fits when the margin equals its fall right edge plus the gap")
                .isTrue();
        }

        @Test
        void testFallAtLastElementDoesNotFitJustBelowBoundary() {
            // One staff space short of the passing boundary must fail.
            var song = songMock();
            var line = lineWithCrotchets(3, song);
            var lastIndex = line.elementCount() - 1;
            var lastElement = line.getElement(lastIndex);
            var lastXSs = ScaleContext.pxToSs(lastElement.getXOffsetPx());
            var fallRightEdgeSs = rightEdgeWithFallSs(lastElement, lastXSs);
            var exactBoundarySs = fallRightEdgeSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(exactBoundarySs - 1);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, lastIndex, null))
                .as("fall at last element fails one staff space below the passing boundary")
                .isFalse();
        }

        @Test
        void testFallMidLinePushesLastElementOverMargin() {
            // Source is not the last element: the fall's wider right extent shifts following
            // elements right. With the margin set to exactly fit the line as-is (no fall), that
            // shift pushes the last element past the margin.
            var song = songMock();
            var line = lineWithCrotchets(3, song);
            var snugWidthSs = lastElementRightEdgeSs(line) + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(snugWidthSs);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("fall mid-line pushes the last element past a snug margin")
                .isFalse();
        }

        @Test
        void testFallProjectsLastElementNotImmediateNext() {
            // Four elements with the fall at index 1: the immediate next (index 2) is not the last
            // (index 3). The overflow projection must target the LAST element — a margin snug to
            // its current right edge fails once the fall shifts the line right. Were the projection
            // wrongly applied to the immediate next element, its shifted edge would stay inside the
            // margin and the check would wrongly pass.
            var song = songMock();
            var line = lineWithCrotchets(4, song);
            var snugWidthSs = lastElementRightEdgeSs(line) + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(snugWidthSs);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("fall shift is projected onto the last element, not the immediate next")
                .isFalse();
        }

        @Test
        void testFallMidLineWithoutPushIgnoresDistantLastElement() {
            // The following element already sits far enough right that the fall cannot push it
            // (shiftSs == 0), so the result must depend only on the source's own fall right edge.
            // If the last-element projection ran regardless of shift, the distant element would
            // overflow the margin and the check would wrongly fail.
            var song = songMock();
            var line = lineWithCrotchets(3, song);

            var source = line.getElement(1);
            var sourceFallRightEdgeSs = rightEdgeWithFallSs(source, ScaleContext.pxToSs(source.getXOffsetPx()));

            var last = line.getElement(2);
            last.setXOffsetPx(ScaleContext.ssToRoundedPx(sourceFallRightEdgeSs + DISTANT_LAST_ELEMENT_GAP_SS));

            var marginSs = sourceFallRightEdgeSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(marginSs);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("no push (shiftSs == 0) fits on the source's own fall edge, ignoring the distant last element")
                .isTrue();
        }

        @Test
        void testFallMidLineFitsWithWideLine() {
            var line = lineWithCrotchets(3, songWithLineWidth(WIDE_LINE_SS));

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("fall mid-line fits on a generously wide line")
                .isTrue();
        }

        @Test
        void testFallUsesLayoutPositionWhenProvided() {
            // With a non-null layout the source X comes from the layout map, not xOffsetPx. A layout
            // position to the right of the element's xOffset pushes the fall's right edge past a
            // margin that the xOffset position would clear, proving the layout drives the geometry.
            var song = songMock();
            var line = lineWithCrotchets(3, song);
            var lastIndex = line.elementCount() - 1;
            var lastElement = line.getElement(lastIndex);
            var fallRightEdgeAtOffsetSs =
                rightEdgeWithFallSs(lastElement, ScaleContext.pxToSs(lastElement.getXOffsetPx()));
            var marginSs = fallRightEdgeAtOffsetSs + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(marginSs);

            var layout = mock(LayoutResult.class);
            when(layout.getElementXSs(any())).thenAnswer(invocation ->
                ScaleContext.pxToSs(((StaffElement) invocation.getArgument(0)).getXOffsetPx()) + LAYOUT_SHIFT_SS);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, lastIndex, layout))
                .as("layout position right of xOffset drives the fall geometry, overflowing the margin")
                .isFalse();
        }

        @Test
        void testRestoresNoSlideStateAfterCheck() {
            // The check measures fall geometry on a clone, so an element that had no slide must be
            // left untouched (no spurious fall on the live model element).
            var line = lineWithCrotchets(2, songWithLineWidth(WIDE_LINE_SS));
            var source = line.getElement(0);

            InsertionSpacingCalculator.hasRoomForFall(line, 0, null);

            assertThat(source.hasFall()).as("temporary fall removed after check").isFalse();
            assertThat(source.hasGlissando()).as("no slide left after check").isFalse();
        }

        @Test
        void testRestoresGlissandoStateAfterCheck() {
            // An element that carried a glissando must keep it — the check measures a clone and
            // must never replace the live element's slide with the fall it applies to the clone.
            var line = lineWithCrotchets(2, songWithLineWidth(WIDE_LINE_SS));
            var source = line.getElement(0);
            source.setGlissando();

            InsertionSpacingCalculator.hasRoomForFall(line, 0, null);

            assertThat(source.hasGlissando()).as("original glissando restored").isTrue();
            assertThat(source.hasFall()).as("fall not left in place").isFalse();
        }
    }

    /**
     * An insertion preview must land where the committed layout will actually place the element,
     * so accepting the preview does not make the note jump. Drives
     * {@link InsertionSpacingCalculator#calculateInsertion} and {@link LayoutEngine#layout(Line)}
     * through the identical line, so both answer the same question the same way.
     *
     * <p>This equality holds only while the line has room to sit at its natural gaps. The preview
     * positions the element locally — off its left neighbour's current X, with only what follows
     * the insertion point shifting — whereas committing re-solves the whole chain and re-flows
     * every gap. Once the line compresses, no local preview can be pixel-equal to that global
     * re-flow, and exact agreement is deliberately not claimed. What {@link
     * InsertionSpacingCalculator#calculateInsertion} does still guarantee under compression is the
     * fit verdict: a line the pre-check accepts is one the layout can always place.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class PreviewMatchesCommittedLayout {

        private static final int PREVIEW_INSERT_INDEX = 1;


        @Test
        void testInsertedElementPreviewXMatchesCommittedX() {
            var line = detachedLine();
            var engine = layoutEngine();

            line.addElement(crotchet());
            var shiftedElement = crotchet();
            line.addElement(shiftedElement);
            line.addElement(crotchet());

            var beforeLayout = requireLayout(engine.layout(line));
            var beforeShiftedXSs = beforeLayout.getElementXSs(shiftedElement);

            var inserted = crotchet();
            var preview = InsertionSpacingCalculator.calculateInsertion(
                line, inserted, PREVIEW_INSERT_INDEX, beforeLayout, lyricRenderMetrics());

            line.addElement(PREVIEW_INSERT_INDEX, inserted);
            var afterLayout = requireLayout(engine.layout(line));

            assertThat(afterLayout.getElementXSs(inserted))
                .as("committed X for the inserted element must match the preview")
                .isCloseTo(preview.insertedElementXSs(), within(PREVIEW_TOLERANCE_SS));

            var afterShiftedXSs = afterLayout.getElementXSs(shiftedElement);
            assertThat(afterShiftedXSs - beforeShiftedXSs)
                .as("the actual shift applied to the following element must match the preview's projected shift")
                .isCloseTo(preview.shiftForSubsequentElementsSs(), within(PREVIEW_TOLERANCE_SS));
        }
    }

    /**
     * The line width at which the projected fragment chain is fully compressed — every non-rigid
     * gap frozen on its strut — the boundary {@link
     * InsertionSpacingCalculator.FragmentInsertionResult#fitsWithinLine} solves against. The
     * fragment analogue of {@link #fullyCompressedWidthSs(InsertionSpacingCalculator.InsertionResult)}.
     */
    private static double fullyCompressedWidthSs(
        InsertionSpacingCalculator.FragmentInsertionResult result) {

        var floorSpanSs = result.projectedSprings().stream()
            .mapToDouble(spring -> spring.rigid() ? spring.naturalLengthSs() : spring.strutSs())
            .sum();
        return result.projectedFirstXSs() + floorSpanSs + result.projectedLastRightExtentSs();
    }

    /**
     * Builds a lightweight {@link ElementColumn} for {@code element}, mirroring the syllable-less
     * column construction {@code InsertionSpacingCalculator} uses internally for a fragment's clones.
     */
    private static ElementColumn lightweightColumn(StaffElement element) {
        var leftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(element);
        var rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(element, false, element.getDirection());
        return new ElementColumn(
            element, Collections.emptyList(), leftExtentSs, rightExtentSs, 0, 0, null, 0, false);
    }

    /** Snapshots every element's identity and xOffsetPx, in order, including the terminal barline. */
    private static List<Object> snapshotElements(Line line) {
        var snapshot = new ArrayList<Object>();

        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);
            snapshot.add(element);
            snapshot.add(element.getXOffsetPx());
        }

        return snapshot;
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateFragmentInsertion {

        @Test
        void testChainsNElementsWithExpectedColumnGaps() {
            // Row: N-element chaining — each clone's X must advance from the previous
            // clone by the same column gap the standard algorithm produces for two
            // identical elements, since the gap depends only on geometry, not position.
            var line = lineWithCrotchets(2, songWithLineWidth(WIDE_LINE_SS));
            var fragment = List.<StaffElement>of(crotchet(), crotchet(), crotchet());

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, line.effectiveElementCount(), null, null, null);

            var positions = result.cloneXPositionsSs();
            assertThat(positions).hasSize(3);

            var firstColumn = lightweightColumn(crotchet());
            firstColumn.setXSs(0);
            var expectedGapSs = springNextColumnXSs(firstColumn, lightweightColumn(crotchet()));
            assertThat(positions.get(1) - positions.get(0)).isEqualTo(expectedGapSs);
            assertThat(positions.get(2) - positions.get(1)).isEqualTo(expectedGapSs);
        }

        @Test
        void testSeedsFromFirstElementXWhenInsertingAtIndexZero() {
            var line = lineWithCrotchets(0, songWithLineWidth(WIDE_LINE_SS));
            var fragment = List.<StaffElement>of(crotchet());

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(line, fragment, 0, null, null, null);

            var expectedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
            assertThat(result.cloneXPositionsSs().get(0)).isEqualTo(expectedXSs);
        }

        @Test
        void testSeedsFromMidLinePredecessorMatchingSingleElementInsertion() {
            // A single-element fragment must seed identically to calculateInsertion,
            // since fragment insertion reuses the same predecessor-seeding rule.
            var line = lineWithCrotchets(2, songWithLineWidth(WIDE_LINE_SS));
            var fragmentElement = crotchet();

            var fragmentResult = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, List.of(fragmentElement), 1, null, null, null);
            var singleResult = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null, null);

            assertThat(fragmentResult.cloneXPositionsSs().get(0)).isEqualTo(singleResult.insertedElementXSs());
        }

        @Test
        void testDeleteRangeSeedsFromElementBeforeTheRangeNotFromWithinIt() {
            var line = lineWithCrotchets(4, songWithLineWidth(WIDE_LINE_SS));
            var predecessor = line.getElement(0);
            var deleteRange = new InsertionSpacingCalculator.DeletedRange(1, 2);
            var fragmentElement = crotchet();

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, List.of(fragmentElement), 1, deleteRange, null, null);

            var predecessorColumn = lightweightColumn(predecessor);
            predecessorColumn.setXSs(ScaleContext.pxToSs(predecessor.getXOffsetPx()));
            var expectedSeedXSs = springNextColumnXSs(predecessorColumn, lightweightColumn(fragmentElement));
            assertThat(result.cloneXPositionsSs().get(0)).isEqualTo(expectedSeedXSs);
        }

        @Test
        void testDeleteRangeShiftsFromElementAfterTheRangeNotFromWithinIt() {
            var line = lineWithCrotchets(4, songWithLineWidth(WIDE_LINE_SS));
            var successor = line.getElement(3);
            var deleteRange = new InsertionSpacingCalculator.DeletedRange(1, 2);
            var fragmentElement = crotchet();

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, List.of(fragmentElement), 1, deleteRange, null, null);

            var lastCloneColumn = lightweightColumn(fragmentElement);
            lastCloneColumn.setXSs(result.cloneXPositionsSs().get(0));
            var successorColumn = lightweightColumn(successor);
            var requiredSuccessorXSs = springNextColumnXSs(lastCloneColumn, successorColumn);
            var successorXSs = ScaleContext.pxToSs(successor.getXOffsetPx());

            assertThat(result.shiftForSubsequentElementsSs()).isEqualTo(requiredSuccessorXSs - successorXSs);
        }

        @Test
        void testFragmentThatJustFitsReturnsFitsVerdict() {
            var line = lineWithCrotchets(3, songWithLineWidth(WIDE_LINE_SS));
            var fittingFragment = List.<StaffElement>of(crotchet(), crotchet());

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fittingFragment, line.effectiveElementCount(), null, null, null);
            var marginSs = fullyCompressedWidthSs(result) + BOUNDARY_SLACK_SS;

            assertThat(result.fitsWithinLine(marginSs)).isTrue();
        }

        @Test
        void testFragmentOneElementLargerReturnsLineFullVerdict() {
            var line = lineWithCrotchets(3, songWithLineWidth(WIDE_LINE_SS));
            var fittingFragment = List.<StaffElement>of(crotchet(), crotchet());
            var fittingResult = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fittingFragment, line.effectiveElementCount(), null, null, null);
            // A margin that just clears the two-element fragment's fully compressed floor.
            var marginSs = fullyCompressedWidthSs(fittingResult) + BOUNDARY_SLACK_SS;

            var oneElementLargerFragment = List.<StaffElement>of(crotchet(), crotchet(), crotchet());
            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, oneElementLargerFragment, line.effectiveElementCount(), null, null, null);

            // One more element raises the compressed floor past that margin, so it cannot fit.
            assertThat(result.fitsWithinLine(marginSs)).isFalse();
        }

        @Test
        void testDoesNotMutateLineOrElementsOnFits() {
            var line = lineWithCrotchets(3, songWithLineWidth(WIDE_LINE_SS));
            var before = snapshotElements(line);
            var fragment = List.<StaffElement>of(crotchet(), crotchet());

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, line.effectiveElementCount(), null, null, null);

            assertThat(result.fitsWithinLine(WIDE_LINE_SS)).isTrue();
            assertThat(snapshotElements(line)).isEqualTo(before);
        }

        @Test
        void testDoesNotMutateLineOrElementsOnLineFull() {
            var line = lineWithCrotchets(3, songWithLineWidth(0));
            var before = snapshotElements(line);
            var fragment = List.<StaffElement>of(crotchet(), crotchet());

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, line.effectiveElementCount(), null, null, null);

            assertThat(result.fitsWithinLine(line.getSong().getLineWidthSs())).isFalse();
            assertThat(snapshotElements(line)).isEqualTo(before);
        }

        @Test
        void testFragmentCloneSyllableWidensGapBetweenClonesWhenLyricMetricsSupplied() {
            var line = lineWithCrotchets(1, songWithLineWidth(WIDE_LINE_SS));

            var firstClone = crotchet();
            firstClone.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "encouragement", Lyric.Extend.NONE);
            var fragment = List.of(firstClone, crotchet());

            var withMetrics = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, line.effectiveElementCount(), null, null, lyricRenderMetrics());
            var withoutMetrics = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, line.effectiveElementCount(), null, null, null);

            var gapWithMetricsSs = withMetrics.cloneXPositionsSs().get(1) - withMetrics.cloneXPositionsSs().get(0);
            var gapWithoutMetricsSs = withoutMetrics.cloneXPositionsSs().get(1) - withoutMetrics.cloneXPositionsSs().get(0);

            assertThat(gapWithMetricsSs)
                .as("a wide syllable on a fragment clone must widen the gap to its neighbour")
                .isGreaterThan(gapWithoutMetricsSs);
        }

        @Test
        void testSurroundingPredecessorSyllableWidensGapToFragmentCloneWhenLyricMetricsSupplied() {
            var line = new Line(songWithLineWidth(WIDE_LINE_SS));
            var predecessor = crotchet();
            predecessor.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "encouragement", Lyric.Extend.NONE);
            var predecessorXSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, predecessor, null, null);
            predecessor.setXOffsetPx(ScaleContext.ssToRoundedPx(predecessorXSs));
            line.addElement(predecessor);

            var fragment = List.<StaffElement>of(crotchet());

            var withMetrics = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, line.effectiveElementCount(), null, null, lyricRenderMetrics());
            var withoutMetrics = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, line.effectiveElementCount(), null, null, null);

            assertThat(withMetrics.cloneXPositionsSs().get(0))
                .as("a wide syllable on the predecessor must push the fragment's clone further right")
                .isGreaterThan(withoutMetrics.cloneXPositionsSs().get(0));
        }

        @Test
        void testPasteReplaceWithNarrowerFragmentProducesNegativeShift() {
            // The single-clone fragment is far narrower than the multi-element range it replaces.
            // The javadoc promises the tail is pulled LEFT (negative shift) in this case — the one
            // behavior that distinguishes paste-replace from pure insertion's
            // Math.max(0, shiftSs) clamp — but no existing test checks the sign.
            var line = lineWithCrotchets(NEGATIVE_SHIFT_LINE_ELEMENT_COUNT, songWithLineWidth(WIDE_LINE_SS));
            var deleteRange = new InsertionSpacingCalculator.DeletedRange(1, NEGATIVE_SHIFT_DELETE_RANGE_END_INDEX);
            var fragment = List.<StaffElement>of(crotchet());

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, 1, deleteRange, null, null);

            assertThat(result.shiftForSubsequentElementsSs())
                .as("a fragment narrower than the deleted range must pull the tail left (negative shift)")
                .isNegative();
        }

        @Test
        void testPureInsertionShiftNeverNegativeEvenWhenGapIsAlreadyWide() {
            // Companion to the paste-replace negative-shift test above: with deleteRange == null,
            // Math.max(0, shiftSs) must clamp even a negative raw required shift to zero.
            var firstXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(0);  // no key accidentals
            var largeGapSs = 50.0;
            var secondElementXSs = firstXSs + largeGapSs;
            var line = widelySpacedTwoCrotchetLine(secondElementXSs);
            var fragment = List.<StaffElement>of(crotchet());

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, 1, null, null, null);

            assertThat(result.shiftForSubsequentElementsSs())
                .as("pure insertion (deleteRange == null) must clamp a negative raw shift to zero")
                .isEqualTo(0.0);
        }

        @Test
        void testMultiElementFragmentWithDeleteRangeChainsFromPredecessorAndShiftsFromSuccessor() {
            // Every existing deleteRange test above uses a single-element fragment; this pins both
            // the clone-to-clone chaining AND the trailing shift for a multi-element paste-replace.
            var line = lineWithCrotchets(MULTI_FRAGMENT_DELETE_LINE_ELEMENT_COUNT, songWithLineWidth(WIDE_LINE_SS));
            var predecessor = line.getElement(0);
            var successor = line.getElement(MULTI_FRAGMENT_DELETE_LINE_ELEMENT_COUNT - 1);
            var deleteRange = new InsertionSpacingCalculator.DeletedRange(1, MULTI_FRAGMENT_DELETE_RANGE_END_INDEX);
            var fragment = List.<StaffElement>of(crotchet(), crotchet());

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, 1, deleteRange, null, null);

            assertThat(result.cloneXPositionsSs()).as("one X per fragment clone").hasSize(fragment.size());

            var predecessorColumn = lightweightColumn(predecessor);
            predecessorColumn.setXSs(ScaleContext.pxToSs(predecessor.getXOffsetPx()));
            var expectedFirstCloneXSs = springNextColumnXSs(predecessorColumn, lightweightColumn(fragment.get(0)));
            assertThat(result.cloneXPositionsSs().get(0))
                .as("first clone seeds from the element before the deleted range")
                .isEqualTo(expectedFirstCloneXSs);

            var firstCloneColumn = lightweightColumn(fragment.get(0));
            firstCloneColumn.setXSs(result.cloneXPositionsSs().get(0));
            var expectedSecondCloneXSs = springNextColumnXSs(firstCloneColumn, lightweightColumn(fragment.get(1)));
            assertThat(result.cloneXPositionsSs().get(1))
                .as("second clone chains off the first clone's natural spring gap")
                .isEqualTo(expectedSecondCloneXSs);

            var lastCloneColumn = lightweightColumn(fragment.get(1));
            lastCloneColumn.setXSs(result.cloneXPositionsSs().get(1));
            var successorColumn = lightweightColumn(successor);
            var requiredSuccessorXSs = springNextColumnXSs(lastCloneColumn, successorColumn);
            var successorXSs = ScaleContext.pxToSs(successor.getXOffsetPx());
            assertThat(result.shiftForSubsequentElementsSs())
                .as("trailing shift is derived from the LAST clone, not from within the deleted range")
                .isEqualTo(requiredSuccessorXSs - successorXSs);
        }

        @Test
        void testUsesLayoutPositionForPredecessorWhenLayoutProvided() {
            // With a non-null layout the predecessor's X comes from the layout map, not xOffsetPx.
            // Every existing CalculateFragmentInsertion test above passes null, so this path
            // (elementXSs falling through to layout.getElementXSs) is otherwise never exercised.
            var line = lineWithCrotchets(1, songWithLineWidth(WIDE_LINE_SS));
            var predecessor = line.getElement(0);
            var fragmentElement = crotchet();

            var layout = mock(LayoutResult.class);
            when(layout.getElementXSs(any())).thenAnswer(invocation ->
                ScaleContext.pxToSs(((StaffElement) invocation.getArgument(0)).getXOffsetPx()) + LAYOUT_SHIFT_SS);

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, List.of(fragmentElement), 1, null, layout, null);

            var layoutPredecessorColumn = lightweightColumn(predecessor);
            layoutPredecessorColumn.setXSs(ScaleContext.pxToSs(predecessor.getXOffsetPx()) + LAYOUT_SHIFT_SS);
            var expectedCloneXSs = springNextColumnXSs(layoutPredecessorColumn, lightweightColumn(fragmentElement));

            assertThat(result.cloneXPositionsSs().get(0))
                .as("predecessor position must come from the supplied layout, not xOffsetPx")
                .isEqualTo(expectedCloneXSs);

            // Prove disagreement: the xOffsetPx-based position must differ from the layout-driven
            // one, so this test can only pass if the layout source was actually used.
            var xOffsetPredecessorColumn = lightweightColumn(predecessor);
            xOffsetPredecessorColumn.setXSs(ScaleContext.pxToSs(predecessor.getXOffsetPx()));
            var xOffsetCloneXSs = springNextColumnXSs(xOffsetPredecessorColumn, lightweightColumn(fragmentElement));

            assertThat(result.cloneXPositionsSs().get(0))
                .as("layout-driven position must differ from the xOffsetPx-only position")
                .isNotEqualTo(xOffsetCloneXSs);
        }

        /**
         * Creates a line with two crotchets spaced far apart — first at the standard first-element X,
         * second at {@code secondElementXSs} — so a fragment inserted between them has a raw required
         * shift that goes negative before {@code calculateFragmentInsertion}'s pure-insertion clamp
         * is applied.
         */
        private static Line widelySpacedTwoCrotchetLine(double secondElementXSs) {
            var line = detachedLine();
            var first = crotchet();
            var firstXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line.getKeyAccidentalCount());
            first.setXOffsetPx(ScaleContext.ssToRoundedPx(firstXSs));
            line.addElement(first);

            var second = crotchet();
            second.setXOffsetPx(ScaleContext.ssToRoundedPx(secondElementXSs));
            line.addElement(second);

            return line;
        }

        @SuppressWarnings("PackageVisibleInnerClass")
        @Nested
        class Validation {

            @Test
            void testEmptyFragmentThrowsIllegalArgumentException() {
                // Pins the calculator's OWN empty-fragment guard, independent of
                // ScoreViewController's upstream guard against ever calling with an empty selection.
                var line = lineWithCrotchets(0);

                assertThatIllegalArgumentException()
                    .isThrownBy(() -> InsertionSpacingCalculator.calculateFragmentInsertion(
                        line, List.of(), 0, null, null, null))
                    .withMessage("fragment must not be empty");
            }

            @Test
            void testNegativeInsertIndexThrowsIllegalArgumentException() {
                var line = lineWithCrotchets(0);
                var fragment = List.<StaffElement>of(crotchet());
                var insertIndex = -1;
                var effectiveCount = line.effectiveElementCount();
                var expectedMessage = "insertIndex " + insertIndex + " out of bounds [0, " + effectiveCount + ']';

                assertThatIllegalArgumentException()
                    .isThrownBy(() -> InsertionSpacingCalculator.calculateFragmentInsertion(
                        line, fragment, insertIndex, null, null, null))
                    .withMessage(expectedMessage);
            }

            @Test
            void testInsertIndexBeyondEffectiveCountThrowsIllegalArgumentException() {
                var line = lineWithCrotchets(0);
                var fragment = List.<StaffElement>of(crotchet());
                var effectiveCount = line.effectiveElementCount();
                var insertIndex = effectiveCount + 1;
                var expectedMessage = "insertIndex " + insertIndex + " out of bounds [0, " + effectiveCount + ']';

                assertThatIllegalArgumentException()
                    .isThrownBy(() -> InsertionSpacingCalculator.calculateFragmentInsertion(
                        line, fragment, insertIndex, null, null, null))
                    .withMessage(expectedMessage);
            }

            @Test
            void testDeleteRangeWithNegativeBeginThrowsIllegalArgumentException() {
                var line = lineWithCrotchets(VALIDATION_LINE_ELEMENT_COUNT);
                var fragment = List.<StaffElement>of(crotchet());
                var deleteRange = new InsertionSpacingCalculator.DeletedRange(-1, 1);
                var expectedMessage = "invalid deleteRange " + deleteRange;

                assertThatIllegalArgumentException()
                    .isThrownBy(() -> InsertionSpacingCalculator.calculateFragmentInsertion(
                        line, fragment, 0, deleteRange, null, null))
                    .withMessage(expectedMessage);
            }

            @Test
            void testDeleteRangeWithBeginGreaterThanEndThrowsIllegalArgumentException() {
                var line = lineWithCrotchets(VALIDATION_LINE_ELEMENT_COUNT);
                var fragment = List.<StaffElement>of(crotchet());
                var deleteRange =
                    new InsertionSpacingCalculator.DeletedRange(VALIDATION_LINE_ELEMENT_COUNT - 1, 0);
                var expectedMessage = "invalid deleteRange " + deleteRange;

                assertThatIllegalArgumentException()
                    .isThrownBy(() -> InsertionSpacingCalculator.calculateFragmentInsertion(
                        line, fragment, 0, deleteRange, null, null))
                    .withMessage(expectedMessage);
            }

            @Test
            void testDeleteRangeWithEndAtOrBeyondEffectiveCountThrowsIllegalArgumentException() {
                var line = lineWithCrotchets(VALIDATION_LINE_ELEMENT_COUNT);
                var fragment = List.<StaffElement>of(crotchet());
                var deleteRange =
                    new InsertionSpacingCalculator.DeletedRange(0, VALIDATION_LINE_ELEMENT_COUNT);
                var expectedMessage = "invalid deleteRange " + deleteRange;

                assertThatIllegalArgumentException()
                    .isThrownBy(() -> InsertionSpacingCalculator.calculateFragmentInsertion(
                        line, fragment, 0, deleteRange, null, null))
                    .withMessage(expectedMessage);
            }

            @Test
            void testInsertIndexNotEqualToDeleteRangeBeginThrowsIllegalArgumentException() {
                var line = lineWithCrotchets(VALIDATION_LINE_ELEMENT_COUNT);
                var fragment = List.<StaffElement>of(crotchet());
                var deleteRange = new InsertionSpacingCalculator.DeletedRange(1, 1);
                var insertIndex = 0;
                var expectedMessage = "insertIndex " + insertIndex
                    + " must equal deleteRange.begin() " + deleteRange.begin();

                assertThatIllegalArgumentException()
                    .isThrownBy(() -> InsertionSpacingCalculator.calculateFragmentInsertion(
                        line, fragment, insertIndex, deleteRange, null, null))
                    .withMessage(expectedMessage);
            }
        }
    }
}
