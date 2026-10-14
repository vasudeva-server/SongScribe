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

    private static final String DEBUG_BEAMS_ENV_VAR = "DEBUG_BEAMS";

    private static final String DEBUG_TIES_ENV_VAR = "DEBUG_TIES";

    private static final String DEBUG_LYRICS_ENV_VAR = "DEBUG_LYRICS";

    private static final String DEBUG_SPACING_ENV_VAR = "DEBUG_SPACING";

    private static final boolean DEBUG_BEAMS_ENABLED = isEnvVarSet(DEBUG_BEAMS_ENV_VAR);

    private static final boolean DEBUG_TIES_ENABLED = isEnvVarSet(DEBUG_TIES_ENV_VAR);

    private static final boolean DEBUG_LYRICS_ENABLED = isEnvVarSet(DEBUG_LYRICS_ENV_VAR);

    private static final boolean DEBUG_SPACING_ENABLED = isEnvVarSet(DEBUG_SPACING_ENV_VAR);

    private LogUtils() {
    }

    public static boolean isTracingBeams(Logger logger) {
        return isTracing(logger, DEBUG_BEAMS_ENABLED);
    }

    public static boolean isTracingTies(Logger logger) {
        return isTracing(logger, DEBUG_TIES_ENABLED);
    }

    public static boolean isTracingLyrics(Logger logger) {
        return isTracing(logger, DEBUG_LYRICS_ENABLED);
    }

    public static boolean isTracingSpacing(Logger logger) {
        return isTracing(logger, DEBUG_SPACING_ENABLED);
    }

    /**
     * Private so that a subsystem's flag is reached only through its matching
     * {@code isTracingXxx} method; another subsystem's flag would gate on the wrong variable.
     */
    private static boolean isTracing(Logger logger, boolean enabled) {
        return enabled && logger.isDebugEnabled();
    }

    private static boolean isEnvVarSet(String envVar) {
        var value = System.getenv(envVar);
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }
}
