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

package songscribe.dom;

/**
 * The non-interactive mark that depicts the song's tempo at the right edge of the first
 * line's staff header.
 * <p>
 * It carries no state of its own: it is a stable identity key, one instance per {@link Song}
 * ({@link Song#getTempoMarkElement()}), and reads the tempo from {@link Song#getTempo()} at
 * layout and paint time.
 */
public final class SongTempoMark extends LineElement {
}
