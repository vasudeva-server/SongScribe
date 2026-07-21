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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.engraving.Staff;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineSpacing;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Unit tests for {@link LineComponent#repaintWithPreviewHeadroom()}.
 * <p>
 * The preview element is painted by {@link StaffPanel} as an overlay, so its ink can land
 * outside the line's own bounds — a plain {@code repaint()} clips the dirty region to those
 * bounds and would leave the ink behind. These tests pin the enlarged dirty rectangle that
 * replaces it.
 */
class LineComponentPreviewHeadroomTest extends UnitTest {

    private static final int LINE_WIDTH_PX = 200;
    private static final int PANEL_WIDTH_PX = 400;
    private static final int PANEL_HEIGHT_PX = 500;

    private static final Font TEST_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

    private static final LyricRenderMetrics LYRIC_RENDER_METRICS =
        new LyricRenderMetrics(TEST_FONT, TEST_FONT, 0.0, 0.0, 0.0);

    /** A {@link StaffPanel} that records the last dirty rectangle it was handed. */
    private static final class RepaintCapturingStaffPanel extends StaffPanel {

        private @Nullable Rectangle lastDirtyRect;

        @Override
        public void repaint(long tm, int x, int y, int width, int height) {
            lastDirtyRect = new Rectangle(x, y, width, height);
            super.repaint(tm, x, y, width, height);
        }
    }

    private static int ceilViewPx(double valueSs) {
        return (int) Math.ceil(valueSs * ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE);
    }

    /** A single-line panel, laid out, whose line carries a known layout result. */
    private static RepaintCapturingStaffPanel laidOutPanel() {
        var panel = new RepaintCapturingStaffPanel();
        panel.setSong(new Song());

        var scoreView = mock(ScoreView.class);
        when(scoreView.getLyricRenderMetrics()).thenReturn(LYRIC_RENDER_METRICS);
        when(scoreView.getViewScale()).thenReturn(ViewScale.IDENTITY);
        when(scoreView.getSelectionCoordinator()).thenReturn(mock(SelectionCoordinator.class));

        var linePanel = panel.getLinePanels().getFirst();
        linePanel.setPreferredSize(new Dimension(LINE_WIDTH_PX, 0));

        var lineComponent = linePanel.getLineComponent();
        lineComponent.setScoreView(scoreView);
        lineComponent.layoutResult = LayoutResult.builder()
            .setContentAboveStaffSs(Staff.MIN_ABOVE_STAFF_SS)
            .setContentBelowStaffSs(Staff.MIN_BELOW_STAFF_SS)
            .build();
        lineComponent.layoutDirty = false;

        panel.setSize(PANEL_WIDTH_PX, PANEL_HEIGHT_PX);
        panel.doLayout();
        linePanel.doLayout();

        // The panel's own repaint bookkeeping during layout is not what these tests measure.
        panel.lastDirtyRect = null;
        return panel;
    }

    /**
     * The dirty rectangle covers the line's own bounds grown by the full preview headroom on
     * both sides — the staff-position range the preview may occupy, plus the ink margin for
     * glyph parts (an accidental in particular) that reach past the notehead centre.
     */
    @Test
    void testDirtyRectGrowsByThePreviewHeadroomOnBothSides() {
        var panel = laidOutPanel();
        var linePanel = panel.getLinePanels().getFirst();
        var lineComponent = linePanel.getLineComponent();

        lineComponent.repaintWithPreviewHeadroom();

        var dirtyRect = panel.lastDirtyRect;

        if (dirtyRect == null) {
            throw new AssertionError("repaintWithPreviewHeadroom did not repaint the staff panel");
        }

        var headroomAbovePx =
            ceilViewPx(Staff.MIN_ABOVE_STAFF_SS + LineSpacing.PREVIEW_REPAINT_MARGIN_SS);
        var headroomBelowPx =
            ceilViewPx(Staff.MIN_BELOW_STAFF_SS + LineSpacing.PREVIEW_REPAINT_MARGIN_SS);

        assertThat(dirtyRect.y)
            .as("dirty rect starts one above-headroom above the line's top edge")
            .isEqualTo(linePanel.getY() - headroomAbovePx);

        assertThat(dirtyRect.height)
            .as("and is tall enough to cover the line plus both headrooms")
            .isEqualTo(lineComponent.getHeight() + headroomAbovePx + headroomBelowPx);

        assertThat(dirtyRect.height)
            .as("the enlarged rect must exceed the line's own bounds, or it clips like repaint()")
            .isGreaterThan(lineComponent.getHeight());
    }

    /**
     * A line with no {@link StaffPanel} ancestor has no overlay host, so nothing can have been
     * drawn outside its bounds. It falls back to a plain repaint rather than walking off the
     * end of the hierarchy.
     */
    @Test
    void testDetachedLineComponentFallsBackToPlainRepaint() {
        var lineComponent = new LineComponent();

        assertThat(lineComponent.getParent())
            .as("fixture precondition: the component must be detached")
            .isNull();

        assertThatCode(lineComponent::repaintWithPreviewHeadroom)
            .as("a detached line must not throw when asked to repaint its headroom")
            .doesNotThrowAnyException();
    }
}
