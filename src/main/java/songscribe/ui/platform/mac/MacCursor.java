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
 * made while the pointer is stationary never reaches the screen.
 * <p>
 * <b>macOS only.</b> {@link ObjC}'s static initializer loads native macOS libraries, so callers
 * must guard with {@code SystemInfo.isMacOS}.
 */
public final class MacCursor {

    private MacCursor() {
    }

    /**
     * Deliberately not {@code +[NSCursor hide]}: its hide count must be balanced with
     * {@code unhide}, and one missed unhide leaves the user with no pointer at all. AppKit
     * restores this one on the next mouse movement whatever else happens.
     */
    public static void hideUntilMouseMoves() {
        ObjC.msgSendVoid(ObjC.objcClass("NSCursor"), ObjC.selector("setHiddenUntilMouseMoves:"), true);
    }
}
