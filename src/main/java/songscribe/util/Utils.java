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
import java.util.stream.IntStream;


import com.formdev.flatlaf.util.SystemInfo;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;

public final class Utils {

    private static final int[] KEY_MASKS = new int[] {
        InputEvent.CTRL_DOWN_MASK,
        InputEvent.ALT_DOWN_MASK,
        InputEvent.META_DOWN_MASK,
        InputEvent.SHIFT_DOWN_MASK,
    };

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

    public static void openWebPage(MainFrame mainFrame, String webPage) {
        if (DesktopUtils.isDesktopSupported()) {
            var desktop = DesktopUtils.getDesktop();

            if (desktop == null) {
                return;
            }

            try {
                desktop.browse(new URI(webPage));
            } catch (Exception e) {
                OptionDialogs.showErrorMessage(
                    null,
                    Strings.ALERT_TITLE_BROWSER_ERROR,
                    Strings.ERROR_WEBPAGE_OPEN
                );
            }
        }
    }

    public static void openEmail(MainFrame mainFrame, String email) {
        if (DesktopUtils.isDesktopSupported()) {
            var desktop = DesktopUtils.getDesktop();

            if (desktop == null) {
                return;
            }

            try {
                desktop.mail(new URI("mailto", email, null));
            } catch (Exception e) {
                OptionDialogs.showErrorMessage(
                    null,
                    Strings.ALERT_TITLE_EMAIL_ERROR,
                    Strings.ERROR_EMAIL_OPEN
                );
            }
        }
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            // Ignore
        }
    }

    public static String getPlatformKeyStrokeString(KeyStroke key) {
        var sb = new StringBuilder(20);
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
