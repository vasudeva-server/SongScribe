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

import songscribe.Strings;
import songscribe.util.UIUtils;

public final class DeleteAction extends PasteboardAction {

    public static DeleteAction createAction() {
        return new DeleteAction();
    }

    private DeleteAction() {
        super(Operation.DELETE, Strings.get(Strings.ACTION_EDIT_DELETE), "edit-delete", 0, 0, Flag.DISABLE_WHEN_PLAYING);
        var keystrokes = new KeyStroke[] {
            KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
            KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0),
            KeyStroke.getKeyStroke(
                KeyEvent.VK_BACK_SPACE,
                UIUtils.MENU_SHORTCUT_MASK
            ),
        };

        var rootPane = getMainFrame().getRootPane();

        for (var keystroke : keystrokes) {
            UIUtils.registerActionKeystroke(rootPane, keystroke, this);
        }
    }

    @Override
    public boolean updateEnabledState() {
        if (!super.updateEnabledState()) {
            return false;
        }

        var scoreView = requireScoreView();
        var selection = scoreView.getSelectionCoordinator();
        var isEnabled =
            selection.hasLyricSelection() ||
                selection.hasActiveSelection() ||
                selection.hasGlissandoSelection() ||
                scoreView.canDeleteLine();
        setEnabled(isEnabled);
        return isEnabled;
    }
}
