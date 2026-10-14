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
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;

import songscribe.error.RuntimeError;
import songscribe.lint.LintMark;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.notification.LintCalloutWasDismissedNotification;
import songscribe.message.notification.LintMarkSelectionDidChangeNotification;
import songscribe.message.notification.LintingNotification;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.component.ScoreView;

/**
 * Decides when the score's lint tooltips show and hide: the callout of the mark selected in the
 * Messages panel, and the tooltip the tooltip manager shows on a hover over a mark. At most one
 * shows: the callout hides the hover tooltip, and a hover onto a mark dismisses the callout.
 * <p>
 * A callout hidden for any reason but a change of the selection is dismissed: this posts a
 * {@link LintCalloutWasDismissedNotification}, so the panel clears the selection that showed it.
 *
 * <h2>Lifecycle</h2>
 * Process-lifetime, as the {@link ScoreView} that owns it is, so it does not implement
 * {@code songscribe.lifecycle.Disposable}.
 */
public final class LintCalloutController {

    private final ScoreView scoreView;
    private final LintCallout callout;

    private final WindowFocusListener windowListener = new WindowAdapter() {
        @Override
        public void windowLostFocus(WindowEvent e) {
            dismissCallout();
        }
    };

    /** {@code null} while no callout shows. */
    private @Nullable Window calloutWindow = null;

    public LintCalloutController(ScoreView scoreView) {
        this.scoreView = scoreView;
        callout = new LintCallout(scoreView);

        scoreView.requireScrollPane().getViewport().addChangeListener(_ -> hideAll());
        MessageSubscription.addProcessListener(this);
    }

    @Handler
    public void lintMarkSelectionDidChange(LintMarkSelectionDidChangeNotification message) {
        var mark = message.getMark();

        if (mark == null) {
            hideCallout();
        } else {
            showCallout(mark);
        }
    }

    /** Hides both tooltips before the zoom moves the items they point at. */
    @Handler(priority = ZoomDidChangeNotification.BEFORE_APPLY_PRIORITY)
    public void zoomDidChange(ZoomDidChangeNotification message) {
        hideAll();
    }

    /**
     * The tooltip manager asks for a new hover tooltip only when the pointer moves.
     */
    @Handler
    public void linting(LintingNotification message) {
        hideAll();
    }

    void hoverDidEnterMark() {
        dismissCallout();
    }

    private void showCallout(LintMark mark) {
        var window = SwingUtilities.getWindowAncestor(scoreView);

        if (window == null) {
            throw RuntimeError.exit("score view is in no window");
        }

        hideCallout();
        hideHoverToolTip();

        // The callout scrolls its item into view, which hides both tooltips through the viewport
        // listener; it shows its popup after that scroll.
        callout.show(mark);
        window.addWindowFocusListener(windowListener);
        calloutWindow = window;
    }

    /** Posts no notification: whoever changed the selection knows. */
    private void hideCallout() {
        callout.hide();

        var window = calloutWindow;

        if (window != null) {
            window.removeWindowFocusListener(windowListener);
            calloutWindow = null;
        }
    }

    private void dismissCallout() {
        if (!callout.isShowing()) {
            return;
        }

        hideCallout();
        MessageCenter.post(new LintCalloutWasDismissedNotification());
    }

    private void hideAll() {
        dismissCallout();
        hideHoverToolTip();
    }

    private static void hideHoverToolTip() {
        var toolTipManager = ToolTipManager.sharedInstance();

        if (toolTipManager.isEnabled()) {
            toolTipManager.setEnabled(false);
            toolTipManager.setEnabled(true);
        }
    }
}
