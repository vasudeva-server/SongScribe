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

import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.font.DocumentFontsHolder;
import songscribe.layout.LayoutEngine.TempoMark;

/**
 * Hands a line's laid-out geometry to code that has no view to ask.
 * <p>
 * The MusicXML writer emits coordinates for external consumers and therefore needs the same
 * geometry the score is painted from. It cannot build that geometry itself: {@link LayoutEngine}
 * needs {@link LyricRenderMetrics}, which the UI owns, and a line's layout depends on the layout
 * of the line before it — a melisma running off the end of one line reappears as a leading stub
 * on the next. So the caller supplies the geometry instead.
 * <p>
 * The interactive save path implements this by returning each line's live layout, which makes
 * saved coordinates identical to painted ones by construction. Callers with no view use
 * {@link #headless(Song, DocumentFontsHolder)}, which reproduces the paint path's
 * lyric-continuation threading, so its coordinates agree with the painted score.
 */
@FunctionalInterface
public interface LineLayoutProvider {

    /**
     * Returns the layout of {@code line}, or null when none is available.
     *
     * @param line the line whose geometry is wanted; must be in its song
     */
    @Nullable LayoutResult layoutFor(Line line);

    /**
     * Returns a provider that lays every line of {@code song} out from scratch, for callers with
     * no view.
     * <p>
     * The lines are laid out at construction, in song order, each one's trailing lyric
     * continuation carried into the next line's leading flag exactly as the paint path does.
     * Laying a line out resolves its automatic stem directions as a side effect, exactly as
     * painting it does. The song's tempo mark is omitted: the provider's callers want
     * coordinates, not a painting, and the inert header mark contributes none.
     *
     * @param song  the song whose lines will be laid out
     * @param fonts the document fonts, which supply the lyrics font the metrics derive from
     */
    static LineLayoutProvider headless(Song song, DocumentFontsHolder fonts) {
        var engine = new LayoutEngine(
            LyricRenderMetrics.forFont(fonts.getLyricsFont()),
            song.getLineWidthSs().value(),
            fonts);
        var layouts = new ArrayList<LayoutResult>(song.lineCount());
        var hasLeadingLyricContinuation = false;

        for (var line : song.getLines()) {
            var result = engine.layout(line, hasLeadingLyricContinuation, TempoMark.OMITTED);
            layouts.add(result);
            hasLeadingLyricContinuation = result.hasTrailingLyricContinuation();
        }

        return line -> layouts.get(line.index());
    }
}
