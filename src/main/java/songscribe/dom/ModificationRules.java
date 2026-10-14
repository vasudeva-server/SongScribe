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
 * closes. The list is fixed: there is no way to register a rule from outside.
 */
final class ModificationRules {

    private final List<ModificationRule> rules = List.of(new TieRule(), new EndingSectionRule());

    void apply(Song song, List<Mutation> batch, ChangeReport.Builder report) {
        for (var rule : rules) {
            rule.apply(song, batch, report);
        }
    }
}
