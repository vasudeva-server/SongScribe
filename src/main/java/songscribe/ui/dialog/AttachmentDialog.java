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
import songscribe.message.mutation.ElementField;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.ui.component.MainFrame;

/**
 * Base dialog for adding, editing, or removing an attachment on a staff element
 * (tempo change, beat change, annotation, etc.).
 */
public abstract class AttachmentDialog<T> extends StandardDialog {

    /**
     * The undo op the current commit performs, so each subclass can name itself
     * (e.g. {@code Add Tempo Change} / {@code Change Tempo Change} /
     * {@code Remove Tempo Change}).
     */
    protected enum AttachmentOp { ADD, CHANGE, REMOVE }

    protected @Nullable StaffElement selectedElement = null;
    protected @Nullable Line selectedLine = null;
    protected final JButton removeButton;

    protected AttachmentDialog(MainFrame mainFrame, String title) {
        super(mainFrame, title);
        removeButton = new JButton(Strings.get(Strings.LABEL_BUTTON_REMOVE));
        removeButton.addActionListener(_ -> {
            var element = selectedElement;
            var line = selectedLine;

            if (element == null || line == null) {
                throw new IllegalStateException("no element selected");
            }

            if (!canClearChange(element)) {
                return;
            }

            var elementIndex = line.getElementIndex(element);
            line.withModification(opLabel(AttachmentOp.REMOVE), () -> line.modifyElement(
                elementIndex, getElementField(), () -> clearChange(element)));
            setVisible(false);
        });
    }

    @Override
    protected Object modifyButtonPanel() {
        buttonPanel.add(removeButton, 0);
        removeButton.setVisible(false);
        return BorderLayout.SOUTH;
    }

    protected abstract ElementField getElementField();

    /**
     * Returns the resolved undo op-name for the given operation, letting each
     * subclass name itself with its own noun.
     */
    protected abstract String opLabel(AttachmentOp op);

    protected abstract @Nullable T getExistingChange(StaffElement element);

    protected abstract void populateControls(@Nullable T change);

    protected abstract void applyChange(StaffElement element);

    protected abstract void clearChange(StaffElement element);

    /**
     * Whether the Remove button may proceed. Checked before any modification bracket opens,
     * because {@link Line#modifyElement} records an ElementModification unconditionally and a
     * refusal inside {@link #clearChange} would leave an empty undo step behind. A subclass that
     * refuses is responsible for telling the user why.
     */
    protected boolean canClearChange(StaffElement element) {
        return true;
    }

    /**
     * Shows this dialog pre-bound to {@code element} on {@code line}.
     *
     * <p>{@link #getData()} resolves its target from the selection via
     * {@link songscribe.ui.component.ScoreView#getSingleSelectedElement()}, which answers null
     * whenever the selection is a directly selected notation object rather than an element index
     * range — the state a click on an attachment leaves behind. A caller that already knows the
     * target must supply it here instead of relying on that resolution.
     */
    void showFor(StaffElement element, Line line) {
        selectedElement = element;
        selectedLine = line;
        setVisible(true);
    }

    @Override
    protected boolean getData() {
        if (selectedElement == null) {
            var score = requireScoreView();
            selectedElement = score.getSingleSelectedElement();
            selectedLine = score.getSong().getLine(
                score.getSelectionCoordinator().getActiveLineIndex());
        }

        var change = selectedElement != null ? getExistingChange(selectedElement) : null;
        var adding = change == null;

        removeButton.setVisible(!adding);
        okButton.setText(Strings.get(adding ? Strings.LABEL_BUTTON_ADD : Strings.LABEL_BUTTON_MODIFY));

        if (selectedElement != null) {
            populateControls(change);
        }

        return true;
    }

    @Override
    protected void setData() {
        var element = selectedElement;
        var line = selectedLine;

        if (element == null || line == null) {
            throw new IllegalStateException("no element selected");
        }

        var elementIndex = line.getElementIndex(element);
        var op = getExistingChange(element) == null ? AttachmentOp.ADD : AttachmentOp.CHANGE;
        line.withModification(opLabel(op), () -> line.modifyElement(
            elementIndex, getElementField(), () -> applyChange(element)));
    }
}
