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

import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Collects the {@link ObservableValue}s read while a derivation body runs, so a
 * {@code computed} can discover its dependencies rather than declare them.
 *
 * <p>Only {@link Bindings#computed} arms the recording; a function passed to {@code bind}
 * acquires no dependencies.
 */
final class DependencyTracker {

    private static final ThreadLocal<@Nullable Set<ObservableValue<?>>> recordingInto = new ThreadLocal<>();

    private DependencyTracker() {}

    /**
     * Records only reads made on this thread during this call; a deferred read is not a
     * dependency. {@code into} is not cleared first.
     */
    static <T> T recording(Set<ObservableValue<?>> into, Supplier<T> body) {
        var previous = recordingInto.get();
        recordingInto.set(into);

        try {
            return body.get();
        } finally {
            if (previous == null) {
                recordingInto.remove();
            } else {
                recordingInto.set(previous);
            }
        }
    }

    /** Every {@link ObservableValue#get} implementation must call this; one that does not is invisible to {@code computed}. */
    static void track(ObservableValue<?> read) {
        var into = recordingInto.get();

        if (into != null) {
            into.add(read);
        }
    }
}
