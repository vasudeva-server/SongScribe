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

import module java.desktop;

import java.awt.event.MouseEvent;

import org.jspecify.annotations.Nullable;

import net.engio.mbassy.listener.Handler;

import songscribe.Strings;
import songscribe.music.BeamInterval;
import songscribe.message.notification.CompositionDidChangeNotification;
import songscribe.message.MessageCenter;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.Control;
import songscribe.ui.OptionDialogs;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.InsertionSpacingCalculator;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.ScaleContext;
import songscribe.message.notification.ModeDidChangeNotification;

/**
 * Manages the insertion element subsystem for {@link LineComponent}.
 * <p>
 * This class owns all static cross-instance state for insertion element tracking,
 * cursor management, and element mutation logic. Only one insertion element can be
 * active across all LineComponents at a time.
 */
public class InsertionElementManager {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Number of ledger lines above the staff. */
    private static final int STAFF_LINES_ABOVE = 3;

    /** Number of ledger lines below the staff. */
    private static final int STAFF_LINES_BELOW = 4;

    /** Minimum (highest-pitched) valid staff position. */
    static final int MIN_STAFF_POSITION_SP = -(STAFF_LINES_ABOVE + 2) * 2;

    /** Maximum (lowest-pitched) valid staff position. */
    static final int MAX_STAFF_POSITION_SP = (STAFF_LINES_BELOW + 2) * 2;

    /** Default cursor. */
    private static final Cursor DEFAULT_CURSOR = Cursor.getDefaultCursor();

    // ==========================================================================
    // Static State
    // ==========================================================================

    /**
     * The LineComponent that currently has the insertion element.
     * <p>
     * Only one line can show the insertion element at a time. When the mouse moves
     * to a different line, the old line is repainted to clear the insertion element.
     */
    @Nullable
    private static LineComponent currentInsertionLine = null;

    /** Current insertion index (0 to elementCount inclusive). */
    private static int currentXIndex = -1;

    /** Current Y position on the staff (in staff position units, not pixels). */
    private static int currentStaffPosition = 0;

    /** Whether the Alt key is currently held down. */
    private static boolean altPressed = false;

    /** The LineComponent the mouse is currently over (independent of insertion note state). */
    @Nullable
    private static LineComponent currentMouseLine = null;

    /** Whether the mouse X (in staff spaces) is within the horizontal bounds of a note head. */
    private static boolean xPosSsMatchesElement = false;

    /** Whether the mouse Y (staff position) is within the vertical bounds of that note head. */
    private static boolean yPosSpMatchesElement = false;

