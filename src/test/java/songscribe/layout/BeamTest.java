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

import org.junit.jupiter.api.Test;
import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;

/**
 * Tests for {@link Beam#getSpanWidthSs}.
 *
 * <p>Row 43 — getSpanWidthSs clamps to MIN_SPAN_WIDTH_SS when end−anchor would be smaller.
 */
class BeamTest extends UnitTest {

    // The minimum span width the production formula enforces (Math.max floor).
    private static final double MIN_SPAN_WIDTH_SS = 1.0;

    // An anchor X far enough from the end that the difference exceeds the clamp floor.
    private static final double ANCHOR_X_SS = 5.0;

    // end−anchor = 0.5, below the clamp floor.
    private static final double END_X_BELOW_FLOOR_SS = ANCHOR_X_SS + 0.5;

    // end−anchor = 1.0, exactly at the clamp floor.
    private static final double END_X_AT_FLOOR_SS = ANCHOR_X_SS + MIN_SPAN_WIDTH_SS;

    // end−anchor = 3.0, above the clamp floor.
    private static final double END_X_ABOVE_FLOOR_SS = ANCHOR_X_SS + 3.0;

    private static final double DELTA = 1e-9;

    private static Beam createBeam() {
        var anchor = ElementType.QUAVER.newInstance();
        var end = ElementType.QUAVER.newInstance();
        return new Beam(anchor, end);
    }

    @Test
    void testGetSpanWidthSsReturnsClamFloorWhenDifferenceIsBelowFloor() {
        var beam = createBeam();

        assertThat(beam.getSpanWidthSs(ANCHOR_X_SS, END_X_BELOW_FLOOR_SS))
            .isCloseTo(MIN_SPAN_WIDTH_SS, within(DELTA));
    }

    @Test
    void testGetSpanWidthSsReturnsClamFloorWhenDifferenceEqualsFloor() {
        var beam = createBeam();

        assertThat(beam.getSpanWidthSs(ANCHOR_X_SS, END_X_AT_FLOOR_SS))
            .isCloseTo(MIN_SPAN_WIDTH_SS, within(DELTA));
    }

    @Test
    void testGetSpanWidthSsReturnsDifferenceWhenAboveFloor() {
        var beam = createBeam();
        var expectedSpan = END_X_ABOVE_FLOOR_SS - ANCHOR_X_SS;

        assertThat(beam.getSpanWidthSs(ANCHOR_X_SS, END_X_ABOVE_FLOOR_SS))
            .isCloseTo(expectedSpan, within(DELTA));
    }
}
