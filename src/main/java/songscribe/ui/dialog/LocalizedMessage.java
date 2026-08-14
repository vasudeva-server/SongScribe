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
 * the arguments its {@code MessageFormat} pattern needs.
 *
 * <p>It exists so that code deciding <em>what</em> to tell the user can do so without producing
 * text. Resolving is a locale concern and belongs to whoever displays the message; a back end
 * that resolved would be making that decision on the domain side of the dialog seam. The
 * presenter passes {@link #key()} and {@link #args()} to
 * {@link songscribe.ui.OptionDialogs}, which owns the resolution rule — a value is a
 * {@code MessageFormat} pattern only when read through the varargs {@code Strings.get}, so an
 * empty {@link #args()} must resolve through the single-argument form instead.
 *
 * <p>Instances are immutable: {@link #args()} answers an unmodifiable list unaffected by later
 * changes to the list the constructor was given.
 *
 * <p>An argument may itself be a {@code LocalizedMessage}, which is how a message names a
 * user-facing word it must not resolve — a unit abbreviation, a role name. The presenter
 * resolves it along with the pattern. A nested message takes no arguments of its own, so the
 * nesting is one level deep and never recursive.
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
     * A message whose value is literal text and takes no format arguments.
     *
     * @param key a {@link songscribe.Strings} constant whose value contains no
     *            {@code MessageFormat} placeholders
     */
    public LocalizedMessage(String key) {
        this(key, List.of());
    }
}
