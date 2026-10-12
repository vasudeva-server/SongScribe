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

import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import javax.swing.JViewport;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;

import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;
import songscribe.lint.LintMark;
import songscribe.ui.component.ScoreView;

/**
 * The tooltip of one lint mark, shown on request rather than by the pointer: the tooltip a hover
 * on the mark shows, at the same place, after the mark's item is scrolled into view.
 * <p>
 * The tooltip manager shows a tooltip only for a mouse event, so this owns its own popup. At most
 * one callout shows at a time.
 * <p>
 * The callout is dismissed — it hides on its own and tells whoever showed it — when the score
 * scrolls or zooms, because its arrow would point where the item was; when the window loses
 * focus; and when a hover tooltip is created in the score. A {@link #hide} asked for by whoever
 * showed it tells nobody, because that caller already knows.
 */
public final class LintCallout {

    private final ScoreView scoreView;

    private final ChangeListener viewportListener = _ -> dismiss();

    private final WindowFocusListener windowListener = new WindowAdapter() {
        @Override
        public void windowLostFocus(WindowEvent e) {
            dismiss();
        }
    };

    /** The callout on screen, or {@code null} while none shows. */
    private @Nullable Showing showing = null;

    /**
     * A callout on screen, and what it listens to while it shows.
     *
     * @param popup     the popup that holds the tooltip
     * @param viewport  the score's viewport, whose scroll or zoom dismisses the callout
     * @param window    the window whose loss of focus dismisses the callout
     * @param onDismiss what to run when the callout is dismissed
     */
    private record Showing(Popup popup, JViewport viewport, Window window, Runnable onDismiss) { }

    /**
     * @param scoreView the score whose marks the callout shows
     */
    public LintCallout(ScoreView scoreView) {
        this.scoreView = scoreView;
    }

    /**
     * Hides the callout on screen, then scrolls the item of {@code mark} into view and shows the
     * mark's tooltip there.
     *
     * @param mark      a mark of the current lint report, which the score always draws
     * @param onDismiss runs once if this callout is dismissed; never runs if it is hidden with
     *                  {@link #hide} or replaced by another {@code show}
     */
    public void show(LintMark mark, Runnable onDismiss) {
        hide();

        var line = mark.element().getParentLine();

        if (line == null) {
            throw RuntimeError.exit("lint mark's element is in no line: " + mark);
        }

        var lineComponent = scoreView.getLineComponent(line.index());

        if (lineComponent == null) {
            throw RuntimeError.exit("no line component for line " + line.index());
        }

        var window = SwingUtilities.getWindowAncestor(scoreView);

        if (window == null) {
            throw RuntimeError.exit("score view is in no window");
        }

        var placed = lineComponent.revealLintMark(mark);

        if (placed == null) {
            throw RuntimeError.exit("score does not draw lint mark: " + mark);
        }

        var toolTip = placed.toolTip();
        var location = placed.locationOnScreen();

        // The tooltip manager pushes a tooltip that would leave the screen back onto it. This does
        // the same, and the arrow moves along the edge to keep pointing at the item.
        var screenBounds = lineComponent.getGraphicsConfiguration().getBounds();
        var maxX = screenBounds.x + screenBounds.width - toolTip.getPreferredSize().width;
        var x = Math.max(screenBounds.x, Math.min(location.x, maxX));

        var popup = PopupFactory.getSharedInstance().getPopup(lineComponent, toolTip, x, location.y);
        var viewport = scoreView.requireScrollPane().getViewport();

        popup.show();
        viewport.addChangeListener(viewportListener);
        window.addWindowFocusListener(windowListener);
        showing = new Showing(popup, viewport, window, onDismiss);
    }

    /**
     * Hides the callout on screen, without running its {@code onDismiss}.
     *
     * @effects nothing when no callout shows
     */
    public void hide() {
        if (showing == null) {
            return;
        }

        showing.viewport().removeChangeListener(viewportListener);
        showing.window().removeWindowFocusListener(windowListener);
        showing.popup().hide();
        showing = null;
    }

    /**
     * Hides the callout on screen, then runs its {@code onDismiss}.
     *
     * @effects nothing when no callout shows
     */
    void dismiss() {
        var dismissed = showing;

        if (dismissed == null) {
            return;
        }

        hide();
        dismissed.onDismiss().run();
    }
}
