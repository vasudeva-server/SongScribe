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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import com.uber.nullaway.annotations.Initializer;
import org.jspecify.annotations.Nullable;

/**
 * The derivation returned by {@link Bindings#computed}; the {@link Bindings} that created it
 * cancels its observations via {@link #cancel}.
 */
final class Computed<T extends @Nullable Object> implements ObservableValue<T>, Subscription {

    private final Supplier<T> body;
    private final Observers observers = new Observers();

    private final Map<ObservableValue<?>, Subscription> dependencies = new LinkedHashMap<>();

    /** Assigned by {@link #evaluate} before any read sees it; a null is the body's answer. */
    private T value;
    private boolean stale = true;
    private boolean evaluating = false;

    Computed(Supplier<T> body) {
        this.body = body;
    }

    @Override
    public T get() {
        DependencyTracker.track(this);

        if (stale) {
            return evaluate();
        }

        return value;
    }

    @Override
    public Subscription observe(Runnable onNotify) {
        return observers.add(onNotify);
    }

    /**
     * Until this runs, a derivation over anything outliving its dialog keeps the dialog and
     * everything the body captured reachable.
     *
     * <p>Idempotent. Marks the value stale, so a later read re-runs the body and re-links.
     */
    @Override
    public void cancel() {
        for (var observation : dependencies.values()) {
            observation.cancel();
        }

        dependencies.clear();
        stale = true;
    }

    @Initializer
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

    private void dependencyDidChange() {
        stale = true;
        observers.notifyObservers();
    }
}
