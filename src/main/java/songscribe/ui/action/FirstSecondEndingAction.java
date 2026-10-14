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

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.command.FirstSecondEndingCommand;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreViewController;

/**
 * Makes a first/second ending from the selection. The command judges the selection again when
 * it runs, so it acts on the selection as it is then.
 */
public final class FirstSecondEndingAction extends UIAction {

    FirstSecondEndingAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_ENDING_MAKE),
            "first-second-ending",
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT
        );
        setUndoOpNameKey(Strings.ACTION_EDIT_OP_MAKE_ENDING);
    }

    public void validate(ScoreViewController ctrl) {
        if (!ctrl.canMakeFirstSecondEnding()) {
            setEnabled(false);
            return;
        }

        // Validity alone doesn't imply enablement, so defer to the standard chain.
        updateEnabledState();
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new FirstSecondEndingCommand());
    }
}
