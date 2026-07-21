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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link PreviewElementManager#paintOverlay(Graphics2D, ScoreView)}.
 * <p>
 * The hover preview is painted by the page rather than by the line that owns it, so this
 * method is responsible for placing the renderer in the line's coordinate space itself: it
 * must translate to the line's origin <em>within the host</em> and apply the view zoom. It is
 * also the sole guard against painting a {@link LineComponent} left over from a previous
 * {@code rebuildLayout()}, whose origin in the host is meaningless.
 */
class PreviewElementManagerPaintOverlayTest extends UnitTest {

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
     * A line that records the transform it is handed instead of rendering, so the placement
     * {@code paintOverlay} performs can be inspected without a real layout or glyph font.
     */
    private static final class TransformCapturingLine extends LineComponent {

        private @Nullable AffineTransform capturedTransform;

        @Override
        void renderPreviewOverlay(Graphics2D g2) {
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
     * The renderer is invoked in the line's own coordinate space: translated to where the line
     * sits in the host, and scaled so it can draw in staff spaces at the current zoom. A
     * regression in either half puts the preview at the wrong place or the wrong size.
     */
    @Test
    void testOverlayIsRenderedAtTheLineOriginScaledForTheViewZoom() {
        var line = new TransformCapturingLine();
        hostContaining(line);
        PreviewElementManager.setCurrentPreviewLine(line);

        var graphics = scratchGraphics();

        try {
            PreviewElementManager.paintOverlay(graphics, (ScoreView) line.getParent());
        } finally {
            graphics.dispose();
        }

        var transform = line.capturedTransform;

        if (transform == null) {
            throw new AssertionError("paintOverlay did not invoke the preview renderer");
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
            .as("uniformly — a non-square scale would shear every glyph")
            .isEqualTo(expectedScale, within(TRANSFORM_TOLERANCE));
    }

    /**
     * A preview line left over from a previous {@code rebuildLayout()} is no longer in the
     * host's hierarchy, so its origin there is meaningless. Painting it anyway would put the
     * preview at an arbitrary position derived from an unrelated tree.
     */
    @Test
    void testStaleLineOutsideTheHostIsNotPainted() {
        var line = new TransformCapturingLine();

        // Attached, but to something other than the host that is painting.
        var otherParent = new JPanel(null);
        line.setBounds(LINE_X_PX, LINE_Y_PX, LINE_WIDTH_PX, LINE_HEIGHT_PX);
        otherParent.add(line);

        var host = hostContaining(new LineComponent());
        PreviewElementManager.setCurrentPreviewLine(line);

        var graphics = scratchGraphics();

        try {
            PreviewElementManager.paintOverlay(graphics, host);
        } finally {
            graphics.dispose();
        }

        assertThat(line.capturedTransform)
            .as("a line outside the host must not be painted into it")
            .isNull();
    }

    /**
     * With no preview active the overlay pass runs on every page repaint, so the null guard is
     * the common case rather than an edge case.
     */
    @Test
    void testNoPreviewLinePaintsNothing() {
        var host = hostContaining(new LineComponent());
        PreviewElementManager.setCurrentPreviewLine(null);

        var graphics = scratchGraphics();

        try {
            assertThatCode(() -> PreviewElementManager.paintOverlay(graphics, host))
                .as("an idle repaint must not fail for want of a preview element")
                .doesNotThrowAnyException();
        } finally {
            graphics.dispose();
        }
    }
}
