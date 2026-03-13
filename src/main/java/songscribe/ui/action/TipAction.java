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

import java.io.IOException;

import songscribe.Strings;
import songscribe.ui.Dialogs;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.TipFrame;

public class TipAction extends AbstractAction {

    private final MainFrame mainFrame;

    public TipAction(MainFrame mainFrame) {
        putValue(Action.NAME, Strings.get(Strings.ACTION_TIP));
        this.mainFrame = mainFrame;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            new TipFrame(mainFrame);
        } catch (IOException e1) {
            Dialogs.showErrorMessage(
                mainFrame,
                Strings.get(Strings.DIALOG_TITLE_TIP_ERROR),
                Strings.get(Strings.ERROR_TIP_READ)
            );
        }
    }
}
