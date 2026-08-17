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
 * <p><strong>The field is never blank once focus has left it.</strong> Emptying it and
 * moving on beeps, says so, and puts back what was there. That promise is the field's
 * own, not something each caller installs, which is what keeps a rule about the value
 * from being one a caller can forget to apply.
 *
 * <p>The alert is deliberate rather than a silent restore: a field that refills itself
 * without a word reads as the application having eaten the keystroke, where the alert
 * names the rule and makes the returning value an answer.
 *
 * <p>The guard speaks only when focus leaves. A dialog that wants the user to see, while
 * they type, that a blank field cannot be committed contributes a validity condition over
 * the field's property — {@code requireValid(computed(() -> !text.get().isBlank()))} —
 * which follows the document and so answers to typing, cut and paste alike. The two are
 * complementary: the condition disables the commit while the field is blank, and the
 * guard is what makes the field non-blank again once the user moves on.
 *
 * <p>Whoever populates the field must call {@link #rememberCurrentText()} for the restored
 * value to be the one the user was looking at rather than the fallback.
 */
public class NonBlankTextField extends MyJTextField {

    private final NonBlankGuard guard;

    /**
     * @param columns  the field's width in columns, as {@link javax.swing.JTextField}
     *                 takes it
     * @param fallback what to put back when the guard has seen no good value at all — the
     *                 field was never populated, or was populated blank. Must not itself
     *                 be blank
     * @throws IllegalArgumentException if {@code fallback} is blank, which would let the
     *                                  guard restore a blank value and so break the
     *                                  class promise
     */
    public NonBlankTextField(int columns, String fallback) {
        super(columns);
        guard = new NonBlankGuard(this, fallback);
        setInputVerifier(guard);
    }

    /**
     * Marks the field's current text as the value to restore, per
     * {@link NonBlankGuard#rememberCurrentText()}.
     *
     * <p>Call it after populating the field. A programmatic write is indistinguishable
     * from typing here, so without this the guard would restore the fallback where the
     * user expects what the dialog had just shown them.
     *
     * @effects replaces the text this field restores on a blank entry, unless the current
     *     text is blank, which is ignored
     */
    public void rememberCurrentText() {
        guard.rememberCurrentText();
    }
}
