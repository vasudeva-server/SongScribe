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

/**
 * A diatonic pitch class — the letter of a note, with no octave and no accidental.
 *
 * <p>Each constant carries the two numbers that define the staff's pitch lattice, and
 * they are stated nowhere else: its offset within a staff-position octave group, and its
 * semitone within the octave. Staff positions count downward, so within a group the pitch
 * descends as the position rises, and the offsets run B, A, G, F, E, D, C.
 */
public enum PitchClass {
    C(6, 0),
    D(5, 2),
    E(4, 4),
    F(3, 5),
    G(2, 7),
    A(1, 9),
    B(0, 11);

    /** Diatonic steps in one octave, which is also one staff-position group. */
    public static final int STEPS_PER_OCTAVE = values().length;

    private static final PitchClass[] BY_GROUP_OFFSET = byGroupOffset();

    private final int groupOffset;
    private final int semitone;

    PitchClass(int groupOffset, int semitone) {
        this.groupOffset = groupOffset;
        this.semitone = semitone;
    }

    /**
     * Returns the pitch class at {@code staffPosition}, ignoring its octave.
     *
     * @param staffPosition a staff position in half staff spaces from the middle line
     * @return the pitch class there; never null, for any int
     */
    public static PitchClass at(int staffPosition) {
        return BY_GROUP_OFFSET[Math.floorMod(staffPosition, STEPS_PER_OCTAVE)];
    }

    /**
     * Returns this pitch class's position within a staff-position octave group.
     *
     * @return 0 for B through 6 for C, each constant its own value
     */
    int groupOffset() {
        return groupOffset;
    }

    /**
     * Returns this pitch class's semitone within the octave.
     *
     * @return 0 for C through 11 for B, ascending in letter order
     */
    int semitone() {
        return semitone;
    }

    private static PitchClass[] byGroupOffset() {
        var table = new PitchClass[STEPS_PER_OCTAVE];

        for (var pitchClass : values()) {
            table[pitchClass.groupOffset] = pitchClass;
        }

        for (var pitchClass : table) {
            if (pitchClass == null) {
                throw new IllegalStateException("Group offsets do not cover 0.." + (STEPS_PER_OCTAVE - 1));
            }
        }

        return table;
    }
}
