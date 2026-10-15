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

package songscribe.ui.selection;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * A value derived from one selection, kept until a different selection asks for it or the
 * owner invalidates it.
 */
final class SelectionCache<V> {

    private record Entry<V>(ElementSelection selection, V value) {}

    private @Nullable Entry<V> entry = null;

    /** The cached value when {@code selection} equals the one it came from, else a fresh one from {@code derive}. */
    V get(ElementSelection selection, Function<? super ElementSelection, ? extends V> derive) {
        var current = entry;

        if (current == null || !current.selection().equals(selection)) {
            current = new Entry<>(selection, derive.apply(selection));
            entry = current;
        }

        return current.value();
    }

    void invalidate() {
        entry = null;
    }
}
