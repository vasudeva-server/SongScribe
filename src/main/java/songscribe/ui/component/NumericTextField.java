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

package songscribe.ui.component;

import module java.desktop;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;

public class NumericTextField extends MyJTextField {

    private int min;
    private int max;
    private boolean isOptional;

    public NumericTextField() {
        InputUtils.addNumericFilter(this);
    }

    public NumericTextField(boolean allowDecimal) {
        InputUtils.addNumericFilter(this, allowDecimal);
    }

    public NumericTextField(int columns) {
        this(columns, false);
    }

    public NumericTextField(int columns, boolean allowDecimal) {
        super(columns);
        InputUtils.addNumericFilter(this, allowDecimal);
    }

    /**
     * Creates a field that validates its value against an inclusive
     * integer range when focus leaves it. A blank field is rejected.
     */
    public NumericTextField(int columns, int min, int max) {
        this(columns, min, max, false);
    }

    /**
     * Creates a field that validates its value against an inclusive
     * integer range when focus leaves it.
     *
     * <p>Validation is enforced via an {@link InputVerifier}: while the
     * value is out of range the field refuses to yield focus, so a button
     * that triggers it (e.g. an OK button) will not fire. To let a button
     * bypass validation (e.g. Cancel), call
     * {@code button.setVerifyInputWhenFocusTarget(false)}.
     *
     * @param isOptional when {@code true}, a blank field is accepted and
     *     range validation is skipped; when {@code false}, a blank field
     *     is rejected
     */
    public NumericTextField(int columns, int min, int max, boolean isOptional) {
        super(columns);
        InputUtils.addNumericFilter(this, false);
        this.min = min;
        this.max = max;
        this.isOptional = isOptional;
        setInputVerifier(new RangeVerifier());
    }

    /**
     * Returns {@code true} when the field holds a non-blank value within the
     * inclusive {@code [min, max]} range. Unlike the focus-yield verifier, a
     * blank field is always rejected here regardless of the optional flag.
     */
    public boolean hasValidValue() {
        var text = getText().strip();

        if (text.isEmpty()) {
            return false;
        }

        return parsesInRange(text);
    }

    private boolean isValueInRange() {
        var text = getText().strip();

        if (text.isEmpty()) {
            return isOptional;
        }

        return parsesInRange(text);
    }

    private boolean parsesInRange(String text) {
        try {
            var value = Integer.parseInt(text);
            return value >= min && value <= max;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private class RangeVerifier extends InputVerifier {

        @Override
        public boolean verify(JComponent input) {
            return isValueInRange();
        }

        @Override
        public boolean shouldYieldFocus(JComponent source, JComponent target) {
            if (verify(source)) {
                return true;
            }

            // Returning false keeps focus on the field; show the reason first.
            OptionDialogs.showWarningMessage(
                NumericTextField.this,
                Strings.ALERT_TITLE_NUMBER_OUT_OF_RANGE,
                Strings.ALERT_NUMBER_OUT_OF_RANGE,
                min,
                max
            );
            return false;
        }
    }
}
