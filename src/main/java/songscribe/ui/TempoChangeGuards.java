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

package songscribe.ui;

import module java.desktop;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElement;

/**
 * Utility class for vetting the removal of a tempo change.
 */
public final class TempoChangeGuards {

    private TempoChangeGuards() {}

    /**
     * Whether the tempo change on {@code element} may be removed. A removal that would leave a
     * later tempo change without the tempo it modifies is refused.
     *
     * <p>This both decides and explains: when it returns false it has already told the user why,
     * so the caller never has to show a message of its own. Unlike {@code EndingConfirms}, it
     * asks the user nothing — the warning it raises is a single-button alert, and a refusal is
     * final. Hence "allow" rather than "confirm".
     *
     * @param parent the component to parent the warning on, so it cannot be hidden behind a
     *               modal dialog the removal was triggered from
     */
    public static boolean allowRemoveTempoChange(@Nullable Component parent, StaffElement element) {
        var line = element.getParentLine();

        // An element in no line has no later tempo change to orphan.
        if (line == null || !line.getSong().wouldOrphanLaterTempoChange(element)) {
            return true;
        }

        OptionDialogs.showWarningMessage(
            parent, Strings.ALERT_TITLE_TEMPO_CHANGE_ORPHANED, Strings.ALERT_TEMPO_CHANGE_ORPHANED);
        return false;
    }
}
