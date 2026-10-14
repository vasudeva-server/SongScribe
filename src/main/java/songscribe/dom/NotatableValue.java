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

package songscribe.dom;

import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * A written note value with its dots: the value a tuplet ratio counts its normal notes in.
 * Not a {@link BeatUnit}: a beat allows one dot, and a ratio's value allows two.
 */
public record NotatableValue(NoteValue value, Dots dots) {

    private static final Map<Integer, NotatableValue> NOTATABLE_BY_TICKS = buildNotatableByTicks();

    /**
     * @return {@code null} when no written value has that duration, which is so for most tick
     *     counts
     */
    public static @Nullable NotatableValue withTicks(int ticks) {
        return NOTATABLE_BY_TICKS.get(ticks);
    }

    private static Map<Integer, NotatableValue> buildNotatableByTicks() {
        var table = new HashMap<Integer, NotatableValue>();

        for (var value : NoteValue.values()) {
            for (var dots : Dots.values()) {
                var notatable = new NotatableValue(value, dots);
                table.put(notatable.ticks(), notatable);
            }
        }

        return Map.copyOf(table);
    }

    public int ticks() {
        return dots.augment(value.ticks());
    }
}
