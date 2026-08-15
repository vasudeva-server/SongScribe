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

import static songscribe.ui.action.Actions.BARLINE_ACTIONS;
import static songscribe.ui.action.Actions.FINAL_DOUBLE_BARLINE_ACTION;

/**
 * The barline palette. The final double barline trails the drawable barlines because it is not
 * one of them: it acts on the song's auto-maintained terminal alone. It sits in the barline popup
 * panel in {@code BarlineToolbar} and in {@code NON_DURATION_ACTION_GROUP} alongside the drawable
 * barlines. What keeps it from acting as a drawing pen is the {@code score.clearSelection()}-
 * before-{@code syncPreviewElementWithSelectedDuration()} ordering in
 * {@code ScoreViewController.modeDidChange}, not its enablement. What confines it to the song's
 * auto-maintained terminal is {@code Flag.REQUIRES_SINGLE_SELECTION} plus its {@code appliesTo}
 * override ({@code Song.isAutoMaintainedTerminalOfItsSong}). The right repeat that is its
 * counterpart for the terminal lives in {@link RepeatsMenu}, where it doubles as an ordinary
 * drawable repeat — see {@code FinalDoubleBarlineAction} (issue #713).
 */
public class BarlineMenu extends JMenu {

    public BarlineMenu() {
        super(Strings.get(Strings.MENU_NOTATION_BARLINES));

        for (var action : BARLINE_ACTIONS) {
            if (action == FINAL_DOUBLE_BARLINE_ACTION) {
                addSeparator();
            }

            add(new JRadioButtonMenuItem(action));
        }
    }
}
