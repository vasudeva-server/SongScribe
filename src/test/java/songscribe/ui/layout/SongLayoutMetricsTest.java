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
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;

class SongLayoutMetricsTest extends UnitTest {

    private static final double TOLERANCE = 0.0001;

    /** Builds a LayoutResult with the given above-staff and total height values. */
    private static LayoutResult fakeLayout(double aboveStaffSs, double lineHeightSs) {
        return fakeLayout(aboveStaffSs, lineHeightSs, 0.0);
    }

    /** Builds a LayoutResult with the given above-staff, total height, and below-content values. */
    private static LayoutResult fakeLayout(double aboveStaffSs, double lineHeightSs, double belowContentSs) {
        return LayoutResult.builder()
            .setAboveStaffSs(aboveStaffSs)
            .setLineHeightSs(lineHeightSs)
            .setBelowContentSs(belowContentSs)
            .build();
    }

    /** Builds a LayoutResult with the given above-staff, total height, and verse count. */
    private static LayoutResult fakeLayoutWithVerses(double aboveStaffSs, double lineHeightSs, int verseCount) {
        return fakeLayoutWithVerses(aboveStaffSs, lineHeightSs, 0.0, verseCount);
    }

    /** Builds a LayoutResult with the given above-staff, total height, below-content, and verse count. */
    private static LayoutResult fakeLayoutWithVerses(
        double aboveStaffSs, double lineHeightSs, double belowContentSs, int verseCount) {
        return LayoutResult.builder()
            .setAboveStaffSs(aboveStaffSs)
            .setLineHeightSs(lineHeightSs)
            .setBelowContentSs(belowContentSs)
            .setVerseCount(verseCount)
            .build();
    }

    @Test
    void testEmptyListUsesMinimumDefaults() {
        var metrics = SongLayoutMetricsBuilder.build(List.of(), 0.0);

        assertThat(metrics.maxAboveStaffSs()).isEqualTo(LayoutStylesheet.MIN_ABOVE_STAFF_SS, within(TOLERANCE));
        assertThat(metrics.maxBelowStaffSs())
            .isEqualTo(LayoutStylesheet.MIN_BELOW_STAFF_SS + LayoutStylesheet.INTER_LINE_MARGIN_SS, within(TOLERANCE));
        assertThat(metrics.totalLineHeightSs())
            .isEqualTo(
                LayoutStylesheet.MIN_ABOVE_STAFF_SS
                + LayoutStylesheet.STAFF_HEIGHT_SS
                + LayoutStylesheet.MIN_BELOW_STAFF_SS
                + LayoutStylesheet.INTER_LINE_MARGIN_SS,
                within(TOLERANCE));
    }

    @Test
    void testMaxAboveStaffSsTakesMaxAcrossLines() {
        // Line A has 3.0 above; line B has 5.0 above
        var lineA = fakeLayout(3.0, 3.0 + LayoutStylesheet.STAFF_HEIGHT_SS + 2.0);
        var lineB = fakeLayout(5.0, 5.0 + LayoutStylesheet.STAFF_HEIGHT_SS + 2.0);

        var metrics = SongLayoutMetricsBuilder.build(List.of(lineA, lineB), 0.0);

        assertThat(metrics.maxAboveStaffSs()).isEqualTo(5.0, within(TOLERANCE));
    }

    @Test
    void testMaxBelowStaffSsTakesMaxAcrossLines() {
        // Use values that exceed the default minimum floor so the max is data-driven
        var minFloor = LayoutStylesheet.MIN_BELOW_STAFF_SS + LayoutStylesheet.INTER_LINE_MARGIN_SS;
        var belowA = minFloor + 1.0;
        var belowB = minFloor + 3.0;

        var lineA = fakeLayout(2.0, 2.0 + LayoutStylesheet.STAFF_HEIGHT_SS + belowA);
        var lineB = fakeLayout(2.0, 2.0 + LayoutStylesheet.STAFF_HEIGHT_SS + belowB);

        var metrics = SongLayoutMetricsBuilder.build(List.of(lineA, lineB), 0.0);

        assertThat(metrics.maxBelowStaffSs()).isEqualTo(belowB, within(TOLERANCE));
    }

    @Test
    void testTotalLineHeightSsIsSumOfParts() {
        // Use a below value that exceeds the minimum floor so total is purely data-driven
        var above = 3.5;
        var below = LayoutStylesheet.MIN_BELOW_STAFF_SS + LayoutStylesheet.INTER_LINE_MARGIN_SS + 2.0;
        var layout = fakeLayout(above, above + LayoutStylesheet.STAFF_HEIGHT_SS + below);

        var metrics = SongLayoutMetricsBuilder.build(List.of(layout), 0.0);

        assertThat(metrics.totalLineHeightSs())
            .isEqualTo(above + LayoutStylesheet.STAFF_HEIGHT_SS + below, within(TOLERANCE));
    }

