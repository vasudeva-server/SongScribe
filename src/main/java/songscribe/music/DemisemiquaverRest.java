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

import songscribe.ui.playback.PlaybackController;

public class DemisemiquaverRest extends Note {

    public static final Rectangle REAL_NOTE_RECT = new Rectangle(0, 13, 14, 34);

    public DemisemiquaverRest() {}

    private DemisemiquaverRest(Note note) {
        super(note);
    }

    @Override
    public DemisemiquaverRest clone() {
        return new DemisemiquaverRest(this);
    }

    @Override
    public NoteType getNoteType() {
        return NoteType.DEMI_SEMIQUAVER_REST;
    }

    @Override
    public Rectangle getRealUpNoteRect() {
        return REAL_NOTE_RECT;
    }

    @Override
    public Rectangle getRealDownNoteRect() {
        return REAL_NOTE_RECT;
    }

    @Override
    public int getYPos() {
        return 0;
    }

    @Override
    public Accidental getAccidental() {
        return Accidental.NONE;
    }

    @Override
    public int getDefaultDuration() {
        return PlaybackController.PPQ / 8;
    }
}
