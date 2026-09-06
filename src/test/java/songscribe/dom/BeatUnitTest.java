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

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BeatUnit}'s two promises: {@link BeatUnit#ticks()} is the value's length plus half again
 * when dotted, exact for every unit; and the constants are declared longest first, which is the
 * order the tempo and beat-change dialogs show them in.
 */
class BeatUnitTest extends UnitTest {

    @ParameterizedTest
    @EnumSource(BeatUnit.class)
    void testTicksIsTheValuesLengthPlusHalfAgainWhenDotted(BeatUnit unit) {
        var valueTicks = unit.noteValue().ticks();

        if (unit.dotted()) {
            assertThat(unit.ticks() * 2)
                .as("%s is exactly one and a half times its value", unit)
                .isEqualTo(valueTicks * 3);
        } else {
            assertThat(unit.ticks())
                .as("%s is its value's length", unit)
                .isEqualTo(valueTicks);
        }
    }

    @Test
    void testUnitsAreDeclaredLongestFirst() {
        var ticksInDeclarationOrder = Stream.of(BeatUnit.values()).map(BeatUnit::ticks).toList();

        assertThat(ticksInDeclarationOrder)
            .as("each unit is shorter than the one declared before it")
            .isSortedAccordingTo((first, second) -> Integer.compare(second, first))
            .doesNotHaveDuplicates();
    }
}
