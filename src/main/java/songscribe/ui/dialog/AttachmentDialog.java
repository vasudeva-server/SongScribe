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

import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.util.Copyable;

/**
 * Base dialog for adding, changing or removing an attachment on a staff element. A dialog
 * instance edits exactly one element.
 *
 * @param <C> the attachment's value type, never a node of the document graph
 */
public abstract class AttachmentDialog<C extends Copyable<C>> extends StandardDialog<@Nullable C, C> {

    protected AttachmentDialog(MainFrame mainFrame, String title, DialogOps<? extends @Nullable C, C> ops) {
        super(mainFrame, title, ops);
    }

    /**
     * An implementation writes to controls and does not decide what the buttons say, which
     * {@link #populate} owns for the whole family.
     *
     * @param existingChange the change the element already carries, or {@code null} when it
     *                       carries none and the dialog is being opened to add one
     */
    protected abstract void populateControls(@Nullable C existingChange);

    /**
     * Whether Remove appears at all was decided when this dialog's {@link DialogOps} was
     * assembled; only what the button is called is decided here.
     */
    @Override
    protected final void populate(@Nullable C existingChange) {
        var adding = existingChange == null;

        okButton.setText(Strings.get(adding ? Strings.LABEL_BUTTON_ADD : Strings.LABEL_BUTTON_MODIFY));
        populateControls(existingChange);
    }
}
