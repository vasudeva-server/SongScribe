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

import songscribe.hit.HitTarget;
import songscribe.message.Message;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.ScoreViewController;

public class MusicSelectionDidChangeNotification extends Message {

    private final int selectionSize;
    private final HitTarget.@Nullable Selectable selectedTarget;
    private final ScoreView scoreView;

    public MusicSelectionDidChangeNotification(ScoreView scoreView) {
        selectionSize = scoreView.getSelectionSize();
        selectedTarget = scoreView.getSelectionCoordinator().getSelectedTarget();
        this.scoreView = scoreView;
    }

    public int getSelectionSize() {
        return selectionSize;
    }

    public boolean hasLyricSelection() {
        return selectedTarget instanceof HitTarget.Lyric;
    }

    /**
     * Returns the score's single selected target when the message was posted, or null if
     * nothing was selected by identity.
     */
    public HitTarget.@Nullable Selectable getSelectedTarget() {
        return selectedTarget;
    }

    public ScoreView getScoreView() {
        return scoreView;
    }

    public @Nullable ScoreViewController getScoreViewController() {
        return scoreView.getController();
    }
}
