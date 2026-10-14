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

/**
 * The re-entrancy flag a {@link Binding} raises during its own write, so the echo of that
 * write is dropped.
 *
 * <p>The scope is one binding, or the two halves of one two-way binding, never a whole
 * {@link Bindings}: a wider flag swallows legitimate propagation through other bindings.
 * A two-way pair shares one guard, or a change from outside the pair is pushed straight
 * back out by the far half.
 *
 * <p>EDT-only; not thread-safe.
 */
final class WriteGuard {

    private boolean raised = false;

    boolean isRaised() {
        return raised;
    }

    void raise() {
        raised = true;
    }

    void lower() {
        raised = false;
    }
}
