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

import module java.desktop;

import java.util.List;

import net.engio.mbassy.listener.Handler;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
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
import songscribe.message.command.ToggleTieCommand;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.command.UpdatePreviewElementCommand;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineScopedMutation;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.notification.DocumentDidLoadNotification;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.message.notification.ElementTypeWasSelectedNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.message.notification.PreviewElementDidChangeNotification;
import songscribe.message.notification.RestModeDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.prefs.PrefsKey;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.layout.AccidentalMaterializer;
import songscribe.layout.AccidentalReconciliation;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.ui.EndingConfirms;
import songscribe.ui.Mode;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.MusicEditOperations.HairpinResolution;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.score.PreviewElementManager;
import songscribe.ui.clipboard.Fragment;
import songscribe.ui.clipboard.PasteSpanReconciliation;
import songscribe.ui.edit.AccidentalRestatements;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.PasteModeManager;
import songscribe.ui.edit.ScoreActions;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.SelectedDecoration;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.dom.EndingValidationResult;
import songscribe.ui.selection.TupletToggleInfo;
import songscribe.undo.OpNames;
import songscribe.util.Debounce;
import songscribe.util.UIUtils;

/**
 * Coordinates message handling for the ScoreView component.
 * Handles all @Handler methods for messages posted to the MessageCenter.
 */
public final class ScoreViewController {

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
        this.scoreActions = score;
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
        updatePreviewElement();
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
        var shift = message.getShift();
        var song = score.getSong();

