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
import songscribe.message.MessageCenter;
import songscribe.message.mutation.ElementField;
import songscribe.music.BeamSpan;
import songscribe.music.Song;
import songscribe.music.ElementLocation;
import songscribe.music.ElementType;
import songscribe.music.Line;
import songscribe.music.StaffElement;
import songscribe.ui.Control;
import songscribe.ui.EndingConfirms;
import songscribe.ui.dialog.TempoChangeDialog;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.Ending;
import songscribe.ui.layout.InsertionSpacingCalculator;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.ScaleContext;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.ui.layout.StaffExtents;
import songscribe.ui.playback.PlaybackController;

/**
 * Manages the preview element subsystem for {@link LineComponent}.
 * <p>
 * This class owns all static cross-instance state for preview element tracking,
 * cursor management, and element mutation logic. Only one preview element can be
 * active across all LineComponents at a time.
 */
public final class PreviewElementManager {

    // ==========================================================================
    // Static State
    // ==========================================================================

    /**
     * The LineComponent that currently has the preview element.
     * <p>
     * Only one line can show the preview element at a time. When the mouse moves
     * to a different line, the old line is repainted to clear the preview element.
     */
    @Nullable
    static LineComponent currentPreviewLine = null;

    /** Current insertion index (0 to elementCount inclusive). */
    static int currentXIndex = -1;

    /** Current Y position on the staff (in staff position units, not pixels). */
    static int currentStaffPosition = 0;

    /** Whether the Alt key is currently held down. */
    private static boolean altPressed = false;

    /** The LineComponent the mouse is currently over (independent of insertion note state). */
    @Nullable
    private static LineComponent currentMouseLine = null;

    /** Whether the mouse X (in staff spaces) is within the horizontal bounds of a note head. */
    static boolean xPosSsMatchesElement = false;

    /** Whether the mouse Y (staff position) is within the vertical bounds of that note head. */
    private static boolean yPosSpMatchesElement = false;

    /** The glissando zone type determined by mouse position (null if no valid zone). */
    private static StaffElement.Glissando.@Nullable Type currentGlissandoZone = null;

    /** Last tracked mouse X in staff-space units; used by LineRenderer to avoid Swing getMousePosition() returning null. */
    private static double currentMouseXSs = 0.0;

    /** Strong reference to prevent GC by the weak-reference message bus; used for subscriptions. */
    private static final PreviewElementManager INSTANCE = new PreviewElementManager();

    static {
        MessageCenter.subscribe(INSTANCE);
    }

    private PreviewElementManager() {
    }

    @Handler
    public void modeDidChange(ModeDidChangeNotification message) {
        if (message.getMode() != Mode.EDIT) {
            clearPreviewElement();
            var editModeManager = EditModeManager.getInstance();

            if (editModeManager != null) {
                editModeManager.setPreviewElement(null);
            }
        } else {
            restorePreviewElement(currentMouseLine);
        }
    }

    @Handler
    public void playbackStateDidChange(PlaybackStateDidChangeNotification message) {
        if (PlaybackController.isPlaying()) {
            clearPreviewElement();
        } else {
            restorePreviewElement(currentMouseLine);
        }
    }

    // ==========================================================================
    // Public Static API
    // ==========================================================================

    /**
     * Hides the preview element and optionally clears position tracking state.
     *
     * @param clear true to also clear position state (e.g., window deactivated);
     *              false to only hide the visual (e.g., hovering over an existing element head)
     */
    public static void hidePreviewElement(boolean clear) {
        if (clear) {
            clearPreviewElement();
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setPreviewElementVisible(false);
        }
    }

