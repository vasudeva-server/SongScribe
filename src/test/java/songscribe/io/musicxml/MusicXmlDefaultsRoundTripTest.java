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
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;

/**
 * Round-trip coverage for the {@code <defaults>} writer + reader (Phase 6): the
 * line width (via {@code <page-width>}), the row-height adjustment (via a
 * {@code <miscellaneous-field>}), and the three round-tripping document-font
 * roles — annotation ({@code <word-font>}), lyrics ({@code <lyric-font>}), and
 * sub-attribution (via the misc-field pair) — reload from a written document. The
 * write-forward layout ({@code <scaling>}, {@code <page-height>},
 * {@code <music-font>}) is emitted schema-valid but ignored on read.
 */
class MusicXmlDefaultsRoundTripTest extends MusicXmlRoundTripSupport {

    // Line width in staff spaces. Integer-valued so the ss → tenths → ss
    // conversion (via the reader's integer tenthsToSs) is exact.
    private static final double LINE_WIDTH_SS = 120.0;

    // A non-zero row-height delta (staff spaces). Stored verbatim as a
    // misc-field, so any value with an exact Double.toString round-trip works.
    private static final double ROW_HEIGHT_ADJUSTMENT_SS = 3.5;

    // Distinct family/size pairs for the three round-tripping roles, chosen so a
    // role mix-up (e.g. word ↔ lyric) is caught: the sizes differ, so even in a
    // test environment where every family resolves to the same fallback face the
    // recovered sizes still discriminate.
    private static final String WORD_FONT_FAMILY             = "Round-Trip Word Family";
    private static final int    WORD_FONT_SIZE               = 41;
    private static final String LYRIC_FONT_FAMILY            = "Round-Trip Lyric Family";
    private static final int    LYRIC_FONT_SIZE              = 43;
    private static final String SUB_ATTRIBUTION_FONT_FAMILY  = "Round-Trip Sub-Attribution Family";
    private static final int    SUB_ATTRIBUTION_FONT_SIZE    = 47;

    // The three roles the writer persists under <defaults> / the miscellaneous
    // block; the other five are write-forward (credit-words only) and reload at
    // their defaults.
    private static final FontKey[] ROUND_TRIP_FONT_ROLES = {
        FontKey.ANNOTATION, FontKey.LYRICS, FontKey.SUB_ATTRIBUTION
    };

    // Write-forward values overwritten by hand to prove the reader ignores them.
    private static final String TAMPERED_PAGE_HEIGHT = "9999";
    private static final String TAMPERED_SCALING_TENTHS = "80";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A minimal one-note song — the writer needs one element to emit a part. */
    private static Song singleNoteSong() {
        return buildSong(line -> line.addElement(ElementType.CROTCHET.newInstance()));
    }

    /** A one-note song carrying a non-default line width and row-height delta. */
    private static Song songWithLayout() {
        var song = singleNoteSong();

        song.withoutMutationTracking(() -> {
            song.setLineWidthSs(LINE_WIDTH_SS);
            song.setRowHeightAdjustmentSs(ROW_HEIGHT_ADJUSTMENT_SS);
        });

        return song;
    }

    /** Default fonts with the three round-tripping roles overridden to distinct values. */
    private static DocumentFonts customFonts() {
        var fonts = DocumentFonts.defaultFonts();
        fonts.setFont(FontKey.ANNOTATION, WORD_FONT_FAMILY, WORD_FONT_SIZE);
        fonts.setFont(FontKey.LYRICS, LYRIC_FONT_FAMILY, LYRIC_FONT_SIZE);
        fonts.setFont(FontKey.SUB_ATTRIBUTION, SUB_ATTRIBUTION_FONT_FAMILY, SUB_ATTRIBUTION_FONT_SIZE);
        return fonts;
    }

    /**
     * The {@link DocumentFonts} the reader must produce for a document written
     * with {@code source}: it starts from the default set and rebuilds each
     * round-tripping role from the {@code font-family}/{@code font-size} the
     * writer emitted — exactly {@code getFamily()}/{@code getSize()} of the source
     * font. Mirroring the reader's own {@code setFont(role, family, size)} here
     * isolates the serialization round-trip (family + size carried through the
     * XML) from the font system's family → font resolution, which is applied
     * identically on the read side and here — so an exact {@code DocumentFonts}
     * equality holds without depending on that resolution being an idempotent
     * fixed point across the {@code getFamily()} round-trip.
     */
    private static DocumentFonts expectedRecoveredFonts(DocumentFonts source) {
        var expected = DocumentFonts.defaultFonts();

        for (var role : ROUND_TRIP_FONT_ROLES) {
            var font = source.getFont(role);
            expected.setFont(role, font.getFamily(), font.getSize());
        }

        return expected;
    }

