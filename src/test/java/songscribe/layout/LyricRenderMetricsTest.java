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
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mockStatic;
import static songscribe.dom.StaffElementFactory.crotchet;

import java.awt.Font;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.message.MessageCenter;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;

class LyricRenderMetricsTest extends UnitTest {

    private static final double TOLERANCE = 0.0001;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0, 0.0);
    // Arbitrary column position and line width: this test measures a box's width, which neither
    // affects.
    private static final double COLUMN_X_SS = 5.0;
    private static final double LINE_WIDTH_SS = 100.0;

    private Song song;
    private Line line;
    private MockedStatic<MessageCenter> messageCenterMock;

    @BeforeEach
    void setUp() {
        messageCenterMock = mockStatic(MessageCenter.class);
        song = new Song();
        line = song.getLine(0);
    }

    @AfterEach
    void tearDown() {
        // Restore the default scale so a test that changed the zoom does not pollute
        // later tests sharing the JVM-global ScaleContext singleton.
        ScaleContext.setPixelsPerStaffSpace(ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
        messageCenterMock.close();
    }

    // T5: lyricBoxWidthSs(text) returns the same value as LyricBoxLayout produces for the same text.
    // The column is built the way layout builds it rather than hand-assembled, because the box width
    // is the width ElementColumnBuilder measured for the active verse, reused rather than re-measured.
    @Test
    void testLyricBoxWidthSsMatchesLayoutBoxWidth() {
        var text = "do";
        var element = crotchet();
        element.lyrics.add(new Lyric(Lyric.FIRST_VERSE, text, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
        song.withoutMutationTracking(() -> line.addElement(element));

        var column = new ElementColumnBuilder(LYRIC_METRICS).buildColumn(element, line);
        column.setXSs(COLUMN_X_SS);

        var result = LyricLayoutBuilder.build(
            List.of(column), song.getActiveVerse(), LYRIC_METRICS, false, LINE_WIDTH_SS);
        var boxes = result.boxes().get(element);

        assertThat(boxes).as("Expected a lyric box for the element, but none were produced").isNotNull();

        assertThat(boxes).hasSize(1);

        var box = boxes.getFirst();

        assertThat(box.widthSs()).isCloseTo(LYRIC_METRICS.lyricBoxWidthSs(text), within(TOLERANCE));
    }

    // -----------------------------------------------------------------------
    // Row 7: lyricBoxWidthSs("") → 0.0 (empty guard)
    // -----------------------------------------------------------------------

    @Test
    void testLyricBoxWidthSsEmptyStringReturnsZero() {
        assertThat(LYRIC_METRICS.lyricBoxWidthSs("")).isEqualTo(0.0);
    }

    // -----------------------------------------------------------------------
    // Row 8: lyricBoxWidthSs(text) — independent oracle via ScaleContext
    // -----------------------------------------------------------------------

    @Test
    void testLyricBoxWidthSsNonEmptyMatchesScaleContextTextWidth() {
        // Independent oracle: call ScaleContext.textWidthSs directly (the underlying
        // utility the production delegates to) rather than lyricBoxWidthSs itself.
        // This is NOT f(x)≈f(x) — it catches any divergence if the production method
        // switches to a different width-computation path.
        var text = "do";
        var expectedWidthSs = ScaleContext.textWidthSs(LYRICS_FONT, text).value();
        assertThat(LYRIC_METRICS.lyricBoxWidthSs(text)).isCloseTo(expectedWidthSs, within(TOLERANCE));
    }

    // -----------------------------------------------------------------------
    // Row 9: lyricBoxMetricsSs("") → LyricBoxMetrics.EMPTY
    // -----------------------------------------------------------------------

    @Test
    void testLyricBoxMetricsSsEmptyStringReturnsEmptySentinel() {
        assertThat(LYRIC_METRICS.lyricBoxMetricsSs(""))
            .isSameAs(LyricRenderMetrics.LyricBoxMetrics.EMPTY);
    }

    // -----------------------------------------------------------------------
    // Row 10: lyricBoxMetricsSs(text) — advance/bearing/extent structural relations
    // -----------------------------------------------------------------------

    @Test
    void testLyricBoxMetricsSsNonEmptyHasPositiveAdvanceAndConsistentExtents() {
        var metrics = LYRIC_METRICS.lyricBoxMetricsSs("do");
        assertAll(
            () -> assertThat(metrics).isNotSameAs(LyricRenderMetrics.LyricBoxMetrics.EMPTY),
            () -> assertThat(metrics.advanceSs())
                      .describedAs("advance must be positive for non-empty text")
                      .isGreaterThan(0.0),
            () -> assertThat(metrics.rightExtentSs())
                      .describedAs("rightmost ink pixel must be to the right of the origin")
                      .isGreaterThan(0.0),
            () -> assertThat(metrics.leftBearingSs())
                      .describedAs("leftBearing is the left overhang; must not exceed advance")
                      .isLessThanOrEqualTo(metrics.advanceSs())
        );
    }

    // -----------------------------------------------------------------------
    // Row 11: lyricBoxHeightSs() — the measured ink height of the lyrics font
    // -----------------------------------------------------------------------

    @Test
    void testLyricBoxHeightSsIsPositiveAndEqualsMeasuredInkHeight() {
        assertAll(
            () -> assertThat(LYRIC_METRICS.lyricBoxHeightSs())
                      .describedAs("height must be positive for a real font")
                      .isGreaterThan(0.0),
            () -> assertThat(LYRIC_METRICS.lyricBoxHeightSs())
                      .isCloseTo(LyricRenderMetrics.fontHeightSs(LYRICS_FONT), within(TOLERANCE))
        );
    }

    /**
     * The whole point of measuring ink rather than the font's own ascent/descent: the
     * reserved row must be strictly shorter than the font-wide worst case, or the lyrics
     * band carries dead space that shows up as excess space between staff lines.
     */
    @Test
    void testLyricBoxHeightSsIsShorterThanAscentPlusDescent() {
        var fontWideHeightSs =
            ScaleContext.fontAscentSs(LYRICS_FONT).value() + ScaleContext.fontDescentSs(LYRICS_FONT).value();
        assertThat(LYRIC_METRICS.lyricBoxHeightSs()).isLessThan(fontWideHeightSs);
    }

    /**
     * The above-baseline extent feeds the first verse's baseline offset, so it must exclude
     * the descender that {@link LyricRenderMetrics#fontHeightSs} includes.
     */
    @Test
    void testFontAboveBaselineSsExcludesTheDescender() {
        assertThat(LyricRenderMetrics.fontAboveBaselineSs(LYRICS_FONT))
            .isGreaterThan(0.0)
            .isLessThan(LyricRenderMetrics.fontHeightSs(LYRICS_FONT));
    }

    // -----------------------------------------------------------------------
    // Row 12: preferredHyphenCellWidthSs() = HYPHEN_WIDENING_FACTOR × hyphenWidthSs
    // -----------------------------------------------------------------------

    @Test
    void testPreferredHyphenCellWidthSsEqualsFactorTimesHyphenWidth() {
        final var hyphenWidthSs = 1.5;
        final var expectedWidthSs = LyricRenderMetrics.HYPHEN_WIDENING_FACTOR * hyphenWidthSs;
        var metricsWithHyphen = new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, hyphenWidthSs, 0.0, 0.0);
        assertThat(metricsWithHyphen.preferredHyphenCellWidthSs())
            .isCloseTo(expectedWidthSs, within(TOLERANCE));
    }

    // -----------------------------------------------------------------------
    // forFont() — every component derived from the lyrics font alone
    // -----------------------------------------------------------------------

    /**
     * The staff-to-lyrics gap is a baseline offset, not the visual gap: it must carry the
     * font's above-baseline ink on top of the margin, or every verse in the song sits one
     * ink-height too high.
     */
    @Test
    void testForFontComposesStaffToLyricsGapFromMarginPlusAboveBaselineInk() {
        var expectedGapSs =
            LineSpacing.LYRICS_ROW_MARGIN_SS + LyricRenderMetrics.fontAboveBaselineSs(LYRICS_FONT);

        assertThat(LyricRenderMetrics.forFont(LYRICS_FONT).staffToLyricsGapSs())
            .isCloseTo(expectedGapSs, within(TOLERANCE));
    }

    /**
     * The gap must exceed the bare margin — a regression that dropped the ink term would
     * still satisfy a tolerance-based check against the margin alone.
     */
    @Test
    void testForFontGapExceedsTheVisualMarginByTheInkHeight() {
        assertThat(LyricRenderMetrics.forFont(LYRICS_FONT).staffToLyricsGapSs())
            .isGreaterThan(LineSpacing.LYRICS_ROW_MARGIN_SS);
    }

    @Test
    void testForFontDerivesFontsAndGlyphWidths() {
        var metrics = LyricRenderMetrics.forFont(LYRICS_FONT);

        assertAll(
            () -> assertThat(metrics.lyricsFont()).isEqualTo(LYRICS_FONT),
            () -> assertThat(metrics.scaledLyricsFont())
                .isEqualTo(ScaleContext.scaleFont(LYRICS_FONT)),
            () -> assertThat(metrics.hyphenWidthSs())
                .isCloseTo(ScaleContext.textWidthSs(LYRICS_FONT, "-").value(), within(TOLERANCE)),
            () -> assertThat(metrics.spaceWidthSs())
                .isCloseTo(ScaleContext.textWidthSs(LYRICS_FONT, " ").value(), within(TOLERANCE))
        );
    }
}
