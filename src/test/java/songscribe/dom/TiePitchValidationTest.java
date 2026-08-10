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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.RangeQueries;

/**
 * Unit tests for {@link RangeQueries#canToggleTie} pitch validation logic.
 * Uses the {@code tie-pitch-validation} fixture (Db major, 5 flats).
 */
@SuppressWarnings({ "OverlyBroadThrowsClause", "StaticVariableMayNotBeInitialized", "StaticVariableUsedBeforeInitialization" })
class TiePitchValidationTest extends UnitTest {

    // Element indices in tie-pitch-validation.musicxml (ordinals match fixture order)
    private enum Note {
        SAME_PITCH_1,           // sp=-1, no accidental
        SAME_PITCH_2,           // sp=-1, no accidental
        F_SHARP_EXPLICIT,       // sp=3, SHARP
        F_SHARP_INHERITED,      // sp=3, no accidental (inherits SHARP from prior)
        B_SHARP,                // sp=0, SHARP (enharmonic with C)
        C_ENHARMONIC,           // sp=-1, no accidental (enharmonic with B#)
        BB_KEY_SIG_1,           // sp=0, no accidental (resolves to Bb via key sig)
        BB_KEY_SIG_2,           // sp=0, no accidental (resolves to Bb via key sig)
        DOUBLE_FLAT_PREFIX,     // sp=-2, DOUBLE_FLAT (sets accidental context)
        FLAT_CANCELS_DOUBLE_FLAT, // sp=-2, FLAT (back to a single flat, matching the key sig)
        KEY_SIG_RESTORED,       // sp=-2, no accidental (reverts to key sig flat)
        B_NATURAL,              // sp=0, NATURAL
        B_SHARP_CONFLICT,       // sp=0, SHARP
        F_SHARP_EXPLICIT_2,     // sp=3, SHARP
        F_NATURAL_CANCELS,      // sp=3, NATURAL (cancels inherited sharp)
    }

    private static Line line;

    @BeforeAll
    static void loadFixtureData() throws Exception {
        var song = loadFixture("tie-pitch-validation");
        line = song.getLine(0);
    }

    @Test
    void testCanTieDifferentPitches() {
        assertAll(
            () -> assertThat(canTiePair(Note.B_NATURAL, Note.B_SHARP_CONFLICT))
                .as("different accidental").isFalse(),
            () -> assertThat(canTiePair(Note.F_SHARP_EXPLICIT_2, Note.F_NATURAL_CANCELS))
                .as("natural cancels inherited").isFalse()
        );
    }

    @Test
    void testCanTieIdenticalPitches() {
        assertAll(
            () -> assertThat(canTiePair(Note.SAME_PITCH_1, Note.SAME_PITCH_2))
                .as("same pitch ties").isTrue(),
            () -> assertThat(canTiePair(Note.F_SHARP_EXPLICIT, Note.F_SHARP_INHERITED))
                .as("inherited accidental ties").isTrue(),
            () -> assertThat(canTiePair(Note.B_SHARP, Note.C_ENHARMONIC))
                .as("enharmonic ties").isTrue()
        );
    }

    @Test
    void testCanTieWithKeySignature() {
        assertAll(
            () -> assertThat(canTiePair(Note.BB_KEY_SIG_1, Note.BB_KEY_SIG_2))
                .as("key sig accidental ties").isTrue(),
            () -> assertThat(canTiePair(Note.FLAT_CANCELS_DOUBLE_FLAT, Note.KEY_SIG_RESTORED))
                .as("explicit flat ties to the same pitch inherited from the key sig").isTrue()
        );
    }

    private static boolean canTiePair(Note first, Note second) {
        return RangeQueries.canToggleTie(
            new ElementSelection(line, first.ordinal(), second.ordinal()));
    }
}
