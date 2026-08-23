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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TempoMarkingTest extends UnitTest {

    private static final String DESCRIPTION = "Allegro";
    private static final String NO_DESCRIPTION = "";

    /**
     * One row of {@link TempoMarking#fromFile}'s domain.
     *
     * @param name            the row's display name
     * @param description     what the file states as the description
     * @param hideMetronome   what the file states about the glyph
     * @param expectedMarking the marking the pair states, repaired where it must be
     * @param expectedRepair  whether the pair was the one that draws nothing
     */
    private record FromFileCase(
        String name,
        String description,
        boolean hideMetronome,
        TempoMarking expectedMarking,
        boolean expectedRepair
    ) {}

    static Stream<FromFileCase> fromFileCases() {
        return Stream.of(
            new FromFileCase(
                "shown, with a description",
                DESCRIPTION, false, new TempoMarking.Metronome(DESCRIPTION), false),
            new FromFileCase(
                "shown, with no description",
                NO_DESCRIPTION, false, new TempoMarking.Metronome(NO_DESCRIPTION), false),
            new FromFileCase(
                "hidden, with a description",
                DESCRIPTION, true, new TempoMarking.TextOnly(DESCRIPTION), false),
            new FromFileCase(
                "hidden, with no description, so repaired",
                NO_DESCRIPTION, true, new TempoMarking.Metronome(NO_DESCRIPTION), true),
            new FromFileCase(
                "hidden, with whitespace for a description, so repaired",
                "   ", true, new TempoMarking.Metronome(NO_DESCRIPTION), true),
            new FromFileCase(
                "hidden, with a padded description, which is stripped",
                "  " + DESCRIPTION + "  ", true, new TempoMarking.TextOnly(DESCRIPTION), false),
            new FromFileCase(
                "shown, with a padded description, which is stripped",
                "  " + DESCRIPTION + "  ", false, new TempoMarking.Metronome(DESCRIPTION), false)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fromFileCases")
    void testFromFileStatesTheMarkingAndWhetherItRepairedThePair(FromFileCase testCase) {
        var read = TempoMarking.fromFile(testCase.description(), testCase.hideMetronome());

        assertThat(read.marking()).isEqualTo(testCase.expectedMarking());
        assertThat(read.repaired()).isEqualTo(testCase.expectedRepair());
    }

    @Test
    void testFromFileCasesReachEveryKindOfMarking() {
        assertThat(fromFileCases().<Class<?>>map(testCase -> testCase.expectedMarking().getClass()))
            .contains(TempoMarking.class.getPermittedSubclasses());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n  "})
    void testTextOnlyRefusesBlankText(String description) {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> new TempoMarking.TextOnly(description));
    }
}
