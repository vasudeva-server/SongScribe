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

package songscribe.message.notification;

import java.awt.Point;

import org.jspecify.annotations.Nullable;

import songscribe.message.Message;

/**
 * Posted by {@link songscribe.ui.ZoomController} to both request and report a zoom change.
 * <p>
 * {@code ScoreView} applies the change itself, from a {@code @Handler(priority =
 * Message.HIGH_PRIORITY)} handler of this same message — see {@code
 * ScoreView.zoomDidChangeApplyZoom}. Every other handler (status bar, action enablement, the
 * active lyric editor, overlay bounds) reacts to an already-applied change, so <b>any new
 * listener to this message MUST use a handler priority strictly less than
 * {@code Message.HIGH_PRIORITY}</b> — a bare {@code @Handler} (priority 0) satisfies this. A
 * handler at {@code HIGH_PRIORITY} or above races {@code ScoreView}'s and may run before the
 * zoom is actually applied.
 */
public class ZoomDidChangeNotification extends Message {

    private final int oldZoomPercent;
    private final int newZoomPercent;

    @Nullable
    private final Point anchorPoint;

    public ZoomDidChangeNotification(int oldZoomPercent, int newZoomPercent, @Nullable Point anchorPoint) {
        this.oldZoomPercent = oldZoomPercent;
        this.newZoomPercent = newZoomPercent;
        this.anchorPoint = anchorPoint;
    }

    public int getOldZoomPercent() {
        return oldZoomPercent;
    }

    public int getNewZoomPercent() {
        return newZoomPercent;
    }

    /**
     * The zoom anchor in the active {@code ScoreView}'s local coordinate space, or null to
     * anchor at the viewport's horizontal center and top edge (menu/keyboard zoom).
     */
    @Nullable
    public Point getAnchorPoint() {
        return anchorPoint;
    }
}
