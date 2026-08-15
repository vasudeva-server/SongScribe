/*
   SongScribe song notation program
   Copyright (C) 2006—2007 Csaba Kavai

   This program is free software; you can redistribute it and/or
   modify it under the terms of the GNU General Public License
   as published by the Free Software Foundation; either version 2
   of the License, or (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/
package songscribe.ui.dialog;

import songscribe.dom.Line;
import songscribe.ui.component.MainFrame;

/**
 * Opens the key signature dialog on a line's own key or on a key signature inside a line.
 *
 * <p>This is a class rather than a private method on either caller for the reason
 * {@link AttachmentEditor} gives: the double-click gesture in {@code LineComponent.mouseClicked}
 * needs a static to observe, because opening the dialog is that gesture's only observable effect.
 * It also has two callers — the gesture and {@code KeyChangeAction}'s placement — so
 * constructing the dialog here keeps that two-step in one place.
 */
public final class KeyChangeEditor {

    private KeyChangeEditor() {
    }

    /**
     * Opens the key signature dialog bound to {@code line} at {@code insertionIndex}.
     *
     * <p>Unlike {@link AttachmentEditor#edit} there is no "nothing to edit" answer and so no
     * return value: every line has a key, and a caller that reached here has already resolved a
     * target — a hit target under the pointer, or an index the insertion predicate accepted.
     *
     * @param mainFrame the dialog's parent frame
     * @param line the line whose key is being added, edited or written into
     * @param insertionIndex the element index the change is anchored to;
     *     {@link KeyChangeDialog#LINE_OWN_KEY_INDEX} for the line's own key
     */
    public static void edit(MainFrame mainFrame, Line line, int insertionIndex) {
        new KeyChangeDialog(mainFrame).showFor(line, insertionIndex);
    }
}
