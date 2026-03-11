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

import net.engio.mbassy.listener.Handler;

import songscribe.ui.message.Message;
import songscribe.ui.message.ModeChangedMessage;

/**
 * An action that cycles between Edit Mode and Select Mode on each invocation.
 * Owns the 'E' accelerator key, so pressing 'E' is equivalent to clicking
 * the mode cycle button in the toolbar.
 */
public class CycleModeAction extends UIAction {

    static final ModeAction[] MODES = {
        Actions.EDIT_MODE_ACTION,
        Actions.SELECT_MODE_ACTION,
    };

    private int currentIndex;

    public CycleModeAction() {
        super(null, "cycle-mode", KeyEvent.VK_E, 0);
        setFlags(Flag.DISABLE_WHEN_EDITING_TEXT, Flag.DISABLE_IN_GRACE_MODE);
        currentIndex = 0;
    }

    public ModeAction getCurrentAction() {
        return MODES[currentIndex];
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        currentIndex = (currentIndex + 1) % MODES.length;
        MODES[currentIndex].perform(e.getSource());
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    @Override
    public void modeDidChange(ModeChangedMessage message) {
        super.modeDidChange(message);

        if (message.isAdjustmentMode()) {
            return;
        }

        var action = message.getAction();

        for (var i = 0; i < MODES.length; i++) {
            if (MODES[i] == action) {
                currentIndex = i;
                return;
            }
        }
    }
}
