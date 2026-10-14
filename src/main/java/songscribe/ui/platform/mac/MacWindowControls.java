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

import org.jspecify.annotations.Nullable;

/**
 * Measures the macOS window controls — the close, minimize and zoom buttons in the top-left
 * corner of every standard window — by asking the running system, because their geometry
 * changes between macOS releases.
 * <p>
 * <b>FlatLaf cannot supply this number.</b>
 * {@code FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS} reports the strip the
 * controls sit in, padded by a trailing inset, with {@code x} as 0 so the inset cannot be
 * subtracted back out. Do not replace this class with that property.
 * <p>
 * <b>macOS only.</b> Callers must guard with
 * {@link com.formdev.flatlaf.util.SystemInfo#isMacOS}.
 */
public final class MacWindowControls {

    private static final long NS_WINDOW_ZOOM_BUTTON = 2;

    private static final long NS_WINDOW_STYLE_MASK_TITLED = 1;

    /**
     * macOS draws a utility window's controls smaller than a document window's, so such a
     * window must not be measured.
     */
    private static final long NS_WINDOW_STYLE_MASK_UTILITY = 1L << 4;

    /** Reported until a window exists to measure. */
    private static final double UNMEASURED_ZOOM_CONTROL_RIGHT_EDGE = 69;

    /** The measured edge, or {@code null} until a measurement succeeds. */
    private static @Nullable Double measuredZoomControlRightEdge = null;

    private MacWindowControls() {
    }

    /**
     * A system metric, not a property of any one window: the first successful measurement is
     * kept for as long as the app runs. Reports where the controls are, never whether a given
     * window shows them (full screen hides them).
     *
     * <p>Call on the event dispatch thread.
     *
     * @return the right edge in points; before any window exists to measure,
     *         {@link #UNMEASURED_ZOOM_CONTROL_RIGHT_EDGE}
     */
    public static double zoomControlRightEdge() {
        var edge = measuredZoomControlRightEdge;

        if (edge == null) {
            edge = measureZoomControlRightEdge();
            measuredZoomControlRightEdge = edge;
        }

        return edge == null ? UNMEASURED_ZOOM_CONTROL_RIGHT_EDGE : edge;
    }

    /**
     * Usable before the app comes to the front, which {@code -mainWindow} is not.
     *
     * @return the right edge in points, or {@code null} when the app owns no
     *         window that carries the controls
     */
    private static @Nullable Double measureZoomControlRightEdge() {
        return ObjC.withAutoreleasePool(() -> {
            var application = ObjC.msgSend(
                ObjC.objcClass("NSApplication"),
                ObjC.selector("sharedApplication")
            );

            var windows = ObjC.msgSend(application, ObjC.selector("windows"));
            var count = ObjC.msgSendLong(windows, ObjC.selector("count"));

            var objectAtIndex = ObjC.selector("objectAtIndex:");
            var styleMask = ObjC.selector("styleMask");
            var standardWindowButton = ObjC.selector("standardWindowButton:");
            var frame = ObjC.selector("frame");

            for (long i = 0; i < count; i++) {
                var window = ObjC.msgSend(windows, objectAtIndex, i);
                var mask = ObjC.msgSendLong(window, styleMask);

                if ((mask & NS_WINDOW_STYLE_MASK_TITLED) == 0 ||
                    (mask & NS_WINDOW_STYLE_MASK_UTILITY) != 0) {
                    continue;
                }

                var control = ObjC.msgSend(window, standardWindowButton, NS_WINDOW_ZOOM_BUTTON);

                if (control.address() != 0) {
                    // -frame is in the superview's space, whose origin is the
                    // window's corner, so the x it reports needs no conversion.
                    return ObjC.msgSendRect(control, frame).maxX();
                }
            }

            return null;
        });
    }
}
