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
import songscribe.message.command.DeselectCommand;
import songscribe.message.command.FirstSecondEndingCommand;
import songscribe.message.command.FlipStemDirectionCommand;
import songscribe.message.command.InsertLineCommand;
import songscribe.message.command.PasteboardOpCommand;
import songscribe.message.command.RemoveDynamicsCommand;
import songscribe.message.command.SelectLineCommand;
import songscribe.message.command.ToggleBeamCommand;
import songscribe.message.command.ToggleTieCommand;
import songscribe.message.command.ToggleTrillCommand;
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
import songscribe.message.notification.ControlDidChangeNotification;
import songscribe.message.notification.ElementTypeWasSelectedNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.message.notification.PrefsDidChangeNotification;
import songscribe.message.notification.RestModeDidChangeNotification;
import songscribe.message.notification.TextEditingDidChangeNotification;
import songscribe.prefs.PrefsKey;
import songscribe.dom.ScaleContext;
import songscribe.dom.Line;
import songscribe.dom.Lyric;
import songscribe.ui.EndingConfirms;
import songscribe.ui.Mode;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.ScoreActions;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.SelectionCoordinator;
import songscribe.dom.EndingValidationResult;
import songscribe.ui.selection.TupletToggleInfo;

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

    private void syncPreviewElementWithSelectedDuration() {
        var selected = Actions.DURATION_ACTION_GROUP.getSelected();

        if (selected != null) {
            score.setPreviewElement(EditModeManager.makePreviewElement(selected.getType()));
        }
    }

    private void updatePreviewElement() {
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement != null) {
            EditModeManager.decorateElement(previewElement);
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
    public void handleToggleTrill(ToggleTrillCommand message) {
        operations.toggleTrill();
    }

    @Handler
    public void handleFlipStemDirection(FlipStemDirectionCommand message) {
        operations.flipStemDirection();
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

    public boolean canRemoveDynamicsFromSelection() {
        return operations.canRemoveDynamicsFromSelection();
    }

    public EndingValidationResult canMakeFirstSecondEnding() {
        return operations.canMakeFirstSecondEnding();
    }

    public boolean canChangeTempo() {
        return operations.canChangeTempo();
    }

    public boolean canToggleTrill() {
        return operations.canToggleTrill();
    }

    public boolean canFlipStemDirection() {
        return operations.canFlipStemDirection();
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

        // Font, metadata, and layout changes (e.g. a Song Settings commit) all
        // require re-laying out every line, not just repainting: invalidating each
        // line clears its cached LayoutResult so positions are recomputed.
        if (hasFullRelayoutMutation(message)) {
            for (var linePanel : mainPanel.getStaffPanel().getLinePanels()) {
                linePanel.getLineComponent().invalidateLayout();
            }
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

    @Handler
    public void controlDidChange(ControlDidChangeNotification message) {
        score.setControl(message.getControl());
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
        handleCopy();
        score.getSong().withModification(this::handleDelete);
    }

    void handleCopy() {
        var state = selectionCoordinator.getActiveSelection();

        if (state != null && state.hasElementSelection()) {
            var line = state.getLine();
            clipboardManager.clear();

            for (var i = state.getSelectionBegin(); i <= state.getSelectionEnd(); i++) {
                clipboardManager.addElement(line.getElement(i).clone());
            }

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
                song.withModification(() -> {
                    line.modifyElement(index, ElementField.LYRIC, () ->
                        line.getElement(index).setLyricForVerse(verse, null, false, "", Lyric.Extend.NONE));
                    line.adjustNeighborsForLyricDeletion(index, verse);
                });
            }

            selectionCoordinator.clearSavedActionStates();
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

            // When the element immediately before the selection is a paired grace note,
            // deleteNote must remove it along with the first selected note — a non-contiguous
            // operation that cannot be expressed as a single range. Fall back to the per-element loop.
            if (line.isHostOfPairedGraceNote(begin)) {
                song.withModification(() -> deleteSelection(begin, end, line));
            } else {
                // Contiguous range: clean up the element before the range, then batch-remove.
                if (begin > 0) {
                    var prevElement = line.getElement(begin - 1);

                    if (prevElement.getGlissando() != null) {
                        prevElement.removeGlissando();
                    }
                }

                // Shift elements after the selection to fill the gap, mirroring the
                // per-element xPos adjustment that deleteNote performs.
                if (end < line.effectiveElementCount() - 1) {
                    var shift = line.getElement(begin).getXOffsetPx() - line.getElement(end + 1).getXOffsetPx();

                    for (var i = end + 1; i < line.effectiveElementCount(); i++) {
                        line.getElement(i).setXOffsetPx(line.getElement(i).getXOffsetPx() + shift);
                    }
                }

                song.withModification(() -> {
                    // Mirror deleteNote: adjust syllable relations and melisma extends
                    // on neighbors before removing. Both helpers require the target
                    // elements to still be present in the list.
                    line.adjustSyllablesForNeighborChange(begin - 1, line.getElement(begin));

                    for (var i = begin; i <= end; i++) {
                        line.adjustExtendsForDeletion(i);
                    }

                    line.removeRange(begin, end);
                });
            }
        } else if (state != null && state.hasGlissandoSelection()) {
            var line = state.getLine();
            line.getElement(state.getSelectedGlissandoElementIndex()).removeGlissando();
        } else if (score.canDeleteLine()) {
            song.removeLine(selectionCoordinator.getSelectedLine());
        }

        // Discard saved action states — the song has changed, so restoring
        // pre-selection states would be stale. Individual action handlers will
        // re-evaluate their enabled state from the current context.
        selectionCoordinator.clearSavedActionStates();
        score.deselect();
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

    private void handlePaste() {
        // TODO: Implement paste with proper insertion-point visual feedback
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
     * paired grace note, removes that as well.
     *
     * @return the number of elements removed (1 or 2)
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

            if (prevElement.getGlissando() != null) {
                prevElement.removeGlissando();
            }
        }

        // Adjust syllable relations and melisma extends before removing —
        // both methods require the element at xIndex to still be in the list.
        line.adjustSyllablesForNeighborChange(firstDeletedIndex - 1, line.getElement(xIndex));
        line.adjustExtendsForDeletion(xIndex);

        // Remove the host note first (higher index), then the orphaned grace note.
        // Removing the higher index first keeps xIndex - 1 valid.
        line.removeElement(xIndex);

        if (hasPrecedingPairedGraceNote) {
            line.removeElement(xIndex - 1);
            return 2;
        }

        return 1;
    }
}
