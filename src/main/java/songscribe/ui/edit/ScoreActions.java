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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.Control;

/**
 * Callback interface for Score actions needed by EditModeManager.
 * <p>
 * This interface decouples EditModeManager from Score, allowing it to request
 * Score to perform UI-related actions without creating a circular dependency.
 * Created as part of Phase 6 of the Score Cleanup refactoring.
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
     * @param insertionNote The note to set as the insertion note
     */
    void setInsertionNote(@Nullable Note insertionNote);

    /**
     * Adjusts the drawing width if the line is wider than the current width.
     *
     * @param line The line to check
     * @param revalidateOnly Whether to only revalidate without changing width
     */
    void drawWidthIfWiderLine(@NotNull Line line, boolean revalidateOnly);

    /**
     * Returns the current control state.
     *
     * @return The current control state
     */
    @NotNull
    Control getControl();

    /**
     * Sets the current control state.
     *
     * @param control The control state to set
     */
    void setControl(@NotNull Control control);
}
