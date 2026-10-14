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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.undo.UndoController;

/**
 * The mutation-recording state machine of a {@link Song}: bracket, suspension, replay and
 * auto-maintenance modes, each depth-counted so nesting is safe. A replay judges nothing, so an
 * undo or a redo always delivers an empty report.
 *
 * <p>See {@code docs/mutations.md} for the emission rules callers must honor.
 */
public final class ModificationSession {

    private final Song song;

    // Modification bracket depth counter. Mutations and reported changes are accumulated
    // while > 0 and flushed as a single SongDidChangeNotification when depth returns to 0.
    private int modificationDepth = 0;

    // Suspension depth counter. While > 0, mutations apply but nothing is recorded:
    // applyChange runs the mutator without requiring an open bracket, and report records
    // nothing. The file readers (SongMapper.map and SongIO) hold it for the whole of a
    // load, and test setup holds it to populate lines without recording undo history.
    private int suspensionDepth = 0;

    // Replay depth counter. While > 0, undo/redo is mechanically re-applying a
    // recorded mutation batch: mutations are still recorded into the open
    // bracket, but companion side-work (auto-maintenance, span invalidation,
    // merging) is suppressed and the Line terminal guards are bypassed, because
    // the recorded batch already contains every change.
    private int replayDepth = 0;

    // While positive, Line mutation guards are bypassed so Song can auto-maintain
    // the terminal invariant without triggering the guards that protect against
    // user-driven invariant violations. Depth-counted because the maintenance
    // paths nest: removeLine calls addLine to repopulate an emptied song, and the
    // inner call must not disarm the guards the outer one is still relying on.
    private int autoMaintenanceDepth = 0;

    // The edit in flight: created when the outermost bracket opens (depth 0→1) and dropped
    // when it closes, so it is non-null exactly while a bracket is open. EDT-only, no
    // synchronization.
    private @Nullable OpenEdit openEdit = null;

    private final ModificationRules rules = new ModificationRules();

    private static final class OpenEdit {

        // The explicit bracket label, or the Tier-A pending op-name held by UndoController,
        // resolved when the outermost bracket opened; null selects the type-based label.
        private final @Nullable String opName;

        // Mutable: each removal a modification rule records is appended while the rules run.
        private final List<Mutation> batch = new ArrayList<>();

        // Starts empty for every edit.
        private final ChangeReport.Builder report;

        OpenEdit(Song song, @Nullable String opName) {
            this.opName = opName;
            this.report = new ChangeReport.Builder(song);
        }
    }

    ModificationSession(Song song) {
        this.song = song;
    }

    // ========== Mode predicates ==========

    public boolean isModifying() {
        return modificationDepth > 0;
    }

    public boolean isMutationTrackingSuspended() {
        return suspensionDepth > 0;
    }

    public boolean isReplaying() {
        return replayDepth > 0;
    }

    boolean isInAutoMaintenance() {
        return autoMaintenanceDepth > 0;
    }

    // ========== Mode brackets ==========

    /**
     * Re-entrant: {@link Song#removeLine} nests an {@link Song#addLine} call.
     */
    void withAutoMaintenance(Runnable body) {
        autoMaintenanceDepth++;

        try {
            body.run();
        } finally {
            autoMaintenanceDepth--;
        }
    }

    /**
     * The caller asserts that this run owes no undo entry of its own: it is not a user edit, or
     * its entry is recorded elsewhere or must not exist. An edit that owes its own entry goes
     * through {@link #withModification(Runnable)}.
     */
    public void withoutMutationTracking(Runnable body) {
        beginSuspendMutationTracking();

        try {
            body.run();
        } finally {
            endSuspendMutationTracking();
        }
    }

    /**
     * Only for a suspension scope that crosses methods, as in the two file readers; otherwise use
     * {@link #withoutMutationTracking(Runnable)}.
     */
    public void beginSuspendMutationTracking() {
        suspensionDepth++;
    }

    public void endSuspendMutationTracking() {
        if (suspensionDepth <= 0) {
            throw new IllegalStateException("No matching beginSuspendMutationTracking");
        }

        suspensionDepth--;
    }

    public void replay(Runnable body) {
        withReplay(() -> inBracket(null, asSupplier(body)));
    }

    /**
     * Companion side-work must not re-run, because the replayed batch already carries it, and
     * the {@link Line} terminal guards must accept the intermediate states a replay passes
     * through.
     */
    private void withReplay(Runnable body) {
        replayDepth++;

        try {
            body.run();
        } finally {
            replayDepth--;
        }
    }

