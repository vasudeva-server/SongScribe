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
import static org.assertj.core.api.Assertions.offset;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.ui.action.Actions;
import songscribe.ui.action.DialogOpenAction;
import songscribe.ui.dialog.SongSettingsDialog;
import songscribe.util.GraphicUtils;

/**
 * Unit tests for {@link TitleComponent}, {@link SubtitleComponent},
 * {@link FootnotesComponent}, and {@link LyricsComponent} (via its concrete
 * subclass {@link UnderLyricsComponent}).
 *
 * <p>Covers:
 * <ul>
 *   <li>7F rows 5-7 — {@link TitleComponent#getPreferredSize()} null/empty guards,
 *       wrapping formula, and number-prefix routing</li>
 *   <li>T7 — {@link SubtitleComponent#getPreferredSize()} empty-collapse, gap formula,
 *       {@link TitleComponent#topGapPx()} == 0, and measured-width sizing — the component
 *       reports the width of its wrapped text, not the song's line width</li>
 *   <li>7F rows 9-10 — {@link FootnotesComponent#getPreferredSize()} null/empty/non-empty,
 *       and {@link FootnotesComponent#calculateRenderX(double)} width-cap invariant</li>
 *   <li>7F rows 14-15 — {@link LyricsComponent#getTextWidth(Graphics2D)} null/empty guards
 *       and {@link LyricsComponent#getPreferredSize()} height-scaling formula</li>
 *   <li>{@link BaseTitleComponent#openEditor()} — with a song set, opens Song Settings at
 *       {@link SongSettingsDialog.Section#TITLE} and answers true; with no song set,
 *       answers false and opens nothing</li>
 * </ul>
 */
class TitleFootnotesLyricsComponentTest extends UnitTest {

    /**
     * A plain font used to initialise {@link ScoreComponent} subclasses before
     * calling {@link javax.swing.JComponent#getFontMetrics(Font)}.
     * <p>
     * {@code JComponent.getFont()} returns null when neither the component nor any
     * ancestor has a font explicitly set; passing null to {@code getFontMetrics}
     * causes a NullPointerException in the JDK.  Setting a font explicitly on each
     * component under test avoids that without adding FlatLaf-specific setup.
     */
    private static final Font TEST_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    /**
     * Staff width for the wrap tests, narrow enough that a long title or subtitle in
     * {@link #TEST_FONT} must wrap while a one-word one still fits.
     * <p>
     * Set explicitly rather than left at {@link Song#FALLBACK_LINE_WIDTH_SS}: at the
     * fallback width the long strings below fit on a single line, so the wrap under
     * test would never occur.
     */
    private static final double WRAP_TEST_LINE_WIDTH_SS = 20.0;

    /**
     * Staff width wide enough that the long wrap-test strings stay on a single line, so a
     * wrapped measurement can be compared against the same text unwrapped.
     */
    private static final double NO_WRAP_LINE_WIDTH_SS = 400.0;

    /** A title long enough to wrap at {@link #WRAP_TEST_LINE_WIDTH_SS}. */
    private static final String WRAP_TEST_TITLE =
        "This Is A Very Long Title That Should Definitely Wrap To Multiple Lines When Laid Out";

    /**
     * A script family (issue #573) whose descenders — e.g. the tail of a "y" —
     * extend below the font's nominal {@link java.awt.FontMetrics#getDescent()}.
     * Not installed on every platform, so tests using it skip via {@code assumeTrue}
     * when unavailable.
     */
    private static final String DESCENDER_OVERSHOOT_FONT_FAMILY = "SignPainter";

    /**
     * Point size large enough for {@link #DESCENDER_OVERSHOOT_FONT_FAMILY}'s
     * descenders to overshoot the nominal descent by whole pixels.
     */
    private static final int DESCENDER_FONT_SIZE = 48;

    // Synthetic ascent/descent and a deliberate overshoot for the font-independent
    // ink-padding tests, which feed hand-built bounds rather than a real font.
    private static final int SAMPLE_ASCENT = 40;
    private static final int SAMPLE_DESCENT = 10;
    private static final int OVERSHOOT_PX = 5;

    private static boolean isFontFamilyAvailable(String family) {
        return Arrays.asList(
            GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        ).contains(family);
    }

    /** Replaces {@code song}'s title, leaving the rest of its metadata alone. */
    private static void setTitle(Song song, String title) {
        song.setMetadata(song.getMetadata().withTitle(title));
    }

    /** A {@link TitleComponent} showing {@code song} in {@code font}. */
    private static TitleComponent titleComponent(Song song, Font font) {
        var component = new TitleComponent();
        component.setFont(font);
        component.setSong(song);

        return component;
    }

