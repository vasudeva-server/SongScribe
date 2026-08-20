/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package songscribe.ui.component;

import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;

/**
 * An {@link InputVerifier} for a field that may not be left blank. Emptying the field beeps, says
 * so, and puts back what was in it. Install it with
 * {@code field.setInputVerifier(new NonBlankGuard(field))}.
 *
 * <p><strong>The field is never blank once focus has left it, and never carries leading or
 * trailing whitespace.</strong> Whoever populates the field gives the guard the value it restores,
 * by calling {@link #rememberCurrentText()}; a caller that also wants the commit disabled while
 * the field is blank binds {@code requireValid} over the field's property, which speaks while the
 * user types where the guard speaks when they leave.
 *
 * <p>Blank rather than empty is the test throughout: a field holding only spaces is a field the
 * user has left with no value in it, whatever the document length says. Text that survives the
 * test is stripped, so the surrounding whitespace never reaches the value either.
 */
public final class NonBlankGuard extends InputVerifier {

    private final JTextComponent field;
    private String previousText;

    /**
     * @param field the text component to guard
     */
    public NonBlankGuard(JTextComponent field) {
        this.field = field;
        previousText = "";
    }

    /**
     * Marks the field's current text as the value to restore.
     *
     * <p>Call it after setting the field programmatically. Such a write is invisible here — it
     * leaves the document looking exactly as typing would — so without this the guard cannot tell a
     * value a dialog just put in from a half-finished edit.
     *
     * <p>Blank text is ignored rather than remembered: a field populated with nothing has no
     * previous value worth restoring. That makes the call safe after a write that may or may not
     * have produced anything.
     *
     * @effects remembers the current text, stripped, unless it is blank
     */
    public void rememberCurrentText() {
        var text = field.getText();

        if (!text.isBlank()) {
            previousText = text.strip();
        }
    }

    /**
     * @return {@code true} when the field holds something other than whitespace
     */
    @Override
    public boolean verify(JComponent input) {
        return !field.getText().isBlank();
    }

    /**
     * Strips the field's text when it holds a value, and tells the user and restores the previous
     * value when it does not. Always yields.
     *
     * <p>Yielding either way is what keeps the guard from trapping the user: once the previous
     * value is back the field is valid, so there is nothing to keep the caret for.
     *
     * <p>Yielding does not on its own keep a button that dismisses the dialog clickable; a button
     * that never reads the field declines this verification instead. See
     * {@link songscribe.ui.dialog.StandardDialog}.
     *
     * <p>Stripping happens here, so what the user is left looking at is what the value will be. A
     * field whose text is already stripped is not written, so leaving a field untouched costs no
     * document change and fires nothing.
     *
     * @return always {@code true}
     * @effects replaces the field's text with its stripped form, or with the previous value when
     *     the field is blank, in which case the user is alerted first
     */
    @Override
    public boolean shouldYieldFocus(JComponent source, JComponent target) {
        if (verify(source)) {
            var stripped = field.getText().strip();

            if (!stripped.equals(field.getText())) {
                field.setText(stripped);
            }

            rememberCurrentText();
        } else {
            // Beeps as well as showing the alert; see OptionDialogs.
            OptionDialogs.showErrorMessage(field, Strings.ALERT_TITLE_INVALID_ENTRY, Strings.ALERT_FIELD_EMPTY);
            field.setText(previousText);
        }

        return true;
    }
}
