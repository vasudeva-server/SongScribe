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
import songscribe.layout.LayoutResult;
import songscribe.layout.NoteGeometry;
import songscribe.ui.Mode;
import songscribe.ui.component.ScoreView;
import songscribe.dom.AnnotationAttachment;
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
import songscribe.ui.renderer.DynamicsRenderer;
import songscribe.ui.renderer.ElementFrame;
import songscribe.ui.renderer.EndingRenderer;
import songscribe.ui.renderer.FermataRenderer;
import songscribe.ui.renderer.GlissandoRenderer;
import songscribe.ui.renderer.GraphicsState;
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

    /** The stroke used to draw the selection rectangle border. */
    private static final BasicStroke SELECTION_RECT_STROKE = new BasicStroke(2.0f);

    /** Corner arc diameter for the rubber-band selection rectangle, in pixels. */
    private static final int SELECTION_RECT_ARC_PX = 2;

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
        var song = lc.getSong();
        var line = lc.getLine();
        var lineIndex = lc.getLineIndex();

        var layoutResult = lc.getLayoutResult();

        if (layoutResult == null) {
            throw RuntimeError.exit("LineRenderer.render called before layout was performed");
        }

        var score = lc.getScoreView();
        var activeEditor = score.getActiveLyricEditor();

        // Build the immutable per-line invariants once.
        var invariants = LineInvariants.builder(song, score)
            .setCurrentLine(line)
            .setLineIndex(lineIndex)
            .setMiddleLineYSs(lc.getMiddleLineYSs())
            .setLayoutResult(layoutResult)
            .setSongLayoutMetrics(score.getSongLayoutMetrics())
            .setLyricRenderMetrics(score.getLyricRenderMetrics())
            .setActivelyEditedElement(activeEditor != null ? activeEditor.getActiveElement() : null)
            .setSelectionProvider(lc.getSelectionProvider())
            .setEditMode(lc.isEditMode())
            .setPlayingNoteIndex(lc.getPlayingNoteIndex())
            .setPlayingGraceNoteIndex(lc.getPlayingGraceNoteIndex())
            .build();

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
        renderGlissandos(g2, invariants, lineFrame);
        renderBeams(g2, invariants, lineFrame);
        renderTies(g2, invariants, lineFrame);
        renderTuplets(g2, invariants, lineFrame);
        renderKeyChanges(g2, invariants);
        renderDynamics(g2, invariants);
        renderEndings(g2, invariants);
        renderAttachments(g2, invariants, lineFrame);
        renderPreviewElement(g2, invariants, lineFrame);
    }

    // ==========================================================================
    // Staff and Line Beginning
    // ==========================================================================

    /**
     * Draws the 5 staff lines as filled rounded rectangles snapped to device pixels.
     */
    private void drawStaffLines(Graphics2D g2, LineInvariants invariants) {
        var selectionProvider = invariants.getSelectionProvider();
        var lineIndex = invariants.getLineIndex();
        var staffSelected = invariants.isEditMode()
            && selectionProvider != null
            && selectionProvider.isLineSelected(lineIndex);

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            g2.setColor(staffSelected ? ScoreView.getSelectionColor() : RenderingUtils.STAFF_LINE_COLOR);

            var lineWidth = invariants.getSong().getLineWidthSs();
            var middleLineYSs = invariants.getMiddleLineYSs();
            var staffLineThicknessSs = invariants.getLineThickness().staffLineSs();

            // Staff has 5 lines, middle line (B) is at index 2.
            // Lines are at: middleLineYSs - 2, middleLineYSs - 1, middleLineYSs,
            //               middleLineYSs + 1, middleLineYSs + 2
            for (var i = -2; i <= 2; i++) {
                var centerY = middleLineYSs + i;
                GraphicUtils.fillHorizontalLine(g2, 0, lineWidth, centerY, staffLineThicknessSs);
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
     * @param lineFrame Line-level frame (carries any active preview shift)
     */
    private void renderElements(Graphics2D g2, LineInvariants invariants, ElementFrame lineFrame) {
        var noteRenderer = NoteRenderer.getInstance();
        var line = invariants.getCurrentLine();

        if (line == null) {
            return;
        }

        var layoutResult = invariants.getLayoutResult();

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            for (var i = 0; i < line.elementCount(); i++) {
                var element = line.getElement(i);

                // Apply color based on selection/playing state
                var color = getElementColor(i, invariants);
                g2.setColor(color);

                var overrideXSs = lineFrame.hasPreviewShift()
                    && i >= lineFrame.previewShiftFromIndex()
                    && element.getType() != ElementType.FINAL_DOUBLE_BARLINE
                    ? layoutResult.getElementXSs(element) + lineFrame.previewShiftSs()
                    : Double.NaN;

                var frame = lineFrame.withElement(i, overrideXSs);
                noteRenderer.render(invariants, frame, element, g2);
            }
        }
    }

    /**
     * Determines the color for rendering an element.
     * <p>
     * Delegates edit mode, playback, selection, and hover logic to
     * {@link LineInvariants#getElementColor}. Adds grace-cancel coloring on top.
     *
     * @param elementIndex The index of the element within this line
     * @param invariants          The per-line invariants
     * @return The color to use for rendering
     */
    private Color getElementColor(int elementIndex, LineInvariants invariants) {
        var color = invariants.getElementColor(elementIndex);

        if (color != Color.BLACK) {
            return color;
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
     * Renders glissandos (wavy ornament lines) for all notes in the line.
     *
     * @param g2    Graphics context
     * @param invariants   Line invariants
     * @param frame Element frame
     */
    private void renderGlissandos(Graphics2D g2, LineInvariants invariants, ElementFrame frame) {
        var line = invariants.getCurrentLine();

        if (line == null) {
            return;
        }

        GlissandoRenderer.getInstance().renderGlissandosFromLine(g2, line, invariants, frame);
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
        var line = invariants.getCurrentLine();

        if (line == null) {
            return;
        }

        for (var beam : line.findRangeElements(Beam.class)) {
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
        var line = invariants.getCurrentLine();

        if (line == null) {
            return;
        }

        var ties = line.findTies();

        for (var span : ties) {
            renderWithPreviewShiftIfNeeded(g2, frame, span.getAnchorElementIndex(),
                () -> tieRenderer.renderTie(g2, span, invariants, frame));
        }
    }

    /**
     * Runs {@code render} with the frame's preview shift translated into {@code g2} when
     * {@code spanStart} falls at or after the shift boundary. The transform is restored
     * on exit (including on exception).
     */
    private static void renderWithPreviewShiftIfNeeded(
        Graphics2D g2,
        ElementFrame frame,
        int spanStart,
        Runnable render
    ) {
        if (frame.hasPreviewShift() && spanStart >= frame.previewShiftFromIndex()) {
            try (var ignored = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
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
        var line = invariants.getCurrentLine();

        if (line == null) {
            return;
        }

        TupletRenderer.getInstance().renderTupletsFromLine(g2, line, invariants, frame);
    }

    /**
     * Renders key signature changes at the end of a line.
     * <p>
     * When the next line has a different key signature, this renders
     * naturals (if needed) and the new key signature at the end of
     * the current line as a warning to the performer.
     *
     * @param g2  Graphics context
     * @param invariants Line invariants
     */
    private void renderKeyChanges(Graphics2D g2, LineInvariants invariants) {
        var song = invariants.getSong();
        var lineIndex = invariants.getLineIndex();
        var line = invariants.getCurrentLine();

        // Only render if there's a next line
        if (lineIndex + 1 >= song.lineCount()) {
            return;
        }

        if (line == null) {
            return;
        }

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
        var line = invariants.getCurrentLine();

        if (line == null) {
            return;
        }

        DynamicsRenderer.getInstance().renderHairpinsFromLine(g2, invariants);
    }

    /**
     * Renders first/second ending brackets.
     *
     * @param g2  Graphics context
     * @param invariants Line invariants
     */
    private void renderEndings(Graphics2D g2, LineInvariants invariants) {
        var line = invariants.getCurrentLine();

        if (line == null) {
            return;
        }

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
     * @param lineFrame Line-level frame (carries any active preview shift)
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

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
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
     * Renders the preview element if this line is the current preview line.
     *
     * @param g2        Graphics context
     * @param invariants       Line invariants
     * @param lineFrame Line-level frame (carries any active preview shift)
     */
    private void renderPreviewElement(Graphics2D g2, LineInvariants invariants, ElementFrame lineFrame) {
        // Only render if this line is the current insertion line
        if (!PreviewElementManager.hasPreviewElement(lc)) {
            return;
        }

        // Don't render preview element when in select mode
        var score = lc.getScoreView();

        if (score.getMode() == Mode.SELECT) {
            return;
        }

        var previewElement = lc.getPreviewElement();

        if (previewElement == null) {
            return;
        }

        // Glissando preview bypasses preview element visibility — it manages its own
        // display logic via shouldShowGlissandoPreview() and never uses the note-head preview.
        if (PreviewElementManager.isGlissandoPlaceholder(previewElement)) {
            if (!PreviewElementManager.shouldShowGlissandoPreview()) {
                return;
            }

            var type = PreviewElementManager.getGlissandoZone();

            if (type == null) {
                return;  // Defensive: shouldShowGlissandoPreview() guards this
            }

            var line = lc.getLine();

            if (line == null) {
                return;
            }

            var sourceIndex = PreviewElementManager.getCurrentXIndex() - 1;
            var sourceNote = line.getElement(sourceIndex);

            if (sourceNote.getGlissando() != null
                && sourceNote.getGlissando().type == type) {
                return;  // Already has this glissando type — no preview needed
            }

            try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
                g2.setColor(ScoreView.getPreviewElementColor());
                GlissandoRenderer.getInstance().renderPreviewGlissando(g2, sourceIndex, type, line, invariants);
            }
            return;
        }

        // Skip if preview element is not visible (e.g., in keyboard mode, or hovering over a note head)
        if (!lc.isPreviewElementVisible()) {
            return;
        }

        // Calculate X position from insertion index
        double x = 0;
        var currentXIndex = PreviewElementManager.getCurrentXIndex();
        var currentStaffPosition = PreviewElementManager.getCurrentStaffPosition();

        // In grace mode, use the locked x position directly — it already accounts
        // for grace note spacing. The standard calculateInsertionXSs would apply
        // normal inter-element spacing instead.
        if (lc.isGraceModeInProgress()) {
            x = lc.getGraceModeLockedXSs();
        } else {
            // Use the last tracked mouse X from PreviewElementManager — Swing's
            // getMousePosition() can return null during repaints even when the mouse
            // is over the component, which would break snap-to-terminal logic.
            var mouseX = PreviewElementManager.getCurrentMouseXSs();
            var line = lc.getLine();

            if (line != null) {
                x = invariants.getLayoutResult().calculateInsertionXSs(currentXIndex, mouseX, previewElement, line);
            }
        }

        // Set the preview element position
        previewElement.setStaffPosition(currentStaffPosition);
        previewElement.setUpper(ScoreView.defaultUpperNote(previewElement));

        // Render the preview element with the preview element color.
        // Pass x as an override so NoteRenderer and decoration renderers apply
        // device-pixel snapping to the raw double directly. Sub-renderers detect
        // preview rendering via hasOverrideElementX() and avoid looking up the
        // preview element in the layout (it isn't there).
        var frame = lineFrame.withElement(lineFrame.currentElementIndex(), x);
        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.COLOR)) {
            g2.setColor(ScoreView.getPreviewElementColor());
            NoteRenderer.getInstance().render(invariants, frame, previewElement, g2);

            // Render articulations and fermata on the preview element.
            // The override X remains set so decoration renderers use the precise position.
            if (!previewElement.getArticulations().isEmpty()) {
                ArticulationRenderer.getInstance().render(invariants, frame, previewElement, g2);
            }

            if (previewElement.findAttachment(FermataAttachment.class) != null) {
                FermataRenderer.getInstance().render(invariants, frame, previewElement, g2);
            }
        }
    }

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

        var roundRect = new RoundRectangle2D.Double(
                dragRectangle.x, dragRectangle.y,
                dragRectangle.width, dragRectangle.height,
                SELECTION_RECT_ARC_PX, SELECTION_RECT_ARC_PX);
        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.STROKE, GraphicsState.Property.COLOR)) {
            g2.setStroke(SELECTION_RECT_STROKE);
            g2.setColor(ScoreView.getSelectionColor());
            g2.draw(roundRect);
        }
    }
}
