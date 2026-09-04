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
 */
public enum BeatUnit {
    SEMI_BREVE(NoteValue.WHOLE, false),
    MINIM_DOTTED(NoteValue.HALF, true),
    MINIM(NoteValue.HALF, false),
    CROTCHET_DOTTED(NoteValue.QUARTER, true),
    CROTCHET(NoteValue.QUARTER, false),
    QUAVER_DOTTED(NoteValue.EIGHTH, true),
    QUAVER(NoteValue.EIGHTH, false);

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

    public int ticks() {
        var ticks = noteValue.ticks();

        if (dotted) {
            ticks += ticks / 2;
        }

        return ticks;
    }
}
