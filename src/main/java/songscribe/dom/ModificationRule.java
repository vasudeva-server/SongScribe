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
 * after each mutation. {@link ModificationRules} holds every rule and runs them in order.
 */
interface ModificationRule {

    /**
     * Judges the song after the edit that recorded {@code batch}, removing what the edit left
     * invalid under this rule.
     * <p>
     * When it runs, and when it does not, is stated once, on
     * {@link ModificationSession#endModification}.
     * <p>
     * Each removal it makes is reported to the user, either into {@code report} or through
     * {@link Song#report}, which records into the same report of the open edit: the tie rule
     * records into {@code report}, and the ending rule removes through
     * {@link Span#removeReported}, which reports through {@link Song#report}.
     *
     * @param song   the song the edit changed
     * @param batch  the mutations the edit recorded, not including any removal a rule makes now;
     *               every change in it has landed; never empty
     * @param report the report of the open edit, the one {@link Song#report} records into
     * @effects records a mutation for each removal it makes, and reports each removal into the
     *     edit's report; it does not read what another rule removed
     */
    void apply(Song song, List<Mutation> batch, ChangeReport report);
}
