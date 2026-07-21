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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.PasteModeManager;

/**
 * Unit tests for the paste-mode half of
 * {@link LineOverlayPainter#paintOverlays(Graphics2D, ScoreView)}.
 * <p>
 * The insertion marker spans every staff position a note could occupy, which is wider than the
 * bounds a line is laid out to, so like the hover preview it is painted by the page. Paste mode
 * suppresses the preview element, so the marker must be driven by {@link PasteModeManager}'s
 * target line and not by the preview line — keying it off the preview is what left the marker
 * unpainted for the whole of paste mode.
 */
class LineOverlayPainterPasteOverlayTest extends UnitTest {

    private static final int HOST_WIDTH_PX = 400;
    private static final int HOST_HEIGHT_PX = 300;

    private static final int LINE_X_PX = 17;
    private static final int LINE_Y_PX = 43;
    private static final int LINE_WIDTH_PX = 200;
    private static final int LINE_HEIGHT_PX = 60;

    /** A zoom far enough from 100% that ignoring it cannot pass as rounding. */
    private static final int ZOOM_PERCENT = 200;
    private static final double ZOOM_FACTOR = 2.0;

    private static final double TRANSFORM_TOLERANCE = 1.0e-9;

    /**
     * A line that records the transform its insertion-marker overlay is handed instead of
     * rendering, so the placement can be inspected without a real layout or glyph font.
     */
    private static final class TransformCapturingLine extends LineComponent {

        private @Nullable AffineTransform capturedTransform;

        @Override
        void renderInsertionPointOverlay(Graphics2D g2) {
            capturedTransform = g2.getTransform();
        }
    }

    @AfterEach
    void clearPreviewLine() {
        // The preview line is static, so it would otherwise leak into every later test.
        PreviewElementManager.setCurrentPreviewLine(null);
    }

    private static Graphics2D scratchGraphics() {
        var image = new BufferedImage(HOST_WIDTH_PX, HOST_HEIGHT_PX, BufferedImage.TYPE_INT_ARGB);
        return image.createGraphics();
    }

    /** A host sized like a page, with {@code line} placed at a known non-zero offset. */
    private static ScoreView hostContaining(LineComponent line) {
        var scoreView = new ScoreView(null);
        scoreView.setLayout(null);
        scoreView.setSize(HOST_WIDTH_PX, HOST_HEIGHT_PX);
        scoreView.getViewScale().setZoomPercent(ZOOM_PERCENT);

        line.setScoreView(scoreView);
        line.setBounds(LINE_X_PX, LINE_Y_PX, LINE_WIDTH_PX, LINE_HEIGHT_PX);
        scoreView.add(line);

        return scoreView;
    }

    /**
     * Paste mode clears the preview element, so the marker has to reach the page overlay pass
     * with no preview line set at all.
     */
    @Test
    void testInsertionMarkerIsPaintedForThePasteTargetWithNoPreviewLine() {
        var line = new TransformCapturingLine();
        var host = hostContaining(line);
        PreviewElementManager.setCurrentPreviewLine(null);

        var pasteModeManager = mock(PasteModeManager.class);
        when(pasteModeManager.getTargetLineComponent()).thenReturn(line);

        var graphics = scratchGraphics();

        try (var pasteModeMock = mockStatic(PasteModeManager.class)) {
            pasteModeMock.when(PasteModeManager::getActiveInstance).thenReturn(pasteModeManager);
            LineOverlayPainter.paintOverlays(graphics, host);
        } finally {
            graphics.dispose();
        }

        var transform = line.capturedTransform;

        if (transform == null) {
            throw new AssertionError("paintOverlays did not invoke the insertion-marker renderer");
        }

        var expectedScale = ScaleContext.getPixelsPerStaffSpace() * ZOOM_FACTOR;

        assertThat(transform.getTranslateX())
            .as("translated to the line's x origin within the host")
            .isEqualTo((double) LINE_X_PX, within(TRANSFORM_TOLERANCE));

        assertThat(transform.getTranslateY())
            .as("translated to the line's y origin within the host")
            .isEqualTo((double) LINE_Y_PX, within(TRANSFORM_TOLERANCE));

        assertThat(transform.getScaleX())
            .as("scaled to staff spaces at the view zoom, so the renderer can draw in Ss")
            .isEqualTo(expectedScale, within(TRANSFORM_TOLERANCE));

        assertThat(transform.getScaleY())
            .as("uniformly — a non-square scale would shear the marker")
            .isEqualTo(expectedScale, within(TRANSFORM_TOLERANCE));
    }

    /**
     * The pointer can sit between lines while paste mode is active, which drops the target and
     * must simply leave the page unmarked.
     */
    @Test
    void testNoTargetLinePaintsNoMarker() {
        var line = new TransformCapturingLine();
        var host = hostContaining(line);

        var pasteModeManager = mock(PasteModeManager.class);
        when(pasteModeManager.getTargetLineComponent()).thenReturn(null);

        var graphics = scratchGraphics();

        try (var pasteModeMock = mockStatic(PasteModeManager.class)) {
            pasteModeMock.when(PasteModeManager::getActiveInstance).thenReturn(pasteModeManager);
            LineOverlayPainter.paintOverlays(graphics, host);
        } finally {
            graphics.dispose();
        }

        assertThat(line.capturedTransform)
            .as("no tracked insertion point means no marker")
            .isNull();
    }

    /**
     * The overlay pass runs on every page repaint, so an inactive paste mode is the common
     * case rather than an edge case.
     */
    @Test
    void testInactivePasteModePaintsNothing() {
        var line = new TransformCapturingLine();
        var host = hostContaining(line);
        PreviewElementManager.setCurrentPreviewLine(null);

        var graphics = scratchGraphics();

        try (var pasteModeMock = mockStatic(PasteModeManager.class)) {
            pasteModeMock.when(PasteModeManager::getActiveInstance).thenReturn(null);
            LineOverlayPainter.paintOverlays(graphics, host);
        } finally {
            graphics.dispose();
        }

        assertThat(line.capturedTransform)
            .as("an idle overlay pass must not paint a marker on any line")
            .isNull();
    }
}
