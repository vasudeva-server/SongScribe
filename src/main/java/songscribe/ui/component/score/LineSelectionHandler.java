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
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;

import songscribe.Strings;
import songscribe.dom.StaffElement;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;
import songscribe.ui.Mode;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.dom.ScaleContext;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.renderer.SlideRenderer;

/**
 * Handles selection, hit-testing, and drag logic for a {@link LineComponent}.
 * <p>
 * Each {@code LineComponent} owns one instance. Drag state (rectangle, start point)
 * lives here; mouse event handlers in {@code LineComponent} delegate to this class.
 */
class LineSelectionHandler {

    /** Half-height of the staff hit zone for line selection, in staff spaces. */
    private static final double STAFF_HIT_RADIUS_SS = 2.0;

    private final LineComponent lc;
    private boolean dragging = false;
    private boolean pressHandled = false;
    private HitResult pressHitResult = new HitResult.Nothing();
    private final Point dragStart = new Point();
    private final Rectangle dragRectangle = new Rectangle();

    LineSelectionHandler(LineComponent lc) {
        this.lc = lc;
    }

    // ======================================================================
    // Accessors
    // ======================================================================

    boolean isDragging() {
        return dragging;
    }

    Rectangle getDragRectangle() {
        return dragRectangle;
    }

    // ======================================================================
    // Hit testing
    // ======================================================================

    /**
     * Hit-tests the given point against all selectable elements in this line.
     * <p>
     * The cascade tests note heads first, then slides, then staff-line proximity.
     * If nothing is hit, {@link HitResult.Nothing} is returned.
     */
    HitResult hitTest(Point point) {
        var elementIndex = ElementHitTest.hitTestElement(lc, point);

        if (elementIndex != -1) {
            return new HitResult.ElementHead(elementIndex);
        }

        var clickXSs = ScaleContext.pxToSs(point.x);
        var clickYSs = ScaleContext.pxToSs(point.y);

        var slideIndex = hitTestSlideAtPoint(clickXSs, clickYSs);

        if (slideIndex != -1) {
            var selState = lc.getLineSelectionState();

            if (selState == null) {
                return new HitResult.Nothing();
            }

            var line = selState.getLine();

            if (line.getElement(slideIndex).getType().isGraceNote()) {
                return new HitResult.GraceGlissando();
            }

            return new HitResult.Slide(slideIndex);
        }

        if (Math.abs(clickYSs - lc.getMiddleLineYSs()) <= STAFF_HIT_RADIUS_SS
                && clickXSs <= headerRightEdgeSs()) {
            return new HitResult.StaffLine();
        }

        return new HitResult.Nothing();
    }

    // ======================================================================
    // Public delegation entry points
    // ======================================================================

    void handlePress(MouseEvent e) {
        dragging = false;
        pressHandled = false;
        dragStart.setLocation(e.getPoint());
        dragRectangle.setBounds(0, 0, 0, 0);

        pressHitResult = hitTest(e.getPoint());

        var lineSelectionState = lc.getLineSelectionState();

        // Don't clear selection on shift+click (preserve for extend)
        if (!e.isShiftDown() || lineSelectionState == null || lineSelectionState.getSelectionAnchor() == -1) {
            lc.getScoreView().clearSelection();

            if (MidiController.sequencer != null) {
                MidiController.sequencer.setTickPosition(0);
            }
        }

        // Select the hit element immediately on press.
        // This prevents rubber-band drag from starting on selectable elements.
        // Shift+click on a note head is handled in handleClick for extend-selection.
        if (e.isShiftDown() && pressHitResult instanceof HitResult.ElementHead) {
            return;
        }

        switch (pressHitResult) {
            case HitResult.ElementHead(var index) -> {
                selectAndPlayElement(index);
                pressHandled = true;
            }

            case HitResult.Slide(var elementIndex) -> {
                if (lineSelectionState != null) {
                    prepareSelection();
                    lineSelectionState.selectSlide(elementIndex);
                    lc.getScoreView().selectionChanged();
                    pressHandled = true;
                }
            }

            case HitResult.GraceGlissando() -> {
                OptionDialogs.showWarningMessage(
                    null,
                    Strings.ALERT_TITLE_GRACE_NOTE_WARNING,
                    Strings.WARNING_GRACE_GLISSANDO_NOT_SELECTABLE
                );
                pressHandled = true;
            }

            case HitResult.StaffLine() -> {
                if (lineSelectionState != null) {
                    prepareSelection();
                    lineSelectionState.setLineSelected(true);
                    lc.getScoreView().selectionChanged();
                    pressHandled = true;
                }
            }

            case HitResult.Nothing() -> lc.repaint();
        }

        if (pressHandled) {
            lc.repaint();
        }
    }

