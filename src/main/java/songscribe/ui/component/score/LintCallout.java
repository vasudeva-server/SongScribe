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

package songscribe.ui.component.score;

import javax.swing.Popup;
import javax.swing.PopupFactory;

import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElement;
import songscribe.error.RuntimeError;
import songscribe.lint.LintMark;
import songscribe.ui.component.ScoreView;

/**
 * The tooltip of one lint mark, shown on request rather than by the pointer. The tooltip manager
 * shows a tooltip only for a mouse event, so this owns its own popup; {@link LintCalloutController}
 * decides when it shows.
 */
final class LintCallout {

    private final ScoreView scoreView;

    /** {@code null} while none shows. */
    private @Nullable Popup popup = null;

    LintCallout(ScoreView scoreView) {
        this.scoreView = scoreView;
    }

    void show(LintMark mark) {
        hide();

        var lineComponent = scoreView.requireLineComponent(mark.element().requireParentLine());
        var placed = lineComponent.revealLintMark(mark);
        var location = placed.locationOnScreenViewPx();
        var shown = PopupFactory.getSharedInstance().getPopup(lineComponent, placed.toolTip(), location.x, location.y);

        shown.show();
        popup = shown;
    }

    void hide() {
        var shown = popup;

        if (shown == null) {
            return;
        }

        popup = null;
        shown.hide();
    }

    boolean isShowing() {
        return popup != null;
    }
}
