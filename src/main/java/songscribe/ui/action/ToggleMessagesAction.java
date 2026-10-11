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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * Shows the Messages panel when it is hidden, and hides it when it is shown. Keyboard focus does
 * not move. The name says what the next invocation does ("Show Messages" or "Hide Messages");
 * {@link MainFrame} calls {@link #setPanelShown} each time the panel is shown or hidden.
 */
public final class ToggleMessagesAction extends UIAction {

    public static ToggleMessagesAction createAction(MainFrame mainFrame) {
        return new ToggleMessagesAction(mainFrame);
    }

    // No DISABLE_WHEN_EDITING_TEXT: it does not move focus, so an in-place text edit goes on.
    private ToggleMessagesAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_WINDOW_MESSAGES_SHOW),
            "window-messages",
            KeyEvent.VK_M, UIUtils.MENU_SHORTCUT_MASK | InputEvent.SHIFT_DOWN_MASK,
            // Shows and hides the panel only; the document is untouched.
            Flag.ENABLED_IN_GRACE_MODE
        );
    }

    /**
     * Sets the name to match the panel.
     *
     * @param shown whether the Messages panel is shown
     * @effects names the action "Hide Messages" when shown, "Show Messages" otherwise
     */
    public void setPanelShown(boolean shown) {
        setName(Strings.get(shown ? Strings.ACTION_WINDOW_MESSAGES_HIDE : Strings.ACTION_WINDOW_MESSAGES_SHOW));
    }

    @Override
    protected void performAction(ActionEvent e) {
        var mainFrame = getMainFrame();

        if (mainFrame.isMessagesPanelVisible()) {
            mainFrame.hideMessagesPanel();
        } else {
            mainFrame.showMessagesPanel();
        }
    }
}