    @Test
    void testVerseCountCollapsesWhenNoLyrics() {
        var layout = fakeLayout(2.0, 2.0 + LayoutStylesheet.STAFF_HEIGHT_SS + 2.0);

        var metrics = SongLayoutMetricsBuilder.build(List.of(layout), 0.0);

        assertThat(metrics.verseCount()).isZero();
        assertThat(metrics.staffToLyricsGapSs()).isZero();
        assertThat(metrics.lyricsLineHeightSs()).isZero();
        assertThat(metrics.lyricsBandHeightSs()).isZero();
    }

    @Test
    void testLyricsBandPopulatedWhenVersesPresent() {
        var above = 3.0;
        var below = LayoutStylesheet.MIN_BELOW_STAFF_SS + LayoutStylesheet.INTER_LINE_MARGIN_SS + 1.0;
        var lineHeight = above + LayoutStylesheet.STAFF_HEIGHT_SS + below;
        // Two lines: one with verse count 2, one with verse count 1 — max should win.
        var layoutA = fakeLayoutWithVerses(above, lineHeight, 2);
        var layoutB = fakeLayoutWithVerses(above, lineHeight, 1);

        var metrics = SongLayoutMetricsBuilder.build(List.of(layoutA, layoutB), 0.0);

        assertThat(metrics.verseCount()).isEqualTo(2);
        assertThat(metrics.staffToLyricsGapSs()).isEqualTo(LayoutStylesheet.LYRICS_ROW_MARGIN_SS, within(TOLERANCE));
        assertThat(metrics.lyricsLineHeightSs()).isEqualTo(LayoutStylesheet.LYRICS_HEIGHT_SS, within(TOLERANCE));
        assertThat(metrics.lyricsBandHeightSs())
            .isEqualTo(2 * LayoutStylesheet.LYRICS_HEIGHT_SS, within(TOLERANCE));
        assertThat(metrics.totalLineHeightSs())
            .isEqualTo(
                above
                + LayoutStylesheet.STAFF_HEIGHT_SS
                + below
                + LayoutStylesheet.LYRICS_ROW_MARGIN_SS
                + 2 * LayoutStylesheet.LYRICS_HEIGHT_SS,
                within(TOLERANCE));
    }

    @Test
    void testVerseBaselineYHelper() {
        var above = 3.0;
        var belowContent = 2.5;
        var lineHeight = above + LayoutStylesheet.STAFF_HEIGHT_SS + 2.0;
        var metrics = SongLayoutMetricsBuilder.build(
            List.of(fakeLayoutWithVerses(above, lineHeight, belowContent, 2)), 0.0);

        var expectedVerse1 = metrics.staffBottomYSsInLine()
            + metrics.maxBelowContentSs()
            + metrics.staffToLyricsGapSs();
        var expectedVerse2 = metrics.staffBottomYSsInLine()
            + metrics.maxBelowContentSs()
            + metrics.staffToLyricsGapSs()
            + metrics.lyricsLineHeightSs();

        assertThat(metrics.verseYSsInLine(1)).isEqualTo(expectedVerse1, within(TOLERANCE));
        assertThat(metrics.verseYSsInLine(2)).isEqualTo(expectedVerse2, within(TOLERANCE));
    }

    @Test
    void testMaxBelowContentSsTakesMaxAcrossLines() {
        var lineA = fakeLayout(2.0, 2.0 + LayoutStylesheet.STAFF_HEIGHT_SS + 2.0, 1.5);
        var lineB = fakeLayout(2.0, 2.0 + LayoutStylesheet.STAFF_HEIGHT_SS + 2.0, 3.5);

        var metrics = SongLayoutMetricsBuilder.build(List.of(lineA, lineB), 0.0);

        assertThat(metrics.maxBelowContentSs()).isEqualTo(3.5, within(TOLERANCE));
    }

    @Test
    void testStaffYHelpers() {
        var above = 3.0;
        var below = 2.0;
        var layout = fakeLayout(above, above + LayoutStylesheet.STAFF_HEIGHT_SS + below);

        var metrics = SongLayoutMetricsBuilder.build(List.of(layout), 0.0);

        assertThat(metrics.staffTopYSsInLine()).isEqualTo(above, within(TOLERANCE));
        assertThat(metrics.staffBottomYSsInLine())
            .isEqualTo(above + LayoutStylesheet.STAFF_HEIGHT_SS, within(TOLERANCE));
    }
}
