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

package songscribe.dom;

import java.awt.Font;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.font.FontKey;
import songscribe.util.MyFontUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AttributionPane}'s measurement, caching, and line
 * resolution. Lines are injected via {@link AttributionPane#setOverrideLines}
 * so the measurement contract can be exercised without the Song model.
 * <p>
 * Expected widths/heights are derived from {@link MyFontUtils#getFontMetrics}
 * — the same source the production code measures with — so the assertions pin
 * the reduction/summation logic without hardcoding platform-specific pixels.
 */
class AttributionPaneTest extends UnitTest {

    // Two headless-safe fonts of clearly different sizes, so a line measured
    // with the wrong role font produces a different height.
    private static final int ATTRIBUTION_FONT_SIZE = 24;
    private static final int SUB_ATTRIBUTION_FONT_SIZE = 12;
    private static final Font ATTRIBUTION_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, ATTRIBUTION_FONT_SIZE);
    private static final Font SUB_ATTRIBUTION_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, SUB_ATTRIBUTION_FONT_SIZE);

    private static final int MARGIN_TOP = 5;
    private static final int MARGIN_BOTTOM = 7;

    private static final String SHORT_TEXT = "I";
    private static final String LONG_TEXT = "A much longer attribution line";
    private static final String WORDS_AND_MUSIC_BY = "Words and Music by";
    private static final String YEAR_TEXT = "1975";

    private static final AttributionLine ATTRIBUTION_LINE =
        new AttributionLine(WORDS_AND_MUSIC_BY, FontKey.ATTRIBUTION);
    private static final AttributionLine SUB_ATTRIBUTION_LINE =
        new AttributionLine(YEAR_TEXT, FontKey.SUB_ATTRIBUTION);

    private static int widthOf(Font font, String text) {
        return MyFontUtils.getFontMetrics(font).stringWidth(text);
    }

    private static int heightOf(Font font) {
        return MyFontUtils.getFontMetrics(font).getHeight();
    }

    @Test
    void testNoSongAndNoOverrideMeasuresMarginsOnly() {
        var pane = new AttributionPane();
        pane.setMarginTop(MARGIN_TOP);
        pane.setMarginBottom(MARGIN_BOTTOM);

        // No song and no override lines: nothing to render, so width is zero and
        // height is just the margins.
        assertThat(pane.getContentWidthPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT)).isZero();
        assertThat(pane.getContentHeightPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .isEqualTo(MARGIN_TOP + MARGIN_BOTTOM);
    }

    @Test
    void testContentWidthIsWidestLine() {
        var pane = new AttributionPane();
        pane.setOverrideLines(List.of(
            new AttributionLine(SHORT_TEXT, FontKey.ATTRIBUTION),
            new AttributionLine(LONG_TEXT, FontKey.ATTRIBUTION)
        ));

        var shortWidth = widthOf(ATTRIBUTION_FONT, SHORT_TEXT);
        var longWidth = widthOf(ATTRIBUTION_FONT, LONG_TEXT);

        // Precondition: the lines really do differ in width, so the max matters.
        assertThat(longWidth).isGreaterThan(shortWidth);
        assertThat(pane.getContentWidthPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT)).isEqualTo(longWidth);
    }

    @Test
    void testContentHeightSumsRoleFontHeightsPlusMargins() {
        var pane = new AttributionPane();
        pane.setOverrideLines(List.of(ATTRIBUTION_LINE, SUB_ATTRIBUTION_LINE));

        var attributionHeight = heightOf(ATTRIBUTION_FONT);
        var subAttributionHeight = heightOf(SUB_ATTRIBUTION_FONT);

        // Precondition: each line must be measured with its own role font;
        // mismatched fonts have different heights for this to detect.
        assertThat(attributionHeight).isNotEqualTo(subAttributionHeight);

        var heightWithoutMargins = pane.getContentHeightPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT);
        var widthWithoutMargins = pane.getContentWidthPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT);
        assertThat(heightWithoutMargins).isEqualTo(attributionHeight + subAttributionHeight);

        pane.setMarginTop(MARGIN_TOP);
        pane.setMarginBottom(MARGIN_BOTTOM);

        // Margins add to height once each, and do not affect width.
        assertThat(pane.getContentHeightPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .isEqualTo(attributionHeight + subAttributionHeight + MARGIN_TOP + MARGIN_BOTTOM);
        assertThat(pane.getContentWidthPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .isEqualTo(widthWithoutMargins);
    }

    @Test
    void testChangingOverrideLinesInvalidatesCachedMeasure() {
        var pane = new AttributionPane();
        pane.setOverrideLines(List.of(new AttributionLine(SHORT_TEXT, FontKey.ATTRIBUTION)));
        var shortWidth = pane.getContentWidthPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT);

        pane.setOverrideLines(List.of(new AttributionLine(LONG_TEXT, FontKey.ATTRIBUTION)));
        var longWidth = pane.getContentWidthPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT);

        // If the cache were not invalidated, the second measure would still
        // report the first (short) width.
        assertThat(longWidth).isGreaterThan(shortWidth);
    }
}
