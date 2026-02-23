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

import org.jetbrains.annotations.Nullable;

import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.UpdateInsertionNoteMessage;

/**
 * This class is the superclass for all actions that change the insertion note.
 */
public class InsertionNoteAction extends UIAction {

    public InsertionNoteAction(String name, String actionCommand) {
        super(name, actionCommand);
    }

    public InsertionNoteAction(
        String name,
        String actionCommand,
        int virtualKey,
        int modifiers
    ) {
        super(name, actionCommand, virtualKey, modifiers);
    }

    public InsertionNoteAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip
    ) {
        super(name, icon, size, actionCommand, tooltip);
    }

    public InsertionNoteAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        boolean isToggle
    ) {
        super(name, icon, size, actionCommand, tooltip, isToggle);
    }

    public InsertionNoteAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        boolean isToggle,
        int virtualKey,
        int modifiers
    ) {
        super(
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            isToggle,
            virtualKey,
            modifiers
        );
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        super.actionPerformed(e);
        MessageCenter.post(new UpdateInsertionNoteMessage());
    }
}
