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
// Disambiguates from org.w3c.dom.events.MouseEvent (java.xml module)
import java.awt.event.MouseEvent;

import org.jspecify.annotations.Nullable;

import songscribe.music.Line;
import songscribe.ui.Mode;
import songscribe.ui.action.Actions;
import songscribe.ui.component.ComponentNames;
import songscribe.ui.edit.EditModeManager;
import songscribe.ui.edit.GraceModeManager;
import songscribe.ui.component.Score;
import songscribe.ui.layout.CollisionDetector;
import songscribe.ui.layout.LayoutEngine;
import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.LayoutResult;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.selection.LineSelectionState;
import songscribe.error.RuntimeError;

/**
 * Component that renders a single staff line with its musical content.
 * <p>
 * This is the core rendering component that:
 * <ul>
 *   <li>Draws the 5-line staff</li>
 *   <li>Renders notes and other elements via {@link LineRenderer}</li>
 * </ul>
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
         * Returns whether the specified element is selected.
         *
         * @param elementIndex The element index within the line
         * @param lineIndex    The line index
         * @return true if the element is selected
         */
        boolean isElementSelected(int elementIndex, int lineIndex);

        /**
         * Returns whether the staff line itself is selected (for deletion).
         *
         * @param lineIndex The line index
         * @return true if the staff line is selected
         */
        boolean isLineSelected(int lineIndex);

        /**
         * Returns whether the glissando owned by the element at the given index
         * is selected.
         *
         * @param elementIndex The element index within the line
         * @param lineIndex    The line index
         * @return true if the glissando is selected
         */
        boolean isGlissandoSelected(int elementIndex, int lineIndex);
    }

    // ==========================================================================
    // Instance Fields
    // ==========================================================================

    /** The line model containing staff elements. */
    @Nullable
    private Line line;

    /** Index of this line within the composition. */
    private int lineIndex;

    /** Per-line selection state. */
    @Nullable
    private LineSelectionState lineSelectionState;

    /** Y coordinate of the middle staff line (B line) in staff-space units. */
    private double middleLineYSs;

    /** Provider for checking note selection state. */
    @Nullable
    private SelectionProvider selectionProvider;

    /** Reference to the Score for accessing composition and services. */
    @Nullable
    private Score score;

    /** Index of the currently playing note (-1 if not playing). */
    private int playingNoteIndex = -1;

    /** Index of the grace note paired with the currently playing note (-1 if none). */
    private int playingGraceNoteIndex = -1;

    /** The layout engine for calculating element positions. */
    @Nullable
    private LayoutEngine layoutEngine;

    /** Cached layout result from the last layout pass. */
    @Nullable
    private LayoutResult layoutResult;

    /** Whether layout needs to be recalculated. */
    private boolean layoutDirty = true;

    /** Handles selection, hit-testing, and drag logic. */
    private final LineSelectionHandler selectionHandler = new LineSelectionHandler(this);

    /** Handles press/drag/release for pitch-dragging a note in NOTE_EDIT mode. */
    private final NoteDragHandler noteDragHandler = new NoteDragHandler(this);

    /** Renderer that handles all drawing for this line. */
    private final LineRenderer lineRenderer = new LineRenderer(this);

    // ==========================================================================
    // Constants
    // ==========================================================================

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
    public void setLine(Line line, int lineIndex) {
        this.line = line;
        this.lineIndex = lineIndex;
        setName(ComponentNames.line(lineIndex));
        this.lineSelectionState = new LineSelectionState(line);
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
    public @Nullable Line getLine() {
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
     * Converts a staff position to a Y pixel coordinate.
     *
     * @param staffPositionSp The staff position (0 = middle line)
     * @return Y coordinate in pixels
     */
    public int staffPositionToYPx(int staffPositionSp) {
        return getMiddleLineYPx() + (int) Math.round(staffPositionSp * Score.STAFF_POSITION_OFFSET_PX);
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
    public void setScore(Score score) {
        this.score = score;

        // Register LineSelectionState with coordinator when score is set
        if (lineSelectionState != null) {
            var coordinator = score.getSelectionCoordinator();

            if (coordinator != null) {
                coordinator.registerLineState(lineIndex, lineSelectionState);
            }
        }
    }

    /**
     * Sets both playing indices atomically, triggering a single repaint.
     *
     * @param noteIndex Note index, or -1 if not playing
     * @param graceNoteIndex Grace note index, or -1 if none
     */
    public void setPlayingIndices(int noteIndex, int graceNoteIndex) {
        if (playingNoteIndex != noteIndex || playingGraceNoteIndex != graceNoteIndex) {
            playingNoteIndex = noteIndex;
            playingGraceNoteIndex = graceNoteIndex;
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
     * Returns the index of the grace note paired with the currently playing note (-1 if none).
     */
    public int getPlayingGraceNoteIndex() {
        return playingGraceNoteIndex;
    }

    /**
     * Returns whether the score is in an interactive editing mode (note edit or select),
     * as opposed to an adjustment mode.
     */
    public boolean isEditMode() {
        return !getScore().getMode().isAdjustmentMode();
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
    private void performLayout(Graphics2D g2) {
        if (composition == null || line == null) {
            return;
        }

        var lyricsFont = composition.getLyricsFont();
        var staffRightMarginSs = composition.getLineWidthSs();
        layoutEngine = new LayoutEngine(g2, lyricsFont, staffRightMarginSs);
        layoutResult = layoutEngine.layout(line);

        if (layoutResult == null) {
            var error = layoutEngine.getLastError();
            System.err.println("Layout failed for line " + lineIndex + ": " + error);
        }

        layoutDirty = false;
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

        var dim = new Dimension(
            (int) Math.ceil(scale.toPixels(widthSs)),
            (int) Math.ceil(scale.toPixels(heightSs))
        );
        return dim;
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
        double defaultSpaceAbove = LayoutStylesheet.STAFF_LINES_ABOVE;  // 3.0 ss
        double spaceAbove = MIN_SPACE_ABOVE_SS;

        // Get extent of notes and attachments (only if line has content)
        if (line != null && !line.isEmpty()) {
            // CollisionDetector still uses pixel-space margin bounds (renderers not yet converted)
            var scale = ScaleContext.getInstance();
            double tempMiddleLineYPx = scale.toPixels(defaultSpaceAbove + 2.0);
            var extent = CollisionDetector.calculateNoteExtent(line, tempMiddleLineYPx);
            spaceAbove = Math.max(MIN_SPACE_ABOVE_SS, scale.fromPixels(Math.abs(extent.getMinY())));
        }

        // Use layout result to determine space needed above staff.
        // Check all decoration layouts (tempo, endings, trills, hairpins, etc.)
        // because any of them may extend above the staff.
        if (layoutResult != null) {
            for (var decorationLayout : layoutResult.getDecorationLayouts().values()) {
                double top = decorationLayout.ySs();

                if (top < 0) {
                    spaceAbove = Math.max(spaceAbove, Math.abs(top));
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
        var comp = getComposition();

        if (line == null || line.isEmpty() || layoutResult == null) {
            return comp.getLineWidthSs();
        }

        // Use the greater of composition width or calculated width from layout
        double calculatedWidth = layoutResult.getLineWidthSs();

        return Math.max(comp.getLineWidthSs(), calculatedWidth);
    }

    /**
     * Minimum space above staff to accommodate a typical note (quarter note with stem).
     * This prevents the line from jumping in height when the first note is added.
     * Value: 5.0 ss (5 staff-space gaps above the top staff line).
     */
    private static final double MIN_SPACE_ABOVE_SS = 10.0;

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
        double defaultSpaceAbove = LayoutStylesheet.STAFF_LINES_ABOVE;  // 3.0 ss
        double defaultSpaceBelow = LayoutStylesheet.STAFF_LINES_BELOW;  // 4.0 ss
        double staffHeight = LayoutStylesheet.STAFF_HEIGHT_SS;   // 4.0 ss

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
            var tempoYOffset = -7.0 * LayoutStylesheet.STAFF_POSITION_OFFSET_SS + tempoChangeYPosSs;
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
        if (lineIndex == 0) {
            return true;
        }

        // Check for tempo change on any note in this line
        if (line != null) {
            for (var i = 0; i < line.elementCount(); i++) {
                if (line.getElement(i).getTempoChange() != null) {
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
     * Delegates to {@link InsertionElementManager}.
     */
    public static void clearInsertionElement() {
        InsertionElementManager.clearInsertionElement();
    }

    /**
     * Sets whether the Alt key is currently pressed and updates the cursor.
     * Delegates to {@link InsertionElementManager}.
     */
    public static void setAltPressed(boolean pressed) {
        InsertionElementManager.setAltPressed(pressed);
    }

    /**
     * Returns whether this line currently has the insertion note.
     */
    public boolean hasInsertionElement() {
        return InsertionElementManager.hasInsertionElement(this);
    }

    /**
     * Returns the current insertion X index.
     */
    public static int getCurrentXIndex() {
        return InsertionElementManager.getCurrentXIndex();
    }

    /**
     * Returns the current insertion Y position.
     */
    public static int getCurrentStaffPosition() {
        return InsertionElementManager.getCurrentStaffPosition();
    }

    // ==========================================================================
    // Mouse Event Handlers
    // ==========================================================================

    @Override
    public void mouseMoved(MouseEvent e) {
        if (getGraceModeManager().mouseMoved(this, e)) {
            return;
        }

        InsertionElementManager.trackMouse(this, e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (getGraceModeManager().mouseDragged(this, e)) {
            return;
        }

        if (noteDragHandler.isDragActive()) {
            noteDragHandler.handleDrag(e);
            return;
        }

        if (selectionHandler.isDragging() || selectionHandler.isSelectionActive(e)) {
            selectionHandler.handleDrag(e);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        // Grace mode handles its own click logic. Returns true to consume the event.
        if (getGraceModeManager().mouseClicked(this, e)) {
            return;
        }

        if (!selectionHandler.handleClick(e)) {
            InsertionElementManager.handleClick(this);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }

        if (getGraceModeManager().mousePressed(this, e)) {
            return;
        }

        // Alt+click in EDIT mode: switch to SELECT, then fall through to normal handling
        if (e.isAltDown() && score != null && score.getMode() == Mode.EDIT) {
            Actions.SELECT_MODE_ACTION.perform(this);
        }

        // In SELECT mode, note head press starts a pitch drag
        if (noteDragHandler.handlePress(e)) {
            return;
        }

        if (selectionHandler.isSelectionActive(e)) {
            selectionHandler.handlePress(e);
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (getGraceModeManager().mouseReleased(this, e)) {
            return;
        }

        if (noteDragHandler.isDragActive()) {
            noteDragHandler.handleRelease();
            return;
        }

        selectionHandler.handleRelease();
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        InsertionElementManager.mouseEnteredLine(this);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        InsertionElementManager.mouseExitedLine(this);
    }

    // ==========================================================================
    // Package-Private Accessors for LineRenderer
    // ==========================================================================

    /**
     * Returns the Score reference.
     */
    Score getScore() {
        if (score == null) {
            throw RuntimeError.exit("Score reference not set on LineComponent");
        }

        return score;
    }

    private GraceModeManager getGraceModeManager() {
        var emm = EditModeManager.getInstance();

        if (emm == null) {
            throw RuntimeError.exit("EditModeManager not initialized");
        }

        return emm.getGraceModeManager();
    }

    /**
     * Returns the selection provider.
     */
    @Nullable SelectionProvider getSelectionProvider() {
        return selectionProvider;
    }

    /**
     * Returns whether a selection drag is in progress.
     */
    public boolean isDraggingSelection() {
        return selectionHandler.isDragging();
    }

    /**
     * Clears any active rubber-band drag rectangle on this line.
     * Called from Score when a window-level mouseReleased catches an orphaned drag.
     */
    public void clearDragRectangle() {
        selectionHandler.handleRelease();
    }

    /**
     * Returns the note pitch-drag handler for this line.
     */
    NoteDragHandler getNoteDragHandler() {
        return noteDragHandler;
    }

    /**
     * Returns the selection handler for this line.
     */
    LineSelectionHandler getSelectionHandler() {
        return selectionHandler;
    }

    /**
     * Returns the current drag rectangle.
     */
    Rectangle getDragRectangle() {
        return selectionHandler.getDragRectangle();
    }
}
