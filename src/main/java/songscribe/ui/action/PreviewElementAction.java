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

import org.jspecify.annotations.Nullable;

import songscribe.message.MessageCenter;
import songscribe.message.command.UpdatePreviewElementCommand;
import songscribe.ui.component.MainFrame;

/**
 * This class is the superclass for all actions that change the preview element.
 */
public class PreviewElementAction extends SelectableUIAction {

    public PreviewElementAction(MainFrame mainFrame, String name, String actionCommand, Flag... flags) {
        super(mainFrame, name, actionCommand, flags);
    }

    public PreviewElementAction(
        MainFrame mainFrame,
        String name,
        String actionCommand,
        int virtualKey,
        int modifiers,
        Flag... flags
    ) {
        super(mainFrame, name, actionCommand, virtualKey, modifiers, flags);
    }

    public PreviewElementAction(
        MainFrame mainFrame,
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        @Nullable String tooltip,
        Flag... flags
    ) {
        super(mainFrame, name, icon, size, actionCommand, tooltip, flags);
    }

    public PreviewElementAction(
        MainFrame mainFrame,
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        @Nullable String tooltip,
        int virtualKey,
        int modifiers,
        Flag... flags
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
            flags
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        toggleOnKeyboardShortcut(e);

        if (applyToSelectionIfActive()) {
            return;
        }

        MessageCenter.post(new UpdatePreviewElementCommand(UpdatePreviewElementCommand.Scope.DECORATIONS));
    }
}
