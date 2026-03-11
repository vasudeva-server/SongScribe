/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.ui.component.score;

import java.awt.event.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.Strings;
import songscribe.data.TieInterval;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.Dialogs;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout2.ScaleContext;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;

/**
 * Handles press/drag/release for pitch-dragging a note head in NOTE_EDIT mode.
 * <p>
 * One instance per {@link LineComponent}. The owning component delegates its
 * mouse events here before passing them on to other handlers.
 */
class ElementDragHandler {

    private final LineComponent lc;

    private boolean dragActive = false;
    private boolean dragMoved = false;
    private boolean pressHandled = false;
    private int dragElementIndex = -1;
    private int originalStaffPosition;
    private boolean originalUpper;
    private int lastPlayedStaffPosition;
    private Line dragLine;

    @Nullable
    private TieInterval tieInterval;

    ElementDragHandler(@NotNull LineComponent lc) {
        this.lc = lc;
    }

    // ======================================================================
    // Accessors
    // ======================================================================

    boolean isDragActive() {
        return dragActive;
    }

    boolean wasDragPerformed() {
        return dragMoved;
    }

    /**
     * Returns whether the last press was captured by this handler (i.e. the user
     * pressed on a note head in NOTE_EDIT mode). This is true regardless of
     * whether a drag subsequently occurred.
     */
    boolean wasPressCaptured() {
        return pressHandled;
    }

    int getDragElementIndex() {
        return dragElementIndex;
    }

    @Nullable
    TieInterval getTieInterval() {
        return tieInterval;
    }

    // ======================================================================
    // Mouse event handlers
    // ======================================================================

    /**
     * Handles a mouse press. Returns {@code true} if a pitch-drag was initiated
     * and the event should not be processed further.
     */
    boolean handlePress(@NotNull MouseEvent e) {
        pressHandled = false;

        var score = lc.getScore();

        if (score == null || score.getMode() != Mode.EDIT) {
            return false;
        }

        if (e.isAltDown() || MidiController.isPlaying()) {
            return false;
        }

        dragMoved = false;

        var hitIndex = ElementHitTest.hitTestElement(lc, e.getPoint());

        if (hitIndex == -1) {
            return false;
        }

        var line = lc.getLine();
        var note = line.getElement(hitIndex);

        if (!note.getType().isNote()) {
            return false;
        }

        lc.getSelectionHandler().selectAndPlayElement(hitIndex);

        // Save state for possible revert on a press+release without drag
        dragElementIndex = hitIndex;
        originalStaffPosition = note.getStaffPosition();
        originalUpper = note.isUpper();
        lastPlayedStaffPosition = originalStaffPosition;
        dragLine = line;
        tieInterval = line.getTies().findInterval(hitIndex);

        InsertionElementManager.clearInsertionElement();

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setInsertionElementVisible(false);
        }

