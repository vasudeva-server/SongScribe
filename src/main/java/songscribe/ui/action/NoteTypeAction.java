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

package songscribe.ui.action;

import java.awt.event.*;

import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.message.BarSelectedMessage;
import songscribe.ui.message.DurationSelectedMessage;
import songscribe.ui.message.MessageCenter;

public class NoteTypeAction extends StickyUIAction implements UIAction.Reflectable {

    public enum Kind { DURATION, NON_DURATION }

    private final NoteType type;
    private final Kind kind;

    public NoteTypeAction(
        Kind kind,
        NoteType type,
        String name,
        String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers
    ) {
        super(name, icon, size, actionCommand, tooltip, virtualKey, modifiers);
        this.kind = kind;
        this.type = type;

        if (kind == Kind.NON_DURATION) {
            setFlags(
                Flag.DISABLE_IN_REST_MODE,
                Flag.DISABLE_WHEN_PLAYING,
                Flag.DISABLE_IN_ADJUSTMENT_MODE,
                Flag.DISABLE_WHEN_EDITING_TEXT
            );
        } else {
            setFlags(
                Flag.DISABLE_WHEN_PLAYING,
                Flag.DISABLE_IN_ADJUSTMENT_MODE,
                Flag.DISABLE_WHEN_EDITING_TEXT
            );
        }
    }

    public NoteType getType() {
        return type;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public boolean appliesTo(Note note) {
        if (kind == Kind.DURATION) {
            return true;
        }

        return note.getNoteType().isNonDuration();
    }

    @Override
    public boolean matchesNote(Note note) {
        return note.getNoteType() == type;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (doActionPerformed(e)) {
            if (kind == Kind.DURATION) {
                MessageCenter.post(new DurationSelectedMessage(type));
            } else {
                MessageCenter.post(new BarSelectedMessage(type));
            }
        }
    }
}
