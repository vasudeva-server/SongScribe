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
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link LineOverlayComponent}'s base-class behavior: the visibility guards in
 * {@link LineOverlayComponent#updateBounds()} (T3), the staff-space-to-view-pixel conversion
 * (T4), and the hint/scale/translate transform {@link LineOverlayComponent#paintComponent}
 * establishes before handing off to {@code renderOverlay} (T5).
 * <p>
 * Uses a minimal {@link OverlayHost} stand-in and a bare {@link LineComponent} target rather
 * than a full {@code ScoreView}/{@code MainPanel}/{@code StaffPanel} hierarchy: none of this
 * class's behavior depends on that hierarchy, only on the {@link OverlayHost} contract and the
 * target's position and zoomed scale within it. {@code ScoreViewOverlayHostingTest} covers the
 * hosting contract itself (registration, z-order, relayout).
 */
class LineOverlayComponentTest extends UnitTest {

    /**
     * A concrete overlay whose ink bounds are set directly by the test and whose
     * {@code renderOverlay} captures the hints and transform it was called with, for T5.
     */
    private static final class TestOverlay extends LineOverlayComponent {

        @Nullable Rectangle2D inkBoundsSs;
        @Nullable RenderingHints capturedHints;
        @Nullable AffineTransform capturedTransform;

        TestOverlay(OverlayHost host) {
            super(host);
        }

        @Override
        protected @Nullable Rectangle2D getInkBoundsSs() {
            return inkBoundsSs;
        }

        @Override
        protected void renderOverlay(Graphics2D g2) {
            capturedHints = g2.getRenderingHints();
            capturedTransform = g2.getTransform();
        }
    }

    private FakeOverlayHost host;
    private TestOverlay overlay;

    @BeforeEach
    void setUp() {
        host = new FakeOverlayHost();
        overlay = new TestOverlay(host);
        host.addOverlay(overlay);
    }

    /**
     * Creates a real {@link LineComponent}, attaches it as a direct child of {@code host} at
     * {@code (originX, originY)}, and gives it a {@link ScoreView} mock whose {@link ViewScale}
     * carries {@code zoomPercent} — so {@link LineComponent#getViewPixelsPerStaffSpace()}
     * reflects a real, controllable zoom.
     */
    private LineComponent attachedLine(int originX, int originY, int zoomPercent) {
        final var lineWidthPx = 500;
        final var lineHeightPx = 100;

        var viewScale = new ViewScale();
        viewScale.setZoomPercent(zoomPercent);
        var scoreViewMock = mock(ScoreView.class);
        when(scoreViewMock.getViewScale()).thenReturn(viewScale);

        var line = new LineComponent();
        line.setScoreView(scoreViewMock);
        host.add(line);
        line.setBounds(originX, originY, lineWidthPx, lineHeightPx);

        return line;
    }

    @Nested
    class VisibilityGuards {

        @Test
        void testUpdateBoundsHidesAndReturnsWhenTargetIsNull() {
            // No target has ever been set; force the stale-visible state updateBounds()
            // is supposed to correct.
            overlay.setVisible(true);

            overlay.updateBounds();

            assertThat(overlay.isVisible())
                .as("updateBounds() must hide the overlay when it has no target line")
                .isFalse();
        }

        @Test
        void testUpdateBoundsHidesAndReturnsWhenTargetDoesNotDescendFromHost() {
            var detachedLine = new LineComponent();
            // Never added to host or anywhere else.
            overlay.inkBoundsSs = new Rectangle2D.Double(0, 0, 1, 1);
            overlay.setVisible(true);

            overlay.setTargetLine(detachedLine);

            assertThat(overlay.isVisible())
                .as("updateBounds() must hide the overlay when its target is not in the host's hierarchy")
                .isFalse();
        }

        @Test
        void testUpdateBoundsHidesAndReturnsWhenInkBoundsIsNull() {
            var line = attachedLine(50, 50, 100);
            overlay.inkBoundsSs = null;
            overlay.setVisible(true);

            overlay.setTargetLine(line);

            assertThat(overlay.isVisible())
                .as("updateBounds() must hide the overlay when getInkBoundsSs() returns null")
                .isFalse();
        }
    }

    @Nested
    class SsToPixelConversion {

        /**
         * A zoom far from 100% (so a formula that silently ignores zoom cannot pass as
         * rounding error) and a fractional ink rectangle (so floor/ceil actually do
         * something, rather than landing on whole pixels by coincidence).
         */
        @Test
        void testBoundsConvertToExpectedViewPixelRectangleAtNonDefaultZoom() {
            final var zoomPercent = 250;
            final var originX = 101;
            final var originY = 53;
            final var minXSs = 0.037;
            final var minYSs = -1.263;
            final var maxXSs = 2.481;
            final var maxYSs = 1.777;
            // The 1-device-pixel pad LineOverlayComponent applies on every side, documented on
            // its INK_PAD_PX field. Exempt from the "no magic numbers" rule (value 1).
            final var inkPadPx = 1;

            var line = attachedLine(originX, originY, zoomPercent);
            overlay.inkBoundsSs = new Rectangle2D.Double(minXSs, minYSs, maxXSs - minXSs, maxYSs - minYSs);

            overlay.setTargetLine(line);

            var scale = line.getViewPixelsPerStaffSpace();
            assertThat(scale)
                .as("precondition: the 250%% zoom must actually change the scale from 100%%")
                .isNotEqualTo(ScaleContext.getPixelsPerStaffSpace());

            var rawLeft = (int) Math.floor(originX + minXSs * scale);
            var rawTop = (int) Math.floor(originY + minYSs * scale);
            var rawRight = (int) Math.ceil(originX + maxXSs * scale);
            var rawBottom = (int) Math.ceil(originY + maxYSs * scale);

            var expectedBounds = new Rectangle(
                rawLeft - inkPadPx,
                rawTop - inkPadPx,
                (rawRight + inkPadPx) - (rawLeft - inkPadPx),
                (rawBottom + inkPadPx) - (rawTop - inkPadPx)
            );

            assertThat(overlay.getBounds())
                .as("bounds = ink rect converted to view pixels via the target's origin and zoomed scale, padded by 1px")
                .isEqualTo(expectedBounds);

            // The pad must land on all four sides individually, not just net out in area.
            assertThat(overlay.getX()).isEqualTo(rawLeft - inkPadPx);
            assertThat(overlay.getY()).isEqualTo(rawTop - inkPadPx);
            assertThat(overlay.getX() + overlay.getWidth()).isEqualTo(rawRight + inkPadPx);
            assertThat(overlay.getY() + overlay.getHeight()).isEqualTo(rawBottom + inkPadPx);
        }
    }

    @Nested
    class PaintComponentTransform {

        @Test
        void testPaintComponentAppliesHintsThenComposesTranslateAndScale() {
            final var zoomPercent = 200;
            final var originX = 40;
            final var originY = 30;

            var line = attachedLine(originX, originY, zoomPercent);
            overlay.inkBoundsSs = new Rectangle2D.Double(-1, -1, 2, 2);
            overlay.setTargetLine(line);
            assertThat(overlay.isVisible()).as("precondition: overlay must be showing").isTrue();

            var image = new BufferedImage(
                Math.max(overlay.getWidth(), 1), Math.max(overlay.getHeight(), 1), BufferedImage.TYPE_INT_ARGB);
            var g = image.getGraphics();

            try {
                overlay.paintComponent(g);
            } finally {
                g.dispose();
            }

            var hints = overlay.capturedHints;

            assertThat(hints).as("renderOverlay must receive rendering hints").isNotNull();

            assertThat(hints.get(RenderingHints.KEY_ANTIALIASING))
                .isEqualTo(RenderingHints.VALUE_ANTIALIAS_ON);
            assertThat(hints.get(RenderingHints.KEY_TEXT_ANTIALIASING))
                .isEqualTo(RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            assertThat(hints.get(RenderingHints.KEY_STROKE_CONTROL))
                .isEqualTo(RenderingHints.VALUE_STROKE_NORMALIZE);
            assertThat(hints.get(RenderingHints.KEY_FRACTIONALMETRICS))
                .isEqualTo(RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            var transform = overlay.capturedTransform;

            assertThat(transform).as("renderOverlay must receive a transform").isNotNull();

            var lineOriginPx = SwingUtilities.convertPoint(line, 0, 0, host);
            var expectedTx = lineOriginPx.x - overlay.getX();
            var expectedTy = lineOriginPx.y - overlay.getY();
            var expectedScale = line.getViewPixelsPerStaffSpace();

            var epsilon = offset(1e-9);
            var mappedOrigin = transform.transform(new Point2D.Double(0, 0), null);
            assertThat(mappedOrigin.getX())
                .as("line-space (0,0) must map to this component's own pixel origin for the target line")
                .isEqualTo(expectedTx, epsilon);
            assertThat(mappedOrigin.getY()).isEqualTo(expectedTy, epsilon);

            var mappedUnit = transform.transform(new Point2D.Double(1, 1), null);
            assertThat(mappedUnit.getX())
                .as("one staff space in line-space must map to the zoomed view-pixel scale")
                .isEqualTo(expectedTx + expectedScale, epsilon);
            assertThat(mappedUnit.getY()).isEqualTo(expectedTy + expectedScale, epsilon);
        }
    }
}
