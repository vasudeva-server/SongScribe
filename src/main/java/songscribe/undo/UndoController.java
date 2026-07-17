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

import net.engio.mbassy.listener.Handler;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.Song;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
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
import songscribe.message.mutation.RangeElementAddition;
import songscribe.message.mutation.RangeElementRemoval;
import songscribe.message.mutation.TieAddition;
import songscribe.message.mutation.TieRemoval;
import songscribe.message.mutation.TupletAddition;
import songscribe.message.mutation.TupletRemoval;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.UndoStateDidChangeNotification;
import songscribe.ui.component.MainFrame;
import songscribe.undo.MutationReplayer;

/**
 * Application-level undo/redo engine. Each {@link SongDidChangeNotification} (one
 * outermost modification bracket) is pushed as a single undo step; undo/redo replays
 * that recorded batch through {@link MutationReplayer} inside an open bracket and the
 * model's replay mode. SongScribe is single-document, so one stack pair is correct.
 *
 * <pre>
 *  forward edit                UndoController                    Song / Line
 *  ────────────                ─────────────                     ───────────
 *  user edits ──▶ Song.endModification ──▶ SongDidChangeNotification
 *                                           │  (applyingReplay == false)
 *                                           ▼
 *                                    push UndoStep(mutations, opName) onto undoStack
 *                                    clear redoStack
 *                                    evict oldest if size &gt; undoStackMaxDepth (default 50)
 *                                    post UndoStateDidChangeNotification
 *
 *  Edit-menu label (composeLabel, per direction):
 *         step = stack.peek()
 *         step == null            ──▶ "Undo" / "Redo"            (empty stack)
 *         step.opName != null     ──▶ "Undo <declared op-name>"  (declared, verbatim)
 *         step.opName == null     ──▶ "Undo <type-based label>"  (fallback via
 *                                     opNameKey(dominantMutation(step.mutations)))
 *
 *  Undo:  peek step from undoStack
 *         applyingReplay = true
 *         song.withModification(() -&gt; song.withReplay(() -&gt;
 *             for m in reverse(step): MutationReplayer.applyUndo(scoreView, m)))
 *         applyingReplay = false          ──▶ posts a SongDidChangeNotification
 *                                             (handler sees applyingReplay==true → ignores)
 *                                             (ScoreViewController still repaints from it)
 *         on success: pop from undoStack, push onto redoStack
 *         recompute modified vs clean
 *         post UndoStateDidChangeNotification
 *
 *  Redo:  peek step from redoStack
 *         applyingReplay = true
 *         song.withModification(() -&gt; song.withReplay(() -&gt;
 *             for m in forward(step): MutationReplayer.applyRedo(scoreView, m)))
 *         applyingReplay = false
 *         on success: pop from redoStack, push onto undoStack
 *         recompute modified vs clean
 *         post UndoStateDidChangeNotification
 * </pre>
 *
 * Keep this diagram in sync if the flow changes.
 */
public final class UndoController {

    private static final Logger LOG = LoggerFactory.getLogger(UndoController.class);

    // Package-private so UndoControllerTest can drive the eviction boundary without
    // duplicating the literal.
    static final int DEFAULT_UNDO_STACK_MAX_DEPTH = 50;

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

    // A field (not a constant) so a future preference can adjust it at runtime.
    private int undoStackMaxDepth = DEFAULT_UNDO_STACK_MAX_DEPTH;

    // Reference to the undo step on top of undoStack at last save (or BASELINE when
    // the stack was empty then). Compared with == everywhere. cleanValid goes false
    // when that step is evicted, making the saved state unreachable.
    private UndoStep cleanStep = BASELINE;
    private boolean cleanValid = true;

    // Guards against double-subscription so resetForTest() can safely re-subscribe the
    // singleton after a test's teardown removed it (see unsubscribeForTest).
    private boolean subscribed;

    // Tier-A op-name declared by the current UI action, set around dispatch by the
    // UIAction template and consumed by Song.beginModification at the depth 0→1
    // transition. Held here — the intermediary between UI and model — rather than on
    // Song so the domain model carries no UI-label state. EDT-only, no synchronization.
    @Nullable
    private String pendingOpName;

    private UndoController() {
    }

