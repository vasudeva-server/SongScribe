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

import java.util.List;

/**
 * A user-facing string named but not resolved: a {@link songscribe.Strings} key together with
 * the arguments its {@code MessageFormat} pattern needs. Resolving is a locale concern that
 * belongs to whoever displays the message, so code deciding <em>what</em> to tell the user
 * produces no text.
 *
 * <p>Instances are immutable: {@link #args()} answers an unmodifiable list unaffected by later
 * changes to the list the constructor was given.
 *
 * <p>An argument may itself be a {@code LocalizedMessage}, which the presenter resolves along
 * with the pattern. A nested message takes no arguments of its own, so nesting is one level deep.
 *
 * @param key  a {@link songscribe.Strings} constant, never a raw string literal
 * @param args the pattern arguments, in the order {@code key}'s pattern indexes them; empty when
 *             the value is literal text
 */
public record LocalizedMessage(String key, List<Object> args) {

    public LocalizedMessage {
        args = List.copyOf(args);
    }

    /**
     * @param key a {@link songscribe.Strings} constant whose value contains no
     *            {@code MessageFormat} placeholders
     */
    public LocalizedMessage(String key) {
        this(key, List.of());
    }
}
