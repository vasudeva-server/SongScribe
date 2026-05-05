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
 * Utility class for exporting music scores as ABC notation files.
 * <p>
 * Note: ABC export is currently not supported and this class serves as a stub
 * for future implementation.
 */
public final class ABCExporter {

    private ABCExporter() {
        // Utility class - prevent instantiation
    }

    /**
     * Exports the score to an ABC notation file.
     *
     * @param outputFile the file to write the ABC notation to
     */
    public static void createABC(File outputFile) {
        // ABC export is not supported
        OptionDialogs.showErrorMessage(
            null,
            Strings.ALERT_TITLE_EXPORT_ERROR,
            Strings.ERROR_ABC_NOT_SUPPORTED
        );
    }
}
