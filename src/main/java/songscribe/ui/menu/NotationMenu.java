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

import static songscribe.ui.action.Actions.ADD_CRESCENDO_ACTION;
import static songscribe.ui.action.Actions.ADD_DIMINUENDO_ACTION;
import static songscribe.ui.action.Actions.FLIP_STEM_DIRECTION_ACTION;
import static songscribe.ui.action.Actions.REMOVE_DYNAMICS_ACTION;
import static songscribe.ui.action.Actions.REMOVE_TUPLET_ACTION;
import static songscribe.ui.action.Actions.TOGGLE_BEAM_ACTION;
import static songscribe.ui.action.Actions.TOGGLE_TIE_ACTION;
import static songscribe.ui.action.Actions.TOGGLE_TUPLET_ACTIONS;

import javax.swing.*;

import songscribe.Strings;
import songscribe.ui.action.FirstSecondEndingAction;
import songscribe.ui.action.ToggleLyricsUnderRestsAction;
import songscribe.ui.action.ToggleTrillAction;
import songscribe.ui.message.MessageCenter;

public class NotationMenu extends JMenu {

    public static final String NAME = Strings.get(Strings.MENU_NOTATION);

    public NotationMenu() {
        super(NAME);
        add(new JMenuItem(TOGGLE_BEAM_ACTION));
        add(new JMenuItem(TOGGLE_TIE_ACTION));
        add(createTupletMenu());
        add(new JMenuItem(new ToggleTrillAction()));

        addSeparator();

        add(createDynamicsMenu());
        add(new FirstSecondEndingAction(true));
        add(new FirstSecondEndingAction(false));

        addSeparator();

        add(new JMenuItem(FLIP_STEM_DIRECTION_ACTION));
        add(new ToggleLyricsUnderRestsAction());

        MessageCenter.subscribe(this);
    }

    private static JMenu createTupletMenu() {
        var menu = new JMenu(Strings.get(Strings.MENU_NOTATION_TUPLET));

        for (var action : TOGGLE_TUPLET_ACTIONS) {
            menu.add(new JMenuItem(action));
        }

        menu.addSeparator();
        menu.add(REMOVE_TUPLET_ACTION);
        return menu;
    }

    private static JMenu createDynamicsMenu() {
        var menu = new JMenu(Strings.get(Strings.MENU_NOTATION_DYNAMICS));
        menu.add(new JMenuItem(ADD_CRESCENDO_ACTION));
        menu.add(new JMenuItem(ADD_DIMINUENDO_ACTION));
        menu.add(new JMenuItem(REMOVE_DYNAMICS_ACTION));
        return menu;
    }
}
