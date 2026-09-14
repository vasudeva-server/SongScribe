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

package songscribe.io;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Accidental;
import songscribe.dom.ChainedAccidental.ChainType;
import songscribe.dom.NoteElement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link songscribe.io.LoadMigrations#run}'s promise for the load pass: a note the file writes
 * an accidental on sounds that accidental, a bare note adopts what is in force at its position —
 * barlines included — and parentheses are ignored, on {@code accidental-load.musicxml}, a fixture
 * this program itself wrote under the barline-reset policy this test pins the repair against.
 */
class LoadMigrationsTest extends UnitTest {

    @Test
    void testABareNoteAfterABarlineSoundsWhatIsInForceAndIsARepetition() throws Exception {
        var song = loadFixture("accidental-load");
        var notes = song.getLine(0).getElements().stream()
            .filter(NoteElement.class::isInstance)
            .map(NoteElement.class::cast)
            .toList();

        var leadingFlat = notes.get(0);
        var afterBarline = notes.get(1);

        assertThat(leadingFlat.chainedAccidental().type()).isEqualTo(ChainType.ANCHOR);
        assertThat(afterBarline.chainedAccidental().accidental())
            .as("the barline written between the two notes does not reset what is in force")
            .isEqualTo(leadingFlat.chainedAccidental().accidental());
        assertThat(afterBarline.chainedAccidental().type()).isEqualTo(ChainType.REPETITION);
    }

    @Test
    void testAParenthesizedAccidentalReadsAsAPlainOne() throws Exception {
        var song = loadFixture("accidental-load");
        var notes = song.getLine(0).getElements().stream()
            .filter(NoteElement.class::isInstance)
            .map(NoteElement.class::cast)
            .toList();

        var plainFlat = notes.get(2);
        var parenthesizedFlat = notes.get(3);

        assertThat(parenthesizedFlat.chainedAccidental().accidental()).isEqualTo(Accidental.FLAT);
        assertThat(parenthesizedFlat.chainedAccidental().type())
            .as("sounding the same flat right after the plain one, with nothing between them, it is implicit — "
                + "exactly as a plain repeated accidental would be")
            .isEqualTo(ChainType.IMPLICIT);
        assertThat(plainFlat.chainedAccidental().type()).isEqualTo(ChainType.ANCHOR);
    }
}
