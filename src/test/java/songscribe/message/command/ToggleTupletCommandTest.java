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
package songscribe.message.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.ui.action.TupletAction;
import songscribe.ui.action.TupletAction.Tuplet;

class ToggleTupletCommandTest extends UnitTest {

    static Stream<Arguments> tupletSizes() {
        return Stream.of(
            Arguments.of(Tuplet.DUPLET, Tuplet.DUPLET.getSize()),
            Arguments.of(Tuplet.TRIPLET, Tuplet.TRIPLET.getSize()),
            Arguments.of(Tuplet.QUADRUPLET, Tuplet.QUADRUPLET.getSize()),
            Arguments.of(Tuplet.QUINTUPLET, Tuplet.QUINTUPLET.getSize()),
            Arguments.of(Tuplet.SEXTUPLET, Tuplet.SEXTUPLET.getSize()),
            Arguments.of(Tuplet.SEPTUPLET, Tuplet.SEPTUPLET.getSize()),
            Arguments.of(Tuplet.REMOVE, Tuplet.REMOVE.getSize())
        );
    }

    @ParameterizedTest(name = "getTupletSize() returns {1} for Tuplet.{0}")
    @MethodSource("tupletSizes")
    void testGetTupletSizeReturnsActionTupletSize(Tuplet tuplet, int expectedSize) {
        var action = mock(TupletAction.class);
        when(action.getTuplet()).thenReturn(tuplet);

        var command = new ToggleTupletCommand(action);

        assertThat(command.getTupletSize()).isEqualTo(expectedSize);
    }
}
