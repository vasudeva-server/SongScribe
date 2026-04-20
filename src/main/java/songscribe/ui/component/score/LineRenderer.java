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
import songscribe.music.ElementType;
import songscribe.ui.Mode;
import songscribe.ui.component.Score;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.BeatChangeAttachment;
import songscribe.ui.layout.DynamicAttachment;
import songscribe.ui.layout.FermataAttachment;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.layout.TempoChangeAttachment;
import songscribe.ui.renderer.AnnotationRenderer;
import songscribe.ui.renderer.ArticulationRenderer;
import songscribe.ui.renderer.BaseElementRenderer;
import songscribe.ui.renderer.BeamGroupRenderer;
import songscribe.ui.renderer.BeatChangeRenderer;
import songscribe.ui.renderer.ClefRenderer;
import songscribe.ui.renderer.DynamicMarkingRenderer;
import songscribe.ui.renderer.DynamicsRenderer;
import songscribe.ui.renderer.ElementRenderContext;
import songscribe.ui.renderer.EndingRenderer;
import songscribe.ui.renderer.FermataRenderer;
import songscribe.ui.renderer.GlissandoRenderer;
import songscribe.ui.renderer.GraphicsState;
import songscribe.ui.renderer.KeySignatureRenderer;
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
        this.lc = lineComponent;
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
        var composition = lc.getComposition();
        var line = lc.getLine();
        var lineIndex = lc.getLineIndex();

        var layoutResult = lc.getLayoutResult();

        if (layoutResult == null) {
            throw RuntimeError.exit("LineRenderer.render called before layout was performed");
        }

        // Create render context for this rendering pass
        var ctx = new ElementRenderContext(composition);
        ctx.setCurrentLine(line);
        ctx.setLineIndex(lineIndex);
        ctx.setMiddleLineYSs(lc.getMiddleLineYSs());
        ctx.setLayoutResult(layoutResult);
        ctx.setSelectionProvider(lc.getSelectionProvider());
        ctx.setEditMode(lc.isEditMode());
        ctx.setPlayingNoteIndex(lc.getPlayingNoteIndex());
        ctx.setPlayingGraceNoteIndex(lc.getPlayingGraceNoteIndex());
        // Ensure NoteRenderer metrics are initialized
        NoteRenderer.initializeAccidentalWidths(g2);

        // When in grace-note insert mode for this line, shift subsequent elements
        // rightward to show where the host note will land before the user clicks.
        lc.applyGracePreviewShift(ctx);

        // Render in proper order (back to front)
        drawStaffLines(g2, ctx);
        renderLineBeginning(g2, ctx);
        renderElements(g2, ctx);
        renderGlissandos(g2, ctx);
        renderBeams(g2, ctx);
        renderTies(g2, ctx);
        renderTuplets(g2, ctx);
        renderKeyChanges(g2, ctx);
        renderDynamics(g2, ctx);
        renderEndings(g2, ctx);
        renderAttachments(g2, ctx);
        renderPreviewElement(g2, ctx);
    }

    // ==========================================================================
    // Staff and Line Beginning
    // ==========================================================================

    /**
     * Draws the 5 staff lines as filled rounded rectangles snapped to device pixels.
     */
    private void drawStaffLines(Graphics2D g2, ElementRenderContext ctx) {
        var selectionProvider = lc.getSelectionProvider();
        var lineIndex = lc.getLineIndex();
        var staffSelected = lc.isEditMode()
            && selectionProvider != null
            && selectionProvider.isLineSelected(lineIndex);

        g2.setColor(staffSelected ? Score.getSelectionStrokeColor() : BaseElementRenderer.STAFF_LINE_COLOR);

        var lineWidth = lc.getComposition().getLineWidthSs();
        var middleLineYSs = lc.getMiddleLineYSs();
        var staffLineThicknessSs = ctx.getLineThickness().staffLineSs();

        // Staff has 5 lines, middle line (B) is at index 2.
        // Lines are at: middleLineYSs - 2, middleLineYSs - 1, middleLineYSs,
        //               middleLineYSs + 1, middleLineYSs + 2
        for (var i = -2; i <= 2; i++) {
            var centerY = middleLineYSs + i;
            GraphicUtils.fillHorizontalLine(g2, 0, lineWidth, centerY, staffLineThicknessSs);
        }
    }

    /**
     * Renders the line beginning (clef and key signature).
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderLineBeginning(Graphics2D g2, ElementRenderContext ctx) {
        var layoutResult = ctx.getLayoutResult();
        var clef = layoutResult.getClef();

        if (clef != null) {
            ClefRenderer.getInstance().render(clef, g2, ctx);
        }

        var keySig = layoutResult.getKeySignature();

        if (keySig != null) {
            KeySignatureRenderer.getInstance().render(keySig, g2, ctx);
        }
    }

    // ==========================================================================
    // Note Rendering
    // ==========================================================================

    /**
     * Renders notes using NoteRenderer.
     *
     * @param g2  Graphics context
     * @param ctx Element render context
     */
    private void renderElements(Graphics2D g2, ElementRenderContext ctx) {
        var noteRenderer = NoteRenderer.getInstance();
        var line = lc.getLine();

        if (line == null) {
            return;
        }

        var hasShift = ctx.hasPreviewShift();
        var shiftFromIndex = hasShift ? ctx.getPreviewShiftFromIndex() : Integer.MAX_VALUE;
        var shiftSs = hasShift ? ctx.getPreviewShiftSs() : 0.0;
        var layoutResult = ctx.getLayoutResult();

        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);

            // Apply color based on selection/playing state
            var color = getElementColor(i, ctx);
            g2.setColor(color);

            if (hasShift && i >= shiftFromIndex && element.getType() != ElementType.FINAL_DOUBLE_BARLINE) {
                ctx.setOverrideElementXSs(layoutResult.getElementXSs(element) + shiftSs);
                noteRenderer.render(g2, element, ctx);
                ctx.clearOverrideElementX();
            } else {
                noteRenderer.render(g2, element, ctx);
            }
        }

        // Restore default color
        g2.setColor(Color.BLACK);
    }

    /**
     * Determines the color for rendering an element.
     * <p>
     * Delegates edit mode, playback, selection, and hover logic to
     * {@link ElementRenderContext#getElementColor}. Adds grace-cancel coloring on top.
     *
     * @param elementIndex The index of the element within this line
     * @param ctx          The render context
     * @return The color to use for rendering
     */
    private Color getElementColor(int elementIndex, ElementRenderContext ctx) {
        var color = ctx.getElementColor(elementIndex);

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
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderGlissandos(Graphics2D g2, ElementRenderContext ctx) {
        var line = lc.getLine();

        if (line == null) {
            return;
        }

        GlissandoRenderer.getInstance().renderGlissandosFromLine(g2, line, ctx);
    }

    /**
     * Renders beam groups connecting beamed notes.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderBeams(Graphics2D g2, ElementRenderContext ctx) {
        var beamRenderer = BeamGroupRenderer.getInstance();
        var line = lc.getLine();

        if (line == null) {
            return;
        }

        var beamings = line.getBeamings();

        for (var iter = beamings.listIterator(); iter.hasNext(); ) {
            var span = iter.next();
            renderWithPreviewShiftIfNeeded(g2, ctx, span.getStart(),
                () -> beamRenderer.renderBeams(g2, line, ctx, span.getStart(), span.getEnd()));
        }
    }

    /**
     * Renders ties between notes.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderTies(Graphics2D g2, ElementRenderContext ctx) {
        var tieRenderer = TieRenderer.getInstance();
        var line = lc.getLine();

        if (line == null) {
            return;
        }

        var ties = line.getTies();

        for (var iter = ties.listIterator(); iter.hasNext(); ) {
            var span = iter.next();
            renderWithPreviewShiftIfNeeded(g2, ctx, span.getStart(),
                () -> tieRenderer.renderTie(g2, span, ctx));
        }
    }

    /**
     * Runs {@code render} with the context's preview shift translated into {@code g2} when
     * {@code spanStart} falls at or after the shift boundary. The transform is restored
     * on exit (including on exception).
     */
    private static void renderWithPreviewShiftIfNeeded(
        Graphics2D g2,
        ElementRenderContext ctx,
        int spanStart,
        Runnable render
    ) {
        if (ctx.hasPreviewShift() && spanStart >= ctx.getPreviewShiftFromIndex()) {
            try (var ignored = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
                g2.translate(ctx.getPreviewShiftSs(), 0);
                render.run();
            }
        } else {
            render.run();
        }
    }

    /**
     * Renders tuplet brackets and numbers.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderTuplets(Graphics2D g2, ElementRenderContext ctx) {
        var line = lc.getLine();

        if (line == null) {
            return;
        }

        TupletRenderer.getInstance().renderTupletsFromLine(g2, line, ctx);
    }

    /**
     * Renders key signature changes at the end of a line.
     * <p>
     * When the next line has a different key signature, this renders
     * naturals (if needed) and the new key signature at the end of
     * the current line as a warning to the performer.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderKeyChanges(Graphics2D g2, ElementRenderContext ctx) {
        var composition = lc.getComposition();
        var lineIndex = lc.getLineIndex();
        var line = lc.getLine();

        // Only render if there's a next line
        if (lineIndex + 1 >= composition.lineCount()) {
            return;
        }

        if (line == null) {
            return;
        }

        var nextLine = composition.getLine(lineIndex + 1);

        // Delegate to KeySignatureRenderer
        KeySignatureRenderer.getInstance().renderKeyChange(
            g2,
            line,
            nextLine,
            composition.getLineWidthSs(),
            ctx
        );
    }

    /**
     * Renders crescendo and diminuendo hairpins.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderDynamics(Graphics2D g2, ElementRenderContext ctx) {
        var line = lc.getLine();

        if (line == null) {
            return;
        }

        DynamicsRenderer.getInstance().renderHairpinsFromLine(g2, ctx);
    }

    /**
     * Renders first/second ending brackets.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderEndings(Graphics2D g2, ElementRenderContext ctx) {
        var line = lc.getLine();

        if (line == null) {
            return;
        }

        EndingRenderer.getInstance().renderEndings(
            g2, line, lc.getLineIndex(), ctx
        );
    }

    // ==========================================================================
    // Note Attachment Rendering
    // ==========================================================================

    /**
     * Renders note attachments using pre-computed positions from {@link songscribe.ui.layout.LayoutResult}.
     * <p>
     * Dispatch is driven by {@link songscribe.ui.layout.LayoutResult.DecorationLayout} presence
     * rather than legacy model flags. Rendering order follows the stacking tier order
     * (near-note first, system-level last).
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderAttachments(Graphics2D g2, ElementRenderContext ctx) {
        var articulationRenderer = ArticulationRenderer.getInstance();
        var fermataRenderer = FermataRenderer.getInstance();
        var dynamicMarkingRenderer = DynamicMarkingRenderer.getInstance();
        var tempoRenderer = TempoChangeRenderer.getInstance();
        var beatChangeRenderer = BeatChangeRenderer.getInstance();
        var annotationRenderer = AnnotationRenderer.getInstance();
        var line = lc.getLine();

        if (line == null) {
            return;
        }

        var layoutResult = ctx.getLayoutResult();

        g2.setColor(Color.BLACK);

        try (var ignored = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
            boolean attachmentShiftActive = false;

            for (var i = 0; i < line.elementCount(); i++) {
                if (!attachmentShiftActive && ctx.hasPreviewShift() && i >= ctx.getPreviewShiftFromIndex()) {
                    g2.translate(ctx.getPreviewShiftSs(), 0);
                    attachmentShiftActive = true;
                }

                ctx.setCurrentElementIndex(i);
                var element = line.getElement(i);

                // Tier 1: Articulations (near-note)
                if (!element.getArticulations().isEmpty()) {
                    articulationRenderer.render(element, g2, ctx);
                }

                // Tier 2: Fermata (note decoration)
                if (layoutResult.findAttachmentDecorationLayout(
                        element, FermataAttachment.class) != null) {
                    fermataRenderer.render(element, g2, ctx);
                }

                // Tier 3: Dynamic markings (below staff)
                if (layoutResult.findAttachmentDecorationLayout(
                        element, DynamicAttachment.class) != null) {
                    dynamicMarkingRenderer.render(element, g2, ctx);
                }

                // Tier 4: Tempo (system)
                if (layoutResult.findAttachmentDecorationLayout(
                        element, TempoChangeAttachment.class) != null) {
                    tempoRenderer.render(element, g2, ctx);
                }

                // Tier 4: Beat change (system)
                if (layoutResult.findAttachmentDecorationLayout(
                        element, BeatChangeAttachment.class) != null) {
                    beatChangeRenderer.render(element, g2, ctx);
                }

                // Tier 4: Annotation (system)
                if (layoutResult.findAttachmentDecorationLayout(
                        element, AnnotationAttachment.class) != null) {
                    annotationRenderer.render(element, g2, ctx);
                }
            }
        }

        ctx.setCurrentElementIndex(-1);

        // Tier 2: Trills (rendered separately as they may span multiple notes)
        TrillRenderer.getInstance().renderTrillsFromLine(g2, ctx);
    }

    // ==========================================================================
    // Overlay Rendering
    // ==========================================================================

    /**
     * Renders the preview element if this line is the current preview line.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderPreviewElement(Graphics2D g2, ElementRenderContext ctx) {
        // Only render if this line is the current insertion line
        if (!PreviewElementManager.hasPreviewElement(lc)) {
            return;
        }

        // Don't render preview element when in select mode
        var score = lc.getScore();

        if (score != null && score.getMode() == Mode.SELECT) {
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

            g2.setColor(Score.getPreviewElementColor());
            GlissandoRenderer.getInstance().renderPreviewGlissando(
                g2, sourceIndex, type, line, ctx
            );
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
                x = ctx.getLayoutResult().calculateInsertionXSs(currentXIndex, mouseX, previewElement, line);
            }
        }

        // Set the preview element position
        previewElement.setStaffPosition(currentStaffPosition);
        previewElement.setUpper(Score.defaultUpperNote(previewElement));

        // Render the preview element with the preview element color.
        // Pass x as an override so NoteRenderer and decoration renderers apply
        // device-pixel snapping to the raw double directly. Sub-renderers detect
        // preview rendering via hasOverrideElementX() and avoid looking up the
        // preview element in the layout (it isn't there).
        ctx.setOverrideElementXSs(x);
        g2.setColor(Score.getPreviewElementColor());
        NoteRenderer.getInstance().render(g2, previewElement, ctx);

        // Render articulations and fermata on the preview element.
        // The override X remains set so decoration renderers use the precise position.
        if (!previewElement.getArticulations().isEmpty()) {
            ArticulationRenderer.getInstance().render(previewElement, g2, ctx);
        }

        if (previewElement.isFermata()) {
            FermataRenderer.getInstance().render(previewElement, g2, ctx);
        }

        ctx.clearOverrideElementX();
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
        var originalStroke = g2.getStroke();
        g2.setStroke(SELECTION_RECT_STROKE);
        g2.setColor(Score.getSelectionStrokeColor());
        g2.draw(roundRect);
        g2.setStroke(originalStroke);
    }
}
