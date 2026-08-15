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

/**
 * An {@link InputVerifier} for a field that may not be left blank. Emptying the field puts back
 * what was in it. Install it with
 * {@code field.setInputVerifier(new NonEmptyGuard(field, fallback))}; the field may be a combo
 * box's editor as easily as a plain text field.
 *
 * <p><strong>The field is never blank once focus has left it.</strong> That is the whole promise,
 * and it is what lets a commit path downstream treat non-blank text as a precondition rather than
 * as a case to handle.
 *
 * <p>Restoring rather than asking is deliberate. Emptying a field says what the user does
 * <em>not</em> want and nothing about what they do, so there is no question worth putting to them:
 * a prompt offering a substitute interrupts an edit to ask about a value the user never chose, and
 * refusing to yield strands the caret in a field they were trying to leave. Putting the previous
 * value back answers it the way undo would.
 *
 * <p>Whoever populates the field must call {@link #rememberCurrentText()} for the restored value to
 * be the one the user was looking at rather than the fallback.
 */
public final class NonEmptyGuard extends InputVerifier {

    private final JTextComponent field;
    private String previousText;

    /**
     * @param field    the text component to guard
     * @param fallback what to put back when the guard has seen no good value at all — the field was
     *                 never populated, or was populated blank. Must not itself be blank
     * @throws IllegalArgumentException if {@code fallback} is blank, which would let the guard
     *                                  restore a blank value and so break its own promise
     */
    public NonEmptyGuard(JTextComponent field, String fallback) {
        if (fallback.isBlank()) {
            throw new IllegalArgumentException("fallback must not be blank");
        }

        this.field = field;
        previousText = fallback;
    }

    /**
     * Marks the field's current text as the value to restore.
     *
     * <p>Call it after setting the field programmatically. Such a write is invisible here — it
     * leaves the document looking exactly as typing would — so without this the guard cannot tell a
     * value a dialog just put in from a half-finished edit, and a user who empties a freshly opened
     * field would get the fallback instead of what they were looking at.
     *
     * <p>Blank text is ignored rather than remembered: a field populated with nothing has no
     * previous value worth restoring, and taking it would break the class promise. That makes the
     * call safe after a write that may or may not have produced anything.
     */
    public void rememberCurrentText() {
        var text = field.getText();

        if (!text.isBlank()) {
            previousText = text;
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
     * Restores the previous value when the field has been left blank, and always yields.
     *
     * <p>Yielding either way is what makes this guard invisible in use: once the previous value is
     * back the field is valid, so there is nothing to keep the caret for. It also means a Cancel
     * button needs no {@code setVerifyInputWhenFocusTarget(false)} on this guard's account — a
     * blank field can never be what stops a dialog being dismissed.
     *
     * @return always {@code true}
     */
    @Override
    public boolean shouldYieldFocus(JComponent source, JComponent target) {
        if (verify(source)) {
            rememberCurrentText();
        } else {
            field.setText(previousText);
        }

        return true;
    }
}
