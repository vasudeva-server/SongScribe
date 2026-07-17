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

import songscribe.Strings;
import songscribe.message.MessageCenter;
import songscribe.message.command.AddDynamicsCommand;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.component.MainFrame;

public final class AddDynamicsAction extends UIAction {

    private final boolean isCrescendo;

    public static AddDynamicsAction createCrescendoAction(MainFrame mainFrame) {
        return new AddDynamicsAction(mainFrame, true);
    }

    public static AddDynamicsAction createDiminuendoAction(MainFrame mainFrame) {
        return new AddDynamicsAction(mainFrame, false);
    }

    private AddDynamicsAction(MainFrame mainFrame, boolean isCrescendo) {
        super(
            mainFrame,
            Strings.get(isCrescendo ? Strings.ACTION_DYNAMICS_CRESCENDO : Strings.ACTION_DYNAMICS_DIMINUENDO),
            null,
            0,
            isCrescendo ? "add-crescendo" : "add-diminuendo",
            Strings.get(isCrescendo ? Strings.ACTION_DYNAMICS_CRESCENDO_TOOLTIP : Strings.ACTION_DYNAMICS_DIMINUENDO_TOOLTIP),
            Flag.REQUIRES_MULTIPLE_SELECTION,
            Flag.DISABLE_IN_REST_MODE,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_IN_GRACE_MODE
        );
        this.isCrescendo = isCrescendo;
        setUndoOpNameKey(isCrescendo ? Strings.ACTION_EDIT_OP_ADD_CRESCENDO : Strings.ACTION_EDIT_OP_ADD_DIMINUENDO);
    }

    public boolean isCrescendo() {
        return isCrescendo;
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        var ctrl = message.getScoreViewController();

        if (ctrl != null && updateEnabledState()) {
            setEnabled(ctrl.canAddDynamicsToSelection());
        }
    }

    @Override
    protected void performAction(ActionEvent e) {
        MessageCenter.post(new AddDynamicsCommand(isCrescendo));
    }
}
