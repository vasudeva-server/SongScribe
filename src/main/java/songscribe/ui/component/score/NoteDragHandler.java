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

import java.awt.event.MouseEvent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.data.TieInterval;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.ui.Mode;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout2.ScaleContext;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayNoteThread;

/**
 * Handles press/drag/release for pitch-dragging a note head in NOTE_EDIT mode.
 * <p>
 * One instance per {@link LineComponent}. The owning component delegates its
 * mouse events here before passing them on to other handlers.
 */
class NoteDragHandler {

    private final LineComponent lc;

    private boolean dragActive = false;
    private boolean dragMoved = false;
    private boolean pressHandled = false;
    private int dragNoteIndex = -1;
    private int originalStaffPosition;
    private boolean originalUpper;
    private int lastPlayedStaffPosition;
    private Line dragLine;

    @Nullable
    private TieInterval tieInterval;

    NoteDragHandler(@NotNull LineComponent lc) {
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

    int getDragNoteIndex() {
        return dragNoteIndex;
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

        if (score == null || score.getMode() != Mode.NOTE_EDIT) {
            return false;
        }

        if (e.isAltDown() || MidiController.isPlaying()) {
            return false;
        }

        dragMoved = false;

        var hitIndex = NoteHitTest.hitTestNote(lc, e.getPoint());

        if (hitIndex == -1) {
            return false;
        }

        var line = lc.getLine();
        var note = line.getNote(hitIndex);

        if (!note.getNoteType().isNote()) {
            return false;
        }

        lc.getSelectionHandler().selectAndPlayNote(hitIndex);

        // Save state for possible revert on a press+release without drag
        dragNoteIndex = hitIndex;
        originalStaffPosition = note.getStaffPosition();
        originalUpper = note.isUpper();
        lastPlayedStaffPosition = originalStaffPosition;
        dragLine = line;
        tieInterval = line.getTies().findInterval(hitIndex);

        InsertionNoteManager.clearInsertionNote();

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setInsertionNoteVisible(false);
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
        var newPosition = InsertionNoteManager.calculateStaffPositionFromMouse(mouseYss, lc.getMiddleLineYSs());

        if (newPosition == lastPlayedStaffPosition || !InsertionNoteManager.isValidStaffPosition(newPosition)) {
            return;
        }

        // Send NOTE_OFF for the pitch we were playing
        var oldNote = dragLine.getNote(dragNoteIndex);
        PlayNoteThread.sendNoteOff(oldNote.getPitch());

        // Update the dragged note (and all tied notes if in a tie)
        int rangeStart;
        int rangeEnd;

        if (tieInterval != null) {
            rangeStart = tieInterval.getStart();
            rangeEnd = tieInterval.getEnd();
        } else {
            rangeStart = dragNoteIndex;
            rangeEnd = dragNoteIndex;
        }

        for (var i = rangeStart; i <= rangeEnd; i++) {
            var note = dragLine.getNote(i);
            note.setStaffPosition(newPosition);
            note.setUpper(Score.defaultUpperNote(note));
        }

        // Play NOTE_ON for the new pitch
        var newPitch = dragLine.getNote(dragNoteIndex).getPitch();
        PlayNoteThread.sendNoteOn(newPitch);
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
            new PlayNoteThread(dragLine.getNote(dragNoteIndex).getPitch(), false).start();

            // Clear selection so the note doesn't appear highlighted after drag
            lc.getScore().clearSelection();

            // Remove connected glissandos that became unison after the pitch drag
            removeUnisonConnectedGlissandos(dragLine, dragNoteIndex);

            // Finalize: notify layout and mark composition modified
            MessageCenter.post(LayoutChangeMessage.scoreContent(dragLine));
            lc.getComposition().setModified(true);
            // TODO: push to undo stack when undo system is re-enabled
        } else {
            // No drag — revert the tentative change (there was none, but guard anyway)
            int rangeStart;
            int rangeEnd;

            if (tieInterval != null) {
                rangeStart = tieInterval.getStart();
                rangeEnd = tieInterval.getEnd();
            } else {
                rangeStart = dragNoteIndex;
                rangeEnd = dragNoteIndex;
            }

            for (var i = rangeStart; i <= rangeEnd; i++) {
                var note = dragLine.getNote(i);
                note.setStaffPosition(originalStaffPosition);
                note.setUpper(originalUpper);
            }
        }

        dragActive = false;
        dragNoteIndex = -1;
        dragLine = null;
        tieInterval = null;

        InsertionNoteManager.restoreInsertionNote(lc);
    }

    /**
     * Removes connected glissandos that became unison after a pitch drag.
     * Checks the glissando FROM the dragged note (to the next note) and
     * the glissando TO the dragged note (from the previous note).
     */
    private static void removeUnisonConnectedGlissandos(@NotNull Line line, int noteIndex) {
        var note = line.getNote(noteIndex);

        // Glissando FROM the dragged note to the next note
        if (note.getGlissando().type == Note.Glissando.Type.CONNECTED
                && noteIndex + 1 < line.noteCount()
                && note.getPitch() == line.getNote(noteIndex + 1).getPitch()) {
            note.removeGlissando();
        }

        // Glissando TO the dragged note from the previous note
        if (noteIndex > 0) {
            var prev = line.getNote(noteIndex - 1);

            if (prev.getGlissando().type == Note.Glissando.Type.CONNECTED
                    && prev.getPitch() == note.getPitch()) {
                prev.removeGlissando();
            }
        }
    }
}
