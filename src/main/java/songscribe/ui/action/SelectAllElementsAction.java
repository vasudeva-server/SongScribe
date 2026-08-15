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
import java.awt.event.KeyEvent;

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.command.SelectAllElementsCommand;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.util.UIUtils;

public final class SelectAllElementsAction extends UIAction {

    public static SelectAllElementsAction createAction(MainFrame mainFrame) {
        return new SelectAllElementsAction(mainFrame);
    }

    private SelectAllElementsAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_EDIT_SELECT_ALL_ELEMENTS),
            "select-all-elements",
            KeyEvent.VK_A,
            UIUtils.MENU_SHORTCUT_MASK,
            Flag.REQUIRES_SELECTION,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_GRACE_MODE,
            // Cmd/Ctrl-A is the platform shortcut for select-all inside a text field.
            // A disabled menu item doesn't swallow the key, so the lyric editor keeps it.
            Flag.DISABLE_WHEN_EDITING_TEXT,
            // A selected line is the selection this action swaps for a selection of
            // every element on that line.
            Flag.ENABLE_WHEN_LINE_SELECTED
        );
    }

    /**
     * Vetoes the one line selection {@link Flag#ENABLE_WHEN_LINE_SELECTED} would otherwise
     * enable: a line with no elements resolves to no selection at all, so there is nothing
     * to swap the line selection for.
     */
    @Override
    protected boolean enableFromSelectionSize(ScoreView score) {
        var coordinator = score.getSelectionCoordinator();

        return !(coordinator.hasLineSelection() && (coordinator.getSelection() == null))
            && super.enableFromSelectionSize(score);
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new SelectAllElementsCommand());
    }
}
