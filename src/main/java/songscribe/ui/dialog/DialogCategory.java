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
package songscribe.ui.dialog;

/**
 * Categorizes dialogs by how they interact with the blocking counter.
 * Only blocking dialogs (EXCLUSIVE and OPERATIONAL) prevent other
 * dialog-opening actions from firing while they are visible.
 */
public enum DialogCategory {

    /** Read-only dialog that never blocks or is blocked. */
    INFORMATIONAL,

    /** Modifies global state; blocks and is blocked by other blocking dialogs. */
    EXCLUSIVE,

    /** Modifies scoped state or runs a task (default); blocks and is blocked. */
    OPERATIONAL;

    public boolean isBlocking() {
        return this == EXCLUSIVE || this == OPERATIONAL;
    }
}
