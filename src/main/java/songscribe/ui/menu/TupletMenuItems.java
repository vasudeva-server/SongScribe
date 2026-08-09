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
import songscribe.ui.component.MainFrame;

/**
 * Builds the grade items shared by the Notation &gt; Tuplet submenu and the toolbar tuplet
 * popup.
 * <p>
 * A grade the selection cannot become is left out entirely rather than shown disabled, so
 * the list only ever offers choices that would do something. When no grade is left, a
 * single disabled item says so. The grade of the tuplet the selection already carries is
 * always listed, even when the span could not be turned into that grade again — otherwise
 * a selection that visibly is a tuplet would show nothing checked.
 */
public final class TupletMenuItems {

    /** Sentinel for "the selection carries no tuplet", matching no real grade. */
    private static final int NO_GRADE = -1;

    private TupletMenuItems() {
    }

    /**
     * Replaces the container's items with the ones the current selection allows. The
     * container is a {@link JMenu} or a {@link JPopupMenu}; both route {@code add} and
     * {@code removeAll} to the same popup contents.
     */
    public static void rebuild(Container container) {
        container.removeAll();

        var existingGrade = existingGrade();
        var addedGrade = false;

        // Radio items with no ButtonGroup: the grade of the selected tuplet is checked via
        // Action.SELECTED_KEY, and a selection with no tuplet leaves all of them unchecked.
        for (var action : Actions.TOGGLE_TUPLET_ACTIONS) {
            if (action.isEnabled() || (action.getTuplet().getSize() == existingGrade)) {
                container.add(new JRadioButtonMenuItem(action));
                addedGrade = true;
            }
        }

        if (!addedGrade) {
            var noneItem = new JMenuItem(Strings.get(Strings.MENU_NOTATION_TUPLET_NONE));
            noneItem.setEnabled(false);
            container.add(noneItem);
        }
    }

    private static int existingGrade() {
        var scoreView = MainFrame.getInstance().getScoreView();
        var ctrl = (scoreView != null) ? scoreView.getController() : null;

        if (ctrl == null) {
            return NO_GRADE;
        }

        var existing = ctrl.canToggleTuplet().existing();
        return (existing != null) ? existing.getGrade() : NO_GRADE;
    }
}
