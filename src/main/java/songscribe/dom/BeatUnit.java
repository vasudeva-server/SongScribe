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
 * A beat unit is a note value with at most one dot, which is the whole domain a tempo or a beat
 * change can name; {@code dotted} is a boolean rather than a count for that reason.
 *
 * <p>The constants are declared longest first. The tempo and beat-change dialogs list their
 * combos straight from {@link #values()}, so declaration order is the order a user sees, and a
 * new constant is declared where its length puts it.
 */
public enum BeatUnit {
    SEMI_BREVE(NoteValue.WHOLE, false),
    MINIM_DOTTED(NoteValue.HALF, true),
    MINIM(NoteValue.HALF, false),
    CROTCHET_DOTTED(NoteValue.QUARTER, true),
    CROTCHET(NoteValue.QUARTER, false),
    QUAVER_DOTTED(NoteValue.EIGHTH, true),
    QUAVER(NoteValue.EIGHTH, false);

    /**
     * The parts a dotted beat divides into before subdividing binarily. Public because
     * {@link #binaryDivisionRootTicks}'s contract names it.
     */
    public static final int DOTTED_BEAT_PARTS = 3;

    private final NoteValue noteValue;
    private final boolean dotted;

    BeatUnit(NoteValue noteValue, boolean dotted) {
        this.noteValue = noteValue;
        this.dotted = dotted;
    }

    public NoteValue noteValue() {
        return noteValue;
    }

    public boolean dotted() {
        return dotted;
    }

    /**
     * This unit's length in ticks: the value's length, plus half again when dotted.
     *
     * @return the length in ticks, at {@link songscribe.midi.MidiSequenceBuilder#PPQ} ticks
     *     per quarter
     * @invariant the result is an exact integer for every unit — the shortest dotted unit is a
     *     dotted eighth, and an eighth is an even number of ticks
     */
    public int ticks() {
        var ticks = noteValue.ticks();

        if (dotted) {
            ticks += ticks / 2;
        }

        return ticks;
    }

    /**
     * The longest length this beat subdivides binarily from. An undotted beat halves directly,
     * so it is its own root. A dotted beat first divides into {@value #DOTTED_BEAT_PARTS} and
     * only then halves, so its root is that part.
     *
     * @return the root length in ticks, at {@link songscribe.midi.MidiSequenceBuilder#PPQ} ticks
     *     per quarter
     * @invariant the result is exact for every unit — a dotted unit is
     *     {@value #DOTTED_BEAT_PARTS} halves of its undotted value, and every undotted value a
     *     unit names is an even number of ticks
     */
    public int binaryDivisionRootTicks() {
        var ticks = ticks();

        return dotted ? ticks / DOTTED_BEAT_PARTS : ticks;
    }
}
