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
 * <p><strong>Nothing calls this today.</strong> Both openers —
 * {@code LineComponent.openKeySignatureDialog} and {@code KeyChangeAction.insertionPointChosen} —
 * construct {@link KeyChangeDialog} themselves, so the routing this class describes was never
 * wired. Phase 4 of {@code plans/ui-dialog-interface.md} is what gives it a body: resolving the
 * route, the opening key and the back end is the mediation between the model and the dialog that
 * the dialog interface forbids the dialog from doing itself.
 */
public final class KeyChangeDialogController {

    private KeyChangeDialogController() {
    }

    /**
     * Opens the key signature dialog bound to {@code line} at {@code insertionIndex}.
     *
     * <p>Unlike {@link AttachmentDialogController#edit} there is no "nothing to edit" answer and so no
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
