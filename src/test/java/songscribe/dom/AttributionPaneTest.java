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
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link AttributionPane}'s measurement, caching, and line
 * resolution. Lines are injected via {@link AttributionPane#setOverrideLines}
 * so the measurement contract can be exercised without the Song model.
 * <p>
 * Expected heights and widths are both derived by calling
 * {@link GraphicUtils#visualBounds} — the same production helper the pane measures
 * with — and rounded up once with {@link Math#ceil}, mirroring how the pane sizes
 * its content. This pins the reduction/summation logic without hardcoding
 * platform-specific pixels, and without re-implementing the glyph measurement in
 * the test, where a copy could silently drift from production.
 * <p>
 * The zoom tests call the package-private {@link AttributionPane#measure} directly so
 * they can assert on the fractional layout at two zoom factors, rather than inferring
 * it from a mocked {@code Graphics2D}.
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

    // A zoom factor deliberately not equal to 1 and not a power of two, so a term
    // that is scaled the wrong number of times cannot coincidentally match.
    private static final double ZOOM_FACTOR = 1.5;

    // Tolerance for comparing fractional pixel sums, which accumulate binary
    // floating-point error across several terms.
    private static final double EPSILON_PX = 1e-9;

    // Calls the same production helper the pane measures with, rather than
    // re-implementing the glyph-vector call: the fractional rendered ink span, not the
    // glyph advance. Callers pass non-empty text; the guard documents that and
    // satisfies the nullness checker.
    private static double widthOf(Font font, String text) {
        var bounds = GraphicUtils.visualBounds(text, font);

        assertThat(bounds).as("test text must be non-empty: " + text).isNotNull();

        return bounds.getWidth();
    }

    // Leading and the sub-attribution gap at natural (unzoomed) scale: fixed
    // staff-space distances converted to fractional pixels, exactly as the pane does.
    private static final double LEADING_PX = ScaleContext.ssToPx(AttributionPane.LEADING_SS);
    private static final double SUB_ATTRIBUTION_GAP_PX = ScaleContext.ssToPx(AttributionPane.SUB_ATTRIBUTION_GAP_SS);

    // A line's height is the fractional ink height of AttributionPane.LINE_BOX_REFERENCE
    // in its font, via the same production helper; leading is added separately, only
    // between consecutive lines.
    private static double heightOf(Font font) {
        var bounds = GraphicUtils.visualBounds(AttributionPane.LINE_BOX_REFERENCE, font);

        assertThat(bounds).as("line-box reference must be non-empty").isNotNull();

        return bounds.getHeight();
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
        assertThat(pane.getContentWidthPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .isEqualTo((int) Math.ceil(longWidth));
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
        var expectedNoMargin = (int) Math.ceil(
            attributionHeight + subAttributionHeight + LEADING_PX + SUB_ATTRIBUTION_GAP_PX);
        assertThat(heightWithoutMargins)
            .describedAs("height must be each line's ink box plus one leading gap plus the sub-attribution gap")
            .isEqualTo(expectedNoMargin);

        pane.setMarginTop(MARGIN_TOP);
        pane.setMarginBottom(MARGIN_BOTTOM);

        // Margins add to height once each, and do not affect width.
        assertThat(pane.getContentHeightPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .describedAs("each margin must add to the height exactly once")
            .isEqualTo(expectedNoMargin + MARGIN_TOP + MARGIN_BOTTOM);
        assertThat(pane.getContentWidthPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .describedAs("margins must not affect content width")
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
        var expectedHeight = (int) Math.ceil(attributionHeight + 2 * subAttributionHeight
            + 2 * LEADING_PX + SUB_ATTRIBUTION_GAP_PX);
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
            .isEqualTo((int) Math.ceil(2 * subAttributionHeight + LEADING_PX));
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
        var expectedHeight = (int) Math.ceil(lineCount * attributionHeight + gapCount * LEADING_PX);
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
            .isEqualTo((int) Math.ceil(nonEmptyWidth));

        // ...but it is still boxed to the full line height and separated by leading.
        assertThat(pane.getContentHeightPx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .describedAs("empty-text line must still occupy a full line box")
            .isEqualTo((int) Math.ceil(2 * attributionHeight + LEADING_PX));
    }

    @Test
    void testLeadingAndGapScaleWithZoomFactor() {
        var pane = new AttributionPane();
        pane.setOverrideLines(List.of(ATTRIBUTION_LINE, SUB_ATTRIBUTION_LINE));

        var natural = pane.measure(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT, AttributionPane.NATURAL_ZOOM_FACTOR);
        var zoomed = pane.measure(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT, ZOOM_FACTOR);

        // The same (unzoomed) fonts are passed both times, so the two line boxes
        // contribute identically and the leading plus the sub-attribution gap are the
        // only terms the zoom factor may scale. Dropping either multiplication, or
        // applying it to the font-derived line box as well, breaks this difference.
        assertThat(zoomed.contentHeightPx() - natural.contentHeightPx())
            .describedAs("leading and the sub-attribution gap must scale linearly with the zoom factor")
            .isCloseTo((ZOOM_FACTOR - 1) * (LEADING_PX + SUB_ATTRIBUTION_GAP_PX), within(EPSILON_PX));
    }

    @Test
    void testMarginsScaleWithZoomFactor() {
        var pane = new AttributionPane();
        pane.setOverrideLines(List.of(ATTRIBUTION_LINE));
        pane.setMarginTop(MARGIN_TOP);
        pane.setMarginBottom(MARGIN_BOTTOM);

        var natural = pane.measure(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT, AttributionPane.NATURAL_ZOOM_FACTOR);
        var zoomed = pane.measure(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT, ZOOM_FACTOR);

        // A single line has no leading and no sub-attribution gap, so the margins are
        // the only zoom-scaled terms left in the total height.
        assertThat(zoomed.contentHeightPx() - natural.contentHeightPx())
            .describedAs("margins must scale with the zoom factor, like the leading does")
            .isCloseTo((ZOOM_FACTOR - 1) * (MARGIN_TOP + MARGIN_BOTTOM), within(EPSILON_PX));
    }

    @Test
    void testContentSizeStaysNaturalAfterZoomedMeasure() {
        var pane = new AttributionPane();
        pane.setOverrideLines(List.of(ATTRIBUTION_LINE, SUB_ATTRIBUTION_LINE));

        var naturalSize = pane.getContentSizePx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT);

        // Measuring at a different zoom must not disturb the natural-scale answer:
        // the layout pass reads this size while paints happen at the view zoom, so a
        // single shared cache slot would hand back the zoomed measurement here.
        pane.measure(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT, ZOOM_FACTOR);

        assertThat(pane.getContentSizePx(ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT))
            .describedAs("content size must stay zoom-invariant after a measure at another zoom")
            .isEqualTo(naturalSize);
    }
}
