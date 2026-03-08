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

package songscribe.ui.message;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.music.Composition;
import songscribe.music.Line;
import songscribe.music.LyricsProcessor;
import songscribe.music.MusicEditOperations;
import songscribe.music.StaffElement;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.action.InsertLineAction;
import songscribe.ui.adjustment.HorizontalAdjustment;
import songscribe.ui.adjustment.LyricsAdjustment;
import songscribe.ui.adjustment.VerticalAdjustment;
import songscribe.ui.clipboard.ClipboardManager;
import songscribe.ui.component.IMainFrame;
import songscribe.ui.component.score.MainPanel;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.menu.DebugState;
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

    /**
     * Callback interface for ScoreMessageCoordinator to communicate with Score.
     */
    public interface Callback {
        void repaint();

        void clearSelection();

        void selectionChanged();

        void setMode(Mode mode);

        void setControl(Control control);

        void setInsertionElement(StaffElement insertionNote);

        Composition getComposition();

        void setComposition(Composition composition);

        boolean requestFocusInWindow();

        boolean isFocusOwner();

        MainPanel getMainPanel();

        IMainFrame getMainFrame();

        HorizontalAdjustment getHorizontalAdjustment();

        VerticalAdjustment getVerticalAdjustment();

        LyricsAdjustment getLyricsAdjustment();

        Control getControl();

        boolean canDeleteLine();

        void setInSelectMode(boolean inSelectMode);
    }

    private final Callback callback;
    private MusicEditOperations operations;
    private final EditModeManager editModeManager;
    private final SelectionCoordinator selectionCoordinator;
    private final ClipboardManager clipboardManager;

    // Timer for debouncing repaints when layout changes occur
    private Timer repaintDebounceTimer = null;

    public ScoreMessageCoordinator(
        @NotNull Callback callback,
        @NotNull MusicEditOperations operations,
        @NotNull EditModeManager editModeManager,
        @NotNull SelectionCoordinator selectionCoordinator,
        @NotNull ClipboardManager clipboardManager
    ) {
        this.callback = callback;
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
        callback.setInsertionElement(editModeManager.makeInsertionElement(message.getNoteType()));
    }

    @Handler
    public void restModeDidChange(RestModeChangedMessage message) {
        callback.setInsertionElement(editModeManager.makeInsertionElement());
    }

    @Handler
    public void onUpdateInsertionElement(UpdateInsertionElementMessage message) {
        updateInsertionElement();
    }

    private void updateInsertionElement() {
        var insertionElement = editModeManager.getInsertionElement();

        if (insertionElement != null) {
            editModeManager.decorateElement(insertionElement);
            callback.repaint();
        } else {
            callback.setInsertionElement(editModeManager.makeInsertionElement());
        }
    }

    @Handler
    public void onInsertLine(@NotNull InsertLineMessage message) {
        var shift = message.getShift();
        var composition = callback.getComposition();
        var mainFrame = callback.getMainFrame();

        if ((selectionCoordinator.getSelectedLine() != -1) || (shift == InsertLineAction.ADD)) {
            var index = (shift >= 0)
                ? (selectionCoordinator.getSelectedLine() + shift)
                : InsertLineAction.ADD;
            composition.addLine(index, new Line());
            callback.clearSelection();
            mainFrame.setDocumentModified(true);
            callback.repaint();
        } else {
            mainFrame.showErrorMessage(Strings.get(Strings.ERROR_LINE_NO_SELECTION));
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
        MessageCenter.post(LayoutChangeMessage.scoreContent(line));
    }

    @Handler
    public void onToggleTie(ToggleTieMessage message) {
        operations.toggleTie();
        postSelectionContentChanged();
    }

    @Handler
    public void onToggleTuplet(@NotNull ToggleTupletMessage message) {
        operations.toggleTuplet(message.getTupletSize());
        callback.selectionChanged();
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
        MessageCenter.post(LayoutChangeMessage.scoreContent());
    }

    @Handler
    public void onFlipStemDirection(FlipStemDirectionMessage message) {
        operations.flipStemDirection();
        postSelectionContentChanged();
    }

    private void postSelectionContentChanged() {
        var state = selectionCoordinator.getActiveSelection();
        MessageCenter.post(LayoutChangeMessage.scoreContent(state != null ? state.getLine() : null));
    }

    @Handler
    public void onLayoutChanged(@NotNull LayoutChangeMessage message) {
        var mainPanel = callback.getMainPanel();

        // Invalidate layout for affected lines when content changes
        if (message.getChangeType() == LayoutChangeMessage.ChangeType.CONTENT) {
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

        // Clear inspector hover immediately when layout changes to avoid stale bounds
        if (DebugState.isInspectorEnabled() && DebugState.getHoveredElement() != null) {
            DebugState.setHoveredElement(null);
            // Force immediate repaint to clear stale visualization
            callback.repaint();
            return;
        }

        // Debounce repaints to batch multiple rapid changes
        if (repaintDebounceTimer == null) {
            repaintDebounceTimer = new Timer(REPAINT_DEBOUNCE_DELAY_MS, e -> callback.repaint());
            repaintDebounceTimer.setRepeats(false);
        }

        repaintDebounceTimer.restart();
    }

    @Handler
    public void controlDidChange(@NotNull ControlChangedMessage message) {
        callback.setControl(message.getControl());
    }

    @Handler(priority = Message.HIGH_PRIORITY)
    public void modeDidChange(@NotNull ModeChangedMessage message) {
        var mode = message.getMode();
        callback.setMode(mode);
        callback.setInSelectMode(mode == Mode.SELECT);

        if (mode != Mode.SELECT) {
            callback.clearSelection();
        }

        callback.getHorizontalAdjustment().setEnabled(mode == Mode.ADJUSTMENT);
        callback.getVerticalAdjustment().setEnabled(mode == Mode.VERTICAL_ADJUSTMENT);
        callback.getLyricsAdjustment().setEnabled(mode == Mode.LYRICS_ADJUSTMENT);
        callback.repaint();
    }

    @Handler
    public void playbackStateDidChange(
        @NotNull PlaybackStateChangedMessage message
    ) {
        if (message.getState() == PlaybackController.PlaybackState.STOPPED) {
            if (MidiController.sequencer != null) {
                MidiController.sequencer.setTickPosition(0);
            }
            callback.repaint();
        }
    }

    @Handler
    public void onPasteboardOp(PasteboardOpMessage message) {
        // Make sure this component has focus
        if (!callback.isFocusOwner()) {
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
        var composition = callback.getComposition();
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
        } else if (callback.canDeleteLine()) {
            composition.removeLine(selectionCoordinator.getSelectedLine());
            LyricsProcessor.spellLyrics(composition);
        }

        callback.clearSelection();
        callback.repaint();
    }

    private void handlePaste() {
        if (!clipboardManager.isEmpty()) {
            editModeManager.setPrevPasteControl(callback.getControl());
            callback.setInsertionElement(StaffElement.PASTE_PLACEHOLDER);
            callback.setControl(Control.MOUSE);
            selectionCoordinator.setInSelectMode(false);
            callback.repaint();
        }
    }

    @Handler
    public void onDeselect(DeselectMessage message) {
        if (callback.isFocusOwner()) {
            callback.clearSelection();
            callback.repaint();
        }
    }

    @Handler
    public void onSelectLine(SelectLineMessage message) {
        var state = selectionCoordinator.getActiveSelection();

        if (state != null) {
            state.selectAll();
            callback.selectionChanged();
            callback.repaint();
        }
    }

    @SuppressWarnings("ObjectEquality")
    private static void deleteNote(int xIndex, @NotNull Line line) {
        if (xIndex < (line.elementCount() - 1)) {
            var shift =
                line.getElement(xIndex).getXPosSs() -
                    line.getElement(xIndex + 1).getXPosSs();

            for (var i = xIndex + 1; i < line.elementCount(); i++) {
                line.getElement(i).setXPosSs(line.getElement(i).getXPosSs() + shift);
            }
        }

        // If the previous note has a glissando targeting this note, remove it
        if (xIndex > 0) {
            var prevElement = line.getElement(xIndex - 1);

            if (prevElement.getGlissando() != StaffElement.NO_GLISSANDO) {
                prevElement.removeGlissando();
            }
        }

        line.removeElement(xIndex);
    }
}
