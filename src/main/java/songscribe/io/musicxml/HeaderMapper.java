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

import songscribe.Constants;
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
 * <p><b>Credit routing.</b> The writer emits the same value (composer, dates,
 * place) in <em>both</em> the head (canonical) and a {@code <credit>}
 * (display-only). Taking the credit value instead of the head value corrupts the
 * model as soon as anyone hand-edits a file, so every credit routes into exactly
 * one of three classes:
 *
 * <ul>
 *   <li><b>Canonical</b>, read into the model: the subtitle credit, the four
 *       score-below credits, and the attribution {@code relative-y}.</li>
 *   <li><b>Display-only</b>, ignored and re-derived on write: the title credit and
 *       the composer, lyricist, arranger, date, rights and place credits.</li>
 *   <li><b>Write-forward</b>, ignored and recomputed or constant on write: rights,
 *       software, encoding-date, supports, scaling, music-font, and the
 *       {@code default-x}/{@code default-y} an external renderer needs.</li>
 * </ul>
 *
 * <p>See {@code docs/musicxml-object-model.md} for the full routing table.
 */
final class HeaderMapper {

    private static final Logger LOG = LoggerFactory.getLogger(HeaderMapper.class);

    private final Song song;

    // The document fonts recovered from <defaults> (<word-font> → ANNOTATION,
    // <lyric-font> → LYRICS), the title/subtitle credits, and the <miscellaneous>
    // sub-attribution-font/-size fields (→ SUB_ATTRIBUTION). The caller owns the
    // instance and hands it to SongLoadResult.Success; this class only writes into it.
    private final DocumentFonts documentFonts;

    // -------------------------------------------------------------------------
    // Head-metadata scratch — accumulated from <movement-*>, <identification> and
    // the subtitle credit, then assembled into a single SongMetadata record at the
    // end of the mapping (see applyHeadMetadata). Defaults mirror the SongMetadata
    // defaults so an absent element leaves its field at the value a blank document
    // would carry.
    // -------------------------------------------------------------------------

    private String headTitle = "";
    private String headNumber = "";
    private String headPlace = "";

    // Composer/lyricist default to empty; SongMetadata coerces empty to
    // SRI_CHINMOY, matching the value a document with no <creator> would carry.
    private String headComposer = "";
    private String headLyricist = "";

    // Set true when a <creator type="arranger"> is seen; the flag is the only
    // information the arranger creator carries (its text is always SRI_CHINMOY).
    private boolean headArrangement = false;

    private boolean headUnofficialTranslation = false;
    private Song.LyricsSource headLyricsSource = Song.LyricsSource.LYRICIST;

    // A date the document does not state. SongMetadata's compact constructor coerces
    // these three values to "no date"; they are held as one DateParts rather than three
    // scalars each so a date stays one value from parse to metadata.
    private static final DateUtils.DateParts NO_DATE = new DateUtils.DateParts("", 0, 0);

    // Composition date (composition-date misc-field).
    private DateUtils.DateParts headDate = NO_DATE;

    // Lyrics/words date (lyrics-date misc-field).
    private DateUtils.DateParts headWordsDate = NO_DATE;

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

    // -------------------------------------------------------------------------
    // Package API
    // -------------------------------------------------------------------------

    /**
     * Applies {@code score}'s header to {@code song} and {@code documentFonts}.
     *
     * @throws SAXException if a head value is malformed (an unknown
     *                      {@code lyrics-source} token, an unparseable number)
     */
    static void map(ScorePartwise score, Song song, DocumentFonts documentFonts) throws SAXException {
        new HeaderMapper(song, documentFonts).mapScore(score);
    }

