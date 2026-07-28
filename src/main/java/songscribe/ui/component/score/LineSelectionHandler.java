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
// Disambiguates from java.awt.List (java.desktop module)
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.StaffElement;
import songscribe.layout.Ending;
import songscribe.dom.ViewPx;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;
import songscribe.ui.Mode;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.dom.ScaleContext;
import songscribe.ui.hit.HitResult;
import songscribe.ui.hit.HitTestContext;
import songscribe.ui.hit.HitTester;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.renderer.EndingRenderer;
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

    /**
     * The hit-test cascade, in priority order: the first tester to report a hit wins.
     * Adding a newly selectable element means adding a tester here, not another branch
     * in {@link #hitTest(Point)}.
     */
    private final List<HitTester> hitTesters;

    private boolean dragging = false;
    private boolean pressHandled = false;
    private HitResult pressHitResult = new HitResult.Nothing();
    private final Point dragStart = new Point();
    private final Rectangle dragRectangle = new Rectangle();

    LineSelectionHandler(LineComponent lc) {
        this.lc = lc;
        hitTesters = List.of(
            context -> ElementHitTest.hit(lc, context),
            this::hitTestSlide,
            this::hitTestEnding,
            this::hitTestStaffLine
        );
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
     * Hit-tests the given point against all selectable elements in this line, running
     * {@link #hitTesters} in order and returning the first hit, or
     * {@link HitResult.Nothing} if nothing is hit.
     * <p>
     * The point must already be in document pixels. This differs from the
     * {@code is…} helpers below, which take view pixels and convert internally.
     */
    HitResult hitTest(Point point) {
        var context = buildContext(point);

        if (context == null) {
            return new HitResult.Nothing();
        }

        for (var tester : hitTesters) {
            var result = tester.hitTest(context);

            if (result != null) {
                return result;
            }
        }

        return new HitResult.Nothing();
    }

    /**
     * Gathers the inputs every {@link HitTester} needs, or {@code null} if this component
     * has no line to hit-test.
     * <p>
     * {@link LineComponent#setLine} is the only place either {@code line} or
     * {@code lineSelectionState} is assigned, and it always assigns both, so in practice
     * they are present or absent together. Should that invariant ever break — a line
     * without a selection state, or the reverse — note-head and staff-line hit-testing,
     * which need no selection state at all, would silently stop reporting hits instead of
     * failing loudly. Whoever breaks the invariant has to revisit this guard.
     */
    private @Nullable HitTestContext buildContext(Point point) {
        var line = lc.getLine();

        if (line == null || lc.getLineSelectionState() == null) {
            return null;
        }

        return new HitTestContext(
            point,
            line,
            lc.getLayoutResult(),
            lc.getMiddleLineYSs()
        );
    }

    /**
     * Asks {@link SlideRenderer} whether any slide's drawn geometry contains the point, then
     * applies the rule the renderer deliberately does not: a slide owned by a grace note is
     * not selectable, so it is reported as {@link HitResult.GraceGlissando} instead.
     */
    private @Nullable HitResult hitTestSlide(HitTestContext context) {
        var line = context.line();
        var elementIndex = SlideRenderer.getInstance().hitTestSlide(context.xSs(), context.ySs(), line);

        if (elementIndex == -1) {
            return null;
        }

        if (line.getElement(elementIndex).getType().isGraceNote()) {
            return new HitResult.GraceGlissando();
        }

        return new HitResult.Slide(elementIndex);
    }

    /**
     * Asks {@link EndingRenderer} which ending's drawn box contains the point, and wraps the
     * answer as a hit result.
     */
    private @Nullable HitResult hitTestEnding(HitTestContext context) {
        var ending = EndingRenderer.getInstance().hitTestEnding(
            context.xSs(), context.ySs(), context.line(), context.layoutResult(), context.middleLineYSs());

        if (ending == null) {
            return null;
        }

        return new HitResult.Ending(ending);
    }

    /**
     * Hit-tests the staff lines: a point within {@link #STAFF_HIT_RADIUS_SS} of the middle
     * staff line and horizontally inside the staff header selects the whole line.
     */
    private @Nullable HitResult hitTestStaffLine(HitTestContext context) {
        if (Math.abs(context.ySs() - context.middleLineYSs()) <= STAFF_HIT_RADIUS_SS
            && isWithinHeaderXSs(context.xSs())) {
            return new HitResult.StaffLine();
        }

        return null;
    }

    /**
     * Hit-tests a point given in view pixels, converting it to document pixels first.
     * <p>
     * A single press needs more than one answer about the same point — whether it landed on
     * the staff lines, and whether it landed on an ending. Callers should run this once and
     * examine the result rather than calling several {@code is…} helpers, each of which
     * walks every element in the line all over again.
     */
    HitResult hitTestViewPoint(Point viewPoint) {
        return hitTest(lc.getViewScale().toDocumentPoint(viewPoint));
    }

    /**
     * Returns whether the given point, in view pixels, hits an ending.
     * <p>
     * Runs the same cascade as the press path, so the EDIT-mode insertion preview is
     * suppressed at exactly the points where a press selects an ending instead of
     * inserting — including the case where an element head over the bracket wins the
     * cascade and insertion still applies.
     */
    boolean isEndingHit(Point viewPoint) {
        return hitTestViewPoint(viewPoint) instanceof HitResult.Ending;
    }

    /**
     * Returns whether the given point, in view pixels, is horizontally within the staff
     * header, regardless of its Y. Unlike {@link #isStaffLineHit}, this covers the whole
     * header column, since no element can be inserted anywhere in it.
     */
    boolean isWithinHeaderX(Point viewPoint) {
        var docPoint = lc.getViewScale().toDocumentPoint(viewPoint);
        return isWithinHeaderXSs(ScaleContext.pxToSs(docPoint.x));
    }

    // ======================================================================
    // Public delegation entry points
    // ======================================================================

    void handlePress(MouseEvent e) {
        dragging = false;
        pressHandled = false;
        dragStart.setLocation(e.getPoint());
        dragRectangle.setBounds(0, 0, 0, 0);

        // Hit-test in document pixels; the drag rectangle stays in view pixels (below)
        // because it is a pixel-space overlay rendered outside the staff-space transform.
        pressHitResult = hitTest(lc.getViewScale().toDocumentPoint(e.getPoint()));

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

            case HitResult.Ending(var ending) -> pressHandled = selectEnding(ending);

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

    /**
     * Selects an ending hit by a press in EDIT mode, returning whether one was hit.
     * <p>
     * EDIT mode otherwise routes presses to element insertion, and reaching the selection
     * handler at all requires SELECT mode (see {@link #isSelectionActive}), so before this
     * existed an ending could not be selected until something else had switched modes.
     * Selecting it in place, without leaving EDIT mode, follows the idiom already
     * established for clicking a lyric.
     * <p>
     * Takes the already-computed cascade result for this press, so an element head over the
     * bracket still wins and falls through to normal EDIT-mode handling.
     */
    boolean handleEditModeEndingPress(HitResult result) {
        if (MidiController.isPlaying()) {
            return false;
        }

        if (!(result instanceof HitResult.Ending(var ending)) || !selectEnding(ending)) {
            return false;
        }

        lc.repaint();
        return true;
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
            lc.getScoreView().extendSelectionTo(index);
            playNoteIfPitched(index);
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
     * Makes {@code ending} the sole selection, returning false when this line has no
     * selection state to record it in.
     */
    private boolean selectEnding(Ending ending) {
        var lineSelectionState = lc.getLineSelectionState();

        if (lineSelectionState == null) {
            return false;
        }

        prepareSelection();
        lineSelectionState.selectEnding(ending);
        lc.getScoreView().selectionChanged();
        return true;
    }

    /**
     * Selects the element at the given index in this line, clearing any prior selection.
     * Used by both the press handler and {@link NoteDragHandler}.
     */
    void selectElementAtIndex(int elementIndex) {
        var scoreView = lc.getScoreView();
        var coordinator = scoreView.getSelectionCoordinator();

        // Repaint the previously active line, since its selection highlight would
        // otherwise stay on screen even though selectSingleElement clears its state.
        // This is for the NoteDragHandler path, which selects a pressed note without
        // clearing first, so the outgoing line is still active here. On the handlePress
        // path there is nothing to do — it calls ScoreView.clearSelection() up front,
        // which repaints the outgoing line itself and leaves no active line behind.
        var deselectedLine = scoreView.getLineComponent(coordinator.getActiveLineIndex());

        var state = coordinator.selectSingleElement(lc.getLineIndex(), elementIndex);

        if (state != null) {
            scoreView.selectionChanged();
        }

        if (deselectedLine != null && deselectedLine != lc) {
            deselectedLine.repaint();
        }
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
     * Returns whether the given X, in staff-space units, is within this line's staff
     * header (clef + optional key signature). Clicks at or before that X select the
     * staff lines.
     */
    private boolean isWithinHeaderXSs(double xSs) {
        var line = lc.getLine();
        var keyAccidentalCount = line != null ? line.getKeyAccidentalCount() : 0;
        return HorizontalSpacingCalculator.isWithinHeaderXSs(xSs, keyAccidentalCount);
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

        // Convert the view-pixel drag rect to document pixels (honoring the current zoom),
        // then to staff spaces on the fixed document scale for intersection with the
        // document-space element hit rects.
        var viewScale = lc.getViewScale();
        var dragRectSs = new Rectangle2D.Double(
            ScaleContext.pxToSs(viewScale.toDocPx(new ViewPx(dragRect.x)).value()),
            ScaleContext.pxToSs(viewScale.toDocPx(new ViewPx(dragRect.y)).value()),
            ScaleContext.pxToSs(viewScale.toDocPx(new ViewPx(dragRect.width)).value()),
            ScaleContext.pxToSs(viewScale.toDocPx(new ViewPx(dragRect.height)).value())
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
