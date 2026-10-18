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
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.dom.DocumentScale;
import songscribe.dom.PartialDate;
import songscribe.dom.PendingBeam;
import songscribe.dom.PendingEnding;
import songscribe.dom.PendingMarks;
import songscribe.dom.PendingTie;
import songscribe.dom.RecordedChange;
import songscribe.dom.Song;
import songscribe.dom.Song.LyricsSource;
import songscribe.dom.SongMetadata;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;
import songscribe.font.DocumentFonts;
import songscribe.layout.InsertionSpacingCalculator;
import songscribe.layout.PageModel;
import songscribe.util.DateUtils;

public final class SongIO {

    /**
     * Revision 1 is parsed by different readers from every later revision.
     */
    private static final int ORIGINAL_MAJOR_VERSION = 1;
    private static final int ORIGINAL_MINOR_VERSION = 0;

    /**
     * Raise these when the reader learns a new revision.
     */
    private static final int MAX_READABLE_MAJOR_VERSION = 2;
    private static final int MAX_READABLE_MINOR_VERSION = 10;

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
    private static final String XML_PLACE = "place";
    private static final String XML_YEAR = "year";
    private static final String XML_MONTH = "month";
    private static final String XML_DAY = "day";
    private static final String XML_LYRICS_DATE = "lyricsDate";

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

    // version 2.9 — subtitle
    private static final String XML_SUBTITLE = "subtitle";

