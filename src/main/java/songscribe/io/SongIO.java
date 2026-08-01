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
package songscribe.io;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import songscribe.Strings;
import songscribe.message.SongData;
import songscribe.font.DocumentFonts;
import songscribe.font.DocumentFontsHolder;
import songscribe.dom.Song;
import songscribe.dom.Song.LyricsSource;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Tempo;
import songscribe.dom.AttributionFormatter;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.PageModel;
import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.TempoChangeAttachment;
import songscribe.util.DateUtils;

public final class SongIO {

    public static final int IO_MAJOR_VERSION = 2;
    public static final int IO_MINOR_VERSION = 10;

    // version 1.0
    private static final String XML_SONG = "song";
    private static final String XML_COMPOSITION_LEGACY = "composition";
    private static final String XML_VERSION = "version";
    private static final String XML_KEYS = "keys";
    private static final String XML_KEYTYPE = "keytype";
    private static final String XML_NUMBER = "number";
    private static final String XML_TITLE = "songtitle";
    private static final String XML_LYRICS = "lyrics";
    private static final String XML_INFO = "rightinfo";
    private static final String XML_FOOTNOTES = "footnotes";
    private static final String XML_NOTES = "notes";
    private static final String XML_TEMPO_CHANGES = "tempochanges";

    // version 1.1
    private static final String XML_LINES = "lines";
    private static final String XML_VIEW = "view";
    private static final String XML_UNDERLYRICS = "underlyrics";
    private static final String XML_TRANSLATED_LYRICS = "translatedlyrics";
    private static final String XML_BANGLA_LYRICS = "banglalyrics";
    private static final String XML_LINE_WIDTH = "linewidth";
    private static final String XML_ROW_HEIGHT = "rowheight";
    private static final String XML_PLACE = "place";
    private static final String XML_YEAR = "year";
    private static final String XML_MONTH = "month";
    private static final String XML_DAY = "day";
    private static final String XML_LYRICS_DATE = "lyricsDate";
    private static final String XML_INFO_STARTY = "rightinfostarty";

    // version 1.3
    private static final String XML_UNOFFICIAL_TRANSLATION =
        "unofficialTranslation";

    // version 1.4
    private static final String XML_DYNAMIC_LAYOUT = "dynamicLayout";

    // version 2.8 — discrete attribution fields
    private static final String XML_COMPOSER = "composer";
    private static final String XML_LYRICIST = "lyricist";
    private static final String XML_LYRICS_SOURCE = "lyricssource";
    private static final String XML_ARRANGEMENT = "arrangement";
    private static final String XML_ATTRIBUTION_Y_OFFSET = "attributionyoffset";

    // version 2.9 — subtitle
    private static final String XML_SUBTITLE = "subtitle";

    private SongIO() {
    }

