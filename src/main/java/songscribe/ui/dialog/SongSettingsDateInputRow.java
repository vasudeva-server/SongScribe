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
package songscribe.ui.dialog;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;

import songscribe.Strings;
import songscribe.ui.binding.Bindings;
import songscribe.ui.binding.Controls;
import songscribe.ui.binding.Property;
import songscribe.ui.binding.Timing;
import songscribe.ui.binding.Widgets;
import songscribe.ui.component.NumericTextField;

import static songscribe.ui.binding.ObservableValue.computed;

/**
 * A self-contained date-input row (year, month, day) that can be embedded in any
 * dialog panel. Owns its three widgets, views each of them as a property, and
 * exposes pure predicate methods so callers can unit-test the enable/reset logic
 * without driving Swing.
 *
 * <p>The row is not a {@link BaseDialog.Tab} and owns no {@code Bindings} of its
 * own: it takes one from the tab that builds it, so the dialog still owns every
 * edge this row declares and disposes them with the rest.
 *
 * <p>Both combos are indexed rather than valued — index {@value #NONE_INDEX} is the
 * empty entry meaning "not chosen" — so the row views them with
 * {@link Controls#itemIndex} and speaks positions throughout: the properties, the
 * getters, the setter and {@link #dayEnabled} all use the index {@code SongMetadata}
 * stores. The item lists exist only to build the combos.
 */
final class SongSettingsDateInputRow {

    private static final int YEAR_MIN = 1942;
    private static final int YEAR_MAX = 2007;
    private static final int MAX_YEAR_CHARS = 4;
    private static final int YEAR_FIELD_COLUMNS = 4;
    private static final int DAYS_IN_MONTH_MAX = 31;

    /** The empty leading entry of both combos: no month, no day. */
    private static final int NONE_INDEX = 0;

    private final List<String> monthNames = List.of(
        "",
        Strings.get(Strings.MONTH_JANUARY),
        Strings.get(Strings.MONTH_FEBRUARY),
        Strings.get(Strings.MONTH_MARCH),
        Strings.get(Strings.MONTH_APRIL),
        Strings.get(Strings.MONTH_MAY),
        Strings.get(Strings.MONTH_JUNE),
        Strings.get(Strings.MONTH_JULY),
        Strings.get(Strings.MONTH_AUGUST),
        Strings.get(Strings.MONTH_SEPTEMBER),
        Strings.get(Strings.MONTH_OCTOBER),
        Strings.get(Strings.MONTH_NOVEMBER),
        Strings.get(Strings.MONTH_DECEMBER)
    );
    private final List<String> dayNames = buildDayNames();

    private final NumericTextField yearField =
        new NumericTextField(YEAR_FIELD_COLUMNS, YEAR_MIN, YEAR_MAX, true, MAX_YEAR_CHARS);
    private final JComboBox<String> monthCombo = uneditableCombo(monthNames);
    private final JComboBox<String> dayCombo = uneditableCombo(dayNames);

    private final Property<String> year = Controls.text(yearField, Timing.ON_COMMIT);
    private final Property<Integer> month = Controls.itemIndex(monthCombo);
    private final Property<Integer> day = Controls.itemIndex(dayCombo);

    /**
     * Builds the row and declares its edges on {@code bindings}.
     *
     * <p>{@code onChange} is the row's only outward signal, and it runs whenever one of
     * the three properties notifies — including when the year becomes invalid. Losing a
     * date is as much a change to what this row contributes as gaining one, and a reader
     * that showed the old date until some unrelated field fired would be showing a date
     * the row no longer holds. The two enabled states are not reported through
     * {@code onChange}; they are bindings, and the framework keeps them current on its
     * own.
     *
     * <p>Clearing the year clears the month and day with it, and each of those writes
     * notifies in turn, so one such gesture runs {@code onChange} more than once. Every
     * run reads the row's current state, so the last one is what stands; a reader that
     * cannot afford the repeats coalesces them itself.
     *
     * <p>{@link #setValues} writes the three properties like any other writer, so
     * seeding the row from stored data runs {@code onChange} once per seeded field.
     *
     * @param bindings the owning dialog's bindings, which this row registers its
     *     edges and effects on and which disposes them
     * @param onChange run after any edit to this row, valid date or not
     */
    SongSettingsDateInputRow(Bindings bindings, Runnable onChange) {
        // The month combo is enabled exactly when the year is valid; the day combo
        // additionally needs a month. Both read the year through the property, never
        // off the field, so the derivation records the dependency.
        bindings.bind(
            Widgets.enabled(monthCombo),
            computed(() -> yearField.isValidValue(year.get()))
        );
        bindings.bind(
            Widgets.enabled(dayCombo),
            computed(() -> dayEnabled(yearField.isValidValue(year.get()), month.get()))
        );

        bindings.onChange(year, () -> {
            if (!yearField.isValidValue(year.get())) {
                // A year that names no date leaves no month or day standing.
                month.set(NONE_INDEX);
                day.set(NONE_INDEX);
            }

            onChange.run();
        });

        bindings.onChange(month, () -> {
            if (month.get() == NONE_INDEX) {
                day.set(NONE_INDEX);
            }

            onChange.run();
        });

        bindings.onChange(day, onChange);
    }