    void handleDrag(MouseEvent e) {
        if (pressHandled) {
            return;
        }

        if (!dragging) {
            var coordinator = lc.getScoreView().getSelectionCoordinator();
            coordinator.dragDidStart(lc);
        }

        dragging = true;

        // Clamp coordinates to component bounds
        var x = Math.clamp(e.getX(), 0, lc.getWidth() - 1);
        var y = Math.clamp(e.getY(), 0, lc.getHeight() - 1);

        dragRectangle.setBounds(
            Math.min(dragStart.x, x),
            Math.min(dragStart.y, y),
            Math.abs(dragStart.x - x),
            Math.abs(dragStart.y - y)
        );

        calculateLineSelectionFromDrag(dragRectangle);
        lc.repaint();
    }

    /**
     * Handles a click event for selection.
     *
     * @return true if the event was handled (selection was active), false to fall through
     */
    boolean handleClick(MouseEvent e) {
        if (!isSelectionActive(e)) {
            return false;
        }

        // Element was already selected on press — nothing more to do
        if (pressHandled) {
            return true;
        }

        // Shift+click on a note head: extend selection from anchor
        var lineSelectionState = lc.getLineSelectionState();

        if (e.isShiftDown()
            && pressHitResult instanceof HitResult.ElementHead(var index)
            && lineSelectionState != null
            && lineSelectionState.getSelectionAnchor() != -1) {
            lineSelectionState.extendSelectionTo(index);
            lc.getScoreView().selectionChanged();
            playNoteIfPitched(index);
            lc.repaint();
        }

        return true;
    }

    void handleRelease() {
        if (dragging) {
            dragging = false;
            dragRectangle.setBounds(0, 0, 0, 0);
            lc.getScoreView().selectionChanged();
            lc.repaint();
        }
    }

    // ======================================================================
    // Guard
    // ======================================================================

    /**
     * Returns whether selection handling should be active for the given event.
     * <p>
     * Selection is active when:
     * <ul>
     *   <li>ScoreView is available and not in an adjustment mode</li>
     *   <li>Not playing back</li>
     *   <li>In SELECT mode (alt-click switches to SELECT mode permanently)</li>
     * </ul>
     *
     * @param e The mouse event (alt key check retained for backwards compatibility)
     * @return true if selection handling should be active
     */
    boolean isSelectionActive(MouseEvent e) {
        var scoreView = lc.getScoreView();
        var line = lc.getLine();

        if (line == null) {
            return false;
        }

        if (MidiController.isPlaying()) {
            return false;
        }

        var mode = scoreView.getMode();
        return mode != Mode.ADJUSTMENT && mode != Mode.VERTICAL_ADJUSTMENT && (mode == Mode.SELECT || e.isAltDown());
    }

    // ======================================================================
    // Selection helpers
    // ======================================================================

    /**
     * Clears all selection and activates this line for new selection.
     */
    private void prepareSelection() {
        lc.getScoreView().clearSelection();
        lc.getScoreView().getSelectionCoordinator().activateLine(lc.getLineIndex());
    }

