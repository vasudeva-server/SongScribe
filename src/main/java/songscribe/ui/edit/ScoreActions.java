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

package songscribe.ui.edit;

import org.jspecify.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.StaffElement;

/**
 * Callback interface for ScoreView actions needed by EditModeManager.
 * <p>
 * This interface decouples EditModeManager from ScoreView, allowing it to request
 * ScoreView to perform UI-related actions without creating a circular dependency.
 * Created as part of Phase 6 of the ScoreView Cleanup refactoring.
 */
public interface ScoreActions {

    /**
     * Clears the current selection.
     */
    void clearSelection();

    /**
     * Repaints the score.
     */
    void repaint();

    /**
     * Sets the insertion note.
     *
     * @param element The note to set as the insertion note
     */
    void setPreviewElement(@Nullable StaffElement element);

    /**
     * Adjusts the drawing width if the line is wider than the current width.
     *
     * @param line The line to check
     * @param revalidateOnly Whether to only revalidate without changing width
     */
    void drawWidthIfWiderLine(Line line, boolean revalidateOnly);
}
