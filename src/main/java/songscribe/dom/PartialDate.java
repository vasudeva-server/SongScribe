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

import songscribe.util.DateUtils;

/**
 * A date known to some precision: nothing, a year, a year and month, or a full day.
 *
 * <p>The year is stored as typed and is not validated beyond being non-blank. Raw parts enter
 * through {@link #of}, the one place the raw encoding is read.
 */
public sealed interface PartialDate {

    /**
     * {@code ""} for {@link EmptyDate}.
     */
    String year();

    /**
     * {@link DateUtils#parseIsoDate} is the inverse.
     */
    String isoDate();

    /**
     * A blank year is no date at all, whatever the month and day say. A day without a month
     * collapses to the year alone.
     */
    static PartialDate of(String year, int month, int day) {
        var trimmedYear = year.trim();

        if (trimmedYear.isEmpty()) {
            return EmptyDate.INSTANCE;
        }

        if (month == 0) {
            return new YearOnly(trimmedYear);
        }

        if (day == 0) {
            return new YearMonth(trimmedYear, month);
        }

        return new YearMonthDay(trimmedYear, month, day);
    }

    private static void requireYear(String year) {
        if (year.isBlank()) {
            throw new IllegalArgumentException("A dated member needs a year");
        }
    }

    private static void requireMonth(int month) {
        if (month < 1 || month > DateUtils.MAX_MONTH) {
            throw new IllegalArgumentException("Month out of range: " + month);
        }
    }

    private static void requireDay(int day) {
        if (day < 1 || day > DateUtils.MAX_DAY) {
            throw new IllegalArgumentException("Day out of range: " + day);
        }
    }

    enum EmptyDate implements PartialDate {
        INSTANCE;

        @Override
        public String year() {
            return "";
        }

        @Override
        public String isoDate() {
            return "";
        }
    }

    record YearOnly(String year) implements PartialDate {

        public YearOnly {
            requireYear(year);
        }

        @Override
        public String isoDate() {
            return year;
        }
    }

    record YearMonth(String year, int month) implements PartialDate {

        public YearMonth {
            requireYear(year);
            requireMonth(month);
        }

        @Override
        public String isoDate() {
            return String.format("%s-%02d", year, month);
        }
    }

    record YearMonthDay(String year, int month, int day) implements PartialDate {

        public YearMonthDay {
            requireYear(year);
            requireMonth(month);
            requireDay(day);
        }

        @Override
        public String isoDate() {
            return String.format("%s-%02d-%02d", year, month, day);
        }
    }
}
