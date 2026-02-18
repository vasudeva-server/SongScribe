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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.data.Interval;
import songscribe.music.Line;
import songscribe.ui.component.Score;
import songscribe.ui.layout.CollisionDetector;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.LineElement;
import songscribe.ui.layout.XPositionCalculator;
import songscribe.ui.renderer.AnnotationRenderer;
import songscribe.ui.renderer.ArticulationRenderer;
import songscribe.ui.renderer.BeamGroupRenderer;
import songscribe.ui.renderer.BeatChangeRenderer;
import songscribe.ui.renderer.DynamicsRenderer;
import songscribe.ui.renderer.ElementRenderContext;
import songscribe.ui.renderer.EndingRenderer;
import songscribe.ui.renderer.FermataRenderer;
import songscribe.ui.renderer.NoteRenderer;
import songscribe.ui.renderer.RendererRegistry;
import songscribe.ui.renderer.TempoRenderer;
import songscribe.ui.renderer.TieRenderer;
import songscribe.ui.renderer.TrillRenderer;
import songscribe.ui.renderer.TupletRenderer;

/**
 * Component that renders a single staff line with its musical content.
 * <p>
 * This is the core rendering component that:
 * <ul>
 *   <li>Draws the 5-line staff</li>
 *   <li>Traverses the {@link LineElement} tree for element rendering</li>
 *   <li>Uses {@link RendererRegistry} for modular element rendering</li>
 *   <li>Provides hit testing via {@link #findElementAt(Point)}</li>
 * </ul>
 * <p>
 * Phase 6 implementation uses modular renderers via the Strategy pattern.
 * Notes are rendered as filled circles (stub); full rendering will be
 * added incrementally in later phases.
 */
public class LineComponent extends ScoreComponent {

    /** The line model containing notes and other elements. */
    private Line line;

    /** Index of this line within the composition. */
    private int lineIndex;

    /** Root element of the LineElement tree for this line. */
    private LineElement rootElement;

    /** Y coordinate of the middle staff line (B line) relative to component top. */
    private int middleLineY;

    /** Color for staff lines. */
    private static final Color STAFF_LINE_COLOR = Color.BLACK;

    /** Color for placeholder rectangles (for unregistered element types). */
    private static final Color PLACEHOLDER_COLOR = new Color(100, 100, 100, 128);

    /**
     * Creates a new LineComponent.
     */
    public LineComponent() {
        super();
    }

    /**
     * Sets the line to render.
     *
     * @param line      The line model
     * @param lineIndex Index of the line in the composition
     */
    public void setLine(@NotNull Line line, int lineIndex) {
        this.line = line;
        this.lineIndex = lineIndex;
        this.rootElement = null;
        revalidate();
        repaint();
    }

    /**
     * Returns the line model.
     */
    public Line getLine() {
        return line;
    }

    /**
     * Returns the line index.
     */
    public int getLineIndex() {
        return lineIndex;
    }

    /**
     * Sets the root element of the LineElement tree.
     *
     * @param rootElement The root element
     */
    public void setRootElement(@Nullable LineElement rootElement) {
        this.rootElement = rootElement;
        repaint();
    }

    /**
     * Returns the root element of the LineElement tree.
     */
    @Nullable
    public LineElement getRootElement() {
        return rootElement;
    }

    /**
     * Sets the Y coordinate of the middle staff line.
     *
     * @param middleLineY Y coordinate relative to component top
     */
    public void setMiddleLineY(int middleLineY) {
        this.middleLineY = middleLineY;
    }

    /**
     * Returns the Y coordinate of the middle staff line.
     */
    public int getMiddleLineY() {
        return middleLineY;
    }

    /**
     * Finds the LineElement at the given point.
     * <p>
     * Traverses the element tree recursively, returning the deepest element
     * that contains the point.
     *
     * @param point Point in component coordinates
     * @return The element at the point, or null if none found
     */
    @Nullable
    public LineElement findElementAt(@NotNull Point point) {
        if (rootElement == null) {
            return null;
        }

        return findElementAtRecursive(rootElement, point.getX(), point.getY());
    }

    /**
     * Recursively finds an element containing the point.
     */
    @Nullable
    private LineElement findElementAtRecursive(
        @NotNull LineElement element,
        double x,
        double y
    ) {
        // Check children first (deepest match wins)
        for (var child : element.getChildren()) {
            var found = findElementAtRecursive(child, x, y);

            if (found != null) {
                return found;
            }
        }

        // Then check this element
        if (element.containsPoint(x, y)) {
            return element;
        }

        return null;
    }

    @Override
    protected void render(Graphics2D g2) {
        if (composition == null || line == null) {
            return;
        }

        // Create render context for this rendering pass
        var ctx = new ElementRenderContext(composition);
        ctx.setCurrentLine(line);
        ctx.setLineIndex(lineIndex);
        ctx.setMiddleLineY(middleLineY);

        // Render in proper order (back to front)
        drawStaffLines(g2);
        renderNotes(g2, ctx);
        renderBeams(g2, ctx);
        renderTies(g2, ctx);
        renderTuplets(g2, ctx);
        renderDynamics(g2, ctx);
        renderEndings(g2, ctx);
        renderAttachments(g2, ctx);
    }

