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
package songscribe.util;

/**
 * A value that can hand out a copy nothing else holds a reference into.
 *
 * <p>The dialog interface bounds its input by this type, so a dialog editing what it was given
 * cannot reach the document through it.
 *
 * <p>Implementing this is the claim that {@link #copy()} is deep enough: an immutable type
 * answers {@code this}; a mutable one returns an instance sharing no mutable state.
 *
 * @param <T> the implementing type itself, so a copy is never a sibling type
 */
public interface Copyable<T extends Copyable<T>> {

    T copy();
}
