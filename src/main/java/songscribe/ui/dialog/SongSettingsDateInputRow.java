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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.binding.Bindings;
import songscribe.ui.binding.Controls;
import songscribe.binding.Property;
import songscribe.ui.binding.Timing;
import songscribe.binding.ValueProperty;
import songscribe.dom.PartialDate;
import songscribe.ui.binding.Widgets;
import songscribe.ui.component.NumericRange;
import songscribe.ui.component.NumericTextField;


/**
 * A date-input row (year, month, day). It owns no {@code Bindings} of its own: it takes one from
 * the tab that builds it, so the dialog disposes every binding this row declares.
 *
 * <p>The month combo is <i>valued</i>: it holds {@link MonthChoice} constants, so no agreement
 * between list order and month number has to hold. The month number {@link PartialDate} carries
 * appears only in {@link #getDate} and {@link #setDate}.
 *
 * <p>The day combo is indexed, with index {@value #NONE_INDEX} the empty entry meaning "no day".
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

    static final int NONE_INDEX = 0;

    /**
     * The number {@link PartialDate} carries is a property of the constant, so reordering or
     * translating the combo cannot change what a selection means.
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

        static MonthChoice ofStored(int stored) {
            for (var choice : values()) {
                if (choice.stored == stored) {
                    return choice;
                }
            }

            throw new IllegalArgumentException("No month is stored as " + stored);
        }

        int stored() {
            return stored;
        }

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
    private final Property<@Nullable Integer> day = Controls.itemIndex(dayCombo);

    /**
     * The row announces nothing of its own: its getters read the properties, so a caller inside a
     * {@code Bindings.computed} is re-derived when the user edits it. Clearing the year clears the
     * month and day with it, so one gesture notifies three times; a reader that cannot afford the
     * repeats derives through a {@link ValueProperty}.
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

    private static boolean dayEnabled(boolean yearValid, MonthChoice month) {
        return yearValid && month != MonthChoice.NONE;
    }

    private static List<String> buildDayNames() {
        var days = new ArrayList<String>(DAYS_IN_MONTH_MAX + 1);
        days.add("");

        for (var i = 1; i <= DAYS_IN_MONTH_MAX; i++) {
            days.add(Integer.toString(i));
        }

        return List.copyOf(days);
    }

    /**
     * Not editable, which makes it a legal subject for {@link Controls#item}.
     */
    private static <E> JComboBox<E> uneditableCombo(List<E> items) {
        var combo = new JComboBox<E>();
        items.forEach(combo::addItem);
        combo.setEditable(false);

        return combo;
    }

    /**
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
     * The year is written first, so that an unusable one clears the month and day before this
     * method seeds them.
     */
    void setDate(PartialDate date) {
        year.set(date.year());

        switch (date) {
            case PartialDate.EmptyDate _, PartialDate.YearOnly _ -> {
                month.set(MonthChoice.NONE);
                day.set(NONE_INDEX);
            }
            case PartialDate.YearMonth yearMonth -> {
                month.set(MonthChoice.ofStored(yearMonth.month()));
                day.set(NONE_INDEX);
            }
            case PartialDate.YearMonthDay full -> {
                month.set(MonthChoice.ofStored(full.month()));
                day.set(full.day());
            }
        }
    }

    /**
     * The year goes in exactly as the field holds it, which may be out of range.
     */
    PartialDate getDate() {
        var dayIndex = day.get();

        if (dayIndex == null) {
            throw new IllegalStateException("The day combo has no selection");
        }

        return PartialDate.of(year.get(), month.get().stored(), dayIndex);
    }
}
