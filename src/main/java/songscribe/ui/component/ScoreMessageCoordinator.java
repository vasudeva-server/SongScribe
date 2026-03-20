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
import songscribe.message.command.UpdateInsertionElementCommand;
import songscribe.message.notification.CompositionDidChangeNotification;
import songscribe.message.notification.ControlDidChangeNotification;
import songscribe.message.notification.ElementTypeWasSelectedNotification;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.message.notification.RestModeDidChangeNotification;
import songscribe.music.Line;
import songscribe.music.LyricsProcessor;
import songscribe.music.MusicEditOperations;
import songscribe.music.StaffElement;
import songscribe.ui.Control;
import songscribe.ui.Dialogs;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
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

        // CRITICAL: Subscribe to message center
        MessageCenter.subscribe(this);
    }

    /**
     * Updates the operations reference when a new composition is set.
     * This is necessary because operations is recreated for each composition.
     */
    public void setOperations(MusicEditOperations operations) {
        this.operations = operations;
    }

    @Handler
    public void elementTypeWasSelected(ElementTypeWasSelectedNotification message) {
        score.setInsertionElement(editModeManager.makeInsertionElement(message.getNoteType()));
    }

    @Handler
    public void restModeDidChange(RestModeDidChangeNotification message) {
        score.setInsertionElement(editModeManager.makeInsertionElement());
    }

    @Handler
    public void handleUpdateInsertionElement(UpdateInsertionElementCommand message) {
        updateInsertionElement();
    }

    private void syncInsertionElementWithSelectedDuration() {
        var selected = Actions.DURATION_ACTION_GROUP.getSelected();

        if (selected != null) {
            score.setInsertionElement(editModeManager.makeInsertionElement(selected.getType()));
        }
    }

    private void updateInsertionElement() {
        var insertionElement = editModeManager.getInsertionElement();

        if (insertionElement != null) {
            editModeManager.decorateElement(insertionElement);
            score.repaint();
        } else {
            score.setInsertionElement(editModeManager.makeInsertionElement());
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
            Dialogs.showErrorMessage(
                null,
                Strings.get(Strings.DIALOG_TITLE_LINE_ERROR),
                Strings.get(Strings.ERROR_LINE_NO_SELECTION)
            );
        }
    }

    @Handler
    public void handleToggleBeam(ToggleBeamCommand message) {
        // Capture line before the operation in case selection changes.
        // Invalidate layout so LayoutEngine recomputes BeamLayout for the new/removed interval.
        // Without this, BeamGroupRenderer draws beams with null BeamLayout (no thickening or slope).
        var selection = selectionCoordinator.getActiveSelection();
        var line = (selection != null) ? selection.getLine() : null;
        operations.toggleBeaming();
        MessageCenter.post(new CompositionDidChangeNotification(CompositionDidChangeNotification.ChangeType.CONTENT, score.getComposition(), line));
    }

    @Handler
    public void handleToggleTie(ToggleTieCommand message) {
        operations.toggleTie();
        postSelectionContentChanged();
    }

    @Handler
    public void handleToggleTuplet(ToggleTupletCommand message) {
        operations.toggleTuplet(message.getTupletSize());
        score.selectionChanged();
        postSelectionContentChanged();
    }

    @Handler
    public void handleAddDynamics(AddDynamicsCommand message) {
        operations.addDynamicsToSelection(message.isCrescendo());
        postSelectionContentChanged();
    }

    @Handler
    public void handleRemoveDynamics(RemoveDynamicsCommand message) {
        operations.removeDynamicsFromSelection();
        postSelectionContentChanged();
    }

    @Handler
    public void handleFirstSecondEnding(FirstSecondEndingCommand message) {
        if (message.isMakeEnding()) {
            operations.makeFirstSecondEnding();
        } else {
            operations.removeFirstSecondEnding();
        }

        postSelectionContentChanged();
    }

    @Handler
    public void handleToggleTrill(ToggleTrillCommand message) {
        operations.toggleTrill();
        postSelectionContentChanged();
    }

    @Handler
    public void handleToggleLyricsUnderRests(
        ToggleLyricsUnderRestsCommand message
    ) {
        operations.toggleLyricsUnderRests();
        MessageCenter.post(new CompositionDidChangeNotification(CompositionDidChangeNotification.ChangeType.CONTENT, score.getComposition()));
    }

    @Handler
    public void handleFlipStemDirection(FlipStemDirectionCommand message) {
        operations.flipStemDirection();
        postSelectionContentChanged();
    }

    private void postSelectionContentChanged() {
        var state = selectionCoordinator.getActiveSelection();
        MessageCenter.post(new CompositionDidChangeNotification(
            CompositionDidChangeNotification.ChangeType.CONTENT,
            score.getComposition(),
            state != null ? state.getLine() : null
        ));
    }

    @Handler
    public void compositionDidChange(CompositionDidChangeNotification message) {
        var mainPanel = score.getMainPanel();

        if (mainPanel == null) {
            return;
        }

        // Invalidate layout for affected lines on content or structure changes
        if (message.hasChangeType(CompositionDidChangeNotification.ChangeType.CONTENT)
            || message.hasChangeType(CompositionDidChangeNotification.ChangeType.STRUCTURE)
            || message.hasChangeType(CompositionDidChangeNotification.ChangeType.FULL)) {
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

        // Font, metadata, and layout changes require a full relayout
        if (message.hasChangeType(CompositionDidChangeNotification.ChangeType.FONT)
            || message.hasChangeType(CompositionDidChangeNotification.ChangeType.METADATA)
            || message.hasChangeType(CompositionDidChangeNotification.ChangeType.LAYOUT)) {
            score.viewChanged();
        }

        // Debounce repaints to batch multiple rapid changes
        if (repaintDebounceTimer == null) {
            repaintDebounceTimer = new Timer(REPAINT_DEBOUNCE_DELAY_MS, e -> score.repaint());
            repaintDebounceTimer.setRepeats(false);
        }

        repaintDebounceTimer.restart();
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

        // When entering edit mode, sync the insertion element with the currently
        // selected duration button. Reflection may have changed the selected button
        // while in select mode without posting a DurationSelectedMessage.
        if (mode == Mode.EDIT) {
            syncInsertionElementWithSelectedDuration();
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
        handleDelete();
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

            for (var i = state.getSelectionEnd(); i >= state.getSelectionBegin(); i--) {
                deleteNote(i, line);
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

    private void handlePaste() {
        if (!clipboardManager.isEmpty()) {
            editModeManager.setPrevPasteControl(score.getControl());
            score.setInsertionElement(StaffElement.PASTE_PLACEHOLDER);
            score.setControl(Control.MOUSE);
            selectionCoordinator.setInSelectMode(false);
            score.repaint();
        }
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

    @SuppressWarnings("ObjectEquality")
    private static void deleteNote(int xIndex, Line line) {
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

            if (prevElement.getGlissando() != StaffElement.NO_GLISSANDO) {
                prevElement.removeGlissando();
            }
        }

        // Remove the host note first (higher index), then the orphaned grace note.
        // Removing the higher index first keeps xIndex - 1 valid.
        line.removeElement(xIndex);

        if (hasPrecedingPairedGraceNote) {
            line.removeElement(xIndex - 1);
        }
    }
}
