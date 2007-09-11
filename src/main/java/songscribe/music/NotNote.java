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
package songscribe.music;

import org.jetbrains.annotations.Nullable;

public abstract class NotNote extends Note {

    protected NotNote() {}

    protected NotNote(Note note) {
        super(note);
    }

    @Override
    public int getYPos() {
        return 0;
    }

    @Override
    public int getDotCount() {
        return 0;
    }

    @Override
    public Note.Accidental getAccidental() {
        return Note.Accidental.NONE;
    }

    @Override
    @Nullable
    public ForceArticulation getForceArticulation() {
        return null;
    }

    @Override
    @Nullable
    public DurationArticulation getDurationArticulation() {
        return null;
    }

    @Override
    public int getDefaultDuration() {
        return 0;
    }
}
