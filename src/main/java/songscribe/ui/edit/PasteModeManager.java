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
import songscribe.dom.ViewPx;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.message.MessageCenter;
import songscribe.message.notification.PasteModeDidChangeNotification;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.PasteOverlay;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.component.score.InsertionMarkerOverlay;
import songscribe.ui.component.score.LineComponent;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.undo.UndoController;
import songscribe.util.UIUtils;

/**
 * Manages the paste-mode state machine — placement of a clipboard fragment by
 * clicking (or pressing Return over) an insertion point on a line.
 *
 * <p>Cmd+V with no selection moves INACTIVE to ACTIVE: the paste-mode overlay appears, every action is
 * disabled, and the preview element is suppressed. Mouse movement over a line resolves an insertion
 * index and retargets the insertion marker overlay. A click, or Return over a tracked point, calls
 * {@code tryInsertFragment}; LINE_FULL shows an error dialog and stays ACTIVE, while INSERTED exits
 * with the clipboard retained. Escape, a click outside any line, or the app being backgrounded also
 * exit. While ACTIVE, presses on a line are inert — no line select, no lyric select, no pitch drag —
 * so the click that follows is always a placement or a cancel.
 *
 * <p>See section 5 of {@code docs/clipboard.md} for the full state diagram.
 *
 * <p>All exits route through the single {@link #exit()} funnel, mirroring
 * {@code GraceModeManager.finish(boolean)}. Open-coding teardown per exit path
 * would be five paths of duplicated steps, and a missed listener removal is
 * invisible — each enter/exit cycle would leak a live listener on the layered
 * pane. Mirrors {@link GraceModeManager}'s single-flag / static-instance shape.
 * <p>
 * "Overlay" means two different things in this class, deliberately kept distinct: the
 * paste-mode <b>overlay</b> ({@link #overlay}, a {@link PasteOverlay}) is a banner in viewport space
 * hosted on {@link MainFrame}'s {@link JLayeredPane}, while the insertion-point <b>marker</b>
 * ({@link #insertionMarkerOverlay}, an {@code InsertionMarkerOverlay}) is a
 * {@code LineOverlayComponent} in page space, hosted by {@link ScoreView} and sized to exactly
 * the ink it draws.
 */
public final class PasteModeManager {

    @Nullable
    private static PasteModeManager instance = null;

    // Dependencies
    private final ScoreView scoreView;

    // State
    private boolean active = false;

    // The insertion point the next placement will use — the line and the index
    // before which the fragment lands. Tracked from mouse movement; -1 index means
    // the mouse has not yet entered a line, so Return is a no-op.
    @Nullable
    private LineComponent targetLineComponent;

    private int targetIndex = -1;

    // The overlay and the layered-pane bounds listener it needs while active.
    // Both are created in enter() and torn down in the single exit() funnel.
    @Nullable
    private PasteOverlay overlay;

    @Nullable
    private ComponentListener overlayBoundsListener;

    // The insertion-point marker. One instance for the lifetime of this manager (and thus of
    // the owning ScoreView) — retargeted and shown/hidden below rather than recreated.
    private final InsertionMarkerOverlay insertionMarkerOverlay;

    public PasteModeManager(ClipboardManager clipboardManager, ScoreView scoreView) {
        this.scoreView = scoreView;
        insertionMarkerOverlay = new InsertionMarkerOverlay(scoreView);
        instance = this;
    }

    /**
     * Registers the insertion marker as a child of the owning view. Deliberately not done in
     * the constructor: this manager is built from {@code ScoreView}'s constructor, before the
     * view has a layout manager to register an absolute-bounds child against, and headless
     * views never acquire one at all. {@code ScoreView.init()} calls this on the interactive
     * path only, mirroring {@link PreviewElementManager#installOverlay}.
     */
    public void installOverlay() {
        scoreView.addOverlay(insertionMarkerOverlay);
    }

    /**
     * Returns whether any PasteModeManager instance is currently active.
     * Used by UIAction.enableFromPasteMode() to check paste-mode status.
     */
    public static boolean isActive() {
        return instance != null && instance.active;
    }

    public boolean isInProgress() {
        return active;
    }

