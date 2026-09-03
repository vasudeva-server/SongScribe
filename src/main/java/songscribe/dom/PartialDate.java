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
 * <p>A song's composition date and words date are entered by hand and are often
 * incomplete — a year alone is the common case. The four members are the four legal
 * precisions, and a day without a month is unrepresentable: there is no member for it.
 *
 * <p>This is a sealed interface rather than a record of three nullable parts because the
 * members <em>are</em> the legal states. A switch over them is exhaustive, so a renderer
 * that handles each member has handled every date, and no consumer has to re-derive from
 * an empty year and a zero month which of the four cases it is looking at.
 *
 * <p>Every member but {@link EmptyDate} carries a non-blank year. {@code month} is
 * {@code 1} to {@value DateUtils#MAX_MONTH} and {@code day} is {@code 1} to
 * {@value DateUtils#MAX_DAY}; the record constructors reject anything else. The year is
 * stored as typed and is not validated beyond being non-blank, because the file formats
 * and the dialog carry it as text.
 *
 * <p>Raw parts — a year that may be blank, a month and a day that may be 0 — enter through
 * {@link #of}, which is the one place the raw encoding is read.
 */
public sealed interface PartialDate {

    /**
     * The year, or {@code ""} for {@link EmptyDate}.
     *
     * @return the year as typed
     */
    String year();

    /**
     * This date in reduced-precision ISO 8601 form: {@code YYYY}, {@code YYYY-MM} or
     * {@code YYYY-MM-DD}, and {@code ""} for {@link EmptyDate}.
     *
     * <p>{@link DateUtils#parseIsoDate} is the inverse: parsing this method's result
     * yields a date equal to this one.
     *
     * @return the ISO form
     */
    String isoDate();

    /**
     * Builds the date the raw parts describe.
     *
     * <p>A blank year is no date at all, whatever the month and day say. A day given
     * without a month collapses to the year alone, since a day is meaningless without
     * the month it falls in.
     *
     * @param year the year, blank for none; surrounding whitespace is dropped
     * @param month the month, {@code 1} to {@value DateUtils#MAX_MONTH}, or {@code 0} for none
     * @param day the day, {@code 1} to {@value DateUtils#MAX_DAY}, or {@code 0} for none
     * @return the member matching the precision the parts describe
     * @throws IllegalArgumentException if {@code month} or {@code day} is neither 0 nor in range
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

    /** No date. */
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

    /**
     * A year alone.
     *
     * @param year the year, non-blank
     */
    record YearOnly(String year) implements PartialDate {

        public YearOnly {
            requireYear(year);
        }

        @Override
        public String isoDate() {
            return year;
        }
    }

    /**
     * A year and a month.
     *
     * @param year the year, non-blank
     * @param month the month, {@code 1} to {@value DateUtils#MAX_MONTH}
     */
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

    /**
     * A full date.
     *
     * @param year the year, non-blank
     * @param month the month, {@code 1} to {@value DateUtils#MAX_MONTH}
     * @param day the day, {@code 1} to {@value DateUtils#MAX_DAY}
     */
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
