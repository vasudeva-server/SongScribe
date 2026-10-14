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
package songscribe.binding;

import org.jspecify.annotations.Nullable;

/**
 * A sink: something the framework can write, and nothing else. With no readable value and no
 * notification it can only be a bind <i>target</i>, so the compiler refuses a binding that
 * would take it as a source and silently never run.
 *
 * <p>EDT-only.
 */
@FunctionalInterface
public interface WritableValue<T extends @Nullable Object> {

    /**
     * An implementation must tolerate being given the value it already holds.
     *
     * @param value the value to write, never {@code null}
     */
    void set(T value);
}