        if ((selectionCoordinator.getSelectedLine() != -1) || (shift == InsertLineAction.ADD)) {
            var index = (shift >= 0)
                ? (selectionCoordinator.getSelectedLine() + shift)
                : InsertLineAction.ADD;
            song.addLine(index, new Line(song));
            score.deselect();
        } else {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_LINE_ERROR,
                Strings.ERROR_LINE_NO_SELECTION
            );
        }
    }

    @Handler
    public void handleToggleBeam(ToggleBeamCommand message) {
        var selection = selectionCoordinator.getActiveSelection();

        if (selection == null) {
            return;
        }

        operations.toggleBeaming();
    }

    @Handler
    public void handleToggleBeamWithPrevious(ToggleBeamWithPreviousCommand message) {
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

        // Re-arming the target is left to the operation, which does it only on the branches
        // that actually modify the line. Arming here instead would leave the slot armed on
        // every refusing path, with no commit coming to consume it.
        if (!MusicEditOperations.toggleBeamWithPredecessor(insertion.line(), insertion.elementIndex())) {
            UIUtils.beep();
        }
    }

    @Handler
    public void handleToggleTie(ToggleTieCommand message) {
        operations.toggleTie();
    }

    @Handler
    public void handleToggleTuplet(ToggleTupletCommand message) {
        operations.toggleTuplet(message.getTupletSize(), operations.canToggleTuplet());
        score.selectionChanged();
    }

    @Handler
    public void handleAddHairpin(AddHairpinCommand message) {
        operations.addHairpinToSelection(message.isCrescendo());
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

    public TupletToggleInfo canToggleTuplet() {
        var cached = cachedTupletToggleInfo;

        if (cached != null) {
            return cached;
        }

        return operations.canToggleTuplet();
    }

    public HairpinResolution resolveHairpinAction() {
        return operations.resolveHairpinAction();
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

        if (all || key == PrefsKey.LOOP_PLAYBACK || key == PrefsKey.PLAY_WITH_REPEATS) {
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
        // An undo/redo can remove elements from the selected line without touching the
        // selection — undoing an insertion is enough — which leaves the selected range
        // running past the end of the line. Every later reader of that range would index
        // off the end, so it is dropped here, in the highest-priority handler for this
        // notification, ahead of warmTupletCache: the first such reader. Ordering by
        // program order rather than by another priority constant keeps the two from
        // drifting apart. The forward delete path has no need of this — it clears the
        // selection itself before shrinking the line.
        var selectionState = selectionCoordinator.getActiveSelection();

        if (selectionState != null) {
            selectionState.revalidateElementSelection();
        }

        warmTupletCache();

        var mainPanel = score.getMainPanel();

        if (mainPanel == null) {
            return;
        }

        // Three mutually exclusive cases, checked in order:
        //   full relayout?  ──yes──▶ invalidate every LinePanel's layout
        //          │no
        //          ▼
        //   line insert/delete? ──yes──▶ rebuild the LinePanel list (add/remove panels)
        //          │no
        //          ▼
        //   line-scoped change?  ──yes──▶ invalidate just the affected LinePanel
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

            for (var linePanel : staffPanel.getLinePanels()) {
                if (targetLine == null || linePanel.getLine() == targetLine) {
                    linePanel.getLineComponent().invalidateLayout();

                    if (targetLine != null) {
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
    public void musicSelectionDidChange(MusicSelectionDidChangeNotification message) {
        PlaybackController.selectionDidChange(score.getSelection());
    }

    @Handler
    public void playbackStateDidChange(
        PlaybackStateDidChangeNotification message
    ) {
        if (message.getState() == PlaybackController.PlaybackState.STOPPED) {
            if (MidiController.sequencer != null) {
                MidiController.sequencer.setTickPosition(0);
            }
            score.repaint();
        }
    }

    @Handler
    public void handlePasteboardOp(PasteboardOpCommand message) {
        // Belt-and-braces: while paste mode is active all pasteboard operations are
        // ignored. The action layer is already disabled via enableFromPasteMode; this
        // covers any non-action dispatch path.
        if (PasteModeManager.isActive()) {
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
        var state = selectionCoordinator.getActiveSelection();

        if (state == null || !state.hasElementSelection()) {
            return;
        }

        var line = state.getLine();
        var begin = state.getSelectionBegin();
        var end = state.getSelectionEnd();

        // Confirm before discarding an ending invalidated by the deletion, and do
        // it first: declining must leave both the clipboard and the score untouched.
        if (!confirmEndingInvalidatedByDeletion(line, line.getElements(begin, end))) {
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
        selectionCoordinator.clearSavedActionStates();
        score.deselect();
    }

    void handleCopy() {
        var state = selectionCoordinator.getActiveSelection();

        if (state != null && state.hasElementSelection()) {
            var line = state.getLine();
            clipboardManager.setFragment(
                Fragment.capture(line, state.getSelectionBegin(), state.getSelectionEnd())
            );
            score.deselect();
        }
    }

    void handleDelete() {
        var song = score.getSong();
        var lyricSelection = selectionCoordinator.getLyricSelection();

        if (lyricSelection != null) {
            var element = lyricSelection.element();
            var line = element.getLine();
            var index = line.getElementIndex(element);
            var verse = lyricSelection.verse();

            if (index >= 0) {
                song.withModification(Strings.get(Strings.ACTION_EDIT_OP_DELETE_LYRIC), () -> {
                    line.modifyElement(index, ElementField.LYRIC, () ->
                        line.getElement(index).setLyricForVerse(verse, null, false, "", Lyric.Extend.NONE));
                    line.adjustNeighborsForLyricDeletion(index, verse);
                });
            }

            selectionCoordinator.restoreSelectedActionStates();
            selectionCoordinator.clearLyricSelection();
            score.selectionChanged();
            score.repaint();
            return;
        }

        var state = selectionCoordinator.getActiveSelection();
        var decoration = state == null ? null : state.getSelectedDecoration();

        if (state != null && state.hasElementSelection()) {
            var line = state.getLine();
            var begin = state.getSelectionBegin();
            var end = state.getSelectionEnd();

            if (line.hasEndingInvalidatedByDeletion(line.getElements(begin, end))) {
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

            deleteElementRange(line, begin, end, deleteLabel, decision);
        } else if (state != null && decoration != null) {
            deleteDecoration(state.getLine(), decoration);
        } else if (score.canDeleteLine()) {
            song.withModification(OpNames.deleteLineLabel(),
                () -> song.removeLine(selectionCoordinator.getSelectedLine()));
        }

        // Restore the pre-selection selected states but not the enabled states — the song
        // has changed, so individual action handlers must re-evaluate enablement from the
        // current context, while the user's chosen duration button survives the delete.
        selectionCoordinator.restoreSelectedActionStates();
        score.deselect();
    }

    /**
     * Deletes the selected decoration from {@code line}, each variant in its own
     * modification bracket so the undo step is named after what was deleted.
     */
    private void deleteDecoration(Line line, SelectedDecoration decoration) {
        switch (decoration) {
            case SelectedDecoration.SlideSelection(var elementIndex) -> {
                var slideElement = line.getElement(elementIndex);
                var slide = slideElement.getSlide();

                // A slide selection is only ever made on an element that carries a slide;
                // guard anyway so the @Nullable getSlide() result is not passed on unchecked.
                if (slide != null) {
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

            case SelectedDecoration.EndingSelection(var ending) ->
                line.withModification(OpNames.deleteEndingLabel(), () -> line.removeRangeElement(ending));

            case SelectedDecoration.HairpinSelection(var hairpin) ->
                line.withModification(OpNames.deleteHairpinLabel(hairpin), () -> {
                    switch (hairpin) {
                        case Crescendo crescendo -> line.removeCrescendo(crescendo);
                        case Diminuendo diminuendo -> line.removeDiminuendo(diminuendo);
                    }
                });
        }
    }

    /**
     * Returns true when the caller may proceed with a deletion of {@code elements}:
     * either it discards no ending, or the user confirmed discarding one. Callers
     * must run this before mutating anything — declining leaves the score untouched.
     */
    private boolean confirmEndingInvalidatedByDeletion(Line line, java.util.List<StaffElement> elements) {
        return !line.hasEndingInvalidatedByDeletion(elements) || EndingConfirms.confirmInvalidation(score);
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
        var reconciledBegin = line.isHostOfPairedGraceNote(begin) ? begin - 1 : begin;
        var rangeEnd = line.effectiveDeleteEnd(end);

        return AccidentalRestatements.confirm(
            score, line, AccidentalRestatements.inDeletedRange(line, reconciledBegin, rangeEnd));
    }

    /**
     * Deletes the element range {@code begin} through {@code end} on {@code line},
     * naming the resulting undo step {@code label}. Confirmation-free: callers are
     * responsible for any ending-invalidation confirm and for clearing the selection
     * before calling this.
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
        var reconciledBegin = line.isHostOfPairedGraceNote(begin) ? begin - 1 : begin;

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
                    new InsertionSpacingCalculator.DeletedRange(reconciledBegin, line.effectiveDeleteEnd(end)),
                    List.of(),
                    List.of(),
                    List.of()),
                decision.removal())
            : List.<AccidentalReconciliation.AccidentalChange>of();

        // When the element immediately before the selection is a paired grace note,
        // deleteNote must remove it along with the first selected note — a non-contiguous
        // operation that cannot be expressed as a single range. Fall back to the per-element loop.
        if (line.isHostOfPairedGraceNote(begin)) {
            withModification(line, label, () -> {
                // Recorded before the removal so undo, which replays in reverse, restores the
                // accidentals once the elements are back at the indices they were recorded at.
                commitDeletionAccidentals(line, accidentalChanges);
                AccidentalRestatements.commitOtherLines(decision, line);
                deleteSelection(begin, end, line);
            });
        } else {
            var rangeEnd = line.effectiveDeleteEnd(end);

            // Shift elements after the selection to fill the gap, mirroring the
            // per-element xPos adjustment that deleteNote performs.
            if (rangeEnd < line.effectiveElementCount() - 1) {
                var shift = line.getElement(begin).getXOffsetPx() - line.getElement(rangeEnd + 1).getXOffsetPx();

                for (var i = rangeEnd + 1; i < line.effectiveElementCount(); i++) {
                    line.getElement(i).setXOffsetPx(line.getElement(i).getXOffsetPx() + shift);
                }
            }

            withModification(line, label, () -> {
                // Recorded before the removal, for the reason given in the other branch.
                commitDeletionAccidentals(line, accidentalChanges);
                AccidentalRestatements.commitOtherLines(decision, line);

                // Clean up the element before the range: its glissando has nothing left to
                // point at. Recorded like every other change here — stripping it raw would
                // leave undo restoring the deleted notes but not the glissando.
                if (begin > 0) {
                    var prevElement = line.getElement(begin - 1);

                    if (prevElement.hasGlissando()) {
                        line.modifyElement(begin - 1, ElementField.SLIDE, prevElement::removeSlide);
                    }
                }

                // Mirror deleteNote: adjust syllable relations and melisma extends
                // on neighbors before removing. Both helpers require the target
                // elements to still be present in the list.
                line.adjustSyllablesForNeighborChange(begin - 1, line.getElement(begin));

                // When rangeEnd was extended to include a trailing breath mark, this
                // loop also runs over that breath mark. Breath marks carry no lyrics,
                // so adjustExtendsForDeletion is a harmless no-op for it.
                for (var i = begin; i <= rangeEnd; i++) {
                    line.adjustExtendsForDeletion(i);
                }

                line.removeRange(begin, rangeEnd);
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
     * Opens a modification bracket for {@code body}, naming the undo step {@code label} when it is
     * non-null and letting the pending op-name stand otherwise. Bridges the {@code @Nullable} label
     * that {@link #deleteElementRange} threads to {@link Line}'s non-null labeled overload.
     */
    private static void withModification(Line line, @Nullable String label, Runnable body) {
        if (label != null) {
            line.withModification(label, body);
        } else {
            line.withModification(body);
        }
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
        var widenedDeleteRange =
            (deleteRange != null && line.isHostOfPairedGraceNote(deleteRange.begin()))
                ? new InsertionSpacingCalculator.DeletedRange(deleteRange.begin() - 1, deleteRange.end())
                : null;
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
                    && line.hasEndingInvalidatedByDeletion(
                        line.getElements(deleteRange.begin(), deleteRange.end()));

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
            line, insertIndex, deleteRange, instantiated.spans());

        // Drop the destination spans this paste lands inside before anything moves,
        // while their anchor/end indices still resolve against the pre-paste line.
        for (var span : reconciliation.targetSpansToRemove()) {
            line.removeInvalidatedRangeElement(span);
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
        // addPastedRangeElement. Adding a span re-parents only the span, not its
        // anchor/end, and getAnchorElementIndex() resolves through the anchor's
        // own getLine() — a span added while its anchors still carry the source
        // line's back-reference makes addElement's isInvalidatedByInsertion sweep
        // evaluate it against the wrong line, yielding a wrong index or -1. The
        // hairpin merge in addPastedRangeElement reads those same indices, so it
        // would mis-measure what to absorb for exactly the same reason.
        //
        // line.addElement additionally drops endings invalidated by the inserted
        // element types — the ending's own barline/repeat-aware rule, which is more
        // precise than the straddle test PasteSpanReconciliation applies to the
        // other span kinds, so endings are left to it. Its tuplet removal is now
        // redundant (the reconciliation above already removed a straddled tuplet)
        // but harmless: findTupletAt finds nothing.
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

        // A pasted hairpin flush against a same-type hairpin already on the line is
        // merged into it by addPastedRangeElement, the same rule that applies when
        // the user draws one there; every other kind is added verbatim.
        for (var span : reconciliation.fragmentSpans()) {
            line.addPastedRangeElement(span);
        }

        return FragmentInsertOutcome.INSERTED;
    }

    private void handlePaste() {
        var fragment = clipboardManager.getFragment();

        // Empty clipboard — nothing to paste.
        if (fragment == null || fragment.elements().isEmpty()) {
            return;
        }

        var state = selectionCoordinator.getActiveSelection();

        if (state == null || !state.hasElementSelection()) {
            // No selection: enter paste mode to place the fragment by clicking an
            // insertion point. The score already has focus (handlePasteboardOp
            // requires it) and the fragment is already known non-empty above.
            EditModeManager.getPasteModeManager().enter();
            return;
        }

        var line = state.getLine();
        var begin = state.getSelectionBegin();
        var deleteRange = new InsertionSpacingCalculator.DeletedRange(
            begin, line.effectiveDeleteEnd(state.getSelectionEnd()));

        // A paste-replace deletes before it inserts, so it can discard an ending the
        // same way Delete and Cut can — confirm on the same terms. Declining leaves
        // the score, the selection, and the clipboard untouched.
        if (!confirmEndingInvalidatedByDeletion(
                line, line.getElements(deleteRange.begin(), deleteRange.end()))) {
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
            selectionCoordinator.clearSavedActionStates();
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
        var state = selectionCoordinator.getActiveSelection();

        if (state != null) {
            state.selectAll();
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
