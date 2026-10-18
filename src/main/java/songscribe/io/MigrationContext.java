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

import songscribe.dom.Line;
import songscribe.dom.Song;

/**
 * Mutable carrier threaded through {@link MigrationPipeline}.
 *
 * <p>{@link #song} holds no lines until the post-assembly phase, so a pre-assembly migration
 * reads {@link #lines}.
 */
final class MigrationContext {

    final Song song;

    List<Line> lines = new ArrayList<>();

    int majorVersion;
    int minorVersion;

    String lyrics = "";

    MigrationContext(Song song) {
        this.song = song;
    }

    boolean isBefore(int major, int minor) {
        return new FormatVersion(majorVersion, minorVersion).isBefore(new FormatVersion(major, minor));
    }
}
