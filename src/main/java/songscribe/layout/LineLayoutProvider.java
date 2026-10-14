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
 * Hands a line's laid-out geometry to code that has no view to ask, such as the MusicXML writer,
 * whose coordinates must agree with the painted score.
 */
@FunctionalInterface
public interface LineLayoutProvider {

    /**
     * Null when no layout is available.
     *
     * @param line must be in its song
     */
    @Nullable LayoutResult layoutFor(Line line);

    /**
     * Laying a line out resolves its automatic stem directions as a side effect, exactly as
     * painting it does.
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
