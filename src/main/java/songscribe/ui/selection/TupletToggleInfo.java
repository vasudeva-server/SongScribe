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

import org.jspecify.annotations.Nullable;

import songscribe.dom.Tuplet;

/**
 * Result of {@link LineSelectionState#canToggleTuplet()}.
 *
 * @param canToggle      true if the selection is uniform (all pitched, all in the same tuplet or none)
 * @param existing       the tuplet at the selection start, or {@code null} if there is none
 * @param coversExisting true iff {@code existing != null} and the selection exactly spans
 *                       {@code [existing.anchorIndex, existing.endIndex]}. Always {@code false} when
 *                       {@code existing} is {@code null}.
 */
public record TupletToggleInfo(
    boolean canToggle,
    @Nullable Tuplet existing,
    boolean coversExisting
) {
    public TupletToggleInfo {
        if (coversExisting && (existing == null)) {
            throw new IllegalArgumentException("coversExisting cannot be true when existing is null");
        }
    }
}
