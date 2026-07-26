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

import org.slf4j.Logger;

/** Shared helpers for gating debug logging beyond the SLF4J log level. */
public final class LogUtils {

    /** Env var that must be set (to {@code 1} or {@code true}) to enable beam debug logging. */
    private static final String DEBUG_BEAMS_ENV_VAR = "DEBUG_BEAMS";

    /** Env var that must be set (to {@code 1} or {@code true}) to enable tie debug logging. */
    private static final String DEBUG_TIES_ENV_VAR = "DEBUG_TIES";

    /** Env var that must be set (to {@code 1} or {@code true}) to enable lyric debug logging. */
    private static final String DEBUG_LYRICS_ENV_VAR = "DEBUG_LYRICS";

    /**
     * Whether {@link #DEBUG_BEAMS_ENV_VAR} was set when the JVM started.
     *
     * <p>The environment cannot change during a run, so each subsystem's flag is read once here
     * rather than at every log site. Being {@code static final} also lets the JIT fold a disabled
     * subsystem's log guards away as dead code.
     */
    public static final boolean DEBUG_BEAMS_ENABLED = isEnvVarSet(DEBUG_BEAMS_ENV_VAR);

    /** Whether {@link #DEBUG_TIES_ENV_VAR} was set when the JVM started. */
    public static final boolean DEBUG_TIES_ENABLED = isEnvVarSet(DEBUG_TIES_ENV_VAR);

    /** Whether {@link #DEBUG_LYRICS_ENV_VAR} was set when the JVM started. */
    public static final boolean DEBUG_LYRICS_ENABLED = isEnvVarSet(DEBUG_LYRICS_ENV_VAR);

    private LogUtils() {
    }

    /**
     * Whether a subsystem's debug logging should run: the subsystem's own flag is set AND
     * {@code logger} has debug enabled. Keeps noisy, subsystem-specific output (e.g. beam
     * scoring) silent when debug logging is turned on for other purposes.
     *
     * <p>The subsystem flag is tested first because it is a cached constant, while the logger's
     * level is a live lookup that can change during the run.
     *
     * @param logger  the logger the caller is about to log to
     * @param enabled the subsystem's flag, e.g. {@link #DEBUG_BEAMS_ENABLED}
     * @return whether both the subsystem flag and {@code logger}'s debug level are enabled
     */
    public static boolean isDebugEnabled(Logger logger, boolean enabled) {
        return enabled && logger.isDebugEnabled();
    }

    /**
     * @param envVar the environment variable to check
     * @return whether {@code envVar} is set to {@code "1"} or {@code "true"} (case-insensitive)
     */
    private static boolean isEnvVarSet(String envVar) {
        var value = System.getenv(envVar);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}
