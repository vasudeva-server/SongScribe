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

package songscribe.undo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

import net.engio.mbassy.listener.Handler;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.mutation.BeamingAddition;
import songscribe.message.mutation.BeamingRemoval;
import songscribe.message.mutation.CrescendoAddition;
import songscribe.message.mutation.CrescendoRemoval;
import songscribe.message.mutation.DiminuendoAddition;
import songscribe.message.mutation.DiminuendoRemoval;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
import songscribe.message.mutation.ElementRangeDeletion;
import songscribe.message.mutation.ElementReplacement;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineKeyChange;
import songscribe.message.mutation.LineLayoutChange;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.MetadataField;
import songscribe.message.mutation.Mutation;
import songscribe.message.mutation.SpanAddition;
import songscribe.message.mutation.SpanRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.UndoStateDidChangeNotification;
import songscribe.ui.component.MainFrame;

/**
 * Application-level undo/redo engine. Each {@link SongDidChangeNotification} (one
 * outermost modification bracket) is pushed as a single undo step; undo/redo replays
 * that recorded batch through {@link MutationReplayer} inside an open bracket and the
 * model's replay mode. SongScribe is single-document, so one stack pair is correct.
 *
 * <p>See {@code docs/undo.md} for the step-by-step flow, the round-trip and identity
 * guarantees the engine owes the rest of the application, and the design rationale.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li><b>One bracket, one step.</b> However many mutations an edit accumulates, and
 *       however deeply its brackets nest, the user undoes it with one Undo. What counts as
 *       one edit is therefore decided by whoever opens the outermost bracket, not here.
 *   <li><b>The two stacks partition the history.</b> Every recorded step is on exactly one
 *       of them, and their concatenation is the document's edit sequence from the current
 *       position. A forward edit discards the redo side, so redo is a line, not a tree.
 *   <li><b>Clean is a position, not a content comparison.</b> The document is clean exactly
 *       while the undo stack's top is the step that was on top at the last save (or both are
 *       empty). Reaching an identical document by another route does not make it clean.
 *   <li><b>Replay is invisible to recording.</b> The bracket the engine opens to replay a
 *       step posts a {@link SongDidChangeNotification} like any other, and subscribers
 *       repaint from it, but it is never itself recorded as a step.
 *   <li><b>Steps are non-empty.</b> Guaranteed by
 *       {@link SongDidChangeNotification}: an empty bracket posts nothing. A step with no
 *       mutations would have no label and nothing to replay.
 * </ul>
 *
 * <p>EDT only, by contract; no synchronization is performed on the stacks, the replay guard
 * or the pending op-name.
 *
 * <h2>Lifecycle</h2>
 * {@link #initialize()} attaches the singleton to the message bus and is
 * idempotent — subscription is deliberately not a constructor side effect, since
 * the singleton is also constructed lazily the first time
 * {@link Song#beginModification} reads the pending op-name.
 *
 * <p>Once {@link #initialize()} has run, the controller is attached for the process. A
 * document load returns the stacks and clean markers to the empty baseline while leaving
 * the controller attached and recording.
 */
public final class UndoController {

    private static final Logger LOG = LoggerFactory.getLogger(UndoController.class);

    /**
     * The most undo steps the stack retains. Pushing past it evicts the oldest step,
     * which is what makes an edit permanently un-undoable, and — when the evicted step
     * is the one the last save marked clean — the document permanently modified.
     */
    public static final int UNDO_STACK_MAX_DEPTH = 50;

    /**
     * One undo step: the recorded mutation batch from a single outermost modification
     * bracket, plus the op-name its initiator declared (or {@code null} for the
     * type-based fallback label). Each instance has a distinct identity, so it doubles
     * as the reference-compared clean marker.
     */
    private record UndoStep(List<Mutation> mutations, @Nullable String opName) {
    }

    /**
     * Sentinel marking the "clean" position when the undo stack was empty at save
     * time. A freshly allocated {@link UndoStep} so reference comparison against
     * pushed steps is unambiguous.
     */
    private static final UndoStep BASELINE = new UndoStep(Collections.unmodifiableList(new ArrayList<>()), null);

    private static final UndoController INSTANCE = new UndoController();

    // Top of each deque is the most-recent step (push/pop); the bottom (removeLast)
    // is the oldest, evicted first when the depth limit is exceeded.
    private final Deque<UndoStep> undoStack = new ArrayDeque<>();
    private final Deque<UndoStep> redoStack = new ArrayDeque<>();

