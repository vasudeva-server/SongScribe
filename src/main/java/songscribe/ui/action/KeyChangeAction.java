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
package songscribe.ui.action;

import java.awt.event.ActionEvent;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.ElementType;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.ui.component.InsertionPointOverlay;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.KeyChangeDialogController;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.InsertionPointMode;

/**
 * Writes a key change into the middle of a line: the user picks the spot, then names the key.
 *
 * <p>The dialog opens from {@link #insertionPointModeDidEnd}, not from
 * {@link #insertionPointChosen}: opening it there would put a modal dialog in front of a banner
 * still telling the user to click.
 *
 * <p>{@link KeyChangeElement}'s position invariant is why some positions are refused.
 */
public final class KeyChangeAction extends UIAction implements InsertionPointMode.Client {

    public static KeyChangeAction createAction(MainFrame mainFrame) {
        return new KeyChangeAction(mainFrame);
    }

    private KeyChangeAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_KEY_CHANGE),
            null,
            0,
            "key-change",
            Strings.get(Strings.ACTION_KEY_CHANGE_TOOLTIP),
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.OPENS_DIALOG
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        EditModeManager.getInsertionPointMode().enter(this);
    }

    // -------------------------------------------------------------------------
    // InsertionPointMode.Client
    // -------------------------------------------------------------------------

    @Override
    public InsertionPointOverlay.Text overlayText() {
        return new InsertionPointOverlay.Text(
            Strings.get(Strings.INSERTION_MODE_KEY_CHANGE_TITLE),
            Strings.get(Strings.INSERTION_MODE_KEY_CHANGE_HINT));
    }

    @Override
    public boolean acceptsInsertionIndex(Line line, int index) {
        return line.canInsertKeyChangeAt(index);
    }

    /**
     * Always completes the placement: {@link #acceptsInsertionIndex} has already refused every
     * position a key change cannot take.
     */
    @Override
    public InsertionPointMode.Placement insertionPointChosen(Line line, int index) {
        return InsertionPointMode.Placement.COMPLETED;
    }

    /**
     * Cancelling the dialog does not reopen the position mode; a second key change starts from
     * the action again.
     */
    @Override
    public void insertionPointModeDidEnd(InsertionPointMode.Outcome outcome) {
        if (outcome instanceof InsertionPointMode.Outcome.Placed placed) {
            KeyChangeDialogController.addKeyChange(getMainFrame(), placed.line(), placed.index());
        }
    }
}
