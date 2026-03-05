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
import songscribe.music.Note;
import songscribe.music.NoteType;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout2.InsertionSpacingCalculator;
import songscribe.ui.layout2.LayoutResult;
import songscribe.ui.layout2.ScaleContext;
import songscribe.ui.message.LayoutChangeMessage;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.ModeChangedMessage;

/**
 * Manages the insertion note subsystem for {@link LineComponent}.
 * <p>
 * This class owns all static cross-instance state for insertion note tracking,
 * cursor management, and note mutation logic. Only one insertion note can be
 * active across all LineComponents at a time.
 */
public class InsertionNoteManager {

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

    /** Whether the mouse X (in staff spaces) is within the horizontal bounds of a note head. */
    private static boolean xPosSsMatchesNote = false;

    /** Whether the mouse Y (staff position) is within the vertical bounds of that note head. */
    private static boolean yPosSpMatchesNote = false;

    /** The glissando zone type determined by mouse position (null if no valid zone). */
    @Nullable
    private static Note.Glissando.Type currentGlissandoZone = null;

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
     * Hides the insertion note preview and optionally clears position tracking state.
     *
     * @param clear true to also clear position state (e.g., window deactivated);
     *              false to only hide the visual (e.g., hovering over an existing note head)
     */
    public static void hideInsertionNote(boolean clear) {
        if (clear) {
            clearInsertionNote();
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setInsertionNoteVisible(false);
        }
    }

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
            xPosSsMatchesNote = false;
            yPosSpMatchesNote = false;
            currentGlissandoZone = null;
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
     * Returns the line index of the note currently highlighted by insertion-note hover,
     * or -1 if the insertion note is not hovering over an existing note head.
     */
    public static int getHoveredNoteLineIndex() {
        return (xPosSsMatchesNote && yPosSpMatchesNote && currentInsertionLine != null)
            ? currentInsertionLine.getLineIndex()
            : -1;
    }

    /**
     * Returns the note index of the note currently highlighted by insertion-note hover,
     * or -1 if the insertion note is not hovering over an existing note head.
     */
    public static int getHoveredNoteIndex() {
        return (xPosSsMatchesNote && yPosSpMatchesNote) ? currentXIndex : -1;
    }

    /**
     * Returns whether the given line currently has the insertion note.
     */
    static boolean hasInsertionNote(LineComponent lc) {
        return currentInsertionLine == lc;
    }

    /**
     * Returns whether the mouse is currently hovering over an existing note head
     * (both X and Y match).
     */
    public static boolean isHoveringOverNoteHead() {
        return xPosSsMatchesNote && yPosSpMatchesNote;
    }

    /**
     * Returns whether a glissando preview line should be drawn.
     * <p>
     * True when the mouse is in a valid glissando zone (not over a note head, not to the
     * left of the first note). The preview is shown for both insertion and removal modes.
     */
    static boolean shouldShowGlissandoPreview() {
        return currentGlissandoZone != null;
    }

    /**
     * Returns whether the glissando tool is in removal mode for the given line.
     * <p>
     * True when the source note already has a glissando of the same type as the current zone.
     */
    @SuppressWarnings("ObjectEquality")
    static boolean isGlissandoRemovalMode(@NotNull Line line) {
        if (currentGlissandoZone == null || currentXIndex <= 0 || line.noteCount() <= 0) {
            return false;
        }

        var glissando = line.getNote(currentXIndex - 1).getGlissando();

        return glissando != Note.NO_GLISSANDO && glissando.type == currentGlissandoZone;
    }

    /**
     * Returns the current glissando zone type, or null if no valid zone exists.
     */
    @Nullable
    static Note.Glissando.Type getGlissandoZone() {
        return currentGlissandoZone;
    }


    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    /**
     * Returns the glissando type corresponding to the currently selected action,
     * or null if neither glissando action is selected.
     */
    @Nullable
    private static Note.Glissando.Type getSelectedGlissandoType() {
        var selected = Actions.DURATION_ACTION_GROUP.getSelected();

        if (selected == Actions.GLISSANDO_ACTION) {
            return Note.Glissando.Type.CONNECTED;
        }

        if (selected == Actions.SLIDE_OUT_ACTION) {
            return Note.Glissando.Type.SLIDE_OUT;
        }

        return null;
    }

