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
import java.awt.geom.Rectangle2D;

import org.jetbrains.annotations.NotNull;

import songscribe.ui.layout.LineElement;

/**
 * Strategy interface for rendering LineElement instances.
 * <p>
 * Each element type has a corresponding renderer implementation that knows
 * how to draw that element. Elements are passive data; rendering is delegated.
 *
 * @param <T> The LineElement type this renderer handles
 */
public interface ElementRenderer<T extends LineElement> {

    /**
     * Renders the element to the graphics context.
     *
     * @param element The element to render
     * @param g2      The graphics context
     * @param ctx     Rendering context (fonts, debug mode, etc.)
     */
    void render(@NotNull T element, @NotNull Graphics2D g2, @NotNull ElementRenderContext ctx);

    /**
     * Calculates the bounds of the rendered element.
     * <p>
     * May differ from element.getContentBounds() if rendering includes
     * visual effects, anti-aliasing overflow, etc.
     *
     * @param element The element to measure
     * @param ctx     Rendering context
     * @return The visual bounds
     */
    @NotNull
    Rectangle2D getBounds(@NotNull T element, @NotNull ElementRenderContext ctx);
}
