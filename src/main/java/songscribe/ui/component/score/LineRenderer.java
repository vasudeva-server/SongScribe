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


import songscribe.error.RuntimeError;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.hit.HitTarget;
import songscribe.layout.LayoutResult;
import songscribe.engraving.LineThickness;
import songscribe.layout.NoteGeometry;
import songscribe.ui.FlatLafKey;
import songscribe.ui.FlatLafProps;
import songscribe.ui.component.ScoreView;
import songscribe.ui.edit.GraceModeManager;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.StaffElement;
import songscribe.dom.Beam;
import songscribe.dom.BeatChangeAttachment;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.FermataAttachment;
import songscribe.dom.TempoChangeAttachment;
import songscribe.ui.renderer.AnnotationRenderer;
import songscribe.ui.renderer.ArticulationRenderer;
import songscribe.ui.renderer.RenderingUtils;
import songscribe.ui.renderer.BeamGroupRenderer;
import songscribe.ui.renderer.BeatChangeRenderer;
import songscribe.ui.renderer.ClefRenderer;
import songscribe.ui.renderer.DynamicMarkingRenderer;
import songscribe.ui.renderer.HairpinRenderer;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.renderer.EndingRenderer;
import songscribe.ui.renderer.FermataRenderer;
import songscribe.ui.renderer.SlideRenderer;
import songscribe.util.GraphicsState;
import songscribe.ui.renderer.KeySignatureRenderer;
import songscribe.ui.renderer.LineInvariants;
import songscribe.ui.renderer.LyricConnectorRenderer;
import songscribe.ui.renderer.LyricTextRenderer;
import songscribe.ui.renderer.NoteRenderer;
import songscribe.ui.renderer.TempoChangeRenderer;
import songscribe.ui.renderer.TieRenderer;
import songscribe.ui.renderer.TrillRenderer;
import songscribe.ui.renderer.TupletRenderer;
import songscribe.util.GraphicUtils;

/**
 * Handles all rendering for a single staff line.
 * <p>
 * Extracted from {@link LineComponent} to separate rendering concerns.
 * Reads state from the owning LineComponent but does not mutate it
 * (except for positioning the insertion note preview before drawing).
 */
class LineRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Width of the selection rectangle border stroke at 100% zoom, in pixels. */
    private static final float SELECTION_RECT_STROKE_WIDTH_PX = 2.0f;

    /** Corner arc diameter for the rubber-band selection rectangle at 100% zoom, in pixels. */
    private static final double SELECTION_RECT_ARC_PX = 2.0;

    // ==========================================================================
    // Instance Fields
    // ==========================================================================

    /** The owning LineComponent whose state we read for rendering. */
    private final LineComponent lc;

    // ==========================================================================
    // Constructor
    // ==========================================================================

    /**
     * Creates a new LineRenderer for the given LineComponent.
     *
     * @param lineComponent The LineComponent to render for
     */
    LineRenderer(LineComponent lineComponent) {
        lc = lineComponent;
    }

    // ==========================================================================
    // Public Entry Points
    // ==========================================================================

    /**
     * Renders the complete staff line content.
     * <p>
     * Called from {@link LineComponent#render(Graphics2D)} after layout
     * has been performed and state is up to date.
     *
     * @param g2 Graphics context with antialiasing enabled
     */
    void render(Graphics2D g2) {
        // Build the immutable per-line invariants once.
        var invariants = buildInvariants();

        // The line-level frame carries the grace-note insert preview shift (rightward
        // displacement of subsequent elements) when this is the active grace line; the
        // per-element frames built below inherit that shift.
        var lineFrame = lc.gracePreviewLineFrame();

        // Ensure accidental metrics are initialized
        NoteGeometry.initializeAccidentalWidths();

        // Render in proper order (back to front)
        drawStaffLines(g2, invariants);
        renderLineBeginning(g2, invariants, lineFrame);
        renderElements(g2, invariants, lineFrame);
        renderSlides(g2, invariants, lineFrame);
        renderBeams(g2, invariants, lineFrame);
        renderTies(g2, invariants, lineFrame);
        renderTuplets(g2, invariants, lineFrame);
        renderKeyChanges(g2, invariants);
        renderDynamics(g2, invariants);
        renderEndings(g2, invariants);
        renderAttachments(g2, invariants, lineFrame);
    }

    /**
     * Builds the immutable per-line invariants from the owning line component's current state.
     *
     * @return The invariants for this render pass
     */
    LineInvariants buildInvariants() {
        var song = lc.getSong();
        var line = lc.getLine();
        var lineIndex = lc.getLineIndex();

        var layoutResult = lc.getLayoutResult();

        if (layoutResult == null) {
            throw RuntimeError.exit("LineRenderer.render called before layout was performed");
        }

        var score = lc.getScoreView();
        var activeEditor = score.getActiveLyricEditor();

        // The edited element and verse are only meaningful as a pair, so derive both from one
        // check rather than letting two conditions drift apart.
        StaffElement editedElement = null;
        var editedVerse = LineInvariants.NO_VERSE;

        if (activeEditor != null) {
            editedElement = activeEditor.getActiveElement();
            editedVerse = activeEditor.getActiveVerse();
        }

        return LineInvariants.builder(song, score)
            .setCurrentLine(line)
            .setLineIndex(lineIndex)
            .setMiddleLineYSs(lc.getMiddleLineYSs())
            .setLayoutResult(layoutResult)
            .setLyricRenderMetrics(score.getLyricRenderMetrics())
            .setActivelyEditedElement(editedElement)
            .setActivelyEditedVerse(editedVerse)
            .setSelectionProvider(lc.getSelectionProvider())
            .setPlayingNoteIndex(lc.getPlayingNoteIndex())
            .setPlayingGraceNoteIndex(lc.getPlayingGraceNoteIndex())
            .setViewScale(score.getViewScale())
            .build();
    }

    // ==========================================================================
    // Staff and Line Beginning
    // ==========================================================================

    /**
     * Draws the 5 staff lines. Package-private for testing.
     */
    void drawStaffLines(Graphics2D g2, LineInvariants invariants) {
        var selectionProvider = invariants.getSelectionProvider();
        var lineIndex = invariants.getLineIndex();
        var staffSelected = selectionProvider != null
            && selectionProvider.isSelected(new HitTarget.StaffLine(), lineIndex);
        var layoutResult = invariants.getLayoutResult();

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            // Red says this line holds more than the staff can show, so the tail of its content is
            // clipped (refs #696). Selection still wins: it is the transient state the user is
            // acting on, and the red returns the moment the line is deselected.
            if (staffSelected) {
                g2.setColor(ScoreView.getSelectionColor());
            } else if (layoutResult.overflowsStaffWidth()) {
                g2.setColor(FlatLafProps.getColor(FlatLafKey.SCORE_STAFF_LINE_OVERFLOW_COLOR));
            } else {
                g2.setColor(RenderingUtils.STAFF_LINE_COLOR);
            }

            var lineWidth = invariants.getSong().getLineWidthSs();
            var middleLineYSs = invariants.getMiddleLineYSs();
            var staffLineThicknessSs = LineThickness.STAFF_LINE_SS;

            // Staff has 5 lines, middle line (B) is at index 2.
            // Lines are at: middleLineYSs - 2, middleLineYSs - 1, middleLineYSs,
            //               middleLineYSs + 1, middleLineYSs + 2
            for (var i = -2; i <= 2; i++) {
                var centerY = middleLineYSs + i;
                GraphicUtils.drawRoundedLine(g2, 0, centerY, lineWidth, centerY, staffLineThicknessSs);
            }
        }
    }

    /**
     * Renders the line beginning (clef and key signature).
     *
     * @param g2    Graphics context
     * @param invariants   Line invariants
     * @param frame Element frame
     */
    private void renderLineBeginning(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        var layoutResult = invariants.getLayoutResult();
        var clef = layoutResult.getClef();

        if (clef != null) {
            ClefRenderer.getInstance().render(invariants, frame, clef, g2);
        }

        var keySig = layoutResult.getKeySignature();

        if (keySig != null) {
            KeySignatureRenderer.getInstance().render(invariants, frame, keySig, g2);
        }
    }

    // ==========================================================================
    // Note Rendering
    // ==========================================================================

    /**
     * Renders notes using NoteRenderer.
     *
     * @param g2        Graphics context
     * @param invariants       Line invariants
     */
    private void renderElements(Graphics2D g2, LineInvariants invariants, ElementFrame lineFrame) {
        var noteRenderer = NoteRenderer.getInstance();
        var line = invariants.requireCurrentLine();
        var layoutResult = invariants.getLayoutResult();

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            for (var i = 0; i < line.elementCount(); i++) {
                var element = line.getElement(i);

                // Apply color based on selection/playing state
                var color = getElementColor(i, invariants);
                g2.setColor(color);

                var overrideXSs = computeOverrideXSs(lineFrame, i, element, layoutResult);

                var frame = lineFrame.withElement(i, overrideXSs);
                noteRenderer.render(invariants, frame, element, g2);
            }
        }
    }

    /**
     * Computes the X-position override (in staff-space) for an element when a preview
     * shift is active and the element falls at or after the shift boundary.
     * <p>
     * Returns the element's natural layout X plus the preview shift amount, or
     * {@link Double#NaN} if no override applies (no shift, element before boundary,
     * or element is a final double barline which is never shifted).
     * Package-private for testing.
     *
     * @param lineFrame    Line-level frame carrying any active preview shift
     * @param elementIndex Index of the element within the line
     * @param element      The element being rendered
     * @param layoutResult The current layout result
     * @return Override X in staff-space, or {@link Double#NaN}
     */
    static double computeOverrideXSs(
        ElementFrame lineFrame,
        int elementIndex,
        StaffElement element,
        LayoutResult layoutResult
    ) {
        return lineFrame.hasPreviewShift()
            && elementIndex >= lineFrame.previewShiftFromIndex()
            && element.getType() != ElementType.FINAL_DOUBLE_BARLINE
            ? layoutResult.getElementXSs(element) + lineFrame.previewShiftSs()
            : Double.NaN;
    }

    /**
     * Determines the color for rendering an element.
     * <p>
     * Delegates edit mode, playback, selection, and hover logic to
     * {@link LineInvariants#getElementColor}. Adds the slide-preview highlight and
     * grace-cancel coloring on top. Package-private for testing.
     *
     * @param elementIndex The index of the element within this line
     * @param invariants          The per-line invariants
     * @return The color to use for rendering
     */
    Color getElementColor(int elementIndex, LineInvariants invariants) {
        var color = invariants.getElementColor(elementIndex);

        if (!LineInvariants.isDefaultColor(color)) {
            return color;
        }

        // The slide-preview highlight marks only the note glyph (notehead, stem, flag,
        // accidentals, dots) of the notes a previewed slide would connect to. It is applied
        // here, on the note's own color, so it does not leak onto the note's decorations,
        // attachments, ties, or lyrics, which each resolve their color independently.
        if (invariants.isSlidePreviewNote(elementIndex)) {
            return ScoreView.getPreviewElementColor();
        }

        var line = lc.getLine();

        if (line != null && lc.isPendingCancelElement(line.getElement(elementIndex))) {
            return Color.RED;
        }

        return Color.BLACK;
    }

    // ==========================================================================
    // Range Element Rendering
    // ==========================================================================

    /**
     * Renders slides for all notes in the line.
     *
     * @param g2    Graphics context
     * @param invariants   Line invariants
     * @param frame Element frame
     */
    private void renderSlides(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        var line = invariants.requireCurrentLine();
        SlideRenderer.getInstance().renderSlidesFromLine(g2, line, invariants, frame);
        renderPendingConnectGlissando(g2, line, invariants);
    }

    /**
     * Renders the grace-mode drag-right connect feedback as a preview glissando. The pending
     * connection is render-only state — mutating the grace note's slide during the drag would
     * leak an untracked change into undo's before-state clones.
     */
    private void renderPendingConnectGlissando(Graphics2D g2, Line line, LineInvariants invariants) {
        // Common case: no grace-mode drag is active anywhere — skip the per-element scan.
        if (!GraceModeManager.hasPendingConnect()) {
            return;
        }

        for (var i = 0; i < line.effectiveElementCount(); i++) {
            if (!lc.isPendingConnectElement(line.getElement(i))) {
                continue;
            }

            try (var _ = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
                g2.setColor(ScoreView.getPreviewElementColor());
                SlideRenderer.getInstance().renderPreviewGlissando(g2, i, line, invariants);
            }

            return;
        }
    }

    /**
     * Renders beam groups connecting beamed notes.
     *
     * @param g2    Graphics context
     * @param invariants   Line invariants
     * @param frame Element frame
     */
    private void renderBeams(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        var beamRenderer = BeamGroupRenderer.getInstance();
        var line = invariants.requireCurrentLine();

        for (var beam : line.findSpans(Beam.class)) {
            var anchorIdx = beam.getAnchorElementIndex();
            var endIdx = beam.getEndElementIndex();
            renderWithPreviewShiftIfNeeded(g2, frame, anchorIdx,
                () -> beamRenderer.renderBeams(g2, line, invariants, frame, anchorIdx, endIdx));
        }
    }

    /**
     * Renders ties between notes.
     *
     * @param g2    Graphics context
     * @param invariants   Line invariants
     * @param frame Element frame
     */
    private void renderTies(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        var tieRenderer = TieRenderer.getInstance();
        var line = invariants.requireCurrentLine();
        var ties = line.findTies();

        for (var span : ties) {
            renderWithPreviewShiftIfNeeded(g2, frame, span.getAnchorElementIndex(),
                () -> tieRenderer.renderTie(g2, span, invariants, frame));
        }
    }

    /**
     * Runs {@code render} with the frame's preview shift translated into {@code g2} when
     * {@code spanStart} falls at or after the shift boundary. The transform is restored
     * on exit (including on exception). Package-private for testing.
     */
    static void renderWithPreviewShiftIfNeeded(
        Graphics2D g2,
        ElementFrame frame,
        int spanStart,
        Runnable render
    ) {
        if (frame.hasPreviewShift() && spanStart >= frame.previewShiftFromIndex()) {
            try (var _ = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
                g2.translate(frame.previewShiftSs(), 0);
                render.run();
            }
        } else {
            render.run();
        }
    }

    /**
     * Renders tuplet brackets and numbers.
     *
     * @param g2    Graphics context
     * @param invariants   Line invariants
     * @param frame Element frame
     */
    private void renderTuplets(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        var line = invariants.requireCurrentLine();
        TupletRenderer.getInstance().renderTupletsFromLine(g2, line, invariants, frame);
    }

    /**
     * Renders key signature changes at the end of a line.
     * <p>
     * When the next line has a different key signature, this renders
     * naturals (if needed) and the new key signature at the end of
     * the current line as a warning to the performer.
     * Package-private for testing.
     *
     * @param g2  Graphics context
     * @param invariants Line invariants
     */
    void renderKeyChanges(Graphics2D g2, LineInvariants invariants) {
        var song = invariants.getSong();
        var lineIndex = invariants.getLineIndex();

        // Only render if there's a next line
        if (lineIndex + 1 >= song.lineCount()) {
            return;
        }

        var line = invariants.requireCurrentLine();
        var nextLine = song.getLine(lineIndex + 1);

        // Delegate to KeySignatureRenderer
        KeySignatureRenderer.getInstance().renderKeyChange(
            g2,
            line,
            nextLine,
            song.getLineWidthSs(),
            invariants
        );
    }

    /**
     * Renders crescendo and diminuendo hairpins.
     *
     * @param g2  Graphics context
     * @param invariants Line invariants
     */
    private void renderDynamics(Graphics2D g2, LineInvariants invariants) {
        invariants.requireCurrentLine();
        HairpinRenderer.getInstance().renderHairpinsFromLine(g2, invariants);
    }

    /**
     * Renders first/second ending brackets.
     *
     * @param g2  Graphics context
     * @param invariants Line invariants
     */
    private void renderEndings(Graphics2D g2, LineInvariants invariants) {
        var line = invariants.requireCurrentLine();
        EndingRenderer.getInstance().renderEndings(g2, line, invariants.getLineIndex(), invariants);
    }

    // ==========================================================================
    // Note Attachment Rendering
    // ==========================================================================

    /**
     * Renders note attachments using pre-computed positions from {@link LayoutResult}.
     * <p>
     * Dispatch is driven by {@link LayoutResult.DecorationLayout} presence
     * rather than legacy model flags. Rendering order follows the stacking tier order
     * (near-note first, system-level last).
     *
     * @param g2        Graphics context
     * @param invariants       Line invariants
     */
    private void renderAttachments(Graphics2D g2, LineInvariants invariants, ElementFrame lineFrame) {
        var articulationRenderer = ArticulationRenderer.getInstance();
        var fermataRenderer = FermataRenderer.getInstance();
        var dynamicMarkingRenderer = DynamicMarkingRenderer.getInstance();
        var tempoRenderer = TempoChangeRenderer.getInstance();
        var beatChangeRenderer = BeatChangeRenderer.getInstance();
        var annotationRenderer = AnnotationRenderer.getInstance();
        var lyricTextRenderer = LyricTextRenderer.getInstance();
        var line = lc.getLine();

        if (line == null) {
            return;
        }

        var layoutResult = invariants.getLayoutResult();

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
            var attachmentShiftActive = false;

            for (var i = 0; i < line.elementCount(); i++) {
                if (!attachmentShiftActive && lineFrame.hasPreviewShift() && i >= lineFrame.previewShiftFromIndex()) {
                    g2.translate(lineFrame.previewShiftSs(), 0);
                    attachmentShiftActive = true;
                }

                // Attachments use the cumulative g2.translate above for the preview shift,
                // not a per-element X override, so the override is always absent here.
                var frame = lineFrame.withElement(i, Double.NaN);
                var element = line.getElement(i);

                // Tier 1: Articulations (near-note)
                if (!element.getArticulations().isEmpty()) {
                    articulationRenderer.render(invariants, frame, element, g2);
                }

                // Tier 2: Fermata (note decoration)
                if (layoutResult.findAttachmentDecorationLayout(
                        element, FermataAttachment.class) != null) {
                    fermataRenderer.render(invariants, frame, element, g2);
                }

                // Tier 3: Dynamic markings (below staff)
                if (layoutResult.findAttachmentDecorationLayout(
                        element, DynamicAttachment.class) != null) {
                    dynamicMarkingRenderer.render(invariants, frame, element, g2);
                }

                // Tier 4: Tempo (system)
                if (layoutResult.findAttachmentDecorationLayout(
                        element, TempoChangeAttachment.class) != null) {
                    tempoRenderer.render(invariants, frame, element, g2);
                }

                // Tier 4: Beat change (system)
                if (layoutResult.findAttachmentDecorationLayout(
                        element, BeatChangeAttachment.class) != null) {
                    beatChangeRenderer.render(invariants, frame, element, g2);
                }

                // Tier 4: Annotation (system)
                if (layoutResult.findAttachmentDecorationLayout(
                        element, AnnotationAttachment.class) != null) {
                    annotationRenderer.render(invariants, frame, element, g2);
                }

                // Tier 5: Lyric syllable text (below staff)
                if (!layoutResult.getLyricBoxes(element).isEmpty()) {
                    lyricTextRenderer.render(invariants, frame, element, g2);
                }
            }
        }

        // Tier 2: Trills (rendered separately as they may span multiple notes)
        TrillRenderer.getInstance().renderTrillsFromLine(g2, invariants, lineFrame);

        // Tier 5: Lyric span connectors (hyphens, extenders) — line-level
        LyricConnectorRenderer.getInstance().render(g2, invariants, lineFrame);
    }

    // ==========================================================================
    // Overlay Rendering
    // ==========================================================================

    /**
     * Renders the drag rectangle during a selection drag on this line.
     *
     * @param g2 Graphics context
     */
    void renderDragRectangle(Graphics2D g2) {
        if (!lc.isDraggingSelection()) {
            return;
        }

        var dragRectangle = lc.getDragRectangle();

        if (dragRectangle.isEmpty()) {
            return;
        }

        // This rectangle is a pixel-space overlay drawn outside the Ss transform (see
        // LineComponent.render), so the border must be scaled by the zoom factor explicitly
        // rather than relying on the transform to do it, unlike Ss-space engraved lines.
        var factor = lc.getViewScale().factor();
        var arcPx = SELECTION_RECT_ARC_PX * factor;
        var strokeWidthPx = SELECTION_RECT_STROKE_WIDTH_PX * factor;
        var halfStrokeWidthPx = strokeWidthPx / 2;

        // The stroke is centered on the rectangle's path, so it extends halfStrokeWidthPx
        // beyond each edge. Swing clips painting at the line's own bounds, so a drag that
        // reaches the top or bottom of the line (see issue #643) would otherwise have its
        // border cut off there. Keep the drawn path inset by that half-width so the full
        // stroke always lands inside the line.
        var minX = halfStrokeWidthPx;
        var minY = halfStrokeWidthPx;
        var maxX = lc.getWidth() - 1 - halfStrokeWidthPx;
        var maxY = lc.getHeight() - 1 - halfStrokeWidthPx;

        var left = Math.max(dragRectangle.x, minX);
        var top = Math.max(dragRectangle.y, minY);
        var right = Math.min(dragRectangle.x + dragRectangle.width, maxX);
        var bottom = Math.min(dragRectangle.y + dragRectangle.height, maxY);

        var roundRect = new RoundRectangle2D.Double(
                left, top,
                Math.max(0, right - left), Math.max(0, bottom - top),
                arcPx, arcPx);

        try (var _ = GraphicsState.save(g2, GraphicsState.Property.STROKE, GraphicsState.Property.COLOR)) {
            g2.setStroke(new BasicStroke((float) strokeWidthPx));
            g2.setColor(ScoreView.getSelectionColor());
            g2.draw(roundRect);
        }
    }
}
