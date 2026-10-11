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
 * The mutation-recording state machine of a {@link Song} — the depth counters that decide
 * whether a change is recorded, replayed, suspended or guard-bypassed, and what the edit in
 * flight accumulates: its batch and the report of the changes it made on its own, delivered
 * together on one {@link SongDidChangeNotification} when the outermost bracket closes
 * ({@link #deliver}).
 *
 * <p>Four independent modes, each depth-counted so nesting is safe:
 * <ul>
 *   <li><b>Modification bracket</b> ({@link #withModification(Runnable)}) — mutations
 *       accumulate while open; the notification fires once when the outermost bracket
 *       closes.</li>
 *   <li><b>Suspension</b> ({@link #withoutMutationTracking}) — records nothing at all.</li>
 *   <li><b>Replay</b> ({@link #replay}) — mutations are still recorded, but companion
 *       side-work is suppressed because the batch already carries it, and no modification
 *       rule runs, so the replay's report records nothing and is delivered empty.</li>
 *   <li><b>Auto-maintenance</b> ({@link #withAutoMaintenance}) — the terminal policy is
 *       bypassed for internally-driven mutations.</li>
 * </ul>
 *
 * <p>One is owned by each {@code Song}, which delegates its whole bracket API here.
 * {@code Line} routes through {@code Song} in turn. See {@code docs/mutations.md}
 * for the emission rules callers must honor.
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

    /**
     * What one edit accumulates between the open and the close of its outermost bracket: the
     * op-name it captured, the mutations it recorded, and the changes it made on its own.
     */
    private static final class OpenEdit {

        // The explicit bracket label, or the Tier-A pending op-name held by UndoController,
        // resolved when the outermost bracket opened; null selects the type-based label.
        private final @Nullable String opName;

        // Mutable: each removal a modification rule records is appended while the rules run.
        private final List<Mutation> batch = new ArrayList<>();

        // Starts empty for every edit; a replay's stays empty, because nothing in a replay records.
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

    /** Returns {@code true} while a modification bracket is open. */
    public boolean isModifying() {
        return modificationDepth > 0;
    }

    /** Returns {@code true} while mutation tracking is suspended. */
    public boolean isMutationTrackingSuspended() {
        return suspensionDepth > 0;
    }

    /** Returns {@code true} while a recorded mutation batch is being replayed. */
    public boolean isReplaying() {
        return replayDepth > 0;
    }

    /** Returns {@code true} while the {@link Line} mutation guards are bypassed. */
    boolean isInAutoMaintenance() {
        return autoMaintenanceDepth > 0;
    }

    // ========== Mode brackets ==========

    /**
     * Runs {@code body} with the auto-maintenance depth raised so that the terminal
     * guards in {@link Line} are bypassed for the duration. Used by {@link Song#addLine} and
     * {@link Song#removeLine} to transfer the terminal without triggering the guard.
     *
     * <p>Re-entrant: {@link Song#removeLine} nests an {@link Song#addLine} call to repopulate
     * a song emptied by removing its sole line, so the guards stay bypassed until the
     * outermost call unwinds.
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
     * Runs {@code body} with mutation tracking suspended. Line-level mutations
     * invoked during {@code body} run silently: no notification is posted, no
     * undo entry is recorded, and the song's {@code modified} flag is
     * not set.
     * <p>
     * The caller is asserting that this run owes no undo entry of its own — either
     * because it is not a user edit at all (establishing, loading or repairing
     * document state), or because the entry for it is recorded elsewhere, as a grace
     * note's insertion is by the bracket that later pairs it with a host, or must not
     * exist at all, as a cancelled operation's must not. An edit that owes its own
     * entry goes through {@link #withModification(Runnable)}.
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
     * Suspends mutation tracking until the matching {@link #endSuspendMutationTracking()}.
     * Use {@link #withoutMutationTracking(Runnable)} when the suspended scope fits in a
     * single block; this pair exists for callers whose suspension scope crosses multiple
     * methods — the two file readers, {@code SongMapper.map} and {@code SongIO}.
     */
    public void beginSuspendMutationTracking() {
        suspensionDepth++;
    }

    /**
     * Resumes mutation tracking. Must be paired with a prior
     * {@link #beginSuspendMutationTracking()} call; calls without a matching
     * begin are a programming error and throw immediately.
     */
    public void endSuspendMutationTracking() {
        if (suspensionDepth <= 0) {
            throw new IllegalStateException("No matching beginSuspendMutationTracking");
        }

        suspensionDepth--;
    }

    /**
     * Replays a recorded batch: replay mode outside, a modification bracket inside, with
     * {@code body} applying the batch's records. The contract, and why the order is what it
     * is, are on {@link Song#replay}.
     *
     * @param body applies the records of one recorded batch
     */
    public void replay(Runnable body) {
        withReplay(() -> inBracket(null, asSupplier(body)));
    }

    /**
     * Runs {@code body} in replay mode: the batch being replayed already contains every
     * change, so the helpers' companion side-work (terminal maintenance, line defaults, span
     * invalidation, tuplet auto-removal, span merging) must not re-run — it would
     * double-apply changes the batch carries — and the {@link Line} terminal guards must
     * accept the legitimate intermediate states that arise mid-replay. Mutations are still
     * recorded, unlike {@link #withoutMutationTracking(Runnable)}. Nestable.
     *
     * @param body the work to run in replay mode; {@link #replay} passes the bracket that
     *     applies the batch
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

    /**
     * Opens a modification bracket, declaring {@code explicitLabel} as the op-name
     * (Tier B) if this is the outermost bracket. Mutations accumulate while the bracket is
     * open; the notification fires only when the outermost bracket closes.
     *
     * <p>The op-name and the edit's empty report are created only at the depth 0→1
     * transition, the op-name resolving as
     * {@code explicitLabel != null ? explicitLabel : pendingOpName}. A nested bracket inside an
     * already-open bracket never re-captures either.
     */
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
     * Closes a modification bracket whose body returned, delivering the edit
     * ({@link #deliver}) when it is the outermost one.
     *
     * <p>This is the one statement of when the modification rules ({@link ModificationRules})
     * run. Just before the outermost bracket closes, while it is still open, they run over the
     * accumulated batch, so the removals they record land in the batch that is delivered and
     * undo with the edit. They run once per edit, when every change and every restamp in the
     * bracket has landed, and only when all of these hold:
     * <ul>
     *   <li>the batch is not empty: an edit that recorded nothing broke nothing. A bracket
     *       closes in the tracking state it opened in, and a bracket opened while mutation
     *       tracking is suspended records nothing, so no rule runs while tracking is suspended,
     *       as it is for a file load;</li>
     *   <li>the bracket is not closing inside an undo or redo replay, whose batch already
     *       carries the removals the edit recorded;</li>
     *   <li>the body returned: a bracket whose body threw closes through {@link #closeBracket}
     *       instead, without the rules, because its edit stopped part way and its notes may not
     *       be restamped.</li>
     * </ul>
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

    /**
     * Closes a modification bracket without the modification rules, delivering the edit when
     * it is the outermost one.
     */
    private void closeBracket() {
        var edit = requireOpenEdit("closeBracket");
        modificationDepth--;

        if (modificationDepth == 0) {
            openEdit = null;
            deliver(edit);
        }
    }

    /**
     * The close of the outermost bracket, and the one place an edit is delivered.
     *
     * <p>Every reporter records a change into the report in the same edit in which it records
     * the mutation that makes the change, so a non-empty report never comes with an empty batch,
     * and the report needs no notification of its own: it travels with the batch. This is the
     * only point that knows the whole edit is over, so the report is built here, complete, with
     * its lines resolved against the song as the edit left it.
     *
     * <p>Runs even when the body or a rule throws, so no batch or report outlives its bracket.
     *
     * @param edit the edit the outermost bracket accumulated, already dropped from the session
     * @effects when {@code edit}'s batch is not empty, marks the song modified and posts one
     *     {@link SongDidChangeNotification} carrying the batch, the op-name and the built report,
     *     which is empty when the edit changed nothing on its own; when the batch is empty, posts
     *     nothing and leaves the modified flag as it is
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

    /**
     * Whether the outermost bracket, about to close with a non-empty batch, owes the
     * modification rules a run: it is not closing inside an undo or redo replay.
     */
    private boolean owesRules() {
        return !isReplaying();
    }

    /**
     * The edit in flight.
     *
     * @param operation the name of the operation that needs it, for the exception message
     * @return the edit the outermost open bracket created
     * @throws IllegalStateException if no modification bracket is open
     */
    private OpenEdit requireOpenEdit(String operation) {
        var edit = openEdit;

        if (edit == null) {
            throw new IllegalStateException(operation + " called outside a modification bracket");
        }

        return edit;
    }

    /**
     * Executes {@code body} inside a modification bracket, then posts a single
     * {@link SongDidChangeNotification} with all accumulated mutations. The bracket is
     * closed even if {@code body} throws, without running the modification rules over the
     * half-applied edit.
     */
    public void withModification(Runnable body) {
        inBracket(null, asSupplier(body));
    }

    /**
     * Executes {@code body} inside a modification bracket that declares {@code label}
     * as its op-name (Tier B), then posts a single {@link SongDidChangeNotification}.
     * The label is captured only if this is the outermost bracket: a labeled bracket nested
     * inside an open one never replaces the op-name the outermost bracket captured.
     */
    public void withModification(String label, Runnable body) {
        inBracket(label, asSupplier(body));
    }

    /**
     * The value-returning form of {@link #withModification(Runnable)}, for a body
     * whose outcome the caller must inspect after the bracket closes.
     *
     * @param body The modification to run
     * @return Whatever {@code body} returns
     */
    public <T> T withModificationResult(Supplier<T> body) {
        return inBracket(null, body);
    }

    /**
     * Runs {@code body} inside a modification bracket opened with {@code explicitLabel}
     * ({@link #beginModification}). The bracket closes through {@link #endModification} when
     * {@code body} returns, and through {@link #closeBracket} when it throws, so a half-applied
     * edit is never judged by the modification rules and no rule can replace the body's
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
     * Posts {@code message} to the message bus inside a modification bracket so
     * that the resulting mutations (from subscribers like {@code Song}'s
     * own {@code @Handler} methods) coalesce into a single
     * {@link SongDidChangeNotification}. Equivalent to
     * {@code withModification(() -> MessageCenter.post(message))} but cleaner
     * at the call site.
     */
    public void postWithModification(Message message) {
        withModification(() -> MessageCenter.post(message));
    }

    /**
     * Like {@link #postWithModification(Message)} but declares an explicit undo
     * op-name {@code label} for the resulting batch (see
     * {@link #withModification(String, Runnable)}).
     */
    public void postWithModification(String label, Message message) {
        withModification(label, () -> MessageCenter.post(message));
    }

    /**
     * Applies a single mutation within an open modification bracket.
     * <p>
     * Runs {@code mutator}, then records {@code mutation} in the accumulated list.
     *
     * <p>The caller's {@code withModification} bracket captures the op-name on the depth 0 to 1
     * transition — its explicit label if it has one, otherwise the pending op-name. Each
     * {@code applyChange} call inside the bracket runs its mutator and appends its mutation.
     * When the outermost bracket closes, the depth returns to 0 and a
     * {@code SongDidChangeNotification} carrying the accumulated mutations and the captured
     * op-name is posted. See {@code docs/undo.md} for the surrounding flow.
     *
     * @throws IllegalStateException if called outside a modification bracket
     */
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
     * Records {@code change} into the report of the edit in flight, which {@link #deliver}
     * posts with the edit's batch.
     * <p>
     * Records nothing while mutation tracking is suspended, where no bracket is required.
     *
     * @param change the change the song made on its own, as the user is told about it, naming
     *               lines of this session's song
     * @effects appends {@code change} to the open report, unless tracking is suspended; its lines
     *     are resolved to indices only when the edit is delivered
     * @throws IllegalStateException if tracking is on and no modification bracket is open
     */
    public void report(RecordedChange change) {
        if (isMutationTrackingSuspended()) {
            return;
        }

        requireOpenEdit("report").report.record(change);
    }
}
