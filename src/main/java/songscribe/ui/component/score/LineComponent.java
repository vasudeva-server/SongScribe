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
import songscribe.dom.*;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.component.ComponentNames;
import songscribe.ui.component.LyricEditor;
import songscribe.ui.component.LyricTargetResolver;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.edit.PasteModeManager;
import songscribe.ui.component.ScoreView;
import songscribe.layout.Ending;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineSpacing;
import songscribe.layout.LyricRenderMetrics;
import songscribe.engraving.Staff;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.renderer.LineInvariants;
import songscribe.util.GraphicsState;
import songscribe.ui.selection.LineSelectionState;
import songscribe.error.RuntimeError;

/**
 * Component that renders a single staff line with its musical content.
 * <p>
 * This is the core rendering component that:
 * <ul>
 *   <li>Draws the 5-line staff</li>
 *   <li>Renders notes and other elements via {@link LineRenderer}</li>
 * </ul>
 */
public class LineComponent extends ScoreComponent
    implements MouseMotionListener, MouseListener {

    // ==========================================================================
    // Functional Interface for Selection State
    // ==========================================================================

    /**
     * Interface for checking selection state.
     * <p>
     * Allows LineComponent to check selection without coupling to ScoreView.
     */
    public interface SelectionProvider {
        /**
         * Returns whether the specified element is selected.
         *
         * @param elementIndex The element index within the line
         * @param lineIndex    The line index
         * @return true if the element is selected
         */
        boolean isElementSelected(int elementIndex, int lineIndex);

        /**
         * Returns whether the staff line itself is selected (for deletion).
         *
         * @param lineIndex The line index
         * @return true if the staff line is selected
         */
        boolean isLineSelected(int lineIndex);

        /**
         * Returns whether the slide owned by the element at the given index
         * is selected.
         *
         * @param elementIndex The element index within the line
         * @param lineIndex    The line index
         * @return true if the slide is selected
         */
        boolean isSlideSelected(int elementIndex, int lineIndex);

        /**
         * Returns whether the given ending is selected.
         *
         * @param ending    The ending
         * @param lineIndex The line index
         * @return true if the ending is selected
         */
        boolean isEndingSelected(Ending ending, int lineIndex);

        boolean isLyricSelected(StaffElement element, int verse, int lineIndex);
    }

    // ==========================================================================
    // Instance Fields
    // ==========================================================================

    /** The line model containing staff elements. */
    @Nullable
    private Line line;

    /** Index of this line within the song. */
    private int lineIndex;

    /** Per-line selection state. */
    @Nullable
    private LineSelectionState lineSelectionState;

    /** Y coordinate of the middle staff line (B line) in staff-space units. */
    private double middleLineYSs;

    /** Whether {@link #middleLineYSs} reflects the current layout. */
    private boolean middleLineYSsValid;

    /** Provider for checking note selection state. */
    @Nullable
    private SelectionProvider selectionProvider;

    /** Index of the currently playing note (-1 if not playing). */
    private int playingNoteIndex = -1;

    /** Index of the grace note paired with the currently playing note (-1 if none). */
    private int playingGraceNoteIndex = -1;

    /** Cached layout result from the last layout pass. Package-private for test injection. */
    @Nullable
    LayoutResult layoutResult;

    /** Whether layout needs to be recalculated. Package-private for test inspection. */
    boolean layoutDirty = true;

    /**
     * True when the most recent layout attempt could not fit this line's content
     * within the staff while maintaining minimum spacing.
     * <p>
     * Interim handling for issue #449: rather than the fatal crash, the app keeps
     * the last good layout (if any) and warns non-fatally. The proper fix — undo
     * the offending edit — is blocked on the undo system being implemented.
     */
    private boolean lineDoesNotFit;

    /**
     * Guards the non-fatal "line too full" warning so a single dialog is shown per
     * failure episode, not once per failing line per paint. Reset after the deferred
     * dialog is dismissed. EDT-only, so a plain static boolean is safe.
     */
    private static boolean lineDoesNotFitWarningScheduled;

    /** True when the previous line's lyric extender continues into this line. */
    private boolean hasLeadingLyricContinuation;

    /**
     * Element index hit by the most recent press, or -1 when that press hit no element.
     * {@link NoteDragHandler} computes this on press and discards it on release, so it is
     * captured in {@link #mouseReleased} for {@link #mouseClicked} — which runs next — to
     * reuse instead of scanning the line's elements a second time for the same point.
     */
    private int pressHitIndex = -1;

    /** Handles selection, hit-testing, and drag logic. */
    private final LineSelectionHandler selectionHandler = new LineSelectionHandler(this);

    /** Handles press/drag/release for pitch-dragging a note in NOTE_EDIT mode. */
    private final NoteDragHandler noteDragHandler = new NoteDragHandler(this);

    /** Renderer that handles all drawing for this line. */
    private final LineRenderer lineRenderer = new LineRenderer(this);

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Click count that identifies a double-click in a {@link MouseEvent}. */
    private static final int DOUBLE_CLICK_COUNT = 2;

    /**
     * Creates a new LineComponent.
     */
    public LineComponent() {
        addMouseMotionListener(this);
        addMouseListener(this);
    }

    /**
     * Sets the line to render.
     *
     * @param line      The line model
     * @param lineIndex Index of the line in the song
     */
    public void setLine(Line line, int lineIndex) {
        this.line = line;
        this.lineIndex = lineIndex;
        setName(ComponentNames.line(lineIndex));
        lineSelectionState = new LineSelectionState(line);
        layoutDirty = true;
        layoutResult = null;
        lineDoesNotFit = false;
        middleLineYSsValid = false;

        // Register with coordinator if score is available
        if (scoreView != null) {
            var coordinator = scoreView.getSelectionCoordinator();
            coordinator.registerLineState(lineIndex, lineSelectionState);
        }

        revalidate();
        repaint();
    }

    /**
     * Returns the line model.
     */
    public @Nullable Line getLine() {
        return line;
    }

    /**
     * Returns the line index.
     */
    public int getLineIndex() {
        return lineIndex;
    }

    /**
     * Returns the per-line selection state.
     */
    @Nullable
    public LineSelectionState getLineSelectionState() {
        return lineSelectionState;
    }

    /**
     * Sets the Y coordinate of the middle staff line in staff-space units.
     *
     * @param middleLineYSs Y coordinate in staff-space units
     */
    public void setMiddleLineYSs(double middleLineYSs) {
        this.middleLineYSs = middleLineYSs;
    }

    /**
     * Returns the Y coordinate of the middle staff line in staff-space units.
     * <p>
     * Lazily calculates the value if it hasn't been set yet.
     */
    public double getMiddleLineYSs() {
        if (!middleLineYSsValid && song != null) {
            middleLineYSs = calculateMiddleLineYSs();
            middleLineYSsValid = true;
        }

        return middleLineYSs;
    }

    /**
     * Returns the Y coordinate of the middle staff line in pixels.
     * <p>
     * Bridge method for callers not yet converted to staff-space units.
     * Will be removed when renderers and mouse code are converted.
     */
    public int getMiddleLineYPx() {
        return (int) Math.round(ScaleContext.ssToPx(getMiddleLineYSs()) * getViewScale().factor());
    }

    /**
     * Returns the zoomed staff-space-to-pixel scale (document {@code pixelsPerStaffSpace} × the
     * view zoom factor) for this line.
     * <p>
     * The component-side counterpart of {@code LineInvariants.getViewPixelsPerStaffSpace()},
     * which is reachable only from inside a render pass. Callers outside one — the overlay
     * components, which establish their own scale-and-translate — read the zoomed scale here
     * rather than re-multiplying the expression inline.
     */
    public double getViewPixelsPerStaffSpace() {
        return ScaleContext.getPixelsPerStaffSpace() * getViewScale().factor();
    }

    /**
     * Converts a staff position to a Y pixel coordinate.
     *
     * @param staffPositionSp The staff position (0 = middle line)
     * @return Y coordinate in pixels
     */
    public int staffPositionToYPx(int staffPositionSp) {
        return getMiddleLineYPx()
            + (int) Math.round(ScaleContext.ssToPx(Staff.spToSs(staffPositionSp)) * getViewScale().factor());
    }

    /**
     * Sets the selection provider for checking note selection state.
     *
     * @param selectionProvider The selection provider
     */
    public void setSelectionProvider(@Nullable SelectionProvider selectionProvider) {
        this.selectionProvider = selectionProvider;
    }

    /**
     * Sets the ScoreView reference for accessing song and services.
     *
     * @param scoreView The ScoreView component
     */
    @Override
    public void setScoreView(ScoreView scoreView) {
        super.setScoreView(scoreView);

        // Register LineSelectionState with coordinator when score is set
        if (lineSelectionState != null) {
            var coordinator = scoreView.getSelectionCoordinator();
            coordinator.registerLineState(lineIndex, lineSelectionState);
        }
    }

    /**
     * Sets both playing indices atomically, triggering a single repaint.
     *
     * @param noteIndex Note index, or -1 if not playing
     * @param graceNoteIndex Grace note index, or -1 if none
     */
    public void setPlayingIndices(int noteIndex, int graceNoteIndex) {
        if (playingNoteIndex != noteIndex || playingGraceNoteIndex != graceNoteIndex) {
            playingNoteIndex = noteIndex;
            playingGraceNoteIndex = graceNoteIndex;
            repaint();
        }
    }

    /**
     * Returns the index of the currently playing note (-1 if not playing).
     */
    public int getPlayingNoteIndex() {
        return playingNoteIndex;
    }

    /**
     * Returns the index of the grace note paired with the currently playing note (-1 if none).
     */
    public int getPlayingGraceNoteIndex() {
        return playingGraceNoteIndex;
    }

    /**
     * Returns whether the score is in an interactive editing mode (note edit or select),
     * as opposed to an adjustment mode.
     */
    public boolean isEditMode() {
        return !getScoreView().getMode().isAdjustmentMode();
    }

    /**
     * Marks the layout as dirty, requiring recalculation on next render.
     */
    public void invalidateLayout() {
        layoutDirty = true;
        layoutResult = null;
        lineDoesNotFit = false;
        middleLineYSsValid = false;
        revalidate();
        repaint();
    }

    /**
     * Returns the current layout result, or null if not yet calculated.
     * <p>
     * The layout result contains calculated bounds for all elements on this line.
     * Renderers can use this to get positions instead of calculating them directly.
     *
     * @return The layout result, or null if layout hasn't been performed yet
     */
    @Nullable
    public LayoutResult getLayoutResult() {
        return layoutResult;
    }

    /**
     * Puts this line into the issue #449 does-not-fit state, in which a null layout result
     * is the expected steady state rather than a signal to lay out again.
     * <p>
     * Exists so tests can reproduce that state directly: it is otherwise only reachable by
     * driving a real layout that fails to fit, which no unit test can arrange cheaply.
     */
    void setLineDoesNotFit(boolean lineDoesNotFit) {
        this.lineDoesNotFit = lineDoesNotFit;
    }

    /**
     * Returns true when no layout result exists and its absence is not the expected
     * outcome of the issue #449 does-not-fit state, which deliberately keeps the last
     * good result (or none at all) rather than producing a new one.
     */
    private boolean layoutMissing() {
        return layoutResult == null && !lineDoesNotFit;
    }

    /** Returns true when the layout must be recomputed before its results can be read. */
    private boolean needsLayout() {
        return layoutDirty || layoutMissing();
    }

    /**
     * Ensures the layout is up to date, computing it if dirty.
     * <p>
     * Called by {@link StaffPanel#ensureAllLineLayouts()} before collecting all layout
     * results, since {@link StaffLinesLayout} measures every line from them.
     */
    public void ensureLayout() {
        if (song != null && line != null && needsLayout()) {
            performLayout();
        }
    }

    /**
     * Sets whether this line starts with a lyric extender continuation from the previous line.
     * <p>
     * The layout pass uses this flag to emit a leading-stub extender from x = 0 to the first
     * lyric-bearing element (or to the first rest that breaks the continuation).
     * <p>
     * Marks the layout dirty if the value changes so the next {@code performLayout()} picks
     * up the new state.
     */
    public void setHasLeadingLyricContinuation(boolean hasLeadingLyricContinuation) {
        if (this.hasLeadingLyricContinuation != hasLeadingLyricContinuation) {
            this.hasLeadingLyricContinuation = hasLeadingLyricContinuation;
            layoutDirty = true;
            middleLineYSsValid = false;
        }
    }

    private void performLayout() {
        if (song == null || line == null || scoreView == null) {
            return;
        }

        var staffRightMarginSs = song.getLineWidthSs();
        var isLastLine = lineIndex == song.lineCount() - 1;
        var isFirstLine = lineIndex == 0;
        var view = scoreView;
        var lyricRenderMetrics = view.getLyricRenderMetrics();
        var layoutEngine = new LayoutEngine(lyricRenderMetrics, staffRightMarginSs, view);

        Attribution attribution = null;

        if (isFirstLine) {
            attribution = song.getAttributionElement();
            var pane = song.getAttributionPane();
            // Measure at natural (unzoomed) scale so the reserved staff-space dimensions
            // are zoom-invariant by construction — no measure-at-zoom-then-divide-back
            // round trip. Zoom is applied only when the block is painted (see render),
            // where the whole block scales uniformly by the view factor.
            var sizePx = pane.getContentSizePx(view.getAttributionFont(), view.getSubAttributionFont());
            attribution.setDimensionsSs(
                ScaleContext.pxToSs(sizePx.width),
                ScaleContext.pxToSs(sizePx.height));
        }

        var result = layoutEngine.layout(
            line, isLastLine, hasLeadingLyricContinuation, attribution);

        if (result == null) {
            // Issue #449 interim handling: the content cannot fit while maintaining
            // minimum spacing. Crashing is not acceptable, and overflowing the margin
            // is not either, so keep the last good layout (if any) and warn the user
            // non-fatally. The proper fix — undo the offending edit — awaits the undo
            // system. layoutResult is intentionally left unchanged.
            lineDoesNotFit = true;
            layoutDirty = false;
            warnLineDoesNotFit();
            return;
        }

        lineDoesNotFit = false;
        layoutResult = result;
        layoutDirty = false;
    }

    /**
     * Shows the non-fatal "line too full" warning once per failure episode.
     * <p>
     * Deferred to a later EDT cycle because layout runs during paint; showing a
     * modal dialog synchronously would re-enter painting. See issue #449.
     */
    private static void warnLineDoesNotFit() {
        if (lineDoesNotFitWarningScheduled) {
            return;
        }

        lineDoesNotFitWarningScheduled = true;

        SwingUtilities.invokeLater(() -> {
            OptionDialogs.showWarningMessage(
                null, Strings.ALERT_TITLE_LINE_TOO_FULL, Strings.ALERT_LINE_TOO_FULL);
            lineDoesNotFitWarningScheduled = false;
        });
    }

    @Override
    protected void render(Graphics2D g2) {
        if (song == null || line == null) {
            return;
        }

        // Perform layout if dirty
        if (needsLayout()) {
            performLayout();
        }

        middleLineYSs = calculateMiddleLineYSs();
        middleLineYSsValid = true;

        // ── Paint pipeline: the zoom factor is applied EXACTLY ONCE ─────────────────
        //
        //   g2.scale(pxPerSs × factor)              <- single factor application, here
        //        │
        //        ├─► LineRenderer + all element renderers draw in Ss; they NEVER
        //        │   re-multiply by pxPerSs or factor (a ssToPx inside a renderer is a bug).
        //        │
        //        └─► exception: LyricTextRenderer strips this transform and re-derives its
        //            own integer-pixel coords from LineInvariants.getViewPixelsPerStaffSpace()
        //            (= pxPerSs × factor) — the only reader of the zoomed scale inside a
        //            render pass.
        //
        //   Attribution below is drawn in pixel space OUTSIDE the transform, so it applies
        //   the factor explicitly (positions/sizes × factor, fonts via zoomedFont).
        //
        //   "EXACTLY ONCE" is per paint pass, not per process. LineOverlayComponent is a
        //   sibling component that paints the line's overlays into its OWN graphics, so it
        //   legitimately establishes a second, parallel scale-and-translate of the same
        //   factor — see LineOverlayComponent.paintComponent. It reads the zoomed scale from
        //   getViewPixelsPerStaffSpace() below rather than re-multiplying inline, and the rule
        //   still holds within each pass: no renderer applies the factor a second time.
        // ────────────────────────────────────────────────────────────────────────────
        var scale = getViewPixelsPerStaffSpace();

        // layoutResult is null only when the very first layout could not fit the
        // content (issue #449); with no prior layout to fall back on, skip drawing
        // the staff content rather than crash. Recovers on the next layout that fits.
        if (layoutResult != null) {
            try (var ignored = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
                g2.scale(scale, scale);
                lineRenderer.render(g2);
            }
        }

        // Render the attribution pane directly in pixel space (first line only).
        // The pane is not a Swing child; it is rendered here after the staff-space
        // transform has been restored.
        if (lineIndex == 0 && song != null && scoreView != null && layoutResult != null) {
            var attribution = song.getAttributionElement();
            var layout = layoutResult.getDecorationLayout(attribution);

            if (layout != null) {
                var view = scoreView;
                // Drawn in pixel space outside the ss transform: apply the view factor to the
                // (zoom-invariant) staff-space positions/size, and bake zoom into the fonts.
                var factor = getViewScale().factor();
                var xPx = ScaleContext.ssToPx(layout.xSs()) * factor;
                var yPx = ScaleContext.ssToPx(layout.ySs() + middleLineYSs) * factor;
                var widthPx = ScaleContext.ssToPx(layout.widthSs()) * factor;
                song.getAttributionPane().render(
                    g2, xPx, yPx, widthPx,
                    zoomedFont(view.getAttributionFont()),
                    zoomedFont(view.getSubAttributionFont()),
                    factor
                );
            }
        }

        // Drag rectangle is a pixel-space UI overlay — render after restoring the transform
        // so it is not affected by the staff-space scale.
        lineRenderer.renderDragRectangle(g2);
    }

    @Override
    public Dimension getPreferredSize() {
        if (song == null || line == null) {
            return new Dimension(0, 0);
        }

        if (needsLayout()) {
            performLayout();
        }

        if (layoutMissing()) {
            // Sizing does not consult the layout, but a null one here still means the
            // layout pass failed for a reason other than the fit failure of issue #449.
            // The throw also guards the @Nullable getScoreView() dereference below.
            throw unexpectedNullLayout();
        }

        // A line always spans the full document width, never just the extent of its last
        // element: LineRenderer draws the staff lines out to song.getLineWidthSs(), and
        // sizing the component any narrower clipped them there — an element-less line
        // collapsed to zero width and vanished entirely (issue #578).
        double heightSs;

        if (layoutResult != null) {
            // The painted height, so the bounds hold everything this line draws — the measured
            // lineHeightSs is the spacing input and can be shorter than the staff surround.
            heightSs = layoutResult.paintLineHeightSs(getScoreView().getLyricRenderMetrics());
        } else {
            // Issue #449: the line has never had a fitting layout. Fall back to the same
            // minimum extents StaffLinesLayout uses for a null LayoutResult.
            heightSs = LineSpacing.MIN_LINE_HEIGHT_SS;
        }

        return new Dimension(
            toViewPx(new Ss(song.getLineWidthSs())).ceilPx(),
            toViewPx(new Ss(heightSs)).ceilPx());
    }

    private double calculateMiddleLineYSs() {
        if (needsLayout()) {
            performLayout();
        }

        var result = layoutResult;

        if (result == null) {
            if (lineDoesNotFit) {
                // First layout could not fit the content (issue #449); fall back to the
                // minimum above-staff space so the staff still positions sensibly.
                return Staff.MIN_ABOVE_STAFF_SS + Staff.STAFF_HALF_SS;
            }

            throw unexpectedNullLayout();
        }

        // The painted extent: the staff must sit where StaffLinesLayout reserved room for it,
        // and that reservation is floored. Using the measured content here drew the staff above
        // the component's own top on any line whose ink stops at the staff, clipping it and
        // overlapping the line above.
        return result.paintAboveMidlineSs();
    }

    /**
     * Builds the error for the unexpected case where layout ran but produced no
     * result and the line is not in the known does-not-fit state (issue #449).
     */
    private RuntimeException unexpectedNullLayout() {
        return RuntimeError.exit("layout result is null after layout for line " + lineIndex);
    }

    // ==========================================================================
    // Insertion Note Delegation
    // ==========================================================================

    /**
     * Clears the insertion note from all lines.
     * Delegates to {@link PreviewElementManager}.
     */
    public static void clearPreviewElement() {
        PreviewElementManager.clearPreviewElement();
    }

    /**
     * Sets whether the Alt key is currently pressed and updates the cursor.
     * Delegates to {@link PreviewElementManager}.
     */
    public static void setAltPressed(boolean pressed) {
        PreviewElementManager.setAltPressed(pressed);
    }

    /**
     * Returns whether this line currently has the insertion note.
     */
    public boolean hasPreviewElement() {
        return PreviewElementManager.hasPreviewElement(this);
    }

    /**
     * Returns the current insertion X index.
     */
    public static int getCurrentXIndex() {
        return PreviewElementManager.getCurrentXIndex();
    }

    /**
     * Returns the current insertion Y position.
     */
    public static int getCurrentStaffPosition() {
        return PreviewElementManager.getCurrentStaffPosition();
    }

    // ==========================================================================
    // Mouse Event Handlers
    // ==========================================================================

    @Override
    public void mouseMoved(MouseEvent e) {
        if (getGraceModeManager().mouseMoved(this, e)) {
            return;
        }

        // Paste mode tracks the insertion point under the mouse and suppresses the
        // normal preview element. Returns true to consume the event.
        if (getPasteModeManager().mouseMoved(this, e)) {
            return;
        }

        // No preview anywhere in the clef/key signature column or over the lyrics, since
        // a click there does not insert an element. The header test is two comparisons,
        // so it runs before the lyric test, which loops over every element in the line.
        if (getScoreView().getMode() == Mode.EDIT
            && (selectionHandler.isWithinHeaderX(e.getPoint()) || hitTestLyric(e.getPoint()) != null)) {
            clearPreviewElement();
            return;
        }

        PreviewElementManager.trackMouse(this, e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (getGraceModeManager().mouseDragged(this, e)) {
            return;
        }

        if (noteDragHandler.isDragActive()) {
            noteDragHandler.handleDrag(e);
            return;
        }

        if (selectionHandler.isDragging() || selectionHandler.isSelectionActive(e)) {
            selectionHandler.handleDrag(e);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        // Grace mode handles its own click logic. Returns true to consume the event.
        if (getGraceModeManager().mouseClicked(this, e)) {
            return;
        }

        // Paste mode places the clipboard fragment at the clicked insertion point.
        // Returns true to consume the event.
        if (getPasteModeManager().mouseClicked(this, e)) {
            return;
        }

        var lyricHit = hitTestLyric(e.getPoint());

        if (lyricHit != null) {
            if (e.getClickCount() == DOUBLE_CLICK_COUNT
                && line != null
                && getScoreView().getActiveLyricEditor() == null) {
                var lyric = lyricHit.element().getLyricForVerse(lyricHit.verse());

                if (lyric != null && !lyric.text().isBlank()) {
                    LyricEditor.deselectAndOpenOn(
                        getScoreView(), line, line.getElementIndex(lyricHit.element()));
                }
            }

            return;
        }

        if (editLyricOnDoubleClickedElement(e)) {
            return;
        }

        if (selectionHandler.handleClick(e)) {
            return;
        }

        // Nothing is inserted anywhere in the clef/key signature column. mouseMoved
        // normally clears the preview there, but it cannot when the header widens under
        // a stationary mouse — a key signature change leaves a stale preview inside the
        // header with no mouse movement to re-clear it.
        if (selectionHandler.isWithinHeaderX(e.getPoint())) {
            return;
        }

        PreviewElementManager.handleClick(this);
    }

    /**
     * Opens the lyric editor on a double-clicked element, returning true when it did.
     * <p>
     * The gesture is a plain double-click in SELECT mode; in EDIT mode it needs
     * {@link #mousePressed} to have already switched permanently to SELECT mode by the time
     * the click arrives, which Alt does anywhere and a plain click does on the staff lines in
     * the clef/key signature column. {@code isSelectionActive} is the gate for exactly those
     * cases, and additionally rules out the adjustment modes and playback. The staff-line
     * route never reaches the editor, since no element sits in that column for the hit test
     * below to find. Shift is excluded separately: shift+click extends the selection, and this
     * method runs before the selection handler sees the click, so without the guard a
     * shift+double-click would discard the selection the user was building.
     */
    private boolean editLyricOnDoubleClickedElement(MouseEvent e) {
        var scoreView = getScoreView();

        if (e.getClickCount() != DOUBLE_CLICK_COUNT
            || e.isShiftDown()
            || line == null
            || scoreView.getActiveLyricEditor() != null
            || !selectionHandler.isSelectionActive(e)) {
            return false;
        }

        // The press already hit-tested this point for every element the drag handler
        // admits; only the cases it declines (a rest, say) still need the scan.
        var hitIndex = pressHitIndex >= 0
            ? pressHitIndex
            : ElementHitTest.hitTestElement(this, getViewScale().toDocumentPoint(e.getPoint()));

        if (hitIndex == -1) {
            return false;
        }

        var targetIndex = LyricTargetResolver.resolveLyricTarget(line, hitIndex);

        if (targetIndex < 0) {
            return false;
        }

        LyricEditor.deselectAndOpenOn(scoreView, line, targetIndex);
        return true;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        if (getGraceModeManager().mousePressed(this, e)) {
            return;
        }

        // In EDIT mode, alt+click anywhere and a plain click on the staff lines in the
        // clef/key signature area both switch to SELECT mode, then fall through to normal
        // handling so the rest of this method acts as if we had been in SELECT mode all
        // along.
        if (scoreView != null
            && scoreView.getMode() == Mode.EDIT
            && (e.isAltDown() || selectionHandler.isStaffLineHit(e.getPoint()))) {
            Actions.SELECT_MODE_ACTION.perform(this);
        }

        var lyricHit = hitTestLyric(e.getPoint());

        if (lyricHit != null) {
            getScoreView().getSelectionCoordinator().selectLyric(lyricHit.element(), lyricHit.verse());
            getScoreView().selectionChanged();
            repaint();
            return;
        }

        // In SELECT mode, note head press starts a pitch drag
        if (noteDragHandler.handlePress(e)) {
            return;
        }

        if (selectionHandler.isSelectionActive(e)) {
            selectionHandler.handlePress(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // Capture before handleRelease() clears it. Refreshed on every release, so
        // mouseClicked can never read a value left over from an earlier press.
        pressHitIndex = noteDragHandler.isDragActive() ? noteDragHandler.getDragElementIndex() : -1;

        if (getGraceModeManager().mouseReleased(this, e)) {
            return;
        }

        if (noteDragHandler.isDragActive()) {
            noteDragHandler.handleRelease();
            return;
        }

        selectionHandler.handleRelease();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        PreviewElementManager.mouseEnteredLine(this);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        getPasteModeManager().mouseExited(this);
        PreviewElementManager.mouseExitedLine(this);
    }

    // ==========================================================================
    // Package-Private Accessors for LineRenderer
    // ==========================================================================

    /**
     * Returns the ScoreView reference, which a live line component always has.
     */
    @Override
    public ScoreView getScoreView() {
        if (scoreView == null) {
            throw RuntimeError.exit("ScoreView reference not set on LineComponent");
        }

        return scoreView;
    }

    /**
     * Returns the lyric render metrics this line is laid out with. The view is populated with
     * them before any line is laid out or interacted with, so a live line component always has
     * them; insertion spacing uses them to measure the syllables the new element must clear.
     */
    public LyricRenderMetrics getLyricRenderMetrics() {
        return getScoreView().getLyricRenderMetrics();
    }

    private GraceModeManager getGraceModeManager() {
        return EditModeManager.getGraceModeManager();
    }

    private PasteModeManager getPasteModeManager() {
        return EditModeManager.getPasteModeManager();
    }

    /** Returns the preview element from edit mode, or null if unavailable. */
    @Nullable StaffElement getPreviewElement() {
        return EditModeManager.getPreviewElement();
    }

    /** Returns whether the preview element should be rendered as visible. */
    boolean isPreviewElementVisible() {
        return EditModeManager.isPreviewElementVisible();
    }

    /** Returns whether grace note insert mode is currently in progress. */
    boolean isGraceModeInProgress() {
        return EditModeManager.getGraceModeManager().isInProgress();
    }

    /**
     * Returns the locked insertion X for grace mode, in staff spaces.
     * Only valid when {@link #isGraceModeInProgress()} is true.
     */
    double getGraceModeLockedXSs() {
        return getGraceModeManager().getLockedInsertionXSs();
    }

    /** Returns whether the given element is pending grace-note cancellation. */
    boolean isPendingCancelElement(StaffElement element) {
        return GraceModeManager.isPendingCancel(element);
    }

    /** Returns whether the given element is a grace note pending a drag-right connect. */
    boolean isPendingConnectElement(StaffElement element) {
        return GraceModeManager.isPendingConnect(element);
    }

    /**
     * Returns the line-level {@link ElementFrame} for this paint, carrying the grace-mode
     * host-insertion preview shift when this is the active grace line. {@code LineRenderer}
     * threads the returned shift into every per-element frame it builds.
     */
    ElementFrame gracePreviewLineFrame() {
        var graceModeManager = EditModeManager.getGraceModeManager();

        if (graceModeManager.getGraceLineComponent() == this) {
            var preview = graceModeManager.getHostInsertionPreview();

            if (preview != null) {
                return ElementFrame.lineLevelWithPreviewShift(
                    graceModeManager.getHostInsertionIndex(),
                    preview.shiftForSubsequentElementsSs()
                );
            }
        }

        return ElementFrame.LINE_LEVEL;
    }

    /**
     * Returns the {@link LineInvariants} an overlay needs to run the real renderers against this
     * line, or null when this line has no layout yet.
     * <p>
     * A missing layout is expected rather than fatal here: an overlay can be asked for its bounds
     * while a line is in the issue-#449 {@code lineDoesNotFit} state.
     */
    @Nullable
    LineInvariants previewInvariants() {
        if (getLayoutResult() == null) {
            return null;
        }

        return lineRenderer.buildInvariants();
    }

    /**
     * Returns the selection provider.
     */
    @Nullable SelectionProvider getSelectionProvider() {
        return selectionProvider;
    }

    private LayoutResult.@Nullable LyricHit hitTestLyric(Point pointPx) {
        var ready = readyLayout();

        if (ready == null) {
            return null;
        }

        // Convert the view-pixel event point to document pixels once, at this input
        // boundary, so the layout hit-test operates in the zoom-independent document space.
        var pointDocPx = getViewScale().toDocumentPoint(pointPx);
        return ready.layoutResult().hitTestLyric(getScoreView().getLyricRenderMetrics(), ready.line(), pointDocPx);
    }

    /** Package-private for testing. */
    record ReadyLayout(Line line, LayoutResult layoutResult) {}

    /** Package-private for testing. */
    @Nullable ReadyLayout readyLayout() {
        if (line == null) {
            return null;
        }

        ensureLayout();

        if (layoutResult == null) {
            return null;
        }

        return new ReadyLayout(line, layoutResult);
    }

    /**
     * Returns whether a selection drag is in progress.
     */
    public boolean isDraggingSelection() {
        return selectionHandler.isDragging();
    }

    /**
     * Clears any active rubber-band drag rectangle on this line.
     * Called from ScoreView when a window-level mouseReleased catches an orphaned drag.
     */
    public void clearDragRectangle() {
        selectionHandler.handleRelease();
    }

    /**
     * Returns the note pitch-drag handler for this line.
     */
    NoteDragHandler getNoteDragHandler() {
        return noteDragHandler;
    }

    /**
     * Returns the selection handler for this line.
     */
    LineSelectionHandler getSelectionHandler() {
        return selectionHandler;
    }

    /**
     * Returns the current drag rectangle.
     */
    Rectangle getDragRectangle() {
        return selectionHandler.getDragRectangle();
    }
}
