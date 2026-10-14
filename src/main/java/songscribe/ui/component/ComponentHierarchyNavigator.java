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

    /** Null when detached (tests). */
    @Nullable
    private ScoreView scoreView;

    public ComponentHierarchyNavigator(ComponentHierarchyProvider provider) {
        this.provider = provider;
    }

    public void setScoreView(ScoreView scoreView) {
        this.scoreView = scoreView;
    }

    private ViewScale viewScale() {
        return scoreView != null ? scoreView.getViewScale() : ViewScale.IDENTITY;
    }

    /** Null if no line component has the given line index. */
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
