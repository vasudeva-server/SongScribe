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

import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Property} that views storage owned by something else, never a second copy of it.
 *
 * <p>Registering whatever calls {@link #notifyObservers}, and undoing it, is the adapter's
 * job. A view over process-global storage must outlive the dialogs that bind it rather than
 * being created per dialog.
 */
public final class ViewProperty<T extends @Nullable Object> implements Property<T> {

    public enum WriteNotification {
        /** The storage reports a write on its own, so notifying here too would notify twice. */
        FROM_SOURCE,

        /** The storage reports nothing, so the write is the notification. */
        FROM_WRITE
    }

    private final Observers observers = new Observers();
    private final Supplier<T> reader;
    private final Consumer<T> writer;
    private final WriteNotification writeNotification;

    public ViewProperty(Supplier<T> reader, Consumer<T> writer, WriteNotification writeNotification) {
        this.reader = reader;
        this.writer = writer;
        this.writeNotification = writeNotification;
    }

    @Override
    public T get() {
        DependencyTracker.track(this);

        return reader.get();
    }

    @Override
    public void set(T value) {
        writer.accept(value);

        if (writeNotification == WriteNotification.FROM_WRITE) {
            observers.notifyObservers();
        }
    }

    @Override
    public Subscription observe(Runnable onNotify) {
        return observers.add(onNotify);
    }

    public void notifyObservers() {
        observers.notifyObservers();
    }
}
