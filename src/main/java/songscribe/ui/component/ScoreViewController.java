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

package songscribe.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Attachment;
import songscribe.dom.AttachmentRemoval;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.EndingValidationResult;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Key;
import songscribe.dom.KeyChangeElement;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.hit.HitTarget;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.AddHairpinCommand;
import songscribe.message.command.AutoStemDirectionCommand;
import songscribe.message.command.DeselectCommand;
import songscribe.message.command.FirstSecondEndingCommand;
import songscribe.message.command.FlipStemDirectionCommand;
import songscribe.message.command.InsertLineCommand;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.message.command.SelectAllElementsCommand;
import songscribe.message.command.ToggleBeamCommand;
import songscribe.message.command.ToggleBeamWithPreviousCommand;
import songscribe.message.command.ToggleFallCommand;
import songscribe.message.command.ToggleFallOnLastInsertionCommand;
import songscribe.message.command.ToggleGlissandoCommand;
import songscribe.message.command.ToggleGlissandoWithPreviousCommand;
import songscribe.message.command.ToggleTieCommand;
import songscribe.message.command.ToggleTieWithPreviousCommand;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.command.UpdatePreviewElementCommand;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineScopedMutation;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.SpanMutation;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.ElementTypeWasSelectedNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.message.notification.PreviewElementDidChangeNotification;
import songscribe.message.notification.RestModeDidChangeNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.message.notification.TupletsWereRemovedNotification;
import songscribe.prefs.PrefsKey;
import songscribe.ui.EditResult;
import songscribe.ui.EndingConfirms;
import songscribe.ui.Mode;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.MusicEditOperations.HairpinResolution;
import songscribe.ui.OptionDialogs;
import songscribe.ui.SlideOperations;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.clipboard.Fragment;
import songscribe.ui.clipboard.PasteSpanReconciliation;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.edit.AccidentalRestatements;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.InsertionPointMode;
import songscribe.ui.edit.ScoreActions;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.ElementSelection;
import songscribe.ui.selection.SelectionActionApplier;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.ui.selection.TupletToggleInfo;
import songscribe.undo.OpNames;
import songscribe.undo.UndoController;
import songscribe.util.Debounce;
import songscribe.util.UIUtils;

/**
 * Coordinates message handling for the ScoreView component.
 * Handles all @Handler methods for messages posted to the MessageCenter.
 */
public final class ScoreViewController {

    private static final Logger LOG = LoggerFactory.getLogger(ScoreViewController.class);

    // Delay in milliseconds for debouncing repaint when layout changes occur
    private static final int REPAINT_DEBOUNCE_DELAY_MS = 300;

    // Runs before all HIGH_PRIORITY subscribers so the tuplet info cache is warm
    // by the time TupletAction handlers (HIGH_PRIORITY) read it.
    static final int TUPLET_INFO_CACHE_PRIORITY = Message.HIGH_PRIORITY + 100;

    private final ScoreView score;
    private final ScoreActions scoreActions;
    private final MusicEditOperations operations;
    private final SelectionCoordinator selectionCoordinator;
    private final ClipboardManager clipboardManager;

    // Cached per-notification-dispatch result of canToggleTuplet(), populated by
    // a TUPLET_INFO_CACHE_PRIORITY handler before any TupletAction handler reads it.
    @Nullable
    private TupletToggleInfo cachedTupletToggleInfo = null;

    // Debounces repaints when layout changes occur
    final Debounce repaintDebounce;

    public ScoreViewController(
        ScoreView score,
        MusicEditOperations operations,
        SelectionCoordinator selectionCoordinator,
        ClipboardManager clipboardManager
    ) {
        this.score = score;
        scoreActions = score;
        this.operations = operations;
        this.selectionCoordinator = selectionCoordinator;
        this.clipboardManager = clipboardManager;

        repaintDebounce = Debounce.rescheduling(REPAINT_DEBOUNCE_DELAY_MS, score::repaint);

        MessageCenter.subscribe(this);
    }

    private void warmTupletCache() {
        cachedTupletToggleInfo = operations.canToggleTuplet();
    }

    @Handler
    public void elementTypeWasSelected(ElementTypeWasSelectedNotification message) {
        score.setPreviewElement(EditModeManager.makePreviewElement(message.getNoteType()));
    }

    @Handler
    public void restModeDidChange(RestModeDidChangeNotification message) {
        score.setPreviewElement(EditModeManager.makePreviewElement());
    }

    @Handler
    public void handleUpdatePreviewElement(UpdatePreviewElementCommand message) {
        switch (message.getScope()) {
            case DECORATIONS -> updatePreviewElement();
            case ELEMENT -> syncPreviewElementWithSelectedDuration();
        }
    }

    /**
     * Syncs the preview element with the selected duration or bar action.
     * <p>
     * Delegates to {@link EditModeManager#makePreviewElement()}, which prefers the selected
     * action's type but falls back to a default type when neither action group has a selection.
     * That fallback matters: a delete discards the saved action states, which can leave both
     * groups deselected, and without it edit mode would be left with no preview element at all
     * and no way to ever recreate one.
     */
    private void syncPreviewElementWithSelectedDuration() {
        score.setPreviewElement(EditModeManager.makePreviewElement());
    }

