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
package songscribe.io;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import songscribe.dom.Line;
import songscribe.dom.Song;

/**
 * Mutable carrier threaded through {@link MigrationPipeline}. Migrations read and write
 * the parsed lines, the song-level scalars, and the version through this object rather
 * than via {@code SongIO.DocumentReader} fields. Fields are plain and mutable, mirroring
 * the {@code DocumentReader} field style.
 *
 * <p>{@link #song} is populated only before the post-assembly phase; pre-assembly
 * migrations must not depend on it.
 */
final class MigrationContext {

    List<Line> lines = new ArrayList<>();

    double topPaddingSs;
    double lineWidthSs;
    double rowHeightAdjustmentSs;
    double attributionStartYSs;

    int majorVersion;
    int minorVersion;

    String attribution = "";
    String lyrics = "";

    @Nullable Song song;

    boolean isBefore(int major, int minor) {
        return majorVersion < major || (majorVersion == major && minorVersion < minor);
    }
}
