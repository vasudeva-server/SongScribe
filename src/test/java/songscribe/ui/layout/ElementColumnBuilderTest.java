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

package songscribe.ui.layout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.StaffElement;
import songscribe.smufl.Engraving;

class ElementColumnBuilderTest extends UnitTest {

    private static StaffElement element(ElementType type) {
        return type.newInstance();
    }

    // T2: Beamed quaver right extent equals notehead-only extent (no flag contribution)
    @Test
    void testBeamedQuaverExtentEqualsNoteheadOnly() {
        var quaver = element(ElementType.QUAVER);
        var noteheadOnly = Engraving.NOTE_HEAD_WIDTH_SS;
        var extent = ElementColumnBuilder.calculateRightExtentSs(quaver, true, true);

        assertThat(extent).isEqualTo(noteheadOnly);
    }

    // T6: Dotted quaver extent is >= both notehead+dots extent and flag extent individually
    @Test
    void testDottedQuaverExtentIsMaxOfDotsAndFlag() {
        var dottedQuaver = element(ElementType.QUAVER);
        dottedQuaver.setDotCount(1);

        var dotsOnlyExtent = Engraving.NOTE_HEAD_WIDTH_SS + 0.25 + 0.5; // DOT_GAP + DOT_WIDTH

        var undottedQuaver = element(ElementType.QUAVER);
        var flagOnlyExtent = ElementColumnBuilder.calculateRightExtentSs(undottedQuaver, false, true);

        var actual = ElementColumnBuilder.calculateRightExtentSs(dottedQuaver, false, true);

        assertThat(actual)
            .isGreaterThanOrEqualTo(dotsOnlyExtent)
            .isGreaterThanOrEqualTo(flagOnlyExtent);
    }

    // T5: Grace quaver gets a scaled flag width (smaller than regular quaver)
    @Test
    void testGraceQuaverExtentSmallerThanRegularQuaver() {
        var graceQuaver = element(ElementType.GRACE_QUAVER);
        var regularQuaver = element(ElementType.QUAVER);

        var graceExtent = ElementColumnBuilder.calculateRightExtentSs(graceQuaver, false, true);
        var regularExtent = ElementColumnBuilder.calculateRightExtentSs(regularQuaver, false, true);

        assertThat(graceExtent).isLessThan(regularExtent);
    }

    // T3: Non-flagged types (CROTCHET, MINIM, SEMIBREVE) are unchanged by beamed/upper
    @Test
    void testNonFlaggedTypesUnchanged() {
        for (var type : new ElementType[]{ElementType.CROTCHET, ElementType.MINIM, ElementType.SEMIBREVE}) {
            var n = element(type);
            var noteheadOnly = Engraving.NOTE_HEAD_WIDTH_SS;
            var extentUnbeamed = ElementColumnBuilder.calculateRightExtentSs(n, false, true);
            var extentBeamed = ElementColumnBuilder.calculateRightExtentSs(n, true, true);

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
        var quaver = element(ElementType.QUAVER);
        var upExtent = ElementColumnBuilder.calculateRightExtentSs(quaver, false, true);
        var downExtent = ElementColumnBuilder.calculateRightExtentSs(quaver, false, false);

        assertThat(upExtent).isNotEqualTo(downExtent);
    }

    // T1: Unbeamed quaver right extent > notehead-only extent
    @Test
    void testUnbeamedQuaverExtentExceedsNoteheadOnly() {
        var quaver = element(ElementType.QUAVER);
        var noteheadOnly = Engraving.NOTE_HEAD_WIDTH_SS;
        var extent = ElementColumnBuilder.calculateRightExtentSs(quaver, false, true);

        assertThat(extent).isGreaterThan(noteheadOnly);
    }
}
