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

import net.engio.mbassy.listener.Handler;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.message.Message;
import songscribe.message.MessageCenter;
import songscribe.message.command.AddDynamicsCommand;
import songscribe.message.command.AutoStemDirectionCommand;
import songscribe.message.command.DeselectCommand;
import songscribe.message.command.FirstSecondEndingCommand;
import songscribe.message.command.FlipStemDirectionCommand;
import songscribe.message.command.InsertLineCommand;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.message.command.RemoveDynamicsCommand;
import songscribe.message.command.SelectLineCommand;
import songscribe.message.command.ToggleBeamCommand;
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
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.layout.Ending;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.ui.EndingConfirms;
import songscribe.ui.Mode;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.clipboard.Fragment;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.PasteModeManager;
import songscribe.ui.edit.ScoreActions;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.dom.EndingValidationResult;
import songscribe.ui.selection.TupletToggleInfo;
import songscribe.undo.OpNames;

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

    // Timer for debouncing repaints when layout changes occur
    final Timer repaintDebounceTimer;

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

        repaintDebounceTimer = new Timer(REPAINT_DEBOUNCE_DELAY_MS, e -> score.repaint());
        repaintDebounceTimer.setRepeats(false);

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
    public void handleToggleTie(ToggleTieCommand message) {
        operations.toggleTie();
    }

    @Handler
    public void handleToggleTuplet(ToggleTupletCommand message) {
        operations.toggleTuplet(message.getTupletSize(), operations.canToggleTuplet());
        score.selectionChanged();
    }

    @Handler
    public void handleAddDynamics(AddDynamicsCommand message) {
        operations.addDynamicsToSelection(message.isCrescendo());
    }

    @Handler
    public void handleRemoveDynamics(RemoveDynamicsCommand message) {
        operations.removeDynamicsFromSelection();
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

    public boolean canAddDynamicsToSelection() {
        return operations.canAddDynamicsToSelection();
    }

    public boolean canRemoveDynamicsFromSelection() {
        return operations.canRemoveDynamicsFromSelection();
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
            scoreActions.updatePageLayout(
                ScaleContext.ssToRoundedPx(score.getSong().getLineWidthSs())
            );
        }
    }

    @Handler
    public void textEditingDidChange(TextEditingDidChangeNotification message) {
        scoreActions.setKeyBindingsEnabled(!message.isEditing());
    }

    @Handler(priority = TUPLET_INFO_CACHE_PRIORITY)
    public void songDidChange(SongDidChangeNotification message) {
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
        repaintDebounceTimer.restart();
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
        score.setInSelectMode(mode == Mode.SELECT);

        if (mode != Mode.SELECT) {
            score.clearSelection();
        }

        // When entering edit mode, sync the preview element with the currently
        // selected duration button. Reflection may have changed the selected button
        // while in select mode without posting a DurationSelectedMessage.
        if (mode == Mode.EDIT) {
            syncPreviewElementWithSelectedDuration();
        }

        var ha = score.getHorizontalAdjustment();
        var va = score.getVerticalAdjustment();

        if (ha != null) {
            ha.setEnabled(mode == Mode.ADJUSTMENT);
        }

        if (va != null) {
            va.setEnabled(mode == Mode.VERTICAL_ADJUSTMENT);
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
        if (line.hasEndingInvalidatedByDeletion(line.getElements(begin, end))) {
            if (!EndingConfirms.confirmInvalidation(score)) {
                return;
            }
        }

        handleCopy();

        // Clear the selection before removing elements so that action handlers
        // reacting to SongDidChangeNotification (posted synchronously when the
        // modification bracket closes) don't query selection indices that no
        // longer exist on the shrunk line.
        selectionCoordinator.clearSelection();

        // One bracket for the deletion — the confirm above already ran, so
        // deleteElementRange performs no further confirmation. The Cut action's op-name
        // (Tier A) names this outermost step, so the inner range delete passes no label.
        score.getSong().withModification(() -> deleteElementRange(line, begin, end, null));

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

        if (state != null && state.hasElementSelection()) {
            var line = state.getLine();
            var begin = state.getSelectionBegin();
            var end = state.getSelectionEnd();

            if (line.hasEndingInvalidatedByDeletion(line.getElements(begin, end))) {
                if (!EndingConfirms.confirmInvalidation(score)) {
                    return;
                }
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

            deleteElementRange(line, begin, end, deleteLabel);
        } else if (state != null && state.hasSlideSelection()) {
            var line = state.getLine();
            var elementIndex = state.getSelectedSlideElementIndex();
            var slideElement = line.getElement(elementIndex);
            var slide = slideElement.getSlide();

            // hasSlideSelection() guarantees the element carries a slide; guard anyway
            // so the @Nullable getSlide() result is not passed on unchecked.
            if (slide != null) {
                line.withModification(OpNames.deleteSlideLabel(slide), () -> line.modifyElement(
                    elementIndex, ElementField.SLIDE, slideElement::removeSlide));
            }
        } else if (state != null && state.hasEndingSelection()) {
            var line = state.getLine();
            var ending = state.getSelectedEnding();

            // hasEndingSelection() guarantees an ending is present; guard anyway
            // so the @Nullable getSelectedEnding() result is not passed on unchecked.
            if (ending != null) {
                line.withModification(OpNames.deleteEndingLabel(), () -> line.removeRangeElement(ending));
            }
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
     * A breath mark immediately after {@code end} is positionally attached to the
     * last selected element, so it must be included in a deletion or copy range that
     * ends at {@code end}. Returns {@code end} extended past that trailing breath
     * mark, or {@code end} unchanged if there is none. Pure query — mutates nothing.
     */
    public static int effectiveDeleteEnd(Line line, int begin, int end) {
        if (end + 1 < line.effectiveElementCount() && line.getElement(end + 1).getType().isBreathMark()) {
            return end + 1;
        }

        return end;
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
    private void deleteElementRange(Line line, int begin, int end, @Nullable String label) {
        // When the element immediately before the selection is a paired grace note,
        // deleteNote must remove it along with the first selected note — a non-contiguous
        // operation that cannot be expressed as a single range. Fall back to the per-element loop.
        if (line.isHostOfPairedGraceNote(begin)) {
            withModification(line, label, () -> deleteSelection(begin, end, line));
        } else {
            var rangeEnd = effectiveDeleteEnd(line, begin, end);

            // Contiguous range: clean up the element before the range, then batch-remove.
            if (begin > 0) {
                var prevElement = line.getElement(begin - 1);

                if (prevElement.hasGlissando()) {
                    prevElement.removeSlide();
                }
            }

            // Shift elements after the selection to fill the gap, mirroring the
            // per-element xPos adjustment that deleteNote performs.
            if (rangeEnd < line.effectiveElementCount() - 1) {
                var shift = line.getElement(begin).getXOffsetPx() - line.getElement(rangeEnd + 1).getXOffsetPx();

                for (var i = rangeEnd + 1; i < line.effectiveElementCount(); i++) {
                    line.getElement(i).setXOffsetPx(line.getElement(i).getXOffsetPx() + shift);
                }
            }

            withModification(line, label, () -> {
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
        EMPTY
    }

    /**
     * Inserts the clipboard fragment into {@code line} at {@code insertIndex},
     * first deleting {@code deleteRange} when present (paste-replace). The fit
     * check runs against the pre-delete line, so on {@code LINE_FULL} nothing has
     * been mutated: the "line full" error is shown, a caller-opened bracket stays
     * empty, and no notification is posted. Callers decide recovery.
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

        var result = InsertionSpacingCalculator.calculateFragmentInsertion(
            line, fragment.elements(), insertIndex, deleteRange, null, score.getLyricRenderMetrics());

        if (!result.fitsWithinLine(line.getSong().getLineWidthSs())) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_LINE_FULL_PASTE
            );
            return FragmentInsertOutcome.LINE_FULL;
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
            deleteElementRange(line, deleteRange.begin(), deleteRange.end(), null);

            // The deletion may have removed elements before the range too (a paired
            // grace note cascade), so re-derive the insertion index from what survived.
            insertAt = successor != null ? line.getElementIndex(successor) : line.effectiveElementCount();
        }

        // Fresh clones every paste — the stored fragment is never itself inserted.
        var instantiated = fragment.instantiate();
        var clones = instantiated.elements();
        var cloneCount = clones.size();

        // Repair the lyric seams around the insertion point, mirroring the
        // single-note insert path in PreviewElementManager.
        line.adjustSyllablesForNeighborChange(insertAt - 1, null);
        line.adjustExtendsForInsertion(insertAt);

        // Hard ordering constraint: every clone must be inserted before the first
        // addRangeElement. addRangeElement re-parents only the span, not its
        // anchor/end, and getAnchorElementIndex() resolves through the anchor's
        // own getLine() — a span added while its anchors still carry the source
        // line's back-reference makes addElement's isInvalidatedByInsertion sweep
        // evaluate it against the wrong line, yielding a wrong index or -1.
        //
        // Accepted loss: line.addElement removes a destination tuplet the insert
        // point falls inside and drops endings invalidated by the inserted element
        // types, so a paste into a tuplet destroys that tuplet. It happens inside
        // the paste's own undo bracket, so a single undo restores it — the same
        // behavior as any single-element insert, deliberately not special-cased.
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

        for (var span : instantiated.spans()) {
            line.addRangeElement(span);
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
            begin, effectiveDeleteEnd(line, begin, state.getSelectionEnd()));

        // One bracket for the whole replace — delete + insert is a single undo
        // step. On LINE_FULL tryInsertFragment mutates nothing, so the bracket
        // closes empty, posts no notification, and the selection stays intact.
        var outcome = new FragmentInsertOutcome[1];

        score.getSong().withModification(() -> {
            outcome[0] = tryInsertFragment(line, begin, deleteRange);

            if (outcome[0] == FragmentInsertOutcome.INSERTED) {
                // Clear the selection before the bracket closes so action handlers
                // reacting to SongDidChangeNotification don't query selection
                // indices that no longer exist on the reshaped line.
                selectionCoordinator.clearSelection();
            }
        });

        if (outcome[0] == FragmentInsertOutcome.INSERTED) {
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

    @Handler
    public void handleSelectLine(SelectLineCommand message) {
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
                prevElement.removeSlide();
            }
        }

        // Adjust syllable relations and melisma extends before removing —
        // both methods require the element at xIndex to still be in the list.
        line.adjustSyllablesForNeighborChange(firstDeletedIndex - 1, line.getElement(xIndex));
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
