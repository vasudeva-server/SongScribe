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
import javax.swing.JTextArea;

import org.jspecify.annotations.Nullable;

import songscribe.ui.binding.BoundText;

public class MyJTextArea extends JTextArea {

    private TextFocusDelegate delegate;

    public MyJTextArea() {
        init();
    }

    public MyJTextArea(String text) {
        super(text);
        init();
    }

    public MyJTextArea(int rows, int columns) {
        super(rows, columns);
        init();
    }

    private void init() {
        delegate = createDelegate();
        setCaret(new SelectionHidingCaret());
    }

    /** Hook for subclasses (and tests) to provide a specialized focus delegate. */
    protected TextFocusDelegate createDelegate() {
        return new TextFocusDelegate(this);
    }

    /**
     * Writes {@code text} into this area, through the area's property view when it
     * has one.
     *
     * <p>A property created over this area with commit timing observes focus loss,
     * which a programmatic write does not cause, so writing the document directly
     * would leave every value derived from the area stale until the user happened to
     * click away. Routing the write into the property instead makes a stray write a
     * correct one, so no call site has to know whether the area it is writing is
     * bound.
     *
     * <p>The write reaches the document either way; see
     * {@link BoundText#routed(javax.swing.text.JTextComponent, String)} for the cases
     * in which it is written here.
     *
     * @param text the text to write, or {@code null} to empty the area
     */
    @Override
    public void setText(@Nullable String text) {
        if (!BoundText.routed(this, text)) {
            super.setText(text);
        }
    }

    @Override
    protected void processKeyEvent(KeyEvent e) {
        if (!delegate.processKeyEvent(e)) {
            super.processKeyEvent(e);
        }
    }
}
