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

package songscribe.ui.component;

import songscribe.dom.Line;
import songscribe.ui.action.EditLyricAction;

/**
 * Decides which staff element a lyric-editing gesture should open the editor on.
 * <p>
 * This is pure policy over a {@link Line} and an element index: it holds no state and
 * needs no editor instance. Every entry point to lyric editing — {@link EditLyricAction},
 * Return/Enter on a selection, and a double-click on an element — resolves its target
 * through {@link #resolveLyricTarget}, so all of them agree on what is editable and on
 * which element an ambiguous gesture lands.
 */
public final class LyricTargetResolver {

    private LyricTargetResolver() {
    }

    /**
     * Returns the index the editor should open on for a gesture aimed at {@code index},
     * or -1 when there is no such target. A grace-host pair's lyric lives on the grace
     * note, so a gesture on the host resolves to the grace note.
     */
    public static int resolveLyricTarget(Line line, int index) {
        var targetIndex = line.isHostOfPairedGraceNote(index) ? index - 1 : index;
        return isLyricTargetEligible(line, targetIndex) ? targetIndex : -1;
    }

    /**
     * Returns true when {@code index} is a structurally valid lyric target: a pitched note,
     * rest, or grace note that is NOT the host of a paired grace note. This is the single
     * source of truth for the host-block rule used by action enablement, gesture target
     * resolution, and editor navigation.
     */
    public static boolean isLyricTargetEligible(Line line, int index) {
        if (line.isHostOfPairedGraceNote(index)) {
            return false;
        }

        var type = line.getElement(index).getType();
        return type.isPitchedNote() || type.isRest() || type.isGraceNote();
    }
}