    // -------------------------------------------------------------------------
    // Round-trip tests
    // -------------------------------------------------------------------------

    @Test
    void testLineWidthAndRowHeightRoundTrip() throws Exception {
        var song = songWithLayout();
        var reloaded = roundTrip(song);

        assertThat(reloaded.getLineWidthSs())
            .as("line width reloads from <page-width>")
            .isEqualTo(LINE_WIDTH_SS);
        assertThat(reloaded.getRowHeightAdjustmentSs())
            .as("row-height adjustment reloads from its misc-field")
            .isEqualTo(ROW_HEIGHT_ADJUSTMENT_SS);
    }

    @Test
    void testDocumentFontsRoundTrip() throws Exception {
        var song = singleNoteSong();
        var fonts = customFonts();

        var result = roundTrip(song, fonts);

        assertThat(result.fonts())
            .as("word / lyric / sub-attribution fonts reload from <defaults> + misc-fields")
            .isEqualTo(expectedRecoveredFonts(fonts));

        // The recovered point sizes are carried verbatim regardless of how the
        // font system resolves the family, so these direct checks are exact.
        assertThat(result.fonts().getFont(FontKey.ANNOTATION).getSize())
            .as("annotation (<word-font>) size survives")
            .isEqualTo(WORD_FONT_SIZE);
        assertThat(result.fonts().getFont(FontKey.LYRICS).getSize())
            .as("lyrics (<lyric-font>) size survives")
            .isEqualTo(LYRIC_FONT_SIZE);
        assertThat(result.fonts().getFont(FontKey.SUB_ATTRIBUTION).getSize())
            .as("sub-attribution (misc-field) size survives")
            .isEqualTo(SUB_ATTRIBUTION_FONT_SIZE);
    }

    @Test
    void testNoDefaultsFontsReadsBackDefaultFonts() throws Exception {
        // Strip the <defaults> block and the sub-attribution misc-fields so the
        // document carries no font information at all; the reader must then fall
        // back to the canonical default set.
        var xml = writeToString(songWithLayout(), customFonts());
        var stripped = xml
            .replaceAll("(?s)\\s*<defaults>.*?</defaults>", "")
            .replaceAll("(?m)^.*name=\"sub-attribution-font.*\\R?", "");

        var result = parseResult(stripped);

        assertThat(result.fonts())
            .as("a document with no <defaults>/sub-attribution fonts reads back the defaults")
            .isEqualTo(DocumentFonts.defaultFonts());
    }

    @Test
    void testHandEditedWriteForwardValuesAreIgnoredOnRead() throws Exception {
        // <page-height> and <scaling> are write-forward: hand-editing them must
        // not change the reloaded model (the line width comes from <page-width>).
        var song = songWithLayout();
        var xml = writeToString(song);

        var tampered = xml
            .replace(
                "<" + MusicXmlTags.PAGE_HEIGHT + ">" + MusicXmlTags.PAGE_HEIGHT_TENTHS
                    + "</" + MusicXmlTags.PAGE_HEIGHT + ">",
                "<" + MusicXmlTags.PAGE_HEIGHT + ">" + TAMPERED_PAGE_HEIGHT
                    + "</" + MusicXmlTags.PAGE_HEIGHT + ">"
            )
            .replace(
                "<" + MusicXmlTags.TENTHS + ">" + MusicXmlTags.SCALING_TENTHS
                    + "</" + MusicXmlTags.TENTHS + ">",
                "<" + MusicXmlTags.TENTHS + ">" + TAMPERED_SCALING_TENTHS
                    + "</" + MusicXmlTags.TENTHS + ">"
            );

        // The replacements must have matched, or the test would prove nothing.
        assertThat(tampered)
            .as("tamper precondition: page-height / scaling values were rewritten")
            .contains(TAMPERED_PAGE_HEIGHT)
            .contains("<" + MusicXmlTags.TENTHS + ">" + TAMPERED_SCALING_TENTHS);

        var reloaded = parse(tampered);

        assertThat(reloaded.getLineWidthSs())
            .as("line width is unaffected by a tampered <page-height>/<scaling>")
            .isEqualTo(LINE_WIDTH_SS);
        assertThat(reloaded.getRowHeightAdjustmentSs())
            .as("row-height adjustment is unaffected by a tampered <page-height>/<scaling>")
            .isEqualTo(ROW_HEIGHT_ADJUSTMENT_SS);
    }

    // -------------------------------------------------------------------------
    // Schema validation
    // -------------------------------------------------------------------------

    @Test
    void testDefaultsRoundTripIsSchemaValid() throws Exception {
        var xml = writeToString(songWithLayout(), customFonts());
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(xml))
            .as("defaults output validates against the MusicXML 4.0 schema")
            .doesNotThrowAnyException();
    }
}
