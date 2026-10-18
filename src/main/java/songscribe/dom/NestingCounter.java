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
package songscribe.dom;

/**
 * A mode that nested scopes switch on: it is on while any scope is open, so an inner scope that
 * closes never switches off a mode an outer scope still relies on.
 */
final class NestingCounter {

    private int depth = 0;

    boolean isOn() {
        return depth > 0;
    }

    /**
     * The scope closes even when {@code body} throws.
     */
    void during(Runnable body) {
        depth++;

        try {
            body.run();
        } finally {
            depth--;
        }
    }
}
