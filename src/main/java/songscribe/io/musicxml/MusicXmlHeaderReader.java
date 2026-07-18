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

import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import songscribe.Constants;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.io.musicxml.MusicXmlReader.Where;
import songscribe.util.DateUtils;

/**
 * Parses the score-header / document-metadata subtree of a MusicXML document on
 * behalf of {@link MusicXmlReader}. Mirrors the writer's {@link MusicXmlHeaderWriter}.
 * <p>
 * This is not a {@link org.xml.sax.helpers.DefaultHandler}: {@code MusicXmlReader}
 * owns the two SAX dispatch switches and delegates each header-owned {@link Where}
 * state's start/end case here. This class owns the header scratch fields (movement,
 * identification, credit, and defaults accumulation) and reads the shared parse
 * spine — the current {@code value} text and the {@link Song} — through the
 * reader-as-context accessors on {@code MusicXmlReader}. The {@link Where}
 * transition graph is documented on {@link MusicXmlReader}.
 * <p>
 * The states owned here are: {@code MOVEMENT_TITLE}, {@code MOVEMENT_NUMBER},
 * {@code IDENTIFICATION}, {@code CREATOR}, {@code RIGHTS}, {@code ENCODING},
 * {@code SOFTWARE}, {@code ENCODING_DATE}, {@code MISCELLANEOUS},
 * {@code MISCELLANEOUS_FIELD}, {@code DEFAULTS}, {@code DEFAULTS_SCALING},
 * {@code DEFAULTS_PAGE_LAYOUT}, {@code DEFAULTS_PAGE_WIDTH},
 * {@code DEFAULTS_STAFF_LAYOUT}, {@code CREDIT}, {@code CREDIT_TYPE}, and
 * {@code CREDIT_WORDS}.
 */
final class MusicXmlHeaderReader {

    private final MusicXmlReader reader;

    // The document fonts recovered from <defaults> (<word-font> → ANNOTATION,
    // <lyric-font> → LYRICS) and the <miscellaneous> sub-attribution-font/-size
    // fields (→ SUB_ATTRIBUTION). This is the SAME mutable instance the reader
    // holds for its SongLoadResult.Success; this class mutates it via setFont
    // while the reader owns the reference (see MusicXmlReader.read).
    private final DocumentFonts documentFonts;

    // The document's <software> provenance tag, captured at </software>. A
    // present-but-foreign value is rejected there; a missing or blank value is
    // rejected at checkProvenance (invoked from the reader's endDocument). Null
    // until the tag is seen.
    @Nullable
    private String software = null;

    // -------------------------------------------------------------------------
    // Credit-reconstruction state — accumulated per <credit> subtree and routed
    // at </credit> (see dispatchCredit). The subtitle is the one canonical head
    // field carried by a credit (there is no <movement-*> equivalent); it is held
    // here and folded into SongMetadata at the terminal </score-partwise>.
    // -------------------------------------------------------------------------

    // The <credit-type> text of the credit currently being read.
    private String creditType = "";

    // The <credit-words> text of the credit currently being read.
    private String creditWords = "";

    // The raw relative-y attribute of the current <credit-words>, or null when
    // absent. Only attribution credits carry it (see MusicXmlWriter.writeCredit).
    @Nullable
    private String creditWordsRelativeYRaw = null;

    // The raw font-family/font-size attributes of the current <credit-words>.
    // Every credit carries them (see MusicXmlHeaderWriter.writeCredit), but only
    // the title/subtitle credits are canonical for their FontKey — the rest are
    // recovered from <defaults>/<miscellaneous> instead (see dispatchCredit).
    @Nullable
    private String creditWordsFontFamily = null;
    @Nullable
    private String creditWordsFontSizeRaw = null;

    // The subtitle recovered from the subtitle credit; empty when the document
    // carries no subtitle credit (a blank subtitle is never written, so absent
    // and empty are indistinguishable). Folded into SongMetadata at
    // </score-partwise>.
    private String subtitle = "";

    // True once the attribution Y offset has been recovered. The writer emits the
    // same relative-y on every attribution credit, so only the first is read.
    private boolean attributionOffsetRead = false;

