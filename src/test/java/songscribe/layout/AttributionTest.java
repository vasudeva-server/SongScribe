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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import songscribe.UnitTest;
import songscribe.dom.Attribution;
import songscribe.dom.AttributionFormatter;
import songscribe.dom.AttributionLine;
import songscribe.dom.AttributionPane;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.font.DocumentFonts;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;
import songscribe.layout.LayoutResult;
import songscribe.layout.stacking.VerticalStackingCalculator;
import songscribe.util.MyFontUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

class AttributionTest extends UnitTest {

    private static final double EPSILON = 1e-10;

    @Test
    void testCtorSetsAttributionMarginBottomSs() {
        var attribution = new Attribution();

        assertThat(attribution.getMarginBottomSs())
            .isCloseTo(Attribution.ATTRIBUTION_MARGIN_BOTTOM_SS, within(EPSILON));
    }

    @Test
    void testGetContentPxConvertsFromSs() {
        var attribution = new Attribution();
        var widthSs = 10.0;
        var heightSs = 4.0;

        attribution.setDimensionsSs(widthSs, heightSs);

        assertThat(attribution.getContentWidthPx())
            .isCloseTo(ScaleContext.ssToPx(widthSs), within(EPSILON));
        assertThat(attribution.getContentHeightPx())
            .isCloseTo(ScaleContext.ssToPx(heightSs), within(EPSILON));
    }

    @Test
    void testGetUserXOffsetSsAlwaysReturnsZero() {
        var attribution = new Attribution();

        assertThat(attribution.getUserXOffsetSs()).isZero();
    }

    @Test
    void testUserYOffsetSsRoundTrips() {
        var attribution = new Attribution();
        var offsetSs = -2.5;

        attribution.setUserYOffsetSs(offsetSs);

        assertThat(attribution.getUserYOffsetSs()).isCloseTo(offsetSs, within(EPSILON));
    }

    @Test
    void testStackAttributionSkipsWhenDimensionsAreZero() {
        // A freshly constructed Attribution has zero width and height.
        // stackAttribution guards against this with `if (widthSs <= 0 || heightSs <= 0) return`,
        // so the attribution must not receive a DecorationLayout in the builder.
        var attribution = new Attribution();
        var builder = LayoutResult.builder();

        new VerticalStackingCalculator().calculate(
            List.of(),
            detachedLine(),
            builder,
            100.0,
            mock(DocumentFontsHolder.class),
            attribution);

        assertThat(builder.getDecorationLayout(attribution))
            .describedAs("attribution with zero dimensions must not be stacked")
            .isNull();
    }

    // -----------------------------------------------------------------------
    // Pane-driven geometry: dimensions set from AttributionPane measurement
    // -----------------------------------------------------------------------

    @Nested
    class PaneDrivenGeometry {

        /**
         * The Attribution's content width in staff-spaces must match the value
         * derived by measuring the pane and converting via ScaleContext.
         * This guards the geometry pipeline: measure → Ss conversion → Attribution.
         */
        @Test
        void testContentWidthSsMatchesPaneMeasurement() {
            var fonts = DocumentFonts.defaultsFromPrefs();
            var aFont = fonts.getFont(FontKey.ATTRIBUTION);
            var saFont = fonts.getFont(FontKey.SUB_ATTRIBUTION);

            var song = new Song();
            var pane = song.getAttributionPane();
            pane.setSong(song);

            // Independently derive the expected natural width from the formatter
            // lines so the assertion anchors to a computed value rather than
            // round-tripping the value we store on the Attribution.
            var lines = AttributionFormatter.lines(song.getMetadata(), song.showTranslation());
            var expectedWidthPx = 0;

            for (var line : lines) {
                var font = line.font() == FontKey.ATTRIBUTION ? aFont : saFont;
                expectedWidthPx = Math.max(
                    expectedWidthPx,
                    MyFontUtils.getFontMetrics(font).stringWidth(line.text()));
            }

            assertThat(expectedWidthPx)
                .describedAs("default song must produce at least one non-empty attribution line")
                .isPositive();

            var widthPx = pane.getContentWidthPx(aFont, saFont);
            assertThat(widthPx)
                .describedAs("pane width must equal the natural max width of the formatter lines")
                .isEqualTo(expectedWidthPx);

            var attribution = new Attribution();
            attribution.setDimensionsSs(
                ScaleContext.pxToSs(widthPx),
                ScaleContext.pxToSs(pane.getContentHeightPx(aFont, saFont)));

            assertThat(attribution.getContentWidthSs())
                .describedAs("attribution width in Ss must match the px→Ss conversion of the independent width")
                .isCloseTo(ScaleContext.pxToSs(expectedWidthPx), within(EPSILON));
        }

        /**
         * Override lines drive the pane measurement — the pane must use the
         * injected lines rather than the song model.
         */
        @Test
        void testOverrideLinesAreUsedForMeasurement() {
            var fonts = DocumentFonts.defaultsFromPrefs();
            var aFont = fonts.getFont(FontKey.ATTRIBUTION);
            var saFont = fonts.getFont(FontKey.SUB_ATTRIBUTION);

            var overrideText = "Words and Music by Sri Chinmoy";
            var overrideLines = List.of(new AttributionLine(overrideText, FontKey.ATTRIBUTION));

            var pane = new AttributionPane();
            pane.setOverrideLines(overrideLines);

            var widthPx = pane.getContentWidthPx(aFont, saFont);

            // The content width must be the string width of the override line's text.
            var expectedWidthPx = MyFontUtils.getFontMetrics(aFont).stringWidth(overrideText);
            assertThat(widthPx)
                .describedAs("pane content width must equal string width of the override line text")
                .isEqualTo(expectedWidthPx);
        }

