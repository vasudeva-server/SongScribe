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
 * The re-entrancy flag a {@link Binding} raises for the duration of its own write,
 * and the scope over which that write's echo is absorbed.
 *
 * <p>Writing a target notifies it, and the notification arrives back synchronously
 * while the flag is still raised, so a binding declines to act on it. <b>The scope is
 * one binding, or the two halves of one two-way binding, and never a whole
 * {@link Bindings}</b> — a dialog-wide flag also swallows legitimate propagation
 * through the other bindings the write set off, which is the defect that makes a
 * hand-wired dialog re-synchronize its controls afterwards.
 *
 * <p>A two-way pair shares one guard because its two halves are one relationship
 * rather than two. Without sharing, a change made by neither side — the store behind
 * a view being written by something else — travels in, moves the near side, and is
 * pushed straight back out by the far side: a write nobody asked for, with whatever
 * that write costs downstream. With sharing, the far half declines while the near
 * half is applying, and the value simply arrives.
 *
 * <p>Not thread-safe, and not intended to be; every call happens on the EDT, per the
 * package invariant.
 */
final class WriteGuard {

    private boolean raised = false;

    /**
     * @return whether a write is in progress within this guard's scope
     */
    boolean isRaised() {
        return raised;
    }

    /**
     * Raises the flag for the duration of a write.
     *
     * @effects raises the flag. A caller reaches this only after {@link #isRaised}
     *     answered false, so there is no already-raised case to reject.
     */
    void raise() {
        raised = true;
    }

    /** Lowers the flag. Called from a {@code finally}, so a failed write cannot strand it. */
    void lower() {
        raised = false;
    }
}
