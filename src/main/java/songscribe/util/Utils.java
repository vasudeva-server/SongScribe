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

import module java.desktop;

import java.net.URI;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Set;
import java.util.stream.IntStream;


import com.formdev.flatlaf.util.SystemInfo;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;

public final class Utils {

    private static final int[] KEY_MASKS = new int[] {
        InputEvent.CTRL_DOWN_MASK,
        InputEvent.ALT_DOWN_MASK,
        InputEvent.META_DOWN_MASK,
        InputEvent.SHIFT_DOWN_MASK,
    };

    // Keys that {@link #getPlatformKeyString} renders as a symbol glyph (rather than a
    // letter or word), so callers can render them in a font that includes the glyph.
    private static final Set<Integer> SYMBOL_KEY_CODES = Set.of(
        KeyEvent.VK_ENTER,
        KeyEvent.VK_BACK_SPACE,
        KeyEvent.VK_DELETE,
        KeyEvent.VK_ESCAPE,
        KeyEvent.VK_TAB
    );

    private Utils() {}

    public static int arrayIndexOf(Object[] array, Object element) {
        return IntStream.range(0, array.length)
            .filter(i -> element.equals(array[i]))
            .findFirst()
            .orElse(-1);
    }

    public static int lineCount(String str) {
        if (str.isEmpty()) {
            return 0;
        }

        return str.trim().split("\n").length;
    }

    public static double roundToTwoDecimalPlaces(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static int getCurrentYear() {
        return Calendar.getInstance().get(Calendar.YEAR);
    }

    @FunctionalInterface
    public interface DesktopOperation {
        @SuppressWarnings("ProhibitedExceptionDeclared") // Can't know in advance what perform will throw
        void perform(DesktopUtils desktop) throws Exception;
    }

    public static void withDesktop(DesktopOperation operation, String title, String message) {
        if (!DesktopUtils.isDesktopSupported()) {
            return;
        }

        var desktop = DesktopUtils.getDesktop();

        if (desktop == null) {
            return;
        }

        try {
            operation.perform(desktop);
        } catch (Exception e) {
            OptionDialogs.showErrorMessage(null, title, message);
        }
    }

    public static void openWebPage(String webPage) {
        withDesktop(
            desktop -> desktop.browse(new URI(webPage)),
            Strings.ALERT_TITLE_BROWSER_ERROR,
            Strings.ERROR_WEBPAGE_OPEN
        );
    }

    public static void openEmail(String email) {
        withDesktop(
            desktop -> desktop.mail(new URI("mailto", email, null)),
            Strings.ALERT_TITLE_EMAIL_ERROR,
            Strings.ERROR_EMAIL_OPEN
        );
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    public static String getPlatformKeyStrokeString(KeyStroke key) {
        return getPlatformModifiersString(key) + getPlatformKeyString(key);
    }

    /**
     * Returns just the modifier portion of the keystroke (e.g. {@code ⌘⇧} on macOS,
     * {@code Ctrl+Shift+} elsewhere). On macOS these are symbol glyphs that the UI
     * font may lack, so callers can render this part in a font that includes them.
     */
    public static String getPlatformModifiersString(KeyStroke key) {
        var sb = new StringBuilder();
        var modifiers = key.getModifiers();
        var isMac = SystemInfo.isMacOS;

        for (var mask : KEY_MASKS) {
            if ((modifiers & mask) == 0) {
                continue;
            }

            switch (mask) {
                case InputEvent.CTRL_DOWN_MASK -> sb.append(
                    isMac ? '⌃' : "Ctrl+"
                );
                case InputEvent.ALT_DOWN_MASK -> sb.append(
                    isMac ? '⌥' : "Alt+"
                );
                case InputEvent.META_DOWN_MASK -> {
                    if (isMac) {
                        sb.append('⌘');
                    } else if (SystemInfo.isLinux) {
                        sb.append("Meta+");
                    } else {
                        sb.append("Win+");
                    }
                }
                case InputEvent.SHIFT_DOWN_MASK -> sb.append(
                    isMac ? '⇧' : "Shift+"
                );
            }
        }

        return sb.toString();
    }

    /**
     * Returns just the key portion of the keystroke (e.g. {@code S}, or a special-key
     * symbol such as {@code ↩︎} for Enter).
     */
    public static String getPlatformKeyString(KeyStroke key) {
        var sb = new StringBuilder();

        switch (key.getKeyCode()) {
            case KeyEvent.VK_ENTER -> sb.append("↩︎");
            case KeyEvent.VK_BACK_SPACE -> sb.append('⌫');
            case KeyEvent.VK_DELETE -> sb.append('⌦');
            case KeyEvent.VK_ESCAPE -> sb.append('⎋');
            case KeyEvent.VK_TAB -> sb.append('⇥');
            case KeyEvent.VK_SPACE -> sb.append("Space");
            default -> {
                if (key.getKeyCode() != 0) {
                    sb.append((char) key.getKeyCode());
                } else {
                    sb.append(key.getKeyChar());
                }
            }
        }

        return sb.toString();
    }

    /**
     * Returns whether {@link #getPlatformKeyString} renders this key as a symbol glyph
     * (e.g. Enter, Backspace, Delete, Escape, Tab) rather than a letter or word.
     */
    public static boolean isPlatformKeySymbol(KeyStroke key) {
        return SYMBOL_KEY_CODES.contains(key.getKeyCode());
    }

    public static String getResourcePath(String resourcePath) {
        // Strip a leading "/" from the resource path
        var path = resourcePath;

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        // Look up the resource directly so it is found on whichever
        // classpath entry contains it (important when tests run from
        // target/test-classes while resources live in target/classes).
        var classLoader = Utils.class.getClassLoader();
        var resource = classLoader.getResource(path);

        if (resource != null) {
            return Paths.get(URI.create(resource.toExternalForm())).toString();
        }

        // Fall back to constructing from the classpath root
        var root = classLoader.getResource("");

        if (root == null) {
            throw new IllegalArgumentException(
                "Resource not found: " + resourcePath
            );
        }

        return Paths.get(root.getPath(), path).toString();
    }
}
