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

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import javax.swing.SwingUtilities;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;
import songscribe.dom.Attribution;
import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.Ss;
import songscribe.dom.StaffElement;
import songscribe.engraving.Staff;
import songscribe.engraving.StaffPosition;
import songscribe.error.RuntimeError;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutEngine;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricRenderMetrics;
import songscribe.ui.Mode;
import songscribe.ui.OptionDialogs;
import songscribe.ui.action.Actions;
import songscribe.ui.component.ComponentNames;
import songscribe.ui.component.LyricEditor;
import songscribe.ui.component.LyricTargetResolver;
import songscribe.ui.component.MainFrame;
import songscribe.ui.component.ScoreView;
import songscribe.ui.dialog.AttachmentDialogController;
import songscribe.ui.dialog.KeyChangeDialogController;
import songscribe.ui.dialog.SongSettingsDialog;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.edit.InsertionPointMode;
import songscribe.ui.playback.PlaybackController;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.renderer.LineInvariants;
import songscribe.util.GraphicsState;
import songscribe.util.UIUtils;

// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)

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
    implements MouseMotionListener {

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
         * Returns whether the given hit target is the current selection.
         * <p>
         * This is the whole interface: every kind a click can address asks the same
         * question, in the same vocabulary, whether it is a note, a lyric syllable, an
         * articulation or the staff line itself.
         *
         * @param target    The thing a click would have selected
         * @param lineIndex The line index
         * @return true if that exact target is selected
         */
        boolean isSelected(HitTarget target, int lineIndex);

        /**
         * Returns whether the element at {@code elementIndex} falls inside the selected
         * index range.
         * <p>
         * The one question {@link #isSelected} cannot answer cheaply. An index range is not
         * something a click addresses — it is the other shape a selection can take — so it has
         * no {@link HitTarget} of its own. Every caller here is drawing an element and already
         * holds its index; asking through {@link #isSelected} would wrap that index into a
         * target only for the answer to derive it back by scanning the line, once per drawn
         * element on every repaint.
         *
         * @param elementIndex The element's index on the line
         * @param lineIndex    The line index
         * @return true if that element falls inside the selected range
         */
        boolean isElementRangeSelected(int elementIndex, int lineIndex);
    }

    // ==========================================================================
    // Instance Fields
    // ==========================================================================

    /** The line model containing staff elements. */
    @Nullable
    private Line line;

    /** Index of this line within the song. */
    private int lineIndex;

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

    /** Cached layout result from the last layout pass. */
    @Nullable
    private LayoutResult layoutResult;

    /** Whether layout needs to be recalculated. */
    private boolean layoutDirty = true;

    /**
     * True once the overflow alert has been shown for the current document, so it is shown once
     * and not once per overflowing line, per paint, or per episode. The red staff lines are the
     * standing indication from then on, and they clear themselves when the line fits again — so
     * there is nothing a second alert would tell the user (refs #696). Reset by
     * {@link #resetOverflowWarning} when a document is installed. EDT-only, so a plain static
     * boolean is safe.
     * <p>
     * One flag for every line in the process, which is correct only because the app holds one
     * document at a time: {@code MainFrame} is a singleton owning a single {@link ScoreView}, and
     * every path that installs a document goes through {@link ScoreView#setSong}, which resets this.
     * A second window, or a document-installing path that bypasses {@code setSong}, would have to
     * scope the flag per document — otherwise it either swallows a warning the user needs or fires a
     * stale one.
     */
    private static boolean overflowWarningShown = false;

    /** True when the previous line's lyric extender continues into this line. */
    private boolean hasLeadingLyricContinuation;

    /**
     * Element index hit by the most recent press, or -1 when that press hit no element.
     * {@link NoteDragHandler} computes this on press and discards it on release, so it is
     * captured in {@link #released} for {@link #clicked} — which runs next — to
     * reuse instead of scanning the line's elements a second time for the same point.
     */
    private int pressHitIndex = -1;

    /** Handles selection, hit-testing, and drag logic. */
    private final LineSelectionHandler selectionHandler = new LineSelectionHandler(this);

    /** Handles press/drag/release for pitch-dragging a note in NOTE_EDIT mode. */
    private final NoteDragHandler noteDragHandler = new NoteDragHandler(this);

    /** Renderer that handles all drawing for this line. */
    private final LineRenderer lineRenderer = new LineRenderer(this);

    /**
     * Creates a new LineComponent.
     */
    public LineComponent() {
        addMouseMotionListener(this);
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
        layoutDirty = true;
        layoutResult = null;
        middleLineYSsValid = false;

        // Register with coordinator if score is available. The selection itself is the
        // coordinator's and is untouched by this rebuild — all that is registered here is
        // which line this index now holds.
        if (scoreView != null) {
            scoreView.getSelectionCoordinator().registerLine(lineIndex, line);
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
     * Returns the Y coordinate of the top staff line in staff-space units.
     * <p>
     * The staff's own vertical extent, which anything drawn to straddle it — the selection band
     * above all — takes from here rather than re-deriving from the midline.
     */
    public Ss getStaffTopYSs() {
        return new Ss(getMiddleLineYSs() - Staff.STAFF_HALF_SS);
    }

    /**
     * Returns the Y coordinate of the bottom staff line in staff-space units.
     *
     * @see #getStaffTopYSs
     */
    public Ss getStaffBottomYSs() {
        return new Ss(getMiddleLineYSs() + Staff.STAFF_HALF_SS);
    }

    /**
     * Returns the Y coordinate of the middle staff line in pixels.
     * <p>
     * Bridge method for callers not yet converted to staff-space units.
     * Will be removed when renderers and mouse code are converted.
     */
    public int getMiddleLineYPx() {
        return (int) Math.round(DocumentScale.ssToPx(getMiddleLineYSs()).value() * getViewScale().factor());
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
        return DocumentScale.PIXELS_PER_STAFF_SPACE * getViewScale().factor();
    }

    /**
     * Converts a staff position to a Y pixel coordinate.
     *
     * @param staffPositionSp The staff position (0 = middle line)
     * @return Y coordinate in pixels
     */
    public int staffPositionToYPx(int staffPositionSp) {
        return getMiddleLineYPx()
            + (int) Math.round(
                DocumentScale.ssToPx(StaffPosition.toSs(staffPositionSp)).value() * getViewScale().factor());
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

        // Register the line with the coordinator when the score arrives after setLine.
        if (line != null) {
            scoreView.getSelectionCoordinator().registerLine(lineIndex, line);
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
     * Marks the layout as dirty, requiring recalculation on next render.
     */
    public void invalidateLayout() {
        layoutDirty = true;
        layoutResult = null;
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

    /** Returns true when this line has no layout result to read. */
    private boolean layoutMissing() {
        return layoutResult == null;
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

        var staffRightMarginSs = song.getLineWidthSs().value();
        var isLastLine = lineIndex == song.lineCount() - 1;
        var isFirstLine = lineIndex == 0;
        var view = scoreView;
        var lyricRenderMetrics = view.getLyricRenderMetrics();
        var layoutEngine = new LayoutEngine(lyricRenderMetrics, staffRightMarginSs, view);

        Attribution attribution = null;
        // The song's tempo is drawn at line 0's staff header, always — even on an empty line or
        // an empty song. Unlike the attribution it needs no pre-measurement: the stacker derives
        // its extent from Song.getTempo() itself.
        var tempoMark = isFirstLine ? song.getTempoMarkElement() : null;

        if (isFirstLine) {
            attribution = song.getAttributionElement();
            var pane = song.getAttributionPane();
            // Measure at natural (unzoomed) scale so the reserved staff-space dimensions
            // are zoom-invariant by construction — no measure-at-zoom-then-divide-back
            // round trip. Zoom is applied only when the block is painted (see render),
            // where the whole block scales uniformly by the view factor.
            var sizePx = pane.getContentSizePx(view.getAttributionFont(), view.getSubAttributionFont());
            attribution.setDimensionsSs(
                DocumentScale.pxToSs(sizePx.width),
                DocumentScale.pxToSs(sizePx.height));
        }

        var result = layoutEngine.layout(
            line, isLastLine, hasLeadingLyricContinuation, tempoMark, attribution);

        layoutResult = result;
        layoutDirty = false;

        if (result.overflowsStaffWidth()) {
            warnLineOverflows();
        }
    }

    /**
     * Clears the once-per-document guard on the overflow alert, so the next document that cannot
     * fit a line warns about it. Called when a document is installed.
     */
    public static void resetOverflowWarning() {
        overflowWarningShown = false;
    }

    /**
     * Warns, once per document, that content is being clipped.
     * <p>
     * Deferred to a later EDT cycle because layout runs during paint; showing a modal dialog
     * synchronously would re-enter painting. The guard is set before the dialog rather than after
     * it, so the several lines that typically overflow together produce one alert instead of one
     * each (refs #696).
     */
    private static void warnLineOverflows() {
        if (overflowWarningShown) {
            return;
        }

        overflowWarningShown = true;

        SwingUtilities.invokeLater(() -> OptionDialogs.showWarningMessage(
            null, Strings.ALERT_TITLE_LINES_DO_NOT_FIT, Strings.ALERT_LINES_DO_NOT_FIT));
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

        // Paint pipeline: the zoom factor is applied EXACTLY ONCE, by the g2.scale(pxPerSs ×
        // factor) below.
        //
        //   LineRenderer and all element renderers draw in Ss; they NEVER re-multiply by pxPerSs
        //   or factor (a ssToPx inside a renderer is a bug). The one exception is
        //   LyricTextRenderer, which strips this transform and re-derives its own integer-pixel
        //   coords from LineInvariants.getViewPixelsPerStaffSpace() (= pxPerSs × factor) — the
        //   only reader of the zoomed scale inside a render pass.
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
        var scale = getViewPixelsPerStaffSpace();

        // Layout always produces a result — an over-full line is placed on its collision floors
        // and drawn clipped, not abandoned (refs #696) — so a null here is a broken invariant,
        // not a line that would not fit.
        var result = layoutResult;

        if (result == null) {
            throw unexpectedNullLayout();
        }

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
            g2.scale(scale, scale);
            lineRenderer.render(g2);
        }

        // Render the attribution pane directly in pixel space (first line only).
        // The pane is not a Swing child; it is rendered here after the staff-space
        // transform has been restored.
        if (lineIndex == 0 && song != null && scoreView != null) {
            var attribution = song.getAttributionElement();
            var layout = result.getDecorationLayout(attribution);

            if (layout != null) {
                var view = scoreView;
                // Drawn in pixel space outside the ss transform: apply the view factor to the
                // (zoom-invariant) staff-space positions/size, and bake zoom into the fonts.
                var factor = getViewScale().factor();
                var xPx = DocumentScale.ssToPx(layout.xSs()).value() * factor;
                var yPx = DocumentScale.ssToPx(layout.ySs() + middleLineYSs).value() * factor;
                var widthPx = DocumentScale.ssToPx(layout.widthSs()).value() * factor;
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
        lineRenderer.renderSelectionBand(g2);
    }

    @Override
    public Dimension getPreferredSize() {
        if (song == null || line == null) {
            return new Dimension(0, 0);
        }

        if (needsLayout()) {
            performLayout();
        }

        var result = layoutResult;

        if (result == null) {
            // Layout always produces a result, so a null one means the pass never ran — it
            // returns early without a score view. The throw also guards the @Nullable
            // getScoreView() dereference below.
            throw unexpectedNullLayout();
        }

        // A line always spans the full document width, never just the extent of its last
        // element: LineRenderer draws the staff lines out to song.getLineWidthSs(), and
        // sizing the component any narrower clipped them there — an element-less line
        // collapsed to zero width and vanished entirely (issue #578).
        //
        // The width is the staff width even for a line whose content overflows it: the overflow
        // is meant to be clipped at the end of the staff, and these bounds are what clip it.
        //
        // The painted height, so the bounds hold everything this line draws — the measured
        // lineHeightSs is the spacing input and can be shorter than the staff surround.
        var heightSs = result.paintLineHeightSs(getScoreView().getLyricRenderMetrics());

        return new Dimension(
            toViewPx(song.getLineWidthSs()).sizePx(),
            toViewPx(heightSs).sizePx());
    }

    private double calculateMiddleLineYSs() {
        if (needsLayout()) {
            performLayout();
        }

        var result = layoutResult;

        if (result == null) {
            throw unexpectedNullLayout();
        }

        // The painted extent: the staff must sit where StaffLinesLayout reserved room for it,
        // and that reservation is floored. Using the measured content here drew the staff above
        // the component's own top on any line whose ink stops at the staff, clipping it and
        // overlapping the line above.
        return result.paintAboveMidlineSs();
    }

    /**
     * Builds the error for the unexpected case where the layout pass produced no result. Layout
     * itself cannot fail — an over-full line is placed and drawn clipped (refs #696) — so this
     * means the pass never ran, which it skips only without a score view.
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

        // A pending placement tracks the insertion point under the mouse and suppresses
        // the normal preview element. Returns true to consume the event.
        if (getInsertionPointMode().mouseMoved(this, e)) {
            return;
        }

        // No preview anywhere in the clef/key signature column or over the lyrics, since a click
        // there selects rather than inserting an element. The header test is two comparisons, so it
        // runs before the lyric test, which walks the line's lyric boxes. This fires on every pixel
        // of pointer motion, so it asks only about lyrics rather than running the whole cascade.
        //
        // This is what makes a lyric outrank the preview rather than the other way round. The lyric
        // row is not out of the preview's reach: the row sits just below the line's laid-out content,
        // so on a line whose content stays high, a preview for a low note would be drawn right on top
        // of the lyric text. Clearing it here means the staff positions a lyric box covers cannot be
        // clicked to insert a note — the accepted price of leaving lyric text clickable wherever it
        // is drawn, and the reason a lyric is the one kind selectable in EDIT mode at all (see
        // handleEditModePress).
        if (getScoreView().getMode() == Mode.EDIT
            && (selectionHandler.isWithinHeaderX(e.getPoint())
                || selectionHandler.hitTestLyricViewPoint(e.getPoint()) != null)) {
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

        // While a placement is pending, a drag must not rubber-band a new selection. The press
        // that would start one is already suppressed (see pressed), so a drag here would
        // band from a stale press point, and any selection it made would be replaced by the
        // placement that follows anyway.
        if (getInsertionPointMode().isInProgress()) {
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
    protected void clicked(MouseEvent e, ScoreView view) {
        // Grace mode handles its own click logic. Returns true to consume the event.
        if (getGraceModeManager().mouseClicked(this, e)) {
            return;
        }

        // A pending placement lands at the clicked insertion point.
        // Returns true to consume the event.
        if (getInsertionPointMode().mouseClicked(this, e)) {
            return;
        }

        // A click does nothing at all while the song is playing, mirroring the guard in
        // pressed: everything below opens an editor, opens a modal dialog or inserts an
        // element, and DISABLE_WHEN_PLAYING does not reach a mouse handler.
        //
        // Read from PlaybackController rather than the sequencer, for the reason pressed
        // gives. The only playback check below is the selection handler's, which asks whether
        // the sequencer object is currently running — a different question during the window
        // between a playback transition and the sequencer following it, and one that now gates
        // a modal dialog rather than just a selection change.
        //
        // This guard is the one that protects the lyric branch, handleClick, isWithinHeaderX
        // and PreviewElementManager.handleClick — none of which go near the double-click
        // gesture. isSelectionActive's own playback test, which gates the three notation
        // gestures below, asks the sequencer a different question, as that method's Javadoc
        // explains; the two reads look like duplicates, but this is the load-bearing one.
        if (PlaybackController.isPlaying()) {
            return;
        }

        // The same lyric test the press path's cascade runs first, so both agree on which
        // syllable is under the pointer. Only the lyric answer matters here, so the rest of the
        // cascade is skipped.
        var lyricHit = selectionHandler.hitTestLyricViewPoint(e.getPoint());

        if (lyricHit != null) {
            // Consumed either way: the press already selected the lyric, and nothing below this
            // should insert an element or re-resolve the click to a note.
            if (UIUtils.isLeftDoubleClick(e)
                && line != null
                && view.getActiveLyricEditor() == null) {
                var element = lyricHit.element();
                var lyric = element.getLyricForVerse(lyricHit.verse());

                if (lyric != null && !lyric.text().isBlank()) {
                    LyricEditor.deselectAndOpenOn(view, line, line.getElementIndex(element));
                }
            }

            return;
        }

        // All four double-click-to-edit gestures ask about the same point, so it is resolved
        // once here and shared, as hitTestViewPoint's own contract asks and as pressed
        // already does with its cascade result. Pinning line to a local here is what lets the
        // three that act on notation take it as a plain non-null argument.
        //
        // No modifier gates this: a double-click always means "edit this," regardless of
        // which keys happen to be down, so shift+double-click on a selectable element opens
        // its editor exactly like a plain double-click would.
        //
        // isSelectionActive gates only the three that act on notation. It requires SELECT mode —
        // a plain double-click in SELECT mode, or one in EDIT mode only once pressed has
        // already switched permanently to SELECT, which Alt does anywhere and a plain click does
        // on the staff lines in the clef/key signature column — and it additionally rules out
        // playback. The attribution runs outside it on purpose; see its own method.
        var clickedLine = line;

        if (UIUtils.isLeftDoubleClick(e) && clickedLine != null) {
            var clickHit = selectionHandler.hitTestViewPoint(e.getPoint());

            if (editDoubleClickedAttribution(clickHit)) {
                return;
            }

            if (selectionHandler.isSelectionActive(e)) {
                if (!e.isShiftDown() && editLyricOnDoubleClickedElement(clickHit, clickedLine)) {
                    return;
                }

                if (editDoubleClickedAttachment(clickHit, clickedLine)) {
                    return;
                }

                if (editDoubleClickedKeyChange(e, clickedLine)) {
                    return;
                }
            }
        }

        if (selectionHandler.handleClick(e)) {
            return;
        }

        // Nothing is inserted anywhere in the clef/key signature column. mouseMoved
        // normally clears the preview there, but it cannot when the header widens under
        // a stationary mouse — a key change leaves a stale preview inside the
        // header with no mouse movement to re-clear it.
        if (selectionHandler.isWithinHeaderX(e.getPoint())) {
            return;
        }

        PreviewElementManager.handleClick(this);
    }

    /**
     * The index in {@code line} of the element {@code clickHit} resolved to, or -1 when the
     * click landed on anything else.
     * <p>
     * Package-private because every handler in this package that turns a resolved hit into an
     * element it can act on asks this same question — the double-click lyric editor here, the
     * pitch drag in {@link NoteDragHandler}, and Shift+click extension in
     * {@link LineSelectionHandler}. Answering it in one place is what keeps a future change to
     * what counts as a hit element — a new {@link HitTarget} kind, say — from having to be
     * remembered three times.
     */
    static int elementIndexOf(@Nullable HitTarget clickHit, Line line) {
        if (clickHit instanceof HitTarget.Element(var element)) {
            return line.getElementIndex(element);
        }

        return -1;
    }

    /**
     * Opens the lyric editor on the double-clicked element {@code clickHit} resolved to,
     * returning true when it did.
     * <p>
     * The staff-line route that makes selection active never reaches the editor, since no
     * element sits in the clef/key signature column for {@code clickHit} to resolve to.
     */
    private boolean editLyricOnDoubleClickedElement(@Nullable HitTarget clickHit, Line line) {
        var view = getScoreView();

        if (view.getActiveLyricEditor() != null) {
            return false;
        }

        // The press already hit-tested this point for every element the drag handler
        // admits; only the cases it declines (a rest, say) still need the resolved hit.
        var hitIndex = pressHitIndex >= 0 ? pressHitIndex : elementIndexOf(clickHit, line);

        if (hitIndex == -1) {
            return false;
        }

        var targetIndex = LyricTargetResolver.resolveLyricTarget(line, hitIndex);

        if (targetIndex < 0) {
            return false;
        }

        LyricEditor.deselectAndOpenOn(view, line, targetIndex);
        return true;
    }

    /**
     * Opens Song Settings on its Attribution tab when the double-click landed on the attribution
     * block, returning true when it opened.
     * <p>
     * This runs outside the {@code isSelectionActive} test the three notation gestures sit
     * behind, on purpose, so the gesture works in EDIT mode as well as SELECT. That matches the
     * title and the subtitle, which reach the same dialog from any mode through
     * {@link ScoreComponent#openEditor}.
     * <p>
     * The preview element takes precedence: the attribution's bottom edge can reach down into
     * the insertable pitch range, and claiming the click there would make those staff positions
     * unreachable in EDIT mode. Within that band the click belongs to the preview and the block
     * is not double-clickable; above it the preview is already cleared and the dialog opens.
     * <p>
     * Answering false is safe. When the hit was not the attribution nothing has been claimed,
     * and when the preview owns the click, letting it fall through to
     * {@link PreviewElementManager#handleClick} is exactly the intended outcome. This probe
     * cannot lean on the reason {@link #editDoubleClickedAttachment} gives — it runs outside
     * {@code isSelectionActive}, so selection need not be active and {@code handleClick} need
     * not consume the click.
     */
    private boolean editDoubleClickedAttribution(@Nullable HitTarget clickHit) {
        if (!(clickHit instanceof HitTarget.Attribution)) {
            return false;
        }

        if (PreviewElementManager.isPreviewClickTarget(this)) {
            return false;
        }

        Actions.SONG_SETTINGS_ACTION.openAt(SongSettingsDialog.Section.ATTRIBUTION);
        return true;
    }

    /**
     * Opens the edit dialog for the double-clicked attachment {@code clickHit} resolved to,
     * returning true when one opened.
     * <p>
     * The press already selected the attachment, so this is only the second half of the
     * gesture. Unlike {@link #editLyricOnDoubleClickedElement} there is no active-editor
     * condition: a lyric editor open elsewhere on the line must not block editing an
     * attachment. A fermata or a dynamic has no dialog, so {@link AttachmentDialogController#edit}
     * answers false for those and the attachment is simply left selected.
     * <p>
     * Answering false is safe even when the click really did land on an attachment.
     * {@link #clicked} hands the click to {@code handleClick} next, which consumes every
     * click while selection is active — and the {@code isSelectionActive} test this runs
     * inside, already true to have got here, says it is. So nothing is inserted at the click
     * point either way.
     */
    private boolean editDoubleClickedAttachment(@Nullable HitTarget clickHit, Line line) {
        if (!(clickHit instanceof HitTarget.Attachment(var attachment))) {
            return false;
        }

        return AttachmentDialogController.edit(MainFrame.getInstance(), attachment, line);
    }

    /**
     * Opens the key change dialog for whichever of the three key edit targets the double-click
     * landed on, returning true when one opened.
     * <p>
     * The targets are the line's header, a key change standing in the middle of the line, and
     * the cautionary drawn at the line's end — which edits the <em>next</em> line's key, not this
     * line's, because that is the change it renders. Each is asked of {@link LayoutResult}, so the
     * rects tested are the ones drawn, including the cautionary's overflow placement.
     * <p>
     * The three occupy disjoint horizontal runs — the header precedes every column, a mid-line
     * key change owns a solved column, and the cautionary sits past the last one — so the order
     * they are tried in cannot change which target a point resolves to. It is cheapest-first.
     * <p>
     * Answering false is safe for the same reason {@link #editDoubleClickedAttachment} gives:
     * {@code handleClick} consumes the click next, so nothing is inserted at the click point.
     */
    private boolean editDoubleClickedKeyChange(MouseEvent e, Line line) {
        var ready = readyLayout();

        if (ready == null) {
            return false;
        }

        var layoutResult = ready.layoutResult();
        var mouseXSs = selectionHandler.layoutXSs(e.getPoint());
        var mainFrame = MainFrame.getInstance();
        var headerTarget = layoutResult.hitTestHeaderKeyEdit(mouseXSs, line);

        if (headerTarget != null) {
            KeyChangeDialogController.editLineKey(mainFrame, headerTarget);
            return true;
        }

        var midLineKeyChange = layoutResult.hitTestMidLineKeyEdit(mouseXSs, line);

        if (midLineKeyChange != null) {
            KeyChangeDialogController.editKeyChange(mainFrame, line, midLineKeyChange);
            return true;
        }

        var cautionaryTarget = layoutResult.hitTestCautionaryKeyEdit(mouseXSs, line);

        if (cautionaryTarget != null) {
            KeyChangeDialogController.editLineKey(mainFrame, cautionaryTarget);
            return true;
        }

        return false;
    }

    @Override
    protected void pressed(MouseEvent e, ScoreView view) {
        if (getGraceModeManager().mousePressed(this, e)) {
            return;
        }

        // While a placement is pending, a press must not change the selection — it must be
        // left for the click that follows to resolve as a placement or a cancel. Without
        // this guard, a press on the staff lines in the header flips EDIT to SELECT and
        // selects the whole line, leaving the placement stuck pending with nothing placed.
        // Replacing a line means selecting it before Cmd+V (see #612's Replace).
        if (getInsertionPointMode().isInProgress()) {
            return;
        }

        // A press does nothing at all while the song is playing.
        //
        // This is a guard, not only an optimization: the mode switch below was reached during
        // playback and really did switch modes. Invoking an action programmatically does not
        // consult its enabled state, so DISABLE_WHEN_PLAYING on the mode actions — which stops the
        // menu item, the keyboard shortcut and the toolbar button — did not stop this call. A
        // staff-line press in the header, or any alt+click, therefore flipped EDIT to SELECT
        // mid-playback. Nothing was selected, because isSelectionActive refuses while playing, so
        // the only symptom was the mode changing on its own.
        //
        // Everything else below already refused: the EDIT-mode selection path, the pitch-drag
        // handler and the selection handler each check playback themselves. Returning here also
        // keeps the cascade — which walks every element in the line — from running for an answer
        // none of them will read.
        //
        // Read from PlaybackController rather than the sequencer, since this stands in for
        // DISABLE_WHEN_PLAYING and that flag resolves against this same state. The grace-mode and
        // paste-mode guards stay above it so their behavior is untouched.
        if (PlaybackController.isPlaying()) {
            return;
        }

        // In EDIT mode, alt+click anywhere and a plain click on the staff lines in the clef/key
        // signature area both switch to SELECT mode, then fall through to normal handling so the
        // rest of this method acts as if we had been in SELECT mode all along.
        //
        // The cascade walks every element in the line, so it runs exactly once per press: this one
        // result feeds the mode switch, the EDIT-mode selection path, the pitch-drag handler and
        // the press dispatch, all below. This is the only handler that needs the whole cascade —
        // mouseMoved and clicked ask about lyrics alone.
        var pressHit = selectionHandler.hitTestViewPoint(e.getPoint());

        if (view.getMode() == Mode.EDIT
            && (e.isAltDown() || pressHit instanceof HitTarget.StaffLine)) {
            Actions.SELECT_MODE_ACTION.perform(this);
        }

        // A lyric is selectable in EDIT mode too, in place and without switching modes; nothing else
        // is. The mode is re-checked because an alt+click or a staff-line hit above may have just
        // switched us to SELECT, where the selection handler below handles lyrics along with
        // everything else.
        if (view.getMode() == Mode.EDIT
            && selectionHandler.handleEditModePress(pressHit)) {
            return;
        }

        // In SELECT mode, a note head press starts a pitch drag. The drag handler reads this same
        // cascade result rather than hit-testing again, so a lyric that outranked an element
        // rectangle reaching into the lyric row can never be mistaken for a note to drag.
        if (noteDragHandler.handlePress(e, pressHit)) {
            return;
        }

        if (selectionHandler.isSelectionActive(e)) {
            selectionHandler.handlePress(e, pressHit);
        }
    }

    @Override
    protected void released(MouseEvent e, ScoreView view) {
        // Capture before handleRelease() clears it. Refreshed on every release, so
        // clicked can never read a value left over from an earlier press.
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
    protected void entered(MouseEvent e, ScoreView view) {
        PreviewElementManager.mouseEnteredLine(this);
    }

    @Override
    protected void exited(MouseEvent e, ScoreView view) {
        getInsertionPointMode().mouseExited(this);
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

    /**
     * Returns the lyric render metrics this line is laid out with, or null when the view has not
     * been populated with them yet.
     * <p>
     * Unlike {@link #getLyricRenderMetrics()}, which exits the application when they are missing,
     * this reports their absence. Hit testing uses it so that a hit test somehow reaching a line
     * before the first layout declines to find a lyric rather than taking the app down.
     */
    @Nullable LyricRenderMetrics findLyricRenderMetrics() {
        return getScoreView().findLyricRenderMetrics();
    }

    private GraceModeManager getGraceModeManager() {
        return EditModeManager.getGraceModeManager();
    }

    private InsertionPointMode getInsertionPointMode() {
        return EditModeManager.getInsertionPointMode();
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
     * before this line's layout pass has run, which it skips while it has no score view.
     */
    @Nullable
    LineInvariants previewInvariants() {
        if (layoutResult == null) {
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

    record ReadyLayout(Line line, LayoutResult layoutResult) {}

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
     * Ends any live selection band on this line.
     * Called from ScoreView when a window-level mouseReleased catches an orphaned drag.
     */
    public void clearSelectionBand() {
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
     * Returns the live selection band's horizontal extent, or {@code null} when no drag is in
     * progress.
     */
    LineSelectionHandler.@Nullable SelectionBand getSelectionBand() {
        return selectionHandler.getSelectionBand();
    }
}
