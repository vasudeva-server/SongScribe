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


import net.engio.mbassy.listener.Handler;

import songscribe.message.MessageCenter;
import songscribe.message.notification.ClipboardDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.ui.component.MainFrame;

public class PasteboardAction extends UIAction {

    public enum Operation {
        COPY,
        CUT,
        DELETE,
        PASTE,
    }

    private final Operation op;

    public PasteboardAction(
        MainFrame mainFrame,
        Operation op,
        String name,
        String actionCommand,
        int virtualKey,
        int modifiers,
        Flag... flags
    ) {
        super(mainFrame, name, actionCommand, virtualKey, modifiers, flags);
        this.op = op;
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        updateEnabledState();
    }

    @Handler
    public void clipboardDidChange(ClipboardDidChangeNotification message) {
        updateEnabledState();
    }

    /**
     * Base {@link UIAction#updateEnabledState()} only accounts for the generic
     * flag-based checks (playback state, grace mode, etc.), and is called directly by
     * several unrelated {@code @Handler} methods in the base class (e.g. song/document
     * load). Without this override, those calls would force-enable COPY/CUT/PASTE
     * regardless of selection or clipboard content, since none of them declare a
     * {@code REQUIRES_SELECTION}-style flag.
     */
    @Override
    public final boolean updateEnabledState() {
        if (!super.updateEnabledState()) {
            return false;
        }

        var isEnabled = updateScoreEnabledState();
        setEnabled(isEnabled);
        return isEnabled;
    }

    /** Computes the enabled state from the score, once the generic flag checks pass. */
    protected boolean updateScoreEnabledState() {
        var scoreView = requireScoreView();

        return switch (op) {
            case PASTE -> scoreView.getPasteboardSize() > 0;
            case COPY, CUT -> scoreView.getSelectionSize() > 0;
            // DELETE is handled by DeleteAction.updateScoreEnabledState() above.
            case DELETE -> true;
        };
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new PasteboardOpCommand(op));
    }
}
