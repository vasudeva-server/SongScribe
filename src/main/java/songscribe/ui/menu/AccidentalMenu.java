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
package songscribe.ui.menu;

import javax.swing.*;

import songscribe.music.StaffElement;
import songscribe.ui.action.Actions;

public class AccidentalMenu extends JMenu {

    public AccidentalMenu() {
        super("Accidental");
        for (var action : Actions.ACCIDENTAL_ACTION_GROUP.getActions()) {
            if (action.getAccidental() == StaffElement.Accidental.NONE) {
                continue;
            }

            add(new JRadioButtonMenuItem(action));
        }

        addSeparator();
        add(new JCheckBoxMenuItem(Actions.ACCIDENTAL_IN_PARENS_ACTION));
    }
}
