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
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import songscribe.Strings;
import songscribe.message.SongData;
import songscribe.font.DocumentFonts;
import songscribe.font.DocumentFontsHolder;
import songscribe.music.Song;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.Tempo;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.component.ScoreView;
import songscribe.ui.layout.InsertionSpacingCalculator;
import songscribe.ui.layout.PageModel;
import songscribe.ui.layout.ScaleContext;
import songscribe.util.Utils;

public final class SongIO {

    public static final int IO_MAJOR_VERSION = 2;
    public static final int IO_MINOR_VERSION = 7;

    // Minor version at which per-note <lyric> serialization was introduced.
    // Files saved before this version carry a legacy <lyrics> blob that must
    // be imported by LegacyLyricsImporter instead.
    private static final int PER_NOTE_LYRIC_VERSION = 6;

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
    private static final String XML_TOP_SPACE = "topspace";
    private static final String XML_LINE_WIDTH = "linewidth";
    private static final String XML_ROW_HEIGHT = "rowheight";
    private static final String XML_PLACE = "place";
    private static final String XML_YEAR = "year";
    private static final String XML_MONTH = "month";
    private static final String XML_DAY = "day";
    private static final String XML_INFO_STARTY = "rightinfostarty";

    // version 1.3
    private static final String XML_UNOFFICIAL_TRANSLATION =
        "unofficialTranslation";

    // version 1.4
    private static final String XML_DYNAMIC_LAYOUT = "dynamicLayout";

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

        var attribution = c.getAttribution();

        if (!attribution.isEmpty()) {
            XML.writeValue(pw, XML_INFO, attribution);
        }

        if (!c.getFootnotes().isEmpty()) {
            XML.writeValue(pw, XML_FOOTNOTES, c.getFootnotes());
        }

        if (c.userSetTopPadding()) {
            XML.writeValue(
                pw,
                XML_TOP_SPACE,
                Double.toString(c.getTopPaddingSs())
            );
        }

