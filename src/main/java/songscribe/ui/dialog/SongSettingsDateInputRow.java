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

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;

import songscribe.Strings;
import songscribe.ui.component.NumericTextField;

/**
 * A self-contained date-input row (year, month, day) that can be embedded
 * in any dialog panel. Owns its three widgets, wires their listeners, and
 * exposes pure predicate methods so callers can unit-test the enable/reset
 * logic without driving Swing.
 */
final class SongSettingsDateInputRow {

    private static final int YEAR_MIN = 1942;
    private static final int YEAR_MAX = 2007;
    private static final int MAX_YEAR_CHARS = 4;
    private static final int YEAR_FIELD_COLUMNS = 4;
    private static final int DAYS_IN_MONTH_MAX = 31;

    private final NumericTextField yearField =
        new NumericTextField(YEAR_FIELD_COLUMNS, YEAR_MIN, YEAR_MAX, true, MAX_YEAR_CHARS);
    private final JComboBox<String> monthCombo = new JComboBox<>(
        new String[]{
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
            Strings.get(Strings.MONTH_DECEMBER),
        }
    );
    private final JComboBox<String> dayCombo;

    // Set while month/day combos are changed programmatically — by the year
    // focus listener's reset and by setValues' seeding — so their action
    // listeners skip the work (and the onChange callback) those changes trigger.
    private boolean adjustingDateFields = false;

    SongSettingsDateInputRow(Runnable onChange) {
        monthCombo.setEditable(false);

        var days = new String[DAYS_IN_MONTH_MAX + 1];
        days[0] = "";

        for (var i = 1; i <= DAYS_IN_MONTH_MAX; i++) {
            days[i] = Integer.toString(i);
        }

        dayCombo = new JComboBox<>(days);
        dayCombo.setEditable(false);

        yearField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                var yearValid = yearField.hasValidValue();

                if (!yearValid) {
                    // Reset month/day programmatically; the guard keeps the
                    // combos' listeners from re-running this same validation.
                    adjustingDateFields = true;
                    monthCombo.setSelectedIndex(0);
                    dayCombo.setSelectedIndex(0);
                    adjustingDateFields = false;
                }

                updateFieldStates(yearValid);

                if (yearValid) {
                    onChange.run();
                }
            }
        });
        monthCombo.addActionListener(e -> {
            if (adjustingDateFields) {
                return;
            }

            if (monthCombo.getSelectedIndex() == 0) {
                dayCombo.setSelectedIndex(0);
            }

            var yearValid = yearField.hasValidValue();
            updateFieldStates(yearValid);

            if (yearValid) {
                onChange.run();
            }
        });
        dayCombo.addActionListener(e -> {
            if (adjustingDateFields) {
                return;
            }

            if (yearField.hasValidValue()) {
                onChange.run();
            }
        });
    }

    /**
     * Returns true when the day combo should be enabled.
     * Pure: no side effects, safe to call from tests.
     */
    static boolean dayEnabled(boolean yearValid, int month) {
        return yearValid && month != 0;
    }

    /**
     * Appends the three labeled date fields (year, month, day) to {@code panel}.
     * The year field is added using the pre-built {@code yearLabel} so the caller
     * can column-align it before layout; month and day use inline left-position labels.
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
     * Seeds the three widgets from stored song data and refreshes the
     * enable states to match. Seeding is guarded so the combos' listeners
     * do not fire the onChange callback once per field during population;
     * the caller refreshes the preview a single time afterward.
     */
    void setValues(String year, int month, int day) {
        adjustingDateFields = true;
        yearField.setText(year);
        monthCombo.setSelectedIndex(month);
        dayCombo.setSelectedIndex(day);
        adjustingDateFields = false;
        updateFieldStates(yearField.hasValidValue());
    }

    String getYear() {
        return yearField.getText();
    }

    int getMonth() {
        return monthCombo.getSelectedIndex();
    }

    int getDay() {
        return dayCombo.getSelectedIndex();
    }

    /**
     * Refreshes the enabled states of the month and day combos based on the
     * current year validity and month selection.
     */
    void updateFieldStates() {
        updateFieldStates(yearField.hasValidValue());
    }

    private void updateFieldStates(boolean yearValid) {
        // The month combo is enabled exactly when the year is valid.
        monthCombo.setEnabled(yearValid);
        dayCombo.setEnabled(dayEnabled(yearValid, monthCombo.getSelectedIndex()));
    }
}
