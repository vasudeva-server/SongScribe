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

import songscribe.music.NoteType;
import songscribe.smufl.EngravingDefaults;
import songscribe.smufl.GlyphAnchors;
import songscribe.smufl.SMuFLGlyph;
import songscribe.smufl.SMuFLMetadata;
import songscribe.ui.layout.LineElement;
import songscribe.util.MyFontUtils;

/**
 * Abstract base class for element renderers.
 * <p>
 * Provides shared utilities:
 * <ul>
 *   <li>Font constants and glyph rendering methods</li>
 *   <li>Common drawing operations (ledger lines, staff lines, etc.)</li>
 *   <li>Debug rendering wrapper</li>
 * </ul>
 *
 * @param <T> The LineElement type this renderer handles
 */
public abstract class BaseElementRenderer<T extends LineElement> implements ElementRenderer<T> {

    // ==========================================================================
    // Fughetta Font Glyph Constants
    // Note: Fughetta uses Private Use Area Unicode codepoints (U+F0xx)
    // Remaining Fughetta constants
    // ==========================================================================

    // Special markings
    protected static final String GLISSANDO = "\uf07e";
    protected static final String TRILL = "\uf0d9";

    // ==========================================================================
    // Font Constants
    // ==========================================================================

    /**
     * The base font size for music notation glyphs, in staff-space units.
     * Under the Graphics2D scale transform, this produces the correct pixel size
     * (4.0 ss * 8 px/ss = 32px).
     */
    public static final float NOTE_FONT_SIZE = 4.0f;

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
     * The Bravura (SMuFL) music notation font at standard note size.
     */
    public static final Font BRAVURA_FONT;

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

            var bravuraBase = Objects.requireNonNull(
                MyFontUtils.getLocalFont("Bravura.otf"),
                "Cannot load Bravura.otf font"
            );
            BRAVURA_FONT = bravuraBase.deriveFont(NOTE_FONT_SIZE);

            var tupletBase = Objects.requireNonNull(
                MyFontUtils.getLocalFont("TupletNumbers.ttf"),
                "Cannot load TupletNumbers.ttf font"
            );
            TUPLET_FONT = tupletBase.deriveFont(1.625f);  // 13px / 8 px/ss
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

    private static final EngravingDefaults ENGRAVING_DEFAULTS =
        SMuFLMetadata.getInstance().getEngravingDefaults();

    protected static final Stroke STAFF_LINE_STROKE = new BasicStroke(
        (float) ENGRAVING_DEFAULTS.staffLineThickness());
    protected static final Stroke STEM_STROKE = new BasicStroke(
        (float) ENGRAVING_DEFAULTS.stemThickness());
    protected static final Stroke LEDGER_LINE_STROKE = new BasicStroke(
        (float) ENGRAVING_DEFAULTS.legerLineThickness());

    // Stem width in staff-space units (scale transform handles pixel conversion).
    protected static final double STEM_WIDTH_SS = ENGRAVING_DEFAULTS.stemThickness();

    // Cached anchor data for notehead glyphs (in staff-space units, Y-down screen convention).
    protected static final GlyphAnchors.Anchor STEM_UP_SE_BLACK;
    protected static final GlyphAnchors.Anchor STEM_DOWN_NW_BLACK;
    protected static final GlyphAnchors.Anchor STEM_UP_SE_HALF;
    protected static final GlyphAnchors.Anchor STEM_DOWN_NW_HALF;

    static {
        var metadata = SMuFLMetadata.getInstance();
        var blackAnchors = metadata.getAnchors(SMuFLGlyph.NOTEHEAD_BLACK);
        var halfAnchors = metadata.getAnchors(SMuFLGlyph.NOTEHEAD_HALF);

        assert blackAnchors != null && blackAnchors.stemUpSE() != null && blackAnchors.stemDownNW() != null;
        assert halfAnchors != null && halfAnchors.stemUpSE() != null && halfAnchors.stemDownNW() != null;

        STEM_UP_SE_BLACK = blackAnchors.stemUpSE();
        STEM_DOWN_NW_BLACK = blackAnchors.stemDownNW();
        STEM_UP_SE_HALF = halfAnchors.stemUpSE();
        STEM_DOWN_NW_HALF = halfAnchors.stemDownNW();
    }

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
    protected static double layoutYToComponentYSs(
        @NotNull songscribe.ui.layout.Bounds bounds,
        @NotNull ElementRenderContext ctx
    ) {
        return ctx.getMiddleLineYSs() + bounds.getTop();
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
    protected static double layoutXToComponentXSs(@NotNull songscribe.ui.layout.Bounds bounds) {
        return bounds.getLeft();
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
            g2.draw(new Line2D.Double(x - halfWidth, y, x + halfWidth, y));
        }
    }

