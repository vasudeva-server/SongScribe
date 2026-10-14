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

import java.util.List;

import org.audiveris.proxymusic.Credit;
import org.audiveris.proxymusic.FormattedText;
import org.audiveris.proxymusic.ScorePartwise;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import songscribe.dom.PartialDate;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.dom.Ss;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.io.DocumentValidation;
import songscribe.util.DateUtils;

/**
 * Maps the score-header of an unmarshalled {@code <score-partwise>} — the
 * {@code <movement-*>} elements, {@code <identification>}, {@code <defaults>} and
 * the {@code <credit>} blocks — onto a {@link Song} and its {@link DocumentFonts}.
 * The inverse of {@link HeaderBuilder}.
 *
 * <p><b>Credit routing.</b> The writer emits composer, dates and place in both the head
 * (canonical) and a {@code <credit>} (display-only). The head value wins, so a hand-edited file
 * cannot corrupt the model through a credit.
 */
final class HeaderMapper {

    private static final Logger LOG = LoggerFactory.getLogger(HeaderMapper.class);

    private final Song song;

    // The document fonts recovered from <defaults> (<word-font> → ANNOTATION,
    // <lyric-font> → LYRICS), the title/subtitle credits, and the <miscellaneous>
    // sub-attribution-font/-size fields (→ SUB_ATTRIBUTION). The caller owns the
    // instance and hands it to SongLoadResult.Success; this class only writes into it.
    private final DocumentFonts documentFonts;

    // The head-metadata fields below are accumulated from <movement-*>, <identification> and the
    // subtitle credit, then assembled into one SongMetadata at the end of the mapping (see
    // applyHeadMetadata). Their defaults mirror the SongMetadata defaults so an absent element
    // leaves a field at the value a blank document would carry.

    private String headTitle = "";
    private String headNumber = "";
    private String headPlace = "";

    // Composer/lyricist default to empty; SongMetadata turns an empty composer into
    // SRI_CHINMOY and an empty lyricist into the composer, matching the value a document
    // with no <creator> would carry.
    private String headComposer = "";
    private String headLyricist = "";

    // The arranger <creator>'s text is always SRI_CHINMOY, so its presence is the only information.
    private boolean headArrangement = false;

    private boolean headUnofficialTranslation = false;
    private Song.LyricsSource headLyricsSource = Song.LyricsSource.LYRICIST;

    // Composition date (composition-date misc-field).
    private PartialDate headDate = PartialDate.EmptyDate.INSTANCE;

    // Lyrics/words date (lyrics-date misc-field).
    private PartialDate headWordsDate = PartialDate.EmptyDate.INSTANCE;

    // The subtitle recovered from the subtitle credit; empty when the document
    // carries no subtitle credit (a blank subtitle is never written, so absent and
    // empty are indistinguishable). There is no setSubtitle mutator, so it is held
    // here and folded into SongMetadata with the rest of the head.
    private String subtitle = "";

    // True once the attribution Y offset has been recovered. The writer emits the
    // same relative-y on every attribution credit, so only the first is read.
    private boolean attributionOffsetRead = false;

    // The sub-attribution font arrives as two separate <miscellaneous-field>s
    // (family, then size); both must be present before the role can be resolved.
    @Nullable
    private String subAttributionFontFamily = null;

    @Nullable
    private Integer subAttributionFontSize = null;

    private HeaderMapper(Song song, DocumentFonts documentFonts) {
        this.song = song;
        this.documentFonts = documentFonts;
    }

    static void map(ScorePartwise score, Song song, DocumentFonts documentFonts) throws SAXException {
        new HeaderMapper(song, documentFonts).mapScore(score);
    }

    /**
     * Called before any mapping, so a foreign document reports its provenance even when its
     * content would also trip a mapper.
     */
    static void checkProvenance(ScorePartwise score)
        throws MusicXmlReader.ForeignSoftwareException, MusicXmlReader.UnsupportedVersionException {
        SoftwareProvenance.check(ProxyMusicAccess.software(score));
    }

    private void mapScore(ScorePartwise score) throws SAXException {
        mapMovement(score);
        mapIdentification(score);
        mapDefaults(score);

        for (var credit : score.getCredit()) {
            mapCredit(credit);
        }

        applyHeadMetadata();
    }

    private void mapMovement(ScorePartwise score) {
        headTitle = orEmpty(score.getMovementTitle());
        headNumber = orEmpty(score.getMovementNumber());
    }

    private void mapIdentification(ScorePartwise score) throws SAXException {
        var identification = score.getIdentification();

        if (identification == null) {
            return;
        }

        for (var creator : identification.getCreator()) {
            applyCreator(creator.getType(), orEmpty(creator.getValue()));
        }

        var miscellaneous = identification.getMiscellaneous();

        if (miscellaneous != null) {
            for (var field : miscellaneous.getMiscellaneousField()) {
                applyMiscField(field.getName(), orEmpty(field.getValue()));
            }
        }
    }

