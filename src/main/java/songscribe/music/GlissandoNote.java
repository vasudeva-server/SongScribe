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

import java.awt.*;

import org.jetbrains.annotations.Nullable;

public class GlissandoNote extends Note {

    GlissandoNote() {}

    @Override
    public NoteType getNoteType() {
        return NoteType.GLISSANDO;
    }

    @Override
    public Note clone() {
        return this;
    }

    @Override
    @Nullable
    public Rectangle getRealUpNoteRect() {
        return null;
    }

    @Override
    @Nullable
    public Rectangle getRealDownNoteRect() {
        return null;
    }

    @Override
    public int getDefaultDuration() {
        return 0;
    }
}
