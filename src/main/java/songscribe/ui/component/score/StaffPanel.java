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

import songscribe.dom.Song;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;

/**
 * Panel containing all staff lines of a song.
 * <p>
 * Uses {@link StaffLinesLayout} to stack {@link LinePanel} components, which sizes each
 * line to its own content and owns all spacing between them.
 * <p>
 * Note: Named StaffPanel to avoid conflict with ScoreView.ScorePanel inner class.
 */
public class StaffPanel extends JPanel {

    /** The song model. */
    @Nullable
    private Song song;

    /** List of line panels, one per staff line. */
    private final List<LinePanel> linePanels = new ArrayList<>();

    /** The owning score view, or null when detached (tests). Supplies the view zoom. */
    @Nullable
    private ScoreView scoreView;

    /**
     * Creates a new StaffPanel.
     */
    public StaffPanel() {
        setLayout(new StaffLinesLayout(this));
        setOpaque(false);
    }

    /** Sets the owning {@link ScoreView} so the inter-line spacing tracks the view zoom. */
    public void setScoreView(ScoreView scoreView) {
        this.scoreView = scoreView;
    }

    /** The view zoom, or {@link ViewScale#IDENTITY} when detached. */
    ViewScale viewScale() {
        return scoreView != null ? scoreView.getViewScale() : ViewScale.IDENTITY;
    }

    /**
     * Reports that this panel's children may overlap, so Swing repaints the full damaged
     * region rather than assuming one child's dirty rectangle cannot expose another's ink.
     * <p>
     * {@link StaffLinesLayout} floors each line's bounds at the minimum staff surround while
     * deriving the inter-line spacing from measured content alone, so two sparse neighbours
     * can have overlapping bounds. Their ink never overlaps — only the reserved headroom does.
     */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    /**
     * Sets the song and rebuilds the layout.
     *
     * @param song The song
     */
    public void setSong(Song song) {
        this.song = song;
        rebuildLayout();
    }

    /**
     * Returns the song.
     */
    public Song getSong() {
        if (song == null) {
            throw RuntimeError.exit("song not initialized");
        }

        return song;
    }

    /**
     * Rebuilds the panel layout based on the current song.
     * <p>
     * Creates a new {@link LinePanel} for each line in the song,
     * with vertical spacing between them.
     */
    public void rebuildLayout() {
        removeAll();
        linePanels.clear();

        if (song == null) {
            revalidate();
            repaint();
            return;
        }

        var lineCount = song.lineCount();

        for (var i = 0; i < lineCount; i++) {
            var line = song.getLine(i);
            var linePanel = new LinePanel(song, line, i);
            linePanel.setAlignmentX(LEFT_ALIGNMENT);
            linePanels.add(linePanel);

            add(linePanel);
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

    /**
     * Forces every line's layout so that {@link StaffLinesLayout} can measure the results.
     * <p>
     * Package-private for testing: tests can spy on this method to avoid
     * the full ScoreView dependency.
     */
    void ensureAllLineLayouts() {
        var firstLineView = linePanels.getFirst().getLineComponent().getScoreView();

        // Ensure LyricRenderMetrics on ScoreView is up-to-date before any line layout runs.
        // Line layouts read hyphenWidthSs and spaceWidthSs from these metrics to reserve
        // column spacing for syllable gaps.
        firstLineView.rebuildLyricRenderMetrics();

        layOutLines();
    }

    /**
     * The lyric geometry every line is laid out against. Resolved through the first line
     * component rather than this panel's own {@link ScoreView} back-reference, because the
     * line components are given theirs first.
     */
    LyricRenderMetrics lyricRenderMetrics() {
        return linePanels.getFirst().getLineComponent().getScoreView().getLyricRenderMetrics();
    }

    /**
     * Lays out every line in order, threading lyric-extender continuation across line boundaries so
     * that a melisma running off the end of one line reappears as a leading stub on the next.
     * <p>
     * Package-private for testing.
     *
     * @return The layout of each line that produced one, in line order. Lines that have not been
     *         laid out yet are skipped, so the list can be shorter than the line count
     */
    @SuppressWarnings("UnusedReturnValue")
    ArrayList<LayoutResult> layOutLines() {
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
        return layouts;
    }
}
