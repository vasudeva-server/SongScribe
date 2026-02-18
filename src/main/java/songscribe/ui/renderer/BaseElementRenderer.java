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

package songscribe.ui.renderer;

import static songscribe.ui.renderer.GraphicsState.Property.COLOR;
import static songscribe.ui.renderer.GraphicsState.Property.FONT;
import static songscribe.ui.renderer.GraphicsState.Property.STROKE;

import java.awt.*;
import java.awt.geom.*;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

import songscribe.ui.layout.LayoutStylesheet;
import songscribe.ui.layout.LineElement;
import songscribe.util.MyFontUtils;

/**
 * Abstract base class for element renderers.
 * <p>
 * Provides shared utilities:
 * <ul>
 *   <li>Fughetta font glyph constants and mappings</li>
 *   <li>Common drawing operations (ledger lines, staff lines, etc.)</li>
 *   <li>Debug rendering wrapper</li>
 * </ul>
 *
 * @param <T> The LineElement type this renderer handles
 */
public abstract class BaseElementRenderer<T extends LineElement> implements ElementRenderer<T> {

    // ==========================================================================
    // Fughetta Font Glyph Constants
    // (Extracted from FughettaRenderer.java)
    // Note: Fughetta uses Private Use Area Unicode codepoints (U+F0xx)
    // ==========================================================================

    // Note heads
    protected static final String SEMIBREVE_HEAD = "\uf077";
    protected static final String MINIM_HEAD = "\uf0cd";
    protected static final String FILLED_NOTE_HEAD = "\uf0cf";

    // Rests
    protected static final String SEMIBREVE_REST = "\uf0ee";
    protected static final String MINIM_REST = "\uf0ee";
    protected static final String CROTCHET_REST = "\uf0ce";
    protected static final String QUAVER_REST = "\uf0e4";
    protected static final String SEMIQUAVER_REST = "\uf0c5";
    protected static final String DEMISEMIQUAVER_REST = "\uf0a8";

    // Accidentals
    protected static final String NATURAL = "\uf06e";
    protected static final String FLAT = "\uf062";
    protected static final String SHARP = "\uf023";
    protected static final String DOUBLE_NATURAL = "\uf06e\uf06e";
    protected static final String DOUBLE_FLAT = "\uf0ba";
    protected static final String DOUBLE_SHARP = "\uf0dc";
    protected static final String NATURAL_FLAT = "\uf06e\uf062";
    protected static final String NATURAL_SHARP = "\uf06e\uf023";

    // Clefs
    protected static final String TREBLE_CLEF = "\uf026";

    // Flags (for stems)
    protected static final String MAIN_UPPER_FLAG = "\uf06a";
    protected static final String SECOND_UPPER_FLAG = "\uf0fb";
    protected static final String MAIN_LOWER_FLAG = "\uf04a";
    protected static final String SECOND_LOWER_FLAG = "\uf0f0";

    // Special markings
    protected static final String GLISSANDO = "\uf07e";
    protected static final String TRILL = "\uf0d9";

    // Parentheses (for cautionary accidentals)
    protected static final String BEGIN_PARENTHESIS = "\uf028";
    protected static final String END_PARENTHESIS = "\uf029";

    // ==========================================================================
    // Font Constants
    // ==========================================================================

    /**
     * The base font size for Fughetta music notation glyphs.
     */
    public static final float NOTE_FONT_SIZE = 32f;

    /**
     * Scale factor for grace note accidentals relative to NOTE_FONT_SIZE.
     */
    public static final float GRACE_ACCIDENTAL_RESIZE_FACTOR = 0.65f;

    /**
     * Horizontal scale factor for tempo change note display.
     */
    public static final double TEMPO_CHANGE_ZOOM_X = 0.8;

    /**
     * Vertical scale factor for tempo change note display.
     */
    public static final double TEMPO_CHANGE_ZOOM_Y = 0.6;

    /**
     * The music notation font at standard note size.
     */
    public static final Font MUSIC_FONT;

    /**
     * The music notation font at grace note size.
     */
    public static final Font MUSIC_FONT_GRACE;

    /**
     * Font for tuplet numbers (e.g., "3" for triplets).
     */
    public static final Font TUPLET_FONT;

    /**
     * Font for first/second ending numbers.
     */
    public static final Font ENDING_FONT;

    static {
        try {
            var fughettaBase = Objects.requireNonNull(
                MyFontUtils.getLocalFont("Fughetta.ttf"),
                "Cannot load Fughetta.ttf font"
            );
            MUSIC_FONT = fughettaBase.deriveFont(NOTE_FONT_SIZE);
            MUSIC_FONT_GRACE = fughettaBase.deriveFont(NOTE_FONT_SIZE * GRACE_ACCIDENTAL_RESIZE_FACTOR);

            var tupletBase = Objects.requireNonNull(
                MyFontUtils.getLocalFont("TupletNumbers.ttf"),
                "Cannot load TupletNumbers.ttf font"
            );
            TUPLET_FONT = tupletBase.deriveFont(13f);
            ENDING_FONT = TUPLET_FONT;
        } catch (Exception e) {
            throw new RuntimeException("Cannot load required fonts for rendering.", e);
        }
    }