        pressHandled = true;
        dragActive = true;
        lc.repaint();
        return true;
    }

    /**
     * Handles mouse drag while a pitch-drag is active.
     */
    void handleDrag(@NotNull MouseEvent e) {
        var mouseYss = ScaleContext.getInstance().fromPixels(e.getY());
        var newPosition = InsertionElementManager.calculateStaffPositionFromMouse(mouseYss, lc.getMiddleLineYSs());

        if (newPosition == lastPlayedStaffPosition || !InsertionElementManager.isValidStaffPosition(newPosition)) {
            return;
        }

        // Send NOTE_OFF for the pitch we were playing
        var oldNote = dragLine.getElement(dragElementIndex);
        PlayThread.sendNoteOff(oldNote.getPitch());

        // Update the dragged note (and all tied notes if in a tie)
        int rangeStart;
        int rangeEnd;

        if (tieInterval != null) {
            rangeStart = tieInterval.getStart();
            rangeEnd = tieInterval.getEnd();
        } else {
            rangeStart = dragElementIndex;
            rangeEnd = dragElementIndex;
        }

        for (var i = rangeStart; i <= rangeEnd; i++) {
            var note = dragLine.getElement(i);
            note.setStaffPosition(newPosition);
            note.setUpper(Score.defaultUpperNote(note));
        }

        // Play NOTE_ON for the new pitch
        var newPitch = dragLine.getElement(dragElementIndex).getPitch();
        PlayThread.sendNoteOn(newPitch);
        lastPlayedStaffPosition = newPosition;

        lc.invalidateLayout();
        lc.repaint();
        dragMoved = true;
    }

    /**
     * Handles mouse release, finalizing or reverting the pitch change.
     */
    void handleRelease() {
        if (dragMoved) {
            // The last drag noteOn is still sounding — schedule a noteOff after the standard duration
            new PlayThread(dragLine.getElement(dragElementIndex).getPitch(), false).start();

            // Clear selection so the note doesn't appear highlighted after drag
            lc.getScore().clearSelection();

            // Remove connected glissandos that became unison after the pitch drag
            removeUnisonConnectedGlissandos(dragLine, dragElementIndex);

            // A grace note dragged to the same pitch as its following note is invalid — remove it
            var draggedElement = dragLine.getElement(dragElementIndex);

            if (draggedElement.getType().isGraceNote()
                && dragElementIndex + 1 < dragLine.elementCount()
                && draggedElement.getPitch() == dragLine.getElement(dragElementIndex + 1).getPitch()) {
                Dialogs.showWarningMessage(
                    null,
                    Strings.get(Strings.DIALOG_TITLE_GRACE_NOTE_WARNING),
                    Strings.get(Strings.WARNING_GRACE_NOTE_SAME_PITCH)
                );
                dragLine.removeElement(dragElementIndex);
            }

            // Finalize: notify layout and mark composition modified
            MessageCenter.post(LayoutChangeMessage.scoreContent(dragLine));
            lc.getComposition().setModified(true);
            // TODO: push to undo stack when undo system is re-enabled
        } else {
            // No drag — click on a note head selects it, switch to select mode
            // (same as alt-click in LineComponent.mousePressed)
            Actions.SELECT_MODE_ACTION.perform(lc);

            // No position change occurred, but guard against floating-point drift
            int rangeStart;
            int rangeEnd;

            if (tieInterval != null) {
                rangeStart = tieInterval.getStart();
                rangeEnd = tieInterval.getEnd();
            } else {
                rangeStart = dragElementIndex;
                rangeEnd = dragElementIndex;
            }

            for (var i = rangeStart; i <= rangeEnd; i++) {
                var note = dragLine.getElement(i);
                note.setStaffPosition(originalStaffPosition);
                note.setUpper(originalUpper);
            }
        }

        dragActive = false;
        dragElementIndex = -1;
        dragLine = null;
        tieInterval = null;

        InsertionElementManager.restoreInsertionElement(lc);
    }

    /**
     * Removes connected glissandos that became unison after a pitch drag.
     * Checks the glissando FROM the dragged note (to the next note) and
     * the glissando TO the dragged note (from the previous note).
     */
    private static void removeUnisonConnectedGlissandos(@NotNull Line line, int elementIndex) {
        var element = line.getElement(elementIndex);

        // Glissando FROM the dragged note to the next note
        if (element.getGlissando().type == StaffElement.Glissando.Type.CONNECTED
            && elementIndex + 1 < line.elementCount()
            && element.getPitch() == line.getElement(elementIndex + 1).getPitch()) {
            element.removeGlissando();
        }

        // Glissando TO the dragged note from the previous note
        if (elementIndex > 0) {
            var prev = line.getElement(elementIndex - 1);

            if (prev.getGlissando().type == StaffElement.Glissando.Type.CONNECTED
                && prev.getPitch() == element.getPitch()) {
                prev.removeGlissando();
            }
        }
    }
}
