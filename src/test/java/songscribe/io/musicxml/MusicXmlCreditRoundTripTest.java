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
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;

/**
 * Round-trip coverage for the {@code <credit>} reader (Phase 8): the canonical
 * credits (subtitle + the four score-below text blocks) reload verbatim, the
 * attribution user Y offset survives via {@code relative-y}, the title/subtitle
 * fonts survive via the credit's {@code font-family}/{@code font-size}, and the
 * display-only credits (title, attribution roles) do not corrupt the canonical
 * head metadata the reader reads back — a hand-edited display-only credit never
 * wins over the canonical value.
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

    // Distinct family/size pairs for the title/subtitle roles, chosen so a role
    // mix-up (e.g. title ↔ subtitle) is caught: the sizes differ, so even in a
    // test environment where every family resolves to the same fallback face the
    // recovered sizes still discriminate (see MusicXmlDefaultsRoundTripTest).
    private static final String TITLE_FONT_FAMILY    = "Round-Trip Title Family";
    private static final int    TITLE_FONT_SIZE       = 53;
    private static final String SUBTITLE_FONT_FAMILY = "Round-Trip Subtitle Family";
    private static final int    SUBTITLE_FONT_SIZE    = 59;

    // A fractional font-size proving applyFont rounds the schema decimal to an int
    // point size: TITLE_FONT_SIZE + ".6" rounds up to TITLE_FONT_SIZE + 1.
    private static final String TITLE_FONT_SIZE_DECIMAL = TITLE_FONT_SIZE + ".6";
    private static final int    TITLE_FONT_SIZE_ROUNDED = TITLE_FONT_SIZE + 1;

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

    /** Default fonts with the title/subtitle roles overridden to distinct values. */
    private static DocumentFonts customFonts() {
        var fonts = DocumentFonts.defaultFonts();
        fonts.setFont(FontKey.TITLE, TITLE_FONT_FAMILY, TITLE_FONT_SIZE);
        fonts.setFont(FontKey.SUBTITLE, SUBTITLE_FONT_FAMILY, SUBTITLE_FONT_SIZE);
        return fonts;
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
    void testTitleAndSubtitleFontsRoundTrip() throws Exception {
        // Both credits must be non-blank for the writer to emit their font
        // attributes (a blank-text credit is skipped entirely).
        var song = fullCreditSong();
        var fonts = customFonts();

        var result = roundTrip(song, fonts);

        // Size is the discriminating check here: the distinct 53/59 sizes catch a
        // title ↔ subtitle role mix-up. The PS-name assertions confirm the identity
        // reloads, but in this test environment both custom families collapse to the
        // same fallback face, so they cannot detect a writer that emits the display
        // family instead of the PS name — that write-side regression is guarded by
        // testCreditFontFamilyCarriesPostScriptName.
        assertThat(result.fonts().getFont(FontKey.TITLE).getPSName())
            .as("title credit font identity reloads from <credit-words>")
            .isEqualTo(fonts.getFont(FontKey.TITLE).getPSName());
        assertThat(result.fonts().getFont(FontKey.TITLE).getSize())
            .as("title credit font-size reloads from <credit-words>")
            .isEqualTo(TITLE_FONT_SIZE);
        assertThat(result.fonts().getFont(FontKey.SUBTITLE).getPSName())
            .as("subtitle credit font identity reloads from <credit-words>")
            .isEqualTo(fonts.getFont(FontKey.SUBTITLE).getPSName());
        assertThat(result.fonts().getFont(FontKey.SUBTITLE).getSize())
            .as("subtitle credit font-size reloads from <credit-words>")
            .isEqualTo(SUBTITLE_FONT_SIZE);
    }

    @Test
    void testCreditFontFamilyCarriesPostScriptName() throws Exception {
        // Regression guard for the write-side of the round-trip: the reader
        // resolves the credit font via MyFontUtils.createFont, which is keyed on
        // the PostScript name — so the writer must emit the PS name (not the
        // display family) in font-family, or the name resolves to a fallback face
        // on read while the size (a separate attribute) misleadingly survives.
        var song = fullCreditSong();
        var fonts = customFonts();
        var titleFont = fonts.getFont(FontKey.TITLE);

        // Only meaningful when the two names differ; they do for any real face
        // (e.g. "LucidaGrande" vs "Lucida Grande"), so a coincidental match here
        // signals a test-environment font whose identity cannot discriminate.
        assertThat(titleFont.getPSName())
            .as("precondition: the title font's PS name differs from its display family")
            .isNotEqualTo(titleFont.getFamily());

        var xml = writeToString(song, fonts);

        var titleSizeAttr = MusicXmlTags.ATTR_FONT_SIZE + "=\"" + TITLE_FONT_SIZE + "\"";
        assertThat(xml)
            .as("the title credit's font-family carries the PostScript name")
            .contains(MusicXmlTags.ATTR_FONT_FAMILY + "=\"" + titleFont.getPSName() + "\" " + titleSizeAttr);
        assertThat(xml)
            .as("the title credit's font-family is not the display family name")
            .doesNotContain(MusicXmlTags.ATTR_FONT_FAMILY + "=\"" + titleFont.getFamily() + "\" " + titleSizeAttr);
    }

    @Test
    void testBlankSubtitleFontFallsBackToDefault() throws Exception {
        // A blank subtitle emits no <credit> (the writer skips blank-text
        // credits), so a customized subtitle font has no credit-words to ride
        // home on and is silently dropped — the role reloads at its default.
        var song = singleNoteSong();
        var fonts = customFonts();

        assertThat(song.getSubtitle()).as("precondition: subtitle starts blank").isEmpty();

        var result = roundTrip(song, fonts);

        assertThat(result.fonts().getFont(FontKey.SUBTITLE))
            .as("a blank subtitle writes no credit, so its custom font is not recovered")
            .isEqualTo(DocumentFonts.defaultFonts().getFont(FontKey.SUBTITLE));
    }

    @Test
    void testCreditWordsMissingFontFamilyLeavesRoleAtDefault() throws Exception {
        // setCreditFont's defensive guard: a hand-edited <credit-words> missing
        // font-family recovers no font, leaving the role at its default. The
        // writer always emits font-family, so this is only reachable by tampering.
        var song = fullCreditSong();
        var fonts = customFonts();
        var xml = writeToString(song, fonts);

        // The resolved family name is shared across credits, but the title size is
        // unique — target the font-family that immediately precedes the title's
        // font-size so only that credit loses its family.
        var titleFontSize = MusicXmlTags.ATTR_FONT_SIZE + "=\"" + TITLE_FONT_SIZE + "\"";
        var familyBeforeTitleSize = MusicXmlTags.ATTR_FONT_FAMILY + "=\"[^\"]*\" " + titleFontSize;
        var tampered = xml.replaceAll(familyBeforeTitleSize, titleFontSize);

        assertThat(xml)
            .as("precondition: the title credit carried a font-family before its font-size")
            .containsPattern(familyBeforeTitleSize);
        assertThat(tampered)
            .as("tamper removed the title credit's font-family")
            .doesNotContainPattern(familyBeforeTitleSize);

        var result = parseResult(tampered);

        assertThat(result.fonts().getFont(FontKey.TITLE))
            .as("a title credit missing font-family recovers no font (stays default)")
            .isEqualTo(DocumentFonts.defaultFonts().getFont(FontKey.TITLE));
        assertThat(result.fonts().getFont(FontKey.SUBTITLE).getSize())
            .as("the untampered subtitle credit still recovers its font")
            .isEqualTo(SUBTITLE_FONT_SIZE);
    }

    @Test
    void testCreditWordsMissingFontSizeLeavesRoleAtDefault() throws Exception {
        // The other half of applyFont's null guard: a hand-edited <credit-words>
        // missing font-size recovers no font, leaving the role at its default. The
        // writer always emits font-size, so this is only reachable by tampering.
        var song = fullCreditSong();
        var fonts = customFonts();
        var xml = writeToString(song, fonts);

        // The title size is unique, so dropping font-size="53" (with its trailing
        // space) targets only the title credit and leaves its font-family intact.
        var titleFontSize = MusicXmlTags.ATTR_FONT_SIZE + "=\"" + TITLE_FONT_SIZE + "\" ";
        var tampered = xml.replace(titleFontSize, "");

        assertThat(xml)
            .as("precondition: the title credit carried a font-size")
            .contains(titleFontSize);
        assertThat(tampered)
            .as("tamper removed the title credit's font-size")
            .doesNotContain(titleFontSize);

        var result = parseResult(tampered);

        assertThat(result.fonts().getFont(FontKey.TITLE))
            .as("a title credit missing font-size recovers no font (stays default)")
            .isEqualTo(DocumentFonts.defaultFonts().getFont(FontKey.TITLE));
        assertThat(result.fonts().getFont(FontKey.SUBTITLE).getSize())
            .as("the untampered subtitle credit still recovers its font")
            .isEqualTo(SUBTITLE_FONT_SIZE);
    }

    @Test
    void testCreditFontSizeDecimalRoundsToInt() throws Exception {
        // font-size is a schema decimal, but DocumentFonts point sizes are ints:
        // applyFont rounds via (int) Math.round(...). A hand-edited or external-tool
        // document may carry a fractional size; the title credit must reload rounded.
        var song = fullCreditSong();
        var fonts = customFonts();
        var xml = writeToString(song, fonts);

        // The title size is unique, so rewriting font-size="53" targets only the
        // title credit; the fractional .6 must round up to 54.
        var titleSize = MusicXmlTags.ATTR_FONT_SIZE + "=\"" + TITLE_FONT_SIZE + "\"";
        var titleDecimalSize = MusicXmlTags.ATTR_FONT_SIZE + "=\"" + TITLE_FONT_SIZE_DECIMAL + "\"";
        var tampered = xml.replace(titleSize, titleDecimalSize);

        assertThat(xml)
            .as("precondition: the title credit carried the integer title size")
            .contains(titleSize);
        assertThat(tampered)
            .as("tamper set the title credit's font-size to a decimal")
            .contains(titleDecimalSize);

        var result = parseResult(tampered);

        assertThat(result.fonts().getFont(FontKey.TITLE).getSize())
            .as("a decimal credit font-size reloads rounded to the nearest int")
            .isEqualTo(TITLE_FONT_SIZE_ROUNDED);
    }

    @Test
    void testCreditFontWeightAndStyleAreNotRead() throws Exception {
        // font-weight/font-style are write-forward: the reader recovers only
        // family/size, so hand-editing a credit's weight/style must not change the
        // recovered font.
        var song = fullCreditSong();
        var fonts = customFonts();
        var xml = writeToString(song, fonts);

        // Anchor on the title size (unique to the title role) so only the title
        // credit's weight/style are rewritten to bold/italic.
        var titleNormalRun = MusicXmlTags.ATTR_FONT_SIZE + "=\"" + TITLE_FONT_SIZE + "\" "
            + MusicXmlTags.ATTR_FONT_WEIGHT + "=\"" + MusicXmlTags.WEIGHT_NORMAL + "\" "
            + MusicXmlTags.ATTR_FONT_STYLE + "=\"" + MusicXmlTags.STYLE_NORMAL + "\"";
        var titleBoldItalicRun = MusicXmlTags.ATTR_FONT_SIZE + "=\"" + TITLE_FONT_SIZE + "\" "
            + MusicXmlTags.ATTR_FONT_WEIGHT + "=\"" + MusicXmlTags.WEIGHT_BOLD + "\" "
            + MusicXmlTags.ATTR_FONT_STYLE + "=\"" + MusicXmlTags.STYLE_ITALIC + "\"";
        var tampered = xml.replace(titleNormalRun, titleBoldItalicRun);

        assertThat(xml)
            .as("precondition: the title credit carried normal weight/style")
            .contains(titleNormalRun);
        assertThat(tampered)
            .as("tamper set the title credit to bold/italic")
            .contains(titleBoldItalicRun);

        var recovered = parseResult(tampered).fonts().getFont(FontKey.TITLE);
        var untampered = roundTrip(song, fonts).fonts().getFont(FontKey.TITLE);

        assertThat(recovered)
            .as("tampered font-weight/font-style are ignored: the recovered font is unchanged")
            .isEqualTo(untampered);
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
