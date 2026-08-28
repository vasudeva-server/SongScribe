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

import java.awt.Point;
import java.awt.event.MouseEvent;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Ss;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.engraving.Staff;
import songscribe.hit.HitRegistry;
import songscribe.hit.HitTarget;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.SweepRange;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;

// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)

/**
 * Handles selection, hit-testing, and drag logic for a {@link LineComponent}.
 * <p>
 * Each {@code LineComponent} owns one instance. Drag state — whether a press armed, whether it
 * grew into a drag, and the two horizontal positions a band spans — lives here; mouse event
 * handlers in {@code LineComponent} delegate to this class.
 * <p>
 * Hit testing itself lives in the line's {@link HitRegistry}, built at layout time. This
 * class only converts the click point into the registry's coordinate space and acts on the
 * answer; adding a newly selectable kind of notation is a registration at layout time, not
 * another tester here.
 */
class LineSelectionHandler {

    private final LineComponent lc;

    private boolean dragging = false;
    private boolean pressHandled = false;

    /**
     * Whether this press may grow into a selection band. Distinct from {@link #pressHandled},
     * which answers a different question — whether the click that follows still has work to do.
     * A Shift press on an element leaves {@code pressHandled} false so the click can extend the
     * range (issue #748), yet must never sweep.
     */
    private boolean bandArmed = false;

    /** What the press landed on, or null if it landed on nothing. */
    private @Nullable HitTarget pressTarget = null;

    /** The band's fixed end, in line-local staff spaces, captured on press. */
    private double anchorXSs = 0.0;

    /** The band's moving end, in line-local staff spaces, as of the last drag event. */
    private double leadXSs = 0.0;

    LineSelectionHandler(LineComponent lc) {
        this.lc = lc;
    }

    // ======================================================================
    // Accessors
    // ======================================================================

    boolean isDragging() {
        return dragging;
    }

    /**
     * The band's horizontal extent, in line-local staff spaces.
     * <p>
     * A band has no vertical extent of its own. It always spans the staff, which is line
     * geometry rather than anything the drag decides, so {@link LineComponent#getStaffTopYSs}
     * and {@link LineComponent#getStaffBottomYSs} answer for that instead.
     * <p>
     * Build one with {@link #spanning}, which orders the two ends. The canonical constructor
     * takes them already ordered and is not the way to turn a pair of drag positions into a band.
     *
     * @invariant {@code leftSs <= rightSs}
     */
    record SelectionBand(Ss leftSs, Ss rightSs) {

        /**
         * The band covering both {@code anchorSs} and {@code leadSs}, in either order.
         *
         * @return the band, whichever direction the drag was drawn in
         */
        static SelectionBand spanning(double anchorSs, double leadSs) {
            return new SelectionBand(
                new Ss(Math.min(anchorSs, leadSs)), new Ss(Math.max(anchorSs, leadSs)));
        }
    }

    /**
     * The live selection band, or {@code null} when no drag is in progress or the line's layout
     * went stale under it.
     * <p>
     * Derived on every call rather than stored, and left in staff spaces rather than converted
     * to pixels. Staff spaces name a position in the music, so a zoom mid-drag re-renders the
     * band over the same music at the new scale instead of leaving a stale pixel rectangle
     * behind. The one conversion to pixels happens at paint time, in
     * {@link LineRenderer#renderSelectionBand}.
     *
     * @return the band to paint, or {@code null} when there is nothing to paint
     */
    @Nullable SelectionBand getSelectionBand() {
        if (!dragging) {
            return null;
        }

        var sweepRange = sweepRange();
        return sweepRange == null ? null : bandWithin(sweepRange);
    }

    // ======================================================================
    // Hit testing
    // ======================================================================

    /**
     * Hit-tests the given point, in view pixels, against everything clickable on this line.
     * <p>
     * A single press needs more than one answer about the same point — whether it landed on
     * the staff lines, and whether it landed on an ending. Callers should run this once and
     * examine the result rather than asking several times about the same point.
     *
     * @return what the point landed on, or {@code null} when it landed on nothing clickable or
     *         this component has no line to test against
     */
    @Nullable HitTarget hitTestViewPoint(Point viewPoint) {
        var registry = readyRegistry();

        if (registry == null) {
            return null;
        }

        return registry.hitTest(layoutXSs(viewPoint), layoutYSs(viewPoint));
    }

    /**
     * This line's hit registry with its layout brought up to date.
     * <p>
     * The layout is ensured here, since the registry is built as part of it: without this a
     * query could answer from the regions of a stale layout.
     *
     * @return the registry, or {@code null} when this component has no line to hit-test or its
     *         layout could not be brought up to date
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
     *
     * @return the same distance in staff spaces
     */
    private double toSs(int viewPx) {
        return lc.getViewScale().toSs(new ViewPx(viewPx)).value();
    }

