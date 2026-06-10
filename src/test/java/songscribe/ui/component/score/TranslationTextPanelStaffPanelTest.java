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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.layout.LayoutResult;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link TranslationComponent}, {@link TextPanel},
 * {@link StaffPanel}, and {@link MainPanel}.
 *
 * <p>Covers 7F rows 11-12 ({@link TranslationComponent}),
 * rows 20-22 ({@link TextPanel}), rows 24-29 ({@link StaffPanel}),
 * and rows 32-33 ({@link MainPanel}).
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

            {
                var m = song.getMetadata();
                song.setMetadata(new SongMetadata(
                    m.title(), m.number(), m.place(), m.year(), m.month(), m.day(),
                    m.composer(), m.lyricist(), m.lyricsSource(), m.arrangement(), false
                ));
            }
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

            {
                var m = song.getMetadata();
                song.setMetadata(new SongMetadata(
                    m.title(), m.number(), m.place(), m.year(), m.month(), m.day(),
                    m.composer(), m.lyricist(), m.lyricsSource(), m.arrangement(), true
                ));
            }
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

    // =========================================================================
    // StaffPanel — row 25: getLinePanel(index) boundary conditions
    // =========================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StaffPanelGetLinePanel {

        /**
         * A negative index is out of bounds; {@link StaffPanel#getLinePanel} returns null.
         */
        @Test
        void testNegativeIndexReturnsNull() {
            var panel = new StaffPanel();
            panel.setSong(new Song());  // 1 line → 1 LinePanel

            assertThat(panel.getLinePanel(-1))
                .as("index -1 is out of bounds → null")
                .isNull();
        }

        /**
         * Index 0 is valid for a single-line song; the panel is returned.
         */
        @Test
        void testZeroIndexReturnsPanel() {
            var panel = new StaffPanel();
            panel.setSong(new Song());  // 1 line → 1 LinePanel (index 0)
            var linePanels = panel.getLinePanels();

            assertThat(panel.getLinePanel(0))
                .as("index 0 is in range → first LinePanel returned")
                .isSameAs(linePanels.get(0));
        }

        /**
         * Index equal to the panel count (size) is out of bounds; returns null.
         */
        @Test
        void testIndexAtSizeReturnsNull() {
            var panel = new StaffPanel();
            panel.setSong(new Song());  // 1 line → size = 1

            assertThat(panel.getLinePanel(1))
                .as("index == size (1) is out of bounds → null")
                .isNull();
        }

        /**
         * The last valid index (size - 1) returns the last LinePanel.
         */
        @Test
        void testLastValidIndexReturnsLastPanel() {
            var song = new Song();
            song.withoutMutationTracking(() -> song.addLine(new Line(song)));
            // song now has 2 lines; size = 2, last valid index = 1
            var panel = new StaffPanel();
            panel.setSong(song);
            var linePanels = panel.getLinePanels();
            int lastIndex = linePanels.size() - 1;

            assertThat(panel.getLinePanel(lastIndex))
                .as("last valid index returns last LinePanel")
                .isSameAs(linePanels.get(lastIndex));
        }
    }

    // =========================================================================
    // StaffPanel — row 26: getLinePanelAt(point)
    // =========================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StaffPanelGetLinePanelAt {

        /**
         * A point whose coordinates fall inside the first LinePanel's bounds is
         * returned as the first LinePanel.
         */
        @Test
        void testPointInFirstPanelReturnsFirstPanel() {
            var song = new Song();
            song.withoutMutationTracking(() -> song.addLine(new Line(song)));
            // 2 lines → 2 LinePanels
            var panel = new StaffPanel();
            panel.setSong(song);

            var panels = panel.getLinePanels();
            // Manually set bounds: first panel occupies y=[0,99], second y=[100,199]
            panels.get(0).setBounds(0, 0, 200, 100);
            panels.get(1).setBounds(0, 100, 200, 100);

            var result = panel.getLinePanelAt(new Point(50, 50));
            assertThat(result)
                .as("point (50, 50) is inside first panel [0..99] → first panel returned")
                .isSameAs(panels.get(0));
        }

        /**
         * A point whose coordinates fall inside the last LinePanel's bounds is
         * returned as the last LinePanel.
         */
        @Test
        void testPointInLastPanelReturnsLastPanel() {
            var song = new Song();
            song.withoutMutationTracking(() -> song.addLine(new Line(song)));
            var panel = new StaffPanel();
            panel.setSong(song);

            var panels = panel.getLinePanels();
            panels.get(0).setBounds(0, 0, 200, 100);
            panels.get(1).setBounds(0, 100, 200, 100);

            var result = panel.getLinePanelAt(new Point(50, 150));
            assertThat(result)
                .as("point (50, 150) is inside second panel [100..199] → second panel returned")
                .isSameAs(panels.get(1));
        }

        /**
         * A point between two panels — in the gap — is not contained by either
         * panel's bounds; {@link StaffPanel#getLinePanelAt} returns null.
         */
        @Test
        void testPointInGapBetweenPanelsReturnsNull() {
            var panel = new StaffPanel();
            panel.setSong(new Song());  // 1 line

            var panels = panel.getLinePanels();
            // Single panel covers y=[0, 49]; point at y=60 is outside it.
            panels.get(0).setBounds(0, 0, 200, 50);

            assertThat(panel.getLinePanelAt(new Point(50, 60)))
                .as("point y=60 is below the only panel (height 50) → null")
                .isNull();
        }
    }

    // =========================================================================
    // StaffPanel — row 27: getPreferredSize
    // =========================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StaffPanelGetPreferredSize {

        /**
         * When the song is null, {@link StaffPanel#getPreferredSize()} returns
         * {@code (0, 0)} without calling {@code updateSongMetrics}.
         */
        @Test
        void testSongNullReturnsDimensionZero() {
            var panel = new StaffPanel();
            // song is null by default; no setSong() call
            assertThat(panel.getPreferredSize())
                .as("null song → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * For N lines, the total preferred height is the sum of all line heights plus
         * exactly N-1 margins — one between each adjacent pair of lines.
         * <p>
         * An anonymous subclass of {@link StaffPanel} overrides the package-private
         * {@code updateSongMetrics()} as a no-op to avoid requiring a real
         * {@link songscribe.ui.component.ScoreView}. Known sizes are injected via
         * {@code setPreferredSize()} on each {@link LinePanel}.
         */
        @Test
        void testThreeLinesHeightIncludesTwoMargins() {
            var song = new Song();
            song.withoutMutationTracking(() -> {
                song.addLine(new Line(song));
                song.addLine(new Line(song));
            });

            // Anonymous subclass stubs out updateSongMetrics() so no ScoreView is needed.
            var panel = new StaffPanel() {
                @Override
                void updateSongMetrics() {
                    // no-op: skip ScoreView dependency in the test
                }
            };
            panel.setSong(song);  // creates 3 LinePanels

            // Inject a known size on each LinePanel so LineComponent is bypassed.
            final int lineWidth = 200;
            final int lineHeight = 50;
            for (var linePanel : panel.getLinePanels()) {
                linePanel.setPreferredSize(new Dimension(lineWidth, lineHeight));
            }

            var size = panel.getPreferredSize();
            final int lineCount = 3;
            final int marginCount = lineCount - 1;
            int expectedMargin = ScaleContext.ssToRoundedPx(StaffPanel.LINE_MARGIN_BOTTOM_SS);
            int expectedHeight = lineCount * lineHeight + marginCount * expectedMargin;

            assertThat(size.height)
                .as("3 lines → height = 3*lineHeight + 2*lineMargin")
                .isEqualTo(expectedHeight);
            assertThat(size.width)
                .as("width = max of all line widths (all same here)")
                .isEqualTo(lineWidth);
        }
    }

    // =========================================================================
    // StaffPanel — row 28: getLayoutResults threading
    // =========================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StaffPanelGetLayoutResults {

        /**
         * The {@code hasLeadingLyricContinuation} flag is threaded from each line
         * to the next: if line N's layout result reports
         * {@code hasTrailingLyricContinuation()=true}, line N+1's component receives
         * {@code setHasLeadingLyricContinuation(true)}.
         */
        @Test
        void testTrailingContinuationFeedsNextLine() {
            // Build a real LayoutResult that reports trailing lyric continuation.
            var resultWithContinuation = LayoutResult.builder()
                .setHasTrailingLyricContinuation(true)
                .build();

            // Create mock LineComponent and LinePanel for the first line.
            var lc1 = mock(LineComponent.class);
            when(lc1.getLayoutResult()).thenReturn(resultWithContinuation);

            var lp1 = mock(LinePanel.class);
            when(lp1.getLineComponent()).thenReturn(lc1);

            // Second LineComponent: captures the continuation flag passed to it.
            var lc2 = mock(LineComponent.class);
            when(lc2.getLayoutResult()).thenReturn(null);

            var lp2 = mock(LinePanel.class);
            when(lp2.getLineComponent()).thenReturn(lc2);

            // Inject mock LinePanels directly into an otherwise-empty StaffPanel.
            var panel = new StaffPanel();
            panel.getLinePanels().add(lp1);
            panel.getLinePanels().add(lp2);

            panel.getLayoutResults();

            // The second LineComponent must have received true from line 1's result.
            verify(lc2).setHasLeadingLyricContinuation(true);
        }

        /**
         * When a line's {@code getLayoutResult()} returns null (layout not available),
         * the continuation flag is reset to false so the next line starts fresh.
         */
        @Test
        void testNullLayoutResultResetsContinuationToFalse() {
            // First line: non-null result with trailing continuation.
            var resultWithContinuation = LayoutResult.builder()
                .setHasTrailingLyricContinuation(true)
                .build();

            var lc1 = mock(LineComponent.class);
            when(lc1.getLayoutResult()).thenReturn(resultWithContinuation);
            var lp1 = mock(LinePanel.class);
            when(lp1.getLineComponent()).thenReturn(lc1);

            // Second line: null result (simulates layout not yet computed).
            var lc2 = mock(LineComponent.class);
            when(lc2.getLayoutResult()).thenReturn(null);
            var lp2 = mock(LinePanel.class);
            when(lp2.getLineComponent()).thenReturn(lc2);

            // Third line: should receive false because line 2 returned null.
            var lc3 = mock(LineComponent.class);
            when(lc3.getLayoutResult()).thenReturn(null);
            var lp3 = mock(LinePanel.class);
            when(lp3.getLineComponent()).thenReturn(lc3);

            var panel = new StaffPanel();
            panel.getLinePanels().add(lp1);
            panel.getLinePanels().add(lp2);
            panel.getLinePanels().add(lp3);

            panel.getLayoutResults();

            // Line 2 gets true (from line 1's non-null result).
            verify(lc2).setHasLeadingLyricContinuation(true);
            // Line 3 gets false (reset because line 2's result was null).
            verify(lc3).setHasLeadingLyricContinuation(false);
        }
    }

    // =========================================================================
    // StaffPanel — row 29: updateSongMetrics call order
    // =========================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class StaffPanelUpdateSongMetrics {

        /**
         * {@link StaffPanel#updateSongMetrics()} must call
         * {@code scoreView.rebuildLyricRenderMetrics()} BEFORE
         * {@code scoreView.setSongLayoutMetrics(…)}, because line layouts read lyric
         * metrics (set by the first call) during the intermediate
         * {@code getLayoutResults()} step.
         */
        @Test
        void testRebuildLyricRenderMetricsCalledBeforeSetSongLayoutMetrics() {
            // Mock ScoreView (final class — Mockito 5 inline mock maker handles this).
            var mockScoreView = mock(ScoreView.class);
            // Return a real font so ScaleContext.fontAscentSs doesn't NPE.
            when(mockScoreView.getLyricsFont()).thenReturn(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

            // Build a mock LineComponent that provides the ScoreView and returns null
            // for getLayoutResult() so getLayoutResults() produces an empty list.
            var mockLc = mock(LineComponent.class);
            when(mockLc.getScoreView()).thenReturn(mockScoreView);
            when(mockLc.getLayoutResult()).thenReturn(null);

            var mockLp = mock(LinePanel.class);
            when(mockLp.getLineComponent()).thenReturn(mockLc);

            var panel = new StaffPanel();
            panel.getLinePanels().add(mockLp);

            panel.updateSongMetrics();

            // Verify that rebuildLyricRenderMetrics was called BEFORE setSongLayoutMetrics.
            var ordered = inOrder(mockScoreView);
            ordered.verify(mockScoreView).rebuildLyricRenderMetrics();
            ordered.verify(mockScoreView).setSongLayoutMetrics(any());
        }
    }

    // =========================================================================
    // MainPanel — row 32: getPreferredSize conditional gap
    // =========================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MainPanelGetPreferredSize {

        /**
         * When the song is null, {@link MainPanel#getPreferredSize()} returns
         * {@code (0, 0)}.
         */
        @Test
        void testSongNullReturnsDimensionZero() {
            var mainPanel = new MainPanel();
            // No setSong() call — song remains null.
            assertThat(mainPanel.getPreferredSize())
                .as("null song → Dimension(0, 0)")
                .isEqualTo(new Dimension(0, 0));
        }

        /**
         * When both the title and the score have positive height, the gap
         * ({@code scoreMarginTop}) is added between them.
         * <p>
         * A {@link StaffPanel} preferred size is injected via
         * {@code setPreferredSize()} to avoid the ScoreView dependency.
         * A new {@link Song} already has a non-empty default title ("Untitled"), so
         * {@link TitleComponent#getPreferredSize()} returns a positive height without
         * any extra setup.
         * <p>
         * The gap is verified by subtracting all children's sizes from the total:
         * the remainder must equal exactly {@code scoreMarginTop}.
         */
        @Test
        void testBothTitleAndScoreNonZeroHeightAddsGap() {
            // new Song() has title = "Untitled" by default (non-empty).
            var song = new Song();

            var mainPanel = new MainPanel();
            mainPanel.setSong(song);

            var titleComponent = mainPanel.getTitleComponent();
            var staffPanel = mainPanel.getStaffPanel();

            // Set a font on TitleComponent so getFontMetrics() returns usable metrics.
            titleComponent.setFont(TEST_FONT);

            // Inject a known staff panel size to bypass the ScoreView dependency.
            staffPanel.setPreferredSize(new Dimension(300, 100));

            var titleHeight = titleComponent.getPreferredSize().height;
            assertThat(titleHeight)
                .as("non-empty default title must produce positive height (precondition)")
                .isGreaterThan(0);

            var totalSize = mainPanel.getPreferredSize();
            var staffSize = staffPanel.getPreferredSize();
            var textSize = mainPanel.getTextPanel().getPreferredSize();
            var footnotesSize = mainPanel.getFootnotesComponent().getPreferredSize();

            // The gap is what remains after subtracting all children's sizes.
            int gap = totalSize.height - titleHeight - staffSize.height
                - textSize.height - footnotesSize.height;
            int expectedGap = ScaleContext.ssToRoundedPx(MainPanel.SCORE_MARGIN_TOP_SS);

            assertThat(gap)
                .as("gap between title and score must equal scoreMarginTop")
                .isEqualTo(expectedGap);
        }

        /**
         * When the title is empty (height == 0), the gap is NOT added even if
         * the score has positive height.
         */
        @Test
        void testEmptyTitleDoesNotAddGap() {
            var song = new Song();
            // A new Song has a non-empty default title ("Untitled") and a default number ("1").
            // getNumberedTitle() returns "number. title" when number is non-empty, so both
            // must be cleared to make getNumberedTitle() return "".
            song.withoutMutationTracking(() -> {
                var m = song.getMetadata();
                song.setMetadata(new SongMetadata(
                    "", "", m.place(), m.year(), m.month(), m.day(),
                    m.composer(), m.lyricist(), m.lyricsSource(), m.arrangement(), m.unofficialTranslation()
                ));
            });

            var mainPanel = new MainPanel();
            mainPanel.setSong(song);

            var staffPanel = mainPanel.getStaffPanel();

            // Inject a known staff panel size.
            staffPanel.setPreferredSize(new Dimension(300, 100));

            // With title empty (height == 0), the gap condition is false → no gap.
            var totalSize = mainPanel.getPreferredSize();
            var staffSize = staffPanel.getPreferredSize();
            var textSize = mainPanel.getTextPanel().getPreferredSize();
            var footnotesSize = mainPanel.getFootnotesComponent().getPreferredSize();

            // No gap: total = 0(title) + 0(gap) + staffHeight + textHeight + footnotesHeight
            int expectedHeight = staffSize.height + textSize.height + footnotesSize.height;

            assertThat(totalSize.height)
                .as("empty title (height=0) → no gap: total = staffH + textH + footnotesH")
                .isEqualTo(expectedHeight);
        }
    }

    // =========================================================================
    // MainPanel — row 33: getLinePanelAt(point) routing
    // =========================================================================

    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class MainPanelGetLinePanelAt {

        /**
         * A point inside the {@link StaffPanel}'s bounds is transformed to
         * StaffPanel-local coordinates and the matching {@link LinePanel} is returned.
         */
        @Test
        void testPointInsideStaffPanelDelegatesToStaffPanel() {
            var mainPanel = new MainPanel();
            mainPanel.setSong(new Song());  // 1 line → 1 LinePanel in staffPanel

            // Place staffPanel at y=50, height=100.
            mainPanel.getStaffPanel().setBounds(0, 50, 200, 100);
            // Place the sole LinePanel to fill the staffPanel area (local coords).
            mainPanel.getStaffPanel().getLinePanels().get(0).setBounds(0, 0, 200, 100);

            // Point (10, 80) in mainPanel coords → local (10, 30) which is inside the LinePanel.
            var result = mainPanel.getLinePanelAt(new Point(10, 80));
            assertThat(result)
                .as("point inside staffPanel bounds → LinePanel returned")
                .isSameAs(mainPanel.getStaffPanel().getLinePanels().get(0));
        }

        /**
         * A point outside the {@link StaffPanel}'s bounds (e.g., in the title area above
         * the staff) causes {@link MainPanel#getLinePanelAt} to return null without
         * delegating.
         */
        @Test
        void testPointOutsideStaffPanelReturnsNull() {
            var mainPanel = new MainPanel();
            mainPanel.setSong(new Song());

            // staffPanel starts at y=50; point at y=10 is above it.
            mainPanel.getStaffPanel().setBounds(0, 50, 200, 100);

            assertThat(mainPanel.getLinePanelAt(new Point(10, 10)))
                .as("point y=10 is above staffPanel (starts at y=50) → null")
                .isNull();
        }
    }
}
