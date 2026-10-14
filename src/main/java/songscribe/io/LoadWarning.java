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
package songscribe.io;

/**
 * A non-fatal problem detected while loading a song: a {@link Type} plus the offending raw text.
 * User-facing strings belong to the UI layer.
 */
public record LoadWarning(Type type, String description) {

    /**
     * The kinds of non-fatal load problems. Related conditions add a constant here, not a new
     * carrier type.
     */
    public enum Type {
        INVALID_LYRICS_DATE
    }
}