    /**
     * Selects the element at the given index in this line, clearing any prior selection.
     * Used by both the press handler and {@link NoteDragHandler}.
     */
    void selectElementAtIndex(int elementIndex) {
        prepareSelection();
        var selState = lc.getLineSelectionState();

        if (selState == null) {
            return;
        }

        selState.setSelectionFromClick(elementIndex);
        lc.getScoreView().selectionChanged();
    }

    /**
     * Selects the element at the given index and plays it if it is a pitched note.
     */
    void selectAndPlayElement(int elementIndex) {
        selectElementAtIndex(elementIndex);
        playNoteIfPitched(elementIndex);
    }

    /**
     * Plays the element at the given index if it is a pitched note (not a rest).
     */
    void playNoteIfPitched(int elementIndex) {
        if (!Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)) {
            return;
        }

        var line = lc.getLine();

        if (line == null) {
            return;
        }

        var element = line.getElement(elementIndex);

        if (element.getType().isNote()) {
            new PlayThread(element.getPitch()).start();
        }
    }

    // ======================================================================
    // Private helpers
    // ======================================================================

    /**
     * Returns the right edge of the staff header (clef + optional key signature)
     * in staff-space units. Clicks at or before this X select the staff lines.
     */
    private double headerRightEdgeSs() {
        var line = lc.getLine();
        var keyAccidentalCount = line != null ? line.getKeyAccidentalCount() : 0;
        return HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(keyAccidentalCount);
    }

    private int hitTestSlideAtPoint(double clickXSs, double clickYSs) {
        var selState = lc.getLineSelectionState();

        if (selState == null) {
            return -1;
        }

        return SlideRenderer.getInstance().hitTestSlide(
            clickXSs, clickYSs, selState.getLine()
        );
    }

    private void buildElementHitRect(StaffElement element, Rectangle2D.Double out) {
        ElementHitTest.buildElementHitRect(lc, element, out, false);
    }

    private void calculateLineSelectionFromDrag(Rectangle dragRect) {
        var scoreView = lc.getScoreView();
        var line = lc.getLine();
        var lineSelectionState = lc.getLineSelectionState();

        if (line == null || lineSelectionState == null) {
            return;
        }

        var coordinator = scoreView.getSelectionCoordinator();
        coordinator.activateLine(lc.getLineIndex());
        lineSelectionState.clearSelection();

        // Convert pixel drag rect to staff spaces for intersection with hit rects
        var dragRectSs = new Rectangle2D.Double(
            ScaleContext.pxToSs(dragRect.x),
            ScaleContext.pxToSs(dragRect.y),
            ScaleContext.pxToSs(dragRect.width),
            ScaleContext.pxToSs(dragRect.height)
        );
        var helper = new Rectangle2D.Double();
        for (var elementIndex = 0; elementIndex < line.elementCount(); elementIndex++) {
            var element = line.getElement(elementIndex);

            // Skip the song's auto-maintained terminal (shared predicate).
            if (!line.getSong().isInteractable(element, line)) {
                continue;
            }

            buildElementHitRect(element, helper);

            if (dragRectSs.intersects(helper)) {
                lineSelectionState.extendSelection(elementIndex);
            }
        }

        // Set anchor to the selection end nearest the drag start point
        if (lineSelectionState.hasElementSelection()) {
            var anchorIndex = ElementHitTest.hitTestElement(lc, dragStart);

            if (anchorIndex != -1) {
                lineSelectionState.setSelectionAnchor(anchorIndex);
            } else {
                var begin = lineSelectionState.getSelectionBegin();
                var end = lineSelectionState.getSelectionEnd();
                var beginElement = line.getElement(begin);
                var endElement = line.getElement(end);
                var distToBegin = Math.abs(dragStart.x - beginElement.getXOffsetPx());
                var distToEnd = Math.abs(dragStart.x - endElement.getXOffsetPx());
                lineSelectionState.setSelectionAnchor(distToBegin <= distToEnd ? begin : end);
            }
        }
    }
}
