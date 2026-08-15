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
package songscribe.ui.dialog;

import org.jspecify.annotations.Nullable;

/**
 * The domain half of an {@link AttachmentDialog}: what the element already carries, how to commit
 * a change to it, and how to take it away.
 *
 * <p>Two additions to {@link DialogBackEnd}, each because an attachment dialog has something the
 * base interface does not model. {@link #remove()} is a third commit path — the Remove button, which
 * neither gathers nor validates. {@link #existingChange()} is a domain fact the dialog needs
 * <em>before</em> the user does anything, because it decides what the controls start at, whether
 * Remove is offered at all, and whether OK says Add or Modify.
 *
 * <p>Implementations live in {@code songscribe.ui.dialog.backend} and are bound to their element
 * at construction; {@link AttachmentDialogController} is the only thing that builds one.
 *
 * @param <C> the attachment's value type — the change the dialog shows, gathers and commits.
 *            A value, not the attachment node that holds it: an attachment belongs to the
 *            document graph and never crosses to the dialog
 */
public interface AttachmentBackEnd<C> extends DialogBackEnd<C> {

    /**
     * Returns the change the element already carries, if any.
     *
     * <p>Answered from the document as it stands rather than from a snapshot taken at binding
     * time. It is stable while the dialog is up in practice, because these dialogs are modal and
     * nothing else edits the element meanwhile.
     *
     * @return the existing change, or {@code null} when the element carries none — in which case
     *         the next {@link #apply} adds one instead of replacing one, and {@link #remove()} has
     *         nothing to take away
     */
    @Nullable C existingChange();

    /**
     * Takes the attachment off the element.
     *
     * <p>One undoable step, as {@link #apply} is, and labelled as a removal rather than as a
     * change so that Undo reads correctly in the Edit menu.
     *
     * <p>Calling it when {@link #existingChange()} is {@code null} leaves the document's content
     * unchanged but still records that step, because {@code Line.modifyElement} records
     * unconditionally. Callers that can reach that case — the dialog's Remove button cannot, as it
     * is hidden unless a change exists — should test {@link #existingChange()} first.
     */
    void remove();
}
