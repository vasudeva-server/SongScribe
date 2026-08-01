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
package songscribe.util;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jspecify.annotations.Nullable;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.ui.platform.mac.ObjC;

/**
 * Detects whether the operating system is using a dark appearance, and notifies
 * registered listeners when that changes.
 * <p>
 * On macOS the value comes from
 * {@code [[NSUserDefaults standardUserDefaults] objectForKey:@"AppleInterfaceStyle"]}
 * — dark iff its value contains "dark". On other platforms detection is not yet
 * implemented and {@link #isDark()} returns {@code false} (the app simply does
 * not follow the system theme).
 * <p>
 * macOS publishes no cheap change callback for this key without an AppKit
 * notification observer (which would require an FFM upcall used as an Obj-C IMP),
 * so a lightweight daemon thread polls instead. Theme changes are rare, so the
 * poll latency is immaterial. The thread runs only while at least one listener is
 * registered.
 */
public final class SystemThemeDetector {

    private static final Logger LOG = LoggerFactory.getLogger(SystemThemeDetector.class);

    private static final String APPLE_INTERFACE_STYLE_KEY = "AppleInterfaceStyle";
    private static final Pattern DARK_PATTERN = Pattern.compile("dark", Pattern.CASE_INSENSITIVE);
    private static final Duration THEME_POLL_INTERVAL = Duration.ofSeconds(2);
    private static final String POLL_THREAD_NAME = "system-theme-detector";

    private static final SystemThemeDetector INSTANCE = new SystemThemeDetector();

    private final Set<Consumer<Boolean>> listeners = new CopyOnWriteArraySet<>();

    private volatile @Nullable Thread pollThread;
    private volatile boolean lastDark;

    private SystemThemeDetector() {
    }

    /**
     * Returns whether the OS is currently using a dark appearance. Safe to call
     * from any thread; on non-macOS platforms always returns {@code false}.
     */
    public static boolean isDark() {
        return INSTANCE.readIsDark();
    }

    /**
     * Registers a listener notified whenever the system appearance changes. The
     * listener receives the new dark/light state and is invoked on the polling
     * thread, so it must marshal back to the EDT itself if it touches Swing.
     */
    public static void registerListener(Consumer<Boolean> listener) {
        INSTANCE.addListener(listener);
    }

    /**
     * Removes a previously registered listener. Polling stops once the last
     * listener is removed.
     */
    public static void removeListener(Consumer<Boolean> listener) {
        INSTANCE.dropListener(listener);
    }

    private synchronized void addListener(Consumer<Boolean> listener) {
        listeners.add(listener);

        if (pollThread == null) {
            startPolling();
        }
    }

    private synchronized void dropListener(Consumer<Boolean> listener) {
        listeners.remove(listener);

        if (listeners.isEmpty()) {
            stopPolling();
        }
    }

    private void startPolling() {
        lastDark = readIsDark();

        var thread = new Thread(this::pollLoop, POLL_THREAD_NAME);
        thread.setDaemon(true);
        pollThread = thread;
        thread.start();
    }

    private void stopPolling() {
        var thread = pollThread;

        if (thread != null) {
            thread.interrupt();
            pollThread = null;
        }
    }

    private void pollLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                //noinspection BusyWait
                Thread.sleep(THEME_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            var dark = readIsDark();

            if (dark != lastDark) {
                lastDark = dark;
                notifyListeners(dark);
            }
        }
    }

    private void notifyListeners(boolean dark) {
        for (var listener : listeners) {
            try {
                listener.accept(dark);
            } catch (RuntimeException e) {
                LOG.warn("System theme listener failed", e);
            }
        }
    }

    private boolean readIsDark() {
        if (!SystemInfo.isMacOS) {
            return false;
        }

        try {
            return readMacInterfaceStyle();
        } catch (RuntimeException e) {
            LOG.warn("System theme detection unavailable, falling back to light: {}", e.getMessage());
            return false;
        }
    }

    private boolean readMacInterfaceStyle() {
        return ObjC.withAutoreleasePool(() -> {
            var userDefaults = ObjC.msgSend(ObjC.objcClass("NSUserDefaults"), ObjC.selector("standardUserDefaults"));
            var key = ObjC.javaToNsString(APPLE_INTERFACE_STYLE_KEY);
            var value = ObjC.msgSend(userDefaults, ObjC.selector("objectForKey:"), key);
            var style = ObjC.nsStringToJava(value);
            return style != null && DARK_PATTERN.matcher(style).find();
        });
    }
}
