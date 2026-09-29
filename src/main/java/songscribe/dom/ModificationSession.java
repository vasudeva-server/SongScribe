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
import songscribe.message.notification.ChangesWereReportedNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.undo.UndoController;

/**
 * The mutation-recording state machine of a {@link Song} — the depth counters that decide
 * whether a change is recorded, replayed, suspended or guard-bypassed, and the accumulated
 * batch that becomes one {@link SongDidChangeNotification}, and the report of the changes the
 * edit made on its own, which becomes one {@link ChangesWereReportedNotification}.
 *
 * <p>Four independent modes, each depth-counted so nesting is safe:
 * <ul>
 *   <li><b>Modification bracket</b> ({@link #beginModification}/{@link #endModification}) —
 *       mutations accumulate while open; the notification fires once when the outermost
 *       bracket closes.</li>
 *   <li><b>Suspension</b> ({@link #withoutMutationTracking}) — records nothing at all.</li>
 *   <li><b>Replay</b> ({@link #withReplay}) — mutations are still recorded, but companion
 *       side-work is suppressed because the batch already carries it.</li>
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

    // Modification bracket depth counter. Mutations are accumulated while > 0 and
    // flushed as a single SongDidChangeNotification when depth returns to 0.
    private int modificationDepth = 0;

    // Suspension depth counter. While > 0, Line.applyChange bypasses the strict
    // bracket check and runs the mutator directly. Used by test setup that
    // populates lines without emitting notifications or recording undo history.
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

    @Nullable
    private ArrayList<Mutation> accumulatedMutations = null;

    // The op-name resolved when the outermost bracket opened (depth 0→1), held for
    // the lifetime of that bracket and shipped on the SongDidChangeNotification at
    // depth 1→0. Resolves an explicit bracket label, or the Tier-A pending op-name
    // held by UndoController. EDT-only, no synchronization.
    @Nullable
    private String capturedOpName = null;

    // The report of the edit in flight: created when the outermost bracket opens (depth 0→1)
    // and dropped when it closes, so it is non-null exactly while a bracket is open.
    private @Nullable ChangeReport report = null;

    private final ModificationRules rules = new ModificationRules();

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
     * single block; this pair exists for callers (e.g. SAX parsing) whose suspension
     * scope crosses multiple methods.
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
     * Runs {@code body} in replay mode. Used by the undo engine while it
     * mechanically re-applies a recorded mutation batch inside a modification
     * bracket that {@code body} opens: the batch already contains every change, so the
     * helpers' companion side-work (terminal maintenance, line defaults, span
     * invalidation, tuplet auto-removal, span merging) must not re-run — it
     * would double-apply changes the batch carries — and the {@link Line}
     * terminal guards must accept the legitimate intermediate states that
     * arise mid-replay. Mutations are still recorded into the bracket, unlike
     * {@link #withoutMutationTracking(Runnable)}. Nestable.
     *
     * <p>The bracket opens and closes inside replay mode, so the modification rules do not run
     * when it closes (see {@link #endModification}), and nothing is reported.
     */
    public void withReplay(Runnable body) {
        replayDepth++;

        try {
            body.run();
        } finally {
            replayDepth--;
        }
    }

    // ========== Modification brackets ==========

    /**
     * Opens a modification bracket with no explicit label. Mutations accumulate while
     * the bracket is open. Brackets may be nested; the notification fires only when the
     * outermost bracket closes.
     */
    public void beginModification() {
        beginModification(null);
    }

    /**
     * Opens a modification bracket, declaring {@code explicitLabel} as the op-name
     * (Tier B) if this is the outermost bracket. Mutations accumulate while the bracket
     * is open; the notification fires only when the outermost bracket closes.
     *
     * <p>The op-name is captured only at the depth 0→1 transition, resolving as
     * {@code explicitLabel != null ? explicitLabel : pendingOpName}. A nested labeled
     * bracket inside an already-open bracket never re-captures.
     */
    public void beginModification(@Nullable String explicitLabel) {
        if (modificationDepth == 0) {
            // UndoController holds the Tier-A pending op-name, set by the UIAction
            // template around synchronous action dispatch (see UIAction.actionPerformed);
            // explicitLabel is the Tier-B name from the labeled withModification overload.
            capturedOpName = explicitLabel != null ? explicitLabel : UndoController.getPendingOpName();
            report = new ChangeReport();
        }

        modificationDepth++;
    }

    /**
     * Closes a modification bracket. When the outermost bracket closes and at least one
     * mutation was accumulated, marks the song modified and posts a single
     * {@link SongDidChangeNotification} carrying all accumulated mutations.
     *
     * <p>Just before the outermost bracket closes, while it is still open, the modification
     * rules ({@link ModificationRules}) run over the accumulated batch, so the removals they
     * record land in the batch that is delivered, and each removal is recorded into the edit's
     * report. They run once per edit, when every change and every restamp in the bracket has
     * landed. They do not run on an empty batch, nor on a bracket that closes inside an undo or
     * redo replay, whose batch already carries the removals the edit recorded, nor while
     * mutation tracking is suspended, where nothing is recorded and loaded content is judged by
     * the load. A bracket whose body threw closes through {@link #closeBracket} instead, without
     * the rules: its edit stopped part way, so its notes may not be restamped.
     *
     * <p>When the edit's report is not empty, a {@link ChangesWereReportedNotification} follows
     * the song notification — this is the only point that knows the whole edit is over, and a
     * report shown any earlier would sit in front of a score not yet relaid out.
     */
    public void endModification() {
        var batch = accumulatedMutations;
        var openReport = report;

        try {
            if (modificationDepth == 1 && batch != null && openReport != null && owesRules()) {
                // A copy: each removal a rule records is appended to the live batch.
                rules.apply(song, List.copyOf(batch), openReport);
            }
        } finally {
            closeBracket();
        }
    }

    /**
     * Closes a modification bracket without the modification rules, delivering the batch when it
     * is the outermost one.
     */
    private void closeBracket() {
        modificationDepth--;

        if (modificationDepth == 0) {
            deliverBatch();
        }
    }

    /**
     * The close of the outermost bracket: delivers the accumulated batch, then the edit's report
     * when it is not empty. Runs even when the body or a rule
     * throws, so no batch or report outlives its bracket.
     */
    private void deliverBatch() {
        var editReport = report;
        report = null;

        if (accumulatedMutations != null) {
            song.setModified(true);
            // Wrap-and-transfer ownership: the notification constructor stores the
            // list directly, so we wrap once here instead of letting it defensively
            // copy a list whose only reference is about to be dropped.
            var mutations = Collections.unmodifiableList(accumulatedMutations);
            accumulatedMutations = null;
            var opName = capturedOpName;
            capturedOpName = null;
            MessageCenter.post(new SongDidChangeNotification(mutations, song, opName));
        }

        if (editReport != null && !editReport.isEmpty()) {
            MessageCenter.post(new ChangesWereReportedNotification(editReport));
        }
    }

    /**
     * Whether the outermost bracket, about to close with a batch, owes the modification rules a
     * run: it is not closing inside an undo or redo replay, nor under suspended tracking.
     */
    private boolean owesRules() {
        return !isReplaying() && !isMutationTrackingSuspended();
    }

    /**
     * Executes {@code body} inside a modification bracket, then posts a single
     * {@link SongDidChangeNotification} with all accumulated mutations.
     * Prefer this over {@link #beginModification()} / {@link #endModification()} to ensure
     * the depth counter is always balanced even if {@code body} throws.
     */
    public void withModification(Runnable body) {
        inBracket(null, asSupplier(body));
    }

    /**
     * Executes {@code body} inside a modification bracket that declares {@code label}
     * as its op-name (Tier B), then posts a single {@link SongDidChangeNotification}.
     * The label is captured only if this is the outermost bracket (see
     * {@link #beginModification(String)}).
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
     * Runs {@code body} inside a modification bracket opened with {@code explicitLabel}. The
     * bracket closes through {@link #endModification} when {@code body} returns, and through
     * {@link #closeBracket} when it throws, so a half-applied edit is never judged by the
     * modification rules and no rule can replace the body's exception with one of its own.
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

        if (modificationDepth == 0) {
            throw new IllegalStateException("applyChange called outside a modification bracket");
        }

        mutator.run();

        if (accumulatedMutations == null) {
            accumulatedMutations = new ArrayList<>();
        }

        accumulatedMutations.add(mutation);
    }

    /**
     * Records {@code change} into the report of the edit in flight, which is posted in a
     * {@link ChangesWereReportedNotification} when the outermost modification bracket closes.
     * <p>
     * Records nothing while mutation tracking is suspended, where no bracket is required, nor
     * during an undo or redo replay: replay re-applies the removals the edit already reported,
     * and does not report them again.
     *
     * @param change the change the song made on its own, as the user is told about it
     * @effects appends {@code change} to the open report, unless tracking is suspended or a
     *     replay is in progress
     * @throws IllegalStateException if tracking is on and no modification bracket is open
     */
    public void report(ReportedChange change) {
        if (isMutationTrackingSuspended()) {
            return;
        }

        var openReport = report;

        if (openReport == null) {
            throw new IllegalStateException("report called outside a modification bracket");
        }

        if (isReplaying()) {
            return;
        }

        openReport.record(change);
    }
}
