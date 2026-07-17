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

import songscribe.Strings;
import songscribe.dom.ArticulationType;
import songscribe.ui.component.MainFrame;

public final class ForceArticulationAction extends ArticulationAction {

    public static ForceArticulationAction createAccentAction(MainFrame mainFrame) {
        return new ForceArticulationAction(
            mainFrame,
            ArticulationType.ACCENT,
            Strings.get(Strings.ACTION_ACCENT), "@", 22,
            "accent", Strings.get(Strings.ACTION_ACCENT_TOOLTIP)
        );
    }

    private ForceArticulationAction(
        MainFrame mainFrame,
        ArticulationType articulationType,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip
    ) {
        super(mainFrame, articulationType, name, icon, size, actionCommand, tooltip);
        setUndoOpNameKey(Strings.ACTION_EDIT_OP_TOGGLE_ACCENT);
    }
}