    /**
     * Provenance gate: only SongScribe-authored documents are accepted. A missing,
     * blank, or foreign {@code <software>} tag is rejected here.
     * {@code SongFileLoader.load} maps the exception to
     * {@code SongLoadResult.WrongSoftware}.
     *
     * <p>{@code SongMapper.map} calls this before any mapping, so a foreign document
     * reports its provenance even when its content would also have tripped a mapper,
     * and no {@code Song} is built out of an input this program does not support.
     */
    static void checkProvenance(ScorePartwise score) throws MusicXmlReader.ForeignSoftwareException {
        var software = ProxyMusicAccess.software(score);

        if (software == null || software.isBlank() || !software.startsWith(Constants.PACKAGE_NAME)) {
            throw new MusicXmlReader.ForeignSoftwareException(software);
        }
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

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

    /**
     * Reads the canonical head values out of {@code <identification>}: the
     * {@code <creator>}s and the {@code <miscellaneous-field>}s. {@code <rights>},
     * {@code <encoding-date>} and {@code <supports>} are write-forward and are not
     * read; {@code <software>} is read only by {@link #checkProvenance}.
     */
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
     * Recovers the canonical values from {@code <defaults>}: the line width from
     * {@code <page-width>} and the annotation/lyric document fonts.
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

    /**
     * Routes one fully-read {@code <credit>} by its {@code <credit-type>}. See the
     * class Javadoc for the three routing classes.
     */
    private void mapCredit(Credit credit) throws SAXException {
        var parts = credit.getCreditTypeOrLinkOrBookmark();
        var creditTypes = ProxyMusicAccess.jaxbValues(parts, MusicXmlTags.CREDIT_TYPE, String.class);
        var creditWordsList = ProxyMusicAccess.jaxbValues(parts, MusicXmlTags.CREDIT_WORDS, FormattedText.class);

        if (creditTypes.isEmpty()) {
            // No <credit-type> to route on — a display-only credit from another
            // program; nothing to read.
            return;
        }

        // The last of each wins, matching the SAX reader's overwrite-per-child
        // accumulation. The writer emits exactly one of each.
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

            // Display-only — unknown credit-types are skipped.
            default -> {
                // no read state
            }
        }
    }

    @Nullable
    private static FormattedText lastCreditWords(List<FormattedText> creditWordsList) {
        return creditWordsList.isEmpty() ? null : creditWordsList.getLast();
    }

    /**
     * Recovers the attribution user Y offset from the current attribution credit's
     * {@code relative-y}, but only from the first attribution credit that carries
     * it — the writer emits the same {@code relative-y} on every attribution
     * credit, so reading it once is sufficient.
     */
    private void readAttributionOffsetOnce(@Nullable FormattedText creditWords) {
        if (attributionOffsetRead || creditWords == null) {
            return;
        }

        var offsetTenths = ProxyMusicAccess.decimal(creditWords.getRelativeY());

        if (offsetTenths == null) {
            return;
        }

        song.getAttributionElement().setUserYOffsetSs(MusicXmlUnits.tenthsToSs(offsetTenths));
        attributionOffsetRead = true;
    }

    /**
     * Routes a {@code <creator>}'s text by its {@code type} attribute:
     * composer/lyricist into the head scratch, arranger into the arrangement flag
     * (its text is always {@code SRI_CHINMOY}, so only the presence matters).
     * Unknown or missing types are ignored.
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

    /**
     * Routes a {@code <miscellaneous-field>}'s text by its {@code name} attribute.
     * The head fields go into the metadata scratch; the two dates go through the
     * shared {@link DateUtils#parseIsoDate} inverse of the writer's
     * {@code toIsoDate} (a malformed date parses to {@code null} and is treated as
     * absent, keeping the scratch date fields at their empty defaults). The
     * defaults residuals — {@code row-height-adjustment}, the default rest length,
     * and the sub-attribution font — are applied straight onto the song /
     * {@link #documentFonts}. Unknown misc-fields are ignored.
     */
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

    /**
     * Recovers a document-font role from a {@code <credit-words>}'s
     * {@code font-family}/{@code font-size}. See {@link #applyFont}.
     */
    private void applyCreditFont(FontKey key, @Nullable FormattedText creditWords) throws SAXException {
        if (creditWords != null) {
            applyFont(key, creditWords.getFontFamily(), creditWords.getFontSize());
        }
    }

