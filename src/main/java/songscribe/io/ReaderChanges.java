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

import java.util.LinkedHashSet;
import java.util.Set;

import songscribe.dom.Line;
import songscribe.dom.RecordedChange;
import songscribe.dom.Song;

/**
 * The changes a file reader makes to what the file says while it reads one file, collected so that
 * every reader reports them in the same order. One instance serves one read.
 */
public final class ReaderChanges {

    private boolean accidentalConverted = false;

    private final Set<Line> linesThatDroppedLyrics = new LinkedHashSet<>();

    /**
     * Records that the reader converted a retired accidental to its replacement.
     */
    public void accidentalConverted() {
        accidentalConverted = true;
    }

    /**
     * Records that the reader dropped a lyric from a note on {@code line}.
     */
    public void lyricDropped(Line line) {
        linesThatDroppedLyrics.add(line);
    }

    /**
     * Must be called once, after the reader has read the whole file.
     *
     * @effects reports through {@link Song#report}, in this order, one
     *     {@link RecordedChange#accidentalsConverted} entry when any accidental was converted, then
     *     one {@link RecordedChange#lyricDropped} entry for each line that lost a lyric, in the
     *     order of each line's first dropped lyric
     */
    public void reportTo(Song song) {
        if (accidentalConverted) {
            song.report(RecordedChange.accidentalsConverted());
        }

        for (var line : linesThatDroppedLyrics) {
            song.report(RecordedChange.lyricDropped(line));
        }
    }
}
