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

import java.awt.event.KeyEvent;

import net.engio.mbassy.listener.Handler;

import songscribe.message.notification.UndoStateDidChangeNotification;
import songscribe.ui.component.MainFrame;

/**
 * Shared behavior of {@link UndoAction} and {@link RedoAction}: both are Cmd/Ctrl-Z
 * variants whose label and enabled state mirror one side of the undo engine and are
 * refreshed on every {@link UndoStateDidChangeNotification}. Subclasses supply the
 * direction-specific label and stack query.
 */
abstract class UndoRedoAction extends UIAction {

    UndoRedoAction(MainFrame mainFrame, String initialLabel, String iconName, int acceleratorModifiers) {
        super(
            mainFrame,
            initialLabel,
            iconName,
            KeyEvent.VK_Z,
            acceleratorModifiers,
            Flag.DISABLE_WHEN_PLAYING
        );

        refreshLabelAndState();
    }

    /** The current direction-specific label (e.g. "Undo Add Note"). */
    abstract String currentLabel();

    /** Whether the direction's stack has a step to apply. */
    abstract boolean stackHasStep();

    /**
     * Folds whether the direction's stack has a step on top of the base enabled state
     * (which honors {@link Flag#DISABLE_WHEN_PLAYING}).
     */
    @Override
    public final boolean updateEnabledState() {
        var enabledState = super.updateEnabledState() && stackHasStep();
        setEnabled(enabledState);
        return enabledState;
    }

    @Handler
    public final void undoStateDidChange(UndoStateDidChangeNotification message) {
        refreshLabelAndState();
    }

    private void refreshLabelAndState() {
        setName(currentLabel());
        updateEnabledState();
    }
}
