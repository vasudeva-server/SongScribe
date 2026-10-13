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
package songscribe;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;

import com.formdev.flatlaf.util.SystemInfo;
import net.engio.mbassy.bus.MBassador;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import songscribe.dom.Song;
import songscribe.error.RuntimeError;
import songscribe.io.musicxml.MusicXmlSerializer;
import songscribe.layout.PageModel;
import songscribe.message.DebugMBassador;
import songscribe.message.MessageCenter;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.util.UIUtils;

public final class SongScribe {

    private SongScribe() {}

    // Look up a platform-specific log directory, creating it if it does not already
    // exist, and return its path. Returns null if the directory could not be created.
    private static @Nullable String resolveLogDir() {
        var userHome = System.getProperty("user.home");
        String dir;

        if (SystemInfo.isMacOS) {
            dir = userHome + "/Library/Logs/SongScribe";
        } else if (SystemInfo.isWindows) {
            var appData = System.getenv("APPDATA");
            dir = (appData != null ? appData : userHome) + "/SongScribe/Logs";
        } else {
            dir = userHome + "/.songscribe/logs";
        }

        var logDir = new File(dir);

        if (!logDir.exists() && !logDir.mkdirs()) {
            System.out.println("Warning: could not create log directory: " + dir);
            return null;
        }

        return dir;
    }

    private static void configureLogging() {
        if (System.getenv("CONSOLE_LOG") != null) {
            var consoleLogUrl = SongScribe.class.getResource("/logback-console.xml");

            if (consoleLogUrl != null) {
                System.setProperty("logback.configurationFile", consoleLogUrl.toString());
            }
        } else {
            var logDir = resolveLogDir();

            if (logDir != null) {
                System.setProperty("songscribe.log.dir", logDir);
            }

            // If logDir is null, songscribe.log.dir stays unset → FILE appender
            // fails to initialize → only CONSOLE appender activates.
        }

        var logLevel = System.getenv("LOG_LEVEL");

        if (logLevel != null) {
            System.setProperty("songscribe.log.level", logLevel.toUpperCase());
        }
    }

    private static void truncateLogIfRequested() {
        if (System.getenv("TRUNCATE_LOG") == null) {
            return;
        }

        // Delete the log file before Logback initializes so it starts fresh.
        // songscribe.log.dir is set by configureLogging() — if unset, logging
        // goes to the console and there is nothing to delete.
        var logDir = System.getProperty("songscribe.log.dir");

        if (logDir == null) {
            return;
        }

        try {
            Files.deleteIfExists(Path.of(logDir, "songscribe.log"));
        } catch (IOException e) {
            System.err.println("Failed to delete log file: " + e.getMessage());
        }
    }

    public static void logBanner(String appName) {
        var log = LoggerFactory.getLogger(SongScribe.class);
        var border = "*".repeat(40);
        log.info(border);
        log.info("* {} {}", appName, Version.PUBLIC_VERSION);
        log.info(border);
        log.info("Log level: {}", System.getProperty("songscribe.log.level", "INFO"));
    }

    /**
     * Builds the MusicXML JAXB binding model on a background thread so the first
     * save of a session does not build it on the event dispatch thread.
     *
     * <p>{@code MainFrame.saveCurrentFile} writes synchronously from a Swing action,
     * and the model spans several hundred generated classes, so paying for it there
     * stalls the UI. A failure here is not fatal — the save path reports its own
     * failure — so it is logged and left alone.
     *
     * <p>The catch is {@link Throwable}, not {@link RuntimeException}: the failure this
     * thread can actually produce is {@code MusicXmlSerializer}'s one-time setup blowing
     * up, which arrives as an {@link ExceptionInInitializerError} — an {@code Error}. A
     * narrower catch would let it kill the thread silently and log nothing, which is the
     * one outcome this method exists to prevent.
     */
    private static void warmMusicXmlSerializer() {
        var warmUp = new Thread(() -> {
            try {
                MusicXmlSerializer.warmUp();
            } catch (Throwable e) {
                LoggerFactory.getLogger(SongScribe.class).warn("MusicXML serializer warm-up failed", e);
            }
        }, "musicxml-warmup");

        warmUp.setDaemon(true);
        warmUp.start();
    }

