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

import org.jspecify.annotations.Nullable;

/**
 * A tuplet as a reader found it in a file, before the load pass has judged it.
 * <p>
 * A reader cannot build a {@link Tuplet}: a file that states no ratio leaves it to be derived
 * from the beat at the anchor, which a later tempo change may still redefine. A pending tuplet is
 * not a span: nothing outside the load path sees one.
 *
 * @param anchor      in a line of the song being read
 * @param end         in the anchor's line at or after the anchor
 * @param statedRatio {@code null} when the file states none and the load pass is to derive one
 * @param yOffsetSs   in staff spaces, positive downward
 */
public record PendingTuplet(
    StaffElement anchor,
    StaffElement end,
    TupletGrade grade,
    @Nullable TupletNormalTime statedRatio,
    double yOffsetSs
) {
}
