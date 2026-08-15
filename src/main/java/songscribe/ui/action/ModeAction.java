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

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.ui.Mode;
import songscribe.ui.component.MainFrame;

public final class ModeAction extends SelectableUIAction {

    private final Mode mode;

    public static ModeAction createSelectModeAction(MainFrame mainFrame) {
        return new ModeAction(
            mainFrame,
            Mode.SELECT,
            Strings.get(Strings.ACTION_MODE_SELECT), "@\uF3D2", 22,
            "select-mode", Strings.get(Strings.ACTION_MODE_SELECT_TOOLTIP)
        );
    }

    public static ModeAction createEditModeAction(MainFrame mainFrame) {
        return new ModeAction(
            mainFrame,
            Mode.EDIT,
            Strings.get(Strings.ACTION_MODE_EDIT), "@\uEF63", 22,
            "edit-mode", Strings.get(Strings.ACTION_MODE_EDIT_TOOLTIP)
        );
    }

    private ModeAction(
        MainFrame mainFrame,
        Mode mode,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip
    ) {
        this(mainFrame, mode, name, icon, size, actionCommand, tooltip, 0, 0);
    }

    private ModeAction(
        MainFrame mainFrame,
        Mode mode,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers
    ) {
        super(
            mainFrame,
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            virtualKey,
            modifiers,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    @Override
    protected void performAction(ActionEvent e) {
        toggleOnKeyboardShortcut(e);
        MessageCenter.post(new ModeDidChangeNotification(this));
    }
}
