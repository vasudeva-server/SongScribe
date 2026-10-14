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

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Property} that holds its value itself, for state with no control behind it.
 * Also the change-only adapter over a derivation; see {@link Bindings#onNotify}.
 *
 * <p>EDT-only.
 */
public final class ValueProperty<T extends @Nullable Object> implements Property<T> {

    private final Observers observers = new Observers();
    private T value;

    public ValueProperty(T initial) {
        value = initial;
    }

    @Override
    public T get() {
        DependencyTracker.track(this);

        return value;
    }

    /**
     * Replaces the held value, and notifies observers only when the new value
     * differs from the current one by {@link Objects#equals}.
     *
     * <p>Writing the value already held is therefore silent, and that is the whole
     * point of this class as an effect source: it is what makes
     * {@link Bindings#onNotify} fire on a real transition rather than on every
     * write, no matter how often whatever feeds the property re-writes it.
     *
     * @param newValue the value to hold, never {@code null}
     * @effects replaces the held value, and on a transition notifies every
     *     observer. An observer that writes this property during the notification
     *     is notified in turn, in the same pass — the guard against a cycle is the
     *     equality stop, so an observer must eventually write a value that stops
     *     changing.
     * @invariant after this call {@link #get} returns {@code newValue}, whether or
     *     not observers were notified.
     */
    @Override
    public void set(T newValue) {
        if (Objects.equals(value, newValue)) {
            return;
        }

        value = newValue;
        observers.notifyObservers();
    }

    @Override
    public Subscription observe(Runnable onNotify) {
        return observers.add(onNotify);
    }
}