    // -------------------------------------------------------------------------
    // Head-metadata scratch — accumulated from <movement-*> and <identification>
    // (creators + <miscellaneous> fields) and assembled, together with the
    // credit-derived subtitle above, into a single SongMetadata record at the
    // terminal </score-partwise> (see applyHeadMetadata). Defaults mirror the
    // SongMetadata defaults so an absent element leaves its field at the value a
    // blank document would carry. Write-forward head elements (<rights>,
    // <software>, <encoding-date>, <supports>) are consumed but not read.
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

    // Composition date (composition-date misc-field).
    private String headYear = "";
    private int headMonth = 0;
    private int headDay = 0;

    // Lyrics/words date (lyrics-date misc-field).
    private String headWordsYear = "";
    private int headWordsMonth = 0;
    private int headWordsDay = 0;

    // The type attribute of the <creator> currently being read, routed at
    // </creator>; null when the element omits it (then ignored).
    @Nullable
    private String creatorType = null;

    // The name attribute of the <miscellaneous-field> currently being read,
    // routed at </miscellaneous-field>; null when the element omits it.
    @Nullable
    private String miscFieldName = null;

    // -------------------------------------------------------------------------
    // Defaults-reconstruction state — the sub-attribution font arrives as two
    // separate <miscellaneous-field>s (family, then size); both must be present
    // before the role can be resolved.
    // -------------------------------------------------------------------------

    @Nullable
    private String subAttributionFontFamily = null;
    @Nullable
    private Integer subAttributionFontSize = null;

    MusicXmlHeaderReader(MusicXmlReader reader, DocumentFonts documentFonts) {
        this.reader = reader;
        this.documentFonts = documentFonts;
    }

    // -------------------------------------------------------------------------
    // startElement header cases (delegated from MusicXmlReader.startElement)
    // -------------------------------------------------------------------------

    /**
     * Resets the per-credit accumulators and enters the {@code CREDIT} subtree.
     * Invoked from the {@code SCORE_PARTWISE} hub on {@code <credit>}; the
     * subtree's {@code <credit-type>}/{@code <credit-words>} fill the accumulators
     * and {@code </credit>} routes on them.
     */
    void beginCredit() {
        creditType = "";
        creditWords = "";
        creditWordsRelativeYRaw = null;
        creditWordsFontFamily = null;
        creditWordsFontSizeRaw = null;
        reader.setWhere(Where.CREDIT);
    }

    void handleStartIdentification(String qName, Attributes attributes) {
        if (qName.equals(MusicXmlTags.CREATOR)) {
            // Capture the routing type here; the name text arrives at </creator>.
            creatorType = attributes.getValue(MusicXmlTags.ATTR_TYPE);
            reader.setWhere(Where.CREATOR);
        } else if (qName.equals(MusicXmlTags.RIGHTS)) {
            reader.setWhere(Where.RIGHTS);
        } else if (qName.equals(MusicXmlTags.ENCODING)) {
            reader.setWhere(Where.ENCODING);
        } else if (qName.equals(MusicXmlTags.MISCELLANEOUS)) {
            reader.setWhere(Where.MISCELLANEOUS);
        }
    }

    void handleStartEncoding(String qName) {
        // <software>/<encoding-date> are write-forward: their subtrees are
        // consumed so their text does not leak, but nothing is read back.
        // <supports> is an empty element with no state, skipped in place.
        reader.startTransition(qName, MusicXmlTags.SOFTWARE, Where.SOFTWARE);
        reader.startTransition(qName, MusicXmlTags.ENCODING_DATE, Where.ENCODING_DATE);
    }

    void handleStartMiscellaneous(String qName, Attributes attributes) {
        if (qName.equals(MusicXmlTags.MISCELLANEOUS_FIELD)) {
            // Capture the routing name here; the value text arrives at
            // </miscellaneous-field>.
            miscFieldName = attributes.getValue(MusicXmlTags.ATTR_NAME);
            reader.setWhere(Where.MISCELLANEOUS_FIELD);
        }
    }

