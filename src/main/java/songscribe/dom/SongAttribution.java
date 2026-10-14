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

import java.util.regex.Pattern;

import songscribe.util.StringUtils;
import songscribe.util.StringUtils.ShortA;

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

    private static final Pattern BLANK_LINE_RUN = Pattern.compile("(\\s*\\n){2,}");

    /**
     * A words-date equal to the composition date collapses to absent here: the model must never
     * hold that state, because a credit block built from it would name the same date twice.
     *
     * <p>A blank lyricist takes the composer credit, as {@link #resolveLyricist} states.
     */
    public SongAttribution {
        place = normalizePlace(place);
        lyricist = resolveLyricist(lyricist, composer);
        composer = normalizePerson(composer);

        if (wordsDate.equals(date)) {
            wordsDate = PartialDate.EmptyDate.INSTANCE;
        }
    }

    /**
     * Public because the song settings dialog shows a field's normalized text when the field is
     * committed, before any record exists, and must show what this record will hold.
     *
     * @param text the raw place, as typed or read from storage
     * @return {@code text} as {@link StringUtils#processText} returns it under {@link ShortA#KEEP}
     * @invariant idempotent: {@code normalizePlace(normalizePlace(text))} equals
     *     {@code normalizePlace(text)}, so the dialog shows what the record stores
     */
    public static String normalizePlace(String text) {
        return StringUtils.processText(text, ShortA.KEEP);
    }

    /**
     * Public for the same reason as {@link #normalizePlace}.
     *
     * @param text the raw name, as typed or read from storage
     * @return {@code text} as {@link StringUtils#processText} returns it under {@link ShortA#KEEP},
     *     with every run of blank lines collapsed to a single line break;
     *     {@link Song#SRI_CHINMOY} when {@code text} carries no other content
     * @invariant idempotent: {@code normalizePerson(normalizePerson(text))} equals
     *     {@code normalizePerson(text)}, so the dialog shows what the record stores
     */
    public static String normalizePerson(String text) {
        var cleaned = StringUtils.processText(text, ShortA.KEEP);
        var collapsed = BLANK_LINE_RUN.matcher(cleaned).replaceAll("\n");
        return collapsed.isEmpty() ? Song.SRI_CHINMOY : collapsed;
    }

    /**
     * Whoever wrote the music is credited with the words too, unless the entry names someone
     * else. Public for the same reason as {@link #normalizePlace}.
     *
     * @param lyricist the raw lyricist, as typed or read from storage
     * @param composer the raw composer, as typed or read from storage
     * @return {@code normalizePerson(composer)} when {@code lyricist} is empty or only
     *     whitespace; {@code normalizePerson(lyricist)} otherwise
     * @invariant idempotent in {@code lyricist}: {@code resolveLyricist(resolveLyricist(lyricist,
     *     composer), composer)} equals {@code resolveLyricist(lyricist, composer)}
     */
    public static String resolveLyricist(String lyricist, String composer) {
        return normalizePerson(lyricist.trim().isEmpty() ? composer : lyricist);
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
