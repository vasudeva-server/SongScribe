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

import org.jspecify.annotations.Nullable;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.ui.platform.mac.MacCursor;
import songscribe.util.Debounce;

/**
 * Takes the system cursor away once the pointer has settled over the hover preview, and gives it
 * back the instant the pointer moves again.
 * <p>
 * Driven from the mouse alone. Every other thing that rebuilds the preview (a tool change, a song
 * edit) does so without the pointer having moved, and the cursor's visibility is not theirs to
 * change.
 */
final class PreviewCursorHider {

    /**
     * How long the pointer must stay put before the cursor is hidden over the hover preview.
     * Long enough that the arrow stays available all the while the mouse is in motion, short
     * enough that it is out of the way by the time the user is looking at the ghost they are
     * about to place.
     */
    private static final int CURSOR_HIDE_DWELL_MS = 100;

    /**
     * How often the dwell deadline is tested. Bounds how late the hide can be
     * ({@link #CURSOR_HIDE_DWELL_MS} plus this), which for a cursor that is about to disappear
     * anyway is well below noticing.
     */
    private static final int CURSOR_HIDE_POLL_MS = 25;

    /**
     * Runs once the pointer has been still for {@link #CURSOR_HIDE_DWELL_MS}, retriggered by
     * every mouse move so it never fires while the mouse is moving. Created on first use:
     * headless converters install no overlay and never arm it.
     */
    @Nullable
    private static Debounce cursorHideDwell = null;

    /**
     * The pointer's screen position as of the last event treated as a real mouse move, held as
     * loose ints so the per-move comparison costs no allocation.
     * <p>
     * An event that lands on the pixel the pointer is already on is not a move:
     * {@link PreviewElementManager#restorePreviewElement} synthesizes MOUSE_MOVED at the current
     * position for reasons that have nothing to do with the user's hand, and AWT re-delivers every
     * real move a second time. Either would otherwise bring the cursor back and re-arm the dwell
     * that is meant to be taking it away.
     */
    private static int lastMouseScreenXPx = 0;

    /** @see #lastMouseScreenXPx */
    private static int lastMouseScreenYPx = 0;

    /** Whether {@link #lastMouseScreenXPx}/{@link #lastMouseScreenYPx} hold a real position yet. */
    private static boolean hasLastMouseScreenPoint = false;

    private PreviewCursorHider() {
    }

    /**
     * Restores the system cursor and retriggers the deadline that takes it away again.
     * <p>
     * Called from {@link PreviewElementManager#trackMouse} on every mouse move over a line,
     * whether or not the preview itself moved: the arrow comes back the instant the mouse moves,
     * and each move pushes the deadline out, so the hide can only land once the pointer has
     * settled.
     */
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
     * Hides the system cursor over the preview, if there is still a preview to obscure.
     * <p>
     * The visibility check covers a preview taken down between the deadline being armed and this
     * running: the mouse can leave the line, or anything can clear the preview element, while it
     * is pending. There would be nothing left to obscure, and the cursor would stay suppressed
     * until the next mouse move.
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
     * Drops any pending cursor-hide dwell, along with the pointer position it was armed against.
     * <p>
     * Called wherever the overlays are replaced or cleared. A pending dwell resolves the preview
     * overlay when it fires rather than when it was armed, so one left outstanding across a swap
     * would act on whichever overlay happens to be installed by then — the next test's, or the
     * next converter host's. The remembered position has to go with it: leave it behind and the
     * first move afterwards can land on the same screen pixel, read as a duplicate, and be
     * ignored.
     */
    static void discard() {
        if (cursorHideDwell != null) {
            cursorHideDwell.cancel();
            cursorHideDwell = null;
        }

        hasLastMouseScreenPoint = false;
    }
}
