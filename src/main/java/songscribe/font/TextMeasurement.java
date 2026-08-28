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
 * <p>
 * Text that is measured here and later drawn is also <em>shaped</em> here, through
 * {@link #glyphVector} and {@link #textLayout}: a shaped run carries the positioning
 * decisions of the context it was built under, so one built elsewhere draws somewhere
 * other than where it measured.
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
     * application's standard rendering hints applied, so that text advances match what is
     * actually rendered on screen.
     * <p>
     * Package-private on purpose: it is the instrument, not a service. A caller outside this
     * package that holds it can build a {@link TextLayout} or a {@link GlyphVector} whose
     * answer nothing here names, and the codebase then has two vocabularies for the same
     * measurement. Every use crossing the package boundary goes through a named query —
     * {@link #glyphVector}, {@link #textLayout}, {@link #textAdvancePx} and the rest.
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
     * Lays {@code text} out in {@code font} under {@link #SCREEN_FRC}, ready to be measured,
     * drawn, or cached and then drawn.
     * <p>
     * A vector is bound to the context it was built under: it carries that context's
     * positioning decisions, so one built elsewhere and drawn here shifts glyph ink against
     * everything measured through this class.
     *
     * @param text the text to shape; may be empty, which yields a vector of no glyphs
     * @param font the font to shape it in
     * @return the shaped glyphs, positioned as the paint pass will place them
     */
    public static GlyphVector glyphVector(String text, Font font) {
        return font.createGlyphVector(SCREEN_FRC, text);
    }

    /**
     * Lays {@code text} — which carries its own font and style attributes — out under
     * {@link #SCREEN_FRC}, for the same reason as {@link #glyphVector}.
     *
     * @param text the attributed text to lay out; must carry a font attribute and must not
     *             be empty
     * @return the laid-out line, which answers its own advance, ascent and descent and can
     *         draw itself
     */
    public static TextLayout textLayout(AttributedString text) {
        return new TextLayout(text.getIterator(), SCREEN_FRC);
    }

    // ---- Advance: how far the pen moves ----

    /**
     * The distance the pen moves across a single line of {@code text} set in {@code font} —
     * the advance, not the ink, so a glyph whose marks overhang its advance is not counted
     * past it.
     *
     * @param font the font to set the text in
     * @param text a single line of text, which must not be empty
     * @return the advance width in pixels at the size {@code font} is expressed in
     */
    public static double textAdvancePx(Font font, String text) {
        return new TextLayout(text, font, SCREEN_FRC).getAdvance();
    }

    /**
     * The staff-space counterpart of {@link #textAdvancePx}, for a font sized in document
     * pixels.
     *
     * @param font the font to set the text in, sized in document pixels
     * @param text a single line of text, which must not be empty
     * @return the advance width in staff-space units
     */
    public static Ss textWidthSs(Font font, String text) {
        return new Ss(DocumentScale.pxToSs(textAdvancePx(font, text)));
    }

    // ---- Ink: where the marks actually land ----

    /**
     * Advance plus visual left and right extents for a lyric box holding {@code text}, all in
     * staff-space units.
     * <p>
     * Produced here rather than at the call site because the three components must come from
     * one layout of one string: mixing an advance taken from one measurement with ink taken
     * from another sizes a box that does not match the text it holds.
     *
     * @param font the lyrics font, sized in document pixels
     * @param text the lyric text; empty yields {@link LyricBoxMetrics#EMPTY}
     * @return the box's advance and ink extents in staff-space units
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
     *
     * @param text the text to measure
     * @param font the font to measure it in
     * @return the baseline-relative ink rectangle, in the units {@code font} is sized in, or
     *         {@code null} if {@code text} is empty and so has no ink at all
     */
    @Nullable
    public static Rectangle2D visualBounds(String text, Font font) {
        if (text.isEmpty()) {
            return null;
        }

        return glyphVector(text, font).getVisualBounds();
    }

    /**
     * The ink bounds of {@code text}, for a caller whose text cannot be empty — a literal,
     * or a value whose emptiness the caller has already excluded.
     *
     * @param text the text to measure, which must not be empty
     * @param font the font to measure it in
     * @return the baseline-relative ink rectangle, in the units {@code font} is sized in
     * @effects if {@code text} is empty, the failure is logged, shown to the user as a fatal
     *          error and the application exits, so this method does not return in that case:
     *          an empty string has no ink to place anything against
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
     * top must be negated to yield a positive height.
     *
     * @param visualBounds baseline-relative ink bounds, as {@link #visualBounds} returns them
     * @return the height from the baseline to the ink's top edge, in the same units the font
     *         the bounds were taken in was sized in
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
     *
     * @param lineBounds the first line's baseline-relative ink bounds, or {@code null} for a
     *                   blank line
     * @param ascent     the font's nominal ascent, in whole pixels
     * @return the overshoot in whole pixels, never negative
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
     *
     * @param lineBounds the last line's baseline-relative ink bounds, or {@code null} for a
     *                   blank line
     * @param descent    the font's nominal descent, in whole pixels
     * @return the overshoot in whole pixels, never negative
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
     *
     * @param font the font to read, at whatever size it is expressed in
     * @return the font's ascent, descent, leading and advances in whole pixels at that size
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
     *
     * @param font the font to read, sized in document pixels
     * @return the single line's ascent + descent in staff-space units
     */
    public static Ss textHeightSs(Font font) {
        var lineMetrics = font.getLineMetrics("", SCREEN_FRC);
        return new Ss(DocumentScale.pxToSs(lineMetrics.getAscent() + lineMetrics.getDescent()));
    }

    /**
     * The font's own nominal ascent — the height it reserves above the baseline, whatever
     * text is set in it.
     *
     * @param font the font to read, sized in document pixels
     * @return the ascent in staff-space units
     */
    public static Ss fontAscentSs(Font font) {
        return new Ss(DocumentScale.pxToSs(font.getLineMetrics("", SCREEN_FRC).getAscent()));
    }

    /**
     * The font's own nominal descent — the depth it reserves below the baseline, whatever
     * text is set in it.
     *
     * @param font the font to read, sized in document pixels
     * @return the descent in staff-space units
     */
    public static Ss fontDescentSs(Font font) {
        return new Ss(DocumentScale.pxToSs(font.getLineMetrics("", SCREEN_FRC).getDescent()));
    }

    // ---- Sizing a font for the coordinate space it will be drawn in ----

}
