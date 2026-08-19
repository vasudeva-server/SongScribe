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

/**
 * A text field that may not be left blank, carrying its own {@link NonBlankGuard}.
 *
 * <p><strong>The field is never blank once focus has left it, unless it has never held a
 * non-blank value.</strong> Emptying a field that has held a value and moving on beeps, says
 * so, and puts back what was there. That promise is the field's own, not something each
 * caller installs, which is what keeps a rule about the value from being one a caller can
 * forget to apply.
 *
 * <p>The alert is deliberate rather than a silent restore: a field that refills itself
 * without a word reads as the application having eaten the keystroke, where the alert
 * names the rule and makes the returning value an answer.
 *
 * <p>The guard speaks only when focus leaves, and it can only restore what it has already
 * seen, so a field that has never held a non-blank value has nothing to fall back to but
 * empty. A dialog that wants a blank field to be uncommittable contributes a validity
 * condition over the field's property — {@code requireValid(bindings().computed(() ->
 * !text.get().isBlank()))} — which follows the document and so answers to typing, cut and
 * paste alike. The two are complementary: the condition is what makes a blank field
 * uncommittable, and the guard is what restores a value once the user moves on.
 *
 * <p>Whoever populates the field must call {@link #rememberCurrentText()} for the restored
 * value to be the one the user was looking at rather than empty.
 */
public class NonBlankTextField extends MyJTextField {

    private final NonBlankGuard guard;

    /**
     * @param columns the field's width in columns, as {@link javax.swing.JTextField}
     *                takes it
     */
    public NonBlankTextField(int columns) {
        super(columns);
        guard = new NonBlankGuard(this);
        setInputVerifier(guard);
    }

    /**
     * Marks the field's current text as the value to restore, per
     * {@link NonBlankGuard#rememberCurrentText()}.
     *
     * <p>Call it after populating the field. A programmatic write is indistinguishable
     * from typing here, so without this the guard would restore empty where the
     * user expects what the dialog had just shown them.
     *
     * @effects replaces the text this field restores on a blank entry, unless the current
     *     text is blank, which is ignored
     */
    public void rememberCurrentText() {
        guard.rememberCurrentText();
    }
}
