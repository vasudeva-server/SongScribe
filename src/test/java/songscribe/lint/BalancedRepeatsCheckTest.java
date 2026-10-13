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
 * The findings {@link BalancedRepeatsCheck} makes from the {@code RepeatSections} model, one row
 * for each kind: a closer with no open section, an opener never closed, and a nested opener. The
 * repeat rules themselves are stated by {@code RepeatSectionsTest}. A bar the check must flag
 * ends in {@code !} in the notation.
 */
class BalancedRepeatsCheckTest extends UnitTest {

    private static final LintSongs.Reason NOT_OPENED =
        new LintSongs.Reason(Strings.LINT_REPEATS_UNBALANCED, Strings.LINT_REASON_REPEAT_NOT_OPENED);

    private static final LintSongs.Reason NOT_CLOSED =
        new LintSongs.Reason(Strings.LINT_REPEATS_UNBALANCED, Strings.LINT_REASON_REPEAT_NOT_CLOSED);

    private static final LintSongs.Reason NESTED =
        new LintSongs.Reason(Strings.LINT_REPEATS_UNBALANCED, Strings.LINT_REASON_REPEAT_NESTED);

    private record RepeatCase(String description, String notation, List<LintSongs.Reason> reasons) {

        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("repeatCases")
    void testFlagsExactlyTheBarsOfEachKindOfFinding(RepeatCase testCase) {
        var song = LintSongs.song(testCase.notation());

        var violations = new BalancedRepeatsCheck().check(song);

        assertThat(LintSongs.found(song, violations))
            .containsExactlyElementsOf(
                LintSongs.expected(LintMark.Flagged.class, testCase.reasons(), testCase.notation()));
    }

    static Stream<RepeatCase> repeatCases() {
        return Stream.of(
            new RepeatCase("a closer after its section is closed is flagged as not opened",
                "|: A :| A :|!",
                List.of(NOT_OPENED)),
            new RepeatCase("an opener with no closer after it is flagged as not closed",
                "|:! A",
                List.of(NOT_CLOSED)),
            new RepeatCase("an opener inside an open section is flagged as nested",
                "|: |:! :| :|",
                List.of(NESTED))
        );
    }
}
