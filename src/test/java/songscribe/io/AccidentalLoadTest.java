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

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Accidental;
import songscribe.dom.ChainedAccidental.ChainType;
import songscribe.dom.NoteElement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MusicXML reader ignores the parentheses of a parenthesized accidental, on
 * {@code accidental-load.musicxml}.
 */
class AccidentalLoadTest extends UnitTest {

    private static final String ACCIDENTAL_FIXTURE = "accidental-load";

    /** The accidental fixture's line. */
    private static final int LINE_INDEX = 0;

    /** The notes of the accidental fixture's line, in line order. */
    private static final int PLAIN_FLAT_INDEX = 2;
    private static final int PARENTHESIZED_FLAT_INDEX = 3;

    /** The notes of the accidental fixture's first line, loaded once for the class. */
    private List<NoteElement> accidentalNotes;

    @BeforeAll
    void loadAccidentalNotes() throws Exception {
        accidentalNotes = loadFixture(ACCIDENTAL_FIXTURE).getLine(LINE_INDEX).getElements().stream()
            .filter(NoteElement.class::isInstance)
            .map(NoteElement.class::cast)
            .toList();
    }

    @Test
    void testAParenthesizedAccidentalReadsAsAPlainOne() {
        var plainFlat = accidentalNotes.get(PLAIN_FLAT_INDEX);
        var parenthesizedFlat = accidentalNotes.get(PARENTHESIZED_FLAT_INDEX);

        assertThat(parenthesizedFlat.chainedAccidental().sounding()).isEqualTo(Accidental.FLAT);
        assertThat(parenthesizedFlat.chainedAccidental().type())
            .as("sounding the same flat right after the plain one, with nothing between them, it is implicit — "
                + "exactly as a plain repeated accidental would be")
            .isEqualTo(ChainType.IMPLICIT);
        assertThat(plainFlat.chainedAccidental().type()).isEqualTo(ChainType.ANCHOR);
    }
}
