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

import java.awt.*;
import java.awt.geom.*;

import org.jetbrains.annotations.NotNull;

import songscribe.data.Interval;
import songscribe.music.Note;
import songscribe.ui.Mode;
import songscribe.ui.component.Score;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.LineElement;
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
import songscribe.ui.renderer.RendererRegistry;
import songscribe.ui.renderer.TempoRenderer;
import songscribe.ui.renderer.TieRenderer;
import songscribe.ui.renderer.TrillRenderer;
import songscribe.ui.renderer.TupletRenderer;

/**
 * Handles all rendering for a single staff line.
 * <p>
 * Extracted from {@link LineComponent} to separate rendering concerns.
 * Reads state from the owning LineComponent but does not mutate it
 * (except for positioning the edit note preview before drawing).
 */
class LineRenderer {

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Color for staff lines. */
    private static final Color STAFF_LINE_COLOR = Color.BLACK;

    /** Color for placeholder rectangles (for unregistered element types). */
    private static final Color PLACEHOLDER_COLOR = new Color(100, 100, 100, 128);

    /** Color for the insertion note preview. */
    private static final Color EDIT_NOTE_COLOR = new Color(3, 136, 255);

    /** The stroke used to draw the selection rectangle border. */
    private static final BasicStroke SELECTION_RECT_STROKE = new BasicStroke(2.0f);

