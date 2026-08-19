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
package songscribe.ui.binding;

import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * Collects the {@link ObservableValue}s read while a derivation body runs, so a
 * {@code computed} can discover its dependencies rather than declare them.
 *
 * <p>Only {@link Bindings#computed} arms the recording. The transforming
 * {@code bind} overloads subscribe to the source they were handed and never run the
 * tracker, so a function passed to {@code bind} may read whatever it likes without
 * acquiring a dependency on it.
 *
 * <p>The recording set is a {@link ThreadLocal} rather than a static field. Nothing
 * in this package is synchronized, and the package's EDT-only invariant is what
 * makes that safe; a thread-local keeps a stray off-EDT derivation from corrupting
 * the EDT's recording as well, which a shared static field would not.
 */
final class DependencyTracker {

    private static final ThreadLocal<@Nullable Set<ObservableValue<?>>> recordingInto = new ThreadLocal<>();

    private DependencyTracker() {}

    /**
     * Runs {@code body} with {@code into} armed as the recording set, so every
     * {@link #track} call made during the run lands in it.
     *
     * <p>Recordings nest: the set armed on entry is restored on the way out, so an
     * inner derivation evaluated by an outer one collects its own dependencies and
     * the outer one keeps collecting its own afterwards. The restore happens in a
     * {@code finally}, so a body that throws cannot leave recording armed and
     * poison every later read on this thread.
     *
     * <p>Only values read on this thread, during this call, are recorded. A read
     * deferred to a later event — anything scheduled rather than run — is not part
     * of the set, and the derivation will not see that value change.
     *
     * @param <T> the body's result type
     * @param into the set to record into; it is not cleared first, so a caller
     *     reusing a set is responsible for its prior contents
     * @param body the derivation to run
     * @return whatever {@code body} returns
     * @effects adds to {@code into} every observable value read during the run.
     *     Leaves the previously armed recording set armed on return, whether the
     *     body returned or threw.
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

    /**
     * Records {@code read} as a dependency of the derivation in progress, if there
     * is one.
     *
     * <p>A no-op when no recording is armed, which is the common case: most reads
     * are made by application code rather than by a derivation body. Every
     * {@link ObservableValue#get} implementation in this package calls this before
     * answering; one that does not is invisible to every {@code computed} that
     * reads it.
     *
     * @param read the value being read
     * @effects adds {@code read} to the armed recording set, if any. Recording the
     *     same value twice in one run is harmless — the set makes it one dependency.
     */
    static void track(ObservableValue<?> read) {
        var into = recordingInto.get();

        if (into != null) {
            into.add(read);
        }
    }
}
