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

import java.awt.event.*;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.stream.IntStream;

import javax.swing.*;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.data.MyDesktop;
import songscribe.ui.component.MainFrame;

public final class Utils {

    private static final int[] KEY_MASKS = new int[] {
        InputEvent.CTRL_DOWN_MASK,
        InputEvent.ALT_DOWN_MASK,
        InputEvent.META_DOWN_MASK,
        InputEvent.SHIFT_DOWN_MASK,
    };

    private Utils() {}

    @Contract(pure = true)
    public static int arrayIndexOf(@NotNull Object[] array, Object element) {
        return IntStream.range(0, array.length)
            .filter(i -> element.equals(array[i]))
            .findFirst()
            .orElse(-1);
    }

    public static int lineCount(@NotNull String str) {
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
        if (MyDesktop.isDesktopSupported()) {
            var desktop = MyDesktop.getDesktop();

            if (desktop == null) {
                return;
            }

            try {
                desktop.browse(new URI(webPage));
            } catch (Exception e) {
                mainFrame.showErrorMessage("Could not open the webpage.");
            }
        }
    }

    public static void openEmail(MainFrame mainFrame, String email) {
        if (MyDesktop.isDesktopSupported()) {
            var desktop = MyDesktop.getDesktop();

            if (desktop == null) {
                return;
            }

            try {
                desktop.mail(new URI("mailto", email, null));
            } catch (Exception e) {
                mainFrame.showErrorMessage("Could not open the webpage.");
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

    @NotNull
    public static String getPlatformKeyStrokeString(@NotNull KeyStroke key) {
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

    @NotNull
    public static String getResourcePath(@NotNull String resourcePath) {
        var classLoader = Utils.class.getClassLoader();
        var resource = classLoader.getResource("");

        if (resource == null) {
            throw new IllegalArgumentException(
                "Resource not found: " + resourcePath
            );
        }

        // Strip a leading "/" from the resource path
        var path = resourcePath;

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        return Paths.get(resource.getPath(), path).toString();
    }
}
