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
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.hit.HitRegistry;
import songscribe.hit.HitTarget;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;
import songscribe.ui.Mode;
import songscribe.layout.ColumnSpan;
import songscribe.layout.ElementColumn;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;
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
     * The gap the band's leading edge keeps from the staff header, so a sweep never touches it.
     * Package-private for testing.
     * <p>
     * There is no matching gap on the right: the auto-maintained terminal is right-aligned with
     * the end of the staff, so the leading edge stopping at its left ink edge already leaves no
     * reachable staff beyond it.
     * <p>
     * Document pixels rather than view pixels: the reachable range is then the same music at
     * every zoom, which is the whole reason the band's endpoints are held in staff spaces.
     */
    static final double HEADER_GAP_PX = 1.0;

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

    /**
     * The band's fixed end, in line-local staff spaces, captured on press. Never clamped, so a
     * press in a leading gap or in the header keeps its raw x.
     */
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
     * The band's horizontal extent, in line-local staff spaces, with {@code leftSs <= rightSs}.
     * <p>
     * A band has no vertical extent of its own. It always spans the staff, which is line
     * geometry rather than anything the drag decides, so {@link LineComponent#getStaffTopYSs}
     * and {@link LineComponent#getStaffBottomYSs} answer for that instead.
     */
    record SelectionBand(double leftSs, double rightSs) {}

    /**
     * The live selection band, or {@code null} when no drag is in progress.
     * <p>
     * Derived on every call rather than stored, and left in staff spaces rather than converted
     * to pixels. Staff spaces name a position in the music, so a zoom mid-drag re-renders the
     * band over the same music at the new scale instead of leaving a stale pixel rectangle
     * behind. The one conversion to pixels happens at paint time, in
     * {@link LineRenderer#renderSelectionBand}.
     */
    @Nullable SelectionBand getSelectionBand() {
        return dragging ? computeBand() : null;
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
        bandArmed = false;

        // Only the press x matters: the band's vertical extent is the staff, not the mouse.
        anchorXSs = toSs(e.getX());
        leadXSs = anchorXSs;

        pressTarget = hitTarget;

        var scoreView = lc.getScoreView();
        var range = scoreView.getSelectionCoordinator().getRange();

        // Don't clear selection on shift+click (preserve for extend)
        if (!e.isShiftDown() || range == null) {
            scoreView.clearSelection();
        }

        // Select the hit element immediately on press.
        // This prevents rubber-band drag from starting on selectable elements.
        // Shift+click on a note head is handled in handleClick for extend-selection.
        if (e.isShiftDown() && hitTarget instanceof HitTarget.Element) {
            // pressHandled stays false so the click that follows can extend the range (#748).
            // bandArmed was cleared at the top of this method and nothing since could have set
            // it, so returning here is what keeps this gesture from ever sweeping a band.
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

        // A band is possible only after a genuine miss — a stem, the space around a glyph,
        // anywhere the registry has no region — and only where there is something to sweep.
        bandArmed = !pressHandled && hasSweepableColumns();

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

        calculateLineSelectionFromDrag(computeBand());
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
     * Whether this line holds anything a band could sweep.
     * <p>
     * An empty line and a line holding only the song's auto-maintained terminal both answer
     * false. There is nothing to select on either, so the press does not arm and the drag is a
     * no-op rather than a band swept across bare staff.
     */
    private boolean hasSweepableColumns() {
        var line = lc.getLine();

        return line != null && line.effectiveElementCount() > 0;
    }

    /** {@link #HEADER_GAP_PX} in staff spaces. */
    private static double headerGapSs() {
        return ScaleContext.pxToSs(HEADER_GAP_PX);
    }

    /**
     * The leftmost x, in line-local staff spaces, the band's leading edge may reach: clear of the
     * staff header, whose own press selects the staff lines rather than sweeping.
     */
    private static double sweepLeftLimitSs(Line line) {
        return HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(line) + headerGapSs();
    }

    /**
     * The rightmost x, in line-local staff spaces, the band's leading edge may reach: the left
     * ink edge of the auto-maintained terminal's column, or the end of the staff when the line
     * carries no terminal.
     * <p>
     * Stopping at the terminal is what keeps it out of every selection (issue #713) — the scan in
     * {@link #calculateLineSelectionFromDrag} would otherwise be the only thing excluding an
     * element the band visibly covers.
     *
     * @param sweepableCount this line's sweepable column count, which is also the terminal's own
     *                       element index when the line carries one
     */
    private static double sweepRightLimitSs(
        Line line, LayoutResult layoutResult, int sweepableCount) {

        var staffEndXSs = line.getSong().getLineWidthSs();

        if (sweepableCount == line.elementCount()) {
            return staffEndXSs;
        }

        // At this point terminalColumn should never be null, but getElementColumn()
        // is Nullable, so we have to provide a fallback.
        var terminalColumn = layoutResult.getElementColumn(line.getElement(sweepableCount));
        return terminalColumn == null ? staffEndXSs : terminalColumn.getLeftEdgeXSs();
    }

    /**
     * The sweepable column containing {@code xSs}, or {@code null} if that x lands in a gap,
     * past the last column, or inside the song's auto-maintained terminal.
     * <p>
     * Recomputed on every call rather than resolved once on press: a mid-drag re-layout would
     * otherwise leave a stale {@link ElementColumn} behind, and there would be an invalidation
     * path to maintain. Spans overlap only at a grace-host boundary (the #560 flag discount);
     * the hit tester's first-match-wins order gives that sliver to the grace note.
     */
    private @Nullable ElementColumn columnAt(
        double xSs, LayoutResult layoutResult, Line line, int sweepableCount) {

        var elementIndex = layoutResult.findElementAtXSs(xSs, line, ColumnSpan.FULL_INK);

        if (elementIndex < 0 || elementIndex >= sweepableCount) {
            return null;
        }

        return layoutResult.getElementColumn(line.getElement(elementIndex));
    }

    /**
     * What one end of the band contributes to it: the whole span of the column that end landed
     * in, or the bare point when it landed in a gap.
     */
    private static SelectionBand contributionAt(double xSs, @Nullable ElementColumn column) {
        if (column == null) {
            return new SelectionBand(xSs, xSs);
        }

        return new SelectionBand(column.getLeftEdgeXSs(), column.getRightEdgeXSs());
    }

    /**
     * The band spanned by the current anchor and leading edges: the smallest interval containing
     * both of their contributions.
     * <p>
     * A contribution is the whole span of the column that end lands in, or the bare point when it
     * lands in a gap. That one rule produces every required behavior — the band snaps out to a
     * column's far extent the moment the mouse enters it and holds there, resumes tracking
     * continuously on the way out, covers the anchor's column from the very first movement, and
     * keeps the anchor column enclosed when the drag reverses across it.
     * <p>
     * The leading edge is clamped to the sweepable range — the full staff between the header and
     * the terminal, not merely the content within it, so the band reaches the bare staff on either
     * side of the music. The anchor is deliberately not clamped, so a press in a leading gap or in
     * the header keeps its raw x.
     */
    private SelectionBand computeBand() {
        var line = lc.getLine();
        var layoutResult = lc.getLayoutResult();
        var rawBand = new SelectionBand(
            Math.min(anchorXSs, leadXSs), Math.max(anchorXSs, leadXSs));

        // A press only arms a band where there is something to sweep, so a line with no sweepable
        // column cannot reach this. It is still guarded rather than assumed: the line's content
        // can change under a live drag, and this is the one path that would then reach for a
        // column that no longer exists.
        if (line == null || layoutResult == null) {
            return rawBand;
        }

        var sweepableCount = line.effectiveElementCount();

        if (sweepableCount == 0) {
            return rawBand;
        }

        var clampedLeadXSs = Math.clamp(
            leadXSs,
            sweepLeftLimitSs(line),
            sweepRightLimitSs(line, layoutResult, sweepableCount));
        var anchor = contributionAt(
            anchorXSs, columnAt(anchorXSs, layoutResult, line, sweepableCount));
        var lead = contributionAt(
            clampedLeadXSs, columnAt(clampedLeadXSs, layoutResult, line, sweepableCount));

        return new SelectionBand(
            Math.min(anchor.leftSs(), lead.leftSs()),
            Math.max(anchor.rightSs(), lead.rightSs()));
    }

    /**
     * Replaces the selection with the elements the band covers, anchored at the first
     * of them.
     * <p>
     * The anchor is {@code begin} whichever direction the band was drawn in, and whatever the
     * press point landed on. It is the fixed point every later Shift+click and Shift+arrow
     * extends from, and nothing but a new plain click or drag may move it (issue #748).
     * <p>
     * An element is swept iff its column's full-ink span overlaps the band. Vertical position is
     * not consulted anywhere, so a note four ledger lines up, a rest and a barline are all swept
     * identically. The scan's {@code 0 .. effectiveElementCount() - 1} bound <b>is</b> the
     * terminal-exclusion rule of issue #713; there is no per-element terminal check alongside it.
     * <p>
     * The whole range is computed before anything is selected, so a drag event assigns the
     * selection exactly once no matter how many elements it sweeps.
     */
    private void calculateLineSelectionFromDrag(SelectionBand band) {
        var scoreView = lc.getScoreView();
        var line = lc.getLine();
        var layoutResult = lc.getLayoutResult();

        if (line == null || layoutResult == null) {
            return;
        }

        var coordinator = scoreView.getSelectionCoordinator();
        coordinator.activateLine(lc.getLineIndex());

        var sweepableCount = line.effectiveElementCount();
        var begin = -1;
        var end = -1;

        for (var elementIndex = 0; elementIndex < sweepableCount; elementIndex++) {
            var column = layoutResult.getElementColumn(line.getElement(elementIndex));

            if (column == null) {
                continue;
            }

            if (column.getLeftEdgeXSs() <= band.rightSs()
                && column.getRightEdgeXSs() >= band.leftSs()) {
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

        coordinator.selectRange(begin, end);
    }
}
