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
import javax.swing.JComponent;

import org.jspecify.annotations.Nullable;

import songscribe.dom.DocumentScale;
import songscribe.dom.Ss;
import songscribe.error.RuntimeError;
import songscribe.util.GraphicUtils;

/**
 * Every text measurement the application makes.
 * <p>
 * A query here answers exactly one of three questions, and they are not
 * interchangeable — asking the wrong one is a visible defect, not a rounding
 * difference:
 * <ul>
 *   <li><b>Advance</b> — how far the pen moves. This is what positions the next
 *   glyph, and what decides where a run of text ends.</li>
 *   <li><b>Ink</b> — where the marks actually land. Ink overshoots the advance for
 *   glyphs like an italic descender, and starts before the drawing origin for the
 *   negative left bearing of a "W". Sizing a box from the advance where ink is
 *   meant clips the glyph.</li>
 *   <li><b>The font's own vertical design</b> — ascent, descent and leading, which
 *   the font declares once and which do not depend on any particular text.</li>
 * </ul>
 * <p>
 * Every answer comes from one measuring instrument, the private scratch graphics
 * this class holds, and a second one cannot exist. Metrics taken without the
 * fractional-metrics hint that {@link GraphicUtils#setRenderingHints} turns on wrap
 * a paragraph at a different word than the paint pass does, and a component sized
 * from those metrics clips its own text. One ruler for measuring and for drawing
 * makes that disagreement impossible.
 */
public final class TextMeasurement {

    /**
     * A 1×1 scratch graphics carrying {@link GraphicUtils#setRenderingHints}, and so the
     * origin of both {@link #SCREEN_FRC} and every {@link #fontMetrics} answer.
     * <p>
     * Deliberately never disposed: it is the application's measuring instrument, and
     * rebuilding an image and a graphics context for each measurement would put that
     * cost on every layout pass.
     */
    private static final Graphics2D MEASURING_GRAPHICS;

    /**
     * A {@link FontRenderContext} derived from the default screen device with the
     * application's standard rendering hints applied. Use for layout-time glyph
     * measurement so that text advances match what is actually rendered on screen.
     */
    public static final FontRenderContext SCREEN_FRC;

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

    // ---- Advance: how far the pen moves ----

    /**
     * Given a text block with one or more lines, calculates the width.
     */
    public static double textBlockWidth(
        String text,
        Graphics2D g2
    ) {
        if (text.isEmpty()) {
            return 0d;
        }

        var context = g2.getFontRenderContext();
        var font = g2.getFont();
        var maxWidth = 0d;
        var lines = text.split("\n");

        for (var line : lines) {
            if (!line.isEmpty()) {
                var layout = new TextLayout(line, font, context);
                maxWidth = Math.max(maxWidth, layout.getBounds().getWidth());
            }
        }

        return maxWidth;
    }

    /** Returns the width of {@code text} in staff-space units for the given font. */
    public static Ss textWidthSs(Font font, String text) {
        return new Ss(DocumentScale.pxToSs(new TextLayout(text, font, SCREEN_FRC).getAdvance()));
    }

    // ---- Ink: where the marks actually land ----

    /**
     * Returns the visual (ink) bounds of {@code text} rendered in {@code font} under
     * {@link #SCREEN_FRC}, or {@code null} if the text is empty.
     * <p>
     * Uses {@link GlyphVector#getVisualBounds} rather than
     * {@link FontMetrics#stringWidth}, because advance width alone does not account for
     * glyphs whose ink extends past their advance (e.g. italic descenders) or before the
     * drawing origin (the negative left bearing of a "W"). The returned rectangle's
     * {@code width} is the full ink span, and {@code x} the ink's offset from the drawing
     * origin.
     * <p>
     * The bounds are a resolution-independent outline extent, not
     * {@link GlyphVector#getPixelBounds}, which snaps to whole device pixels: pixel
     * snapping makes a measurement jump as the font sweeps across pixel boundaries, which
     * is visible as stepping in anything sized or centered from it while the zoom changes.
     */
    @Nullable
    public static Rectangle2D visualBounds(String text, Font font) {
        if (text.isEmpty()) {
            return null;
        }

        return font.createGlyphVector(SCREEN_FRC, text).getVisualBounds();
    }

    /**
     * The ink bounds of {@code text}, for a caller whose text cannot be empty — a literal,
     * or a value whose emptiness the caller has already excluded. Fatal if it is empty,
     * because an empty string has no ink to place anything against.
     */
    public static Rectangle2D requireVisualBounds(String text, Font font) {
        var bounds = visualBounds(text, font);

        if (bounds == null) {
            throw RuntimeError.exit("cannot measure the ink of empty text");
        }

        return bounds;
    }

