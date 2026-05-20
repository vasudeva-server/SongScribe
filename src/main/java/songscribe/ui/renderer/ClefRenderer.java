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


import songscribe.smufl.SMuFLGlyph;
import songscribe.dom.Clef;

/**
 * Renders the treble clef at the start of a staff line.
 * <p>
 * The treble clef is positioned so that its inner curve wraps around
 * the G line (second line from bottom, staff line index 3).
 */
public final class ClefRenderer extends BaseElementRenderer<Clef> {

    /** Singleton instance. */
    private static final ClefRenderer INSTANCE = new ClefRenderer();

    /**
     * Private constructor - use {@link #getInstance()}.
     */
    private ClefRenderer() {
    }

    /**
     * Returns the singleton instance.
     */
    public static ClefRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    protected void renderElement(
        Clef element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        // The clef's position comes from the element
        var x = element.getXSs();

        // The SMuFL G clef origin is on the G line (second line from bottom,
        // one staff space below the middle line)
        var baseline = ctx.getMiddleLineYSs() + 1.0;

        drawBravuraGlyph(g2, SMuFLGlyph.G_CLEF, x, baseline);
    }

}
