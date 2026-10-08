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

import songscribe.dom.StaffElementRun.ElementRange;

/**
 * A hairpin as a reader found it in a file, judged by {@link Hairpin#spanning}.
 *
 * @param kind      whether the hairpin is a crescendo or a diminuendo
 * @param anchor    the hairpin's first element, in a line of the song being read
 * @param end       the hairpin's last element, in the anchor's line at or after the anchor
 * @param xOffsetSs the user's horizontal offset of the hairpin in staff spaces, positive
 *                  rightward
 * @param yOffsetSs the user's vertical offset of the hairpin in staff spaces, positive downward
 */
public record PendingHairpin(
    Hairpin.Kind kind,
    StaffElement anchor,
    StaffElement end,
    double xOffsetSs,
    double yOffsetSs
) implements PendingSpan<Hairpin> {

    @Override
    public Spanning<Hairpin> spanning(Line line, ElementRange range) {
        return Hairpin.spanning(line, kind, range);
    }
}