    /**
     * Computes the glissando zone type based on the selected tool.
     * <p>
     * Returns null if the mouse is to the left of the first note (no source note).
     * Otherwise validates whether the selected glissando type can be inserted at
     * the given index: CONNECTED requires a note to the right with a different pitch,
     * SLIDE_OUT only requires a source note to the left.
     *
     * @param line   The line containing the notes
     * @param xIndex Insertion index from {@link LayoutResult#findInsertionIndex}
     * @return The zone type, or null if no valid zone
     */
    @Nullable
    private static Note.Glissando.Type computeGlissandoZone(
            @NotNull Line line,
            int xIndex) {
        // xIndex=0 means to the left of the first note — no source note to draw from
        if (xIndex <= 0 || line.noteCount() == 0) {
            return null;
        }

        var intendedType = getSelectedGlissandoType();

        if (intendedType == null) {
            return null;
        }

        if (intendedType == Note.Glissando.Type.CONNECTED) {
            // Connected requires a note to the right
            if (xIndex >= line.noteCount()) {
                return null;
            }

            // Same-pitch connected glissando is musically meaningless
            if (line.getNote(xIndex - 1).getPitch() == line.getNote(xIndex).getPitch()) {
                return null;
            }
        }

        return intendedType;
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

        // Calculate X index and note-head match from mouse using layout result
        var layoutResult = lc.getLayoutResult();
        var line = lc.getLine();

        if (layoutResult == null || line == null) {
            // Layout is being recalculated (e.g., mid-drag). Clear stale hover state
            // so that the next repaint does not show a highlight on the wrong note.
            xPosSsMatchesNote = false;
            yPosSpMatchesNote = false;
            return;
        }

        int xIndex = layoutResult.findInsertionIndex(mouseXss, line);
        int noteAtX = layoutResult.findNoteAtXSs(mouseXss, line);

        // Compute new position match flags before the early-return check, so that a
        // change in hover state (e.g., mouse slides from gap into note-head bounds at
        // the same xIndex) is not silently dropped.
        boolean newXMatch = noteAtX >= 0;
        boolean newYMatch = newXMatch
            && Math.abs(staffPosition - line.getNote(noteAtX).getStaffPosition()) <= 1;

        // Compute glissando zone before change detection so zone changes trigger repaints.
        // Only compute when not over a note head (noteAtX < 0), as hovering over a note
        // head means there is no valid glissando target to the left.
        var editModeManager = EditModeManager.getInstance();
        Note.Glissando.Type newGlissandoZone = null;

        if (editModeManager != null && editModeManager.getInsertionNote() == Note.GLISSANDO_NOTE
                && noteAtX < 0) {
            newGlissandoZone = computeGlissandoZone(line, xIndex);
        }

        // Check if position actually changed
        if (lc == currentInsertionLine && xIndex == currentXIndex
            && staffPosition == currentStaffPosition
            && newXMatch == xPosSsMatchesNote && newYMatch == yPosSpMatchesNote
            && newGlissandoZone == currentGlissandoZone) {
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
        xPosSsMatchesNote = newXMatch;
        yPosSpMatchesNote = newYMatch;
        currentGlissandoZone = newGlissandoZone;

        if (editModeManager != null) {
            var insertionNote = editModeManager.getInsertionNote();

            if (insertionNote == Note.GLISSANDO_NOTE) {
                // No note-head preview for glissando tool — renderInsertionNote draws the preview line.
                lc.repaint();
                return;
            }

            // Hide the insertion note only when both X and Y match: the hover highlight on the
            // existing note already signals what will be replaced. When only X matches, show the
            // preview so the user can see the pitch that will replace the existing note.
            if (xPosSsMatchesNote && yPosSpMatchesNote) {
                editModeManager.setInsertionNoteVisible(false);
            } else {
                editModeManager.setInsertionNoteVisible(true);
            }

            // Update the insertion note's Y position
            if (insertionNote != null) {
                insertionNote.setStaffPosition(staffPosition);
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

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            var insertionNote = editModeManager.getInsertionNote();

            if (insertionNote == Note.GLISSANDO_NOTE) {
                var zoneType = currentGlissandoZone;

                if (zoneType == null) {
                    return;  // No valid zone = click is a no-op
                }

                var sourceNote = line.getNote(currentXIndex - 1);
                var existingGlissando = sourceNote.getGlissando();

                // Toggle: if existing glissando matches the zone type, remove; otherwise set.
                // If existing glissando is a different type, replace it.
                @SuppressWarnings("ObjectEquality")
                var isSameType = existingGlissando != Note.NO_GLISSANDO
                    && existingGlissando.type == zoneType;

                if (isSameType) {
                    sourceNote.removeGlissando();
                } else {
                    sourceNote.setGlissando(zoneType);
                }

                var composition = line.getComposition();

                if (composition != null) {
                    composition.setModified(true);
                }

                MessageCenter.post(LayoutChangeMessage.scoreContent(line));
                lc.repaint();
                return;  // Stay in glissando mode
            }
        }

        // Determine action based on position
        if (currentXIndex == line.noteCount()) {
            addInsertionNote(lc, line);
        } else if (xPosSsMatchesNote) {
            modifyExistingNote(lc, currentXIndex, line);
        } else {
            insertNote(lc, currentXIndex, line);
        }
    }

    /**
     * Handles mouse entering a line. Sets up cursor and insertion note visibility.
     */
    static void mouseEnteredLine(LineComponent lc) {
        currentMouseLine = lc;
        updateCursor();

        var editModeManager = EditModeManager.getInstance();

        if (shouldHandleInsertionNote(lc) && editModeManager != null) {
            var insertionNote = editModeManager.getInsertionNote();

            if (insertionNote != Note.GLISSANDO_NOTE) {
                editModeManager.setInsertionNoteVisible(true);
            }
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
            editModeManager.setInsertionNoteVisible(false);
        }
    }

    // ==========================================================================
    // Internal Helpers
    // ==========================================================================

    /**
     * Returns whether insertion note handling should be active for the given line.
     * <p>
     * Requires: edit mode enabled, MOUSE control, NOTE_EDIT mode, and an insertion note set.
     */
    private static boolean shouldHandleInsertionNote(LineComponent lc) {
        var editModeManager = EditModeManager.getInstance();

        if (!lc.isEditMode() || editModeManager == null || !editModeManager.hasInsertionNote()) {
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

        // Invalidate the cached xIndex so that the early-return guard in trackMouse
        // does not skip recomputation when the mouse position is unchanged but the
        // underlying notes have moved (e.g., after a drag finalises a pitch change).
        currentXIndex = -1;

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
     * Adds an insertion note to the end of the line.
     *
     * @param lc   The LineComponent
     * @param line The line to add the note to
     */
    private static void addInsertionNote(LineComponent lc, @NotNull Line line) {
        var score = lc.getScore();

        if (score == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager == null) {
            return;
        }

        var insertionNote = editModeManager.getInsertionNote();

        if (insertionNote == null) {
            return;
        }

        if (editModeManager.noteWasModified(line, line.noteCount())) {
            editModeManager.insertionNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        insertionNote.setXPosSs((int) Math.round(
            InsertionSpacingCalculator.calculateAppendPositionSs(line, insertionNote)));
        line.addNote(insertionNote);

        applyAutomaticBeaming(line, line.noteCount() - 1);

        editModeManager.insertionNoteDidChange(line, line.noteCount() - 1);
    }

    /**
     * Inserts an insertion note at the specified index in the line.
     *
     * @param lc     The LineComponent
     * @param xIndex The index to insert at
     * @param line   The line to insert into
     */
    private static void insertNote(LineComponent lc, int xIndex, @NotNull Line line) {
        var score = lc.getScore();

        if (score == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager == null) {
            return;
        }

        var insertionNote = editModeManager.getInsertionNote();

        if (insertionNote == null) {
            return;
        }

        if (editModeManager.noteWasModified(line, xIndex)) {
            editModeManager.insertionNoteDidChange(line, xIndex);
            return;
        }

        // If the user tries to insert into triplet, they will get an error message
        var iv = line.getTuplets().findInterval(xIndex - 1);

        if ((iv != null) && ((xIndex - 1) < iv.getEnd())) {
            score.getMainFrame().showErrorMessage("Cannot insert into a triplet.");
            return;
        }

        line.removeInterval(xIndex - 1, xIndex);
        var insertion = InsertionSpacingCalculator.calculateInsertion(line, insertionNote, xIndex);
        insertionNote.setXPosSs((int) Math.round(insertion.insertedNoteXSs()));
        line.addNote(xIndex, insertionNote);
        var shift = (int) Math.round(insertion.shiftForSubsequentNotesSs());

        for (var i = xIndex + 1; i < line.noteCount(); i++) {
            line.getNote(i).setXPosSs(line.getNote(i).getXPosSs() + shift);
        }

        applyAutomaticBeaming(line, xIndex);
        editModeManager.insertionNoteDidChange(line, xIndex);
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
     * Replaces an existing note entirely with the current insertion note.
     * Only the x position is preserved from the existing note; all other attributes
     * (type, duration, dots, beaming, ties) come from the insertion note.
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

        var insertionNote = editModeManager.getInsertionNote();

        if (insertionNote == null) {
            return;
        }

        if (editModeManager.noteWasModified(line, noteIndex)) {
            editModeManager.insertionNoteDidChange(line, noteIndex);
            return;
        }

        // Preserve the existing note's x position
        insertionNote.setXPosSs(line.getNote(noteIndex).getXPosSs());
        insertionNote.setStaffPosition(currentStaffPosition);

        if (insertionNote.isStemDirectionAuto()) {
            insertionNote.setUpper(Score.defaultUpperNote(insertionNote));
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
        line.setNote(noteIndex, insertionNote);

        applyAutomaticBeaming(line, noteIndex);

        // Also check the note after the replaced one: when noteIndex is the start of a beam,
        // applyAutomaticBeaming only scans backward and misses the forward neighbor.
        if (noteIndex + 1 < line.noteCount()) {
            applyAutomaticBeaming(line, noteIndex + 1);
        }

        editModeManager.insertionNoteDidChange(line, noteIndex);
    }
}
