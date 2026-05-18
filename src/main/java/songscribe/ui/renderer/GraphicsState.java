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

import module java.desktop;

import org.jspecify.annotations.Nullable;


/**
 * Saves and restores {@link Graphics2D} properties using try-with-resources.
 * <p>
 * Usage:
 * <pre>
 * try (var ignored = GraphicsState.save(g2, Property.COLOR, Property.STROKE)) {
 *     g2.setColor(Color.RED);
 *     g2.setStroke(new BasicStroke(2f));
 *     // ... render ...
 * } // color and stroke are automatically restored
 * </pre>
 */
public final class GraphicsState implements AutoCloseable {

    public enum Property {
        COLOR,
        STROKE,
        FONT,
        TRANSFORM,
        ANTIALIASING,
        STROKE_CONTROL,
        CLIP
    }

    private final Graphics2D g2;
    private final int propertyMask;
    private @Nullable Color color;
    private @Nullable Stroke stroke;
    private @Nullable Font font;
    private @Nullable AffineTransform transform;
    private @Nullable Object antialiasing;
    private @Nullable Object strokeControl;
    private @Nullable Shape clip;

    private GraphicsState(Graphics2D g2, int propertyMask) {
        this.g2 = g2;
        this.propertyMask = propertyMask;
    }

    /**
     * Saves the specified properties from the graphics context.
     *
     * @param g2         the graphics context
     * @param properties the properties to save and restore
     * @return an {@link AutoCloseable} that restores the saved properties when closed
     */
    public static GraphicsState save(
        Graphics2D g2,
        Property ... properties
    ) {
        var mask = 0;

        for (var property : properties) {
            mask |= (1 << property.ordinal());
        }

        var state = new GraphicsState(g2, mask);

        if (state.has(Property.COLOR)) {
            state.color = g2.getColor();
        }

        if (state.has(Property.STROKE)) {
            state.stroke = g2.getStroke();
        }

        if (state.has(Property.FONT)) {
            state.font = g2.getFont();
        }

        if (state.has(Property.TRANSFORM)) {
            state.transform = g2.getTransform();
        }

        if (state.has(Property.ANTIALIASING)) {
            state.antialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        }

        if (state.has(Property.STROKE_CONTROL)) {
            state.strokeControl = g2.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
        }

        if (state.has(Property.CLIP)) {
            state.clip = g2.getClip();
        }

        return state;
    }

    @Override
    public void close() {
        if (has(Property.CLIP)) {
            g2.setClip(clip);
        }

        if (has(Property.STROKE_CONTROL) && strokeControl != null) {
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, strokeControl);
        }

        if (has(Property.ANTIALIASING) && antialiasing != null) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
        }

        if (has(Property.TRANSFORM) && transform != null) {
            g2.setTransform(transform);
        }

        if (has(Property.FONT) && font != null) {
            g2.setFont(font);
        }

        if (has(Property.STROKE) && stroke != null) {
            g2.setStroke(stroke);
        }

        if (has(Property.COLOR) && color != null) {
            g2.setColor(color);
        }
    }

    private boolean has(Property property) {
        return (propertyMask & (1 << property.ordinal())) != 0;
    }
}
