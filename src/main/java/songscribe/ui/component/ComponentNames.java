/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.component;

/**
 * Centralized registry of Swing component names used for e2e test lookups.
 * Both production code and tests should reference these constants
 * to ensure compile-time safety.
 */
public final class ComponentNames {
    public static final String SCORE = "score";
    public static final String MODE_CYCLE_BUTTON = "btn-mode-cycle";
    public static final String LINE_PREFIX = "line-";

    public static String line(int index) {
        return LINE_PREFIX + index;
    }

    private ComponentNames() {}
}