    // ========== Modification brackets ==========

    private void beginModification(@Nullable String explicitLabel) {
        if (modificationDepth == 0) {
            // UndoController holds the Tier-A pending op-name, set by the UIAction
            // template around synchronous action dispatch (see UIAction.actionPerformed);
            // explicitLabel is the Tier-B name from the labeled withModification overload.
            openEdit = new OpenEdit(song, explicitLabel != null ? explicitLabel : UndoController.getPendingOpName());
        }

        modificationDepth++;
    }

    /**
     * The one statement of when the modification rules ({@link ModificationRules}) run: once per
     * edit, just before the outermost bracket closes, so the removals they record undo with the
     * edit. They do not run on an empty batch (so never while tracking is suspended, as in a file
     * load), inside a replay, or when the body threw, because that edit stopped part way.
     */
    private void endModification() {
        var edit = requireOpenEdit("endModification");

        try {
            if (modificationDepth == 1 && !edit.batch.isEmpty() && owesRules()) {
                // A copy: each removal a rule records is appended to the live batch.
                rules.apply(song, List.copyOf(edit.batch), edit.report);
            }
        } finally {
            closeBracket();
        }
    }

    private void closeBracket() {
        var edit = requireOpenEdit("closeBracket");
        modificationDepth--;

        if (modificationDepth == 0) {
            openEdit = null;
            deliver(edit);
        }
    }

    /**
     * The one place an edit is delivered. A non-empty report never comes with an empty batch,
     * because the report travels with the batch. Runs even when the body or a rule throws, so no
     * batch or report outlives its bracket.
     */
    private void deliver(OpenEdit edit) {
        if (edit.batch.isEmpty()) {
            return;
        }

        song.setModified(true);
        // Wrap-and-transfer ownership: the notification constructor stores the list
        // directly, and the edit holding the only other reference has been dropped, so
        // wrapping once here spares the notification a defensive copy.
        var mutations = Collections.unmodifiableList(edit.batch);
        MessageCenter.post(new SongDidChangeNotification(mutations, song, edit.report.build(), edit.opName));
    }

    private boolean owesRules() {
        return !isReplaying();
    }

    private OpenEdit requireOpenEdit(String operation) {
        var edit = openEdit;

        if (edit == null) {
            throw new IllegalStateException(operation + " called outside a modification bracket");
        }

        return edit;
    }

    /**
     * The bracket closes even if {@code body} throws, without running the modification rules over
     * the half-applied edit.
     */
    public void withModification(Runnable body) {
        inBracket(null, asSupplier(body));
    }

    /**
     * A labeled bracket nested inside an open one never replaces the outermost bracket's op-name.
     */
    public void withModification(String label, Runnable body) {
        inBracket(label, asSupplier(body));
    }

    public <T> T withModificationResult(Supplier<T> body) {
        return inBracket(null, body);
    }

    /**
     * A body that throws closes through {@link #closeBracket}, so no rule can replace its
     * exception with one of its own.
     */
    private <T extends @Nullable Object> T inBracket(@Nullable String explicitLabel, Supplier<T> body) {
        beginModification(explicitLabel);
        T result;

        try {
            result = body.get();
        } catch (Throwable thrown) {
            closeBracket();
            throw thrown;
        }

        endModification();
        return result;
    }

    private static Supplier<@Nullable Void> asSupplier(Runnable body) {
        return () -> {
            body.run();
            return null;
        };
    }

    /**
     * The mutations that subscribers make in response coalesce into one
     * {@link SongDidChangeNotification}.
     */
    public void postWithModification(Message message) {
        withModification(() -> MessageCenter.post(message));
    }

    public void postWithModification(String label, Message message) {
        withModification(label, () -> MessageCenter.post(message));
    }

    public void applyChange(Mutation mutation, Runnable mutator) {
        // Suspended tracking (e.g. a file load): the state change applies but nothing is
        // recorded, so no SongDidChangeNotification fires mid-load. Suspension also lifts the
        // bracket requirement below — a load runs no bracket at all.
        if (isMutationTrackingSuspended()) {
            mutator.run();
            return;
        }

        var edit = requireOpenEdit("applyChange");
        mutator.run();
        edit.batch.add(mutation);
    }

    /**
     * @param change names lines of this session's song
     */
    public void report(RecordedChange change) {
        if (isMutationTrackingSuspended()) {
            return;
        }

        requireOpenEdit("report").report.record(change);
    }
}