    /**
     * The X of a view-pixel point in the registry's line-local staff spaces.
     *
     * @return the point's X in staff spaces, measured from the start of the line
     */
    double layoutXSs(Point viewPoint) {
        return toSs(viewPoint.x);
    }

    /**
     * The Y of a view-pixel point in the registry's layout space, whose origin is the
     * staff midline rather than the top of this component.
     *
     * @return the point's Y in staff spaces, negative above the midline and positive below
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
     *
     * @return the lyric under the point, or {@code null} when the point is not over one
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
     * Whether the given point, in view pixels, is horizontally within the staff header,
     * regardless of its Y. Unlike the registry's staff-line region, which is bounded
     * vertically too, this covers the whole header column, since no element can be inserted
     * anywhere in it.
     *
     * @return true when the point's X falls in the header
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
        bandArmed = false;

        // Only the press x matters: the band's vertical extent is the staff, not the mouse.
        anchorXSs = toSs(e.getX());
        leadXSs = anchorXSs;

        pressTarget = hitTarget;

        var scoreView = lc.getScoreView();
        var range = scoreView.getSelectionCoordinator().getRange();

        // Extend is the sole reason to keep an existing selection through a press, so it is
        // the sole exception to clearing: every press that is not one starts over. It needs
        // all three conditions. Shift alone is not enough because only HitTarget.Element
        // supports extend — handleClick resolves it via elementIndexOf — so shift over any
        // other target, Attribution included, would otherwise preserve a selection nothing
        // could extend, leaving the press with no visible effect. And the range must belong
        // to the line just clicked, since an index computed against this line's elements
        // means something different on the line the range actually names.
        var isExtend = e.isShiftDown()
            && hitTarget instanceof HitTarget.Element
            && range != null
            && range.line() == lc.getLine();

        if (isExtend) {
            // pressHandled stays false so the click that follows can extend the range (#748).
            // bandArmed was cleared at the top of this method and nothing since could have set
            // it, so returning here is what keeps this gesture from ever sweeping a band.
            return;
        }

        scoreView.clearSelection();

        // Select the hit element immediately on press.
        // This prevents rubber-band drag from starting on selectable elements.

        // Every variant is spelled out rather than defaulted: what a click does to a newly
        // selectable kind of notation is a decision, and a default arm would make it silently.
        pressHandled = switch (hitTarget) {
            case null -> false;

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

            // A double-click target only, never selected: a press over the attribution does
            // exactly what a press over the empty space above the staff does. Yielding false is
            // also what leaves the rubber band armed there, unchanged from before the block was
            // registered.
            case HitTarget.Attribution _ -> false;
        };

        // A band is possible only after a genuine miss — a stem, the space around a glyph,
        // anywhere the registry has no region — and only where there is something to sweep.
        //
        // The staff test is what confines a sweep to the music. The component is much taller than
        // the staff, and the regions inside it are only as tall as what they draw, so a press in
        // the empty space above the staff or down in the lyric row misses everything however far
        // left or right it is. Without this, such a press over the header or over the terminal
        // would arm a band anchored where a drag is not allowed to reach.
        bandArmed = !pressHandled
            && isWithinStaffY(e.getPoint())
            && hasSweepableColumns();

        // Unconditional, so that "the press changed what is selected" and "the press selected
        // nothing" repaint alike. Which arm produced the answer must not decide it: a target
        // that yields false is indistinguishable on screen from a miss, and registering a new
        // kind of region turns misses into hits without changing what the user sees.
        lc.repaint();
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
     *
     * @param target what this press landed on, or {@code null} when it landed on nothing
     * @return true when a lyric was selected, so the press is finished; false to fall through to
     *         normal EDIT-mode handling
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
        if (!bandArmed) {
            return;
        }

        if (!dragging) {
            var coordinator = lc.getScoreView().getSelectionCoordinator();
            coordinator.getDragTracker().dragDidStart(lc);
        }

        dragging = true;

        // X is clamped to the component, which is exactly the staff's width, so columns clipped
        // off an overflowing line stay unreachable — matching what the user can actually see.
        // Y is not clamped because it is not consulted: the band survives the mouse leaving the
        // line vertically and keeps tracking x until release.
        leadXSs = toSs(Math.clamp(e.getX(), 0, lc.getWidth() - 1));

        var sweepRange = sweepRange();

        if (sweepRange != null) {
            selectSweptColumns(sweepRange, bandWithin(sweepRange));
        }

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
        var scoreView = lc.getScoreView();
        var line = lc.getLine();

        if (e.isShiftDown()
            && line != null
            && scoreView.getSelectionCoordinator().getRange() != null) {
            var elementIndex = LineComponent.elementIndexOf(pressTarget, line);

            if (elementIndex >= 0) {
                scoreView.extendSelectionTo(elementIndex);
                playNoteIfPitched(elementIndex);
            }
        }

        return true;
    }

    void handleRelease() {
        if (dragging) {
            // The two staff-space endpoints are deliberately left alone. Every read of them is
            // gated on dragging, which is false from here until the next press overwrites both.
            dragging = false;
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
     *
     * @return true, always — the constant result is what distinguishes a lyric from every other
     *         target, which can fail on an unregistered line
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
     * the sole selection.
     * <p>
     * The registration is what makes the selected line resolvable: activating an unregistered
     * index would leave the selection pointing at a line nothing can answer for.
     *
     * @return true when the target became the selection; false when this line is not registered
     *         with the coordinator, in which case nothing was selected
     */
    private boolean selectTarget(HitTarget.Selectable target) {
        var scoreView = lc.getScoreView();
        var coordinator = scoreView.getSelectionCoordinator();

        if (coordinator.getLine(lc.getLineIndex()) == null) {
            return false;
        }

        prepareSelection();
        coordinator.select(target);
        scoreView.selectionChanged();

        // The caller repaints this line only. A cross-line tie is also drawn by the line
        // holding its other half, which reports the tie selected too and so has to repaint
        // for the highlight to reach both halves.
        scoreView.repaintTieHalves(target);
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
     * Selects {@code element} and plays it if it is a pitched note.
     * <p>
     * Targets name elements by identity; the index the selection range is built from is derived
     * here, at the point of use.
     *
     * @return true when the element became the selection; false when it is not on this
     *         component's line
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
     * Whether the given X, in staff-space units, is within this line's staff header (clef +
     * optional key signature). Clicks at or before that X select the staff lines.
     *
     * @return true when the X falls in the header
     */
    private boolean isWithinHeaderXSs(double xSs) {
        return HorizontalSpacingCalculator.isWithinHeaderXSs(xSs, lc.getLine());
    }

