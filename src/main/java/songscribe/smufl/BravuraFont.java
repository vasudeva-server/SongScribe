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

import module java.desktop;

import songscribe.util.MyFontUtils;

/**
 * The Bravura (SMuFL) music font, and the glyph outlines drawn from it.
 * <p>
 * The font lives here rather than with the renderers because it is the other half of the data this
 * package already owns: {@link SMuFLMetadata} declares where a glyph's ink is, and the font is what
 * actually draws it. Layout needs both — a script collides by its outline, not by its box — and
 * layout must not depend on the rendering package.
 */
public final class BravuraFont {

    /** The font resource, loaded from {@code /fonts/}. */
    private static final String FONT_FILE = "Bravura.otf";

    /**
     * The point size at which the font's own units coincide with staff spaces. SMuFL fixes one em at
     * four staff spaces, so a glyph drawn at this size measures staff spaces directly — which is why
     * renderers can draw it straight into a {@code pixelsPerStaffSpace} transform, and why an outline
     * taken at this size needs no scaling to be compared against a {@link SMuFLMetadata} bbox.
     */
    public static final float SIZE_SS = 4.0f;

    /**
     * Outlines are pure geometry: no hinting, no antialiasing, no device transform. A fixed context
     * keeps {@link #glyphOutline} independent of whatever {@code Graphics2D} happens to be current,
     * and lets it run headless.
     */
    private static final FontRenderContext OUTLINE_FRC = new FontRenderContext(null, false, false);

    private BravuraFont() {
    }

    /**
     * The Bravura music font at {@link #SIZE_SS}. Callers needing another size should
     * {@code deriveFont()} from it.
     */
    public static Font font() {
        return Holder.INSTANCE;
    }

    /**
     * Initialization-on-demand holder: the JVM's class-initialization lock makes this lazy and
     * thread-safe without synchronizing every call.
     * <p>
     * The locking is not incidental. Both {@code NoteAttachedStacker} (deriving the staccato's
     * reserved outline) and {@code RenderingUtils} (drawing glyphs) reach the font from their own
     * static initializers, on whatever thread touches them first. An unguarded {@code if (font ==
     * null)} lets two threads enter {@code Font.createFont} at once, and a failure there is not
     * benign: {@code MyFontUtils.getLocalFont} reports it through {@code RuntimeError.missingResource},
     * which shows a fatal dialog and exits.
     */
    private static final class Holder {
        static final Font INSTANCE = MyFontUtils.getLocalFont(FONT_FILE, SIZE_SS);
    }

    /**
     * {@code glyph}'s real ink outline, in staff spaces, Y-down, positioned as the font draws it from
     * a pen origin at {@code (0, 0)}.
     * <p>
     * The outline is scaled uniformly so its bounding box matches the glyph's declared
     * {@link SMuFLMetadata} bbox. The two disagree slightly — Bravura's staccato dot draws 0.34375 ss
     * wide where its metadata claims 0.336 — and everything else in the layout (a script's footprint,
     * its centring on the notehead) is built from the metadata. LilyPond reconciles the same
     * disagreement the same way, scaling the FreeType outline to the metric box before it builds a
     * skyline from it ({@code stencil-integral.cc} {@code add_named_glyph_segments}).
     */
    public static Shape glyphOutline(SMuFLGlyph glyph) {
        var outline = font().createGlyphVector(OUTLINE_FRC, glyph.asString()).getOutline();
        var outlineWidthSs = outline.getBounds2D().getWidth();

        // A missing glyph renders as an empty or zero-width outline, whose scale would come out
        // infinite and silently poison every profile derived from it — and those profiles are held
        // in static fields for the life of the process.
        if (outlineWidthSs <= 0.0) {
            throw new IllegalStateException("Bravura drew " + glyph + " with a zero-width outline");
        }

        var scale = SMuFLMetadata.requireBBox(glyph).width() / outlineWidthSs;

        return AffineTransform.getScaleInstance(scale, scale).createTransformedShape(outline);
    }
}
