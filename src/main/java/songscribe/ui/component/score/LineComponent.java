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
import songscribe.ui.layout.TempoAttachment;
import songscribe.ui.layout2.LayoutEngine;
import songscribe.ui.layout2.LayoutResult;
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

    // ==========================================================================
    // Functional Interface for Selection State
    // ==========================================================================

    /**
     * Functional interface for checking note selection state.
     * <p>
     * Allows LineComponent to check selection without coupling to Score.
     */
    @FunctionalInterface
    public interface SelectionProvider {
        /**
         * Returns whether the specified note is selected.
         *
         * @param noteIndex The note index within the line
         * @param lineIndex The line index
         * @return true if the note is selected
         */
        boolean isNoteSelected(int noteIndex, int lineIndex);
    }

    // ==========================================================================
    // Instance Fields
    // ==========================================================================

    /** The line model containing notes and other elements. */
    private Line line;

    /** Index of this line within the composition. */
    private int lineIndex;

    /** Root element of the LineElement tree for this line. */
    private LineElement rootElement;

    /** Y coordinate of the middle staff line (B line) relative to component top. */
    private int middleLineY;

    /** Provider for checking note selection state. */
    private SelectionProvider selectionProvider;

    /** Index of the currently playing note (-1 if not playing). */
    private int playingNoteIndex = -1;

    /** Whether edit mode is enabled (affects coloring). */
    private boolean editMode = true;

    /** The layout engine for calculating element positions. */
    private LayoutEngine layoutEngine;

    /** Cached layout result from the last layout pass. */
    private LayoutResult layoutResult;

    /** Whether layout needs to be recalculated. */
    private boolean layoutDirty = true;

    // ==========================================================================
    // Constants
    // ==========================================================================

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
        this.layoutDirty = true;
        this.layoutResult = null;
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
     * <p>
     * Lazily calculates the value if it hasn't been set yet.
     */
    public int getMiddleLineY() {
        if (middleLineY == 0 && composition != null) {
            middleLineY = calculateMiddleLineY();
        }

        return middleLineY;
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
     * Sets the index of the currently playing note.
     *
     * @param playingNoteIndex Note index, or -1 if not playing
     */
    public void setPlayingNoteIndex(int playingNoteIndex) {
        if (this.playingNoteIndex != playingNoteIndex) {
            this.playingNoteIndex = playingNoteIndex;
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
     * Sets whether edit mode is enabled.
     *
     * @param editMode true if edit mode is enabled
     */
    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    /**
     * Returns whether edit mode is enabled.
     */
    public boolean isEditMode() {
        return editMode;
    }

    /**
     * Marks the layout as dirty, requiring recalculation on next render.
     */
    public void invalidateLayout() {
        this.layoutDirty = true;
        this.layoutResult = null;
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
     * Performs layout calculation for this line.
     * <p>
     * This method uses the {@link LayoutEngine} to calculate bounds for all
     * elements on the line. The result is cached and can be retrieved via
     * {@link #getLayoutResult()}.
     *
     * @param g2 Graphics context for font metrics
     */
    private void performLayout(@NotNull Graphics2D g2) {
        if (composition == null || line == null) {
            return;
        }

        var lyricsFont = composition.getLyricsFont();
        var staffRightMargin = composition.getLineWidth();
        layoutEngine = new LayoutEngine(g2, lyricsFont, staffRightMargin);
        layoutResult = layoutEngine.layout(line);

        if (layoutResult == null) {
            var error = layoutEngine.getLastError();
            System.err.println("Layout failed for line " + lineIndex + ": " + error);
        }

        layoutDirty = false;
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

        // Perform layout if dirty
        if (layoutDirty || layoutResult == null) {
            performLayout(g2);

            // Recalculate middleLineY after layout to account for elements above staff
            var newMiddleLineY = calculateMiddleLineY();

            if (newMiddleLineY != middleLineY) {
                middleLineY = newMiddleLineY;
                revalidate();
            }
        }

        // Create render context for this rendering pass
        var ctx = new ElementRenderContext(composition);
        ctx.setCurrentLine(line);
        ctx.setLineIndex(lineIndex);
        ctx.setMiddleLineY(middleLineY);
        ctx.setLayoutResult(layoutResult);

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
     * Renders the line beginning (clef and key signature).
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderLineBeginning(Graphics2D g2, ElementRenderContext ctx) {
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

    /**
     * Renders glissandos (wavy ornament lines) for all notes in the line.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderGlissandos(Graphics2D g2, ElementRenderContext ctx) {
        GlissandoRenderer.getInstance().renderGlissandosFromLine(g2, line, ctx);
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
        if (!editMode) {
            return Color.BLACK;
        }

        // Check if note is currently playing
        if (playingNoteIndex == noteIndex) {
            return Score.PLAYING_NOTE_COLOR;
        }

        // Check if note is selected
        if (selectionProvider != null && selectionProvider.isNoteSelected(noteIndex, lineIndex)) {
            return Score.SELECTION_STROKE_COLOR;
        }

        return Color.BLACK;
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

    @Override
    public Dimension getPreferredSize() {
        if (composition == null) {
            return new Dimension(0, 0);
        }

        // Calculate width and height
        int width = calculateLineWidth();
        int height = calculateLineHeight();

        // Calculate middleLineY based on actual space above (which may include tempo)
        middleLineY = calculateMiddleLineY();

        return new Dimension(width, height);
    }

    /**
     * Calculates the Y position of the middle staff line.
     * <p>
     * This accounts for extra space needed above the staff for tempo markings
     * and other elements that extend above the default staff area.
     *
     * @return Y position of middle staff line in component coordinates
     */
    private int calculateMiddleLineY() {
        var staffLineYOffset = LayoutStylesheet.STAFF_LINE_Y_OFFSET;
        var defaultSpaceAbove = Score.STAFF_LINES_ABOVE * staffLineYOffset;
        double spaceAbove = defaultSpaceAbove;

        // Get extent of notes and attachments (only if line has content)
        if (line != null && !line.isEmpty()) {
            double tempMiddleLineY = defaultSpaceAbove + (2.0 * staffLineYOffset);
            var extent = CollisionDetector.calculateNoteExtent(line, tempMiddleLineY);
            spaceAbove = Math.max(defaultSpaceAbove, Math.abs(extent.getMinY()));
        }

        // Use layout result to determine space needed above staff
        // Layout result positions are relative to middleLineY=0, so negative Y means above staff
        if (layoutResult != null) {
            // Find the minimum (topmost) Y position from layout result
            // We need to scan all elements since we don't have a method to get all bounds
            // For now, check if first note has tempo attachment
            if (line != null && line.noteCount() > 0) {
                var firstNote = line.getNote(0);
                var tempoBounds = layoutResult.findAttachmentBounds(firstNote, TempoAttachment.class);

                if (tempoBounds != null) {
                    double tempoTop = tempoBounds.getTop();

                    if (tempoTop < 0) {
                        // Element extends above middleLineY, need extra space
                        double tempoSpaceNeeded = Math.abs(tempoTop);
                        spaceAbove = Math.max(spaceAbove, tempoSpaceNeeded);
                    }
                }
            }
        }

        var result = (int) spaceAbove + (2 * staffLineYOffset);

        // Middle line is 2 staff line offsets below the top staff line
        return result;
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
        if (line == null || line.isEmpty() || layoutResult == null) {
            return composition.getLineWidth();
        }

        // Use the greater of composition width or calculated width from layout
        double calculatedWidth = layoutResult.getLineWidth();

        return (int) Math.max(composition.getLineWidth(), Math.ceil(calculatedWidth));
    }

    /**
     * Calculates the height needed for this line.
     * <p>
     * Uses CollisionDetector to measure the extent of notes and attachments
     * above and below the staff. Also accounts for tempo markings on the
     * first line.
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

        double spaceAbove = defaultSpaceAbove;
        double spaceBelow = defaultSpaceBelow;

        // Get extent of notes and attachments (only if line has content)
        if (line != null && !line.isEmpty()) {
            double tempMiddleLineY = defaultSpaceAbove + (2.0 * staffLineYOffset);
            var extent = CollisionDetector.calculateNoteExtent(line, tempMiddleLineY);
            spaceAbove = Math.max(defaultSpaceAbove, Math.abs(extent.getMinY()));
            spaceBelow = Math.max(
                defaultSpaceBelow,
                extent.getMaxY() - (staffHeight / 2.0)
            );
        }

        // Account for tempo marking on first line (even if line is empty)
        if (lineIndex == 0 && hasTempo()) {
            // Tempo is rendered at middleLineY + tempoYOffset (where tempoYOffset is negative)
            // The tempo includes a note symbol and text, which extend above the baseline
            // Typical tempo content height above baseline: ~25 pixels
            var tempoChangeYPos = (line != null) ? line.getTempoChangeYPos() : 0;
            var tempoYOffset = (int) (-7 * LayoutStylesheet.NOTE_Y_OFFSET) + tempoChangeYPos;
            // Calculate space needed: |tempoYOffset| + contentHeight - distanceFromTopToMiddle
            // distanceFromTopToMiddle = 2*staffLineYOffset (top staff line to middle line)
            var tempoContentHeight = 25;  // Note symbol + text ascent
            var tempoSpaceAbove = Math.abs(tempoYOffset) + tempoContentHeight - (2 * staffLineYOffset);
            spaceAbove = Math.max(spaceAbove, tempoSpaceAbove);
        }

        return (int) Math.ceil(spaceAbove + staffHeight + spaceBelow);
    }

    /**
     * Returns whether this line has a tempo marking to display.
     * <p>
     * For line 0, returns true if the composition has an initial tempo,
     * even if the line is empty. This ensures proper space allocation
     * before notes are added.
     */
    private boolean hasTempo() {
        // Check for initial tempo on first line (even if empty)
        if (lineIndex == 0 && composition.getTempo() != null) {
            return true;
        }

        // Check for tempo change on any note in this line
        if (line != null) {
            for (var i = 0; i < line.noteCount(); i++) {
                if (line.getNote(i).getTempoChange() != null) {
                    return true;
                }
            }
        }

        return false;
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
