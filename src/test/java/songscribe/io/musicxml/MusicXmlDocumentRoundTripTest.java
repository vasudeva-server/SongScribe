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
import static songscribe.dom.StaffElementFactory.crotchet;

import java.awt.Component;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Duration;
import songscribe.dom.KeyType;
import songscribe.dom.Lyric;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;

/**
 * Phase 10 — the full round-trip + regression gate: a single song populated
 * across every Phase 7 area (head metadata, every misc-field, custom document
 * fonts, line width + row-height adjustment, title/subtitle/attribution/
 * score-below credits with an attribution offset, and both ABOVE/BELOW
 * annotations) plus notes/lyrics/tempo/key from earlier phases, asserted equal
 * after a single {@link #roundTrip}. Also proves the display-only credits
 * (title, attribution roles) re-derive from head metadata rather than being
 * read from the credit text — a hand-edited credit must not corrupt the model.
 */
class MusicXmlDocumentRoundTripTest extends MusicXmlRoundTripSupport {

    // -- Head metadata --
    private static final String TITLE    = "Full Round-Trip Song";
    private static final String NUMBER   = "12";
    private static final String SUBTITLE = "A Complete Subtitle";
    private static final String COMPOSER = "Round-Trip Composer";
    private static final String LYRICIST = "Round-Trip Lyricist";
    private static final String PLACE    = "Full Round-Trip City";

    // Distinct, full composition date and a distinct, partial lyrics date so a
    // date-routing mix-up is caught and the lyrics-date normalization (equal to
    // composition date -> omitted) is not accidentally triggered.
    private static final String COMPOSITION_YEAR  = "1985";
    private static final int    COMPOSITION_MONTH = 8;
    private static final int    COMPOSITION_DAY   = 21;
    private static final String LYRICS_YEAR       = "1991";
    private static final int    LYRICS_MONTH      = 3;
    private static final int    NO_DATE_PART      = 0;

    // -- Attribution offset + score-below credits --
    private static final double ATTRIBUTION_Y_OFFSET_SS = 4.0;
    private static final String UNDERLYRICS   = "Full round-trip underlyrics text";
    private static final String BANGLA_LYRICS = "Full round-trip Bangla lyrics text";
    private static final String TRANSLATION   = "Full round-trip translated lyrics text";
    private static final String FOOTNOTES     = "Full round-trip footnote text";

    // -- Layout: line width + row-height adjustment --
    // The width is deliberately fractional, and on the finest grid the model
    // uses (1/8 staff space): a whole-number width would pass even if the
    // reader rounded <page-width> to a whole staff space.
    private static final double LINE_WIDTH_SS            = 130.125;
    private static final double ROW_HEIGHT_ADJUSTMENT_SS = 2.5;

    // -- Custom fonts: the five roles that round-trip (annotation, lyrics,
    // sub-attribution, title, subtitle). Distinct sizes so a role mix-up is
    // caught.
    private static final String WORD_FONT_FAMILY            = "Full Round-Trip Word Family";
    private static final int    WORD_FONT_SIZE              = 34;
    private static final String LYRIC_FONT_FAMILY           = "Full Round-Trip Lyric Family";
    private static final int    LYRIC_FONT_SIZE             = 36;
    private static final String SUB_ATTRIBUTION_FONT_FAMILY = "Full Round-Trip Sub-Attribution Family";
    private static final int    SUB_ATTRIBUTION_FONT_SIZE   = 38;
    private static final String TITLE_FONT_FAMILY           = "Full Round-Trip Title Family";
    private static final int    TITLE_FONT_SIZE             = 54;
    private static final String SUBTITLE_FONT_FAMILY        = "Full Round-Trip Subtitle Family";
    private static final int    SUBTITLE_FONT_SIZE          = 56;

    private static final FontKey[] ROUND_TRIP_FONT_ROLES = {
        FontKey.ANNOTATION, FontKey.LYRICS, FontKey.SUB_ATTRIBUTION, FontKey.TITLE, FontKey.SUBTITLE
    };

    // -- Tempo --
    private static final int    BASE_TEMPO_BPM      = 108;
    private static final String NO_TEMPO_DESCRIPTION = "";

    // -- Key --
    private static final int MID_SONG_KEY_ACCIDENTAL_COUNT = 3;

    // -- Lyrics --
    private static final int    FIRST_VERSE      = 1;
    private static final String FIRST_LINE_LYRIC  = "Full";
    private static final String SECOND_LINE_LYRIC = "Round-Trip";

    // -- Annotations --
    private static final String ABOVE_ANNOTATION_TEXT = "dolce";
    private static final String BELOW_ANNOTATION_TEXT = "pizz.";
    private static final double ABOVE_Y_OFFSET_SS = 2.0;
    private static final double BELOW_Y_OFFSET_SS = -2.0;

