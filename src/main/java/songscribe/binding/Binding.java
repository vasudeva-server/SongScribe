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
 * <p>Re-entrancy is handled by a {@link WriteGuard}, raised for the duration of this
 * binding's own write. Writing a Swing control fires that control's own notification,
 * which arrives back synchronously while the guard is raised, and is dropped. A
 * one-way binding holds its own guard; the two halves of a two-way binding share one,
 * so neither pushes back what the other has just brought in. See {@code WriteGuard}
 * for why the scope is never wider than that.
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
    private final WriteGuard guard;

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
     *     it must be cheap and free of side effects. It is also run while a write is
     *     already in progress within {@code guard} — that is how the declined write
     *     records what the target now holds — so it must answer correctly part-way
     *     through the other half's write, and not only after one has finished.
     * @param initial whether to write the target now
     * @param guard the re-entrancy scope this binding writes within, shared with the
     *     other half when this is one side of a two-way binding
     */
    Binding(
        WritableValue<T> target,
        ObservableValue<?> source,
        Supplier<T> value,
        InitialWrite initial,
        WriteGuard guard
    ) {
        this.target = target;
        this.value = value;
        this.guard = guard;
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
     * <p>Writes nothing when a write is already in progress within this binding's
     * guard — the notification this binding's own write caused, or, for one half of a
     * two-way binding, the one the other half's write caused. It still records the
     * value as written, because the target now holds it: the write this binding is
     * declining to make has already been made, by whatever raised the guard. Skipping
     * that record would leave the last-written value stale, and the unchanged-value
     * stop below would then decline a later write that was genuinely needed.
     */
    private void apply() {
        if (guard.isRaised()) {
            lastWritten = value.get();
            written = true;

            return;
        }

        var next = value.get();

        if (written && Objects.equals(lastWritten, next)) {
            return;
        }

        guard.raise();

        try {
            target.set(next);
        } finally {
            guard.lower();
        }

        lastWritten = next;
        written = true;
    }

    @Override
    public void cancel() {
        observation.cancel();
    }
}
