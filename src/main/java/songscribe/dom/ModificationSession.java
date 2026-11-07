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
package songscribe.dom;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import songscribe.message.Message;
import songscribe.message.mutation.Mutation;

/**
 * The recording policy of a {@link Song}: what a change to the song leaves behind — an undo entry,
 * a {@link songscribe.message.notification.SongDidChangeNotification}, a report entry, or
 * nothing.
 *
 * <p>A song holds one session at a time, and the session decides which policy every change
 * follows:
 *
 * <ul>
 * <li>{@link EditSession} is the policy of a song open for editing: a change is recorded into a
 * modification bracket, and the outermost bracket delivers the edit as one undo entry with its
 * report.
 * <li>{@link LoadSession} is the policy of a song being built from a file: a change records
 * nothing for undo and posts nothing, and every reported change goes into the one report of the
 * load.
 * </ul>
 *
 * <p>Only one policy has {@link #replay}, {@link #postWithModification} and {@link #endLoad}. An
 * undo or a redo replays, and a post coalesces the edits its subscribers make, so both exist only
 * for a song open for editing; only a load ends a load. Each of the three throws
 * {@link IllegalStateException} in the policy that lacks it, because no caller of that policy has
 * a use for it.
 */
sealed abstract class ModificationSession permits EditSession, LoadSession {

    abstract boolean isModifying();

    abstract boolean isMutationTrackingSuspended();

    abstract boolean isReplaying();

    /**
     * While {@code true}, every change leaves the song in a state an edited song may hold. While
     * {@code false}, during a load or a suspension, the song may pass through states that an
     * edited song may not hold, and nothing repairs them as they arise.
     */
    abstract boolean enforcesEditInvariants();

    /**
     * The caller asserts that this run owes no undo entry of its own: it is not a user edit, or
     * its entry is recorded elsewhere or must not exist. An edit that owes its own entry goes
     * through {@link #withModification(Runnable)}.
     */
    abstract void withoutMutationTracking(Runnable body);

    /**
     * Carries the assertion stated on {@link #withoutMutationTracking(Runnable)}.
     */
    abstract <T extends @Nullable Object> T withoutMutationTrackingResult(Supplier<T> body);

    void replay(Runnable body) {
        throw unsupported("replay");
    }

    abstract void withModification(Runnable body);

    abstract void withModification(String label, Runnable body);

    abstract <T> T withModificationResult(Supplier<T> body);

    abstract <T> T withModificationResult(String label, Supplier<T> body);

    void postWithModification(Message message) {
        throw unsupported("postWithModification");
    }

    abstract void applyChange(Mutation mutation, Runnable mutator);

    /**
     * @param change names lines of this session's song that the same edit or load does not
     *               remove
     */
    abstract void report(RecordedChange change);

    /**
     * Must be called once.
     */
    ChangeReport endLoad() {
        throw unsupported("endLoad");
    }

    private IllegalStateException unsupported(String operation) {
        return new IllegalStateException(operation + " called on " + getClass().getSimpleName());
    }
}
