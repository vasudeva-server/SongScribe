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

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.util.GraphicsState;

/**
 * The ink a renderer produced, captured by {@link RecordingGraphics2D} instead of painted.
 * <p>
 * Everything in the list is in staff spaces, relative to the origin the renderers drew from — for
 * an overlay that is the target note's origin. Position is therefore <em>not</em> part of the list:
 * a caller translates {@code g2} before {@link #replay} and offsets {@link #inkBoundsSs} by the
 * same amount, so moving an overlay never rebuilds it. The music font is fixed-size
 * ({@link songscribe.smufl.BravuraFont#SIZE_SS}) with zoom applied as a transform, so a zoom change
 * does not rebuild it either.
 * <p>
 * {@link #inkBoundsSs} and {@link #replay} read the same list, which is the point: a component's
 * bounds cannot disagree with what it paints.
 *
 * @param drawables the recorded ink, in draw order
 */
public record DisplayList(List<Drawable> drawables) {

    /** A display list with no ink. */
    public static final DisplayList EMPTY = new DisplayList(List.of());

    /** One recorded drawing operation: it knows the ink it covers and how to paint itself. */
    public sealed interface Drawable permits GlyphInk, ShapeInk {

        /** The ink this drawable covers, in staff spaces relative to the recording origin. */
        Rectangle2D boundsSs();

        /**
         * Paints this drawable. The color is the caller's — the renderers deliberately do not set
         * one, so a display list is monochrome and an overlay applies its preview color once.
         */
        void paint(Graphics2D g2);
    }

    /**
     * A glyph drawn with {@code drawString} or {@code drawGlyphVector}.
     * <p>
     * The placement transform is the recording transform composed with the pen position, so replay
     * needs no knowledge of how the renderer got there. Bounds come from SMuFL metadata rather than
     * the outline — see {@link RecordingGraphics2D}.
     *
     * @param glyphVector the glyph to draw, at the font size it was recorded with
     * @param placement   maps the glyph's pen origin into the recording space
     * @param boundsSs    metadata ink bounds, already transformed by {@code placement}
     */
    public record GlyphInk(GlyphVector glyphVector, AffineTransform placement, Rectangle2D boundsSs)
        implements Drawable {

        @Override
        public void paint(Graphics2D g2) {
            try (var _ = GraphicsState.save(g2, GraphicsState.Property.TRANSFORM)) {
                g2.transform(placement);
                g2.drawGlyphVector(glyphVector, 0, 0);
            }
        }
    }

    /**
     * A shape, already flattened by the transform in force when it was recorded. A stroked shape is
     * recorded as its {@code createStrokedShape} outline, so replay always fills.
     *
     * @param shapeSs the ink, in staff spaces relative to the recording origin
     */
    public record ShapeInk(Shape shapeSs) implements Drawable {

        @Override
        public Rectangle2D boundsSs() {
            return shapeSs.getBounds2D();
        }

        @Override
        public void paint(Graphics2D g2) {
            g2.fill(shapeSs);
        }
    }

    /**
     * The union of every drawable's ink, or null if nothing was recorded. Null means "no ink",
     * which for an overlay means "nothing to show".
     */
    @Nullable
    public Rectangle2D inkBoundsSs() {
        Rectangle2D bounds = null;

        for (var drawable : drawables) {
            var drawableBounds = drawable.boundsSs();

            if (bounds == null) {
                bounds = drawableBounds;
            } else {
                bounds = bounds.createUnion(drawableBounds);
            }
        }

        return bounds;
    }

    /** Paints the recorded ink in draw order, in the caller's current color. */
    public void replay(Graphics2D g2) {
        for (var drawable : drawables) {
            drawable.paint(g2);
        }
    }
}
