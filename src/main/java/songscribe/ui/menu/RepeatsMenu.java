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

import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;

import songscribe.Strings;

import static songscribe.ui.action.Actions.REPEAT_ACTIONS;

public class RepeatsMenu extends JMenu {

    public RepeatsMenu() {
        super(Strings.get(Strings.MENU_NOTATION_REPEATS));

        for (var action : REPEAT_ACTIONS) {
            add(new JRadioButtonMenuItem(action));
        }
    }
}