    private SongIO() {
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

        private static final FormatVersion FIRST_ATTRIBUTION_TAGS_VERSION = new FormatVersion(2, 8);

        @Nullable
        private Where where = null;

        private final LeafTagText leaf = new LeafTagText();

        private StaffElementIO.@Nullable StaffElementReader noteReader = null;
        private TempoIO.@Nullable TempoReader tempoReader = null;
        private LineIO.@Nullable LineReader lineReader = null;
        private ViewIO.@Nullable ViewReader viewReader = null;
        private int majorVersion = 0, minorVersion = 0;

        private final Song song;

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
        private PartialDate wordsDate = PartialDate.EmptyDate.INSTANCE;
        @Nullable
        private String invalidLyricsDate = null;
        // The song-level key an old file carried, held as its two tags until they can be
        // judged together. Only line 0 consumes it — see applyDocumentKeyToFirstLine.
        //
        // Each tag overwrites its own half, so a file carrying only one of them keeps the default
        // for the other: <keys>3</keys> with no <keytype> has always meant three flats. The halves
        // therefore default separately, both derived from the one default key so they cannot
        // drift apart from it.
        private int documentKeyAccidentalCount = Key.DEFAULT.accidentalCount();
        private LegacyKeyType documentKeyType = LegacyKeyType.forKey(Key.DEFAULT);
        private boolean hasBeenDynamicallyLaidOut = false;
        private final List<Line> parsedLines = new ArrayList<>();

        /**
         * @param song empty, and in a load
         */
        public DocumentReader(Song song) {
            this.song = song;
        }

        private Key documentKey() throws SAXException {
            return documentKeyType.keyFor(LOG, documentKeyAccidentalCount);
        }

        /**
         * Line 0 has nothing to inherit from, so it alone needs the document-level key.
         */
        private void applyDocumentKeyToFirstLine(List<Line> lines) throws SAXException {
            if (lines.isEmpty()) {
                return;
            }

            var firstLine = lines.getFirst();

            if (firstLine.getKey() == null) {
                firstLine.setKey(documentKey());
            }
        }

        /**
         * Never fetches anything a {@code DOCTYPE} names.
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
            leaf.reset();

            if (where == null) {
                if (qName.equals(XML_SONG) || qName.equals(XML_COMPOSITION_LEGACY)) {
                    var version = attributes.getValue(XML_VERSION);

                    if (version == null) {
                        throw new SAXException("The <" + qName + "> element has no version attribute.");
                    }

                    try {
                        var dotIndex = version.indexOf('.');
                        majorVersion = Integer.parseInt(
                            version.substring(0, dotIndex)
                        );
                        minorVersion = Integer.parseInt(
                            version.substring(dotIndex + 1)
                        );
                        where = Where.SONG;

                        if (majorVersion == ORIGINAL_MAJOR_VERSION && minorVersion == ORIGINAL_MINOR_VERSION) {
                            noteReader = new StaffElementIO.StaffElementReader();
                            tempoReader = new TempoIO.TempoReader();
                        } else if (
                            (majorVersion == ORIGINAL_MAJOR_VERSION && minorVersion > ORIGINAL_MINOR_VERSION) ||
                            (majorVersion == MAX_READABLE_MAJOR_VERSION && minorVersion <= MAX_READABLE_MINOR_VERSION)
                        ) {
                            lineReader = new LineIO.LineReader(song);
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
        }

        public void startElement10(
            String uri,
            String localName,
            String qName,
            Attributes attributes
        ) throws SAXException {
            if (where == Where.NOTES) {
                if (noteReader == null) {
                    return;
                }

                if (noteReader.startElement10(qName, attributes)) {
                    parsedLines.add(new Line(song));
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
                    leaf.open(qName);
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
                    default -> leaf.open(qName);
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName)
                throws SAXException {
            try {
                if ((majorVersion == 1) && (minorVersion == 0)) {
                    endElement10(qName);
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
                        // v1.0 carries no per-line key; the auto-created line takes the document
                        // key at the end of the load, with every other keyless first line.
                        parsedLines.add(new Line(song));
                    }

                    parsedLines.getLast().addElement(note);
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

                                if (!line.hasIndex(idx)) {
                                    throw DocumentValidation.corrupt(LOG, "Corrupt document: legacy tempo position index out of range: {}, element count: {}", idx, line.elementCount());
                                }

                                var element = line.getElement(idx);
                                DocumentValidation.attachUnique(LOG, element, new TempoChangeAttachment(tc));
                                break;
                            }

                            firstElementInLine += line.elementCount() + 1;
                        }
                    }
                }
            } else if (where == Where.SONG) {
                leaf.close(qName, (tag, str) -> {
                    switch (tag) {
                        case XML_KEYS -> documentKeyAccidentalCount =
                            DocumentValidation.parseIntOrThrow(LOG, XML_KEYS, str);
                        case XML_KEYTYPE -> documentKeyType = LegacyKeyType.parse(LOG, str);
                        case XML_NUMBER -> number = str;
                        case XML_TITLE -> title =
                            str.isEmpty() ? "Untitled" : str;
                        case XML_LYRICS -> lyrics = str;
                        case XML_INFO -> attribution = str;
                        case XML_FOOTNOTES -> footnotes = str;
                    }
                });
            }

            leaf.reset();
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
                leaf.close(qName, (tag, str) -> {
                    switch (tag) {
                        case XML_KEYS ->
                            documentKeyAccidentalCount = DocumentValidation.parseIntOrThrow(LOG, XML_KEYS, str);
                        case XML_KEYTYPE -> documentKeyType = LegacyKeyType.parse(LOG, str);
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
                        case XML_COMPOSER -> composer = str;
                        case XML_LYRICIST -> lyricist = str;
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
                        case XML_DYNAMIC_LAYOUT -> hasBeenDynamicallyLaidOut =
                            Boolean.parseBoolean(str);
                        case XML_LYRICS_DATE -> parseLyricsDate(str);
                    }
                });
            } else if (where == Where.VIEW) {
                if (viewReader == null) {
                    return;
                }

                viewReader.endElement11(qName);
            }

            leaf.reset();
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
            } else if (where == Where.SONG) {
                leaf.append(ch, start, length);
            }
        }

        private boolean accidentalsConverted() {
            return (noteReader != null && noteReader.sawLegacyAccidental())
                || (lineReader != null && lineReader.sawLegacyAccidental());
        }

        /**
         * Must be called once, after the parse. A lyric whose number names no script is dropped
         * rather than refusing the document.
         *
         * @throws SAXException when the document had no {@code <song>} or {@code <composition>}
         *     element, or its document key is damaged
         */
        LegacyBuild finishBuild() throws SAXException {
            if (noteReader == null && lineReader == null) {
                throw new SAXException("The document has no <song> or <composition> element.");
            }

            if (isLegacyAttributionFile()) {
                parseLegacyAttributionBlob();
            }

            var ctx = new MigrationContext(song);
            ctx.lines = parsedLines;
            ctx.majorVersion = majorVersion;
            ctx.minorVersion = minorVersion;
            ctx.lyrics = lyrics;

            MigrationPipeline.runPreAssembly(ctx);
            applyDocumentKeyToFirstLine(ctx.lines);

            song.setMetadata(new SongMetadata(
                title,
                number,
                place,
                PartialDate.of(year, month, day),
                composer,
                lyricist,
                lyricsSource,
                arrangement,
                unofficialTranslation,
                subtitle,
                wordsDate
            ));

            if (tempo != null) {
                song.setTempo(tempo);
            }

            song.setUnderLyrics(underLyrics);
            song.setBanglaLyrics(banglaLyrics);
            song.setTranslatedLyrics(translatedLyrics);
            song.setFootnotes(footnotes);
            ctx.lines.forEach(song::addLine);
            song.setHasBeenDynamicallyLaidOut(hasBeenDynamicallyLaidOut);
            song.setFormatVersion(CURRENT_FORMAT_VERSION);

            MigrationPipeline.runPostAssembly(ctx);

            if (accidentalsConverted()) {
                song.report(RecordedChange.accidentalsConverted());
            }

            var warnings = new ArrayList<LoadWarning>();

            if (invalidLyricsDate != null) {
                warnings.add(new LoadWarning(LoadWarning.Type.INVALID_LYRICS_DATE, invalidLyricsDate));
            }

            var marks = new PendingMarks(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

            // Only a lineReader reads lyrics and line-level marks: a v1.0 document keeps its
            // lyrics at song level, where the legacy lyrics import places them, and carries no
            // marks.
            if (lineReader != null) {
                for (var line : lineReader.linesThatDroppedLyrics()) {
                    song.report(RecordedChange.lyricDropped(line));
                }

                marks = lineReader.pendingMarks();
            }

            return new LegacyBuild(marks, getDocumentFonts(), warnings);
        }

        private DocumentFonts getDocumentFonts() {
            return viewReader != null
                ? viewReader.getDocumentFonts()
                : DocumentFonts.defaultFonts();
        }

        private boolean isLegacyAttributionFile() {
            return version().isBefore(FIRST_ATTRIBUTION_TAGS_VERSION);
        }

        private FormatVersion version() {
            return new FormatVersion(majorVersion, minorVersion);
        }

        // True when the file is so old that place/date were only in the attribution blob.
        private boolean isV10File() {
            return majorVersion == 1 && minorVersion == 0;
        }

        private void parseLegacyAttributionBlob() {
            if (attribution.isBlank()) {
                return;
            }

            var blobLines = attribution.split("\n", -1);
            @Nullable Integer byLineIndex = null;

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

            // Only v1.0 files lack separate date and place tags.
            if (!isV10File() || byLineIndex == null) {
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

        private void parseLegacyDateLine(String dateLine) {
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

            year = dateLine.trim();
        }

        /**
         * Does not throw: a bad {@code lyricsDate} must not abort the document load.
         */
        private void parseLyricsDate(String str) {
            var parsed = DateUtils.parseIsoDate(str);

            if (parsed == null) {
                LOG.warn("Corrupt document: malformed lyricsDate: '{}'", str);
                invalidLyricsDate = str;
                return;
            }

            wordsDate = parsed;
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
