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

import org.jetbrains.annotations.NotNull;

import net.engio.mbassy.listener.Handler;

import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.MusicSelectionChangedMessage;
import songscribe.ui.message.PasteboardOpMessage;

public class PasteboardAction extends UIAction {

    public enum Operation {
        COPY,
        CUT,
        DELETE,
        PASTE,
    }

    private final Operation op;

    public PasteboardAction(
        Operation op,
        String name,
        String actionCommand,
        int virtualKey,
        int modifiers
    ) {
        super(name, actionCommand, virtualKey, modifiers);
        this.op = op;
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        @NotNull MusicSelectionChangedMessage message
    ) {
        if (updateEnabledState()) {
            switch (op) {
                case PASTE -> setEnabled(
                    message.getScore().getPasteboardSize() > 0
                );
                // Allow lines to be deleted
                case DELETE -> setEnabled(
                    (message.getSelectionSize() > 0) ||
                    message.hasGlissandoSelection() ||
                    (message.getScore().canDeleteLine())
                );
                case null, default -> setEnabled(
                    message.getSelectionSize() > 0
                );
            }
        }
    }

    // TODO: Handle text selection changes

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new PasteboardOpMessage(op));
    }
}
