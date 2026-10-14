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
     * A press on the score always gives the score focus, so callers do this before any mode or
     * state guard: otherwise score key bindings would be typed into the lyric editor. The
     * double-click path opens the editor after this has run.
     */
    void takeFocus();

    /**
     * Cancels a pending insertion-point placement and clears the selection. Does nothing when no
     * placement is in progress and the view is not in select mode. Leaves focus alone.
     * <p>
     * The selection is cleared directly, not by a deselect command, whose handler acts only
     * while the score holds focus.
     */
    void cancelPlacementAndDeselect();

    /**
     * Extends the active element selection to {@code targetIndex}, keeping the anchor
     * fixed.
     */
    void extendSelectionTo(int targetIndex);

    /**
     * A selection holding no note, and no selection at all, are no-ops.
     */
    void editLyricOnSelection();

    /**
     * Negative {@code preciseWheelRotation} zooms in, positive zooms out.
     */
    void zoomByWheel(double preciseWheelRotation, Point viewPoint);

    /**
     * {@code viewPoint} is {@code null} to anchor at the viewport center. Positive
     * {@code magnification} zooms in, negative zooms out.
     */
    void zoomByMagnification(double magnification, @Nullable Point viewPoint);

    /**
     * A {@code MouseWheelListener} on this view makes AWT stop its ancestor search here instead
     * of reaching the {@code JScrollPane}, so ordinary scrolling needs this forward.
     */
    void forwardWheelScroll(MouseWheelEvent e);
}
