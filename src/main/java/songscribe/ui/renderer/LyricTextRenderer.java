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

import static songscribe.util.GraphicsState.Property.COLOR;
import static songscribe.util.GraphicsState.Property.FONT;
import static songscribe.util.GraphicsState.Property.TRANSFORM;

import module java.desktop;

import songscribe.dom.StaffElement;
import songscribe.layout.LayoutResult;
import songscribe.layout.LyricBoxLayout;
import songscribe.util.GraphicsState;

/**
 * Renders per-element lyric syllable text beneath the staff.
 * <p>
 * Reads {@link LyricBoxLayout} entries from the current
 * {@link LayoutResult} and draws each syllable at the verse
 * baseline provided by that line's own {@link LayoutResult}. Stateless.
 */
public final class LyricTextRenderer implements ElementRenderer<StaffElement> {

    private static final LyricTextRenderer INSTANCE = new LyricTextRenderer();

    private LyricTextRenderer() {
    }

    public static LyricTextRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public void render(
        LineInvariants invariants,
        ElementFrame frame,
        StaffElement element,
        Graphics2D g2
    ) {
        // While the editor is open, suppress the rendered box so the overlay is the
        // sole representation. Connectors are not suppressed (no connectors in 1a).
        if (invariants.getActivelyEditedElement() == element) {
            return;
        }

        var boxes = invariants.getLayoutResult().getLyricBoxes(element);

        if (boxes.isEmpty()) {
            return;
        }

        var lyricRenderMetrics = invariants.getLyricRenderMetrics();
        // The zoomed scale: the outer transform (which we strip below) is pxPerSs × factor,
        // so we re-derive integer pixel coordinates from the same zoomed value.
        var viewPxPerSs = invariants.getViewPixelsPerStaffSpace();

        try (var ignored = GraphicsState.save(g2, COLOR, FONT, TRANSFORM)) {
            // Strip the staff-space → pixel transform (which also carries the current
            // zoom factor) so this renderer goes through the same rasterization path
            // JTextField uses: a font drawn at integer logical-pixel coordinates rather
            // than through an arbitrary affine scale. Drawing through the SS scale instead
            // (with scaledLyricsFont) takes a different float-ascent rounding path that
            // shifts the baseline by up to one device pixel relative to the editor.
            // Since the transform's zoom is stripped along with it, re-derive the font at
            // the current zoom so glyph size still tracks zoom like everything else drawn
            // inside the transform.
            g2.scale(1.0 / viewPxPerSs, 1.0 / viewPxPerSs);
            g2.setFont(invariants.getViewScale().zoomedFont(lyricRenderMetrics.lyricsFont()));

            for (var box : boxes) {
                g2.setColor(invariants.getLyricColor(frame.currentElementIndex(), element, box.verseIndex()));

                var baselineYSs = invariants.getLayoutResult().verseYSsInLine(box.verseIndex(), lyricRenderMetrics);
                var xPx = (int) Math.round(box.xSs() * viewPxPerSs);
                var yPx = (int) Math.round(baselineYSs * viewPxPerSs);
                g2.drawString(box.text(), xPx, yPx);
            }
        }
    }
}
