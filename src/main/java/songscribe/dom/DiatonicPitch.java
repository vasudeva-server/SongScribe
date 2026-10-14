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
 * A pitch as the staff writes it: a pitch class and an octave, with no accidental. This is
 * the one place the staff's pitch lattice is turned into a sounding pitch or a written
 * spelling, and the one place the octave boundary is computed.
 */
public record DiatonicPitch(PitchClass pitchClass, int octave) {

    /** The octave of the staff-position group holding position 0, where B sits: B4. */
    private static final int BASE_OCTAVE = 4;

    private static final int SEMITONES_PER_OCTAVE = 12;

    /** MIDI numbers put C-1 at 0. */
    private static final int MIDI_OCTAVE_ORIGIN = 1;

    /**
     * Returns the pitch written at {@code staffPosition}.
     *
     * @param staffPosition a staff position in half staff spaces from the middle line
     * @return the pitch there; the exact inverse of {@link #staffPosition()}
     */
    public static DiatonicPitch at(int staffPosition) {
        return new DiatonicPitch(
            PitchClass.at(staffPosition),
            BASE_OCTAVE - Math.floorDiv(staffPosition, PitchClass.STEPS_PER_OCTAVE));
    }

    /**
     * Returns the staff position this pitch is written at.
     *
     * @return the staff position; the exact inverse of {@link #at(int)}, and not
     *     necessarily within {@link songscribe.engraving.StaffPosition#MIN}..{@link
     *     songscribe.engraving.StaffPosition#MAX} — a caller that needs a drawable position
     *     checks that itself
     */
    public int staffPosition() {
        return (BASE_OCTAVE - octave) * PitchClass.STEPS_PER_OCTAVE + pitchClass.groupOffset();
    }

    /**
     * Returns the MIDI note number this pitch sounds as when altered by {@code accidental}.
     *
     * @param accidental the accidental in effect
     * @return the MIDI note number
     */
    public int midi(Accidental accidental) {
        return (octave + MIDI_OCTAVE_ORIGIN) * SEMITONES_PER_OCTAVE
            + pitchClass.semitone()
            + accidental.semitones();
    }
}