    /**
     * Returns the ink height above the baseline for a glyph's visual bounds.
     * <p>
     * Visual bounds extend upward from the baseline into negative Y, so the
     * top must be negated to yield a positive height. The result is in the
     * same units the font was sized in.
     */
    public static double inkHeight(Rectangle2D visualBounds) {
        return -visualBounds.getY();
    }

    /**
     * Extra room a text block needs above its first baseline because that line's ink
     * overshoots the font's nominal {@code ascent} — 0 when {@code lineBounds} is null
     * (a blank line) or the ink stays within the ascent.
     * <p>
     * Some fonts (e.g. script fonts like Sign Painter) render glyph ink beyond the font's
     * nominal ascent and descent. Only the first and last lines of a block sit at its top
     * and bottom edges, so only their ink can be clipped; excess ink on interior lines
     * bleeds into the inter-line gap instead. The overshoot is rounded up, because it is
     * a size: a fraction of a pixel left unpadded is a fraction of a pixel clipped.
     */
    public static int extraInkAbove(@Nullable Rectangle2D lineBounds, int ascent) {
        if (lineBounds == null) {
            return 0;
        }

        return Math.max(0, (int) Math.ceil(inkHeight(lineBounds)) - ascent);
    }

    /**
     * The below-the-baseline counterpart of {@link #extraInkAbove}: extra room the block
     * needs below its last baseline because that line's ink overshoots the font's nominal
     * {@code descent}. Rounded up for the same reason.
     */
    public static int extraInkBelow(@Nullable Rectangle2D lineBounds, int descent) {
        if (lineBounds == null) {
            return 0;
        }

        return Math.max(0, (int) Math.ceil(lineBounds.getMaxY()) - descent);
    }

    // ---- The font's own vertical design: ascent, descent, leading ----

    /**
     * The {@link FontMetrics} for {@code font} under {@link #SCREEN_FRC}.
     * <p>
     * Measure through this rather than through a component's own
     * {@link JComponent#getFontMetrics(Font)}: the component's metrics are built without
     * the fractional-metrics hint that {@link GraphicUtils#setRenderingHints} turns on, so
     * their advances can wrap a paragraph at a different word than the paint pass would.
     * Where a component is sized to the text it measures, that disagreement is text
     * clipped at paint time. One ruler for measuring and drawing makes it impossible.
     */
    public static FontMetrics fontMetrics(Font font) {
        return MEASURING_GRAPHICS.getFontMetrics(font);
    }

    /**
     * Returns the tight height of a text block with {@code lineCount} lines in the
     * given {@code metrics}: each line is ascent + descent tall, with the font's
     * leading inserted only between lines, never below the last descender.
     */
    public static int textBlockHeight(FontMetrics metrics, int lineCount) {
        var glyphHeight = metrics.getAscent() + metrics.getDescent();
        return lineCount * glyphHeight + (lineCount - 1) * metrics.getLeading();
    }

    /**
     * Returns the text height (ascent + descent) in staff-space units for the given font.
     * <p>
     * This is the single-line case of {@link #textBlockHeight}, in staff spaces: both are
     * ascent + descent per line, and both insert the font's leading only between lines,
     * never below the last descender. There is no third height query to add.
     * <p>
     * The two are separate because they read different instruments: this one reads
     * {@link java.awt.font.LineMetrics}, whose ascent and descent are floats, while
     * {@link #textBlockHeight} reads {@link FontMetrics}, whose are integers. Collapsing
     * them would force the float caller through a rounded value.
     */
    public static Ss textHeightSs(Font font) {
        var lineMetrics = font.getLineMetrics("", SCREEN_FRC);
        return new Ss(DocumentScale.pxToSs(lineMetrics.getAscent() + lineMetrics.getDescent()));
    }

    /** Returns the font ascent in staff-space units for the given font. */
    public static Ss fontAscentSs(Font font) {
        return new Ss(DocumentScale.pxToSs(font.getLineMetrics("", SCREEN_FRC).getAscent()));
    }

    /** Returns the font descent in staff-space units for the given font. */
    public static Ss fontDescentSs(Font font) {
        return new Ss(DocumentScale.pxToSs(font.getLineMetrics("", SCREEN_FRC).getDescent()));
    }

    // ---- Sizing the font a measurement will be taken in ----

    /** Returns {@code font} scaled from pixel units to staff-space units. */
    public static Font scaleFont(Font font) {
        return font.deriveFont((float) DocumentScale.pxToSs(font.getSize()));
    }
}