    // -- Line indices --
    private static final int FIRST_LINE_INDEX  = 0;
    private static final int SECOND_LINE_INDEX = 1;
    private static final int LINE_COUNT        = 2;

    // -- Hand-edited-credit tamper text --
    private static final String TAMPERED_TITLE_TEXT    = "Tampered Title Credit Text";
    private static final String TAMPERED_COMPOSER_TEXT = "Tampered Composer Credit Text";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Annotation aboveAnnotation() {
        var annotation = new Annotation(ABOVE_ANNOTATION_TEXT);
        annotation.setXAlignment(Component.CENTER_ALIGNMENT);
        annotation.setPlacement(Annotation.Placement.ABOVE);
        annotation.setUserYOffsetSs(ABOVE_Y_OFFSET_SS);
        return annotation;
    }

    private static Annotation belowAnnotation() {
        var annotation = new Annotation(BELOW_ANNOTATION_TEXT);
        annotation.setXAlignment(Component.CENTER_ALIGNMENT);
        annotation.setPlacement(Annotation.Placement.BELOW);
        annotation.setUserYOffsetSs(BELOW_Y_OFFSET_SS);
        return annotation;
    }

    /** Default fonts with the five round-tripping roles overridden to distinct values. */
    private static DocumentFonts customFonts() {
        var fonts = DocumentFonts.defaultFonts();
        fonts.setFont(FontKey.ANNOTATION, WORD_FONT_FAMILY, WORD_FONT_SIZE);
        fonts.setFont(FontKey.LYRICS, LYRIC_FONT_FAMILY, LYRIC_FONT_SIZE);
        fonts.setFont(FontKey.SUB_ATTRIBUTION, SUB_ATTRIBUTION_FONT_FAMILY, SUB_ATTRIBUTION_FONT_SIZE);
        fonts.setFont(FontKey.TITLE, TITLE_FONT_FAMILY, TITLE_FONT_SIZE);
        fonts.setFont(FontKey.SUBTITLE, SUBTITLE_FONT_FAMILY, SUBTITLE_FONT_SIZE);
        return fonts;
    }

    /**
     * The {@link DocumentFonts} the reader must produce for a document written
     * with {@code source}: the default set with each round-tripping role
     * rebuilt from {@code source}'s PostScript name/size (the writer emits the PS
     * name, which the reader resolves via {@code MyFontUtils.createFont}) —
     * mirrors {@code MusicXmlDefaultsRoundTripTest.expectedRecoveredFonts}.
     */
    private static DocumentFonts expectedRecoveredFonts(DocumentFonts source) {
        var expected = DocumentFonts.defaultFonts();

        for (var role : ROUND_TRIP_FONT_ROLES) {
            var font = source.getFont(role);
            expected.setFont(role, font.getPSName(), font.getSize());
        }

        return expected;
    }

