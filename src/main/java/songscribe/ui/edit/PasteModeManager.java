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

package songscribe.ui.edit;

import java.awt.MouseInfo;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;

import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.ScaleContext;
import songscribe.dom.ViewPx;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PasteModeDidChangeNotification;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.PasteOverlay;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.component.score.LineComponent;
import songscribe.undo.UndoController;
import songscribe.util.UIUtils;

/**
 * Manages the paste-mode state machine — placement of a clipboard fragment by
 * clicking (or pressing Return over) an insertion point on a line.
 * <pre>
 *                     Cmd+V, no selection
 *         INACTIVE ─────────────────────────> ACTIVE ──────────> [overlay shown, all actions disabled,
 *             ^                                 │                 preview element suppressed]
 *             │                                 │
 *             │                          mouseMoved on a line
 *             │                                 │  └─> findInsertionIndex → track (lc, index), repaint old+new
 *             │                                 │
 *             │        ┌── click / Return ──────┤   (Return with no tracked point ⇒ no-op, stay ACTIVE)
 *             │        │      └─> tryInsertFragment(index, no deleteRange)
 *             │        │             ├─ LINE_FULL ─> error dialog ─> STAY ACTIVE
 *             │        │             └─ INSERTED  ─> exit  (clipboard retained)
 *             │        │
 *             └────────┴── Escape (before DeselectCommand; selection left intact)
 *                      ├── click outside any line
 *                      └── app backgrounded
 *
 *         ALL exits funnel through ONE exit():
 *             active=false → post notification → remove overlay → remove ComponentListener
 * </pre>
 * All exits route through the single {@link #exit()} funnel, mirroring
 * {@code GraceModeManager.finish(boolean)}. Open-coding teardown per exit path
 * would be five paths of duplicated steps, and a missed listener removal is
 * invisible — each enter/exit cycle would leak a live listener on the layered
 * pane. Mirrors {@link GraceModeManager}'s single-flag / static-instance shape.
 */
public final class PasteModeManager {

    @Nullable
    private static PasteModeManager instance = null;

    // Dependencies
    private final ClipboardManager clipboardManager;
    private final ScoreView scoreView;

    // State
    private boolean active = false;

    // The insertion point the next placement will use — the line and the index
    // before which the fragment lands. Tracked from mouse movement; -1 index means
    // the mouse has not yet entered a line, so Return is a no-op.
    @Nullable
    private LineComponent targetLineComponent;

    private int targetIndex = -1;

    // The overlay pill and the layered-pane bounds listener it needs while active.
    // Both are created in enter() and torn down in the single exit() funnel.
    @Nullable
    private PasteOverlay overlay;

    @Nullable
    private ComponentListener overlayBoundsListener;

    public PasteModeManager(ClipboardManager clipboardManager, ScoreView scoreView) {
        this.clipboardManager = clipboardManager;
        this.scoreView = scoreView;
        instance = this;
    }

    /**
     * Returns whether any PasteModeManager instance is currently active.
     * Used by UIAction.enableFromPasteMode() to check paste-mode status.
     */
    public static boolean isActive() {
        return instance != null && instance.isInProgress();
    }

    public boolean isInProgress() {
        return active;
    }

    /**
     * Returns the active {@link PasteModeManager} instance, or null when paste mode is
     * not in progress. Used by {@code LineRenderer} to determine whether a given line is
     * the current insertion target.
     */
    @Nullable
    public static PasteModeManager getActiveInstance() {
        return instance != null && instance.isInProgress() ? instance : null;
    }

    /** Returns the line component currently tracked as the insertion target, or null. */
    @Nullable
    public LineComponent getTargetLineComponent() {
        return targetLineComponent;
    }

    /** Returns the tracked insertion index, or -1 if no insertion point is tracked. */
    public int getTargetIndex() {
        return targetIndex;
    }

    ClipboardManager getClipboardManager() {
        return clipboardManager;
    }

    ScoreView getScoreView() {
        return scoreView;
    }

    /**
     * Returns the {@link ScoreViewController} to place clipboard content through,
     * or null if the ScoreView has not finished initializing yet.
     */
    @Nullable ScoreViewController getScoreViewController() {
        return scoreView.getController();
    }

    void setActive(boolean active) {
        this.active = active;
        MessageCenter.post(new PasteModeDidChangeNotification(active));
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Enters paste mode. Called from {@code handlePaste}'s no-selection branch, so
     * the clipboard is already known non-empty and the score already has focus.
     */
    public void enter() {
        if (active) {
            return;
        }

        setActive(true);
        syncTargetToMouse();

        var layeredPane = MainFrame.getInstance().getLayeredPane();
        var newOverlay = new PasteOverlay(scoreView);
        overlay = newOverlay;

        // No layout manager runs over a JLayeredPane child — track the pane's size
        // ourselves so the overlay stays anchored full-bleed through window resizes.
        var newOverlayBoundsListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                newOverlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
            }
        };
        overlayBoundsListener = newOverlayBoundsListener;

