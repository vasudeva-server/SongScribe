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

import songscribe.message.MessageCenter;
import songscribe.ui.Control;
import songscribe.ui.component.MainFrame;
import songscribe.message.notification.ControlDidChangeNotification;

public final class ControlAction extends SelectableUIAction {

    public final Control control;

    public static ControlAction createMouseControlAction(MainFrame mainFrame) {
        return new ControlAction(mainFrame, Control.MOUSE);
    }

    public static ControlAction createKeyboardControlAction(MainFrame mainFrame) {
        return new ControlAction(mainFrame, Control.KEYBOARD);
    }

    private ControlAction(MainFrame mainFrame, Control control) {
        super(
            mainFrame,
            control.getDescription(),
            null,
            0,
            control.name(),
            "Control editing with " + control.getDescription().toLowerCase()
        );
        this.control = control;
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new ControlDidChangeNotification(control));
    }
}
