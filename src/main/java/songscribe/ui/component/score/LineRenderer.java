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


import songscribe.music.StaffElement;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.Mode;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.layout.AnnotationAttachment;
import songscribe.ui.layout.BeatChangeAttachment;
import songscribe.ui.layout.Clef;
import songscribe.ui.layout.FermataAttachment;
import songscribe.ui.layout.KeySignature;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.layout.TempoAttachment;
import songscribe.ui.renderer.AnnotationRenderer;
import songscribe.ui.renderer.ArticulationRenderer;
import songscribe.ui.renderer.BeamGroupRenderer;
import songscribe.ui.renderer.BeatChangeRenderer;
import songscribe.ui.renderer.ClefRenderer;
import songscribe.ui.renderer.DynamicsRenderer;
import songscribe.ui.renderer.ElementRenderContext;
import songscribe.ui.renderer.EndingRenderer;
import songscribe.ui.renderer.FermataRenderer;
import songscribe.ui.renderer.GlissandoRenderer;
import songscribe.ui.renderer.KeySignatureRenderer;
import songscribe.ui.renderer.NoteRenderer;
import songscribe.ui.renderer.TempoRenderer;
import songscribe.ui.renderer.TieRenderer;
import songscribe.ui.renderer.TrillRenderer;
import songscribe.ui.renderer.TupletRenderer;
import songscribe.ui.FlatLafKeys;
import songscribe.ui.FlatLafProps;
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

    /** Color for staff lines. */
    private static final Color STAFF_LINE_COLOR = Color.BLACK;

    /** Staff line thickness in staff-space units (from SMuFL engraving defaults). */
    private static final double STAFF_LINE_THICKNESS =
        SMuFLMetadata.getInstance().getEngravingDefaults().staffLineThickness();

    /** The stroke used to draw the selection rectangle border. */
    private static final BasicStroke SELECTION_RECT_STROKE = new BasicStroke(2.0f);

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

        // Create render context for this rendering pass
        var ctx = new ElementRenderContext(composition);
        ctx.setCurrentLine(line);
        ctx.setLineIndex(lineIndex);
        ctx.setMiddleLineYSs(lc.getMiddleLineYSs());
        ctx.setLayoutResult(lc.getLayoutResult());
        ctx.setSelectionProvider(lc.getSelectionProvider());
        ctx.setEditMode(lc.isEditMode());
        ctx.setPlayingNoteIndex(lc.getPlayingNoteIndex());
        ctx.setPlayingGraceNoteIndex(lc.getPlayingGraceNoteIndex());

        // Ensure NoteRenderer metrics are initialized
        NoteRenderer.initializeAccidentalWidths(g2);

        // Render in proper order (back to front)
        drawStaffLines(g2);
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
        renderInsertionElement(g2, ctx);
    }

    /**
     * Renders debug visualizations for the staff line.
     * <p>
     * Called from {@link LineComponent#renderDebug(Graphics2D)} after
     * the base class debug rendering.
     *
     * @param g2 Graphics context
     */
    void renderDebug(Graphics2D g2) {
        var middleLineYPx = lc.getMiddleLineYPx();
        var line = lc.getLine();
        var layoutResult = lc.getLayoutResult();

        // Draw middle line indicator
        g2.setColor(new Color(0, 0, 255, 128));
        g2.drawLine(0, middleLineYPx, 20, middleLineYPx);

        // Draw note positions
        if (line != null) {
            g2.setColor(new Color(255, 0, 255, 128));

            for (var i = 0; i < line.elementCount(); i++) {
                var element = line.getElement(i);
                var x = element.getXPosSs();
                var y = lc.staffPositionToYPx(element.getStaffPosition());
                g2.fillOval(x - 2, y - 2, 4, 4);
            }
        }

        // Draw note columns and stacking areas when DEBUG environment variable is set
        if (layoutResult != null) {
            // Save original stroke
            var originalStroke = g2.getStroke();
            // Use a thin stroke that doesn't expand bounds
            g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

            // Draw note column rectangles
            g2.setColor(new Color(0, 255, 0, 80));  // Green with transparency
            var staffTopYSs = layoutResult.getStaffTopYSs();
            var staffBottomYSs = layoutResult.getStaffBottomYSs();

            for (var column : layoutResult.getElementColumns().values()) {
                var leftX = column.getLeftEdgeXSs();
                var rightX = column.getRightEdgeXSs();
                var width = rightX - leftX;
                var height = staffBottomYSs - staffTopYSs;

                // Draw column rectangle (X is absolute, Y centered on middleLineYPx)
                var rect = new Rectangle2D.Double(
                    leftX,
                    middleLineYPx - height / 2,
                    width,
                    height
                );
                g2.draw(rect);
            }

            // Draw element bounds (stacking areas)
            g2.setColor(new Color(255, 165, 0, 80));  // Orange with transparency

            for (var bounds : layoutResult.getElementBounds().values()) {
                // X is absolute, Y is relative to middleLineYPx
                var rect = new Rectangle2D.Double(
                    bounds.getLeft(),
                    middleLineYPx + bounds.getTop(),
                    bounds.getWidth(),
                    bounds.getHeight()
                );
                g2.draw(rect);
            }

            // Restore original stroke
            g2.setStroke(originalStroke);
        }
    }

    // ==========================================================================
    // Staff and Line Beginning
    // ==========================================================================

    /**
     * Draws the 5 staff lines as filled rectangles snapped to device pixels.
     */
    private void drawStaffLines(Graphics2D g2) {
        var selectionProvider = lc.getSelectionProvider();
        var lineIndex = lc.getLineIndex();
        var staffSelected = lc.isEditMode()
            && selectionProvider != null
            && selectionProvider.isLineSelected(lineIndex);

        g2.setColor(staffSelected ? Score.getSelectionStrokeColor() : STAFF_LINE_COLOR);

        var lineWidth = lc.getComposition().getLineWidthSs();
        var middleLineYSs = lc.getMiddleLineYSs();

        // Staff has 5 lines, middle line (B) is at index 2.
        // Lines are at: middleLineYSs - 2, middleLineYSs - 1, middleLineYSs,
        //               middleLineYSs + 1, middleLineYSs + 2
        // Each line is drawn as a filled rectangle snapped to device pixels
        // to avoid anti-aliasing artifacts from stroking.
        for (var i = -2; i <= 2; i++) {
            var centerY = middleLineYSs + i;
            var snappedTop = GraphicUtils.snapYToDevicePixel(g2, centerY - STAFF_LINE_THICKNESS / 2);
            var snappedBottom = GraphicUtils.snapYToDevicePixel(g2, centerY + STAFF_LINE_THICKNESS / 2);

            g2.fill(new Rectangle2D.Double(0, snappedTop, lineWidth, snappedBottom - snappedTop));
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

        if (layoutResult == null) {
            return;
        }

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

        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);

            // Apply color based on selection/playing state
            var color = getElementColor(i, ctx);
            g2.setColor(color);

            noteRenderer.render(g2, element, ctx);
        }

        // Restore default color
        g2.setColor(Color.BLACK);
    }

    /**
     * Determines the color for rendering an element based on selection and playback state.
     *
     * @param elementIndex The index of the element within this line
     * @return The color to use for rendering
     */
    private Color getElementColor(int elementIndex, ElementRenderContext ctx) {
        if (!lc.isEditMode()) {
            return Color.BLACK;
        }

        // Check if element is currently playing (primary note or grace note)
        if (ctx.isElementPlaying(elementIndex)) {
            return Score.getPlayingNoteColor();
        }

        // Check if element is part of a tie that contains the playing note
        if (ctx.isElementInPlayingTie(elementIndex)) {
            return Score.getPlayingNoteColor();
        }

        // Check if element is selected or highlighted by insertion element hover
        var selectionProvider = lc.getSelectionProvider();
        var isSelected = selectionProvider != null
            && selectionProvider.isElementSelected(elementIndex, lc.getLineIndex());

        if (isSelected) {
            return ctx.getSelectionColor();
        }

        var isHovered = InsertionElementManager.getHoveredElementLineIndex() == lc.getLineIndex()
            && InsertionElementManager.getHoveredElementIndex() == elementIndex;

        if (isHovered) {
            return Score.getInsertionElementColor();
        }

        // Grace note pending cancellation (drag-left past slop)
        var line = lc.getLine();

        if (line != null) {
            var element = line.getElement(elementIndex);

            if (GraceModeManager.isPendingCancel(element)) {
                return Color.RED;
            }
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
            var interval = iter.next();
            beamRenderer.renderBeams(g2, line, ctx, interval.getStart(), interval.getEnd());
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
            var interval = iter.next();
            tieRenderer.renderTie(g2, interval, ctx);
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
        var tempoRenderer = TempoRenderer.getInstance();
        var beatChangeRenderer = BeatChangeRenderer.getInstance();
        var annotationRenderer = AnnotationRenderer.getInstance();
        var line = lc.getLine();
        var layoutResult = lc.getLayoutResult();

        if (line == null) {
            return;
        }

        g2.setColor(Color.BLACK);

        for (var i = 0; i < line.elementCount(); i++) {
            var element = line.getElement(i);

            // Tier 1: Articulations (near-note)
            if (!element.getArticulations().isEmpty()) {
                articulationRenderer.render(element, g2, ctx);
            }

            // Tier 2: Fermata (note decoration)
            if (layoutResult != null
                && layoutResult.findAttachmentDecorationLayout(
                    element, FermataAttachment.class) != null) {
                fermataRenderer.render(element, g2, ctx);
            }

            // Tier 4: Tempo (system)
            if (layoutResult != null
                && layoutResult.findAttachmentDecorationLayout(
                    element, TempoAttachment.class) != null) {
                tempoRenderer.render(element, g2, ctx);
            }

            // Tier 4: Beat change (system)
            if (layoutResult != null
                && layoutResult.findAttachmentDecorationLayout(
                    element, BeatChangeAttachment.class) != null) {
                beatChangeRenderer.render(element, g2, ctx);
            }

            // Tier 4: Annotation (system)
            if (layoutResult != null
                && layoutResult.findAttachmentDecorationLayout(
                    element, AnnotationAttachment.class) != null) {
                annotationRenderer.render(element, g2, ctx);
            }
        }

        // Tier 2: Trills (rendered separately as they may span multiple notes)
        TrillRenderer.getInstance().renderTrillsFromLine(g2, ctx);
    }

    // ==========================================================================
    // Overlay Rendering
    // ==========================================================================

    /**
     * Renders the insertion element if this line is the current insertion line.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderInsertionElement(Graphics2D g2, ElementRenderContext ctx) {
        // Only render if this line is the current insertion line
        if (!InsertionElementManager.hasInsertionElement(lc)) {
            return;
        }

        // Don't render insertion element when in select mode
        var score = lc.getScore();

        if (score != null && score.getMode() == Mode.SELECT) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager == null) {
            return;
        }

        var insertionElement = editModeManager.getInsertionElement();

        if (insertionElement == null) {
            return;
        }

        // Glissando preview bypasses insertion element visibility — it manages its own
        // display logic via shouldShowGlissandoPreview() and never uses the note-head preview.
        if (InsertionElementManager.isGlissandoPlaceholder(insertionElement)) {
            if (!InsertionElementManager.shouldShowGlissandoPreview()) {
                return;
            }

            var type = InsertionElementManager.getGlissandoZone();

            if (type == null) {
                return;  // Defensive: shouldShowGlissandoPreview() guards this
            }

            var line = lc.getLine();

            if (line == null) {
                return;
            }

            var sourceIndex = InsertionElementManager.getCurrentXIndex() - 1;
            var sourceNote = line.getElement(sourceIndex);

            if (sourceNote.getGlissando() != null
                && sourceNote.getGlissando().type == type) {
                return;  // Already has this glissando type — no preview needed
            }

            g2.setColor(Score.getInsertionElementColor());
            GlissandoRenderer.getInstance().renderPreviewGlissando(
                g2, sourceIndex, type, line, ctx
            );
            return;
        }

        // Skip if insertion element is not visible (e.g., in keyboard mode, or hovering over a note head)
        if (!editModeManager.isInsertionElementVisible()) {
            return;
        }

        // Calculate X position from insertion index
        double x = 0;
        var currentXIndex = InsertionElementManager.getCurrentXIndex();
        var currentStaffPosition = InsertionElementManager.getCurrentStaffPosition();

        // In grace mode, use the locked x position directly — it already accounts
        // for grace note spacing. The standard calculateInsertionXSs would apply
        // normal inter-element spacing instead.
        var graceModeManager = editModeManager.getGraceModeManager();

        if (graceModeManager.isInProgress()) {
            x = graceModeManager.getLockedInsertionXSs();
        } else {
            // Pass mouse X so it can snap to note heads when mouse is over them
            double mouseX = 0;
            var mousePos = lc.getMousePosition();

            if (mousePos != null) {
                mouseX = ScaleContext.getInstance().fromPixels(mousePos.getX());
            }

            var layoutResult = lc.getLayoutResult();
            var line = lc.getLine();

            if (layoutResult != null && line != null) {
                x = layoutResult.calculateInsertionXSs(currentXIndex, mouseX, insertionElement, line);
            }
        }

        // Set the insertion element position
        insertionElement.setStaffPosition(currentStaffPosition);
        insertionElement.setUpper(Score.defaultUpperNote(insertionElement));

        // Render the insertion element with the insertion element color.
        // Pass x as an override so NoteRenderer and decoration renderers apply
        // device-pixel snapping to the raw double directly, exactly as they do for
        // composition elements via layoutResult.getElementXSs(). Temporarily clear
        // layoutResult to prevent sub-renderers from looking up the insertion element
        // (which is not in the layout).
        var savedLayout = ctx.getLayoutResult();
        ctx.setOverrideElementXSs(x);
        ctx.setLayoutResult(null);
        g2.setColor(Score.getInsertionElementColor());
        NoteRenderer.getInstance().render(g2, insertionElement, ctx);

        // Render articulations and fermata on the insertion element preview.
        // The override X remains set so decoration renderers use the precise position.
        if (!insertionElement.getArticulations().isEmpty()) {
            ArticulationRenderer.getInstance().render(insertionElement, g2, ctx);
        }

        if (insertionElement.isFermata()) {
            FermataRenderer.getInstance().render(insertionElement, g2, ctx);
        }

        ctx.clearOverrideElementX();
        ctx.setLayoutResult(savedLayout);
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

        g2.setColor(FlatLafProps.get(FlatLafKeys.SCORE_SELECTION_RECT_FILL));
        g2.fill(dragRectangle);

        var originalStroke = g2.getStroke();
        g2.setStroke(SELECTION_RECT_STROKE);
        g2.setColor(Score.getSelectionStrokeColor());
        g2.draw(dragRectangle);
        g2.setStroke(originalStroke);
    }
}
