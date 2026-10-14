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

package songscribe.ui.component.score;

import java.awt.event.MouseEvent;

import com.formdev.flatlaf.util.SystemInfo;
import org.jspecify.annotations.Nullable;

import songscribe.ui.platform.mac.MacCursor;
import songscribe.util.Debounce;

/**
 * Takes the system cursor away once the pointer has settled over the hover preview, and gives it
 * back the instant the pointer moves again. Driven from the mouse alone: a tool change or song
 * edit rebuilds the preview without the pointer having moved and is not to change the cursor.
 */
final class PreviewCursorHider {

    /**
     * Long enough that the arrow stays available while the mouse is in motion, short enough that
     * it is gone by the time the user looks at the ghost they are about to place.
     */
    private static final int CURSOR_HIDE_DWELL_MS = 100;

    /**
     * Bounds how late the hide can be: {@link #CURSOR_HIDE_DWELL_MS} plus this.
     */
    private static final int CURSOR_HIDE_POLL_MS = 25;

    /**
     * Retriggered by every mouse move so it never fires while the mouse is moving. Created on
     * first use: headless converters never arm it.
     */
    @Nullable
    private static Debounce cursorHideDwell = null;

    /**
     * An event on the pixel the pointer is already on is not a move:
     * {@link PreviewElementManager#restorePreviewElement} synthesizes MOUSE_MOVED at the current
     * position, and AWT re-delivers every real move; either would bring the cursor back and
     * re-arm the dwell.
     */
    private static int lastMouseScreenXPx = 0;

    private static int lastMouseScreenYPx = 0;

    private static boolean hasLastMouseScreenPoint = false;

    private PreviewCursorHider() {
    }

    static void cursorDidMove(MouseEvent e) {
        var previewOverlay = PreviewOverlayRegistry.getOverlay();

        if (previewOverlay == null) {
            return;
        }

        // Screen rather than component coordinates, so a move that scrolls the score under a
        // stationary pointer still reads as "not a move". Both are plain field reads on a real
        // AWT event — the peer fills them in at construction.
        var screenXPx = e.getXOnScreen();
        var screenYPx = e.getYOnScreen();

        if (hasLastMouseScreenPoint && screenXPx == lastMouseScreenXPx && screenYPx == lastMouseScreenYPx) {
            return;
        }

        hasLastMouseScreenPoint = true;
        lastMouseScreenXPx = screenXPx;
        lastMouseScreenYPx = screenYPx;
        previewOverlay.setHidesCursor(false);

        var dwell = cursorHideDwell;

        if (dwell == null) {
            dwell = Debounce.polling(
                CURSOR_HIDE_DWELL_MS,
                CURSOR_HIDE_POLL_MS,
                PreviewCursorHider::hideCursorOverPreview
            );
            cursorHideDwell = dwell;
        }

        dwell.trigger();
    }

    /**
     * The visibility check covers a preview taken down while the deadline was pending; the cursor
     * would otherwise stay suppressed until the next mouse move.
     */
    private static void hideCursorOverPreview() {
        var previewOverlay = PreviewOverlayRegistry.getOverlay();

        if (previewOverlay == null || !previewOverlay.isVisible()) {
            return;
        }

        previewOverlay.setHidesCursor(true);

        // The Swing-level hide above is what every other platform acts on, but macOS applies a
        // cursor change only in a cursor-update context, so one made while the pointer is standing
        // still — which a dwell-driven hide always is — never reaches the screen. Ask AppKit
        // directly instead; it also restores the pointer on the next movement by itself, which is
        // the same rule cursorDidMove applies on the Swing side.
        if (SystemInfo.isMacOS) {
            MacCursor.hideUntilMouseMoves();
        }
    }

    /**
     * Call wherever the overlays are replaced or cleared. A pending dwell resolves the overlay
     * when it fires, so one outstanding across a swap would act on whichever overlay is then
     * installed. The remembered position goes too, or the first move afterwards can read as a
     * duplicate and be ignored.
     */
    static void discard() {
        if (cursorHideDwell != null) {
            cursorHideDwell.cancel();
            cursorHideDwell = null;
        }

        hasLastMouseScreenPoint = false;
    }
}
