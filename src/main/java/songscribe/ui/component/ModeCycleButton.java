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

import static songscribe.ui.action.Actions.CYCLE_MODE_ACTION;
import static songscribe.ui.action.Actions.MODE_ACTION_GROUP;

import javax.swing.DefaultButtonModel;

import net.engio.mbassy.listener.Handler;

import songscribe.message.MessageCenter;
import songscribe.ui.action.ModeAction;
import songscribe.ui.edit.GraceModeManager;
import songscribe.message.notification.GraceModeStateDidChangeNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.ui.playback.PlaybackController;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.util.UIUtils;

/**
 * A button that cycles between Edit Mode and Select Mode on each click,
 * updating its icon and tooltip to reflect the current mode.
 */
public class ModeCycleButton extends ToolbarToggleButton {

    public ModeCycleButton() {
        super(null);
        setName(ComponentNames.MODE_CYCLE_BUTTON);

        // Replace the ToggleButtonModel with a plain DefaultButtonModel so the
        // button has no sticky "selected" state. The selected state would latch
        // on press and leave a persistent highlight when the mouse is released
        // outside the button, because ToggleButtonModel toggles selection on
        // every armed release.
        setModel(new DefaultButtonModel());

        updateButton(CYCLE_MODE_ACTION.getCurrentAction());

        addActionListener(e -> CYCLE_MODE_ACTION.perform(this));

        MessageCenter.subscribe(this);
    }

    @Handler
    public void modeDidChange(ModeDidChangeNotification message) {
        if (!message.isAdjustmentMode()) {
            updateButton(message.getAction());
        }
    }

    @Handler
    public void playbackStateDidChange(PlaybackStateDidChangeNotification message) {
        setEnabled(
            !PlaybackController.isPlaying()
        );
    }

    @Handler
    public void graceModeStateDidChange(GraceModeStateDidChangeNotification message) {
        setEnabled(!GraceModeManager.isActive());
    }

    private void updateButton(ModeAction action) {
        MODE_ACTION_GROUP.setSelected(action, true);
        UIUtils.configureButtonFromAction(this, action);
        setSelected(false);
    }
}
