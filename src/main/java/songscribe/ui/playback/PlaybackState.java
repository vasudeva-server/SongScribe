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

/** The state of {@link PlaybackController}'s playback. */
public enum PlaybackState {
    PLAYING,

    /**
     * Playback ended with the music selection left intact, so the next
     * {@link PlaybackController#play} resumes at the beginning of that selection.
     */
    STOPPED,

    /**
     * Playback ended and the music selection was dropped, so the next
     * {@link PlaybackController#play} starts at the beginning of the song. Distinct from
     * {@link #STOPPED} only in that the selection is gone — the score clears its own selection
     * when it sees this state, rather than being reached into from here.
     */
    REWOUND,
}
