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
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

/**
 * One binding: a target, a source to observe, and the value to write when that
 * source notifies.
 *
 * <p>Every binding carries its own re-entrancy flag. Writing a Swing control fires
 * that control's own notification, which arrives back at this binding; the flag is
 * raised for the duration of the write and a notification arriving while it is
 * raised is dropped. <b>The flag is per binding and never per {@link Bindings}</b> — a
 * dialog-wide flag also swallows legitimate propagation through the other bindings
 * that the write set off, which is the defect that makes a hand-wired dialog
 * re-synchronize its controls by hand afterwards.
 *
 * @param <T> the target's value type
 */
final class Binding<T> implements Subscription {

    /** Whether a newly created binding writes its target immediately. */
    enum InitialWrite {
        /** Evaluate and write at once, settling the target. */
        WRITE,

        /**
         * Record the current value as though it had been written, without writing.
         * For the reverse half of a two-way binding, whose target the forward half
         * has just settled.
         */
        SKIP
    }

    private final WritableValue<T> target;
    private final Supplier<T> value;
    private final Subscription observation;

    private boolean applying = false;
    private boolean written = false;
    private @Nullable T lastWritten = null;

    /**
     * Creates the binding, observes {@code source}, and settles the target according
     * to {@code initial}.
     *
     * @param target what this binding writes
     * @param source what this binding observes; every notification from it re-runs
     *     {@code value} and possibly writes {@code target}
     * @param value produces the value to write; it is run on every notification, so
     *     it must be cheap and free of side effects
     * @param initial whether to write the target now
     */
    Binding(WritableValue<T> target, ObservableValue<?> source, Supplier<T> value, InitialWrite initial) {
        this.target = target;
        this.value = value;
        observation = source.observe(this::apply);

        if (initial == InitialWrite.WRITE) {
            apply();
        } else {
            lastWritten = value.get();
            written = true;
        }
    }

    /**
     * Re-runs the value supplier and writes the target, unless the result is the
     * value this binding last wrote.
     *
     * <p>The comparison is against the last <i>written</i> value, never against the
     * target's current value, because a {@link WritableValue} has no readable
     * current value at all — a preview sink has a setter and no getter, and the
     * adapters over such state are built on exactly that. A binding that has never
     * written has no last value, so its first application always writes.
     *
     * <p>Does nothing when the notification is the one this binding's own write
     * caused.
     */
    private void apply() {
        if (applying) {
            return;
        }

        var next = value.get();

        if (written && Objects.equals(lastWritten, next)) {
            return;
        }

        applying = true;

        try {
            target.set(next);
        } finally {
            applying = false;
        }

        lastWritten = next;
        written = true;
    }

    @Override
    public void cancel() {
        observation.cancel();
    }
}
