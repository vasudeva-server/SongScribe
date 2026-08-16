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

/**
 * Base dialog for adding, changing or removing an attachment on a staff element — a tempo change,
 * a beat change, an annotation.
 *
 * <p>A widget shell over a value. It shows the change the element already carries, gathers what
 * the controls now say, and hands both to the {@link DialogOps} it was constructed with; it never
 * looks at the document to find out what it is editing, and never writes to it. Everything a
 * subclass implements is therefore about controls: {@link #populateControls} puts a value into
 * them and {@link #gather()} reads one back out. Which element is being edited, whether an
 * attachment is already there, what the undo step is called and how the write is bracketed all
 * belong to the controller on the other end of {@code ops} — see
 * {@link AttachmentDialogController}, the only thing that binds one.
 *
 * <p><strong>Lifecycle.</strong> The controller is fixed for the life of the dialog, so a dialog
 * instance edits exactly one element. Opening one for a different element means constructing
 * another, which is what {@link AttachmentDialogController} does on every gesture.
 *
 * @param <C> the attachment's value type — a value the dialog can display and build, never a node
 *            of the document graph
 */
public abstract class AttachmentDialog<C> extends StandardDialog<@Nullable C, C> {

    /**
     * @param mainFrame the window this dialog parents itself to
     * @param title     the window title
     * @param ops       the controller's four operations, already bound to the element being edited
     */
    protected AttachmentDialog(MainFrame mainFrame, String title, DialogOps<@Nullable C, C> ops) {
        super(mainFrame, title, ops);
    }

    /**
     * Sets the controls to show {@code existingChange}, or to this dialog's defaults for a new
     * attachment when there is none.
     *
     * <p>Called once per opening, before the window appears. An implementation writes to controls
     * and does nothing else — in particular it does not decide what the buttons say, which
     * {@link #populate} owns for the whole family.
     *
     * @param existingChange the change the element already carries, or {@code null} when it
     *                       carries none and the dialog is being opened to add one
     */
    protected abstract void populateControls(@Nullable C existingChange);

    /**
     * {@inheritDoc}
     *
     * <p>Whether an attachment already exists is a domain fact, answered by {@link DialogOps#read()};
     * what to call the button because of it is a presentation decision and is made here. OK reads
     * <em>Add</em> when there is nothing there yet and <em>Modify</em> when there is. Whether
     * Remove appears at all was already decided when this dialog's {@link DialogOps} was
     * assembled — see {@link StandardDialog}.
     */
    @Override
    protected final void populate(@Nullable C existingChange) {
        var adding = existingChange == null;

        okButton.setText(Strings.get(adding ? Strings.LABEL_BUTTON_ADD : Strings.LABEL_BUTTON_MODIFY));
        populateControls(existingChange);
    }
}
