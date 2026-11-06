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

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Song;
import songscribe.error.RuntimeError;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.ViewScale;
import songscribe.ui.component.ScoreView;

/**
 * Panel containing all staff lines of a song.
 */
public class StaffPanel extends JPanel {

    private final List<LinePanel> linePanels = new ArrayList<>();

    private final ScoreView scoreView;

    public StaffPanel(ScoreView scoreView) {
        this.scoreView = scoreView;
        setLayout(new StaffLinesLayout(this));
        setOpaque(false);
    }

    ViewScale viewScale() {
        return scoreView.getViewScale();
    }

    /**
     * {@link StaffLinesLayout} floors each line's bounds at the minimum staff surround, so two
     * sparse neighbours can have overlapping bounds, though their ink never overlaps.
     */
    @Override
    public boolean isOptimizedDrawingEnabled() {
        return false;
    }

    /** Builds one line panel for each line of the score view's current song. */
    public void rebuildLayout() {
        removeAll();
        linePanels.clear();

        var song = scoreView.getSong();
        var lineCount = song.lineCount();

        for (var i = 0; i < lineCount; i++) {
            var line = song.getLine(i);
            var linePanel = new LinePanel(scoreView, line, i);
            linePanel.setAlignmentX(LEFT_ALIGNMENT);
            linePanels.add(linePanel);

            add(linePanel);
        }

        revalidate();
        repaint();
    }

    public List<LinePanel> getLinePanels() {
        return linePanels;
    }

    /**
     * @return null if index is out of bounds
     */
    @Nullable
    public LinePanel getLinePanel(int index) {
        if (index < 0 || index >= linePanels.size()) {
            return null;
        }

        return linePanels.get(index);
    }

    /**
     * @return null if point is not in any line
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

    void ensureAllLineLayouts() {
        // Line layouts read hyphenWidthSs and spaceWidthSs from the metrics to reserve column
        // spacing for syllable gaps, so they are rebuilt before any layout runs.
        scoreView.rebuildLyricRenderMetrics();

        layOutLines();
    }

    @SuppressWarnings("UnusedReturnValue")
    private ArrayList<LayoutResult> layOutLines() {
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
