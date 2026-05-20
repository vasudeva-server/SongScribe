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

package songscribe.ui.renderer;


import songscribe.dom.Song;

/**
 * Interface providing render-time data for the Renderer.
 * <p>
 * RenderContext decouples Renderer from ScoreView, enabling:
 * <ul>
 *   <li>Pure rendering: (model + layout) → pixels</li>
 *   <li>Testability: Mock context for unit tests</li>
 *   <li>Clean separation of concerns</li>
 * </ul>
 * <p>
 * ScoreView implements this interface to provide rendering data.
 */
public interface RenderContext {

    // -------------------------------------------------------------------------
    // Core data
    // -------------------------------------------------------------------------

    /**
     * Returns the song (music model) to render.
     */
    Song getSong();

    // -------------------------------------------------------------------------
    // Coordinate system
    // -------------------------------------------------------------------------

    /**
     * Returns the Y position of the middle staff line (B4) of the first staff.
     */
    int getMiddleLineYPx();

    /**
     * Returns the vertical distance between staff rows.
     */
    int getRowHeightPx();

    /**
     * Calculates the Y position for an element at the given staff position and line index.
     *
     * @param staffPosition The element's staff position relative to middle line (0 = B4)
     * @param lineIndex     The staff line index (0-based)
     * @return The Y coordinate in pixels
     */
    int getNoteYPosPx(int staffPosition, int lineIndex);

    /**
     * Returns the Y position for content rendered below the lyrics area.
     */
    int getUnderLyricsYPosPx();

    /**
     * Returns the starting Y position for the score content.
     */
    int getStartY();

    /**
     * Returns the X position where key signature accidentals are drawn.
     */
    int getLeadingKeysPosPx();

    // -------------------------------------------------------------------------
    // Selection state (for highlighting)
    // -------------------------------------------------------------------------

    /**
     * Returns whether the element at the given index is selected.
     *
     * @param elementIndex The element index within the line
     * @param lineIndex    The staff line index
     * @return true if the element is selected
     */
    boolean isElementSelected(int elementIndex, int lineIndex);

    /**
     * Returns the index of the currently selected staff line, or -1 if none.
     */
    int getSelectedLine();

    // -------------------------------------------------------------------------
    // Playback state (for highlighting)
    // -------------------------------------------------------------------------

    /**
     * Returns the index of the line currently being played, or -1 if not playing.
     */
    int getPlayingLine();

    /**
     * Returns the index of the note currently being played, or -1 if not playing.
     */
    int getPlayingNote();
}
