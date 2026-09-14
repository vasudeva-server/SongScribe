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
 * {@code <alter>} derivation. The pitch lattice itself — a staff position's letter, octave and
 * sounding pitch — belongs to {@link PitchClass} and {@link DiatonicPitch}; what stays here is
 * only the MusicXML vocabulary and the fact that the displayed accidental glyph cannot be
 * recovered from the sounding alteration alone.
 *
 * <h2>Sounding {@code <alter>} vs displayed {@code <accidental>}</h2>
 * {@code <step>} + {@code <octave>} + {@code <alter>} are <b>authoritative for
 * pitch</b> and key-independent: pitch is restored from {@code <step>}/{@code
 * <octave>} alone, and {@code <alter>} is the sounding accidental stored on the
 * note itself, emitted so an external renderer plays the correct pitch. The
 * <i>displayed</i> accidental glyph is a separate concern, decided by the chain
 * walk and recovered on read from {@code <accidental>}, <b>not</b> from
 * {@code <alter>}. The two are genuinely independent:
 * <ul>
 *   <li>a note altered <b>by key</b> sounds altered ({@code <alter>} &ne; 0) yet
 *       shows <b>no</b> glyph (no {@code <accidental>});</li>
 *   <li>a <b>cautionary natural</b> shows a glyph ({@code <accidental>natural})
 *       while carrying <b>no</b> key alteration ({@code <alter>} == 0).</li>
 * </ul>
 * This is also why no {@code <alter>}&rarr;{@code Accidental} reverse exists:
 * the two bullets above show {@code <alter>} and the displayed glyph varying
 * independently of each other, so the glyph cannot be recovered from the
 * sounding alteration alone — the reader takes it from {@code <accidental>}.
 */
public final class PitchSpelling {

    private PitchSpelling() {}

    // -------------------------------------------------------------------------
    // Inverse: (<step>, <octave>) -> staffPosition, diatonic, accidental-free
    // -------------------------------------------------------------------------

    /**
     * Returns the staff position for the given diatonic {@code <step>} letter and
     * {@code <octave>}.
     *
     * @param step   the diatonic step letter, {@code A}..{@code G}
     * @param octave the scientific-pitch-notation octave number
     * @return the staff position the pitch spells to
     * @throws PitchOutOfRangeException if that position lies outside
     *     {@link StaffPosition#MIN}..{@link StaffPosition#MAX}
     * @throws IllegalArgumentException if {@code step} is not one of the seven diatonic letters,
     *     which {@link PitchClass#valueOf} refuses. MusicXML cannot express such a step — the
     *     schema type is an enumeration of {@code A}..{@code G} — so a document never reaches
     *     this, and no separate refusal is composed for it
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
     * Returns the sounding alteration in semitones for a note, from
     * {@link NoteElement#soundingAccidental()} alone, so the emitted {@code <pitch>}
     * sounds correct whether or not the note shows a visible accidental.
     */
    public static int soundingAlterFor(NoteElement note) {
        return Accidental.semitoneAdjustmentOf(note.soundingAccidental());
    }
}
