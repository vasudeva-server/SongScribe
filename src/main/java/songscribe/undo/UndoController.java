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
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.engio.mbassy.listener.Handler;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.Beam;
import songscribe.dom.Ending;
import songscribe.dom.Hairpin;
import songscribe.dom.Song;
import songscribe.dom.Span;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementModification;
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
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.DocumentWasSavedNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.UndoStateDidChangeNotification;

/**
 * Application-level undo/redo engine. SongScribe is single-document, so one stack pair is correct.
 *
 * <ul>
 *   <li>One bracket, one step: whoever opens the outermost bracket decides what counts as one
 *       edit.
 *   <li>Clean is a position, not a content comparison: the document is clean exactly while the
 *       undo stack's top is the step that was on top at the last save (or the load, when the
 *       load left the song as its file holds it). Reaching an identical document by another
 *       route does not make it clean.
 *   <li>Replay posts a {@link SongDidChangeNotification} like any edit but is never recorded as
 *       a step.
 * </ul>
 *
 * <p>EDT only, by contract; no synchronization.
 *
 * <p>Subscription is not a constructor side effect, since the singleton is also constructed
 * lazily by the first outermost modification bracket. Until {@link #initialize(Supplier)}
 * runs, undo and redo do nothing; afterwards the controller stays attached for the process,
 * across document loads.
 */
public final class UndoController {

    private static final Logger LOG = LoggerFactory.getLogger(UndoController.class);

    /**
     * The most undo steps retained. A push past it evicts the oldest step; if that was the
     * clean marker, the document can no longer return to clean.
     */
    private static final int UNDO_STACK_MAX_DEPTH = 50;

    /** Compared by identity, so a step doubles as the clean marker. A null op-name selects the type-based label. */
    private record UndoStep(List<Mutation> mutations, @Nullable String opName) {
    }

    /** Freshly allocated so identity comparison against pushed steps is unambiguous. */
    private static final UndoStep BASELINE =
        new UndoStep(Collections.unmodifiableList(new ArrayList<>()), null);

    private static final UndoController INSTANCE = new UndoController();

    // Top of each deque is the most-recent step (push/pop); the bottom (removeLast)
    // is the oldest, evicted first when the depth limit is exceeded.
    private final Deque<UndoStep> undoStack = new ArrayDeque<>();
    private final Deque<UndoStep> redoStack = new ArrayDeque<>();

    // Reentrancy guard: true while replaying a step, so the SongDidChangeNotification
    // the replay bracket posts is not itself pushed as a new step.
    private boolean applyingReplay;

    // Reference to the undo step on top of undoStack at last save (or BASELINE when
    // the stack was empty then). Compared with == everywhere. cleanValid is false while no
    // position in the history is clean: the marked step was evicted, a replay failed, or
    // the load changed the song and it has not been saved since.
    private UndoStep cleanStep = BASELINE;
    private boolean cleanValid = true;

    // Tier-A op-name declared by the current UI action, set around dispatch by the
    // UIAction template and consumed by the song's modification bracket at the depth 0→1
    // transition. Held here — the intermediary between UI and model — rather than on
    // Song so the domain model carries no UI-label state. EDT-only, no synchronization.
    @Nullable
    private String pendingOpName;

    // The open document as the engine sees it, or null when none is open.
    private Supplier<@Nullable ReplayTarget> target = () -> null;

    private UndoController() {
    }

    /** For a test that drives the engine without the main window; the caller subscribes it. */
    UndoController(Supplier<@Nullable ReplayTarget> target) {
        this.target = target;
    }

    /**
     * Registers on every call, unguarded: a call made after the bus in force has changed must
     * reach the new bus.
     *
     * @param target gives the open document, or {@code null} when none is open
     */
    public static void initialize(Supplier<@Nullable ReplayTarget> target) {
        INSTANCE.target = target;
        MessageSubscription.addProcessListener(INSTANCE);
    }

