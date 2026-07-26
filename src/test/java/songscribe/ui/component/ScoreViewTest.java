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
package songscribe.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JComponent;
import javax.swing.KeyStroke;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.xml.sax.SAXException;

import songscribe.RequiresDisplay;
import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.io.SongIO;
import songscribe.io.SongLoadResult;
import songscribe.io.SongLoader;
import songscribe.io.musicxml.MusicXmlWriter;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.PageModel;
import songscribe.engraving.Staff;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.LineSelectionState;

/**
 * Unit tests for {@link ScoreView} behaviors not covered by {@link ScoreViewSetFontsTest}:
 * the {@link ScoreView#getDocumentFonts()} guard, the {@link ScoreView#installDocumentFonts}
 * non-mutation contract, {@link ScoreView#rebuildLyricRenderMetrics()} guard and
 * idempotency, {@link ScoreView#getSuggestedFileName()} branch logic,
 * {@link ScoreView#getNoteYPosPx(int, int)} coordinate formula, and
 * {@link ScoreView#drawWidthIfWiderLine(songscribe.dom.Line, boolean)} rescaling.
 */
class ScoreViewTest extends UnitTest {

    @Nested
    class GetDocumentFonts {

        @Test
        void testGetDocumentFontsThrowsIllegalStateExceptionBeforeInitialization() {
            // ScoreView(null) creates a headless instance without installing document fonts.
            var scoreView = new ScoreView(null);

            assertThatThrownBy(scoreView::getDocumentFonts)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("documentFonts not initialized");
        }
    }

    @Nested
    class InstallDocumentFonts {

        private MockedStatic<MessageCenter> messageCenterMock;

        @BeforeEach
        void setUp() {
            messageCenterMock = mockStatic(MessageCenter.class);
        }

        @AfterEach
        void tearDown() {
            messageCenterMock.close();
        }

        @Test
        void testInstallDocumentFontsSetsFontsFieldWithoutPostingFontChange() {
            var scoreView = new ScoreView(null);
            var fonts = DocumentFonts.defaultFonts();

            scoreView.installDocumentFonts(fonts);

            // Must not post any message (not an undoable user edit, no FontChange recorded).
            messageCenterMock.verify(
                () -> MessageCenter.post(any(Message.class)),
                org.mockito.Mockito.never()
            );

            // getDocumentFonts() must return the installed instance.
            assertThat(scoreView.getDocumentFonts()).isSameAs(fonts);
        }
    }

    @Nested
    class RebuildLyricRenderMetrics {

        @Test
        void testRebuildLyricRenderMetricsIsNoOpWhenSongIsNull() {
            // song == null: early return must not throw.
            var scoreView = new ScoreView(null);
            scoreView.installDocumentFonts(DocumentFonts.defaultFonts());

            // documentFonts is set but song is null — must return silently.
            scoreView.rebuildLyricRenderMetrics();

            // The song == null guard must leave the field null. Without the guard the method
            // would silently build metrics from the installed fonts, so this assertion is what
            // catches the guard being removed.
            assertThat(scoreView.lyricRenderMetrics).isNull();
        }

        @Test
        void testRebuildLyricRenderMetricsIsNoOpWhenDocumentFontsIsNull() {
            // documentFonts == null: early return must not throw.
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());
            // documentFonts deliberately not installed.

            scoreView.rebuildLyricRenderMetrics();

            // The documentFonts == null guard must leave the field null (and not NPE on
            // documentFonts.getLyricsFont()).
            assertThat(scoreView.lyricRenderMetrics).isNull();
        }

        @Test
        void testRebuildLyricRenderMetricsIsIdempotentWhenLyricsFontUnchanged() {
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());
            scoreView.installDocumentFonts(DocumentFonts.defaultFonts());

            scoreView.rebuildLyricRenderMetrics();
            var firstMetrics = scoreView.getLyricRenderMetrics();

            // Same font: second call must return the same instance (idempotency guard).
            scoreView.rebuildLyricRenderMetrics();
            var secondMetrics = scoreView.getLyricRenderMetrics();