    // ==========================================================================
    // Colors
    // ==========================================================================

    protected static final Color STAFF_LINE_COLOR = Color.BLACK;
    protected static final Color NOTE_COLOR = Color.BLACK;
    protected static final Color DEBUG_CONTENT_COLOR = new Color(0, 0, 255, 64);
    protected static final Color DEBUG_CONTENT_BORDER = new Color(0, 0, 255, 128);
    protected static final Color DEBUG_MARGIN_COLOR = new Color(255, 0, 0, 64);
    protected static final Color DEBUG_MARGIN_BORDER = new Color(255, 0, 0, 128);
    protected static final Color DEBUG_LABEL_COLOR = new Color(0, 0, 0, 192);
    protected static final Color DEBUG_LABEL_BACKGROUND = new Color(255, 255, 255, 200);

    // ==========================================================================
    // Strokes
    // ==========================================================================

    protected static final Stroke STAFF_LINE_STROKE = new BasicStroke(1.0f);
    protected static final Stroke STEM_STROKE = new BasicStroke(1.0f);
    protected static final Stroke LEDGER_LINE_STROKE = new BasicStroke(1.0f);

    // ==========================================================================
    // Rendering Template Method
    // ==========================================================================

    /**
     * Final render method with debug wrapper.
     * <p>
     * Calls {@link #renderElement(LineElement, Graphics2D, ElementRenderContext)} for actual rendering,
     * then overlays debug visualization if enabled.
     */
    @Override
    public final void render(
        @NotNull T element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        renderElement(element, g2, ctx);
        // Note: Debug/inspector visualization is handled separately by DebugRenderer,
        // which only draws for the hovered element. We don't call renderDebug() here
        // to avoid drawing debug rectangles for all elements.
    }

    /**
     * Renders the element. Subclasses implement element-specific drawing.
     *
     * @param element The element to render
     * @param g2      The graphics context
     * @param ctx     Rendering context
     */
    protected abstract void renderElement(
        @NotNull T element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    );

    // ==========================================================================
    // Coordinate Transformation Utilities
    // ==========================================================================

    /**
     * Converts a Y coordinate from layout space to component space.
     * <p>
     * Layout coordinates are relative to middleLineY=0 (positive below, negative above).
     * Component coordinates are relative to the component's top edge.
     * <p>
     * This transformation is needed when using layout results for rendering,
     * as layout positions must be converted to actual screen positions.
     *
     * @param bounds The layout bounds
     * @param ctx    The rendering context containing middleLineY
     * @return The Y coordinate in component space
     */
    protected static int layoutYToComponentY(
        @NotNull songscribe.ui.layout.Bounds bounds,
        @NotNull ElementRenderContext ctx
    ) {
        return ctx.getMiddleLineY() + (int) bounds.getTop();
    }

    /**
     * Converts an X coordinate from layout space to component space.
     * <p>
     * Currently, X coordinates are the same in both spaces, but this method
     * is provided for symmetry and future-proofing.
     *
     * @param bounds The layout bounds
     * @return The X coordinate in component space
     */
    protected static int layoutXToComponentX(@NotNull songscribe.ui.layout.Bounds bounds) {
        return (int) bounds.getLeft();
    }

    /**
     * Renders debug visualization (content bounds, margin bounds, element type label).
     * <p>
     * Default implementation draws:
     * <ul>
     *   <li>Content bounds filled in semi-transparent blue</li>
     *   <li>Margin bounds filled in semi-transparent red</li>
     *   <li>Element type label at top-left of content bounds</li>
     * </ul>
     * Subclasses can override to add element-specific debug info.
     *
     * @param element The element being debugged
     * @param g2      The graphics context
     * @param ctx     Rendering context
     */
    protected void renderDebug(
        @NotNull T element,
        @NotNull Graphics2D g2,
        @NotNull ElementRenderContext ctx
    ) {
        var contentBounds = element.getContentBounds();
        var marginBounds = element.getMarginBounds();

        // Draw margin bounds first (behind content bounds)
        if (ctx.isShowMargins() || ctx.isDebugEnabled()) {
            try (var ignored = GraphicsState.save(g2, COLOR)) {
                g2.setColor(DEBUG_MARGIN_COLOR);
                g2.fill(marginBounds);
                g2.setColor(DEBUG_MARGIN_BORDER);
                g2.draw(marginBounds);
            }
        }

        // Draw content bounds on top
        if (ctx.isShowBoundingBoxes() || ctx.isDebugEnabled()) {
            try (var ignored = GraphicsState.save(g2, COLOR)) {
                g2.setColor(DEBUG_CONTENT_COLOR);
                g2.fill(contentBounds);
                g2.setColor(DEBUG_CONTENT_BORDER);
                g2.draw(contentBounds);
            }
        }

        // Draw element type label
        if (ctx.isDebugEnabled()) {
            drawElementLabel(element, g2, contentBounds);
        }
    }