    /**
     * {@code <scaling>}, {@code <page-height>}, {@code <staff-layout>},
     * {@code <music-font>} and {@code <lyric-language>} are write-forward.
     */
    private void mapDefaults(ScorePartwise score) throws SAXException {
        var defaults = score.getDefaults();

        if (defaults == null) {
            return;
        }

        var pageLayout = defaults.getPageLayout();

        if (pageLayout != null) {
            var pageWidth = ProxyMusicAccess.decimal(pageLayout.getPageWidth());

            if (pageWidth != null) {
                // The conversion must not round: the width is a fractional model
                // value that the writer emits exactly, so rounding here would shift
                // it on every open.
                song.setLineWidthSs(new Ss(MusicXmlUnits.tenthsToExactSs(pageWidth)));
            }
        }

        var wordFont = defaults.getWordFont();

        if (wordFont != null) {
            applyFont(FontKey.ANNOTATION, wordFont.getFontFamily(), wordFont.getFontSize());
        }

        var lyricFonts = defaults.getLyricFont();

        if (!lyricFonts.isEmpty()) {
            var lyricFont = lyricFonts.getFirst();
            applyFont(FontKey.LYRICS, lyricFont.getFontFamily(), lyricFont.getFontSize());
        }
    }

    private void mapCredit(Credit credit) throws SAXException {
        var parts = credit.getCreditTypeOrLinkOrBookmark();
        var creditTypes = ProxyMusicAccess.jaxbValues(parts, MusicXmlTags.CREDIT_TYPE, String.class);
        var creditWordsList = ProxyMusicAccess.jaxbValues(parts, MusicXmlTags.CREDIT_WORDS, FormattedText.class);

        if (creditTypes.isEmpty()) {
            // No <credit-type> to route on, so nothing to read.
            return;
        }

        // The writer emits exactly one of each.
        var creditType = creditTypes.getLast();
        var creditWords = lastCreditWords(creditWordsList);
        var text = creditWords == null ? "" : orEmpty(creditWords.getValue());

        switch (creditType) {
            // Canonical — the subtitle has no <movement-*> equivalent, so the credit
            // is its source of truth. Folded into SongMetadata with the rest of the
            // head (there is no setSubtitle mutator).
            case MusicXmlTags.CREDIT_SUBTITLE -> {
                subtitle = text;
                applyCreditFont(FontKey.SUBTITLE, creditWords);
            }

            // Canonical — the four score-below text blocks are standalone Song
            // fields with direct setters.
            case MusicXmlTags.CREDIT_UNDERLYRICS -> song.setUnderLyrics(text);
            case MusicXmlTags.CREDIT_BANGLA_LYRICS -> song.setBanglaLyrics(text);
            case MusicXmlTags.CREDIT_TRANSLATION -> song.setTranslatedLyrics(text);
            case MusicXmlTags.CREDIT_FOOTNOTES -> song.setFootnotes(text);

            // Display-only attribution roles — the text is re-derived from the head
            // <creator>/<rights>/misc-fields, so it is ignored; only the shared
            // relative-y (the attribution user Y offset) is recovered, once.
            case MusicXmlTags.CREDIT_COMPOSER,
                 MusicXmlTags.CREDIT_LYRICIST,
                 MusicXmlTags.CREDIT_ARRANGER,
                 MusicXmlTags.CREDIT_COMPOSITION_DATE,
                 MusicXmlTags.CREDIT_LYRICS_DATE,
                 MusicXmlTags.CREDIT_RIGHTS,
                 MusicXmlTags.CREDIT_PLACE -> readAttributionOffsetOnce(creditWords);

            // Display-only text, canonical font — the title text is re-derived from
            // <movement-*>, but the credit is still the font's source of truth.
            case MusicXmlTags.CREDIT_TITLE -> applyCreditFont(FontKey.TITLE, creditWords);

            default -> {
            }
        }
    }

    @Nullable
    private static FormattedText lastCreditWords(List<FormattedText> creditWordsList) {
        return creditWordsList.isEmpty() ? null : creditWordsList.getLast();
    }

    /**
     * The writer emits the same {@code relative-y} on every attribution credit.
     */
    private void readAttributionOffsetOnce(@Nullable FormattedText creditWords) {
        if (attributionOffsetRead || creditWords == null) {
            return;
        }

        var offsetTenths = ProxyMusicAccess.decimal(creditWords.getRelativeY());

        if (offsetTenths == null) {
            return;
        }

        song.getAttributionElement().setUserYOffsetSs(MusicXmlUnits.tenthsToExactSs(offsetTenths));
        attributionOffsetRead = true;
    }

