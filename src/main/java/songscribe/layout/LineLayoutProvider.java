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

package songscribe.layout;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Attribution;
import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.Song;
import songscribe.font.DocumentFontsHolder;

/**
 * Hands a line's laid-out geometry to code that has no view to ask.
 * <p>
 * The MusicXML writer emits coordinates for external consumers and therefore needs the same
 * geometry the score is painted from. It cannot build that geometry itself: {@link LayoutEngine}
 * needs {@link LyricRenderMetrics}, which the UI owns, and it needs the real {@code isLastLine} and
 * {@code attribution} arguments the paint path passes — the convenience overloads would emit
 * coordinates that disagree with the painted score on the first and last lines. So the caller
 * supplies the geometry instead.
 * <p>
 * The interactive save path implements this by returning each line's live layout, which makes
 * saved coordinates identical to painted ones by construction. Callers with no view use
 * {@link #headless(Song, DocumentFontsHolder)}.
 */
@FunctionalInterface
public interface LineLayoutProvider {

    /**
     * Returns the layout of {@code line}, or null when none is available.
     *
     * @param line      the line whose geometry is wanted
     * @param lineIndex the line's index within the song, which decides whether it is the first
     *                  line (attribution) or the last (right-pinned final barline)
     */
    @Nullable LayoutResult layoutFor(Line line, int lineIndex);

    /**
     * Returns a provider that lays each line out from scratch, for callers with no view.
     * <p>
     * It passes the same real arguments the paint path does, so its coordinates agree with a
     * painted score. Laying a line out resolves its automatic stem directions as a side effect,
     * exactly as painting it does.
     *
     * @param song  the song whose lines will be laid out
     * @param fonts the document fonts, which supply the lyrics font the metrics derive from
     */
    static LineLayoutProvider headless(Song song, DocumentFontsHolder fonts) {
        var engine = new LayoutEngine(
            LyricRenderMetrics.forFont(fonts.getLyricsFont()),
            song.getLineWidthSs(),
            fonts);
        var lastLineIndex = song.lineCount() - 1;

        return (line, lineIndex) -> engine.layout(
            line,
            lineIndex == lastLineIndex,
            false,
            lineIndex == 0 ? measuredAttribution(song, fonts) : null);
    }

    /**
     * Returns the song's attribution block with its reserved dimensions measured at natural
     * (unzoomed) scale, the way the paint path measures it before laying out the first line.
     */
    private static Attribution measuredAttribution(Song song, DocumentFontsHolder fonts) {
        var attribution = song.getAttributionElement();
        var sizePx = song.getAttributionPane()
            .getContentSizePx(fonts.getAttributionFont(), fonts.getSubAttributionFont());

        attribution.setDimensionsSs(
            DocumentScale.pxToSs(sizePx.width),
            DocumentScale.pxToSs(sizePx.height));

        return attribution;
    }
}
