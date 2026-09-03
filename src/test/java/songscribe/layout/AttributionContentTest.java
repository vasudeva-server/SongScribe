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

import java.awt.Font;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.AttributionLine;
import songscribe.dom.DocumentScale;
import songscribe.font.FontKey;
import songscribe.font.TextMeasurement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Both cases exist because the block's vertical arithmetic and its centering are stated in staff
 * spaces while every font is sized in document pixels, so each measurement crosses a conversion.
 * A conversion dropped or applied twice leaves a block that still looks like a block.
 */
class AttributionContentTest extends UnitTest {

    /** Point size of the stand-in attribution font, in document pixels. */
    private static final int ATTRIBUTION_FONT_SIZE_PX = 16;

    /**
     * Point size of the stand-in sub-attribution font, in document pixels. Deliberately unlike the
     * attribution size, so the two line boxes cannot stand in for each other.
     */
    private static final int SUB_ATTRIBUTION_FONT_SIZE_PX = 11;

    private static final Font ATTRIBUTION_FONT =
        new Font(Font.SERIF, Font.PLAIN, ATTRIBUTION_FONT_SIZE_PX);

    private static final Font SUB_ATTRIBUTION_FONT =
        new Font(Font.SANS_SERIF, Font.ITALIC, SUB_ATTRIBUTION_FONT_SIZE_PX);

    /** Leading "W" so at least one line carries a negative left bearing. */
    private static final AttributionLine WORDS =
        new AttributionLine("Words: Sri Chinmoy", FontKey.ATTRIBUTION);

    private static final AttributionLine MUSIC =
        new AttributionLine("Music: Sri Chinmoy", FontKey.ATTRIBUTION);

    private static final AttributionLine PLACE =
        new AttributionLine("New York, 5 May 1974", FontKey.SUB_ATTRIBUTION);

    private static final Offset<Double> TOLERANCE = within(1.0e-9);

    private static AttributionContent contentOf(AttributionLine... lines) {
        return AttributionContent.forLines(List.of(lines), ATTRIBUTION_FONT, SUB_ATTRIBUTION_FONT);
    }

    @Test
    void testHeightSumsTheLineBoxesTheLeadingAndTheSubAttributionGap() {
        // A one-line block is one line box and nothing else: no leading before the first line,
        // none after the last, and no transition to a sub-attribution line.
        var attributionBoxSs = contentOf(WORDS).heightSs();
        var subAttributionBoxSs = contentOf(PLACE).heightSs();

        // What a second line of the same role adds beyond its own box is the leading.
        var leadingSs = contentOf(WORDS, MUSIC).heightSs() - 2 * attributionBoxSs;

        // What crossing from an attribution line to a sub-attribution line adds beyond the box
        // and the leading is the transition gap.
        var transitionGapSs = contentOf(WORDS, PLACE).heightSs()
            - attributionBoxSs - leadingSs - subAttributionBoxSs;

        assertThat(contentOf(WORDS, MUSIC, PLACE).heightSs()).isCloseTo(
            2 * attributionBoxSs + 2 * leadingSs + transitionGapSs + subAttributionBoxSs,
            TOLERANCE);
    }

    @Test
    void testEachLineCentersItsInkWithinTheBlockWidth() {
        var sourceLines = List.of(WORDS, MUSIC, PLACE);
        var block = contentOf(WORDS, MUSIC, PLACE);
        var typesetLines = block.lines();

        assertThat(typesetLines).hasSameSizeAs(sourceLines);

        var maxInkWidthSs = 0.0;

        for (var i = 0; i < sourceLines.size(); i++) {
            var sourceLine = sourceLines.get(i);
            var font = sourceLine.font() == FontKey.ATTRIBUTION
                ? ATTRIBUTION_FONT
                : SUB_ATTRIBUTION_FONT;
            var inkPx = TextMeasurement.requireVisualBounds(sourceLine.text(), font);
            var inkWidthSs = DocumentScale.pxToSs(inkPx.getWidth());
            var leftBearingSs = DocumentScale.pxToSs(inkPx.getX());

            maxInkWidthSs = Math.max(maxInkWidthSs, inkWidthSs);

            assertThat(typesetLines.get(i).xSs())
                .describedAs(sourceLine.text())
                .isCloseTo((block.widthSs() - inkWidthSs) / 2.0 - leftBearingSs, TOLERANCE);
        }

        assertThat(block.widthSs()).isCloseTo(maxInkWidthSs, TOLERANCE);
    }
}
