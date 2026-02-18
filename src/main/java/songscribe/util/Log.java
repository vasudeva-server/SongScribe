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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.jetbrains.annotations.Nullable;

import com.formdev.flatlaf.util.SystemInfo;

@SuppressWarnings("CallToPrintStackTrace")
public final class Log {

    private static String filename = "songscribe";
    private static Logger instance = null;

    public static void setNameWithoutExtension(String filename) {
        Log.filename = filename;
    }

    public static void configureDebugLogging() {
        if (System.getenv("DEBUG") == null) {
            return;
        }

        // Enable FINE logging for all songscribe.* loggers
        Logger.getLogger("songscribe").setLevel(Level.FINE);

        // Lower the root logger's handlers so FINE records are not filtered before reaching them
        for (var handler : Logger.getLogger("").getHandlers()) {
            handler.setLevel(Level.FINE);
        }
    }

    public static void log(Level level, String msg) {
        getInstance().log(level, msg);
    }

    public static void info(String msg) {
        getInstance().info(msg);
    }

    public static void warning(String msg) {
        getInstance().warning(msg);
    }

    public static void error(String msg) {
        getInstance().severe(msg);
    }

    public static void error(String msg, Throwable throwable) {
        var message = throwable.getMessage();

        if ((message == null) && (throwable.getCause() != null)) {
            message = throwable.getCause().getMessage();
        }

        getInstance().severe(msg + ": " + message);
    }

    public static void error(Throwable throwable) {
        getInstance()
            .severe(
                throwable.getMessage() + "\n" + getStackTrace(throwable)
            );
    }

    private static Logger getInstance() {
        if (instance == null) {
            instance = Logger.getLogger("org.songscribe");
            init(instance);
        }

        return instance;
    }

    private static void init(Logger logger) {
        try {
            var logDir = getLogDir();

            if (logDir != null) {
                var fileHandler = new FileHandler(
                    logDir + '/' + filename + ".log",
                    1024 * 1024,
                    5,
                    true
                );
                fileHandler.setLevel(Level.ALL);
                fileHandler.setFormatter(new CustomFormatter());
                logger.addHandler(fileHandler);
            } else {
                var consoleHandler = new ConsoleHandler();
                consoleHandler.setLevel(Level.ALL);
                consoleHandler.setFormatter(new CustomFormatter());
                logger.addHandler(consoleHandler);
            }

            logger.setLevel(Level.ALL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Nullable
    private static String getLogDir() {
        var path = "";

        if (SystemInfo.isMacOS) {
            path = Paths.get(
                System.getProperty("user.home"),
                "Library",
                "Logs",
                "SongScribe"
            ).toString();
        } else if (SystemInfo.isWindows) {
            path = Paths.get(
                System.getenv("APPDATA"),
                "SongScribe",
                "Logs"
            ).toString();
        } else {
            // Assume Linux or other Unix-like OS
            path = Paths.get(
                System.getProperty("user.home"),
                ".songscribe",
                "logs"
            ).toString();
        }

        // Make the directory if necessary
        var logDir = Paths.get(path);

        if (!Files.exists(logDir)) {
            try {
                Files.createDirectories(logDir);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        return path;
    }

    public static String getStackTrace(Throwable throwable) {
        var stringWriter = new StringWriter();
        var printWriter = new PrintWriter(stringWriter);
        throwable.printStackTrace(printWriter);
        return stringWriter.toString();
    }

    private Log() {}

    private static class CustomFormatter extends Formatter {

        private static final String FORMAT = "%1$s %2$-7s %3$s%n";
        private static final DateTimeFormatter dateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(
                ZoneId.systemDefault()
            );

        @Override
        public synchronized String format(LogRecord record) {
            var date = dateTimeFormatter.format(
                Instant.ofEpochMilli(record.getMillis())
            );
            var level = record.getLevel().getLocalizedName();
            var message = formatMessage(record);
            return String.format(FORMAT, date, level, message);
        }
    }
}
