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

import java.awt.Font;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.layout.ElementColumn;
import songscribe.layout.LyricLayoutBuilder;
import songscribe.layout.LyricRenderMetrics;
import songscribe.message.MessageCenter;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.smufl.Engraving;

class LyricRenderMetricsTest extends UnitTest {

    private static final double TOLERANCE = 0.0001;
    private static final Font LYRICS_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final LyricRenderMetrics LYRIC_METRICS =
        new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, 0.0, 0.0);

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
        messageCenterMock.close();
    }

    // T5: lyricBoxWidthSs(text) returns the same value as LyricBoxLayout produces for the same text
    @Test
    void testLyricBoxWidthSsMatchesLayoutBoxWidth() {
        var text = "do";
        var element = ElementType.CROTCHET.newInstance();
        element.lyrics.add(new Lyric(2, text, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
        song.withoutMutationTracking(() -> line.addElement(element));
        var column = columnAt(element, 5.0);
        var result = LyricLayoutBuilder.build(List.of(column), LYRIC_METRICS, false, 100.0);
        var boxes = result.boxes().get(element);

        if (boxes == null) {
            throw new AssertionError("Expected verse-2 boxes for element, but none were produced");
        }

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
        final String text = "do";
        final double expectedWidthSs = ScaleContext.textWidthSs(LYRICS_FONT, text);
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
    // Row 11: lyricBoxHeightSs() — positive and equals ascent + descent
    // -----------------------------------------------------------------------

    @Test
    void testLyricBoxHeightSsIsPositiveAndEqualsAscentPlusDescent() {
        final double expectedHeightSs =
            ScaleContext.fontAscentSs(LYRICS_FONT) + ScaleContext.fontDescentSs(LYRICS_FONT);
        assertAll(
            () -> assertThat(LYRIC_METRICS.lyricBoxHeightSs())
                      .describedAs("height must be positive for a real font")
                      .isGreaterThan(0.0),
            () -> assertThat(LYRIC_METRICS.lyricBoxHeightSs())
                      .isCloseTo(expectedHeightSs, within(TOLERANCE))
        );
    }

    // -----------------------------------------------------------------------
    // Row 12: preferredHyphenCellWidthSs() = HYPHEN_WIDENING_FACTOR × hyphenWidthSs
    // -----------------------------------------------------------------------

    @Test
    void testPreferredHyphenCellWidthSsEqualsFactorTimesHyphenWidth() {
        final double hyphenWidthSs = 1.5;
        final double expectedWidthSs = LyricRenderMetrics.HYPHEN_WIDENING_FACTOR * hyphenWidthSs;
        var metricsWithHyphen = new LyricRenderMetrics(LYRICS_FONT, LYRICS_FONT, hyphenWidthSs, 0.0);
        assertThat(metricsWithHyphen.preferredHyphenCellWidthSs())
            .isCloseTo(expectedWidthSs, within(TOLERANCE));
    }

    // -----------------------------------------------------------------------
    // Row 13: COMPRESSED_MIN_SYLLABLE_GAP_SS < MIN_SYLLABLE_GAP_SS invariant
    // -----------------------------------------------------------------------

    @Test
    void testCompressedGapIsStrictlyLessThanNormalGap() {
        assertThat(LyricRenderMetrics.COMPRESSED_MIN_SYLLABLE_GAP_SS)
            .isLessThan(LyricRenderMetrics.MIN_SYLLABLE_GAP_SS);
    }

    // ==========================================================================
    // Helpers
    // ==========================================================================

    private static ElementColumn columnAt(StaffElement element, double xSs) {
        var column = new ElementColumn(
            element, Collections.emptyList(), 0.0, Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false);
        column.setXSs(xSs);
        return column;
    }
}
