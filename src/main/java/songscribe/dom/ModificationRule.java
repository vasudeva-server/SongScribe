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
     * It runs once for each edit, when the outermost modification bracket closes, while that
     * bracket is still open, so each removal it records lands in the batch that is delivered and
     * undoes with the edit. It does not run during an undo or redo replay, whose batch already
     * carries the removals, nor while mutation tracking is suspended.
     *
     * @param song   the song the edit changed
     * @param batch  the mutations the edit recorded, not including any removal a rule makes now;
     *               every change in it has landed
     * @param report the report of the edit, into which each removal this rule makes is recorded
     * @effects records a mutation for each removal it makes, and records each removal into
     *     {@code report}; it does not read what another rule removed
     */
    void apply(Song song, List<Mutation> batch, ChangeReport report);
}
