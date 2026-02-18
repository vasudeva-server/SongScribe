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
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Rectangle2D;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.data.Interval;
import songscribe.music.BeamCalculator;
import songscribe.music.Crotchet;
import songscribe.music.Line;
import songscribe.music.Note;
import songscribe.music.NoteType;
import net.engio.mbassy.listener.Handler;

import songscribe.ui.action.Actions;
import songscribe.ui.component.Score;
import songscribe.ui.Control;
import songscribe.ui.Mode;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.layout2.InsertionSpacingCalculator;
import songscribe.ui.message.MessageCenter;
import songscribe.ui.message.ModeChangedMessage;
import songscribe.ui.playback.MidiController;
import songscribe.ui.playback.PlayNoteThread;
import songscribe.ui.selection.LineSelectionState;
import songscribe.ui.selection.SelectionCoordinator;
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
public class LineComponent extends ScoreComponent
    implements MouseMotionListener, MouseListener {

    // ==========================================================================
    // Functional Interface for Selection State
    // ==========================================================================

    /**
     * Interface for checking selection state.
     * <p>
     * Allows LineComponent to check selection without coupling to Score.
     */
    public interface SelectionProvider {
        /**
         * Returns whether the specified note is selected.
         *
         * @param noteIndex The note index within the line
         * @param lineIndex The line index
         * @return true if the note is selected
         */
        boolean isNoteSelected(int noteIndex, int lineIndex);

        /**
         * Returns whether the staff line itself is selected (for deletion).
         *
         * @param lineIndex The line index
         * @return true if the staff line is selected
         */
        boolean isLineSelected(int lineIndex);
    }

    // ==========================================================================
    // Instance Fields
    // ==========================================================================

    /** The line model containing notes and other elements. */
    private Line line;

    /** Index of this line within the composition. */
    private int lineIndex;

    /** Per-line selection state. */
    private LineSelectionState lineSelectionState;

    /** Root element of the LineElement tree for this line. */
    private LineElement rootElement;

    /** Y coordinate of the middle staff line (B line) relative to component top. */
    private int middleLineY;

    /** Provider for checking note selection state. */
    private SelectionProvider selectionProvider;

    /** Reference to the Score for accessing composition and services. */
    private songscribe.ui.component.Score score;

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

    /** Whether a selection drag is in progress on this line. */
    private boolean draggingSelection = false;

    /** The point where a selection drag started (component-local coordinates). */
    private final Point dragStart = new Point();

    /** The current drag rectangle (component-local coordinates). */
    private final Rectangle dragRectangle = new Rectangle();

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

    /** Number of ledger lines above the staff. */
    private static final int STAFF_LINES_ABOVE = 3;

    /** Number of ledger lines below the staff. */
    private static final int STAFF_LINES_BELOW = 4;

    /** Number of lines in a staff. */
    private static final int STAFF_LINE_COUNT = 5;

    /** Crosshair cursor for selection mode. */
    private static final Cursor CROSSHAIR_CURSOR = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);

    /** Default cursor. */
    private static final Cursor DEFAULT_CURSOR = Cursor.getDefaultCursor();

    // ==========================================================================
    // Static State for Insertion Note Tracking
    // ==========================================================================

    /**
     * The LineComponent that currently has the insertion note.
     * <p>
     * Only one line can show the insertion note at a time. When the mouse moves
     * to a different line, the old line is repainted to clear the insertion note.
     */
    @Nullable
    private static LineComponent currentInsertionLine = null;

    /** Current insertion index (0 to noteCount inclusive). */
    private static int currentXIndex = -1;

    /** Current Y position on the staff (in note units, not pixels). */
    private static int currentYPos = 0;

    /** Whether the Alt key is currently held down. */
    private static boolean altPressed = false;

    /** The LineComponent the mouse is currently over (independent of insertion note state). */
    @Nullable
    private static LineComponent currentMouseLine = null;

    /** Whether the current insertion position is directly over an existing note head. */
    private static boolean currentIsOverNoteHead = false;

    /** Strong reference to prevent garbage collection by the weak-reference message bus. */
    private static final ModeChangeListener MODE_CHANGE_LISTENER = new ModeChangeListener();

    // Subscribe the static listener for mode change messages
    static {
        MessageCenter.subscribe(MODE_CHANGE_LISTENER);
    }

    /**
     * Static listener that receives mode change messages and updates cursor state.
     */
    private static class ModeChangeListener {
        @Handler
        public void modeDidChange(ModeChangedMessage message) {
            onModeChanged();
        }
    }

    /**
     * Creates a new LineComponent.
     */
    public LineComponent() {
        super();
        addMouseMotionListener(this);
        addMouseListener(this);
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
        this.lineSelectionState = new LineSelectionState(line);
        this.rootElement = null;
        this.layoutDirty = true;
        this.layoutResult = null;

        // Register with coordinator if score is available
        if (score != null) {
            var coordinator = score.getSelectionCoordinator();

            if (coordinator != null) {
                coordinator.registerLineState(lineIndex, lineSelectionState);
            }
        }

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
     * Returns the per-line selection state.
     */
    @Nullable
    public LineSelectionState getLineSelectionState() {
        return lineSelectionState;
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
     * Sets the Score reference for accessing composition and services.
     *
     * @param score The Score component
     */
    public void setScore(@Nullable songscribe.ui.component.Score score) {
        this.score = score;

        // Register LineSelectionState with coordinator when score is set
        if (score != null && lineSelectionState != null) {
            var coordinator = score.getSelectionCoordinator();

            if (coordinator != null) {
                coordinator.registerLineState(lineIndex, lineSelectionState);
            }
        }
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
        renderInsertionNote(g2, ctx);
        renderDragRectangle(g2);
    }

    /**
     * Draws the 5 staff lines.
     */
    private void drawStaffLines(Graphics2D g2) {
        var staffSelected = editMode
            && selectionProvider != null
            && selectionProvider.isLineSelected(lineIndex);

        g2.setColor(staffSelected ? Score.SELECTION_STROKE_COLOR : STAFF_LINE_COLOR);

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
    // Insertion Note Handling
    // ==========================================================================

    /**
     * Clears the insertion note from all lines.
     * <p>
     * Call this when exiting edit mode or when the mouse leaves the score area.
     */
    public static void clearInsertionNote() {
        if (currentInsertionLine != null) {
            var oldLine = currentInsertionLine;
            currentInsertionLine = null;
            currentXIndex = -1;
            currentYPos = 0;
            currentIsOverNoteHead = false;
            oldLine.repaint();
        }
    }

    /**
     * Sets whether the Alt key is currently pressed and updates the cursor.
     *
     * @param pressed true if Alt is pressed
     */
    public static void setAltPressed(boolean pressed) {
        altPressed = pressed;
        updateCursor();

        // When Alt is released, re-trigger insertion note from current mouse position
        if (!pressed && currentMouseLine != null) {
            currentMouseLine.restoreInsertionNote();
        }
    }

    /**
     * Called when the score mode changes. Updates cursor and restores insertion
     * note if switching back to NOTE_EDIT mode.
     */
    public static void onModeChanged() {
        updateCursor();

        if (currentMouseLine != null) {
            currentMouseLine.restoreInsertionNote();
        }
    }

    /**
     * Restores the insertion note from the current mouse position.
     * Called when Alt is released to immediately show the insertion note
     * without requiring mouse movement.
     */
    private void restoreInsertionNote() {
        if (!shouldHandleInsertionNote()) {
            return;
        }

        var mousePos = getMousePosition();

        if (mousePos == null) {
            return;
        }

        // Ensure edit note is visible (it may have been hidden if the mouse
        // entered while in a non-edit mode like SELECT)
        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setEditNoteVisible(true);
        }

        // Synthesize a MouseEvent and delegate to mouseMoved
        var syntheticEvent = new MouseEvent(
            this, MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(), 0,
            mousePos.x, mousePos.y, 0, false
        );
        mouseMoved(syntheticEvent);
    }

    /**
     * Updates the cursor on the current insertion line based on Alt/select state.
     */
    private static void updateCursor() {
        if (currentMouseLine == null) {
            return;
        }

        var score = currentMouseLine.score;
        var isSelectMode = score != null && score.getMode() == Mode.SELECT;
        var shouldCrosshair = isSelectMode || altPressed;
        var cursor = shouldCrosshair ? CROSSHAIR_CURSOR : DEFAULT_CURSOR;
        currentMouseLine.setCursor(cursor);
    }

    /**
     * Returns the current insertion line, or null if no insertion note is active.
     */
    @Nullable
    public static LineComponent getCurrentInsertionLine() {
        return currentInsertionLine;
    }

    /**
     * Returns the current insertion X index.
     */
    public static int getCurrentXIndex() {
        return currentXIndex;
    }

    /**
     * Returns the current insertion Y position.
     */
    public static int getCurrentYPos() {
        return currentYPos;
    }

    /**
     * Returns whether this line currently has the insertion note.
     */
    public boolean hasInsertionNote() {
        return currentInsertionLine == this;
    }

    /**
     * Adds an edit note to the end of the line.
     *
     * @param line The line to add the note to
     */
    public void addEditNote(@NotNull Line line) {
        if (score == null) {
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

        if (editModeManager.noteWasModified(line, line.noteCount())) {
            editModeManager.editNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        editNote.setXPos((int) Math.round(
            InsertionSpacingCalculator.calculateAppendPosition(line, editNote)));
        line.addNote(editNote);

        // Decide automatic beaming
        if (
            editNote.getNoteType().isBeamable() &&
                (line.noteCount() >= 2) &&
                (line.getTuplets().findInterval(line.noteCount() - 2) == null)
        ) {
            var sum = 0;

            for (var i = line.noteCount() - 2; i >= 0; i--) {
                if (line.getNote(i).getNoteType() == NoteType.QUAVER) {
                    sum += 2;
                } else if (
                    (line.getNote(i).getNoteType() == NoteType.SEMIQUAVER) ||
                        (line.getNote(i).getNoteType() == NoteType.DEMI_SEMIQUAVER)
                ) {
                    sum += 1;
                } else {
                    break;
                }

                var interval = line.getBeamings().findInterval(i);

                if ((interval != null) && (interval.getStart() == i)) {
                    break;
                }
            }

            if (
                ((editNote.getNoteType() == NoteType.QUAVER) &&
                    (sum > 0) &&
                    ((sum % 2) == 0) &&
                    ((sum % 4) != 0)) ||
                    (((editNote.getNoteType() == NoteType.SEMIQUAVER) ||
                        (editNote.getNoteType() == NoteType.DEMI_SEMIQUAVER)) &&
                        (sum > 0) &&
                        ((sum % 4) != 0))
            ) {
                line
                    .getBeamings()
                    .addInterval(line.noteCount() - 2, line.noteCount() - 1);
            }

            BeamCalculator.calculateLengthenings(line.noteCount() - 1, line, true);
        }

        editModeManager.editNoteDidChange(line, line.noteCount() - 1);
    }

    /**
     * Inserts an edit note at the specified index in the line.
     *
     * @param xIndex The index to insert at
     * @param line   The line to insert into
     */
    public void insertEditNote(int xIndex, @NotNull Line line) {
        if (score == null) {
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

        if (editModeManager.noteWasModified(line, xIndex)) {
            editModeManager.editNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        // If the user tries to insert into triplet, they will get an error message
        var iv = line.getTuplets().findInterval(xIndex - 1);

        if ((iv != null) && ((xIndex - 1) < iv.getEnd())) {
            score.getMainFrame().showErrorMessage("Cannot insert into a triplet.");
            return;
        }

        line.removeInterval(xIndex - 1, xIndex);
        var insertion = InsertionSpacingCalculator.calculateInsertion(line, editNote, xIndex);
        editNote.setXPos((int) Math.round(insertion.insertedNoteX()));
        line.addNote(xIndex, editNote);
        var shift = (int) Math.round(insertion.shiftForSubsequentNotes());

        for (var i = xIndex + 1; i < line.noteCount(); i++) {
            line.getNote(i).setXPos(line.getNote(i).getXPos() + shift);
        }

        editModeManager.editNoteDidChange(line, xIndex);
    }

    /**
     * Modifies an existing note's pitch by changing its Y position.
     * Called when the user clicks directly on an existing note head.
     *
     * @param noteIndex The index of the note to modify
     * @param line      The line containing the note
     */
    public void modifyExistingNote(int noteIndex, @NotNull Line line) {
        if (score == null) {
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

        if (editModeManager.noteWasModified(line, noteIndex)) {
            editModeManager.editNoteDidChange(line, line.noteCount() - 1);
            return;
        }

        var existingNote = line.getNote(noteIndex);
        existingNote.setYPos(currentYPos);
        existingNote.setUpper(Score.defaultUpperNote(existingNote));

        editModeManager.editNoteDidChange(line, noteIndex);
    }

    // ==========================================================================
    // Selection Handling
    // ==========================================================================

    /**
     * Returns whether selection handling should be active for the given event.
     * <p>
     * Selection is active when:
     * <ul>
     *   <li>Score is available and not in an adjustment mode</li>
     *   <li>Not playing back</li>
     *   <li>In SELECT mode (alt-click switches to SELECT mode permanently)</li>
     * </ul>
     *
     * @param e The mouse event (alt key check retained for backwards compatibility)
     * @return true if selection handling should be active
     */
    private boolean isSelectionActive(@NotNull MouseEvent e) {
        if (score == null || line == null) {
            return false;
        }

        if (MidiController.isPlaying()) {
            return false;
        }

        var mode = score.getMode();

        if (mode == Mode.NOTE_ADJUSTMENT || mode == Mode.VERTICAL_ADJUSTMENT
                || mode == Mode.LYRICS_ADJUSTMENT) {
            return false;
        }

        return mode == Mode.SELECT || e.isAltDown();
    }

    /**
     * Performs hit testing for a single click point against notes in this line.
     * Updates the selection manager with the result.
     *
     * @param clickPoint The click point in component-local coordinates
     */
    private void calculateLineSelectionFromClick(@NotNull Point clickPoint) {
        var coordinator = score.getSelectionCoordinator();
        coordinator.activateLine(lineIndex);
        lineSelectionState.clearSelection();

        var helper = new Rectangle();

        for (var noteIndex = 0; noteIndex < line.noteCount(); noteIndex++) {
            var note = line.getNote(noteIndex);
            buildNoteHitRect(note, noteIndex, helper);

            if (helper.contains(clickPoint)) {
                lineSelectionState.setSelectionFromClick(noteIndex);
                return;
            }
        }

        // No note was hit — check proximity to staff lines for line selection
        if (Math.abs(clickPoint.y - middleLineY) <= 2 * Score.STAFF_LINE_Y_OFFSET) {
            lineSelectionState.setLineSelected(true);
        }
    }

    /**
     * Performs hit testing for a drag rectangle against notes in this line.
     * Updates the selection manager with all notes that intersect the rectangle.
     *
     * @param dragRect The drag rectangle in component-local coordinates
     */
    private void calculateLineSelectionFromDrag(@NotNull Rectangle dragRect) {
        var coordinator = score.getSelectionCoordinator();
        coordinator.activateLine(lineIndex);
        lineSelectionState.clearSelection();

        var helper = new Rectangle();

        for (var noteIndex = 0; noteIndex < line.noteCount(); noteIndex++) {
            var note = line.getNote(noteIndex);
            buildNoteHitRect(note, noteIndex, helper);

            if (dragRect.intersects(helper)) {
                lineSelectionState.extendSelection(noteIndex);
            }
        }
    }

    /**
     * Builds the hit-test rectangle for a note in component-local coordinates.
     *
     * @param note      The note
     * @param noteIndex The index of the note within the line
     * @param out       The rectangle to populate with the hit-test bounds
     */
    private void buildNoteHitRect(@NotNull Note note, int noteIndex, @NotNull Rectangle out) {
        if (line.getBeamings().findInterval(noteIndex) != null) {
            out.setBounds(
                note.isUpper() ? Crotchet.REAL_UP_NOTE_RECT : Crotchet.REAL_DOWN_NOTE_RECT
            );
        } else {
            out.setBounds(
                note.isUpper() ? note.getRealUpNoteRect() : note.getRealDownNoteRect()
            );
        }

        var noteY = middleLineY + (int) (note.getYPos() * Score.NOTE_Y_OFFSET);
        out.translate(note.getXPos(), noteY - Note.HOT_SPOT.y);
    }

    // ==========================================================================
    // Mouse Event Handlers
    // ==========================================================================

    @Override
    public void mouseMoved(MouseEvent e) {
        if (e.isAltDown()) {
            clearInsertionNote();
            return;
        }

        if (!shouldHandleInsertionNote()) {
            return;
        }

        // Calculate Y position from mouse
        int yPos = calculateYPosFromMouse(e.getY());

        if (!isValidYPos(yPos)) {
            // Mouse is outside valid range, clear insertion note if on this line
            if (currentInsertionLine == this) {
                clearInsertionNote();
            }

            return;
        }

        // Calculate X index from mouse using layout result
        int xIndex = 0;
        boolean isOverNoteHead = false;

        if (layoutResult != null && line != null) {
            xIndex = layoutResult.findInsertionIndex(e.getX(), line);
            isOverNoteHead = layoutResult.isMouseOverNoteHead(e.getX(), line);
        }

        // Check if position actually changed
        if (this == currentInsertionLine && xIndex == currentXIndex
                && yPos == currentYPos && isOverNoteHead == currentIsOverNoteHead) {
            return;  // No change, no repaint
        }

        // Repaint old line if different
        if (currentInsertionLine != null && currentInsertionLine != this) {
            currentInsertionLine.repaint();
        }

        // Update static state
        currentInsertionLine = this;
        currentXIndex = xIndex;
        currentYPos = yPos;
        currentIsOverNoteHead = isOverNoteHead;

        // Update the edit note's Y position
        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            var editNote = editModeManager.getEditNote();

            if (editNote != null) {
                editNote.setYPos(yPos);
            }
        }

        // Repaint this line
        repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!isSelectionActive(e)) {
            return;
        }

        draggingSelection = true;

        // Clamp coordinates to component bounds
        var x = Math.max(0, Math.min(e.getX(), getWidth() - 1));
        var y = Math.max(0, Math.min(e.getY(), getHeight() - 1));

        dragRectangle.setBounds(
            Math.min(dragStart.x, x),
            Math.min(dragStart.y, y),
            Math.abs(dragStart.x - x),
            Math.abs(dragStart.y - y)
        );

        calculateLineSelectionFromDrag(dragRectangle);
        repaint();
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        // If Alt is pressed, fully switch to Select mode
        if (e.isAltDown() && score != null && score.getMode() != Mode.SELECT) {
            Actions.SELECT_MODE_ACTION.perform(this);
        }

        // Handle selection (select mode or alt-click)
        if (isSelectionActive(e)) {
            score.clearSelection();

            if (MidiController.sequencer != null) {
                MidiController.sequencer.setTickPosition(0);
            }

            calculateLineSelectionFromClick(e.getPoint());
            score.selectionChanged();

            // Play single selected note
            if (lineSelectionState.getSelectionSize() == 1) {
                var note = line.getNote(lineSelectionState.getSelectionBegin());

                if (note.getNoteType().isNote()) {
                    new PlayNoteThread(note.getPitch()).start();
                }
            }

            repaint();
            return;
        }

        // Handle insertion note
        if (!shouldHandleInsertionNote()) {
            return;
        }

        // Only handle if this line has the insertion note
        if (currentInsertionLine != this || line == null) {
            return;
        }

        // Determine action based on position
        if (currentXIndex == line.noteCount()) {
            addEditNote(line);
        } else if (currentIsOverNoteHead) {
            modifyExistingNote(currentXIndex, line);
        } else {
            insertEditNote(currentXIndex, line);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        // If Alt is pressed, fully switch to Select mode
        if (e.isAltDown() && score != null && score.getMode() != Mode.SELECT) {
            Actions.SELECT_MODE_ACTION.perform(this);
        }

        if (isSelectionActive(e)) {
            draggingSelection = false;
            dragStart.setLocation(e.getPoint());
            dragRectangle.setBounds(0, 0, 0, 0);
            score.clearSelection();

            if (MidiController.sequencer != null) {
                MidiController.sequencer.setTickPosition(0);
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (draggingSelection) {
            draggingSelection = false;
            dragRectangle.setBounds(0, 0, 0, 0);
            score.selectionChanged();
            repaint();
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        currentMouseLine = this;
        updateCursor();

        var editModeManager = EditModeManager.getInstance();

        if (shouldHandleInsertionNote() && editModeManager != null) {
            editModeManager.setEditNoteVisible(true);
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        currentMouseLine = null;
        setCursor(DEFAULT_CURSOR);

        // Clear insertion note when mouse leaves this line
        if (currentInsertionLine == this) {
            clearInsertionNote();
        }

        var editModeManager = EditModeManager.getInstance();

        if (editModeManager != null) {
            editModeManager.setEditNoteVisible(false);
        }
    }

    /**
     * Returns whether insertion note handling should be active.
     * <p>
     * Requires: edit mode enabled, MOUSE control, NOTE_EDIT mode, and an edit note set.
     */
    private boolean shouldHandleInsertionNote() {
        var editModeManager = EditModeManager.getInstance();

        if (!editMode || editModeManager == null || !editModeManager.hasEditNote()) {
            return false;
        }

        if (score == null) {
            return false;
        }

        return score.getControl() == Control.MOUSE && score.getMode() == Mode.NOTE_EDIT;
    }

    /**
     * Calculates the Y position (in note units) from a mouse Y coordinate.
     * <p>
     * The Y position is relative to the middle staff line, with positive values
     * going down (lower pitches) and negative values going up (higher pitches).
     *
     * @param mouseY Mouse Y coordinate in component coordinates
     * @return Y position in note units
     */
    private int calculateYPosFromMouse(int mouseY) {
        var noteYOffset = LayoutStylesheet.NOTE_Y_OFFSET;
        return (int) Math.round((mouseY - middleLineY) / noteYOffset);
    }

    /**
     * Returns whether the given Y position is within the valid range for notes.
     * <p>
     * Valid range extends from STAFF_LINES_ABOVE ledger lines above the staff
     * to STAFF_LINES_BELOW ledger lines below the staff.
     *
     * @param yPos Y position in note units
     * @return true if the position is valid
     */
    private boolean isValidYPos(int yPos) {
        // Valid range: -(STAFF_LINES_ABOVE + 2) * 2 to (STAFF_LINES_BELOW + 2) * 2
        // This is slightly more permissive than the strict ledger line range
        var minY = -(STAFF_LINES_ABOVE + 2) * 2;
        var maxY = (STAFF_LINES_BELOW + 2) * 2;
        return yPos >= minY && yPos <= maxY;
    }

    /**
     * Renders the insertion note if this line is the current insertion line.
     *
     * @param g2  Graphics context
     * @param ctx Render context
     */
    private void renderInsertionNote(Graphics2D g2, ElementRenderContext ctx) {
        // Only render if this line is the current insertion line
        if (currentInsertionLine != this) {
            return;
        }

        // Don't render insertion note when in select mode
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
        var mouseEvent = getMousePosition();
        if (mouseEvent != null) {
            mouseX = mouseEvent.getX();
        }

        if (layoutResult != null && line != null) {
            x = layoutResult.calculateInsertionX(currentXIndex, mouseX, editNote, line);
        }

        // Set the edit note position
        editNote.setXPos((int) x);
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
            // Render the edit note with the edit note color
            g2.setColor(EDIT_NOTE_COLOR);
            NoteRenderer.getInstance().render(g2, editNote, middleLineY);
        }
    }

    /**
     * Renders the drag rectangle during a selection drag on this line.
     *
     * @param g2 Graphics context
     */
    private void renderDragRectangle(Graphics2D g2) {
        if (!draggingSelection || dragRectangle.isEmpty()) {
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
