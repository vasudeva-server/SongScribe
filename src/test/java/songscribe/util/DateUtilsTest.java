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

package songscribe.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import songscribe.UnitTest;

class DateUtilsTest extends UnitTest {

    @Nested
    class ToIsoDate {

        @Test
        void testFullDateFormatsAsYearMonthDay() {
            assertThat(DateUtils.toIsoDate("1984", 6, 27)).isEqualTo("1984-06-27");
        }

        @Test
        void testYearAndMonthOnlyFormatsAsYearMonth() {
            assertThat(DateUtils.toIsoDate("1984", 6, 0)).isEqualTo("1984-06");
        }

        @Test
        void testYearOnlyFormatsAsBareYear() {
            assertThat(DateUtils.toIsoDate("1984", 0, 0)).isEqualTo("1984");
        }

        @Test
        void testAbsentYearFormatsAsEmptyString() {
            assertThat(DateUtils.toIsoDate("", 0, 0)).isEmpty();
        }
    }

    @Nested
    class ParseIsoDate {

        @Test
        void testYearOnlyParsesWithZeroMonthAndDay() {
            var parsed = DateUtils.parseIsoDate("1984");

            assertThat(parsed).isNotNull();

            if (parsed == null) {
                return;
            }

            assertThat(parsed.year()).isEqualTo("1984");
            assertThat(parsed.month()).isZero();
            assertThat(parsed.day()).isZero();
        }

        @Test
        void testYearAndMonthParsesWithZeroDay() {
            var parsed = DateUtils.parseIsoDate("1984-06");

            assertThat(parsed).isNotNull();

            if (parsed == null) {
                return;
            }

            assertThat(parsed.year()).isEqualTo("1984");
            assertThat(parsed.month()).isEqualTo(6);
            assertThat(parsed.day()).isZero();
        }

        @Test
        void testYearMonthAndDayParsesAllParts() {
            var parsed = DateUtils.parseIsoDate("1984-06-27");

            assertThat(parsed).isNotNull();

            if (parsed == null) {
                return;
            }

            assertThat(parsed.year()).isEqualTo("1984");
            assertThat(parsed.month()).isEqualTo(6);
            assertThat(parsed.day()).isEqualTo(27);
        }

        // Mirrors SongIOTest.LyricsDateIO's nine malformed-lyricsDate cases —
        // both must reject exactly the same set, since SongIO.parseLyricsDate
        // delegates to this method.
        @ParameterizedTest
        @ValueSource(strings = {
            "1984-13",       // month 13 is out of range
            "1984--6",       // double-dash is not a valid ISO separator
            "1984-6-",       // trailing hyphen is not a valid ISO date
            "1984-06-27-01", // four-part value exceeds the pattern
            "1984-XX",       // non-numeric month component
            "1984-06-32",    // day 32 is out of range
            "1984-00",       // month 0 is below range (lower-bound guard)
            "1984-06-00",    // day 0 is below range (lower-bound guard)
            "1984-6",        // single-digit month is not zero-padded
        })
        void testMalformedOrOutOfRangeInputReturnsNull(String rawValue) {
            assertThat(DateUtils.parseIsoDate(rawValue)).isNull();
        }
    }
}
