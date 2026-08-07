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

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Line;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.hit.HitRegistry;
import songscribe.hit.HitTarget;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;
import songscribe.ui.Mode;
import songscribe.layout.ElementHitGeometry;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.ui.ViewScale;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;

/**
 * Handles selection, hit-testing, and drag logic for a {@link LineComponent}.
 * <p>
 * Each {@code LineComponent} owns one instance. Drag state (rectangle, start point)
 * lives here; mouse event handlers in {@code LineComponent} delegate to this class.
 * <p>
 * Hit testing itself lives in the line's {@link HitRegistry}, built at layout time. This
 * class only converts the click point into the registry's coordinate space and acts on the
 * answer; adding a newly selectable kind of notation is a registration at layout time, not
 * another tester here.
 */
class LineSelectionHandler {

    /**
     * Smallest width and height a live drag rectangle may have, in view pixels. A drag rectangle
     * is never allowed below this while dragging; it is only ever 0×0 when no drag is in
     * progress.
     */
    private static final int MIN_DRAG_EXTENT_PX = 1;

    private final LineComponent lc;

    private boolean dragging = false;
    private boolean pressHandled = false;

    /** What the press landed on, or null if it landed on nothing. */
    private @Nullable HitTarget pressTarget = null;

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
     * Hit-tests the given point, in view pixels, against everything clickable on this line,
     * or {@code null} if the point lands on nothing.
     * <p>
     * A single press needs more than one answer about the same point — whether it landed on
     * the staff lines, and whether it landed on an ending. Callers should run this once and
     * examine the result rather than asking several times about the same point.
     */
    @Nullable HitTarget hitTestViewPoint(Point viewPoint) {
        var registry = readyRegistry();

        if (registry == null) {
            return null;
        }

        return registry.hitTest(layoutXSs(viewPoint), layoutYSs(viewPoint));
    }

    /**
     * Returns this line's hit registry with its layout brought up to date, or {@code null}
     * if this component has no line to hit-test.
     * <p>
     * The layout is ensured here, since the registry is built as part of it: without this a
     * query could answer from the regions of a stale layout.
     */
    private @Nullable HitRegistry readyRegistry() {
        if (lc.getLine() == null) {
            return null;
        }

        var ready = lc.readyLayout();

        if (ready == null) {
            return null;
        }

        return ready.layoutResult().getHitRegistry();
    }

    /**
     * A view-pixel distance in staff spaces at the view's current zoom.
     * <p>
     * Goes straight from view pixels to staff spaces through {@link ViewScale#toSs}, the same
     * conversion {@code PreviewElementManager.trackMouse} uses on the mouse position. Nothing
     * downstream wants an intermediate document-pixel value: the registry's regions and the
     * element hit rects are both staff spaces, so rounding through whole document pixels on
     * the way would only cost precision — half a document pixel, which the zoom multiplies
     * back into visible slop.
     */
    private double toSs(int viewPx) {
        return lc.getViewScale().toSs(new ViewPx(viewPx)).value();
    }

    /** The X of a view-pixel point in the registry's line-local staff spaces. */
    private double layoutXSs(Point viewPoint) {
        return toSs(viewPoint.x);
    }

    /**
     * The Y of a view-pixel point in the registry's layout space, whose origin is the
     * staff midline rather than the top of this component.
     */
    private double layoutYSs(Point viewPoint) {
        return toSs(viewPoint.y) - lc.getMiddleLineYSs();
    }

    /**
     * Hit-tests a point, in view pixels, against the lyric row alone.
     * <p>
     * Answers the same question as looking for a {@link HitTarget.Lyric} in
     * {@link #hitTestViewPoint}'s result — a lyric outranks everything else, so nothing can
     * take a point away from it — while testing only the regions marked hover-testable.
     * {@code mouseMoved} asks this on every pixel of pointer motion, which is the whole
     * reason the registry offers a hover query rather than leaving callers to filter a full
     * resolution afterwards.
     */
    HitTarget.@Nullable Lyric hitTestLyricViewPoint(Point viewPoint) {
        var registry = readyRegistry();

        if (registry == null) {
            return null;
        }

        var hover = registry.hitTestHover(layoutXSs(viewPoint), layoutYSs(viewPoint));

        if (hover instanceof HitTarget.Lyric lyric) {
            return lyric;
        }

        return null;
    }