    // Reentrancy guard: true while replaying a step, so the SongDidChangeNotification
    // the replay bracket posts is not itself pushed as a new step.
    private boolean applyingReplay;

    // Reference to the undo step on top of undoStack at last save (or BASELINE when
    // the stack was empty then). Compared with == everywhere. cleanValid goes false
    // when that step is evicted, making the saved state unreachable.
    private UndoStep cleanStep = BASELINE;
    private boolean cleanValid = true;

    // Tier-A op-name declared by the current UI action, set around dispatch by the
    // UIAction template and consumed by Song.beginModification at the depth 0→1
    // transition. Held here — the intermediary between UI and model — rather than on
    // Song so the domain model carries no UI-label state. EDT-only, no synchronization.
    @Nullable
    private String pendingOpName;

    private UndoController() {
    }

    /**
     * Subscribes the singleton to the bus in force, attaching it for the rest of the process.
     * Registers on every call, unguarded: the bus ignores a listener it already holds, and a
     * call made after the bus in force has changed must reach the new bus.
     *
     * <p>Subscription is deliberately not a constructor side effect: the singleton is
     * also constructed lazily the first time {@link Song#beginModification} reads the
     * pending op-name, which orders it against nothing in particular. Keeping subscription
     * explicit means only a call to this method ever attaches the controller, at a point
     * startup chooses.
     *
     * @effects the controller begins recording mutations posted on the bus in force
     */
    public static void initialize() {
        MessageSubscription.addProcessListener(INSTANCE);
    }

    /**
     * Records a completed forward edit as one new undo step, so that a subsequent
     * {@link #undo()} reverses exactly the edit this notification describes.
     *
     * <p>Afterwards {@link #canUndo()} is true, {@link #canRedo()} is false — a forward
     * edit discards the redo branch, so redo is linear rather than a tree — and the
     * document is modified unless the pushed step happens to be the clean marker again.
     * The step's label follows from the notification's op-name; see {@link #undoLabel()}.
     *
     * <p>The stack retains at most {@value #UNDO_STACK_MAX_DEPTH} steps; a push past
     * that evicts the oldest, and if the evicted step was the clean marker the document
     * can no longer return to clean. Posts {@link UndoStateDidChangeNotification} so the
     * Edit menu follows.
     *
     * <p>A notification posted by the engine's own replay bracket is ignored: replaying a
     * step must not push a step, or undo could never empty the stack.
     *
     * <p>Runs at {@link Message#HIGH_PRIORITY} so that lower-priority subscribers reading
     * {@link #canUndo()} or {@link #undoLabel()} while handling the same notification see
     * the step already pushed.
     */
    @Handler(priority = Message.HIGH_PRIORITY)
    public void songDidChange(SongDidChangeNotification message) {
        if (applyingReplay) {
            return;
        }

        undoStack.push(new UndoStep(message.getMutations(), message.getOpName()));
        redoStack.clear();

        if (undoStack.size() > UNDO_STACK_MAX_DEPTH) {
            var evicted = undoStack.removeLast();

            if (evicted == cleanStep) {
                cleanValid = false;
            }
        }

        recomputeModified(message.getSong());
        MessageCenter.post(new UndoStateDidChangeNotification());
    }

    /**
     * Reverses the most recent recorded step, restoring the document to the state it held
     * immediately before that step's edit — the round-trip guarantee stated in
     * {@code docs/undo.md}. The step moves to the redo stack, so {@link #canRedo()} is then
     * true and {@link #redo()} re-applies it.
     *
     * <p>Elements are restored in place rather than replaced, so anchors held by spans and
     * by the current selection stay valid across any interleaving of undo and redo.
     *
     * <p>A no-op, posting nothing, when {@link #canUndo()} is false or no document is open.
     * Otherwise the document's modified flag is recomputed against the save point (see
     * {@link #documentWasSaved}) and {@link UndoStateDidChangeNotification} is posted. The
     * replay itself posts a {@link SongDidChangeNotification} describing the inverse
     * mutations — which is how the score repaints — without recording a new step.
     *
     * <p>Never throws. A replay that fails is an engine bug, not a caller error: the model
     * is then mid-step and matches neither stack, so both stacks are cleared and the
     * document is forced modified, leaving the user able to save but not to undo further.
     *
     * <p>EDT only.
     */
    public static void undo() {
        INSTANCE.performUndo();
    }

