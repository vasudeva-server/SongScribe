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

import org.jspecify.annotations.Nullable;

import songscribe.message.MessageCenter;
import songscribe.message.notification.ZoomDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;

/**
 * Stateless orchestrator for changing the active score's zoom level.
 * <p>
 * Zoom is expressed as an integer percentage, with 100% being the default
 * scale. The discrete zoom stops live in {@link #ZOOM_LEVEL_PERCENTS}. This
 * class holds no zoom state: the {@link ScoreView}'s {@code ViewScale} is the
 * sole source of truth and level stepping is done with pure functions. Each
 * mutator resolves the active {@code ScoreView} via
 * {@link MainFrame#getInstance()}{@code .getScoreView()} to read its current
 * percent and no-ops silently when there is none. When the percent actually
 * changes, this class does not apply it directly — it posts a
 * {@link ZoomDidChangeNotification} carrying the requested percent and anchor;
 * {@code ScoreView} applies the change itself from a high-priority handler of
 * that message (see {@code ScoreView.zoomDidChangeApplyZoom}), so every
 * reactor — the score tree included — goes through the same single message
 * rather than a mix of direct calls and bus notifications.
 * <p>
 * All mutators are EDT-only by contract; they perform no locking. Callers must
 * invoke them on the AWT event-dispatch thread.
 */
public final class ZoomController {

    /** Discrete zoom stops, in ascending order, as integer percentages. */
    public static final int[] ZOOM_LEVEL_PERCENTS = {50, 100, 150, 200, 250, 300, 350, 400, 800};

    /** Lowest selectable zoom percent. */
    public static final int MIN_ZOOM_PERCENT = ZOOM_LEVEL_PERCENTS[0];

    /** Highest selectable zoom percent. */
    public static final int MAX_ZOOM_PERCENT = ZOOM_LEVEL_PERCENTS[ZOOM_LEVEL_PERCENTS.length - 1];

    /** Default zoom percent applied to a freshly opened document. */
    public static final int DEFAULT_ZOOM_PERCENT = 100;

    /**
     * Fractional zoom change applied per unit of {@link java.awt.event.MouseWheelEvent#getPreciseWheelRotation()}
     * during Ctrl+wheel/trackpad-pinch zoom. Multiplicative rather than a fixed
     * percent-point step, so the same gesture feels proportionally consistent at
     * any zoom level.
     * <p>
     * Package-private so {@code ZoomControllerTest} can derive sub-step rotations
     * without duplicating the literal.
     */
    static final double WHEEL_ZOOM_FACTOR_PER_NOTCH = 0.05;

    private ZoomController() {}

    /**
     * Returns the active view's current zoom percent, falling back to
     * {@link #DEFAULT_ZOOM_PERCENT} when there is no active {@link ScoreView}.
     */
    public static int getZoomPercent() {
        var scoreView = MainFrame.getInstance().getScoreView();

        if (scoreView == null) {
            return DEFAULT_ZOOM_PERCENT;
        }

        return scoreView.getViewScale().getZoomPercent();
    }

    /**
     * Steps up to the next discrete zoom stop above the current percent. No-op
     * when already at or above the maximum. EDT-only.
     */
    public static void zoomIn() {
        applyZoom(nextLevelAbove(getZoomPercent()), null);
    }

    /**
     * Steps down to the next discrete zoom stop below the current percent.
     * No-op when already at or below the minimum. EDT-only.
     */
    public static void zoomOut() {
        applyZoom(nextLevelBelow(getZoomPercent()), null);
    }

    /** Resets the zoom to the default 100%. EDT-only. */
    public static void resetZoom() {
        applyZoom(DEFAULT_ZOOM_PERCENT, null);
    }

    /**
     * Sets the zoom to {@code percent}, clamped into
     * {@code [MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT]}. Entry point for the
     * status-bar percent menu. EDT-only.
     */
    public static void setZoomPercent(int percent) {
        applyZoom(clampZoomPercent(percent), null);
    }

    /**
     * Sets the zoom to {@code percent}, clamped into
     * {@code [MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT]}, anchored around
     * {@code viewAnchorPoint} (in the active {@link ScoreView}'s local coordinate
     * space) rather than the viewport center. EDT-only.
     */
    public static void setZoomPercent(int percent, Point viewAnchorPoint) {
        applyZoom(clampZoomPercent(percent), viewAnchorPoint);
    }

    /**
     * Zooms in or out around {@code viewAnchorPoint} (in the active
     * {@link ScoreView}'s local coordinate space) in response to a Ctrl+wheel or
     * trackpad-pinch gesture. {@code preciseWheelRotation} is the raw rotation
     * from the originating {@link java.awt.event.MouseWheelEvent}: negative zooms
     * in, positive zooms out. No-op when there is no active view. EDT-only.
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
     * Zooms in or out around {@code viewAnchorPoint} (or the viewport center when
     * null) in response to a native macOS trackpad-pinch magnification gesture.
     * {@code magnification} is the per-event delta from a {@code MagnificationEvent}:
     * positive zooms in, negative zooms out. The delta is already a proportional
     * fraction of the current scale, so it is applied directly as {@code 1 + magnification}
     * with no {@link #WHEEL_ZOOM_FACTOR_PER_NOTCH}-style sensitivity constant. Unlike
     * {@link #zoomByWheel}, there is no dead-gesture nudge — a pinch fires many events, so
     * events whose proportional change rounds back to the current percent are filtered by
     * {@link #applyZoom}'s unchanged-percent guard. No-op when there is no active view.
     * EDT-only.
     */
    public static void zoomByMagnification(double magnification, @Nullable Point viewAnchorPoint) {
        var newPercent = (int) Math.round(getZoomPercent() * (1 + magnification));

        // Calls applyZoom directly rather than setZoomPercent(int, Point): that overload
        // requires a non-null anchor, whereas a pinch anchors at the viewport center
        // (null) whenever the cursor is unavailable or outside the view.
        applyZoom(clampZoomPercent(newPercent), viewAnchorPoint);
    }

    /**
     * Returns the next discrete zoom stop strictly above {@code percent}, or
     * {@code percent} unchanged when already at or above the maximum. Pure.
     */
    static int nextLevelAbove(int percent) {
        for (var levelPercent : ZOOM_LEVEL_PERCENTS) {
            if (levelPercent > percent) {
                return levelPercent;
            }
        }

        return percent;
    }

    /**
     * Returns the next discrete zoom stop strictly below {@code percent}, or
     * {@code percent} unchanged when already at or below the minimum. Pure.
     */
    static int nextLevelBelow(int percent) {
        for (var i = ZOOM_LEVEL_PERCENTS.length - 1; i >= 0; i--) {
            if (ZOOM_LEVEL_PERCENTS[i] < percent) {
                return ZOOM_LEVEL_PERCENTS[i];
            }
        }

        return percent;
    }

    /** Clamps {@code percent} into {@code [MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT]}. Pure. */
    private static int clampZoomPercent(int percent) {
        return Math.clamp(percent, MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT);
    }

    /**
     * Posts a {@link ZoomDidChangeNotification} requesting {@code newZoomPercent} on the
     * active {@link ScoreView}, anchored around {@code viewAnchorPoint} (or the viewport
     * center when null). No-ops silently when there is no active view, or when the percent
     * is unchanged. Does not apply the zoom itself — see {@link ZoomDidChangeNotification}.
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
