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
import javax.swing.JRootPane;

import org.jspecify.annotations.Nullable;

import songscribe.ui.component.MainFrame;

/**
 * An abstract UIAction that does not toggle its selected state off if triggered
 * by a keysroke. Subclasses should call super.doActionPerformed() in their
 * actionPerformed() method, and only process the action if it returns true.
 */
public class StickyUIAction extends SelectableUIAction {

    protected StickyUIAction(
        MainFrame mainFrame,
        String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
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

    // Subclasses should call this method in their actionPerformed() method,
    // and only process the action if it returns true.
    protected boolean doActionPerformed(ActionEvent e) {
        if ((e.getSource() instanceof JRootPane) && isSelected()) {
            // If the action was triggered by a keystroke, we don't want to
            // toggle the selected state off.
            return false;
        }

        toggleOnKeyboardShortcut(e);
        return true;
    }
}
