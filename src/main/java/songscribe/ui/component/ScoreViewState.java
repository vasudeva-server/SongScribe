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

import songscribe.ui.Mode;

/**
 * Holds the mutable view-state that ScoreView and ScoreViewController both read.
 * Plain storage: no bus subscriptions, no behavior.
 * <p>
 * The class is {@code public} so cross-package consumers (e.g. menu/action wiring)
 * can read state via the getters, but construction is deliberately package-private —
 * only {@code ScoreView} owns the lifecycle.
 */
public class ScoreViewState {

    private Mode mode = Mode.EDIT;

    ScoreViewState() {
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }
}
