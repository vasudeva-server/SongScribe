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
import songscribe.message.command.ToggleLyricsUnderRestsCommand;
import songscribe.message.command.ToggleTieCommand;
import songscribe.message.command.ToggleTrillCommand;
import songscribe.message.command.ToggleTupletCommand;
import songscribe.message.command.UpdatePreviewElementCommand;
import songscribe.message.mutation.FontChange;
import songscribe.message.mutation.LayoutChange;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.LineScopedMutation;
import songscribe.message.mutation.LyricsChange;
import songscribe.message.mutation.MetadataChange;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.CompositionDidChangeNotification;
import songscribe.message.notification.ControlDidChangeNotification;
import songscribe.message.notification.ElementTypeWasSelectedNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.MusicSelectionDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.message.notification.RestModeDidChangeNotification;
import songscribe.music.Line;
import songscribe.music.LyricsProcessor;
import songscribe.ui.Mode;
import songscribe.ui.MusicEditOperations;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.FirstSecondEndingAction;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.selection.SelectionCoordinator;

/**
 * Coordinates message handling for the Score component.
 * Handles all @Handler methods for messages posted to the MessageCenter.
 */
public final class ScoreMessageCoordinator {

    // Delay in milliseconds for debouncing repaint when layout changes occur
    private static final int REPAINT_DEBOUNCE_DELAY_MS = 300;

    private final Score score;
    private MusicEditOperations operations;
    private final EditModeManager editModeManager;
    private final SelectionCoordinator selectionCoordinator;
    private final ClipboardManager clipboardManager;

    // Timer for debouncing repaints when layout changes occur
    @Nullable
    private Timer repaintDebounceTimer = null;

    public ScoreMessageCoordinator(
        Score score,
        MusicEditOperations operations,
        EditModeManager editModeManager,
        SelectionCoordinator selectionCoordinator,
        ClipboardManager clipboardManager
    ) {
        this.score = score;
        this.operations = operations;
        this.editModeManager = editModeManager;
        this.selectionCoordinator = selectionCoordinator;
        this.clipboardManager = clipboardManager;

        MessageCenter.subscribe(this);
    }

    public void setOperations(MusicEditOperations operations) {
        this.operations = operations;
    }

    @Handler
    public void elementTypeWasSelected(ElementTypeWasSelectedNotification message) {
        score.setPreviewElement(editModeManager.makePreviewElement(message.getNoteType()));
    }

    @Handler
    public void restModeDidChange(RestModeDidChangeNotification message) {
        score.setPreviewElement(editModeManager.makePreviewElement());
    }

    @Handler
    public void handleUpdatePreviewElement(UpdatePreviewElementCommand message) {
        updatePreviewElement();
    }

    private void syncPreviewElementWithSelectedDuration() {
        var selected = Actions.DURATION_ACTION_GROUP.getSelected();

        if (selected != null) {
            score.setPreviewElement(editModeManager.makePreviewElement(selected.getType()));
        }
    }

    private void updatePreviewElement() {
        var previewElement = editModeManager.getPreviewElement();

        if (previewElement != null) {
            editModeManager.decorateElement(previewElement);
            score.repaint();
        } else {
            score.setPreviewElement(editModeManager.makePreviewElement());
        }
    }

