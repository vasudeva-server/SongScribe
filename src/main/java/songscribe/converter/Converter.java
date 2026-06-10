/*
 * SongScribe song notation program
 * Copyright (C) Sri Chinmoy Centres International
 *
 * This file is part of SongScribe.
 *
 * SongScribe is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * SongScribe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package songscribe.converter;

import java.io.File;

import songscribe.dom.Song;
import songscribe.ui.component.ScoreView;

public final class Converter {

    private Converter() {}

    /**
     * Loads a song from a file into the given score.
     *
     * @return the loaded song
     */
    public static Song loadSong(File file, ScoreView score) {
        score.openFile(file, false);
        return score.getSong();
    }

    /**
     * Applies export exclusions by mutating the song directly.
     * <p>
     * TODO: Pass ExportOptions through the export pipeline instead of mutating
     * Song. This is a temporary measure until the rendering pipeline
     * supports ExportOptions natively (for PDF and SVG export).
     */
    public static void applyExportExclusions(
        Song song,
        boolean withoutLyrics,
        boolean withoutSongTitle
    ) {
        if (withoutLyrics) {
            song.setUnderLyrics("");
            song.setTranslatedLyrics("");
        }

        if (withoutSongTitle) {
            song.setMetadata(song.getMetadata().withTitle(""));
        }
    }
}
