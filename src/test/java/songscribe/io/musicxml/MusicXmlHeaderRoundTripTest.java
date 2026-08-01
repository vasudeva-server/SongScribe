/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.io.musicxml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;

/**
 * Round-trip coverage for the head-metadata reader (Phase 5): {@code <movement-*>}
 * and the {@code <identification>} creators + {@code <miscellaneous>} fields are
 * accumulated and assembled into {@link SongMetadata} at the terminal
 * {@code </score-partwise>}, so a fully-populated head reloads with every metadata
 * field equal. An empty number omits {@code <movement-number>} and reloads blank,
 * and the emitted head validates against the MusicXML 4.0 schema.
 */
class MusicXmlHeaderRoundTripTest extends MusicXmlRoundTripSupport {

    // Distinct, already-normalized ASCII values so the model-side normalization is
    // idempotent and the round-trip is verbatim; distinct composer/lyricist catch a
    // creator-routing mix-up.
    private static final String TITLE    = "My Song Title";
    private static final String NUMBER   = "7";
    private static final String COMPOSER = "A Composer";
    private static final String LYRICIST = "A Lyricist";
    private static final String PLACE    = "New York";

    // The head test keeps the subtitle blank so the equality assertion turns only
    // on the head reader; the subtitle credit is covered by the credit round-trip.
    private static final String NO_SUBTITLE = "";

    // A full composition date (YYYY-MM-DD) and a partial, distinct lyrics date
    // (YYYY-MM). Distinct so the writer emits the lyrics-date rather than
    // normalizing it away as a duplicate of the composition date.
    private static final String COMPOSITION_YEAR  = "1987";
    private static final int    COMPOSITION_MONTH = 12;
    private static final int    COMPOSITION_DAY   = 1;
    private static final String LYRICS_YEAR       = "1990";
    private static final int    LYRICS_MONTH      = 5;

    // Sentinel for an absent month/day slot.
    private static final int NO_DATE_PART = 0;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A minimal one-note song — the writer needs one element to emit a part. */
    private static Song singleNoteSong() {
        return buildSong(line -> line.addElement(ElementType.CROTCHET.newInstance()));
    }

    /**
     * A song whose head is populated across every head field: title + number,
     * distinct composer/lyricist, arrangement, a non-default lyrics source, place,
     * unofficial-translation, a full composition date and a distinct partial lyrics
     * date.
     */
    private static Song fullHeaderSong() {
        var song = singleNoteSong();

        song.setMetadata(new SongMetadata(
            TITLE, NUMBER, PLACE,
            COMPOSITION_YEAR, COMPOSITION_MONTH, COMPOSITION_DAY,
            COMPOSER, LYRICIST,
            Song.LyricsSource.TEXT,
            true,   // arrangement
            true,   // unofficialTranslation
            NO_SUBTITLE,
            LYRICS_YEAR, LYRICS_MONTH, NO_DATE_PART
        ));

        return song;
    }

    // -------------------------------------------------------------------------
    // Round-trip tests
    // -------------------------------------------------------------------------

    @Test
    void testFullHeadMetadataRoundTripsEqual() throws Exception {
        var song = fullHeaderSong();
        var reloaded = roundTrip(song);

        assertThat(reloaded.getMetadata())
            .as("every head-metadata field reloads equal (title, number, "
                + "composer/lyricist, arrangement, lyrics source, dates, place, "
                + "unofficial-translation)")
            .isEqualTo(song.getMetadata());
    }

    @Test
    void testPartialCompositionDateRoundTripsEqual() throws Exception {
        // A year-only composition date with no lyrics date exercises the partial
        // (YYYY) form and the omitted-lyrics-date path (an empty words date is not
        // emitted, so it must reload empty).
        var song = singleNoteSong();

        song.setMetadata(new SongMetadata(
            TITLE, NUMBER, PLACE,
            COMPOSITION_YEAR, NO_DATE_PART, NO_DATE_PART,
            COMPOSER, LYRICIST,
            Song.LyricsSource.OTHER,
            false,
            false,
            NO_SUBTITLE,
            "", NO_DATE_PART, NO_DATE_PART
        ));

        var reloaded = roundTrip(song);

        assertThat(reloaded.getMetadata())
            .as("a partial (year-only) composition date and an empty lyrics date "
                + "reload equal")
            .isEqualTo(song.getMetadata());
    }

    @Test
    void testEmptyNumberOmitsMovementNumberAndReloadsBlank() throws Exception {
        var song = singleNoteSong();

        song.setMetadata(new SongMetadata(
            TITLE, "", PLACE,
            "", NO_DATE_PART, NO_DATE_PART,
            COMPOSER, LYRICIST,
            Song.LyricsSource.LYRICIST,
            false,
            false,
            NO_SUBTITLE,
            "", NO_DATE_PART, NO_DATE_PART
        ));

        var xml = writeToString(song);

        assertThat(xml)
            .as("an empty number omits the <movement-number> element entirely")
            .doesNotContain('<' + MusicXmlTags.MOVEMENT_NUMBER + '>');

        var reloaded = roundTrip(song);

        assertThat(reloaded.getNumber())
            .as("an omitted <movement-number> reloads as an empty number")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Schema validation
    // -------------------------------------------------------------------------

    @Test
    void testHeaderRoundTripIsSchemaValid() throws Exception {
        var song = fullHeaderSong();
        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(xml))
            .as("head output validates against the MusicXML 4.0 schema")
            .doesNotThrowAnyException();
    }
}
