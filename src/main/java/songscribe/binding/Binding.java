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
 * One binding: a target, a source to observe, and the value to write when that source notifies.
 * The two halves of a two-way binding share one {@link WriteGuard}.
 */
final class Binding<T extends @Nullable Object> implements Subscription {

    enum InitialWrite {
        WRITE,

        /** For the reverse half of a two-way binding, whose target the forward half has just settled. */
        SKIP
    }

    private final WritableValue<T> target;
    private final Supplier<T> value;
    private final Subscription observation;
    private final WriteGuard guard;

    private boolean written = false;
    private @Nullable T lastWritten = null;

    /**
     * {@code value} is run on every notification, including part-way through the other half's
     * write, so it must be cheap, free of side effects, and correct at that moment.
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
     * Compares against the last <i>written</i> value, never the target's, because a
     * {@link WritableValue} has no readable value. A declined write is still recorded as
     * written, or the unchanged-value stop would later decline a write that was needed.
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
