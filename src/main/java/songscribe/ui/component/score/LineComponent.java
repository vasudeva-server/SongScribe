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
import java.awt.event.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import songscribe.music.Line;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.component.Score;
import songscribe.ui.layout.CollisionDetector;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.LineElement;
import songscribe.ui.layout.TempoAttachment;
import songscribe.ui.layout2.LayoutEngine;
import songscribe.ui.layout2.LayoutResult;
import songscribe.ui.renderer.RendererRegistry;
import songscribe.ui.selection.LineSelectionState;

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

    /** Handles selection, hit-testing, and drag logic. */
    private final SelectionHandler selectionHandler = new SelectionHandler(this);

    /** Renderer that handles all drawing for this line. */
    private final LineRenderer lineRenderer = new LineRenderer(this);

    // ==========================================================================
    // Constants
    // ==========================================================================

    /** Number of lines in a staff. */
    private static final int STAFF_LINE_COUNT = 5;

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

        lineRenderer.render(g2);
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
     * <p>
     * Even for empty lines, uses minimum spacing to accommodate typical notes,
     * preventing position jumps when the first note is inserted.
     *
     * @return Y position of middle staff line in component coordinates
     */
    private int calculateMiddleLineY() {
        var staffLineYOffset = LayoutStylesheet.STAFF_LINE_Y_OFFSET;
        var defaultSpaceAbove = Score.STAFF_LINES_ABOVE * staffLineYOffset;
        double spaceAbove = MIN_SPACE_ABOVE;

        // Get extent of notes and attachments (only if line has content)
        if (line != null && !line.isEmpty()) {
            double tempMiddleLineY = defaultSpaceAbove + (2.0 * staffLineYOffset);
            var extent = CollisionDetector.calculateNoteExtent(line, tempMiddleLineY);
            spaceAbove = Math.max(MIN_SPACE_ABOVE, Math.abs(extent.getMinY()));
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
     * Minimum space above staff to accommodate a typical note (quarter note with stem).
     * This prevents the line from jumping in height when the first note is added.
     * Value determined empirically: 5 * STAFF_LINE_Y_OFFSET = 40px
     */
    private static final int MIN_SPACE_ABOVE = 5 * LayoutStylesheet.STAFF_LINE_Y_OFFSET;

    /**
     * Calculates the height needed for this line.
     * <p>
     * Uses CollisionDetector to measure the extent of notes and attachments
     * above and below the staff. Also accounts for tempo markings on the
     * first line.
     * <p>
     * Even for empty lines, uses minimum spacing to accommodate typical notes,
     * preventing height jumps when the first note is inserted.
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

        // Start with minimum space needed for typical notes
        double spaceAbove = MIN_SPACE_ABOVE;
        double spaceBelow = defaultSpaceBelow;

        // Get extent of notes and attachments (only if line has content)
        if (line != null && !line.isEmpty()) {
            double tempMiddleLineY = defaultSpaceAbove + (2.0 * staffLineYOffset);
            var extent = CollisionDetector.calculateNoteExtent(line, tempMiddleLineY);
            spaceAbove = Math.max(MIN_SPACE_ABOVE, Math.abs(extent.getMinY()));
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
        lineRenderer.renderDebug(g2);
    }

    // ==========================================================================
    // Insertion Note Delegation
    // ==========================================================================

    /**
     * Clears the insertion note from all lines.
     * Delegates to {@link InsertionNoteManager}.
     */
    public static void clearInsertionNote() {
        InsertionNoteManager.clearInsertionNote();
    }

    /**
     * Sets whether the Alt key is currently pressed and updates the cursor.
     * Delegates to {@link InsertionNoteManager}.
     */
    public static void setAltPressed(boolean pressed) {
        InsertionNoteManager.setAltPressed(pressed);
    }

    /**
     * Returns whether this line currently has the insertion note.
     */
    public boolean hasInsertionNote() {
        return InsertionNoteManager.hasInsertionNote(this);
    }

    /**
     * Returns the current insertion X index.
     */
    public static int getCurrentXIndex() {
        return InsertionNoteManager.getCurrentXIndex();
    }

    /**
     * Returns the current insertion Y position.
     */
    public static int getCurrentYPos() {
        return InsertionNoteManager.getCurrentYPos();
    }

    // ==========================================================================
    // Mouse Event Handlers
    // ==========================================================================

    @Override
    public void mouseMoved(MouseEvent e) {
        InsertionNoteManager.trackMouse(this, e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (selectionHandler.isSelectionActive(e)) {
            selectionHandler.handleDrag(e);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        if (!selectionHandler.handleClick(e)) {
            InsertionNoteManager.handleClick(this);
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

        if (selectionHandler.isSelectionActive(e)) {
            selectionHandler.handlePress(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        selectionHandler.handleRelease();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        InsertionNoteManager.mouseEnteredLine(this);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        InsertionNoteManager.mouseExitedLine(this);
    }

    // ==========================================================================
    // Package-Private Accessors for LineRenderer
    // ==========================================================================

    /**
     * Returns the Score reference.
     */
    Score getScore() {
        return score;
    }

    /**
     * Returns the selection provider.
     */
    SelectionProvider getSelectionProvider() {
        return selectionProvider;
    }

    /**
     * Returns whether a selection drag is in progress.
     */
    boolean isDraggingSelection() {
        return selectionHandler.isDragging();
    }

    /**
     * Returns the current drag rectangle.
     */
    Rectangle getDragRectangle() {
        return selectionHandler.getDragRectangle();
    }
}
