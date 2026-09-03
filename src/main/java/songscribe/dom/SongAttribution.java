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
 * Who a song is credited to, where it was composed and when — the whole of what the
 * credit block under a song's title is built from.
 *
 * <p>This is exactly the input {@link AttributionFormatter} reads, which is why it
 * exists separately from {@link SongMetadata}. A caller that has a whole song passes
 * {@link SongMetadata#attribution()}; a caller that has only half-entered dialog state
 * builds one directly, without having to invent a title, a number and a subtitle that
 * the formatter would ignore. Adding a field to the credit block is then a change to
 * this record, and every producer of one has to answer for it, rather than a silent
 * empty string at whichever call site nobody revisited.
 *
 * <p><b>The record normalizes on construction</b>, so a producer may pass raw text
 * straight from a text field. That normalization is the same one {@link SongMetadata}
 * applies, because {@code SongMetadata} applies it by building one of these — the
 * rules live here and exist once.
 *
 * @param place where the song was composed, tidied and typographically substituted
 * @param date when the music was composed, to whatever precision is known
 * @param composer who composed the music
 * @param lyricist who wrote the words
 * @param lyricsSource the role the lyricist is credited in, which decides the connector
 *     joining their name to it
 * @param arrangement whether the music is an arrangement rather than an original
 * @param wordsDate when the words were written, {@link PartialDate.EmptyDate} when the
 *     same as the music's
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
     * Normalizes every field, and collapses a words-date that merely repeats the
     * composition date.
     *
     * <p>Place, composer and lyricist are trimmed and typographically substituted but
     * not short-A stripped, which is reserved for the title, subtitle and lyrics. The
     * two people additionally go through {@link Song#coercePerson}.
     *
     * <p>A words-date equal to the composition date says nothing the composition date
     * has not already said, and the model must never hold that state — a credit block
     * built from it would name the same date twice — so it collapses to absent here
     * rather than at each place that formats it.
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
     * Returns the word joining {@code person}'s name to the role they are credited in.
     *
     * <p>Sri Chinmoy is always connected as a lyricist, whatever {@link #lyricsSource}
     * says, because the connector is a function of the person rather than of the role.
     *
     * @param person the name being credited
     * @return the connector to print between the role and {@code person}
     */
    public String connectorFor(String person) {
        if (person.equals(Song.SRI_CHINMOY)) {
            return Song.LyricsSource.LYRICIST.getConnector();
        }

        return lyricsSource.getConnector();
    }
}
