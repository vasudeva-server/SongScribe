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
 * <p>The constants are declared outermost first, so {@link #values()} walks the stack from the
 * primary beam inward and {@link #next()} steps one level in. A level exists for every beam a
 * beamable note can carry, and for no more.
 */
public enum BeamLevel {
    /** The primary beam, carried by every member of a beam group. */
    EIGHTH(1),

    /** The second beam, carried by 16ths and shorter. */
    SIXTEENTH(2),

    /** The third beam, carried by 32nds. */
    THIRTY_SECOND(3);

    /** The MusicXML {@code <beam number>} of this level. */
    private final int musicXmlNumber;

    BeamLevel(int musicXmlNumber) {
        this.musicXmlNumber = musicXmlNumber;
    }

    /**
     * Returns this level's MusicXML {@code <beam number>}, which counts levels from the outside
     * in.
     *
     * @return 1 for the primary beam, one more for each level further in
     */
    public int number() {
        return musicXmlNumber;
    }

    /**
     * Returns whether a note of {@code type} carries a beam at this level. A note carries every
     * level up to its {@link ElementType#beamCount()}, so the levels a note carries are always
     * the outermost ones.
     *
     * @param type the note's type
     * @return {@code true} when {@code type}'s beam count reaches this level; {@code false} for
     *     every level when {@code type} is not beamable
     */
    public boolean isCarriedBy(ElementType type) {
        return type.beamCount() >= number();
    }

    /**
     * Returns the level one step further in from this one.
     *
     * @return the next inner level, or {@code null} when this is the innermost level,
     *     {@link #THIRTY_SECOND}
     */
    public @Nullable BeamLevel next() {
        var levels = values();
        var nextOrdinal = ordinal() + 1;

        return nextOrdinal < levels.length ? levels[nextOrdinal] : null;
    }
}
