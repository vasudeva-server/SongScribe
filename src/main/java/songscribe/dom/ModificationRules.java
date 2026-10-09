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
 * Every {@link ModificationRule}, in the order they run when the outermost modification bracket
 * closes.
 * <p>
 * The list is fixed. Every rule is a domain rule of the model, so there is no way to register
 * one from outside.
 * <p>
 * Key-invariant maintenance and the accidental chain walk are not rules here: each runs after
 * every mutation ({@link Song#applyChange}), and a rule here runs only once for each edit.
 */
final class ModificationRules {

    private final List<ModificationRule> rules = List.of(new TieRule(), new EndingSectionRule());

    /**
     * Runs every rule over {@code batch}, in order.
     *
     * @param song   the song the edit changed
     * @param batch  the mutations the edit recorded; every change in it has landed; never empty
     * @param report the report of the open edit: the tie rule records its removals into it, and
     *               the ending rule reports its removals through {@link Song#report}, which
     *               records into the same report
     * @effects whatever each rule's {@link ModificationRule#apply} does
     */
    void apply(Song song, List<Mutation> batch, ChangeReport.Builder report) {
        for (var rule : rules) {
            rule.apply(song, batch, report);
        }
    }
}
