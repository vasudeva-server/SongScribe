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
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.layout.ElementColumn;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.smufl.Engraving;

class HorizontalSpacingCalculatorTest extends UnitTest {

    private static final double TOLERANCE = 0.001;
    private static final int THREE_KEY_ACCIDENTALS = 3;
    private static final int SEVEN_KEY_ACCIDENTALS = 7;
    private static final double PREV_COLUMN_X_SS = 10.0;

    // Row 23 (strengthened): calculatePositions places first column at clef+keyAccidentals+firstNoteOffset
    // (pinned concrete value — not self-referential against calculateFirstElementXSs)
    @Test
    void testCalculatePositionsFirstColumnXSsEqualsClefPlusKeyAccidentalsPlusOffset() {
        var calculator = new HorizontalSpacingCalculator();
        var line = detachedLine(); // C major, 0 accidentals
        var element = ElementType.CROTCHET.newInstance();
        var column = new ElementColumn(
            element,
            Collections.emptyList(),
            0.0,
            Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false
        );
        var columns = List.of(column);

        calculator.calculatePositions(columns, line);

        // Concrete expected value: clef width + 0*keyAccidentalWidth + firstNoteOffset
        var expectedXSs = Engraving.G_CLEF_WIDTH_SS
            + line.getKeyAccidentalCount() * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS
            + HorizontalSpacingCalculator.FIRST_NOTE_OFFSET_SS;
        assertThat(column.getXSs()).isCloseTo(expectedXSs, within(TOLERANCE));
    }

    // Row 24: calculateHeaderRightEdgeSs(n) = G_CLEF_WIDTH_SS + n * KEY_ACCIDENTAL_WIDTH_SS
    @Test
    void testCalculateHeaderRightEdgeSsWithZeroAccidentals() {
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(0))
            .isEqualTo(Engraving.G_CLEF_WIDTH_SS);
    }

    @Test
    void testCalculateHeaderRightEdgeSsWithThreeAccidentals() {
        var expected = Engraving.G_CLEF_WIDTH_SS + THREE_KEY_ACCIDENTALS * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS;
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(THREE_KEY_ACCIDENTALS)).isEqualTo(expected);
    }

    @Test
    void testCalculateHeaderRightEdgeSsWithSevenAccidentals() {
        var expected = Engraving.G_CLEF_WIDTH_SS + SEVEN_KEY_ACCIDENTALS * HorizontalSpacingCalculator.KEY_ACCIDENTAL_WIDTH_SS;
        assertThat(HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(SEVEN_KEY_ACCIDENTALS)).isEqualTo(expected);
    }

    // Row 25: calculateNextColumnXSs for two plain columns (no accidentals, no lyrics)
    // = prevXSs + prevRightExtent + DEFAULT_COLUMN_GAP_SS
    @Test
    void testCalculateNextColumnXSsTwoPlainColumns() {
        var prevElement = ElementType.CROTCHET.newInstance();
        var currElement = ElementType.CROTCHET.newInstance();
        var prevColumn = new ElementColumn(
            prevElement,
            Collections.emptyList(),
            0.0, Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false
        );
        var currColumn = new ElementColumn(
            currElement,
            Collections.emptyList(),
            0.0, Engraving.NOTE_HEAD_WIDTH_SS,
            0.0, 0.0, null, 0.0, false
        );
        prevColumn.setXSs(PREV_COLUMN_X_SS);

        var expected = PREV_COLUMN_X_SS + Engraving.NOTE_HEAD_WIDTH_SS + HorizontalSpacingCalculator.DEFAULT_COLUMN_GAP_SS;
        assertThat(HorizontalSpacingCalculator.calculateNextColumnXSs(prevColumn, currColumn))
            .isEqualTo(expected);
    }
}
