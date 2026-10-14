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
 * by calling {@link #rememberCurrentText()}. Blank, not empty, is the test: a field holding only
 * spaces has no value.
 */
public final class NonBlankGuard extends InputVerifier {

    private final JTextComponent field;
    private String previousText;

    public NonBlankGuard(JTextComponent field) {
        this.field = field;
        previousText = "";
    }

    /**
     * Call it after setting the field programmatically: such a write looks to the guard like
     * typing. Blank text is ignored, so the call is safe after a write that may have produced
     * nothing.
     */
    public void rememberCurrentText() {
        var text = field.getText();

        if (!text.isBlank()) {
            previousText = text.strip();
        }
    }

    @Override
    public boolean verify(JComponent input) {
        return !field.getText().isBlank();
    }

    /**
     * Yielding either way keeps the guard from trapping the user: once the previous value is
     * back the field is valid. A button that dismisses the dialog and never reads the field
     * declines this verification instead; see {@link songscribe.ui.dialog.StandardDialog}.
     *
     * <p>A field whose text is already stripped is not written, so leaving it untouched fires
     * nothing.
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
