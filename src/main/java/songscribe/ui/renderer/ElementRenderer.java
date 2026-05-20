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


import songscribe.dom.LineElement;

/**
 * Strategy interface for rendering LineElement instances.
 * <p>
 * Each element type has a corresponding renderer implementation that knows
 * how to draw that element. Elements are passive data; rendering is delegated.
 *
 * @param <T> The LineElement type this renderer handles
 */
@FunctionalInterface
public interface ElementRenderer<T extends LineElement> {

    /**
     * Renders the element to the graphics context.
     *
     * @param inv     The per-line invariants
     * @param frame   The per-element frame
     * @param element The element to render
     * @param g2      The graphics context
     */
    void render(LineInvariants inv, ElementFrame frame, T element, Graphics2D g2);


}
