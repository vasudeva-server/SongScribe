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

package songscribe.ui;

import java.awt.Point;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.message.MessageCenter;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;

/**
 * Stateless orchestrator for changing the active score's zoom level.
 * <p>
 * All mutators are EDT-only by contract; no locking.
 */
public final class ZoomController {

    /** Discrete zoom stops, in ascending order, as integer percentages. */
    public static final List<Integer> ZOOM_LEVEL_PERCENTS = List.of(50, 100, 150, 200, 250, 300, 350, 400, 800);

    public static final int MIN_ZOOM_PERCENT = ZOOM_LEVEL_PERCENTS.getFirst();

    public static final int MAX_ZOOM_PERCENT = ZOOM_LEVEL_PERCENTS.getLast();

    public static final int DEFAULT_ZOOM_PERCENT = 100;

    private static final double WHEEL_ZOOM_FACTOR_PER_NOTCH = 0.05;

    private ZoomController() {}

    public static int getZoomPercent() {
        var scoreView = MainFrame.getInstance().getScoreView();

        if (scoreView == null) {
            return DEFAULT_ZOOM_PERCENT;
        }

        return scoreView.getViewScale().getZoomPercent();
    }

    public static void zoomIn() {
        applyZoom(nextLevelAbove(getZoomPercent()), null);
    }

    public static void zoomOut() {
        applyZoom(nextLevelBelow(getZoomPercent()), null);
    }

    public static void resetZoom() {
        applyZoom(DEFAULT_ZOOM_PERCENT, null);
    }

    public static void setZoomPercent(int percent) {
        applyZoom(clampZoomPercent(percent), null);
    }

    /**
     * {@code viewAnchorPoint} is in the active {@link ScoreView}'s local coordinate space.
     */
    public static void setZoomPercent(int percent, Point viewAnchorPoint) {
        applyZoom(clampZoomPercent(percent), viewAnchorPoint);
    }

    /**
     * {@code viewAnchorPoint} is in the active {@link ScoreView}'s local coordinate space.
     */
    public static void zoomByWheel(double preciseWheelRotation, Point viewAnchorPoint) {
        var currentPercent = getZoomPercent();
        var scaleFactor = 1 - preciseWheelRotation * WHEEL_ZOOM_FACTOR_PER_NOTCH;
        var newPercent = (int) Math.round(currentPercent * scaleFactor);

        // A slow trackpad pinch delivers small rotations whose multiplicative change
        // rounds back to the current percent; nudge by one step in the gesture
        // direction so the gesture is never dead — and symmetric between in and out,
        // which half-up rounding alone is not.
        if (newPercent == currentPercent && preciseWheelRotation != 0) {
            newPercent -= (int) Math.signum(preciseWheelRotation);
        }

        setZoomPercent(newPercent, viewAnchorPoint);
    }

    /**
     * {@code viewAnchorPoint} is the viewport center when null.
     */
    public static void zoomByMagnification(double magnification, @Nullable Point viewAnchorPoint) {
        var newPercent = (int) Math.round(getZoomPercent() * (1 + magnification));

        // Calls applyZoom directly rather than setZoomPercent(int, Point): that overload
        // requires a non-null anchor, whereas a pinch anchors at the viewport center
        // (null) whenever the cursor is unavailable or outside the view.
        applyZoom(clampZoomPercent(newPercent), viewAnchorPoint);
    }

    static int nextLevelAbove(int percent) {
        for (var levelPercent : ZOOM_LEVEL_PERCENTS) {
            if (levelPercent > percent) {
                return levelPercent;
            }
        }

        return percent;
    }

    static int nextLevelBelow(int percent) {
        for (var i = ZOOM_LEVEL_PERCENTS.size() - 1; i >= 0; i--) {
            var levelPercent = ZOOM_LEVEL_PERCENTS.get(i);

            if (levelPercent < percent) {
                return levelPercent;
            }
        }

        return percent;
    }

    private static int clampZoomPercent(int percent) {
        return Math.clamp(percent, MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT);
    }

    /**
     * {@code viewAnchorPoint} is the viewport center when null.
     */
    private static void applyZoom(int newZoomPercent, @Nullable Point viewAnchorPoint) {
        var scoreView = MainFrame.getInstance().getScoreView();

        if (scoreView == null) {
            return;
        }

        var oldZoomPercent = scoreView.getViewScale().getZoomPercent();

        if (newZoomPercent == oldZoomPercent) {
            return;
        }

        MessageCenter.post(new ZoomDidChangeNotification(oldZoomPercent, newZoomPercent, viewAnchorPoint));
    }
}
