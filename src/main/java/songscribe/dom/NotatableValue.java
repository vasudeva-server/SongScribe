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
 * <p>
 * It is not a {@link BeatUnit}: a beat allows one dot, and a ratio's value allows two.
 *
 * @param value the undotted note value
 * @param dots  the dots the value carries
 */
public record NotatableValue(NoteValue value, Dots dots) {

    /** Every writable note value, keyed by its duration in PPQ ticks. */
    private static final Map<Integer, NotatableValue> NOTATABLE_BY_TICKS = buildNotatableByTicks();

    /**
     * Returns the written value whose duration is {@code ticks}.
     *
     * @param ticks a duration, at {@link songscribe.midi.MidiSequenceBuilder#PPQ} ticks per quarter
     * @return the note value and dots whose {@link #ticks()} equals {@code ticks}, or {@code null}
     *     when no written value has that duration, which is so for most tick counts
     * @invariant no two note value and dots pairs share a duration, so the answer is unique
     */
    public static @Nullable NotatableValue withTicks(int ticks) {
        return NOTATABLE_BY_TICKS.get(ticks);
    }

    /**
     * Every {@link NoteValue} at every {@link Dots} value, keyed by duration. No two of them
     * share a duration.
     */
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

    /**
     * Returns this value's written duration.
     *
     * @return the note value's ticks lengthened by the dots, at
     *     {@link songscribe.midi.MidiSequenceBuilder#PPQ} ticks per quarter
     * @invariant exact for every {@link NoteValue} and {@link Dots} pair — see {@link Dots#augment}
     */
    public int ticks() {
        return dots.augment(value.ticks());
    }
}
