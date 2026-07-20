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

import songscribe.message.MessageCenter;
import songscribe.message.notification.ClipboardDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.ui.component.MainFrame;

public class PasteboardAction extends UIAction {

    public enum Operation {
        COPY,
        CUT,
        DELETE,
        PASTE,
    }

    private final Operation op;

    public PasteboardAction(
        MainFrame mainFrame,
        Operation op,
        String name,
        String actionCommand,
        int virtualKey,
        int modifiers,
        Flag... flags
    ) {
        super(mainFrame, name, actionCommand, virtualKey, modifiers, flags);
        this.op = op;
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        if (updateEnabledState()) {
            switch (op) {
                case PASTE -> setEnabled(
                    message.getScoreView().getPasteboardSize() > 0
                );
                case COPY, CUT -> setEnabled(
                    message.getSelectionSize() > 0
                );
                // DELETE is handled by DeleteAction.updateEnabledState() above.
                case DELETE -> {}
            }
        }
    }

    @Handler
    public void clipboardDidChange(ClipboardDidChangeNotification message) {
        if (updateEnabledState()) {
            switch (op) {
                case PASTE -> {
                    var scoreView = getScoreView();
                    setEnabled(scoreView != null && scoreView.getPasteboardSize() > 0);
                }
                // COPY/CUT are gated by music selection, unaffected by clipboard content.
                // DELETE is handled by DeleteAction.updateEnabledState() above.
                case COPY, CUT, DELETE -> {}
            }
        }
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new PasteboardOpCommand(op));
    }
}