    /**
     * Returns the active {@link PasteModeManager} instance, or null when paste mode is
     * not in progress.
     */
    @Nullable
    public static PasteModeManager getActiveInstance() {
        return instance != null && instance.active ? instance : null;
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

    /** Sets the singleton instance; intended for test teardown only. */
    static void setInstance(@Nullable PasteModeManager value) {
        instance = value;
    }

    /** Returns the insertion-point marker overlay. Package-private: test support only. */
    InsertionMarkerOverlay getInsertionMarkerOverlay() {
        return insertionMarkerOverlay;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Enters paste mode. Called from {@code handlePaste}'s no-selection branch, so
     * the clipboard is already known non-empty and the score already has focus.
     * <p>
     * {@link #syncTargetToMouse()} shows the insertion marker on the spot when a target is
     * already resolvable (the mouse is already over a line), by routing through
     * {@link #updateTarget}, the same path a real {@code mouseMoved} takes.
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
     * {@code mouseMoved} event. Must run before the overlay is added to
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
     * the notification (via {@link #setActive}), and drop the tracked insertion point
     * (via {@link #clearTarget}, which also hides the insertion marker).
     */
    private void exit() {
        setActive(false);
        clearTarget();

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
     * Drops the tracked insertion point when the mouse leaves the line that owns it, so
     * the marker disappears while the pointer is between lines or off the score entirely.
     * Guarded on identity: when the pointer crosses directly into another line, that line's
     * {@code mouseMoved} may arrive first, and this exit must not undo it.
     */
    public void mouseExited(LineComponent lineComponent) {
        if (active && lineComponent == targetLineComponent) {
            clearTarget();
        }
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
     * Drops the tracked insertion point and hides the insertion marker. Paste mode stays
     * active — Return simply becomes a no-op again until the mouse re-enters a valid insertion
     * position.
     */
    private void clearTarget() {
        targetLineComponent = null;
        targetIndex = -1;
        insertionMarkerOverlay.setTarget(null, -1);
    }

    /**
     * Recomputes the insertion index under the mouse and, when it or the line changes,
     * retargets the insertion marker. {@code InsertionMarkerOverlay}'s height is identical on
     * every line, so a line change only ever repositions the marker — Swing dirties its old and
     * new bounds automatically when it moves, with no explicit repaint call needed.
     */
    private void updateTarget(LineComponent lineComponent, MouseEvent e) {
        var line = lineComponent.getLine();
        var layoutResult = lineComponent.getLayoutResult();

        if (line == null || layoutResult == null) {
            return;
        }

        var mouseXSs = lineComponent.getScoreView().getViewScale().toSs(new ViewPx(e.getX())).value();

        // Nothing can be inserted into the staff header or past the staff's right edge,
        // so outside that span there is no insertion point to show.
        if (HorizontalSpacingCalculator.isWithinHeaderXSs(mouseXSs, line)
            || mouseXSs > line.getSong().getLineWidthSs()) {
            clearTarget();
            return;
        }

        // findInsertionIndex over an element head returns that element's index — never
        // on an element, always before N — and every return path is bounded by
        // effectiveElementCount(), so no clamping is needed.
        var index = layoutResult.findInsertionIndex(mouseXSs, line);

        if (lineComponent != targetLineComponent || index != targetIndex) {
            targetLineComponent = lineComponent;
            targetIndex = index;
            insertionMarkerOverlay.setTarget(lineComponent, index);
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
        // Placement bypasses UIAction.actionPerformed (it is driven by a mouse click
        // or Return keypress, not a Cmd+V dispatch), so the Tier-A op-name capture
        // that PasteAction relies on must be set here around the bracket instead.
        var priorOpName = UndoController.getPendingOpName();
        UndoController.setPendingOpName(Strings.get(Strings.ACTION_EDIT_OP_PASTE));
        ScoreViewController.FragmentInsertOutcome outcome;

        try {
            outcome = line.withModificationResult(() -> controller.tryInsertFragment(line, index, null));
        } finally {
            UndoController.setPendingOpName(priorOpName);
        }

        if (outcome == ScoreViewController.FragmentInsertOutcome.INSERTED
                || outcome == ScoreViewController.FragmentInsertOutcome.CANCELLED) {
            exit();
        }
    }
}
