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
 * The multi-row counterpart of {@link NonBlankTextField}: a text area that installs its own
 * {@link NonBlankGuard}.
 *
 * <p><strong>The area is never blank once focus has left it.</strong> {@link NonBlankGuard}
 * carries the rule; the strip it performs on focus loss takes leading and trailing line breaks
 * along with spaces, and leaves an interior break alone.
 *
 * <p>Whoever populates the area must call {@link #rememberCurrentText()} for the restored value
 * to be the one the user was looking at.
 */
public class NonBlankTextArea extends MyJTextArea {

    private final NonBlankGuard guard;

    /**
     * @param rows    the area's height in rows, as {@link javax.swing.JTextArea} takes it
     * @param columns the area's width in columns, as {@link javax.swing.JTextArea} takes it
     */
    public NonBlankTextArea(int rows, int columns) {
        super(rows, columns);
        guard = new NonBlankGuard(this);
        setInputVerifier(guard);
    }

    /**
     * Marks the area's current text as the value to restore, per
     * {@link NonBlankGuard#rememberCurrentText()}.
     *
     * @effects replaces the text this area restores on a blank entry, unless the current text is
     *     blank, which is ignored
     */
    public void rememberCurrentText() {
        guard.rememberCurrentText();
    }
}