    private void performUndo() {
        var scoreView = MainFrame.getInstance().getScoreView();

        if (scoreView == null || undoStack.isEmpty()) {
            return;
        }

        var song = scoreView.getSong();
        var step = undoStack.peek();

        applyingReplay = true;

        try {
            var mutations = step.mutations();
            song.withModification(() -> song.withReplay(() -> {
                for (var i = mutations.size() - 1; i >= 0; i--) {
                    MutationReplayer.applyUndo(scoreView, mutations.get(i));
                }
            }));

            undoStack.pop();
            redoStack.push(step);
        } catch (RuntimeException e) {
            handleReplayFailure(step, e);
        } finally {
            applyingReplay = false;
        }

        recomputeModified(song);
        MessageCenter.post(new UndoStateDidChangeNotification());
    }

    /**
     * Re-applies the most recently undone step, restoring the document to the state it held
     * immediately after that step's original edit. The step moves back to the undo stack.
     *
     * <p>The redo stack holds only steps put there by {@link #undo()} and is discarded by the
     * next forward edit, so redo is reachable only along the path just undone.
     *
     * <p>A no-op, posting nothing, when {@link #canRedo()} is false or no document is open.
     * In every other respect — in-place restoration, the recomputed modified flag, the posted
     * notifications, and the never-throws fail-safe — it matches {@link #undo()}.
     *
     * <p>EDT only.
     */
    public static void redo() {
        INSTANCE.performRedo();
    }

    private void performRedo() {
        var scoreView = MainFrame.getInstance().getScoreView();

        if (scoreView == null || redoStack.isEmpty()) {
            return;
        }

        var song = scoreView.getSong();
        var step = redoStack.peek();

        applyingReplay = true;

        try {
            song.withModification(() -> song.withReplay(() -> {
                for (var mutation : step.mutations()) {
                    MutationReplayer.applyRedo(scoreView, mutation);
                }
            }));

            redoStack.pop();
            undoStack.push(step);
        } catch (RuntimeException e) {
            handleReplayFailure(step, e);
        } finally {
            applyingReplay = false;
        }

        recomputeModified(song);
        MessageCenter.post(new UndoStateDidChangeNotification());
    }

    /**
     * A replay exception is always an engine bug: the recorded batch could not be
     * reproduced, so the model is now mid-step and desynced from both stacks. Clear
     * both stacks and force {@code modified = true} (via {@code cleanValid = false})
     * so no further undo acts on the desynced model and the user is prompted to save
     * before losing work. The step's full mutation list is logged for diagnosis.
     */
    private void handleReplayFailure(UndoStep step, RuntimeException e) {
        LOG.error("Undo/redo replay failed; clearing history. Step mutations: {}", step.mutations(), e);
        undoStack.clear();
        redoStack.clear();
        cleanValid = false;
    }

    /**
     * Marks the current position in the undo history as the saved state. The document is
     * clean exactly while the undo stack's top is the step that was on top here, so undoing
     * past the save point marks it modified and redoing back to it marks it clean again.
     *
     * <p>The position is held by reference, not by content: two edits that happen to produce
     * identical documents are still distinct positions, and only the recorded one is clean.
     *
     * <p>The history survives a save — both stacks are left as they are. What does not
     * survive is eviction: once the marked step is dropped at
     * {@value #UNDO_STACK_MAX_DEPTH}, the saved state is unreachable and the document stays
     * modified however far back the user undoes.
     *
     * <p>A no-op when no document is open.
     */
    @Handler
    public void documentWasSaved(DocumentWasSavedNotification message) {
        var scoreView = MainFrame.getInstance().getScoreView();

        if (scoreView == null) {
            return;
        }

        cleanStep = undoStack.isEmpty() ? BASELINE : undoStack.peek();
        cleanValid = true;
        recomputeModified(scoreView.getSong());
    }