    /**
     * Builds a song exercising every Phase 7 area plus notes/lyrics/tempo/key
     * from earlier phases: two lines, each with a single note. The first
     * note carries the mirrored song base tempo, a lyric, and an ABOVE
     * annotation; the second line changes key and its note carries a distinct
     * lyric and a BELOW annotation. The song-level metadata, layout, and
     * score-below fields are populated after construction.
     */
    private static Song fullDocumentSong() {
        var baseTempo = new Tempo(BASE_TEMPO_BPM, Duration.CROTCHET, NO_TEMPO_DESCRIPTION, true);

        var song = buildSong(
            line -> {
                line.setKeyType(Song.DEFAULT_KEY_TYPE);
                line.setKeyAccidentalCount(Song.DEFAULT_KEY_ACCIDENTAL_COUNT);

                var note = crotchet();
                line.addElement(note);
                note.addAttachment(new TempoChangeAttachment(note, baseTempo));
                note.lyrics.add(new Lyric(FIRST_VERSE, FIRST_LINE_LYRIC, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
                note.addAttachment(new AnnotationAttachment(note, aboveAnnotation()));
            },
            line -> {
                line.setKeyType(KeyType.SHARPS);
                line.setKeyAccidentalCount(MID_SONG_KEY_ACCIDENTAL_COUNT);

                var note = crotchet();
                line.addElement(note);
                note.lyrics.add(new Lyric(FIRST_VERSE, SECOND_LINE_LYRIC, Lyric.Extend.NONE, Lyric.Syllabic.SINGLE, false));
                note.addAttachment(new AnnotationAttachment(note, belowAnnotation()));
            }
        );

        song.withoutMutationTracking(() -> {
            song.setTempo(baseTempo);
            song.setLineWidthSs(LINE_WIDTH_SS);
            song.setRowHeightAdjustmentSs(ROW_HEIGHT_ADJUSTMENT_SS);
        });

        song.setMetadata(new SongMetadata(
            TITLE, NUMBER, PLACE,
            COMPOSITION_YEAR, COMPOSITION_MONTH, COMPOSITION_DAY,
            COMPOSER, LYRICIST,
            Song.LyricsSource.TEXT,
            true,  // arrangement
            true,  // unofficialTranslation
            SUBTITLE,
            LYRICS_YEAR, LYRICS_MONTH, NO_DATE_PART
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
    void testFullHeadMetadataAndMiscFieldsRoundTripEqual() throws Exception {
        var song = fullDocumentSong();
        var reloaded = roundTrip(song);

        assertThat(reloaded.getMetadata())
            .as("every head-metadata / miscellaneous-field value reloads equal "
                + "(title, number, place, composition + lyrics dates, composer, "
                + "lyricist, lyrics source, arrangement, unofficial-translation, subtitle)")
            .isEqualTo(song.getMetadata());

        assertThat(reloaded.getRowHeightAdjustmentSs())
            .as("row-height adjustment reloads from its residual misc-field")
            .isEqualTo(ROW_HEIGHT_ADJUSTMENT_SS);

        assertThat(reloaded.getLineWidthSs())
            .as("line width reloads from <page-width>")
            .isEqualTo(LINE_WIDTH_SS);
    }

    @Test
    void testDocumentFontsRoundTripByEquality() throws Exception {
        var song = fullDocumentSong();
        var fonts = customFonts();

        var result = roundTrip(song, fonts);

        assertThat(result.fonts())
            .as("DocumentFonts equality: word/lyric/sub-attribution/title/subtitle fonts "
                + "reload; the other three roles (write-forward, credit-words only) stay default")
            .isEqualTo(expectedRecoveredFonts(fonts));
    }

    @Test
    void testAttributionOffsetAndScoreBelowCreditsRoundTrip() throws Exception {
        var song = fullDocumentSong();
        var reloaded = roundTrip(song);

        assertThat(reloaded.getAttributionElement().getUserYOffsetSs())
            .as("attribution user Y offset reloads from relative-y")
            .isEqualTo(ATTRIBUTION_Y_OFFSET_SS);

        assertThat(reloaded.getUnderLyrics()).as("underlyrics credit reloads verbatim").isEqualTo(UNDERLYRICS);
        assertThat(reloaded.getBanglaLyrics()).as("bangla-lyrics credit reloads verbatim").isEqualTo(BANGLA_LYRICS);
        assertThat(reloaded.getTranslatedLyrics()).as("translation credit reloads verbatim").isEqualTo(TRANSLATION);
        assertThat(reloaded.getFootnotes()).as("footnotes credit reloads verbatim").isEqualTo(FOOTNOTES);
    }

    @Test
    void testNotesLyricsTempoAndKeyRoundTrip() throws Exception {
        var song = fullDocumentSong();
        var reloaded = roundTrip(song);

        assertThat(reloaded.getLines()).as("line count").hasSize(LINE_COUNT);

        var firstLine = reloaded.getLine(FIRST_LINE_INDEX);
        var secondLine = reloaded.getLine(SECOND_LINE_INDEX);

        assertThat(firstLine.getKeyType()).as("first line key type").isEqualTo(Song.DEFAULT_KEY_TYPE);
        assertThat(firstLine.getKeyAccidentalCount())
            .as("first line accidental count").isEqualTo(Song.DEFAULT_KEY_ACCIDENTAL_COUNT);
        assertThat(secondLine.getKeyType()).as("second line key type").isEqualTo(KeyType.SHARPS);
        assertThat(secondLine.getKeyAccidentalCount())
            .as("second line accidental count").isEqualTo(MID_SONG_KEY_ACCIDENTAL_COUNT);

        var firstNote = firstLine.getElement(0);
        var secondNote = secondLine.getElement(0);

        var tempo = firstNote.findAttachment(TempoChangeAttachment.class);
        assertThat(tempo).as("first note carries the mirrored base tempo").isNotNull();

        assertThat(tempo.getTempo().getVisibleTempo()).as("base tempo bpm").isEqualTo(BASE_TEMPO_BPM);
        assertThat(tempo.getTempo().getTempoType()).as("base tempo type").isEqualTo(Duration.CROTCHET);

        assertThat(reloaded.getTempo().getVisibleTempo())
            .as("song base tempo bpm").isEqualTo(BASE_TEMPO_BPM);

        var firstLyric = firstNote.getLyricForVerse(FIRST_VERSE);
        var secondLyric = secondNote.getLyricForVerse(FIRST_VERSE);

        assertThat(firstLyric).as("first line lyric present").isNotNull();
        assertThat(secondLyric).as("second line lyric present").isNotNull();

        assertThat(firstLyric.text()).as("first line lyric text").isEqualTo(FIRST_LINE_LYRIC);
        assertThat(secondLyric.text()).as("second line lyric text").isEqualTo(SECOND_LINE_LYRIC);
    }

    @Test
    void testAboveAndBelowAnnotationsRoundTrip() throws Exception {
        var song = fullDocumentSong();
        var reloaded = roundTrip(song);

        var firstNote = reloaded.getLine(FIRST_LINE_INDEX).getElement(0);
        var secondNote = reloaded.getLine(SECOND_LINE_INDEX).getElement(0);

        var aboveAttachment = firstNote.findAttachment(AnnotationAttachment.class);
        var belowAttachment = secondNote.findAttachment(AnnotationAttachment.class);

        assertThat(aboveAttachment).as("ABOVE annotation attachment present").isNotNull();
        assertThat(belowAttachment).as("BELOW annotation attachment present").isNotNull();

        var aboveAnnotation = aboveAttachment.getAnnotation();
        assertThat(aboveAnnotation.getAnnotation()).as("ABOVE annotation text").isEqualTo(ABOVE_ANNOTATION_TEXT);
        assertThat(aboveAnnotation.getPlacement())
            .as("ABOVE annotation placement")
            .isEqualTo(Annotation.Placement.ABOVE);
        assertThat(aboveAnnotation.getUserYOffsetSs()).as("ABOVE annotation Y offset").isEqualTo(ABOVE_Y_OFFSET_SS);

        var belowAnnotation = belowAttachment.getAnnotation();
        assertThat(belowAnnotation.getAnnotation()).as("BELOW annotation text").isEqualTo(BELOW_ANNOTATION_TEXT);
        assertThat(belowAnnotation.getPlacement())
            .as("BELOW annotation placement")
            .isEqualTo(Annotation.Placement.BELOW);
        assertThat(belowAnnotation.getUserYOffsetSs()).as("BELOW annotation Y offset").isEqualTo(BELOW_Y_OFFSET_SS);
    }

    /**
     * The display-only title and composer credits must not win over the
     * canonical head metadata. Unlike {@code MusicXmlCreditRoundTripTest}
     * (which relies on distinct interspersed values), this hand-edits the
     * written XML so the credit text itself disagrees with the head — proving
     * the reader never reads a display-only credit's text into the model.
     */
    @Test
    void testDisplayOnlyCreditsDoNotCorruptCanonicalMetadataWhenHandEdited() throws Exception {
        var song = fullDocumentSong();
        var xml = writeToString(song);

        var creditWordsOpenTag = '<' + MusicXmlTags.CREDIT_WORDS + "[^>]*>";
        var creditWordsCloseTag = "</" + MusicXmlTags.CREDIT_WORDS + '>';

        var tampered = xml
            .replaceAll(
                '(' + creditWordsOpenTag + ')' + Pattern.quote(song.getNumberedTitle()) + '(' + creditWordsCloseTag + ')',
                "$1" + TAMPERED_TITLE_TEXT + "$2"
            )
            .replaceAll(
                '(' + creditWordsOpenTag + ')' + Pattern.quote(COMPOSER) + '(' + creditWordsCloseTag + ')',
                "$1" + TAMPERED_COMPOSER_TEXT + "$2"
            );

        // The replacements must have matched, or the test would prove nothing.
        assertThat(tampered)
            .as("tamper precondition: the title and composer credit texts were rewritten")
            .contains(TAMPERED_TITLE_TEXT)
            .contains(TAMPERED_COMPOSER_TEXT);

        var reloaded = parse(tampered);

        assertThat(reloaded.getTitle())
            .as("title re-derives from <movement-title>, unaffected by a tampered title credit")
            .isEqualTo(TITLE);
        assertThat(reloaded.getComposer())
            .as("composer re-derives from <creator type=\"composer\">, unaffected by a tampered composer credit")
            .isEqualTo(COMPOSER);
    }

    // -------------------------------------------------------------------------
    // Schema validation
    // -------------------------------------------------------------------------

    @Test
    void testFullDocumentRoundTripIsSchemaValid() throws Exception {
        var song = fullDocumentSong();
        var xml = writeToString(song, customFonts());
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(xml))
            .as("the fully-populated document validates against the MusicXML 4.0 schema")
            .doesNotThrowAnyException();
    }
}
