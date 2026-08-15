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

import songscribe.message.Message;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.ui.component.MainFrame;

/**
 * An action that toggles between Edit Mode and Select Mode on each invocation.
 * Owns the 'M' accelerator key, so pressing 'M' is equivalent to clicking
 * the mode cycle button in the toolbar.
 */
public final class CycleModeAction extends UIAction {

    private ModeAction currentAction;

    public static CycleModeAction createAction(MainFrame mainFrame) {
        return new CycleModeAction(mainFrame);
    }

    private CycleModeAction(MainFrame mainFrame) {
        super(mainFrame, null, "cycle-mode", KeyEvent.VK_M, 0, Flag.DISABLE_WHEN_EDITING_TEXT, Flag.DISABLE_IN_GRACE_MODE);
        currentAction = Actions.EDIT_MODE_ACTION;
    }

    public ModeAction getCurrentAction() {
        return currentAction;
    }

    @Override
    protected void performAction(ActionEvent e) {
        // Update before performing rather than relying on the notification the perform
        // posts: callers that mock the message bus never deliver it back to this handler.
        currentAction = otherAction();
        currentAction.perform(e.getSource());
    }

    @Handler(priority = Message.MEDIUM_PRIORITY)
    @Override
    public void modeDidChange(ModeDidChangeNotification message) {
        super.modeDidChange(message);

        currentAction = message.getAction();
    }

    /**
     * Returns the mode action this one toggles to. Reads {@link Actions} live rather than
     * caching the pair, so the two mode actions can be recreated without stranding a
     * stale reference here.
     */
    private ModeAction otherAction() {
        return currentAction == Actions.EDIT_MODE_ACTION
            ? Actions.SELECT_MODE_ACTION
            : Actions.EDIT_MODE_ACTION;
    }
}
