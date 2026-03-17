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

import module java.desktop;

import songscribe.Strings;
import songscribe.ui.action.Actions;
import songscribe.ui.action.InsertLineAction;


public final class InsertMenu extends JMenu {

    private static final InsertMenu instance = new InsertMenu();

    public static InsertMenu getInstance() {
        return instance;
    }

    private InsertMenu() {
        super(Strings.get(Strings.MENU_INSERT));
        add(new DurationMenu());
        add(new JCheckBoxMenuItem(Actions.REST_ACTION));
        add(new RepeatsMenu());
        add(new BarlineMenu());

        addSeparator();

        add(new DotMenu());
        add(new AccidentalMenu());
        add(new ArticulationMenu());
        add(new JCheckBoxMenuItem(Actions.FERMATA_ACTION));
        add(new JRadioButtonMenuItem(Actions.BREATH_MARK_ACTION));

        addSeparator();

        add(Actions.TEMPO_CHANGE_ACTION);
        add(Actions.BEAT_CHANGE_ACTION);
        add(Actions.ANNOTATION_ACTION);
        add(Actions.KEY_SIGNATURE_CHANGE_ACTION);

        addSeparator();

        var lineMenu = new JMenu(Strings.get(Strings.MENU_INSERT_LINE));
        lineMenu.add(InsertLineAction.createAddLineAction());
        lineMenu.add(InsertLineAction.createInsertLineBeforeAction());
        lineMenu.add(InsertLineAction.createInsertLineAfterAction());
        add(lineMenu);
    }
}