        layeredPane.addComponentListener(newOverlayBoundsListener);
        newOverlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        layeredPane.add(newOverlay, JLayeredPane.PALETTE_LAYER);
        layeredPane.revalidate();
        layeredPane.repaint();
    }

    /**
     * Locates the LineComponent currently under the mouse pointer, if any, and
     * immediately tracks it as the insertion target so the marker appears the
     * instant paste mode is entered rather than waiting for the first real
     * {@code mouseMoved} event. Must run before the overlay pill is added to
     * the layered pane: once added, its full-bleed bounds would be the topmost
     * hit there, shadowing the score underneath from {@link UIUtils#getComponentUnderMouse}.
     */
    private void syncTargetToMouse() {
        var component = UIUtils.getComponentUnderMouse();

        if (!(component instanceof LineComponent lineComponent)) {
            return;
        }

        var mousePosition = MouseInfo.getPointerInfo().getLocation();
        SwingUtilities.convertPointFromScreen(mousePosition, lineComponent);

        var syntheticEvent = new MouseEvent(
            lineComponent, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0,
            mousePosition.x, mousePosition.y, 0, false);

        updateTarget(lineComponent, syntheticEvent);
    }

    /**
     * Cancels paste mode without placing anything. Routes through the single
     * {@link #exit()} funnel. No-op when not in progress.
     */
    public void cancel() {
        if (active) {
            exit();
        }
    }

    /**
     * The single teardown funnel every exit routes through: reset the flag and post
     * the notification (via {@link #setActive}), drop the tracked insertion point,
     * and repaint the line that was showing the insertion marker so it clears.
     */
    private void exit() {
        setActive(false);

        var lineComponent = targetLineComponent;
        targetLineComponent = null;
        targetIndex = -1;

        if (lineComponent != null) {
            lineComponent.repaint();
        }

        var layeredPane = MainFrame.getInstance().getLayeredPane();
        var boundsListener = overlayBoundsListener;
        overlayBoundsListener = null;

        if (boundsListener != null) {
            layeredPane.removeComponentListener(boundsListener);
        }

        var currentOverlay = overlay;
        overlay = null;

        if (currentOverlay != null) {
            layeredPane.remove(currentOverlay);
            layeredPane.revalidate();
            layeredPane.repaint();
        }
    }

    // -------------------------------------------------------------------------
    // Mouse routing (consume-first from LineComponent)
    // -------------------------------------------------------------------------

    /**
     * Tracks the insertion point under the mouse. Returns true (consuming the event)
     * whenever paste mode is active, so the normal preview-element handling is skipped.
     */
    public boolean mouseMoved(LineComponent lineComponent, MouseEvent e) {
        if (!active) {
            return false;
        }

        updateTarget(lineComponent, e);
        return true;
    }

    /**
     * Places the fragment at the clicked insertion point. Returns true (consuming the
     * event) whenever paste mode is active.
     */
    public boolean mouseClicked(LineComponent lineComponent, MouseEvent e) {
        if (!active) {
            return false;
        }

        updateTarget(lineComponent, e);
        placeAtTarget();
        return true;
    }

    /**
     * Places the fragment at the currently tracked insertion point (Return/Enter path).
     * With no tracked point (the mouse never entered a line) this is a no-op and paste
     * mode stays pending. No-op when not in progress.
     */
    public void place() {
        if (!active) {
            return;
        }

        placeAtTarget();
    }

    /**
     * Recomputes the insertion index under the mouse and, when it or the line changes,
     * repaints the previously tracked line and the new one so only those two lines redraw.
     */
    private void updateTarget(LineComponent lineComponent, MouseEvent e) {
        var line = lineComponent.getLine();
        var layoutResult = lineComponent.getLayoutResult();

        if (line == null || layoutResult == null) {
            return;
        }

        // Convert the view-pixel event x to the fixed document scale, then to staff
        // spaces — the same recipe PreviewElementManager.trackMouse uses.
        var mouseXSs = ScaleContext.pxToSs(
            lineComponent.getScoreView().getViewScale().toDocPx(new ViewPx(e.getX())).value());

        // findInsertionIndex over an element head returns that element's index — never
        // on an element, always before N — and every return path is bounded by
        // effectiveElementCount(), so no clamping is needed.
        var index = layoutResult.findInsertionIndex(mouseXSs, line);

        if (lineComponent != targetLineComponent || index != targetIndex) {
            var previous = targetLineComponent;
            targetLineComponent = lineComponent;
            targetIndex = index;

            if (previous != null && previous != lineComponent) {
                previous.repaint();
            }

            lineComponent.repaint();
        }
    }

    /**
     * Inserts the clipboard fragment at the tracked insertion point in one modification
     * bracket. On {@code INSERTED} the mode exits (the clipboard is retained, so another
     * Cmd+V starts a fresh paste); on {@code LINE_FULL} the error is already shown by
     * {@code tryInsertFragment} and the mode stays active for another try.
     *
     * <p>{@code CANCELLED} — the user declined the ending-invalidation confirm — also
     * exits: declining is a decision about the paste, not about this insertion point,
     * unlike the retryable "line full" case.
     */
    private void placeAtTarget() {
        var lineComponent = targetLineComponent;

        if (lineComponent == null || targetIndex < 0) {
            return;
        }

        var line = lineComponent.getLine();
        var controller = getScoreViewController();

        if (line == null || controller == null) {
            return;
        }

        var index = targetIndex;
        var outcome = new ScoreViewController.FragmentInsertOutcome[1];

        // Placement bypasses UIAction.actionPerformed (it is driven by a mouse click
        // or Return keypress, not a Cmd+V dispatch), so the Tier-A op-name capture
        // that PasteAction relies on must be set here around the bracket instead.
        var priorOpName = UndoController.getPendingOpName();
        UndoController.setPendingOpName(Strings.get(Strings.ACTION_EDIT_OP_PASTE));

        try {
            line.withModification(() -> outcome[0] = controller.tryInsertFragment(line, index, null));
        } finally {
            UndoController.setPendingOpName(priorOpName);
        }

        if (outcome[0] == ScoreViewController.FragmentInsertOutcome.INSERTED
                || outcome[0] == ScoreViewController.FragmentInsertOutcome.CANCELLED) {
            exit();
        }
    }
}