    public static void writeSong(Song c, DocumentFontsHolder fonts, PrintWriter pw) {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        pw.println(
            '<' +
                XML_SONG +
                ' ' +
                XML_VERSION +
                "=\"" +
                IO_MAJOR_VERSION +
                '.' +
                IO_MINOR_VERSION +
                "\">"
        );
        XML.setIndent(2);
        XML.writeValue(
            pw,
            XML_KEYS,
            Integer.toString(c.getDefaultKeyAccidentalCount())
        );
        var keyType = c.getDefaultKeyType();
        XML.writeValue(pw, XML_KEYTYPE, keyType.name());

        var tempo = c.getTempo();

        if (tempo != null) {
            TempoIO.writeTempo(tempo, pw, 2);
        }

        XML.setIndent(2);

        XML.writeValue(pw, XML_NUMBER, c.getNumber());

        if (!c.getTitle().isEmpty()) {
            XML.writeValue(pw, XML_TITLE, c.getTitle());
        }

        if (!c.getSubtitle().isEmpty()) {
            XML.writeValue(pw, XML_SUBTITLE, c.getSubtitle());
        }

        if (!c.getPlace().isEmpty()) {
            XML.writeValue(pw, XML_PLACE, c.getPlace());
        }

        if (!c.getYear().isEmpty()) {
            XML.writeValue(pw, XML_YEAR, c.getYear());
        }

        if (c.getMonth() > 0) {
            XML.writeValue(pw, XML_MONTH, Integer.toString(c.getMonth()));
        }

        if (c.getDay() > 0) {
            XML.writeValue(pw, XML_DAY, Integer.toString(c.getDay()));
        }

        var lyricsDateStr = DateUtils.toIsoDate(c.getWordsYear(), c.getWordsMonth(), c.getWordsDay());

        if (!lyricsDateStr.isEmpty()) {
            XML.writeValue(pw, XML_LYRICS_DATE, lyricsDateStr);
        }

        if (!c.getUnderLyrics().isEmpty()) {
            XML.writeValue(pw, XML_UNDERLYRICS, c.getUnderLyrics());
        }

        if (!c.getBanglaLyrics().isEmpty()) {
            XML.writeValue(pw, XML_BANGLA_LYRICS, c.getBanglaLyrics());
        }

        if (!c.getTranslatedLyrics().isEmpty()) {
            XML.writeValue(pw, XML_TRANSLATED_LYRICS, c.getTranslatedLyrics());
        }

        if (c.isUnofficialTranslation()) {
            XML.writeValue(
                pw,
                XML_UNOFFICIAL_TRANSLATION,
                Boolean.toString(true)
            );
        }

        XML.writeValue(pw, XML_COMPOSER, c.getComposer());
        XML.writeValue(pw, XML_LYRICIST, c.getLyricist());
        XML.writeValue(pw, XML_LYRICS_SOURCE, c.getLyricsSource().name());

        if (c.isArrangement()) {
            XML.writeValue(pw, XML_ARRANGEMENT, Boolean.toString(true));
        }

        // Keep writing the computed blob for interop with older readers.
        var attributionBlob = AttributionFormatter.text(c.getMetadata(), c.showTranslation());

        if (!attributionBlob.isEmpty()) {
            XML.writeValue(pw, XML_INFO, attributionBlob);
        }

        if (!c.getFootnotes().isEmpty()) {
            XML.writeValue(pw, XML_FOOTNOTES, c.getFootnotes());
        }

        if (c.getRowHeightAdjustmentSs() != 0) {
            XML.writeValue(
                pw,
                XML_ROW_HEIGHT,
                Double.toString(c.getRowHeightAdjustmentSs())
            );
        }

        // Line width in staff-space units
        XML.writeValue(pw, XML_LINE_WIDTH, Double.toString(c.getLineWidthSs()));

        // Always write dynamicLayout=true for new documents
        XML.writeValue(pw, XML_DYNAMIC_LAYOUT, Boolean.toString(true));

        var attributionYOffsetSs = c.getAttributionElement().getUserYOffsetSs();

        if (attributionYOffsetSs != 0) {
            XML.writeValue(pw, XML_ATTRIBUTION_Y_OFFSET, Double.toString(attributionYOffsetSs));
        }

        pw.println("  <" + XML_LINES + '>');

        for (var l = 0; l < c.lineCount(); l++) {
            LineIO.writeLine(c.getLine(l), pw);
        }

        pw.println("  </" + XML_LINES + '>');
        pw.println("  <" + XML_VIEW + '>');
        ViewIO.writeView(fonts, pw);
        pw.println("  </" + XML_VIEW + '>');
        pw.println("</" + XML_SONG + '>');
    }

    public static class NewerVersionException extends SAXException {

        public NewerVersionException() {
            super("File version is newer than the application supports.");
        }
    }

    public static class DocumentReader extends DefaultHandler {

        private static final Logger LOG = LoggerFactory.getLogger(DocumentReader.class);
        private static final String BY_PREFIX = "by ";
        private static final int CURRENT_FORMAT_VERSION = 3;

        @Nullable
        private Where where = null;

        @Nullable
        private String lastTag;

        private final StringBuilder value = new StringBuilder(200);

