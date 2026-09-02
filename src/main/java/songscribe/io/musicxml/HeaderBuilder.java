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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.audiveris.proxymusic.Credit;
import org.audiveris.proxymusic.Encoding;
import org.audiveris.proxymusic.FontStyle;
import org.audiveris.proxymusic.FontWeight;
import org.audiveris.proxymusic.LeftCenterRight;
import org.audiveris.proxymusic.Miscellaneous;
import org.audiveris.proxymusic.ObjectFactory;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.Supports;
import org.audiveris.proxymusic.TypedText;
import org.audiveris.proxymusic.YesNo;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import songscribe.dom.Song;
import songscribe.font.DocumentFontsHolder;
import songscribe.font.FontKey;
import songscribe.util.DateUtils;

/**
 * Builds the {@code score-header} portion of a {@link ScorePartwise} graph:
 * {@code <movement-number>}/{@code <movement-title>}, {@code <identification>}, {@code <defaults>}
 * and the {@code <credit>} list.
 */
final class HeaderBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(HeaderBuilder.class);

    private HeaderBuilder() {}

    /**
     * The clock- and date-derived header strings, resolved once per build so the
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
     * Populates {@code <movement-number>} (omitted when {@code getNumber()} is blank) and
     * {@code <movement-title>} on {@code scorePartwise}.
     */
    static void buildMovementInfo(BuildContext context, ScorePartwise scorePartwise) {
        var song = context.song();

        if (!song.getNumber().isEmpty()) {
            scorePartwise.setMovementNumber(song.getNumber());
        }

        scorePartwise.setMovementTitle(song.getTitle());
    }

    /**
     * Populates {@code <identification>}: composer/lyricist/arranger {@code <creator>}s, the
     * write-forward {@code <rights>} and {@code <encoding>} blocks (dated from {@code headerText} so
     * builder-output tests can pin a fixed date), and the residual {@code <miscellaneous>} block.
     */
    static void buildIdentification(
            BuildContext context, ScorePartwise scorePartwise, HeaderText headerText) {
        var song = context.song();
        var fonts = context.fonts();
        var factory = context.factory();

        var identification = factory.createIdentification();
        identification.getCreator().add(newCreator(factory, song.getComposer(), MusicXmlTags.CREATOR_COMPOSER));
        identification.getCreator().add(newCreator(factory, song.getLyricist(), MusicXmlTags.CREATOR_LYRICIST));

        if (song.isArrangement()) {
            identification.getCreator().add(newCreator(factory, Song.SRI_CHINMOY, MusicXmlTags.CREATOR_ARRANGER));
        }

        var rights = factory.createTypedText();
        rights.setValue(headerText.rights());
        identification.getRights().add(rights);

        identification.setEncoding(buildEncoding(factory, headerText));
        identification.setMiscellaneous(buildMiscellaneous(factory, song, fonts, headerText));

        scorePartwise.setIdentification(identification);
    }

    private static TypedText newCreator(ObjectFactory factory, String name, String type) {
        var creator = factory.createTypedText();
        creator.setValue(name);
        creator.setType(type);
        return creator;
    }

    /**
     * Builds the {@code <encoding>} block: {@code <software>}, {@code <encoding-date>}, and three
     * {@code <supports>} entries for accidental, beam and stem, each with {@code type="yes"}.
     *
     * <p>{@code Encoding.getEncodingDateOrEncoderOrSoftware()} is a {@code List<JAXBElement<?>>}, so
     * every item is wrapped by the matching {@code ObjectFactory} method rather than added raw.
     */
    private static Encoding buildEncoding(ObjectFactory factory, HeaderText headerText) {
        var encoding = factory.createEncoding();
        var items = encoding.getEncodingDateOrEncoderOrSoftware();

        items.add(factory.createEncodingSoftware(SoftwareProvenance.SOFTWARE));
        items.add(factory.createEncodingEncodingDate(toEncodingDate(headerText.encodingDate())));
        items.add(factory.createEncodingSupports(newSupports(factory, MusicXmlTags.SUPPORTS_ACCIDENTAL)));
        items.add(factory.createEncodingSupports(newSupports(factory, MusicXmlTags.SUPPORTS_BEAM)));
        items.add(factory.createEncodingSupports(newSupports(factory, MusicXmlTags.SUPPORTS_STEM)));

        return encoding;
    }

    private static Supports newSupports(ObjectFactory factory, String element) {
        var supports = factory.createSupports();
        supports.setElement(element);
        // #REQUIRED in the schema; JAXB omits a null silently, so this must always be set.
        supports.setType(YesNo.YES);
        return supports;
    }

    /**
     * Parses {@code headerText.encodingDate()} (an ISO local-date string, e.g. from
     * {@link HeaderText#of}) into the {@code XMLGregorianCalendar}
     * {@code Encoding.getEncodingDateOrEncoderOrSoftware()} requires for {@code <encoding-date>}.
     */
    private static XMLGregorianCalendar toEncodingDate(String isoDate) {
        try {
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(isoDate);
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("No XML datatype factory available", e);
        }
    }

    /**
     * Builds the residual {@code <miscellaneous>} block (composition-date, lyrics-date,
     * composition-place, lyrics-source, unofficial-translation, sub-attribution-font/-size,
     * row-height-adjustment, default-rest-length). Fields are collected into one insertion-ordered
     * name→value map first, then walked into the {@code <miscellaneous-field>} list in that order.
     */
    private static Miscellaneous buildMiscellaneous(
            ObjectFactory factory, Song song, DocumentFontsHolder fonts, HeaderText headerText) {
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

        var subAttributionFont = fonts.getFont(FontKey.SUB_ATTRIBUTION);
        fields.put(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT, subAttributionFont.getPSName());
        fields.put(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE, String.valueOf(subAttributionFont.getSize()));

        var rowHeightAdjustmentSs = song.getRowHeightAdjustmentSs();

        if (rowHeightAdjustmentSs != 0) {
            fields.put(MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT, String.valueOf(rowHeightAdjustmentSs));
        }

        var defaultRestLengthSs = song.getDefaultRestLengthSs();

        if (defaultRestLengthSs != Song.DEFAULT_REST_LENGTH_SS) {
            fields.put(MusicXmlTags.MISC_DEFAULT_REST_LENGTH, String.valueOf(defaultRestLengthSs));
        }

        var miscellaneous = factory.createMiscellaneous();

        for (var field : fields.entrySet()) {
            var miscellaneousField = factory.createMiscellaneousField();
            miscellaneousField.setName(field.getKey());
            miscellaneousField.setValue(field.getValue());
            miscellaneous.getMiscellaneousField().add(miscellaneousField);
        }

        return miscellaneous;
    }

    /**
     * Populates {@code <defaults>} on {@code scorePartwise}: the fixed {@code <scaling>}, the
     * {@code <page-layout>} carrying the model line width as {@code <page-width>}, a zero
     * {@code <staff-layout>}, and the document fonts. {@code <scaling>}, {@code <page-height>}, and
     * {@code <music-font>} are write-forward (fixed) and ignored on read; {@code <page-width>},
     * {@code <word-font>}, and {@code <lyric-font>} round-trip.
     */
    static void buildDefaults(BuildContext context, ScorePartwise scorePartwise) {
        var song = context.song();
        var fonts = context.fonts();
        var factory = context.factory();

        var defaults = factory.createDefaults();

        var scaling = factory.createScaling();
        scaling.setMillimeters(new BigDecimal(String.format(Locale.ROOT, "%.0f", MusicXmlTags.SCALING_MILLIMETERS)));
        scaling.setTenths(new BigDecimal(MusicXmlTags.SCALING_TENTHS));
        defaults.setScaling(scaling);

        var pageLayout = factory.createPageLayout();
        pageLayout.setPageHeight(new BigDecimal(MusicXmlTags.PAGE_HEIGHT_TENTHS));
        pageLayout.setPageWidth(MusicXmlUnits.ssAsTenths(lineWidthSs(song)));
        defaults.setPageLayout(pageLayout);

        var staffLayout = factory.createStaffLayout();
        staffLayout.setStaffDistance(new BigDecimal(MusicXmlUnits.STAFF_DISTANCE_TENTHS));
        defaults.getStaffLayout().add(staffLayout);

        var musicFont = factory.createEmptyFont();
        musicFont.setFontFamily(MusicXmlTags.MUSIC_FONT_FAMILY);
        musicFont.setFontSize(MusicXmlTags.MUSIC_FONT_SIZE);
        defaults.setMusicFont(musicFont);

        var wordFont = factory.createEmptyFont();
        var annotationFont = fonts.getFont(FontKey.ANNOTATION);
        wordFont.setFontFamily(annotationFont.getPSName());
        wordFont.setFontSize(String.valueOf(annotationFont.getSize()));
        defaults.setWordFont(wordFont);

        var lyricFont = factory.createLyricFont();
        var lyricsFont = fonts.getFont(FontKey.LYRICS);
        lyricFont.setFontFamily(lyricsFont.getPSName());
        lyricFont.setFontSize(String.valueOf(lyricsFont.getSize()));
        defaults.getLyricFont().add(lyricFont);

        var lyricLanguage = factory.createLyricLanguage();
        lyricLanguage.setLang(MusicXmlTags.LYRIC_LANGUAGE_DEFAULT);
        defaults.getLyricLanguage().add(lyricLanguage);

        scorePartwise.setDefaults(defaults);
    }

    /**
     * The line width to use for {@code <page-width>}, never zero or negative.
     * <p>
     * A song whose width never got initialized would otherwise persist a zero-width staff, and
     * nothing on the read side rejects one: the document reopens with a staff no content can fit, so
     * every line opens clipped and drawn in red, behind a warning that content is being cut off,
     * instead of showing the song. Substituting the fallback keeps the document readable, and the
     * warning records that a song reached the builder in that state.
     */
    private static double lineWidthSs(Song song) {
        var lineWidthSs = song.getLineWidthSs().value();

        if (lineWidthSs > 0) {
            return lineWidthSs;
        }

        LOG.warn(
            "Song has a non-positive line width ({} ss); using the fallback {} ss instead",
            lineWidthSs, Song.FALLBACK_LINE_WIDTH_SS);

        return Song.FALLBACK_LINE_WIDTH_SS;
    }

    /**
     * Populates the {@code <credit>} elements on {@code scorePartwise}: title, subtitle, each
     * attribution role, and the score-below text blocks, in that order. Every credit is page 1
     * (single-page model), so {@code page} is never set (the schema default is already {@code 1}).
     */
    static void buildCredits(BuildContext context, ScorePartwise scorePartwise, HeaderText headerText) {
        var song = context.song();
        var credits = scorePartwise.getCredit();

        addCredit(context, credits, MusicXmlTags.CREDIT_TITLE, FontKey.TITLE,
            song.getNumberedTitle(), CreditStyle.centered());
        addCredit(context, credits, MusicXmlTags.CREDIT_SUBTITLE, FontKey.SUBTITLE,
            song.getSubtitle(), CreditStyle.plain());

        var attributionRelativeYSs = song.getAttributionElement().getUserYOffsetSs();

        addAttributionCredit(context, credits, MusicXmlTags.CREDIT_COMPOSER,
            song.getComposer(), attributionRelativeYSs);
        addAttributionCredit(context, credits, MusicXmlTags.CREDIT_LYRICIST,
            song.getLyricist(), attributionRelativeYSs);

        if (song.isArrangement()) {
            addAttributionCredit(context, credits, MusicXmlTags.CREDIT_ARRANGER,
                Song.SRI_CHINMOY, attributionRelativeYSs);
        }

        addAttributionCredit(context, credits, MusicXmlTags.CREDIT_COMPOSITION_DATE,
            headerText.compositionDate(), attributionRelativeYSs);

        if (!headerText.lyricsDate().isEmpty()) {
            addAttributionCredit(context, credits, MusicXmlTags.CREDIT_LYRICS_DATE,
                headerText.lyricsDate(), attributionRelativeYSs);
        }

        addAttributionCredit(context, credits, MusicXmlTags.CREDIT_RIGHTS,
            headerText.rights(), attributionRelativeYSs);
        addAttributionCredit(context, credits, MusicXmlTags.CREDIT_PLACE,
            song.getPlace(), attributionRelativeYSs);

        addCredit(context, credits, MusicXmlTags.CREDIT_UNDERLYRICS, FontKey.LYRICS,
            song.getUnderLyrics(), CreditStyle.plain());
        addCredit(context, credits, MusicXmlTags.CREDIT_BANGLA_LYRICS, FontKey.BANGLA,
            song.getBanglaLyrics(), CreditStyle.inLanguage(MusicXmlTags.CREDIT_LANGUAGE_BANGLA));
        addCredit(context, credits, MusicXmlTags.CREDIT_TRANSLATION, FontKey.LYRICS,
            song.getTranslatedLyrics(), CreditStyle.plain());
        addCredit(context, credits, MusicXmlTags.CREDIT_FOOTNOTES, FontKey.FOOTNOTE,
            song.getFootnotes(), CreditStyle.plain());
    }

    /**
     * The optional {@code <credit-words>} style attributes, as one named value.
     *
     * <p>Exactly one is ever set — the title is centered, the Bangla block declares its
     * language, an attribution role carries the shared vertical offset, everything else sets
     * none — so these are named factories rather than three trailing parameters. As positional
     * arguments each call read {@code …, null, null, null)}, and which {@code null} meant which
     * attribute could only be recovered by counting against the signature.
     */
    private record CreditStyle(
        @Nullable LeftCenterRight justify,
        @Nullable Double relativeYSs,
        @Nullable String xmlLang) {

        private static final CreditStyle PLAIN = new CreditStyle(null, null, null);

        /** No optional attribute: the renderer places the credit. */
        static CreditStyle plain() {
            return PLAIN;
        }

        /** {@code justify="center"} — the title credit only. */
        static CreditStyle centered() {
            return new CreditStyle(LeftCenterRight.CENTER, null, null);
        }

        /**
         * {@code relative-y} at {@code offsetSs} staff spaces. Written even when the offset is
         * {@code 0}, because the reader recovers the attribution block's position from it and
         * cannot tell an omitted attribute from a deliberate zero.
         */
        static CreditStyle atOffset(double offsetSs) {
            return new CreditStyle(null, offsetSs, null);
        }

        /** {@code xml:lang} — the Bangla lyric block is the only credit that declares one. */
        static CreditStyle inLanguage(String language) {
            return new CreditStyle(null, null, language);
        }
    }

    /**
     * Adds one attribution-role {@code <credit>} (composer, lyricist, arranger, the two dates,
     * rights, place): an {@code ATTRIBUTION}-font credit carrying the shared attribution
     * {@code relative-y}. Convenience over {@link #addCredit} for the seven attribution calls,
     * which are identical but for their type and text.
     */
    private static void addAttributionCredit(
            BuildContext context, List<Credit> credits, String creditType, String text, double relativeYSs) {
        addCredit(context, credits, creditType, FontKey.ATTRIBUTION, text, CreditStyle.atOffset(relativeYSs));
    }

    /**
     * Adds one {@code <credit>} — {@code <credit-type>} then {@code <credit-words>} — when
     * {@code text} is non-blank; adds nothing otherwise. {@code <credit-words>} always carries
     * font-family/font-size/font-weight/font-style from {@code context.fonts().getFont(fontKey)}
     * (weight/style via {@link Font#isBold()}/{@link Font#isItalic()}), and whichever optional
     * attribute {@code style} names — a {@code relative-y} offset is converted here via
     * {@link MusicXmlUnits#ssAsTenths}. {@code default-x}/{@code default-y} are write-forward,
     * external-renderer-only fields and are never set here.
     */
    private static void addCredit(
            BuildContext context,
            List<Credit> credits,
            String creditType,
            FontKey fontKey,
            String text,
            CreditStyle style) {
        if (text.isBlank()) {
            return;
        }

        var factory = context.factory();
        var credit = factory.createCredit();
        var items = credit.getCreditTypeOrLinkOrBookmark();
        items.add(factory.createCreditCreditType(creditType));

        var font = context.fonts().getFont(fontKey);
        var creditWords = factory.createFormattedText();
        creditWords.setValue(text);
        creditWords.setFontFamily(font.getPSName());
        creditWords.setFontSize(String.valueOf(font.getSize()));
        creditWords.setFontWeight(font.isBold() ? FontWeight.BOLD : FontWeight.NORMAL);
        creditWords.setFontStyle(font.isItalic() ? FontStyle.ITALIC : FontStyle.NORMAL);
        creditWords.setJustify(style.justify());
        creditWords.setLang(style.xmlLang());

        var relativeYSs = style.relativeYSs();

        if (relativeYSs != null) {
            creditWords.setRelativeY(MusicXmlUnits.ssAsTenths(relativeYSs));
        }

        items.add(factory.createCreditCreditWords(creditWords));
        credits.add(credit);
    }
}
