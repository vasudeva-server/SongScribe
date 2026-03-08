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

import songscribe.music.StaffElement;
import songscribe.ui.Mode;
import songscribe.ui.layout2.ScaleContext;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.renderer.GlissandoRenderer;

/**
 * Handles selection, hit-testing, and drag logic for a {@link LineComponent}.
 * <p>
 * Each {@code LineComponent} owns one instance. Drag state (rectangle, start point)
 * lives here; mouse event handlers in {@code LineComponent} delegate to this class.
 */
class SelectionHandler {

    /** Half-height of the staff hit zone for line selection, in staff spaces. */
    private static final double STAFF_HIT_RADIUS_SS = 2.0;

    private final LineComponent lc;
    private boolean dragging = false;
    private boolean pressHandled = false;
    private HitResult pressHitResult = new HitResult.Nothing();
    private final Point dragStart = new Point();
    private final Rectangle dragRectangle = new Rectangle();

    SelectionHandler(@NotNull LineComponent lc) {
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
     * The cascade tests note heads first, then glissandos, then staff-line proximity.
     * If nothing is hit, {@link HitResult.Nothing} is returned.
     */
    HitResult hitTest(@NotNull Point point) {
        var elementIndex = ElementHitTest.hitTestElement(lc, point);

        if (elementIndex != -1) {
            return new HitResult.ElementHead(elementIndex);
        }

        var glissandoIndex = hitTestGlissandoAtPoint(point);

        if (glissandoIndex != -1) {
            return new HitResult.Glissando(glissandoIndex);
        }

        var clickYSs = ScaleContext.getInstance().fromPixels(point.y);

        if (Math.abs(clickYSs - lc.getMiddleLineYSs()) <= STAFF_HIT_RADIUS_SS) {
            return new HitResult.StaffLine();
        }

        return new HitResult.Nothing();
    }

    // ======================================================================
    // Public delegation entry points
    // ======================================================================

    void handlePress(@NotNull MouseEvent e) {
        dragging = false;
        pressHandled = false;
        dragStart.setLocation(e.getPoint());
        dragRectangle.setBounds(0, 0, 0, 0);

        pressHitResult = hitTest(e.getPoint());

        var lineSelectionState = lc.getLineSelectionState();

        // Don't clear selection on shift+click (preserve for extend)
        if (!e.isShiftDown() || lineSelectionState.getSelectionAnchor() == -1) {
            lc.getScore().clearSelection();

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

            case HitResult.Glissando(var elementIndex) -> {
                prepareSelection();
                lineSelectionState.selectGlissando(elementIndex);
                lc.getScore().selectionChanged();
                pressHandled = true;
            }

            case HitResult.StaffLine() -> {
                prepareSelection();
                lineSelectionState.setLineSelected(true);
                lc.getScore().selectionChanged();
                pressHandled = true;
            }

            case HitResult.Nothing() -> lc.repaint();
        }

        if (pressHandled) {
            lc.repaint();
        }
    }

    void handleDrag(@NotNull MouseEvent e) {
        if (pressHandled) {
            return;
        }

        dragging = true;

        // Clamp coordinates to component bounds
        var x = Math.max(0, Math.min(e.getX(), lc.getWidth() - 1));
        var y = Math.max(0, Math.min(e.getY(), lc.getHeight() - 1));

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
    boolean handleClick(@NotNull MouseEvent e) {
        if (!isSelectionActive(e)) {
            return false;
        }

        // Element was already selected on press — nothing more to do
        if (pressHandled) {
            return true;
        }

        // Shift+click on a note head: extend selection from anchor
        if (e.isShiftDown()
            && pressHitResult instanceof HitResult.ElementHead(var index)
            && lc.getLineSelectionState().getSelectionAnchor() != -1) {
            var lineSelectionState = lc.getLineSelectionState();
            lineSelectionState.extendSelectionTo(index);
            lc.getScore().selectionChanged();
            playNoteIfPitched(index);
            lc.repaint();
        }

        return true;
    }

    void handleRelease() {
        if (dragging) {
            dragging = false;
            dragRectangle.setBounds(0, 0, 0, 0);
            lc.getScore().selectionChanged();
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
     *   <li>Score is available and not in an adjustment mode</li>
     *   <li>Not playing back</li>
     *   <li>In SELECT mode (alt-click switches to SELECT mode permanently)</li>
     * </ul>
     *
     * @param e The mouse event (alt key check retained for backwards compatibility)
     * @return true if selection handling should be active
     */
    boolean isSelectionActive(@NotNull MouseEvent e) {
        var score = lc.getScore();
        var line = lc.getLine();

        if (score == null || line == null) {
            return false;
        }

        if (MidiController.isPlaying()) {
            return false;
        }

        var mode = score.getMode();

        if (mode == Mode.ADJUSTMENT || mode == Mode.VERTICAL_ADJUSTMENT
            || mode == Mode.LYRICS_ADJUSTMENT) {
            return false;
        }

        return mode == Mode.SELECT || e.isAltDown();
    }

    // ======================================================================
    // Selection helpers
    // ======================================================================

    /**
     * Clears all selection and activates this line for new selection.
     */
    private void prepareSelection() {
        lc.getScore().clearSelection();
        lc.getScore().getSelectionCoordinator().activateLine(lc.getLineIndex());
    }

    /**
     * Selects the element at the given index in this line, clearing any prior selection.
     * Used by both the press handler and {@link ElementDragHandler}.
     */
    void selectElementAtIndex(int elementIndex) {
        prepareSelection();
        lc.getLineSelectionState().setSelectionFromClick(elementIndex);
        lc.getScore().selectionChanged();
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
    private void playNoteIfPitched(int elementIndex) {
        var element = lc.getLine().getElement(elementIndex);

        if (element.getType().isNote()) {
            new PlayThread(element.getPitch()).start();
        }
    }

    // ======================================================================
    // Private helpers
    // ======================================================================

    private int hitTestGlissandoAtPoint(@NotNull Point point) {
        var scaleContext = ScaleContext.getInstance();
        var clickXSs = scaleContext.fromPixels(point.x);
        var clickYSs = scaleContext.fromPixels(point.y);
        return GlissandoRenderer.getInstance().hitTestGlissando(
            clickXSs, clickYSs, lc.getLineSelectionState().getLine()
        );
    }

    private void buildElementHitRect(@NotNull StaffElement element, @NotNull Rectangle out) {
        ElementHitTest.buildElementHitRect(lc, element, out);
    }

    private void calculateLineSelectionFromDrag(@NotNull Rectangle dragRect) {
        var score = lc.getScore();
        var line = lc.getLine();
        var lineSelectionState = lc.getLineSelectionState();
        var coordinator = score.getSelectionCoordinator();
        coordinator.activateLine(lc.getLineIndex());
        lineSelectionState.clearSelection();

        var helper = new Rectangle();

        for (var elementIndex = 0; elementIndex < line.elementCount(); elementIndex++) {
            var element = line.getElement(elementIndex);
            buildElementHitRect(element, helper);

            if (dragRect.intersects(helper)) {
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
                var distToBegin = Math.abs(dragStart.x - beginElement.getXPosSs());
                var distToEnd = Math.abs(dragStart.x - endElement.getXPosSs());
                lineSelectionState.setSelectionAnchor(distToBegin <= distToEnd ? begin : end);
            }
        }
    }
}
