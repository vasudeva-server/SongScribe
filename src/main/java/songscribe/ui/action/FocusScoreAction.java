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

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import songscribe.Strings;
import songscribe.ui.component.MainFrame;
import songscribe.util.UIUtils;

/**
 * Moves keyboard focus to the score. {@link MainFrame#syncScoreAction()} keeps its selected state.
 */
public final class FocusScoreAction extends SelectableUIAction {

    public static FocusScoreAction createAction(MainFrame mainFrame) {
        return new FocusScoreAction(mainFrame);
    }

    // No DISABLE_WHEN_EDITING_TEXT: the in-place lyric editor commits when it loses focus, or
    // keeps focus when the lyric does not fit, so moving focus never leaves an edit half done.
    private FocusScoreAction(MainFrame mainFrame) {
        super(
            mainFrame,
            Strings.get(Strings.ACTION_WINDOW_SCORE),
            "window-score",
            KeyEvent.VK_0, UIUtils.MENU_SHORTCUT_MASK,
            // Moves focus only; the document is untouched.
            Flag.ENABLED_IN_GRACE_MODE
        );
    }

    @Override
    protected void performAction(ActionEvent e) {
        var mainFrame = getMainFrame();
        mainFrame.focusScore();
        mainFrame.syncScoreAction();
    }
}
