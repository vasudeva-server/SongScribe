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

package songscribe.ui.renderer;

import java.util.EnumSet;

/**
 * The facts that can decide the color an item of the score is drawn in, in priority order:
 * when several hold for one item, the one declared first decides its color.
 * <p>
 * <b>The declaration order is the priority.</b> Do not reorder the constants unless a new
 * priority is intended; {@link #decide} reads nothing but that order.
 * <p>
 * Playback outranks everything because it follows the music as it sounds. Selection outranks
 * hover and lint because the selection is what the next Delete removes, and the user is never
 * left guessing at that because something else colors the item.
 */
public enum ColorPriority {

    /** The item is sounding in playback. */
    PLAYING,

    /** The item reads as selected. */
    SELECTED,

    /** The item is the one an insertion preview would replace. */
    HOVERED,

    /** The lint report flags the item. */
    LINT,

    /** Nothing above holds; the item is drawn in its plain color. */
    DEFAULT;

    /**
     * Returns the fact that decides the color of an item for which {@code facts} hold.
     *
     * @param facts every fact that holds for the item; empty when none does
     * @return the constant of {@code facts} declared first, or {@link #DEFAULT} when
     *         {@code facts} is empty
     * @invariant the result is a member of {@code facts}, or {@link #DEFAULT}
     * @invariant adding a constant declared after the result to {@code facts} leaves the
     *            result unchanged
     */
    public static ColorPriority decide(EnumSet<ColorPriority> facts) {
        // An EnumSet iterates in declaration order, so its first element is the winner.
        return facts.isEmpty() ? DEFAULT : facts.iterator().next();
    }
}
