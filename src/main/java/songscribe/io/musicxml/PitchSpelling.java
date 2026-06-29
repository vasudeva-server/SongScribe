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

import org.jspecify.annotations.Nullable;

import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Accidental;

/**
 * Bijective conversion between a SongScribe {@code staffPosition} and a MusicXML
 * {@code <pitch>}'s diatonic {@code <step>} + {@code <octave>}, plus the
 * sounding {@code <alter>} derivation.
 *
 * <h2>The lattice</h2>
 * A {@code staffPosition} is a diatonic step index (half a staff space each):
 * the origin <b>B4 = staffPosition 0</b>, increasing <i>downward</i> in pitch
 * (positive = lower). Seven staff positions span one octave, and the octave
 * number (scientific pitch notation) flips at the B&rarr;C boundary, which by
 * construction falls on every multiple of seven — so each contiguous run of
 * seven staff positions is exactly one octave, B down to C.
 *
 * <pre>
 *   staffPosition : step / octave        natural MIDI
 *   ------------- : -------------         ------------
 *        -2       :  D5                   74
 *        -1       :  C5                   72
 *  group  0  ---> :  B4   &lt;-- origin       71   floorDiv(s,7) == 0  => octave 4
 *         1       :  A4                   69
 *         2       :  G4                   67
 *         3       :  F4                   65
 *         4       :  E4                   64
 *         5       :  D4                   62
 *         6       :  C4                   60
 *  group  7  ---> :  B3                   59   floorDiv(s,7) == 1  => octave 3
 *         8       :  A3                   57
 *        ...
 *
 *   step  = STEP_BY_PITCH_INDEX[StaffElement.getPitchIndex(s)]
 *           (getPitchIndex: 0=B 1=C 2=D 3=E 4=F 5=G 6=A, owned by MIDI_PITCHES)
 *   octave = BASE_OCTAVE - floorDiv(s, STEPS_PER_OCTAVE)
 * </pre>
 *
 * <p>The step decomposition reuses {@link StaffElement#getPitchIndex(int)} — the
 * single source of truth for the B4 = 0 origin — rather than re-deriving it, so
 * this helper cannot silently diverge if that origin ever changes. The inverse
 * likewise probes {@code getPitchIndex} over the seven candidates of the target
 * octave group rather than hardcoding a reverse table, keeping forward&middot;inverse
 * an exact identity by construction.
 *
 * <h2>Sounding {@code <alter>} vs displayed {@code <accidental>}</h2>
 * {@code <step>} + {@code <octave>} + {@code <alter>} are <b>authoritative for
 * pitch</b> and key-independent: pitch is restored from {@code <step>}/{@code
 * <octave>} alone (the {@code <alter>} is the sounding semitone, emitted so an
 * external renderer plays the correct pitch). The <i>displayed</i> accidental
 * glyph is a separate concern recovered from {@code <accidental>} (Phase 5),
 * <b>not</b> from {@code <alter>}. The two are genuinely independent:
 * <ul>
 *   <li>a note altered <b>by key</b> sounds altered ({@code <alter>} &ne; 0) yet
 *       shows <b>no</b> glyph (no {@code <accidental>});</li>
 *   <li>a <b>cautionary natural</b> shows a glyph ({@code <accidental>natural})
 *       while carrying <b>no</b> key alteration ({@code <alter>} == 0).</li>
 * </ul>
 * This is also why no {@code <alter>}&rarr;{@code Accidental} reverse exists: a
 * given semitone (e.g. &minus;1) is spelled by more than one accidental
 * ({@code FLAT} or {@code NATURAL_FLAT}), so the glyph cannot be recovered from
 * the sounding alteration — the reader takes it from {@code <accidental>}.
 */
public final class PitchSpelling {

    // Origin of the lattice: B4 lives at staffPosition 0, so the octave of the
    // staff-position group containing 0 is 4.
    private static final int BASE_OCTAVE = 4;

    // Diatonic steps per octave (one staff-position group).
    private static final int STEPS_PER_OCTAVE = 7;

    // <step> letters indexed by StaffElement.getPitchIndex(): 0=B .. 6=A.
    private static final char[] STEP_BY_PITCH_INDEX = {'B', 'C', 'D', 'E', 'F', 'G', 'A'};

    private PitchSpelling() {}

    // -------------------------------------------------------------------------
    // Forward: staffPosition -> (<step>, <octave>), diatonic, accidental-free
    // -------------------------------------------------------------------------

    /**
     * Returns the diatonic {@code <step>} letter and {@code <octave>} for the
     * given staff position. Independent of any accidental.
     */
    public static Pitch spell(int staffPosition) {
        var step = STEP_BY_PITCH_INDEX[StaffElement.getPitchIndex(staffPosition)];
        var octave = BASE_OCTAVE - Math.floorDiv(staffPosition, STEPS_PER_OCTAVE);

        return new Pitch(step, octave);
    }

    // -------------------------------------------------------------------------
    // Inverse: (<step>, <octave>) -> staffPosition, diatonic, accidental-free
    // -------------------------------------------------------------------------

    /**
     * Returns the staff position for the given diatonic {@code <step>} letter and
     * {@code <octave>}. Exact inverse of {@link #spell(int)}.
     */
    public static int staffPositionFor(char step, int octave) {
        var pitchIndex = pitchIndexForStep(step);
        var groupStart = (BASE_OCTAVE - octave) * STEPS_PER_OCTAVE;

        // Within the octave group [groupStart, groupStart + 6] exactly one staff
        // position has the requested pitch index. Probing getPitchIndex (instead
        // of a hardcoded reverse) keeps this consistent with the forward path.
        for (var offset = 0; offset < STEPS_PER_OCTAVE; offset++) {
            var candidate = groupStart + offset;

            if (StaffElement.getPitchIndex(candidate) == pitchIndex) {
                return candidate;
            }
        }

        throw new IllegalArgumentException("Unspellable pitch: step=" + step + " octave=" + octave);
    }

    // -------------------------------------------------------------------------
    // Sounding <alter> derivation (semitones)
    // -------------------------------------------------------------------------

    /**
     * Returns the sounding alteration in semitones for the given accidental, for
     * emission as {@code <alter>}. A {@code null} accidental yields {@code 0}
     * (natural / no alteration).
     */
    public static int alterFor(@Nullable Accidental accidental) {
        return (accidental == null) ? 0 : StaffElement.getPitchAdjustment(accidental);
    }

    /**
     * Returns the sounding alteration in semitones for a note: from its explicit
     * accidental when present, otherwise from the effective key
     * ({@link StaffElement#findLastAccidental()}), so the emitted {@code <pitch>}
     * sounds correct even with no visible accidental. Mirrors the logic of
     * {@link StaffElement#getPitch()} so {@code <alter>} agrees with playback.
     */
    public static int soundingAlterFor(StaffElement note) {
        var accidental = note.getAccidental();

        if (accidental == null) {
            accidental = note.findLastAccidental();
        }

        return alterFor(accidental);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private static int pitchIndexForStep(char step) {
        for (var i = 0; i < STEP_BY_PITCH_INDEX.length; i++) {
            if (STEP_BY_PITCH_INDEX[i] == step) {
                return i;
            }
        }

        throw new IllegalArgumentException("Unknown step letter: " + step);
    }

    // -------------------------------------------------------------------------
    // Value type
    // -------------------------------------------------------------------------

    /**
     * A diatonic MusicXML pitch spelling: the {@code <step>} letter (A&ndash;G)
     * and the {@code <octave>} number. Carries no alteration.
     */
    public record Pitch(char step, int octave) {}
}
