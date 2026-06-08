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
package songscribe;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SongScribeTest extends UnitTest {

    // System property names under test — constants to guard against typos.
    private static final String PROP_LOGBACK_CONFIG = "logback.configurationFile";
    private static final String PROP_LOG_DIR = "songscribe.log.dir";
    private static final String PROP_LOG_LEVEL = "songscribe.log.level";
    private static final String PROP_USER_HOME = "user.home";

    // Environment variable names used by SongScribe.
    private static final String ENV_CONSOLE_LOG = "CONSOLE_LOG";
    private static final String ENV_LOG_LEVEL = "LOG_LEVEL";

    // -------------------------------------------------------------------------
    // configureLogging — CONSOLE_LOG present, resource URL non-null (row 7)
    // -------------------------------------------------------------------------

    // Returns "1" when the env key matches CONSOLE_LOG, null otherwise.
    private static Function<String, @Nullable String> consoleLogPresentEnv() {
        return key -> ENV_CONSOLE_LOG.equals(key) ? "1" : null;
    }

    // Returns null for all env var lookups (simulates a clean environment).
    private static Function<String, @Nullable String> emptyEnv() {
        return key -> null;
    }

    @Nested
    class WhenConsoleLogSet {

        @AfterEach
        void restoreProperties() {
            System.clearProperty(PROP_LOGBACK_CONFIG);
            System.clearProperty(PROP_LOG_LEVEL);
        }

        @Test
        void testConsoleLogWithResourceSetsLogbackConfigProperty() {
            // Obtain the real URL for the console logback config that lives on the classpath.
            var realUrl = SongScribe.class.getResource("/logback-console.xml");
            assertThat(realUrl).as("logback-console.xml must exist on the classpath").isNotNull();

            // Simulate CONSOLE_LOG being set (non-null value) with the real URL.
            SongScribe.configureLogging(consoleLogPresentEnv(), realUrl);

            assertThat(System.getProperty(PROP_LOGBACK_CONFIG))
                .as("logback.configurationFile must be set to the resource URL")
                .isEqualTo(realUrl.toString());
        }

        // -----------------------------------------------------------------------
        // configureLogging — CONSOLE_LOG present, resource URL null (row 8)
        // -----------------------------------------------------------------------

        @Test
        void testConsoleLogWithNullResourceDoesNotSetLogbackConfigProperty() {
            System.clearProperty(PROP_LOGBACK_CONFIG);

            // Simulate CONSOLE_LOG set but the classpath resource is absent (null URL).
            SongScribe.configureLogging(consoleLogPresentEnv(), null);

            assertThat(System.getProperty(PROP_LOGBACK_CONFIG))
                .as("logback.configurationFile must not be set when the resource URL is null")
                .isNull();
        }
    }

    // -------------------------------------------------------------------------
    // configureLogging — CONSOLE_LOG absent, resolveLogDir succeeds (row 9)
    // -------------------------------------------------------------------------

    @Nested
    class WhenConsoleLogAbsent {

        @AfterEach
        void restoreProperties() {
            System.clearProperty(PROP_LOG_DIR);
            System.clearProperty(PROP_LOG_LEVEL);
        }

        @Test
        void testResolvedLogDirSetsLogDirProperty(@TempDir Path tempHome) {
            // Route user.home to tempHome so resolveLogDir creates its subdirectory there.
            var originalHome = System.getProperty(PROP_USER_HOME);
            System.setProperty(PROP_USER_HOME, tempHome.toString());

            try {
                // CONSOLE_LOG absent → resolveLogDir is called.
                SongScribe.configureLogging(emptyEnv(), null);

                assertThat(System.getProperty(PROP_LOG_DIR))
                    .as("songscribe.log.dir must be set when the log directory is resolved")
                    .isNotNull();
            } finally {
                System.setProperty(PROP_USER_HOME, originalHome);
            }
        }

        // -----------------------------------------------------------------------
        // configureLogging — CONSOLE_LOG absent, resolveLogDir returns null (row 10)
        // -----------------------------------------------------------------------

        @Test
        void testUnresolvableLogDirDoesNotSetLogDirProperty(@TempDir Path blockingDir) throws IOException {
            // Create a regular file where the log sub-directory would need to be created,
            // so mkdirs() cannot succeed (a file is in the way of the parent path).
            // Simpler approach: use a path whose parent is a regular file.
            // Create a regular file and set user.home to a path *inside* it so that
            // the computed dir is under a file, where mkdirs() must fail.
            var blockerFile = blockingDir.resolve("blocker.file");
            Files.createFile(blockerFile);

            // Set user.home to a path whose first component is a regular file.
            // The log subdirectory path will be something like
            // "<blockingDir>/blocker.file/Library/Logs/SongScribe" (macOS)
            // or similar — all of which require traversing through a regular file.
            var originalHome = System.getProperty(PROP_USER_HOME);
            System.setProperty(PROP_USER_HOME, blockerFile.toString());
            System.clearProperty(PROP_LOG_DIR);

            try {
                SongScribe.configureLogging(emptyEnv(), null);

                assertThat(System.getProperty(PROP_LOG_DIR))
                    .as("songscribe.log.dir must not be set when the log directory cannot be created")
                    .isNull();
            } finally {
                System.setProperty(PROP_USER_HOME, originalHome);
            }
        }
    }

    // -------------------------------------------------------------------------
    // configureLogging — LOG_LEVEL present → uppercased property set (row 11)
    // -------------------------------------------------------------------------

    // Returns "debug" when the env key matches LOG_LEVEL, null otherwise.
    private static Function<String, @Nullable String> logLevelDebugEnv() {
        return key -> ENV_LOG_LEVEL.equals(key) ? "debug" : null;
    }

    @Nested
    class WhenLogLevelSet {

        @AfterEach
        void restoreProperties() {
            System.clearProperty(PROP_LOG_LEVEL);
            System.clearProperty(PROP_LOG_DIR);
        }

        @Test
        void testLogLevelEnvSetToUppercasedProperty(@TempDir Path tempHome) {
            var originalHome = System.getProperty(PROP_USER_HOME);
            System.setProperty(PROP_USER_HOME, tempHome.toString());

            try {
                SongScribe.configureLogging(logLevelDebugEnv(), null);

                assertThat(System.getProperty(PROP_LOG_LEVEL))
                    .as("songscribe.log.level must be set to the uppercased LOG_LEVEL value")
                    .isEqualTo("DEBUG");
            } finally {
                System.setProperty(PROP_USER_HOME, originalHome);
            }
        }

        // -----------------------------------------------------------------------
        // configureLogging — LOG_LEVEL absent → property not set (row 12)
        // -----------------------------------------------------------------------

        @Test
        void testLogLevelAbsentDoesNotSetProperty(@TempDir Path tempHome) {
            var originalHome = System.getProperty(PROP_USER_HOME);
            System.setProperty(PROP_USER_HOME, tempHome.toString());
            System.clearProperty(PROP_LOG_LEVEL);

            try {
                // All env vars absent.
                SongScribe.configureLogging(emptyEnv(), null);

                assertThat(System.getProperty(PROP_LOG_LEVEL))
                    .as("songscribe.log.level must not be set when LOG_LEVEL env var is absent")
                    .isNull();
            } finally {
                System.setProperty(PROP_USER_HOME, originalHome);
            }
        }
    }

    // -------------------------------------------------------------------------
    // resolveLogDir — macOS path construction (row 13)
    // -------------------------------------------------------------------------

    @Test
    void testMacOsPathEndsWithLibraryLogsSongScribe(@TempDir Path tempHome) {
        var originalHome = System.getProperty(PROP_USER_HOME);
        System.setProperty(PROP_USER_HOME, tempHome.toString());

        try {
            var result = SongScribe.resolveLogDir(emptyEnv());

            // On macOS (SystemInfo.isMacOS == true), the path is
            // {user.home}/Library/Logs/SongScribe.
            // The test is written to be meaningful on the CI platform (macOS/Darwin).
            assertThat(result)
                .as("resolveLogDir must return a non-null path when the directory can be created")
                .isNotNull();

            if (result == null) {
                return; // unreachable — satisfies NullAway
            }

            assertThat(new File(result).isDirectory())
                .as("resolveLogDir must return a path to an existing directory")
                .isTrue();

            // The path under tempHome must match the expected macOS suffix.
            assertThat(result)
                .as("resolveLogDir on macOS must end with Library/Logs/SongScribe")
                .endsWith("Library/Logs/SongScribe");
        } finally {
            System.setProperty(PROP_USER_HOME, originalHome);
        }
    }
}