        private StaffElementIO.@Nullable StaffElementReader noteReader = null;
        private TempoIO.@Nullable TempoReader tempoReader = null;
        private LineIO.@Nullable LineReader lineReader = null;
        private ViewIO.@Nullable ViewReader viewReader = null;
        private int majorVersion = 0, minorVersion = 0;

        // Stub Song that gets attached to every parsed Line during XML parsing,
        // then repopulated via loadFrom(data) in getSong() once parsing completes.
        @Nullable
        private Song parsingSong = null;

        // Parsed song data (replaces direct Song mutation)
        @Nullable
        private Tempo tempo = null;
        private String number = Strings.get(Strings.SONG_DEFAULT_NUMBER);
        private String title = Strings.get(Strings.DOCUMENT_UNTITLED);
        private String place = "";
        private int month = 0;
        private int day = 0;
        private boolean monthInvalid = false;
        private String year = "";
        private String lyrics = "";
        private String underLyrics = "";
        private String banglaLyrics = "";
        private String translatedLyrics = "";
        private String composer = Song.SRI_CHINMOY;
        private String lyricist = Song.SRI_CHINMOY;
        private LyricsSource lyricsSource = LyricsSource.LYRICIST;
        private boolean arrangement = false;
        private String subtitle = "";
        private String attribution = "";
        private String footnotes = "";
        private boolean unofficialTranslation = false;
        private String wordsYear = "";
        private int wordsMonth = 0;
        private int wordsDay = 0;
        @Nullable
        private String invalidLyricsDate = null;
        private int defaultKeyAccidentalCount = Song.DEFAULT_KEY_ACCIDENTAL_COUNT;
        private KeyType defaultKeyType = Song.DEFAULT_KEY_TYPE;
        private double attributionUserYOffsetSs = 0;
        private double rowHeightAdjustmentSs = 0;
        private double lineWidthSs = PageModel.getDefaultLineWidthSs();
        private boolean hasBeenDynamicallyLaidOut = false;
        private final List<Line> parsedLines = new ArrayList<>();
        private final Map<Line, LegacyLineOffsets> parsedLegacyOffsets = new HashMap<>();

        /**
         * Refuses to fetch anything a {@code DOCTYPE} names, handing the parser an
         * empty stand-in instead. {@code SAXParser.parse} installs this handler as
         * the entity resolver, so this override is all it takes — see
         * {@link SafeXmlParser#emptyEntitySource()}.
         */
        @Override
        public InputSource resolveEntity(String publicId, String systemId) {
            return SafeXmlParser.emptyEntitySource();
        }

        @Override
        public void startElement(
            String uri,
            String localName,
            String qName,
            Attributes attributes
        ) throws SAXException, NewerVersionException {
            if (where == null) {
                if (qName.equals(XML_SONG) || qName.equals(XML_COMPOSITION_LEGACY)) {
                    try {
                        var version = attributes.getValue(XML_VERSION);
                        var dotIndex = version.indexOf('.');
                        majorVersion = Integer.parseInt(
                            version.substring(0, dotIndex)
                        );
                        minorVersion = Integer.parseInt(
                            version.substring(dotIndex + 1)
                        );
                        where = Where.SONG;

                        parsingSong = newSuspendedStubSong();

                        if ((majorVersion == 1) && (minorVersion == 0)) {
                            noteReader = new StaffElementIO.StaffElementReader();
                            tempoReader = new TempoIO.TempoReader();
                        } else if (
                            (majorVersion == 1 && minorVersion >= 1) ||
                            // Hard-coded to IO_MINOR_VERSION; bump when the reader is updated.
                            (majorVersion == 2 && minorVersion <= 10)
                        ) {
                            lineReader = new LineIO.LineReader(parsingSong);
                            viewReader = new ViewIO.ViewReader();
                        } else {
                            throw new NewerVersionException();
                        }
                    } catch (NumberFormatException e) {
                        throw new SAXException(
                            "SongScribe version is not a number.",
                            e
                        );
                    }
                }
            } else {
                if ((majorVersion == 1) && (minorVersion == 0)) {
                    startElement10(uri, localName, qName, attributes);
                } else {
                    startElement11(uri, localName, qName, attributes);
                }
            }

            value.delete(0, value.length());
        }

