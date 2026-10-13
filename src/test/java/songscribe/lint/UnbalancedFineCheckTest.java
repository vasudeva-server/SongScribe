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
 * Which annotated elements {@link UnbalancedFineCheck} flags, and with which reason, for the
 * Fines and the references its class description defines. In the notation, {@code @text} is an
 * annotation with underscores for spaces and each {@code !} is a violation expected on that
 * element.
 */
class UnbalancedFineCheckTest extends UnitTest {

    private static final LintSongs.Reason NOT_ON_BAR =
        new LintSongs.Reason(Strings.LINT_FINE_UNBALANCED, Strings.LINT_REASON_FINE_NOT_ON_BAR);

    private static final LintSongs.Reason ON_LAST_BAR =
        new LintSongs.Reason(Strings.LINT_FINE_UNBALANCED, Strings.LINT_REASON_FINE_ON_LAST_BAR);

    private static final LintSongs.Reason NOT_REFERENCED =
        new LintSongs.Reason(Strings.LINT_FINE_UNBALANCED, Strings.LINT_REASON_FINE_NOT_REFERENCED);

    private static final LintSongs.Reason REFERENCE_WITHOUT_FINE =
        new LintSongs.Reason(Strings.LINT_FINE_MISSING, Strings.LINT_REASON_FINE_REFERENCE_WITHOUT_FINE);

    private record FineCase(String description, List<String> notations, List<LintSongs.Reason> reasons) {

        FineCase(String description, String notation, List<LintSongs.Reason> reasons) {
            this(description, List.of(notation), reasons);
        }

        String[] notationArray() {
            return notations.toArray(String[]::new);
        }

        @Override
        public String toString() {
            return description;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fineCases")
    void testFlagsExactlyTheFinesAndReferencesTheClassDescriptionNames(FineCase testCase) {
        var song = LintSongs.song(testCase.notationArray());

        var violations = new UnbalancedFineCheck().check(song);

        assertThat(LintSongs.found(song, violations))
            .containsExactlyElementsOf(LintSongs.expected(
                LintMark.FlaggedAnnotation.class, testCase.reasons(), testCase.notationArray()));
    }

    static Stream<FineCase> fineCases() {
        return Stream.of(
            new FineCase("a Fine on a note is flagged",
                "A@Fine! | A@D.C._al_Fine |",
                List.of(NOT_ON_BAR)),
            new FineCase("a Fine on the last barline of the song is flagged",
                "A | A |@Fine!!",
                List.of(ON_LAST_BAR, NOT_REFERENCED)),
            new FineCase("a Fine on the last bar before a blank line is flagged",
                List.of("A | A |@Fine!!", ""),
                List.of(ON_LAST_BAR, NOT_REFERENCED)),
            new FineCase("a Fine with no later reference is flagged",
                "A |@Fine! A |",
                List.of(NOT_REFERENCED)),
            new FineCase("a Fine followed by D.C. al Fine gives no finding",
                "A |@Fine A |@D.C._al_Fine A |",
                List.of()),
            new FineCase("one reference after two Fines answers both",
                "A |@Fine A |@Fine A |@D.C._al_Fine A |",
                List.of()),
            new FineCase("FINE followed by D.C. al fine gives no finding",
                "A |@FINE A |@D.C._al_fine A |",
                List.of()),
            new FineCase("a Fine on a repeat bar gives no finding",
                "|: A :|@Fine A |@D.C._al_Fine A |",
                List.of()),
            new FineCase("a Fine with spaces around it is a Fine",
                "A |@_Fine_ A |@D.C._al_Fine A |",
                List.of()),
            new FineCase("D.C. al Fine with no earlier Fine is flagged",
                "A |@D.C._al_Fine! A |",
                List.of(REFERENCE_WITHOUT_FINE)),
            new FineCase("a reference before the first Fine is flagged, and the Fine after it is unreferenced",
                "A |@D.C._al_Fine! A |@Fine! A |",
                List.of(REFERENCE_WITHOUT_FINE, NOT_REFERENCED)),
            new FineCase("al Fine. is a reference",
                "A |@al_Fine.! A |",
                List.of(REFERENCE_WITHOUT_FINE)),
            new FineCase("D.C. al fine is a reference",
                "A |@D.C._al_fine! A |",
                List.of(REFERENCE_WITHOUT_FINE)),
            new FineCase("Finest is not a reference",
                "A |@Finest A |",
                List.of())
        );
    }
}
