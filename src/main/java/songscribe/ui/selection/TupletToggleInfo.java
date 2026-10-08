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

package songscribe.ui.selection;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import songscribe.dom.TupletGrade;

/**
 * What the tuplet menu shows for a selection: {@link RangeQueries#canToggleTuplet}'s answer.
 * It enables and checks the menu items only; the edit asks its own question when it runs.
 *
 * @param validGrades   the grades a tuplet over the selection may take, as
 *                      {@link songscribe.dom.Tuplet#spanning} judges them under
 *                      {@link songscribe.dom.TupletValidator.Strictness#STRICT}; empty when the
 *                      selection holds part of a tuplet
 * @param existingGrade the grade of the tuplet the selection covers or reaches into, or
 *                      {@code null} when it holds no element of a tuplet
 */
public record TupletToggleInfo(Set<TupletGrade> validGrades, @Nullable TupletGrade existingGrade) {

    /** The answer for a selection a tuplet cannot group, and that holds no tuplet. */
    public static final TupletToggleInfo NONE = new TupletToggleInfo(Set.of(), null);

    public TupletToggleInfo {
        validGrades = Set.copyOf(validGrades);
    }
}
