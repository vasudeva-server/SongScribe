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
package songscribe.io.musicxml;

import java.awt.Font;
import java.io.PrintWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import songscribe.Constants;
import songscribe.Version;
import songscribe.dom.Song;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;
import songscribe.io.XML;
import songscribe.util.DateUtils;

final class MusicXmlHeaderWriter {

    private static final Logger LOG = LoggerFactory.getLogger(MusicXmlHeaderWriter.class);

    private MusicXmlHeaderWriter() {}

    /**
     * The clock- and date-derived header strings, resolved once per write so the
     * {@code <miscellaneous>} block and the {@code <credit>} list agree on a
     * single value for each.
     *
     * @param compositionDate the composition date as a reduced-precision ISO
     *                        string, or {@code ""} when the song has none
     * @param lyricsDate      the words/lyrics date, or {@code ""} when absent
     *                        (the model normalizes a words-date equal to the
     *                        composition date to empty, so a non-empty value
     *                        here is always genuinely distinct)
     * @param rights          the {@code <rights>}/rights-credit copyright line
     * @param encodingDate    the {@code <encoding-date>} in ISO local-date form
     */
    record HeaderText(String compositionDate, String lyricsDate, String rights, String encodingDate) {

        static HeaderText of(Song song, Clock clock) {
            var compositionDate = DateUtils.toIsoDate(song.getYear(), song.getMonth(), song.getDay());
            var lyricsDate = DateUtils.toIsoDate(song.getWordsYear(), song.getWordsMonth(), song.getWordsDay());

            var currentDate = LocalDate.now(clock);
            var rights = String.format(MusicXmlTags.COPYRIGHT, currentDate.getYear());
            var encodingDate = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

            return new HeaderText(compositionDate, lyricsDate, rights, encodingDate);
        }
    }

    /**
     * Writes {@code <movement-number>} (omitted when {@code getNumber()} is
     * blank) followed by {@code <movement-title>}, in schema order.
     */
    static void writeMovementInfo(Song song, PrintWriter pw) {
        if (!song.getNumber().isEmpty()) {
            XML.writeValue(pw, MusicXmlTags.MOVEMENT_NUMBER, song.getNumber());
        }

        XML.writeValue(pw, MusicXmlTags.MOVEMENT_TITLE, song.getTitle());
    }

    /**
     * Writes {@code <identification>}: composer/lyricist/arranger
     * {@code <creator>}s, the write-forward {@code <rights>} and
     * {@code <encoding>} blocks (dated from {@code headerText} so writer-output
     * tests can pin a fixed date), and the residual {@code <miscellaneous>}
     * block.
     */
    static void writeIdentification(Song song, DocumentFontsHolder fonts, HeaderText headerText, PrintWriter pw) {
        XML.writeBeginTag(pw, MusicXmlTags.IDENTIFICATION);
        XML.indent();

        XML.writeValue(
            pw,
            MusicXmlTags.CREATOR,
            song.getComposer(),
            MusicXmlTags.ATTR_TYPE, MusicXmlTags.CREATOR_COMPOSER
        );
        XML.writeValue(
            pw,
            MusicXmlTags.CREATOR,
            song.getLyricist(),
            MusicXmlTags.ATTR_TYPE, MusicXmlTags.CREATOR_LYRICIST
        );

        if (song.isArrangement()) {
            XML.writeValue(
                pw,
                MusicXmlTags.CREATOR,
                Song.SRI_CHINMOY,
                MusicXmlTags.ATTR_TYPE, MusicXmlTags.CREATOR_ARRANGER
            );
        }

        XML.writeValue(pw, MusicXmlTags.RIGHTS, headerText.rights());

        XML.writeBeginTag(pw, MusicXmlTags.ENCODING);
        XML.indent();
        XML.writeValue(pw, MusicXmlTags.SOFTWARE, Constants.PACKAGE_NAME + ' ' + Version.PUBLIC_VERSION);
        XML.writeValue(pw, MusicXmlTags.ENCODING_DATE, headerText.encodingDate());
        XML.writeEmptyTag(
            pw,
            MusicXmlTags.SUPPORTS,
            MusicXmlTags.ATTR_ELEMENT, MusicXmlTags.SUPPORTS_ACCIDENTAL,
            MusicXmlTags.ATTR_TYPE, MusicXmlTags.YES
        );
        XML.writeEmptyTag(
            pw,
            MusicXmlTags.SUPPORTS,
            MusicXmlTags.ATTR_ELEMENT, MusicXmlTags.SUPPORTS_BEAM,
            MusicXmlTags.ATTR_TYPE, MusicXmlTags.YES
        );
        XML.writeEmptyTag(
            pw,
            MusicXmlTags.SUPPORTS,
            MusicXmlTags.ATTR_ELEMENT, MusicXmlTags.SUPPORTS_STEM,
            MusicXmlTags.ATTR_TYPE, MusicXmlTags.YES
        );
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.ENCODING);

