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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>Because {@code S >= belowMidline[N] + gap + aboveMidline[N+1]} for every pair, adjacent
 *       lines' <em>content</em> is always at least {@link LineSpacing#MIN_INTER_LINE_GAP_SS}
 *       apart.</li>
 * </ul>
 * Component <em>bounds</em>, however, are floored at the minimum staff surround
 * ({@link LineSpacing#MIN_ABOVE_MIDLINE_SS} / {@link LineSpacing#MIN_BELOW_MIDLINE_SS}) so a
 * line always contains its own staff, and those floors are excluded from {@code S}. Two sparse
 * neighbours
 * can therefore have overlapping bounds even though their ink does not overlap — which is why
 * {@link StaffPanel#isOptimizedDrawingEnabled} reports {@code false}.
 * This class is the seam where staff spaces become view pixels: everything above is computed
 * in {@code Ss}, then converted once per child through the view zoom.
 */
public class StaffLinesLayout implements LayoutManager2 {

    private static final Logger LOG = LoggerFactory.getLogger(StaffLinesLayout.class);

    private final StaffPanel staffPanel;

    public StaffLinesLayout(StaffPanel staffPanel) {
        this.staffPanel = staffPanel;
    }

    /**
     * The midline geometry of every child, in staff spaces, plus the uniform
     * midline-to-midline distance derived from it.
     * <p>
     * Two extents are kept per line, and the split is the whole point:
     * <ul>
     *   <li>{@code aboveMidlineSs} / {@code belowMidlineSs} — the line's <em>measured content</em>,
     *       and the only inputs to {@code spacingSs}. Spacing therefore still follows content
     *       exactly as issue #591 requires.</li>
     *   <li>{@code paintAboveMidlineSs} / {@code paintBelowMidlineSs} — the same values floored
     *       at the minimum staff surround, used only for component <em>bounds</em>. Without the
     *       floor a line whose ink stops at the staff top gets bounds that start there, and
     *       Swing clips the top staff line, its ledger lines, and anything else drawn above.</li>
     * </ul>
     * The floors never reach {@code spacingSs}, so adding them cannot widen the gaps.
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

        double topYSs(int index) {
            return midlineYSs(index) - paintAboveMidlineSs[index];
        }

        double heightSs(int index) {
            return paintAboveMidlineSs[index] + paintBelowMidlineSs[index];
        }

        double totalHeightSs() {
            var last = count() - 1;
            return midlineYSs(last) + paintBelowMidlineSs[last];
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

        var paintAboveMidlineSs = new double[count];
        var paintBelowMidlineSs = new double[count];

        for (var i = 0; i < count; i++) {
            var result = layoutResultOf(parent.getComponent(i));

            if (result == null) {
                // The line could not fit its content (issue #449) and has no layout to
                // measure; reserve the minimum staff surround so it still stacks sensibly.
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
        var worstPairIndex = -1;

        for (var i = 0; i < count - 1; i++) {
            var pairSs = belowMidlineSs[i] + LineSpacing.MIN_INTER_LINE_GAP_SS + aboveMidlineSs[i + 1];

            if (pairSs > spacingSs) {
                spacingSs = pairSs;
                worstPairIndex = i;
            }
        }

        var geometry = new Geometry(
            aboveMidlineSs, belowMidlineSs, paintAboveMidlineSs, paintBelowMidlineSs, spacingSs);

        if (LOG.isDebugEnabled()) {
            logGeometry(parent, geometry, worstPairIndex, lyricRenderMetrics);
        }

        return geometry;
    }

    /**
     * Dumps the full spacing derivation: each line's extents, which adjacent pair set the
     * uniform spacing, and the resulting visible gap between consecutive components. Only the
     * pair named as {@code worstPairIndex} is tight against
     * {@link LineSpacing#MIN_INTER_LINE_GAP_SS}; every other gap is wider by construction,
     * which is the model working as designed rather than stray padding.
     */
    private void logGeometry(
        Container parent, Geometry geometry, int worstPairIndex, LyricRenderMetrics lyricRenderMetrics) {
        LOG.debug(
            "staff spacing: {} lines, S={} ss set by pair ({},{}); lyric metrics: staffToLyricsGap={} ss, lyricBoxHeight={} ss",
            geometry.count(), geometry.spacingSs(), worstPairIndex, worstPairIndex + 1,
            lyricRenderMetrics.staffToLyricsGapSs(), lyricRenderMetrics.lyricBoxHeightSs());

        for (var i = 0; i < geometry.count(); i++) {
            var result = layoutResultOf(parent.getComponent(i));
            double contentAboveSs;
            double lyricsBandSs;

            if (result == null) {
                contentAboveSs = Double.NaN;
                lyricsBandSs = Double.NaN;
            } else {
                contentAboveSs = result.staffTopYSsInLine();
                lyricsBandSs = result.lyricsBandHeightSs(lyricRenderMetrics);
            }

            // contentBelow is not exposed directly; recover it from the below-midline total.
            var contentBelowSs = geometry.belowMidlineSs[i] - Staff.STAFF_HALF_SS - lyricsBandSs;

            LOG.debug(
                "  line {}: above={} below={} ss (contentAbove={} contentBelow={} lyricsBand={} ss); "
                    + "painted above={} below={} ss, topY={} height={} ss",
                i, geometry.aboveMidlineSs[i], geometry.belowMidlineSs[i],
                contentAboveSs, contentBelowSs, lyricsBandSs,
                geometry.paintAboveMidlineSs[i], geometry.paintBelowMidlineSs[i],
                geometry.topYSs(i), geometry.heightSs(i));
        }

        for (var i = 0; i < geometry.count() - 1; i++) {
            // Content gap: bottom of line i's lyrics band to the top of line i+1's above-staff
            // content. This is the one MIN_INTER_LINE_GAP_SS governs. The bounds gap can be
            // smaller, and negative when two sparse neighbours' reserved headroom overlaps.
            var contentGapSs = (geometry.midlineYSs(i + 1) - geometry.aboveMidlineSs[i + 1])
                - (geometry.midlineYSs(i) + geometry.belowMidlineSs[i]);
            var boundsGapSs = geometry.topYSs(i + 1) - (geometry.topYSs(i) + geometry.heightSs(i));
            LOG.debug(
                "  gap {}->{}: content={} ss (min {}), bounds={} ss",
                i, i + 1, contentGapSs, LineSpacing.MIN_INTER_LINE_GAP_SS, boundsGapSs);
        }
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
