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

import songscribe.music.Composition;
import songscribe.ui.component.Score;

public final class Converter {

    private Converter() {}

    /**
     * Loads a composition from a file into the given score.
     *
     * @return the loaded composition
     */
    public static Composition loadComposition(File file, Score score) {
        score.openFile(file, false);
        return score.getComposition();
    }

    /**
     * Applies export exclusions by mutating the composition directly.
     * <p>
     * TODO: Pass ExportOptions through the export pipeline instead of mutating
     * Composition. This is a temporary measure until the rendering pipeline
     * supports ExportOptions natively (for PDF and SVG export).
     */
    public static void applyExportExclusions(
        Composition composition,
        boolean withoutLyrics,
        boolean withoutSongTitle
    ) {
        if (withoutLyrics) {
            composition.setUnderLyrics("");
            composition.setTranslatedLyrics("");
        }

        if (withoutSongTitle) {
            composition.setTitle("");
        }
    }
}
