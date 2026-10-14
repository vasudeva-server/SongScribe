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
package songscribe.dom;

/**
 * The lines a recorded change was made on, named by the {@link Line} objects themselves so the
 * place stays right whatever else the same edit or load does to the song before its report is
 * built.
 *
 * @param first the line the change starts on
 * @param last  the line the change ends on
 * @invariant {@code first} and {@code last} are lines of one song
 * @invariant {@code last} is {@code first} or a later line of the song; the record does not
 *     check it, because every producer guarantees it
 */
public record ChangeLines(Line first, Line last) {

    public static ChangeLines on(Line line) {
        return new ChangeLines(line, line);
    }
}