    /**
     * The arranger's text is always {@code SRI_CHINMOY}, so only its presence matters.
     */
    private void applyCreator(@Nullable String creatorType, String text) {
        if (MusicXmlTags.CREATOR_COMPOSER.equals(creatorType)) {
            headComposer = text;
        } else if (MusicXmlTags.CREATOR_LYRICIST.equals(creatorType)) {
            headLyricist = text;
        } else if (MusicXmlTags.CREATOR_ARRANGER.equals(creatorType)) {
            headArrangement = true;
        }
    }

    private void applyMiscField(@Nullable String name, String text) throws SAXException {
        if (MusicXmlTags.MISC_COMPOSITION_DATE.equals(name)) {
            headDate = parsedDateOr(text, headDate);
        } else if (MusicXmlTags.MISC_LYRICS_DATE.equals(name)) {
            headWordsDate = parsedDateOr(text, headWordsDate);
        } else if (MusicXmlTags.MISC_COMPOSITION_PLACE.equals(name)) {
            headPlace = text;
        } else if (MusicXmlTags.MISC_LYRICS_SOURCE.equals(name)) {
            headLyricsSource = lyricsSourceOrThrow(text);
        } else if (MusicXmlTags.MISC_UNOFFICIAL_TRANSLATION.equals(name)) {
            headUnofficialTranslation = Boolean.parseBoolean(text);
        } else if (MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT.equals(name)) {
            // A staff-space delta stored verbatim (the writer omits it when 0).
            song.setRowHeightAdjustmentSs(
                MusicXmlUnits.parseDoubleOrThrow(MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT, text)
            );
        } else if (MusicXmlTags.MISC_DEFAULT_REST_LENGTH.equals(name)) {
            // A staff-space line rest stored verbatim (the writer omits it at the
            // default); the setter clamps it, and an absent field leaves the song's
            // default in place.
            song.setDefaultRestLengthSs(
                MusicXmlUnits.parseDoubleOrThrow(MusicXmlTags.MISC_DEFAULT_REST_LENGTH, text)
            );
        } else if (MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT.equals(name)) {
            subAttributionFontFamily = text;
            applySubAttributionFont();
        } else if (MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE.equals(name)) {
            subAttributionFontSize = MusicXmlUnits.parseIntOrThrow(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE, text);
            applySubAttributionFont();
        }
    }

    private void applyCreditFont(FontKey key, @Nullable FormattedText creditWords) throws SAXException {
        if (creditWords != null) {
            applyFont(key, creditWords.getFontFamily(), creditWords.getFontSize());
        }
    }

    /**
     * Weight/style are write-forward (not emitted, not read).
     */
    private void applyFont(FontKey key, @Nullable String family, @Nullable String sizeRaw) throws SAXException {
        if (family == null || sizeRaw == null) {
            return;
        }

        // font-size is a schema decimal; the writer emits an integer point size.
        var size = (int) Math.round(MusicXmlUnits.parseDoubleOrThrow(MusicXmlTags.ATTR_FONT_SIZE, sizeRaw));
        documentFonts.setFont(key, family, size);
    }

    private void applySubAttributionFont() {
        if (subAttributionFontFamily != null && subAttributionFontSize != null) {
            documentFonts.setFont(FontKey.SUB_ATTRIBUTION, subAttributionFontFamily, subAttributionFontSize);
        }
    }

    /**
     * A malformed date is treated as absent, not rejected, so the rest of the document stays
     * readable.
     */
    private static PartialDate parsedDateOr(String text, PartialDate fallback) {
        var date = DateUtils.parseIsoDate(text);
        return date == null ? fallback : date;
    }

    /**
     * Builds the record once, so the subtitle credit's following the head cannot matter.
     */
    private void applyHeadMetadata() {
        song.setMetadata(new SongMetadata(
            headTitle, headNumber, headPlace, headDate,
            headComposer, headLyricist, headLyricsSource,
            headArrangement, headUnofficialTranslation,
            subtitle, headWordsDate
        ));
    }

    /**
     * Fails rather than defaulting: the writer only emits an enum constant name, so an unknown
     * token means a corrupt document.
     */
    private static Song.LyricsSource lyricsSourceOrThrow(String token) throws SAXException {
        try {
            return Song.LyricsSource.valueOf(token);
        } catch (IllegalArgumentException e) {
            // The IllegalArgumentException is not chained, as in DocumentValidation's own
            // parseXxxOrThrow methods: it says only that the constant does not exist, which the
            // message below already reports, with the field.
            throw DocumentValidation.corrupt(
                LOG, "Corrupt document: malformed <{}> value: '{}'", MusicXmlTags.MISC_LYRICS_SOURCE, token);
        }
    }

    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
