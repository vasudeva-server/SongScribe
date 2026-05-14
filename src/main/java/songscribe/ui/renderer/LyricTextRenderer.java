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

import static songscribe.ui.renderer.GraphicsState.Property.COLOR;
import static songscribe.ui.renderer.GraphicsState.Property.FONT;
import static songscribe.ui.renderer.GraphicsState.Property.TRANSFORM;

import module java.desktop;

import songscribe.music.StaffElement;
import songscribe.ui.layout.ScaleContext;
import songscribe.ui.layout.SongLayoutMetrics;

/**
 * Renders per-element lyric syllable text beneath the staff.
 * <p>
 * Reads {@link songscribe.ui.layout.LyricBoxLayout} entries from the current
 * {@link songscribe.ui.layout.LayoutResult} and draws each syllable at the verse
 * baseline provided by the song-wide
 * {@link SongLayoutMetrics}. Stateless.
 */
public final class LyricTextRenderer extends BaseElementRenderer<StaffElement> {

    private static final LyricTextRenderer INSTANCE = new LyricTextRenderer();

    private LyricTextRenderer() {
    }

    public static LyricTextRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    protected void renderElement(
        StaffElement element,
        Graphics2D g2,
        ElementRenderContext ctx
    ) {
        // While the editor is open, suppress the rendered box so the overlay is the
        // sole representation. Connectors are not suppressed (no connectors in 1a).
        if (ctx.getActivelyEditedElement() == element) {
            return;
        }

        var boxes = ctx.getLayoutResult().getLyricBoxes(element);

        if (boxes.isEmpty()) {
            return;
        }

        var metrics = ctx.getSongLayoutMetrics();
        var lyricRenderMetrics = ctx.getLyricRenderMetrics();
        var scaleContext = ScaleContext.getInstance();
        var pxPerSs = ctx.getPixelsPerStaffSpace();

        try (var ignored = GraphicsState.save(g2, COLOR, FONT, TRANSFORM)) {
            // Strip only the staff-space → pixel factor from the current transform,
            // leaving any HiDPI / zoom scale intact. This makes the renderer go through
            // the same rasterization path JTextField uses: unscaled lyricsFont drawn at
            // integer logical-pixel coordinates. Drawing through the SS scale instead
            // (with scaledLyricsFont) takes a different float-ascent rounding path that
            // shifts the baseline by up to one device pixel relative to the editor.
            g2.scale(1.0 / pxPerSs, 1.0 / pxPerSs);
            g2.setFont(lyricRenderMetrics.lyricsFont());

            for (var box : boxes) {
                g2.setColor(ctx.getLyricColor(ctx.getCurrentElementIndex(), element, box.verseIndex()));

                var baselineYSs = metrics.verseYSsInLine(box.verseIndex());
                var xPx = scaleContext.ssToRoundedPx(box.xSs());
                var yPx = scaleContext.ssToRoundedPx(baselineYSs);
                g2.drawString(box.text(), xPx, yPx);
            }
        }
    }
}
