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
 * The augmentation dots a {@link Dottable} element is written with: none, one or two.
 *
 * <p>{@link #of(int)} is the one conversion from a raw count and the only place the range is
 * checked.
 */
public enum Dots {
    NONE(0),
    ONE(1),
    TWO(2);

    private final int count;

    Dots(int count) {
        this.count = count;
    }

    public static Dots of(int count) {
        return switch (count) {
            case 0 -> NONE;
            case 1 -> ONE;
            case 2 -> TWO;
            default -> throw new IllegalArgumentException("Dot count must be 0, 1 or 2, got " + count);
        };
    }

    public int count() {
        return count;
    }

    /**
     * Returns {@code ticks} lengthened by these dots: each dot adds half of what the previous one
     * added, so the result is {@code ticks × (2^(d+1) − 1) / 2^d} where {@code d} is
     * {@link #count()}.
     *
     * <p>At both tick bases in use — {@link songscribe.midi.MidiSequenceBuilder#PPQ} in the
     * model and {@link songscribe.io.musicxml.NoteTypeMapping#DIVISIONS} in MusicXML — every
     * {@link NoteValue} augments exactly, so the integer division truncates only for a caller
     * supplying a base of its own.
     *
     * @param ticks the undotted duration
     * @return the dotted duration, in the same ticks
     * @invariant {@code augment(ticks) >= ticks}, with equality exactly for {@link #NONE}
     */
    public int augment(int ticks) {
        var numerator = (1 << (count + 1)) - 1;
        var denominator = 1 << count;

        return (ticks * numerator) / denominator;
    }
}
