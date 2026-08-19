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
import songscribe.ui.binding.ValueProperty;
import songscribe.ui.binding.Widgets;
import songscribe.ui.component.NumericRange;
import songscribe.ui.component.NumericTextField;


/**
 * A self-contained date-input row (year, month, day) that can be embedded in any
 * dialog panel. Owns its three widgets and views each of them as a property.
 *
 * <p>The row is not a {@link BaseDialog.Tab} and owns no {@code Bindings} of its
 * own: it takes one from the tab that builds it, so the dialog still owns every
 * edge this row declares and disposes them with the rest.
 *
 * <p>The month combo is <i>valued</i>: it holds {@link MonthChoice} constants, so the
 * property answers a month rather than a position and no agreement between list order
 * and month number has to hold. The stored integer {@code SongMetadata} carries is
 * produced in {@link #getMonth} and consumed in {@link #setValues}, which are the only
 * two places that representation appears.
 *
 * <p>The day combo is still indexed — a day genuinely is its number — with index
 * {@value #NONE_INDEX} the empty entry meaning "no day".
 */
final class SongSettingsDateInputRow {

    private static final int YEAR_MIN = 1942;
    private static final int YEAR_MAX = 2007;
    private static final int MAX_YEAR_CHARS = 4;
    private static final int YEAR_FIELD_COLUMNS = 4;

    // A song's date may be absent entirely, so an empty year field stands. Held as a
    // constant rather than reached through yearField so that the derivations below ask
    // the rule directly instead of through a Swing control.
    private static final NumericRange YEAR_RANGE =
        new NumericRange(YEAR_MIN, YEAR_MAX, NumericRange.Blank.ACCEPTED);
    private static final int DAYS_IN_MONTH_MAX = 31;

    /** The empty leading entry of the day combo: no day. */
    static final int NONE_INDEX = 0;

    /**
     * The month combo's items: the twelve months and the absence of one.
     *
     * <p>A closed set rather than a list of names read by position. The number
     * {@code SongMetadata} stores is a property of the constant, so reordering the
     * combo, translating it, or inserting a separator cannot change what a selection
     * means.
     */
    private enum MonthChoice {
        NONE(0, Strings.MONTH_NONE),
        JANUARY(1, Strings.MONTH_JANUARY),
        FEBRUARY(2, Strings.MONTH_FEBRUARY),
        MARCH(3, Strings.MONTH_MARCH),
        APRIL(4, Strings.MONTH_APRIL),
        MAY(5, Strings.MONTH_MAY),
        JUNE(6, Strings.MONTH_JUNE),
        JULY(7, Strings.MONTH_JULY),
        AUGUST(8, Strings.MONTH_AUGUST),
        SEPTEMBER(9, Strings.MONTH_SEPTEMBER),
        OCTOBER(10, Strings.MONTH_OCTOBER),
        NOVEMBER(11, Strings.MONTH_NOVEMBER),
        DECEMBER(12, Strings.MONTH_DECEMBER);

        private final int stored;
        private final String labelKey;

        MonthChoice(int stored, String labelKey) {
            this.stored = stored;
            this.labelKey = labelKey;
        }

        /**
         * Returns the constant {@code SongMetadata} stores as {@code stored}.
         *
         * @param stored the stored month number, 0 for none
         * @return the matching constant
         * @throws IllegalArgumentException if {@code stored} names no month and is not
         *     0 — which a song file damaged outside this application can produce, and
         *     nothing in the application can
         */
        static MonthChoice ofStored(int stored) {
            for (var choice : values()) {
                if (choice.stored == stored) {
                    return choice;
                }
            }

            throw new IllegalArgumentException("No month is stored as " + stored);
        }

        /** @return the number {@code SongMetadata} stores for this month, 0 for none */
        int stored() {
            return stored;
        }

        /** @return the localized month name, which for {@link #NONE} is empty */
        @Override
        public String toString() {
            return Strings.get(labelKey);
        }
    }

    private final List<String> dayNames = buildDayNames();

