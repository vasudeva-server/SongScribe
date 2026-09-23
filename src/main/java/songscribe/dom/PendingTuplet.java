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
 * A reader cannot build a {@link Tuplet}: a tuplet's ratio must be known, and a file that
 * states none leaves it to be derived from the beat at the anchor, which a tempo change later
 * in the file may still redefine while the reader is mid-parse. So a reader records what the
 * file said here, and {@link TupletLoadPass#run} — once the song is complete — turns each
 * pending tuplet into a {@code Tuplet} on its anchor's line or drops it.
 * <p>
 * A pending tuplet is not a span. It sits in no registry, is never drawn or recorded for undo,
 * and nothing outside the load path sees one.
 *
 * @param anchor      the tuplet's first element, in a line of the song being read
 * @param end         the tuplet's last element, in the anchor's line at or after the anchor
 * @param grade       the printed tuplet number, as the file states it
 * @param statedRatio the ratio the file states, or {@code null} when it states none and the
 *                    load pass is to derive one
 * @param yOffsetSs   the user's vertical offset of the bracket in staff spaces, positive
 *                    downward; it becomes the tuplet's {@link LineElement#getUserYOffsetSs()}
 */
public record PendingTuplet(
    StaffElement anchor,
    StaffElement end,
    int grade,
    @Nullable TupletRatio statedRatio,
    double yOffsetSs
) {
}
