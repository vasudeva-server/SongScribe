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
package songscribe.export;

import java.io.File;

import songscribe.Strings;
import songscribe.ui.OptionDialogs;

/**
 * Utility class for exporting music scores as SVG files.
 */
public class SVGExporter {

    private SVGExporter() {
        // Utility class - prevent instantiation
    }

    /**
     * Exports the score to an SVG file.
     *
     * @param outputFile the file to write the SVG to
     */
    public static void createSVG(File outputFile) {
        // SVG export not yet implemented with component-based rendering
        OptionDialogs.showErrorMessage(
            null,
            Strings.get(Strings.ALERT_TITLE_EXPORT_ERROR),
            Strings.get(Strings.ERROR_EXPORT_NOT_IMPLEMENTED, "SVG")
        );
    }
}
