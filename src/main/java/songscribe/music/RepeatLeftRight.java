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

public class RepeatLeftRight extends NotNote {

    public static final Rectangle REAL_NOTE_RECT = new Rectangle(0, 12, 22, 32);

    public RepeatLeftRight() {}

    private RepeatLeftRight(Note note) {
        super(note);
    }

    @Override
    public RepeatLeftRight clone() {
        return new RepeatLeftRight(this);
    }

    @Override
    public NoteType getNoteType() {
        return NoteType.REPEAT_LEFT_RIGHT;
    }

    @Override
    public Rectangle getRealUpNoteRect() {
        return REAL_NOTE_RECT;
    }

    @Override
    public Rectangle getRealDownNoteRect() {
        return REAL_NOTE_RECT;
    }
}