        XML.writeValue(
            pw,
            XML_INFO_STARTY,
            Double.toString(c.getAttributionStartYSs())
        );

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
        private String year = "";
        private String lyrics = "";
        private String underLyrics = "";
        private String banglaLyrics = "";
        private String translatedLyrics = "";
        private String attribution = Strings.get(Strings.SONG_DEFAULT_ATTRIBUTION);
        private String footnotes = "";
        private boolean unofficialTranslation = false;
        private int defaultKeyAccidentalCount = Song.DEFAULT_KEY_ACCIDENTAL_COUNT;
        private KeyType defaultKeyType = Song.DEFAULT_KEY_TYPE;
        private double topPaddingSs = 0;
        private double attributionStartYSs = 0;
        private double rowHeightAdjustmentSs = 0;
        private double lineWidthSs = PageModel.getDefaultLineWidthSs();
        private boolean hasBeenDynamicallyLaidOut = false;
        private final List<Line> parsedLines = new ArrayList<>();

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
                            (majorVersion == 2 && minorVersion <= 7)
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
        ) {
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

        public void endElement10(String qName) {
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
                    note.setXOffsetPx(ScaleContext.getInstance().ssToRoundedPx(
                        InsertionSpacingCalculator.calculateAppendPositionSs(line, note, null)));
                    note.setUpper(ScoreView.defaultUpperNote(note));
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
                                line
                                    .getElement(
                                        tempoReader.getPos10() - firstElementInLine
                                    )
                                    .setTempoChange(tc);
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

        public void endElement11(String qName) {
            // No change except at the end of the line reading we set
            // the quaver notes to upper position.
            endElement12(qName);

            if ((where == Where.LINES) && !parsedLines.isEmpty()) {
                var lastLine = parsedLines.getLast();

                for (var i = 0; i < lastLine.elementCount(); i++) {
                    if (lastLine.getElement(i).getType().isGraceNote()) {
                        lastLine.getElement(i).setUpper(true);
                    }
                }
            }
        }

        public void endElement12(String qName) {
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
                        case XML_PLACE -> place = str;
                        case XML_YEAR -> year = str;
                        case XML_MONTH -> month =
                            Integer.parseInt(str);
                        case XML_DAY -> day =
                            Integer.parseInt(str);
                        case XML_LYRICS -> lyrics = str;
                        case XML_UNDERLYRICS -> underLyrics = str;
                        case XML_BANGLA_LYRICS -> banglaLyrics = str;
                        case XML_TRANSLATED_LYRICS -> translatedLyrics = str;
                        case XML_UNOFFICIAL_TRANSLATION -> unofficialTranslation =
                            Boolean.parseBoolean(str);
                        case XML_FOOTNOTES -> footnotes = str;
                        case XML_INFO -> attribution = str;
                        case XML_TOP_SPACE -> topPaddingSs =
                            parseVersionedDouble(str);
                        case XML_INFO_STARTY -> attributionStartYSs =
                            parseVersionedDouble(str);
                        case XML_ROW_HEIGHT -> rowHeightAdjustmentSs =
                            parseVersionedDouble(str);
                        case XML_LINE_WIDTH -> lineWidthSs =
                            parseVersionedDouble(str);
                        case XML_DYNAMIC_LAYOUT -> hasBeenDynamicallyLaidOut =
                            Boolean.parseBoolean(str);
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

        public Song getSong() {
            // Determine format version for migration
            var formatVersion = majorVersion >= 2 ? 2 : 1;

            // Migrate from legacy format (IntervalSets, inline Note attachments)
            // to new format (RangeElements, Attachment objects).
            FormatMigrator.migrate(parsedLines, formatVersion);

            // After migration, format version is always 2
            formatVersion = 2;

            // Migrate pre-2.3 annotation-based dynamics to DynamicAttachment.
            // Runs for all files saved before v2.3 introduced native serialization.
            if (majorVersion < 2 || (majorVersion == 2 && minorVersion < 3)) {
                FormatMigrator.migrateAnnotationDynamics(parsedLines);
            }

            // Enforce the terminal invariant for all pre-v2.4 files.
            if (majorVersion < 2 || (majorVersion == 2 && minorVersion < 4)) {
                FormatMigrator.migrateFinalTerminal(parsedLines);
            }

            // For pre-v2.1 files, convert pixel-based positions to staff-space units.
            // v2.1+ files already store values in staff-space units.
            if (majorVersion < 2 || (majorVersion == 2 && minorVersion < 1)) {
                var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;

                // Song-level pixel-to-ss conversion
                topPaddingSs /= pps;
                lineWidthSs /= pps;
                rowHeightAdjustmentSs /= pps;
                attributionStartYSs /= pps;

                // Line-level pixel-to-ss conversion
                FormatMigrator.migratePixelsToStaffSpace(parsedLines);
            }

            // Legacy fallback: if topPadding wasn't set in file, calculate initial value.
            // Layout calculation will recalculate this properly, but this provides
            // a reasonable default for any code that accesses topPadding before layout.
            if (topPaddingSs == 0) {
                var titleSize = Prefs.getInt(PrefsKey.TITLE_FONT_SIZE);
                var attributionSize = Prefs.getInt(PrefsKey.ATTRIBUTION_FONT_SIZE);
                topPaddingSs = ((2 * titleSize) +
                    (Utils.lineCount(attribution) * attributionSize)) -
                    ScaleContext.getInstance().ssToRoundedPx(2.0);
            }

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
                attribution,
                footnotes,
                unofficialTranslation,
                defaultKeyAccidentalCount,
                defaultKeyType,
                topPaddingSs,
                attributionStartYSs,
                rowHeightAdjustmentSs,
                lineWidthSs,
                parsedLines,
                hasBeenDynamicallyLaidOut,
                formatVersion
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
            } finally {
                song.endSuspendMutationTracking();
            }

            // Populate per-note Lyric records from the captured legacy `<lyrics>` blob.
            // Only fires for files saved before per-note <lyric> serialization was
            // introduced; new-format files carry per-note data directly on each element.
            if (!lyrics.isBlank() &&
                (majorVersion < 2 || (majorVersion == 2 && minorVersion < PER_NOTE_LYRIC_VERSION))) {
                LegacyLyricsImporter.importLegacyLyrics(song.getLines(), lyrics);
            }

            // Normalize stored syllabic values to match relation-chain derivation. Required
            // for legacy files that may carry only locally-consistent values from the read
            // path or no <syllabic> at all (LegacyLyricsImporter / pre-syllabic XML).
            for (var line : song.getLines()) {
                line.backfillSyllabic();
            }

            return song;
        }

        /**
         * Returns the document fonts parsed from the file's {@code <view>} block.
         * For v1.0 files (which have no {@code <view>} section) and v1.1+ files
         * whose block omits a role, the missing roles fall through to
         * {@link DocumentFonts#defaultsFromPrefs()}.
         * <p>
         * Must be called after parsing completes; the result is independent of
         * {@link #getSong()} and is the authoritative font source for the
         * load path.
         */
        public DocumentFonts getDocumentFonts() {
            return viewReader != null
                ? viewReader.getDocumentFonts()
                : DocumentFonts.defaultsFromPrefs();
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
