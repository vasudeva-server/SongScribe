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

import org.jspecify.annotations.Nullable;

import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;

/**
 * Base class for actions that have a toggle selected state
 * (e.g. accidentals, note durations, rest mode).
 * Non-selectable actions extend UIAction directly.
 */
@SuppressWarnings("NonStaticInitializer")
public abstract class SelectableUIAction extends UIAction
    implements UIAction.Selectable {

    { putValue(SELECTED_KEY, false); }

    protected SelectableUIAction(String name, String actionCommand, Flag... flags) {
        super(name, actionCommand, flags);
    }

    protected SelectableUIAction(
        String name,
        String actionCommand,
        int virtualKey,
        int modifiers,
        Flag... flags
    ) {
        super(name, actionCommand, virtualKey, modifiers, flags);
    }

    protected SelectableUIAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        @Nullable String tooltip,
        Flag... flags
    ) {
        this(name, icon, size, actionCommand, tooltip, null, flags);
    }

    protected SelectableUIAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        @Nullable String tooltip,
        @Nullable PrefsKey prefsKey,
        Flag... flags
    ) {
        super(name, icon, size, actionCommand, tooltip, flags);

        if (prefsKey != null) {
            setSelected(Prefs.getBoolean(prefsKey));
        }
    }

    protected SelectableUIAction(
        @Nullable String name,
        @Nullable String icon,
        int size,
        String actionCommand,
        @Nullable String tooltip,
        int virtualKey,
        int modifiers,
        Flag... flags
    ) {
        super(name, icon, size, actionCommand, tooltip, virtualKey, modifiers, flags);
    }

    @Override
    public boolean isSelected() {
        var value = getValue(SELECTED_KEY);
        return value instanceof Boolean b && b;
    }

    @Override
    public void setSelected(boolean selected) {
        putValue(SELECTED_KEY, selected);
    }

    public void reset() {
        setSelected(false);
    }
}
