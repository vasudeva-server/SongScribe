/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package songscribe.dom;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

class SongTest extends UnitTest {

    private record CoercePersonCase(String input, String expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("coercePersonCases")
    void testCoercePerson(CoercePersonCase testCase) {
        assertThat(Song.coercePerson(testCase.input())).isEqualTo(testCase.expected());
    }

    static Stream<CoercePersonCase> coercePersonCases() {
        return Stream.of(
            new CoercePersonCase("a\n\nb", "a\nb"),
            new CoercePersonCase("a\n \nb", "a\nb"),
            new CoercePersonCase("\na\n", "a"),
            new CoercePersonCase("  ", Song.SRI_CHINMOY)
        );
    }
}
