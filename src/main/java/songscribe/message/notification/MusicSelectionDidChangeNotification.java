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

package songscribe.message.notification;

import org.jspecify.annotations.Nullable;

import songscribe.message.Message;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;
import songscribe.ui.selection.SelectionCoordinator.LyricSelection;

public class MusicSelectionDidChangeNotification extends Message {

    private final int selectionSize;
    private final boolean hasGlissandoSelection;
    @Nullable
    private final LyricSelection lyricSelection;
    private final ScoreView score;

    public MusicSelectionDidChangeNotification(ScoreView score) {
        selectionSize = score.getSelectionSize();
        hasGlissandoSelection = score.getSelectionCoordinator().hasGlissandoSelection();
        lyricSelection = score.getSelectionCoordinator().getLyricSelection();
        this.score = score;
    }

    public int getSelectionSize() {
        return selectionSize;
    }

    public boolean hasGlissandoSelection() {
        return hasGlissandoSelection;
    }

    public boolean hasLyricSelection() {
        return lyricSelection != null;
    }

    public @Nullable LyricSelection getLyricSelection() {
        return lyricSelection;
    }

    public ScoreView getScore() {
        return score;
    }

    public @Nullable ScoreViewController getScoreViewController() {
        return score.getMessageCoordinator();
    }
}
