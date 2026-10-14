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
 * The facts that can decide the color an item of the score is drawn in. When several hold for
 * one item, the one declared first decides.
 * <p>
 * <b>The declaration order is the priority.</b> Do not reorder the constants unless a new
 * priority is intended; {@link #decide} reads nothing but that order.
 * <p>
 * Selection outranks hover and lint because the selection is what the next Delete removes.
 */
public enum ColorPriority {

    PLAYING,

    SELECTED,

    /** The item is the one an insertion preview would replace. */
    HOVERED,

    LINT,

    DEFAULT;

    public static ColorPriority decide(EnumSet<ColorPriority> facts) {
        // An EnumSet iterates in declaration order, so its first element is the winner.
        return facts.isEmpty() ? DEFAULT : facts.iterator().next();
    }
}