    /**
     * Returns true when the day combo should be enabled.
     * Pure: no side effects, safe to call from tests.
     *
     * @param yearValid whether the year field holds a value in range
     * @param month the selected month index, {@value #NONE_INDEX} for none
     * @return {@code true} when a valid year and a chosen month together make a day
     *     meaningful
     */
    static boolean dayEnabled(boolean yearValid, int month) {
        return yearValid && month != 0;
    }

    /**
     * Builds the day combo's items: the empty "no day" entry followed by 1 through
     * {@value #DAYS_IN_MONTH_MAX}, so that each item's position is the day number.
     *
     * @return the items, in index order
     */
    private static List<String> buildDayNames() {
        var days = new ArrayList<String>(DAYS_IN_MONTH_MAX + 1);
        days.add("");

        for (var i = 1; i <= DAYS_IN_MONTH_MAX; i++) {
            days.add(Integer.toString(i));
        }

        return List.copyOf(days);
    }

    /**
     * Builds a combo over {@code items} that the user may select from but not type
     * into, which is what makes its selected item the whole of its value and so a
     * legal subject for {@link Controls#item}.
     *
     * @param items the items, in index order
     * @return the combo, with its first item selected
     */
    private static JComboBox<String> uneditableCombo(List<String> items) {
        var combo = new JComboBox<>(items.toArray(new String[0]));
        combo.setEditable(false);

        return combo;
    }

    /**
     * Appends the three labeled date fields (year, month, day) to {@code panel}.
     * The year field is added using the pre-built {@code yearLabel} so the caller
     * can column-align it before layout; month and day use inline left-position labels.
     *
     * @param panel the container to append the three rows to
     * @param yearLabel the year field's label, already sized by the caller
     */
    void addTo(JComponent panel, JLabel yearLabel) {
        BaseDialog.addLabeledField(panel, yearLabel, yearField);

        BaseDialog.addLabeledField(
            panel,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_MONTH),
            monthCombo,
            BaseDialog.LabelPosition.LEFT
        );

        BaseDialog.addLabeledField(
            panel,
            Strings.get(Strings.DIALOG_SONG_SETTINGS_DAY),
            dayCombo,
            BaseDialog.LabelPosition.LEFT
        );
    }

    /**
     * Seeds the three widgets from stored song data. The enabled states follow from
     * the bindings and need no refresh here.
     *
     * <p>The year is written first, so that an unusable one clears the month and day
     * before this method seeds them — what is put in is what comes back out either
     * way. Each write that changes a property runs the row's {@code onChange}.
     *
     * @param yearText the stored year, empty when the song has none
     * @param monthIndex the stored month, {@value #NONE_INDEX} for none
     * @param dayIndex the stored day, {@value #NONE_INDEX} for none
     */
    void setValues(String yearText, int monthIndex, int dayIndex) {
        year.set(yearText);
        month.set(monthIndex);
        day.set(dayIndex);
    }

    /**
     * @return the year exactly as the field holds it, which may be empty or out of range
     */
    String getYear() {
        return year.get();
    }

    /**
     * @return the selected month's index, {@value #NONE_INDEX} for none
     */
    int getMonth() {
        return month.get();
    }

    /**
     * @return the selected day's index, {@value #NONE_INDEX} for none
     */
    int getDay() {
        return day.get();
    }
}
