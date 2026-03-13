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

import org.jetbrains.annotations.Nullable;

import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.UpdateInsertionElementMessage;

/**
 * This class is the superclass for all actions that change the insertion element.
 */
public class InsertionElementAction extends SelectableUIAction {

    public InsertionElementAction(String name, String actionCommand) {
        super(name, actionCommand);
    }

    public InsertionElementAction(
        String name,
        String actionCommand,
        int virtualKey,
        int modifiers
    ) {
        super(name, actionCommand, virtualKey, modifiers);
    }

    public InsertionElementAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip
    ) {
        super(name, icon, size, actionCommand, tooltip);
    }

    public InsertionElementAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers
    ) {
        super(
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            virtualKey,
            modifiers
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        toggleOnKeyboardShortcut(e);

        if (applyToSelectionIfActive()) {
            return;
        }

        MessageCenter.post(new UpdateInsertionElementMessage());
    }
}
