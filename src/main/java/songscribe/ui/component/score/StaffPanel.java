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

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Composition;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.LineElement;

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
    private Composition composition;

    /** List of line panels, one per staff line. */
    private final List<LinePanel> linePanels = new ArrayList<>();

    /** Spacing between lines. */
    private final int lineMargin;

    /**
     * Creates a new StaffPanel.
     */
    public StaffPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        lineMargin = LayoutStylesheet.px(LayoutStylesheet.LINE_MARGIN_BOTTOM_MU);
    }

    /**
     * Sets the composition and rebuilds the layout.
     *
     * @param composition The composition
     */
    public void setComposition(@NotNull Composition composition) {
        this.composition = composition;
        rebuildLayout();
    }

    /**
     * Returns the composition.
     */
    public Composition getComposition() {
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
     * Finds the LineElement at the given point.
     *
     * @param point Point in panel coordinates
     * @return The element at the point, or null
     */
    @Nullable
    public LineElement findElementAt(@NotNull Point point) {
        for (var linePanel : linePanels) {
            var panelBounds = linePanel.getBounds();

            if (panelBounds.contains(point)) {
                var localPoint = new Point(
                    point.x - panelBounds.x,
                    point.y - panelBounds.y
                );
                return linePanel.findElementAt(localPoint);
            }
        }

        return null;
    }

    /**
     * Returns the line panel containing the given point.
     *
     * @param point Point in panel coordinates
     * @return The line panel, or null if point is not in any line
     */
    @Nullable
    public LinePanel getLinePanelAt(@NotNull Point point) {
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

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }
}
