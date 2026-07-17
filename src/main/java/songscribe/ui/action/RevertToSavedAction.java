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

import module java.desktop;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.RevertToSavedCommand;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.ui.component.MainFrame;

public final class RevertToSavedAction extends UIAction {

    public static RevertToSavedAction createAction(MainFrame mainFrame) {
        return new RevertToSavedAction(mainFrame);
    }

    private RevertToSavedAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_FILE_REVERT_TO_SAVED),
            "revert-to-saved",
            Flag.DISABLE_WHEN_PLAYING
        );
    }

    // Not flag-driven: enabled only when there is a saved file on disk to revert to
    // and the document has unsaved changes to discard.
    @Override
    public boolean updateEnabledState() {
        var enabled = super.updateEnabledState() &&
            getMainFrame().getCurrentFile() != null &&
            requireScoreView().getSong().isModified();
        setEnabled(enabled);
        return enabled;
    }

    // Saving clears the modified flag but posts no SongDidChangeNotification, so the
    // inherited handlers never re-run; listen for the save directly to disable the action.
    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void documentWasSaved(DocumentWasSavedNotification message) {
        updateEnabledState();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new RevertToSavedCommand());
    }
}
