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

/**
 * A hairpin as a reader found it in a file, before the load pass has judged it.
 * <p>
 * A reader does not build a {@link Hairpin}: every hairpin is well formed, and whether a range
 * holds a well-formed hairpin is {@link Hairpin#spanning}'s question, not the reader's. So a
 * reader records what the file said here, and {@link HairpinLoadPass#run} — once the song is
 * complete — turns each pending hairpin into a {@code Hairpin} on its anchor's line or drops it.
 * <p>
 * A pending hairpin is not a span. It sits in no registry, is never drawn or recorded for undo,
 * and nothing outside the load path sees one.
 *
 * @param kind      whether the hairpin is a crescendo or a diminuendo
 * @param anchor    the hairpin's first element, in a line of the song being read
 * @param end       the hairpin's last element, in the anchor's line at or after the anchor
 * @param x1ShiftSs the user's horizontal shift of the hairpin's start in staff spaces; it
 *                  becomes the hairpin's {@link Hairpin#getX1ShiftSs()}
 * @param x2ShiftSs the user's horizontal shift of the hairpin's end in staff spaces; it becomes
 *                  the hairpin's {@link Hairpin#getX2ShiftSs()}
 * @param yOffsetSs the user's vertical offset of the hairpin in staff spaces, positive downward;
 *                  it becomes the hairpin's {@link LineElement#getUserYOffsetSs()}
 */
public record PendingHairpin(
    Hairpin.Kind kind,
    StaffElement anchor,
    StaffElement end,
    double x1ShiftSs,
    double x2ShiftSs,
    double yOffsetSs
) {
}
