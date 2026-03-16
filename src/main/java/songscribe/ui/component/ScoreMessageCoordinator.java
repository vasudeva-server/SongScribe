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

import songscribe.message.CompositionChangedMessage;
import songscribe.message.Message;
import songscribe.message.MessageCenter;

import org.jetbrains.annotations.NotNull;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.ui.Dialogs;
import songscribe.music.Line;
import songscribe.music.LyricsProcessor;
import songscribe.music.MusicEditOperations;
import songscribe.music.StaffElement;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.message.AddDynamicsMessage;
import songscribe.ui.message.ControlChangedMessage;
import songscribe.ui.message.DeselectMessage;
import songscribe.ui.message.ElementTypeSelectedMessage;
import songscribe.ui.message.FirstSecondEndingMessage;
import songscribe.ui.message.FlipStemDirectionMessage;
import songscribe.ui.message.InsertLineMessage;
import songscribe.ui.message.ModeChangedMessage;
import songscribe.ui.message.PasteboardOpMessage;
import songscribe.ui.message.RemoveDynamicsMessage;
import songscribe.ui.message.RestModeChangedMessage;
import songscribe.ui.message.SelectLineMessage;
import songscribe.ui.message.ToggleBeamMessage;
import songscribe.ui.message.ToggleLyricsUnderRestsMessage;
import songscribe.ui.message.ToggleTieMessage;
import songscribe.ui.message.ToggleTrillMessage;
import songscribe.ui.message.ToggleTupletMessage;
import songscribe.ui.message.UpdateInsertionElementMessage;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.playback.PlaybackStateChangedMessage;
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
    private Timer repaintDebounceTimer = null;

    public ScoreMessageCoordinator(
        @NotNull Score score,
        @NotNull MusicEditOperations operations,
        @NotNull EditModeManager editModeManager,
        @NotNull SelectionCoordinator selectionCoordinator,
        @NotNull ClipboardManager clipboardManager
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
    public void setOperations(@NotNull MusicEditOperations operations) {
        this.operations = operations;
    }

    @Handler
    public void noteTypeWasSelected(@NotNull ElementTypeSelectedMessage message) {
        score.setInsertionElement(editModeManager.makeInsertionElement(message.getNoteType()));
    }

    @Handler
    public void restModeDidChange(RestModeChangedMessage message) {
        score.setInsertionElement(editModeManager.makeInsertionElement());
    }

    @Handler
    public void onUpdateInsertionElement(UpdateInsertionElementMessage message) {
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
    public void onInsertLine(@NotNull InsertLineMessage message) {
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
    public void onToggleBeaming(ToggleBeamMessage message) {
        // Capture line before the operation in case selection changes.
        // Invalidate layout so LayoutEngine recomputes BeamLayout for the new/removed interval.
        // Without this, BeamGroupRenderer draws beams with null BeamLayout (no thickening or slope).
        var selection = selectionCoordinator.getActiveSelection();
        var line = (selection != null) ? selection.getLine() : null;
        operations.toggleBeaming();
        MessageCenter.post(new CompositionChangedMessage(CompositionChangedMessage.ChangeType.CONTENT, score.getComposition(), line));
    }

    @Handler
    public void onToggleTie(ToggleTieMessage message) {
        operations.toggleTie();
        postSelectionContentChanged();
    }

    @Handler
    public void onToggleTuplet(@NotNull ToggleTupletMessage message) {
        operations.toggleTuplet(message.getTupletSize());
        score.selectionChanged();
        postSelectionContentChanged();
    }

    @Handler
    public void onAddDynamics(@NotNull AddDynamicsMessage message) {
        operations.addDynamicsToSelection(message.isCrescendo());
        postSelectionContentChanged();
    }

    @Handler
    public void onRemoveDynamics(@NotNull RemoveDynamicsMessage message) {
        operations.removeDynamicsFromSelection();
        postSelectionContentChanged();
    }

    @Handler
    public void onFirstSecondEnding(@NotNull FirstSecondEndingMessage message) {
        if (message.isMakeEnding()) {
            operations.makeFirstSecondEnding();
        } else {
            operations.removeFirstSecondEnding();
        }

        postSelectionContentChanged();
    }

    @Handler
    public void onToggleTrill(ToggleTrillMessage message) {
        operations.toggleTrill();
        postSelectionContentChanged();
    }

    @Handler
    public void onToggleLyricsUnderRests(
        ToggleLyricsUnderRestsMessage message
    ) {
        operations.toggleLyricsUnderRests();
        MessageCenter.post(new CompositionChangedMessage(CompositionChangedMessage.ChangeType.CONTENT, score.getComposition()));
    }

    @Handler
    public void onFlipStemDirection(FlipStemDirectionMessage message) {
        operations.flipStemDirection();
        postSelectionContentChanged();
    }

    private void postSelectionContentChanged() {
        var state = selectionCoordinator.getActiveSelection();
        MessageCenter.post(new CompositionChangedMessage(
            CompositionChangedMessage.ChangeType.CONTENT,
            score.getComposition(),
            state != null ? state.getLine() : null
        ));
    }

    @Handler
    public void onCompositionChanged(@NotNull CompositionChangedMessage message) {
        var mainPanel = score.getMainPanel();

        // Invalidate layout for affected lines on content or structure changes
        if (message.hasChangeType(CompositionChangedMessage.ChangeType.CONTENT)
            || message.hasChangeType(CompositionChangedMessage.ChangeType.STRUCTURE)
            || message.hasChangeType(CompositionChangedMessage.ChangeType.FULL)) {
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
        if (message.hasChangeType(CompositionChangedMessage.ChangeType.FONT)
            || message.hasChangeType(CompositionChangedMessage.ChangeType.METADATA)
            || message.hasChangeType(CompositionChangedMessage.ChangeType.LAYOUT)) {
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
    public void controlDidChange(@NotNull ControlChangedMessage message) {
        score.setControl(message.getControl());
    }

    @Handler(priority = Message.HIGH_PRIORITY)
    public void modeDidChange(@NotNull ModeChangedMessage message) {
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

        score.getHorizontalAdjustment().setEnabled(mode == Mode.ADJUSTMENT);
        score.getVerticalAdjustment().setEnabled(mode == Mode.VERTICAL_ADJUSTMENT);
        score.getLyricsAdjustment().setEnabled(mode == Mode.LYRICS_ADJUSTMENT);
        score.repaint();
    }

    @Handler
    public void playbackStateDidChange(
        @NotNull PlaybackStateChangedMessage message
    ) {
        if (message.getState() == PlaybackController.PlaybackState.STOPPED) {
            if (MidiController.sequencer != null) {
                MidiController.sequencer.setTickPosition(0);
            }
            score.repaint();
        }
    }

    @Handler
    public void onPasteboardOp(PasteboardOpMessage message) {
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
    public void onDeselect(DeselectMessage message) {
        if (score.isFocusOwner()) {
            score.clearSelection();
            score.repaint();
        }
    }

    @Handler
    public void onSelectLine(SelectLineMessage message) {
        var state = selectionCoordinator.getActiveSelection();

        if (state != null) {
            state.selectAll();
            score.selectionChanged();
            score.repaint();
        }
    }

    @SuppressWarnings("ObjectEquality")
    private static void deleteNote(int xIndex, @NotNull Line line) {
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
