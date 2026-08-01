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

package songscribe.io.musicxml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.UnitTest;
import songscribe.dom.Duration;

/**
 * Verifies {@link BeatUnitMapping#forDuration(Duration)} and
 * {@link BeatUnitMapping#forBeatUnit(String, int)} round-trip every
 * {@link Duration} value through its MusicXML beat-unit token and dot count.
 */
class BeatUnitMappingTest extends UnitTest {

    /**
     * Every {@link Duration} must map forward to a beat-unit entry and back to the
     * same value. {@code @EnumSource} covers all values automatically, so a
     * newly-added {@link Duration} with no mapping fails here.
     */
    @ParameterizedTest
    @EnumSource(Duration.class)
    void testEveryDurationRoundTrips(Duration duration) {
        var entry = BeatUnitMapping.forDuration(duration);

        assertThat(entry)
            .as("%s must have a forward-map entry", duration)
            .isNotNull();

        assertThat(BeatUnitMapping.forBeatUnit(entry.token(), entry.dotCount()))
            .as("(%s, %d) must map back to %s", entry.token(), entry.dotCount(), duration)
            .isEqualTo(duration);
    }

    @Test
    void testForDurationEntriesHaveDistinctTokenDotPairs() {
        // Guards against two Duration values sharing a (token, dotCount) pair,
        // which would make the reverse map lossy.
        var values = Duration.values();

        for (var i = 0; i < values.length; i++) {
            for (var j = i + 1; j < values.length; j++) {
                var first = BeatUnitMapping.forDuration(values[i]);
                var second = BeatUnitMapping.forDuration(values[j]);

                assertThat(first).isNotNull();
                assertThat(second).isNotNull();
                assertThat(first)
                    .as("%s and %s must not share a (token, dotCount) pair", values[i], values[j])
                    .isNotEqualTo(second);
            }
        }
    }

    @Test
    void testForBeatUnitReturnsNullForUnknownToken() {
        assertThat(BeatUnitMapping.forBeatUnit("bogus", 0))
            .as("an unrecognised token must map to null")
            .isNull();
    }

    @Test
    void testForBeatUnitReturnsNullForUnsupportedDotCount() {
        final var unsupportedDotCount = 2;

        assertThat(BeatUnitMapping.forBeatUnit(NoteTypeMapping.TYPE_QUARTER, unsupportedDotCount))
            .as("no Duration has more than one dot")
            .isNull();
    }
}
