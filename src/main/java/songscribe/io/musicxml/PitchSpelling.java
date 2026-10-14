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
package songscribe.io.musicxml;

import songscribe.dom.Accidental;
import songscribe.dom.DiatonicPitch;
import songscribe.dom.NoteElement;
import songscribe.dom.PitchClass;
import songscribe.engraving.PitchOutOfRangeException;
import songscribe.engraving.StaffPosition;

/**
 * The MusicXML edge of pitch: converting a SongScribe {@code staffPosition} to and from a
 * {@code <pitch>}'s diatonic {@code <step>} + {@code <octave>}, plus the sounding
 * {@code <alter>} derivation.
 *
 * <p>The displayed accidental glyph is recovered on read from {@code <accidental>}, never from
 * {@code <alter>}: a key-altered note has a nonzero {@code <alter>} and no glyph, and a
 * cautionary natural has a glyph and a zero {@code <alter>}. That is why no
 * {@code <alter>}&rarr;{@code Accidental} reverse exists.
 */
public final class PitchSpelling {

    private PitchSpelling() {}

    // -------------------------------------------------------------------------
    // Inverse: (<step>, <octave>) -> staffPosition, diatonic, accidental-free
    // -------------------------------------------------------------------------

    /**
     * {@code step} is one of {@code A}..{@code G}, which the schema guarantees for a document.
     */
    public static StaffPosition staffPositionFor(char step, int octave) {
        var staffPositionSp = new DiatonicPitch(PitchClass.valueOf(String.valueOf(step)), octave)
            .staffPosition();

        return new StaffPosition(staffPositionSp);
    }

    // -------------------------------------------------------------------------
    // Sounding <alter> derivation (semitones)
    // -------------------------------------------------------------------------

    /**
     * Derived from {@link NoteElement#soundingAccidental()} alone, so the pitch sounds correct
     * whether or not the note shows an accidental.
     */
    public static int soundingAlterFor(NoteElement note) {
        return note.soundingAccidental().semitones();
    }
}
