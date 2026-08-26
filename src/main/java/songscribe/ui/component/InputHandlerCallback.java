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

import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseWheelEvent;

import org.jspecify.annotations.Nullable;

import songscribe.ui.Mode;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Callback interface for ScoreInputHandler to communicate with ScoreView.
 * <p>
 * {@link #takeFocus()} and {@link #cancelPlacementAndDeselect()} also carry a second
 * contract: {@code ScoreComponent} calls both directly on the concrete {@code ScoreView},
 * outside this interface entirely.
 */
public interface InputHandlerCallback {

    void repaint();

    Mode getMode();



    @Nullable Window getWindow();

    SelectionCoordinator getSelectionCoordinator();

    void selectionChanged();

    /**
     * Gives the score view focus, in response to a press anywhere on the score surface.
     * <p>
     * A press on the score always gives the score focus, with no exceptions, which is why
     * callers do this before any mode or state guard: without it a user who was typing in
     * the lyric editor and presses back onto the score would have score key bindings typed
     * into the lyric instead. The lyric editor still takes focus on the double-click path,
     * which opens the editor after this has run.
     *
     * @effects requests focus for the score view; touches no selection or edit-mode state
     */
    void takeFocus();

    /**
     * Cancels a pending insertion-point placement and clears the selection — what a click
     * nothing else claimed does.
     * <p>
     * A click reaches this whether it landed on the view's own background or on a score
     * component that opened no editor, so both routes leave the view in the same state.
     * When no placement is in progress and the view is not in select mode, it does nothing.
     * <p>
     * The selection is cleared directly rather than by posting a deselect command: that
     * command's handler acts only while the score holds focus, an arbitration the menu
     * action and the Escape key need because they fire while something else may hold focus.
     * A click on the score is itself proof of the target, so it needs none.
     *
     * @effects cancels the insertion-point placement when one is in progress; clears the
     *          selection when the view is in select mode; leaves focus alone
     */
    void cancelPlacementAndDeselect();

    /**
     * Extends the active element selection to {@code targetIndex}, keeping the anchor
     * fixed. Shared by shift-click and shift-arrow, which each compute the target index
     * to extend or shrink the selection to.
     */
    void extendSelectionTo(int targetIndex);

    /**
     * Opens the lyric editor on the first note in the current selection, in response to
     * Return/Enter. A selection holding no note, and no selection at all, are both no-ops.
     */
    void editLyricOnSelection();

    /**
     * Zooms in or out around {@code viewPoint} (in this view's local coordinate
     * space) in response to a Cmd+wheel (macOS) or Ctrl+wheel (other platforms)
     * gesture. {@code preciseWheelRotation} is the raw rotation from the
     * originating {@link MouseWheelEvent}; negative values zoom in, positive
     * values zoom out.
     */
    void zoomByWheel(double preciseWheelRotation, Point viewPoint);

    /**
     * Zooms in or out around {@code viewPoint} (ScoreView-local coordinates, or
     * {@code null} to anchor at the viewport center) in response to a native macOS
     * trackpad-pinch magnification gesture. {@code magnification} is the per-event
     * delta from {@link com.apple.eawt.event.MagnificationEvent}; positive zooms
     * in, negative zooms out.
     */
    void zoomByMagnification(double magnification, @Nullable Point viewPoint);

    /**
     * Forwards a non-zoom wheel event to the scroll pane's own default scroll
     * handling. Required because registering a {@code MouseWheelListener} directly
     * on this view makes AWT stop its ancestor search here instead of continuing
     * up to the {@code JScrollPane}, so ordinary scroll gestures would otherwise
     * silently do nothing once a wheel listener is present.
     */
    void forwardWheelScroll(MouseWheelEvent e);
}
