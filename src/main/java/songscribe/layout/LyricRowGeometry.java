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

package songscribe.layout;

/**
 * The vertical size of a line's lyric row, from the lyrics font a line was laid out with.
 *
 * @param lyricBoxHeightSs   independent of the typed text, so the editor box does not change
 *                           height as descenders are typed or deleted
 * @param staffToLyricsGapSs a <em>baseline</em> offset from a line's below-staff content to the
 *                           lyric row's baseline, not the visual gap
 */
public record LyricRowGeometry(double lyricBoxHeightSs, double staffToLyricsGapSs) {}