    /**
     * Whether a press at this point may grow into a band, as far as the staff is concerned.
     * <p>
     * The band spans the staff and sweeps by horizontal position alone, so a gesture that starts
     * off the staff would be one the user has no way to aim. Both staff lines count as on it.
     *
     * @return true when the point's Y lies on the staff, its top and bottom lines included
     */
    private boolean isWithinStaffY(Point viewPoint) {
        return Math.abs(layoutYSs(viewPoint)) <= Staff.STAFF_HALF_SS;
    }

    /**
     * Whether this line holds anything a band could sweep.
     * <p>
     * An empty line and a line holding only the song's auto-maintained terminal both answer
     * false. There is nothing to select on either, so the press does not arm and the drag is a
     * no-op rather than a band swept across bare staff.
     *
     * @return true when a drag on this line could select something
     */
    private boolean hasSweepableColumns() {
        return sweepRange() != null;
    }

    /**
     * What this line's current layout lets a drag reach and select.
     * <p>
     * Rebuilt on every ask rather than captured on press: a line's content can change under a
     * live drag, and re-deriving is what keeps a gesture reading the layout in front of it
     * instead of one that has been replaced.
     *
     * @return the range, or {@code null} when this component has no line, has no layout, or the
     *         line holds nothing a drag could select
     */
    private @Nullable SweepRange sweepRange() {
        var line = lc.getLine();
        var layoutResult = lc.getLayoutResult();

        if (line == null || layoutResult == null) {
            return null;
        }

        return SweepRange.of(line, layoutResult);
    }

    /**
     * The band the current anchor and lead describe, brought inside what {@code sweepRange}
     * allows.
     * <p>
     * Each end is a bare mouse position, never widened out to the column it happens to land in,
     * so the band's edge follows the mouse continuously across a column rather than jumping to
     * the column's far side. Both ends are clamped, so no press position can anchor a band
     * somewhere a drag could not have taken its leading edge.
     *
     * @return the band, whichever direction the drag was drawn in
     */
    private SelectionBand bandWithin(SweepRange sweepRange) {
        return SelectionBand.spanning(sweepRange.clamp(anchorXSs), sweepRange.clamp(leadXSs));
    }

    /**
     * Replaces the selection with the columns the band touches, anchored at the first of them.
     * <p>
     * The anchor is the first column whichever direction the band was drawn in, and whatever the
     * press point landed on. It is the fixed point every later Shift+click and Shift+arrow
     * extends from, and nothing but a new plain click or drag may move it (issue #748).
     * <p>
     * Which columns those are is {@code sweepRange}'s answer, not this method's — including the
     * exclusion of the auto-maintained terminal (issue #713), which is a consequence of the range
     * not holding it rather than a check made here.
     * <p>
     * The whole range is resolved before anything is selected, so a drag event assigns the
     * selection exactly once no matter how many columns it touches.
     */
    private void selectSweptColumns(SweepRange sweepRange, SelectionBand band) {
        var coordinator = lc.getScoreView().getSelectionCoordinator();
        coordinator.activateLine(lc.getLineIndex());

        var touched = sweepRange.overlapping(band.leftSs().value(), band.rightSs().value());

        if (touched == null) {
            coordinator.clearActiveSelection();
            return;
        }

        coordinator.selectRange(touched.begin(), touched.end());
    }
}
