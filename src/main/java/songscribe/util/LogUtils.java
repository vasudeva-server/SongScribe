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
    public static final String DEBUG_BEAMS_ENV_VAR = "DEBUG_BEAMS";

    /** Env var that must be set (to {@code 1} or {@code true}) to enable tie debug logging. */
    public static final String DEBUG_TIES_ENV_VAR = "DEBUG_TIES";

    private LogUtils() {
    }

    /**
     * Whether debug logging is enabled for {@code logger} AND a subsystem-specific
     * debug flag is set. Used to keep noisy, subsystem-specific debug logging
     * (e.g. beam scoring) silent when debug logging is turned on for other
     * purposes.
     *
     * @param logger the logger the caller is about to log to
     * @param envVar the environment variable that gates this subsystem's debug output
     * @return whether both {@code logger}'s debug level and {@code envVar} are enabled
     */
    public static boolean isDebugEnabled(Logger logger, String envVar) {
        return logger.isDebugEnabled() && isEnvVarSet(envVar);
    }

    /**
     * @param envVar the environment variable to check
     * @return whether {@code envVar} is set to {@code "1"} or {@code "true"} (case-insensitive)
     */
    public static boolean isEnvVarSet(String envVar) {
        var value = System.getenv(envVar);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}
