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

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.MenuElement;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import songscribe.Strings;
import songscribe.ui.action.UIAction;
import songscribe.ui.component.MainFrame;

import static songscribe.ui.action.Actions.AUTO_STEM_DIRECTION_ACTION;
import static songscribe.ui.action.Actions.BREATH_MARK_ACTION;
import static songscribe.ui.action.Actions.DYNAMIC_MARKING_ACTION_GROUP;
import static songscribe.ui.action.Actions.EDIT_LYRIC_ACTION;
import static songscribe.ui.action.Actions.FALL_ACTION;
import static songscribe.ui.action.Actions.FERMATA_ACTION;
import static songscribe.ui.action.Actions.FLIP_STEM_DIRECTION_ACTION;
import static songscribe.ui.action.Actions.GLISSANDO_ACTION;
import static songscribe.ui.action.Actions.HAIRPIN_CRESCENDO_ACTION;
import static songscribe.ui.action.Actions.HAIRPIN_DIMINUENDO_ACTION;
import static songscribe.ui.action.Actions.MAKE_ENDING_ACTION;
import static songscribe.ui.action.Actions.REST_ACTION;
import static songscribe.ui.action.Actions.STAFF_ANNOTATION_ACTIONS;
import static songscribe.ui.action.Actions.TOGGLE_BEAM_ACTION;
import static songscribe.ui.action.Actions.TOGGLE_TIE_ACTION;
import static songscribe.ui.action.Actions.TOGGLE_TUPLET_ACTIONS;
import static songscribe.ui.action.Actions.TRILL_ACTION;

public class NotationMenu extends JMenu {

    public static final String NAME = Strings.get(Strings.MENU_NOTATION);

    public NotationMenu(MainFrame mainFrame) {
        super(NAME);

        // Group 1: Pitch & Rhythm
        add(new DurationMenu());
        add(new JCheckBoxMenuItem(REST_ACTION));
        add(new DotMenu());
        add(new AccidentalMenu());

        addSeparator();

        // Group 2: Spans & Barlines
        add(new RepeatsMenu());
        add(new BarlineMenu());

        addSeparator();

        // Group 3: Articulation & Expression
        add(new ArticulationMenu());
        add(createTupletMenu());
        add(createDynamicsMenu());
        add(new JCheckBoxMenuItem(TRILL_ACTION));
        add(new JCheckBoxMenuItem(FERMATA_ACTION));
        add(new JCheckBoxMenuItem(BREATH_MARK_ACTION));

        addSeparator();

        // Group 4: Display
        add(new JMenuItem(GLISSANDO_ACTION));
        add(new JMenuItem(FALL_ACTION));
        add(new JMenuItem(TOGGLE_BEAM_ACTION));
        add(new JMenuItem(TOGGLE_TIE_ACTION));
        add(createStemDirectionMenu());
        add(new JMenuItem(EDIT_LYRIC_ACTION));

        addSeparator();

        // Group 5: Staff Annotations
        for (var action : STAFF_ANNOTATION_ACTIONS) {
            add(action);
        }

        addSeparator();

        // Group 6: Passage
        add(new JMenuItem(MAKE_ENDING_ACTION));

        disableWhenLineSelected(this);

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

    /**
     * Every action reachable from this menu operates on individual elements, so none of
     * them apply when a whole line is selected. Tagging the built menu tree rather than
     * each action at its construction site keeps the two from drifting apart, and catches
     * the actions that submenus create inline rather than exposing via {@code Actions}.
     */
    private static void disableWhenLineSelected(MenuElement element) {
        if (element instanceof JMenuItem menuItem && menuItem.getAction() instanceof UIAction action) {
            action.setFlags(UIAction.Flag.DISABLE_WHEN_LINE_SELECTED);
        }

        for (var child : element.getSubElements()) {
            disableWhenLineSelected(child);
        }
    }

    private static JMenu createTupletMenu() {
        var menu = new JMenu(Strings.get(Strings.MENU_NOTATION_TUPLET));

        // Radio items with no ButtonGroup: the grade of the selected tuplet is checked via
        // Action.SELECTED_KEY, and a selection with no tuplet leaves all of them unchecked.
        for (var action : TOGGLE_TUPLET_ACTIONS) {
            menu.add(new JRadioButtonMenuItem(action));
        }

        // Which grades the selection could become is only known when the menu opens, so the
        // items above are thrown away and rebuilt then. They are still built here so that
        // disableWhenLineSelected() below finds the actions and tags them.
        menu.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent e) {
                TupletMenuItems.rebuild(menu);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });

        return menu;
    }

    private static JMenu createStemDirectionMenu() {
        var menu = new JMenu(Strings.get(Strings.MENU_NOTATION_STEM_DIRECTION));
        menu.add(new JMenuItem(FLIP_STEM_DIRECTION_ACTION));
        menu.add(new JMenuItem(AUTO_STEM_DIRECTION_ACTION));
        return menu;
    }

    private static JMenu createDynamicsMenu() {
        var menu = new JMenu(Strings.get(Strings.MENU_NOTATION_DYNAMICS));
        menu.add(new JMenuItem(HAIRPIN_CRESCENDO_ACTION));
        menu.add(new JMenuItem(HAIRPIN_DIMINUENDO_ACTION));

        menu.addSeparator();

        for (var action : DYNAMIC_MARKING_ACTION_GROUP.getActions()) {
            menu.add(new JRadioButtonMenuItem(action));
        }

        return menu;
    }
}
