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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;

import com.formdev.flatlaf.util.SystemInfo;

import songscribe.converter.AbcConverter;
import songscribe.error.RuntimeError;
import songscribe.converter.ImageConverter;
import songscribe.converter.MidiConverter;
import songscribe.converter.PDFConverter;
import songscribe.ui.component.MainFrame;
import songscribe.uiconverter.UIConverter;
import songscribe.util.UIUtils;

public final class SongScribe {

    private SongScribe() {}

    // Package-private for testing: look up a platform-specific log directory,
    // creating it if it does not already exist, and return its path. Returns
    // null if the directory could not be created.
    static @Nullable String resolveLogDir(Function<String, @Nullable String> env) {
        return resolveLogDir(env, SystemInfo.isMacOS, SystemInfo.isWindows);
    }

    // Package-private for testing: accepts explicit platform flags so tests can
    // exercise Windows and "other OS" branches without requiring a real OS switch.
    static @Nullable String resolveLogDir(
        Function<String, @Nullable String> env,
        boolean isMacOS,
        boolean isWindows
    ) {
        var userHome = System.getProperty("user.home");
        String dir;

        if (isMacOS) {
            dir = userHome + "/Library/Logs/SongScribe";
        } else if (isWindows) {
            var appData = env.apply("APPDATA");
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

    public static void configureLogging() {
        configureLogging(System::getenv, SongScribe.class.getResource("/logback-console.xml"));
    }

    // Package-private for testing: accepts an env-var provider and console-log
    // resource URL so tests can supply controlled values without relying on actual
    // OS environment variables or classpath resources.
    static void configureLogging(Function<String, @Nullable String> env, @Nullable URL consoleLogUrl) {
        if (env.apply("CONSOLE_LOG") != null) {
            if (consoleLogUrl != null) {
                System.setProperty("logback.configurationFile", consoleLogUrl.toString());
            }
        } else {
            var logDir = resolveLogDir(env);

            if (logDir != null) {
                System.setProperty("songscribe.log.dir", logDir);
            }

            // If logDir is null, songscribe.log.dir stays unset → FILE appender
            // fails to initialize → only CONSOLE appender activates.
        }

        var logLevel = env.apply("LOG_LEVEL");

        if (logLevel != null) {
            System.setProperty("songscribe.log.level", logLevel.toUpperCase());
        }
    }

    static void truncateLogIfRequested() {
        truncateLogIfRequested(System::getenv);
    }

    // Package-private for testing: accepts an env-var provider so tests can
    // control whether TRUNCATE_LOG is considered set without touching the real OS env.
    static void truncateLogIfRequested(Function<String, @Nullable String> env) {
        if (env.apply("TRUNCATE_LOG") == null) {
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

        // Figure out which app to start. The default is Song Writer.
        String app;

        if (args.length > 0) {
            app = args[0];
        } else {
            var prop = System.getProperty("songscribe");
            app = (prop == null) ? "sw" : prop;
        }

        // Enable anti-aliasing and sub-pixel rendering for fonts. Like the macOS
        // properties above, these are read during AWT/Swing initialization, so they
        // must be set on the main thread before the toolkit starts. macOS ignores
        // them, but other platforms rely on them for quality text rendering.
        var aaSetting = "on";  // safe default

        if (SystemInfo.isMacOS) {  // or check System.getProperty("os.name")
            aaSetting = "lcd";
        }

        System.setProperty("swing.aatext", "true");
        System.setProperty("awt.useSystemAAFontSettings", aaSetting);
        System.setProperty("apple.awt.textantialiasing", "true");

        // Allow Swing components to handle a property change with a null property name,
        // which indicates more than one property has changed.
        System.setProperty("swing.actions.reconfigureOnNull", "true");

        truncateLogIfRequested();
        logBanner(app.contains("converter") ? "SongScribe Converter" : "SongScribe");

        switch (app) {
            case "image_converter" -> ImageConverter.main(args);
            case "midi_converter" -> MidiConverter.main(args);
            case "pdf_converter" -> PDFConverter.main(args);
            case "ui_converter" -> UIConverter.main(args);
            case "abc_converter" -> AbcConverter.main(args);
            default -> {
                // macOS system properties are already set above on the main thread.
                // Bootstrap the UI on the EDT so the splash can appear immediately.
                SwingUtilities.invokeLater(() -> MainFrame.main(args));
            }
        }
    }
}
