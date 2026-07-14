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

package songscribe.ui.platform.mac;

import java.awt.MouseInfo;
import java.awt.Point;

import javax.swing.SwingUtilities;

import com.apple.eawt.event.GestureUtilities;
import com.apple.eawt.event.MagnificationEvent;
import com.apple.eawt.event.MagnificationListener;

import org.jspecify.annotations.Nullable;

import songscribe.ui.component.ScoreView;

/**
 * Installs native macOS trackpad-pinch zoom on a {@link ScoreView}.
 * <p>
 * A trackpad pinch produces native {@code NSEvent} magnification, not a
 * scroll-wheel event, so it is delivered through the {@code com.apple.eawt.event}
 * gesture API rather than the wheel path. Each magnify event is routed through the
 * same anchored-zoom machinery as the other zoom paths so pinch behaves identically
 * (continuous, clamped, anchored under the cursor).
 * <p>
 * These classes exist only in the macOS JDK, so this is the <em>only</em> file
 * importing {@code com.apple.eawt.event}, and it must be referenced only on macOS
 * (its single caller is guarded by {@code SystemInfo.isMacOS}) so it is never
 * class-loaded on another platform.
 */
public final class PinchZoomGesture {

    private PinchZoomGesture() {
    }

    /**
     * Attaches a magnification listener that zooms {@code scoreView} in or out
     * around the cursor in response to trackpad-pinch gestures.
     */
    public static void installOn(ScoreView scoreView) {
        MagnificationListener listener = event -> onMagnify(scoreView, event);
        GestureUtilities.addGestureListenerTo(scoreView, listener);
    }

    private static void onMagnify(ScoreView scoreView, MagnificationEvent event) {
        scoreView.zoomByMagnification(event.getMagnification(), cursorAnchor(scoreView));
        event.consume();
    }

    /**
     * Returns the cursor position in {@code scoreView}-local coordinates, or
     * {@code null} to anchor at the viewport center when the pointer is
     * unavailable or falls outside the view.
     */
    @Nullable
    private static Point cursorAnchor(ScoreView scoreView) {
        // A MagnificationEvent carries no location (it is an InputEvent, not a
        // MouseEvent), so — unlike the wheel path, which reads the event's own point —
        // the cursor position must be queried globally and converted to view-local space.
        var pointerInfo = MouseInfo.getPointerInfo();

        if (pointerInfo == null) {
            return null;
        }

        var anchor = pointerInfo.getLocation();
        SwingUtilities.convertPointFromScreen(anchor, scoreView);

        if (!scoreView.contains(anchor)) {
            return null;
        }

        return anchor;
    }
}
