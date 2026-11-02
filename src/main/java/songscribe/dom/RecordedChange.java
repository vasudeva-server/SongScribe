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
package songscribe.dom;

import org.jspecify.annotations.Nullable;

import songscribe.Strings;

/**
 * One change the song made on its own during an edit or a load, before its report is built.
 *
 * @param headingKey a {@code Strings} key whose value is plain text
 * @param where      {@code null} only for a change with no place in the song
 */
public record RecordedChange(String headingKey, String description, @Nullable ChangeLines where) {

    /**
     * One entry stands for every accidental conversion in the file.
     */
    public static RecordedChange accidentalsConverted() {
        return new RecordedChange(
            Strings.REPORT_ACCIDENTALS_CONVERTED,
            Strings.get(Strings.REPORT_ACCIDENTALS_DETAIL),
            null);
    }

    /**
     * Each line that lost a lyric gets one entry, however many it lost.
     */
    public static RecordedChange lyricDropped(Line line) {
        return new RecordedChange(
            Strings.REPORT_LYRICS_REMOVED,
            Strings.get(Strings.REPORT_LYRICS_DETAIL),
            ChangeLines.on(line));
    }
}