    void handleStartDefaults(String qName, Attributes attributes) throws SAXException {
        // <scaling>/<staff-layout> and their leaves are write-forward and
        // consumed by their own states; <music-font>/<lyric-language> are
        // empty write-forward elements skipped in place. Only <page-layout>
        // (for <page-width>) and the <word-font>/<lyric-font> roles read.
        if (qName.equals(MusicXmlTags.SCALING)) {
            reader.setWhere(Where.DEFAULTS_SCALING);
        } else if (qName.equals(MusicXmlTags.PAGE_LAYOUT)) {
            reader.setWhere(Where.DEFAULTS_PAGE_LAYOUT);
        } else if (qName.equals(MusicXmlTags.STAFF_LAYOUT)) {
            reader.setWhere(Where.DEFAULTS_STAFF_LAYOUT);
        } else if (qName.equals(MusicXmlTags.WORD_FONT)) {
            setDocumentFont(FontKey.ANNOTATION, attributes);
        } else if (qName.equals(MusicXmlTags.LYRIC_FONT)) {
            setDocumentFont(FontKey.LYRICS, attributes);
        }
    }

    void handleStartDefaultsPageLayout(String qName) {
        // <page-height> is write-forward, consumed by staying in place;
        // <page-width> carries the model line width.
        reader.startTransition(qName, MusicXmlTags.PAGE_WIDTH, Where.DEFAULTS_PAGE_WIDTH);
    }

    void handleStartCredit(String qName, Attributes attributes) {
        if (qName.equals(MusicXmlTags.CREDIT_TYPE)) {
            reader.setWhere(Where.CREDIT_TYPE);
        } else if (qName.equals(MusicXmlTags.CREDIT_WORDS)) {
            // relative-y and the font attributes are captured here; the text
            // arrives at </credit-words>. Only the title/subtitle credits
            // apply the recovered font (see dispatchCredit) — every other
            // role's font is write-forward, recovered from <defaults>/
            // <miscellaneous> instead.
            creditWordsRelativeYRaw = attributes.getValue(MusicXmlTags.ATTR_RELATIVE_Y);
            creditWordsFontFamily = attributes.getValue(MusicXmlTags.ATTR_FONT_FAMILY);
            creditWordsFontSizeRaw = attributes.getValue(MusicXmlTags.ATTR_FONT_SIZE);
            reader.setWhere(Where.CREDIT_WORDS);
        }
    }

    // -------------------------------------------------------------------------
    // endElement header cases (delegated from MusicXmlReader.endElement)
    // -------------------------------------------------------------------------

    void handleEndMovementTitle(String qName) {
        if (qName.equals(MusicXmlTags.MOVEMENT_TITLE)) {
            headTitle = reader.valueString();
            reader.setWhere(Where.SCORE_PARTWISE);
        }
    }

    void handleEndMovementNumber(String qName) {
        if (qName.equals(MusicXmlTags.MOVEMENT_NUMBER)) {
            headNumber = reader.valueString();
            reader.setWhere(Where.SCORE_PARTWISE);
        }
    }

    void handleEndCreator(String qName) {
        if (qName.equals(MusicXmlTags.CREATOR)) {
            applyCreator(reader.valueString());
            reader.setWhere(Where.IDENTIFICATION);
        }
    }

    void handleEndSoftware(String qName) throws SAXException {
        // Provenance tag. <software> arrives in the header before any <part>, so
        // reject a present-but-foreign document here rather than parsing the
        // whole score body only to discard it. A missing or blank tag can only
        // be detected once the document ends, so that case is deferred to
        // checkProvenance (invoked from the reader's endDocument).
        if (qName.equals(MusicXmlTags.SOFTWARE)) {
            var text = reader.valueString();
            software = text;

            if (!text.isBlank() && !text.startsWith(Constants.PACKAGE_NAME)) {
                throw new MusicXmlReader.ForeignSoftwareException(text);
            }

            reader.setWhere(Where.ENCODING);
        }
    }

    void handleEndMiscellaneousField(String qName) throws SAXException {
        if (qName.equals(MusicXmlTags.MISCELLANEOUS_FIELD)) {
            applyMiscField(miscFieldName, reader.valueString());
            reader.setWhere(Where.MISCELLANEOUS);
        }
    }

    void handleEndDefaultsPageWidth(String qName) throws SAXException {
        if (qName.equals(MusicXmlTags.PAGE_WIDTH)) {
            // page-width (tenths) → line width (staff spaces). Write-forward
            // <page-height>/<scaling> are ignored, so the recovered width is
            // the sole canonical page-layout value.
            var song = reader.songOrNull();

            if (song != null) {
                song.setLineWidthSs(
                    MusicXmlUnits.tenthsToSs(MusicXmlUnits.parseDoubleOrThrow(MusicXmlTags.PAGE_WIDTH, reader.valueString()))
                );
            }

            reader.setWhere(Where.DEFAULTS_PAGE_LAYOUT);
        }
    }

