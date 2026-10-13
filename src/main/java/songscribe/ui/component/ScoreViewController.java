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
import javax.swing.JOptionPane;

import net.engio.mbassy.listener.Handler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.Strings;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Attachment;
import songscribe.dom.AttachmentRemoval;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Hairpin;
import songscribe.dom.Key;
import songscribe.dom.KeyChangeStranding;
import songscribe.dom.Line;
import songscribe.dom.ProjectedEdit;
import songscribe.dom.RecordedChange;
import songscribe.dom.SongStart;
import songscribe.dom.StaffElement;
import songscribe.dom.StaffElementRun;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.error.RuntimeError;
import songscribe.hit.HitTarget;
import songscribe.layout.AccidentalFitGate;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.MessageSubscription;
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
import songscribe.message.mutation.Mutation;
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
import songscribe.prefs.PrefsKey;
import songscribe.ui.EditResult;
import songscribe.ui.Mode;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.playback.PlaybackState;
import songscribe.ui.selection.RangeQueries.HairpinResolution;
import songscribe.ui.FitRefusals;
import songscribe.ui.OptionDialogs;
import songscribe.ui.SlideOperations;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.clipboard.Fragment;
import songscribe.ui.clipboard.FragmentPaste;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.InsertionPointMode;
import songscribe.ui.edit.ScoreActions;
import songscribe.ui.playback.PlaybackController;
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

        MessageSubscription.addProcessListener(this);
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

    /**
     * The index of the line that has a line selection, for a caller already inside a
     * precondition that guarantees one is selected.
     */
    private int requireSelectedLine() {
        var selectedLine = selectionCoordinator.getSelectedLine();

        if (selectedLine == null) {
            throw RuntimeError.exit("no line selected");
        }

        return selectedLine;
    }

    @Handler
    public void handleInsertLine(InsertLineCommand message) {
        var song = score.getSong();
        var line = new Line(song);

        // The two relative variants are disabled without a line selection,
        // so requireSelectedLine() is only called where it is known to be valid.
        switch (message.getType()) {
            case ADD_AT_END -> song.addLine(line);
            case INSERT_BEFORE -> song.addLine(requireSelectedLine(), line);
            case INSERT_AFTER -> song.addLine(requireSelectedLine() + 1, line);
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
        operations.toggleTuplet(message.getGrade());
        score.selectionChanged();
    }

    @Handler
    public void handleAddHairpin(AddHairpinCommand message) {
        operations.addHairpinToSelection(message.kind());
    }

    @Handler
    public void handleFirstSecondEnding(FirstSecondEndingCommand message) {
        if (operations.makeFirstSecondEnding()) {
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

    public boolean canMakeFirstSecondEnding() {
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
        // affects (one, or two for a tie crossing a line break).
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
            var tieReach = linesReached(message, message.getSong()::tieReach);
            var keyMoveReach = keyMoveReach(message);

            for (var linePanel : staffPanel.getLinePanels()) {
                var line = linePanel.getLine();

                if (targetLine == null
                    || line == targetLine
                    || tieReach.contains(line)
                    || keyMoveReach.contains(line)) {
                    linePanel.getLineComponent().invalidateLayout();

                    // With neither a tie across a line break nor a key move in the notification
                    // the named line is the only one that can match, so the panels after it have
                    // nothing left to be checked against.
                    if (targetLine != null && tieReach.isEmpty() && keyMoveReach.isEmpty()) {
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
     * The union, over this notification's mutations, of the lines {@code reachedBy} answers for
     * each, or an empty set when it answers none for every one of them.
     * <p>
     * A set rather than a list because two mutations in one batch routinely reach the same
     * lines, and because the panel loop asks membership rather than order.
     */
    private static Set<Line> linesReached(
        SongDidChangeNotification message, Function<Mutation, List<Line>> reachedBy) {
        Set<Line> reach = null;

        for (var mutation : message.getMutations()) {
            var lines = reachedBy.apply(mutation);

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
     * Every line whose drawn key content this notification's mutations moved, or an empty set
     * when none of them moves a key.
     * <p>
     * A key move's layout effect reaches past the line the mutation names. It runs
     * forward through every line that inherits the moved key — each of whose headers is solved
     * from the key it runs in — and back one line, whose trailing space is solved from the
     * cautionary it leads into. Without this the line before the change redraws its cautionary
     * from the live document while keeping the spacing it was solved with, and an inheriting line
     * goes on drawing the key it was in before the edit.
     * <p>
     * {@link Song#keyMoveReach} is the one answer; this only unions it over the batch, where a
     * key change and the accidentals the chain walk restamps after it routinely reach the same
     * lines.
     */
    private static Set<Line> keyMoveReach(SongDidChangeNotification message) {
        return linesReached(message, message.getSong()::keyMoveReach);
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

        if (state == PlaybackState.PLAYING) {
            return;
        }

        // A rewind goes back to the top of the song, so the selection that would otherwise
        // anchor the next Play is dropped. The score owns its selection, so it drops it
        // here rather than being reached into from the playback controller. Guarded because
        // clearSelection() posts a MusicSelectionDidChangeNotification even when there was
        // nothing selected.
        if (state == PlaybackState.REWOUND && score.getSelection() != null) {
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

        // Widened once here and passed to the stranding and the deletion, so the range the key
        // changes are computed against is the range that goes.
        var bounds = line.effectiveRange(range.range());

        var stranding = deletionStranding(line, bounds);

        handleCopy();

        // Clear the selection before removing elements so that action handlers
        // reacting to SongDidChangeNotification (posted synchronously when the
        // modification bracket closes) don't query selection indices that no
        // longer exist on the shrunk line.
        selectionCoordinator.clearSelection();

        // One bracket for the deletion. The Cut action's op-name (Tier A) names this outermost
        // step.
        score.getSong().withModification(() -> deleteElementRange(line, bounds, stranding));

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
                Fragment.capture(range.line(), range.range())
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
     *   <li>A non-null element range deletes it with {@code deleteElementRange()}. A tie whose end
     *       note the range takes is untied with it.
     *   <li>A non-null selected target dispatches on its kind in {@code deleteSelectedTarget()}: a
     *       slide, articulation or attachment goes through {@code modifyElement}; every span kind
     *       — ending, trill, hairpin, beam, tuplet — through its own {@code removeFrom}; a tie
     *       through {@code removeTie}, once, on its anchor's line, even when it crosses a line
     *       break. An attachment resolves further to its own field (fermata, dynamic,
     *       annotation, beat change, tempo change).
     *   <li>Otherwise, if {@code canDeleteLine()} allows it, the line itself is removed. Taking a
     *       line out moves the key every following line inherits, so this branch removes with it
     *       any mid-line key change the move strands, exactly as an inserted key change does.
     * </ul>
     *
     * <p>The two branches that move a key — the element range and the line — compute what they
     * strand before opening a bracket and sweep it inside, so the stranded key changes and the
     * deletion are one undo step. Neither asks anything: the song's chain walk after the bracket
     * closes re-decides every note the deletion reaches.
     *
     * <p>Every branch but the lyric one falls through to the shared tail, which restores the
     * selected action states and deselects the score.
     */
    void handleDelete() {
        var song = score.getSong();

        if (selectionCoordinator.getSelectedTarget() instanceof HitTarget.Lyric(var element, var script)) {
            // The selection layer clears a selection that is no longer live, so a selected
            // lyric's element is in a line.
            var line = element.requireParentLine();
            var index = line.getElementIndex(element);

            song.withModification(Strings.get(Strings.ACTION_EDIT_OP_DELETE_LYRIC), () -> {
                line.modifyElement(index, ElementField.LYRIC, () ->
                    line.getElement(index).removeLyricForScript(script));
                line.adjustNeighborsForLyricDeletion(index, script);
            });

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
            var selected = range.range();

            // Widened once here and passed to the stranding and the deletion, so the range the
            // key changes are computed against is the range that goes.
            var bounds = line.effectiveRange(selected);

            var stranding = deletionStranding(line, bounds);

            // Name the undo step from the categories of the user-selected elements
            // (computed before removal, while they are still present on the line).
            var selectedTypes = line.getElements(selected).stream()
                .map(StaffElement::getType)
                .toList();
            var deleteLabel = OpNames.deleteLabel(selectedTypes);

            // Clear the selection before removing elements so that action handlers
            // reacting to SongDidChangeNotification (posted synchronously when the
            // modification bracket closes) don't query selection indices that no
            // longer exist on the shrunk line.
            selectionCoordinator.clearSelection();

            // deleteElementRange's own bracket nests inside the one opened here, which names
            // the step: the op name is captured only at the outermost bracket.
            song.withModification(deleteLabel,
                () -> deleteElementRange(line, bounds, stranding));
        } else if (selectedTarget != null && targetLine != null) {
            deleteSelectedTarget(targetLine, selectedTarget);
        } else if (score.canDeleteLine()) {
            var lineIndex = requireSelectedLine();
            var deletedLine = song.getLine(lineIndex);

            // Computed against the lines as they stand, before the bracket opens. The stranding
            // never names the deleted line itself: the line deletion takes it away whole.
            var stranding = KeyChangeStranding.ofLineDeletion(deletedLine);

            song.withModification(OpNames.deleteLineLabel(), () -> {
                stranding.sweep();
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

                // A slide selection is only ever made on an element that carries a slide;
                // guard anyway so the @Nullable getSlide() result isn't passed on unchecked.
                if (slide != null) {
                    // Capture before the removal: stripping the glissando un-pairs the grace
                    // note, so by the time the sync runs there is no pairing left to read.
                    var wasPairedGraceNote = line.isPairedGraceNote(elementIndex);

                    line.getSong().withModification(OpNames.deleteSlideLabel(slide), () -> {
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
                line.getSong().withModification(OpNames.deleteEndingLabel(), () -> ending.removeFrom());

            case HitTarget.Hairpin(var hairpin) ->
                line.getSong().withModification(OpNames.deleteHairpinLabel(hairpin), () -> hairpin.removeFrom());

            // A cross-line tie's anchor sits on only the first of its two lines (see
            // HitTarget.Tie), which is not necessarily the line the hit was made against, so
            // the removal is recorded on the anchor's own line.
            case HitTarget.Tie(var tie) -> {
                var anchor = tie.anchor();
                var anchorLine = anchor.requireParentLine();

                anchorLine.getSong().withModification(
                    OpNames.removeTieLabel(), () -> anchorLine.removeTie(anchor));
            }

            case HitTarget.Beam(var beam) ->
                line.getSong().withModification(OpNames.removeBeamLabel(), () -> beam.removeFrom());

            case HitTarget.Tuplet(var tuplet) ->
                line.getSong().withModification(OpNames.removeTupletLabel(), () -> tuplet.removeFrom());

            // The explicit label is not optional: the generic SpanRemoval's unlabeled fallback
            // op-name is the literal text "Ending", so an unlabeled trill removal would make
            // the undo menu read "Undo Ending".
            case HitTarget.Trill(var trill) ->
                line.getSong().withModification(OpNames.removeTrillLabel(), () -> trill.removeFrom());

            case HitTarget.Articulation(var articulation) -> {
                var owner = articulation.owner();
                var elementIndex = resolveOwnerIndex(line, owner);

                if (owner != null && elementIndex != null) {
                    line.getSong().withModification(OpNames.removeArticulationLabel(articulation.getType()), () ->
                        line.modifyElement(elementIndex, ElementField.ARTICULATION,
                            () -> owner.removeArticulation(articulation)));
                }
            }

            case HitTarget.Attachment(var attachment) -> deleteAttachment(line, attachment);

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

        if (!line.contains(owner)) {
            LOG.debug("Cannot delete the selected target: its owner element ({}) is not on the active line",
                owner.getType());
            return null;
        }

        return line.getElementIndex(owner);
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
        var element = attachment.owner();
        var elementIndex = resolveOwnerIndex(line, element);

        if (element == null || elementIndex == null) {
            return;
        }

        var plan = switch (attachment) {
            case FermataAttachment _ ->
                new AttachmentRemovalPlan(ElementField.FERMATA, () -> FermataAttachment.removeFermata(element));

            case DynamicAttachment dynamic ->
                new AttachmentRemovalPlan(ElementField.DYNAMIC_ATTACHMENT, () -> element.removeAttachment(dynamic));

            case AnnotationAttachment _ ->
                new AttachmentRemovalPlan(ElementField.ANNOTATION, () -> AttachmentRemoval.removeAnnotation(element));

            case BeatChangeAttachment _ ->
                new AttachmentRemovalPlan(ElementField.BEAT_CHANGE, () -> AttachmentRemoval.removeBeatChange(element));

            case TempoChangeAttachment _ ->
                new AttachmentRemovalPlan(ElementField.TEMPO_CHANGE, () -> AttachmentRemoval.removeTempoChange(element));
        };

        line.getSong().withModification(OpNames.removeAttachmentLabel(attachment), () ->
            line.modifyElement(elementIndex, plan.field(), plan.mutator()));
    }

    /**
     * Returns the key changes a deletion of {@code bounds} strands, on {@code line} and on every
     * line inheriting past it. Pure and pre-mutation: callers compute it <b>before</b> opening a
     * modification bracket and hand it to {@link #deleteElementRange}, which sweeps it inside.
     *
     * <p>The range is widened exactly as {@link Line#deleteRange} widens it — a paired grace
     * note before the range does not survive its host, and a trailing breath mark, or the barline a
     * key change sits behind, goes with the range — so the key changes counted are the ones the
     * deletion really removes.
     *
     * <p><b>The stranding is more than this line.</b> A deletion that takes a mid-line key change
     * with it moves the key every following line inherits, so it strands what an inserted key
     * change strands, ending where the inheritance chain does. A deletion that removes no key
     * change leaves the line's end key where it was, so {@link KeyChangeStranding#ofMidLineEdit}
     * reaches nothing past this line — which is why it is computed unconditionally rather than
     * behind a test for what the range holds. See {@code docs/key-changes.md}.
     *
     * @param line   the line the deletion happens on
     * @param bounds the range that really goes
     * @return the stranding the deletion owes; empty on every line when it moves no key
     */
    private static KeyChangeStranding deletionStranding(Line line, StaffElementRun.EffectiveRange bounds) {
        var range = bounds.range();

        // A deletion that takes a key change with it leaves the key that was running before the
        // range running past it, which can leave a later key change on this same line restating
        // it.
        var keyAfterRemoval = line.keyBefore(range.begin());
        var strandedAfter = line.redundantKeyChangeRanges(range.end() + 1, keyAfterRemoval);

        return KeyChangeStranding.ofMidLineEdit(
            line, strandedAfter, line.keyAtEndOfLineAfterRemoving(bounds));
    }

    /**
     * Sweeps the key changes a deletion of {@code range} on {@code line} strands, then deletes
     * that range through {@link Line#deleteRange}. Callers are responsible for clearing the
     * selection before calling this.
     * <p>
     * Names no undo step of its own: every caller opens the outermost bracket and names the
     * step there, and the op-name is captured only at the outermost bracket, so the bracket
     * opened here only nests.
     * <p>
     * Deletion is not fit-gated: the song's chain walk after the bracket closes may write an
     * accidental on the first note after the range at a position the range held one at, but the
     * range freed that note's head and its accidental, so the line can never get wider.
     * <p>
     * The stranding is the caller's, in {@code stranding}, computed before any bracket opens
     * against the pre-removal line, which is why it is swept before the range goes: its ranges
     * name pre-removal indices, all past the range, which the removal would move.
     */
    private void deleteElementRange(
        Line line,
        StaffElementRun.EffectiveRange range,
        KeyChangeStranding stranding) {

        line.getSong().withModification(() -> {
            stranding.sweep();
            line.deleteRange(range);
        });
    }

    /**
     * Outcome of {@link #tryInsertFragment}. Only {@link #INSERTED} means the line was mutated;
     * every other constant leaves it untouched.
     */
    public enum FragmentInsertOutcome {
        /** The paste happened, or the gesture was spent on a fragment that reduced to nothing. */
        INSERTED,
        /** The fit gate refused the paste and the "line full" error was shown. */
        LINE_FULL,
        /** The clipboard holds nothing. */
        EMPTY,
        /** The user declined a confirm about the paste itself, so the gesture is over. */
        CANCELLED,
        /**
         * An error alert was shown about the chosen point, nothing was mutated, and the user may
         * pick another point.
         */
        DECLINED_BY_ALERT
    }

    /** Index of the "Remove & paste" button in {@link #confirmRemoveLeadingBreathMark}'s options. */
    private static final int REMOVE_AND_PASTE_OPTION = 0;

    /**
     * Confirms removing the breath mark a fragment leads with, when the line cannot take it at
     * the chosen point, so the rest of the fragment can be pasted.
     *
     * @return {@code true} when the user chose "Remove & paste"; {@code false} on Cancel or a
     *     closed dialog
     */
    private static boolean confirmRemoveLeadingBreathMark() {
        var cancelLabel = Strings.get(Strings.BUTTON_CANCEL);
        var removeLabel = Strings.get(Strings.BUTTON_REMOVE_AND_PASTE);
        var result = OptionDialogs.showOptionDialog(
            null,
            Strings.CONFIRM_TITLE_PASTE,
            Strings.CONFIRM_PASTE_LEADING_BREATH_MARK,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            new Object[]{removeLabel, cancelLabel},
            removeLabel
        );
        return result == REMOVE_AND_PASTE_OPTION;
    }

    /**
     * Inserts the clipboard fragment into {@code line} at {@code insertIndex},
     * first deleting {@code deleteRange} when present (paste-replace). The fit
     * check runs against the pre-delete line, so on {@code LINE_FULL} nothing has
     * been mutated: the "line full" error is shown, a caller-opened bracket stays
     * empty, and no notification is posted. Callers decide recovery.
     *
     * <p><b>A breath mark stands only immediately after a duration element</b>, and the paste
     * keeps that on both of its ends before the fit gate runs. A fragment leading with a breath
     * mark the line cannot take at {@code insertIndex} ({@link StaffElementRun#canLeadRunAt}) is
     * refused with the "A breath mark cannot be placed here" alert and {@code DECLINED_BY_ALERT}
     * when the breath mark is the whole fragment; otherwise a confirm offers "Remove &amp;
     * paste", which drops the leading breath mark and pastes the rest, and Cancel, which returns
     * {@code CANCELLED}. A fragment whose last element may not stand before the breath mark at
     * {@code insertIndex} ({@link StaffElementRun#canTrailRunAt}) is refused with the "These
     * elements may not be inserted before a breath mark" alert and {@code DECLINED_BY_ALERT}.
     * Both refusals leave the line untouched.
     *
     * <p><b>No bar line stands first in the song</b> ({@link SongStart}). When {@code insertIndex}
     * is the song-start slot ({@link StaffElementRun#isSongStartAt}) and the fragment starts with
     * one or more bar lines, the paste drops each of them, with the key change that may sit
     * behind one, and pastes the rest. The drop happens before the breath-mark test and the fit
     * gate, so every later step sees the run that lands. It asks nothing: once the paste is
     * certain to happen, it records one {@link SongStart#removedEntry} for {@code line} into the
     * open bracket's report, and a fragment span with an endpoint on a dropped element is not
     * pasted and is reported with the other spans the paste leaves out. When the drop takes a key
     * change, the key of the last one it takes becomes {@code line}'s own key
     * ({@link Line#setKey}), set inside the caller's bracket ahead of the fragment, so the paste
     * and the key move are one undo step; the key reaches the lines inheriting from {@code line}
     * as a key set by hand does, and the paste sweeps each key change it then leaves restating
     * the key in effect.
     *
     * <p>Must be called inside a modification bracket — both paste-replace and
     * paste-mode placement supply their own, so delete + insert form one undo step.
     *
     * <p><b>The paste is gated on the accidentals it will draw.</b> The chain walk runs over a
     * projection of the destination line with the clones in place, so the fit check measures each
     * clone as it will sound where it lands and every later note whose chain the paste changes —
     * on this line and, through {@link AccidentalFitGate}, on any other line a restamp widens.
     * The projection ({@link ProjectedEdit}) starts the line in the key the paste leaves it
     * starting in — the moved key when the drop at the song start takes a key change, and the
     * line's running key otherwise — so the walk decides each accidental, and the fit check
     * measures the line's opening key signature, in that key, although the key itself moves only
     * once the paste lands.
     *
     * <p><b>A fragment carrying a key change re-keys more than this line.</b> The key it
     * leaves the destination line in reaches every line inheriting past it, so the paste removes
     * every key change it leaves restating the key already in effect — one of the fragment's own
     * included — together with the barline behind it, exactly as a key change written by hand
     * does. A line a moved key leaves overflowing, with no restamp on it, is <b>not</b> fit-gated:
     * it renders overflowing and flagged, which is what the program does everywhere outside the
     * edits {@code KeyEditFitCalculator} covers.
     *
     * <p><b>The paste reports every span it removes or leaves out.</b> Once the gates pass,
     * {@link FragmentPaste#paste} lands the fragment: it records into the open bracket's report
     * each destination span the paste lands inside, each fragment span a span kind's rule drops
     * or refuses where it landed, and each fragment span whose endpoint went with a key change the
     * paste dropped. The report is shown when the caller's bracket closes. A refusal reports
     * nothing.
     *
     * @param line   The destination line
     * @param target Where the fragment lands: a plain index, or a range this paste deletes
     *               first and the index it leaves the fragment at
     * @return {@code EMPTY} when the clipboard holds nothing; {@code LINE_FULL} when the fit gate
     *     refused the paste; {@code DECLINED_BY_ALERT} when a breath-mark alert refused the chosen
     *     point and the notator may pick another; {@code CANCELLED} when the notator declined a
     *     confirm; and {@code INSERTED} when the paste happened. {@code INSERTED} also covers the
     *     case where the fragment reduced to nothing because every key change it carried restates
     *     the key already running here, or because it held nothing but bar lines dropped at the
     *     start of the song — the gesture is spent and the caller should complete it, even though
     *     no element was placed; a paste-replace has still deleted its range, and a dropped key
     *     change has still moved its key. Only {@code INSERTED} means the line was mutated
     */
    public FragmentInsertOutcome tryInsertFragment(Line line, StaffElementRun.InsertionTarget target) {
        var fragment = clipboardManager.getFragment();

        if (fragment == null || fragment.elements().isEmpty()) {
            return FragmentInsertOutcome.EMPTY;
        }

        var insertIndex = target.insertIndex();
        var deleteRange = target.deleteRange();

        // The elements the paste-replace removes, for the projections and the spacing
        // calculation, which read the raw range. The paired grace note before the caller's
        // selection is already inside it, so the clones are positioned against a predecessor
        // that survives.
        var rawDeleteRange = deleteRange == null ? null : deleteRange.range();

        // The key the fragment's first element lands in.
        var keyAtInsertion = line.keyBefore(insertIndex);

        // Fresh clones every paste — the stored fragment is never itself inserted. The clones
        // carry no line back-reference until the paste inserts them, so building them early
        // touches nothing — which is also why the fit gate can measure these clones rather than
        // the stored fragment's elements.
        //
        // A fragment carries the key it was copied under, so one of its own key changes can
        // arrive restating the key already running where it lands. It is dropped here rather
        // than deleted after the insertion, so the chain walk, the fit measurement and the
        // span reconciliation all see the run that actually lands.
        var reduction = fragment.instantiate().withoutRedundantKeyChanges(keyAtInsertion);

        // No bar line stands first in the song, so at the song-start slot the bar lines that
        // start the fragment are dropped — each with the key change it may carry, which cannot
        // outlive it — and the paste goes ahead with the rest, as one more thing the song changed
        // on its own. Dropped here rather than after the insertion, for the same reason the
        // redundant key changes are.
        //
        // A key change dropped with its bar line set the key the rest of the fragment sounds in,
        // so that key becomes the line's own key when the paste lands.
        var songChanges = new ArrayList<RecordedChange>();
        @Nullable Key movedKey = null;

        if (line.isSongStartAt(insertIndex) && leadsWithBar(reduction.fragment())) {
            var drop = reduction.withoutLeadingBars();
            reduction = drop.reduction();
            movedKey = drop.movedKey();
            songChanges.add(SongStart.removedEntry(line, movedKey));
        }

        var instantiated = reduction.fragment();

        // A fragment of nothing but a key change restating the key already running here, and the
        // barline behind it, or of nothing but bar lines dropped at the start of the song, leaves
        // nothing to place. What is left of the paste is the deletion a paste-replace owes, and
        // the key a dropped key change moves; a pure insertion moving no key has nothing left to
        // do at all. Either way the gesture is spent, not declined.
        if (instantiated.elements().isEmpty()) {
            landFragment(line, target, reduction, new SongChanges(songChanges, movedKey));
            return FragmentInsertOutcome.INSERTED;
        }

        // A breath mark stands only immediately after a duration element, so the fragment is
        // tested at both ends before anything is measured or mutated. A paste-replace deletes
        // from insertIndex, so the predecessor the leading test reads is the same before and
        // after the deletion; its trailing test can never fail, because the effective range
        // already takes a breath mark after the range into the deletion.
        var leadsWithBreathMark = instantiated.elements().getFirst().getType().isBreathMark();

        if (leadsWithBreathMark && !line.canLeadRunAt(insertIndex)) {
            if (instantiated.elements().size() == 1) {
                OptionDialogs.showErrorMessage(
                    null, Strings.ALERT_TITLE_PASTE, Strings.ALERT_PASTE_BREATH_MARK_NOT_HERE);
                return FragmentInsertOutcome.DECLINED_BY_ALERT;
            }

            if (!confirmRemoveLeadingBreathMark()) {
                return FragmentInsertOutcome.CANCELLED;
            }

            // The leading breath mark is dropped here rather than after the insertion, so the
            // chain walk, the fit measurement and the span reconciliation below all see the run
            // that actually lands. A breath mark anchors no span, so every fragment span survives.
            var elements = instantiated.elements();
            instantiated = new Fragment(elements.subList(1, elements.size()), instantiated.spans());
            reduction = new Fragment.Reduction(instantiated, reduction.strandedSpans());
        }

        var clones = instantiated.elements();

        if (!line.canTrailRunAt(insertIndex, clones.getLast().getType())) {
            OptionDialogs.showErrorMessage(
                null, Strings.ALERT_TITLE_PASTE, Strings.ALERT_PASTE_NOT_BEFORE_BREATH_MARK);
            return FragmentInsertOutcome.DECLINED_BY_ALERT;
        }

        // The line as the paste will leave it: the deleted range swapped for the clones, starting
        // in the key a dropped key change moves onto it. The key moves only once the paste lands,
        // so the walk and the fit measurement read it from the projection.
        var startKey = (movedKey != null) ? movedKey : line.getRunningKey();
        var edit = (rawDeleteRange == null)
            ? ProjectedEdit.inserting(line, insertIndex, clones, startKey)
            : ProjectedEdit.replacing(line, rawDeleteRange, clones, startKey);
        var restamps = edit.walk();

        // The LINE_FULL refusal leaves the line exactly as it was (C1), so it lives inside the
        // fit gate: it stamps the walk's accidentals, runs this check with them in place so the
        // projection measures the right widths, and then puts every live note back as it was,
        // leaving only the clones stamped.
        var lyricRenderMetrics = score.getLyricRenderMetrics();

        var verdict = AccidentalFitGate.judge(
            line, restamps, clones, lyricRenderMetrics,
            () -> InsertionSpacingCalculator.calculateFragmentInsertion(edit, lyricRenderMetrics)
                .fitsWithinLine(line.getSong().getLineWidthSs().value()));

        var committed = FitRefusals.accepts(
            verdict, null,
            () -> OptionDialogs.showErrorMessage(
                null, Strings.ALERT_TITLE_INSERT_ERROR, Strings.ERROR_LINE_FULL_PASTE));

        if (!committed) {
            return FragmentInsertOutcome.LINE_FULL;
        }

        landFragment(line, target, reduction, new SongChanges(songChanges, movedKey));

        return FragmentInsertOutcome.INSERTED;
    }

    /** Whether the first element of {@code fragment} is a bar line; {@code false} when it is empty. */
    private static boolean leadsWithBar(Fragment fragment) {
        var elements = fragment.elements();

        return !elements.isEmpty() && elements.getFirst().getType().isBar();
    }

    /**
     * What a paste changes in the song on its own, beyond landing its fragment.
     *
     * @param entries  the change-report entries to record
     * @param movedKey the key a dropped key change moves onto the destination line, or
     *                 {@code null} when the paste moves none
     */
    private record SongChanges(List<RecordedChange> entries, @Nullable Key movedKey) {
    }

    /**
     * Records {@code songChanges} into the open bracket's report, makes their moved key the
     * destination line's own key, then lands what is left of a paste through
     * {@link FragmentPaste#paste}. The changes are made only here, once the paste is certain to
     * happen, so a refused paste leaves the bracket empty.
     *
     * <p>The key is set before the fragment lands, so the paste measures the key changes it
     * strands — on the line from where the fragment ends, and on every line inheriting past it —
     * against the key the line now starts in, and sweeps them as a key set by hand sweeps them.
     */
    private static void landFragment(
        Line line, StaffElementRun.InsertionTarget target, Fragment.Reduction reduction,
        SongChanges songChanges
    ) {
        var song = line.getSong();
        songChanges.entries().forEach(song::report);

        var movedKey = songChanges.movedKey();

        if (movedKey != null) {
            line.setKey(movedKey);
        }

        FragmentPaste.paste(line, target, reduction);
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
        // takes the barline in front of a key change at begin, so the spacing was measured for
        // a smaller range than the one that goes.
        var effective = line.effectiveRange(range.range());

        // One bracket for the whole replace — delete + insert is a single undo
        // step. On LINE_FULL tryInsertFragment mutates nothing, so the bracket
        // closes empty, posts no notification, and the selection stays intact.
        var outcome = score.getSong().withModificationResult(() -> {
            var result = tryInsertFragment(line, new StaffElementRun.InsertionTarget.Replacing(effective));

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
