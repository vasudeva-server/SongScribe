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

package songscribe.lint;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which bars {@link BalancedRepeatsCheck} flags, and with which reason, for the cases its class
 * description works through. A bar the check must flag ends in {@code !} in the notation.
 */
class BalancedRepeatsCheckTest extends UnitTest {

    private record RepeatCase(String notation, List<String> reasonKeys) {

        @Override
        public String toString() {
            return notation;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("repeatCases")
    void testFlagsExactlyTheBarsTheClassDescriptionNames(RepeatCase testCase) {
        var song = LintSongs.song(testCase.notation());

        var violations = new BalancedRepeatsCheck().check(song);

        assertThat(LintSongs.found(song, violations))
            .containsExactlyElementsOf(LintSongs.expected(testCase.reasonKeys(), testCase.notation()));
    }

    static Stream<RepeatCase> repeatCases() {
        return Stream.of(
            new RepeatCase("|: A :|", List.of()),
            new RepeatCase("A :|", List.of()),
            new RepeatCase("A || A :|!", List.of(Strings.LINT_REASON_REPEAT_NOT_OPENED)),
            new RepeatCase("A :| A :|!", List.of(Strings.LINT_REASON_REPEAT_NOT_OPENED)),
            new RepeatCase("|:! A", List.of(Strings.LINT_REASON_REPEAT_NOT_CLOSED)),
            new RepeatCase("|: |:! :| :|", List.of(Strings.LINT_REASON_REPEAT_NESTED)),
            new RepeatCase("|: A :|: A :|", List.of()),
            new RepeatCase("|: A :|:! A", List.of(Strings.LINT_REASON_REPEAT_NOT_CLOSED))
        );
    }
}
