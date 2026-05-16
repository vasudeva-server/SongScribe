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
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.ui.dialog.TempoChangeDialog;

public final class TempoChangeAction extends UIAction {

    public static TempoChangeAction createAction() {
        return new TempoChangeAction();
    }

    private TempoChangeAction() {
        super(
            Strings.get(Strings.ACTION_TEMPO_CHANGE),
            null,
            0,
            "tempo-change",
            Strings.get(Strings.ACTION_TEMPO_CHANGE_TOOLTIP),
            Flag.REQUIRES_SINGLE_SELECTION,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_IN_GRACE_MODE,
            Flag.OPENS_DIALOG
        );
    }

    @Override
    @Handler
    public void musicSelectionDidChange(
        MusicSelectionDidChangeNotification message
    ) {
        var ctrl = message.getScoreViewController();

        if (ctrl != null && updateEnabledState()) {
            setEnabled(ctrl.canChangeTempo());
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        new TempoChangeDialog().setVisible(true);
    }
}
