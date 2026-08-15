/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.action;

import java.awt.event.ActionEvent;

import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.AboutDialog;

import static songscribe.util.StringUtils.toKebabCase;

/**
 * Opens the About window.
 *
 * <p>Not a {@link DialogOpenAction}: that caches one {@code BaseDialog} instance and reopens
 * it, whereas {@link AboutDialog} disposes itself on dismissal and is built fresh each time.
 */
public class AboutOpenAction extends UIAction implements UIAction.AppMenuAction {

    private static final String NATIVE_MENU_TITLE = "About";

    public AboutOpenAction(MainFrame mainFrame) {
        this(mainFrame, Strings.get(Strings.ACTION_ABOUT));
    }

    // The action command is derived from the name, so the name has to be computed
    // before super() rather than inline in the public constructor.
    private AboutOpenAction(MainFrame mainFrame, String name) {
        super(mainFrame, name, toKebabCase(name), Flag.OPENS_DIALOG);
    }

    @Override
    public String getNativeMenuTitle() {
        return NATIVE_MENU_TITLE;
    }

    @Override
    protected void performAction(ActionEvent e) {
        AboutDialog.show(getMainFrame());
    }
}