    // -------------------------------------------------------------------------
    // TitleComponent — rows 5-7
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TitleComponentPreferredSize {

        /**
         * When {@code song} is null, {@link TitleComponent#getPreferredSize()} returns
         * {@code (0, 0)}.
         */
        @Test
        void testNullSongReturnsDimensionZero() {
            var component = new TitleComponent();
            // song is null by default — no setSong() call.
            assertThat(component.getPreferredSize())
                .as("null song → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * When the song has an empty title and empty number, {@link Song#getNumberedTitle()}
         * returns an empty string and {@link TitleComponent#getPreferredSize()} returns
         * {@code (0, 0)}.
         */
        @Test
        void testEmptyNumberedTitleReturnsDimensionZero() {
            var song = new Song();
            // Clear both number and title so that getNumberedTitle() returns "".
            var current = song.getMetadata();
            song.setMetadata(new SongMetadata(
                "", "",
                current.place(), current.year(), current.month(), current.day(),
                current.composer(), current.lyricist(),
                current.lyricsSource(), current.arrangement(), current.unofficialTranslation(),
                current.subtitle(), "", 0, 0
            ));
            var component = new TitleComponent();
            component.setSong(song);

            assertThat(component.getPreferredSize())
                .as("empty getNumberedTitle() → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * When the song has a non-empty title, {@link TitleComponent#getPreferredSize()}
         * returns the measured width of the wrapped text — positive, and strictly less
         * than the song's line width for a short, unwrapped title — and a positive height
         * equal to {@code lineHeight * wrappedLineCount + marginBottom}.
         * <p>
         * The width is asserted relationally rather than against a recomputed expected
         * value: a test that mirrored the centering/wrapping arithmetic would pass even if
         * that arithmetic were wrong in both places.
         */
        @Test
        void testNonEmptyTitleReturnsMeasuredWidthAndPositiveHeight() {
            var song = new Song();
            setTitle(song, "Ode");
            var component = titleComponent(song, TEST_FONT);

            var size = component.getPreferredSize();
            var lineWidthPx = song.getLineWidthPx();

            assertThat(size.width)
                .as("width must be positive and strictly less than song.getLineWidthPx() for a short title")
                .isPositive()
                .isLessThan(lineWidthPx);
            assertThat(size.height)
                .as("height = lineHeight * lineCount + marginBottom > marginBottom alone")
                .isGreaterThan(component.getMarginBottom());
        }

        /**
         * A title's measured width grows as its (single-line, unwrapped) text grows,
         * confirming the width tracks the actual rendered text rather than a fixed
         * constant such as the song's line width.
         */
        @Test
        void testTitleWidthGrowsWhenTitleTextGrows() {
            var song = new Song();
            setTitle(song, "A");
            var component = titleComponent(song, TEST_FONT);
            var shortWidth = component.getPreferredSize().width;

            setTitle(song, "A Much Longer Title");
            var longWidth = component.getPreferredSize().width;

            assertThat(longWidth)
                .as("a longer unwrapped title must produce a strictly greater measured width")
                .isGreaterThan(shortWidth);
        }

        /**
         * Wrapping is what bounds the measured width: the same title measured at a line
         * width that forces a wrap is far narrower than at one that does not.
         * <p>
         * Deliberately not asserted as {@code width <= lineWidthPx()}. Wrapping is
         * computed from advance width ({@code StringUtils.wrapText}) while the component
         * measures ink, and ink overhangs the advance in many faces, so a wrapped line's
         * ink legitimately exceeds the constraint that wrapped it by a glyph's overhang.
         * Sizing to the advance instead is what clipped the tail of the title (#756).
         */
        @Test
        void testWrappingBoundsTitleWidth() {
            var wrappedWidth = titleWidthAtLineWidth(WRAP_TEST_LINE_WIDTH_SS);
            var unwrappedWidth = titleWidthAtLineWidth(NO_WRAP_LINE_WIDTH_SS);

            assertThat(wrappedWidth)
                .as("wrapping the title must bound its measured width well below the unwrapped width")
                .isLessThan(unwrappedWidth);
        }

        /** The measured width of {@link #WRAP_TEST_TITLE} when the song's line width is {@code lineWidthSs}. */
        private int titleWidthAtLineWidth(double lineWidthSs) {
            var song = new Song();
            song.setLineWidthSs(lineWidthSs);
            setTitle(song, WRAP_TEST_TITLE);

            return titleComponent(song, TEST_FONT).getPreferredSize().width;
        }

        /**
         * A title long enough to wrap across multiple lines produces a strictly greater
         * height than a short one-word title on the same component, because
         * {@code height = lineHeight * wrappedLineCount + marginBottom}.
         */
        @Test
        void testWrappingTitleProducesGreaterHeightThanShortTitle() {
            var song = new Song();
            song.setLineWidthSs(WRAP_TEST_LINE_WIDTH_SS);

            // Short title — expected to fit on one line.
            setTitle(song, "A");
            var component = titleComponent(song, TEST_FONT);
            var shortHeight = component.getPreferredSize().height;

            // Long title — many words that should wrap to two or more lines.
            setTitle(song, WRAP_TEST_TITLE);
            var longHeight = component.getPreferredSize().height;

            assertThat(longHeight)
                .as("a long title that wraps must produce a strictly greater height than a short one")
                .isGreaterThan(shortHeight);
        }

        /**
         * When {@code number} is non-empty but {@code title} is empty,
         * {@link Song#getNumberedTitle()} returns a non-empty string (e.g. "5. "),
         * so {@link TitleComponent#getPreferredSize()} returns a non-zero height.
         * <p>
         * This test would fail if the implementation used {@link Song#getTitle()} instead
         * of {@link Song#getNumberedTitle()}: the empty title would produce {@code (0, 0)}.
         */
        @Test
        void testNumberPrefixIncludedInSizeCalculation() {
            var song = new Song();
            var current = song.getMetadata();
            song.setMetadata(new SongMetadata(
                "", "5",
                current.place(), current.year(), current.month(), current.day(),
                current.composer(), current.lyricist(),
                current.lyricsSource(), current.arrangement(), current.unofficialTranslation(),
                current.subtitle(), "", 0, 0
            ));
            // getNumberedTitle() → "5. " (non-empty); getTitle() → "" (empty).
            var component = new TitleComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);

            var size = component.getPreferredSize();
            assertThat(size.height)
                .as("number prefix in getNumberedTitle() causes non-zero height even when title is empty")
                .isGreaterThan(0);
        }

        /**
         * Issue #573: a script font whose descenders (e.g. the tail of a "y") extend
         * below {@link java.awt.FontMetrics#getDescent()} must not have its ink clipped.
         * {@link BaseTitleComponent#getPreferredSize()} must report a height that covers
         * the title's actual rendered ink, not just the font's nominal descent.
         *
         * <p>Secondary, integration-level smoke test: it needs a real overshooting font
         * and so is skipped where that font is absent (e.g. Linux CI). The font-independent
         * arithmetic is covered unconditionally by {@link InkPadding}.
         */
        @Test
        void testPreferredSizeHeightCoversDescenderOvershoot() {
            assumeTrue(
                isFontFamilyAvailable(DESCENDER_OVERSHOOT_FONT_FAMILY),
                DESCENDER_OVERSHOOT_FONT_FAMILY + " is not installed on this system"
            );

            var title = "Satya";
            var song = new Song();
            setTitle(song, title);
            var font = new Font(DESCENDER_OVERSHOOT_FONT_FAMILY, Font.PLAIN, DESCENDER_FONT_SIZE);
            var component = titleComponent(song, font);

            var metrics = GraphicUtils.fontMetrics(font);
            var inkBounds = GraphicUtils.visualBounds(title, font);

            assertThat(inkBounds).as("ink bounds for '" + title + "' must not be null").isNotNull();

            // Precondition: confirm this font/size actually overshoots the nominal
            // descent here, otherwise the assertion below would hold trivially.
            var inkBottomFromBaseline = inkBounds.getMaxY();
            assumeTrue(
                inkBottomFromBaseline > metrics.getDescent(),
                title + " in " + DESCENDER_OVERSHOOT_FONT_FAMILY + " does not overshoot the descent here"
            );

            // The bottom of the rendered ink, relative to the top of the text block,
            // is ascent (baseline offset) + how far the ink descends below the baseline.
            var inkBottomFromTop = metrics.getAscent() + inkBottomFromBaseline;

            assertThat((double) component.getPreferredSize().height)
                .as("preferred height must cover the descender ink, not just the nominal descent")
                .isGreaterThanOrEqualTo(inkBottomFromTop);
        }

        /**
         * Font-independent coverage of the ink-padding arithmetic
         * ({@link GraphicUtils#extraInkAbove} / {@link GraphicUtils#extraInkBelow}),
         * using hand-built bounds so it runs identically on every platform — unlike the
         * {@code SignPainter}-gated smoke test above.
         */
        @Nested
        class InkPadding {

            @Test
            void testExtraInkAboveNullBoundsIsZero() {
                assertThat(GraphicUtils.extraInkAbove(null, SAMPLE_ASCENT))
                    .as("null line bounds contribute no top padding")
                    .isZero();
            }

            @Test
            void testExtraInkAboveWithinAscentIsZero() {
                // Ink rises exactly to the ascent (inkHeight == -y == ascent): no overshoot.
                var bounds = new Rectangle(0, -SAMPLE_ASCENT, SAMPLE_ASCENT, SAMPLE_ASCENT);

                assertThat(GraphicUtils.extraInkAbove(bounds, SAMPLE_ASCENT))
                    .as("ink within the ascent needs no top padding")
                    .isZero();
            }

            @Test
            void testExtraInkAboveOvershootIsExcess() {
                // Ink rises OVERSHOOT_PX above the ascent (inkHeight == ascent + overshoot).
                var bounds = new Rectangle(0, -(SAMPLE_ASCENT + OVERSHOOT_PX), SAMPLE_ASCENT, SAMPLE_ASCENT);

                assertThat(GraphicUtils.extraInkAbove(bounds, SAMPLE_ASCENT))
                    .as("ink above the ascent is padded by exactly the overshoot")
                    .isEqualTo(OVERSHOOT_PX);
            }

            @Test
            void testExtraInkBelowNullBoundsIsZero() {
                assertThat(GraphicUtils.extraInkBelow(null, SAMPLE_DESCENT))
                    .as("null line bounds contribute no bottom padding")
                    .isZero();
            }

            @Test
            void testExtraInkBelowWithinDescentIsZero() {
                // Ink drops exactly to the descent (y + height == descent): no overshoot.
                var bounds = new Rectangle(0, 0, SAMPLE_DESCENT, SAMPLE_DESCENT);

                assertThat(GraphicUtils.extraInkBelow(bounds, SAMPLE_DESCENT))
                    .as("ink within the descent needs no bottom padding")
                    .isZero();
            }

            @Test
            void testExtraInkBelowOvershootIsExcess() {
                // Ink drops OVERSHOOT_PX below the descent (y + height == descent + overshoot).
                var bounds = new Rectangle(0, 0, SAMPLE_DESCENT, SAMPLE_DESCENT + OVERSHOOT_PX);

                assertThat(GraphicUtils.extraInkBelow(bounds, SAMPLE_DESCENT))
                    .as("ink below the descent is padded by exactly the overshoot")
                    .isEqualTo(OVERSHOOT_PX);
            }
        }
    }

    // -------------------------------------------------------------------------
    // TitleComponent.render — U1
    // -------------------------------------------------------------------------

    /**
     * The claim {@link BaseTitleComponent#openEditor()} rests the double-click gesture on —
     * "this component is exactly as wide as the text it draws" — checked against real paint
     * rather than against the arithmetic that produced the width.
     * <p>
     * {@link BaseTitleComponent#getPreferredSize()} and {@link BaseTitleComponent#render}
     * are two passes over the same text, and every sizing test above watches only the
     * first. These paint the second into an image wider than the component, so ink that
     * lands outside the component's bounds shows up on the canvas instead of being clipped
     * away unseen.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TitleComponentRender {

        /**
         * A logical family — so it is present on every JVM — whose italic glyphs paint
         * well past their advance width, which is what a component sized to the advance
         * used to clip (#756).
         */
        private static final Font OVERHANGING_FONT = new Font(Font.SERIF, Font.ITALIC, 48);

        /**
         * Chosen for the gap between its ink and its advance box in
         * {@link #OVERHANGING_FONT}: the leading "A" leans left of the origin and the
         * trailing "f" paints well past its own advance, so its ink is some 11px wider
         * than {@code stringWidth} reports. Sizing to the advance clips both ends by more
         * than {@link #ANTIALIAS_TOLERANCE_PX} — which is what makes these assertions able
         * to fail. Short enough not to wrap.
         */
        private static final String OVERHANGING_TITLE = "Away With Grief";

        /**
         * Blank canvas on each side of the component, so ink drawn beyond its bounds is
         * recorded rather than clipped by an exactly-sized image and missed.
         */
        private static final int OVERFLOW_MARGIN_PX = 40;

        /**
         * Antialiasing feathers a glyph outline by up to a pixel past its geometric
         * extent, so one edge pixel is not evidence of a sizing bug. The clipping these
         * tests exist to catch takes whole glyph tails, not fractions of a pixel.
         */
        private static final int ANTIALIAS_TOLERANCE_PX = 1;

        /** Alpha channel of an ARGB pixel; non-zero means something was painted there. */
        private static final int ALPHA_MASK = 0xFF00_0000;

        /**
         * The regression #756 was filed for: sizing to the advance width rather than to the
         * ink leaves an italic face's overhanging tail outside the component.
         */
        @Test
        void testRenderKeepsAnOverhangingTitleInsideTheComponentWidth() {
            assertPaintsWithinItsWidth(OVERHANGING_TITLE, NO_WRAP_LINE_WIDTH_SS);
        }

        /**
         * The multi-line case: every wrapped line is centered on the component's own width,
         * so each is its own chance to be placed outside it, and only the widest one is the
         * line that width was taken from.
         */
        @Test
        void testRenderKeepsAWrappedTitleInsideTheComponentWidth() {
            assertPaintsWithinItsWidth(WRAP_TEST_TITLE, WRAP_TEST_LINE_WIDTH_SS);
        }

        /**
         * Paints {@code title} at its own preferred size and asserts every painted pixel
         * falls within the component's width.
         */
        private void assertPaintsWithinItsWidth(String title, double lineWidthSs) {
            var song = new Song();
            song.setLineWidthSs(lineWidthSs);
            setTitle(song, title);
            var component = titleComponent(song, OVERHANGING_FONT);
            var size = component.getPreferredSize();
            component.setSize(size);

            var inkColumns = paintedColumns(component, size);

            assertThat(inkColumns.min()).as("the title must actually paint something").isNotNegative();
            assertThat(inkColumns.min())
                .as("no ink may fall left of the component")
                .isGreaterThanOrEqualTo(OVERFLOW_MARGIN_PX - ANTIALIAS_TOLERANCE_PX);
            assertThat(inkColumns.max())
                .as("no ink may fall right of the component")
                .isLessThan(OVERFLOW_MARGIN_PX + size.width + ANTIALIAS_TOLERANCE_PX);
        }

        /**
         * Paints {@code component} into an image inset by {@link #OVERFLOW_MARGIN_PX} on
         * every side and reports the leftmost and rightmost image columns carrying ink,
         * or {@code (-1, -1)} when nothing was painted.
         */
        private ColumnRange paintedColumns(TitleComponent component, Dimension size) {
            var image = new BufferedImage(
                size.width + 2 * OVERFLOW_MARGIN_PX,
                size.height + 2 * OVERFLOW_MARGIN_PX,
                BufferedImage.TYPE_INT_ARGB
            );
            var g2 = image.createGraphics();

            try {
                g2.translate(OVERFLOW_MARGIN_PX, OVERFLOW_MARGIN_PX);
                component.paintComponent(g2);
            } finally {
                g2.dispose();
            }

            var min = -1;
            var max = -1;

            for (var x = 0; x < image.getWidth(); x++) {
                for (var y = 0; y < image.getHeight(); y++) {
                    if ((image.getRGB(x, y) & ALPHA_MASK) != 0) {
                        min = min < 0 ? x : min;
                        max = x;
                        break;
                    }
                }
            }

            return new ColumnRange(min, max);
        }

        private record ColumnRange(int min, int max) {}
    }

    // -------------------------------------------------------------------------
    // SubtitleComponent and TitleComponent topGapPx — T7
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class SubtitleComponentPreferredSize {

        /**
         * T7: When subtitle text is empty, {@link SubtitleComponent#getPreferredSize()}
         * returns {@code (0, 0)} — no gap is emitted when the subtitle is absent.
         */
        @Test
        void testEmptySubtitleReturnsDimensionZero() {
            var song = new Song();
            // song.getSubtitle() returns "" by default — no setter needed.
            var component = new SubtitleComponent();
            component.setSong(song);

            assertThat(component.getPreferredSize())
                .as("empty subtitle → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * T7: When subtitle text is non-empty, height = textBlock + topGapPx,
         * so height &gt; topGapPx(), which itself is positive. Width is the measured
         * width of the wrapped text — positive, and strictly less than the song's line
         * width for a short, unwrapped subtitle.
         * <p>
         * The width is asserted relationally rather than against a recomputed expected
         * value: a test that mirrored the centering/wrapping arithmetic would pass even
         * if that arithmetic were wrong in both places.
         */
        @Test
        void testNonEmptySubtitleReturnsMeasuredWidthAndHeightAboveTopGap() {
            var song = new Song();
            var current = song.getMetadata();
            song.setMetadata(new SongMetadata(
                current.title(), current.number(), current.place(), current.year(),
                current.month(), current.day(),
                current.composer(), current.lyricist(),
                current.lyricsSource(), current.arrangement(), current.unofficialTranslation(),
                "A Song Subtitle", "", 0, 0
            ));
            var component = new SubtitleComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);

            var size = component.getPreferredSize();
            var topGap = component.topGapPx();

            assertThat(size.width)
                .as("subtitle width must be positive and strictly less than song.getLineWidthPx()")
                .isPositive()
                .isLessThan(song.getLineWidthPx());
            // topGapPx() is positive for SubtitleComponent (derived from FlatLaf property).
            assertThat(topGap)
                .as("subtitle topGapPx() must be > 0 (derived from FlatLaf subtitle gap)")
                .isGreaterThan(0);
            assertThat(size.height)
                .as("subtitle height = textBlock + topGapPx > topGapPx alone")
                .isGreaterThan(topGap);
        }

        /**
         * T7: A subtitle's measured width grows as its (single-line, unwrapped) text
         * grows, confirming the width tracks the actual rendered text rather than a
         * fixed constant such as the song's line width — the subtitle-path equivalent
         * of the title width-growth coverage above.
         */
        @Test
        void testSubtitleWidthGrowsWhenSubtitleTextGrows() {
            var song = new Song();
            var current = song.getMetadata();
            song.setMetadata(new SongMetadata(
                current.title(), current.number(), current.place(), current.year(),
                current.month(), current.day(),
                current.composer(), current.lyricist(),
                current.lyricsSource(), current.arrangement(), current.unofficialTranslation(),
                "A", "", 0, 0
            ));
            var component = new SubtitleComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);
            var shortWidth = component.getPreferredSize().width;

            song.setMetadata(new SongMetadata(
                current.title(), current.number(), current.place(), current.year(),
                current.month(), current.day(),
                current.composer(), current.lyricist(),
                current.lyricsSource(), current.arrangement(), current.unofficialTranslation(),
                "A Much Longer Subtitle", "", 0, 0
            ));
            var longWidth = component.getPreferredSize().width;

            assertThat(longWidth)
                .as("a longer unwrapped subtitle must produce a strictly greater measured width")
                .isGreaterThan(shortWidth);
        }

        /**
         * T7: {@link TitleComponent#topGapPx()} returns 0 — the title owns no leading
         * gap (the subtitle gap lives in {@link SubtitleComponent#topGapPx()}).
         */
        @Test
        void testTitleComponentTopGapIsZero() {
            var component = new TitleComponent();
            assertThat(component.topGapPx())
                .as("TitleComponent.topGapPx() must be 0")
                .isEqualTo(0);
        }

        /**
         * T7: A subtitle long enough to wrap across multiple lines produces a strictly
         * greater height than a short one, exercising the shared
         * {@code lineCount * glyphHeight + (lineCount - 1) * leading} wrap formula on
         * the subtitle path (not just the title path).
         */
        @Test
        void testHeightGrowsWhenSubtitleWraps() {
            var song = new Song();
            song.setLineWidthSs(WRAP_TEST_LINE_WIDTH_SS);
            var current = song.getMetadata();
            song.setMetadata(new SongMetadata(
                current.title(), current.number(), current.place(), current.year(),
                current.month(), current.day(),
                current.composer(), current.lyricist(),
                current.lyricsSource(), current.arrangement(), current.unofficialTranslation(),
                "Short", "", 0, 0
            ));
            var component = new SubtitleComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);
            var shortHeight = component.getPreferredSize().height;

            song.setMetadata(new SongMetadata(
                current.title(), current.number(), current.place(), current.year(),
                current.month(), current.day(),
                current.composer(), current.lyricist(),
                current.lyricsSource(), current.arrangement(), current.unofficialTranslation(),
                "This Is A Very Long Subtitle That Should Definitely Wrap To Multiple Lines When Laid Out", "", 0, 0
            ));
            var longHeight = component.getPreferredSize().height;

            assertThat(longHeight)
                .as("a long subtitle that wraps must produce a strictly greater height than a short one")
                .isGreaterThan(shortHeight);
        }
    }

    // -------------------------------------------------------------------------
    // BaseTitleComponent#openEditor() — TitleComponent & SubtitleComponent
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class BaseTitleComponentOpenEditor {

        private DialogOpenAction<SongSettingsDialog> originalSongSettingsAction;
        private DialogOpenAction<SongSettingsDialog> mockSongSettingsAction;
        private SongSettingsDialog mockSongSettingsDialog;

        @SuppressWarnings("unchecked")
        @BeforeEach
        void setUp() {
            originalSongSettingsAction = Actions.SONG_SETTINGS_ACTION;
            mockSongSettingsDialog = mock(SongSettingsDialog.class);
            mockSongSettingsAction = mock(DialogOpenAction.class);
            when(mockSongSettingsAction.getDialog()).thenReturn(mockSongSettingsDialog);
            Actions.SONG_SETTINGS_ACTION = mockSongSettingsAction;
        }

        @AfterEach
        void tearDown() {
            Actions.SONG_SETTINGS_ACTION = originalSongSettingsAction;
        }

        /**
         * With a song set, {@link TitleComponent#openEditor()} (inherited from
         * {@link BaseTitleComponent}) answers true and opens Song Settings at
         * {@link SongSettingsDialog.Section#TITLE}.
         */
        @Test
        void testTitleComponentOpenEditorWithSongOpensSongSettingsAtTitleSection() {
            var component = new TitleComponent();
            component.setSong(new Song());

            assertThat(component.openEditor())
                .as("openEditor answers true when a song is set")
                .isTrue();
            verify(mockSongSettingsDialog).show(SongSettingsDialog.Section.TITLE);
        }

        /**
         * With no song set, {@link TitleComponent#openEditor()} answers false and never
         * opens the dialog.
         */
        @Test
        void testTitleComponentOpenEditorWithNoSongAnswersFalseAndOpensNothing() {
            var component = new TitleComponent();
            // song is null by default — no setSong() call.

            assertThat(component.openEditor())
                .as("openEditor answers false when no song is set")
                .isFalse();
            verify(mockSongSettingsAction, never()).getDialog();
        }

        /**
         * With a song set, {@link SubtitleComponent#openEditor()} answers true and opens
         * Song Settings at {@link SongSettingsDialog.Section#SUBTITLE} — the same tab the
         * title uses, but landing on the subtitle field, because that is the text that was
         * double-clicked.
         */
        @Test
        void testSubtitleComponentOpenEditorWithSongOpensSongSettingsAtSubtitleSection() {
            var component = new SubtitleComponent();
            component.setSong(new Song());

            assertThat(component.openEditor())
                .as("openEditor answers true when a song is set")
                .isTrue();
            verify(mockSongSettingsDialog).show(SongSettingsDialog.Section.SUBTITLE);
        }

        /**
         * With no song set, {@link SubtitleComponent#openEditor()} answers false and
         * never opens the dialog.
         */
        @Test
        void testSubtitleComponentOpenEditorWithNoSongAnswersFalseAndOpensNothing() {
            var component = new SubtitleComponent();
            // song is null by default — no setSong() call.

            assertThat(component.openEditor())
                .as("openEditor answers false when no song is set")
                .isFalse();
            verify(mockSongSettingsAction, never()).getDialog();
        }
    }

    // -------------------------------------------------------------------------
    // FootnotesComponent — rows 9-10
    // -------------------------------------------------------------------------

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FootnotesComponentPreferredSize {

        /**
         * When {@code song} is null, {@link FootnotesComponent#getPreferredSize()}
         * returns {@code (0, 0)}.
         */
        @Test
        void testNullSongReturnsDimensionZero() {
            var component = new FootnotesComponent();
            assertThat(component.getPreferredSize())
                .as("null song → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * When the song's footnotes are empty, {@link FootnotesComponent#getPreferredSize()}
         * returns {@code (0, 0)}.
         */
        @Test
        void testEmptyFootnotesReturnsDimensionZero() {
            var song = new Song();
            // song.getFootnotes() returns "" by default.
            var component = new FootnotesComponent();
            component.setSong(song);

            assertThat(component.getPreferredSize())
                .as("empty footnotes → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * For a single-line footnote, {@link FootnotesComponent#getPreferredSize()} returns
         * {@code Dimension(lineWidthPx, marginTop + lineHeight)}, where {@code lineHeight}
         * is positive.
         */
        @Test
        void testSingleLineFootnoteReturnsLineWidthAndPositiveHeight() {
            var song = new Song();
            song.setFootnotes("Single footnote line");
            var component = new FootnotesComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);

            var size = component.getPreferredSize();
            assertThat(size.width)
                .as("width equals song.getLineWidthPx()")
                .isEqualTo(song.getLineWidthPx());
            assertThat(size.height)
                .as("height = marginTop + lineHeight, which is > marginTop alone")
                .isGreaterThan(component.getMarginTop());
        }

        /**
         * A two-line footnote (split on {@code '\n'}) produces a height equal to
         * {@code marginTop + lineHeight * 2}, which is strictly greater than the
         * height for a single-line footnote.
         */
        @Test
        void testMultiLineFootnotesHeightScalesWithLineCount() {
            var song = new Song();

            song.setFootnotes("Line one");
            var component = new FootnotesComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);
            var oneLineHeight = component.getPreferredSize().height;

            song.setFootnotes("Line one\nLine two");
            var twoLineHeight = component.getPreferredSize().height;

            assertThat(twoLineHeight)
                .as("two lines should produce strictly greater height than one line")
                .isGreaterThan(oneLineHeight);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class FootnotesComponentWidthCap {

        /**
         * When {@code song} is null, {@link FootnotesComponent#calculateRenderX(double)}
         * returns {@code 0} without throwing.
         */
        @Test
        void testNullSongReturnsZeroX() {
            var component = new FootnotesComponent();
            // song is null by default.
            assertThat((double) component.calculateRenderX(100.0))
                .as("null song → calculateRenderX returns 0")
                .isEqualTo(0.0);
        }

        /**
         * When the footnote text width is narrower than the 2/3 line-width cap, the
         * centering formula {@code x = (lineWidth - textWidth) / 2} produces a
         * non-negative x (trivially, since textWidth < lineWidth).
         */
        @Test
        void testNarrowFootnotesXIsNonNegative() {
            var song = new Song();
            song.setFootnotes("Short");
            var component = new FootnotesComponent();
            component.setSong(song);

            // textWidth = 0 (narrowest possible) → x = lineWidth / 2 > 0
            var x = component.calculateRenderX(0.0);
            assertThat((double) x)
                .as("x for zero-width text must be non-negative")
                .isGreaterThanOrEqualTo(0.0);
        }

        /**
         * When the footnote text is very wide (wider than the line), the width cap
         * {@code actualWidth = min(textWidth, lineWidth * 2/3)} limits it to 2/3 of
         * the line width, so {@code x = lineWidth / 6} which is strictly positive.
         * <p>
         * Without the cap, a textWidth > lineWidth would yield a negative x.
         */
        @Test
        void testVeryWideFootnotesXIsNonNegativeDueToWidthCap() {
            var song = new Song();
            song.setFootnotes("Placeholder");
            var component = new FootnotesComponent();
            component.setSong(song);

            // Simulate a text block wider than the full line width.
            var oversizedTextWidth = song.getLineWidthPx() * 2.0;
            var x = component.calculateRenderX(oversizedTextWidth);

            assertThat((double) x)
                .as("even for over-wide text the cap keeps x non-negative")
                .isGreaterThanOrEqualTo(0.0);

            // Without the cap: x = (lineWidth - oversizedTextWidth) / 2 < 0.
            // With the cap:    x = (lineWidth - lineWidth*2/3) / 2 = lineWidth/6 > 0.
            var expectedX = song.getLineWidthPx() / 6.0;
            assertThat((double) x)
                .as("capped x = lineWidth/6 (within floating-point tolerance)")
                .isCloseTo(expectedX, offset(1.0));
        }
    }

    // -------------------------------------------------------------------------
    // LyricsComponent (via UnderLyricsComponent) — rows 14-15
    // -------------------------------------------------------------------------

    /**
     * Creates an off-screen {@link Graphics2D} backed by a 1×1 pixel image.
     * Used to test {@link LyricsComponent#getTextWidth} without a real display context.
     * Caller is responsible for disposing the returned object.
     */
    private static Graphics2D createOffscreenGraphics() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LyricsComponentGetTextWidth {

        /**
         * When {@code song} is null, {@link LyricsComponent#getTextWidth(Graphics2D)}
         * returns 0 without throwing.
         */
        @Test
        void testNullSongReturnsZero() {
            var component = new UnderLyricsComponent();
            // song is null by default.
            var g2 = createOffscreenGraphics();

            try {
                assertThat(component.getTextWidth(g2))
                    .as("null song → getTextWidth returns 0")
                    .isEqualTo(0.0);
            } finally {
                g2.dispose();
            }
        }

        /**
         * When the song has empty under-lyrics, {@link LyricsComponent#getTextWidth(Graphics2D)}
         * returns 0.
         */
        @Test
        void testEmptyLyricsReturnsZero() {
            var song = new Song();
            // song.getUnderLyrics() returns "" by default.
            var component = new UnderLyricsComponent();
            component.setSong(song);
            var g2 = createOffscreenGraphics();

            try {
                assertThat(component.getTextWidth(g2))
                    .as("empty under-lyrics → getTextWidth returns 0")
                    .isEqualTo(0.0);
            } finally {
                g2.dispose();
            }
        }

        /**
         * When under-lyrics are non-empty, {@link LyricsComponent#getTextWidth(Graphics2D)}
         * returns a positive value — confirming delegation to
         * {@link GraphicUtils#getTextBlockWidth}.
         */
        @Test
        void testNonEmptyLyricsReturnsPositiveWidth() {
            var song = new Song();
            song.setUnderLyrics("La la la");
            var component = new UnderLyricsComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);
            var g2 = createOffscreenGraphics();

            try {
                assertThat(component.getTextWidth(g2))
                    .as("non-empty under-lyrics → getTextWidth returns positive width")
                    .isGreaterThan(0.0);
            } finally {
                g2.dispose();
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class LyricsComponentPreferredSize {

        /**
         * When {@code song} is null, {@link LyricsComponent#getPreferredSize()} returns
         * {@code (0, 0)}.
         */
        @Test
        void testNullSongReturnsDimensionZero() {
            var component = new UnderLyricsComponent();
            assertThat(component.getPreferredSize())
                .as("null song → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * When under-lyrics are empty, {@link LyricsComponent#getPreferredSize()} returns
         * {@code (0, 0)}.
         */
        @Test
        void testEmptyLyricsReturnsDimensionZero() {
            var song = new Song();
            var component = new UnderLyricsComponent();
            component.setSong(song);

            assertThat(component.getPreferredSize())
                .as("empty lyrics → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * For a single-line lyric, {@link LyricsComponent#getPreferredSize()} returns
         * {@code Dimension(lineWidthPx, lineHeight + marginTop)}, where the width is exactly
         * {@code song.getLineWidthPx()} and the height is positive.
         */
        @Test
        void testSingleLineLyricReturnsLineWidthAndPositiveHeight() {
            var song = new Song();
            song.setUnderLyrics("La la la");
            var component = new UnderLyricsComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);

            var size = component.getPreferredSize();
            assertThat(size.width)
                .as("width = song.getLineWidthPx()")
                .isEqualTo(song.getLineWidthPx());
            assertThat(size.height)
                .as("height = lineHeight + marginTop > marginTop alone")
                .isGreaterThan(component.getMarginTop());
        }

        /**
         * Multi-line lyrics (split on {@code '\n'}) scale the height linearly:
         * two lines produce a strictly greater height than one line.
         */
        @Test
        void testMultiLineLyricsHeightScalesWithLineCount() {
            var song = new Song();

            song.setUnderLyrics("Line one");
            var component = new UnderLyricsComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);
            var oneLineHeight = component.getPreferredSize().height;

            song.setUnderLyrics("Line one\nLine two");
            var twoLineHeight = component.getPreferredSize().height;

            assertThat(twoLineHeight)
                .as("two lyric lines produce a strictly greater height than one line")
                .isGreaterThan(oneLineHeight);
        }
    }
}
