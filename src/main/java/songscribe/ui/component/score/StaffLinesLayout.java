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

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Ss;
import songscribe.engraving.Staff;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineSpacing;
import songscribe.layout.LyricRenderMetrics;

/**
 * Lays out the {@link LinePanel} children of a {@link StaffPanel} so that the distance
 * between adjacent staff midlines is uniform down the whole song, while each line is
 * sized to its own content.
 * <p>
 * Each line reports how far it reaches above and below its staff midline. The uniform
 * midline-to-midline distance is the worst adjacent pair:
 * <pre>
 * S = max over N in [0, count-2] of
 *         ( belowMidline[N] + MIN_INTER_LINE_GAP_SS + aboveMidline[N+1] )
 *
 * midlineY[0] = aboveMidline[0]
 * midlineY[N] = midlineY[0] + N * S
 * </pre>
 * Two consequences follow, and both are intentional:
 * <ul>
 *   <li>{@code aboveMidline[0]} appears only in {@code midlineY[0]}, never in a pair, so
 *       content above the <em>first</em> line's staff translates the whole block downward
 *       without widening the spacing. Content below it does enter pair (0,1) and widens it.</li>
 *   <li>Because {@code S >= belowMidline[N] + gap + aboveMidline[N+1]} for every pair,
 *       adjacent components are always at least {@link LineSpacing#MIN_INTER_LINE_GAP_SS}
 *       apart and never overlap. Swing's default child clipping is therefore harmless and
 *       must not be worked around.</li>
 * </ul>
 * This class is the seam where staff spaces become view pixels: everything above is computed
 * in {@code Ss}, then converted once per child through the view zoom.
 */
public class StaffLinesLayout implements LayoutManager2 {

    private final StaffPanel staffPanel;

    public StaffLinesLayout(StaffPanel staffPanel) {
        this.staffPanel = staffPanel;
    }

    /**
     * The midline geometry of every child, in staff spaces, plus the uniform
     * midline-to-midline distance derived from it.
     */
    private record Geometry(double[] aboveMidlineSs, double[] belowMidlineSs, double spacingSs) {

        int count() {
            return aboveMidlineSs.length;
        }

        double midlineYSs(int index) {
            return aboveMidlineSs[0] + index * spacingSs;
        }

        double topYSs(int index) {
            return midlineYSs(index) - aboveMidlineSs[index];
        }

        double heightSs(int index) {
            return aboveMidlineSs[index] + belowMidlineSs[index];
        }

        double totalHeightSs() {
            var last = count() - 1;
            return midlineYSs(last) + belowMidlineSs[last];
        }
    }

    /**
     * Measures every child, or returns null when there is nothing to lay out.
     * <p>
     * Always re-runs the line layouts first: a live drag moves elements without firing a
     * mutation, so there is no dirty signal to key off here. It is cheap because each line's
     * own {@code ensureLayout()} is a no-op when that line is clean.
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

        for (var i = 0; i < count; i++) {
            var result = layoutResultOf(parent.getComponent(i));

            if (result == null) {
                // The line could not fit its content (issue #449) and has no layout to
                // measure; reserve the minimum staff surround so it still stacks sensibly.
                aboveMidlineSs[i] = Staff.MIN_ABOVE_STAFF_SS + Staff.STAFF_HALF_SS;
                belowMidlineSs[i] = Staff.MIN_BELOW_STAFF_SS + Staff.STAFF_HALF_SS;
            } else {
                aboveMidlineSs[i] = result.aboveMidlineSs();
                belowMidlineSs[i] = result.belowMidlineSs(lyricRenderMetrics);
            }
        }

        // With a single child there are no pairs, so the spacing stays 0 and it sits at y = 0.
        var spacingSs = 0.0;

        for (var i = 0; i < count - 1; i++) {
            spacingSs = Math.max(
                spacingSs,
                belowMidlineSs[i] + LineSpacing.MIN_INTER_LINE_GAP_SS + aboveMidlineSs[i + 1]
            );
        }

        return new Geometry(aboveMidlineSs, belowMidlineSs, spacingSs);
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
                // Positions round to nearest so lines stay centred on their midline;
                // sizes round up so no content is clipped at the component edge.
                var y = viewScale.toViewPx(new Ss(geometry.topYSs(i))).roundedPx();
                var height = viewScale.toViewPx(new Ss(geometry.heightSs(i))).ceilPx();
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
            var height = staffPanel.viewScale().toViewPx(new Ss(geometry.totalHeightSs())).ceilPx();

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
