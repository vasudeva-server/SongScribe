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

package songscribe.smufl;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;

import songscribe.font.LocalFonts;

/**
 * The Bravura (SMuFL) music font, and the glyph outlines drawn from it.
 * <p>
 * The font lives here because layout needs its outlines and must not depend on the rendering
 * package.
 */
public final class BravuraFont {

    private static final String FONT_FILE = "Bravura.otf";

    /**
     * The point size at which the font's own units coincide with staff spaces: SMuFL fixes one em
     * at four staff spaces.
     */
    public static final float SIZE_SS = 4.0f;

    private static final FontRenderContext OUTLINE_FRC = new FontRenderContext(null, false, false);

    private BravuraFont() {
    }

    /**
     * Callers needing another size should {@code deriveFont()} from it.
     *
     * <p>Every {@link SMuFLGlyph} constant must name a glyph this font draws ink for; nothing
     * checks it.
     */
    public static Font font() {
        return Holder.INSTANCE;
    }

    /**
     * Initialization-on-demand holder. The locking is not incidental: the font is reached from
     * static initializers on whatever thread touches them first, and two threads entering
     * {@code Font.createFont} at once would fail through a fatal dialog.
     */
    private static final class Holder {
        static final Font INSTANCE = LocalFonts.load(FONT_FILE, SIZE_SS);
    }

    /**
     * The outline is scaled uniformly so its bounding box matches the glyph's declared
     * {@link SMuFLMetadata} bbox, which the rest of the layout is built from.
     *
     * <p>The outline is non-empty and has positive width (see {@link #font()}); nothing here
     * re-checks it.
     */
    public static Shape glyphOutline(SMuFLGlyph glyph) {
        var outline = font().createGlyphVector(OUTLINE_FRC, glyph.asString()).getOutline();
        var scale = SMuFLMetadata.bboxSs(glyph).widthSs() / outline.getBounds2D().getWidth();

        return AffineTransform.getScaleInstance(scale, scale).createTransformedShape(outline);
    }
}