    /**
     * Draws the 5 staff lines.
     */
    private void drawStaffLines(Graphics2D g2) {
        g2.setColor(STAFF_LINE_COLOR);

        var lineWidth = composition.getLineWidth();
        var staffLineYOffset = LayoutStylesheet.STAFF_LINE_Y_OFFSET;

        // Staff has 5 lines, middle line (B) is at index 2
        // Lines are at: middleLineY - 2*offset, middleLineY - offset, middleLineY,
        //               middleLineY + offset, middleLineY + 2*offset
        for (var i = -2; i <= 2; i++) {
            var y = middleLineY + (i * staffLineYOffset);
            g2.drawLine(0, y, lineWidth, y);
        }
    }

    /**
     * Renders notes using the modular renderer system.
     *
     * @param g2  Graphics context
     * @param ctx Element render context
     */
    private void renderNotes(Graphics2D g2, ElementRenderContext ctx) {
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

        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);
            noteRenderer.render(g2, note, ctx);
        }
    }

    // ==========================================================================
    // Range Element Rendering (IntervalSet Bridge)
    // ==========================================================================

    /**
     * Renders beam groups connecting beamed notes.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderBeams(Graphics2D g2, ElementRenderContext ctx) {
        var beamRenderer = BeamGroupRenderer.getInstance();
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
        TupletRenderer.getInstance().renderTupletsFromLine(g2, line, ctx);
    }

    /**
     * Renders crescendo and diminuendo hairpins.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderDynamics(Graphics2D g2, ElementRenderContext ctx) {
        var dynamicsRenderer = DynamicsRenderer.getInstance();
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
        EndingRenderer.getInstance().renderEndings(g2, line, lineIndex, ctx);
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

        for (var i = 0; i < line.noteCount(); i++) {
            var note = line.getNote(i);

            // Tempo marking
            if (note.getTempoChange() != null) {
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

    @Override
    public Dimension getPreferredSize() {
        if (composition == null) {
            return new Dimension(0, 0);
        }

        // Calculate width and height
        int width = calculateLineWidth();
        int height = calculateLineHeight();

        // Middle line Y is at spaceAbove + 2*staffLineYOffset from top
        var staffLineYOffset = LayoutStylesheet.STAFF_LINE_Y_OFFSET;
        var spaceAbove = Score.STAFF_LINES_ABOVE * staffLineYOffset;
        middleLineY = spaceAbove + (2 * staffLineYOffset);

        return new Dimension(width, height);
    }

    /**
     * Calculates the width needed for this line.
     * <p>
     * Uses the composition's line width, or calculates from note positions
     * if the line has notes.
     *
     * @return Width in pixels
     */
    private int calculateLineWidth() {
        if (line == null || line.isEmpty()) {
            return composition.getLineWidth();
        }

        // Use the greater of composition width or calculated width from notes
        double calculatedWidth = XPositionCalculator.calculateLineWidth(line);

        return (int) Math.max(composition.getLineWidth(), Math.ceil(calculatedWidth));
    }

    /**
     * Calculates the height needed for this line.
     * <p>
     * Uses CollisionDetector to measure the extent of notes and attachments
     * above and below the staff.
     *
     * @return Height in pixels
     */
    private int calculateLineHeight() {
        var staffLineYOffset = LayoutStylesheet.STAFF_LINE_Y_OFFSET;

        // Default space above and below staff for ledger lines
        var defaultSpaceAbove = Score.STAFF_LINES_ABOVE * staffLineYOffset;
        var defaultSpaceBelow = Score.STAFF_LINES_BELOW * staffLineYOffset;

        // Staff height: 4 gaps between 5 lines
        var staffHeight = LayoutStylesheet.STAFF_HEIGHT;

        if (line == null || line.isEmpty()) {
            return defaultSpaceAbove + staffHeight + defaultSpaceBelow;
        }

        // Calculate middle line Y position for extent calculation
        double tempMiddleLineY = defaultSpaceAbove + (2.0 * staffLineYOffset);

        // Get extent of notes and attachments
        var extent = CollisionDetector.calculateNoteExtent(line, tempMiddleLineY);

        // Calculate actual space needed above and below
        double spaceAbove = Math.max(defaultSpaceAbove, Math.abs(extent.getMinY()));
        double spaceBelow = Math.max(
            defaultSpaceBelow,
            extent.getMaxY() - (staffHeight / 2.0)
        );

        return (int) Math.ceil(spaceAbove + staffHeight + spaceBelow);
    }

    @Override
    protected void renderDebug(Graphics2D g2) {
        super.renderDebug(g2);

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
    }
}
