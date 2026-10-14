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
 * A value that can be read and observed, but not written — only ever a bind <i>source</i>,
 * which is what stops a caller binding into a {@link Bindings#computed}.
 *
 * <p>EDT-only. Values are replaced, never mutated; see the package documentation.
 */
public interface ObservableValue<T extends @Nullable Object> {

    /**
     * An implementation must call {@code DependencyTracker.track(this)} before answering;
     * one that does not is invisible to every {@link Bindings#computed}, which then never
     * recomputes, with nothing reporting it.
     *
     * @return the current value, never {@code null}
     */
    T get();

    /**
     * A notification is not a change: what notifies is each implementation's own promise. See
     * {@link Bindings#onNotify}. An uncancelled registration keeps {@code onNotify} and
     * everything it captures reachable for as long as this value is.
     */
    Subscription observe(Runnable onNotify);
}
