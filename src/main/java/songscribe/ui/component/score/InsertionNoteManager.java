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

import java.awt.*;
import java.awt.event.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.data.BeamInterval;
import songscribe.music.Line;
import songscribe.music.NoteType;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout2.InsertionSpacingCalculator;
import songscribe.ui.layout2.ScaleContext;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.ModeChangedMessage;

/**
 * Manages the insertion note subsystem for {@link LineComponent}.
 * <p>
 * This class owns all static cross-instance state for insertion note tracking,
 * cursor management, and note mutation logic. Only one insertion note can be
 * active across all LineComponents at a time.
 */
class InsertionNoteManager {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Number of ledger lines above the staff. */
    private static final int STAFF_LINES_ABOVE = 3;

    /** Number of ledger lines below the staff. */
    private static final int STAFF_LINES_BELOW = 4;

    /** Crosshair cursor for selection mode. */
    private static final Cursor CROSSHAIR_CURSOR = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);

    /** Default cursor. */
    private static final Cursor DEFAULT_CURSOR = Cursor.getDefaultCursor();

    // ==========================================================================
    // Static State
    // ==========================================================================

    /**
     * The LineComponent that currently has the insertion note.
     * <p>
     * Only one line can show the insertion note at a time. When the mouse moves
     * to a different line, the old line is repainted to clear the insertion note.
     */
    @Nullable
    private static LineComponent currentInsertionLine = null;

    /** Current insertion index (0 to noteCount inclusive). */
    private static int currentXIndex = -1;

    /** Current Y position on the staff (in note units, not pixels). */
    private static int currentStaffPosition = 0;

    /** Whether the Alt key is currently held down. */
    private static boolean altPressed = false;

    /** Whether the primary mouse button is currently pressed. */
    private static boolean mouseButtonPressed = false;

    /** Whether the press occurred on a selectable element (e.g., a note or staff line). */
    private static boolean pressedOnSelectableElement = false;

    /** The LineComponent the mouse is currently over (independent of insertion note state). */
    @Nullable
    private static LineComponent currentMouseLine = null;

    /** Whether the current insertion position is directly over an existing note head. */
    private static boolean currentIsOverNoteHead = false;

    /** Strong reference to prevent garbage collection by the weak-reference message bus. */
    private static final ModeChangeListener MODE_CHANGE_LISTENER = new ModeChangeListener();

    // Subscribe the static listener for mode change messages
    static {
        MessageCenter.subscribe(MODE_CHANGE_LISTENER);
    }

    /**
     * Static listener that receives mode change messages and updates cursor state.
     */
    private static class ModeChangeListener {
        @Handler
        public void modeDidChange(ModeChangedMessage message) {
            onModeChanged();
        }
    }

    // Prevent instantiation
    private InsertionNoteManager() {
    }

    // ==========================================================================
    // Public Static API
    // ==========================================================================

    /**
     * Clears the insertion note from all lines.
     * <p>
     * Call this when exiting edit mode or when the mouse leaves the score area.
     */
    static void clearInsertionNote() {
        if (currentInsertionLine != null) {
            var oldLine = currentInsertionLine;
            currentInsertionLine = null;
            currentXIndex = -1;
            currentStaffPosition = 0;
            currentIsOverNoteHead = false;
            oldLine.repaint();
        }
    }

    /**
     * Sets whether the Alt key is currently pressed and updates the cursor.
     *
     * @param pressed true if Alt is pressed
     */
    static void setAltPressed(boolean pressed) {
        altPressed = pressed;
        updateCursor();

        // When Alt is released, re-trigger insertion note from current mouse position
        if (!pressed && currentMouseLine != null) {
            restoreInsertionNote(currentMouseLine);
        }
    }

    /**
     * Called when the score mode changes. Updates cursor and restores insertion
     * note if switching back to NOTE_EDIT mode.
     */
    static void onModeChanged() {
        updateCursor();

        if (currentMouseLine != null) {
            restoreInsertionNote(currentMouseLine);
        }
    }

    /**
     * Returns the current insertion line, or null if no insertion note is active.
     */
    @Nullable
    static LineComponent getCurrentInsertionLine() {
        return currentInsertionLine;
    }

    /**
     * Returns the current insertion X index.
     */
    static int getCurrentXIndex() {
        return currentXIndex;
    }

    /**
     * Returns the current insertion Y position.
     */
    static int getCurrentStaffPosition() {
        return currentStaffPosition;
    }

    /**
     * Returns whether the given line currently has the insertion note.
     */
    static boolean hasInsertionNote(LineComponent lc) {
        return currentInsertionLine == lc;
    }

    // ==========================================================================
    // Delegation Entry Points (called from LineComponent mouse handlers)
    // ==========================================================================

    /**
     * Handles mouse movement over a line, updating insertion note position.
     * Replaces the insertion-note logic formerly inline in {@code LineComponent.mouseMoved()}.
     */
    static void trackMouse(LineComponent lc, MouseEvent e) {
        if (e.isAltDown()) {
            clearInsertionNote();
            return;
        }

        if (!shouldHandleInsertionNote(lc)) {
            return;
        }

        // Convert mouse pixel coordinates to staff-space units
        var scale = ScaleContext.getInstance();
        var mouseXss = scale.fromPixels(e.getX());
        var mouseYss = scale.fromPixels(e.getY());

        // Calculate Y position from mouse (in staff-space coordinates)
        int staffPosition = calculateStaffPositionFromMouse(mouseYss, lc.getMiddleLineYSs());

        if (!isValidStaffPosition(staffPosition)) {
            // Mouse is outside valid range, clear insertion note if on this line
            if (currentInsertionLine == lc) {
                clearInsertionNote();
            }

            return;
        }

        // Calculate X index from mouse using layout result
        int xIndex = 0;
        boolean isOverNoteHead = false;
        var layoutResult = lc.getLayoutResult();
        var line = lc.getLine();

        if (layoutResult != null && line != null) {
            xIndex = layoutResult.findInsertionIndex(mouseXss, line);
            isOverNoteHead = layoutResult.isMouseOverNoteHead(mouseXss, line);
        }

        // Check if position actually changed
        if (lc == currentInsertionLine && xIndex == currentXIndex
            && staffPosition == currentStaffPosition && isOverNoteHead == currentIsOverNoteHead) {
            return;  // No change, no repaint
        }

        // Repaint old line if different
        if (currentInsertionLine != null && currentInsertionLine != lc) {
            currentInsertionLine.repaint();
        }

        // Update static state
        currentInsertionLine = lc;
        currentXIndex = xIndex;
        currentStaffPosition = staffPosition;
        currentIsOverNoteHead = isOverNoteHead;

        // Update the edit note's Y position
        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            var editNote = editModeManager.getEditNote();

            if (editNote != null) {
                editNote.setStaffPosition(staffPosition);
            }
        }

        // Repaint this line
        lc.repaint();
    }

    /**
     * Handles a click on the insertion note, performing the appropriate action
     * (append, insert, or modify). Called from {@code LineComponent.mouseClicked()}.
     */
    static void handleClick(LineComponent lc) {
        if (!shouldHandleInsertionNote(lc)) {
            return;
        }

        // Only handle if this line has the insertion note
        var line = lc.getLine();

        if (currentInsertionLine != lc || line == null) {
            return;
        }

        // Determine action based on position
        if (currentXIndex == line.noteCount()) {
            addEditNote(lc, line);
        } else if (currentIsOverNoteHead) {
            modifyExistingNote(lc, currentXIndex, line);
        } else {
            insertEditNote(lc, currentXIndex, line);
        }
    }

    /**
     * Handles mouse entering a line. Sets up cursor and edit note visibility.
     */
    static void mouseEnteredLine(LineComponent lc) {
        currentMouseLine = lc;
        updateCursor();

        var editModeManager = EditModeManager.getInstance();

        if (shouldHandleInsertionNote(lc) && editModeManager != null) {
            editModeManager.setEditNoteVisible(true);
        }
    }

    /**
     * Handles mouse exiting a line. Clears cursor and insertion note.
     */
    static void mouseExitedLine(LineComponent lc) {
        currentMouseLine = null;
        lc.setCursor(DEFAULT_CURSOR);

        // Clear insertion note when mouse leaves this line
        if (currentInsertionLine == lc) {
            clearInsertionNote();
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setEditNoteVisible(false);
        }
    }

    // ==========================================================================
    // Internal Helpers
    // ==========================================================================

    /**
     * Returns whether insertion note handling should be active for the given line.
     * <p>
     * Requires: edit mode enabled, MOUSE control, NOTE_EDIT mode, and an edit note set.
     */
    private static boolean shouldHandleInsertionNote(LineComponent lc) {
        var editModeManager = EditModeManager.getInstance();

        if (!lc.isEditMode() || editModeManager == null || !editModeManager.hasEditNote()) {
            return false;
        }

        var score = lc.getScore();

        if (score == null) {
            return false;
        }

        return score.getControl() == Control.MOUSE && score.getMode() == Mode.NOTE_EDIT;
    }

    /**
     * Restores the insertion note from the current mouse position.
     * Called when Alt is released to immediately show the insertion note
     * without requiring mouse movement.
     */
    static void restoreInsertionNote(LineComponent lc) {
        if (!shouldHandleInsertionNote(lc)) {
            return;
        }

        var mousePos = lc.getMousePosition();

        if (mousePos == null) {
            return;
        }

        // Ensure edit note is visible (it may have been hidden if the mouse
        // entered while in a non-edit mode like SELECT)
        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setEditNoteVisible(true);
        }

        // Synthesize a MouseEvent and delegate to trackMouse
        var syntheticEvent = new MouseEvent(
            lc, MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(), 0,
            mousePos.x, mousePos.y, 0, false
        );
        trackMouse(lc, syntheticEvent);
    }

    /**
     * Called when the primary mouse button is pressed.
     *
     * @param onSelectableElement true if the press occurred on a selectable element
     */
    static void onMousePressed(boolean onSelectableElement) {
        mouseButtonPressed = true;
        pressedOnSelectableElement = onSelectableElement;
        updateCursor();
    }

    /**
     * Called when the primary mouse button is released. Restores the default cursor.
     */
    static void onMouseReleased() {
        mouseButtonPressed = false;
        pressedOnSelectableElement = false;
        updateCursor();
    }

    /**
     * Updates the cursor on the current mouse line based on mode and input state.
     * <p>
     * Crosshair is shown while the mouse button is held in select mode or with Alt
     * pressed, provided the press did not land on a selectable element.
     */
    private static void updateCursor() {
        if (currentMouseLine == null) {
            return;
        }

        var score = currentMouseLine.getScore();
        var isSelectMode = score != null && score.getMode() == Mode.SELECT;
        var shouldCrosshair = mouseButtonPressed
            && (isSelectMode || altPressed)
            && !pressedOnSelectableElement;
        var cursor = shouldCrosshair ? CROSSHAIR_CURSOR : DEFAULT_CURSOR;
        currentMouseLine.setCursor(cursor);
    }

    /**
     * Calculates the staff position (in note units) from a mouse Y coordinate.
     *
     * @param mouseYss    Mouse Y coordinate in staff-space units
     * @param middleLineYSs Y coordinate of the middle staff line in staff-space units
     * @return Staff position in note units
     */
    static int calculateStaffPositionFromMouse(double mouseYss, double middleLineYSs) {
        return (int) Math.round((mouseYss - middleLineYSs) / LayoutStylesheet.NOTE_Y_OFFSET);
    }

    /**
     * Returns whether the given Y position is within the valid range for notes.
     *
     * @param staffPosition Staff position in note units
     * @return true if the position is valid
     */
    static boolean isValidStaffPosition(int staffPosition) {
        var minY = -(STAFF_LINES_ABOVE + 2) * 2;
        var maxY = (STAFF_LINES_BELOW + 2) * 2;
        return staffPosition >= minY && staffPosition <= maxY;
    }

    // ==========================================================================
    // Note Mutation Methods
    // ==========================================================================

    /**
     * Adds an edit note to the end of the line.
     *
     * @param lc   The LineComponent
     * @param line The line to add the note to
     */
    private static void addEditNote(LineComponent lc, @NotNull Line line) {
        var score = lc.getScore();

        if (score == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager == null) {
            return;
        }

        var editNote = editModeManager.getEditNote();

        if (editNote == null) {
            return;
        }

        if (editModeManager.noteWasModified(line, line.noteCount())) {
            editModeManager.editNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        editNote.setXPosSs((int) Math.round(
            InsertionSpacingCalculator.calculateAppendPositionSs(line, editNote)));
        line.addNote(editNote);

        applyAutomaticBeaming(line, line.noteCount() - 1);

        editModeManager.editNoteDidChange(line, line.noteCount() - 1);
    }

    /**
     * Inserts an edit note at the specified index in the line.
     *
     * @param lc     The LineComponent
     * @param xIndex The index to insert at
     * @param line   The line to insert into
     */
    private static void insertEditNote(LineComponent lc, int xIndex, @NotNull Line line) {
        var score = lc.getScore();

        if (score == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager == null) {
            return;
        }

        var editNote = editModeManager.getEditNote();

        if (editNote == null) {
            return;
        }

        if (editModeManager.noteWasModified(line, xIndex)) {
            editModeManager.editNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        // If the user tries to insert into triplet, they will get an error message
        var iv = line.getTuplets().findInterval(xIndex - 1);

        if ((iv != null) && ((xIndex - 1) < iv.getEnd())) {
            score.getMainFrame().showErrorMessage("Cannot insert into a triplet.");
            return;
        }

        line.removeInterval(xIndex - 1, xIndex);
        var insertion = InsertionSpacingCalculator.calculateInsertion(line, editNote, xIndex);
        editNote.setXPosSs((int) Math.round(insertion.insertedNoteXSs()));
        line.addNote(xIndex, editNote);
        var shift = (int) Math.round(insertion.shiftForSubsequentNotesSs());

        for (var i = xIndex + 1; i < line.noteCount(); i++) {
            line.getNote(i).setXPosSs(line.getNote(i).getXPosSs() + shift);
        }

        applyAutomaticBeaming(line, xIndex);
        editModeManager.editNoteDidChange(line, xIndex);
    }

    /**
     * Applies automatic beaming for the note at the given index.
     * Scans backward from the note to find beamable neighbors and creates
     * a beam interval if the rhythmic grouping conditions are met.
     *
     * @param line      The line containing the note
     * @param noteIndex The index of the just-inserted note
     */
    private static void applyAutomaticBeaming(@NotNull Line line, int noteIndex) {
        var note = line.getNote(noteIndex);

        if (
            !note.getNoteType().isBeamable() ||
                (noteIndex < 1) ||
                (line.getTuplets().findInterval(noteIndex - 1) != null)
        ) {
            return;
        }

        var sum = 0;

        for (var i = noteIndex - 1; i >= 0; i--) {
            if (line.getNote(i).getNoteType() == NoteType.QUAVER) {
                sum += 2;
            } else if (
                (line.getNote(i).getNoteType() == NoteType.SEMIQUAVER) ||
                    (line.getNote(i).getNoteType() == NoteType.DEMI_SEMIQUAVER)
            ) {
                sum += 1;
            } else {
                break;
            }

            var interval = line.getBeamings().findInterval(i);

            if ((interval != null) && (interval.getStart() == i)) {
                break;
            }
        }

        if (
            ((note.getNoteType() == NoteType.QUAVER) &&
                (sum > 0) &&
                ((sum % 2) == 0) &&
                ((sum % 4) != 0)) ||
                (((note.getNoteType() == NoteType.SEMIQUAVER) ||
                    (note.getNoteType() == NoteType.DEMI_SEMIQUAVER)) &&
                    (sum > 0) &&
                    ((sum % 4) != 0))
        ) {
            line
                .getBeamings()
                .addInterval(new BeamInterval(noteIndex - 1, noteIndex));
        }
    }

    /**
     * Replaces an existing note entirely with the current edit note.
     * Only the x position is preserved from the existing note; all other attributes
     * (type, duration, dots, beaming, ties) come from the edit note.
     * Called when the user clicks on an existing note head with the insertion note active.
     *
     * @param lc        The LineComponent
     * @param noteIndex The index of the note to replace
     * @param line      The line containing the note
     */
    private static void modifyExistingNote(LineComponent lc, int noteIndex, @NotNull Line line) {
        var score = lc.getScore();

        if (score == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager == null) {
            return;
        }

        var editNote = editModeManager.getEditNote();

        if (editNote == null) {
            return;
        }

        if (editModeManager.noteWasModified(line, noteIndex)) {
            editModeManager.editNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        // Preserve the existing note's x position
        editNote.setXPosSs(line.getNote(noteIndex).getXPosSs());
        editNote.setStaffPosition(currentStaffPosition);

        if (editNote.isStemDirectionAuto()) {
            editNote.setUpper(Score.defaultUpperNote(editNote));
        }

        // Remove all beam intervals touching this note — the new note type may differ
        var beam = line.getBeamings().findInterval(noteIndex);

        while (beam != null) {
            line.getBeamings().removeInterval(beam);
            beam = line.getBeamings().findInterval(noteIndex);
        }

        // Remove all tie intervals touching this note
        var tie = line.getTies().findInterval(noteIndex);

        while (tie != null) {
            line.getTies().removeInterval(tie);
            tie = line.getTies().findInterval(noteIndex);
        }

        // Replace the note entirely (line.setNote marks the composition modified)
        line.setNote(noteIndex, editNote);

        applyAutomaticBeaming(line, noteIndex);

        editModeManager.editNoteDidChange(line, noteIndex);
    }
}
