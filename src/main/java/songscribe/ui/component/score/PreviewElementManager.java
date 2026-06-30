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
import songscribe.dom.Beam;
import songscribe.dom.Song;
import songscribe.dom.ElementLocation;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.Line;
import songscribe.dom.SlideZone;
import songscribe.dom.StaffElement;
import songscribe.ui.Control;
import songscribe.ui.EndingConfirms;
import songscribe.ui.component.MainFrame;
import songscribe.ui.dialog.TempoChangeDialog;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.EditModeManager;
import songscribe.layout.Ending;
import songscribe.layout.LineEndingSupport;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.dom.ScaleContext;
import songscribe.message.notification.ModeDidChangeNotification;
import songscribe.message.notification.PlaybackStateDidChangeNotification;
import songscribe.layout.StaffExtents;
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
    private static LineComponent currentPreviewLine = null;

    /** Current insertion index (0 to elementCount inclusive). */
    private static int currentXIndex = -1;

    /** Current Y position on the staff (in staff position units, not pixels). */
    private static int currentStaffPosition = 0;

    /** The LineComponent the mouse is currently over (independent of insertion note state). */
    @Nullable
    private static LineComponent currentMouseLine = null;

    /** Whether the mouse X (in staff spaces) is within the horizontal bounds of a note head. */
    private static boolean xPosSsMatchesElement = false;

    /** Whether the mouse Y (staff position) is within the vertical bounds of that note head. */
    private static boolean yPosSpMatchesElement = false;

    /** The slide zone determined by mouse position (null if no valid zone). */
    private static @Nullable SlideZone currentSlideZone = null;

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
            EditModeManager.setPreviewElement(null);
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

        EditModeManager.setPreviewElementVisible(false);
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
            currentSlideZone = null;
            oldLine.repaint();
        }
    }

    /**
     * Sets whether the Alt key is currently pressed.
     *
     * @param pressed true if Alt is pressed
     */
    static void setAltPressed(boolean pressed) {
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
        if (!xPosSsMatchesElement || currentPreviewLine == null) {
            return null;
        }

        // A breath mark never replaces an existing element, so it never highlights one
        // as a replacement target.
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement != null && previewElement.getType().isBreathMark()) {
            return null;
        }

        // A slide tool never replaces an existing element either — it attaches a
        // slide to a note. Instead of the red replacement highlight, the connected
        // notes are drawn in the preview color (see isSlidePreviewNote).
        if (isSlidePlaceholder(previewElement)) {
            return null;
        }

        return new ElementLocation(currentPreviewLine.getLineIndex(), currentXIndex);
    }

    /**
     * The notes a previewed slide would connect to, and the line they live on. A connecting
     * glissando highlights both the source note and the target note to its right; a fall
     * highlights only the source, so {@code targetIndex} is -1. {@link #NONE} means no preview.
     */
    public record SlidePreviewNotes(int lineIndex, int sourceIndex, int targetIndex) {
        public static final SlidePreviewNotes NONE = new SlidePreviewNotes(-1, -1, -1);

        /** Returns whether the element at {@code (atLineIndex, atElementIndex)} is highlighted. */
        public boolean highlights(int atLineIndex, int atElementIndex) {
            if (atLineIndex != lineIndex) {
                return false;
            }

            // sourceIndex/targetIndex are -1 when absent (a fall has no target; NONE has
            // neither). Guard so a negative query index never matches an absent endpoint.
            return (sourceIndex >= 0 && atElementIndex == sourceIndex)
                || (targetIndex >= 0 && atElementIndex == targetIndex);
        }
    }

    /**
     * Resolves which notes the currently previewed slide would connect to. Computed once so a
     * caller can reuse it across every element on a line instead of re-resolving per element.
     * <p>
     * Returns {@link SlidePreviewNotes#NONE} when no slide preview is being shown. The
     * conditions mirror those in {@link LineRenderer#renderPreviewElement}: the highlight is only
     * shown when the preview slide itself is drawn, which excludes the case where the source
     * note already carries this slide type.
     */
    public static SlidePreviewNotes getSlidePreviewNotes() {
        if (!shouldShowSlidePreview() || currentPreviewLine == null) {
            return SlidePreviewNotes.NONE;
        }

        // shouldShowSlidePreview() above already guarantees the zone is non-null.
        var type = currentSlideZone;
        var line = currentPreviewLine.getLine();
        var sourceIndex = currentXIndex - 1;

        if (line == null || sourceIndex < 0) {
            return SlidePreviewNotes.NONE;
        }

        // No preview line is drawn (and thus no highlight) when the source note already
        // carries this slide type.
        if (sourceAlreadyHasSlide(line, sourceIndex, type)) {
            return SlidePreviewNotes.NONE;
        }

        // A connecting glissando also highlights the target note immediately to the right.
        var targetIndex = type == SlideZone.GLISSANDO ? currentXIndex : -1;

        return new SlidePreviewNotes(currentPreviewLine.getLineIndex(), sourceIndex, targetIndex);
    }

    /**
     * Returns whether the note at {@code (lineIndex, elementIndex)} is one the currently previewed
     * slide would connect to. Prefer {@link #getSlidePreviewNotes()} when checking several
     * elements on the same line so the resolution runs only once.
     */
    public static boolean isSlidePreviewNote(int lineIndex, int elementIndex) {
        return getSlidePreviewNotes().highlights(lineIndex, elementIndex);
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
     * Returns whether a slide preview line should be drawn.
     * <p>
     * True when the mouse is in a valid slide zone (not over a note head, not to the
     * left of the first note). The preview is shown for both insertion and removal modes.
     */
    public static boolean shouldShowSlidePreview() {
        return currentSlideZone != null;
    }

    /**
     * Returns the current slide zone type, or null if no valid zone exists.
     */
    static @Nullable SlideZone getSlideZone() {
        return currentSlideZone;
    }

    /**
     * Sets the current insertion X index (0 to elementCount inclusive).
     */
    static void setCurrentXIndex(int index) {
        currentXIndex = index;
    }

    /**
     * Sets the current preview line.
     */
    static void setCurrentPreviewLine(@Nullable LineComponent line) {
        currentPreviewLine = line;
    }

    /**
     * Sets the current Y position on the staff (in staff position units, not pixels).
     */
    static void setCurrentStaffPosition(int position) {
        currentStaffPosition = position;
    }

    /**
     * Returns whether the mouse X (in staff spaces) is within the horizontal bounds of a note head.
     */
    static boolean isXPosSsMatchesElement() {
        return xPosSsMatchesElement;
    }

    /**
     * Sets whether the mouse X (in staff spaces) is within the horizontal bounds of a note head.
     */
    static void setXPosSsMatchesElement(boolean matches) {
        xPosSsMatchesElement = matches;
    }

    /**
     * Sets the current slide zone type (package-private for test setup).
     */
    static void setCurrentSlideZone(@Nullable SlideZone zone) {
        currentSlideZone = zone;
    }

    // ==========================================================================
    // Private Helpers
    // ==========================================================================

    /**
     * Returns whether the given element is a slide placeholder (the insertion
     * element created when a slide tool is selected).
     */
    static boolean isSlidePlaceholder(@Nullable StaffElement element) {
        return element != null && element.getType() == ElementType.SLIDE;
    }

    /**
     * Returns whether the source note at {@code sourceIndex} on {@code line} already carries the
     * slide {@code zone} represents. When it does, no preview slide is drawn (and thus no preview
     * highlight) — the note already has what the tool would add. A null {@code zone} (no zone)
     * trivially matches nothing.
     */
    static boolean sourceAlreadyHasSlide(
        Line line, int sourceIndex, @Nullable SlideZone zone
    ) {
        return zone != null && zone.matches(line.getElement(sourceIndex));
    }

    /**
     * Returns the slide zone corresponding to the currently selected action,
     * or null if neither slide action is selected.
     */
    private static @Nullable SlideZone getSelectedSlideZone() {
        var selected = Actions.DURATION_ACTION_GROUP.getSelected();

        if (selected instanceof ElementTypeAction eta) {
            return eta.getSlideZone();
        }

        return null;
    }

    /**
     * Computes the slide zone type for the given intended type.
     * <p>
     * Returns null if the mouse is to the left of the first note (no source note),
     * or if {@code intendedZone} is null. Otherwise validates whether the given zone
     * can be inserted at the given index: {@code GLISSANDO} requires a pitched note to the
     * right with a different pitch, {@code FALL} only requires a pitched source note.
     * Only pitched notes can be a slide source or target — bar lines, grace
     * notes, rests, and other non-pitched elements are rejected.
     *
     * @param line         The line containing the notes
     * @param xIndex       Insertion index from {@link LayoutResult#findInsertionIndex}
     * @param intendedZone The slide zone to validate, or null
     * @return The zone, or null if no valid zone
     */
    static @Nullable SlideZone computeSlideZone(
        Line line,
        int xIndex,
        @Nullable SlideZone intendedZone) {
        // xIndex=0 means to the left of the first note — no source note to draw from
        if (xIndex <= 0 || line.elementCount() == 0) {
            return null;
        }

        if (intendedZone == null) {
            return null;
        }

        // Only pitched notes can be a slide source or target
        var sourceElement = line.getElement(xIndex - 1);

        if (!sourceElement.getType().isPitchedNote()) {
            return null;
        }

        if (intendedZone == SlideZone.GLISSANDO) {
            // A connecting glissando requires a note to the right
            if (xIndex >= line.elementCount()) {
                return null;
            }

            var targetElement = line.getElement(xIndex);

            // Target must be a pitched note (bar lines, grace notes, rests, etc. are invalid)
            if (!targetElement.getType().isPitchedNote()) {
                return null;
            }

            // Same-pitch connected glissando is musically meaningless
            if (sourceElement.getPitch() == targetElement.getPitch()) {
                return null;
            }
        }

        return intendedZone;
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
        var mouseYss = ScaleContext.pxToSs(e.getY());

        // In grace mode, lock the x-position to the host note slot
        var graceModeManager = EditModeManager.getGraceModeManager();
        double mouseXss;

        if (graceModeManager.isInProgress()) {
            mouseXss = graceModeManager.getLockedInsertionXSs();
        } else {
            mouseXss = ScaleContext.pxToSs(e.getX());
        }

        currentMouseXSs = mouseXss;

        // Calculate Y position from mouse (in staff-space coordinates)
        var staffPosition = calculateStaffPositionFromMouse(mouseYss, lc.getMiddleLineYSs());

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

        var xIndex = layoutResult.findInsertionIndex(mouseXss, line);

        // In grace mode the locked x coincides with an existing note that will be
        // shifted (not replaced), so suppress the element-at-x match to avoid
        // painting it red as if it were the replacement target.
        var inGraceMode = graceModeManager.isInProgress();
        var elementAtX = inGraceMode ? -1 : layoutResult.findElementAtXSs(mouseXss, line);

        // Suppress preview over the song's auto-maintained terminal (unless the
        // active preview element can legally replace it — exemption in isPositionBlockedByTerminal).
        var song = line.getSong();

        if (isPositionBlockedByTerminal(song, line, xIndex, elementAtX >= 0)) {
            clearPreviewElement();
            return;
        }

        var previewElement = EditModeManager.getPreviewElement();

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
        var newXMatch = elementAtX >= 0;
        var newYMatch = newXMatch
            && Math.abs(staffPosition - line.getElement(elementAtX).getStaffPosition()) <= 1;

        // A breath mark must be inserted between or after a note or rest; it may not sit at
        // index 0 or over an existing element (it never replaces one). At any such position
        // the ghost preview is hidden, and clicking there shows an alert. The red "will be
        // replaced" highlight is separately suppressed for breath marks in
        // getHoveredElementLocation, so newXMatch stays raw for the over-element-head check.
        var breathMarkBlocked = isBreathMarkInsertionBlocked(previewElement, xIndex, line, newXMatch);

        // Compute slide zone before change detection so zone changes trigger repaints.
        // Only compute when not over an element head (elementAtX < 0), as hovering over an
        // element head means there is no valid slide target to the left.
        SlideZone newSlideZone = null;

        if (isSlidePlaceholder(previewElement) && elementAtX < 0) {
            var intendedZone = getSelectedSlideZone();

            if (intendedZone != null) {
                newSlideZone = computeSlideZone(line, xIndex, intendedZone);
            }
        }

        // Check if position actually changed
        if (lc == currentPreviewLine && xIndex == currentXIndex
            && staffPosition == currentStaffPosition
            && newXMatch == xPosSsMatchesElement && newYMatch == yPosSpMatchesElement
            && newSlideZone == currentSlideZone) {
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
        currentSlideZone = newSlideZone;

        if (isSlidePlaceholder(previewElement)) {
            // No note-head preview for slide tool — renderPreviewElement draws the preview line.
            lc.repaint();
            return;
        }

        // Always show the ghost preview — even when hovering over an existing element head.
        // The preview shows the user what pitch/type will replace the existing element.
        // Exception: breath marks must follow a note or rest, so suppress the ghost
        // wherever that is not the case.
        EditModeManager.setPreviewElementVisible(!breathMarkBlocked);

        // Rests snap to their default staff position; pitched notes follow the mouse Y
        if (previewElement != null) {
            applyStaffPosition(previewElement, staffPosition);
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

        var previewElement = EditModeManager.getPreviewElement();

        if (isSlidePlaceholder(previewElement)) {
            var zone = currentSlideZone;

            if (zone == null) {
                return;  // No valid zone = click is a no-op
            }

            var noteIndex = currentXIndex - 1;

            if (zone == SlideZone.FALL
                    && !InsertionSpacingCalculator.hasRoomForFall(line, noteIndex, lc.getLayoutResult())) {
                OptionDialogs.showErrorMessage(
                    null,
                    Strings.ALERT_TITLE_INSERT_ERROR,
                    Strings.ERROR_LINE_FULL_FALL
                );
                return;
            }

            var sourceNote = line.getElement(noteIndex);

            line.withModification(() -> line.modifyElement(
                noteIndex,
                ElementField.SLIDE,
                () -> zone.applyTo(sourceNote)
            ));
            lc.repaint();
            return;  // Stay in slide mode
        }

        // Belt-and-braces: block clicks that would try to insert past the auto-maintained
        // terminal. trackMouse already clears the preview at these positions.
        var song = line.getSong();

        if (isPositionBlockedByTerminal(song, line, currentXIndex, xPosSsMatchesElement)) {
            return;
        }

        // Route a direct click on the terminal to replaceTerminal when the active preview
        // element can legally replace it. This bypasses the normal insertion path entirely.
        if (xPosSsMatchesElement) {
            if (previewElement != null
                    && isDirectClickOnTerminal(song, line, currentXIndex)) {
                var previewType = previewElement.getType();

                if (song.canReplaceTerminal(previewType)) {
                    song.replaceTerminal(previewType);
                    return;
                }
            }
        }

        if (isBreathMarkInsertionBlocked(previewElement, currentXIndex, line, xPosSsMatchesElement)) {
            OptionDialogs.showErrorMessage(null, Strings.ALERT_TITLE_BREATH_MARK, Strings.ALERT_BREATH_MARK_POSITION);
            return;
        }

        var wasFirstLineEmpty = song.indexOfLine(line) == 0
            && line.effectiveElementCount() == 0;

        // Determine action based on position. Wrap in a modification bracket so the
        // line.add/setElement calls inside actually accumulate mutations and fire a
        // SongDidChangeNotification, which the ScoreViewController uses to
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
            TempoChangeDialog.showForElement(MainFrame.getInstance(), line.getElement(0), line);
        }
    }

    /**
     * Handles mouse entering a line. Sets up cursor and preview element visibility.
     */
    static void mouseEnteredLine(LineComponent lc) {
        currentMouseLine = lc;

        if (shouldHandlePreviewElement(lc)) {
            var previewElement = EditModeManager.getPreviewElement();

            if (!isSlidePlaceholder(previewElement)) {
                EditModeManager.setPreviewElementVisible(true);
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

        EditModeManager.setPreviewElementVisible(false);
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
        var elementCount = line.elementCount();

        if (elementCount == 0) {
            return false;
        }

        var lastIdx = elementCount - 1;
        var terminalElement = line.getElement(lastIdx);

        if (!song.isAutoMaintainedTerminal(terminalElement, line)) {
            return false;
        }

        if (xMatchesElement && xIndex == lastIdx) {
            // Mouse is directly on the terminal. Lift the block when the active preview
            // element can legally replace it; otherwise keep it blocked.
            var previewElement = EditModeManager.getPreviewElement();
            return previewElement == null
                || !song.canReplaceTerminal(previewElement.getType());
        }

        // Mouse is at the append slot immediately after the terminal — always blocked.
        return xIndex == elementCount;
    }

    /**
     * Returns {@code true} when inserting a breath mark at {@code xIndex} should be
     * blocked. A breath mark attaches to the element immediately before it, which must
     * be a pitched note or a rest, and it never replaces an existing element. So it is
     * blocked at index 0 (no preceding element), directly after anything other than a
     * note or rest, while the cursor is over an existing element
     * ({@code overExistingElement}), or when the following element is already a breath
     * mark (consecutive breath marks are forbidden).
     */
    static boolean isBreathMarkInsertionBlocked(
        @Nullable StaffElement previewElement, int xIndex, Line line, boolean overExistingElement
    ) {
        if (previewElement == null || !previewElement.getType().isBreathMark()) {
            return false;
        }

        if (overExistingElement || xIndex == 0) {
            return true;
        }

        var precedingType = line.getElement(xIndex - 1).getType();

        if (!precedingType.isDuration()) {
            return true;
        }

        return xIndex < line.effectiveElementCount() && line.getElement(xIndex).getType().isBreathMark();
    }

    /**
     * Returns whether preview element handling should be active for the given line.
     * <p>
     * Requires: edit mode enabled, MOUSE control, NOTE_EDIT mode, and a preview element set.
     */
    private static boolean shouldHandlePreviewElement(LineComponent lc) {
        if (!lc.isEditMode() || !EditModeManager.hasPreviewElement()) {
            return false;
        }

        var scoreView = lc.getScoreView();

        return scoreView.getControl() == Control.MOUSE
            && scoreView.getMode() == Mode.EDIT
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
    static void applyStaffPosition(StaffElement element, int staffPositionSp) {
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
                Strings.ERROR_LINE_FULL_NOTE
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
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement == null) {
            return;
        }

        var elementCount = line.elementCount();

        if (EditModeManager.elementWasModified(line, elementCount)) {
            EditModeManager.previewElementDidChange(line, elementCount - 1);
            return;
        }

        var insertion = calculateInsertionOrShowError(line, previewElement, elementCount, lc.getLayoutResult());

        if (insertion == null) {
            return;
        }

        previewElement.setXOffsetPx(ScaleContext.ssToRoundedPx(insertion.insertedElementXSs()));
        line.addElement(previewElement);

        var newLastIndex = line.elementCount() - 1;
        applyAutomaticBeaming(line, newLastIndex);
        EditModeManager.previewElementDidChange(line, newLastIndex);
    }

    @Nullable
    private static StaffElement validateAndGetPreviewElement(Line line, int elementIndex) {
        var previewElement = EditModeManager.getPreviewElement();

        if (previewElement == null) {
            return null;
        }

        if (EditModeManager.elementWasModified(line, elementIndex)) {
            EditModeManager.previewElementDidChange(line, elementIndex);
            return null;
        }

        return previewElement;
    }

    private static void insertElement(LineComponent lc, int xIndex, Line line) {
        var previewElement = validateAndGetPreviewElement(line, xIndex);

        if (previewElement == null) {
            return;
        }

        // If inserting into a tuplet, remove it — the new element changes the rhythmic grouping.
        var tuplet = line.findTupletAt(xIndex - 1);

        if ((tuplet != null) && ((xIndex - 1) < tuplet.getEndElementIndex())) {
            line.removeTuplet(tuplet);
        }

        var insertion = calculateInsertionOrShowError(line, previewElement, xIndex, lc.getLayoutResult());

        if (insertion == null) {
            return;
        }

        previewElement.setXOffsetPx(ScaleContext.ssToRoundedPx(insertion.insertedElementXSs()));

        if (line.hasEndingInvalidatedByInsertion(xIndex, previewElement.getType())) {
            if (!EndingConfirms.confirmInvalidation(lc)) {
                return;
            }
        }

        line.adjustSyllablesForNeighborChange(xIndex - 1, null);
        line.adjustExtendsForInsertion(xIndex);
        line.addElement(xIndex, previewElement);
        line.adjustSyllablesForSuccessorAfterInsertion(xIndex);

        // A connecting glissando joins a note to the note that immediately follows it.
        // Inserting another pitched note simply re-targets it, but inserting anything else
        // (rest, breath mark, grace note) leaves it with no valid target, so remove it from
        // the preceding note.
        if (!previewElement.getType().isPitchedNote() && xIndex > 0) {
            var precedingElement = line.getElement(xIndex - 1);

            if (precedingElement.hasGlissando()) {
                precedingElement.removeSlide();
            }
        }
        var shift = ScaleContext.ssToRoundedPx(insertion.shiftForSubsequentElementsSs());

        for (var i = xIndex + 1; i < line.effectiveElementCount(); i++) {
            var element = line.getElement(i);
            element.setXOffsetPx(element.getXOffsetPx() + shift);
        }

        applyAutomaticBeaming(line, xIndex);

        EditModeManager.previewElementDidChange(line, xIndex);
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
        var insertedType = element.getType();

        if (
            !insertedType.isBeamable() ||
                (elementIndex < 1) ||
                (line.findTupletAt(elementIndex - 1) != null)
        ) {
            return;
        }

        var sum = 0;

        for (var i = elementIndex - 1; i >= 0; i--) {
            var elementType = line.getElement(i).getType();

            if (elementType == ElementType.QUAVER) {
                sum += 2;
            } else if (
                (elementType == ElementType.SEMIQUAVER) ||
                    (elementType == ElementType.DEMI_SEMIQUAVER)
            ) {
                sum += 1;
            } else {
                break;
            }

            var beam = line.findBeamAt(i);

            if ((beam != null) && (beam.getAnchorElementIndex() == i)) {
                break;
            }
        }

        if (
            ((insertedType == ElementType.QUAVER) &&
                (sum > 0) &&
                ((sum % 2) == 0) &&
                ((sum % 4) != 0)) ||
                (((insertedType == ElementType.SEMIQUAVER) ||
                    (insertedType == ElementType.DEMI_SEMIQUAVER)) &&
                    (sum > 0) &&
                    ((sum % 4) != 0))
        ) {
            line.addBeaming(new Beam(
                line.getElement(elementIndex - 1),
                line.getElement(elementIndex)
            ));
        }
    }

    /**
     * Replaces an existing element with the current preview element's type and pitch.
     * Note-entry decorations the user sets on the preview element via the toolbar/menu
     * (accidental, dot count, articulations, fermata) are taken from the preview. Other
     * decorations not settable on the preview (trill, annotation, tempo/beat change,
     * dynamic attachments, lyrics, x position) are preserved from the existing element.
     * Called when the user clicks on an existing element head with the preview element active.
     *
     * @param lc           The LineComponent
     * @param elementIndex The index of the element to replace
     * @param line         The line containing the element
     */
    private static void modifyExistingElement(LineComponent lc, int elementIndex, Line line) {
        var previewElement = validateAndGetPreviewElement(line, elementIndex);

        if (previewElement == null) {
            return;
        }

        // Deep-copy the existing element under the new type to carry over all decorations
        // (fermata, trill, annotation, tempo/beat change, articulations, attachments, lyrics,
        // x position), then override with the preview's note-entry attributes.
        var existing = line.getElement(elementIndex);
        var previewType = previewElement.getType();
        var replacement = new StaffElement(previewType, existing);
        replacement.setDotCount(previewElement.getDotCount());
        replacement.setAccidental(previewElement.getAccidental());
        replacement.setAccidentalInParentheses(previewElement.isAccidentalInParentheses());
        replacement.setStemDirectionAuto(previewElement.isStemDirectionAuto());

        // Articulations and the fermata are note-entry decorations the user sets on the preview
        // element via the toolbar/menu, exactly like the accidental above. Carry them over from
        // the preview, overriding any inherited from the existing element. Other attachments
        // (dynamics, annotations, trills) are not preview-settable, so they remain copied from
        // the existing element by the copy constructor.
        replacement.copyArticulationsFrom(previewElement);
        replacement.setFermata(previewElement.findAttachment(FermataAttachment.class) != null);

        // Rests snap to their default staff position; pitched notes use the mouse Y position
        applyStaffPosition(replacement, currentStaffPosition);

        if (replacement.isStemDirectionAuto()) {
            replacement.setUpper(ScoreView.defaultUpperNote(replacement));
        } else {
            replacement.setUpper(previewElement.isUpper());
        }

        // Remove all beam spans touching this element — the new element type may differ
        var beam = line.findBeamAt(elementIndex);

        while (beam != null) {
            line.removeBeaming(beam);
            beam = line.findBeamAt(elementIndex);
        }

        // Remove all tie range elements touching this element
        var tie = line.findTieAt(elementIndex);

        while (tie != null) {
            line.removeTie(tie);
            tie = line.findTieAt(elementIndex);
        }

        // Remove any containing tuplet if the duration type or dot count changes —
        // the replacement would make the tuplet rhythmically invalid.
        if (existing.getType() != previewType
                || existing.getDotCount() != replacement.getDotCount()) {
            line.removeOverlappingTuplets(elementIndex, elementIndex);
        }

        // Check whether replacing this element would affect a first-second ending,
        // and show the appropriate confirmation dialog if so.
        var endingEffect = LineEndingSupport.findEndingReplacementEffect(line, elementIndex, replacement);

        switch (endingEffect) {
            case Ending.EndingEffect.Invalidate _ -> {
                if (!EndingConfirms.confirmInvalidation(lc)) {
                    return;
                }
                // proceed: line.setElement will remove the ending via isInvalidatedByReplacement
            }
            case Ending.EndingEffect.CompensateEnd ce -> {
                if (!EndingConfirms.confirmCompensateEnd(lc, ce)) {
                    return;
                }
                EndingConfirms.applyCompensatingEndChange(line, ce);
            }
            case Ending.EndingEffect.CompensateSplit cs -> {
                if (!EndingConfirms.confirmCompensateSplit(lc, cs, previewType)) {
                    return;
                }
                EndingConfirms.applyCompensatingSplitChange(line, cs);
            }
            case Ending.EndingEffect.None _ -> {}
        }

        // Replace the element entirely (line.setElement marks the song modified)
        line.setElement(elementIndex, replacement);

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
        if (line.isHostOfPairedGraceNote(elementIndex)
                && !previewType.isPitchedNote()) {
            line.removeElement(elementIndex - 1);
            elementIndex--;
        }

        EditModeManager.previewElementDidChange(line, elementIndex);
    }
}
