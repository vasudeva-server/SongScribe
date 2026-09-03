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
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.TupletsWereRemovedNotification;
import songscribe.undo.UndoController;

/**
 * The mutation-recording state machine of a {@link Song} — the depth counters that decide
 * whether a change is recorded, replayed, suspended or guard-bypassed, and the accumulated
 * batch that becomes one {@link SongDidChangeNotification}.
 *
 * <p>Five independent modes, each depth-counted so nesting is safe:
 * <ul>
 *   <li><b>Modification bracket</b> ({@link #beginModification}/{@link #endModification}) —
 *       mutations accumulate while open; the notification fires once when the outermost
 *       bracket closes.</li>
 *   <li><b>Suspension</b> ({@link #withoutMutationTracking}) — records nothing at all.</li>
 *   <li><b>Replay</b> ({@link #withReplay}) — mutations are still recorded, but companion
 *       side-work is suppressed because the batch already carries it.</li>
 *   <li><b>Auto-maintenance</b> ({@link #withAutoMaintenance}) — the {@link Line} guards
 *       are bypassed for internally-driven mutations.</li>
 *   <li><b>Beat-defining edit</b> ({@link #withBeatDefiningEdit}) — the chokepoint that
 *       drops tuplets a new beat context invalidates, and reports the loss once.</li>
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

    // Nesting depth of withBeatDefiningEdit, so that an inner beat-defining write that
    // does the removing is still reported by the outermost call, which is the one whose
    // return value the initiating UI reads.
    private int beatDefiningEditDepth = 0;

    // Whether the outermost in-flight beat-defining edit has removed any tuplet. Reset
    // when that outermost call unwinds.
    private boolean beatEditRemovedTuplets = false;

    // Why the in-flight edit removed tuplets, or null when it removed none. Armed by the
    // outermost beat-defining edit and by the paste path, and disarmed by endModification,
    // which posts TupletsWereRemovedNotification. Deferring the post to the outermost
    // bracket keeps a modal warning from going up while the score is still painted as it
    // was before the removals, and collapses an edit that nests several beat-defining
    // writes into the one report the user should see.
    private TupletsWereRemovedNotification.@Nullable Cause tupletsRemovedNoticeCause = null;

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
     * Intended for test setup that populates lines outside a user-driven
     * modification bracket. Production code should use
     * {@link #withModification(Runnable)} instead.
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
     * mechanically re-applies a recorded mutation batch inside an open
     * modification bracket: the batch already contains every change, so the
     * helpers' companion side-work (terminal maintenance, line defaults, span
     * invalidation, tuplet auto-removal, span merging) must not re-run — it
     * would double-apply changes the batch carries — and the {@link Line}
     * terminal guards must accept the legitimate intermediate states that
     * arise mid-replay. Mutations are still recorded into the bracket, unlike
     * {@link #withoutMutationTracking(Runnable)}. Nestable.
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
        }

        modificationDepth++;
    }

    /**
     * Closes a modification bracket. When the outermost bracket closes and at least one
     * mutation was accumulated, marks the song modified and posts a single
     * {@link SongDidChangeNotification} carrying all accumulated mutations.
     *
     * <p>A beat-defining edit that removed tuplets also reports itself here, after the
     * song notification — this is the only point that knows the whole edit is over, and a
     * warning shown any earlier would sit in front of a score not yet relaid out.
     */
    public void endModification() {
        modificationDepth--;

        if (modificationDepth != 0) {
            return;
        }

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

        if (tupletsRemovedNoticeCause != null) {
            var cause = tupletsRemovedNoticeCause;
            tupletsRemovedNoticeCause = null;
            MessageCenter.post(new TupletsWereRemovedNotification(cause));
        }
    }

    /**
     * Executes {@code body} inside a modification bracket, then posts a single
     * {@link SongDidChangeNotification} with all accumulated mutations.
     * Prefer this over {@link #beginModification()} / {@link #endModification()} to ensure
     * the depth counter is always balanced even if {@code body} throws.
     */
    public void withModification(Runnable body) {
        beginModification();

        try {
            body.run();
        } finally {
            endModification();
        }
    }

    /**
     * Executes {@code body} inside a modification bracket that declares {@code label}
     * as its op-name (Tier B), then posts a single {@link SongDidChangeNotification}.
     * The label is captured only if this is the outermost bracket (see
     * {@link #beginModification(String)}).
     */
    public void withModification(String label, Runnable body) {
        beginModification(label);

        try {
            body.run();
        } finally {
            endModification();
        }
    }

    /**
     * The value-returning form of {@link #withModification(Runnable)}, for a body
     * whose outcome the caller must inspect after the bracket closes.
     *
     * @param body The modification to run
     * @return Whatever {@code body} returns
     */
    public <T> T withModificationResult(Supplier<T> body) {
        beginModification();

        try {
            return body.get();
        } finally {
            endModification();
        }
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
        // Suspended tracking (e.g. SAX file load): apply the state change but
        // record nothing, so no SongDidChangeNotification fires mid-load. Mirrors
        // the guard in Line.applyChange for song-scoped setters that call here
        // directly (e.g. setMetadata).
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

    // ========== Beat-defining edits ==========

    /**
     * Applies a beat-defining state change and, inside the same modification bracket,
     * removes every tuplet at or after the edit position that the new beat context
     * invalidates.
     *
     * <p>A tuplet's validity is defined relative to the beat in effect at its anchor, so a
     * write that defines a beat can invalidate a tuplet that nothing went near: changing the
     * song's own tempo, or an earlier tempo change, can turn an attachment already sitting
     * inside a span into a beat barrier. That is why this is a chokepoint rather than a
     * hand-maintained list of call sites, and why it is not a
     * {@code SongDidChangeNotification} subscriber — the notification fires after the
     * outermost bracket closes, so the removals would land in a second undo step.
     *
     * <p>{@code edit} must perform the raw state change only, without recording its own
     * mutation. Callers that do record one invoke this from inside their
     * {@link #applyChange} mutator: the removals are then recorded while the mutator runs
     * and land ahead of the primary mutation, which is the companion ordering reverse-order
     * undo needs (see {@code docs/mutations.md}).
     *
     * <p>Nothing is validated during replay — the recorded batch already carries the
     * removals — or while mutation tracking is suspended, since a file load judges its
     * tuplets in the load pass under {@link TupletValidator.Strictness#LENIENT} instead.
     *
     * <p>Nested calls aggregate: an inner beat-defining write does the removing, and the
     * outermost call still reports it, so a caller that wraps a self-routing setter gets a
     * truthful answer.
     *
     * <p>When anything was removed, a single {@link TupletsWereRemovedNotification} is
     * posted once the outermost modification bracket closes, so every route into a
     * beat-defining edit warns the user on the same terms without {@code dom} knowing what
     * a dialog is. The return value is for callers that need the fact locally; nobody has
     * to read it to get the warning.
     *
     * @param lineIndex    the index of the line the edit sits on
     * @param elementIndex the index of the element within that line
     * @param edit         the raw state change
     * @return {@code true} if at least one tuplet was removed
     */
    public boolean withBeatDefiningEdit(int lineIndex, int elementIndex, Runnable edit) {
        beatDefiningEditDepth++;

        try {
            return withModificationResult(() -> {
                edit.run();

                if (!isReplaying()
                    && !isMutationTrackingSuspended()
                    && removeTupletsInvalidatedFrom(lineIndex, elementIndex)) {
                    beatEditRemovedTuplets = true;
                }

                // Arm from the outermost beat-defining edit only — an inner write that did
                // the removing is reported by its outermost caller, not on its own. Armed
                // here rather than after the bracket closes because this call may itself be
                // the outermost bracket, in which case endModification has already run by
                // the time this method unwinds.
                if (beatDefiningEditDepth == 1 && beatEditRemovedTuplets) {
                    noteTupletsWereRemoved(TupletsWereRemovedNotification.Cause.BEAT_EDIT);
                }

                return beatEditRemovedTuplets;
            });
        } finally {
            beatDefiningEditDepth--;

            if (beatDefiningEditDepth == 0) {
                beatEditRemovedTuplets = false;
            }
        }
    }

    /**
     * {@link #withBeatDefiningEdit} for a write that hangs on an element rather than on the
     * song, locating the edit position from that element. An element that is not in a
     * document — a detached attachment owner, a clipboard fragment, a dialog test double —
     * has no position to validate forward from, so the edit simply runs.
     *
     * @param owner the element the edited attachment hangs on, or {@code null} if detached
     * @param edit  the raw state change
     * @return {@code true} if at least one tuplet was removed
     */
    public static boolean withBeatDefiningEditOn(@Nullable StaffElement owner, Runnable edit) {
        var line = owner != null ? owner.getParentLine() : null;

        if (owner == null || line == null) {
            edit.run();
            return false;
        }

        var song = line.getSong();
        var elementIndex = line.getElementIndex(owner);

        if (!song.contains(line) || elementIndex < 0) {
            edit.run();
            return false;
        }

        return song.withBeatDefiningEdit(line.index(), elementIndex, edit);
    }

    /**
     * Records that the edit in flight cost the user one or more tuplets, so a single
     * {@link TupletsWereRemovedNotification} goes up once the outermost modification bracket
     * closes. Calling it more than once within one bracket still yields one notification;
     * the first cause recorded is the one reported, since it names the action the user took.
     *
     * <p>The beat-edit chokepoint arms this itself. It is public for the paste path, which
     * drops tuplets outside {@code dom} and must warn on the same terms — the alternative
     * being a second, differently-timed warning the user would have no way to relate to the
     * first.
     *
     * <p>Does nothing during undo/redo replay or while mutation tracking is suspended:
     * neither re-derives the removals, so neither should re-announce them.
     *
     * @param cause what the user did that removed them
     */
    public void noteTupletsWereRemoved(TupletsWereRemovedNotification.Cause cause) {
        if (isReplaying() || isMutationTrackingSuspended() || tupletsRemovedNoticeCause != null) {
            return;
        }

        tupletsRemovedNoticeCause = cause;
    }

    /**
     * Removes every tuplet anchored at or after the given position that no longer validates
     * under {@link TupletValidator.Strictness#STRICT}. Only positions from the edit forward
     * are walked: a beat-defining event cannot reach backwards.
     *
     * @return {@code true} if at least one tuplet was removed
     */
    private boolean removeTupletsInvalidatedFrom(int lineIndex, int elementIndex) {
        if (lineIndex < 0 || lineIndex >= song.lineCount()) {
            return false;
        }

        var editLine = song.getLine(lineIndex);

        // An edit position outside the line — the song tempo's nominal position in a song
        // with no notes yet — has no beat to resolve and no span to reach.
        if (!editLine.hasIndex(elementIndex)) {
            return false;
        }

        var startIndex = elementIndex;
        var enclosingTuplet = editLine.findTupletAt(elementIndex);

        // A tuplet the edit landed inside is anchored before the edit, and the forward walk
        // only opens a span at its anchor — so back the start up to the anchor, or the one
        // tuplet the edit is most likely to have broken would never be judged.
        if (enclosingTuplet != null && enclosingTuplet.getAnchorElementIndex() >= 0) {
            startIndex = Math.min(startIndex, enclosingTuplet.getAnchorElementIndex());
        }

        var removedAny = false;

        for (var verdict : TupletValidator.validateFrom(
            song, lineIndex, startIndex, TupletValidator.Strictness.STRICT)
        ) {
            if (!verdict.result().valid()) {
                song.getLine(verdict.lineIndex()).removeTuplet(verdict.tuplet());
                removedAny = true;
            }
        }

        return removedAny;
    }
}
