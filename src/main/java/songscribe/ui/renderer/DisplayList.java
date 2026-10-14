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
 * Everything in the list is in staff spaces, relative to the origin the renderers drew from.
 * Position is not part of the list: a caller translates {@code g2} before {@link #replay} and
 * offsets {@link #inkBoundsSs} by the same amount. {@link #inkBoundsSs} and {@link #replay} read
 * the same list, so a component's bounds cannot disagree with what it paints.
 *
 * @param drawables the recorded ink, in draw order
 */
public record DisplayList(List<Drawable> drawables) {

    public static final DisplayList EMPTY = new DisplayList(List.of());

    public sealed interface Drawable permits GlyphInk, ShapeInk {

        Rectangle2D boundsSs();

        /**
         * The color is the caller's: the renderers set none, so an overlay applies its preview
         * color once.
         */
        void paint(Graphics2D g2);
    }

    /**
     * Bounds come from SMuFL metadata rather than the outline — see {@link RecordingGraphics2D}.
     *
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
     * A stroked shape is recorded as its outline, so replay always fills.
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
     * Null if nothing was recorded: no ink.
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

    public void replay(Graphics2D g2) {
        for (var drawable : drawables) {
            drawable.paint(g2);
        }
    }
}