    /** Fill color for selection rectangle (transparent version of selection stroke). */
    private static final Color SELECTION_RECT_FILL_COLOR = new Color(
        Score.SELECTION_STROKE_COLOR.getRed(),
        Score.SELECTION_STROKE_COLOR.getGreen(),
        Score.SELECTION_STROKE_COLOR.getBlue(),
        8
    );

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
    LineRenderer(@NotNull LineComponent lineComponent) {
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
        var middleLineY = lc.getMiddleLineY();
        var lineIndex = lc.getLineIndex();

        // Create render context for this rendering pass
        var ctx = new ElementRenderContext(composition);
        ctx.setCurrentLine(line);
        ctx.setLineIndex(lineIndex);
        ctx.setMiddleLineY(middleLineY);
        ctx.setLayoutResult(lc.getLayoutResult());
        ctx.setSelectionProvider(lc.getSelectionProvider());
        ctx.setEditMode(lc.isEditMode());

        // Ensure NoteRenderer metrics are initialized
        NoteRenderer.initializeAccidentalWidths(g2);

        // Render in proper order (back to front)
        drawStaffLines(g2);
        renderLineBeginning(g2, ctx);
        renderNotes(g2, ctx);
        renderGlissandos(g2, ctx);
        renderBeams(g2, ctx);
        renderTies(g2, ctx);
        renderTuplets(g2, ctx);
        renderKeyChanges(g2, ctx);
        renderDynamics(g2, ctx);
        renderEndings(g2, ctx);
        renderAttachments(g2, ctx);
        renderInsertionNote(g2, ctx);
        renderDragRectangle(g2);
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
        var middleLineY = lc.getMiddleLineY();
        var line = lc.getLine();
        var layoutResult = lc.getLayoutResult();

        // Draw middle line indicator
        g2.setColor(new Color(0, 0, 255, 128));
        g2.drawLine(0, middleLineY, 20, middleLineY);

        // Draw note positions
        if (line != null) {
            g2.setColor(new Color(255, 0, 255, 128));
            var noteYOffset = Score.NOTE_Y_OFFSET;

            for (var i = 0; i < line.noteCount(); i++) {
                var note = line.getNote(i);
                var x = note.getXPos();
                var y = middleLineY + (int) (note.getYPos() * noteYOffset);
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
            var staffTopY = layoutResult.getStaffTopY();
            var staffBottomY = layoutResult.getStaffBottomY();

            for (var column : layoutResult.getNoteColumns().values()) {
                var leftX = column.getLeftEdgeX();
                var rightX = column.getRightEdgeX();
                var width = rightX - leftX;
                var height = staffBottomY - staffTopY;

                // Draw column rectangle (X is absolute, Y centered on middleLineY)
                var rect = new Rectangle2D.Double(
                    leftX,
                    middleLineY - height / 2,
                    width,
                    height
                );
                g2.draw(rect);
            }

            // Draw element bounds (stacking areas)
            g2.setColor(new Color(255, 165, 0, 80));  // Orange with transparency

            for (var bounds : layoutResult.getElementBounds().values()) {
                // X is absolute, Y is relative to middleLineY
                var rect = new Rectangle2D.Double(
                    bounds.getLeft(),
                    middleLineY + bounds.getTop(),
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
     * Draws the 5 staff lines.
     */
    private void drawStaffLines(Graphics2D g2) {
        var selectionProvider = lc.getSelectionProvider();
        var lineIndex = lc.getLineIndex();
        var staffSelected = lc.isEditMode()
            && selectionProvider != null
            && selectionProvider.isLineSelected(lineIndex);

        g2.setColor(staffSelected ? Score.SELECTION_STROKE_COLOR : STAFF_LINE_COLOR);

        var lineWidth = lc.getComposition().getLineWidth();
        var middleLineY = lc.getMiddleLineY();
        var staffLineYOffset = LayoutStylesheet.STAFF_SPACE;

        // Staff has 5 lines, middle line (B) is at index 2
        // Lines are at: middleLineY - 2*offset, middleLineY - offset, middleLineY,
        //               middleLineY + offset, middleLineY + 2*offset
        for (var i = -2; i <= 2; i++) {
            var y = middleLineY + (i * staffLineYOffset);
            g2.drawLine(0, y, lineWidth, y);
        }
    }

    /**
     * Renders the line beginning (clef and key signature).
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderLineBeginning(Graphics2D g2, ElementRenderContext ctx) {
        var line = lc.getLine();
        var middleLineY = lc.getMiddleLineY();

        // Render treble clef
        ClefRenderer.getInstance().renderClef(g2, ctx);

        // Render key signature
        KeySignatureRenderer.getInstance().renderKeySignature(
            g2,
            line.getKeyType(),
            line.getKeyAccidentalCount(),
            ctx.getLeadingKeysPos(),
            middleLineY,
            ctx
        );
    }

    // ==========================================================================
    // Note Rendering
    // ==========================================================================

    /**
     * Renders notes using the modular renderer system.
     *
     * @param g2  Graphics context
     * @param ctx Element render context
     */
    private void renderNotes(Graphics2D g2, ElementRenderContext ctx) {
        var rootElement = lc.getRootElement();

        if (rootElement != null) {
            // Render LineElement tree using registered renderers
            renderElement(g2, rootElement, ctx);
        } else {
            // Fallback: render notes directly using NoteRenderer
            renderNotesDirectly(g2, ctx);
        }
    }

    /**
     * Recursively renders a LineElement and its children using registered renderers.
     *
     * @param g2      Graphics context
     * @param element Element to render
     * @param ctx     Render context
     */
    private void renderElement(
        Graphics2D g2,
        @NotNull LineElement element,
        ElementRenderContext ctx
    ) {
        var registry = RendererRegistry.getInstance();
        var renderer = registry.getRenderer(element);

        if (renderer != null) {
            // Use registered renderer
            renderer.render(element, g2, ctx);
        } else {
            // Fallback: Draw content bounds as placeholder for unregistered types
            var bounds = element.getContentBounds();
            g2.setColor(PLACEHOLDER_COLOR);
            g2.fillRect(
                (int) bounds.getX(),
                (int) bounds.getY(),
                (int) bounds.getWidth(),
                (int) bounds.getHeight()
            );
        }

        // Render children
        for (var child : element.getChildren()) {
            renderElement(g2, child, ctx);
        }
    }

    /**
     * Renders notes directly using NoteRenderer (when no LineElement tree).
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderNotesDirectly(Graphics2D g2, ElementRenderContext ctx) {
        var noteRenderer = NoteRenderer.getInstance();
        var line = lc.getLine();

        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);

            // Apply color based on selection/playing state
            var color = getNoteColor(i);
            g2.setColor(color);

            noteRenderer.render(g2, note, ctx);
        }

        // Restore default color
        g2.setColor(Color.BLACK);
    }

    /**
     * Determines the color for rendering a note based on selection and playback state.
     *
     * @param noteIndex The index of the note within this line
     * @return The color to use for rendering
     */
    private Color getNoteColor(int noteIndex) {
        if (!lc.isEditMode()) {
            return Color.BLACK;
        }

        // Check if note is currently playing
        if (lc.getPlayingNoteIndex() == noteIndex) {
            return Score.PLAYING_NOTE_COLOR;
        }

        // Check if note is selected
        var selectionProvider = lc.getSelectionProvider();

        if (selectionProvider != null
            && selectionProvider.isNoteSelected(noteIndex, lc.getLineIndex())) {
            return Score.SELECTION_STROKE_COLOR;
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
        GlissandoRenderer.getInstance().renderGlissandosFromLine(g2, lc.getLine(), ctx);
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
        var beamings = line.getBeamings();

        for (var iter = beamings.listIterator(); iter.hasNext(); ) {
            var interval = (Interval) iter.next();
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
        var ties = line.getTies();

        for (var iter = ties.listIterator(); iter.hasNext(); ) {
            var interval = (Interval) iter.next();
            var startNote = line.getNote(interval.getStart());
            var endNote = line.getNote(interval.getEnd());
            tieRenderer.renderTie(g2, startNote, endNote, ctx);
        }
    }

    /**
     * Renders tuplet brackets and numbers.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderTuplets(Graphics2D g2, ElementRenderContext ctx) {
        TupletRenderer.getInstance().renderTupletsFromLine(g2, lc.getLine(), ctx);
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

        var nextLine = composition.getLine(lineIndex + 1);

        // Delegate to KeySignatureRenderer
        KeySignatureRenderer.getInstance().renderKeyChange(
            g2,
            line,
            nextLine,
            composition.getLineWidth(),
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
        var dynamicsRenderer = DynamicsRenderer.getInstance();
        var line = lc.getLine();
        dynamicsRenderer.renderCrescendosFromLine(g2, line, ctx);
        dynamicsRenderer.renderDiminuendosFromLine(g2, line, ctx);
    }

    /**
     * Renders first/second ending brackets.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderEndings(Graphics2D g2, ElementRenderContext ctx) {
        EndingRenderer.getInstance().renderEndings(
            g2, lc.getLine(), lc.getLineIndex(), ctx
        );
    }

    // ==========================================================================
    // Note Attachment Rendering
    // ==========================================================================

    /**
     * Renders note attachments: tempo, beat change, fermata, annotations,
     * articulations, and trills.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderAttachments(Graphics2D g2, ElementRenderContext ctx) {
        var tempoRenderer = TempoRenderer.getInstance();
        var beatChangeRenderer = BeatChangeRenderer.getInstance();
        var fermataRenderer = FermataRenderer.getInstance();
        var annotationRenderer = AnnotationRenderer.getInstance();
        var articulationRenderer = ArticulationRenderer.getInstance();
        var line = lc.getLine();
        var layoutResult = lc.getLayoutResult();

        g2.setColor(Color.BLACK);

        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);

            // Tempo marking (including initial tempo on first note of first line)
            // Check layout result for TempoAttachment - layout creates one for initial tempo too
            var tempoBounds = layoutResult != null
                ? layoutResult.findAttachmentBounds(note, TempoAttachment.class)
                : null;

            if (tempoBounds != null) {
                tempoRenderer.render(note, g2, ctx);
            }

            // Beat change
            if (note.getBeatChange() != null) {
                beatChangeRenderer.render(note, g2, ctx);
            }

            // Fermata
            if (note.isFermata()) {
                fermataRenderer.render(note, g2, ctx);
            }

            // Annotation
            if (note.getAnnotation() != null) {
                annotationRenderer.render(note, g2, ctx);
            }

            // Articulations
            if (!note.getArticulations().isEmpty()) {
                articulationRenderer.render(note, g2, ctx);
            }
        }

        // Trills (rendered separately as they may span multiple notes)
        TrillRenderer.getInstance().renderTrillsFromLine(g2, line, ctx);
    }

    // ==========================================================================
    // Overlay Rendering
    // ==========================================================================

    /**
     * Renders the insertion note if this line is the current insertion line.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderInsertionNote(Graphics2D g2, ElementRenderContext ctx) {
        // Only render if this line is the current insertion line
        if (!InsertionNoteManager.hasInsertionNote(lc)) {
            return;
        }

        // Don't render insertion note when in select mode
        var score = lc.getScore();

        if (score != null && score.getMode() == Mode.SELECT) {
            return;
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager == null) {
            return;
        }

        var editNote = editModeManager.getEditNote();

        if (editNote == null) {
            return;
        }

        // Skip if edit note is not visible (e.g., in keyboard mode)
        if (!editModeManager.isEditNoteVisible()) {
            return;
        }

        // Calculate X position from insertion index
        // Pass mouse X so it can snap to note heads when mouse is over them
        double x = 0;
        double mouseX = 0;

        // Get the last mouse X position (stored when mouseMoved was called)
        var mousePos = lc.getMousePosition();

        if (mousePos != null) {
            mouseX = mousePos.getX();
        }

        var layoutResult = lc.getLayoutResult();
        var line = lc.getLine();
        var currentXIndex = InsertionNoteManager.getCurrentXIndex();
        var currentYPos = InsertionNoteManager.getCurrentYPos();
        var middleLineY = lc.getMiddleLineY();

        if (layoutResult != null && line != null) {
            x = layoutResult.calculateInsertionX(currentXIndex, mouseX, editNote, line);
        }

        // Set the edit note position
        editNote.setYPos(currentYPos);
        editNote.setUpper(Score.defaultUpperNote(editNote));

        // Handle glissando note specially
        if (editNote == Note.GLISSANDO_NOTE) {
            if (currentXIndex > 0) {
                GlissandoRenderer.getInstance().renderEditGlissando(
                    g2,
                    currentXIndex - 1,
                    new Note.Glissando(currentYPos),
                    line,
                    ctx
                );
            }
        } else {
            // Render the edit note with the edit note color.
            // Pass x as an override so NoteRenderer applies device-pixel snapping
            // to the raw double directly, exactly as it does for composition notes
            // via layoutResult.getNoteX(). Temporarily clear layoutResult to prevent
            // sub-renderers from looking up the edit note (which is not in the layout).
            var savedLayout = ctx.getLayoutResult();
            ctx.setOverrideNoteX(x);
            ctx.setLayoutResult(null);
            g2.setColor(EDIT_NOTE_COLOR);
            NoteRenderer.getInstance().render(g2, editNote, ctx);
            ctx.clearOverrideNoteX();

            // Set xPos for articulation/fermata renderers, which read it directly.
            // Simple rounding is fine since those renderers apply their own device-pixel
            // snapping internally.
            editNote.setXPos((int) Math.round(x));

            // Render articulations and fermata on the insertion note preview
            if (!editNote.getArticulations().isEmpty()) {
                ArticulationRenderer.getInstance().render(editNote, g2, ctx);
            }

            if (editNote.isFermata()) {
                FermataRenderer.getInstance().render(editNote, g2, ctx);
            }

            ctx.setLayoutResult(savedLayout);
        }
    }

    /**
     * Renders the drag rectangle during a selection drag on this line.
     *
     * @param g2 Graphics context
     */
    private void renderDragRectangle(Graphics2D g2) {
        if (!lc.isDraggingSelection()) {
            return;
        }

        var dragRectangle = lc.getDragRectangle();

        if (dragRectangle.isEmpty()) {
            return;
        }

        g2.setColor(SELECTION_RECT_FILL_COLOR);
        g2.fill(dragRectangle);

        var originalStroke = g2.getStroke();
        g2.setStroke(SELECTION_RECT_STROKE);
        g2.setColor(Score.SELECTION_STROKE_COLOR);
        g2.draw(dragRectangle);
        g2.setStroke(originalStroke);
    }
}
