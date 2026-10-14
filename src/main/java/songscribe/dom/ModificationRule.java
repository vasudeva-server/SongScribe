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

import java.util.List;

import songscribe.message.mutation.Mutation;

/**
 * A domain rule judged once for each edit, over the whole of what the edit did, rather than
 * after each mutation.
 */
interface ModificationRule {

    /**
     * Each removal it makes is reported to the user, into {@code report} or through
     * {@link Song#report}.
     *
     * @param batch never empty
     */
    void apply(Song song, List<Mutation> batch, ChangeReport.Builder report);
}
