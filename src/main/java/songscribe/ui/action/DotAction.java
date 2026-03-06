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

import org.jetbrains.annotations.Nullable;

import songscribe.music.Note;

public class DotAction extends InsertionNoteAction implements UIAction.Reflectable {

    private final DotLevel dotLevel;

    public DotAction(
        DotLevel dotLevel,
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers
    ) {
        super(
            name,
            icon,
            size,
            actionCommand,
            tooltip,
            true,
            virtualKey,
            modifiers
        );
        this.dotLevel = dotLevel;
        setFlags(
            Flag.DISABLE_WHEN_PLAYING,
            Flag.DISABLE_IN_ADJUSTMENT_MODE,
            Flag.DISABLE_WHEN_BAR_SELECTED,
            Flag.ENABLE_WHEN_DURATION_SELECTED,
            Flag.DISABLE_WHEN_EDITING_TEXT
        );
    }

    public DotLevel getDotLevel() {
        return dotLevel;
    }

    @Override
    public boolean appliesTo(Note note) {
        return note.getNoteType().isDuration();
    }

    @Override
    public boolean matchesNote(Note note) {
        return switch (dotLevel) {
            case SINGLE -> note.getDotCount() == 1;
            case DOUBLE -> note.getDotCount() == 2;
        };
    }

    public enum DotLevel {
        SINGLE,
        DOUBLE,
    }
}
