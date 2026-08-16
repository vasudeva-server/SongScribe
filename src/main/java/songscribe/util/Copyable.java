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
 * <p>It exists for the dialog interface. {@code DialogController.ops()} copies what {@code read()}
 * answers before any dialog sees it, so a dialog editing what it was given cannot reach the
 * document through it. Because that type parameter is bounded by this interface, a type that has
 * not said how it copies cannot be a dialog's input at all — the check is the compiler's rather
 * than a reviewer's, which is the whole reason the bound is here instead of a rule in a guide.
 *
 * <p><strong>Implementing this is the claim that {@link #copy()} is deep enough.</strong> An
 * immutable type answers {@code this}, and that is the whole of its obligation; a mutable one
 * returns an instance sharing no mutable state with the original. Nothing can check which of the
 * two a given type is, so the declaration is what carries the promise — implementing this on a
 * mutable type whose {@code copy()} is shallow states something untrue.
 *
 * <p>Not {@link Cloneable}, which declares no method at all, and not {@code Object.clone()}, which
 * is protected: neither can be called through a type parameter, so neither can carry a bound.
 *
 * @param <T> the implementing type itself, so a copy is never a sibling type
 */
public interface Copyable<T extends Copyable<T>> {

    /**
     * @return a value equal to this one that shares no mutable state with it, or {@code this} where
     *         the type is immutable and there is no mutable state to share
     */
    T copy();
}
