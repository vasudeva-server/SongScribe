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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * The derivation returned by {@link ObservableValue#computed}, whose contract this
 * class implements and does not extend.
 *
 * <p>Reached only through that factory: nothing outside this package can name the
 * type, so the derivation can only ever be used as an {@link ObservableValue}.
 *
 * @param <T> the derived value's type
 */
final class Computed<T> implements ObservableValue<T> {

    private final Supplier<T> body;
    private final Observers observers = new Observers();

    /**
     * The dependencies of the most recent run, each mapped to the observation held
     * on it. Insertion-ordered so an unlink pass is deterministic.
     */
    private final Map<ObservableValue<?>, Subscription> dependencies = new LinkedHashMap<>();

    private @Nullable T value = null;
    private boolean stale = true;
    private boolean evaluating = false;

    Computed(Supplier<T> body) {
        this.body = body;
    }

    @Override
    public T get() {
        DependencyTracker.track(this);
        var current = value;

        if (stale || current == null) {
            return evaluate();
        }

        return current;
    }

    @Override
    public Subscription observe(Runnable onChange) {
        return observers.add(onChange);
    }

    /**
     * Runs the body with recording armed, re-links the dependency set to what the
     * run actually read, and caches the result.
     *
     * @return the freshly derived value
     * @throws IllegalStateException if the body reads this computed, directly or
     *     through another computed — a cycle has no fixed point and would otherwise
     *     recurse until the stack ends
     */
    private T evaluate() {
        if (evaluating) {
            throw new IllegalStateException(
                "Cyclic computed: the body defined in " + body.getClass().getName() + " reads the value it defines"
            );
        }

        evaluating = true;
        var touched = new LinkedHashSet<ObservableValue<?>>();
        T result;

        try {
            result = DependencyTracker.recording(touched, body);
        } finally {
            evaluating = false;
        }

        relink(touched);
        value = result;
        stale = false;

        return result;
    }

    /**
     * Drops the observations on dependencies this run did not read and takes one on
     * each dependency it read for the first time.
     *
     * <p>The unlink half is what makes a branching body correct: a value read only
     * on the branch last taken must stop marking this computed stale once another
     * branch is being taken, or the derivation recomputes on changes that cannot
     * affect it.
     *
     * @param touched the values read during the run just finished
     */
    private void relink(Set<ObservableValue<?>> touched) {
        var linked = dependencies.entrySet().iterator();

        while (linked.hasNext()) {
            var dependency = linked.next();

            if (!touched.contains(dependency.getKey())) {
                dependency.getValue().cancel();
                linked.remove();
            }
        }

        for (var dependency : touched) {
            if (!dependencies.containsKey(dependency)) {
                dependencies.put(dependency, dependency.observe(this::dependencyDidChange));
            }
        }
    }

    /**
     * Marks the cached value stale and passes the notification on at once, so an
     * eager consumer — a binding — reads and thereby re-runs the body, while an
     * unread computed defers the work until somebody wants the value.
     */
    private void dependencyDidChange() {
        stale = true;
        observers.notifyObservers();
    }
}
