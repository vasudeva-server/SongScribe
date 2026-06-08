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
import org.junit.jupiter.api.BeforeEach;
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

    // -------------------------------------------------------------------------
    // resolveLogDir — Windows path with APPDATA set (row 14a)
    // -------------------------------------------------------------------------

    @Nested
    class WhenWindowsPlatform {

        private static final String ENV_APPDATA = "APPDATA";

        private @Nullable String originalHome;

        @BeforeEach
        void saveHome() {
            originalHome = System.getProperty(PROP_USER_HOME);
        }

        @AfterEach
        void restoreHome() {
            if (originalHome != null) {
                System.setProperty(PROP_USER_HOME, originalHome);
            } else {
                System.clearProperty(PROP_USER_HOME);
            }
        }

        @Test
        void testWindowsPathWithAppDataStartsWithAppDataValue(@TempDir Path tempAppData) {
            // Simulate Windows platform with APPDATA set.
            // Force the Windows branch by passing isMacOS=false, isWindows=true.
            Function<String, @Nullable String> env = key -> ENV_APPDATA.equals(key) ? tempAppData.toString() : null;

            var result = SongScribe.resolveLogDir(env, false, true);

            assertThat(result)
                .as("resolveLogDir on Windows with APPDATA set must return a non-null path")
                .isNotNull();

            if (result == null) {
                return; // unreachable — satisfies NullAway
            }

            assertThat(result)
                .as("resolveLogDir on Windows with APPDATA set must start with the APPDATA value")
                .startsWith(tempAppData.toString());

            assertThat(new File(result).isDirectory())
                .as("resolveLogDir on Windows must create and return an existing directory")
                .isTrue();
        }

        // -----------------------------------------------------------------------
        // resolveLogDir — Windows path, APPDATA null, falls back to user.home (row 14b)
        // -----------------------------------------------------------------------

        @Test
        void testWindowsPathWithoutAppDataFallsBackToUserHome(@TempDir Path tempHome) {
            // Simulate Windows platform with no APPDATA env var.
            System.setProperty(PROP_USER_HOME, tempHome.toString());
            Function<String, @Nullable String> env = key -> null; // APPDATA absent

            var result = SongScribe.resolveLogDir(env, false, true);

            assertThat(result)
                .as("resolveLogDir on Windows without APPDATA must return a non-null path under user.home")
                .isNotNull();

            if (result == null) {
                return; // unreachable — satisfies NullAway
            }

            assertThat(result)
                .as("resolveLogDir on Windows without APPDATA must fall back to user.home")
                .startsWith(tempHome.toString());

            assertThat(result)
                .as("resolveLogDir on Windows without APPDATA must end with /SongScribe/Logs")
                .endsWith("/SongScribe/Logs");

            assertThat(new File(result).isDirectory())
                .as("resolveLogDir on Windows without APPDATA must create an existing directory")
                .isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // resolveLogDir — other platform path (row 15)
    // -------------------------------------------------------------------------

    @Test
    void testOtherPlatformPathEndsWithDotSongscribeLogs(@TempDir Path tempHome) {
        var originalHome = System.getProperty(PROP_USER_HOME);
        System.setProperty(PROP_USER_HOME, tempHome.toString());

        try {
            // Simulate a non-macOS, non-Windows platform.
            var result = SongScribe.resolveLogDir(emptyEnv(), false, false);

            assertThat(result)
                .as("resolveLogDir on other platform must return a non-null path")
                .isNotNull();

            if (result == null) {
                return; // unreachable — satisfies NullAway
            }

            assertThat(result)
                .as("resolveLogDir on other platform must end with .songscribe/logs")
                .endsWith(".songscribe/logs");

            assertThat(new File(result).isDirectory())
                .as("resolveLogDir on other platform must create an existing directory")
                .isTrue();
        } finally {
            System.setProperty(PROP_USER_HOME, originalHome);
        }
    }

    // -------------------------------------------------------------------------
    // resolveLogDir — directory creation fails → returns null (row 16)
    // -------------------------------------------------------------------------

    @Test
    void testResolveLogDirReturnNullWhenMkdirsFails(@TempDir Path blockingDir) throws IOException {
        // Place a regular file where the log subdirectory ancestor must be created,
        // so mkdirs() cannot succeed (a file is in the way).
        var blockerFile = blockingDir.resolve("blocker.file");
        Files.createFile(blockerFile);

        var originalHome = System.getProperty(PROP_USER_HOME);
        // Set user.home to a path *inside* a regular file so the computed
        // log directory path is blocked at every OS branch.
        System.setProperty(PROP_USER_HOME, blockerFile.toString());

        try {
            // Use the three-argument overload with isMacOS=false, isWindows=false
            // ("other" branch) so the path is blockerFile + "/.songscribe/logs".
            // mkdirs() must fail since blockerFile is a regular file, not a directory.
            var result = SongScribe.resolveLogDir(emptyEnv(), false, false);

            assertThat(result)
                .as("resolveLogDir must return null when the log directory cannot be created")
                .isNull();
        } finally {
            System.setProperty(PROP_USER_HOME, originalHome);
        }
    }

    // -------------------------------------------------------------------------
    // resolveLogDir — directory already exists → returned without calling mkdirs (row 16b)
    // -------------------------------------------------------------------------

    @Test
    void testResolveLogDirReturnsPathWhenDirectoryAlreadyExists(@TempDir Path tempHome) throws IOException {
        var originalHome = System.getProperty(PROP_USER_HOME);
        System.setProperty(PROP_USER_HOME, tempHome.toString());

        try {
            // Pre-create the exact directory that the "other platform" branch would produce,
            // so logDir.exists() returns true and mkdirs() is never invoked.
            var preExistingDir = tempHome.resolve(".songscribe/logs");
            Files.createDirectories(preExistingDir);

            var result = SongScribe.resolveLogDir(emptyEnv(), false, false);

            assertThat(result)
                .as("resolveLogDir must return the path when the log directory already exists")
                .isNotNull();

            if (result == null) {
                return; // unreachable — satisfies NullAway
            }

            assertThat(result)
                .as("resolveLogDir must return the pre-existing directory path")
                .endsWith(".songscribe/logs");

            assertThat(new File(result).isDirectory())
                .as("resolveLogDir must return a path to an existing directory")
                .isTrue();
        } finally {
            System.setProperty(PROP_USER_HOME, originalHome);
        }
    }

    // -------------------------------------------------------------------------
    // truncateLogIfRequested — TRUNCATE_LOG absent → no file side-effects (row 17)
    // -------------------------------------------------------------------------

    @Nested
    class WhenTruncateLog {

        private static final String LOG_FILE_NAME = "songscribe.log";
        private static final String ENV_TRUNCATE_LOG = "TRUNCATE_LOG";

        private @Nullable String savedLogDir;

        @BeforeEach
        void saveLogDir() {
            savedLogDir = System.getProperty(PROP_LOG_DIR);
        }

        @AfterEach
        void restoreLogDir() {
            if (savedLogDir != null) {
                System.setProperty(PROP_LOG_DIR, savedLogDir);
            } else {
                System.clearProperty(PROP_LOG_DIR);
            }
        }

        @Test
        void testTruncateLogAbsentDoesNothingAndDoesNotCrash(@TempDir Path tempDir) throws IOException {
            // Place a sentinel log file; it must remain untouched when TRUNCATE_LOG is absent.
            var logFile = tempDir.resolve(LOG_FILE_NAME);
            Files.createFile(logFile);
            System.setProperty(PROP_LOG_DIR, tempDir.toString());

            // Simulate TRUNCATE_LOG absent.
            SongScribe.truncateLogIfRequested(key -> null);

            assertThat(logFile)
                .as("truncateLogIfRequested must not delete the log file when TRUNCATE_LOG is absent")
                .exists();
        }

        // -----------------------------------------------------------------------
        // truncateLogIfRequested — TRUNCATE_LOG set but songscribe.log.dir not set (row 18)
        // -----------------------------------------------------------------------

        @Test
        void testTruncateLogSetButLogDirAbsentDoesNotCrash(@TempDir Path tempDir) throws IOException {
            // Ensure songscribe.log.dir is not set.
            System.clearProperty(PROP_LOG_DIR);

            // Place a sentinel file that must remain untouched.
            var logFile = tempDir.resolve(LOG_FILE_NAME);
            Files.createFile(logFile);

            // TRUNCATE_LOG is "set" (non-null), but songscribe.log.dir is absent.
            SongScribe.truncateLogIfRequested(key -> ENV_TRUNCATE_LOG.equals(key) ? "1" : null);

            // The log file is under tempDir which is not referenced by the property,
            // so it must still exist (no deletion possible without the property).
            assertThat(logFile)
                .as("truncateLogIfRequested must not crash or delete anything when songscribe.log.dir is absent")
                .exists();
        }

        // -----------------------------------------------------------------------
        // truncateLogIfRequested — TRUNCATE_LOG set, log dir set, log file exists (row 19)
        // -----------------------------------------------------------------------

        @Test
        void testTruncateLogDeletesLogFileWhenItExists(@TempDir Path tempDir) throws IOException {
            // Create the log file that the method should delete.
            var logFile = tempDir.resolve(LOG_FILE_NAME);
            Files.createFile(logFile);
            System.setProperty(PROP_LOG_DIR, tempDir.toString());

            SongScribe.truncateLogIfRequested(key -> ENV_TRUNCATE_LOG.equals(key) ? "1" : null);

            assertThat(logFile)
                .as("truncateLogIfRequested must delete the log file when TRUNCATE_LOG is set and the file exists")
                .doesNotExist();
        }

        // -----------------------------------------------------------------------
        // truncateLogIfRequested — TRUNCATE_LOG set, log dir set, no log file (row 20)
        // -----------------------------------------------------------------------

        @Test
        void testTruncateLogWithNoLogFileDoesNotCrash(@TempDir Path tempDir) {
            // The log file does not exist; deleteIfExists is a no-op.
            System.setProperty(PROP_LOG_DIR, tempDir.toString());

            // Must not throw.
            SongScribe.truncateLogIfRequested(key -> ENV_TRUNCATE_LOG.equals(key) ? "1" : null);

            // No file was present and none was created — directory is still empty.
            assertThat(tempDir.toFile().list())
                .as("truncateLogIfRequested with no log file must leave the directory empty")
                .isEmpty();
        }
    }
}

