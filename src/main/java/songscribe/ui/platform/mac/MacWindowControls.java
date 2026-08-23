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
 * Measures the macOS window controls — the close, minimize and zoom buttons that
 * macOS draws in the top-left corner of every standard window.
 * <p>
 * An app that draws its own content into the title bar has to place that content
 * itself, and the only correct anchor is where the controls actually end. Their
 * geometry is a system metric that changes between macOS releases, so this class
 * asks the running system rather than assuming a size.
 * <p>
 * <b>FlatLaf cannot supply this number.</b>
 * {@code FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_BOUNDS} reports the
 * strip the controls sit in, not the controls themselves: it pads the width by a
 * trailing inset mirroring the close control's leading inset, and reports
 * {@code x} as 0, so the inset cannot be subtracted back out. On macOS 26.5.2 it
 * reports a width of 78 where the zoom control's right edge is 69. Do not replace
 * this class with that property.
 * <p>
 * <b>macOS only.</b> This class messages AppKit through {@link ObjC}, so callers
 * must guard with {@link com.formdev.flatlaf.util.SystemInfo#isMacOS}.
 */
public final class MacWindowControls {

    /** {@code NSWindowZoomButton}, the green control, which sits last of the three. */
    private static final long NS_WINDOW_ZOOM_BUTTON = 2;

    /** {@code NSWindowStyleMaskTitled}. */
    private static final long NS_WINDOW_STYLE_MASK_TITLED = 1;

    /**
     * {@code NSWindowStyleMaskUtilityWindow}. macOS draws a utility window's
     * controls smaller than a document window's, so such a window must not be
     * measured.
     */
    private static final long NS_WINDOW_STYLE_MASK_UTILITY = 1L << 4;

    /**
     * Right edge of the zoom control on macOS 26, reported until a window exists
     * to measure. The first window is created during {@code pack()}, so this
     * applies only to layout that runs before the app has any window at all.
     */
    private static final double UNMEASURED_ZOOM_CONTROL_RIGHT_EDGE = 69;

    /** The measured edge, or {@code null} until a measurement succeeds. */
    private static @Nullable Double measuredZoomControlRightEdge = null;

    private MacWindowControls() {
    }

    /**
     * Returns the x coordinate of the right edge of the zoom control, in the
     * window's coordinate space, which for x matches Swing's root pane space.
     * <p>
     * This is a system metric, not a property of any one window. Every standard
     * window carries the same control geometry, so the first measurable window
     * answers for all of them, and the answer holds for as long as the app runs.
     * The first successful measurement is kept and returned thereafter.
     * <p>
     * This method reports where the controls are, never whether a given window
     * shows them. A window in full screen hides its controls, and that is the
     * caller's question about its own window, not this class's.
     *
     * @return the right edge in points; before any window exists to measure,
     *         {@link #UNMEASURED_ZOOM_CONTROL_RIGHT_EDGE}
     * @invariant the result is always positive
     * @effects sends Obj-C messages to AppKit inside a fresh autorelease pool;
     *          call on the event dispatch thread
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
     * Measures the first window that carries standard-size controls.
     *
     * <p>{@code -[NSApplication windows]} lists every window the app owns, on
     * screen or not, whether or not the app is active. That makes it usable
     * before the app comes to the front, which {@code -mainWindow} is not.
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
