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

/**
 * A {@link Property} that views storage owned by something else: a read function, a
 * write function, and the observer list whatever owns that storage drives.
 *
 * <p>The counterpart to {@link ValueProperty}, which holds its value itself. Use this
 * one when the value already lives somewhere — in a Swing control, in the preferences
 * store, in a model object — and a second copy would be a value that can disagree with
 * the real one.
 *
 * <p>This is the whole of what an adapter package needs from this package. Every view
 * differs only in its two functions and in what calls {@link #notifyObservers}, so
 * there is one implementation rather than one per kind of storage, and the framework's
 * own bookkeeping stays internal.
 *
 * <p>Registering whatever calls {@link #notifyObservers} — a Swing listener, a message
 * subscription — is the adapter's job, and so is deciding whether that registration
 * needs undoing. A listener on a control the dialog owns dies with the control; a
 * subscription to anything process-global does not, and a view over such storage must
 * outlive the dialogs that bind it rather than being created per dialog.
 *
 * @param <T> the viewed value's type
 */
public final class ViewProperty<T> implements Property<T> {

    /**
     * Whether a write made through this view notifies on its own.
     *
     * <p>Which one an adapter passes follows from the storage: a write route that
     * reports back on its own notifies by itself, and one that reports nothing has
     * nothing else to notify with.
     */
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

    /**
     * @param reader reads the current value from the underlying storage
     * @param writer writes a value to the underlying storage
     * @param writeNotification whether {@link #set} notifies, or the storage does
     */
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

    /**
     * Notifies this property's observers, called by whatever watches the underlying
     * storage when that storage reports a change.
     */
    public void notifyObservers() {
        observers.notifyObservers();
    }
}
