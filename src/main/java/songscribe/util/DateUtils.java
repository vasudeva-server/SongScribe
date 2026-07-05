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

package songscribe.util;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Shared reduced-precision ISO 8601 date logic ({@code YYYY} / {@code YYYY-MM} /
 * {@code YYYY-MM-DD}), used by the legacy {@code .mssw} reader/writer and the
 * MusicXML reader/writer so both share one implementation.
 */
public final class DateUtils {

    public static final int MAX_MONTH = 12;
    public static final int MAX_DAY = 31;

    public static final Pattern ISO_DATE_PATTERN =
        Pattern.compile("^(\\d{4})(?:-(\\d{2})(?:-(\\d{2}))?)?$");

    private DateUtils() {
    }

    /**
     * Formats a words date as a reduced-precision ISO 8601 string.
     * Returns {@code ""} when {@code year} is blank.
     * Appends {@code -MM} when {@code month > 0}, and {@code -DD} when both
     * {@code month > 0} and {@code day > 0} (day without month is impossible
     * by the dialog's enable rules).
     */
    public static String toIsoDate(String year, int month, int day) {
        if (year.isEmpty()) {
            return "";
        }

        if (month <= 0) {
            return year;
        }

        if (day <= 0) {
            return String.format("%s-%02d", year, month);
        }

        return String.format("%s-%02d-%02d", year, month, day);
    }

    /**
     * Parses a reduced-precision ISO 8601 date string into year/month/day parts.
     *
     * <p>Accepts the forms {@code YYYY}, {@code YYYY-MM}, and {@code YYYY-MM-DD}
     * (two-digit month/day only). Validates both bounds ({@code 1 <= month <=
     * MAX_MONTH}, {@code 1 <= day <= MAX_DAY}).
     *
     * <p>Returns {@code null} when the string is malformed or a month/day
     * component is out of range. Performs no logging and has no side effects —
     * callers decide how to report failure.
     */
    public static @Nullable DateParts parseIsoDate(String str) {
        var matcher = ISO_DATE_PATTERN.matcher(str);

        if (!matcher.matches()) {
            return null;
        }

        // Group 1 is always present (4 digits); parseInt cannot throw.
        var year = matcher.group(1);
        var monthGroup = matcher.group(2);
        var dayGroup = matcher.group(3);

        var month = 0;
        var day = 0;

        if (monthGroup != null) {
            month = Integer.parseInt(monthGroup);

            if (month < 1 || month > MAX_MONTH) {
                return null;
            }
        }

        if (dayGroup != null) {
            day = Integer.parseInt(dayGroup);

            if (day < 1 || day > MAX_DAY) {
                return null;
            }
        }

        return new DateParts(year, month, day);
    }

    /** The year/month/day parts of a reduced-precision ISO 8601 date. */
    public record DateParts(String year, int month, int day) {
    }
}
