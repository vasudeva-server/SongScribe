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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.swing.JComponent;

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
import songscribe.dom.EndingValidationResult;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.Span;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
import songscribe.dom.Tempo;
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
import songscribe.message.mutation.MetadataField;
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
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.edit.AccidentalRestatements;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.InsertionPointMode;
import songscribe.ui.edit.KeyChangeReconciliation;
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

        if (all || ScoreActions.PLAYBACK_SYNC_PREFS_KEYS.contains(key)) {
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

        // Three mutually exclusive cases for the staff, checked widest first: a line insert or
        // delete rebuilds the LinePanel list; a change that reaches every line invalidates every
        // LinePanel's layout; and a line-scoped change invalidates only the LinePanels the change
        // affects (one, or two for a span straddling a line boundary).
        //
        // The rebuild is tested ahead of the every-line case because it subsumes it — it
        // recreates every LinePanel, layout and all — while the reverse is not true, and a batch
        // that changes fonts and inserts a line carries both.
        if (message.hasMutationOf(LineInsertion.class) || message.hasMutationOf(LineDeletion.class)) {
            // StaffPanel.rebuildLayout() is the only add/remove primitive for LinePanels;
            // per-panel invalidation cannot add a panel for an inserted line or remove one
            // for a deleted line. Coarse (recreates every LinePanel) but correct for both
            // forward edits and undo/redo replay, which funnel through this same handler.
            mainPanel.getStaffPanel().rebuildLayout();
            // rebuildLayout() creates fresh LineComponents with only song and line set;
            // re-wire the scoreView into each (as document load does) so their next
            // performLayout() has a live scoreView and does not produce a null layout.
            score.setupLineComponentState();
        } else if (invalidatesEveryLine(message)) {
            for (var linePanel : mainPanel.getStaffPanel().getLinePanels()) {
                linePanel.getLineComponent().invalidateLayout();
            }
        } else if (hasLineLayoutMutation(message)) {
            var staffPanel = mainPanel.getStaffPanel();
            var targetLine = message.getLine();
            var spans = mutatedSpans(message);
            var keyMoveReach = keyMoveReach(message);

            for (var linePanel : staffPanel.getLinePanels()) {
                var line = linePanel.getLine();

                if (targetLine == null
                    || line == targetLine
                    || spanReaches(spans, line)
                    || keyMoveReach.contains(line)) {
                    linePanel.getLineComponent().invalidateLayout();

                    // With neither a span nor a key move in the notification the named line is
                    // the only one that can match, so the panels after it have nothing left to
                    // be checked against.
                    if (targetLine != null && spans.isEmpty() && keyMoveReach.isEmpty()) {
                        break;
                    }
                }
            }
        }

        // Additive, not a fourth case in the chain: a Song Settings commit carries metadata
        // alongside fonts and tempo and can arrive in the same batch as a line-scoped mutation
        // elsewhere in the song, where the chain above would invalidate that line and leave
        // line 0 — which lays out the attribution and the tempo mark — holding its stale layout.
        invalidateMetadataViews(message, mainPanel);

        // Nothing re-fits the page here. Every branch above ends in a revalidate on the component
        // whose size changed, and ScoreView.getPreferredSize derives the page height from its
        // content, so the page follows on its own. A test of which mutations "move the page"
        // would have to name every mutation that can change a line's height — which is all of
        // them, as a key change that lifts an ending above a new accidental showed.

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
     * Every line whose drawn key content this notification's mutations moved, or an empty set
     * when none of them moves a key.
     * <p>
     * A key move is the one edit whose layout effect is not the line the mutation names. It runs
     * forward through every line that inherits the moved key — each of whose headers is solved
     * from the key it runs in — and back one line, whose trailing space is solved from the
     * cautionary it leads into. Without this the line before the change redraws its cautionary
     * from the live document while keeping the spacing it was solved with, and an inheriting line
     * goes on drawing the key it was in before the edit.
     * <p>
     * {@link Song#keyMoveReach} is the one answer; this only unions it over the batch. A set
     * rather than a list because two mutations in one batch — a key change and the accidental
     * restatements it forces — routinely reach the same lines, and because the panel loop below
     * asks membership rather than order.
     */
    private static Set<Line> keyMoveReach(SongDidChangeNotification message) {
        Set<Line> reach = null;

        for (var mutation : message.getMutations()) {
            var lines = message.getSong().keyMoveReach(mutation);

            if (!lines.isEmpty()) {
                if (reach == null) {
                    reach = Collections.newSetFromMap(new IdentityHashMap<>());
                }

                reach.addAll(lines);
            }
        }

        return reach == null ? Set.of() : reach;
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
     * Returns whether the notification carries any mutation that changes how <em>every</em>
     * line is laid out, and so costs the whole song a re-solve.
     * <p>
     * A font or layout-property change reaches every line by definition. A metadata change
     * reaches every line only when it redefined the song's beat, which regroups beams and
     * revalidates tuplets throughout. The rest of the metadata — the title, the number, the
     * subtitle, the attribution, the footnotes, and a tempo edit that only changed how the
     * marking reads — is drawn outside the staves or on line 0 alone, and is dealt with by
     * {@link #invalidateMetadataViews} at the cost of the one line it belongs to.
     */
    private static boolean invalidatesEveryLine(SongDidChangeNotification message) {
        for (var mutation : message.getMutations()) {
            if (mutation instanceof FontChange || mutation instanceof LayoutChange) {
                return true;
            }

            if (mutation instanceof MetadataChange metadataChange && redefinesBeat(metadataChange)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether {@code change} is a tempo change that redefined the song's beat.
     * <p>
     * {@link MetadataField#TEMPO} is one coarse field covering both the tempo type — the beat —
     * and how the tempo is displayed, so the field alone cannot answer this and the recorded
     * values have to. The casts hold because {@link MetadataChange}'s constructor validates
     * every value against {@link MetadataField#getExpectedType()}.
     */
    private static boolean redefinesBeat(MetadataChange change) {
        return change.field() == MetadataField.TEMPO
            && !Tempo.haveSameBeat((Tempo) change.oldValue(), (Tempo) change.newValue());
    }

    /**
     * Invalidates what the metadata in {@code message} is actually drawn by: the header and
     * footer components that carry it, and line 0, the only line that lays out the attribution
     * block and the tempo mark (see {@code LineComponent.performLayout}).
     * <p>
     * The header and footer components measure their text on every sizing pass, so they have no
     * cache to clear — they only have to be marked invalid, so that the {@link
     * ScoreView#relayoutPage} the caller ends on measures them afresh instead of being handed the
     * size they had before the edit.
     * <p>
     * Runs whatever the staff branch above decided, since a batch can carry a metadata change
     * beside a line-scoped one. Invalidating line 0 a second time when that branch already did
     * costs nothing.
     */
    private static void invalidateMetadataViews(SongDidChangeNotification message, MainPanel mainPanel) {
        var firstLineAffected = false;

        for (var mutation : message.getMutations()) {
            if (!(mutation instanceof MetadataChange metadataChange)) {
                continue;
            }

            switch (metadataChange.field()) {
                case ATTRIBUTION -> {
                    firstLineAffected = true;
                    invalidate(mainPanel.getTitleComponent());
                    invalidate(mainPanel.getSubtitleComponent());
                }

                // Reached only for a tempo edit that left the beat alone; one that redefined it
                // was answered by invalidatesEveryLine. Either way the marking is line 0's, so
                // invalidating that line is all the marking itself needs.
                case TEMPO -> firstLineAffected = true;

                case FOOTNOTES -> invalidate(mainPanel.getFootnotesComponent());
            }
        }

        if (firstLineAffected) {
            var linePanels = mainPanel.getStaffPanel().getLinePanels();

            // Empty while a document is being torn down or before its lines exist.
            if (!linePanels.isEmpty()) {
                linePanels.getFirst().getLineComponent().invalidateLayout();
            }
        }
    }

    /** Drops {@code component}'s cached size so the next layout pass measures it afresh. */
    private static void invalidate(JComponent component) {
        component.invalidate();
        component.repaint();
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

        // Widened once here and passed to both the question and the deletion, so the range the
        // notator was asked about is the range that goes.
        var bounds = line.effectiveRange(begin, end);

        // Asked before the bracket opens, and before the clipboard is written: cancelling must
        // leave both the clipboard and the score untouched, exactly as declining the ending
        // confirm above does.
        var confirmed = reconcileAndConfirmDeletion(line, bounds);

        if (confirmed.isCancelled()) {
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
        score.getSong().withModification(() -> deleteElementRange(line, bounds, null, confirmed));

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
     *   <li>Otherwise, if {@code canDeleteLine()} allows it, the line itself is removed. Taking a
     *       line out moves the key every following line inherits, so this branch owes the same
     *       cross-line reconciliation an inserted key change owes, and removes with it any
     *       mid-line key change the move strands.
     * </ul>
     *
     * <p>The two branches that reconcile — the element range and the line — ask their one question
     * before opening a bracket, and return without touching the selection when the answer is
     * Cancel, so a cancelled delete leaves nothing mutated and no undo step.
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

            // Widened once here and passed to both the question and the deletion, so the range the
            // notator was asked about is the range that goes.
            var bounds = line.effectiveRange(begin, end);
            var confirmed = reconcileAndConfirmDeletion(line, bounds);

            if (confirmed.isCancelled()) {
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
                () -> deleteElementRange(line, bounds, null, confirmed));
        } else if (selectedTarget != null && targetLine != null) {
            deleteSelectedTarget(targetLine, selectedTarget);
        } else if (score.canDeleteLine()) {
            var lineIndex = selectionCoordinator.getSelectedLine();
            var deletedLine = song.getLine(lineIndex);
            var reach = AccidentalReconciliation.lineDeletionReach(deletedLine);

            var confirmed = KeyChangeReconciliation.confirm(
                score,
                List.of(new AccidentalRestatements.EditedLine(
                    deletedLine,
                    AccidentalRestatements.inDeletedRange(
                        deletedLine, 0, deletedLine.effectiveElementCount() - 1))),
                reach);

            if (confirmed.isCancelled()) {
                return;
            }

            song.withModification(OpNames.deleteLineLabel(), () -> {
                confirmed.apply();
                song.removeLine(lineIndex);
            });
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
     * never disagree. The two remaining selectable kinds are deliberately a no-op rather than
     * falling through to the whole-line delete.
     * <p>
     * {@link #handleDelete} carries the diagram of how a keystroke reaches each arm below.
     */
    private void deleteSelectedTarget(Line line, HitTarget.Selectable target) {
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
            // The staff line and a lyric are both selectable, and handleDelete owns each: it
            // deletes the whole line for the one and the syllable for the other before reaching
            // here. Every kind that is not selectable at all — a note head, which is selected as
            // an index range, a grace-note glissando and the attribution — is excluded by the
            // parameter type instead of by an arm.
            case HitTarget.StaffLine _, HitTarget.Lyric _ -> {
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
     * Reconciles a deletion of {@code [begin, end]} over every line it reaches and asks whether it
     * should also take away the later notes that restate the accidentals it removes. Callers must
     * run this <b>before</b> opening a modification bracket, and must abandon the deletion entirely
     * when the answer is Cancel.
     *
     * <p>The range is widened exactly as {@link Line#deleteRange} widens it — a paired grace
     * note before the range does not survive its host, and a trailing breath mark, or the barline a
     * key signature sits behind, goes with the range — so the accidentals offered are the ones the
     * deletion really removes.
     *
     * <p><b>The reach is more than this line.</b> A deletion that takes a mid-line key signature
     * with it moves the key every following line inherits, so it owes the same cross-line
     * reconciliation an inserted key change owes, ending where the inheritance chain does. A
     * deletion that removes no key signature leaves the line's end key where it was, so
     * {@link AccidentalReconciliation#linesInheriting} reaches nothing and the reach is this line
     * alone — which is why it is computed unconditionally rather than behind a test for what the
     * range holds. See {@code docs/key-signatures.md}.
     *
     * <p>One dialog covers the whole of it: the elements going away and the accidentals the
     * reconciliation clears on every reached line are asked about together.
     *
     * @param line   the line the deletion happens on
     * @param bounds the range that really goes, already widened by {@link Line#effectiveRange}
     * @return the notator's answer, the changes to record, and the reach they answer — including
     *     the key changes the deletion strands, which the caller must remove with it
     */
    private KeyChangeReconciliation.Confirmed reconcileAndConfirmDeletion(
        Line line, StaffElementRun.EffectiveRange bounds) {

        // A deletion that takes a key change with it leaves the key that was running before the
        // range running past it, which can leave a later key change on this same line restating
        // it. keyAt's bound is inclusive, so it is asked one index below the range; index 0 can
        // hold no key change, so asking about it is the same question.
        var keyAfterRemoval = line.keyAt(Math.max(bounds.begin() - 1, 0));
        var strandedAfter = line.redundantKeyChangeRanges(bounds.end() + 1, keyAfterRemoval);

        // This line reconciled as a removal, so the projection holds neither the range going away
        // nor the key changes its going away strands further along; then every line that inherits.
        //
        // The paired grace note immediately before the range does not survive this deletion
        // either, so an explicit accidental on it is removed content and changes the context
        // arriving at the boundary — the same reason, and the same compensation, that
        // tryInsertFragment applies for spacing. The widening in {@code bounds} already covers it.
        var reach = new ArrayList<AccidentalReconciliation.ReachedLine>();

        reach.add(AccidentalReconciliation.ReachedLine.receiving(
            line,
            new AccidentalReconciliation.Insertion(
                bounds.begin(),
                new InsertionSpacingCalculator.DeletedRange(bounds.begin(), bounds.end()),
                AccidentalReconciliation.ArrivingElements.NONE),
            strandedAfter));

        reach.addAll(AccidentalReconciliation.linesInheriting(
            line, line.keyAtEndOfLineAfterRemoving(bounds.begin(), bounds.end())));

        return KeyChangeReconciliation.confirm(
            score,
            List.of(new AccidentalRestatements.EditedLine(
                line, AccidentalRestatements.inDeletedRange(line, bounds.begin(), bounds.end()))),
            reach);
    }

    /**
     * Reconciles the accidentals a deletion of {@code begin} through {@code end} on {@code line}
     * owes, then deletes that range through {@link Line#deleteRange}, naming the resulting undo
     * step {@code label}. Confirmation-free: callers are responsible for any ending-invalidation
     * confirm and for clearing the selection before calling this.
     * <p>
     * When invoked as the outermost modification (delete), {@code label} names the
     * undo step. When invoked inside a caller's bracket (cut), the label is ignored —
     * the op-name is captured only at the outermost bracket — so callers that already
     * name their step pass {@code null}.
     * <p>
     * The accidental reconciliation is the caller's, in {@code confirmed}: it has to be run and
     * asked about before any bracket opens, and a paste-replace — one mutation, reconciled as a
     * whole — passes {@link KeyChangeReconciliation.Confirmed#PROCEED} so its deletion reconciles
     * nothing a second time.
     */
    private void deleteElementRange(
        Line line,
        StaffElementRun.EffectiveRange range,
        @Nullable String label,
        KeyChangeReconciliation.Confirmed confirmed) {

        // Deletion is not fit-gated and must not become so, and it cannot need to be: a
        // materialization can only arise from a staff position carrying an explicit accidental in
        // the removed content, and each such position yields at most one (only the first following
        // note lacking its own accidental needs fixing). So removing k accidental-carrying notes
        // frees k noteheads plus k accidental glyphs and adds back at most k accidental glyphs —
        // the line can never get wider.
        var accidentalChanges = confirmed.changesFor(line);

        // This line's changes are recorded here, by commitDeletionAccidentals, because its note
        // indices have to be captured against the pre-removal line.
        var reconciliation = confirmed.withChangesRecordedByCaller(line);

        line.withOptionallyNamedModification(label, () -> {
            // Recorded before the removal so undo, which replays in reverse, restores the
            // accidentals once the elements are back at the indices they were recorded at. The
            // sweep follows in the same bracket and still before the removal itself: every range
            // it takes stands after this one, so taking them first leaves this range's own indices
            // where they were.
            commitDeletionAccidentals(line, accidentalChanges);
            reconciliation.apply();

            line.deleteRange(range);
        });
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
     * The element index a paste effectively starts at: {@code insertIndex} for a pure
     * insertion, or the widened begin of {@code deleteRange} for a paste-replace, since
     * {@link Line#deleteRange} takes a paired grace note before its range with it.
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
     * <p>{@code CANCELLED} means the user declined a confirm — the one shown when the pasted
     * content would invalidate a first-second ending, or the one asked about the accidental
     * restatements this paste strands. Like {@code LINE_FULL} it leaves the line untouched.
     *
     * <p>Must be called inside a modification bracket — both paste-replace and
     * paste-mode placement supply their own, so delete + insert form one undo step.
     *
     * <p><b>A fragment carrying a key change re-keys more than this line.</b> The key it
     * leaves the destination line in reaches every line inheriting past it, so the paste owes the
     * same reconciliation and raises the same single restatement prompt a key change written
     * by hand does, and removes every key change it leaves restating the key already in
     * effect — one of the fragment's own included — together with the barline behind it. Those
     * lines are <b>not</b> fit-gated: this gate measures the destination line only, and a line
     * left overflowing by a key that moved renders overflowing and flagged, which is what the
     * program does everywhere outside the edits {@code KeyEditFitCalculator} covers.
     *
     * @param line        The destination line
     * @param insertIndex The index where the fragment's first element will land
     * @param deleteRange The effective range to delete first, or null for pure insertion
     * @return {@code EMPTY} when the clipboard holds nothing; {@code LINE_FULL} when the fit gate
     *     refused the paste; {@code CANCELLED} when the notator declined either confirm; and
     *     {@code INSERTED} when the paste happened. {@code INSERTED} also covers the case where
     *     the fragment reduced to nothing because every key change it carried restates the key
     *     already running here — the gesture is spent and the caller should complete it, even
     *     though no element was placed. Only {@code INSERTED} means the line was mutated
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

        // The first index this paste leaves standing after itself, and so the first one every
        // projection below re-reads.
        var successorIndex =
            (spacingDeleteRange == null) ? spacingInsertIndex : (spacingDeleteRange.end() + 1);

        // The key the fragment's first element lands in. keyAt's bound is inclusive, so it is
        // asked one index lower — index 0 can hold no key change, so asking about it is the
        // same question.
        var keyAtInsertion = line.keyAt(Math.max(spacingInsertIndex - 1, 0));

        // Fresh clones every paste — the stored fragment is never itself inserted.
        // Instantiated before any mutation because the confirms and the reconciliation
        // below decide which of *these* spans survive, and they must read pre-mutation
        // indices off the line. The clones carry no line back-reference until addElement
        // below, so building them early touches nothing — which is also why the fit gate
        // can measure these clones rather than the stored fragment's elements.
        //
        // A fragment carries the key it was copied under, so one of its own key changes can
        // arrive restating the key already running where it lands. It is dropped here rather
        // than deleted after the insertion, so the reconciliation, the fit measurement and the
        // span reconciliation below all see the run that actually lands.
        var instantiated = fragment.instantiate().withoutRedundantKeyChanges(keyAtInsertion);

        // A key the fragment brings in reaches the rest of this line and every line inheriting
        // past it, exactly as a key change written by hand does — this is the reach that edit
        // owes, and the key changes it strands along the way.
        var keyAfterFragment = instantiated.keyAtEndUnder(keyAtInsertion);
        var strandedAfter = line.redundantKeyChangeRanges(successorIndex, keyAfterFragment);
        var tail = AccidentalReconciliation.linesInheriting(
            line, line.keyAtEndOfLineUnder(successorIndex, keyAfterFragment));

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
        var reach = new ArrayList<AccidentalReconciliation.ReachedLine>();

        reach.add(AccidentalReconciliation.ReachedLine.receiving(
            line,
            new AccidentalReconciliation.Insertion(
                spacingInsertIndex,
                spacingDeleteRange,
                new AccidentalReconciliation.ArrivingElements(
                    instantiated.elements(), instantiated.priorAccidentals(),
                    instantiated.spans())),
            strandedAfter));

        reach.addAll(tail);

        // A paste-replace removes the explicit accidentals of the range it overwrites, so it asks
        // the same question a plain deletion does. Folded into the reach's own prompt rather than
        // raised beside it, so however much of the song this paste re-keys it still asks once —
        // before the fit gate, and before anything is mutated, so Cancel reuses the LINE_FULL
        // contract exactly.
        var overwritten = (spacingDeleteRange == null)
            ? List.<AccidentalRestatements.EditedLine>of()
            : List.of(new AccidentalRestatements.EditedLine(
                line,
                AccidentalRestatements.inDeletedRange(
                    line, spacingDeleteRange.begin(), spacingDeleteRange.end())));

        var confirmed = KeyChangeReconciliation.confirm(score, overwritten, reach);

        if (confirmed.isCancelled()) {
            return FragmentInsertOutcome.CANCELLED;
        }

        // A fragment of nothing but a key change restating the key already running here, and the
        // barline behind it, leaves nothing to place. What is left of the paste is the deletion a
        // paste-replace owes, under the reconciliation just confirmed; a pure insertion has
        // nothing left to do at all. Either way the gesture is spent, not declined.
        if (instantiated.elements().isEmpty()) {
            if (spacingDeleteRange != null) {
                deleteElementRange(
                    line,
                    line.effectiveRange(spacingDeleteRange.begin(), spacingDeleteRange.end()),
                    null,
                    confirmed);
            }

            return FragmentInsertOutcome.INSERTED;
        }

        var accidentalChanges = confirmed.changesFor(line);

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
                    var insertedTypes =
                        instantiated.elements().stream().map(StaffElement::getType).toList();

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

        // The lines the fragment's key re-keys, and the accepted restatements past them, join the
        // caller's bracket, so the paste and every removal it authorized are one undo step. This
        // line is recorded by the gate above, against its pre-mutation indices, so the
        // reconciliation skips it here rather than recording it twice.
        var keyReconciliation = confirmed.withChangesRecordedByCaller(line);

        keyReconciliation.commit();

        // The key changes the moved key strands on those lines, in the same bracket. Nothing below
        // touches them, so their indices are still the ones the reach reported. What the key
        // strands on this line waits until the paste is done moving it — see the deferred sweep at
        // the end of this method.
        keyReconciliation.sweepExcept(line);

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
                line, line.effectiveRange(deleteRange.begin(), deleteRange.end()), null,
                KeyChangeReconciliation.Confirmed.PROCEED);

            // The deletion may have removed elements before the range too (a paired
            // grace note cascade), so re-derive the insertion index from what survived.
            insertAt = successor != null ? line.getElementIndex(successor) : line.effectiveElementCount();
        }

        var clones = instantiated.elements();

        // The trailing shift is re-derived from the successor's captured target X so it stays
        // correct after the deletion gap-fill. With no successor there is nothing after the
        // fragment to move, and insertRun's shift loop has no elements to reach.
        var tailShiftPx = successor != null ? successorTargetXPx - successor.getXOffsetPx() : 0;

        // Hard ordering constraint: every clone must be inserted before the first
        // addPastedSpan. Adding a span re-parents only the span, not its
        // anchor/end, and getAnchorElementIndex() resolves through the anchor's
        // own getLine() — a span added while its anchors still carry the source
        // line's back-reference makes addElement's isInvalidatedByInsertion sweep
        // evaluate it against the wrong line, yielding a wrong index or -1. The
        // hairpin merge in addPastedSpan reads those same indices, so it
        // would mis-measure what to absorb for exactly the same reason.
        //
        // The addElement inside insertRun additionally drops the spans the inserted
        // element types invalidate, each by its own rule: the ending's
        // barline/repeat-aware one, which is more precise than the straddle test
        // PasteSpanReconciliation applies to the other span kinds, so endings are left
        // to it; and the tie's separator rule. Its tuplet removal is now redundant but
        // harmless — the reconciliation above already removed a straddled tuplet, so
        // findTupletAt finds nothing. Its tie removal is not redundant: the
        // reconciliation judges only ties with both endpoints in this line, leaving a
        // tie that straddles a line boundary to this sweep, which resolves it against
        // the receiving line.
        line.insertRun(insertAt, result.place(clones), tailShiftPx);

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

        // The key changes the fragment's key strands on the rest of this line go last, so
        // every index the paste itself resolves — the successor it measures the trailing shift
        // from, the span anchors — is still the one it was measured against. Each of these ranges
        // begins at or past the successor, so all of them moved by exactly what the successor
        // moved by.
        var strandedShift = insertAt + clones.size() - successorIndex;

        keyReconciliation.sweepDeferred(line, strandedShift);

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
}
