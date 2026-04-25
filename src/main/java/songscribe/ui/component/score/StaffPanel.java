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

import java.util.ArrayList;
import java.util.List;
import songscribe.error.RuntimeError;

import org.jspecify.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.ui.layout.CompositionLayoutMetricsBuilder;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.LyricRenderMetrics;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.ScaleContext;

/**
 * Panel containing all staff lines of a composition.
 * <p>
 * Uses BoxLayout.Y_AXIS to stack {@link LinePanel} components with
 * {@link LayoutStylesheet#LINE_MARGIN_BOTTOM_MU} spacing between them.
 * <p>
 * Note: Named StaffPanel to avoid conflict with Score.ScorePanel inner class.
 */
public class StaffPanel extends JPanel {

    /** The composition model. */
    @Nullable
    private Composition composition;

    /** List of line panels, one per staff line. */
    private final List<LinePanel> linePanels = new ArrayList<>();

    /** Spacing between lines. */
    private final int lineMargin;

    /**
     * Cached {@link LyricRenderMetrics} from the previous {@link #updateCompositionMetrics}
     * call. Reused when the lyrics font has not changed to avoid re-measuring hyphen and
     * space widths via a fresh {@link java.awt.font.TextLayout} on every paint pass.
     */
    @Nullable
    private LyricRenderMetrics lyricRenderMetrics;

    /**
     * Creates a new StaffPanel.
     */
    public StaffPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        lineMargin = ScaleContext.getInstance().toRoundedPixels(LayoutStylesheet.LINE_MARGIN_BOTTOM_SS);
    }

    /**
     * Sets the composition and rebuilds the layout.
     *
     * @param composition The composition
     */
    public void setComposition(Composition composition) {
        this.composition = composition;
        rebuildLayout();
    }

    /**
     * Returns the composition.
     */
    public Composition getComposition() {
        if (composition == null) {
            throw RuntimeError.exit("composition not initialized");
        }

        return composition;
    }

    /**
     * Rebuilds the panel layout based on the current composition.
     * <p>
     * Creates a new {@link LinePanel} for each line in the composition,
     * with vertical spacing between them.
     */
    public void rebuildLayout() {
        removeAll();
        linePanels.clear();

        if (composition == null) {
            revalidate();
            repaint();
            return;
        }

        var lineCount = composition.lineCount();

        for (var i = 0; i < lineCount; i++) {
            var line = composition.getLine(i);
            var linePanel = new LinePanel(composition, line, i);
            linePanel.setAlignmentX(LEFT_ALIGNMENT);
            linePanels.add(linePanel);

            add(linePanel);

            // Add spacing after each line except the last
            if (i < lineCount - 1) {
                add(Box.createVerticalStrut(lineMargin));
            }
        }

        revalidate();
        repaint();
    }

    /**
     * Returns the line panels.
     */
    public List<LinePanel> getLinePanels() {
        return linePanels;
    }

    /**
     * Returns the line panel at the given index.
     *
     * @param index Line index
     * @return The line panel, or null if index is out of bounds
     */
    @Nullable
    public LinePanel getLinePanel(int index) {
        if (index < 0 || index >= linePanels.size()) {
            return null;
        }

        return linePanels.get(index);
    }

    /**
     * Returns the line panel containing the given point.
     *
     * @param point Point in panel coordinates
     * @return The line panel, or null if point is not in any line
     */
    @Nullable
    public LinePanel getLinePanelAt(Point point) {
        for (var linePanel : linePanels) {
            if (linePanel.getBounds().contains(point)) {
                return linePanel;
            }
        }

        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        if (composition == null || linePanels.isEmpty()) {
            return new Dimension(0, 0);
        }

        // Always recompute: live drag updates positions without firing a mutation, so we
        // can't rely on a dirty-flag signal here. Each line's own ensureLayout is a no-op
        // when its layout isn't dirty, so the cost of re-running this is bounded by the
        // metrics aggregation.
        updateCompositionMetrics();

        var width = 0;
        var height = 0;

        for (var i = 0; i < linePanels.size(); i++) {
            var size = linePanels.get(i).getPreferredSize();
            width = Math.max(width, size.width);
            height += size.height;

            // Add spacing after each line except the last
            if (i < linePanels.size() - 1) {
                height += lineMargin;
            }
        }

        return new Dimension(width, height);
    }

    /**
     * Forces all line layouts, builds {@link songscribe.ui.layout.CompositionLayoutMetrics}
     * from the results, and pushes the metrics onto the owning {@link songscribe.ui.component.Score}
     * so that all lines report a uniform preferred height.
     */
    private void updateCompositionMetrics() {
        // LyricRenderMetrics is derived from the lyrics font alone, so populate it on
        // the Score before any line layouts run — layout reads hyphenWidthSs and
        // spaceWidthSs (two spaces) from here to reserve column spacing for syllable gaps.
        // Skip the (TextLayout-allocating) rebuild when the font has not changed.
        var score = linePanels.get(0).getLineComponent().getScore();
        var composition = score.getComposition();
        var lyricsFont = composition.getLyricsFont();
        var existingMetrics = lyricRenderMetrics;

        if (existingMetrics == null || !existingMetrics.lyricsFont().equals(lyricsFont)) {
            var scale = ScaleContext.getInstance();
            var scaledFont = scale.scaleFont(lyricsFont);
            var hyphenWidthSs = scale.textWidthSs(lyricsFont, "-");
            var spaceWidthSs = scale.textWidthSs(lyricsFont, "  ");
            lyricRenderMetrics = new LyricRenderMetrics(lyricsFont, scaledFont, hyphenWidthSs, spaceWidthSs);
            score.setLyricRenderMetrics(lyricRenderMetrics);
        }

        // Lay out each line in order, threading lyric-extender continuation across line
        // boundaries so that a melisma that runs off the end of one line reappears as a
        // leading stub on the next.
        var layouts = new ArrayList<LayoutResult>();
        var hasLeadingLyricContinuation = false;

        for (var linePanel : linePanels) {
            var lineComponent = linePanel.getLineComponent();
            lineComponent.setHasLeadingLyricContinuation(hasLeadingLyricContinuation);
            lineComponent.ensureLayout();
            var result = lineComponent.getLayoutResult();

            if (result != null) {
                layouts.add(result);
                hasLeadingLyricContinuation = result.hasTrailingLyricContinuation();
            } else {
                hasLeadingLyricContinuation = false;
            }
        }

        var metrics = CompositionLayoutMetricsBuilder.build(layouts);
        score.setCompositionLayoutMetrics(metrics);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
