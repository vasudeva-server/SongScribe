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

package songscribe.io.musicxml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElement.Accidental;

/**
 * Verifies the {@link PitchSpelling} bijection: {@code spell ∘ staffPositionFor}
 * and {@code staffPositionFor ∘ spell} are both identity, and that
 * {@code alterFor} returns the correct sounding semitone per accidental.
 */
class PitchSpellingTest extends UnitTest {

    // Staff-position range under test: B7 (staffPosition -21) down to B-1
    // (staffPosition 35), spanning 8 full octaves of the diatonic lattice.
    private static final int STAFF_POSITION_MIN = -21;
    private static final int STAFF_POSITION_MAX = 35;

    // Expected sounding <alter> semitones per accidental, pinned to literals so a
    // regression in the production alteration table is caught (these must NOT be
    // derived from production constants).
    private static final int NATURAL_ALTER       = 0;
    private static final int FLAT_ALTER          = -1;
    private static final int SHARP_ALTER         = 1;
    private static final int DOUBLE_FLAT_ALTER   = -2;
    private static final int DOUBLE_SHARP_ALTER  = 2;
    private static final int NATURAL_FLAT_ALTER  = -1;
    private static final int NATURAL_SHARP_ALTER = 1;

    // -------------------------------------------------------------------------
    // Bijection: spell ∘ staffPositionFor  (forward then inverse)
    // -------------------------------------------------------------------------

    @Test
    void testSpellThenStaffPositionForIsIdentityAcrossFullRange() {
        for (var staffPosition = STAFF_POSITION_MIN; staffPosition <= STAFF_POSITION_MAX; staffPosition++) {
            var pitch = PitchSpelling.spell(staffPosition);
            var roundTripped = PitchSpelling.staffPositionFor(pitch.step(), pitch.octave());
            assertThat(roundTripped)
                .as("spell then staffPositionFor must be identity: staffPosition=%d spelled as %s%d",
                    staffPosition, pitch.step(), pitch.octave())
                .isEqualTo(staffPosition);
        }
    }

    // -------------------------------------------------------------------------
    // Bijection: staffPositionFor ∘ spell  (inverse then forward)
    // -------------------------------------------------------------------------

    @Test
    void testStaffPositionForThenSpellIsIdentityAcrossFullRange() {
        for (var staffPosition = STAFF_POSITION_MIN; staffPosition <= STAFF_POSITION_MAX; staffPosition++) {
            var expected = PitchSpelling.spell(staffPosition);
            var candidatePosition = PitchSpelling.staffPositionFor(expected.step(), expected.octave());
            var roundTripped = PitchSpelling.spell(candidatePosition);
            assertThat(roundTripped)
                .as("staffPositionFor then spell must be identity: staffPosition=%d (step=%s octave=%d)",
                    staffPosition, expected.step(), expected.octave())
                .isEqualTo(expected);
        }
    }

    // -------------------------------------------------------------------------
    // alterFor: sounding-semitone derivation per accidental, pinned to literals
    // -------------------------------------------------------------------------

    @Test
    void testAlterForReturnsCorrectSoundingSemitonePerAccidental() {
        assertThat(PitchSpelling.alterFor(Accidental.NATURAL)).as("NATURAL").isEqualTo(NATURAL_ALTER);
        assertThat(PitchSpelling.alterFor(Accidental.FLAT)).as("FLAT").isEqualTo(FLAT_ALTER);
        assertThat(PitchSpelling.alterFor(Accidental.SHARP)).as("SHARP").isEqualTo(SHARP_ALTER);
        assertThat(PitchSpelling.alterFor(Accidental.DOUBLE_FLAT)).as("DOUBLE_FLAT").isEqualTo(DOUBLE_FLAT_ALTER);
        assertThat(PitchSpelling.alterFor(Accidental.DOUBLE_SHARP)).as("DOUBLE_SHARP").isEqualTo(DOUBLE_SHARP_ALTER);
        assertThat(PitchSpelling.alterFor(Accidental.NATURAL_FLAT)).as("NATURAL_FLAT").isEqualTo(NATURAL_FLAT_ALTER);
        assertThat(PitchSpelling.alterFor(Accidental.NATURAL_SHARP)).as("NATURAL_SHARP").isEqualTo(NATURAL_SHARP_ALTER);
    }

