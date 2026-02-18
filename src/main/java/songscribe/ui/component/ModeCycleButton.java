/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.component;

import net.engio.mbassy.listener.Handler;

import songscribe.ui.action.ModeAction;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.ModeChangedMessage;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.playback.PlaybackStateChangedMessage;
import songscribe.util.UIUtils;

import static songscribe.ui.action.Actions.CYCLE_MODE_ACTION;
import static songscribe.ui.action.Actions.MODE_ACTION_GROUP;

/**
 * A button that cycles between Edit Mode and Select Mode on each click,
 * updating its icon and tooltip to reflect the current mode.
 */
public class ModeCycleButton extends ToolbarToggleButton {

    public ModeCycleButton() {
        super(null);

        // Replace the ToggleButtonModel with a plain DefaultButtonModel so the
        // button has no sticky "selected" state. The selected state would latch
        // on press and leave a persistent highlight when the mouse is released
        // outside the button, because ToggleButtonModel toggles selection on
        // every armed release.
        setModel(new javax.swing.DefaultButtonModel());

        updateButton(CYCLE_MODE_ACTION.getCurrentAction());

        addActionListener(e -> CYCLE_MODE_ACTION.perform(this));

        MessageCenter.subscribe(this);
    }

    @Handler
    public void modeDidChange(ModeChangedMessage message) {
        if (!message.isAdjustmentMode()) {
            updateButton(message.getAction());
        }
    }

    @Handler
    public void playbackStateDidChange(PlaybackStateChangedMessage message) {
        setEnabled(
            message.getState() != PlaybackController.PlaybackState.PLAYING
        );
    }

    private void updateButton(ModeAction action) {
        MODE_ACTION_GROUP.setSelected(action, true);
        UIUtils.configureButtonFromAction(this, action);
        setSelected(false);
    }
}
