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

import songscribe.smufl.SMuFLMetadata;
import songscribe.util.GraphicsState;

import static songscribe.util.GraphicsState.Property.FONT;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

/**
 * A SMuFL line segment glyph tiled {@code count} times along one baseline, as a trill's wavy
 * extension or a missing-lyric squiggle is drawn.
 * <p>
 * Every glyph is drawn at the {@link ScaledGlyph}'s own scale and advance. The run is never
 * stretched or squeezed to fit a span, so a span that is not a whole number of glyphs wide
 * leaves a gap, and a span narrower than one glyph is overflowed. The two factories choose
 * the count and the position that fit a span. All coordinates are in staff spaces, for a
 * graphics context that carries the staff-space transform.
 *
 * @param tile        the glyph tiled along the run
 * @param startXSs    where the first glyph's pen origin sits
 * @param count       how many glyphs the run holds, at least one
 * @param baselineYSs the Y every glyph is drawn at
 */
record GlyphRun(ScaledGlyph tile, double startXSs, int count, double baselineYSs) {

    /**
     * The run of as many whole glyphs as fit between {@code startXSs} and {@code endXSs}, at
     * least one, with the first glyph's pen origin at {@code startXSs}.
     */
    static GlyphRun startingAt(ScaledGlyph tile, double startXSs, double endXSs, double baselineYSs) {
        return new GlyphRun(tile, startXSs, fittingCount(tile, endXSs - startXSs), baselineYSs);
    }

    /**
     * The run of as many whole glyphs as fit between {@code leftXSs} and {@code rightXSs}, at
     * least one, centered on that span. A run wider than the span overflows it equally on both
     * sides.
     */
    static GlyphRun centeredOn(ScaledGlyph tile, double leftXSs, double rightXSs, double baselineYSs) {
        var count = fittingCount(tile, rightXSs - leftXSs);
        var runWidthSs = count * tile.advanceSs();
        var startXSs = (leftXSs + rightXSs - runWidthSs) / 2;

        return new GlyphRun(tile, startXSs, count, baselineYSs);
    }

    private static int fittingCount(ScaledGlyph tile, double lengthSs) {
        return Math.max(1, (int) Math.floor(lengthSs / tile.advanceSs()));
    }

    /**
     * Draws the run in the music font and the color {@code g2} already carries.
     *
     * @param g2 the graphics context, with the staff-space transform applied
     * @effects leaves the font and transform of {@code g2} as it found them
     */
    void draw(Graphics2D g2) {
        var glyph = tile.glyph();
        var designAdvanceSs = SMuFLMetadata.advanceWidthSs(glyph);

        try (var _ = GraphicsState.save(g2, TRANSFORM, FONT)) {
            g2.setFont(RenderingUtils.MUSIC_FONT);
            g2.translate(startXSs, baselineYSs);
            g2.scale(tile.scale(), tile.scale());

            for (var i = 0; i < count; i++) {
                g2.drawString(glyph.asString(), (float) (i * designAdvanceSs), 0f);
            }
        }
    }
}