    /**
     * Discards the outgoing document's history: a document that has just been opened or
     * created has nothing to undo, and replaying a step recorded against the previous
     * document would corrupt this one.
     */
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        reset();
    }

    /**
     * Returns undo state to the empty baseline: both stacks cleared, the document
     * marked clean, no pending op-name. Posts {@link UndoStateDidChangeNotification}
     * so the Edit menu follows. Leaves the controller attached; a document load performs
     * exactly this.
     */
    private static void reset() {
        INSTANCE.undoStack.clear();
        INSTANCE.redoStack.clear();
        INSTANCE.cleanStep = BASELINE;
        INSTANCE.cleanValid = true;
        INSTANCE.pendingOpName = null;
        MessageCenter.post(new UndoStateDidChangeNotification());
    }

    /**
     * Recomputes the modified flag against the reference-based clean marker. The
     * document is clean only when the undo stack's current top is the exact step
     * that was on top at the last save (or both are the empty BASELINE).
     */
    private void recomputeModified(Song song) {
        var atCleanPosition = cleanStep == (undoStack.isEmpty() ? BASELINE : undoStack.peek());
        song.setModified(!cleanValid || !atCleanPosition);
    }

    /**
     * Sets the Tier-A op-name that the next outermost modification bracket will capture.
     * Prefer {@link #withPendingOpName}, which pairs the set with its restore. EDT-only.
     */
    public static void setPendingOpName(@Nullable String opName) {
        INSTANCE.pendingOpName = opName;
    }

    /**
     * The Tier-A op-name a bracket opening now would capture. Read by
     * {@code Song.beginModification} at the outermost-bracket transition. EDT-only.
     *
     * @return the pending op-name, or {@code null} when no dispatch has declared one and
     *         an edit opening now would fall back to a type-based label
     */
    public static @Nullable String getPendingOpName() {
        return INSTANCE.pendingOpName;
    }

    /**
     * Runs {@code body} with {@code opName} pending, so the outermost modification bracket
     * that opens synchronously inside it captures that name, and restores the previously
     * pending name afterwards — including when {@code body} throws.
     *
     * <p>Restoring rather than clearing is what makes the bracket nest: a dispatch that runs
     * inside another one hands the outer name back when it returns, instead of leaving the
     * next unrelated edit to adopt whatever this one set.
     *
     * <p>Every path that opens a bracket outside the {@code UIAction} dispatch template has to
     * do this for itself — the last-insertion keys and paste placement both bypass it — so the
     * save/set/restore lives here, next to the state it guards, rather than being written out
     * again at each of those call sites.
     */
    public static void withPendingOpName(@Nullable String opName, Runnable body) {
        withPendingOpNameResult(opName, () -> {
            body.run();
            return null;
        });
    }

    /**
     * The value-returning form of {@link #withPendingOpName(String, Runnable)}, for a body
     * whose outcome the caller must inspect after the name is restored.
     *
     * @return whatever {@code body} returned, unchanged
     */
    public static <T> T withPendingOpNameResult(@Nullable String opName, Supplier<T> body) {
        var priorOpName = getPendingOpName();
        setPendingOpName(opName);

        try {
            return body.get();
        } finally {
            setPendingOpName(priorOpName);
        }
    }

    /**
     * @return {@code true} when a step is available to reverse — equivalently, when
     *         {@link #undo()} would change the document rather than do nothing
     */
    public static boolean canUndo() {
        return !INSTANCE.undoStack.isEmpty();
    }

    /**
     * @return {@code true} when a previously undone step is available to re-apply —
     *         equivalently, when {@link #redo()} would change the document
     */
    public static boolean canRedo() {
        return !INSTANCE.redoStack.isEmpty();
    }

    /**
     * The Edit-menu label for Undo, naming the operation the next {@link #undo()} would
     * reverse: the plain {@code "Undo"} when {@link #canUndo()} is false, the top step's
     * declared op-name verbatim when its initiator declared one, and otherwise a label
     * derived from the kind of edit the step's dominant mutation records — never a bare
     * {@code "Undo"} over a non-empty stack.
     *
     * @return the localized label, ready to display as-is
     */
    public static String undoLabel() {
        return INSTANCE.composeLabel(INSTANCE.undoStack, Strings.ACTION_EDIT_UNDO, Strings.ACTION_EDIT_UNDO_LABELED);
    }

    /**
     * The Edit-menu label for Redo, naming the operation the next {@link #redo()} would
     * re-apply. Composed exactly as {@link #undoLabel()} is, from the redo stack.
     *
     * @return the localized label, ready to display as-is
     */
    public static String redoLabel() {
        return INSTANCE.composeLabel(INSTANCE.redoStack, Strings.ACTION_EDIT_REDO, Strings.ACTION_EDIT_REDO_LABELED);
    }

    private String composeLabel(Deque<UndoStep> stack, String plainKey, String labeledKey) {
        var step = stack.peek();

        if (step == null) {
            return Strings.get(plainKey);
        }

        // A declared op-name is used verbatim; otherwise derive the type-based
        // fallback label from the step's dominant mutation.
        var opName = step.opName();

        //noinspection ReplaceNullCheck
        if (opName != null) {
            return Strings.get(labeledKey, opName);
        }

        return Strings.get(labeledKey, opName(dominantMutation(step.mutations())));
    }

    /**
     * Selects the step's dominant mutation by precedence tier (highest wins; tie-break
     * is first occurrence). Companion ordering is not uniform, so "first mutation"
     * would mislabel — e.g. deleting a tuplet-spanned note (tuplet-removal companion
     * emitted first) must still read "Delete Note". Callers pass a non-empty step.
     *
     * @return the mutation the step's label is derived from
     */
    private static Mutation dominantMutation(List<? extends Mutation> step) {
        for (var mutation : step) {
            if (mutation instanceof LineInsertion || mutation instanceof LineDeletion) {
                return mutation;
            }
        }

        for (var mutation : step) {
            if (mutation instanceof ElementInsertion
                || mutation instanceof ElementDeletion
                || mutation instanceof ElementRangeDeletion
                || mutation instanceof ElementReplacement
                || mutation instanceof ElementModification) {
                return mutation;
            }
        }

        for (var mutation : step) {
            if (mutation instanceof MetadataChange
                || mutation instanceof LayoutChange
                || mutation instanceof LineKeyChange
                || mutation instanceof LineLayoutChange
                || mutation instanceof LyricsChange
                || mutation instanceof FontChange) {
                return mutation;
            }
        }

        // Tier 4 (span add/remove) — the only remaining types; dominant is the first.
        return step.getFirst();
    }

    /**
     * The fallback name for a step that declared none of its own, resolved here rather than
     * returned as a key: a delete label carries its own plural as a choice suffix, so it needs
     * the element count, and only the mutation holds that. Resolving every arm keeps the
     * mutation types enumerated once. See {@code OpNames}, which builds the declared names.
     *
     * @param dominant the step's dominant mutation
     * @return the localized operation name
     */
    private static String opName(Mutation dominant) {
        return switch (dominant) {
            case ElementInsertion _ -> Strings.get(Strings.ACTION_EDIT_OP_ADD_NOTE);
            case ElementDeletion _ -> Strings.get(Strings.ACTION_EDIT_OP_DELETE_NOTE, 1);

            case ElementRangeDeletion rangeDeletion ->
                Strings.get(Strings.ACTION_EDIT_OP_DELETE_NOTE, rangeDeletion.deletedElements().size());

            case ElementReplacement _ -> Strings.get(Strings.ACTION_EDIT_OP_REPLACE_NOTE);
            case ElementModification _ -> Strings.get(Strings.ACTION_EDIT_OP_EDIT_NOTE);
            case LineInsertion _ -> Strings.get(Strings.ACTION_EDIT_OP_ADD_LINE);
            case LineDeletion _ -> Strings.get(Strings.ACTION_EDIT_OP_DELETE_LINE);
            case LineKeyChange _ -> Strings.get(Strings.ACTION_EDIT_OP_CHANGE_KEY);
            case LineLayoutChange _, LayoutChange _ -> Strings.get(Strings.ACTION_EDIT_OP_CHANGE_LAYOUT);
            case BeamingAddition _, BeamingRemoval _ -> Strings.get(Strings.ACTION_EDIT_OP_BEAMING);
            case TieAddition _, TieRemoval _ -> Strings.get(Strings.ACTION_EDIT_OP_TIE);
            case TupletAddition _, TupletRemoval _ -> Strings.get(Strings.ACTION_EDIT_OP_TUPLET);
            case CrescendoAddition _, CrescendoRemoval _ -> Strings.get(Strings.ACTION_EDIT_OP_CRESCENDO);
            case DiminuendoAddition _, DiminuendoRemoval _ -> Strings.get(Strings.ACTION_EDIT_OP_DIMINUENDO);
            case SpanAddition _, SpanRemoval _ -> Strings.get(Strings.ACTION_EDIT_OP_SPAN);
            case MetadataChange metadataChange -> Strings.get(metadataOpNameKey(metadataChange.field()));
            case FontChange _ -> Strings.get(Strings.ACTION_EDIT_OP_CHANGE_FONTS);
            case LyricsChange _ -> Strings.get(Strings.ACTION_EDIT_OP_EDIT_LYRICS);
        };
    }

    private static String metadataOpNameKey(MetadataField field) {
        return switch (field) {
            case ATTRIBUTION -> Strings.ACTION_EDIT_OP_CHANGE_ATTRIBUTION;
            case TEMPO -> Strings.ACTION_EDIT_OP_CHANGE_TEMPO;
            case FOOTNOTES -> Strings.ACTION_EDIT_OP_CHANGE_FOOTNOTES;
        };
    }
}
