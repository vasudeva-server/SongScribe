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
 * A non-fatal problem detected while loading a song. The loader stays in the
 * headless {@code io} layer: it reports <em>what</em> went wrong as a
 * {@link Type} plus the offending raw text, and leaves the mapping to
 * user-facing strings and dialogs to the UI layer.
 *
 * @param type        which load problem occurred
 * @param description the offending raw text (e.g. the unparseable date string)
 */
public record LoadWarning(Type type, String description) {

    /**
     * The kinds of non-fatal load problems. New ISO-8601 date fields (such as a
     * future music date) add a constant here rather than a new carrier type.
     */
    public enum Type {
        INVALID_LYRICS_DATE
    }
}
