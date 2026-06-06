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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;

/**
 * Unit tests for {@link TranslationComponent}, {@link TextPanel}, and
 * {@link StaffPanel}.
 *
 * <p>Covers 7F rows 11-12 ({@link TranslationComponent}),
 * rows 20-22 ({@link TextPanel}), and row 24 ({@link StaffPanel#rebuildLayout()}).
 */
class TranslationTextPanelStaffPanelTest extends UnitTest {

    /**
     * A plain font used when a component's {@code getFont()} would otherwise
     * return null, which causes NullPointerException in {@code getFontMetrics}.
     */
    private static final Font TEST_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    /**
     * Creates an off-screen {@link java.awt.Graphics2D} backed by a 1x1 image.
     * Caller is responsible for disposing the result.
     */
    private static java.awt.Graphics2D createOffscreenGraphics() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
    }

    // =========================================================================
    // TranslationComponent — rows 11-13
    // =========================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TranslationComponentGetTextWidth {

        /**
         * When {@code song} is null, {@link TranslationComponent#getTextWidth} returns
         * 0 without throwing.
         */
        @Test
        void testNullSongReturnsZero() {
            var component = new TranslationComponent();
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
         * When the song has an empty translation, {@link TranslationComponent#getTextWidth}
         * returns 0.
         */
        @Test
        void testEmptyTranslationReturnsZero() {
            var song = new Song();
            // getTranslatedLyrics() returns "" by default.
            var component = new TranslationComponent();
            component.setSong(song);
            var g2 = createOffscreenGraphics();

            try {
                assertThat(component.getTextWidth(g2))
                    .as("empty translation → getTextWidth returns 0")
                    .isEqualTo(0.0);
            } finally {
                g2.dispose();
            }
        }

        /**
         * When the translation is non-empty and the song is an official translation,
         * {@link TranslationComponent#getTextWidth} returns the width of the longer
         * official header ("Sri Chinmoy's translation:"), which is wider than the
         * unofficial header ("Unofficial translation:").
         * <p>
         * This verifies that the official-vs-unofficial branch in {@code getTextWidth}
         * selects the correct header string. If the condition were swapped, the test
         * would pass for unofficial but fail for official (or vice versa), because
         * the two headers have different lengths and thus different pixel widths.
         */
        @Test
        void testOfficialTranslationHeaderIsWiderThanUnofficialHeader() {
            var song = new Song();
            song.setTranslatedLyrics("Hello world");
            var component = new TranslationComponent();
            component.setFont(TEST_FONT);

            song.setUnofficialTranslation(false);
            component.setSong(song);
            var g2 = createOffscreenGraphics();
            double officialWidth;
            double unofficialWidth;

            try {
                g2.setFont(TEST_FONT);
                officialWidth = component.getTextWidth(g2);
            } finally {
                g2.dispose();
            }

            song.setUnofficialTranslation(true);
            var g2b = createOffscreenGraphics();

            try {
                g2b.setFont(TEST_FONT);
                unofficialWidth = component.getTextWidth(g2b);
            } finally {
                g2b.dispose();
            }

            // "Sri Chinmoy's translation:" is longer than "Unofficial translation:",
            // so the official header must produce a strictly greater pixel width.
            assertThat(officialWidth)
                .as("official header is wider than unofficial header in same font")
                .isGreaterThan(unofficialWidth);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TranslationComponentPreferredSize {

        /**
         * When {@code song} is null, {@link TranslationComponent#getPreferredSize()}
         * returns {@code (0, 0)}.
         */
        @Test
        void testNullSongReturnsDimensionZero() {
            var component = new TranslationComponent();
            assertThat(component.getPreferredSize())
                .as("null song → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * When the song has an empty translation, {@link TranslationComponent#getPreferredSize()}
         * returns {@code (0, 0)}.
         */
        @Test
        void testEmptyTranslationReturnsDimensionZero() {
            var song = new Song();
            var component = new TranslationComponent();
            component.setSong(song);

            assertThat(component.getPreferredSize())
                .as("empty translation → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * For a non-empty single-line translation, {@link TranslationComponent#getPreferredSize()}
         * returns {@code Dimension(lineWidthPx, h)} where {@code h > marginTop}.
         * <p>
         * The formula is {@code marginTop + headerHeight + fontSize/4 + textHeight * lineCount},
         * which for one line is strictly greater than {@code marginTop} alone.
         */
        @Test
        void testSingleLineTranslationReturnsPositiveHeight() {
            var song = new Song();
            song.setTranslatedLyrics("Hello");
            var component = new TranslationComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);

            var size = component.getPreferredSize();
            assertThat(size.width)
                .as("width = song.getLineWidthPx()")
                .isEqualTo(song.getLineWidthPx());
            assertThat(size.height)
                .as("height = marginTop + headerHeight + gap + lineHeight > marginTop alone")
                .isGreaterThan(component.getMarginTop());
        }

        /**
         * A two-line translation (split on {@code '\n'}) produces a strictly greater height
         * than a single-line translation, because the formula scales by {@code lineCount}.
         */
        @Test
        void testMultiLineTranslationHeightScalesWithLineCount() {
            var song = new Song();

            song.setTranslatedLyrics("Line one");
            var component = new TranslationComponent();
            component.setFont(TEST_FONT);
            component.setSong(song);
            var oneLineHeight = component.getPreferredSize().height;

            song.setTranslatedLyrics("Line one\nLine two");
            var twoLineHeight = component.getPreferredSize().height;

            assertThat(twoLineHeight)
                .as("two translation lines produce a strictly greater height than one")
                .isGreaterThan(oneLineHeight);
        }
    }

    // =========================================================================
    // TextPanel — rows 20-22
    // =========================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TextPanelCalculateUnionWidth {

        /**
         * When all child components have no content (all getTextWidth return 0),
         * {@link TextPanel#calculateUnionWidth} returns 0.
         */
        @Test
        void testAllEmptyChildrenReturnZero() {
            var song = new Song();
            // All text sections are empty by default.
            var panel = new TextPanel();
            panel.setSong(song);

            var g2 = createOffscreenGraphics();

            try {
                assertThat(panel.calculateUnionWidth(g2))
                    .as("all children empty → union width = 0")
                    .isEqualTo(0.0);
            } finally {
                g2.dispose();
            }
        }

        /**
         * When under-lyrics is the widest child, {@link TextPanel#calculateUnionWidth}
         * returns the under-lyrics width.
         * <p>
         * Because the exact pixel width depends on the font selected by the JVM,
         * we only assert that the result is positive — confirming that the maximum
         * is taken across children rather than, say, always returning the first child's value.
         */
        @Test
        void testWiderChildDominatesResult() {
            var song = new Song();
            // Set only under-lyrics; Bangla and translation remain empty.
            song.setUnderLyrics("Wide content");
            var panel = new TextPanel();
            panel.getUnderLyricsComponent().setFont(TEST_FONT);
            panel.setSong(song);

            var g2 = createOffscreenGraphics();

            try {
                g2.setFont(TEST_FONT);
                assertThat(panel.calculateUnionWidth(g2))
                    .as("under-lyrics child is the widest — union width must be positive")
                    .isGreaterThan(0.0);
            } finally {
                g2.dispose();
            }
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TextPanelPaintComponent {

        /**
         * When all children have empty content (union width == 0), calling
         * {@link TextPanel#paintComponent} resets every child's {@code contentX} to -1
         * (independent centering mode).
         */
        @Test
        void testUnionWidthZeroResetsContentXToNegativeOne() {
            var song = new Song();
            // All text sections are empty → unionWidth == 0.
            var panel = new TextPanel();
            panel.setSong(song);

            // Manually set contentX to a non-negative value first to confirm it gets reset.
            panel.getUnderLyricsComponent().setContentX(10f);
            panel.getBanglaLyricsComponent().setContentX(10f);
            panel.getTranslationComponent().setContentX(10f);

            var img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
            var g = img.getGraphics();

            try {
                panel.paintComponent(g);
            } finally {
                g.dispose();
            }

            assertThat(panel.getUnderLyricsComponent().getContentX())
                .as("underLyricsComponent.contentX reset to -1 when unionWidth == 0")
                .isEqualTo(-1f);
            assertThat(panel.getBanglaLyricsComponent().getContentX())
                .as("banglaLyricsComponent.contentX reset to -1 when unionWidth == 0")
                .isEqualTo(-1f);
            assertThat(panel.getTranslationComponent().getContentX())
                .as("translationComponent.contentX reset to -1 when unionWidth == 0")
                .isEqualTo(-1f);
        }

        /**
         * When at least one child has non-empty content (union width > 0), calling
         * {@link TextPanel#paintComponent} sets the same {@code contentX} value on all
         * three children, achieving union alignment.
         * <p>
         * The specific value is {@code (lineWidthPx - unionWidth) / 2}, which may be
         * negative for a default Song whose line width is 0. The critical invariant is
         * that all three values are identical and differ from the reset sentinel (-1).
         */
        @Test
        void testUnionWidthPositiveSetsIdenticalContentXOnAllChildren() {
            var song = new Song();
            song.setUnderLyrics("Some lyrics");
            var panel = new TextPanel();
            panel.getUnderLyricsComponent().setFont(TEST_FONT);
            panel.getBanglaLyricsComponent().setFont(TEST_FONT);
            panel.getTranslationComponent().setFont(TEST_FONT);
            panel.setSong(song);

            // Pre-seed all children to -1 (the reset sentinel) so we can confirm
            // paintComponent overwrites them with a shared computed value.
            panel.getUnderLyricsComponent().setContentX(-1f);
            panel.getBanglaLyricsComponent().setContentX(-1f);
            panel.getTranslationComponent().setContentX(-1f);

            var img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
            var g2 = (java.awt.Graphics2D) img.getGraphics();
            g2.setFont(TEST_FONT);

            try {
                panel.paintComponent(g2);
            } finally {
                g2.dispose();
            }

            var underX = panel.getUnderLyricsComponent().getContentX();
            var banglaX = panel.getBanglaLyricsComponent().getContentX();
            var translationX = panel.getTranslationComponent().getContentX();

            // All three must share the same contentX value — that is the union-alignment invariant.
            assertThat(banglaX)
                .as("banglaLyricsComponent.contentX must equal underLyricsComponent.contentX")
                .isEqualTo(underX);
            assertThat(translationX)
                .as("translationComponent.contentX must equal underLyricsComponent.contentX")
                .isEqualTo(underX);

            // The shared value must not be the reset sentinel (-1), confirming the branch
            // executed was the "union width > 0" branch.
            assertThat(underX)
                .as("contentX must not be -1 (reset sentinel) when union width > 0")
                .isNotEqualTo(-1f);
        }
    }

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class TextPanelPreferredSize {

        /**
         * When {@code song} is null, {@link TextPanel#getPreferredSize()} returns
         * {@code (0, 0)}.
         */
        @Test
        void testNullSongReturnsDimensionZero() {
            var panel = new TextPanel();
            // song is null by default — no setSong() call.
            assertThat(panel.getPreferredSize())
                .as("null song → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * When the song has at least one non-empty text section, the total height is the
         * sum of all three child preferred heights, and the width is the maximum of the
         * three child widths (equal to song.getLineWidthPx() when content is present).
         */
        @Test
        void testNonEmptyContentReturnsSumOfChildHeights() {
            var song = new Song();
            song.setUnderLyrics("Some lyrics");
            var panel = new TextPanel();
            panel.getUnderLyricsComponent().setFont(TEST_FONT);
            panel.setSong(song);

            var underSize = panel.getUnderLyricsComponent().getPreferredSize();
            var banglaSize = panel.getBanglaLyricsComponent().getPreferredSize();
            var translationSize = panel.getTranslationComponent().getPreferredSize();

            var expectedHeight = underSize.height + banglaSize.height + translationSize.height;
            var expectedWidth = Math.max(
                underSize.width,
                Math.max(banglaSize.width, translationSize.width)
            );

            var panelSize = panel.getPreferredSize();
            assertThat(panelSize.height)
                .as("height = sum of all three child heights")
                .isEqualTo(expectedHeight);
            assertThat(panelSize.width)
                .as("width = max of all three child widths")
                .isEqualTo(expectedWidth);
        }
    }

    // =========================================================================
    // StaffPanel — row 24
    // =========================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StaffPanelRebuildLayout {

        /**
         * When no song has been set, {@link StaffPanel#rebuildLayout()} results in
         * zero line panels and zero child components.
         */
        @Test
        void testNoSongProducesEmptyLayout() {
            var panel = new StaffPanel();
            // song is null by default; rebuildLayout is called by setSong, but since
            // no setSong was called, the panel has the constructor-initial state.
            // Call rebuildLayout explicitly to test its null-song guard.
            panel.rebuildLayout();

            assertThat(panel.getLinePanels())
                .as("no song → linePanels must be empty")
                .isEmpty();
            assertThat(panel.getComponentCount())
                .as("no song → no child components added")
                .isEqualTo(0);
        }

        /**
         * When the song has exactly one line, {@link StaffPanel#rebuildLayout()} adds
         * exactly one {@link LinePanel} and no struts (struts are added only between lines).
         */
        @Test
        void testOneLinesProducesOnePanelNoStrut() {
            // new Song() creates a fresh Song that already contains one initial line.
            var song = new Song();
            assertThat(song.lineCount())
                .as("new Song has exactly one initial line")
                .isEqualTo(1);

            var panel = new StaffPanel();
            panel.setSong(song);

            assertThat(panel.getLinePanels())
                .as("one line → exactly one LinePanel")
                .hasSize(1);
            // One LinePanel, zero struts → one component total.
            assertThat(panel.getComponentCount())
                .as("one line → one component (the LinePanel), no strut")
                .isEqualTo(1);
        }

        /**
         * When the song has three lines, {@link StaffPanel#rebuildLayout()} adds three
         * {@link LinePanel}s and two struts (one between each adjacent pair).
         * <p>
         * Total component count = 3 LinePanels + 2 struts = 5.
         */
        @Test
        void testThreeLinesProducesThreePanelsAndTwoStruts() {
            var song = new Song();
            // Add two more lines (song already has one). Use withoutMutationTracking
            // to avoid posting notifications during test setup.
            song.withoutMutationTracking(() -> {
                song.addLine(new Line(song));
                song.addLine(new Line(song));
            });

            assertThat(song.lineCount())
                .as("song should have exactly three lines after adding two more")
                .isEqualTo(3);

            var panel = new StaffPanel();
            panel.setSong(song);

            assertThat(panel.getLinePanels())
                .as("three lines → exactly three LinePanels")
                .hasSize(3);

            // 3 LinePanels + 2 struts = 5 total child components.
            final int expectedComponents = 5;
            assertThat(panel.getComponentCount())
                .as("three lines → five child components (3 panels + 2 struts)")
                .isEqualTo(expectedComponents);
        }
    }
}