    /**
     * Runs at {@link Message#HIGH_PRIORITY} so that lower-priority subscribers reading
     * {@link #canUndo()} or {@link #undoLabel()} see the step already pushed.
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
     * Never throws: a failed replay clears both stacks and forces the document modified.
     */
    public static void undo() {
        INSTANCE.performUndo();
    }

    void performUndo() {
        replayTop(undoStack, (song, fonts, step) ->
            MutationReplayer.undoBatch(song, fonts, step.mutations()), redoStack::push);
    }

    /** Never throws, as {@link #undo()}. */
    public static void redo() {
        INSTANCE.performRedo();
    }

    void performRedo() {
        replayTop(redoStack, (song, fonts, step) ->
            MutationReplayer.redoBatch(song, fonts, step.mutations()), undoStack::push);
    }

    @FunctionalInterface
    private interface StepReplay {
        void replay(Song song, FontTarget fonts, UndoStep step);
    }

    private void replayTop(Deque<UndoStep> source, StepReplay replay, Consumer<UndoStep> destination) {
        var replayTarget = target.get();

        if (replayTarget == null || source.isEmpty()) {
            return;
        }

        var song = replayTarget.getSong();
        var step = source.peek();

        applyingReplay = true;

        try {
            replay.replay(song, replayTarget, step);

            source.pop();
            destination.accept(step);
        } catch (RuntimeException e) {
            handleReplayFailure(step, e);
        } finally {
            applyingReplay = false;
        }

        recomputeModified(song);
        MessageCenter.post(new UndoStateDidChangeNotification());
    }

    /** A replay exception is an engine bug that leaves the model mid-step, desynced from both stacks. */
    private void handleReplayFailure(UndoStep step, RuntimeException e) {
        LOG.error("Undo/redo replay failed; clearing history. Step mutations: {}", step.mutations(), e);
        undoStack.clear();
        redoStack.clear();
        cleanValid = false;
    }

    /** The clean position is held by reference, not by content. */
    @Handler
    public void documentWasSaved(DocumentWasSavedNotification message) {
        var replayTarget = target.get();

        if (replayTarget == null) {
            return;
        }

        cleanStep = undoStack.isEmpty() ? BASELINE : undoStack.peek();
        cleanValid = true;
        recomputeModified(replayTarget.getSong());
    }

    /**
     * Replaying a step recorded against the previous document would corrupt the new one.
     * When the load changed the song, the document stays modified however far back the user
     * undoes, until it is saved.
     */
    @Handler
    public void documentDidLoad(DocumentDidLoadNotification message) {
        undoStack.clear();
        redoStack.clear();
        cleanStep = BASELINE;
        cleanValid = message.getLoadChange() == DocumentDidLoadNotification.LoadChange.UNCHANGED;
        pendingOpName = null;
        recomputeModified(message.getSong());
        MessageCenter.post(new UndoStateDidChangeNotification());
    }

    private void recomputeModified(Song song) {
        var atCleanPosition = cleanStep == (undoStack.isEmpty() ? BASELINE : undoStack.peek());
        song.setModified(!cleanValid || !atCleanPosition);
    }

    /** {@link #withPendingOpName} is the only caller: it pairs the set with its restore. */
    private static void setPendingOpName(@Nullable String opName) {
        INSTANCE.pendingOpName = opName;
    }

    /** @return the pending op-name, or {@code null} when none is declared (the type-based label applies) */
    public static @Nullable String getPendingOpName() {
        return INSTANCE.pendingOpName;
    }

    /**
     * Restores the outer name on return rather than clearing, so nested dispatches do not leak
     * a name to the next unrelated edit. Every bracket opened outside the {@code UIAction}
     * dispatch template must use this.
     */
    public static void withPendingOpName(@Nullable String opName, Runnable body) {
        withPendingOpNameResult(opName, () -> {
            body.run();
            return null;
        });
    }

    public static <T> T withPendingOpNameResult(@Nullable String opName, Supplier<T> body) {
        var priorOpName = getPendingOpName();
        setPendingOpName(opName);

        try {
            return body.get();
        } finally {
            setPendingOpName(priorOpName);
        }
    }

