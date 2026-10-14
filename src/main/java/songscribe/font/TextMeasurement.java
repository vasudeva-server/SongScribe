/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.font;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.AttributedString;
import javax.swing.JComponent;

import org.jspecify.annotations.Nullable;

import songscribe.dom.DocumentScale;
import songscribe.dom.Ss;
import songscribe.error.RuntimeError;
import songscribe.util.GraphicUtils;

/**
 * Every text measurement the application makes.
 * <p>
 * A query here answers exactly one of three questions, and they are not interchangeable:
 * <b>advance</b> (how far the pen moves), <b>ink</b> (where the marks land, which can overshoot
 * the advance or start before the drawing origin), or <b>the font's own vertical design</b>
 * (ascent, descent, leading). Sizing a box from the advance where ink is meant clips the glyph.
 * <p>
 * Every answer comes from one measuring instrument, and a second one must not exist: metrics
 * taken without the fractional-metrics hint that {@link GraphicUtils#setRenderingHints} turns
 * on wrap a paragraph at a different word than the paint pass does.
 * <p>
 * An answer is in the unit the measured font was sized in, which a query cannot know: a font
 * sized in staff spaces answers in staff spaces, and one sized in document pixels answers in
 * document pixels. Text is measured at document scale with no view, so one layout holds at every
 * zoom; a measurement taken off a live {@code Graphics2D} is valid only at that paint pass's
 * zoom, so it is drawn from at once and never stored or fed into layout.
 * <p>
 * Text that is measured here and later drawn is also <em>shaped</em> here, through
 * {@link #glyphVector} and {@link #textLayout}.
 */
public final class TextMeasurement {

    /**
     * Deliberately never disposed: it is the application's measuring instrument.
     */
    private static final Graphics2D MEASURING_GRAPHICS;

    /**
     * Package-private on purpose: every use crossing the package boundary goes through a named
     * query ({@link #glyphVector}, {@link #textLayout}, {@link #textAdvancePx} and the rest).
     */
    static final FontRenderContext SCREEN_FRC;

    static {
        if (GraphicsEnvironment.isHeadless()) {
            MEASURING_GRAPHICS = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        } else {
            var config = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice()
                .getDefaultConfiguration();
            MEASURING_GRAPHICS = config.createCompatibleImage(1, 1).createGraphics();
        }

        GraphicUtils.setRenderingHints(MEASURING_GRAPHICS);
        SCREEN_FRC = MEASURING_GRAPHICS.getFontRenderContext();
    }

    private TextMeasurement() {}

    // ---- Laying text out under the instrument ----

    /**
     * A vector built under another context shifts glyph ink against everything measured through
     * this class.
     *
     * @param text may be empty, which yields a vector of no glyphs
     */
    public static GlyphVector glyphVector(String text, Font font) {
        return font.createGlyphVector(SCREEN_FRC, text);
    }

    /**
     * @param text must carry a font attribute and must not be empty
     */
    public static TextLayout textLayout(AttributedString text) {
        return new TextLayout(text.getIterator(), SCREEN_FRC);
    }

    // ---- Advance: how far the pen moves ----

    /**
     * The advance, not the ink.
     *
     * @param text a single line of text, which must not be empty
     */
    public static double textAdvancePx(Font font, String text) {
        return new TextLayout(text, font, SCREEN_FRC).getAdvance();
    }

    /**
     * @param font sized in document pixels
     * @param text a single line of text, which must not be empty
     */
    public static Ss textWidthSs(Font font, String text) {
        return new Ss(DocumentScale.pxToSs(textAdvancePx(font, text)));
    }

    // ---- Ink: where the marks actually land ----

    /**
     * The three components come from one layout of one string.
     *
     * @param font sized in document pixels
     * @param text empty yields {@link LyricBoxMetrics#EMPTY}
     */
    public static LyricBoxMetrics lyricBoxMetricsSs(Font font, String text) {
        if (text.isEmpty()) {
            return LyricBoxMetrics.EMPTY;
        }

        var layout = new TextLayout(text, font, SCREEN_FRC);
        var bounds = layout.getBounds();
        return new LyricBoxMetrics(
            DocumentScale.pxToSs(layout.getAdvance()),
            DocumentScale.pxToSs(bounds.getX()),
            DocumentScale.pxToSs(bounds.getX() + bounds.getWidth())
        );
    }