    /**
     * Stores a document-font {@code key} from a raw {@code font-family}/
     * {@code font-size} pair into {@link #documentFonts}. Weight/style are
     * write-forward (not emitted, not read). Left at its default when either value
     * is absent — defensive only, since the writer always emits both.
     */
    private void applyFont(FontKey key, @Nullable String family, @Nullable String sizeRaw) throws SAXException {
        if (family == null || sizeRaw == null) {
            return;
        }

        // font-size is a schema decimal; the writer emits an integer point size.
        var size = (int) Math.round(MusicXmlUnits.parseDoubleOrThrow(MusicXmlTags.ATTR_FONT_SIZE, sizeRaw));
        documentFonts.setFont(key, family, size);
    }

    /**
     * Sets the {@link FontKey#SUB_ATTRIBUTION} role once both its family and size
     * misc-fields have been read — they arrive as two separate
     * {@code <miscellaneous-field>}s, so neither half alone can resolve the font.
     */
    private void applySubAttributionFont() {
        if (subAttributionFontFamily != null && subAttributionFontSize != null) {
            documentFonts.setFont(FontKey.SUB_ATTRIBUTION, subAttributionFontFamily, subAttributionFontSize);
        }
    }

    /**
     * {@code text} as a date, or {@code fallback} when it is not one. A malformed date is
     * treated as absent rather than rejected: the writer only ever emits the well-formed
     * form, so a bad one means a hand-edit, and dropping just the date keeps the rest of
     * the document readable.
     */
    private static DateUtils.DateParts parsedDateOr(String text, DateUtils.DateParts fallback) {
        var parts = DateUtils.parseIsoDate(text);
        return parts == null ? fallback : parts;
    }

    /**
     * Assembles the accumulated head scratch and the credit-derived subtitle into
     * the song's {@link SongMetadata} in one all-args construction, mirroring
     * {@code SongIO.DocumentReader -> Song.loadFrom}. Building the record once
     * dissolves the identification-before-credit ordering hazard (the subtitle
     * credit follows the head) and needs no {@code setSubtitle} mutator.
     */
    private void applyHeadMetadata() {
        // The SongMetadata compact constructor normalizes/coerces every field, so
        // the scratch values need no pre-normalization here.
        song.setMetadata(new SongMetadata(
            headTitle, headNumber, headPlace,
            headDate.year(), headDate.month(), headDate.day(),
            headComposer, headLyricist, headLyricsSource,
            headArrangement, headUnofficialTranslation,
            subtitle,
            headWordsDate.year(), headWordsDate.month(), headWordsDate.day()
        ));
    }

    /**
     * Resolves a {@code lyrics-source} token to a {@link Song.LyricsSource},
     * throwing on an unknown token. Fails hard rather than defaulting, matching the
     * reader's {@code parseIntOrThrow}/{@code parseDoubleOrThrow} convention — the
     * writer only ever emits an enum constant name, so an unknown token means a
     * corrupt document.
     *
     * @param token the {@code lyrics-source} miscellaneous-field value
     * @return the source it names; never null
     * @throws SAXException if {@code token} names no {@link Song.LyricsSource} constant
     */
    private static Song.LyricsSource lyricsSourceOrThrow(String token) throws SAXException {
        try {
            return Song.LyricsSource.valueOf(token);
        } catch (IllegalArgumentException e) {
            // The IllegalArgumentException is dropped rather than chained, as it is in
            // DocumentValidation's own parseXxxOrThrow methods: it says only that the constant
            // does not exist, which is what the message below already reports, with the field.
            throw DocumentValidation.corrupt(
                LOG, "Corrupt document: malformed <{}> value: '{}'", MusicXmlTags.MISC_LYRICS_SOURCE, token);
        }
    }

    /** An optional text value, with an absent one read as the empty string. */
    private static String orEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
