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
 * The tooltip of one lint mark, shown on request rather than by the pointer: the tooltip a hover
 * on the mark shows, at the same place, after the mark's item is scrolled into view.
 * <p>
 * The tooltip manager shows a tooltip only for a mouse event, so this owns its own popup. It only
 * shows and hides that popup; {@link LintCalloutController} decides when.
 */
final class LintCallout {

    private final ScoreView scoreView;

    /** The popup on screen, or {@code null} while none shows. */
    private @Nullable Popup popup = null;

    /**
     * @param scoreView the score whose marks the callout shows
     */
    LintCallout(ScoreView scoreView) {
        this.scoreView = scoreView;
    }

    /**
     * Hides the callout on screen, then scrolls the full height of the line that holds
     * {@code mark} and the mark's item into view, and shows the mark's tooltip at its item.
     *
     * @param mark a mark of the song's current lint report, whose element is therefore in a line
     *             of the song the score shows; the score draws every such mark
     * @throws RuntimeError.ExitInProgressError, after raising the fatal-error dialog, when the
     *         mark's element is in no line ({@link StaffElement#requireParentLine}), or when the
     *         score has no component for that line ({@link ScoreView#requireLineComponent}).
     *         Neither can happen: the song is linted at once after every change, so a mark of
     *         the current report names an element in a line of the song, and the score has a
     *         component for every line of its song.
     */
    void show(LintMark mark) {
        hide();

        var lineComponent = scoreView.requireLineComponent(mark.element().requireParentLine());
        var placed = lineComponent.revealLintMark(mark);
        var location = placed.locationOnScreenViewPx();
        var shown = PopupFactory.getSharedInstance().getPopup(lineComponent, placed.toolTip(), location.x, location.y);

        shown.show();
        popup = shown;
    }

    /**
     * Hides the callout on screen.
     *
     * @effects nothing when no callout shows
     */
    void hide() {
        var shown = popup;

        if (shown == null) {
            return;
        }

        popup = null;
        shown.hide();
    }

    /** Answers whether the callout is on screen. */
    boolean isShowing() {
        return popup != null;
    }
}
