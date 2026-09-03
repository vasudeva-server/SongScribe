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

import songscribe.dom.PartialDate;

/**
 * Parsing of the reduced-precision ISO 8601 date form ({@code YYYY} / {@code YYYY-MM} /
 * {@code YYYY-MM-DD}) that {@link PartialDate#isoDate} writes, shared by the legacy
 * {@code .mssw} reader and the MusicXML reader.
 */
public final class DateUtils {

    public static final int MAX_MONTH = 12;
    public static final int MAX_DAY = 31;

    public static final Pattern ISO_DATE_PATTERN =
        Pattern.compile("^(\\d{4})(?:-(\\d{2})(?:-(\\d{2}))?)?$");

    private DateUtils() {
    }

    /**
     * Parses a reduced-precision ISO 8601 date string.
     *
     * <p>Accepts the forms {@code YYYY}, {@code YYYY-MM}, and {@code YYYY-MM-DD}
     * (two-digit month/day only). Validates both bounds ({@code 1 <= month <=
     * MAX_MONTH}, {@code 1 <= day <= MAX_DAY}). The inverse of
     * {@link PartialDate#isoDate}.
     *
     * <p>Returns {@code null} when the string is malformed or a month/day
     * component is out of range. Performs no logging and has no side effects —
     * callers decide how to report failure.
     */
    public static @Nullable PartialDate parseIsoDate(String str) {
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

        return PartialDate.of(year, month, day);
    }
}