        writeMiscellaneousFields(song, fonts, headerText, pw);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.IDENTIFICATION);
    }

    /**
     * Writes the residual {@code <miscellaneous>} block (composition-date,
     * lyrics-date, composition-place, lyrics-source, unofficial-translation,
     * sub-attribution-font/-size, row-height-adjustment). The block is always
     * emitted: lyrics-source and the sub-attribution font are unconditional, so
     * at least three fields are always present. Fields are collected into one insertion-ordered
     * name→value map so they emit in order in a single {@code <miscellaneous>}
     * block, with each name paired to its value at the point of insertion (no
     * index coupling between separate name/value lists).
     */
    private static void writeMiscellaneousFields(Song song, DocumentFontsHolder fonts, HeaderText headerText, PrintWriter pw) {
        var fields = new LinkedHashMap<String, String>();

        if (!headerText.compositionDate().isEmpty()) {
            fields.put(MusicXmlTags.MISC_COMPOSITION_DATE, headerText.compositionDate());
        }

        if (!headerText.lyricsDate().isEmpty()) {
            fields.put(MusicXmlTags.MISC_LYRICS_DATE, headerText.lyricsDate());
        }

        if (!song.getPlace().isEmpty()) {
            fields.put(MusicXmlTags.MISC_COMPOSITION_PLACE, song.getPlace());
        }

        fields.put(MusicXmlTags.MISC_LYRICS_SOURCE, song.getLyricsSource().name());

        if (song.isUnofficialTranslation()) {
            fields.put(MusicXmlTags.MISC_UNOFFICIAL_TRANSLATION, "true");
        }

        // The sub-attribution font rides in the miscellaneous block (no
        // <defaults> element carries a sub-attribution role); it is always
        // emitted so the reader can recover it.
        var subAttributionFont = fonts.getFont(FontKey.SUB_ATTRIBUTION);
        // The PostScript name is the font's canonical identity here: the reader
        // resolves it via MyFontUtils.createFont, which is keyed on PS name (the
        // family name would not resolve). Mirrors the native format (ViewIO).
        fields.put(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT, subAttributionFont.getPSName());
        fields.put(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE, String.valueOf(subAttributionFont.getSize()));

        // Row-height adjustment is a delta from the computed base; omit when 0.
        var rowHeightAdjustmentSs = song.getRowHeightAdjustmentSs();

        if (rowHeightAdjustmentSs != 0) {
            fields.put(MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT, String.valueOf(rowHeightAdjustmentSs));
        }

        // The line-wide rest length is a song scalar; omit it at the default so untouched songs
        // and older readers stay clean (the reader falls back to the default when it is absent).
        var defaultRestLengthSs = song.getDefaultRestLengthSs();

        if (defaultRestLengthSs != Song.DEFAULT_REST_LENGTH_SS) {
            fields.put(MusicXmlTags.MISC_DEFAULT_REST_LENGTH, String.valueOf(defaultRestLengthSs));
        }

        XML.writeBeginTag(pw, MusicXmlTags.MISCELLANEOUS);
        XML.indent();

        for (var field : fields.entrySet()) {
            XML.writeValue(
                pw,
                MusicXmlTags.MISCELLANEOUS_FIELD,
                field.getValue(),
                MusicXmlTags.ATTR_NAME, field.getKey()
            );
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.MISCELLANEOUS);
    }

    /**
     * Writes the {@code <defaults>} block: the fixed {@code <scaling>}, the
     * {@code <page-layout>} carrying the model line width as {@code <page-width>},
     * a zero {@code <staff-layout>}, and the document fonts. {@code <scaling>},
     * {@code <page-height>}, and {@code <music-font>} are write-forward (fixed)
     * and ignored on read; {@code <page-width>}, {@code <word-font>}, and
     * {@code <lyric-font>} round-trip (the sub-attribution font rides in the
     * {@code <miscellaneous>} block, as MusicXML has no sub-attribution role).
     */
    static void writeDefaults(Song song, DocumentFontsHolder fonts, PrintWriter pw) {
        XML.writeBeginTag(pw, MusicXmlTags.DEFAULTS);
        XML.indent();

        // Fixed scaling: 7 mm per 40 tenths (one 4-space staff), the MusicXML
        // default. Write-forward — the reader ignores it.
        XML.writeBeginTag(pw, MusicXmlTags.SCALING);
        XML.indent();
        XML.writeValue(
            pw,
            MusicXmlTags.MILLIMETERS,
            String.format(Locale.ROOT, "%.0f", MusicXmlTags.SCALING_MILLIMETERS)
        );
        XML.writeValue(pw, MusicXmlTags.TENTHS, MusicXmlTags.SCALING_TENTHS);
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.SCALING);

        // Page layout: page-height is a fixed write-forward value; page-width
        // carries the model line width — the one canonical page-layout field.
        XML.writeBeginTag(pw, MusicXmlTags.PAGE_LAYOUT);
        XML.indent();
        XML.writeValue(pw, MusicXmlTags.PAGE_HEIGHT, MusicXmlTags.PAGE_HEIGHT_TENTHS);
        XML.writeValue(pw, MusicXmlTags.PAGE_WIDTH, MusicXmlUnits.formatSsAsTenths(lineWidthSs(song)));
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PAGE_LAYOUT);

        XML.writeBeginTag(pw, MusicXmlTags.STAFF_LAYOUT);
        XML.indent();
        XML.writeValue(pw, MusicXmlTags.STAFF_DISTANCE, MusicXmlUnits.STAFF_DISTANCE_TENTHS);
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.STAFF_LAYOUT);

        // Fixed music font (write-forward); the word/lyric fonts round-trip.
        XML.writeEmptyTag(
            pw,
            MusicXmlTags.MUSIC_FONT,
            MusicXmlTags.ATTR_FONT_FAMILY, MusicXmlTags.MUSIC_FONT_FAMILY,
            MusicXmlTags.ATTR_FONT_SIZE, MusicXmlTags.MUSIC_FONT_SIZE
        );
        writeDocumentFont(pw, MusicXmlTags.WORD_FONT, fonts.getFont(FontKey.ANNOTATION));
        writeDocumentFont(pw, MusicXmlTags.LYRIC_FONT, fonts.getFont(FontKey.LYRICS));
        XML.writeEmptyTag(
            pw,
            MusicXmlTags.LYRIC_LANGUAGE,
            MusicXmlTags.ATTR_XML_LANG, MusicXmlTags.LYRIC_LANGUAGE_DEFAULT
        );

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.DEFAULTS);
    }

    /**
     * Writes a self-closing document-font element ({@code <word-font>} /
     * {@code <lyric-font>}) carrying the role's {@code font-family} and
     * {@code font-size}. Weight/style are not emitted — the reader recovers only
     * family and size back into the {@code DocumentFonts} result.
     * <p>
     * {@code font-family} carries the PostScript name, not the display family:
     * the reader resolves it via {@link songscribe.util.MyFontUtils#createFont},
     * which is keyed on PS name. Mirrors the native format ({@code ViewIO}).
     */
    private static void writeDocumentFont(PrintWriter pw, String tag, Font font) {
        XML.writeEmptyTag(
            pw,
            tag,
            MusicXmlTags.ATTR_FONT_FAMILY, font.getPSName(),
            MusicXmlTags.ATTR_FONT_SIZE, String.valueOf(font.getSize())
        );
    }

    /**
     * The line width to write as {@code <page-width>}, never zero or negative.
     * <p>
     * A song whose width never got initialized would otherwise persist a zero-width
     * staff, and nothing on the read side rejects one: the document reopens with a
     * staff no content can fit, so every line opens clipped and drawn in red, behind a
     * warning that content is being cut off, instead of showing the song. Substituting
     * the fallback keeps the document readable, and the warning records that a song
     * reached the writer in that state.
     */
    private static double lineWidthSs(Song song) {
        var lineWidthSs = song.getLineWidthSs();

        if (lineWidthSs > 0) {
            return lineWidthSs;
        }

        LOG.warn(
            "Song has a non-positive line width ({} ss); writing the fallback {} ss instead",
            lineWidthSs, Song.FALLBACK_LINE_WIDTH_SS);

        return Song.FALLBACK_LINE_WIDTH_SS;
    }

    /**
     * Writes the {@code <credit>} elements: title, subtitle, each attribution
     * role, and the score-below text blocks, in that order. Every credit is
     * page 1 (single-page model), so the {@code page} attribute is never
     * written (the schema default is already {@code 1}).
     */
    static void writeCredits(Song song, DocumentFontsHolder fonts, HeaderText headerText, PrintWriter pw) {
        writeCredit(pw, fonts, MusicXmlTags.CREDIT_TITLE, FontKey.TITLE, song.getNumberedTitle(), MusicXmlTags.JUSTIFY_CENTER, null, null);
        writeCredit(pw, fonts, MusicXmlTags.CREDIT_SUBTITLE, FontKey.SUBTITLE, song.getSubtitle(), null, null, null);

        var attributionRelativeYSs = song.getAttributionElement().getUserYOffsetSs();

        writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_COMPOSER, song.getComposer(), attributionRelativeYSs);
        writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_LYRICIST, song.getLyricist(), attributionRelativeYSs);

        if (song.isArrangement()) {
            writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_ARRANGER, Song.SRI_CHINMOY, attributionRelativeYSs);
        }

        writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_COMPOSITION_DATE, headerText.compositionDate(), attributionRelativeYSs);

        if (!headerText.lyricsDate().isEmpty()) {
            writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_LYRICS_DATE, headerText.lyricsDate(), attributionRelativeYSs);
        }

        writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_RIGHTS, headerText.rights(), attributionRelativeYSs);
        writeAttributionCredit(pw, fonts, MusicXmlTags.CREDIT_PLACE, song.getPlace(), attributionRelativeYSs);

        writeCredit(pw, fonts, MusicXmlTags.CREDIT_UNDERLYRICS, FontKey.LYRICS, song.getUnderLyrics(), null, null, null);
        writeCredit(pw, fonts, MusicXmlTags.CREDIT_BANGLA_LYRICS, FontKey.BANGLA, song.getBanglaLyrics(), null, null, MusicXmlTags.CREDIT_LANGUAGE_BANGLA);
        writeCredit(pw, fonts, MusicXmlTags.CREDIT_TRANSLATION, FontKey.LYRICS, song.getTranslatedLyrics(), null, null, null);
        writeCredit(pw, fonts, MusicXmlTags.CREDIT_FOOTNOTES, FontKey.FOOTNOTE, song.getFootnotes(), null, null, null);
    }

    /**
     * Writes one attribution-role {@code <credit>} (composer, lyricist, arranger,
     * the two dates, rights, place): an {@code ATTRIBUTION}-font credit carrying
     * the shared attribution {@code relative-y} and no {@code justify}/
     * {@code xml:lang}. Convenience over {@link #writeCredit} for the seven
     * attribution calls, which are identical but for their type and text.
     */
    private static void writeAttributionCredit(PrintWriter pw, DocumentFontsHolder fonts, String creditType, String text, double relativeYSs) {
        writeCredit(pw, fonts, creditType, FontKey.ATTRIBUTION, text, null, relativeYSs, null);
    }

    /**
     * Writes one {@code <credit>} — {@code <credit-type>} then
     * {@code <credit-words>} — when {@code text} is non-blank; emits nothing
     * otherwise. {@code <credit-words>} always carries font-family/font-size/
     * font-weight/font-style from {@code fonts.getFont(fontKey)} (weight/style
     * via {@link Font#isBold()}/{@link Font#isItalic()});
     * {@code justify}/{@code xmlLang} are written only when non-null, and
     * {@code relativeYSs} (a staff-space offset, converted here via the shared
     * {@link MusicXmlUnits#ssToTenths} / {@link MusicXmlUnits#formatTenths}) only when non-null — the
     * attribution roles always pass a value (even {@code 0}) so the reader can
     * recover the offset; title/subtitle/score-below credits pass {@code null}
     * so no {@code relative-y} is written. {@code default-x}/{@code default-y}
     * are write-forward, external-renderer-only fields (see the sub-plan's
     * "Explicitly OUT of scope" list) and are never emitted here.
     */
    private static void writeCredit(
            PrintWriter pw,
            DocumentFontsHolder fonts,
            String creditType,
            FontKey fontKey,
            String text,
            @Nullable String justify,
            @Nullable Double relativeYSs,
            @Nullable String xmlLang) {
        if (text.isBlank()) {
            return;
        }

        XML.writeBeginTag(pw, MusicXmlTags.CREDIT);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.CREDIT_TYPE, creditType);

        var font = fonts.getFont(fontKey);
        var creditWordsAttrs = new ArrayList<String>();
        creditWordsAttrs.add(MusicXmlTags.ATTR_FONT_FAMILY);
        // The PostScript name, not the display family: the reader resolves it via
        // MyFontUtils.createFont (keyed on PS name). Mirrors the native format.
        creditWordsAttrs.add(font.getPSName());
        creditWordsAttrs.add(MusicXmlTags.ATTR_FONT_SIZE);
        creditWordsAttrs.add(String.valueOf(font.getSize()));
        creditWordsAttrs.add(MusicXmlTags.ATTR_FONT_WEIGHT);
        creditWordsAttrs.add(font.isBold() ? MusicXmlTags.WEIGHT_BOLD : MusicXmlTags.WEIGHT_NORMAL);
        creditWordsAttrs.add(MusicXmlTags.ATTR_FONT_STYLE);
        creditWordsAttrs.add(font.isItalic() ? MusicXmlTags.STYLE_ITALIC : MusicXmlTags.STYLE_NORMAL);

        if (justify != null) {
            creditWordsAttrs.add(MusicXmlTags.ATTR_JUSTIFY);
            creditWordsAttrs.add(justify);
        }

        if (xmlLang != null) {
            creditWordsAttrs.add(MusicXmlTags.ATTR_XML_LANG);
            creditWordsAttrs.add(xmlLang);
        }

        if (relativeYSs != null) {
            creditWordsAttrs.add(MusicXmlTags.ATTR_RELATIVE_Y);
            creditWordsAttrs.add(MusicXmlUnits.formatSsAsTenths(relativeYSs));
        }

        XML.writeValue(pw, MusicXmlTags.CREDIT_WORDS, text, creditWordsAttrs.toArray(new String[0]));

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.CREDIT);
    }
}