    static void main(String[] args) {
        configureLogging();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // If the fatal-error alert is already showing, let the EDT continue
            // processing so the dialog remains interactive.
            if (throwable instanceof RuntimeError.ExitInProgressError) {
                return;
            }

            Runnable handler = () -> {
                try {
                    throw RuntimeError.exit("Uncaught exception in thread: " + thread.getName(), throwable);
                } catch (RuntimeError.ExitInProgressError ignored) {
                    // Exit is already in progress; swallow so the error never escapes
                    // into the AWT nested event loop and kills the in-progress dialog.
                }
            };

            if (SwingUtilities.isEventDispatchThread()) {
                handler.run();
            } else {
                SwingUtilities.invokeLater(handler);
            }
        });

        // macOS system properties must be set on the main thread before
        // AWT/Swing is initialized — setting them later has no effect.
        if (SystemInfo.isMacOS) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.appearance", "system");
        }

        // Enable anti-aliasing for fonts. Like the macOS properties above, these are
        // read during AWT/Swing initialization, so they must be set on the main thread
        // before the toolkit starts. The macOS toolkit does not read
        // awt.useSystemAAFontSettings; UIUtils.initMinimalTheme sets the hint there.
        System.setProperty("swing.aatext", "true");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("apple.awt.textantialiasing", "true");

        // Allow Swing components to handle a property change with a null property name,
        // which indicates more than one property has changed.
        System.setProperty("swing.actions.reconfigureOnNull", "true");

        truncateLogIfRequested();
        logBanner("SongScribe");

        warmMusicXmlSerializer();

        // macOS system properties are already set above on the main thread.
        // Bootstrap the UI on the EDT so the splash can appear immediately.
        SwingUtilities.invokeLater(() -> MainFrame.main(args));
    }

    /**
     * Sets the message bus and builds the main window without showing it. Every process that
     * builds the main window builds it here, so the window the end-to-end suite drives is the
     * window the application shows.
     *
     * <p>Requires {@link UIUtils#initMinimalTheme} to have run. Call it once per process: the bus,
     * the main window and the preview element manager all live for the life of the process, and
     * a second call would replace the bus under the listeners the first one subscribed.
     *
     * <p>Whatever runs before this — the minimal theme, the splash, and the start of MIDI
     * initialization — runs before the bus exists, so it must not post or subscribe. Everything
     * this method does after setting the bus may.
     *
     * @effects sets the message bus; installs the remaining font faces; keeps tooltips open while
     *          the pointer hovers their item; subscribes the process's preview element manager;
     *          supplies the page's default line width to every new {@link Song}; builds and wires
     *          the main window. Must run on the event dispatch thread.
     * @return the main window, built and not yet visible
     */
    public static MainFrame buildMainWindow() {
        setBus();
        UIUtils.installEagerFonts();
        UIUtils.keepToolTipsOpenWhileHovered();

        // The bus holds the listener strongly, so nothing here needs to keep it.
        new PreviewElementManager();
        Song.setDefaultLineWidthProvider(PageModel::getDefaultLineWidthSs);

        var mainFrame = MainFrame.getInstance();
        mainFrame.initFrame();
        return mainFrame;
    }

    /**
     * Sets the application's message bus, and with it the publication-error policy. Call it once
     * for the life of the process, before anything can post or subscribe.
     */
    private static void setBus() {
        if (System.getenv("DEBUG") != null) {
            MessageCenter.setBus(new DebugMBassador<>(MessageCenter::exitOnPublicationError));
        } else {
            MessageCenter.setBus(new MBassador<>(MessageCenter::exitOnPublicationError));
        }
    }
}
