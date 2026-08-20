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
 * <p>The field owns the presentation of the rule — the input filter, the focus
 * verifier, the alert naming the bounds — and the {@link NumericRange} owns the rule
 * itself, so a binding or a controller can ask the same question without holding the
 * control. See {@link #isValidValue}.
 */
public class NumericTextField extends MyJTextField {

    private final NumericRange range;

    /**
     * Creates a field accepting the values in {@code range}, with no limit on how many
     * characters may be typed.
     *
     * <p>Validation is enforced with an {@link InputVerifier}: while the value is
     * unacceptable the field refuses to yield focus, so a button that triggers it — OK,
     * say — will not fire. A dialog built on {@link songscribe.ui.dialog.StandardDialog} needs
     * nothing for this, since it already exempts Cancel and Remove; a dismissing button outside
     * that family exempts itself the same way.
     *
     * @param columns the field's width, in the usual Swing column units
     * @param range the values this field accepts, and whether it may be left empty
     */
    public NumericTextField(int columns, NumericRange range) {
        this(columns, range, InputUtils.NO_MAX_CHARS);
    }

    /**
     * Creates a field accepting the values in {@code range} and limiting typed input to
     * {@code maxChars} characters.
     *
     * @param columns the field's width, in the usual Swing column units
     * @param range the values this field accepts, and whether it may be left empty
     * @param maxChars the most characters that may be typed, or
     *     {@link InputUtils#NO_MAX_CHARS} for no limit
     * @effects installs an input filter and a focus verifier on this field.
     */
    public NumericTextField(int columns, NumericRange range, int maxChars) {
        super(columns);
        InputUtils.addNumericFilter(this, false, maxChars);
        this.range = range;
        setInputVerifier(new RangeVerifier());
    }

    /**
     * Returns whether this field currently holds a value its range contains.
     *
     * <p>Stricter than the focus verifier: a blank field answers {@code false} here
     * even when the range accepts blank entries, because blank names no value.
     *
     * @return {@code true} when this field's text names an integer within the range
     */
    public boolean hasValidValue() {
        return isValidValue(getText());
    }

    /**
     * Returns whether {@code text} names a value this field's range contains.
     *
     * <p>The range is this field's own; the text need not be. A caller holding the
     * field's text as a bound property asks through this method rather than reading the
     * control, so that a derivation records the property as its dependency rather than
     * acquiring none.
     *
     * @param text the text to test against this field's range
     * @return {@code true} when {@code text} strips to a non-empty string naming an
     *     integer the range contains
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
