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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.Font;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.engraving.Staff;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineSpacing;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link StaffLinesLayout}'s positioning formula.
 * <p>
 * Every line's extents are injected directly as a {@link LayoutResult}, so the layout
 * engine never runs and each test states exactly the geometry it depends on. The formula
 * under test is:
 * <pre>
 * S = max over N in [0, count-2] of ( belowMidline[N] + gap + aboveMidline[N+1] )
 * midlineY[N] = paintAboveMidline[0] + N * S
 * </pre>
 * The asymmetry in that first line — {@code aboveMidline[0]} never appears in a pair — is
 * what distinguishes this algorithm from "uniform height", and several tests below exist
 * only to pin it.
 */
class StaffLinesLayoutTest extends UnitTest {

    /**
     * Content beyond the minimum staff surround, so a line built with it clears the
     * {@link LineSpacing#MIN_ABOVE_MIDLINE_SS} / {@link LineSpacing#MIN_BELOW_MIDLINE_SS}
     * floors and its measured extents — not the floors — drive the spacing.
     */
    private static final double CONTENT_HEADROOM_SS = 1.5;

    /** A deliberately large extent, used to make one line dominate the pairwise maximum. */
    private static final double TALL_CONTENT_SS = 8.0;

    /** Width given to each line panel; the vertical formula is indifferent to it. */
    private static final int LINE_WIDTH_PX = 200;

    /** Panel width used when laying out, wide enough that no child is width-constrained. */
    private static final int PANEL_WIDTH_PX = 400;

    /** Panel height used when laying out; the layout derives child Y from geometry, not this. */
    private static final int PANEL_HEIGHT_PX = 2000;

    private static final Font TEST_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    /**
     * Lyric geometry the test lines are measured against. The zero gap and zero widths keep
     * the lyrics band to the reserved verse rows alone, so expectations stay legible.
     */
    private static final LyricRenderMetrics LYRIC_RENDER_METRICS =
        new LyricRenderMetrics(TEST_FONT, TEST_FONT, 0.0, 0.0, 0.0);

    /**
     * The above/below-staff content extents of one test line, in staff spaces.
     * <p>
     * These are the two numbers a real {@code LayoutResult} derives from a line's ink, and
     * the only per-line input {@link StaffLinesLayout} reads.
     */
    private record LineExtents(double contentAboveStaffSs, double contentBelowStaffSs) {}

    /** A line whose extents sit exactly at the minimum staff surround. */
    private static LineExtents plainLine() {
        return new LineExtents(Staff.MIN_ABOVE_STAFF_SS, Staff.MIN_BELOW_STAFF_SS);
    }

    /** A line reaching {@code extraSs} further above its staff than {@link #plainLine}. */
    private static LineExtents lineReachingAbove(double extraSs) {
        return new LineExtents(Staff.MIN_ABOVE_STAFF_SS + extraSs, Staff.MIN_BELOW_STAFF_SS);
    }

    /** A line reaching {@code extraSs} further below its staff than {@link #plainLine}. */
    private static LineExtents lineReachingBelow(double extraSs) {
        return new LineExtents(Staff.MIN_ABOVE_STAFF_SS, Staff.MIN_BELOW_STAFF_SS + extraSs);
    }

    /**
     * The lyrics band every test line reserves. No test line carries a verse, so each still
     * reserves {@link LineSpacing#MIN_RESERVED_VERSE_ROWS} rows of measured font ink.
     */
    private static double lyricsBandHeightSs() {
        return LineSpacing.LYRICS_ROW_MARGIN_SS
            + LineSpacing.MIN_RESERVED_VERSE_ROWS * LYRIC_RENDER_METRICS.lyricBoxHeightSs();
    }

    private static double aboveMidlineSs(LineExtents extents) {
        return Staff.STAFF_HALF_SS + extents.contentAboveStaffSs();
    }

    private static double belowMidlineSs(LineExtents extents) {
        return Staff.STAFF_HALF_SS + extents.contentBelowStaffSs() + lyricsBandHeightSs();
    }

