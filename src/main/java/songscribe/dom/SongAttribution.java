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

import songscribe.util.StringUtils;

/**
 * Who a song is credited to, where it was composed and when — exactly the input
 * {@link AttributionFormatter} reads.
 *
 * <p>The record normalizes on construction, so a producer may pass raw text straight from a text
 * field. {@link SongMetadata} applies the same normalization by building one of these.
 */
public record SongAttribution(
    String place,
    PartialDate date,
    String composer,
    String lyricist,
    Song.LyricsSource lyricsSource,
    boolean arrangement,
    PartialDate wordsDate
) {

    /**
     * A words-date equal to the composition date collapses to absent here: the model must never
     * hold that state, because a credit block built from it would name the same date twice.
     */
    public SongAttribution {
        // No short-A stripping: that is reserved for title/subtitle/lyrics.
        place = StringUtils.processText(place, false);
        composer = Song.coercePerson(StringUtils.processText(composer, false));
        lyricist = Song.coercePerson(StringUtils.processText(lyricist, false));

        if (wordsDate.equals(date)) {
            wordsDate = PartialDate.EmptyDate.INSTANCE;
        }
    }

    /**
     * Sri Chinmoy is always connected as a lyricist, whatever {@link #lyricsSource} says.
     */
    public String connectorFor(String person) {
        if (person.equals(Song.SRI_CHINMOY)) {
            return Song.LyricsSource.LYRICIST.getConnector();
        }

        return lyricsSource.getConnector();
    }
}