    void handleEndCreditType(String qName) {
        if (qName.equals(MusicXmlTags.CREDIT_TYPE)) {
            creditType = reader.valueString();
            reader.setWhere(Where.CREDIT);
        }
    }

    void handleEndCreditWords(String qName) {
        if (qName.equals(MusicXmlTags.CREDIT_WORDS)) {
            // P-1: read the accumulated text only here, at the end element, so a
            // long credit split across multiple SAX characters() chunks
            // (footnotes/underlyrics) is not truncated.
            creditWords = reader.valueString();
            reader.setWhere(Where.CREDIT);
        }
    }

    void handleEndCredit(String qName) throws SAXException {
        if (qName.equals(MusicXmlTags.CREDIT)) {
            dispatchCredit();
            reader.setWhere(Where.SCORE_PARTWISE);
        }
    }

    // -------------------------------------------------------------------------
    // Terminal / lifecycle hooks (invoked directly from MusicXmlReader)
    // -------------------------------------------------------------------------

    /**
     * Provenance gate: only SongScribe-authored documents are accepted. Invoked
     * from the reader's {@code endDocument}; a missing, blank, or foreign
     * {@code <software>} tag is rejected here.
     */
    void checkProvenance() throws SAXException {
        if (software == null || software.isBlank() || !software.startsWith(Constants.PACKAGE_NAME)) {
            throw new MusicXmlReader.ForeignSoftwareException(software);
        }
    }

    /**
     * Assembles the accumulated head scratch and the credit-derived subtitle into
     * the song's {@link SongMetadata} at the terminal {@code </score-partwise>},
     * mirroring {@code SongIO.DocumentReader -> Song.loadFrom} (one all-args
     * construction). Building the record once here — rather than piecemeal at each
     * container's end — dissolves the {@code </identification>}-before-
     * {@code </credit>} ordering hazard (the subtitle credit follows the head), and
     * needs no {@code setSubtitle}/{@code withSubtitle} mutator (neither exists).
     * Runs while mutation tracking is still suspended, so the assembly is silent.
     */
    void applyHeadMetadata() {
        var parsedSong = reader.songOrNull();

        if (parsedSong == null) {
            return;
        }

        // subtitle is empty when the document carries no subtitle credit,
        // matching a blank document. The SongMetadata compact constructor
        // normalizes/coerces every field, so the scratch values need no
        // pre-normalization here.
        parsedSong.setMetadata(new SongMetadata(
            headTitle, headNumber, headPlace,
            headYear, headMonth, headDay,
            headComposer, headLyricist, headLyricsSource,
            headArrangement, headUnofficialTranslation,
            subtitle,
            headWordsYear, headWordsMonth, headWordsDay
        ));
    }

