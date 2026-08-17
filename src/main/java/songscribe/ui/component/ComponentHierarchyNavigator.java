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

import org.jspecify.annotations.Nullable;

import songscribe.ui.ViewScale;
import songscribe.ui.component.score.LineComponent;

public final class ComponentHierarchyNavigator {

    private final ComponentHierarchyProvider provider;

    /** The owning score view, or null when detached (tests). Supplies the view zoom. */
    @Nullable
    private ScoreView scoreView;

    public ComponentHierarchyNavigator(ComponentHierarchyProvider provider) {
        this.provider = provider;
    }

    /** Sets the owning {@link ScoreView} so the single-line fallback spacing tracks zoom. */
    public void setScoreView(ScoreView scoreView) {
        this.scoreView = scoreView;
    }

    /** The view zoom, or {@link ViewScale#IDENTITY} when detached. */
    private ViewScale viewScale() {
        return scoreView != null ? scoreView.getViewScale() : ViewScale.IDENTITY;
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
     * Sets up the selection provider and scoreView reference for all line components.
     *
     * @param selectionProvider Function that checks if a note is selected
     * @param scoreView The scoreView instance to set on line components
     */
    public void setupLineComponentState(
        LineComponent.SelectionProvider selectionProvider,
        ScoreView scoreView
    ) {
        var mainPanel = provider.getMainPanel();

        if (mainPanel == null) {
            return;
        }

        var staffPanel = mainPanel.getStaffPanel();

        for (var linePanel : staffPanel.getLinePanels()) {
            var lineComponent = linePanel.getLineComponent();
            lineComponent.setSelectionProvider(selectionProvider);
            lineComponent.setScoreView(scoreView);
        }
    }

}
