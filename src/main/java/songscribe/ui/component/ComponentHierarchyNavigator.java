/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.ui.component;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.StaffPanel;
import songscribe.ui.layout.ScaleContext;

public final class ComponentHierarchyNavigator {

    private final ComponentHierarchyProvider provider;

    public ComponentHierarchyNavigator(ComponentHierarchyProvider provider) {
        this.provider = provider;
    }

    /**
     * Returns the LineComponent for the given line index, or null if not found.
     */
    @Nullable
    public LineComponent getLineComponent(int lineIndex) {
        var mainPanel = provider.getMainPanel();

        if (mainPanel == null) {
            return null;
        }

        for (var linePanel : mainPanel.getStaffPanel().getLinePanels()) {
            var lineComponent = linePanel.getLineComponent();

            if (lineComponent.getLineIndex() == lineIndex) {
                return lineComponent;
            }
        }

        return null;
    }

    /**
     * Returns the actual middle Y coordinate of a line in the Score coordinate system.
     */
    public int getActualLineMiddleYPx(int lineIndex) {
        var mainPanel = provider.getMainPanel();

        if (mainPanel == null) {
            return 0;
        }

        var staffPanel = mainPanel.getStaffPanel();
        var linePanels = staffPanel.getLinePanels();

        if (lineIndex < 0 || lineIndex >= linePanels.size()) {
            return 0;
        }

        var linePanel = linePanels.get(lineIndex);
        var lineComponent = linePanel.getLineComponent();

        // Get the LineComponent's Y position in the Score coordinate system
        var linePanelY = linePanel.getY();
        var staffPanelY = staffPanel.getY();
        var mainPanelY = mainPanel.getY();
        var lineComponentY = lineComponent.getY();
        var componentMiddleLineYPx = lineComponent.getMiddleLineYPx();

        // The middleLineY is relative to the LineComponent, so we need to add the offsets
        return mainPanelY + staffPanelY + linePanelY + lineComponentY + componentMiddleLineYPx;
    }

    /**
     * Finds the line index at the given Y coordinate.
     *
     * @param y The Y coordinate in Score coordinate system
     * @return The line index, or -1 if not found
     */
    public int findLineIndexAtPoint(int y, int rowHeight) {
        var mainPanel = provider.getMainPanel();
        var song = provider.getSong();

        if (mainPanel == null) {
            return (int) (y - song.getTopPaddingSs()) / rowHeight;
        }

        // Convert Score Y to StaffPanel Y
        var staffPanel = mainPanel.getStaffPanel();
        var staffPanelY = y - mainPanel.getY() - staffPanel.getY();

        // Find which LinePanel contains this Y
        var linePanels = staffPanel.getLinePanels();

        for (var i = 0; i < linePanels.size(); i++) {
            var linePanel = linePanels.get(i);
            var bounds = linePanel.getBounds();

            if (staffPanelY >= bounds.y && staffPanelY < bounds.y + bounds.height) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Sets up the selection provider and score reference for all line components.
     *
     * @param selectionProvider Function that checks if a note is selected
     * @param score The score instance to set on line components
     */
    public void setupLineComponentState(
        LineComponent.SelectionProvider selectionProvider,
        Score score
    ) {
        var mainPanel = provider.getMainPanel();

        if (mainPanel == null) {
            return;
        }

        var staffPanel = mainPanel.getStaffPanel();

        for (var linePanel : staffPanel.getLinePanels()) {
            var lineComponent = linePanel.getLineComponent();
            lineComponent.setSelectionProvider(selectionProvider);
            lineComponent.setScore(score);
        }
    }

    /**
     * Updates the layout values from the component hierarchy.
     * <p>
     * This calculates middleLineY and rowHeight based on the actual component positions
     * rather than a separate layout manager.
     *
     * @param layoutUpdater Consumer that receives [middleLineY, rowHeight]
     */
    public void updateLayoutFromComponents(Consumer<int[]> layoutUpdater) {
        var mainPanel = provider.getMainPanel();

        if (mainPanel == null) {
            return;
        }

        var staffPanel = mainPanel.getStaffPanel();
        var linePanels = staffPanel.getLinePanels();

        if (linePanels.isEmpty()) {
            return;
        }

        // middleLineYPx = first line's absolute middle Y
        var middleLineYPx = getActualLineMiddleYPx(0);

        // rowHeightPx = distance between consecutive line midpoints
        int rowHeightPx;

        if (linePanels.size() >= 2) {
            rowHeightPx = getActualLineMiddleYPx(1) - getActualLineMiddleYPx(0);
        } else {
            var linePanel = linePanels.getFirst();
            rowHeightPx = linePanel.getLineComponent().getHeight()
                + ScaleContext.getInstance().toRoundedPixels(StaffPanel.LINE_MARGIN_BOTTOM_SS);
        }

        layoutUpdater.accept(new int[]{middleLineYPx, rowHeightPx});
    }
}
