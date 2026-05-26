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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

/**
 * Tests for {@link BeatChange#fromLegacyName(String)}.
 *
 * Row 28 — fromLegacyName: canonical names + aliases → correct duration/beat pairs
 */
class BeatChangeTest extends UnitTest {

    // -------------------------------------------------------------------------
    // Row 28 — fromLegacyName: 5 canonical names + aliases → correct record fields
    // -------------------------------------------------------------------------

    static Stream<Arguments> legacyNames() {
        return Stream.of(
            // canonical name, alias, expected duration (from), expected beat (to)
            Arguments.of("QUAVER_EQUALS_QUAVER",             "QUAVEREQUALSQUAVER",           Duration.QUAVER,         Duration.QUAVER),
            Arguments.of("DOTTED_CROCHET_EQUALS_MINIM",      "DOTTEDCROCHETEQUALSMINIM",     Duration.CROTCHET_DOTTED, Duration.MINIM),
            Arguments.of("MINIM_EQUALS_DOTTED_CROCHET",      "MINIMEQUALSDOTTEDCROCHET",     Duration.MINIM,          Duration.CROTCHET_DOTTED),
            Arguments.of("CROTCHET_EQUALS_DOTTED_CROCHET",   "CROTCHETQUALSDOTTEDCROCHET",   Duration.CROTCHET,       Duration.CROTCHET_DOTTED),
            Arguments.of("DOTTED_CROCHET_EQUALS_CROCHET",    "DOTTEDCROCHETQUALSCROCHET",    Duration.CROTCHET_DOTTED, Duration.CROTCHET)
        );
    }

    @ParameterizedTest(name = "canonical \"{0}\" → duration={2}, beat={3}")
    @MethodSource("legacyNames")
    void testFromLegacyNameCanonicalReturnCorrectPair(
        String canonicalName,
        String alias,
        Duration expectedDuration,
        Duration expectedBeat
    ) {
        var result = BeatChange.fromLegacyName(canonicalName);

        assertThat(result.duration()).isEqualTo(expectedDuration);
        assertThat(result.beat()).isEqualTo(expectedBeat);
    }

    @ParameterizedTest(name = "alias \"{1}\" → duration={2}, beat={3}")
    @MethodSource("legacyNames")
    void testFromLegacyNameAliasReturnCorrectPair(
        String canonicalName,
        String alias,
        Duration expectedDuration,
        Duration expectedBeat
    ) {
        var result = BeatChange.fromLegacyName(alias);

        assertThat(result.duration()).isEqualTo(expectedDuration);
        assertThat(result.beat()).isEqualTo(expectedBeat);
    }

    @Test
    void testFromLegacyNameThrowsForUnknownName() {
        assertThatThrownBy(() -> BeatChange.fromLegacyName("UNKNOWN_BEAT_CHANGE"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
