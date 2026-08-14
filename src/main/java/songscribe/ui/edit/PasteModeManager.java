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

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;

import javax.swing.JLayeredPane;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.PasteOverlay;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.undo.UndoController;

/**
 * Paste's half of the "click to place" interaction: the clipboard fragment, what counts as a
 * valid paste index, and the insertion itself. Picking the index is
 * {@link InsertionPointMode}'s, and this class is one of its clients.
 *
 * <p>Cmd+V with no selection calls {@link #enter()}, which puts the score into the
 * insertion-point mode and raises the paste-mode banner. A click, or Return over a tracked
 * point, calls {@code tryInsertFragment}: {@code LINE_FULL} shows an error dialog and declines
 * the point, leaving the mode live for another try, while {@code INSERTED} and {@code CANCELLED}
 * complete it. Escape, a click outside any line, or the app being backgrounded cancel it
 * instead. In every one of those cases the banner comes down in
 * {@link #insertionPointModeDidEnd}, the mode's single end-of-placement callback.
 *
 * <p>Every index the mode's geometry yields is a valid paste index, so
 * {@link #acceptsInsertionIndex} accepts them all — see its contract for why that is a real
 * rule and not a missing one.
 *
 * <p>See section 5 of {@code docs/clipboard.md} for the full state diagram.
 *
 * <p>"Overlay" means two different things here, deliberately kept distinct: the paste-mode
 * <b>overlay</b> ({@link #overlay}, a {@link PasteOverlay}) is a banner in viewport space
 * hosted on {@link MainFrame}'s {@link JLayeredPane} and owned by this class, while the
 * insertion-point <b>marker</b> is a {@code LineOverlayComponent} in page space owned by
 * {@link InsertionPointMode}.
 */
public final class PasteModeManager implements InsertionPointMode.Client {

    // Dependencies
    private final ScoreView scoreView;
    private final InsertionPointMode insertionPointMode;

    // The banner and the layered-pane bounds listener it needs while a paste is pending.
    // Both are created in enter() and torn down in insertionPointModeDidEnd().
    @Nullable
    private PasteOverlay overlay = null;

    @Nullable
    private ComponentListener overlayBoundsListener = null;

    public PasteModeManager(ScoreView scoreView, InsertionPointMode insertionPointMode) {
        this.scoreView = scoreView;
        this.insertionPointMode = insertionPointMode;
    }

    /**
     * Starts a paste placement: enters {@link InsertionPointMode} and raises the paste-mode
     * banner over the score. Called from {@code handlePaste}'s no-selection branch, so the
     * clipboard is already known non-empty and the score already has focus.
     *
     * <p>The banner goes up only after the mode has entered, and only when it entered on this
     * call — a full-bleed layered-pane child added first would shadow the score from the
     * mode's under-the-pointer lookup, and a banner raised while some other client's placement
     * is already pending would never come down.
     */
    public void enter() {
        if (!insertionPointMode.enter(this)) {
            return;
        }

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

    // -------------------------------------------------------------------------
    // InsertionPointMode.Client
    // -------------------------------------------------------------------------

    /**
     * Accepts every index the mode offers. A fragment may be pasted before any element of a
     * line, including before the first and after the last, so paste adds nothing to the
     * geometric rule the mode already applies — the header and the region past the staff's
     * right edge are excluded there, and everything left over is a paste target.
     *
     * <p>Whether the fragment actually fits is not decided here: it depends on the fragment's
     * width, which is measured by {@code tryInsertFragment} once a point has been chosen, and
     * a "line full" answer is retryable rather than a reason to refuse to track the point.
     *
     * @param line the line the pointer is over
     * @param index the insertion index under the pointer
     * @return {@code true}, always
     */
    @Override
    public boolean acceptsInsertionIndex(Line line, int index) {
        return true;
    }

    /**
     * Inserts the clipboard fragment at the chosen point in one modification bracket.
     *
     * <p>{@code LINE_FULL} declines the point: the error is already shown by
     * {@code tryInsertFragment} and nothing was mutated, so the mode stays live and the user
     * can pick a roomier spot. {@code EMPTY} declines it for the same reason — nothing was
     * mutated — leaving the placement pending rather than silently consuming the gesture.
     * {@code INSERTED} completes the placement, with the clipboard retained so another Cmd+V
     * starts a fresh paste. {@code CANCELLED} — the user declined the ending-invalidation
     * confirm — completes it too: declining is a decision about the paste, not about this
     * insertion point, unlike the retryable "line full" case.
     *
     * @param line the line the user picked
     * @param index the insertion index within {@code line}
     * @return {@link InsertionPointMode.Placement#DECLINED} when nothing was inserted and the
     *     user may try again, {@link InsertionPointMode.Placement#COMPLETED} otherwise
     */
    @Override
    public InsertionPointMode.Placement insertionPointChosen(Line line, int index) {
        var controller = scoreView.getController();

        if (controller == null) {
            return InsertionPointMode.Placement.DECLINED;
        }

        // Placement bypasses UIAction.actionPerformed (it is driven by a mouse click
        // or Return keypress, not a Cmd+V dispatch), so the Tier-A op-name capture
        // that PasteAction relies on must be set here around the bracket instead.
        var outcome = UndoController.withPendingOpNameResult(
            Strings.get(Strings.ACTION_EDIT_OP_PASTE),
            () -> line.withModificationResult(() -> controller.tryInsertFragment(line, index, null)));

        return switch (outcome) {
            case LINE_FULL, EMPTY -> InsertionPointMode.Placement.DECLINED;
            case INSERTED, CANCELLED -> InsertionPointMode.Placement.COMPLETED;
        };
    }

    /**
     * Takes the paste-mode banner and its bounds listener back down, whether the paste was
     * placed or abandoned. Both are dropped from the fields first, so a second call — which the
     * mode's exactly-once promise rules out, but which costs nothing to survive — finds nothing
     * to remove rather than removing someone else's overlay.
     */
    @Override
    public void insertionPointModeDidEnd(InsertionPointMode.EndReason reason) {
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
}
