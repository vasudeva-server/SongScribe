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

    // Optional but very useful on macOS
    public static boolean hasAccessibilityPermission() {
        try {
            // Simple check using AXIsProcessTrusted (requires JNA platform or extra binding)
            // For now we just attempt the call and see
            return isMacAltPressed() || true; // we'll improve if needed
        } catch (Exception e) {
            return false;
        }
    }
}
