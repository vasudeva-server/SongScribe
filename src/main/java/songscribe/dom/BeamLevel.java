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

package songscribe.dom;

import org.jspecify.annotations.Nullable;

/**
 * One level of a beam stack, from the primary beam every member carries inward to the
 * shortest value a beam can join.
 *
 * <p>The constants are declared outermost first, which {@link #values()} and {@link #next()}
 * rely on.
 */
public enum BeamLevel {
    EIGHTH,

    SIXTEENTH,

    THIRTY_SECOND;

    private static final BeamLevel[] LEVELS = values();

    public int number() {
        return ordinal() + 1;
    }

    public boolean isCarriedBy(ElementType type) {
        return type.beamCount() >= number();
    }

    /**
     * @return {@code null} when this is the innermost level
     */
    public @Nullable BeamLevel next() {
        var nextOrdinal = ordinal() + 1;

        return nextOrdinal < LEVELS.length ? LEVELS[nextOrdinal] : null;
    }
}