    /** The glissando zone type determined by mouse position (null if no valid zone). */
    private static StaffElement.Glissando.@Nullable Type currentGlissandoZone = null;

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
        public void modeDidChange(ModeDidChangeNotification message) {
            onModeChanged();
        }
    }

    // Prevent instantiation
    private InsertionElementManager() {
    }

    // ==========================================================================
    // Public Static API
    // ==========================================================================

    /**
     * Hides the insertion element preview and optionally clears position tracking state.
     *
     * @param clear true to also clear position state (e.g., window deactivated);
     *              false to only hide the visual (e.g., hovering over an existing element head)
     */
    public static void hideInsertionElement(boolean clear) {
        if (clear) {
            clearInsertionElement();
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setInsertionElementVisible(false);
        }
    }

    /**
     * Clears the insertion element from all lines.
     * <p>
     * Call this when exiting edit mode or when the mouse leaves the score area.
     */
    static void clearInsertionElement() {
        if (currentInsertionLine != null) {
            var oldLine = currentInsertionLine;
            currentInsertionLine = null;
            currentXIndex = -1;
            currentStaffPosition = 0;
            xPosSsMatchesElement = false;
            yPosSpMatchesElement = false;
            currentGlissandoZone = null;
            oldLine.repaint();
        }
    }

    /**
     * Sets whether the Alt key is currently pressed.
     *
     * @param pressed true if Alt is pressed
     */
    static void setAltPressed(boolean pressed) {
        altPressed = pressed;

        // When Alt is released, re-trigger insertion element from current mouse position
        if (!pressed && currentMouseLine != null) {
            restoreInsertionElement(currentMouseLine);
        }
    }

    /**
     * Called when the score mode changes. Restores insertion element if switching
     * back to NOTE_EDIT mode.
     */
    static void onModeChanged() {
        if (currentMouseLine != null) {
            restoreInsertionElement(currentMouseLine);
        }
    }

    /**
     * Returns the current insertion line, or null if no insertion element is active.
     */
    @Nullable
    static LineComponent getCurrentInsertionLine() {
        return currentInsertionLine;
    }

    /**
     * Returns the current insertion X index.
     */
    public static int getCurrentXIndex() {
        return currentXIndex;
    }

    /**
     * Returns the current insertion Y position.
     */
    static int getCurrentStaffPosition() {
        return currentStaffPosition;
    }

    /**
     * Returns the line index of the element currently highlighted by insertion element hover,
     * or -1 if the insertion element is not hovering over an existing element head.
     */
    public static int getHoveredElementLineIndex() {
        return (xPosSsMatchesElement && yPosSpMatchesElement && currentInsertionLine != null)
            ? currentInsertionLine.getLineIndex()
            : -1;
    }

    /**
     * Returns the element index of the element currently highlighted by insertion element hover,
     * or -1 if the insertion element is not hovering over an existing element head.
     */
    public static int getHoveredElementIndex() {
        return (xPosSsMatchesElement && yPosSpMatchesElement) ? currentXIndex : -1;
    }

    /**
     * Returns whether the given line currently has the insertion element.
     */
    static boolean hasInsertionElement(LineComponent lc) {
        return currentInsertionLine == lc;
    }

    /**
     * Returns whether the mouse is currently hovering over an existing note head
     * (both X and Y match).
     */
    public static boolean isHoveringOverElementHead() {
        return xPosSsMatchesElement && yPosSpMatchesElement;
    }

    /**
     * Returns whether a glissando preview line should be drawn.
     * <p>
     * True when the mouse is in a valid glissando zone (not over a note head, not to the
     * left of the first note). The preview is shown for both insertion and removal modes.
     */
    public static boolean shouldShowGlissandoPreview() {
        return currentGlissandoZone != null;
    }

    /**
     * Returns the current glissando zone type, or null if no valid zone exists.
     */
    static StaffElement.Glissando.@Nullable Type getGlissandoZone() {
        return currentGlissandoZone;
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    /**
     * Returns the glissando type corresponding to the currently selected action,
     * or null if neither glissando action is selected.
     */
    private static StaffElement.Glissando.@Nullable Type getSelectedGlissandoType() {
        var selected = Actions.DURATION_ACTION_GROUP.getSelected();

        if (selected instanceof ElementTypeAction eta) {
            return eta.getGlissandoType();
        }

        return null;
    }

    /**
     * Computes the glissando zone type based on the selected tool.
     * <p>
     * Returns null if the mouse is to the left of the first note (no source note).
     * Otherwise validates whether the selected glissando type can be inserted at
     * the given index: CONNECTED requires a pitched note to the right with a different pitch,
     * SLIDE_OUT only requires a pitched source note to the left.
     *
     * @param line   The line containing the notes
     * @param xIndex Insertion index from {@link LayoutResult#findInsertionIndex}
     * @return The zone type, or null if no valid zone
     */
    private static StaffElement.Glissando.@Nullable Type computeGlissandoZone(
        Line line,
        int xIndex) {
        // xIndex=0 means to the left of the first note — no source note to draw from
        if (xIndex <= 0 || line.elementCount() == 0) {
            return null;
        }

        var intendedType = getSelectedGlissandoType();

        if (intendedType == null) {
            return null;
        }

        // Rests cannot be glissando source or target
        if (line.getElement(xIndex - 1).getType().isRest()) {
            return null;
        }

        if (intendedType == StaffElement.Glissando.Type.CONNECTED) {
            // Connected requires a note to the right
            if (xIndex >= line.elementCount()) {
                return null;
            }

            // Target cannot be a rest
            if (line.getElement(xIndex).getType().isRest()) {
                return null;
            }

            // Same-pitch connected glissando is musically meaningless
            if (line.getElement(xIndex - 1).getPitch() == line.getElement(xIndex).getPitch()) {
                return null;
            }
        }

        return intendedType;
    }

    // ==========================================================================
    // Delegation Entry Points (called from LineComponent mouse handlers)
    // ==========================================================================

    /**
     * Handles mouse movement over a line, updating insertion element position.
     * Replaces the insertion element logic formerly inline in {@code LineComponent.mouseMoved()}.
     */
    static void trackMouse(LineComponent lc, MouseEvent e) {
        if (e.isAltDown()) {
            clearInsertionElement();
            return;
        }

        if (!shouldHandleInsertionElement(lc)) {
            return;
        }

        // Convert mouse pixel coordinates to staff-space units
        var scale = ScaleContext.getInstance();
        var mouseYss = scale.fromPixels(e.getY());

        // In grace mode, lock the x-position to the host note slot
        var editModeManager = EditModeManager.getInstance();
        double mouseXss;

        if (editModeManager != null && editModeManager.getGraceModeManager().isInProgress()) {
            mouseXss = editModeManager.getGraceModeManager().getLockedInsertionXSs();
        } else {
            mouseXss = scale.fromPixels(e.getX());
        }

        // Calculate Y position from mouse (in staff-space coordinates)
        int staffPosition = calculateStaffPositionFromMouse(mouseYss, lc.getMiddleLineYSs());

        if (!isValidStaffPosition(staffPosition)) {
            // Mouse is outside valid range, clear insertion note if on this line
            if (currentInsertionLine == lc) {
                clearInsertionElement();
            }

            return;
        }

        // Calculate X index and element-head match from mouse using layout result
        var layoutResult = lc.getLayoutResult();
        var line = lc.getLine();

        if (layoutResult == null || line == null) {
            // Layout is being recalculated (e.g., mid-drag). Clear stale hover state
            // so that the next repaint does not show a highlight on the wrong element.
            xPosSsMatchesElement = false;
            yPosSpMatchesElement = false;
            return;
        }

        int xIndex = layoutResult.findInsertionIndex(mouseXss, line);
        int elementAtX = layoutResult.findElementAtXSs(mouseXss, line);

        var insertionElement = editModeManager != null ? editModeManager.getInsertionElement() : null;

        // Hide the insertion element when a grace note is selected and the mouse
        // is between an existing grace/host pair — insertion there is not allowed.
        if (insertionElement != null
            && insertionElement.getType().isGraceNote()
            && line.isInsideGraceHostPair(xIndex)) {
            if (currentInsertionLine == lc) {
                clearInsertionElement();
            }

            return;
        }

        // Compute new position match flags before the early-return check, so that a
        // change in hover state (e.g., mouse slides from gap into element-head bounds at
        // the same xIndex) is not silently dropped.
        boolean newXMatch = elementAtX >= 0;
        boolean newYMatch = newXMatch
            && Math.abs(staffPosition - line.getElement(elementAtX).getStaffPosition()) <= 1;

        // Compute glissando zone before change detection so zone changes trigger repaints.
        // Only compute when not over an element head (elementAtX < 0), as hovering over an
        // element head means there is no valid glissando target to the left.
        StaffElement.Glissando.Type newGlissandoZone = null;

        if (insertionElement == StaffElement.GLISSANDO_PLACEHOLDER && elementAtX < 0) {
            newGlissandoZone = computeGlissandoZone(line, xIndex);
        }

        // Check if position actually changed
        if (lc == currentInsertionLine && xIndex == currentXIndex
            && staffPosition == currentStaffPosition
            && newXMatch == xPosSsMatchesElement && newYMatch == yPosSpMatchesElement
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
        xPosSsMatchesElement = newXMatch;
        yPosSpMatchesElement = newYMatch;
        currentGlissandoZone = newGlissandoZone;

        if (editModeManager != null) {
            if (insertionElement == StaffElement.GLISSANDO_PLACEHOLDER) {
                // No note-head preview for glissando tool — renderInsertionElement draws the preview line.
                lc.repaint();
                return;
            }

            // Always show the ghost preview — even when hovering over an existing element head.
            // The preview shows the user what pitch/type will replace the existing element.
            editModeManager.setInsertionElementVisible(true);

            // Rests snap to their default staff position; pitched notes follow the mouse Y
            if (insertionElement != null) {
                applyStaffPosition(insertionElement, staffPosition);
            }
        }

        // Repaint this line
        lc.repaint();
    }

    /**
     * Handles a click on the insertion element, performing the appropriate action
     * (append, insert, or modify). Called from {@code LineComponent.mouseClicked()}.
     */
    public static void handleClick(LineComponent lc) {
        if (!shouldHandleInsertionElement(lc)) {
            return;
        }

        // Only handle if this line has the insertion element
        var line = lc.getLine();

        if (currentInsertionLine != lc || line == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            var insertionElement = editModeManager.getInsertionElement();

            if (insertionElement == StaffElement.GLISSANDO_PLACEHOLDER) {
                var zoneType = currentGlissandoZone;

                if (zoneType == null) {
                    return;  // No valid zone = click is a no-op
                }

                var sourceNote = line.getElement(currentXIndex - 1);
                sourceNote.setGlissando(zoneType);

                var composition = line.getComposition();

                if (composition != null) {
                    composition.setModified(true);
                    MessageCenter.post(new CompositionDidChangeNotification(CompositionDidChangeNotification.ChangeType.CONTENT, composition, line));
                }
                lc.repaint();
                return;  // Stay in glissando mode
            }
        }

        // Determine action based on position
        if (currentXIndex == line.elementCount()) {
            addInsertionElement(lc, line);
        } else if (xPosSsMatchesElement) {
            modifyExistingElement(lc, currentXIndex, line);
        } else {
            insertElement(lc, currentXIndex, line);
        }
    }

    /**
     * Handles mouse entering a line. Sets up cursor and insertion element visibility.
     */
    static void mouseEnteredLine(LineComponent lc) {
        currentMouseLine = lc;

        var editModeManager = EditModeManager.getInstance();

        if (shouldHandleInsertionElement(lc) && editModeManager != null) {
            var insertionElement = editModeManager.getInsertionElement();

            if (insertionElement != StaffElement.GLISSANDO_PLACEHOLDER) {
                editModeManager.setInsertionElementVisible(true);
            }
        }
    }

    /**
     * Handles mouse exiting a line. Clears cursor and insertion element.
     */
    static void mouseExitedLine(LineComponent lc) {
        currentMouseLine = null;
        lc.setCursor(DEFAULT_CURSOR);

        // Clear insertion element when mouse leaves this line
        if (currentInsertionLine == lc) {
            clearInsertionElement();
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setInsertionElementVisible(false);
        }
    }

    // ==========================================================================
    // Internal Helpers
    // ==========================================================================

    /**
     * Returns whether insertion element handling should be active for the given line.
     * <p>
     * Requires: edit mode enabled, MOUSE control, NOTE_EDIT mode, and an insertion element set.
     */
    private static boolean shouldHandleInsertionElement(LineComponent lc) {
        var editModeManager = EditModeManager.getInstance();

        if (!lc.isEditMode() || editModeManager == null || !editModeManager.hasInsertionElement()) {
            return false;
        }

        var score = lc.getScore();

        if (score == null) {
            return false;
        }

        return score.getControl() == Control.MOUSE && score.getMode() == Mode.EDIT;
    }

    /**
     * Restores the insertion element from the current mouse position.
     * Called when Alt is released to immediately show the insertion element
     * without requiring mouse movement.
     */
    public static void restoreInsertionElement(LineComponent lc) {
        if (!shouldHandleInsertionElement(lc)) {
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
     * Calculates the staff position from a mouse Y coordinate.
     *
     * @param mouseYss      Mouse Y coordinate in staff-space units
     * @param middleLineYSs Y coordinate of the middle staff line in staff-space units
     * @return Staff position
     */
    static int calculateStaffPositionFromMouse(double mouseYss, double middleLineYSs) {
        return (int) Math.round((mouseYss - middleLineYSs) / LayoutStylesheet.STAFF_POSITION_OFFSET_SS);
    }

    /**
     * Sets the staff position on an element: rests snap to their type's default
     * position, pitched notes use the given mouse-derived position.
     */
    private static void applyStaffPosition(StaffElement element, int staffPositionSp) {
        if (element.getType().isRest()) {
            element.setStaffPosition(element.getType().getDefaultStaffPosition());
        } else {
            element.setStaffPosition(staffPositionSp);
        }
    }

    /**
     * Returns whether the given staff position is within the valid range for elements.
     *
     * @param staffPosition Staff position
     * @return true if the position is valid
     */
    static boolean isValidStaffPosition(int staffPosition) {
        return staffPosition >= MIN_STAFF_POSITION_SP && staffPosition <= MAX_STAFF_POSITION_SP;
    }

    // ==========================================================================
    // Element Mutation Methods
    // ==========================================================================

    /**
     * Returns the active insertion element from {@link EditModeManager},
     * or null if the manager or element is unavailable.
     */
    @Nullable
    private static StaffElement getActiveInsertionElement() {
        var editModeManager = EditModeManager.getInstance();

        if (editModeManager == null) {
            return null;
        }

        return editModeManager.getInsertionElement();
    }

    /**
     * Calculates the insertion result for adding an element to a line.
     * If the element would not fit within the line width, shows an error
     * message and returns null.
     *
     * @param line    The line to insert into
     * @param element The element to insert
     * @param index   The insertion index
     * @return The insertion result, or null if the line is full
     */
    private static InsertionSpacingCalculator.@Nullable InsertionResult calculateInsertionOrShowError(
        Line line, StaffElement element, int index
    ) {
        var insertion = InsertionSpacingCalculator.calculateInsertion(line, element, index);
        var composition = line.getComposition();

        if (composition != null && !insertion.fitsWithinLine(composition.getLineWidthSs())) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.get(Strings.DIALOG_TITLE_INSERT_ERROR),
                Strings.get(Strings.ERROR_LINE_FULL)
            );
            return null;
        }

        return insertion;
    }

    /**
     * Adds an insertion element to the end of the line.
     *
     * @param lc   The LineComponent
     * @param line The line to add the element to
     */
    private static void addInsertionElement(LineComponent lc, Line line) {
        var score = lc.getScore();

        if (score == null) {
            return;
        }

        var insertionElement = getActiveInsertionElement();

        if (insertionElement == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null && editModeManager.elementWasModified(line, line.elementCount())) {
            editModeManager.insertionElementDidChange(line, line.elementCount() - 1);
            return;
        }

        var insertion = calculateInsertionOrShowError(line, insertionElement, line.elementCount());

        if (insertion == null) {
            return;
        }

        insertionElement.setXPosSs(ScaleContext.getInstance().toRoundedPixels(insertion.insertedElementXSs()));
        line.addElement(insertionElement);

        applyAutomaticBeaming(line, line.elementCount() - 1);

        if (editModeManager != null) {
            editModeManager.insertionElementDidChange(line, line.elementCount() - 1);
        }
    }

    /**
     * Inserts an insertion element at the specified index in the line.
     *
     * @param lc     The LineComponent
     * @param xIndex The index to insert at
     * @param line   The line to insert into
     */
    private static void insertElement(LineComponent lc, int xIndex, Line line) {
        var score = lc.getScore();

        if (score == null) {
            return;
        }

        var insertionElement = getActiveInsertionElement();

        if (insertionElement == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null && editModeManager.elementWasModified(line, xIndex)) {
            editModeManager.insertionElementDidChange(line, xIndex);
            return;
        }

        // If the user tries to insert into triplet, they will get an error message
        var iv = line.getTuplets().findInterval(xIndex - 1);

        if ((iv != null) && ((xIndex - 1) < iv.getEnd())) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.get(Strings.DIALOG_TITLE_INSERT_ERROR),
                Strings.get(Strings.ERROR_TRIPLET_INSERT)
            );
            return;
        }

        line.removeInterval(xIndex - 1, xIndex);
        var insertion = calculateInsertionOrShowError(line, insertionElement, xIndex);

        if (insertion == null) {
            return;
        }

        insertionElement.setXPosSs(ScaleContext.getInstance().toRoundedPixels(insertion.insertedElementXSs()));
        line.addElement(xIndex, insertionElement);
        var shift = ScaleContext.getInstance().toRoundedPixels(insertion.shiftForSubsequentElementsSs());

        for (var i = xIndex + 1; i < line.elementCount(); i++) {
            line.getElement(i).setXPosSs(line.getElement(i).getXPosSs() + shift);
        }

        applyAutomaticBeaming(line, xIndex);

        if (editModeManager != null) {
            editModeManager.insertionElementDidChange(line, xIndex);
        }
    }

    /**
     * Applies automatic beaming for the element at the given index.
     * Scans backward from the element to find beamable neighbors and creates
     * a beam interval if the rhythmic grouping conditions are met.
     *
     * @param line         The line containing the element
     * @param elementIndex The index of the just-inserted element
     */
    private static void applyAutomaticBeaming(Line line, int elementIndex) {
        var element = line.getElement(elementIndex);

        if (
            !element.getType().isBeamable() ||
                (elementIndex < 1) ||
                (line.getTuplets().findInterval(elementIndex - 1) != null)
        ) {
            return;
        }

        var sum = 0;

        for (var i = elementIndex - 1; i >= 0; i--) {
            if (line.getElement(i).getType() == ElementType.QUAVER) {
                sum += 2;
            } else if (
                (line.getElement(i).getType() == ElementType.SEMIQUAVER) ||
                    (line.getElement(i).getType() == ElementType.DEMI_SEMIQUAVER)
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
            ((element.getType() == ElementType.QUAVER) &&
                (sum > 0) &&
                ((sum % 2) == 0) &&
                ((sum % 4) != 0)) ||
                (((element.getType() == ElementType.SEMIQUAVER) ||
                    (element.getType() == ElementType.DEMI_SEMIQUAVER)) &&
                    (sum > 0) &&
                    ((sum % 4) != 0))
        ) {
            line
                .getBeamings()
                .addInterval(new BeamInterval(elementIndex - 1, elementIndex));
        }
    }

    /**
     * Replaces an existing element entirely with the current insertion element.
     * Only the x position is preserved from the existing element; all other attributes
     * (type, duration, dots, beaming, ties) come from the insertion element.
     * Called when the user clicks on an existing element head with the insertion element active.
     *
     * @param lc           The LineComponent
     * @param elementIndex The index of the element to replace
     * @param line         The line containing the element
     */
    private static void modifyExistingElement(LineComponent lc, int elementIndex, Line line) {
        var score = lc.getScore();

        if (score == null) {
            return;
        }

        var insertionElement = getActiveInsertionElement();

        if (insertionElement == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null && editModeManager.elementWasModified(line, elementIndex)) {
            editModeManager.insertionElementDidChange(line, elementIndex);
            return;
        }

        // Preserve the existing element's x position
        insertionElement.setXPosSs(line.getElement(elementIndex).getXPosSs());

        // Rests snap to their default staff position; pitched notes use the mouse Y position
        applyStaffPosition(insertionElement, currentStaffPosition);

        if (insertionElement.isStemDirectionAuto()) {
            insertionElement.setUpper(Score.defaultUpperNote(insertionElement));
        }

        // Remove all beam intervals touching this element — the new element type may differ
        var beam = line.getBeamings().findInterval(elementIndex);

        while (beam != null) {
            line.getBeamings().removeInterval(beam);
            beam = line.getBeamings().findInterval(elementIndex);
        }

        // Remove all tie intervals touching this element
        var tie = line.getTies().findInterval(elementIndex);

        while (tie != null) {
            line.getTies().removeInterval(tie);
            tie = line.getTies().findInterval(elementIndex);
        }

        // Replace the element entirely (line.setElement marks the composition modified)
        line.setElement(elementIndex, insertionElement);

        applyAutomaticBeaming(line, elementIndex);

        // Also check the element after the replaced one: when elementIndex is the start of a beam,
        // applyAutomaticBeaming only scans backward and misses the forward neighbor.
        if (elementIndex + 1 < line.elementCount()) {
            applyAutomaticBeaming(line, elementIndex + 1);
        }

        // Grace note cleanup: if the preceding element is a paired grace note (grace + connected
        // glissando to the replaced host), and the replacement is not a pitched note, remove the
        // grace note. For pitched note replacements the glissando reattaches automatically since
        // setElement preserves the element index.
        if (elementIndex > 0
                && line.isPairedGraceNote(elementIndex - 1)
                && !insertionElement.getType().isPitchedNote()) {
            line.removeElement(elementIndex - 1);
            elementIndex--;
        }

        if (editModeManager != null) {
            editModeManager.insertionElementDidChange(line, elementIndex);
        }
    }
}