    /**
     * Returns the index of the element hit by the given view-pixel point, or -1 if the point
     * resolves to something other than an element.
     */
    int hitTestElementIndex(Point viewPoint) {
        var line = lc.getLine();

        if (line == null || !(hitTestViewPoint(viewPoint) instanceof HitTarget.Element(var element))) {
            return -1;
        }

        return line.getElementIndex(element);
    }

    /**
     * Returns whether the given point, in view pixels, is horizontally within the staff
     * header, regardless of its Y. Unlike the registry's staff-line region, which is bounded
     * vertically too, this covers the whole header column, since no element can be inserted
     * anywhere in it.
     */
    boolean isWithinHeaderX(Point viewPoint) {
        return isWithinHeaderXSs(layoutXSs(viewPoint));
    }

    // ======================================================================
    // Public delegation entry points
    // ======================================================================

    void handlePress(MouseEvent e, @Nullable HitTarget hitTarget) {
        dragging = false;
        pressHandled = false;
        dragStart.setLocation(e.getPoint());
        dragRectangle.setBounds(0, 0, 0, 0);

        pressTarget = hitTarget;

        var range = lc.getScoreView().getSelectionCoordinator().getRange();

        // Don't clear selection on shift+click (preserve for extend)
        if (!e.isShiftDown() || range == null) {
            lc.getScoreView().clearSelection();

            if (MidiController.sequencer != null) {
                MidiController.sequencer.setTickPosition(0);
            }
        }

        // Select the hit element immediately on press.
        // This prevents rubber-band drag from starting on selectable elements.
        // Shift+click on a note head is handled in handleClick for extend-selection.
        if (e.isShiftDown() && hitTarget instanceof HitTarget.Element) {
            return;
        }

        // Every variant is spelled out rather than defaulted: what a click does to a newly
        // selectable kind of notation is a decision, and a default arm would make it silently.
        pressHandled = switch (hitTarget) {
            case null -> {
                lc.repaint();
                yield false;
            }

            case HitTarget.Lyric(var element, var verse) -> selectLyric(element, verse);

            case HitTarget.Element(var element) -> selectAndPlayElement(element);

            case HitTarget.GraceGlissando _ -> {
                OptionDialogs.showWarningMessage(
                    null,
                    Strings.ALERT_TITLE_GRACE_NOTE_WARNING,
                    Strings.WARNING_GRACE_GLISSANDO_NOT_SELECTABLE
                );
                yield true;
            }

            case HitTarget.StaffLine staffLine -> selectTarget(staffLine);

            case HitTarget.Slide slide -> selectTarget(slide);
            case HitTarget.Hairpin hairpin -> selectTarget(hairpin);
            case HitTarget.Ending ending -> selectTarget(ending);
            case HitTarget.Articulation articulation -> selectTarget(articulation);
            case HitTarget.Attachment attachment -> selectTarget(attachment);
            case HitTarget.Accidental accidental -> selectTarget(accidental);
            case HitTarget.Tie tie -> selectTarget(tie);
            case HitTarget.Beam beam -> selectTarget(beam);
            case HitTarget.Trill trill -> selectTarget(trill);
            case HitTarget.Tuplet tuplet -> selectTarget(tuplet);
        };

        if (pressHandled) {
            lc.repaint();
        }
    }