    @Handler
    public void handleInsertLine(InsertLineCommand message) {
        var shift = message.getShift();
        var composition = score.getComposition();

        if ((selectionCoordinator.getSelectedLine() != -1) || (shift == InsertLineAction.ADD)) {
            var index = (shift >= 0)
                ? (selectionCoordinator.getSelectedLine() + shift)
                : InsertLineAction.ADD;
            composition.addLine(index, new Line());
            score.clearSelection();
            score.repaint();
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
        var result = FirstSecondEndingAction.MAKE_ENDING_ACTION.getCachedResult();

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
    public void handleToggleLyricsUnderRests(
        ToggleLyricsUnderRestsCommand message
    ) {
        operations.toggleLyricsUnderRests();
    }

    @Handler
    public void handleFlipStemDirection(FlipStemDirectionCommand message) {
        operations.flipStemDirection();
    }

    @Handler
    public void compositionDidChange(CompositionDidChangeNotification message) {
        var mainPanel = score.getMainPanel();

        if (mainPanel == null) {
            return;
        }

        if (hasLineLayoutMutation(message)) {
            var staffPanel = mainPanel.getStaffPanel();

            if (staffPanel != null) {
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
        }

        // Font, metadata, and layout changes require a full relayout.
        if (hasFullRelayoutMutation(message)) {
            score.viewChanged();
        }

        // Debounce repaints to batch multiple rapid changes
        if (repaintDebounceTimer == null) {
            repaintDebounceTimer = new Timer(REPAINT_DEBOUNCE_DELAY_MS, e -> score.repaint());
            repaintDebounceTimer.setRepeats(false);
        }

        repaintDebounceTimer.restart();
    }

    /**
     * Returns whether the notification carries any mutation that requires line layout
     * invalidation: line-scoped element changes, line insert/delete, or lyrics changes.
     */
    private static boolean hasLineLayoutMutation(CompositionDidChangeNotification message) {
        for (Mutation mutation : message.getMutations()) {
            if (mutation instanceof LineScopedMutation
                || mutation instanceof LineInsertion
                || mutation instanceof LineDeletion
                || mutation instanceof LyricsChange) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns whether the notification carries any mutation that requires a full
     * composition relayout (font / metadata / layout property changes).
     */
    private static boolean hasFullRelayoutMutation(CompositionDidChangeNotification message) {
        for (Mutation mutation : message.getMutations()) {
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
        var la = score.getLyricsAdjustment();

        if (ha != null) {
            ha.setEnabled(mode == Mode.ADJUSTMENT);
        }

        if (va != null) {
            va.setEnabled(mode == Mode.VERTICAL_ADJUSTMENT);
        }

        if (la != null) {
            la.setEnabled(mode == Mode.LYRICS_ADJUSTMENT);
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
        score.getComposition().withModification(this::handleDelete);
    }

    private void handleCopy() {
        var state = selectionCoordinator.getActiveSelection();

        if (state != null && state.hasElementSelection()) {
            var line = state.getLine();
            clipboardManager.clear();

            for (var i = state.getSelectionBegin(); i <= state.getSelectionEnd(); i++) {
                clipboardManager.addElement(line.getElement(i).clone());
            }

            clipboardManager.setIntervalsCopyBuffer(line.copyIntervals(
                state.getSelectionBegin(),
                state.getSelectionEnd()
            ));
        }
    }

    private void handleDelete() {
        var composition = score.getComposition();
        var state = selectionCoordinator.getActiveSelection();

        if (state != null && state.hasElementSelection()) {
            var line = state.getLine();
            var begin = state.getSelectionBegin();
            var end = state.getSelectionEnd();

            // When the element immediately before the selection is a paired grace note,
            // deleteNote must remove it along with the first selected note — a non-contiguous
            // operation that cannot be expressed as a single range. Fall back to the per-element loop.
            if (begin > 0 && line.isPairedGraceNote(begin - 1)) {
                composition.withModification(() -> deleteSelection(begin, end, line));
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
                if (end < line.elementCount() - 1) {
                    var shift = line.getElement(begin).getXPosSs() - line.getElement(end + 1).getXPosSs();

                    for (var i = end + 1; i < line.elementCount(); i++) {
                        line.getElement(i).setXPosSs(line.getElement(i).getXPosSs() + shift);
                    }
                }

                composition.withModification(() -> line.removeRange(begin, end));
            }

            LyricsProcessor.spellLyrics(line);
        } else if (state != null && state.hasGlissandoSelection()) {
            var line = state.getLine();
            line.getElement(state.getSelectedGlissandoElementIndex()).removeGlissando();
        } else if (score.canDeleteLine()) {
            composition.removeLine(selectionCoordinator.getSelectedLine());
            LyricsProcessor.spellLyrics(composition);
        }

        // Discard saved action states — the composition has changed, so restoring
        // pre-selection states would be stale. Individual action handlers will
        // re-evaluate their enabled state from the current context.
        selectionCoordinator.clearSavedActionStates();
        score.clearSelection();
        score.repaint();
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
            score.clearSelection();
            score.repaint();
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
        var hasPrecedingPairedGraceNote = xIndex > 0 && line.isPairedGraceNote(xIndex - 1);

        // Determine the left edge of the deletion — if a paired grace note precedes
        // the deleted note, it is also being removed, so the gap starts there.
        var firstDeletedIndex = hasPrecedingPairedGraceNote ? xIndex - 1 : xIndex;

        if (xIndex < (line.elementCount() - 1)) {
            var shift =
                line.getElement(firstDeletedIndex).getXPosSs() -
                    line.getElement(xIndex + 1).getXPosSs();

            for (var i = xIndex + 1; i < line.elementCount(); i++) {
                line.getElement(i).setXPosSs(line.getElement(i).getXPosSs() + shift);
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