            assertThat(secondMetrics).isSameAs(firstMetrics);
        }

        @Test
        void testRebuildLyricRenderMetricsRebuildsWhenLyricsFontChanges() {
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());

            var initialFonts = DocumentFonts.defaultFonts();
            scoreView.installDocumentFonts(initialFonts);
            scoreView.rebuildLyricRenderMetrics();
            var firstMetrics = scoreView.getLyricRenderMetrics();

            // Change the lyrics font and install the updated DocumentFonts.
            var updatedFonts = new DocumentFonts(initialFonts);
            updatedFonts.setFont(FontKey.LYRICS, new Font("Serif", Font.PLAIN, 18));
            scoreView.installDocumentFonts(updatedFonts);

            scoreView.rebuildLyricRenderMetrics();
            var secondMetrics = scoreView.getLyricRenderMetrics();

            assertThat(secondMetrics).isNotSameAs(firstMetrics);
            assertThat(secondMetrics.lyricsFont())
                .isEqualTo(updatedFonts.getFont(FontKey.LYRICS));
        }
    }

    @Nested
    class GetSuggestedFileName {

        @Test
        void testGetSuggestedFileNameZeroPadsNumericSongNumber() {
            var scoreView = new ScoreView(null);
            var song = new Song();
            var current3 = song.getMetadata();
            song.setMetadata(new SongMetadata(
                "Mélodie", "3",
                current3.place(), current3.year(), current3.month(), current3.day(),
                current3.composer(), current3.lyricist(),
                current3.lyricsSource(), current3.arrangement(), current3.unofficialTranslation(),
                current3.subtitle(), "", 0, 0
            ));
            scoreView.setSong(song);

            // Numeric number → zero-padded to three digits; diacritics stripped from title.
            assertThat(scoreView.getSuggestedFileName()).isEqualTo("003 Melodie");
        }

        @Test
        void testGetSuggestedFileNameUsesNonNumericSongNumberVerbatim() {
            var scoreView = new ScoreView(null);
            var song = new Song();
            var currentA = song.getMetadata();
            song.setMetadata(new SongMetadata(
                "Title", "A",
                currentA.place(), currentA.year(), currentA.month(), currentA.day(),
                currentA.composer(), currentA.lyricist(),
                currentA.lyricsSource(), currentA.arrangement(), currentA.unofficialTranslation(),
                currentA.subtitle(), "", 0, 0
            ));
            scoreView.setSong(song);

            // Non-numeric number → used as-is, followed by a space and the title.
            assertThat(scoreView.getSuggestedFileName()).isEqualTo("A Title");
        }

        @Test
        void testGetSuggestedFileNameOmitsLeadingSeparatorWhenNumberIsEmpty() {
            var scoreView = new ScoreView(null);
            var song = new Song();
            var currentEmpty = song.getMetadata();
            song.setMetadata(new SongMetadata(
                "Mélodie", "",
                currentEmpty.place(), currentEmpty.year(), currentEmpty.month(), currentEmpty.day(),
                currentEmpty.composer(), currentEmpty.lyricist(),
                currentEmpty.lyricsSource(), currentEmpty.arrangement(), currentEmpty.unofficialTranslation(),
                currentEmpty.subtitle(), "", 0, 0
            ));
            scoreView.setSong(song);

            // Empty number → no leading separator; only diacritic-stripped title.
            assertThat(scoreView.getSuggestedFileName()).isEqualTo("Melodie");
        }
    }

    @Nested
    class GetNoteYPosPx {

        @Test
        void testGetNoteYPosPxComputesCorrectCoordinate() {
            // Verify the formula: middleLineYPx + ssToPx(spToSs(staffPosition)) + lineIndex * rowHeightPx
            var scoreView = new ScoreView(null);
            var middleLineY = 100;
            var rowHeight = 120;
            var staffPosition = 2;
            var lineIndex = 1;
            scoreView.setMiddleLineYPx(middleLineY);
            scoreView.setRowHeightPx(rowHeight);

            var expected = (int) Math.round(
                middleLineY
                    + ScaleContext.ssToPx(Staff.spToSs(staffPosition))
                    + lineIndex * rowHeight
            );

            assertThat(scoreView.getNoteYPosPx(staffPosition, lineIndex)).isEqualTo(expected);
        }
    }

    /**
     * Unit tests for the export-sizing core promise (Phase 6, gap): {@link ScoreView#getSheetWidthPx()}
     * and {@link ScoreView#getSheetHeightPx()} must not vary with the view's current zoom, so exporters
     * never inherit view zoom.
     */
    @Nested
    class ExportSizing {

        private static final int LINE_WIDTH_PX = 800;

        // Deliberately larger than any page-height-plus-margins value in document px, so the
        // content term dominates the max() in getSheetHeightPx and the test actually exercises
        // the ViewPx -> DocPx conversion rather than trivially returning the page height.
        private static final int DOMINANT_CONTENT_HEIGHT_VIEW_PX = 100_000;

        private static final int ZOOM_PERCENT_QUADRUPLE = 400;

        /** Returns a ScoreView whose song reports the given lineWidthPx. */
        private ScoreView scoreViewWithLineWidth(int lineWidthPx) {
            var songMock = mock(Song.class);
            when(songMock.getLineWidthPx()).thenReturn(lineWidthPx);
            when(songMock.getLineWidthSs()).thenReturn(ScaleContext.pxToSs(lineWidthPx));
            var scoreView = new ScoreView(null);
            scoreView.setSong(songMock);
            return scoreView;
        }

        @Test
        void testGetSheetWidthPxIsZoomIndependent() {
            var scoreView = scoreViewWithLineWidth(LINE_WIDTH_PX);
            var widthAtDefaultZoom = scoreView.getSheetWidthPx();

            scoreView.getViewScale().setZoomPercent(ZOOM_PERCENT_QUADRUPLE);

            assertThat(scoreView.getSheetWidthPx()).isEqualTo(widthAtDefaultZoom);
        }

        @Test
        void testGetSheetHeightPxIsZoomIndependent() {
            var scoreView = scoreViewWithLineWidth(LINE_WIDTH_PX);
            var mainPanelMock = mock(MainPanel.class);
            when(mainPanelMock.getPreferredSize())
                .thenReturn(new Dimension(LINE_WIDTH_PX, DOMINANT_CONTENT_HEIGHT_VIEW_PX));
            scoreView.setMainPanel(mainPanelMock);
            var heightAtDefaultZoom = scoreView.getSheetHeightPx();

            // Simulate the real-world effect of zooming: on-screen preferred sizes grow with
            // the view factor, so the mocked mainPanel's preferred height grows proportionally.
            scoreView.getViewScale().setZoomPercent(ZOOM_PERCENT_QUADRUPLE);
            when(mainPanelMock.getPreferredSize()).thenReturn(new Dimension(
                LINE_WIDTH_PX * ZOOM_PERCENT_QUADRUPLE / 100,
                DOMINANT_CONTENT_HEIGHT_VIEW_PX * ZOOM_PERCENT_QUADRUPLE / 100
            ));

            assertThat(scoreView.getSheetHeightPx()).isEqualTo(heightAtDefaultZoom);
        }
    }

    /**
     * Unit tests for {@link ScoreView#computeAnchoredViewPosition}: the pure,
     * Swing-free helper that computes the post-zoom scroll position. Most tests
     * use {@link #DEFAULT_ANCHOR_VIEWPORT_OFFSET} — the viewport center
     * horizontally, its top edge vertically — matching the default (non-cursor)
     * menu/keyboard zoom anchor; with {@link #EXTENT_SIZE} that's (200, 0).
     */
    @Nested
    class ComputeAnchoredViewPosition {

        private static final Point ORIGIN = new Point(0, 0);
        private static final Dimension EXTENT_SIZE = new Dimension(400, 300);
        private static final double UNITY_ZOOM_RATIO = 1.0;
        private static final Point DEFAULT_ANCHOR_VIEWPORT_OFFSET = new Point(EXTENT_SIZE.width / 2, 0);

        @Test
        void testAnchoringWithUnchangedZoomReturnsUnscaledTarget() {
            // ratio == 1.0: content point maps straight through; only the horizontal
            // center offset (200) is subtracted, the vertical top offset is 0.
            var anchorContentPoint = new Point(500, 300);
            var viewSize = new Dimension(2000, 1500);

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, UNITY_ZOOM_RATIO, ORIGIN, DEFAULT_ANCHOR_VIEWPORT_OFFSET, viewSize, EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(300, 300));
        }

        @Test
        void testZoomInRatioScalesAnchorUpByRatio() {
            // ratio == 2.0 (e.g. 200% zoom from 100%): the anchor content coordinates
            // must be doubled before subtracting the horizontal center offset.
            var anchorContentPoint = new Point(500, 300);
            var zoomInRatio = 2.0;
            var viewSize = new Dimension(4000, 3000);

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, zoomInRatio, ORIGIN, DEFAULT_ANCHOR_VIEWPORT_OFFSET, viewSize, EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(800, 600));
        }

        @Test
        void testZoomOutRatioScalesAnchorDownByRatio() {
            // ratio == 0.5 (e.g. 50% zoom from 100%): the anchor content coordinates
            // must be halved before subtracting the horizontal center offset.
            var anchorContentPoint = new Point(500, 300);
            var zoomOutRatio = 0.5;
            var viewSize = new Dimension(1000, 750);

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, zoomOutRatio, ORIGIN, DEFAULT_ANCHOR_VIEWPORT_OFFSET, viewSize, EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(50, 150));
        }

        @Test
        void testUsesContentOriginOffset() {
            // A non-zero content origin (the ScoreView's post-validate position within the
            // scrolled view) must be added to both axes. Dropping the contentOrigin term
            // would yield (300, 300) instead of (400, 380).
            var contentOrigin = new Point(100, 80);
            var anchorContentPoint = new Point(500, 300);
            var viewSize = new Dimension(2000, 1500);

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint,
                UNITY_ZOOM_RATIO,
                contentOrigin,
                DEFAULT_ANCHOR_VIEWPORT_OFFSET,
                viewSize,
                EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(400, 380));
        }

        @Test
        void testClampsToMinXWhenTargetIsNegative() {
            // Anchor near the content's left edge pushes targetX below 0 — must clamp to 0.
            var anchorContentPoint = new Point(10, 500);
            var viewSize = new Dimension(2000, 1500);

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, UNITY_ZOOM_RATIO, ORIGIN, DEFAULT_ANCHOR_VIEWPORT_OFFSET, viewSize, EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(0, 500));
        }

        @Test
        void testClampsToMaxXWhenTargetExceedsViewWidth() {
            // Anchor near the content's right edge pushes targetX past viewSize - extentSize.
            var anchorContentPoint = new Point(1900, 500);
            var viewSize = new Dimension(2000, 1500);
            var maxX = viewSize.width - EXTENT_SIZE.width;

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, UNITY_ZOOM_RATIO, ORIGIN, DEFAULT_ANCHOR_VIEWPORT_OFFSET, viewSize, EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(maxX, 500));
        }

        @Test
        void testClampsToMinYWhenTargetIsNegative() {
            // An anchor content point above the ScoreView origin yields a negative targetY
            // (the vertical anchor offset is 0, so only a negative content Y drives this) —
            // must clamp to 0.
            var anchorContentPoint = new Point(500, -50);
            var viewSize = new Dimension(2000, 1500);

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, UNITY_ZOOM_RATIO, ORIGIN, DEFAULT_ANCHOR_VIEWPORT_OFFSET, viewSize, EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(300, 0));
        }

        @Test
        void testClampsToMaxYWhenTargetExceedsViewHeight() {
            // Anchor near the content's bottom edge pushes targetY past viewSize - extentSize.
            var anchorContentPoint = new Point(500, 1400);
            var viewSize = new Dimension(2000, 1500);
            var maxY = viewSize.height - EXTENT_SIZE.height;

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, UNITY_ZOOM_RATIO, ORIGIN, DEFAULT_ANCHOR_VIEWPORT_OFFSET, viewSize, EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(300, maxY));
        }

        @Test
        void testClampsToZeroWhenViewIsSmallerThanExtent() {
            // Content smaller than the viewport: max = Math.max(0, viewSize - extentSize)
            // is 0 on both axes, so any target pins to (0, 0). Without the Math.max(0, …)
            // guard, maxX/maxY would be negative and Math.clamp would throw.
            var anchorContentPoint = new Point(500, 300);
            var viewSize = new Dimension(200, 150);

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, UNITY_ZOOM_RATIO, ORIGIN, DEFAULT_ANCHOR_VIEWPORT_OFFSET, viewSize, EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(0, 0));
        }

        @Test
        void testAnchorAtContentOriginClampsToZeroRegardlessOfZoom() {
            // The anchor is the content origin (0, 0), so scaling by the ratio leaves it at
            // 0; after subtracting the center offset and clamping, the result pins to (0, 0)
            // for any zoom ratio. Verifies the origin/clamp path, not ratio scaling (which
            // testZoomIn/OutRatio cover).
            var anchorContentPoint = new Point(0, 0);
            var zoomInRatio = 2.0;
            var viewSize = new Dimension(4000, 3000);

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, zoomInRatio, ORIGIN, DEFAULT_ANCHOR_VIEWPORT_OFFSET, viewSize, EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(0, 0));
        }

        @Test
        void testCustomAnchorViewportOffsetTargetsThatOffsetInsteadOfCenter() {
            // A cursor-driven zoom anchors at an arbitrary viewport offset, not the
            // center/top default — the anchor content point must land under that
            // offset instead.
            var anchorContentPoint = new Point(500, 300);
            var cursorViewportOffset = new Point(50, 120);
            var viewSize = new Dimension(2000, 1500);

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, UNITY_ZOOM_RATIO, ORIGIN, cursorViewportOffset, viewSize, EXTENT_SIZE
            );

            assertThat(result).isEqualTo(new Point(450, 180));
        }

        @Test
        void testCustomAnchorViewportOffsetCombinesWithNonUnityZoomRatio() {
            // The real cursor-zoom path always has both non-trivial at once: the anchor
            // is first scaled by the ratio, THEN the cursor offset is subtracted. Order
            // matters — scaling the offset (or subtracting before scaling) would fail here.
            var anchorContentPoint = new Point(500, 300);
            var zoomInRatio = 2.0;
            var cursorViewportOffset = new Point(50, 120);
            var viewSize = new Dimension(4000, 3000);

            var result = ScoreView.computeAnchoredViewPosition(
                anchorContentPoint, zoomInRatio, ORIGIN, cursorViewportOffset, viewSize, EXTENT_SIZE
            );

            // (500*2 - 50, 300*2 - 120) = (950, 480), within bounds so unclamped.
            assertThat(result).isEqualTo(new Point(950, 480));
        }
    }

    @Nested
    class DrawWidthIfWiderLine {

        private static final int LINE_WIDTH_PX = 800;
        private static final int FIRST_X = 0;
        private static final int MID_X = 500;
        private static final int END_X = 1000;
        private static final int END_X_WITHIN_THRESHOLD = 700;

        /** Returns a ScoreView whose song reports the given lineWidthPx. */
        private ScoreView scoreViewWithLineWidth(int lineWidthPx) {
            var songMock = mock(Song.class);
            when(songMock.getLineWidthPx()).thenReturn(lineWidthPx);
            var scoreView = new ScoreView(null);
            scoreView.setSong(songMock);
            return scoreView;
        }

        @Test
        void testDrawWidthIfWiderLineRescalesXOffsetsProportionallyWhenEndExceedsThreshold() {
            var scoreView = scoreViewWithLineWidth(LINE_WIDTH_PX);
            var line = detachedLine();
            var elem0 = ElementType.CROTCHET.newInstance();
            var elem1 = ElementType.CROTCHET.newInstance();
            var elem2 = ElementType.CROTCHET.newInstance();
            elem0.setXOffsetPx(FIRST_X);
            elem1.setXOffsetPx(MID_X);
            elem2.setXOffsetPx(END_X);
            line.addElement(elem0);
            line.addElement(elem1);
            line.addElement(elem2);

            scoreView.drawWidthIfWiderLine(line, false);

            // Compute expected values using the same formula as production code.
            var idealSpace = (float)(ScaleContext.ssToPx(HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS) + 20);
            var ratio = (LINE_WIDTH_PX - idealSpace - FIRST_X) / (float)(END_X - FIRST_X);
            var expectedMid = FIRST_X + Math.round((MID_X - FIRST_X) * ratio);
            var expectedEnd = FIRST_X + Math.round((END_X - FIRST_X) * ratio);

            // elem0 anchors at FIRST_X and must be untouched (guards the loop start index).
            assertThat(elem0.getXOffsetPx()).isEqualTo(FIRST_X);
            assertThat(elem1.getXOffsetPx()).isEqualTo(expectedMid);
            assertThat(elem2.getXOffsetPx()).isEqualTo(expectedEnd);
            // The proportional rescale must also record the spacing ratio on the line.
            assertThat(line.getElementSpacingRatio()).isEqualTo(ratio);
        }

        @Test
        void testDrawWidthIfWiderLineUsesEndNoteContentWidthAsIdealSpaceWhenRevalidateOnly() {
            // revalidateOnly=true takes the other idealSpace branch: the end note's own
            // content width rather than the default column-gap formula.
            var scoreView = scoreViewWithLineWidth(LINE_WIDTH_PX);
            var line = detachedLine();
            var elem0 = ElementType.CROTCHET.newInstance();
            var elem1 = ElementType.CROTCHET.newInstance();
            elem0.setXOffsetPx(FIRST_X);
            elem1.setXOffsetPx(END_X);  // END_X > LINE_WIDTH_PX → exceeds threshold
            line.addElement(elem0);
            line.addElement(elem1);

            scoreView.drawWidthIfWiderLine(line, true);

            var idealSpace = (float) elem1.getContentWidthPx();
            var ratio = (LINE_WIDTH_PX - idealSpace - FIRST_X) / (float) (END_X - FIRST_X);
            var expectedEnd = FIRST_X + Math.round((END_X - FIRST_X) * ratio);

            assertThat(elem0.getXOffsetPx()).isEqualTo(FIRST_X);
            assertThat(elem1.getXOffsetPx()).isEqualTo(expectedEnd);
            assertThat(line.getElementSpacingRatio()).isEqualTo(ratio);
        }

        @Test
        void testDrawWidthIfWiderLineDoesNotChangeXOffsetsWhenEndNoteIsWithinThreshold() {
            var scoreView = scoreViewWithLineWidth(LINE_WIDTH_PX);
            var line = detachedLine();
            var elem0 = ElementType.CROTCHET.newInstance();
            var elem1 = ElementType.CROTCHET.newInstance();
            var elem2 = ElementType.CROTCHET.newInstance();
            elem0.setXOffsetPx(FIRST_X);
            elem1.setXOffsetPx(MID_X);
            elem2.setXOffsetPx(END_X_WITHIN_THRESHOLD);
            line.addElement(elem0);
            line.addElement(elem1);
            line.addElement(elem2);

            scoreView.drawWidthIfWiderLine(line, false);

            // End note is within the threshold — x-offsets must be unchanged.
            assertThat(elem1.getXOffsetPx()).isEqualTo(MID_X);
            assertThat(elem2.getXOffsetPx()).isEqualTo(END_X_WITHIN_THRESHOLD);
        }

        @Test
        void testDrawWidthIfWiderLineIsNoOpWhenEffectiveElementCountIsOne() {
            // A line with exactly one effective element must not attempt rescaling:
            // effectiveCount <= 1 is the early-exit guard; removing it would cause
            // an index-out-of-bounds on line.getElement(effectiveCount - 1).
            var scoreView = scoreViewWithLineWidth(LINE_WIDTH_PX);
            var line = detachedLine();
            var singleElem = ElementType.CROTCHET.newInstance();
            singleElem.setXOffsetPx(END_X);  // far enough to exceed threshold if rescaling ran
            line.addElement(singleElem);

            scoreView.drawWidthIfWiderLine(line, false);

            // x-offset must be completely unchanged — no rescaling occurred.
            assertThat(singleElem.getXOffsetPx()).isEqualTo(END_X);
        }
    }

    @Nested
    class SetKeyBindingsEnabled {

        private static final String ACTION_KEY = "testAction";
        private static final KeyStroke STROKE =
            KeyStroke.getKeyStroke(KeyEvent.VK_A, 0);

        /**
         * Injects a synthetic binding into the score-view's keybinding map so
         * tests can exercise the enable/disable toggle without running full init().
         */
        private ScoreView scoreViewWithBinding() {
            var scoreView = new ScoreView(null);
            scoreView.scoreKeyBindings.put(STROKE, ACTION_KEY);
            // Register the action key in the component's input map to simulate
            // the state after installKeyBindings().
            scoreView.getInputMap(JComponent.WHEN_FOCUSED).put(STROKE, ACTION_KEY);
            return scoreView;
        }

        @Test
        void testSetKeyBindingsEnabledFalseReplacesEachBindingWithNoneSentinel() {
            var scoreView = scoreViewWithBinding();

            scoreView.setKeyBindingsEnabled(false);

            var inputMap = scoreView.getInputMap(JComponent.WHEN_FOCUSED);
            // The stroke must now map to the disabled sentinel, not the original action key.
            assertThat(inputMap.get(STROKE)).isEqualTo("none");
        }

        @Test
        void testSetKeyBindingsEnabledTrueRestoresOriginalActionKeys() {
            var scoreView = scoreViewWithBinding();

            // Disable then re-enable.
            scoreView.setKeyBindingsEnabled(false);
            scoreView.setKeyBindingsEnabled(true);

            var inputMap = scoreView.getInputMap(JComponent.WHEN_FOCUSED);
            // The original action key must be restored after re-enabling.
            assertThat(inputMap.get(STROKE)).isEqualTo(ACTION_KEY);
        }
    }

    @Nested
    class IsInitialized {

        @Test
        void testIsInitializedReturnsFalseBeforeSongIsSet() {
            // A headless ScoreView(null) has song == null until setSong() is called.
            var scoreView = new ScoreView(null);

            assertThat(scoreView.isInitialized()).isFalse();
        }

        @Test
        void testIsInitializedReturnsTrueAfterSongIsSet() {
            var scoreView = new ScoreView(null);
            scoreView.setSong(new Song());

            assertThat(scoreView.isInitialized()).isTrue();
        }
    }

    /**
     * Unit tests for {@link ScoreView#openFile}: each failure branch returns {@code false}
     * and the success path returns {@code true}, installs fonts, sets the song, and fires
     * the {@code onFileOpened} callback.
     *
     * <p>Failure branches are driven by mocking {@link SongLoader#load} so no real file I/O
     * is needed. The success path uses a real fixture file to verify the full happy path.
     */
    @Nested
    class OpenFile {

        // stubFile is the dummy file used in failure tests; all failure branches
        // are exercised by mocking SongLoader.load within each test's try-with-resources.
        private static final File STUB_FILE = new File("stub.mssw");

        // An actual-inches value that clearly exceeds MAX_LINE_WIDTH_INCHES; used in the
        // direct LineWidthTooLarge test so the value is self-documenting rather than raw.
        private static final double EXCESS_LINE_WIDTH_INCHES = 100.0;

        // A non-SongScribe provenance string for the WrongSoftware arm.
        private static final String FOREIGN_SOFTWARE = "Finale";

        @Test
        void testOpenFileReturnsFalseOnNewerVersion() {
            var cause = new SongIO.NewerVersionException();
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.NewerVersion(STUB_FILE, cause));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileReturnsFalseOnParseError() {
            var cause = new SAXException("damaged");
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.ParseError(STUB_FILE, cause));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileReturnsFalseOnIoError() {
            var cause = new IOException("permission denied");
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.IoError(STUB_FILE, cause));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileReturnsFalseWhenLineTooWide() {
            // openFile converts a Success whose line exceeds MAX_LINE_WIDTH_INCHES into
            // a LineWidthTooLarge before the switch — verify the switch arm returns false.
            // We supply the conversion result directly so the test remains focused on the
            // switch arm rather than the conversion arithmetic.
            var e = new SongLoadResult.LineWidthTooLarge(STUB_FILE, EXCESS_LINE_WIDTH_INCHES, PageModel.MAX_LINE_WIDTH_INCHES);
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE)).thenReturn(e);

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileReturnsFalseOnWrongSoftware() {
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.WrongSoftware(STUB_FILE, FOREIGN_SOFTWARE));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileReturnsFalseOnWrongSoftwareWithNullSoftware() {
            // Null software drives the fallback branch of the arm's dialog-name
            // ternary; a regression there would NPE rather than return false.
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.WrongSoftware(STUB_FILE, null));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileReturnsFalseOnUnsupportedFileFormat() {
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.UnsupportedFileFormat(STUB_FILE, "pdf"));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
            }
        }

        @Test
        void testOpenFileDetectsLineWidthTooLargeFromSuccessResult() {
            // Verify the conversion path: a Success whose song reports a line width that
            // exceeds MAX_LINE_WIDTH_INCHES must be converted to LineWidthTooLarge, causing
            // openFile to return false without setting a song.
            var songMock = mock(Song.class);
            // getLineWidthSs() is called inside openFile to convert to inches; return a
            // value large enough that any reasonable DPI pushes it past the max.
            var hugeLineWidthSs = 100_000.0;
            when(songMock.getLineWidthSs()).thenReturn(hugeLineWidthSs);
            var fonts = DocumentFonts.defaultFonts();
            try (var mock = mockStatic(SongLoader.class)) {
                mock.when(() -> SongLoader.load(STUB_FILE))
                    .thenReturn(new SongLoadResult.Success(songMock, fonts, null));

                var scoreView = new ScoreView(null);
                assertThat(scoreView.openFile(STUB_FILE, false)).isFalse();
                // Song must not have been installed — the file was refused.
                assertThat(scoreView.isInitialized()).isFalse();
            }
        }

        @Test
        @RequiresDisplay
        void testOpenFileReturnsTrueOnSuccessInstallsFontsSetsSongAndFiresCallback()
            throws URISyntaxException {

            // Use a real fixture file; SongLoader is not mocked here so the full
            // success code path runs with real I/O.
            var file = fixtureFile("full-line");
            var capturedFile = new AtomicReference<File>();
            var scoreView = new ScoreView(capturedFile::set);
            var result = scoreView.openFile(file, true);

            assertThat(result).isTrue();
            // installDocumentFonts was called — getDocumentFonts() must not throw.
            assertThat(scoreView.getDocumentFonts()).isNotNull();
            // setSong was called — isInitialized() checks song != null.
            assertThat(scoreView.isInitialized()).isTrue();
            // Legacy .mssw import is one-way: the callback fires with null so the
            // document stays untitled and the first Save writes a fresh .musicxml.
            assertThat(capturedFile.get()).isNull();
        }

        @Test
        @RequiresDisplay
        void testOpenFileReturnsTrueAndFiresCallbackWithFileForWriterProducedMusicXml(
            @TempDir Path tempDir
        ) throws Exception {

            // Guards the silent "legacy branch regresses to file" failure mode: unlike
            // the .mssw case above (untitled ⇒ null), opening a writer-produced .musicxml
            // must report the file itself, since MusicXML is the canonical format.
            var fixture = loadFixtureResult("full-line");
            var stringWriter = new StringWriter();
            var printWriter = new PrintWriter(stringWriter);
            MusicXmlWriter.writeSong(fixture.song(), fixture.fonts(), printWriter);
            printWriter.flush();

            var file = tempDir.resolve("song.musicxml").toFile();
            Files.writeString(file.toPath(), stringWriter.toString());

            var capturedFile = new AtomicReference<File>();
            var scoreView = new ScoreView(capturedFile::set);
            var result = scoreView.openFile(file, true);

            assertThat(result).isTrue();
            assertThat(scoreView.isInitialized()).isTrue();
            assertThat(capturedFile.get()).isEqualTo(file);
        }

        @Test
        @RequiresDisplay
        void testOpenFileDoesNotFireCallbackWhenUpdateCurrentFileIsFalse()
            throws URISyntaxException {

            // updateCurrentFile=false: the onFileOpened guard must suppress the callback
            // even on a successful load; without the guard the callback would always fire.
            var file = fixtureFile("full-line");
            var capturedFile = new AtomicReference<File>();
            var scoreView = new ScoreView(capturedFile::set);
            var result = scoreView.openFile(file, false);

            assertThat(result).isTrue();
            // Song must still be installed.
            assertThat(scoreView.isInitialized()).isTrue();
            // Callback must not have been invoked.
            assertThat(capturedFile.get()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // editLyricOnSelection — the Return/Enter path into the lyric editor
    // -------------------------------------------------------------------------

    /**
     * Every test here drives the real {@link ScoreView#editLyricOnSelection()} against a real
     * selection coordinator and asserts on whether the editor was asked to open. Opening is
     * observed by stubbing {@link LyricEditor} statically: the production method's only
     * observable effect is the call to {@code deselectAndOpenOn}, so verifying that call (or
     * its absence) is what distinguishes "opened on the right element" from "declined".
     * <p>
     * Line layout used throughout: [normal(0), grace(1)+CONNECTED, host(2), rest(3)]
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class EditLyricOnSelection {

        private static final int INDEX_NORMAL = 0;
        private static final int INDEX_GRACE = 1;
        private static final int INDEX_HOST = 2;
        private static final int INDEX_REST = 3;

        private ScoreView scoreView;
        private Line line;
        private LineSelectionState selectionState;

        @BeforeEach
        void setUp() {
            scoreView = new ScoreView(null);

            var song = new Song();
            line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                line.addElement(ElementType.CROTCHET.newInstance());

                var grace = ElementType.GRACE_QUAVER.newInstance();
                grace.setGlissando();
                line.addElement(grace);

                line.addElement(ElementType.CROTCHET.newInstance());
                line.addElement(ElementType.CROTCHET_REST.newInstance());
            });

            selectionState = new LineSelectionState(line);

            var coordinator = scoreView.getSelectionCoordinator();
            coordinator.registerLineState(0, selectionState);
            coordinator.activateLine(0);
        }

        /** Selects exactly one element, the shape the menu command also requires. */
        private void selectSingle(int index) {
            selectionState.setSelectionFromClick(index);
        }

        @Test
        void testOpensOnTheSingleSelectedNote() {
            selectSingle(INDEX_NORMAL);

            try (MockedStatic<LyricEditor> lyricEditor = mockStatic(LyricEditor.class)) {
                scoreView.editLyricOnSelection();

                lyricEditor.verify(
                    () -> LyricEditor.deselectAndOpenOn(scoreView, line, INDEX_NORMAL));
            }
        }

        @Test
        void testOpensOnTheGraceWhenItsHostIsSelected() {
            // The host carries no lyric of its own; the pair's lyric lives on the grace note.
            selectSingle(INDEX_HOST);

            try (MockedStatic<LyricEditor> lyricEditor = mockStatic(LyricEditor.class)) {
                scoreView.editLyricOnSelection();

                lyricEditor.verify(
                    () -> LyricEditor.deselectAndOpenOn(scoreView, line, INDEX_GRACE));
            }
        }

        @Test
        void testOpensOnASelectedRest() {
            // A rest can carry a lyric, matching what the menu command allows.
            selectSingle(INDEX_REST);

            try (MockedStatic<LyricEditor> lyricEditor = mockStatic(LyricEditor.class)) {
                scoreView.editLyricOnSelection();

                lyricEditor.verify(() -> LyricEditor.deselectAndOpenOn(scoreView, line, INDEX_REST));
            }
        }

        @Test
        void testDoesNothingWithNoSelection() {
            // Without the null-selection guard this would dereference a null selection.
            try (MockedStatic<LyricEditor> lyricEditor = mockStatic(LyricEditor.class)) {
                scoreView.editLyricOnSelection();

                lyricEditor.verifyNoInteractions();
            }
        }

        @Test
        void testDoesNothingWithAMultiElementSelection() {
            // The menu command is disabled unless exactly one element is selected; Return
            // must not silently pick one note out of a range the user never singled out.
            selectionState.setSelectionRange(INDEX_NORMAL, INDEX_HOST);

            try (MockedStatic<LyricEditor> lyricEditor = mockStatic(LyricEditor.class)) {
                scoreView.editLyricOnSelection();

                lyricEditor.verifyNoInteractions();
            }
        }

        @Test
        void testDoesNothingWithAWholeLineSelected() {
            // A selected staff line reports itself as a selection spanning every element,
            // so this is the multi-element guard seen through the other gesture that hits it.
            selectionState.setLineSelected(true);

            try (MockedStatic<LyricEditor> lyricEditor = mockStatic(LyricEditor.class)) {
                scoreView.editLyricOnSelection();

                lyricEditor.verifyNoInteractions();
            }
        }

        @Test
        void testDoesNothingWhenTheSelectedElementHasNoLyricTarget() {
            // Without this guard the editor would be asked to open on element -1.
            var barlineIndex = line.elementCount();
            line.getSong().withoutMutationTracking(
                () -> line.addElement(ElementType.SINGLE_BARLINE.newInstance()));
            selectSingle(barlineIndex);

            try (MockedStatic<LyricEditor> lyricEditor = mockStatic(LyricEditor.class)) {
                scoreView.editLyricOnSelection();

                lyricEditor.verifyNoInteractions();
            }
        }

        @Test
        void testDoesNothingDuringPlayback() {
            selectSingle(INDEX_NORMAL);

            try (MockedStatic<PlaybackController> playback = mockStatic(PlaybackController.class);
                 MockedStatic<LyricEditor> lyricEditor = mockStatic(LyricEditor.class)) {

                playback.when(PlaybackController::isPlaying).thenReturn(true);

                scoreView.editLyricOnSelection();

                lyricEditor.verifyNoInteractions();
            }
        }
    }
}