    /**
     * Selects a lyric hit by a press in EDIT mode, returning whether one was selected.
     * <p>
     * A lyric is the <b>only</b> thing selectable in EDIT mode. Everything else — every
     * decoration, every note — needs SELECT mode or an alt+click. That is one rule for the whole
     * hit vocabulary rather than a per-kind exception list, and it keeps EDIT mode's own rule
     * simple: a press inserts.
     * <p>
     * A lyric is the exception because it is the one kind that insertion cannot reach anyway.
     * {@code mouseMoved} clears the insertion preview over a lyric box, so the staff positions a
     * lyric covers cannot be clicked to insert a note whatever this method does. Selecting there
     * takes nothing away from EDIT mode.
     * <p>
     * The {@link LineComponent#hasPreviewElement()} guard is therefore a fallback rather than the
     * rule — it only bites if a preview appears over lyric text with no mouse movement to clear
     * it, and insertion winning is the safe answer in that case.
     * <p>
     * Takes the already-computed cascade result for this press, so an element head over the lyric
     * still wins and falls through to normal EDIT-mode handling.
     */
    boolean handleEditModePress(@Nullable HitTarget target) {
        if (MidiController.isPlaying()) {
            return false;
        }

        if (lc.hasPreviewElement()) {
            return false;
        }

        var selected = target instanceof HitTarget.Lyric(var element, var verse)
            && selectLyric(element, verse);

        if (selected) {
            lc.repaint();
        }

        return selected;
    }

