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

import javax.swing.InputVerifier;
import javax.swing.JComponent;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;

/**
 * A text field that accepts digits and refuses to yield focus while what it holds is
 * outside a {@link NumericRange}.
 *
 * <p>The {@link NumericRange} owns the rule itself, so a binding or a controller can ask the
 * same question without holding the control. See {@link #isValidValue}.
 */
public class NumericTextField extends MyJTextField {

    private final NumericRange range;

    /**
     * While the value is unacceptable the field refuses to yield focus, so a button that
     * triggers it — OK, say — will not fire. {@link songscribe.ui.dialog.StandardDialog} already
     * exempts Cancel and Remove; a dismissing button outside that family must exempt itself.
     */
    public NumericTextField(int columns, NumericRange range) {
        this(columns, range, InputUtils.NO_MAX_CHARS);
    }

    public NumericTextField(int columns, NumericRange range, int maxChars) {
        super(columns);
        InputUtils.addNumericFilter(this, InputUtils.NumericFormat.INTEGER, maxChars);
        this.range = range;
        setInputVerifier(new RangeVerifier());
    }

    /**
     * Stricter than the focus verifier: a blank field answers {@code false} here
     * even when the range accepts blank entries, because blank names no value.
     */
    public boolean hasValidValue() {
        return isValidValue(getText());
    }

    /**
     * The range is this field's own; the text need not be. A caller holding the text as a bound
     * property asks here, so a derivation records the property as its dependency.
     */
    public boolean isValidValue(String text) {
        return range.containsValue(text);
    }

    private class RangeVerifier extends InputVerifier {

        @Override
        public boolean verify(JComponent input) {
            return range.acceptsInput(getText());
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
                range.min(),
                range.max()
            );
            return false;
        }
    }
}
