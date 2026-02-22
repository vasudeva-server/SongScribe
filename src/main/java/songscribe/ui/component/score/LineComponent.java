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
import songscribe.ui.layout2.ScaleContext;
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

    /** Y coordinate of the middle staff line (B line) in staff-space units. */
    private double middleLineYSs;

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
        if (middleLineYSs == 0.0 && composition != null) {
            middleLineYSs = calculateMiddleLineYSs();
        }

        return middleLineYSs;
    }

    /**
     * Returns the Y coordinate of the middle staff line in pixels.
     * <p>
     * Bridge method for callers not yet converted to staff-space units.
     * Will be removed when renderers and mouse code are converted.
     */
    public int getMiddleLineYPx() {
        return (int) Math.round(ScaleContext.getInstance().toPixels(getMiddleLineYSs()));
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
        var staffRightMarginSs = composition.getLineWidth();
        layoutEngine = new LayoutEngine(g2, lyricsFont, staffRightMarginSs);
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
        }

        // Update middleLineY from layout result (render owns this field)
        middleLineYSs = calculateMiddleLineYSs();

        // Apply staff-space to pixel scale transform at the render boundary.
        // All downstream drawing uses staff-space coordinates.
        var savedTransform = g2.getTransform();
        var scale = ScaleContext.getInstance().getPixelsPerStaffSpace();
        g2.scale(scale, scale);

        try {
            lineRenderer.render(g2);
        } finally {
            g2.setTransform(savedTransform);
        }

        // Drag rectangle is a pixel-space UI overlay — render after restoring the transform
        // so it is not affected by the staff-space scale.
        lineRenderer.renderDragRectangle(g2);
    }

    @Override
    public Dimension getPreferredSize() {
        if (composition == null) {
            return new Dimension(0, 0);
        }

        // Calculate width and height in staff-space units
        double widthSs = calculateLineWidthSs();
        double heightSs = calculateLineHeightSs();

        // Convert to pixels at the Swing boundary
        var scale = ScaleContext.getInstance();

        return new Dimension(
            (int) Math.ceil(scale.toPixels(widthSs)),
            (int) Math.ceil(scale.toPixels(heightSs))
        );
    }

    /**
     * Calculates the Y position of the middle staff line in staff-space units.
     * <p>
     * This accounts for extra space needed above the staff for tempo markings
     * and other elements that extend above the default staff area.
     * <p>
     * Even for empty lines, uses minimum spacing to accommodate typical notes,
     * preventing position jumps when the first note is inserted.
     *
     * @return Y position of middle staff line in staff-space units
     */
    private double calculateMiddleLineYSs() {
        // In staff-space: spacing between adjacent staff lines is 1.0 ss
        double defaultSpaceAbove = Score.STAFF_LINES_ABOVE;  // 3.0 ss
        double spaceAbove = MIN_SPACE_ABOVE_SS;

        // Get extent of notes and attachments (only if line has content)
        if (line != null && !line.isEmpty()) {
            // CollisionDetector still uses pixel-space margin bounds (renderers not yet converted)
            var scale = ScaleContext.getInstance();
            double tempMiddleLineYPx = scale.toPixels(defaultSpaceAbove + 2.0);
            var extent = CollisionDetector.calculateNoteExtent(line, tempMiddleLineYPx);
            spaceAbove = Math.max(MIN_SPACE_ABOVE_SS, scale.fromPixels(Math.abs(extent.getMinY())));
        }

        // Use layout result to determine space needed above staff
        // Layout result positions are in ss relative to middleLineY=0
        if (layoutResult != null) {
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

        // Middle line is 2 staff-space offsets below the top staff line
        return spaceAbove + 2.0;
    }

    /**
     * Calculates the width needed for this line in staff-space units.
     * <p>
     * Uses the composition's line width, or calculates from note positions
     * if the line has notes.
     *
     * @return Width in staff-space units
     */
    private double calculateLineWidthSs() {
        if (line == null || line.isEmpty() || layoutResult == null) {
            return composition.getLineWidth();
        }

        // Use the greater of composition width or calculated width from layout
        double calculatedWidth = layoutResult.getLineWidthSs();

        return Math.max(composition.getLineWidth(), calculatedWidth);
    }

    /**
     * Minimum space above staff to accommodate a typical note (quarter note with stem).
     * This prevents the line from jumping in height when the first note is added.
     * Value: 5.0 ss (5 staff-space gaps above the top staff line).
     */
    private static final double MIN_SPACE_ABOVE_SS = 5.0;

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
     * @return Height in staff-space units
     */
    private double calculateLineHeightSs() {
        // All values in staff-space units
        double defaultSpaceAbove = Score.STAFF_LINES_ABOVE;  // 3.0 ss
        double defaultSpaceBelow = Score.STAFF_LINES_BELOW;  // 4.0 ss
        double staffHeight = LayoutStylesheet.STAFF_HEIGHT;   // 4.0 ss

        double spaceAbove = MIN_SPACE_ABOVE_SS;
        double spaceBelow = defaultSpaceBelow;

        // Get extent of notes and attachments (only if line has content)
        if (line != null && !line.isEmpty()) {
            // CollisionDetector still uses pixel-space margin bounds (renderers not yet converted)
            var scale = ScaleContext.getInstance();
            double tempMiddleLineYPx = scale.toPixels(defaultSpaceAbove + 2.0);
            var extent = CollisionDetector.calculateNoteExtent(line, tempMiddleLineYPx);
            spaceAbove = Math.max(MIN_SPACE_ABOVE_SS, scale.fromPixels(Math.abs(extent.getMinY())));
            spaceBelow = Math.max(
                defaultSpaceBelow,
                scale.fromPixels(extent.getMaxY()) - (staffHeight / 2.0)
            );
        }

        // Account for tempo marking on first line (even if line is empty)
        if (lineIndex == 0 && hasTempo()) {
            // tempoChangeYPosPx is a legacy pixel offset (deprecated, typically 0)
            var tempoChangeYPosSs = ScaleContext.getInstance().fromPixels(
                (line != null) ? line.getTempoChangeYPosPx() : 0
            );
            var tempoYOffset = -7.0 * LayoutStylesheet.NOTE_Y_OFFSET + tempoChangeYPosSs;
            // Approximate tempo content height (note symbol + text ascent): ~3.125 ss
            var tempoContentHeight = 3.125;
            var tempoSpaceAbove = Math.abs(tempoYOffset) + tempoContentHeight - 2.0;
            spaceAbove = Math.max(spaceAbove, tempoSpaceAbove);
        }

        return spaceAbove + staffHeight + spaceBelow;
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
    public static int getCurrentStaffPosition() {
        return InsertionNoteManager.getCurrentStaffPosition();
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
            var pressedOnNote = selectionHandler.didHitSelectableElement(e.getPoint());
            InsertionNoteManager.onMousePressed(pressedOnNote);
            selectionHandler.handlePress(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        InsertionNoteManager.onMouseReleased();
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
