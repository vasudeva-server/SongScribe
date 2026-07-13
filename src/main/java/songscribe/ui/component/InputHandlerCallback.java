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

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Callback interface for ScoreInputHandler to communicate with ScoreView.
 */
public interface InputHandlerCallback {

    void repaint();

    Control getControl();

    Mode getMode();

    @Nullable JPopupMenu getEditPopup();

    @SuppressWarnings("UnusedReturnValue")
    boolean requestFocusInWindow();

    @Nullable Window getWindow();

    SelectionCoordinator getSelectionCoordinator();

    void selectionChanged();

    /**
     * Extends the active element selection to {@code targetIndex}, keeping the anchor
     * fixed. Shared by shift-click and shift-arrow, which each compute the target index
     * to extend or shrink the selection to.
     */
    void extendSelectionTo(int targetIndex);

    /**
     * Zooms in or out around {@code viewPoint} (in this view's local coordinate
     * space) in response to a Cmd+wheel (macOS) or Ctrl+wheel (other platforms)
     * gesture. {@code preciseWheelRotation} is the raw rotation from the
     * originating {@link MouseWheelEvent}; negative values zoom in, positive
     * values zoom out.
     */
    void zoomByWheel(double preciseWheelRotation, Point viewPoint);

    /**
     * Forwards a non-zoom wheel event to the scroll pane's own default scroll
     * handling. Required because registering a {@code MouseWheelListener} directly
     * on this view makes AWT stop its ancestor search here instead of continuing
     * up to the {@code JScrollPane}, so ordinary scroll gestures would otherwise
     * silently do nothing once a wheel listener is present.
     */
    void forwardWheelScroll(MouseWheelEvent e);
}