    private void updatePreviewElement() {
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement != null) {
            EditModeManager.decorateElement(previewElement);
            MessageCenter.post(new PreviewElementDidChangeNotification(previewElement));

            // Decorations (accidental, dots, articulations) change the ink the preview overlay
            // has cached, so it has to re-record rather than merely repaint.
            PreviewElementManager.previewElementDidChange();
            score.repaint();
        } else {
            score.setPreviewElement(EditModeManager.makePreviewElement());
        }
    }

    @Handler
    public void handleInsertLine(InsertLineCommand message) {
        var song = score.getSong();
        var line = new Line(song);

        // The two relative variants are disabled without a line selection,
        // so selectedLine is only read where it is known to be valid.
        var selectedLine = selectionCoordinator.getSelectedLine();

        switch (message.getType()) {
            case ADD_AT_END -> song.addLine(line);
            case INSERT_BEFORE -> song.addLine(selectedLine, line);
            case INSERT_AFTER -> song.addLine(selectedLine + 1, line);
        }

        score.deselect();
    }

    @Handler
    public void handleToggleBeam(ToggleBeamCommand message) {
        if (selectionCoordinator.getRange() == null) {
            return;
        }

        operations.toggleBeaming();
    }

    /**
     * Shared shell for a command that acts on the last insertion: beam, tie, glissando and fall
     * all gate on the same two preconditions and answer for the outcome under the same rule,
     * differing only in which operation they run.
     *
     * <p>{@code operation} performs the edit and reports what it did. Each outcome has exactly
     * one response: {@code MODIFIED} re-points the key at the element it just acted on, so a
     * second press toggles the same pair back off; {@code REFUSED} beeps, because a silent
     * failure leaves the user unable to tell the press registered at all; {@code REPORTED} says
     * nothing, because an error dialog is already on screen and a beep as it is dismissed reads
     * as a second, separate failure.
     *
     * <p>Re-pointing is safe here, and only here, because message posting is synchronous: the
     * operation has returned, so its bracket has closed and the notification that cleared the
     * slot has already been delivered. That is what lets the outcome be observed rather than
     * predicted — an operation cannot arm ahead of its own gate without leaving the slot armed
     * on the refusing paths, where no commit ever arrives to consume it.
     *
     * <p>{@code targetAction} is the menu action the key falls through to when the binding is
     * disabled, and supplies the Tier-A undo label through the same
     * {@code UndoController.withPendingOpName} bracket {@code UIAction.actionPerformed} uses.
     * These commands arrive from key bindings that bypass {@code UIAction} entirely, so without
     * it the step falls through to {@code UndoController.opNameKey}'s mutation-kind fallback and
     * the key labels the very same edit differently from the menu action that also performs it —
     * "Undo Beaming" against the menu's "Undo Toggle Beam". Reading the label off the action is
     * what makes those two agree by construction, and it already answers {@code null} for the
     * Tier-B operations that label their own bracket ({@code SlideOperations} does, via
     * {@code OpNames}).
     */
    private void handleLastInsertionCommand(
        UIAction targetAction,
        Function<EditModeManager.Insertion, EditResult> operation
    ) {
        // Stands in for the DISABLE_WHEN_PLAYING flag the toggle-beam action carries;
        // this command arrives from a key binding that no action's enabled state gates.
        if (PlaybackController.isPlaying()) {
            UIUtils.beep();
            return;
        }

        var insertion = EditModeManager.getLastInsertion();

        if (insertion == null) {
            UIUtils.beep();
            return;
        }

        var result = UndoController.withPendingOpNameResult(
            targetAction.getUndoOpName(), () -> operation.apply(insertion));

        switch (result) {
            case MODIFIED -> EditModeManager.setLastInsertion(insertion.line(), insertion.elementIndex());
            case REFUSED -> UIUtils.beep();
            case REPORTED -> { }
        }
    }

    @Handler
    public void handleToggleBeamWithPrevious(ToggleBeamWithPreviousCommand message) {
        handleLastInsertionCommand(
            Actions.TOGGLE_BEAM_ACTION,
            insertion -> EditResult.of(
                MusicEditOperations.toggleBeamWithPredecessor(insertion.line(), insertion.elementIndex())));
    }

    @Handler
    public void handleToggleTieWithPrevious(ToggleTieWithPreviousCommand message) {
        handleLastInsertionCommand(
            Actions.TOGGLE_TIE_ACTION,
            insertion -> EditResult.of(
                MusicEditOperations.toggleTieWithPredecessor(insertion.line(), insertion.elementIndex())));
    }

    @Handler
    public void handleToggleGlissando(ToggleGlissandoCommand message) {
        if (selectionCoordinator.getRange() == null) {
            return;
        }

        operations.toggleGlissando(score.getLyricRenderMetrics());
    }

    @Handler
    public void handleToggleFall(ToggleFallCommand message) {
        if (selectionCoordinator.getRange() == null) {
            return;
        }

        operations.toggleFall(score.getLyricRenderMetrics());
    }

    @Handler
    public void handleToggleGlissandoWithPrevious(ToggleGlissandoWithPreviousCommand message) {
        handleLastInsertionCommand(
            Actions.GLISSANDO_ACTION,
            insertion -> SlideOperations.toggleGlissandoWithPredecessor(
                insertion.line(), insertion.elementIndex(), score.getLyricRenderMetrics()));
    }

    @Handler
    public void handleToggleFallOnLastInsertion(ToggleFallOnLastInsertionCommand message) {
        handleLastInsertionCommand(
            Actions.FALL_ACTION,
            insertion -> SlideOperations.toggleFallOnLastInsertion(
                insertion.line(), insertion.elementIndex(), score.getLyricRenderMetrics()));
    }

    @Handler
    public void handleToggleTie(ToggleTieCommand message) {
        operations.toggleTie();
    }

    @Handler
    public void handleToggleTuplet(ToggleTupletCommand message) {
        // The cached form, not operations.canToggleTuplet(): computing it walks the song
        // backward to the governing beat and tests every candidate grade, and the cache
        // holds the answer for this very selection, warmed by the same notification that
        // enabled the action the user just triggered.
        operations.toggleTuplet(message.getTupletSize(), canToggleTuplet());
        score.selectionChanged();
    }

    /**
     * Warns that an edit forced tuplets out of the song. Every route that can do so — either
     * attachment dialog's Add, Modify or Remove button, the song's own tempo note value, and
     * a paste whose destination breaks the pasted span — reports through this one handler,
     * so the warning appears exactly once per edit no matter how many tuplets went. Only the
     * wording varies, because "the beat changed" would be a puzzle to someone who just
     * pressed Paste.
     */
    @Handler
    public void tupletsWereRemoved(TupletsWereRemovedNotification message) {
        var messageKey = switch (message.getCause()) {
            case BEAT_EDIT -> Strings.ALERT_TUPLETS_REMOVED_BEAT;
            case PASTE -> Strings.ALERT_TUPLETS_REMOVED_PASTE;
        };

        OptionDialogs.showWarningMessage(
            null,
            Strings.ALERT_TITLE_TUPLETS_REMOVED,
            messageKey
        );
    }

    @Handler
    public void handleAddHairpin(AddHairpinCommand message) {
        operations.addHairpinToSelection(message.kind());
    }

    @Handler
    public void handleFirstSecondEnding(FirstSecondEndingCommand message) {
        var result = Actions.MAKE_ENDING_ACTION.getCachedResult();

        if (result != null && result.isValid()) {
            operations.makeFirstSecondEnding(result);
            MessageCenter.post(new DeselectCommand());
        }
    }

    @Handler
    public void handleFlipStemDirection(FlipStemDirectionCommand message) {
        operations.flipStemDirection();
    }

    @Handler
    public void handleAutoStemDirection(AutoStemDirectionCommand message) {
        operations.autoStemDirection();
    }

    public boolean canToggleBeaming() {
        return operations.canToggleBeaming();
    }

    public boolean canToggleTie() {
        return operations.canToggleTie();
    }

    public boolean canToggleGlissando() {
        return operations.canToggleGlissando();
    }

    public boolean canToggleFall() {
        return operations.canToggleFall();
    }

    public TupletToggleInfo canToggleTuplet() {
        var cached = cachedTupletToggleInfo;

        if (cached != null) {
            return cached;
        }

        return operations.canToggleTuplet();
    }

    public HairpinResolution resolveHairpinAction(Hairpin.Kind kind) {
        return operations.resolveHairpinAction(kind);
    }

    public EndingValidationResult canMakeFirstSecondEnding() {
        return operations.canMakeFirstSecondEnding();
    }

    public boolean canChangeTempo() {
        return operations.canChangeTempo();
    }

    public boolean canModifyStemDirection() {
        return operations.canModifyStemDirection();
    }

    @Handler(priority = TUPLET_INFO_CACHE_PRIORITY)
    public void musicSelectionDidChangeCacheTupletInfo(MusicSelectionDidChangeNotification message) {
        warmTupletCache();
    }

    @Handler(priority = TUPLET_INFO_CACHE_PRIORITY)
    public void documentDidLoadCacheTupletInfo(DocumentDidLoadNotification message) {
        warmTupletCache();
    }

    @Handler
    public void prefsDidChange(PrefsDidChangeNotification message) {
        // PrefsKey.ALL fires on resetAll() and is the only signal that any
        // specific key may have changed — handle it for every affected effect.
        var key = message.getKey();
        var all = key == PrefsKey.ALL;

        if (
            all ||
            key == PrefsKey.LOOP_PLAYBACK ||
            key == PrefsKey.PLAY_WITH_REPEATS ||
            key == PrefsKey.PLAY_INSERTED_NOTE ||
            key == PrefsKey.PLAYBACK_NOTE_DURATION ||
            key == PrefsKey.TEMPO_CHANGE_PERCENT ||
            key == PrefsKey.INSTRUMENT
        ) {
            scoreActions.syncPlaybackPrefs();
        }

        if ((all || key == PrefsKey.PAGE_SIZE) && score.isInitialized()) {
            scoreActions.updatePageLayout(score.getSong().getLineWidthSs());
        }
    }

    @Handler
    public void textEditingDidChange(TextEditingDidChangeNotification message) {
        scoreActions.setKeyBindingsEnabled(!message.isEditing());
    }

    @Handler(priority = TUPLET_INFO_CACHE_PRIORITY)
    public void songDidChange(SongDidChangeNotification message) {
        // The selected range is spliced through this batch of mutations here, before any
        // other reader of the range sees it — in particular ahead of warmTupletCache, the
        // first such reader below. That ordering is expressed as program order within this
        // one method rather than as a second priority constant that could drift out of step
        // with TUPLET_INFO_CACHE_PRIORITY.
        selectionCoordinator.revalidateElementSelection(message);

        warmTupletCache();

        var mainPanel = score.getMainPanel();

        if (mainPanel == null) {
            return;
        }

        // Three mutually exclusive cases, checked in order: a full relayout invalidates every
        // LinePanel's layout; a line insert or delete rebuilds the LinePanel list; and a
        // line-scoped change invalidates only the LinePanels the change affects (one, or two
        // for a span straddling a line boundary).
        //
        // Font, metadata, and layout changes (e.g. a Song Settings commit) all
        // require re-laying out every line, not just repainting: invalidating each
        // line clears its cached LayoutResult so positions are recomputed.
        if (hasFullRelayoutMutation(message)) {
            for (var linePanel : mainPanel.getStaffPanel().getLinePanels()) {
                linePanel.getLineComponent().invalidateLayout();
            }
        } else if (message.hasMutationOf(LineInsertion.class) || message.hasMutationOf(LineDeletion.class)) {
            // StaffPanel.rebuildLayout() is the only add/remove primitive for LinePanels;
            // per-panel invalidation cannot add a panel for an inserted line or remove one
            // for a deleted line. Coarse (recreates every LinePanel) but correct for both
            // forward edits and undo/redo replay, which funnel through this same handler.
            mainPanel.getStaffPanel().rebuildLayout();
            // rebuildLayout() creates fresh LineComponents with only song and line set;
            // re-wire the scoreView into each (as document load does) so their next
            // performLayout() has a live scoreView and does not produce a null layout.
            score.setupLineComponentState();
        } else if (hasLineLayoutMutation(message)) {
            var staffPanel = mainPanel.getStaffPanel();
            var targetLine = message.getLine();
            var spans = mutatedSpans(message);

            for (var linePanel : staffPanel.getLinePanels()) {
                var line = linePanel.getLine();

                if (targetLine == null || line == targetLine || spanReaches(spans, line)) {
                    linePanel.getLineComponent().invalidateLayout();

                    // With no span in the notification the named line is the only one that can
                    // match, so the panels after it have nothing left to be checked against.
                    if (targetLine != null && spans.isEmpty()) {
                        break;
                    }
                }
            }
        }

        // Re-sync derived layout coordinates from the (now invalidated) components.
        if (hasFullRelayoutMutation(message)) {
            score.viewChanged();
        }

        // Debounce repaints to batch multiple rapid changes
        repaintDebounce.trigger();
    }

    /**
     * Every span this notification's mutations name, in the order the mutations were
     * recorded, or an empty list when none of them names one.
     * <p>
     * Collected once for the whole panel loop rather than re-derived per panel: the loop runs
     * over every line on screen, and a single paste bundles dozens of mutations. An empty
     * result is also what lets that loop stop at the line the notification names.
     * <p>
     * Every mutation carrying a span answers, not only the tie ones, so the answer matches
     * the question {@link #spanReaches} asks. Only a tie can straddle a line boundary today,
     * which is what makes the others no-ops rather than optional: a beam, tuplet or hairpin
     * resolves to the one line it is in, and {@link Span#isIn} then reports that line alone —
     * the same line the mutation already named. Including them costs nothing and means a span
     * type that later gains a second line is drawn on both.
     */
    private static List<Span> mutatedSpans(SongDidChangeNotification message) {
        List<Span> spans = null;

        for (var mutation : message.getMutations()) {
            if (mutation instanceof SpanMutation spanMutation) {
                if (spans == null) {
                    spans = new ArrayList<>();
                }

                spans.add(spanMutation.getSpan());
            }
        }

        return spans == null ? List.of() : spans;
    }

    /**
     * Returns whether any of {@code spans} has an endpoint in {@code line}, making
     * {@code line} one of the lines that draws it.
     * <p>
     * A line-scoped mutation names one line, which for almost every span is the only line
     * whose rendering it changes. A tie whose two notes sit in different lines is the
     * exception: adding or removing it changes what <em>both</em> lines draw — one gains or
     * loses the half running off its right edge, the other the half entering from the left —
     * while the mutation still names only one of them. Without this, the far line keeps its
     * cached {@code LayoutResult} and its half is never drawn, which is the half-tie the
     * whole of #493 exists to prevent, arriving by way of the repaint rather than the model.
     * <p>
     * Ties are the only spans that can straddle a boundary today; the test below is written
     * over spans in general because the question — which lines does this span reach — has the
     * same answer for all of them, not because the others are expected to.
     */
    private static boolean spanReaches(List<Span> spans, Line line) {
        for (var span : spans) {
            if (span.isIn(line)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether the notification carries any mutation that requires line layout
     * invalidation: line-scoped element changes (including per-note lyric edits, which
     * arrive as {@code ElementModification} with {@code ElementField.LYRIC}), or line
     * insert/delete. Song-wide {@code LyricsChange} mutations target legacy text
     * fields and do not affect rendered layout.
     */
    private static boolean hasLineLayoutMutation(SongDidChangeNotification message) {
        for (var mutation : message.getMutations()) {
            if (mutation instanceof LineScopedMutation
                || mutation instanceof LineInsertion
                || mutation instanceof LineDeletion) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether the notification carries any mutation that requires a full
     * song relayout (font / metadata / layout property changes).
     */
    private static boolean hasFullRelayoutMutation(SongDidChangeNotification message) {
        for (var mutation : message.getMutations()) {
            if (mutation instanceof FontChange
                || mutation instanceof MetadataChange
                || mutation instanceof LayoutChange) {
                return true;
            }
        }

        return false;
    }

    @Handler(priority = Message.HIGH_PRIORITY)
    public void modeDidChange(ModeDidChangeNotification message) {
        var mode = message.getMode();
        score.setMode(mode);

        // Entering edit mode drops any selection and syncs the preview element with the
        // currently selected duration button. Reflection may have changed the selected
        // button while in select mode without posting a DurationSelectedMessage.
        if (mode == Mode.EDIT) {
            score.clearSelection();
            syncPreviewElementWithSelectedDuration();
        }

        score.repaint();
    }

    @Handler
    public void playbackStateDidChange(
        PlaybackStateDidChangeNotification message
    ) {
        var state = message.getState();

        if (state == PlaybackController.PlaybackState.PLAYING) {
            return;
        }

        // A rewind goes back to the top of the song, so the selection that would otherwise
        // anchor the next Play is dropped. The score owns its selection, so it drops it
        // here rather than being reached into from the playback controller. Guarded because
        // clearSelection() posts a MusicSelectionDidChangeNotification even when there was
        // nothing selected.
        if (state == PlaybackController.PlaybackState.REWOUND && score.getSelection() != null) {
            score.clearSelection();
        }

        score.repaint();
    }

    @Handler
    public void musicSelectionDidChangeCancelRewind(MusicSelectionDidChangeNotification message) {
        // Selecting music after a rewind means the next Play should start at that selection
        // rather than at the top of the song. Ignore the cleared case, which is the rewind
        // itself reaching this handler via clearSelection() above.
        if (score.getSelection() != null) {
            PlaybackController.selectionDidChange();
        }
    }

    @Handler
    public void handlePasteboardOp(PasteboardOpCommand message) {
        // Belt-and-braces: while a placement is pending all pasteboard operations are
        // ignored. The action layer is already disabled via enableFromInsertionPointMode;
        // this covers any non-action dispatch path.
        if (InsertionPointMode.isActive()) {
            return;
        }

        // Make sure this component has focus
        if (!score.isFocusOwner()) {
            return;
        }

        switch (message.getOperation()) {
            case CUT -> handleCut();
            case COPY -> handleCopy();
            case DELETE -> handleDelete();
            case PASTE -> handlePaste();
        }
    }

    private void handleCut() {
        var range = selectionCoordinator.getRange();

        if (range == null) {
            return;
        }

        var line = range.line();
        var begin = range.begin();
        var end = range.end();

        // Confirm before discarding an ending invalidated by the deletion, and do
        // it first: declining must leave both the clipboard and the score untouched.
        if (!confirmEndingInvalidatedByDeletion(line, begin, end)) {
            return;
        }

        // Asked before the bracket opens, and before the clipboard is written: cancelling must
        // leave both the clipboard and the score untouched, exactly as declining the ending
        // confirm above does.
        var decision = confirmDeletionRestatements(line, begin, end);

        if (decision.isCancelled()) {
            return;
        }

        handleCopy();

        // Clear the selection before removing elements so that action handlers
        // reacting to SongDidChangeNotification (posted synchronously when the
        // modification bracket closes) don't query selection indices that no
        // longer exist on the shrunk line.
        selectionCoordinator.clearSelection();

        // One bracket for the deletion — the confirms above already ran, so
        // deleteElementRange performs no further confirmation. The Cut action's op-name
        // (Tier A) names this outermost step, so the inner range delete passes no label.
        score.getSong().withModification(() -> deleteElementRange(line, begin, end, null, decision));

        // Discard saved action states — the song has changed, so restoring
        // pre-selection states would be stale. Individual action handlers will
        // re-evaluate their enabled state from the current context.
        selectionCoordinator.getActionReflector().clearSavedActionStates();
        score.deselect();
    }

    void handleCopy() {
        var range = selectionCoordinator.getRange();

        if (range != null) {
            clipboardManager.setFragment(
                Fragment.capture(range.line(), range.begin(), range.end())
            );
            score.deselect();
        }
    }

    /**
     * Deletes whatever the Backspace / Delete keystroke should delete, dispatching on what is
     * selected. The lyric branch owns its own cleanup and returns; every other branch falls
     * through to the shared tail.
     *
     * <p>The branches, checked in order:
     *
     * <ul>
     *   <li>A selected {@code HitTarget.Lyric} clears the lyric via {@code modifyElement(LYRIC)},
     *       then restores action states, clears the selection, repaints and returns — it does not
     *       reach the shared tail.
     *   <li>A non-null element range deletes it with {@code deleteElementRange()}.
     *   <li>A non-null selected target dispatches on its kind in {@code deleteSelectedTarget()}: a
     *       slide, articulation or attachment goes through {@code modifyElement}; an ending or
     *       trill through {@code removeSpan}; a hairpin, tie, beam or tuplet through its own
     *       remover, with a tie removed from both lines it touches. An attachment resolves further
     *       to its own field (fermata, dynamic, annotation, beat change, tempo change). An
     *       accidental goes through {@code SelectionActionApplier.apply}, which runs restatements,
     *       reconciliation and the fit gate, and may refuse the deletion outright.
     *   <li>Otherwise, if {@code canDeleteLine()} allows it, the line itself is removed.
     * </ul>
     *
     * <p>Every branch but the lyric one falls through to the shared tail, which restores the
     * selected action states and deselects the score.
     */
    void handleDelete() {
        var song = score.getSong();

        if (selectionCoordinator.getSelectedTarget() instanceof HitTarget.Lyric(var element, var verse)) {
            var line = element.getParentLine();

            // An element in no line has no index, so there is nothing to delete —
            // the same outcome as the index guard below.
            if (line != null) {
                var index = line.getElementIndex(element);

                if (index >= 0) {
                    song.withModification(Strings.get(Strings.ACTION_EDIT_OP_DELETE_LYRIC), () -> {
                        line.modifyElement(index, ElementField.LYRIC, () ->
                            line.getElement(index).setLyricForVerse(verse, null, false, "", Lyric.Extend.NONE));
                        line.adjustNeighborsForLyricDeletion(index, verse);
                    });
                }
            }

            selectionCoordinator.getActionReflector().restoreSelectedActionStates();
            selectionCoordinator.clearSelection();
            score.selectionChanged();
            score.repaint();
            return;
        }

        var range = selectionCoordinator.getRange();

        // hasDecorationSelection() rather than a null check on the target: a whole-line
        // selection is also a target now, and it is deleted by the canDeleteLine branch
        // below rather than by deleteSelectedTarget.
        var selectedTarget = selectionCoordinator.hasDecorationSelection()
            ? selectionCoordinator.getSelectedTarget()
            : null;
        var targetLine = selectionCoordinator.getActiveLine();

        if (range != null) {
            var line = range.line();
            var begin = range.begin();
            var end = range.end();

            if (line.hasEndingInvalidatedByDeletion(begin, end)) {
                if (!EndingConfirms.confirmInvalidation(score)) {
                    return;
                }
            }

            var decision = confirmDeletionRestatements(line, begin, end);

            if (decision.isCancelled()) {
                return;
            }

            // Name the undo step from the categories of the user-selected elements
            // (computed before removal, while they are still present on the line).
            var selectedTypes = line.getElements(begin, end).stream()
                .map(StaffElement::getType)
                .toList();
            var deleteLabel = OpNames.deleteLabel(selectedTypes);

            // Clear the selection before removing elements so that action handlers
            // reacting to SongDidChangeNotification (posted synchronously when the
            // modification bracket closes) don't query selection indices that no
            // longer exist on the shrunk line.
            selectionCoordinator.clearSelection();

            // deleteElementRange's own bracket nests inside the one opened here and passes a
            // null label, since the op name is captured only at the outermost bracket.
            song.withModification(deleteLabel,
                () -> deleteElementRange(line, begin, end, null, decision));
        } else if (selectedTarget != null && targetLine != null) {
            deleteSelectedTarget(targetLine, selectedTarget);
        } else if (score.canDeleteLine()) {
            var lineIndex = selectionCoordinator.getSelectedLine();

            song.withModification(OpNames.deleteLineLabel(), () -> song.removeLine(lineIndex));
        }

        // Restore the pre-selection selected states but not the enabled states — the song
        // has changed, so individual action handlers must re-evaluate enablement from the
        // current context, while the user's chosen duration button survives the delete.
        selectionCoordinator.getActionReflector().restoreSelectedActionStates();
        score.deselect();
    }

    /**
     * Deletes the selected target from {@code line}, each variant in its own
     * modification bracket so the undo step is named after what was deleted.
     * <p>
     * Every notation object that can be directly selected is deleted here, each through the
     * tracked removal API that owns it, so Delete and the object's own toolbar toggle can
     * never disagree. The remaining variants are deliberately a no-op rather than falling
     * through to the whole-line delete.
     * <p>
     * {@link #handleDelete} carries the diagram of how a keystroke reaches each arm below.
     */
    private void deleteSelectedTarget(Line line, HitTarget target) {
        switch (target) {
            case HitTarget.Slide(var slideElement) -> {
                var elementIndex = line.getElementIndex(slideElement);
                var slide = slideElement.getSlide();

                // A slide selection is only ever made on an element that carries a slide and
                // is still on this line; guard anyway so neither the @Nullable getSlide()
                // result nor a -1 index is passed on unchecked.
                if (slide != null && elementIndex >= 0) {
                    // Capture before the removal: stripping the glissando un-pairs the grace
                    // note, so by the time the sync runs there is no pairing left to read.
                    var wasPairedGraceNote = line.isPairedGraceNote(elementIndex);

                    line.withModification(OpNames.deleteSlideLabel(slide), () -> {
                        line.modifyElement(elementIndex, ElementField.SLIDE, slideElement::removeSlide);

                        // Un-pairing dissolves the automatic melisma. Both elements survive, so
                        // the syllable simply stays on the now-ordinary former grace note.
                        if (wasPairedGraceNote) {
                            line.syncGraceHostMelisma(elementIndex);
                        }
                    });
                }
            }

            case HitTarget.Ending(var ending) ->
                line.withModification(OpNames.deleteEndingLabel(), () -> line.removeSpan(ending));

            case HitTarget.Hairpin(var hairpin) ->
                line.withModification(OpNames.deleteHairpinLabel(hairpin), () -> {
                    switch (hairpin) {
                        case Crescendo crescendo -> line.removeCrescendo(crescendo);
                        case Diminuendo diminuendo -> line.removeDiminuendo(diminuendo);
                    }
                });

            // Line.removeChild also removes the tie from the other line of a cross-line tie,
            // so both halves vanish under a single TieRemoval and one undo restores both.
            case HitTarget.Tie(var tie) ->
                line.withModification(OpNames.removeTieLabel(), () -> line.removeTie(tie));

            case HitTarget.Beam(var beam) ->
                line.withModification(OpNames.removeBeamLabel(), () -> line.removeBeaming(beam));

            case HitTarget.Tuplet(var tuplet) ->
                line.withModification(OpNames.removeTupletLabel(), () -> line.removeTuplet(tuplet));

            // The explicit label is not optional: the generic SpanRemoval's unlabeled fallback
            // op-name is the literal text "Ending", so an unlabeled trill removal would make
            // the undo menu read "Undo Ending".
            case HitTarget.Trill(var trill) ->
                line.withModification(OpNames.removeTrillLabel(), () -> line.removeSpan(trill));

            case HitTarget.Articulation(var articulation) -> {
                var owner = articulation.getOwnerElement();
                var elementIndex = resolveOwnerIndex(line, owner);

                if (owner != null && elementIndex != null) {
                    line.withModification(OpNames.removeArticulationLabel(articulation.getType()), () ->
                        line.modifyElement(elementIndex, ElementField.ARTICULATION,
                            () -> owner.removeArticulation(articulation)));
                }
            }

            case HitTarget.Attachment(var attachment) -> deleteAttachment(line, attachment);

            case HitTarget.Accidental(var owner) -> deleteAccidental(line, owner);

            // Nothing to delete. Listed one per kind rather than folded into a `default` arm so
            // that adding a selectable kind fails to compile here and forces the question of
            // what Delete should do with it. A `default` would answer "nothing" silently, which
            // is the failure mode SelectionCoordinator.isSelected avoids for the same reason.
            //
            // A note is selected as an index range rather than a target, so it never arrives
            // here at all — the range branch in handleDelete owns it. A grace-note glissando is
            // not selectable, and the staff line and a lyric are handled by handleDelete before
            // it reaches here.
            case HitTarget.Element _,
                 HitTarget.GraceGlissando _,
                 HitTarget.StaffLine _,
                 HitTarget.Lyric _ -> {
            }
        }
    }

    /**
     * Resolves {@code owner}'s index on {@code line}, or null when there is no owner or it is
     * no longer on the line.
     * <p>
     * Both failures are logged because every caller silently swallows the user's keystroke when
     * this returns null, and a Delete that does nothing with no diagnostic is the worst outcome
     * this feature can produce.
     */
    private static @Nullable Integer resolveOwnerIndex(Line line, @Nullable StaffElement owner) {
        if (owner == null) {
            LOG.debug("Cannot delete the selected target: it has no owner element");
            return null;
        }

        var elementIndex = line.getElementIndex(owner);

        if (elementIndex < 0) {
            LOG.debug("Cannot delete the selected target: its owner element ({}) is not on the active line",
                owner.getType());
            return null;
        }

        return elementIndex;
    }

    /**
     * What a single attachment removal changes on its owner element: the field the mutation is
     * recorded under, and the mutation itself.
     */
    private record AttachmentRemovalPlan(ElementField field, Runnable mutator) {}

    /**
     * Deletes {@code attachment} from its owner element on {@code line}.
     */
    private void deleteAttachment(Line line, Attachment attachment) {
        var element = attachment.getOwnerElement();
        var elementIndex = resolveOwnerIndex(line, element);

        if (element == null || elementIndex == null) {
            return;
        }

        var plan = switch (attachment) {
            case FermataAttachment _ ->
                new AttachmentRemovalPlan(ElementField.FERMATA, () -> element.setFermata(false));

            case DynamicAttachment dynamic ->
                new AttachmentRemovalPlan(ElementField.DYNAMIC_ATTACHMENT, () -> element.removeAttachment(dynamic));

            case AnnotationAttachment _ ->
                new AttachmentRemovalPlan(ElementField.ANNOTATION, () -> AttachmentRemoval.removeAnnotation(element));

            case BeatChangeAttachment _ ->
                new AttachmentRemovalPlan(ElementField.BEAT_CHANGE, () -> AttachmentRemoval.removeBeatChange(element));

            case TempoChangeAttachment _ ->
                new AttachmentRemovalPlan(ElementField.TEMPO_CHANGE, () -> AttachmentRemoval.removeTempoChange(element));
        };

        line.withModification(OpNames.removeAttachmentLabel(attachment), () ->
            line.modifyElement(elementIndex, plan.field(), plan.mutator()));
    }

    /**
     * Deletes {@code owner}'s accidental, routed through the same pipeline the toolbar toggle
     * uses so the two can never disagree about restatements and courtesy accidentals. The
     * pipeline can refuse — its reconciliation and fit gate decide, not this method.
     */
    private void deleteAccidental(Line line, StaffElement owner) {
        var elementIndex = resolveOwnerIndex(line, owner);

        if (elementIndex == null || owner.getAccidental() == null) {
            return;
        }

        // Any action in the group will do: AccidentalAction.applyToElement(element, false) is
        // element.setAccidental(null) regardless of which accidental the action represents, and
        // decideChanges gates on appliesTo rather than matchesElement. Scanning the group for
        // the action matching this note would buy nothing and its no-match branch would
        // silently eat the keystroke.
        var accidentalAction = Actions.ACCIDENTAL_ACTION_GROUP.getActions().getFirst();

        SelectionActionApplier.apply(
            selectionCoordinator,
            new ElementSelection(line, elementIndex, elementIndex),
            accidentalAction,
            false,
            score,
            OpNames.removeAccidentalLabel());
    }

    /**
     * Returns true when the caller may proceed with a deletion of {@code [begin, end]}:
     * either it discards no ending, or the user confirmed discarding one. Callers
     * must run this before mutating anything — declining leaves the score untouched.
     */
    private boolean confirmEndingInvalidatedByDeletion(Line line, int begin, int end) {
        return !line.hasEndingInvalidatedByDeletion(begin, end)
            || EndingConfirms.confirmInvalidation(score);
    }

    /**
     * Asks whether a deletion of {@code [begin, end]} should also take away the later notes that
     * restate the accidentals it removes. Callers must run this <b>before</b> opening a
     * modification bracket, and must abandon the deletion entirely when the answer is Cancel.
     *
     * <p>The range is widened exactly as {@link #deleteElementRange} widens it — a paired grace
     * note before the range does not survive its host, and a trailing breath mark goes with the
     * range — so the accidentals offered are the ones the deletion really removes.
     */
    private AccidentalRestatements.Decision confirmDeletionRestatements(Line line, int begin, int end) {
        var bounds = line.effectiveRange(begin, end);

        return AccidentalRestatements.confirm(
            score, line, AccidentalRestatements.inDeletedRange(line, bounds.begin(), bounds.end()));
    }

    /**
     * Establishes {@code key} at the start of {@code line}, or clears the line's own key so that it
     * inherits again, reconciling the accidentals the change moves and naming the resulting undo
     * step {@code label}.
     *
     * <p>A key change moves pitches, so it owes the same protection an inserted barline owes: every
     * note whose sounding pitch the change would move is given an explicit accidental, and notation
     * the change makes redundant is cleared. Its reach is the inheritance chain — from this line
     * forward to the first line that establishes a key of its own — which is why this is the one
     * edit in the program whose reconciliation spans more than one line. See
     * {@code docs/key-signatures.md}.
     *
     * <p>The restatement prompt, when there is one, covers the whole reach in a single dialog and
     * is raised <b>before</b> the modification bracket opens, as every other confirm is.
     *
     * <p><b>The fit check is the caller's, and runs first.</b> A change that will be refused for not
     * fitting must not first ask the user about accidentals it will never apply.
     *
     * @param line  The line whose own key changes
     * @param key   The key to establish here, or null to inherit the previous line's
     * @param label The name the undo step takes
     * @return True when the change was committed; false when the notator cancelled at the
     *         restatement prompt, in which case nothing was mutated and no undo step exists
     * @throws IllegalArgumentException if {@code key} is null on a line with nothing to inherit
     *                                  from — the first line of the song, which must establish a
     *                                  key of its own
     */
    public boolean changeLineKey(Line line, @Nullable Key key, String label) {
        var song = line.getSong();
        var lineIndex = song.indexOfLine(line);

        if (key == null && lineIndex <= 0) {
            throw new IllegalArgumentException(
                "the first line of a song has nothing to inherit a key from");
        }

        var runningKey = key != null ? key : song.getLine(lineIndex - 1).keyAtEndOfLine();
        var reach = AccidentalReconciliation.lineKeyChangeReach(line, runningKey);

        // Pure and pre-mutation, so the removals it would make can be read off it and asked about
        // before anything is written.
        var reconciled = AccidentalReconciliation.reconcileModification(
            reach, AccidentalReconciliation.RestatementRemoval.NONE);

        var decision = AccidentalRestatements.confirm(
            score, AccidentalRestatements.accidentalsClearedBy(reconciled));

        if (decision.isCancelled()) {
            return false;
        }

        // Reconciled a second time only when the answer moves the result: accepted restatements
        // are cleared by the same walk everything else travels.
        var accidentalChanges = decision.answer() == AccidentalRestatements.Answer.YES
            ? AccidentalReconciliation.reconcileModification(reach, decision.removal())
            : reconciled;

        var reconciledLines = reach.stream()
            .map(AccidentalReconciliation.ModifiedLine::line)
            .toList();

        song.withModification(label, () -> {
            for (var reconciledLine : accidentalChanges) {
                AccidentalMaterializer.commit(reconciledLine.line(), reconciledLine.changes());
            }

            // The accepted restatements sitting past the reach's end, which no line of the reach
            // reconciles.
            AccidentalRestatements.commitOtherLines(decision, reconciledLines);
            line.setKey(key);
        });

        return true;
    }

    /**
     * Writes a key change into the middle of {@code line} at {@code insertionIndex}, adding ahead
     * of it the {@link ElementType#SINGLE_BARLINE} the chosen position needs when it does not
     * already follow a barline or repeat.
     *
     * <p><b>The barline is what keeps {@link KeyChangeElement}'s position invariant true
     * without restricting where the user may click.</b> A position that already follows a barline
     * or a repeat takes the key signature alone; every other position takes two elements, barline
     * first. Either way they enter inside <b>one</b> modification bracket, so a single undo takes
     * back the whole edit rather than leaving the barline behind — see {@code docs/mutations.md}.
     *
     * <p>Positions are measured against the line as it stands, before the bracket opens: an
     * insertion moves nothing ahead of itself, so the key the change cancels and the positions of
     * its predecessors are already settled.
     *
     * <p><b>The fit check is the caller's, and runs first</b>, exactly as it does for
     * {@link #changeLineKey}: a change that will be refused for want of room must not first be
     * written and then taken back.
     *
     * <p>An inserted key signature moves pitches from its index forward, so it owes the same
     * reconciliation a change to the line's own key owes, and raises the same single restatement
     * prompt. Its reach differs in shape at the head only: the host line is reconciled as an
     * <em>insertion</em>, so the projection carries the new element and the notes after it resolve
     * against it, while the lines that inherit are reached exactly as they are for a line-key
     * change. See {@code docs/key-signatures.md}.
     *
     * @param line the line the key change is written into
     * @param insertionIndex the index the key signature lands at — at least
     *                       {@link Line#FIRST_LEGAL_KEY_CHANGE_INDEX}, since a key signature is
     *                       never the first element on a line, and at most
     *                       {@link Line#effectiveElementCount()}
     * @param key the key taking effect from that position on
     * @return true when the change was committed; false when the notator cancelled at the
     *         restatement prompt, in which case nothing was mutated and no undo step exists
     * @throws IndexOutOfBoundsException if {@code insertionIndex} is below
     *                                   {@link Line#FIRST_LEGAL_KEY_CHANGE_INDEX} or above
     *                                   {@link Line#effectiveElementCount()}
     */
    public boolean insertKeySignature(Line line, int insertionIndex, Key key) {
        if (insertionIndex < Line.FIRST_LEGAL_KEY_CHANGE_INDEX
            || insertionIndex > line.effectiveElementCount()) {

            throw new IndexOutOfBoundsException(
                "key signature insertion index " + insertionIndex + " out of bounds ["
                    + Line.FIRST_LEGAL_KEY_CHANGE_INDEX + ", "
                    + line.effectiveElementCount() + ']');
        }

        var inserted = new ArrayList<StaffElement>();

        if (KeyChangeElement.needsBarlineBefore(line, insertionIndex)) {
            inserted.add(ElementType.SINGLE_BARLINE.newInstance());
        }

        inserted.add(new KeyChangeElement(key));

        // The head: the host line reconciled as an insertion, so the projection holds the new key
        // signature and every note after it resolves against the key it establishes. Empty prior
        // accidentals because these elements have no source context — nothing is being pasted.
        var region = new AccidentalReconciliation.InsertionRegion(
            line, insertionIndex, null, inserted, List.of(), List.of());
        var hostChanges = AccidentalReconciliation.reconcile(
            region, AccidentalReconciliation.RestatementRemoval.NONE);

        // The tail: an existing key signature already standing after the insertion point still has
        // the last word on the key this line leaves off in, so the inserted key reaches the next
        // line only when none does.
        var tail = AccidentalReconciliation.linesInheriting(
            line, line.keyAtEndOfLineUnder(insertionIndex, key));
        var reconciled = new ArrayList<AccidentalReconciliation.ReconciledLine>();

        reconciled.add(new AccidentalReconciliation.ReconciledLine(line, hostChanges));
        reconciled.addAll(AccidentalReconciliation.reconcileModification(
            tail, AccidentalReconciliation.RestatementRemoval.NONE));

        // One prompt for the whole edit, head and tail together, before any bracket opens.
        var decision = AccidentalRestatements.confirm(
            score, AccidentalRestatements.accidentalsClearedBy(reconciled));

        if (decision.isCancelled()) {
            return false;
        }

        var accidentalChanges = decision.answer() == AccidentalRestatements.Answer.YES
            ? withRemovalApplied(region, tail, decision.removal())
            : reconciled;
        var reconciledLines = reconciled.stream()
            .map(AccidentalReconciliation.ReconciledLine::line)
            .toList();

        var spacing = InsertionSpacingCalculator.calculateFragmentInsertion(
            line, inserted, insertionIndex, null, null, score.getLyricRenderMetrics());
        var insertedCount = inserted.size();

        line.getSong().withModification(Strings.get(Strings.ACTION_EDIT_OP_ADD_KEY), () -> {
            // Applied before the elements land, as every reconciliation is: the changes name live
            // notes and their pre-insertion positions.
            for (var reconciledLine : accidentalChanges) {
                AccidentalMaterializer.commit(reconciledLine.line(), reconciledLine.changes());
            }

            AccidentalRestatements.commitOtherLines(decision, reconciledLines);

            // The lyric seams on either side of the insertion, repaired as they are for any
            // other bare element: the half that reads pre-insertion indices runs first, the
            // half that reads the successor runs once every element is in.
            line.repairNeighborsBeforeInsertion(insertionIndex);

            for (var i = 0; i < insertedCount; i++) {
                var element = inserted.get(i);
                element.setXOffsetPx(ScaleContext.ssToRoundedPx(spacing.cloneXPositionsSs().get(i)));
                line.addElement(insertionIndex + i, element);
            }

            line.adjustSyllablesForSuccessorAfterInsertion(insertionIndex + insertedCount - 1);

            var tailShiftPx = ScaleContext.ssToRoundedPx(spacing.shiftForSubsequentElementsSs());

            for (var i = insertionIndex + insertedCount; i < line.effectiveElementCount(); i++) {
                var element = line.getElement(i);
                element.setXOffsetPx(element.getXOffsetPx() + tailShiftPx);
            }
        });

        return true;
    }

    /**
     * Re-runs an inserted key signature's reconciliation with the restatements the notator accepted
     * folded in, head and tail alike.
     *
     * <p>Only reached on {@link AccidentalRestatements.Answer#YES}: the removal is what changes the
     * result, so a No answer keeps the reconciliation already computed rather than repeating it.
     *
     * @param region the host line's insertion, exactly as first reconciled
     * @param tail the lines inheriting from the host, exactly as first reached
     * @param removal the accepted restatements and the staff positions they suppress
     * @return one entry per line, host first, in the same order as the original reconciliation
     */
    private static List<AccidentalReconciliation.ReconciledLine> withRemovalApplied(
        AccidentalReconciliation.InsertionRegion region,
        List<AccidentalReconciliation.ModifiedLine> tail,
        AccidentalReconciliation.RestatementRemoval removal) {

        var reconciled = new ArrayList<AccidentalReconciliation.ReconciledLine>();

        reconciled.add(new AccidentalReconciliation.ReconciledLine(
            region.line(), AccidentalReconciliation.reconcile(region, removal)));
        reconciled.addAll(AccidentalReconciliation.reconcileModification(tail, removal));

        return reconciled;
    }

    /**
     * Deletes the element range {@code begin} through {@code end} on {@code line} — widened to
     * {@link Line#effectiveRange}, so an element paired with one the caller named goes with it —
     * naming the resulting undo step {@code label}. Confirmation-free: callers are
     * responsible for any ending-invalidation confirm and for clearing the selection before
     * calling this.
     * <p>
     * When invoked as the outermost modification (delete), {@code label} names the
     * undo step. When invoked inside a caller's bracket (cut), the label is ignored —
     * the op-name is captured only at the outermost bracket — so callers that already
     * name their step pass {@code null}.
     */
    private void deleteElementRange(
        Line line, int begin, int end, @Nullable String label, AccidentalRestatements.Decision decision) {

        deleteElementRange(line, begin, end, label, decision, true);
    }

    /**
     * As {@link #deleteElementRange(Line, int, int, String, AccidentalRestatements.Decision)}, but
     * with the accidental reconciliation suppressible. Only {@link #tryInsertFragment} passes
     * false: a paste-replace is one mutation, already reconciled as a whole, so its deletion must
     * not reconcile a second time. The flag lives on an overload rather than on the five-argument
     * signature so that the callers who delete for their own sake do not have to state a value they
     * do not care about.
     */
    private void deleteElementRange(
        Line line,
        int begin,
        int end,
        @Nullable String label,
        AccidentalRestatements.Decision decision,
        boolean reconcileAccidentals) {

        // The paired grace note immediately before the range does not survive this deletion
        // either (deleteNote removes it with its host), so an explicit accidental on it is
        // removed content and changes the context arriving at the boundary — the same reason,
        // and the same compensation, that tryInsertFragment applies for spacing.
        var deleteBounds = line.effectiveRange(begin, end);
        var reconciledBegin = deleteBounds.begin();

        // Deletion is not fit-gated and must not become so, and it cannot need to be: a
        // materialization can only arise from a staff position carrying an explicit accidental in
        // the removed content, and each such position yields at most one (only the first following
        // note lacking its own accidental needs fixing). So removing k accidental-carrying notes
        // frees k noteheads plus k accidental glyphs and adds back at most k accidental glyphs —
        // the line can never get wider.
        var accidentalChanges = reconcileAccidentals
            ? AccidentalReconciliation.reconcile(
                new AccidentalReconciliation.InsertionRegion(
                    line,
                    reconciledBegin,
                    new InsertionSpacingCalculator.DeletedRange(reconciledBegin, deleteBounds.end()),
                    List.of(),
                    List.of(),
                    List.of()),
                decision.removal())
            : List.<AccidentalReconciliation.AccidentalChange>of();

        // When the element immediately before the selection is a paired grace note,
        // deleteNote must remove it along with the first selected note — a non-contiguous
        // operation that cannot be expressed as a single range. Fall back to the per-element loop.
        if (line.isHostOfPairedGraceNote(begin)) {
            line.withOptionallyNamedModification(label, () -> {
                // Recorded before the removal so undo, which replays in reverse, restores the
                // accidentals once the elements are back at the indices they were recorded at.
                commitDeletionAccidentals(line, accidentalChanges);
                AccidentalRestatements.commitOtherLines(decision, line);
                deleteSelection(begin, end, line);
            });
        } else {
            // The whole effective range is removed here, both ends included. The grace-note
            // widening is the other branch's business, so what reaches this one is the range
            // widened over a breath mark or over the barline a key signature sits behind.
            var rangeBegin = deleteBounds.begin();
            var rangeEnd = deleteBounds.end();

            // Shift elements after the selection to fill the gap, mirroring the
            // per-element xPos adjustment that deleteNote performs.
            if (rangeEnd < line.effectiveElementCount() - 1) {
                var shift = line.getElement(rangeBegin).getXOffsetPx() - line.getElement(rangeEnd + 1).getXOffsetPx();

                for (var i = rangeEnd + 1; i < line.effectiveElementCount(); i++) {
                    line.getElement(i).setXOffsetPx(line.getElement(i).getXOffsetPx() + shift);
                }
            }

            line.withOptionallyNamedModification(label, () -> {
                // Recorded before the removal, for the reason given in the other branch.
                commitDeletionAccidentals(line, accidentalChanges);
                AccidentalRestatements.commitOtherLines(decision, line);

                // Clean up the element before the range: its glissando has nothing left to
                // point at. Recorded like every other change here — stripping it raw would
                // leave undo restoring the deleted notes but not the glissando.
                if (rangeBegin > 0) {
                    var prevElement = line.getElement(rangeBegin - 1);

                    if (prevElement.hasGlissando()) {
                        line.modifyElement(rangeBegin - 1, ElementField.SLIDE, prevElement::removeSlide);
                    }
                }

                // Mirror deleteNote: adjust syllable relations and melisma extends
                // on neighbors before removing. Both helpers require the target
                // elements to still be present in the list.
                line.adjustSyllablesForNeighborChange(rangeBegin - 1, line.getElement(rangeBegin));

                // When the range was widened to include a trailing breath mark or a paired
                // barline, this loop also runs over it. Neither carries lyrics, so
                // adjustExtendsForDeletion is a harmless no-op for them.
                for (var i = rangeBegin; i <= rangeEnd; i++) {
                    line.adjustExtendsForDeletion(i);
                }

                line.removeRange(rangeBegin, rangeEnd);
            });
        }
    }

    /**
     * Records {@code accidentalChanges} inside the caller's already-open modification bracket.
     * Deletion has no fit gate, so the gate always accepts; the shared materializer is still used
     * so the "nothing is mutated on refusal" contract has exactly one implementation.
     */
    private static void commitDeletionAccidentals(
        Line line, List<AccidentalReconciliation.AccidentalChange> accidentalChanges) {

        AccidentalMaterializer.applyIfAccepted(line, accidentalChanges, List.of(), () -> true);
    }

    /**
     * Deletes elements {@code begin} through {@code end} one at a time using
     * {@link #deleteNote}, which handles the paired-grace-note case. Must be
     * called inside a modification bracket.
     */
    private void deleteSelection(int begin, int end, Line line) {
        for (var i = end; i >= begin; i--) {
            var removedCount = deleteNote(i, line);

            // When deleteNote also removes a preceding paired grace note,
            // skip the extra index so we don't process an already-removed element.
            i -= (removedCount - 1);
        }
    }

    /**
     * The element index a paste effectively starts at: {@code insertIndex} for a pure
     * insertion, or the widened begin of {@code deleteRange} for a paste-replace, since
     * {@link #deleteElementRange} takes a paired grace note before its range with it.
     */
    private static int pasteDisplacementIndex(
        Line line, int insertIndex, InsertionSpacingCalculator.@Nullable DeletedRange deleteRange) {

        return deleteRange == null ? insertIndex : line.effectiveBegin(deleteRange.begin());
    }

    /** Outcome of {@link #tryInsertFragment}. */
    public enum FragmentInsertOutcome {
        INSERTED,
        LINE_FULL,
        EMPTY,
        CANCELLED
    }

    /**
     * Inserts the clipboard fragment into {@code line} at {@code insertIndex},
     * first deleting {@code deleteRange} when present (paste-replace). The fit
     * check runs against the pre-delete line, so on {@code LINE_FULL} nothing has
     * been mutated: the "line full" error is shown, a caller-opened bracket stays
     * empty, and no notification is posted. Callers decide recovery.
     *
     * <p>{@code CANCELLED} means the user declined the confirm shown when the pasted
     * content would invalidate a first-second ending. Like {@code LINE_FULL} it leaves
     * the line untouched.
     *
     * <p>Must be called inside a modification bracket — both paste-replace and
     * paste-mode placement supply their own, so delete + insert form one undo step.
     *
     * @param line        The destination line
     * @param insertIndex The index where the fragment's first element will land
     * @param deleteRange The effective range to delete first, or null for pure insertion
     * @return The outcome: {@code INSERTED}, {@code LINE_FULL}, or {@code EMPTY}
     */
    public FragmentInsertOutcome tryInsertFragment(
        Line line, int insertIndex, InsertionSpacingCalculator.@Nullable DeletedRange deleteRange) {

        var fragment = clipboardManager.getFragment();

        if (fragment == null || fragment.elements().isEmpty()) {
            return FragmentInsertOutcome.EMPTY;
        }

        // deleteElementRange also removes a paired grace note immediately before the
        // range (its host cannot outlive it), so the spacing calculation must count
        // that element as deleted too. Otherwise the clones are positioned against a
        // predecessor that does not survive, leaving a gap where the grace note was.
        var displacementIndex = pasteDisplacementIndex(line, insertIndex, deleteRange);
        InsertionSpacingCalculator.@Nullable DeletedRange widenedDeleteRange = null;

        if (deleteRange != null && displacementIndex != deleteRange.begin()) {
            widenedDeleteRange =
                new InsertionSpacingCalculator.DeletedRange(displacementIndex, deleteRange.end());
        }

        var spacingDeleteRange = (widenedDeleteRange != null) ? widenedDeleteRange : deleteRange;
        var spacingInsertIndex = (widenedDeleteRange != null) ? widenedDeleteRange.begin() : insertIndex;

        // Fresh clones every paste — the stored fragment is never itself inserted.
        // Instantiated before any mutation because the confirms and the reconciliation
        // below decide which of *these* spans survive, and they must read pre-mutation
        // indices off the line. The clones carry no line back-reference until addElement
        // below, so building them early touches nothing — which is also why the fit gate
        // can measure these clones rather than the stored fragment's elements.
        var instantiated = fragment.instantiate();

        // The accidentals this paste must make explicit so no pitch the user did not touch
        // changes. Reconciled against the pre-mutation line and applied *before* the fit
        // gate below: ElementColumnBuilder derives element extents including accidental
        // width and LayoutEngine treats accidental widths as a layout input, so the
        // projected column chain must already see the materialized accidentals or the gate
        // measures the wrong widths.
        //
        // The spacing pair is passed rather than the caller's raw insertIndex/deleteRange
        // for the same single reason spacing uses it: the paired grace note immediately
        // before the range does not survive deleteElementRange, so an explicit accidental
        // on it is removed content and changes the context arriving at the boundary.
        // A paste-replace removes the explicit accidentals of the range it overwrites, so it asks
        // the same question a plain deletion does — before the fit gate, and before anything is
        // mutated, so Cancel reuses the LINE_FULL contract exactly. A pure insertion removes
        // nothing and so is never asked.
        var decision = (spacingDeleteRange == null)
            ? AccidentalRestatements.Decision.PROCEED
            : AccidentalRestatements.confirm(
                score,
                line,
                AccidentalRestatements.inDeletedRange(
                    line, spacingDeleteRange.begin(), spacingDeleteRange.end()));

        if (decision.isCancelled()) {
            return FragmentInsertOutcome.CANCELLED;
        }

        var accidentalChanges = AccidentalReconciliation.reconcile(
            new AccidentalReconciliation.InsertionRegion(
                line, spacingInsertIndex, spacingDeleteRange, instantiated.elements(),
                instantiated.priorAccidentals(), instantiated.spans()),
            decision.removal());

        // Both refusals — LINE_FULL and CANCELLED — leave the line exactly as it was (C1), so
        // both live inside the materializer's gate: it applies the accidentals with the plain
        // setter, runs this gate with them in place so the projection measures the right widths,
        // and then either puts them back untouched or re-records them through modifyElement.
        //
        // The gate's two products are needed after it returns, hence the holders: a lambda cannot
        // assign to a local.
        //
        // The refusal holder starts at CANCELLED rather than null so that this method cannot
        // return null from a non-nullable signature if a refusal path is ever added below without
        // naming its outcome. CANCELLED is the safe default: it is the outcome that reports
        // "nothing happened, and the user has already been told why or chose it".
        var spacingResult = new InsertionSpacingCalculator.FragmentInsertionResult[1];
        var refusal = new FragmentInsertOutcome[]{FragmentInsertOutcome.CANCELLED};

        var committed = AccidentalMaterializer.applyIfAccepted(
            line, accidentalChanges, instantiated.elements(), () -> {
                var fit = InsertionSpacingCalculator.calculateFragmentInsertion(
                    line, instantiated.elements(), spacingInsertIndex, spacingDeleteRange, null,
                    score.getLyricRenderMetrics());

                if (!fit.fitsWithinLine(line.getSong().getLineWidthSs())) {
                    OptionDialogs.showErrorMessage(
                        null,
                        Strings.ALERT_TITLE_INSERT_ERROR,
                        Strings.ERROR_LINE_FULL_PASTE
                    );
                    refusal[0] = FragmentInsertOutcome.LINE_FULL;
                    return false;
                }

                // A pasted barline or repeat landing inside an ending discards it, exactly as
                // inserting one by hand does — confirm on the same terms, before anything is
                // mutated. Skipped when the paste-replace's own deletion already invalidates an
                // ending: handlePaste has confirmed that, and the ending is going either way.
                var deletionAlreadyConfirmed = deleteRange != null
                    && line.hasEndingInvalidatedByDeletion(deleteRange.begin(), deleteRange.end());

                if (!deletionAlreadyConfirmed) {
                    var insertedTypes = fragment.elements().stream().map(StaffElement::getType).toList();

                    if (line.hasEndingInvalidatedByInsertion(insertIndex, insertedTypes)
                            && !EndingConfirms.confirmInvalidation(score)) {
                        refusal[0] = FragmentInsertOutcome.CANCELLED;
                        return false;
                    }
                }

                spacingResult[0] = fit;
                return true;
            });

        if (!committed) {
            return refusal[0];
        }

        // Accepted restatements on later lines join the caller's bracket, so the paste and every
        // removal it authorized are one undo step.
        AccidentalRestatements.commitOtherLines(decision, line);

        // The accidentals are now recorded mutations, deliberately ahead of the deletion below:
        // UndoController replays a step's mutations in reverse, so undo reaches them last, after
        // the deletion has been undone and the surviving notes are back at the pre-delete indices
        // AccidentalMaterializer recorded them at.
        var result = spacingResult[0];

        var reconciliation = PasteSpanReconciliation.reconcile(
            line, insertIndex, deleteRange, instantiated.elements(), instantiated.spans());

        // Drop the destination spans this paste lands inside before anything moves,
        // while their anchor/end indices still resolve against the pre-paste line.
        for (var span : reconciliation.targetSpansToRemove()) {
            line.removeInvalidatedSpan(span);
        }

        // Capture the successor and its target X before any mutation: the trailing
        // shift was measured against pre-delete positions, and deleteElementRange's
        // gap-fill moves the tail before the clones go in.
        var successorIndex = deleteRange == null ? insertIndex : deleteRange.end() + 1;
        var successor = successorIndex < line.effectiveElementCount()
            ? line.getElement(successorIndex)
            : null;
        var successorTargetXPx = successor != null
            ? successor.getXOffsetPx() + ScaleContext.ssToRoundedPx(result.shiftForSubsequentElementsSs())
            : 0;
        var insertAt = insertIndex;

        if (deleteRange != null) {
            // This paste has already been reconciled as a whole — the deletion and the insertion
            // are one mutation — so the range delete must not reconcile again.
            deleteElementRange(
                line, deleteRange.begin(), deleteRange.end(), null,
                AccidentalRestatements.Decision.PROCEED, false);

            // The deletion may have removed elements before the range too (a paired
            // grace note cascade), so re-derive the insertion index from what survived.
            insertAt = successor != null ? line.getElementIndex(successor) : line.effectiveElementCount();
        }

        var clones = instantiated.elements();
        var cloneCount = clones.size();

        // Repair the lyric seams around the insertion point, mirroring the
        // single-note insert path in PreviewElementManager. The successor half runs after
        // the clones are in, against the last of them rather than the first.
        line.repairNeighborsBeforeInsertion(insertAt);

        // Hard ordering constraint: every clone must be inserted before the first
        // addPastedSpan. Adding a span re-parents only the span, not its
        // anchor/end, and getAnchorElementIndex() resolves through the anchor's
        // own getLine() — a span added while its anchors still carry the source
        // line's back-reference makes addElement's isInvalidatedByInsertion sweep
        // evaluate it against the wrong line, yielding a wrong index or -1. The
        // hairpin merge in addPastedSpan reads those same indices, so it
        // would mis-measure what to absorb for exactly the same reason.
        //
        // line.addElement additionally drops the spans the inserted element types
        // invalidate, each by its own rule: the ending's barline/repeat-aware one,
        // which is more precise than the straddle test PasteSpanReconciliation
        // applies to the other span kinds, so endings are left to it; and the tie's
        // separator rule. Its tuplet removal is now redundant but harmless — the
        // reconciliation above already removed a straddled tuplet, so findTupletAt
        // finds nothing. Its tie removal is not redundant: the reconciliation judges
        // only ties with both endpoints in this line, leaving a tie that straddles a
        // line boundary to this sweep, which resolves it against the receiving line.
        for (var k = 0; k < cloneCount; k++) {
            var clone = clones.get(k);
            clone.setXOffsetPx(ScaleContext.ssToRoundedPx(result.cloneXPositionsSs().get(k)));
            line.addElement(insertAt + k, clone);
        }

        line.adjustSyllablesForSuccessorAfterInsertion(insertAt + cloneCount - 1);

        // Apply the single trailing shift to every surviving element after the
        // fragment, mirroring the single-note insert path. The delta is re-derived
        // from the successor's captured target X so it stays correct after the
        // deletion gap-fill.
        if (successor != null) {
            var tailShiftPx = successorTargetXPx - successor.getXOffsetPx();

            for (var i = insertAt + cloneCount; i < line.effectiveElementCount(); i++) {
                var element = line.getElement(i);
                element.setXOffsetPx(element.getXOffsetPx() + tailShiftPx);
            }
        }

        // A pasted tuplet the destination's beat context rejects is dropped here
        // (#604): after the clones are in, so every index resolves against the final
        // line, and before any span is added, so the bracket is never created and
        // needs no undo of its own.
        reconciliation = reconciliation.dropTupletsRejectedByTarget(line);

        // Losing a bracket is the one part of a paste the user did not ask for, so it
        // warns on exactly the same terms as a beat edit that costs them one — Song
        // collapses both into a single notification when this paste's bracket closes.
        if (!reconciliation.tupletsRejectedByTarget().isEmpty()) {
            line.getSong().noteTupletsWereRemoved(TupletsWereRemovedNotification.Cause.PASTE);
        }

        // A pasted hairpin flush against a same-type hairpin already on the line is
        // merged into it by addPastedSpan, the same rule that applies when
        // the user draws one there; every other kind is added verbatim.
        for (var span : reconciliation.fragmentSpans()) {
            line.addPastedSpan(span);
        }

        return FragmentInsertOutcome.INSERTED;
    }

    private void handlePaste() {
        var fragment = clipboardManager.getFragment();

        // Empty clipboard — nothing to paste.
        if (fragment == null || fragment.elements().isEmpty()) {
            return;
        }

        var range = selectionCoordinator.getRange();

        if (range == null) {
            // No selection: enter paste mode to place the fragment by clicking an
            // insertion point. The score already has focus (handlePasteboardOp
            // requires it) and the fragment is already known non-empty above.
            EditModeManager.getPasteModeManager().enter();
            return;
        }

        var line = range.line();

        // The whole range the replace really removes, widened at both ends exactly as a plain
        // deletion is. Using the raw begin here understated it: the deletion this authorizes also
        // takes the barline in front of a key signature at begin, so the spacing was measured for
        // a smaller range than the one that goes.
        var effective = line.effectiveRange(range.begin(), range.end());
        var begin = effective.begin();
        var deleteRange = new InsertionSpacingCalculator.DeletedRange(begin, effective.end());

        // A paste-replace deletes before it inserts, so it can discard an ending the
        // same way Delete and Cut can — confirm on the same terms. Declining leaves
        // the score, the selection, and the clipboard untouched.
        if (!confirmEndingInvalidatedByDeletion(line, deleteRange.begin(), deleteRange.end())) {
            return;
        }

        // One bracket for the whole replace — delete + insert is a single undo
        // step. On LINE_FULL tryInsertFragment mutates nothing, so the bracket
        // closes empty, posts no notification, and the selection stays intact.
        var outcome = score.getSong().withModificationResult(() -> {
            var result = tryInsertFragment(line, begin, deleteRange);

            if (result == FragmentInsertOutcome.INSERTED) {
                // Clear the selection before the bracket closes so action handlers
                // reacting to SongDidChangeNotification don't query selection
                // indices that no longer exist on the reshaped line.
                selectionCoordinator.clearSelection();
            }

            return result;
        });

        if (outcome == FragmentInsertOutcome.INSERTED) {
            // Discard saved action states — the song has changed, so restoring
            // pre-selection states would be stale. Individual action handlers will
            // re-evaluate their enabled state from the current context.
            selectionCoordinator.getActionReflector().clearSavedActionStates();
            score.deselect();
        }
    }

    @Handler
    public void handleDeselect(DeselectCommand message) {
        if (score.isFocusOwner()) {
            score.deselect();
        }
    }

    /**
     * Selects every element on the active line. When the line itself is selected, this
     * swaps that whole-line selection for a selection of its elements — an empty line has
     * nothing to swap to, so its line selection stands.
     */
    @Handler
    public void handleSelectAllElements(SelectAllElementsCommand message) {
        if (selectionCoordinator.getActiveLine() != null) {
            selectionCoordinator.selectAll();
            score.selectionChanged();
            score.repaint();
        }
    }

    /**
     * Deletes the element at {@code xIndex} and, if the preceding element is a
     * paired grace note, removes that as well. After the primary removal, if the
     * surviving element at {@code firstDeletedIndex} is a breath mark, it is
     * cascade-deleted via a recursive call so all gap-fill, glissando, and
     * syllable/extend logic is reused.
     *
     * <p>Reconciles no accidentals: the caller owns that. This is the per-element worker
     * {@link #deleteSelection} loops over for a range {@link #deleteElementRange} has already
     * reconciled as a whole, and its own breath-mark cascade below removes an unpitched element
     * that can never carry an accidental.
     *
     * @return the number of elements removed (1 or 2), not counting any
     *         cascade-deleted trailing breath mark
     */
    static int deleteNote(int xIndex, Line line) {
        // If the preceding note is a paired grace note, it becomes orphaned when
        // this note is deleted and must be removed along with it.
        var hasPrecedingPairedGraceNote = line.isHostOfPairedGraceNote(xIndex);

        // Determine the left edge of the deletion — if a paired grace note precedes
        // the deleted note, it is also being removed, so the gap starts there.
        var firstDeletedIndex = hasPrecedingPairedGraceNote ? xIndex - 1 : xIndex;

        if (xIndex < (line.effectiveElementCount() - 1)) {
            var shift =
                line.getElement(firstDeletedIndex).getXOffsetPx() -
                    line.getElement(xIndex + 1).getXOffsetPx();

            for (var i = xIndex + 1; i < line.effectiveElementCount(); i++) {
                line.getElement(i).setXOffsetPx(line.getElement(i).getXOffsetPx() + shift);
            }
        }

        // If the previous note is a paired grace note, it disappears entirely —
        // no need to strip its glissando separately. Otherwise remove any standalone
        // incoming glissando from the previous note.
        if (!hasPrecedingPairedGraceNote && xIndex > 0) {
            var prevElement = line.getElement(xIndex - 1);

            if (prevElement.hasGlissando()) {
                line.modifyElement(xIndex - 1, ElementField.SLIDE, prevElement::removeSlide);
            }
        }

        // Adjust syllable relations and melisma extends before removing —
        // both methods require the element at xIndex to still be in the list.
        // This must run before the hand-back below: it decides whether to break the
        // predecessor's word by reading the deleted element's own lyric, which the
        // transfer would have already moved away.
        line.adjustSyllablesForNeighborChange(firstDeletedIndex - 1, line.getElement(xIndex));

        // Deleting a paired grace note on its own hands its syllable back to the host,
        // which becomes an ordinary note again and is eligible to carry a lyric. Runs
        // before adjustExtendsForDeletion so it sees the final lyric state: the transfer
        // takes the melisma START off the grace and drops the host's STOP carrier, so
        // there is no longer a chain to unwind.
        if (line.isPairedGraceNote(xIndex)) {
            line.transferLyrics(xIndex, xIndex + 1);
        }

        line.adjustExtendsForDeletion(xIndex);

        // Remove the host note first (higher index), then the orphaned grace note.
        // Removing the higher index first keeps xIndex - 1 valid.
        line.removeElement(xIndex);

        int removed;

        if (hasPrecedingPairedGraceNote) {
            line.removeElement(xIndex - 1);
            removed = 2;
        } else {
            removed = 1;
        }

        // Cascade-delete a breath mark that immediately follows the deleted element.
        // After removal the successor lands at firstDeletedIndex. Recurse through
        // deleteNote (not a bare removeElement) so gap-fill, glissando strip, and
        // syllable/extend adjustments are reused. The cascade is excluded from
        // `removed` because deleteSelection's caller loop counts down, so the breath
        // mark (a higher index, visited on an earlier iteration) is already accounted
        // for and must not shift the loop's index a second time.
        if (firstDeletedIndex < line.effectiveElementCount() &&
                line.getElement(firstDeletedIndex).getType().isBreathMark()) {
            deleteNote(firstDeletedIndex, line);
        }

        return removed;
    }
}
