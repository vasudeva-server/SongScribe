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

import songscribe.ui.dialog.TempoChangeDialog;
import songscribe.ui.message.MusicSelectionChangedMessage;

public class TempoChangeAction extends UIAction {

    public TempoChangeAction() {
        super(
            "Tempo Change...",
            null,
            0,
            "tempo-change",
            "Insert tempo change"
        );
        setFlags(
            Flag.REQUIRES_SINGLE_SELECTION,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE
        );
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        @NotNull MusicSelectionChangedMessage message
    ) {
        if (updateEnabledState()) {
            setEnabled(message.getScore().canChangeTempo());
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new TempoChangeDialog().setVisible(true);
    }
}