    /**
     * Draws a SMuFL glyph using the Bravura font.
     *
     * @param g2    Graphics context
     * @param glyph The SMuFL glyph to draw
     * @param x     X position
     * @param y     Y position
     */
    protected void drawBravuraGlyph(
        @NotNull Graphics2D g2,
        @NotNull SMuFLGlyph glyph,
        double x,
        double y
    ) {
        drawBravuraGlyph(g2, glyph, x, y, false);
    }

    /**
     * Draws a SMuFL glyph using the Bravura font.
     *
     * @param g2            Graphics context
     * @param glyph         The SMuFL glyph to draw
     * @param x             X position
     * @param y             Y position
     * @param preserveColor If true, preserves the current graphics color instead of setting NOTE_COLOR
     */
    protected void drawBravuraGlyph(
        @NotNull Graphics2D g2,
        @NotNull SMuFLGlyph glyph,
        double x,
        double y,
        boolean preserveColor
    ) {
        try (var ignored = GraphicsState.save(g2, COLOR, FONT)) {
            g2.setFont(BRAVURA_FONT);
            if (!preserveColor) {
                g2.setColor(NOTE_COLOR);
            }
            g2.drawString(glyph.asString(), (float) x, (float) y);
        }
    }

    /**
     * Converts a staff line index to Y coordinate in staff-space units.
     * <p>
     * Staff lines are indexed 0-4 where:
     * <ul>
     *   <li>0 = top line (F5)</li>
     *   <li>2 = middle line (B4)</li>
     *   <li>4 = bottom line (E4)</li>
     * </ul>
     * Each staff line is 1.0 ss apart.
     *
     * @param lineIndex   Staff line index (0-4)
     * @param middleLineYSs Y position of middle staff line in staff spaces
     * @return Y coordinate in staff spaces
     */
    protected double staffLineToYSs(int lineIndex, double middleLineYSs) {
        return middleLineYSs + (lineIndex - 2);
    }

    /**
     * Calculates the Y coordinate for a note given its staff position.
     * <p>
     * The staff position is relative to the middle line (B4), where:
     * <ul>
     *   <li>0 = B4 (middle line)</li>
     *   <li>Negative values = higher pitches (above middle line)</li>
     *   <li>Positive values = lower pitches (below middle line)</li>
     * </ul>
     * Each staff position is 0.5 ss (half a staff space).
     *
     * @param staffPosition The note's staff position relative to middle line
     * @param middleLineYSs Y position of middle staff line in staff spaces
     * @return Y coordinate for the note in staff spaces
     */
    protected double noteYPosToCoordinateSs(int staffPosition, double middleLineYSs) {
        return middleLineYSs + staffPosition * 0.5;
    }

    /**
     * Returns the X offset in staff spaces from the note reference point to the stem center,
     * for the given stem direction and note type.
     *
     * @param noteType the note type (determines which notehead anchor to use)
     * @param upper    true = stem goes up (stem-up SE anchor); false = stem goes down (stem-down NW anchor)
     * @return X offset from note reference point to stem center, in staff spaces
     */
    protected static double stemCenterXOffsetSs(@NotNull NoteType noteType, boolean upper) {
        boolean isMinim = noteType == NoteType.MINIM;
        double anchorX = upper
            ? (isMinim ? STEM_UP_SE_HALF.x()   : STEM_UP_SE_BLACK.x())
            : (isMinim ? STEM_DOWN_NW_HALF.x() : STEM_DOWN_NW_BLACK.x());

        // upper: SE anchor is the stem's right edge; center = anchorX - half stem width
        // lower: NW anchor is the stem's left edge (after notehead shift); center = anchorX
        return upper ? anchorX - STEM_WIDTH_SS / 2.0 : anchorX;
    }
}