    private void subscribeToBus() {
        if (!subscribed) {
            MessageCenter.subscribe(this);
            subscribed = true;
        }
    }

    /**
     * Subscribes the singleton to the bus at startup, before the user can edit.
     * Subscription is deliberately not a constructor side effect: the singleton is
     * also constructed lazily the first time {@link Song#beginModification} reads the
     * pending op-name, which in tests can occur while the message bus is mocked —
     * subscribing then would register the listener against a mock and corrupt its
     * later real subscription. Keeping subscription explicit means only startup (and
     * {@link #resetForTest}) ever register it, always against the live bus.
     */
    public static void initialize() {
        INSTANCE.subscribeToBus();
    }

    /**
     * Records a completed forward edit as a new undo step. Ignored while replaying:
     * the notification the replay bracket posts must not become a new step.
     */
    @Handler(priority = Message.HIGH_PRIORITY)
    public void songDidChange(SongDidChangeNotification message) {
        if (applyingReplay) {
            return;
        }

        undoStack.push(new UndoStep(message.getMutations(), message.getOpName()));
        redoStack.clear();

        if (undoStack.size() > undoStackMaxDepth) {
            var evicted = undoStack.removeLast();

            if (evicted == cleanStep) {
                cleanValid = false;
            }
        }

        recomputeModified(message.getSong());
        MessageCenter.post(new UndoStateDidChangeNotification());
    }

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

    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        undoStack.clear();
        redoStack.clear();
        cleanStep = BASELINE;
        cleanValid = true;
        MessageCenter.post(new UndoStateDidChangeNotification());
    }

    /**
     * Resets the shared singleton's stacks and clean markers to the empty baseline and
     * ensures it is subscribed to the bus. The singleton persists across a JVM's test
     * classes, so a label/coalescing test calls this before each case to isolate itself
     * from steps left by earlier tests; the re-subscribe restores the subscription that
     * {@link #unsubscribeForTest} removes between tests.
     */
    static void resetForTest() {
        INSTANCE.subscribeToBus();
        INSTANCE.undoStack.clear();
        INSTANCE.redoStack.clear();
        INSTANCE.cleanStep = BASELINE;
        INSTANCE.cleanValid = true;
        INSTANCE.pendingOpName = null;
    }

    /**
     * Test-only: removes the singleton from the message bus and releases the songs its
     * steps pin. Without this, any test that loads the singleton leaves it recording — and
     * retaining — every later test's edits for the JVM's life, both polluting unrelated
     * tests and steadily growing heap. {@code UnitTest}'s teardown calls this after every
     * test; undo tests re-subscribe via {@link #resetForTest} in their setup.
     */
    public static void unsubscribeForTest() {
        MessageCenter.unsubscribe(INSTANCE);
        INSTANCE.subscribed = false;
        INSTANCE.undoStack.clear();
        INSTANCE.redoStack.clear();
        INSTANCE.pendingOpName = null;
    }

    /**
     * Recomputes the modified flag against the reference-based clean marker. The
     * document is clean only when the undo stack's current top is the exact step
     * that was on top at the last save (or both are the empty BASELINE).
     */
    private void recomputeModified(Song song) {
        var atCleanPosition = undoStack.isEmpty() ? cleanStep == BASELINE : undoStack.peek() == cleanStep;
        song.setModified(!cleanValid || !atCleanPosition);
    }

    /**
     * Sets the Tier-A op-name that the next outermost modification bracket will capture.
     * Called by the {@code UIAction} template around action dispatch. EDT-only.
     */
    public static void setPendingOpName(@Nullable String opName) {
        INSTANCE.pendingOpName = opName;
    }

    /**
     * Returns the currently pending Tier-A op-name (or {@code null}). Read by
     * {@code Song.beginModification} at the outermost-bracket transition, and by the
     * {@code UIAction} template to save and restore it around nested dispatch. EDT-only.
     */
    public static @Nullable String getPendingOpName() {
        return INSTANCE.pendingOpName;
    }

    public static boolean canUndo() {
        return !INSTANCE.undoStack.isEmpty();
    }

    public static boolean canRedo() {
        return !INSTANCE.redoStack.isEmpty();
    }

    /**
     * Fully composed Edit-menu label for Undo: {@code "Undo"} when the stack is empty,
     * else {@code "Undo <op>"} derived from the top step's dominant mutation.
     */
    public static String undoLabel() {
        return INSTANCE.composeLabel(INSTANCE.undoStack, Strings.ACTION_EDIT_UNDO, Strings.ACTION_EDIT_UNDO_LABELED);
    }

    /**
     * Fully composed Edit-menu label for Redo (see {@link #undoLabel()}).
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

        if (opName != null) {
            return Strings.get(labeledKey, opName);
        }

        return Strings.get(labeledKey, Strings.get(opNameKey(dominantMutation(step.mutations()))));
    }

    /**
     * Selects the step's dominant mutation by precedence tier (highest wins; tie-break
     * is first occurrence). Companion ordering is not uniform, so "first mutation"
     * would mislabel — e.g. deleting a tuplet-spanned note (tuplet-removal companion
     * emitted first) must still read "Delete Note". Callers pass a non-empty step.
     */
    private static Mutation dominantMutation(List<Mutation> step) {
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
        return step.get(0);
    }

    private static String opNameKey(Mutation dominant) {
        return switch (dominant) {
            case ElementInsertion _ -> Strings.ACTION_EDIT_OP_ADD_NOTE;
            case ElementDeletion _ -> Strings.ACTION_EDIT_OP_DELETE_NOTE;
            case ElementRangeDeletion _ -> Strings.ACTION_EDIT_OP_DELETE_NOTE;
            case ElementReplacement _ -> Strings.ACTION_EDIT_OP_REPLACE_NOTE;
            case ElementModification _ -> Strings.ACTION_EDIT_OP_EDIT_NOTE;
            case LineInsertion _ -> Strings.ACTION_EDIT_OP_ADD_LINE;
            case LineDeletion _ -> Strings.ACTION_EDIT_OP_DELETE_LINE;
            case LineKeyChange _ -> Strings.ACTION_EDIT_OP_CHANGE_KEY;
            case LineLayoutChange _ -> Strings.ACTION_EDIT_OP_CHANGE_LAYOUT;
            case LayoutChange _ -> Strings.ACTION_EDIT_OP_CHANGE_LAYOUT;
            case BeamingAddition _ -> Strings.ACTION_EDIT_OP_BEAMING;
            case BeamingRemoval _ -> Strings.ACTION_EDIT_OP_BEAMING;
            case TieAddition _ -> Strings.ACTION_EDIT_OP_TIE;
            case TieRemoval _ -> Strings.ACTION_EDIT_OP_TIE;
            case TupletAddition _ -> Strings.ACTION_EDIT_OP_TUPLET;
            case TupletRemoval _ -> Strings.ACTION_EDIT_OP_TUPLET;
            case CrescendoAddition _ -> Strings.ACTION_EDIT_OP_CRESCENDO;
            case CrescendoRemoval _ -> Strings.ACTION_EDIT_OP_CRESCENDO;
            case DiminuendoAddition _ -> Strings.ACTION_EDIT_OP_DIMINUENDO;
            case DiminuendoRemoval _ -> Strings.ACTION_EDIT_OP_DIMINUENDO;
            case RangeElementAddition _ -> Strings.ACTION_EDIT_OP_RANGE_ELEMENT;
            case RangeElementRemoval _ -> Strings.ACTION_EDIT_OP_RANGE_ELEMENT;
            case MetadataChange metadataChange -> metadataOpNameKey(metadataChange.field());
            case FontChange _ -> Strings.ACTION_EDIT_OP_CHANGE_FONTS;
            case LyricsChange _ -> Strings.ACTION_EDIT_OP_EDIT_LYRICS;
        };
    }

    private static String metadataOpNameKey(MetadataField field) {
        return switch (field) {
            case ATTRIBUTION -> Strings.ACTION_EDIT_OP_CHANGE_ATTRIBUTION;
            case TEMPO -> Strings.ACTION_EDIT_OP_CHANGE_TEMPO;
            case FOOTNOTES -> Strings.ACTION_EDIT_OP_CHANGE_FOOTNOTES;
            case DEFAULT_KEY_ACCIDENTAL_COUNT -> Strings.ACTION_EDIT_OP_CHANGE_KEY;
            case DEFAULT_KEY_TYPE -> Strings.ACTION_EDIT_OP_CHANGE_KEY;
        };
    }
}