        public void startElement10(
            String uri,
            String localName,
            String qName,
            Attributes attributes
        ) {
            if (where == Where.NOTES) {
                if (noteReader == null || parsingSong == null) {
                    return;
                }

                if (noteReader.startElement10(qName, attributes)) {
                    parsedLines.add(new Line(parsingSong));
                }
            } else if (where == Where.TEMPO_CHANGE) {
                if (tempoReader == null) {
                    return;
                }

                tempoReader.startElement10(qName);
            } else if (where == Where.SONG) {
                if (qName.equals(XML_NOTES)) {
                    where = Where.NOTES;
                } else if (qName.equals(XML_TEMPO_CHANGES)) {
                    where = Where.TEMPO_CHANGE;
                } else {
                    lastTag = qName;
                }
            }
        }

        public void startElement11(
            String uri,
            String localName,
            String qName,
            Attributes attributes
        ) throws SAXException {
            if (where == Where.LINES) {
                if (lineReader == null) {
                    return;
                }

                lineReader.startElement11(qName, attributes);
            } else if (where == Where.VIEW) {
                if (viewReader == null) {
                    return;
                }

                viewReader.startElement11(qName);
            } else if (where == Where.TEMPO) {
                if (tempoReader == null) {
                    return;
                }

                tempoReader.startElement11(qName);
            } else if (where == Where.SONG) {
                switch (qName) {
                    case XML_LINES -> where = Where.LINES;
                    case XML_VIEW -> where = Where.VIEW;
                    case TempoIO.XML_TEMPO -> {
                        where = Where.TEMPO;
                        tempoReader = new TempoIO.TempoReader();
                        tempoReader.startElement11(qName);
                    }
                    default -> lastTag = qName;
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            try {
                if ((majorVersion == 1) && (minorVersion == 0)) {
                    endElement10(qName);
                } else if ((majorVersion == 1) && (minorVersion == 1)) {
                    endElement11(qName);
                } else {
                    endElement12(qName);
                }
            } catch (RuntimeException e) {
                throw new SAXException(e.getMessage(), e);
            }
        }

        public void endElement10(String qName) throws SAXException {
            if (qName.equals(XML_NOTES)) {
                where = Where.SONG;
            } else if (qName.equals(XML_TEMPO_CHANGES)) {
                where = Where.SONG;
            } else if (where == Where.NOTES) {
                if (noteReader == null) {
                    return;
                }

                var note = noteReader.endElement10(qName);

                if (note != null) {
                    if (parsedLines.isEmpty()) {
                        if (parsingSong == null) {
                            return;
                        }

                        parsedLines.add(new Line(parsingSong));
                    }

                    var line = parsedLines.getLast();
                    note.setXOffsetPx(ScaleContext.ssToRoundedPx(
                        InsertionSpacingCalculator.calculateAppendPositionSs(line, note, null, null)));
                    note.setDirection(StaffElement.defaultDirection(note));
                    line.addElement(note);
                }
            } else if (where == Where.TEMPO_CHANGE) {
                if (tempoReader == null) {
                    return;
                }

                var tc = tempoReader.endElement10(qName);

                if (tc != null) {
                    if (tempoReader.getPos10() == 0) {
                        tempo = tc;
                    } else {
                        var firstElementInLine = 0;

                        for (var line : parsedLines) {
                            if (
                                tempoReader.getPos10() <
                                    (firstElementInLine + line.elementCount())
                            ) {
                                var idx = tempoReader.getPos10() - firstElementInLine;

                                if (idx < 0 || idx >= line.elementCount()) {
                                    throw DocumentValidation.corrupt(LOG, "Corrupt document: legacy tempo position index out of range: {}, element count: {}", idx, line.elementCount());
                                }

                                var element = line.getElement(idx);
                                element.addAttachment(new TempoChangeAttachment(element, tc));
                                break;
                            }

                            firstElementInLine += line.elementCount() + 1;
                        }
                    }
                }
            } else if (where == Where.SONG) {
                //noinspection PointlessNullCheck
                if (lastTag != null && qName.equals(lastTag)) {
                    var str = value.toString();

                    switch (lastTag) {
                        case XML_KEYS -> defaultKeyAccidentalCount =
                            Integer.parseInt(str);
                        case XML_KEYTYPE -> defaultKeyType =
                            KeyType.valueOf(str);
                        case XML_NUMBER -> number = str;
                        case XML_TITLE -> title =
                            str.isEmpty() ? "Untitled" : str;
                        case XML_LYRICS -> lyrics = str;
                        case XML_INFO -> attribution = str;
                        case XML_FOOTNOTES -> footnotes = str;
                    }
                }
            }

            value.delete(0, value.length());
            lastTag = null;
        }

        public void endElement11(String qName) throws SAXException {
            // No change except at the end of the line reading we set
            // the quaver notes to upper position.
            endElement12(qName);

            if ((where == Where.LINES) && !parsedLines.isEmpty()) {
                var lastLine = parsedLines.getLast();

                for (var i = 0; i < lastLine.elementCount(); i++) {
                    if (lastLine.getElement(i).getType().isGraceNote()) {
                        lastLine.getElement(i).setDirection(StaffElement.Direction.UP);
                    }
                }
            }
        }

        public void endElement12(String qName) throws SAXException {
            if (qName.equals(XML_LINES)) {
                where = Where.SONG;
            } else if (qName.equals(XML_VIEW)) {
                where = Where.SONG;
            } else if (where == Where.LINES) {
                if (lineReader == null) {
                    return;
                }

                var l = lineReader.endElement11(qName);

                if (l != null) {
                    parsedLines.add(l);
                    parsedLegacyOffsets.put(l, lineReader.getLegacyOffsets());
                }
            } else if (where == Where.TEMPO) {
                if (tempoReader == null) {
                    return;
                }

                var t = tempoReader.endElement11(qName);

                if (t != null) {
                    tempo = t;
                    where = Where.SONG;
                }
            } else if (where == Where.SONG) {
                //noinspection PointlessNullCheck
                if (lastTag != null && qName.equals(lastTag)) {
                    var str = value.toString();

                    switch (lastTag) {
                        case XML_KEYS ->
                            defaultKeyAccidentalCount = DocumentValidation.parseIntOrThrow(LOG, XML_KEYS, str);
                        case XML_KEYTYPE -> {
                            try {
                                defaultKeyType = KeyType.valueOf(str);
                            } catch (IllegalArgumentException e) {
                                throw DocumentValidation.corrupt(LOG, "Corrupt document: unknown key type: '{}'", str);
                            }
                        }
                        case XML_NUMBER -> number = str;
                        case XML_TITLE -> title =
                            str.isEmpty() ? "Untitled" : str;
                        case XML_SUBTITLE -> subtitle = str;
                        case XML_PLACE -> place = str;
                        case XML_YEAR -> year = str;
                        case XML_MONTH -> {
                            month = DocumentValidation.parseIntOrThrow(LOG, XML_MONTH, str);
                            if (month < 1 || month > DateUtils.MAX_MONTH) {
                                LOG.warn("Corrupt document: month out of range: {}", month);
                                month = 0;
                                monthInvalid = true;
                            }
                        }
                        case XML_DAY -> {
                            day = DocumentValidation.parseIntOrThrow(LOG, XML_DAY, str);
                            if (day < 1 || day > DateUtils.MAX_DAY) {
                                LOG.warn("Corrupt document: day out of range: {}", day);
                                day = 0;
                            }
                            if (monthInvalid) {
                                day = 0;
                            }
                        }
                        case XML_LYRICS -> lyrics = str;
                        case XML_UNDERLYRICS -> underLyrics = str;
                        case XML_BANGLA_LYRICS -> banglaLyrics = str;
                        case XML_TRANSLATED_LYRICS -> translatedLyrics = str;
                        case XML_UNOFFICIAL_TRANSLATION -> unofficialTranslation =
                            Boolean.parseBoolean(str);
                        case XML_COMPOSER -> composer = Song.coercePerson(str);
                        case XML_LYRICIST -> lyricist = Song.coercePerson(str);
                        case XML_LYRICS_SOURCE -> {
                            try {
                                lyricsSource = LyricsSource.valueOf(str);
                            } catch (IllegalArgumentException e) {
                                LOG.warn("Unknown lyrics source '{}', defaulting to LYRICIST", str);
                                lyricsSource = LyricsSource.LYRICIST;
                            }
                        }
                        case XML_ARRANGEMENT -> arrangement = Boolean.parseBoolean(str);
                        case XML_FOOTNOTES -> footnotes = str;
                        case XML_INFO -> attribution = str;
                        // Still read XML_INFO_STARTY from older files for the pixel→ss migration;
                        // new files (≥ 2.8) do not write it.
                        case XML_INFO_STARTY -> parseVersionedDouble(str);
                        case XML_ROW_HEIGHT -> rowHeightAdjustmentSs =
                            parseVersionedDouble(str);
                        case XML_LINE_WIDTH -> lineWidthSs =
                            parseVersionedDouble(str);
                        case XML_DYNAMIC_LAYOUT -> hasBeenDynamicallyLaidOut =
                            Boolean.parseBoolean(str);
                        case XML_ATTRIBUTION_Y_OFFSET -> {
                            try {
                                attributionUserYOffsetSs = Double.parseDouble(str);
                            } catch (NumberFormatException e) {
                                LOG.warn("Corrupt document: malformed attributionyoffset: '{}', using default", str);
                            }
                        }
                        case XML_LYRICS_DATE -> parseLyricsDate(str);
                    }
                }
            } else if (where == Where.VIEW) {
                if (viewReader == null) {
                    return;
                }

                viewReader.endElement11(qName);
            }

            value.delete(0, value.length());
            lastTag = null;
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (where == Where.LINES) {
                if (lineReader != null) {
                    lineReader.characters(ch, start, length);
                }
            } else if (where == Where.VIEW) {
                if (viewReader != null) {
                    viewReader.characters(ch, start, length);
                }
            } else if (where == Where.NOTES) {
                if (noteReader != null) {
                    noteReader.characters(ch, start, length);
                }
            } else if (where == Where.TEMPO_CHANGE) {
                if (tempoReader != null) {
                    tempoReader.characters(ch, start, length);
                }
            } else if (where == Where.TEMPO) {
                if (tempoReader != null) {
                    tempoReader.characters(ch, start, length);
                }
            } else if ((where == Where.SONG) && (lastTag != null)) {
                value.append(ch, start, length);
            }
        }

        /**
         * Returns true if this document named a retired accidental anywhere, which was
         * converted to the accidental that sounds the same. Only one of the two note
         * readers exists for a given file — {@code noteReader} for v1.0, {@code lineReader}
         * for later versions — so both are consulted.
         */
        public boolean accidentalsConverted() {
            return (noteReader != null && noteReader.sawLegacyAccidental())
                || (lineReader != null && lineReader.sawLegacyAccidental());
        }

        public Song getSong() {
            // For legacy files that predate the discrete attribution tags, derive
            // composer/lyricist (and, for v1.0 only, date/place) from the blob.
            if (isLegacyAttributionFile()) {
                parseLegacyAttributionBlob();
            }

            // Run the ordered pre-assembly migrations over the parsed lines and
            // song-level scalars. The pipeline owns all version-gated dispatch;
            // see MigrationPipeline for the stage list and ordering invariant.
            var ctx = new MigrationContext();
            ctx.lines = parsedLines;
            ctx.legacyLineOffsets = parsedLegacyOffsets;
            ctx.lineWidthSs = lineWidthSs;
            ctx.rowHeightAdjustmentSs = rowHeightAdjustmentSs;
            ctx.majorVersion = majorVersion;
            ctx.minorVersion = minorVersion;
            ctx.lyrics = lyrics;

            MigrationPipeline.runPreAssembly(ctx);

            // After migration, format version is CURRENT_FORMAT_VERSION (discrete attribution fields).
            var formatVersion = CURRENT_FORMAT_VERSION;

            // Read the migrated scalars and lines back from the context.
            var data = new SongData(
                tempo,
                number,
                title,
                place,
                month,
                day,
                year,
                underLyrics,
                banglaLyrics,
                translatedLyrics,
                composer,
                lyricist,
                lyricsSource,
                arrangement,
                footnotes,
                unofficialTranslation,
                defaultKeyAccidentalCount,
                defaultKeyType,
                ctx.rowHeightAdjustmentSs,
                ctx.lineWidthSs,
                ctx.lines,
                hasBeenDynamicallyLaidOut,
                formatVersion,
                subtitle,
                wordsYear,
                wordsMonth,
                wordsDay
            );

            // Repopulate the stub Song that was created at <song> startElement
            // and attached to every parsed Line. This preserves Line.song
            // references already established during parsing. Mutation tracking
            // remains suspended (from newSuspendedStubSong) through loadFrom so
            // that line-level setters during reload bypass the bracket check.
            if (parsingSong == null) {
                throw new IllegalStateException("parsingSong was not initialized");
            }

            var song = parsingSong;

            try {
                song.loadFrom(data);
                if (attributionUserYOffsetSs != 0) {
                    song.getAttributionElement().setUserYOffsetSs(attributionUserYOffsetSs);
                }
            } finally {
                song.endSuspendMutationTracking();
            }

            // Run the post-assembly migrations against the assembled song
            // (legacy lyric import + syllabic backfill).
            ctx.song = song;
            MigrationPipeline.runPostAssembly(ctx);

            return song;
        }

        /**
         * Returns the document fonts parsed from the file's {@code <view>} block.
         * For v1.0 files (which have no {@code <view>} section) and v1.1+ files
         * whose block omits a role, the missing roles fall through to
         * {@link DocumentFonts#defaultFonts()}.
         * <p>
         * Must be called after parsing completes; the result is independent of
         * {@link #getSong()} and is the authoritative font source for the
         * load path.
         */
        public DocumentFonts getDocumentFonts() {
            return viewReader != null
                ? viewReader.getDocumentFonts()
                : DocumentFonts.defaultFonts();
        }

        /**
         * Returns the raw {@code lyricsDate} string from the file when it was
         * present but could not be parsed, or {@code null} when the tag was
         * absent or valid.  Phase 3 uses this to show a warning to the user.
         */
        @Nullable
        public String getInvalidLyricsDate() {
            return invalidLyricsDate;
        }

        /**
         * Creates the stub Song that gets attached to every parsed Line and
         * suspends its mutation tracking. Suspension is released in {@link #getSong()}
         * after the final {@link Song#loadFrom(SongData)} call.
         */
        private static Song newSuspendedStubSong() {
            var stub = Song.newParsingStub();
            stub.beginSuspendMutationTracking();
            return stub;
        }

        private double parseVersionedDouble(String str) {
            return (majorVersion >= 2 && minorVersion >= 1)
                ? Double.parseDouble(str)
                : Integer.parseInt(str);
        }

        // True when the file predates the discrete attribution tags (< 2.8).
        private boolean isLegacyAttributionFile() {
            return majorVersion < 2 || (majorVersion == 2 && minorVersion < 8);
        }

        // True when the file is so old that place/date were only in the attribution blob.
        private boolean isV10File() {
            return majorVersion == 1 && minorVersion == 0;
        }

        /**
         * Parses the legacy {@code XML_INFO} blob to populate {@code composer},
         * {@code lyricist}, and (for v1.0 only) {@code month}/{@code day}/{@code year}/{@code place}.
         *
         * <p>The blob format is:
         * <pre>
         * Words and Music
         * [bB]y &lt;person&gt;
         * [optional date: "Month Day, Year" / "Month, Year" / "Year"]
         * [optional place]
         * </pre>
         */
        private void parseLegacyAttributionBlob() {
            if (attribution.isBlank()) {
                return;
            }

            var blobLines = attribution.split("\n", -1);
            var byLineIndex = -1;

            for (var i = 0; i < blobLines.length; i++) {
                var trimmed = blobLines[i].trim();

                if (trimmed.toLowerCase().startsWith(BY_PREFIX)) {
                    var person = trimmed.substring(BY_PREFIX.length()).trim();

                    if (!person.isEmpty()) {
                        composer = person;
                        lyricist = person;
                    }

                    byLineIndex = i;
                    break;
                }
            }

            // Only extract date/place from the blob for v1.0 files,
            // where those fields were not serialized as separate XML tags.
            if (!isV10File() || byLineIndex < 0) {
                return;
            }

            var remainingStart = byLineIndex + 1;

            if (remainingStart >= blobLines.length) {
                return;
            }

            var dateLine = blobLines[remainingStart].trim();

            if (!dateLine.isEmpty()) {
                parseLegacyDateLine(dateLine);
                remainingStart++;
            }

            if (remainingStart < blobLines.length) {
                var placeLine = blobLines[remainingStart].trim();

                if (!placeLine.isEmpty()) {
                    place = placeLine;
                }
            }
        }

        private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        };

        /**
         * Parses a date string of the form "Month Day, Year", "Month, Year", or "Year"
         * into the {@code month}, {@code day}, and {@code year} fields.
         */
        private void parseLegacyDateLine(String dateLine) {
            // Try "Month Day, Year" or "Month, Year"
            for (var i = 0; i < MONTH_NAMES.length; i++) {
                if (dateLine.startsWith(MONTH_NAMES[i])) {
                    month = i + 1;
                    var rest = dateLine.substring(MONTH_NAMES[i].length()).trim();

                    if (rest.startsWith(",")) {
                        rest = rest.substring(1).trim();
                    }

                    var spaceIdx = rest.indexOf(' ');

                    if (spaceIdx > 0) {
                        // "Day, Year" — the rest starts with a day number
                        var dayStr = rest.substring(0, spaceIdx).replace(",", "").trim();

                        try {
                            var parsedDay = Integer.parseInt(dayStr);

                            if (parsedDay >= 1 && parsedDay <= DateUtils.MAX_DAY) {
                                day = parsedDay;
                            }
                        } catch (NumberFormatException ignored) { /* best-effort */ }

                        year = rest.substring(spaceIdx).replace(",", "").trim();
                    } else {
                        year = rest;
                    }

                    return;
                }
            }

            // Fall back: treat the whole line as a year
            year = dateLine.trim();
        }

        /**
         * Parses a reduced-precision ISO 8601 lyrics date string into the
         * {@code wordsYear}, {@code wordsMonth}, and {@code wordsDay} fields.
         *
         * <p>Accepts the forms {@code YYYY}, {@code YYYY-MM}, and {@code YYYY-MM-DD}.
         * On any parse or range failure, logs a warning and leaves the fields at
         * their defaults (blank/zero); also sets {@link #invalidLyricsDate} to
         * the raw string for Phase 3 to surface to the user.
         *
         * <p>This method deliberately does not throw — a bad {@code lyricsDate}
         * must not abort the whole document load.
         */
        private void parseLyricsDate(String str) {
            var parsed = DateUtils.parseIsoDate(str);

            if (parsed == null) {
                LOG.warn("Corrupt document: malformed lyricsDate: '{}'", str);
                invalidLyricsDate = str;
                return;
            }

            wordsYear = parsed.year();
            wordsMonth = parsed.month();
            wordsDay = parsed.day();
        }

        private enum Where {
            SONG,
            LINES,
            VIEW,
            NOTES,
            TEMPO,
            TEMPO_CHANGE,
        }
    }
}
