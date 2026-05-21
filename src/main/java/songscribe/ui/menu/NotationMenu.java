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
import static songscribe.ui.action.Actions.EDIT_LYRIC_ACTION;
import static songscribe.ui.action.Actions.BREATH_MARK_ACTION;
import static songscribe.ui.action.Actions.DYNAMIC_MARKING_ACTION_GROUP;
import static songscribe.ui.action.Actions.FERMATA_ACTION;
import static songscribe.ui.action.Actions.FLIP_STEM_DIRECTION_ACTION;
import static songscribe.ui.action.Actions.MAKE_ENDING_ACTION;
import static songscribe.ui.action.Actions.REMOVE_DYNAMICS_ACTION;
import static songscribe.ui.action.Actions.REMOVE_TUPLET_ACTION;
import static songscribe.ui.action.Actions.REST_ACTION;
import static songscribe.ui.action.Actions.STAFF_ANNOTATION_ACTIONS;
import static songscribe.ui.action.Actions.TOGGLE_BEAM_ACTION;
import static songscribe.ui.action.Actions.TOGGLE_TIE_ACTION;
import static songscribe.ui.action.Actions.TOGGLE_TRILL_ACTION;
import static songscribe.ui.action.Actions.TOGGLE_TUPLET_ACTIONS;

import module java.desktop;

import songscribe.Strings;
import songscribe.ui.component.MainFrame;

public class NotationMenu extends JMenu {

    public static final String NAME = Strings.get(Strings.MENU_NOTATION);

    private final MainFrame mainFrame;

    public NotationMenu(MainFrame mainFrame) {
        super(NAME);

        this.mainFrame = mainFrame;

        // Group 1: Pitch & Rhythm
        add(new DurationMenu());
        add(new JCheckBoxMenuItem(REST_ACTION));
        add(new DotMenu());
        add(new AccidentalMenu());

        addSeparator();

        // Group 2: Spans & Barlines
        add(new GlissandoMenu());
        add(new RepeatsMenu());
        add(new BarlineMenu(mainFrame));

        addSeparator();

        // Group 3: Articulation & Expression
        add(new ArticulationMenu());
        add(createTupletMenu());
        add(createDynamicsMenu());
        add(new JCheckBoxMenuItem(TOGGLE_TRILL_ACTION));
        add(new JCheckBoxMenuItem(FERMATA_ACTION));
        add(new JCheckBoxMenuItem(BREATH_MARK_ACTION));

        addSeparator();

        // Group 4: Display
        add(new JMenuItem(TOGGLE_BEAM_ACTION));
        add(new JMenuItem(TOGGLE_TIE_ACTION));
        add(new JMenuItem(FLIP_STEM_DIRECTION_ACTION));
        add(new JMenuItem(EDIT_LYRIC_ACTION));

        addSeparator();

        // Group 5: Staff Annotations
        for (var action : STAFF_ANNOTATION_ACTIONS) {
            add(action);
        }

        addSeparator();

        // Group 6: Passage
        add(new JMenuItem(MAKE_ENDING_ACTION));

        addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                var scoreView = mainFrame.getScoreView();
                var ctrl = scoreView != null ? scoreView.getController() : null;

                if (ctrl != null) {
                    MAKE_ENDING_ACTION.validate(ctrl);
                } else {
                    MAKE_ENDING_ACTION.setEnabled(false);
                }
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
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

        menu.addSeparator();

        for (var action : DYNAMIC_MARKING_ACTION_GROUP.getActions()) {
            menu.add(new JRadioButtonMenuItem(action));
        }

        return menu;
    }
}
