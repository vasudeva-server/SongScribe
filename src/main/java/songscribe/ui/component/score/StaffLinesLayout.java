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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager2;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Ss;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineSpacing;

/**
 * Lays out the {@link LinePanel} children of a {@link StaffPanel} so that the distance
 * between adjacent staff midlines is uniform down the whole song, while each line is
 * sized to its own content. The distance is the worst adjacent pair, so adjacent lines'
 * <em>content</em> is always at least the inter-line gap apart, and content above the first
 * line's staff translates the whole block without widening the spacing.
 * <p>
 * Component <em>bounds</em> are floored at the minimum staff surround
 * ({@link LineSpacing#MIN_ABOVE_MIDLINE_SS} / {@link LineSpacing#MIN_BELOW_MIDLINE_SS}) and
 * those floors are excluded from the spacing. Two sparse neighbours can therefore have
 * overlapping bounds though their ink does not overlap, which is why
 * {@link StaffPanel#isOptimizedDrawingEnabled} reports {@code false}.
 */
public class StaffLinesLayout implements LayoutManager2 {

    private final StaffPanel staffPanel;

    public StaffLinesLayout(StaffPanel staffPanel) {
        this.staffPanel = staffPanel;
    }

    /**
     * {@code aboveMidlineSs} / {@code belowMidlineSs} are the measured content and the only
     * inputs to {@code spacingSs}; {@code paintAboveMidlineSs} / {@code paintBelowMidlineSs} are
     * the same values floored at the minimum staff surround, used only for component bounds, so
     * Swing does not clip the top staff line. The floors never reach {@code spacingSs}.
     */
    private record Geometry(
        double[] aboveMidlineSs,
        double[] belowMidlineSs,
        double[] paintAboveMidlineSs,
        double[] paintBelowMidlineSs,
        double spacingSs
    ) {

        int count() {
            return aboveMidlineSs.length;
        }

        double midlineYSs(int index) {
            // Anchored on the painted extent so the first line's headroom cannot fall above
            // y = 0, where the panel edge would clip it.
            return paintAboveMidlineSs[0] + index * spacingSs;
        }

        Ss topYSs(int index) {
            return new Ss(midlineYSs(index) - paintAboveMidlineSs[index]);
        }

        Ss heightSs(int index) {
            return new Ss(paintAboveMidlineSs[index] + paintBelowMidlineSs[index]);
        }

        Ss totalHeightSs() {
            var last = count() - 1;
            return new Ss(midlineYSs(last) + paintBelowMidlineSs[last]);
        }
    }

    /**
     * Null when there is nothing to lay out. Always re-runs the line layouts first: a live drag
     * moves elements without firing a mutation, so there is no dirty signal.
     */
    private @Nullable Geometry computeGeometry(Container parent) {
        var count = parent.getComponentCount();

        if (count == 0) {
            return null;
        }

        staffPanel.ensureAllLineLayouts();

        var lyricRenderMetrics = staffPanel.lyricRenderMetrics();
        var aboveMidlineSs = new double[count];
        var belowMidlineSs = new double[count];

        var paintAboveMidlineSs = new double[count];
        var paintBelowMidlineSs = new double[count];

        for (var i = 0; i < count; i++) {
            var result = layoutResultOf(parent.getComponent(i));

            if (result == null) {
                // Not a line panel, or a line whose layout pass never ran; with no layout to
                // measure, reserve the minimum staff surround so it still stacks sensibly.
                aboveMidlineSs[i] = LineSpacing.MIN_ABOVE_MIDLINE_SS;
                belowMidlineSs[i] = LineSpacing.MIN_BELOW_MIDLINE_SS;
                paintAboveMidlineSs[i] = LineSpacing.MIN_ABOVE_MIDLINE_SS;
                paintBelowMidlineSs[i] = LineSpacing.MIN_BELOW_MIDLINE_SS;
            } else {
                aboveMidlineSs[i] = result.aboveMidlineSs();
                belowMidlineSs[i] = result.belowMidlineSs(lyricRenderMetrics);
                // Floored by LayoutResult, so LineComponent's own sizing and midline placement
                // resolve to the same numbers this layout positions against.
                paintAboveMidlineSs[i] = result.paintAboveMidlineSs();
                paintBelowMidlineSs[i] = result.paintBelowMidlineSs(lyricRenderMetrics);
            }
        }

        // With a single child there are no pairs, so the spacing stays 0 and it sits at y = 0.
        var spacingSs = 0.0;
        var interLineGapSs = LineSpacing.interLineGapSs(LineSpacing.DEFAULT_INTER_LINE_GAP_SS);

        for (var i = 0; i < count - 1; i++) {
            var pairSs = belowMidlineSs[i] + interLineGapSs + aboveMidlineSs[i + 1];
            spacingSs = Math.max(spacingSs, pairSs);
        }

        return new Geometry(
            aboveMidlineSs, belowMidlineSs, paintAboveMidlineSs, paintBelowMidlineSs, spacingSs);
    }

    private @Nullable LayoutResult layoutResultOf(Component child) {
        if (child instanceof LinePanel linePanel) {
            return linePanel.getLineComponent().getLayoutResult();
        }

        return null;
    }

    @Override
    public void layoutContainer(Container parent) {
        synchronized (parent.getTreeLock()) {
            var geometry = computeGeometry(parent);

            if (geometry == null) {
                return;
            }

            var viewScale = staffPanel.viewScale();
            var insets = parent.getInsets();
            var width = parent.getWidth() - insets.left - insets.right;

            for (var i = 0; i < geometry.count(); i++) {
                // Positions round to nearest so lines stay centered on their midline;
                // sizes round up so no content is clipped at the component edge.
                var y = viewScale.toViewPx(geometry.topYSs(i)).positionPx();
                var height = viewScale.toViewPx(geometry.heightSs(i)).sizePx();
                parent.getComponent(i).setBounds(insets.left, insets.top + y, width, height);
            }
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        synchronized (parent.getTreeLock()) {
            var geometry = computeGeometry(parent);

            if (geometry == null) {
                return new Dimension(0, 0);
            }

            var width = 0;

            for (var i = 0; i < geometry.count(); i++) {
                width = Math.max(width, parent.getComponent(i).getPreferredSize().width);
            }

            var insets = parent.getInsets();
            var height = staffPanel.viewScale().toViewPx(geometry.totalHeightSs()).sizePx();

            return new Dimension(
                width + insets.left + insets.right,
                height + insets.top + insets.bottom
            );
        }
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public Dimension maximumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    @Override
    public void addLayoutComponent(Component component, Object constraints) {
        // No per-child constraints: a child's geometry comes entirely from its LayoutResult.
    }

    @Override
    public void addLayoutComponent(String name, Component component) {
        // No per-child constraints: a child's geometry comes entirely from its LayoutResult.
    }

    @Override
    public void removeLayoutComponent(Component component) {
        // Nothing to forget: no per-child state is kept.
    }

    @Override
    public float getLayoutAlignmentX(Container parent) {
        return 0.0f;
    }

    @Override
    public float getLayoutAlignmentY(Container parent) {
        return 0.0f;
    }

    @Override
    public void invalidateLayout(Container parent) {
        // Nothing to discard: every measurement is recomputed from the line layouts on
        // each pass, so there is no cached state that could go stale.
    }
}
