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

import java.awt.event.*;

import org.jetbrains.annotations.NotNull;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.ui.dialog.KeySignatureChangeDialog;
import songscribe.ui.message.Message;
import songscribe.ui.message.MusicSelectionChangedMessage;

public class KeySignatureChangeAction extends UIAction {

    public KeySignatureChangeAction() {
        super(
            Strings.get(Strings.ACTION_KEY_SIGNATURE_CHANGE),
            null,
            0,
            "key-signature-change",
            Strings.get(Strings.ACTION_KEY_SIGNATURE_CHANGE_TOOLTIP)
        );
        setFlags(
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_IN_GRACE_MODE
        );
    }

    @Override
    @Handler(priority = Message.MEDIUM_PRIORITY)
    public void musicSelectionDidChange(
        @NotNull MusicSelectionChangedMessage message
    ) {
        if (updateEnabledState()) {
            // Key signature changes can only be made when a line is selected
            setEnabled(message.getScore().getSelectedLine() != -1);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new KeySignatureChangeDialog().setVisible(true);
    }
}
