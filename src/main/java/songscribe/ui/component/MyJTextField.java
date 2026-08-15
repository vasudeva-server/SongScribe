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

import java.awt.event.KeyEvent;
import javax.swing.JTextField;

import org.jspecify.annotations.Nullable;

public class MyJTextField extends JTextField {

    protected final TextFocusDelegate focusDelegate;

    public MyJTextField() {
        this(null, 0);
    }

    public MyJTextField(String text) {
        this(text, 0);
    }

    public MyJTextField(int columns) {
        this(null, columns);
    }

    protected MyJTextField(@Nullable String text, int columns) {
        super(text, columns);
        focusDelegate = createFocusDelegate();
        setCaret(new SelectionHidingCaret());
    }

    /** Hook for subclasses to provide a specialized focus delegate. */
    protected TextFocusDelegate createFocusDelegate() {
        return new TextFocusDelegate(this);
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
        if (focusDelegate.processKeyEvent(e)) {
            return;
        }
        super.processKeyEvent(e);
    }
}
