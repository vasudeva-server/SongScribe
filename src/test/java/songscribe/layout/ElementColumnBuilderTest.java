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

package songscribe.layout;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.layout.ElementColumnBuilder;
import songscribe.smufl.Engraving;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;

class ElementColumnBuilderTest extends UnitTest {

    private static final int DOUBLE_DOT_COUNT = 2;

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

    // T6: Dotted quaver right extent is exactly max(notehead+dots, flag) extent
    @Test
    void testDottedQuaverExtentIsMaxOfDotsAndFlag() {
        var dottedQuaver = element(ElementType.QUAVER);
        dottedQuaver.setDotCount(1);

        var noteheadPlusDotsExtent =
            Engraving.NOTE_HEAD_WIDTH_SS + (ElementColumnBuilder.DOT_GAP_SS + ElementColumnBuilder.DOT_WIDTH_SS);
        var flagExtent =
            Engraving.NOTEHEAD_BLACK_STEM_UP_SE.x() + SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.FLAG_8TH_UP);
        var expected = Math.max(noteheadPlusDotsExtent, flagExtent);

        var actual = ElementColumnBuilder.calculateRightExtentSs(dottedQuaver, false, true);

        assertThat(actual).isEqualTo(expected);
    }

    // Two-dot quaver right extent adds two (gap + dot) pairs to the notehead extent
    @Test
    void testDoubleDottedQuaverExtentIncludesTwoDotPairs() {
        var doubleDottedQuaver = element(ElementType.QUAVER);
        doubleDottedQuaver.setDotCount(DOUBLE_DOT_COUNT);

        var dotPairSs = ElementColumnBuilder.DOT_GAP_SS + ElementColumnBuilder.DOT_WIDTH_SS;
        var noteheadPlusTwoDotsExtent = Engraving.NOTE_HEAD_WIDTH_SS + dotPairSs + dotPairSs;
        var flagExtent =
            Engraving.NOTEHEAD_BLACK_STEM_UP_SE.x() + SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.FLAG_8TH_UP);
        var expected = Math.max(noteheadPlusTwoDotsExtent, flagExtent);

        var actual = ElementColumnBuilder.calculateRightExtentSs(doubleDottedQuaver, false, true);

        assertThat(actual).isEqualTo(expected);
    }

    // T5: Grace quaver uses the small notehead + small-stem anchor, regular uses full-size — exact values
    @Test
    void testGraceQuaverExtentSmallerThanRegularQuaver() {
        var graceQuaver = element(ElementType.GRACE_QUAVER);
        var regularQuaver = element(ElementType.QUAVER);

        var flagAdvanceSs = SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.FLAG_8TH_UP);
        var expectedGrace = Math.max(
            ElementColumnBuilder.NOTE_HEAD_SMALL_WIDTH_SS,
            NoteGeometry.STEM_UP_SE_BLACK_SMALL.x() + flagAdvanceSs);
        var expectedRegular = Math.max(
            Engraving.NOTE_HEAD_WIDTH_SS,
            Engraving.NOTEHEAD_BLACK_STEM_UP_SE.x() + flagAdvanceSs);

        var graceExtent = ElementColumnBuilder.calculateRightExtentSs(graceQuaver, false, true);
        var regularExtent = ElementColumnBuilder.calculateRightExtentSs(regularQuaver, false, true);

        assertThat(graceExtent).isEqualTo(expectedGrace);
        assertThat(regularExtent).isEqualTo(expectedRegular);
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

    // T4: Stem-up and stem-down unbeamed quaver each use their own stem anchor + flag — exact values
    @Test
    void testStemUpVsStemDownProduceDifferentExtents() {
        var quaver = element(ElementType.QUAVER);

        var expectedUp = Math.max(
            Engraving.NOTE_HEAD_WIDTH_SS,
            Engraving.NOTEHEAD_BLACK_STEM_UP_SE.x()
                + SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.FLAG_8TH_UP));
        var expectedDown = Math.max(
            Engraving.NOTE_HEAD_WIDTH_SS,
            Engraving.NOTEHEAD_BLACK_STEM_DOWN_NW.x()
                + SMuFLMetadata.getAdvanceWidthOrZero(SMuFLGlyph.FLAG_8TH_DOWN));

        var upExtent = ElementColumnBuilder.calculateRightExtentSs(quaver, false, true);
        var downExtent = ElementColumnBuilder.calculateRightExtentSs(quaver, false, false);

        assertThat(upExtent).isEqualTo(expectedUp);
        assertThat(downExtent).isEqualTo(expectedDown);
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
