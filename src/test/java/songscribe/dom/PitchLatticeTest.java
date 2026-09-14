/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.dom;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.engraving.StaffPosition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DiatonicPitch#at}'s promise to spell and sound every staff position exactly as the
 * pitch lattice defines it, and its promise to {@link DiatonicPitch#staffPosition()} that the
 * two are exact inverses of one another.
 */
class PitchLatticeTest extends UnitTest {

    /**
     * One row of the lattice. {@code midiNatural} is the pitch the position sounds both with a
     * written natural and with no accidental at all, which is the lattice's own claim that the
     * two agree.
     */
    private record PitchLatticeCase(
        int staffPosition,
        PitchClass pitchClass,
        int octave,
        int midiNatural,
        int midiFlat,
        int midiSharp,
        int midiDoubleFlat,
        int midiDoubleSharp) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testAtSpellsTheStepAndOctaveTheCorpusRecords(PitchLatticeCase testCase) {
        var pitch = DiatonicPitch.at(testCase.staffPosition());

        assertThat(pitch.pitchClass()).isEqualTo(testCase.pitchClass());
        assertThat(pitch.octave()).isEqualTo(testCase.octave());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testMidiSoundsThePitchTheCorpusRecordsForEveryAccidental(PitchLatticeCase testCase) {
        var pitch = DiatonicPitch.at(testCase.staffPosition());

        assertThat(pitch.midi(null)).isEqualTo(testCase.midiNatural());
        assertThat(pitch.midi(Accidental.NATURAL)).isEqualTo(testCase.midiNatural());
        assertThat(pitch.midi(Accidental.FLAT)).isEqualTo(testCase.midiFlat());
        assertThat(pitch.midi(Accidental.SHARP)).isEqualTo(testCase.midiSharp());
        assertThat(pitch.midi(Accidental.DOUBLE_FLAT)).isEqualTo(testCase.midiDoubleFlat());
        assertThat(pitch.midi(Accidental.DOUBLE_SHARP)).isEqualTo(testCase.midiDoubleSharp());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testStaffPositionInvertsAt(PitchLatticeCase testCase) {
        assertThat(DiatonicPitch.at(testCase.staffPosition()).staffPosition())
            .isEqualTo(testCase.staffPosition());
    }

    @Test
    void testCorpusCoversEveryStaffPosition() {
        assertThat(cases()).hasSize(StaffPosition.MAX.value() - StaffPosition.MIN.value() + 1);
    }

    static Stream<PitchLatticeCase> cases() {
        return Stream.of(
            new PitchLatticeCase(-10, PitchClass.E, 6, 88, 87, 89, 86, 90),
            new PitchLatticeCase(-9, PitchClass.D, 6, 86, 85, 87, 84, 88),
            new PitchLatticeCase(-8, PitchClass.C, 6, 84, 83, 85, 82, 86),
            new PitchLatticeCase(-7, PitchClass.B, 5, 83, 82, 84, 81, 85),
            new PitchLatticeCase(-6, PitchClass.A, 5, 81, 80, 82, 79, 83),
            new PitchLatticeCase(-5, PitchClass.G, 5, 79, 78, 80, 77, 81),
            new PitchLatticeCase(-4, PitchClass.F, 5, 77, 76, 78, 75, 79),
            new PitchLatticeCase(-3, PitchClass.E, 5, 76, 75, 77, 74, 78),
            new PitchLatticeCase(-2, PitchClass.D, 5, 74, 73, 75, 72, 76),
            new PitchLatticeCase(-1, PitchClass.C, 5, 72, 71, 73, 70, 74),
            new PitchLatticeCase(0, PitchClass.B, 4, 71, 70, 72, 69, 73),
            new PitchLatticeCase(1, PitchClass.A, 4, 69, 68, 70, 67, 71),
            new PitchLatticeCase(2, PitchClass.G, 4, 67, 66, 68, 65, 69),
            new PitchLatticeCase(3, PitchClass.F, 4, 65, 64, 66, 63, 67),
            new PitchLatticeCase(4, PitchClass.E, 4, 64, 63, 65, 62, 66),
            new PitchLatticeCase(5, PitchClass.D, 4, 62, 61, 63, 60, 64),
            new PitchLatticeCase(6, PitchClass.C, 4, 60, 59, 61, 58, 62),
            new PitchLatticeCase(7, PitchClass.B, 3, 59, 58, 60, 57, 61),
            new PitchLatticeCase(8, PitchClass.A, 3, 57, 56, 58, 55, 59),
            new PitchLatticeCase(9, PitchClass.G, 3, 55, 54, 56, 53, 57),
            new PitchLatticeCase(10, PitchClass.F, 3, 53, 52, 54, 51, 55),
            new PitchLatticeCase(11, PitchClass.E, 3, 52, 51, 53, 50, 54),
            new PitchLatticeCase(12, PitchClass.D, 3, 50, 49, 51, 48, 52)
        );
    }
}
