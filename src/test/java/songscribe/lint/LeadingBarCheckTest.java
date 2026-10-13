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
 * Which bar line {@link LeadingBarCheck} flags: the one that stands first in the song, whichever
 * of the six bar types it is, and not the song's terminal or a bar line after a note. A bar line
 * the check must flag ends in {@code !} in the notation.
 */
class LeadingBarCheckTest extends UnitTest {

    private static final LintSongs.Reason LEADING_BAR =
        new LintSongs.Reason(Strings.LINT_BAR_LEADING, Strings.REPORT_REASON_LEADING_BAR);

    private record LeadingBarCase(String description, List<String> notations, List<LintSongs.Reason> reasons) {

        String[] notationArray() {
            return notations.toArray(String[]::new);
        }

        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("leadingBarCases")
    void testFlagsExactlyTheBarLineThatStandsFirst(LeadingBarCase testCase) {
        var song = LintSongs.song(testCase.notationArray());

        var violations = new LeadingBarCheck().check(song);

        assertThat(LintSongs.found(song, violations))
            .containsExactlyElementsOf(
                LintSongs.expected(LintMark.Flagged.class, testCase.reasons(), testCase.notationArray()));
    }

    static Stream<LeadingBarCase> leadingBarCases() {
        return Stream.of(
            startsWith("a single barline", "|!"),
            startsWith("a double barline", "||!"),
            startsWith("a final double barline", "|||!"),
            startsWith("a left repeat", "|:!"),
            startsWith("a right repeat", ":|!"),
            startsWith("a left-right repeat", ":|:!"),
            new LeadingBarCase("a song that holds only its terminal gives no finding",
                List.of("|||"),
                List.of()),
            new LeadingBarCase("a bar line after the first note gives no finding",
                List.of("A | A"),
                List.of()),
            new LeadingBarCase("an empty first line leaves the bar line that starts the second line flagged",
                List.of("", "|! A"),
                List.of(LEADING_BAR))
        );
    }

    private static LeadingBarCase startsWith(String barType, String leadingToken) {
        return new LeadingBarCase("a song that starts with " + barType + " is flagged on it",
                                  List.of(leadingToken + " A"),
                                  List.of(LEADING_BAR));
    }
}