    /** The uniform midline-to-midline distance the formula produces for {@code lines}. */
    private static double midlineSpacingSs(LineExtents... lines) {
        var spacingSs = 0.0;
        var gapSs = LineSpacing.interLineGapSs(LineSpacing.DEFAULT_INTER_LINE_GAP_SS);

        for (var i = 0; i < lines.length - 1; i++) {
            spacingSs = Math.max(spacingSs, belowMidlineSs(lines[i]) + gapSs + aboveMidlineSs(lines[i + 1]));
        }

        return spacingSs;
    }

    private static int toViewPx(double valueSs) {
        return (int) Math.round(valueSs * ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    /**
     * Slack allowed when comparing two independently rounded pixel distances.
     * <p>
     * {@link StaffLinesLayout} rounds each child's Y to the nearest pixel, so a midline gap
     * measured between two of them can land one pixel either side of the exact staff-space
     * spacing. The uniformity guarantee is exact in staff spaces and approximate in pixels;
     * asserting exact pixel equality would be asserting a rounding accident.
     */
    private static final int ROUNDING_TOLERANCE_PX = 1;

    /**
     * Builds a laid-out {@link StaffPanel} whose lines carry exactly {@code lines}' extents.
     * <p>
     * Passing a {@code null} entry leaves that line's {@code layoutResult} unset, which is the
     * issue-#449 "line does not fit" state the layout must tolerate.
     */
    private static StaffPanel laidOutPanel(LineExtents... lines) {
        var panel = panelWith(lines);
        panel.setSize(PANEL_WIDTH_PX, PANEL_HEIGHT_PX);
        panel.doLayout();
        return panel;
    }

    private static StaffPanel panelWith(LineExtents... lines) {
        var song = new Song();
        song.withoutMutationTracking(() -> {
            for (var i = 1; i < lines.length; i++) {
                song.addLine(new Line(song));
            }
        });

        var panel = new StaffPanel();

        if (lines.length > 0) {
            panel.setSong(song);
        }

        var scoreView = mock(ScoreView.class);
        when(scoreView.getLyricRenderMetrics()).thenReturn(LYRIC_RENDER_METRICS);
        when(scoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));

        var linePanels = panel.getLinePanels();

        for (var i = 0; i < linePanels.size(); i++) {
            var linePanel = linePanels.get(i);
            // Width only: the stack's height comes from the layout results, never from a
            // child's preferred height.
            linePanel.setPreferredSize(new Dimension(LINE_WIDTH_PX, 0));

            var lineComponent = linePanel.getLineComponent();
            lineComponent.setScoreView(scoreView);
            var extents = lines[i];

            if (extents == null) {
                // setSong() already ran a real layout, so the result must be cleared
                // explicitly rather than merely left unset — and the line marked as not
                // fitting, or ensureAllLineLayouts() would simply lay it out again.
                lineComponent.layoutResult = null;
                lineComponent.setLineDoesNotFit(true);
            } else {
                lineComponent.layoutResult = LayoutResult.builder()
                    .setContentAboveStaffSs(extents.contentAboveStaffSs())
                    .setContentBelowStaffSs(extents.contentBelowStaffSs())
                    .build();
            }

            // Clean, so ensureAllLineLayouts() finds nothing to recompute.
            lineComponent.layoutDirty = false;
        }

        return panel;
    }

    /** The midline Y of laid-out line {@code index}, in panel pixels. */
    private static int midlineYPx(StaffPanel panel, int index) {
        var linePanel = panel.getLinePanels().get(index);
        var result = linePanel.getLineComponent().getLayoutResult();

        if (result == null) {
            throw new AssertionError("line " + index + " has no layout result to measure");
        }

        return linePanel.getY() + toViewPx(result.paintAboveMidlineSs());
    }

    /** The gaps between consecutive midlines of a laid-out panel, in panel pixels. */
    private static int[] midlineGapsPx(StaffPanel panel) {
        var count = panel.getLinePanels().size();
        var gaps = new int[count - 1];

        for (var i = 0; i < count - 1; i++) {
            gaps[i] = midlineYPx(panel, i + 1) - midlineYPx(panel, i);
        }

        return gaps;
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class UniformSpacing {

        /**
         * Three lines with identical extents are separated by one uniform midline distance,
         * and the first line's midline sits its own painted reach below the panel top.
         */
        @Test
        void testEqualLinesAreSpacedUniformly() {
            var lines = new LineExtents[] {plainLine(), plainLine(), plainLine()};
            var panel = laidOutPanel(lines);
            var expectedSpacingPx = toViewPx(midlineSpacingSs(lines));

            for (var gapPx : midlineGapsPx(panel)) {
                assertThat(gapPx)
                    .as("equal lines → every midline gap is the uniform spacing")
                    .isCloseTo(expectedSpacingPx, within(ROUNDING_TOLERANCE_PX));
            }
        }

        /**
         * The spacing is driven by the worst <em>adjacent</em> pair, not by the global
         * maximum of all extents. The deepest-below line and the tallest-above line are
         * placed non-adjacently here, so a global-maximum implementation would space the
         * block wider than the pairwise rule requires.
         */
        @Test
        void testSpacingFollowsWorstAdjacentPairNotGlobalMaximum() {
            // Line 0 reaches far below, line 2 reaches far above, and plain line 1 separates
            // them — so the two extremes never share a pair.
            var lines = new LineExtents[] {
                lineReachingBelow(TALL_CONTENT_SS),
                plainLine(),
                lineReachingAbove(TALL_CONTENT_SS)
            };
            var panel = laidOutPanel(lines);

            var gapSs = LineSpacing.interLineGapSs(LineSpacing.DEFAULT_INTER_LINE_GAP_SS);
            var globalMaximumSs = belowMidlineSs(lines[0]) + gapSs + aboveMidlineSs(lines[2]);
            var pairwiseSs = midlineSpacingSs(lines);

            assertThat(pairwiseSs)
                .as("the extremes are not adjacent, so the pairwise rule must spend less than the global maximum")
                .isLessThan(globalMaximumSs);

            for (var gapPx : midlineGapsPx(panel)) {
                assertThat(gapPx)
                    .as("spacing comes from the worst adjacent pair")
                    .isCloseTo(toViewPx(pairwiseSs), within(ROUNDING_TOLERANCE_PX));
            }
        }

        /**
         * Adjacent lines' content never overlaps: the measured content bottom of one line
         * always clears the measured content top of the next by at least the inter-line gap.
         * This is the guarantee that lets Swing's child clipping alone be correct.
         */
        @Test
        void testAdjacentContentIsNeverCloserThanTheInterLineGap() {
            var lines = new LineExtents[] {
                lineReachingBelow(TALL_CONTENT_SS),
                lineReachingAbove(CONTENT_HEADROOM_SS),
                plainLine()
            };
            var panel = laidOutPanel(lines);
            var minimumGapPx = toViewPx(LineSpacing.interLineGapSs(LineSpacing.DEFAULT_INTER_LINE_GAP_SS));

            for (var i = 0; i < lines.length - 1; i++) {
                var contentBottomPx = midlineYPx(panel, i) + toViewPx(belowMidlineSs(lines[i]));
                var nextContentTopPx = midlineYPx(panel, i + 1) - toViewPx(aboveMidlineSs(lines[i + 1]));

                // The guarantee is exact in staff spaces; per-child pixel rounding can shave
                // one pixel off the measured result without the ink actually colliding.
                assertThat(nextContentTopPx - contentBottomPx)
                    .as("content gap between line %d and line %d", i, i + 1)
                    .isGreaterThanOrEqualTo(minimumGapPx - ROUNDING_TOLERANCE_PX);
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FirstLineAsymmetry {

        /**
         * Content above the <em>first</em> line translates the whole block downward without
         * widening the spacing — {@code aboveMidline[0]} appears only in {@code midlineY[0]},
         * never in a pair. This is the defining property of the algorithm.
         */
        @Test
        void testContentAboveFirstLineTranslatesBlockWithoutWideningSpacing() {
            var baseline = new LineExtents[] {plainLine(), plainLine()};
            var raised = new LineExtents[] {lineReachingAbove(TALL_CONTENT_SS), plainLine()};

            var baselinePanel = laidOutPanel(baseline);
            var raisedPanel = laidOutPanel(raised);

            assertThat(midlineGapsPx(raisedPanel))
                .as("content above line 0 must not widen the spacing")
                .isEqualTo(midlineGapsPx(baselinePanel));

            assertThat(midlineYPx(raisedPanel, 0) - midlineYPx(baselinePanel, 0))
                .as("the whole block instead shifts down by the added headroom")
                .isEqualTo(toViewPx(TALL_CONTENT_SS));
        }

        /**
         * Content <em>below</em> the first line is not exempt: it enters pair (0,1) and so
         * does widen the uniform spacing. The counterpart to the test above — together they
         * pin the asymmetry to the side it actually applies to.
         */
        @Test
        void testContentBelowFirstLineWidensSpacing() {
            var baseline = new LineExtents[] {plainLine(), plainLine()};
            var deepened = new LineExtents[] {lineReachingBelow(TALL_CONTENT_SS), plainLine()};

            var baselineGapPx = midlineGapsPx(laidOutPanel(baseline))[0];
            var deepenedGapPx = midlineGapsPx(laidOutPanel(deepened))[0];

            assertThat(deepenedGapPx - baselineGapPx)
                .as("content below line 0 widens the spacing by exactly what was added")
                .isEqualTo(toViewPx(TALL_CONTENT_SS));
        }

        /**
         * Content above a <em>middle</em> line widens the spacing, and because the spacing is
         * one song-wide distance every gap widens together — including the gap that does not
         * touch the tall line.
         */
        @Test
        void testContentAboveMiddleLineWidensAllGapsEqually() {
            var baseline = new LineExtents[] {plainLine(), plainLine(), plainLine()};
            var raised = new LineExtents[] {
                plainLine(), lineReachingAbove(TALL_CONTENT_SS), plainLine()
            };

            var baselineGapPx = midlineGapsPx(laidOutPanel(baseline))[0];
            var raisedGaps = midlineGapsPx(laidOutPanel(raised));

            assertThat(raisedGaps[0] - baselineGapPx)
                .as("content above line 1 widens the pair it belongs to")
                .isCloseTo(toViewPx(TALL_CONTENT_SS), within(ROUNDING_TOLERANCE_PX));

            assertThat(raisedGaps[1])
                .as("and the spacing is uniform, so the untouched pair widens identically")
                .isCloseTo(raisedGaps[0], within(ROUNDING_TOLERANCE_PX));
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class DegenerateCases {

        /**
         * A panel with no children has nothing to measure and reports no size, rather than
         * throwing or reporting the insets alone.
         */
        @Test
        void testNoChildrenReportsZeroSize() {
            var panel = new StaffPanel();

            assertThat(panel.getPreferredSize())
                .as("no line panels → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * One child forms no pairs, so no spacing is computed and it sits at the top of the
         * panel — its own painted reach above its midline, and no inter-line gap anywhere.
         */
        @Test
        void testSingleLineSitsAtPanelTopWithNoSpacing() {
            var panel = laidOutPanel(plainLine());

            assertThat(panel.getLinePanels().get(0).getY())
                .as("the only line starts at the panel top")
                .isEqualTo(0);
        }

        /**
         * A line whose layout could not be produced (issue #449) is reserved the minimum
         * staff surround instead of crashing the whole panel's layout.
         */
        @Test
        void testLineWithNullLayoutResultReservesTheMinimumStaffSurround() {
            var lines = new LineExtents[] {plainLine(), null};
            var panel = laidOutPanel(lines);
            var linePanels = panel.getLinePanels();

            assertThat(linePanels.get(1).getLineComponent().getLayoutResult())
                .as("fixture precondition: line 1 must have no layout result")
                .isNull();

            var expectedHeightPx = toViewPx(
                LineSpacing.MIN_ABOVE_MIDLINE_SS + LineSpacing.MIN_BELOW_MIDLINE_SS);

            assertThat(linePanels.get(1).getHeight())
                .as("a line with no layout result is still sized to the minimum staff surround")
                .isEqualTo(expectedHeightPx);

            assertThat(linePanels.get(1).getY())
                .as("and it is still positioned below the first line")
                .isGreaterThan(linePanels.get(0).getY());
        }
    }
}
