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

import songscribe.message.mutation.Mutation;

/**
 * The recording policy of a load: a change applies and records nothing for undo, nothing is
 * posted, and every reported change goes into the load's report, in the order reported.
 *
 * <p>A modification bracket groups nothing in a load, so a bracket runs its body and nothing
 * else, and no bracket is required. A load is not a suspension: a suspension nested in a load
 * still reports into the load's report.
 */
final class LoadSession extends ModificationSession {

    private final ChangeReport.Builder report;

    LoadSession(Song song) {
        this.report = new ChangeReport.Builder(song);
    }

    @Override
    ChangeReport endLoad() {
        return report.build();
    }

    @Override
    boolean isModifying() {
        return false;
    }

    @Override
    boolean isMutationTrackingSuspended() {
        return false;
    }

    @Override
    boolean isReplaying() {
        return false;
    }

    @Override
    boolean enforcesEditInvariants() {
        return false;
    }

    @Override
    void withoutMutationTracking(Runnable body) {
        body.run();
    }

    @Override
    <T extends @Nullable Object> T withoutMutationTrackingResult(Supplier<T> body) {
        return body.get();
    }

    @Override
    void withModification(Runnable body) {
        body.run();
    }

    @Override
    void withModification(String label, Runnable body) {
        body.run();
    }

    @Override
    <T> T withModificationResult(Supplier<T> body) {
        return body.get();
    }

    @Override
    <T> T withModificationResult(String label, Supplier<T> body) {
        return body.get();
    }

    @Override
    void applyChange(Mutation mutation, Runnable mutator) {
        mutator.run();
    }

    @Override
    void report(RecordedChange change) {
        report.record(change);
    }
}
