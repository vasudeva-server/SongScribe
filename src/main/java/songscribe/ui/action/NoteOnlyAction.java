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

import songscribe.music.StaffElement;

/**
 * Abstract base for actions whose attributes apply only to notes
 * (not rests or barlines). Provides a shared {@link #appliesTo} implementation.
 */
public abstract class NoteOnlyAction extends InsertionElementAction
    implements UIAction.ElementModifiable {

    public static final Flag[] FLAGS = {
        Flag.DISABLE_IN_REST_MODE,
        Flag.DISABLE_WHEN_PLAYING,
        Flag.DISABLE_IN_ADJUSTMENT_MODE,
        Flag.DISABLE_WHEN_BAR_SELECTED,
        Flag.ENABLE_WHEN_DURATION_SELECTED,
    };

    public NoteOnlyAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        Flag... flags
    ) {
        super(name, icon, size, actionCommand, tooltip, flags);
    }

    public NoteOnlyAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        String tooltip,
        int virtualKey,
        int modifiers,
        Flag... flags
    ) {
        super(name, icon, size, actionCommand, tooltip, virtualKey, modifiers, flags);
    }

    @Override
    public boolean appliesTo(StaffElement element) {
        return element.getType().isNote();
    }
}
