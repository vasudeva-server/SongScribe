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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;

import org.jspecify.annotations.Nullable;

public final class DesktopUtils {

    private static boolean supported = false;
    private static @Nullable Class<?> klass = null;
    private final Object desktop;

    private DesktopUtils(Object desktop) {
        this.desktop = desktop;
    }

    public static boolean isDesktopSupported() {
        if (klass == null) {
            klass = DesktopUtils.class;
            supported = false;

            try {
                var desktopClass = Class.forName("java.awt.Desktop");
                var method = desktopClass.getMethod("isDesktopSupported");
                var isSupported = (Boolean) method.invoke(null);

                // If we get this far without an exception, we're good to go
                klass = desktopClass;
                supported = isSupported;
            } catch (
                ClassNotFoundException
                | NoSuchMethodException
                | SecurityException ignored
            ) {} catch (Exception e) {
                // Ignore
            }
        }

        return supported;
    }

    @Nullable
    public static DesktopUtils getDesktop() {
        if (isDesktopSupported() && klass != null) {
            try {
                return new DesktopUtils(
                    klass.getMethod("getDesktop").invoke(null)
                );
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public void browse(URI uri) {
        if (klass == null) {
            return;
        }

        if (isSupported(Action.BROWSE)) {
            try {
                klass.getMethod("browse", URI.class).invoke(desktop, uri);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void mail(URI uri) {
        if (klass == null) {
            return;
        }

        if (isSupported(Action.MAIL)) {
            try {
                klass.getMethod("mail", URI.class).invoke(desktop, uri);
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public void open(File file) throws IOException {
        if (klass == null) {
            return;
        }

        if (isSupported(Action.OPEN)) {
            try {
                klass.getMethod("open", File.class).invoke(desktop, file);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Ignore
            } catch (InvocationTargetException e) {
                throw new IOException(e.getTargetException());
            }
        }
    }

    private boolean isSupported(Action p) {
        if (klass == null) {
            return false;
        }

        if (isDesktopSupported()) {
            try {
                var actionClass = Class.forName("java.awt.Desktop$Action");
                var action = actionClass
                    .getMethod("valueOf", String.class)
                    .invoke(null, p.name());
                return (Boolean) klass
                    .getMethod("isSupported", actionClass)
                    .invoke(desktop, action);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public enum Action {
        BROWSE,
        MAIL,
        OPEN,
    }
}
