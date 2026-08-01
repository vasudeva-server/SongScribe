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
import java.util.function.ToDoubleFunction;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
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

class InsertionSpacingCalculatorTest extends UnitTest {

    // Column extents include accidental width, and NoteGeometry exits the process rather than
    // guessing when its width tables are unpopulated. Only the accidental cases below reach that
    // code, but the tables are process-wide, so they are filled once for the whole class.
    @BeforeAll
    static void initializeNoteGeometry() {
        NoteGeometry.initializeAccidentalWidths();
    }

    /** A line width wide enough that any insertion or fall comfortably fits. */
    private static final double WIDE_LINE_SS = 500;

    /**
     * A line rest wider than {@link HorizontalSpacingCalculator#DEFAULT_COLUMN_GAP_SS}, so the two
     * reservations can be told apart.
     */
    private static final double WIDE_LINE_REST_SS = 4.0;

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
        // Every gap gives down to its strut — mirroring
        // SpringSpacer.compress, so this is the solver's true floor rather than an underestimate.
        var floorSpanSs = result.projectedSprings().stream()
            .mapToDouble(Spring::strutSs)
            .sum();
        return result.projectedFirstXSs() + floorSpanSs + result.projectedTrailingReservationSs();
    }

    /**
     * Real lyric-render metrics for the preview-vs-committed equality test — shared by
     * {@link #layoutEngine} and the direct {@code calculateInsertion} preview call, so both see
     * identical metrics.
     */
    private static LyricRenderMetrics lyricRenderMetrics() {
        var lyricsFont = new Font("Dialog", Font.PLAIN, 12);
        return LyricRenderMetrics.forFont(lyricsFont);
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
        // An unstubbed mock reports verse 0, which no lyric ever carries, so every syllable in
        // these fixtures would measure as absent.
        when(song.getActiveVerse()).thenReturn(Lyric.FIRST_VERSE);
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
     * Creates a line of crotchets closed by the song's auto-maintained terminal barline — the shape
     * every real last line has. Without this the mock's default {@code isAutoMaintainedTerminal ==
     * false} makes {@code effectiveElementCount() == elementCount()} on every fixture, leaving the
     * terminal-append branch of the projection gates unexercised.
     */
    private static Line lineWithTerminal(int crotchetCount, Song song) {
        var line = lineWithCrotchets(crotchetCount, song);
        var terminal = ElementType.FINAL_DOUBLE_BARLINE.newInstance();
        var xSs = InsertionSpacingCalculator.calculateAppendPositionSs(line, terminal, null, null);
        terminal.setXOffsetPx(ScaleContext.ssToRoundedPx(xSs));
        line.addElement(terminal);
        when(song.isAutoMaintainedTerminal(terminal, line)).thenReturn(true);
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
     * The chain {@code hasRoomForFall} solves — every column with a fall-carrying clone in the
     * source's place — reduced to the two margins a test can name: the natural width the line wants
     * and the fully compressed floor below which no solve succeeds.
     */
    private record FallProjection(double anchorXSs, List<Spring> springs, double trailingReservationSs) {

        private double widthSs(ToDoubleFunction<? super Spring> gapSs) {
            return anchorXSs + springs.stream().mapToDouble(gapSs).sum() + trailingReservationSs;
        }

        /** The width the chain occupies with every gap at its natural length — nothing compressed. */
        double naturalWidthSs() {
            return widthSs(Spring::naturalLengthSs);
        }

        /**
         * The narrowest margin the chain can still be solved within: every gap frozen on its strut.
         * Every gap gives down to its strut, mirroring {@code SpringSpacer.compress}.
         */
        double compressedFloorSs() {
            return widthSs(Spring::strutSs);
        }
    }

    /** Builds the projected chain a fall at {@code sourceIndex} produces, as the gate builds it. */
    private static FallProjection fallProjection(Line line, int sourceIndex) {
        var sourceWithFall = line.getElement(sourceIndex).clone();
        sourceWithFall.setFall();

        var elementCount = line.elementCount();
        var effectiveCount = line.effectiveElementCount();
        var columns = new ArrayList<ElementColumn>(elementCount);

        for (var i = 0; i < effectiveCount; i++) {
            columns.add(lightweightColumn(i == sourceIndex ? sourceWithFall : line.getElement(i)));
        }

        // Mirror the gate's conditional terminal append rather than looping every element: the two
        // coincide only while a line has no auto-maintained terminal.
        if (effectiveCount < elementCount) {
            columns.add(lightweightColumn(line.getElement(elementCount - 1)));
        }

        return new FallProjection(
            HorizontalSpacingCalculator.calculateAnchorXSs(columns.getFirst(), line),
            LyricLift.applyLyricLift(HorizontalSpacingCalculator.buildSprings(columns, line), columns),
            HorizontalSpacingCalculator.trailingReservationSs(columns.getLast(), line));
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
            var expectedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line);

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
            var insertedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line);
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
            var expectedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line);
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
            // retired fixed-margin rule rejected exactly this case. The line's last column carries
            // no terminal, so the margin must also grant the mandatory trailing line-rest gap
            // (refs #617) — that reservation is fixed, not part of the slack under test.
            var line = lineWithCrotchets(2);
            var result = InsertionSpacingCalculator.calculateInsertion(line, crotchet(), 1, null, null);
            var marginSs = result.newLineWidthSs() + Song.DEFAULT_REST_LENGTH_SS - NARROWER_MARGIN_SS;
            assertThat(result.fitsWithinLine(marginSs)).isTrue();
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
            // gaps still hold slack above their struts, so the grace note is admitted. The grace
            // note becomes the line's last (non-terminal) column, so the margin must also grant
            // the mandatory trailing line-rest gap (refs #617) — fixed, not part of the slack
            // under test.
            var song = songMock();
            var line = lineWithCrotchets(5, song);
            when(song.getLineWidthSs())
                .thenReturn(lastElementRightEdgeSs(line) + Song.DEFAULT_REST_LENGTH_SS);

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
            var firstXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line);
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
            var firstXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(detachedLine());
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

        /**
         * The bug behind refs #617: a line whose natural spacing leaves no room for a fall may still
         * have slack in its springs, and the committed layout compresses that slack away. A gate that
         * measures the uncompressed width rejects the fall the layout would have accepted — exactly
         * what inserting a note at the end of the same line does not do, because insertion solves.
         */
        @Test
        void testFallFitsOnAFullLineWithRoomToCompress() {
            var song = songMock();
            var line = lineWithCrotchets(5, song);
            var projection = fallProjection(line, 1);
            var marginSs = projection.compressedFloorSs() + BOUNDARY_SLACK_SS;
            when(song.getLineWidthSs()).thenReturn(marginSs);

            assertThat(marginSs)
                .as("fixture is only meaningful if the margin is too narrow for the uncompressed line")
                .isLessThan(projection.naturalWidthSs());

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("a fall fits when the line's springs can compress to make room")
                .isTrue();
        }

        @Test
        void testFallDoesNotFitBelowTheCompressedFloor() {
            // Below the floor every gap is already on its strut, so no compression can save the fall.
            var song = songMock();
            var line = lineWithCrotchets(3, song);
            var marginSs = fallProjection(line, 1).compressedFloorSs() - BOUNDARY_SLACK_SS;
            when(song.getLineWidthSs()).thenReturn(marginSs);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("a fall below the fully compressed floor cannot fit")
                .isFalse();
        }

        /**
         * The fall gate must reserve the same trailing span the committed solve reserves after a
         * non-terminal last element, or it can accept a fall that then fails to lay out (refs #617).
         * With a line rest wider than the flat default column gap the boundary moves out to the
         * rest — a gate still keying off the flat gap would wrongly accept the narrower margin.
         */
        @Test
        void testFallAtNonTerminalLastElementReservesTheSongsLineRest() {
            var song = songMock();
            when(song.getDefaultRestLengthSs()).thenReturn(WIDE_LINE_REST_SS);
            var line = lineWithCrotchets(3, song);
            var lastIndex = line.elementCount() - 1;
            var floorSs = fallProjection(line, lastIndex).compressedFloorSs();

            var flatGapMarginSs = floorSs - WIDE_LINE_REST_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
            when(song.getLineWidthSs()).thenReturn(flatGapMarginSs);
            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, lastIndex, null))
                .as("the flat default column gap is too little once the song's line rest exceeds it")
                .isFalse();

            when(song.getLineWidthSs()).thenReturn(floorSs + BOUNDARY_SLACK_SS);
            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, lastIndex, null))
                .as("a margin granting the full line rest fits")
                .isTrue();
        }

        /**
         * A line closed by the auto-maintained terminal reserves only the terminal's own extent —
         * layout pins it flush-right, so nothing owes a trailing rest past it. The gate must both
         * include that column in the chain it solves and skip the rest, or it disagrees with the
         * committed layout in one direction or the other (refs #617).
         */
        @Test
        void testFallOnALineClosedByTheTerminalReservesNoTrailingRest() {
            var song = songMock();
            var line = lineWithTerminal(3, song);
            var floorSs = fallProjection(line, 1).compressedFloorSs();

            when(song.getLineWidthSs()).thenReturn(floorSs - BOUNDARY_SLACK_SS);
            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("the terminal's own column still consumes span, so below the floor nothing fits")
                .isFalse();

            when(song.getLineWidthSs()).thenReturn(floorSs + BOUNDARY_SLACK_SS);
            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("a terminal is pinned flush-right, so no line rest is owed past it")
                .isTrue();
        }

        /**
         * The sole production caller always supplies real lyric metrics, so the syllable-aware
         * column path — not the syllable-less fallback every other test here takes — is the one that
         * actually gates the user's click. A wide syllable widens the chain and must be able to turn
         * a fitting fall into a non-fitting one.
         */
        @Test
        void testWideSyllableCanDenyAFallThatFitsWithoutLyrics() {
            var song = songMock();
            var line = lineWithCrotchets(3, song);
            line.getElement(1).setLyricForVerse(
                1, Lyric.Syllabic.SINGLE, false, "indefatigability", Lyric.Extend.NONE);
            // Hoisted: fallProjection reads the song mock, which Mockito forbids inside when(...).
            var marginSs = fallProjection(line, 1).compressedFloorSs() + BOUNDARY_SLACK_SS;
            when(song.getLineWidthSs()).thenReturn(marginSs);

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("without lyric metrics the syllable is invisible and the fall fits")
                .isTrue();

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, lyricRenderMetrics()))
                .as("measured, the syllable widens the chain past the same margin")
                .isFalse();
        }

        @Test
        void testFallMidLineFitsWithWideLine() {
            var line = lineWithCrotchets(3, songWithLineWidth(WIDE_LINE_SS));

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("fall mid-line fits on a generously wide line")
                .isTrue();
        }

        /**
         * The gate re-solves the chain from the line's columns, so the elements' stored X positions —
         * which after an edit may be stale, and are in any case the pre-fall spacing — must not move
         * the verdict either way.
         */
        @Test
        void testFallVerdictIgnoresStoredElementPositions() {
            var song = songMock();
            var line = lineWithCrotchets(3, song);
            // Hoisted: fallProjection reads the song mock, which Mockito forbids inside when(...).
            var marginSs = fallProjection(line, 1).compressedFloorSs() + BOUNDARY_SLACK_SS;
            when(song.getLineWidthSs()).thenReturn(marginSs);

            for (var i = 0; i < line.elementCount(); i++) {
                var element = line.getElement(i);
                element.setXOffsetPx(ScaleContext.ssToRoundedPx(
                    ScaleContext.pxToSs(element.getXOffsetPx()) + LAYOUT_SHIFT_SS));
            }

            assertThat(InsertionSpacingCalculator.hasRoomForFall(line, 1, null))
                .as("shifting every stored position right does not change a solve-based verdict")
                .isTrue();
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
     * The line width at which the projected fragment chain is fully compressed — every
     * gap frozen on its strut — the boundary {@link
     * InsertionSpacingCalculator.FragmentInsertionResult#fitsWithinLine} solves against. The
     * fragment analogue of {@link #fullyCompressedWidthSs(InsertionSpacingCalculator.InsertionResult)}.
     */
    private static double fullyCompressedWidthSs(
        InsertionSpacingCalculator.FragmentInsertionResult result) {

        var floorSpanSs = result.projectedSprings().stream()
            .mapToDouble(Spring::strutSs)
            .sum();
        return result.projectedFirstXSs() + floorSpanSs + result.projectedTrailingReservationSs();
    }

    /**
     * Builds a lightweight {@link ElementColumn} for {@code element}, mirroring the syllable-less
     * column construction {@code InsertionSpacingCalculator} uses internally for a fragment's clones.
     */
    private static ElementColumn lightweightColumn(StaffElement element) {
        var direction = element.getDirection();
        var leftExtentSs = ElementColumnBuilder.calculateLeftExtentSs(element);
        var rightExtentSs = ElementColumnBuilder.calculateRightExtentSs(element, false, direction);
        // The augmentation-excluding extent must be passed explicitly, as production does: the
        // shorter ctor defaults it to rightExtentSs, which for a fall-carrying column would inflate
        // every natural gap after it and quietly diverge from the chain the gate actually solves.
        var rightExtentExcludingAugmentationSs =
            ElementColumnBuilder.calculateRightExtentExcludingAugmentationSs(element, false, direction);

        return new ElementColumn(
            element, Collections.emptyList(), leftExtentSs, rightExtentSs,
            rightExtentExcludingAugmentationSs, 0, 0, null, 0, false);
    }

    /** Snapshots every element's identity and xOffsetPx, in order, including the terminal barline. */
    private static List<Object> snapshotElements(Line line) {
        var snapshot = new ArrayList<>();

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

            var expectedXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line);
            assertThat(result.cloneXPositionsSs().getFirst()).isEqualTo(expectedXSs);
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

            assertThat(fragmentResult.cloneXPositionsSs().getFirst()).isEqualTo(singleResult.insertedElementXSs());
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
            assertThat(result.cloneXPositionsSs().getFirst()).isEqualTo(expectedSeedXSs);
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
            lastCloneColumn.setXSs(result.cloneXPositionsSs().getFirst());
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

        /**
         * The fit-gate margins the two tests above derive from {@link #fullyCompressedWidthSs} read
         * the projected reservation back off the result, so they move with whatever the production
         * code returns and cannot catch a wrong reservation. This pins it independently: the
         * fragment's last clone is a crotchet — no terminal — so the gate must keep its right extent
         * plus a full line rest clear of the margin (refs #617).
         */
        @Test
        void testProjectsATrailingLineRestAfterANonTerminalLastClone() {
            var line = lineWithCrotchets(3, songWithLineWidth(WIDE_LINE_SS));
            var fragment = List.<StaffElement>of(crotchet(), crotchet());

            var result = InsertionSpacingCalculator.calculateFragmentInsertion(
                line, fragment, line.effectiveElementCount(), null, null, null);

            var lastCloneExtentSs = lightweightColumn(crotchet()).getRightExtentSs();
            assertThat(result.projectedTrailingReservationSs())
                .isEqualTo(lastCloneExtentSs + Song.DEFAULT_REST_LENGTH_SS);
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

            assertThat(withMetrics.cloneXPositionsSs().getFirst())
                .as("a wide syllable on the predecessor must push the fragment's clone further right")
                .isGreaterThan(withoutMetrics.cloneXPositionsSs().getFirst());
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
            var firstXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(detachedLine());
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
            var expectedFirstCloneXSs = springNextColumnXSs(predecessorColumn, lightweightColumn(fragment.getFirst()));
            assertThat(result.cloneXPositionsSs().getFirst())
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

            assertThat(result.cloneXPositionsSs().getFirst())
                .as("predecessor position must come from the supplied layout, not xOffsetPx")
                .isEqualTo(expectedCloneXSs);

            // Prove disagreement: the xOffsetPx-based position must differ from the layout-driven
            // one, so this test can only pass if the layout source was actually used.
            var xOffsetPredecessorColumn = lightweightColumn(predecessor);
            xOffsetPredecessorColumn.setXSs(ScaleContext.pxToSs(predecessor.getXOffsetPx()));
            var xOffsetCloneXSs = springNextColumnXSs(xOffsetPredecessorColumn, lightweightColumn(fragmentElement));

            assertThat(result.cloneXPositionsSs().getFirst())
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
            var firstXSs = HorizontalSpacingCalculator.calculateFirstElementXSs(line);
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

    /**
     * The in-place modification gate: an edit that changes no element count but widens columns —
     * an accidental materialized onto a selection, a dot added — still has to fit the line.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class CalculateModification {

        private static final int MODIFICATION_LINE_ELEMENT_COUNT = 5;

        /** The element the projection sharpens — a middle column, so its left extent grows a gap. */
        private static final int SHARPENED_INDEX = 2;

        /**
         * The narrowest margin the projected chain can still be solved within: every gap frozen on
         * its strut, mirroring {@code SpringSpacer.compress}.
         */
        private static double compressedFloorSs(InsertionSpacingCalculator.ModificationResult result) {
            return result.projectedFirstXSs()
                + result.projectedSprings().stream().mapToDouble(Spring::strutSs).sum()
                + result.projectedTrailingReservationSs();
        }

        /**
         * The line's own elements, with the one at {@code index} swapped for a clone carrying
         * {@code accidental} — the shape {@code AccidentalMaterializer} produces for the gate.
         */
        private static List<StaffElement> projectionWithAccidentalAt(
            Line line, int index, StaffElement.@Nullable Accidental accidental) {

            var projected = new ArrayList<StaffElement>();

            for (var i = 0; i < line.effectiveElementCount(); i++) {
                var element = line.getElement(i);

                if (i == index) {
                    var sharpened = element.clone();
                    sharpened.setAccidental(accidental);
                    projected.add(sharpened);
                    continue;
                }

                projected.add(element);
            }

            return projected;
        }

        private static InsertionSpacingCalculator.ModificationResult modificationWithAccidental(
            Line line, StaffElement.@Nullable Accidental accidental) {

            return InsertionSpacingCalculator.calculateModification(
                line, projectionWithAccidentalAt(line, SHARPENED_INDEX, accidental), null);
        }

        @Test
        void testAProjectionOfTheWrongSizeThrowsIllegalArgumentException() {
            var line = lineWithCrotchets(MODIFICATION_LINE_ELEMENT_COUNT);
            var tooShort = projectionWithAccidentalAt(line, SHARPENED_INDEX, null)
                .subList(0, MODIFICATION_LINE_ELEMENT_COUNT - 1);

            assertThatIllegalArgumentException()
                .isThrownBy(() -> InsertionSpacingCalculator.calculateModification(line, tooShort, null))
                .withMessageContaining("for " + MODIFICATION_LINE_ELEMENT_COUNT + " effective elements");
        }

        @Test
        void testAnEmptyProjectionOnAnEmptyLineThrowsIllegalArgumentException() {
            assertThatIllegalArgumentException()
                .isThrownBy(() -> InsertionSpacingCalculator.calculateModification(
                    detachedLine(), List.of(), null))
                .withMessageContaining("must not be empty");
        }

        @Test
        void testALineWithSlackAcceptsAnAccidentalAddedToTheSelection() {
            var line = lineWithCrotchets(MODIFICATION_LINE_ELEMENT_COUNT);
            var sharpened = modificationWithAccidental(line, StaffElement.Accidental.SHARP);
            var marginSs = compressedFloorSs(sharpened) + BOUNDARY_SLACK_SS;

            assertThat(sharpened.fitsWithinLine(marginSs))
                .as("a chain with slack past its compressed floor is accepted")
                .isTrue();
        }

        @Test
        void testANearlyFullLineRefusesAnAccidentalAddedToTheSelection() {
            var line = lineWithCrotchets(MODIFICATION_LINE_ELEMENT_COUNT);
            var plain = modificationWithAccidental(line, null);
            var sharpened = modificationWithAccidental(line, StaffElement.Accidental.SHARP);
            // Both sides are judged one slack past the unmodified floor rather than on it: the
            // floor is re-summed here in a different association than the solver uses, so an exact
            // boundary would turn on a rounding bit.
            var marginSs = compressedFloorSs(plain) + BOUNDARY_SLACK_SS;

            // Precondition: the accidental widens the chain by more than that slack. Without this
            // the refusal below would prove nothing.
            assertThat(compressedFloorSs(sharpened))
                .as("the accidental widens the compressed floor past the slack")
                .isGreaterThan(marginSs);

            assertThat(plain.fitsWithinLine(marginSs))
                .as("the unmodified line still fits at that margin")
                .isTrue();
            assertThat(sharpened.fitsWithinLine(marginSs))
                .as("the same line with the accidental no longer fits")
                .isFalse();
        }
    }
}
