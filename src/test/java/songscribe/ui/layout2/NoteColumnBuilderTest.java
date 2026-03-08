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

package songscribe.ui.layout2;

import songscribe.UnitTest;
import songscribe.music.Note;
import songscribe.music.NoteType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteColumnBuilderTest extends UnitTest {

    private static Note note(NoteType type) {
        return type.newInstance();
    }

    // T1: Unbeamed quaver right extent > notehead-only extent
    @Test
    void testUnbeamedQuaverExtentExceedsNoteheadOnly() {
        var quaver = note(NoteType.QUAVER);
        double noteheadOnly = NoteColumnBuilder.NOTE_HEAD_WIDTH_SS;
        double extent = NoteColumnBuilder.calculateRightExtentSs(quaver, false, true);

        assertThat(extent).isGreaterThan(noteheadOnly);
    }

    // T2: Beamed quaver right extent equals notehead-only extent (no flag contribution)
    @Test
    void testBeamedQuaverExtentEqualsNoteheadOnly() {
        var quaver = note(NoteType.QUAVER);
        double noteheadOnly = NoteColumnBuilder.NOTE_HEAD_WIDTH_SS;
        double extent = NoteColumnBuilder.calculateRightExtentSs(quaver, true, true);

        assertThat(extent).isEqualTo(noteheadOnly);
    }

    // T3: Non-flagged types (CROTCHET, MINIM, SEMIBREVE) are unchanged by beamed/upper
    @Test
    void testNonFlaggedTypesUnchanged() {
        for (var type : new NoteType[]{NoteType.CROTCHET, NoteType.MINIM, NoteType.SEMIBREVE}) {
            var n = note(type);
            double noteheadOnly = NoteColumnBuilder.NOTE_HEAD_WIDTH_SS;
            double extentUnbeamed = NoteColumnBuilder.calculateRightExtentSs(n, false, true);
            double extentBeamed = NoteColumnBuilder.calculateRightExtentSs(n, true, true);

            assertThat(extentUnbeamed)
                .as("Unbeamed %s should equal notehead extent", type)
                .isEqualTo(noteheadOnly);
            assertThat(extentBeamed)
                .as("Beamed %s should equal notehead extent", type)
                .isEqualTo(noteheadOnly);
        }
    }

    // T4: Stem-up vs stem-down produce different extents for an unbeamed quaver
    @Test
    void testStemUpVsStemDownProduceDifferentExtents() {
        var quaver = note(NoteType.QUAVER);
        double upExtent = NoteColumnBuilder.calculateRightExtentSs(quaver, false, true);
        double downExtent = NoteColumnBuilder.calculateRightExtentSs(quaver, false, false);

        assertThat(upExtent).isNotEqualTo(downExtent);
    }

    // T5: Grace quaver gets a scaled flag width (smaller than regular quaver)
    @Test
    void testGraceQuaverExtentSmallerThanRegularQuaver() {
        var graceQuaver = note(NoteType.GRACE_QUAVER);
        var regularQuaver = note(NoteType.QUAVER);

        double graceExtent = NoteColumnBuilder.calculateRightExtentSs(graceQuaver, false, true);
        double regularExtent = NoteColumnBuilder.calculateRightExtentSs(regularQuaver, false, true);

        assertThat(graceExtent).isLessThan(regularExtent);
    }

    // T6: Dotted quaver extent is >= both notehead+dots extent and flag extent individually
    @Test
    void testDottedQuaverExtentIsMaxOfDotsAndFlag() {
        var dottedQuaver = note(NoteType.QUAVER);
        dottedQuaver.setDotCount(1);

        double dotsOnlyExtent = NoteColumnBuilder.NOTE_HEAD_WIDTH_SS + 0.25 + 0.5; // DOT_GAP + DOT_WIDTH

        var undottedQuaver = note(NoteType.QUAVER);
        double flagOnlyExtent = NoteColumnBuilder.calculateRightExtentSs(undottedQuaver, false, true);

        double actual = NoteColumnBuilder.calculateRightExtentSs(dottedQuaver, false, true);

        assertThat(actual)
            .isGreaterThanOrEqualTo(dotsOnlyExtent)
            .isGreaterThanOrEqualTo(flagOnlyExtent);
    }
}