    /**
     * Clears the preview element from all lines.
     * <p>
     * Call this when exiting edit mode or when the mouse leaves the score area.
     */
    static void clearPreviewElement() {
        if (currentPreviewLine != null) {
            var oldLine = currentPreviewLine;
            currentPreviewLine = null;
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

        // When Alt is released, re-trigger preview element from current mouse position
        if (!pressed) {
            restorePreviewElement(currentMouseLine);
        }
    }

    /**
     * Returns the current preview line, or null if no preview element is active.
     */
    @Nullable
    static LineComponent getCurrentInsertionLine() {
        return currentPreviewLine;
    }

    /**
     * Returns the current insertion X index.
     */
    public static int getCurrentXIndex() {
        return currentXIndex;
    }

    /**
     * Returns the last tracked mouse X in staff-space units.
     */
    public static double getCurrentMouseXSs() {
        return currentMouseXSs;
    }

    /**
     * Returns the current insertion Y position.
     */
    static int getCurrentStaffPosition() {
        return currentStaffPosition;
    }

    /**
     * Returns the location of the element currently highlighted by preview element hover,
     * or null if the preview element's x-position does not match an existing element.
     */
    @Nullable
    public static ElementLocation getHoveredElementLocation() {
        return (xPosSsMatchesElement && currentPreviewLine != null)
            ? new ElementLocation(currentPreviewLine.getLineIndex(), currentXIndex)
            : null;
    }

    /**
     * Returns whether the given line currently has the preview element.
     */
    static boolean hasPreviewElement(LineComponent lc) {
        return currentPreviewLine == lc;
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
     * Returns whether the given element is a glissando placeholder (the insertion
     * element created when a glissando tool is selected).
     */
    static boolean isGlissandoPlaceholder(@Nullable StaffElement element) {
        return element != null && element.getType() == ElementType.GLISSANDO;
    }

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
     * Computes the glissando zone type for the given intended type.
     * <p>
     * Returns null if the mouse is to the left of the first note (no source note),
     * or if {@code intendedType} is null. Otherwise validates whether the given type
     * can be inserted at the given index: CONNECTED requires a pitched note to the
     * right with a different pitch, SLIDE_OUT only requires a pitched source note.
     *
     * @param line         The line containing the notes
     * @param xIndex       Insertion index from {@link LayoutResult#findInsertionIndex}
     * @param intendedType The glissando type to validate, or null
     * @return The zone type, or null if no valid zone
     */
    static StaffElement.Glissando.@Nullable Type computeGlissandoZone(
        Line line,
        int xIndex,
        StaffElement.Glissando.@Nullable Type intendedType) {
        // xIndex=0 means to the left of the first note — no source note to draw from
        if (xIndex <= 0 || line.elementCount() == 0) {
            return null;
        }

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
     * Handles mouse movement over a line, updating preview element position.
     * Replaces the preview element logic formerly inline in {@code LineComponent.mouseMoved()}.
     */
    static void trackMouse(LineComponent lc, MouseEvent e) {
        if (e.isAltDown()) {
            clearPreviewElement();
            return;
        }

        if (!shouldHandlePreviewElement(lc)) {
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

        currentMouseXSs = mouseXss;

        // Calculate Y position from mouse (in staff-space coordinates)
        int staffPosition = calculateStaffPositionFromMouse(mouseYss, lc.getMiddleLineYSs());

        if (!isValidStaffPosition(staffPosition)) {
            // Mouse is outside valid range, clear insertion note if on this line
            if (currentPreviewLine == lc) {
                clearPreviewElement();
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

        // In grace mode the locked x coincides with an existing note that will be
        // shifted (not replaced), so suppress the element-at-x match to avoid
        // painting it red as if it were the replacement target.
        boolean inGraceMode = editModeManager != null
            && editModeManager.getGraceModeManager().isInProgress();
        int elementAtX = inGraceMode ? -1 : layoutResult.findElementAtXSs(mouseXss, line);

        // Suppress preview over the song's auto-maintained terminal (unless the
        // active preview element can legally replace it — exemption in isPositionBlockedByTerminal).
        var song = line.getSong();

        if (isPositionBlockedByTerminal(song, line, xIndex, elementAtX >= 0)) {
            clearPreviewElement();
            return;
        }

        var previewElement = editModeManager != null ? editModeManager.getPreviewElement() : null;

        // Hide the preview element when a grace note is selected and the mouse
        // is between an existing grace/host pair — insertion there is not allowed.
        if (previewElement != null
            && previewElement.getType().isGraceNote()
            && line.isInsideGraceHostPair(xIndex)) {
            if (currentPreviewLine == lc) {
                clearPreviewElement();
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

        if (isGlissandoPlaceholder(previewElement) && elementAtX < 0) {
            var intendedType = getSelectedGlissandoType();

            if (intendedType != null) {
                newGlissandoZone = computeGlissandoZone(line, xIndex, intendedType);
            }
        }

        // Check if position actually changed
        if (lc == currentPreviewLine && xIndex == currentXIndex
            && staffPosition == currentStaffPosition
            && newXMatch == xPosSsMatchesElement && newYMatch == yPosSpMatchesElement
            && newGlissandoZone == currentGlissandoZone) {
            return;  // No change, no repaint
        }

        // Repaint old line if different
        if (currentPreviewLine != null && currentPreviewLine != lc) {
            currentPreviewLine.repaint();
        }

        // Update static state
        currentPreviewLine = lc;
        currentXIndex = xIndex;
        currentStaffPosition = staffPosition;
        xPosSsMatchesElement = newXMatch;
        yPosSpMatchesElement = newYMatch;
        currentGlissandoZone = newGlissandoZone;

        if (editModeManager != null) {
            if (isGlissandoPlaceholder(previewElement)) {
                // No note-head preview for glissando tool — renderPreviewElement draws the preview line.
                lc.repaint();
                return;
            }

            // Always show the ghost preview — even when hovering over an existing element head.
            // The preview shows the user what pitch/type will replace the existing element.
            editModeManager.setPreviewElementVisible(true);

            // Rests snap to their default staff position; pitched notes follow the mouse Y
            if (previewElement != null) {
                applyStaffPosition(previewElement, staffPosition);
            }
        }

        // Repaint this line
        lc.repaint();
    }

    /**
     * Handles a click on the preview element, performing the appropriate action
     * (append, insert, or modify). Called from {@code LineComponent.mouseClicked()}.
     */
    public static void handleClick(LineComponent lc) {
        handleClick(lc, false);
    }

    /**
     * Like {@link #handleClick(LineComponent)}, but when {@code forceInsert} is {@code true}
     * always inserts rather than modifying an existing element at the same x position.
     * Used by grace mode, which must insert a new host note even when an existing note
     * occupies the locked insertion slot.
     */
    public static void handleClick(LineComponent lc, boolean forceInsert) {
        if (!shouldHandlePreviewElement(lc)) {
            return;
        }

        // Only handle if this line has the preview element
        var line = lc.getLine();

        if (currentPreviewLine != lc || line == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            var previewElement = editModeManager.getPreviewElement();

            if (isGlissandoPlaceholder(previewElement)) {
                var zoneType = currentGlissandoZone;

                if (zoneType == null) {
                    return;  // No valid zone = click is a no-op
                }

                var noteIndex = currentXIndex - 1;
                var sourceNote = line.getElement(noteIndex);

                line.withModification(() -> line.modifyElement(
                    noteIndex,
                    ElementField.GLISSANDO,
                    () -> sourceNote.setGlissando(zoneType)
                ));
                lc.repaint();
                return;  // Stay in glissando mode
            }
        }

        // Belt-and-braces: block clicks that would try to insert past the auto-maintained
        // terminal. trackMouse already clears the preview at these positions.
        var song = line.getSong();

        if (isPositionBlockedByTerminal(song, line, currentXIndex, xPosSsMatchesElement)) {
            return;
        }

        // Route a direct click on the terminal to replaceTerminal when the active preview
        // element can legally replace it. This bypasses the normal insertion path entirely.
        if (xPosSsMatchesElement && editModeManager != null) {
            var previewElement = editModeManager.getPreviewElement();

            if (previewElement != null
                    && isDirectClickOnTerminal(song, line, currentXIndex)
                    && song.canReplaceTerminal(previewElement.getType())) {
                song.replaceTerminal(previewElement.getType());
                return;
            }
        }

        var wasFirstLineEmpty = song.indexOfLine(line) == 0
            && line.effectiveElementCount() == 0;

        // Determine action based on position. Wrap in a modification bracket so the
        // line.add/setElement calls inside actually accumulate mutations and fire a
        // SongDidChangeNotification, which the ScoreMessageCoordinator uses to
        // invalidate the line's cached layout.
        line.withModification(() -> {
            if (currentXIndex == line.elementCount()) {
                addPreviewElement(lc, line);
            } else if (!forceInsert && xPosSsMatchesElement) {
                modifyExistingElement(lc, currentXIndex, line);
            } else {
                insertElement(lc, currentXIndex, line);
            }
        });

        if (wasFirstLineEmpty && line.effectiveElementCount() == 1) {
            TempoChangeDialog.showForElement(line.getElement(0), line);
        }
    }

    /**
     * Handles mouse entering a line. Sets up cursor and preview element visibility.
     */
    static void mouseEnteredLine(LineComponent lc) {
        currentMouseLine = lc;

        var editModeManager = EditModeManager.getInstance();

        if (shouldHandlePreviewElement(lc) && editModeManager != null) {
            var previewElement = editModeManager.getPreviewElement();

            if (!isGlissandoPlaceholder(previewElement)) {
                editModeManager.setPreviewElementVisible(true);
            }
        }
    }

    /**
     * Handles mouse exiting a line. Clears cursor and preview element.
     */
    static void mouseExitedLine(LineComponent lc) {
        currentMouseLine = null;
        lc.setCursor(Cursor.getDefaultCursor());

        // Clear preview element when mouse leaves this line
        if (currentPreviewLine == lc) {
            clearPreviewElement();
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setPreviewElementVisible(false);
        }
    }

    // ==========================================================================
    // Internal Helpers
    // ==========================================================================

    /**
     * Returns {@code true} when {@code xIndex} points to the auto-maintained terminal
     * element on {@code line}.
     */
    private static boolean isDirectClickOnTerminal(Song song, Line line, int xIndex) {
        return xIndex < line.elementCount()
            && song.isAutoMaintainedTerminal(line.getElement(xIndex), line);
    }

    /**
     * Returns {@code true} when the given mouse position should be blocked because
     * it coincides with the song's auto-maintained terminal element.
     * Checks both "mouse is on the terminal element" and "mouse is at the
     * append position immediately after the terminal".
     * <p>
     * When the mouse is directly on the terminal and the active preview element can
     * legally replace it, the block is lifted so the user can see the ghost preview.
     */
    private static boolean isPositionBlockedByTerminal(
        Song song, Line line, int xIndex, boolean xMatchesElement) {
        if (line.elementCount() == 0) {
            return false;
        }

        var lastIdx = line.elementCount() - 1;
        var terminalElement = line.getElement(lastIdx);

        if (!song.isAutoMaintainedTerminal(terminalElement, line)) {
            return false;
        }

        if (xMatchesElement && xIndex == lastIdx) {
            // Mouse is directly on the terminal. Lift the block when the active preview
            // element can legally replace it; otherwise keep it blocked.
            var previewElement = getActivePreviewElement();
            return previewElement == null
                || !song.canReplaceTerminal(previewElement.getType());
        }

        // Mouse is at the append slot immediately after the terminal — always blocked.
        return xIndex == line.elementCount();
    }

    /**
     * Returns whether preview element handling should be active for the given line.
     * <p>
     * Requires: edit mode enabled, MOUSE control, NOTE_EDIT mode, and a preview element set.
     */
    private static boolean shouldHandlePreviewElement(LineComponent lc) {
        var editModeManager = EditModeManager.getInstance();

        if (!lc.isEditMode() || editModeManager == null || !editModeManager.hasPreviewElement()) {
            return false;
        }

        var score = lc.getScore();

        if (score == null) {
            return false;
        }

        return score.getControl() == Control.MOUSE
            && score.getMode() == Mode.EDIT
            && !PlaybackController.isPlaying();
    }

    /**
     * Restores the preview element from the current mouse position on the given line.
     * Called when state changes (mode, playback, Alt key) may affect preview element visibility.
     */
    public static void restorePreviewElement(@Nullable LineComponent lc) {
        if (lc == null || !shouldHandlePreviewElement(lc)) {
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
        return StaffExtents.ssToSp(mouseYss - middleLineYSs);
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
        return staffPosition >= StaffExtents.MIN_STAFF_POSITION_SP
            && staffPosition <= StaffExtents.MAX_STAFF_POSITION_SP;
    }

    // ==========================================================================
    // Element Mutation Methods
    // ==========================================================================

    /**
     * Returns the active preview element from {@link EditModeManager},
     * or null if the manager or element is unavailable.
     */
    @Nullable
    private static StaffElement getActivePreviewElement() {
        var editModeManager = EditModeManager.getInstance();

        if (editModeManager == null) {
            return null;
        }

        return editModeManager.getPreviewElement();
    }

    /**
     * Calculates the insertion result for adding an element to a line.
     * If the element would not fit within the line width, shows an error
     * message and returns null.
     *
     * @param line    The line to insert into
     * @param element The element to insert
     * @param index   The insertion index
     * @param layout  Layout result for position lookup; null falls back to {@code xOffset}
     * @return The insertion result, or null if the line is full
     */
    private static InsertionSpacingCalculator.@Nullable InsertionResult calculateInsertionOrShowError(
        Line line, StaffElement element, int index, @Nullable LayoutResult layout
    ) {
        var insertion = InsertionSpacingCalculator.calculateInsertion(line, element, index, layout);
        var song = line.getSong();

        if (!insertion.fitsWithinLine(song.getLineWidthSs())) {
            OptionDialogs.showErrorMessage(
                null,
                Strings.ALERT_TITLE_INSERT_ERROR,
                Strings.ERROR_LINE_FULL
            );
            return null;
        }

        return insertion;
    }

    /**
     * Adds a preview element to the end of the line.
     *
     * @param lc   The LineComponent
     * @param line The line to add the element to
     */
    private static void addPreviewElement(LineComponent lc, Line line) {
        var score = lc.getScore();

        if (score == null) {
            return;
        }

        var previewElement = getActivePreviewElement();

        if (previewElement == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null && editModeManager.elementWasModified(line, line.elementCount())) {
            editModeManager.previewElementDidChange(line, line.elementCount() - 1);
            return;
        }

        var insertion = calculateInsertionOrShowError(line, previewElement, line.elementCount(), lc.getLayoutResult());

        if (insertion == null) {
            return;
        }

        previewElement.setXOffsetPx(ScaleContext.getInstance().toRoundedPixels(insertion.insertedElementXSs()));
        line.addElement(previewElement);

        applyAutomaticBeaming(line, line.elementCount() - 1);

        if (editModeManager != null) {
            editModeManager.previewElementDidChange(line, line.elementCount() - 1);
        }
    }

    /**
     * Inserts a preview element at the specified index in the line.
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

        var previewElement = getActivePreviewElement();

        if (previewElement == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null && editModeManager.elementWasModified(line, xIndex)) {
            editModeManager.previewElementDidChange(line, xIndex);
            return;
        }

        // If inserting into a tuplet, remove it — the new element changes the rhythmic grouping.
        // The subsequent removeSpan call handles the remaining span sets (beamings, ties, etc.).
        var tuplet = line.getTuplets().findSpan(xIndex - 1);

        if ((tuplet != null) && ((xIndex - 1) < tuplet.getEnd())) {
            line.removeTuplet(tuplet);
        }

        line.removeSpan(xIndex - 1, xIndex);
        var insertion = calculateInsertionOrShowError(line, previewElement, xIndex, lc.getLayoutResult());

        if (insertion == null) {
            return;
        }

        previewElement.setXOffsetPx(ScaleContext.getInstance().toRoundedPixels(insertion.insertedElementXSs()));

        if (line.hasEndingInvalidatedByInsertion(xIndex, previewElement.getType())) {
            if (!EndingConfirms.confirmInvalidation()) {
                return;
            }
        }

        line.adjustSyllablesForNeighborChange(xIndex - 1, null);
        line.adjustExtendsForInsertion(xIndex);
        line.addElement(xIndex, previewElement);
        line.adjustSyllablesForSuccessorAfterInsertion(xIndex);
        var shift = ScaleContext.getInstance().toRoundedPixels(insertion.shiftForSubsequentElementsSs());

        for (var i = xIndex + 1; i < line.effectiveElementCount(); i++) {
            line.getElement(i).setXOffsetPx(line.getElement(i).getXOffsetPx() + shift);
        }

        applyAutomaticBeaming(line, xIndex);

        if (editModeManager != null) {
            editModeManager.previewElementDidChange(line, xIndex);
        }
    }

    /**
     * Applies automatic beaming for the element at the given index.
     * Scans backward from the element to find beamable neighbors and creates
     * a beam span if the rhythmic grouping conditions are met.
     *
     * @param line         The line containing the element
     * @param elementIndex The index of the just-inserted element
     */
    private static void applyAutomaticBeaming(Line line, int elementIndex) {
        var element = line.getElement(elementIndex);

        if (
            !element.getType().isBeamable() ||
                (elementIndex < 1) ||
                (line.getTuplets().findSpan(elementIndex - 1) != null)
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

            var span = line.getBeamings().findSpan(i);

            if ((span != null) && (span.getStart() == i)) {
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
                .addSpan(new BeamSpan(elementIndex - 1, elementIndex));
        }
    }

    /**
     * Replaces an existing element entirely with the current preview element.
     * The x position and lyrics are preserved from the existing element; all other attributes
     * (type, duration, dots, beaming, ties) come from the preview element.
     * Called when the user clicks on an existing element head with the preview element active.
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

        var previewElement = getActivePreviewElement();

        if (previewElement == null) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null && editModeManager.elementWasModified(line, elementIndex)) {
            editModeManager.previewElementDidChange(line, elementIndex);
            return;
        }

        // Preserve the existing element's x position and lyrics
        var existing = line.getElement(elementIndex);
        previewElement.setXOffsetPx(existing.getXOffsetPx());

        for (var lyric : existing.getLyrics()) {
            previewElement.setLyricForVerse(lyric.verse(), lyric.syllabic(), lyric.compound(), lyric.text(), lyric.extend());
        }

        // Rests snap to their default staff position; pitched notes use the mouse Y position
        applyStaffPosition(previewElement, currentStaffPosition);

        if (previewElement.isStemDirectionAuto()) {
            previewElement.setUpper(Score.defaultUpperNote(previewElement));
        }

        // Remove all beam spans touching this element — the new element type may differ
        var beam = line.getBeamings().findSpan(elementIndex);

        while (beam != null) {
            line.getBeamings().removeSpan(beam);
            beam = line.getBeamings().findSpan(elementIndex);
        }

        // Remove all tie spans touching this element
        var tie = line.getTies().findSpan(elementIndex);

        while (tie != null) {
            line.getTies().removeSpan(tie);
            tie = line.getTies().findSpan(elementIndex);
        }

        // Remove any containing tuplet if the duration type or dot count changes —
        // the replacement would make the tuplet rhythmically invalid.
        if (existing.getType() != previewElement.getType()
                || existing.getDotCount() != previewElement.getDotCount()) {
            line.removeOverlappingTuplets(elementIndex, elementIndex);
        }

        // Check whether replacing this element would affect a first-second ending,
        // and show the appropriate confirmation dialog if so.
        var endingEffect = line.findEndingReplacementEffect(elementIndex, previewElement);

        switch (endingEffect) {
            case Ending.EndingEffect.Invalidate _ -> {
                if (!EndingConfirms.confirmInvalidation()) {
                    return;
                }
                // proceed: line.setElement will remove the ending via isInvalidatedByReplacement
            }
            case Ending.EndingEffect.CompensateEnd ce -> {
                if (!EndingConfirms.confirmCompensateEnd(ce)) {
                    return;
                }
                EndingConfirms.applyCompensatingEndChange(line, ce);
            }
            case Ending.EndingEffect.CompensateSplit cs -> {
                if (!EndingConfirms.confirmCompensateSplit(cs, previewElement.getType())) {
                    return;
                }
                EndingConfirms.applyCompensatingSplitChange(line, cs);
            }
            case Ending.EndingEffect.None _ -> {}
        }

        // Replace the element entirely (line.setElement marks the song modified)
        line.setElement(elementIndex, previewElement);

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
                && !previewElement.getType().isPitchedNote()) {
            line.removeElement(elementIndex - 1);
            elementIndex--;
        }

        if (editModeManager != null) {
            editModeManager.previewElementDidChange(line, elementIndex);
        }
    }
}
