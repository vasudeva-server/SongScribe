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

import static java.awt.event.KeyEvent.*;

import songscribe.Strings;
import songscribe.ui.component.LyricEditor;
import songscribe.ui.component.Score;

public final class EditLyricAction extends UIAction {

    public static final Flag[] FLAGS = new Flag[]{
        Flag.REQUIRES_SINGLE_SELECTION,
        Flag.DISABLE_WHEN_PLAYING,
        Flag.DISABLE_WHEN_EDITING_TEXT,
        Flag.DISABLE_IN_ADJUSTMENT_MODE,
        Flag.DISABLE_WHEN_BAR_SELECTED,
        Flag.DISABLE_WHEN_SONG_EMPTY,
        Flag.DISABLE_IN_GRACE_MODE,
    };

    public static EditLyricAction createAction() {
        return new EditLyricAction();
    }

    private EditLyricAction() {
        super(
            Strings.get(Strings.ACTION_EDIT_LYRIC),
            "@\uEF6E", 22,
            "edit-lyric",
            Strings.get(Strings.ACTION_EDIT_LYRIC_TOOLTIP),
            VK_L, META_DOWN_MASK,
            FLAGS
        );
    }

    @Override
    protected boolean enableFromSelection(boolean activeSelection, Score score) {
        var element = score.getSelectionCoordinator().getSingleSelectedElement();

        if (element == null) {
            return false;
        }

        return element.getType().isPitchedNote() || element.getType().isRest();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        var score = requireScore();
        var element = score.getSelectionCoordinator().getSingleSelectedElement();

        if (element == null) {
            throw new IllegalStateException(
                "EditLyricAction fired with no selected element — REQUIRES_SINGLE_SELECTION should have prevented this");
        }

        score.deselect();
        LyricEditor.openOn(score, element.getLine(), element);
    }
}
