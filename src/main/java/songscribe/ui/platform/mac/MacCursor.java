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

/**
 * AppKit's own cursor visibility, for the one thing Swing cannot express: hiding the pointer
 * while it is standing still.
 * <p>
 * macOS applies a cursor change only in a cursor-update context, so a {@code Component.setCursor}
 * made while the pointer is stationary never reaches the screen — verified directly, with neither
 * a transparent nor an unmistakable built-in cursor appearing while AWT reported the component
 * under the pointer carrying exactly the cursor that had been set.
 * <p>
 * <b>macOS only.</b> {@link ObjC}'s static initializer loads native macOS libraries, so callers
 * must guard with {@code SystemInfo.isMacOS}.
 */
public final class MacCursor {

    private MacCursor() {
    }

    /**
     * Hides the pointer until the mouse next moves, via
     * {@code +[NSCursor setHiddenUntilMouseMoves:YES]}.
     * <p>
     * Deliberately not {@code +[NSCursor hide]}: that keeps a hide count that every caller must
     * balance with {@code unhide}, and a single missed unhide — an app deactivated mid-hide, a
     * window closed, an exception on the path back — leaves the user with no pointer at all and
     * no way to get it back. AppKit restores this one on the next mouse movement whatever else
     * happens, which is also exactly the visibility rule wanted here.
     */
    public static void hideUntilMouseMoves() {
        ObjC.msgSendVoid(ObjC.objcClass("NSCursor"), ObjC.selector("setHiddenUntilMouseMoves:"), true);
    }
}
