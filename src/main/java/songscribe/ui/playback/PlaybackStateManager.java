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

package songscribe.ui.playback;

/**
 * Manages playback state for the Score.
 * <p>
 * PlaybackStateManager tracks which line and note are currently being played
 * during MIDI playback, enabling visual highlighting of the playing note.
 */
public final class PlaybackStateManager {

    // The index of the currently playing line (-1 if not playing)
    private int playingLine = -1;

    // The index of the currently playing note within the line (-1 if not playing)
    private int playingNote = -1;

    // -------------------------------------------------------------------------
    // State accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the index of the line currently being played, or -1 if not playing.
     */
    public int getPlayingLine() {
        return playingLine;
    }

    /**
     * Returns the index of the note currently being played, or -1 if not playing.
     */
    public int getPlayingNote() {
        return playingNote;
    }

    // -------------------------------------------------------------------------
    // State mutators
    // -------------------------------------------------------------------------

    /**
     * Sets the currently playing position.
     *
     * @param line The line index
     * @param note The note index within the line
     */
    public void setPlayingPosition(int line, int note) {
        this.playingLine = line;
        this.playingNote = note;
    }

    /**
     * Resets the playback state to not playing.
     */
    public void reset() {
        playingLine = -1;
        playingNote = -1;
    }

    /**
     * Returns whether playback is currently active.
     */
    public boolean isPlaying() {
        return playingLine != -1;
    }
}