    private final NumericTextField yearField =
        new NumericTextField(YEAR_FIELD_COLUMNS, YEAR_RANGE, MAX_YEAR_CHARS);
    private final JComboBox<MonthChoice> monthCombo = uneditableCombo(List.of(MonthChoice.values()));
    private final JComboBox<String> dayCombo = uneditableCombo(dayNames);

    private final Property<String> year = Controls.text(yearField, Timing.ON_COMMIT);
    private final Property<MonthChoice> month = Controls.item(monthCombo);
    private final Property<Integer> day = Controls.itemIndex(dayCombo);

    /**
     * Builds the row and declares its edges on {@code bindings}.
     *
     * <p>The row announces nothing of its own. Its three getters read the three
     * properties, so a caller that reads them inside a {@code Bindings.computed}
     * acquires a dependency on each one it reads and is re-derived when the user edits
     * it — including when the year becomes invalid, losing a date being as much a change
     * to what this row contributes as gaining one. A second callback route beside that
     * would carry the same change and fire on a different schedule.
     *
     * <p>Clearing the year clears the month and day with it, so one such gesture
     * notifies three times. A reader that cannot afford the repeats derives through a
     * {@link ValueProperty}, which notifies only on a transition.
     *
     * <p>The two enabled states are bindings, and the framework keeps them current on
     * its own.
     *
     * @param bindings the owning dialog's bindings, which this row registers its
     *     edges and effects on and which disposes them
     */
    SongSettingsDateInputRow(Bindings bindings) {
        // The month combo is enabled exactly when the year is valid; the day combo
        // additionally needs a month. Both read the year through the property, never
        // off the field, so the derivation records the dependency.
        bindings.bind(
            Widgets.enabled(monthCombo),
            bindings.computed(() -> YEAR_RANGE.containsValue(year.get()))
        );
        bindings.bind(
            Widgets.enabled(dayCombo),
            bindings.computed(() -> dayEnabled(YEAR_RANGE.containsValue(year.get()), month.get()))
        );

        bindings.onNotify(year, () -> {
            if (!YEAR_RANGE.containsValue(year.get())) {
                // A year that names no date leaves no month or day standing.
                month.set(MonthChoice.NONE);
                day.set(NONE_INDEX);
            }
        });

        bindings.onNotify(month, () -> {
            if (month.get() == MonthChoice.NONE) {
                day.set(NONE_INDEX);
            }
        });
    }

    /**
     * Returns whether the day combo should be enabled.
     *
     * <p>A total function of its two arguments: it reads no field and no control, so
     * the derivation that drives the combo's enabled state passes what it read from
     * the properties rather than letting this reach for it.
     *
     * @param yearValid whether the year field holds a value in range
     * @param month the selected month, {@link MonthChoice#NONE} for none
     * @return {@code true} when a valid year and a chosen month together make a day
     *     meaningful
     */
    private static boolean dayEnabled(boolean yearValid, MonthChoice month) {
        return yearValid && month != MonthChoice.NONE;
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
     * @param <E> the item type
     * @param items the items, in the order they are offered
     * @return the combo, with its first item selected
     */
    private static <E> JComboBox<E> uneditableCombo(List<E> items) {
        var combo = new JComboBox<E>();
        items.forEach(combo::addItem);
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
     * way.
     *
     * @param yearText the stored year, empty when the song has none
     * @param monthNumber the stored month, 0 for none
     * @param dayIndex the stored day, {@value #NONE_INDEX} for none
     * @throws IllegalArgumentException if {@code monthNumber} names no month and is
     *     not 0
     */
    void setValues(String yearText, int monthNumber, int dayIndex) {
        year.set(yearText);
        month.set(MonthChoice.ofStored(monthNumber));
        day.set(dayIndex);
    }

    /**
     * @return the year exactly as the field holds it, which may be empty or out of range
     */
    String getYear() {
        return year.get();
    }

    /**
     * @return the selected month as {@code SongMetadata} stores it, 0 for none
     */
    int getMonth() {
        return month.get().stored();
    }

    /**
     * @return the selected day's index, {@value #NONE_INDEX} for none
     */
    int getDay() {
        return day.get();
    }
}