    /**
     * Draws a label showing the element type at the top-left of its content bounds.
     *
     * @param element       The element being labeled
     * @param g2            The graphics context
     * @param contentBounds The element's content bounds
     */
    private void drawElementLabel(
        @NotNull T element,
        @NotNull Graphics2D g2,
        @NotNull Rectangle2D contentBounds
    ) {
        String typeName = element.getClass().getSimpleName();
        Font labelFont = new Font(Font.SANS_SERIF, Font.PLAIN, 9);
        FontMetrics fm = g2.getFontMetrics(labelFont);

        int labelX = (int) contentBounds.getX();
        int labelY = (int) contentBounds.getY() - 2;

        int textWidth = fm.stringWidth(typeName);
        int textHeight = fm.getHeight();

        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            // Draw background for label
            g2.setColor(DEBUG_LABEL_BACKGROUND);
            g2.fillRect(labelX - 1, labelY - textHeight + fm.getDescent(), textWidth + 2, textHeight);

            // Draw label text
            g2.setColor(DEBUG_LABEL_COLOR);
            g2.setFont(labelFont);
            g2.drawString(typeName, labelX, labelY);
        }
    }

    /**
     * Default getBounds implementation returns element's content bounds.
     * <p>
     * Subclasses can override if rendered bounds differ from logical bounds.
     */
    @Override
    public @NotNull Rectangle2D getBounds(
        @NotNull T element,
        @NotNull ElementRenderContext ctx
    ) {
        return element.getContentBounds();
    }

    // ==========================================================================
    // Shared Drawing Utilities
    // ==========================================================================

    /**
     * Draws a ledger line for a note above or below the staff.
     *
     * @param g2    Graphics context
     * @param x     Center X position of the note
     * @param y     Y position of the ledger line
     * @param width Width of the ledger line
     */
    protected void drawLedgerLine(@NotNull Graphics2D g2, double x, double y, double width) {
        try (var ignored = GraphicsState.save(g2, COLOR, STROKE)) {
            g2.setStroke(LEDGER_LINE_STROKE);
            g2.setColor(STAFF_LINE_COLOR);

            double halfWidth = width / 2.0;
            g2.drawLine(
                (int) (x - halfWidth),
                (int) y,
                (int) (x + halfWidth),
                (int) y
            );
        }
    }

    /**
     * Draws a glyph string from the Fughetta font.
     *
     * @param g2    Graphics context
     * @param glyph The Fughetta font string (may contain multiple characters)
     * @param x     X position
     * @param y     Y position
     * @param ctx   Render context for font access
     */
    protected void drawFughettaGlyph(
        @NotNull Graphics2D g2,
        @NotNull String glyph,
        double x,
        double y,
        @NotNull ElementRenderContext ctx
    ) {
        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(ctx.getMusicFont());
            g2.setColor(NOTE_COLOR);
            g2.drawString(glyph, (float) x, (float) y);
        }
    }

    /**
     * Converts a staff line index to Y coordinate.
     * <p>
     * Staff lines are indexed 0-4 where:
     * <ul>
     *   <li>0 = top line (F5)</li>
     *   <li>2 = middle line (B4)</li>
     *   <li>4 = bottom line (E4)</li>
     * </ul>
     *
     * @param lineIndex   Staff line index (0-4)
     * @param middleLineY Y position of middle staff line
     * @return Y coordinate
     */
    protected int staffLineToY(int lineIndex, int middleLineY) {
        int offset = (lineIndex - 2) * LayoutStylesheet.STAFF_LINE_Y_OFFSET;
        return middleLineY + offset;
    }

    /**
     * Calculates the Y position for a note given its pitch position.
     * <p>
     * The pitch position is relative to the middle line (B4), where:
     * <ul>
     *   <li>0 = B4 (middle line)</li>
     *   <li>Negative values = higher pitches (above middle line)</li>
     *   <li>Positive values = lower pitches (below middle line)</li>
     * </ul>
     *
     * @param yPos        The note's y-position relative to middle line
     * @param middleLineY Y position of middle staff line
     * @return Y coordinate for the note
     */
    protected int noteYPosToCoordinate(int yPos, int middleLineY) {
        return middleLineY + (int) (yPos * LayoutStylesheet.NOTE_Y_OFFSET);
    }
}
