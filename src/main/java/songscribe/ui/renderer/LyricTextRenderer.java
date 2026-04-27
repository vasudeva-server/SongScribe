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

import static songscribe.ui.renderer.GraphicsState.Property.FONT;

import module java.desktop;

import songscribe.music.StaffElement;
import songscribe.ui.layout.SongLayoutMetrics;

/**
 * Renders per-element lyric syllable text beneath the staff.
 * <p>
 * Reads {@link songscribe.ui.layout.LyricBoxLayout} entries from the current
 * {@link songscribe.ui.layout.LayoutResult} and draws each syllable at the verse
 * baseline provided by the song-wide
 * {@link SongLayoutMetrics}. Stateless.
 */
public class LyricTextRenderer extends BaseElementRenderer<StaffElement> {

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

        try (var ignored = GraphicsState.save(g2, FONT)) {
            g2.setFont(lyricRenderMetrics.scaledLyricsFont());

            for (var box : boxes) {
                var baselineYSs = metrics.verseYSsInLine(box.verseIndex());
                g2.drawString(box.text(), (float) box.xSs(), (float) baselineYSs);
            }
        }
    }
}
