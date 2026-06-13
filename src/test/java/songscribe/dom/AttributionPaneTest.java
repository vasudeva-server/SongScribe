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
import songscribe.util.GraphicUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AttributionPane}'s measurement, caching, and line
 * resolution. Lines are injected via {@link AttributionPane#setOverrideLines}
 * so the measurement contract can be exercised without the Song model.
 * <p>
 * Expected heights and widths are both derived from {@code getPixelBounds} under
 * {@link GraphicUtils#SCREEN_FRC} — the same source the production code measures
 * with — so the assertions pin the reduction/summation logic without hardcoding
 * platform-specific pixels.
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
    private static final String SECOND_YEAR_TEXT = "1980";

    private static final AttributionLine ATTRIBUTION_LINE =
        new AttributionLine(WORDS_AND_MUSIC_BY, FontKey.ATTRIBUTION);
    private static final AttributionLine SUB_ATTRIBUTION_LINE =
        new AttributionLine(YEAR_TEXT, FontKey.SUB_ATTRIBUTION);
    private static final AttributionLine SECOND_SUB_ATTRIBUTION_LINE =
        new AttributionLine(SECOND_YEAR_TEXT, FontKey.SUB_ATTRIBUTION);

    // Mirrors AttributionPane's ink-based width measurement: the rendered ink span
    // under GraphicUtils.SCREEN_FRC, not the glyph advance. Callers pass non-empty
    // text; the guard documents that and satisfies the nullness checker.
    private static int widthOf(Font font, String text) {
        var bounds = GraphicUtils.inkBounds(text, font);

        if (bounds == null) {
            throw new AssertionError("test text must be non-empty: " + text);
        }

        return bounds.width;
    }

    // Mirrors AttributionPane's leading: a fixed gap, in staff spaces, between the
    // descender of one line and the ascender of the next. Lines are boxed to the
    // rendered height of AttributionPane.LINE_BOX_REFERENCE, separated only by this gap.
    private static final double LEADING_SS = 0.5;
    private static final int LEADING_PX = ScaleContext.ssToRoundedPx(LEADING_SS);

    // Mirrors AttributionPane.SUB_ATTRIBUTION_GAP_SS: an extra gap, in staff
    // spaces, added above the first sub-attribution line — at the transition from
    // attribution to sub-attribution lines — on top of the normal leading.
    private static final double SUB_ATTRIBUTION_GAP_SS = 0.5;
    private static final int SUB_ATTRIBUTION_GAP_PX = ScaleContext.ssToRoundedPx(SUB_ATTRIBUTION_GAP_SS);

    // A line's height is the rendered ink height of AttributionPane.LINE_BOX_REFERENCE
    // in its font; leading is added separately, only between consecutive lines.
    private static int heightOf(Font font) {
        var bounds = GraphicUtils.inkBounds(AttributionPane.LINE_BOX_REFERENCE, font);

        if (bounds == null) {
            throw new AssertionError("line-box reference must be non-empty");
        }

        return bounds.height;
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

        // Two lines: their natural heights plus a single leading gap between them,
        // plus the extra sub-attribution gap above the sub-attribution line.
        assertThat(heightWithoutMargins)
            .isEqualTo(attributionHeight + subAttributionHeight + LEADING_PX + SUB_ATTRIBUTION_GAP_PX);

        pane.setMarginTop(MARGIN_TOP);
        pane.setMarginBottom(MARGIN_BOTTOM);

        // Margins add to height once each, and do not affect width.
        assertThat(pane.getContentHeightPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .isEqualTo(attributionHeight + subAttributionHeight + LEADING_PX + SUB_ATTRIBUTION_GAP_PX
                + MARGIN_TOP + MARGIN_BOTTOM);
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
        assertThat(longWidth)
            .describedAs("changing override lines must invalidate the cache and re-measure")
            .isGreaterThan(shortWidth);
    }

    @Test
    void testSubAttributionGapAddedOnceForMultipleSubLines() {
        var pane = new AttributionPane();
        pane.setOverrideLines(List.of(
            ATTRIBUTION_LINE, SUB_ATTRIBUTION_LINE, SECOND_SUB_ATTRIBUTION_LINE));

        var attributionHeight = heightOf(ATTRIBUTION_FONT);
        var subAttributionHeight = heightOf(SUB_ATTRIBUTION_FONT);

        // One attribution line then two sub-attribution lines: three line boxes,
        // two (n-1) leading gaps, and the sub-attribution gap exactly once — above
        // the first sub line, not before the second.
        var expectedHeight = attributionHeight + 2 * subAttributionHeight
            + 2 * LEADING_PX + SUB_ATTRIBUTION_GAP_PX;
        assertThat(pane.getContentHeightPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .describedAs("sub-attribution gap must be added once, not before every sub line")
            .isEqualTo(expectedHeight);
    }

    @Test
    void testNoSubAttributionGapWhenNoAttributionPrecedes() {
        var pane = new AttributionPane();
        pane.setOverrideLines(List.of(SUB_ATTRIBUTION_LINE, SECOND_SUB_ATTRIBUTION_LINE));

        var subAttributionHeight = heightOf(SUB_ATTRIBUTION_FONT);

        // Both lines are sub-attribution with no attribution line above, so there is
        // no attribution→sub transition: only the inter-line leading applies and the
        // extra gap is not added.
        assertThat(pane.getContentHeightPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .describedAs("gap must not be added when no attribution line precedes the sub lines")
            .isEqualTo(2 * subAttributionHeight + LEADING_PX);
    }

    @Test
    void testLeadingAppliedBetweenEachConsecutiveLine() {
        var pane = new AttributionPane();
        pane.setOverrideLines(List.of(
            new AttributionLine(SHORT_TEXT, FontKey.ATTRIBUTION),
            new AttributionLine(LONG_TEXT, FontKey.ATTRIBUTION),
            new AttributionLine(WORDS_AND_MUSIC_BY, FontKey.ATTRIBUTION)
        ));

        var attributionHeight = heightOf(ATTRIBUTION_FONT);

        // Three attribution lines: three line boxes and exactly n-1 leading gaps, and
        // no sub-attribution gap since every line shares the attribution role. A
        // fence-post error producing n gaps would change this total.
        var lineCount = 3;
        var gapCount = lineCount - 1;
        var expectedHeight = lineCount * attributionHeight + gapCount * LEADING_PX;
        assertThat(pane.getContentHeightPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .describedAs("leading must apply between each consecutive pair, i.e. n-1 times")
            .isEqualTo(expectedHeight);
    }

    @Test
    void testEmptyTextLineContributesNoWidthButOccupiesHeight() {
        var pane = new AttributionPane();
        pane.setOverrideLines(List.of(
            new AttributionLine(WORDS_AND_MUSIC_BY, FontKey.ATTRIBUTION),
            new AttributionLine("", FontKey.ATTRIBUTION)
        ));

        var attributionHeight = heightOf(ATTRIBUTION_FONT);
        var nonEmptyWidth = widthOf(ATTRIBUTION_FONT, WORDS_AND_MUSIC_BY);

        // The empty line has no ink, so it adds nothing to the content width...
        assertThat(pane.getContentWidthPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .describedAs("empty-text line must not contribute to content width")
            .isEqualTo(nonEmptyWidth);

        // ...but it is still boxed to the full line height and separated by leading.
        assertThat(pane.getContentHeightPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .describedAs("empty-text line must still occupy a full line box")
            .isEqualTo(2 * attributionHeight + LEADING_PX);
    }
}