    public static boolean canUndo() {
        return !INSTANCE.undoStack.isEmpty();
    }

    public static boolean canRedo() {
        return !INSTANCE.redoStack.isEmpty();
    }

    public static String undoLabel() {
        return INSTANCE.composeLabel(INSTANCE.undoStack, Strings.ACTION_EDIT_UNDO, Strings.ACTION_EDIT_UNDO_LABELED);
    }

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

    /** Declared weakest first: a later constant outranks an earlier one. */
    private enum LabelPrecedence {
        SPAN_OR_TIE,
        PROPERTY,
        ELEMENT,
        LINE
    }

    private static LabelPrecedence precedenceOf(Mutation mutation) {
        return switch (mutation) {
            case LineInsertion _, LineDeletion _ -> LabelPrecedence.LINE;
            case ElementInsertion _, ElementDeletion _, ElementReplacement _, ElementModification _ ->
                LabelPrecedence.ELEMENT;

            case MetadataChange _, LayoutChange _, LineKeyChange _, LineLayoutChange _, LyricsChange _,
                 FontChange _ -> LabelPrecedence.PROPERTY;

            case SpanAddition _, SpanRemoval _, TieAddition _, TieRemoval _ -> LabelPrecedence.SPAN_OR_TIE;
        };
    }

    /** Callers pass a non-empty step. */
    private static Mutation dominantMutation(List<? extends Mutation> step) {
        Mutation dominant = step.getFirst();

        for (var mutation : step) {
            if (precedenceOf(mutation).compareTo(precedenceOf(dominant)) > 0) {
                dominant = mutation;
            }
        }

        return dominant;
    }

    private static String opName(Mutation dominant) {
        return switch (dominant) {
            case ElementInsertion _ -> Strings.get(Strings.ACTION_EDIT_OP_ADD_NOTE);
            case ElementDeletion deletion ->
                Strings.get(Strings.ACTION_EDIT_OP_DELETE_NOTE, deletion.range().size());

            case ElementReplacement _ -> Strings.get(Strings.ACTION_EDIT_OP_REPLACE_NOTE);
            case ElementModification _ -> Strings.get(Strings.ACTION_EDIT_OP_EDIT_NOTE);
            case LineInsertion _ -> Strings.get(Strings.ACTION_EDIT_OP_ADD_LINE);
            case LineDeletion _ -> Strings.get(Strings.ACTION_EDIT_OP_DELETE_LINE);
            case LineKeyChange _ -> Strings.get(Strings.ACTION_EDIT_OP_CHANGE_KEY);
            case LineLayoutChange _, LayoutChange _ -> Strings.get(Strings.ACTION_EDIT_OP_CHANGE_LAYOUT);
            case TieAddition _, TieRemoval _ -> Strings.get(Strings.ACTION_EDIT_OP_TIE);
            case SpanAddition addition -> Strings.get(spanOpNameKey(addition.span()));
            case SpanRemoval removal -> Strings.get(spanOpNameKey(removal.span()));
            case MetadataChange metadataChange -> Strings.get(metadataOpNameKey(metadataChange.field()));
            case FontChange _ -> Strings.get(Strings.ACTION_EDIT_OP_CHANGE_FONTS);
            case LyricsChange _ -> Strings.get(Strings.ACTION_EDIT_OP_EDIT_LYRICS);
        };
    }

    private static String spanOpNameKey(Span<?> span) {
        return switch (span) {
            case Beam _ -> Strings.ACTION_EDIT_OP_BEAMING;
            case Tuplet _ -> Strings.ACTION_EDIT_OP_TUPLET;
            case Hairpin hairpin -> switch (hairpin.getKind()) {
                case CRESCENDO -> Strings.ACTION_EDIT_OP_CRESCENDO;
                case DIMINUENDO -> Strings.ACTION_EDIT_OP_DIMINUENDO;
            };
            case Trill _ -> Strings.ACTION_EDIT_OP_TRILL;
            case Ending _ -> Strings.ACTION_EDIT_OP_ENDING;
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
