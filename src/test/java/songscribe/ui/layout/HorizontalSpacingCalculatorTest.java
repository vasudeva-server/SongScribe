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
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.smufl.Engraving;

class HorizontalSpacingCalculatorTest extends UnitTest {

    private static final double TOLERANCE = 0.001;

    // T5: First note X position after calculatePositions() equals calculateFirstElementXSs() (regression)
    @Test
    void testFirstNoteXMatchesCalculateFirstElementXSs() {
        var calculator = new HorizontalSpacingCalculator();
        var line = new Line(); // C major, 0 accidentals
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

        double expectedXSs = LayoutStylesheet.calculateFirstElementXSs(line.getKeyAccidentalCount());
        assertThat(column.getXSs()).isCloseTo(expectedXSs, within(TOLERANCE));
    }
}
