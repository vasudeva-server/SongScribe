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
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.NoteElement;
import songscribe.dom.Song;
import songscribe.dom.Ss;
import songscribe.dom.StaffElement;
import songscribe.dom.ViewPx;
import songscribe.engraving.Staff;
import songscribe.hit.HitRegion;
import songscribe.hit.HitRegistry;
import songscribe.hit.HitTarget;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.layout.SweepRange;
import songscribe.lint.LintMark;
import songscribe.lint.LintReport;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayThread;
import songscribe.ui.renderer.LineInvariants;

// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)

/**
 * Handles selection, hit-testing, and drag logic for a {@link LineComponent}. Hit testing itself
 * lives in the line's {@link HitRegistry}.
 */
class LineSelectionHandler {

    private final LineComponent lc;

    private boolean dragging = false;
    private boolean pressHandled = false;

    /**
     * Distinct from {@link #pressHandled}: a Shift press on an element leaves it false so the
     * click can extend the range, yet must never sweep.
     */
    private boolean bandArmed = false;

    /** Null if the press landed on nothing. */
    private @Nullable HitTarget pressTarget = null;

    private double anchorXSs = 0.0;

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
     * A band has no vertical extent of its own; it always spans the staff. Build one with
     * {@link #spanning}, which orders the two ends; the canonical constructor takes them already
     * ordered.
     */
    record SelectionBand(Ss leftSs, Ss rightSs) {

        static SelectionBand spanning(double anchorSs, double leadSs) {
            return new SelectionBand(
                new Ss(Math.min(anchorSs, leadSs)), new Ss(Math.max(anchorSs, leadSs)));
        }
    }

    /**
     * {@code null} when no drag is in progress or the line's layout went stale under it. Kept in
     * staff spaces so a zoom mid-drag re-renders the band over the same music.
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
     * @return {@code null} when the point landed on nothing clickable or this component has no
     *         line to test against
     */
    @Nullable HitTarget hitTestViewPoint(Point viewPoint) {
        var region = hitRegionAtViewPoint(viewPoint);

        if (region == null) {
            return null;
        }

        return region.target();
    }

    /** Null when {@link #hitTestViewPoint} returns null. */
    @Nullable HitRegion hitRegionAtViewPoint(Point viewPoint) {
        var registry = readyRegistry();

        if (registry == null) {
            return null;
        }

        return registry.hitRegion(layoutXSs(viewPoint), layoutYSs(viewPoint));
    }

    /**
     * @return {@code null} when this component has no line to hit-test or its layout could not
     *         be brought up to date
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
     * Converts straight to staff spaces: rounding through whole document pixels would cost half a
     * pixel of precision, which the zoom multiplies into visible slop.
     */
    private double toSs(int viewPx) {
        return lc.getViewScale().toSs(new ViewPx(viewPx)).value();
    }

    double layoutXSs(Point viewPoint) {
        return toSs(viewPoint.x);
    }

    private double layoutYSs(Point viewPoint) {
        return toSs(viewPoint.y) - lc.getMiddleLineYSs();
    }

    /**
     * A "?" has no region in the hit registry: layout places a placeholder under every slot
     * lacking a syllable, but the lint report decides which are drawn, and layout never reads it.
     *
     * @return {@code null} when the point is not over a "?" or this component has no line laid out
     */
    @Nullable MissingLyricHit hitTestMissingLyricViewPoint(Point viewPoint) {
        var ready = lc.readyLayout();

        if (ready == null) {
            return null;
        }

        var line = ready.line();
        var layoutResult = ready.layoutResult();
        var report = line.getSong().getLinter().getReport();
        var lyricRenderMetrics = lc.getLyricRenderMetrics();
        var pointSs = new Point2D.Double(layoutXSs(viewPoint), layoutYSs(viewPoint));

        for (var element : line.getElements()) {
            for (var mark : report.marksOn(element)) {
                if (!(mark instanceof LintMark.MissingLyric missingLyric)
                    || LineInvariants.drawnLyricPlaceholder(missingLyric, layoutResult) == null) {
                    continue;
                }

                var boundsSs = markBoundsSs(missingLyric, layoutResult, lyricRenderMetrics);

                if (boundsSs.contains(pointSs)) {
                    return new MissingLyricHit(missingLyric, boundsSs);
                }
            }
        }

        return null;
    }

    /**
     * The "?" comes first because it stands in the lyric row, and a lyric outranks every other
     * target.
     *
     * @param region       {@code null} means the point is on no region, or this component has no
     *                     line laid out
     * @param missingLyric {@code null} means the point is on no "?"
     * @return {@code null} when no mark is hit
     */
    @Nullable LintMarkHit lintMarkAt(
        @Nullable HitRegion region,
        @Nullable MissingLyricHit missingLyric,
        LintReport report,
        LayoutResult layoutResult
    ) {
        if (missingLyric != null) {
            return new LintMarkHit(missingLyric.mark(), missingLyric.boundsSs());
        }

        if (region == null) {
            return null;
        }

        var mark = lintMarkOf(region.target(), report);

        if (mark == null) {
            return null;
        }

        return new LintMarkHit(mark, markBoundsSs(mark, layoutResult, lc.getLyricRenderMetrics()));
    }

    LintMarkHit lintMarkHit(LintMark mark) {
        return new LintMarkHit(mark, markBoundsSs(mark, lc.requireLayoutResult(), lc.getLyricRenderMetrics()));
    }

    /**
     * The box is in line-local, midline-relative staff spaces, before the registry's inclusive
     * edge margin.
     *
     * @param lyricRenderMetrics the lyric render metrics {@code layoutResult} was laid out with
     */
    static Rectangle2D markBoundsSs(LintMark mark, LayoutResult layoutResult, LyricRenderMetrics lyricRenderMetrics) {
        return switch (mark) {
            case LintMark.Flagged flagged -> layoutResult.elementHitRectSs(flagged.element());

            case LintMark.FlaggedAnnotation flaggedAnnotation -> {
                var annotationLayout = layoutResult.decorationLayouts()
                    .findAttachmentDecorationLayout(flaggedAnnotation.element(), AnnotationAttachment.class);

                if (annotationLayout == null) {
                    throw new IllegalStateException("layout has no annotation for lint mark: " + mark);
                }

                yield annotationLayout.inkRectSs();
            }

            case LintMark.MissingLyric missingLyric -> {
                var placeholder = LineInvariants.drawnLyricPlaceholder(missingLyric, layoutResult);

                if (placeholder == null) {
                    throw new IllegalStateException("layout draws no \"?\" for lint mark: " + mark);
                }

                yield layoutResult.lyricBoxRectSs(placeholder, lyricRenderMetrics);
            }
        };
    }

    /**
     * @return {@code null} when {@code report} puts no such mark on the target
     */
    private static @Nullable LintMark lintMarkOf(HitTarget target, LintReport report) {
        LintMark mark = null;

        if (target instanceof HitTarget.Element(var element)) {
            mark = new LintMark.Flagged(element);
        } else if (target instanceof HitTarget.Attachment(AnnotationAttachment annotation)
            && annotation.owner() instanceof StaffElement owner) {
            mark = new LintMark.FlaggedAnnotation(owner);
        }

        if (mark == null || !report.marksOn(mark.element()).contains(mark)) {
            return null;
        }

        return mark;
    }

    record MissingLyricHit(LintMark.MissingLyric mark, Rectangle2D boundsSs) { }

    record LintMarkHit(LintMark mark, Rectangle2D boundsSs) { }

    /**
     * Covers the whole header column, since no element can be inserted anywhere in it.
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
            // pressHandled stays false so the click that follows can extend the range.
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

            case HitTarget.Lyric(var element, var script) -> selectLyric(element, script);

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
     * A lyric is the <b>only</b> thing selectable in EDIT mode, because insertion cannot reach it
     * anyway: {@code mouseMoved} clears the insertion preview over a lyric box. The
     * {@link LineComponent#hasPreviewElement()} guard is a fallback for a preview that appears
     * over lyric text without mouse movement; insertion wins there. Takes the already-computed
     * cascade result, so an element head over the lyric still wins.
     *
     * @param target {@code null} when the press landed on nothing
     */
    boolean handleEditModePress(@Nullable HitTarget target) {
        if (MidiController.isPlaying()) {
            return false;
        }

        if (lc.hasPreviewElement()) {
            return false;
        }

        var selected = target instanceof HitTarget.Lyric(var element, var script)
            && selectLyric(element, script);

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

            if (elementIndex != null) {
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

    private void prepareSelection() {
        lc.getScoreView().clearSelection();
        lc.getScoreView().getSelectionCoordinator().activateLine(lc.getLineIndex());
    }

    /**
     * Always succeeds: a lyric names its own line, so there is no registration to find missing.
     */
    @SuppressWarnings("SameReturnValue")
    private boolean selectLyric(StaffElement element, Song.Script script) {
        var scoreView = lc.getScoreView();
        scoreView.getSelectionCoordinator().selectLyric(element, script);
        scoreView.selectionChanged();
        return true;
    }

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

    void selectElementAtIndex(int elementIndex) {
        var scoreView = lc.getScoreView();
        var coordinator = scoreView.getSelectionCoordinator();

        // Repaint the previously active line, since its selection highlight would
        // otherwise stay on screen even though selectSingleElement clears its selection.
        // This is for the NoteDragHandler path, which selects a pressed note without
        // clearing first, so the outgoing line is still active here. On the handlePress
        // path there is nothing to do — it calls ScoreView.clearSelection() up front,
        // which repaints the outgoing line itself and leaves no active line behind.
        var activeLineIndex = coordinator.getActiveLineIndex();
        var deselectedLine = activeLineIndex == null ? null : scoreView.getLineComponent(activeLineIndex);

        if (coordinator.selectSingleElement(lc.getLineIndex(), elementIndex)) {
            scoreView.selectionChanged();
        }

        if (deselectedLine != null && deselectedLine != lc) {
            deselectedLine.repaint();
        }
    }

    void selectAndPlayElement(int elementIndex) {
        selectElementAtIndex(elementIndex);
        playNoteIfPitched(elementIndex);
    }

    private boolean selectAndPlayElement(StaffElement element) {
        var line = lc.getLine();

        if (line == null || !line.contains(element)) {
            return false;
        }

        selectAndPlayElement(line.getElementIndex(element));
        return true;
    }

    void playNoteIfPitched(int elementIndex) {
        if (!Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)) {
            return;
        }

        var line = lc.getLine();

        if (line == null) {
            return;
        }

        if (line.getElement(elementIndex) instanceof NoteElement note) {
            new PlayThread(note.getPitch()).start();
        }
    }

    // ======================================================================
    // Private helpers
    // ======================================================================

    private boolean isWithinHeaderXSs(double xSs) {
        return HorizontalSpacingCalculator.isWithinHeaderXSs(xSs, lc.getLine());
    }

    /**
     * The band sweeps by horizontal position alone, so a gesture starting off the staff could
     * not be aimed. Both staff lines count as on it.
     */
    private boolean isWithinStaffY(Point viewPoint) {
        return Math.abs(layoutYSs(viewPoint)) <= Staff.STAFF_HALF_SS;
    }

    /**
     * An empty line and a line holding only the song's auto-maintained terminal answer false, so
     * the press does not arm.
     */
    private boolean hasSweepableColumns() {
        return sweepRange() != null;
    }

    /**
     * Rebuilt on every ask, since a line's content can change under a live drag.
     *
     * @return {@code null} when this component has no line, has no layout, or the line holds
     *         nothing a drag could select
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
     * Each end is a bare mouse position, never widened to its column, so the edge follows the
     * mouse continuously. Both ends are clamped.
     */
    private SelectionBand bandWithin(SweepRange sweepRange) {
        return SelectionBand.spanning(sweepRange.clamp(anchorXSs), sweepRange.clamp(leadXSs));
    }

    /**
     * The anchor is the first column whichever direction the band was drawn in. It is the fixed
     * point every later Shift+click and Shift+arrow extends from; only a new plain click or drag
     * may move it.
     */
    private void selectSweptColumns(SweepRange sweepRange, SelectionBand band) {
        var coordinator = lc.getScoreView().getSelectionCoordinator();
        coordinator.activateLine(lc.getLineIndex());

        var touched = sweepRange.overlapping(band.leftSs().value(), band.rightSs().value());

        if (touched == null) {
            coordinator.clearActiveSelection();
            return;
        }

        coordinator.selectRange(touched);
    }
}
