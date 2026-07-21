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

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.engraving.Staff;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineSpacing;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for {@link LineComponent#repaintWithOverlayHeadroom()}.
 * <p>
 * The preview element is painted by {@link ScoreView} as an overlay, so its ink can land
 * outside the line's own bounds — a plain {@code repaint()} clips the dirty region to those
 * bounds and would leave the ink behind. These tests pin the enlarged dirty rectangle that
 * replaces it, and that it is registered against the {@code ScoreView} rather than a nearer
 * ancestor, whose bounds would clip the region back off — plus the {@code ScoreView} opt-out
 * from optimized drawing that keeps the overlay pass running at all.
 */
class LineComponentPreviewHeadroomTest extends UnitTest {

    private static final int VIEW_WIDTH_PX = 400;
    private static final int VIEW_HEIGHT_PX = 500;

    private static final int DETACHED_LINE_WIDTH_PX = 200;
    private static final int DETACHED_LINE_HEIGHT_PX = 60;

    private static final Font TEST_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    /**
     * Records the dirty regions handed to one target component. {@link ScoreView} is final, so
     * the region cannot be captured by overriding {@code repaint} on a subclass.
     */
    private static final class RepaintRecorder extends RepaintManager {

        private final JComponent target;
        private @Nullable Rectangle lastDirtyRect;

        private RepaintRecorder(JComponent target) {
            this.target = target;
        }

        @Override
        public void addDirtyRegion(JComponent component, int x, int y, int width, int height) {
            if (component == target) {
                // Recorded before super, which clips the region to the component's bounds.
                lastDirtyRect = new Rectangle(x, y, width, height);
            }

            super.addDirtyRegion(component, x, y, width, height);
        }
    }

    private static int ceilViewPx(double valueSs) {
        return (int) Math.ceil(valueSs * ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    /** A laid-out score view paired with the line under test. */
    private record Fixture(ScoreView scoreView, LineComponent lineComponent) {}

    /** A laid-out score view whose single line carries a known layout result. */
    private static Fixture laidOutScoreView() {
        // The hierarchy is assembled directly rather than through ScoreView.init(), which
        // installs interactive-only machinery (a macOS pinch-zoom gesture in particular) that
        // is unavailable to a unit test. This mirrors what initMainPanel wires up.
        var scoreView = new ScoreView(null);
        scoreView.setLayout(new BorderLayout());
        scoreView.setSize(VIEW_WIDTH_PX, VIEW_HEIGHT_PX);
        scoreView.setLyricRenderMetrics(LyricRenderMetrics.forFont(TEST_FONT));

        var mainPanel = new MainPanel();
        mainPanel.setScoreView(scoreView);
        mainPanel.setSong(new Song());
        scoreView.add(mainPanel, BorderLayout.CENTER);

        var staffPanel = mainPanel.getStaffPanel();

        // Every line is measured during layout, so all of them need their ScoreView.
        for (var panel : staffPanel.getLinePanels()) {
            panel.getLineComponent().setScoreView(scoreView);
        }

        var linePanel = staffPanel.getLinePanels().getFirst();
        var lineComponent = linePanel.getLineComponent();
        lineComponent.layoutResult = LayoutResult.builder()
            .setContentAboveStaffSs(Staff.MIN_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(Staff.MIN_BELOW_STAFF_SS)
            .build();
        lineComponent.layoutDirty = false;

        // Lay the whole page out so the line's origin in view coordinates is real.
        scoreView.doLayout();
        mainPanel.doLayout();
        staffPanel.doLayout();
        linePanel.doLayout();

        return new Fixture(scoreView, lineComponent);
    }

    /**
     * The dirty rectangle covers the line's own bounds grown by the full preview headroom on
     * both sides — the staff-position range the preview may occupy, plus the ink margin for
     * glyph parts (an accidental in particular) that reach past the notehead centre — and is
     * registered against the score view, the component that paints the overlay.
     */
    @Test
    void testDirtyRectGrowsByThePreviewHeadroomOnBothSides() {
        var fixture = laidOutScoreView();
        var scoreView = fixture.scoreView();
        var lineComponent = fixture.lineComponent();

        var recorder = new RepaintRecorder(scoreView);
        var previousManager = RepaintManager.currentManager(scoreView);
        RepaintManager.setCurrentManager(recorder);

        try {
            lineComponent.repaintWithOverlayHeadroom();
        } finally {
            RepaintManager.setCurrentManager(previousManager);
        }

        var dirtyRect = recorder.lastDirtyRect;

        if (dirtyRect == null) {
            throw new AssertionError("repaintWithOverlayHeadroom did not repaint the score view");
        }

        var lineTopInView = SwingUtilities
            .convertPoint(lineComponent, 0, 0, scoreView).y;
        var headroomAbovePx =
            ceilViewPx(Staff.MIN_ABOVE_STAFF_SS + LineSpacing.PREVIEW_REPAINT_MARGIN_SS);
        var headroomBelowPx =
            ceilViewPx(Staff.MIN_BELOW_STAFF_SS + LineSpacing.PREVIEW_REPAINT_MARGIN_SS);

        assertThat(dirtyRect.y)
            .as("dirty rect starts one above-headroom above the line's top edge")
            .isEqualTo(lineTopInView - headroomAbovePx);

        assertThat(dirtyRect.height)
            .as("and is tall enough to cover the line plus both headrooms")
            .isEqualTo(lineComponent.getHeight() + headroomAbovePx + headroomBelowPx);

        assertThat(dirtyRect.height)
            .as("the enlarged rect must exceed the line's own bounds, or it clips like repaint()")
            .isGreaterThan(lineComponent.getHeight());
    }

    /**
     * A line with no {@link ScoreView} ancestor has no overlay host, so nothing can have been
     * drawn outside its bounds. It falls back to a plain repaint rather than walking off the
     * end of the hierarchy.
     */
    @Test
    void testDetachedLineComponentFallsBackToPlainRepaint() {
        var lineComponent = new LineComponent();
        lineComponent.setSize(DETACHED_LINE_WIDTH_PX, DETACHED_LINE_HEIGHT_PX);

        assertThat(lineComponent.getParent())
            .as("fixture precondition: the component must be detached")
            .isNull();

        var recorder = new RepaintRecorder(lineComponent);
        var previousManager = RepaintManager.currentManager(lineComponent);
        RepaintManager.setCurrentManager(recorder);

        try {
            assertThatCode(lineComponent::repaintWithOverlayHeadroom)
                .as("a detached line must not throw when asked to repaint its headroom")
                .doesNotThrowAnyException();
        } finally {
            RepaintManager.setCurrentManager(previousManager);
        }

        // Falling back must still repaint — a branch that silently did nothing would leave
        // the line stale, and would pass a no-throw assertion on its own.
        assertThat(recorder.lastDirtyRect)
            .as("the fallback repaints the line's own bounds, ungrown")
            .isEqualTo(new Rectangle(0, 0, DETACHED_LINE_WIDTH_PX, DETACHED_LINE_HEIGHT_PX));
    }

    /**
     * Without this, Swing may resolve a repaint to a descendant and paint it directly, never
     * running {@code ScoreView.paintChildren} — which is what draws the preview on top of the
     * children — so the overlay would be erased by any unrelated repaint beneath it.
     */
    @Test
    void testScoreViewIsNotOptimizedForDrawingSoTheOverlayPassAlwaysRuns() {
        assertThat(new ScoreView(null).isOptimizedDrawingEnabled())
            .as("the score view must remain the paint root for its subtree")
            .isFalse();
    }
}