    void handleDrag(MouseEvent e) {
        if (pressHandled) {
            return;
        }

        if (!dragging) {
            var coordinator = lc.getScoreView().getSelectionCoordinator();
            coordinator.getDragTracker().dragDidStart(lc);
        }

        dragging = true;

        // Clamp coordinates to component bounds. The rectangle stays in view pixels because it
        // is a pixel-space overlay rendered outside the staff-space transform; the sweep that
        // reads it converts to staff spaces itself.
        var x = Math.clamp(e.getX(), 0, lc.getWidth() - 1);
        var y = Math.clamp(e.getY(), 0, lc.getHeight() - 1);

        // Never smaller than one pixel on either side while a drag is live. A drag along the
        // staff can hold Y exactly constant, and a rectangle with no height sweeps nothing at
        // all: Rectangle2D.intersects rejects an empty rectangle outright, whatever it is tested
        // against. The sweep would then find no elements and clear the selection instead of
        // extending it, so a perfectly straight drag would undo itself.
        dragRectangle.setBounds(
            Math.min(dragStart.x, x),
            Math.min(dragStart.y, y),
            Math.max(MIN_DRAG_EXTENT_PX, Math.abs(dragStart.x - x)),
            Math.max(MIN_DRAG_EXTENT_PX, Math.abs(dragStart.y - y))
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
        var line = lc.getLine();

        if (e.isShiftDown()
            && pressTarget instanceof HitTarget.Element(var element)
            && line != null
            && lc.getScoreView().getSelectionCoordinator().getRange() != null) {
            var elementIndex = line.getElementIndex(element);

            if (elementIndex >= 0) {
                lc.getScoreView().extendSelectionTo(elementIndex);
                playNoteIfPitched(elementIndex);
            }
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
     *   <li>ScoreView is available</li>
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

        return line != null &&
            !MidiController.isPlaying() &&
            (scoreView.getMode() == Mode.SELECT || e.isAltDown());

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
     * Makes the lyric on {@code element} in verse {@code verse} the sole selection.
     * <p>
     * Unlike every other target, a lyric names the line it belongs to, so the coordinator
     * resolves and activates that line itself: there is no registration this can find missing,
     * and it always succeeds.
     */
    @SuppressWarnings("SameReturnValue")
    private boolean selectLyric(StaffElement element, int verse) {
        var scoreView = lc.getScoreView();
        scoreView.getSelectionCoordinator().selectLyric(element, verse);
        scoreView.selectionChanged();
        return true;
    }

    /**
     * Makes {@code target} — a decoration, a note's accidental, or the staff line itself —
     * the sole selection, returning false when this line is not registered with the
     * coordinator.
     * <p>
     * The registration is what makes the selected line resolvable: activating an unregistered
     * index would leave the selection pointing at a line nothing can answer for.
     */
    private boolean selectTarget(HitTarget target) {
        var scoreView = lc.getScoreView();
        var coordinator = scoreView.getSelectionCoordinator();

        if (coordinator.getLine(lc.getLineIndex()) == null) {
            return false;
        }

        prepareSelection();
        coordinator.select(target);
        scoreView.selectionChanged();
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
        // otherwise stay on screen even though selectSingleElement clears its selection.
        // This is for the NoteDragHandler path, which selects a pressed note without
        // clearing first, so the outgoing line is still active here. On the handlePress
        // path there is nothing to do — it calls ScoreView.clearSelection() up front,
        // which repaints the outgoing line itself and leaves no active line behind.
        var deselectedLine = scoreView.getLineComponent(coordinator.getActiveLineIndex());

        if (coordinator.selectSingleElement(lc.getLineIndex(), elementIndex)) {
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
     * Selects {@code element} and plays it if it is a pitched note, returning false when it
     * is not on this component's line. Targets name elements by identity; the index the
     * selection range is built from is derived here, at the point of use.
     */
    private boolean selectAndPlayElement(StaffElement element) {
        var line = lc.getLine();

        if (line == null) {
            return false;
        }

        var elementIndex = line.getElementIndex(element);

        if (elementIndex < 0) {
            return false;
        }

        selectAndPlayElement(elementIndex);
        return true;
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
        return HorizontalSpacingCalculator.isWithinHeaderXSs(xSs, lc.getLine());
    }

    /**
     * Replaces the selection with the elements the rubber band covers, anchored at the end
     * nearest where the drag started.
     * <p>
     * The whole range is computed before anything is selected, so a drag event assigns the
     * selection exactly once no matter how many elements it sweeps.
     */
    private void calculateLineSelectionFromDrag(Rectangle dragRect) {
        var scoreView = lc.getScoreView();
        var line = lc.getLine();
        var layoutResult = lc.getLayoutResult();

        if (line == null || layoutResult == null) {
            return;
        }

        var coordinator = scoreView.getSelectionCoordinator();
        coordinator.activateLine(lc.getLineIndex());

        // Convert the view-pixel drag rect to staff spaces (honoring the current zoom), then
        // to layout space by moving the Y origin from the top of this component to the staff
        // midline — the space the element hit rects are built in.
        var dragRectSs = new Rectangle2D.Double(
            toSs(dragRect.x),
            toSs(dragRect.y) - lc.getMiddleLineYSs(),
            toSs(dragRect.width),
            toSs(dragRect.height)
        );
        var helper = new Rectangle2D.Double();
        var begin = -1;
        var end = -1;

        for (var elementIndex = 0; elementIndex < line.elementCount(); elementIndex++) {
            var element = line.getElement(elementIndex);

            // Skip the song's auto-maintained terminal (shared predicate).
            if (!line.getSong().isInteractable(element, line)) {
                continue;
            }

            // Unexpanded, unlike the registry's click rects: a rubber band should catch
            // exactly the elements it visually covers.
            ElementHitGeometry.elementHitRectSs(
                layoutResult.getElementXSs(element), element, helper, false);

            if (dragRectSs.intersects(helper)) {
                if (begin == -1) {
                    begin = elementIndex;
                }

                end = elementIndex;
            }
        }

        if (begin == -1) {
            coordinator.clearActiveSelection();
            return;
        }

        coordinator.selectRange(begin, end, dragAnchor(line, begin, end));
    }

    /**
     * The end of {@code begin..end} the drag started from, which stays put while the other
     * end follows the pointer. The element under the drag start point when there is one,
     * otherwise whichever end is horizontally nearer to it.
     */
    private int dragAnchor(Line line, int begin, int end) {
        var anchorIndex = hitTestElementIndex(dragStart);

        if (anchorIndex != -1) {
            return anchorIndex;
        }

        var distToBegin = Math.abs(dragStart.x - line.getElement(begin).getXOffsetPx());
        var distToEnd = Math.abs(dragStart.x - line.getElement(end).getXOffsetPx());

        return (distToBegin <= distToEnd) ? begin : end;
    }
}
