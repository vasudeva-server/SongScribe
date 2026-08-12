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

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.MainFrame;

/**
 * Base dialog for adding, changing or removing an attachment on a staff element — a tempo change,
 * a beat change, an annotation.
 *
 * <p>A widget shell over a value. It shows the change the element already carries, gathers what
 * the controls now say, and hands that to its {@link AttachmentBackEnd}; it never looks at the
 * document to find out what it is editing, and never writes to it. Everything a subclass
 * implements is therefore about controls: {@link #populateControls} puts a value into them and
 * {@link #gatherChange()} reads one back out. Which element is being edited, whether an
 * attachment is already there, what the undo step is called and how the write is bracketed all
 * belong to the back end, which arrives already bound to its element — see
 * {@link AttachmentEditor}, the only thing that binds one.
 *
 * <p><strong>Lifecycle.</strong> The back end is fixed for the life of the dialog, so a dialog
 * instance edits exactly one element. Opening one for a different element means constructing
 * another, which is what {@link AttachmentEditor} does on every gesture.
 *
 * @param <C> the attachment's value type — a value the dialog can display and build, never a node
 *            of the document graph
 */
public abstract class AttachmentDialog<C> extends StandardDialog {

    protected final JButton removeButton;
    private final AttachmentBackEnd<C> backEnd;

    /**
     * @param mainFrame the window this dialog parents itself to
     * @param title     the window title
     * @param backEnd   the domain half, already bound to the element being edited
     */
    protected AttachmentDialog(MainFrame mainFrame, String title, AttachmentBackEnd<C> backEnd) {
        super(mainFrame, title);
        this.backEnd = backEnd;

        removeButton = new JButton(Strings.get(Strings.LABEL_BUTTON_REMOVE));
        removeButton.addActionListener(_ -> {
            backEnd.remove();
            setVisible(false);
        });
    }

    @Override
    protected Object modifyButtonPanel() {
        buttonPanel.add(removeButton, 0);
        removeButton.setVisible(false);
        return BorderLayout.SOUTH;
    }

    /**
     * Sets the controls to show {@code existingChange}, or to this dialog's defaults for a new
     * attachment when there is none.
     *
     * <p>Called once per opening, before the window appears. An implementation writes to controls
     * and does nothing else — in particular it does not decide what the buttons say, which
     * {@link #getData()} owns for the whole family.
     *
     * @param existingChange the change the element already carries, or {@code null} when it
     *                       carries none and the dialog is being opened to add one
     */
    protected abstract void populateControls(@Nullable C existingChange);

    /**
     * Reads the controls and returns the change they currently describe.
     *
     * <p>Reads only: calling it leaves the controls untouched, so it may be called more than once
     * during a single OK and answer the same thing each time. It is total over every state the
     * controls can reach through the UI — there is no combination of selections for which a
     * subclass may decline to produce a value. Whether the result is acceptable is
     * {@link AttachmentBackEnd#validate}'s question, not this one's.
     *
     * @return the change described by the controls as they now stand
     */
    protected abstract C gatherChange();

    /**
     * Populates the controls from the back end and labels the buttons for what OK will do.
     *
     * <p>Whether an attachment already exists is a domain fact and comes from
     * {@link AttachmentBackEnd#existingChange()}; what to call the button because of it is a
     * presentation decision and is made here. OK reads <em>Add</em> when there is nothing there
     * yet and <em>Modify</em> when there is, and Remove is offered only in the second case — which
     * is what keeps {@link AttachmentBackEnd#remove()} off the path where it would record a step
     * that removes nothing.
     *
     * @return always {@code true}; there is no state in which this dialog declines to open, as its
     *         element was resolved before it was constructed
     */
    @Override
    protected boolean getData() {
        var existingChange = backEnd.existingChange();
        var adding = existingChange == null;

        removeButton.setVisible(!adding);
        okButton.setText(Strings.get(adding ? Strings.LABEL_BUTTON_ADD : Strings.LABEL_BUTTON_MODIFY));
        populateControls(existingChange);

        return true;
    }

    /**
     * Asks the back end whether the gathered change may be applied, and shows the first reason it
     * may not.
     *
     * @return {@code true} when OK may proceed to {@link #setData()}, {@code false} when the user
     *         has been told why it may not and the dialog stays up
     */
    @Override
    protected boolean isValidData() {
        var result = backEnd.validate(gatherChange());

        if (result.isValid()) {
            return true;
        }

        // Only the first failure is shown: stacking modal alerts is worse than under-reporting,
        // and no attachment back end can currently produce more than one. StandardDialog takes
        // over this presentation for every dialog in Phase 2 of plans/ui-dialog-seam.md, which is
        // where the multi-failure question gets decided once rather than per family.
        var failure = result.failures().getFirst();
        var message = failure.message();
        OptionDialogs.showErrorMessage(contentPanel, failure.titleKey(), message.key(), message.args().toArray());

        return false;
    }

    /**
     * Commits the gathered change through the back end, as one undoable step.
     */
    @Override
    protected void setData() {
        backEnd.apply(gatherChange());
    }
}