    /**
     * Restores the song-level base tempo after assembly. The base tempo is
     * anchored on the first element of the first line (mirroring
     * {@code Line.attachInitialTempoIfNeeded}), so when that element carries a
     * {@link TempoChangeAttachment}, its tempo is the song's base tempo.
     */
    void applyInitialTempo() {
        var song = reader.songOrNull();

        if (song == null || song.lineCount() == 0) {
            return;
        }

        var firstLine = song.getLine(0);

        if (firstLine.elementCount() == 0) {
            return;
        }

        var attachment = firstLine.getElement(0).findAttachment(TempoChangeAttachment.class);

        if (attachment != null) {
            song.setTempo(attachment.getTempo());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Routes a fully-read {@code <credit>} by its {@code <credit-type>}. Every
     * credit routes into exactly one of three classes; the reader treats each
     * differently. This is the read side of the writer's data-flow contract: the
     * same value (composer, dates, place) is emitted in BOTH head (canonical) and
     * a credit (display-only), and the reader MUST take head and ignore the credit
     * or a hand-edited credit corrupts the model.
     *
     * <pre>
     *                          WRITER                          READER
     *   Song field ─────────────┬─────────────────┐
     *                           │                 │
     *    ┌──────────────────────▼───┐   ┌─────────▼────────────┐
     *    │ HEAD (identification/     │   │ CREDIT (&lt;credit&gt;)    │
     *    │  movement/miscellaneous)  │   │  fonts + positions   │
     *    └──────────┬────────────────┘   └───┬──────────────┬───┘
     *               │                         │              │
     *    ┌──────────▼──────────┐  ┌───────────▼───┐  ┌───────▼─────────────┐
     *    │ CANONICAL           │  │ DISPLAY-ONLY  │  │ WRITE-FORWARD       │
     *    │ read → model        │  │ ignored;      │  │ ignored;            │
     *    │                     │  │ re-derived    │  │ recomputed/constant │
     *    ├─────────────────────┤  ├───────────────┤  ├─────────────────────┤
     *    │ subtitle credit     │  │ title credit  │  │ rights, software,   │
     *    │ 4 score-below credit│  │ composer/     │  │ encoding-date,      │
     *    │ attribution rel-y   │  │  lyricist/    │  │ supports, scaling,  │
     *    │                     │  │  arranger/    │  │ music-font,         │
     *    │                     │  │  date/rights/ │  │ default-x/default-y │
     *    │                     │  │  place credits│  │ (external renderer) │
     *    └─────────────────────┘  └───────────────┘  └─────────────────────┘
     * </pre>
     */
    private void dispatchCredit() throws SAXException {
        var parsedSong = reader.songOrNull();

        if (parsedSong == null) {
            return;
        }

        switch (creditType) {
            // Canonical — the subtitle has no <movement-*> equivalent, so the
            // credit is its source of truth. Held until </score-partwise>, where
            // it is folded into SongMetadata (there is no setSubtitle mutator).
            case MusicXmlTags.CREDIT_SUBTITLE -> {
                subtitle = creditWords;
                setCreditFont(FontKey.SUBTITLE);
            }

            // Canonical — the four score-below text blocks are standalone Song
            // fields with direct setters.
            case MusicXmlTags.CREDIT_UNDERLYRICS -> parsedSong.setUnderLyrics(creditWords);
            case MusicXmlTags.CREDIT_BANGLA_LYRICS -> parsedSong.setBanglaLyrics(creditWords);
            case MusicXmlTags.CREDIT_TRANSLATION -> parsedSong.setTranslatedLyrics(creditWords);
            case MusicXmlTags.CREDIT_FOOTNOTES -> parsedSong.setFootnotes(creditWords);

            // Display-only attribution roles — the text is re-derived from the
            // head <creator>/<rights>/misc-fields, so it is ignored; only the
            // shared relative-y (the attribution user Y offset) is recovered, once.
            case MusicXmlTags.CREDIT_COMPOSER,
                 MusicXmlTags.CREDIT_LYRICIST,
                 MusicXmlTags.CREDIT_ARRANGER,
                 MusicXmlTags.CREDIT_COMPOSITION_DATE,
                 MusicXmlTags.CREDIT_LYRICS_DATE,
                 MusicXmlTags.CREDIT_RIGHTS,
                 MusicXmlTags.CREDIT_PLACE -> readAttributionOffsetOnce(parsedSong);

            // Display-only text, canonical font — the title text is re-derived
            // from <movement-*>, but the credit is still the font's source of
            // truth (see MusicXmlHeaderWriter.writeCredit).
            case MusicXmlTags.CREDIT_TITLE -> setCreditFont(FontKey.TITLE);

            // Display-only — unknown credit-types are skipped.
            default -> {
                // no read state
            }
        }
    }

    /**
     * Recovers the attribution user Y offset from the current attribution credit's
     * {@code relative-y}, but only from the first attribution credit that carries
     * it — the writer emits the same {@code relative-y} on every attribution
     * credit, so reading it once is sufficient.
     */
    private void readAttributionOffsetOnce(Song parsedSong) throws SAXException {
        if (attributionOffsetRead || creditWordsRelativeYRaw == null) {
            return;
        }

        var offsetTenths = MusicXmlUnits.parseDoubleOrThrow(MusicXmlTags.ATTR_RELATIVE_Y, creditWordsRelativeYRaw);
        parsedSong.getAttributionElement().setUserYOffsetSs(MusicXmlUnits.tenthsToSs(offsetTenths));
        attributionOffsetRead = true;
    }

    /**
     * Routes a {@code <creator>}'s text by its captured {@code type} attribute:
     * composer/lyricist into the head scratch, arranger into the arrangement flag
     * (its text is always {@code SRI_CHINMOY}, so only the presence matters).
     * Unknown or missing types are ignored.
     */
    private void applyCreator(String text) {
        if (MusicXmlTags.CREATOR_COMPOSER.equals(creatorType)) {
            headComposer = text;
        } else if (MusicXmlTags.CREATOR_LYRICIST.equals(creatorType)) {
            headLyricist = text;
        } else if (MusicXmlTags.CREATOR_ARRANGER.equals(creatorType)) {
            headArrangement = true;
        }
    }

    /**
     * Routes a {@code <miscellaneous-field>}'s text by its captured {@code name}
     * attribute. The head fields go into the metadata scratch; the two dates go
     * through the shared {@link DateUtils#parseIsoDate} inverse of the writer's
     * {@code toIsoDate} (a malformed date parses to {@code null} and is treated as
     * absent, keeping the scratch date fields at their empty defaults). The
     * defaults residuals — {@code row-height-adjustment} and the sub-attribution
     * font — are applied straight onto the song / {@link #documentFonts}. Unknown
     * misc-fields are ignored.
     */
    private void applyMiscField(@Nullable String name, String text) throws SAXException {
        if (MusicXmlTags.MISC_COMPOSITION_DATE.equals(name)) {
            var parts = DateUtils.parseIsoDate(text);

            if (parts != null) {
                headYear = parts.year();
                headMonth = parts.month();
                headDay = parts.day();
            }
        } else if (MusicXmlTags.MISC_LYRICS_DATE.equals(name)) {
            var parts = DateUtils.parseIsoDate(text);

            if (parts != null) {
                headWordsYear = parts.year();
                headWordsMonth = parts.month();
                headWordsDay = parts.day();
            }
        } else if (MusicXmlTags.MISC_COMPOSITION_PLACE.equals(name)) {
            headPlace = text;
        } else if (MusicXmlTags.MISC_LYRICS_SOURCE.equals(name)) {
            headLyricsSource = lyricsSourceOrThrow(text);
        } else if (MusicXmlTags.MISC_UNOFFICIAL_TRANSLATION.equals(name)) {
            headUnofficialTranslation = Boolean.parseBoolean(text);
        } else if (MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT.equals(name)) {
            // A staff-space delta stored verbatim (the writer omits it when 0).
            var song = reader.songOrNull();

            if (song != null) {
                song.setRowHeightAdjustmentSs(
                    MusicXmlUnits.parseDoubleOrThrow(MusicXmlTags.MISC_ROW_HEIGHT_ADJUSTMENT, text)
                );
            }
        } else if (MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT.equals(name)) {
            subAttributionFontFamily = text;
            applySubAttributionFont();
        } else if (MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE.equals(name)) {
            subAttributionFontSize = MusicXmlUnits.parseIntOrThrow(MusicXmlTags.MISC_SUB_ATTRIBUTION_FONT_SIZE, text);
            applySubAttributionFont();
        }
    }

    /**
     * Recovers a document-font role from a {@code <word-font>}/{@code <lyric-font>}
     * element's {@code font-family}/{@code font-size} attributes. See
     * {@link #applyFont}.
     */
    private void setDocumentFont(FontKey key, Attributes attributes) throws SAXException {
        applyFont(key, attributes.getValue(MusicXmlTags.ATTR_FONT_FAMILY), attributes.getValue(MusicXmlTags.ATTR_FONT_SIZE));
    }

    /**
     * Recovers a document-font role from the current {@code <credit-words>}'s
     * captured {@code font-family}/{@code font-size}. See {@link #applyFont}.
     */
    private void setCreditFont(FontKey key) throws SAXException {
        applyFont(key, creditWordsFontFamily, creditWordsFontSizeRaw);
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
     * Resolves a {@code lyrics-source} token to a {@link Song.LyricsSource},
     * throwing a {@link SAXException} on an unknown token. Fails hard rather than
     * defaulting, matching the reader's {@code parseIntOrThrow}/
     * {@code parseDoubleOrThrow} convention — the writer only ever emits an enum
     * constant name, so an unknown token means a corrupt document.
     */
    private static Song.LyricsSource lyricsSourceOrThrow(String token) throws SAXException {
        try {
            return Song.LyricsSource.valueOf(token);
        } catch (IllegalArgumentException e) {
            throw new SAXException(
                "Corrupt document: malformed <" + MusicXmlTags.MISC_LYRICS_SOURCE +
                "> value: '" + token + "'", e
            );
        }
    }
}