    @Test
    void testAlterForNullAccidentalReturnsZero() {
        assertThat(PitchSpelling.alterFor(null))
            .as("a null accidental sounds natural (alter 0)")
            .isEqualTo(NATURAL_ALTER);
    }

    @Test
    void testStaffPositionForThrowsForUnknownStepLetter() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> PitchSpelling.staffPositionFor('X', 4))
            .withMessageContaining("X");
    }

    // -------------------------------------------------------------------------
    // soundingAlterFor: explicit-accidental path
    // -------------------------------------------------------------------------

    @Test
    void testSoundingAlterForWithExplicitSharpReturnsPositiveOne() {
        var line = detachedLine();
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);
        note.setAccidental(Accidental.SHARP);

        assertThat(PitchSpelling.soundingAlterFor(note))
            .as("soundingAlterFor a note with explicit SHARP accidental must return +1")
            .isEqualTo(1);
    }

    @Test
    void testSoundingAlterForWithExplicitFlatReturnsNegativeOne() {
        var line = detachedLine();
        var note = ElementType.CROTCHET.newInstance();
        line.addElement(note);
        note.setAccidental(Accidental.FLAT);

        assertThat(PitchSpelling.soundingAlterFor(note))
            .as("soundingAlterFor a note with explicit FLAT accidental must return -1")
            .isEqualTo(-1);
    }

    // -------------------------------------------------------------------------
    // soundingAlterFor: key-derived-accidental path (both KeyType values)
    //
    // When a note has no explicit accidental, soundingAlterFor looks up the
    // effective key via findLastAccidental → getAccidental(Line).
    // -------------------------------------------------------------------------

    @Test
    void testSoundingAlterForUsesKeyDerivedSharpWhenNoExplicitAccidental() {
        // G major: 1 sharp → F# (pitch index 4 = F is sharped).
        // F4 lies at staffPosition 3: getPitchIndex(3) = (7 − 3) % 7 = 4 = F.
        var fSharpStaffPosition = PitchSpelling.staffPositionFor('F', 4);
        var song = new Song();
        var noteHolder = new StaffElement[1];

        song.withoutMutationTracking(() -> {
            song.removeLine(0);

            var line = new Line(song);
            line.setKeyType(KeyType.SHARPS);
            line.setKeyAccidentalCount(1);

            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(fSharpStaffPosition);
            line.addElement(note);
            song.addLine(line);
            noteHolder[0] = note;
        });

        assertThat(PitchSpelling.soundingAlterFor(noteHolder[0]))
            .as("soundingAlterFor F4 in 1-sharp key (G major) with no explicit accidental must return +1")
            .isEqualTo(1);
    }

    @Test
    void testSoundingAlterForUsesKeyDerivedFlatWhenNoExplicitAccidental() {
        // F major: 1 flat → Bb (pitch index 0 = B is flatted).
        // B4 is the lattice origin: staffPosition 0 = B, octave 4.
        var bFlatStaffPosition = PitchSpelling.staffPositionFor('B', 4);
        var song = new Song();
        var noteHolder = new StaffElement[1];

        song.withoutMutationTracking(() -> {
            song.removeLine(0);

            var line = new Line(song);
            line.setKeyType(KeyType.FLATS);
            line.setKeyAccidentalCount(1);

            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(bFlatStaffPosition);
            line.addElement(note);
            song.addLine(line);
            noteHolder[0] = note;
        });

        assertThat(PitchSpelling.soundingAlterFor(noteHolder[0]))
            .as("soundingAlterFor B4 in 1-flat key (F major) with no explicit accidental must return -1")
            .isEqualTo(-1);
    }
}
