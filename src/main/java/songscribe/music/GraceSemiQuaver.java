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

public class GraceSemiQuaver extends Note {

    public static final Rectangle REAL_UP_NOTE_RECT = new Rectangle(
        2,
        11,
        13,
        20
    );
    public static final Rectangle REAL_DOWN_NOTE_RECT = new Rectangle(
        0,
        24,
        9,
        20
    );

    private int y0Pos = 0, x2DiffPos = 0;

    public GraceSemiQuaver() {}

    private GraceSemiQuaver(Note note) {
        super(note);
    }

    @Override
    public GraceSemiQuaver clone() {
        return new GraceSemiQuaver(this);
    }

    @Override
    public NoteType getNoteType() {
        return NoteType.GRACE_SEMIQUAVER;
    }

    @Override
    public Rectangle getRealUpNoteRect() {
        return REAL_UP_NOTE_RECT;
    }

    @Override
    public Rectangle getRealDownNoteRect() {
        return REAL_DOWN_NOTE_RECT;
    }

    @Override
    public int getDotCount() {
        return 0;
    }

    @Override
    public int getDefaultDuration() {
        return 0;
    }

    public int getY0Pos() {
        return y0Pos;
    }

    public void setY0Pos(int y0Pos) {
        this.y0Pos = y0Pos;
    }

    public int getX2DiffPos() {
        return x2DiffPos;
    }

    public void setX2DiffPos(int x2DiffPos) {
        this.x2DiffPos = x2DiffPos;
    }
}
