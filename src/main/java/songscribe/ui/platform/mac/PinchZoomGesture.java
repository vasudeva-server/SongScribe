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
 * {@code com.apple.eawt.event} exists only in the macOS JDK, so this is the <em>only</em> file
 * importing it, and it must be referenced only on macOS (its single caller is guarded by
 * {@code SystemInfo.isMacOS}).
 */
public final class PinchZoomGesture {

    private PinchZoomGesture() {
    }

    public static void installOn(ScoreView scoreView) {
        MagnificationListener listener = event -> onMagnify(scoreView, event);
        GestureUtilities.addGestureListenerTo(scoreView, listener);
    }

    private static void onMagnify(ScoreView scoreView, MagnificationEvent event) {
        scoreView.zoomByMagnification(event.getMagnification(), cursorAnchor(scoreView));
        event.consume();
    }

    /** {@code null} when the pointer is unavailable or outside the view. */
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
