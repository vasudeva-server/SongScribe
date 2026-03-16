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

import songscribe.Strings;
import songscribe.ui.message.AddDynamicsMessage;
import songscribe.message.MessageCenter;

public class AddDynamicsAction extends UIAction {

    private final boolean isCrescendo;

    public AddDynamicsAction(boolean isCrescendo) {
        super(
            Strings.get(isCrescendo ? Strings.ACTION_DYNAMICS_CRESCENDO : Strings.ACTION_DYNAMICS_DIMINUENDO),
            null,
            0,
            isCrescendo ? "add-crescendo" : "add-diminuendo",
            Strings.get(isCrescendo ? Strings.ACTION_DYNAMICS_CRESCENDO_TOOLTIP : Strings.ACTION_DYNAMICS_DIMINUENDO_TOOLTIP)
        );
        this.isCrescendo = isCrescendo;
        setFlags(
            Flag.REQUIRES_MULTIPLE_SELECTION,
            Flag.DISABLE_IN_REST_MODE,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_WHEN_EDITING_TEXT,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_IN_GRACE_MODE
        );
    }

    public boolean isCrescendo() {
        return isCrescendo;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        MessageCenter.post(new AddDynamicsMessage(isCrescendo));
    }
}
