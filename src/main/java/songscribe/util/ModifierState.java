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
package songscribe.util;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModifierState {

    private static final Logger LOG = LoggerFactory.getLogger(ModifierState.class);

    private ModifierState() {
    }

    // ==================== macOS ====================
    @SuppressWarnings("InterfaceNeverImplemented")
    @FunctionalInterface
    private interface ApplicationServices extends Library {
        ApplicationServices INSTANCE = Native.load("ApplicationServices", ApplicationServices.class);
        int CGEventSourceKeyState(int sourceState, int keyCode);
    }

    private static final int kCGEventSourceStateCombinedSessionState = 0;

    private static final int VK_OPTION_LEFT  = 0x3A;  // Left Option (Alt)
    private static final int VK_OPTION_RIGHT = 0x3D;  // Right Option (Alt)
    private static final int VK_MENU = 0x12; // Windows VK_MENU for Alt

    private static boolean isMacAltPressed() {
        try {
            var left  = ApplicationServices.INSTANCE.CGEventSourceKeyState(
                kCGEventSourceStateCombinedSessionState, VK_OPTION_LEFT) != 0;

            var right = ApplicationServices.INSTANCE.CGEventSourceKeyState(
                kCGEventSourceStateCombinedSessionState, VK_OPTION_RIGHT) != 0;

            return left || right;
        } catch (Exception e) {
            LOG.error("macOS keyboard polling error: ", e);
            return false;
        }
    }

    // ==================== Windows ====================
    @SuppressWarnings("InterfaceNeverImplemented")
    @FunctionalInterface
    private interface User32 extends Library {
        User32 INSTANCE = Native.load("user32", User32.class);
        short GetAsyncKeyState(int vKey);
    }

    private static boolean isWindowsAltPressed() {
        var state = User32.INSTANCE.GetAsyncKeyState(VK_MENU);
        return (state & 0x8000) != 0;
    }

    // ==================== Public API ====================

    public static boolean isAltPressed() {
        if (Platform.isMac()) {
            return isMacAltPressed();
        }

        return Platform.isWindows() && isWindowsAltPressed();
    }
}