        /**
         * Pane-driven geometry integrates with the stacker: dimensions derived
         * from AttributionPane measurement produce a valid DecorationLayout.
         */
        @Test
        void testPaneDerivedDimensionsProduceDecorationLayout() {
            var fonts = DocumentFonts.defaultsFromPrefs();
            var aFont = fonts.getFont(FontKey.ATTRIBUTION);
            var saFont = fonts.getFont(FontKey.SUB_ATTRIBUTION);

            var song = new Song();
            var pane = song.getAttributionPane();
            pane.setSong(song);

            var widthPx = pane.getContentWidthPx(aFont, saFont);
            var heightPx = pane.getContentHeightPx(aFont, saFont);

            var attribution = new Attribution();
            attribution.setDimensionsSs(ScaleContext.pxToSs(widthPx), ScaleContext.pxToSs(heightPx));

            var staffRightSs = 120.0;
            var builder = LayoutResult.builder();

            new VerticalStackingCalculator().calculate(
                List.of(),
                detachedLine(),
                builder,
                staffRightSs,
                mock(DocumentFontsHolder.class),
                attribution);

            assertThat(builder.getDecorationLayout(attribution))
                .describedAs("pane-derived dimensions must produce a DecorationLayout in the stacker")
                .isNotNull()
                .satisfies(layout -> {
                    assertThat(layout.widthSs())
                        .describedAs("layout width must carry the pane-derived width")
                        .isCloseTo(ScaleContext.pxToSs(widthPx), within(EPSILON));
                    assertThat(layout.heightSs())
                        .describedAs("layout height must carry the pane-derived height")
                        .isCloseTo(ScaleContext.pxToSs(heightPx), within(EPSILON));
                });
        }
    }

    // -----------------------------------------------------------------------
    // Natural-width: content width is text-driven, not staff-forced
    // -----------------------------------------------------------------------

    @Nested
    class NaturalWidth {

        /**
         * The pane's content width must equal the natural max line width: the
         * widest string measured at the given font, with no staff-width forcing.
         * A regression that forces the width to the staff width would cause this
         * assertion to fail whenever the text is narrower than the staff.
         */
        @Test
        void testContentWidthIsNaturalMaxLineWidth() {
            var fonts = DocumentFonts.defaultsFromPrefs();
            var aFont = fonts.getFont(FontKey.ATTRIBUTION);
            var saFont = fonts.getFont(FontKey.SUB_ATTRIBUTION);

            // Two lines: first is the wide ATTRIBUTION line; second is the narrow SUB_ATTRIBUTION
            var wideText = "Words and Music by Sri Chinmoy";
            var narrowText = "1984";

            var overrideLines = List.of(
                new AttributionLine(wideText, FontKey.ATTRIBUTION),
                new AttributionLine(narrowText, FontKey.SUB_ATTRIBUTION)
            );

            var pane = new AttributionPane();
            pane.setOverrideLines(overrideLines);

            var paneMeasuredWidth = pane.getContentWidthPx(aFont, saFont);

            // The expected natural width is the max of the two string widths.
            var aMetrics = MyFontUtils.getFontMetrics(aFont);
            var saMetrics = MyFontUtils.getFontMetrics(saFont);
            var expectedNaturalWidth = Math.max(
                aMetrics.stringWidth(wideText),
                saMetrics.stringWidth(narrowText)
            );

            assertThat(paneMeasuredWidth)
                .describedAs("content width must equal natural max line width, not a staff-forced value")
                .isEqualTo(expectedNaturalWidth);
        }

        /**
         * When the pane's content width is smaller than a wide staff, the
         * Attribution Ss width derived from it is also smaller than the staff
         * width. This confirms the absence of staff-width forcing.
         */
        @Test
        void testContentWidthSsIsBelowWideStaffWidth() {
            var fonts = DocumentFonts.defaultsFromPrefs();
            var aFont = fonts.getFont(FontKey.ATTRIBUTION);
            var saFont = fonts.getFont(FontKey.SUB_ATTRIBUTION);

            // Short single line — cannot fill a 120ss staff.
            var lineText = "Sri Chinmoy";
            var overrideLines = List.of(
                new AttributionLine(lineText, FontKey.ATTRIBUTION)
            );

            var pane = new AttributionPane();
            pane.setOverrideLines(overrideLines);

            var widthSs = ScaleContext.pxToSs(pane.getContentWidthPx(aFont, saFont));

            // The width must equal the exact natural width of the single line...
            var expectedWidthSs = ScaleContext.pxToSs(
                MyFontUtils.getFontMetrics(aFont).stringWidth(lineText));
            assertThat(widthSs)
                .describedAs("content width must equal the exact natural line width in Ss")
                .isCloseTo(expectedWidthSs, within(EPSILON));

            // ...and a short one-word line must be narrower than a realistic
            // staff, confirming there is no staff-width forcing.
            var wideStaffSs = 120.0;
            assertThat(widthSs)
                .describedAs("short attribution line must be narrower than the staff width")
                .isLessThan(wideStaffSs);
        }
    }
}
