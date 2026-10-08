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

package songscribe.undo;

import songscribe.font.DocumentFonts;
import songscribe.message.mutation.FontChange;

/**
 * Where a replay applies a {@link FontChange}. The document fonts are the one piece of edited
 * state that does not live on the {@link songscribe.dom.Song}, so {@link MutationReplayer}
 * takes this alongside the song.
 */
@FunctionalInterface
public interface FontTarget {

    /**
     * Replaces the document fonts with {@code fonts}, recording a {@link FontChange} into the
     * open modification bracket when they differ from the fonts in force.
     *
     * @param fonts the fonts the replayed record restores or re-applies
     * @effects records one {@link FontChange} and applies {@code fonts} when they differ from
     *     the fonts in force; does nothing otherwise
     */
    void setFonts(DocumentFonts fonts);
}