    /**
     * Returns {@code null} if the text is empty and so has no ink at all.
     * <p>
     * The bounds are a resolution-independent outline extent, not
     * {@link GlyphVector#getPixelBounds}: pixel snapping makes a measurement step as the zoom
     * changes.
     */
    @Nullable
    public static Rectangle2D visualBounds(String text, Font font) {
        if (text.isEmpty()) {
            return null;
        }

        return glyphVector(text, font).getVisualBounds();
    }

    /**
     * For a caller whose text cannot be empty. An empty {@code text} is shown as a fatal error
     * and the application exits, so this method does not return in that case.
     */
    public static Rectangle2D requireVisualBounds(String text, Font font) {
        var bounds = visualBounds(text, font);

        if (bounds == null) {
            throw RuntimeError.exit("cannot measure the ink of empty text");
        }

        return bounds;
    }

    public static double inkHeight(Rectangle2D visualBounds) {
        return -visualBounds.getY();
    }

    /**
     * Some fonts (e.g. script fonts like Sign Painter) render glyph ink beyond the font's
     * nominal ascent and descent. Only the first and last lines of a block sit at its top
     * and bottom edges, so only their ink can be clipped; excess ink on interior lines
     * bleeds into the inter-line gap instead. The overshoot is rounded up, because it is
     * a size: a fraction of a pixel left unpadded is a fraction of a pixel clipped.
     *
     * @param lineBounds {@code null} for a blank line
     */
    public static int extraInkAbovePx(@Nullable Rectangle2D lineBounds, int ascent) {
        if (lineBounds == null) {
            return 0;
        }

        return Math.max(0, (int) Math.ceil(inkHeight(lineBounds)) - ascent);
    }

    /**
     * Rounded up for the same reason as {@link #extraInkAbovePx}.
     *
     * @param lineBounds {@code null} for a blank line
     */
    public static int extraInkBelowPx(@Nullable Rectangle2D lineBounds, int descent) {
        if (lineBounds == null) {
            return 0;
        }

        return Math.max(0, (int) Math.ceil(lineBounds.getMaxY()) - descent);
    }

    // ---- The font's own vertical design: ascent, descent, leading ----

    /**
     * Measure through this rather than a component's own {@link JComponent#getFontMetrics(Font)},
     * which lacks the fractional-metrics hint and so can wrap at a different word than the paint
     * pass.
     */
    public static FontMetrics fontMetrics(Font font) {
        return MEASURING_GRAPHICS.getFontMetrics(font);
    }

    /**
     * Returns the tight height of a text block with {@code lineCount} lines in the
     * given {@code metrics}: each line is ascent + descent tall, with the font's
     * leading inserted only between lines, never below the last descender.
     *
     * @param metrics   the metrics of the font the block is set in
     * @param lineCount the number of lines in the block, at least 1
     * @return the block's height in whole pixels, in the units {@code metrics} answers in
     */
    public static int textBlockHeight(FontMetrics metrics, int lineCount) {
        var glyphHeight = metrics.getAscent() + metrics.getDescent();
        return lineCount * glyphHeight + (lineCount - 1) * metrics.getLeading();
    }

    /**
     * The single-line case of {@link #textBlockHeight}, in staff spaces, from float metrics.
     *
     * @param font sized in document pixels
     */
    public static Ss textHeightSs(Font font) {
        var lineMetrics = font.getLineMetrics("", SCREEN_FRC);
        return new Ss(DocumentScale.pxToSs(lineMetrics.getAscent() + lineMetrics.getDescent()));
    }

    /**
     * @param font sized in document pixels
     */
    public static Ss fontAscentSs(Font font) {
        return new Ss(DocumentScale.pxToSs(font.getLineMetrics("", SCREEN_FRC).getAscent()));
    }

    /**
     * @param font sized in document pixels
     */
    public static Ss fontDescentSs(Font font) {
        return new Ss(DocumentScale.pxToSs(font.getLineMetrics("", SCREEN_FRC).getDescent()));
    }

    // ---- Sizing a font for the coordinate space it will be drawn in ----

}
