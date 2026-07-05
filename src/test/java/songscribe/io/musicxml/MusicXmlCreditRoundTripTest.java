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
 * Round-trip coverage for the {@code <credit>} reader (Phase 8): the canonical
 * credits (subtitle + the four score-below text blocks) reload verbatim, the
 * attribution user Y offset survives via {@code relative-y}, and the display-only
 * credits (title, attribution roles) do not corrupt the canonical head metadata
 * the reader reads back — a hand-edited display-only credit never wins over the
 * canonical value.
 */
class MusicXmlCreditRoundTripTest extends MusicXmlRoundTripSupport {

    // Distinct texts so a credit-routing mix-up (title → subtitle, composer →
    // subtitle, translation → footnotes, etc.) is caught. All are already-
    // normalized ASCII so the model-side normalization is idempotent and the
    // round-trip is verbatim.
    private static final String TITLE          = "My Song Title";
    private static final String NUMBER         = "5";
    private static final String SUBTITLE       = "My Subtitle";
    private static final String COMPOSER       = "A Composer";
    private static final String LYRICIST       = "A Lyricist";
    private static final String PLACE          = "New York";
    private static final String UNDERLYRICS    = "Under lyrics text";
    private static final String BANGLA_LYRICS  = "Bangla lyrics text";
    private static final String TRANSLATION    = "Translated lyrics text";
    private static final String FOOTNOTES      = "Footnote text";

    // A non-zero attribution Y offset (staff spaces). Integer-valued so the
    // ss → tenths → ss conversion is exact.
    private static final double ATTRIBUTION_Y_OFFSET_SS = 3.0;

    // A month/day sentinel for the "date absent" metadata slots.
    private static final int NO_DATE_PART = 0;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A minimal one-note song — the writer needs one element to emit a part. */
    private static Song singleNoteSong() {
        return buildSong(line -> line.addElement(ElementType.CROTCHET.newInstance()));
    }

    /**
     * Returns a copy of {@code metadata} with the subtitle replaced. There is no
     * {@code withSubtitle} mutator, so the record is rebuilt from its components.
     */
    private static SongMetadata withSubtitle(SongMetadata metadata, String subtitle) {
        return new SongMetadata(
            metadata.title(), metadata.number(), metadata.place(),
            metadata.year(), metadata.month(), metadata.day(),
            metadata.composer(), metadata.lyricist(), metadata.lyricsSource(),
            metadata.arrangement(), metadata.unofficialTranslation(),
            subtitle,
            metadata.wordsYear(), metadata.wordsMonth(), metadata.wordsDay()
        );
    }

    /**
     * Builds a song exercising every credit-type: title + number (title credit),
     * subtitle (canonical), distinct composer/lyricist/place (display-only
     * attribution credits), a non-zero attribution Y offset, and all four
     * score-below text blocks (canonical).
     */
    private static Song fullCreditSong() {
        var song = singleNoteSong();

        song.setMetadata(new SongMetadata(
            TITLE, NUMBER, PLACE,
            "", NO_DATE_PART, NO_DATE_PART,
            COMPOSER, LYRICIST,
            Song.LyricsSource.LYRICIST,
            false,
            false,
            SUBTITLE,
            "", NO_DATE_PART, NO_DATE_PART
        ));

        song.getAttributionElement().setUserYOffsetSs(ATTRIBUTION_Y_OFFSET_SS);

        song.setUnderLyrics(UNDERLYRICS);
        song.setBanglaLyrics(BANGLA_LYRICS);
        song.setTranslatedLyrics(TRANSLATION);
        song.setFootnotes(FOOTNOTES);

        return song;
    }

    // -------------------------------------------------------------------------
    // Round-trip tests
    // -------------------------------------------------------------------------

    @Test
    void testSubtitleAndScoreBelowBlocksRoundTripVerbatim() throws Exception {
        var song = fullCreditSong();
        var reloaded = roundTrip(song);

        assertThat(reloaded.getSubtitle())
            .as("subtitle credit reloads verbatim")
            .isEqualTo(song.getSubtitle());
        assertThat(reloaded.getUnderLyrics())
            .as("underlyrics credit reloads verbatim")
            .isEqualTo(song.getUnderLyrics());
        assertThat(reloaded.getBanglaLyrics())
            .as("bangla-lyrics credit reloads verbatim")
            .isEqualTo(song.getBanglaLyrics());
        assertThat(reloaded.getTranslatedLyrics())
            .as("translation credit reloads verbatim")
            .isEqualTo(song.getTranslatedLyrics());
        assertThat(reloaded.getFootnotes())
            .as("footnotes credit reloads verbatim")
            .isEqualTo(song.getFootnotes());
    }

    @Test
    void testAttributionYOffsetSurvives() throws Exception {
        // A minimal song still emits a composer credit (composer is never blank —
        // it is coerced to SRI_CHINMOY), so the shared relative-y is present.
        var song = singleNoteSong();
        song.getAttributionElement().setUserYOffsetSs(ATTRIBUTION_Y_OFFSET_SS);

        var reloaded = roundTrip(song);

        assertThat(reloaded.getAttributionElement().getUserYOffsetSs())
            .as("attribution user Y offset reloads from relative-y")
            .isEqualTo(ATTRIBUTION_Y_OFFSET_SS);
    }

    @Test
    void testDisplayOnlyCreditsDoNotCorruptCanonicalMetadata() throws Exception {
        // The title + attribution credits (composer/lyricist/place/...) are all
        // display-only; interspersed with the canonical subtitle credit they must
        // not bleed into the model. The canonical subtitle wins.
        var song = fullCreditSong();
        var reloaded = roundTrip(song);

        assertThat(reloaded.getSubtitle())
            .as("subtitle wins over the interspersed display-only credits")
            .isEqualTo(song.getSubtitle());
        assertThat(reloaded.getSubtitle())
            .as("the title credit text never becomes the subtitle")
            .isNotEqualTo(song.getNumberedTitle());
        assertThat(reloaded.getSubtitle())
            .as("the composer credit text never becomes the subtitle")
            .isNotEqualTo(COMPOSER);

        // The display-only title credit carries the numbered title; it is ignored,
        // never read into the title field (which is re-derived from <movement-*>).
        assertThat(reloaded.getTitle())
            .as("the display-only title credit's numbered text is not read into the title")
            .isNotEqualTo(song.getNumberedTitle());
    }

    @Test
    void testBlankSubtitleReloadsBlank() throws Exception {
        var song = singleNoteSong();

        assertThat(song.getSubtitle()).as("precondition: subtitle starts blank").isEmpty();

        var reloaded = roundTrip(song);

        assertThat(reloaded.getSubtitle())
            .as("a blank subtitle emits no credit and reloads blank")
            .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Schema validation
    // -------------------------------------------------------------------------

    @Test
    void testCreditRoundTripIsSchemaValid() throws Exception {
        var song = fullCreditSong();
        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(xml))
            .as("credit output validates against the MusicXML 4.0 schema")
            .doesNotThrowAnyException();
    }
}
