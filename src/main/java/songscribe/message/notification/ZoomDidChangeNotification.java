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
 * {@code ScoreView} applies the change from a handler at {@link #APPLY_PRIORITY}. Any other
 * listener MUST use a priority strictly less than {@link #APPLY_PRIORITY} (a bare
 * {@code @Handler} does), or it may run before the zoom is applied. Only a handler that must
 * see the score before the zoom uses {@link #BEFORE_APPLY_PRIORITY}.
 * <p>
 * A handler runs mid-change, before the view has re-laid out. Work that needs the settled zoom
 * observes the view's zoom property instead, which reports after re-layout.
 */
public class ZoomDidChangeNotification extends Message {

    public static final int APPLY_PRIORITY = Message.HIGH_PRIORITY;

    public static final int BEFORE_APPLY_PRIORITY = APPLY_PRIORITY + 1;

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

    /** The zoom anchor in the active {@code ScoreView}'s coordinates, or null for the viewport's horizontal center and top edge. */
    @Nullable
    public Point getAnchorPoint() {
        return anchorPoint;
    }
}
