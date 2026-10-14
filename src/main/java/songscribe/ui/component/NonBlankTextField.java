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
 * A text field that installs its own {@link NonBlankGuard}.
 *
 * <p><strong>The field is never blank once focus has left it.</strong> Emptying it and moving on
 * beeps, says so, and puts back what was there; {@link NonBlankGuard} carries the rule.
 *
 * <p>Whoever populates the field must call {@link #rememberCurrentText()} for the restored value
 * to be the one the user was looking at.
 */
public class NonBlankTextField extends MyJTextField {

    private final NonBlankGuard guard;

    public NonBlankTextField(int columns) {
        super(columns);
        guard = new NonBlankGuard(this);
        setInputVerifier(guard);
    }

    public void rememberCurrentText() {
        guard.rememberCurrentText();
    }
}
