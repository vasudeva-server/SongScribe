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

import java.awt.Font;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import songscribe.message.CompositionData;
import songscribe.music.Composition;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.music.Tempo;
import songscribe.ui.component.Score;
import songscribe.ui.layout2.InsertionSpacingCalculator;
import songscribe.ui.layout2.ScaleContext;
import songscribe.util.Utils;

public final class CompositionIO {

    public static final int IO_MAJOR_VERSION = 2;
    public static final int IO_MINOR_VERSION = 2;

    // version 1.0
    private static final String XML_COMPOSITION = "composition";
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

    private CompositionIO() {
    }

    public static void writeComposition(Composition c, PrintWriter pw) {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        pw.println(
            '<' +
                XML_COMPOSITION +
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
        XML.writeValue(pw, XML_KEYTYPE, c.getDefaultKeyType().name());
        TempoIO.writeTempo(c.getTempo(), pw, 2);
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

        if (!c.getLyrics().isEmpty()) {
            XML.writeValue(pw, XML_LYRICS, c.getLyrics());
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

        if (!c.getAttribution().isEmpty()) {
            XML.writeValue(pw, XML_INFO, c.getAttribution());
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
        ViewIO.writeView(c, pw);
        pw.println("  </" + XML_VIEW + '>');
        pw.println("</" + XML_COMPOSITION + '>');
    }

    public static class DocumentReader extends DefaultHandler {

        private Where where = null;

        @Nullable
        private String lastTag;

        private final StringBuilder value = new StringBuilder(200);
        private StaffElementIO.StaffElementReader noteReader = null;
        private TempoIO.TempoReader tempoReader = null;
        private LineIO.LineReader lineReader = null;
        private ViewIO.ViewReader viewReader = null;
        private int majorVersion = 0, minorVersion = 0;

        // Parsed composition data (replaces direct Composition mutation)
        private Tempo tempo = new Tempo();
        private String number = Composition.DEFAULT_NUMBER;
        private String title = Composition.DEFAULT_TITLE;
        private String place = "";
        private int month = 0;
        private int day = 0;
        private String year = "";
        private String lyrics = "";
        private String underLyrics = "";
        private String banglaLyrics = "";
        private String translatedLyrics = "";
        private String attribution = Composition.DEFAULT_ATTRIBUTION;
        private String footnotes = "";
        private boolean unofficialTranslation = false;
        private int defaultKeyAccidentalCount = Composition.DEFAULT_KEY_ACCIDENTAL_COUNT;
        private KeyType defaultKeyType = Composition.DEFAULT_KEY_TYPE;
        private double topPaddingSs = 0;
        private double attributionStartYSs = 0;
        private double rowHeightAdjustmentSs = 0;
        private double lineWidthSs = ScaleContext.getInstance().fromPixels(Score.PAGE_CONTENT_SIZE.width);
        private boolean hasBeenDynamicallyLaidOut = false;
        private final List<Line> parsedLines = new ArrayList<>();

        @Override
        public void startElement(
            String uri,
            String localName,
            String qName,
            Attributes attributes
        ) throws SAXException {
            if (where == null) {
                if (qName.equals(XML_COMPOSITION)) {
                    try {
                        var version = attributes.getValue(XML_VERSION);
                        var dotIndex = version.indexOf('.');
                        majorVersion = Integer.parseInt(
                            version.substring(0, dotIndex)
                        );
                        minorVersion = Integer.parseInt(
                            version.substring(dotIndex + 1)
                        );
                        where = Where.COMPOSITION;

                        if ((majorVersion == 1) && (minorVersion == 0)) {
                            noteReader = new StaffElementIO.StaffElementReader();
                            tempoReader = new TempoIO.TempoReader();
                        } else if (
                            (majorVersion == 1 && minorVersion >= 1) ||
                            (majorVersion == 2 && minorVersion <= 2)
                        ) {
                            lineReader = new LineIO.LineReader();
                            viewReader = new ViewIO.ViewReader();
                        } else {
                            throw new SAXException(
                                "Unsupported version number."
                            );
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
                try {
                    noteReader.startElement10(qName, attributes);
                } catch (NewLineException e) {
                    parsedLines.add(new Line());
                }
            } else if (where == Where.TEMPO_CHANGE) {
                tempoReader.startElement10(qName);
            } else if (where == Where.COMPOSITION) {
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
                lineReader.startElement11(qName, attributes);
            } else if (where == Where.VIEW) {
                viewReader.startElement11(qName);
            } else if (where == Where.TEMPO) {
                tempoReader.startElement11(qName);
            } else if (where == Where.COMPOSITION) {
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
        public void endElement(String uri, String localName, String qName) {
            if ((majorVersion == 1) && (minorVersion == 0)) {
                endElement10(qName);
            } else if ((majorVersion == 1) && (minorVersion == 1)) {
                endElement11(qName);
            } else {
                endElement12(qName);
            }
        }

        public void endElement10(String qName) {
            if (qName.equals(XML_NOTES)) {
                where = Where.COMPOSITION;
            } else if (qName.equals(XML_TEMPO_CHANGES)) {
                where = Where.COMPOSITION;
            } else if (where == Where.NOTES) {
                var note = noteReader.endElement10(qName);

                if (note != null) {
                    if (parsedLines.isEmpty()) {
                        parsedLines.add(new Line());
                    }

                    var line = parsedLines.get(parsedLines.size() - 1);
                    note.setXPosSs(ScaleContext.getInstance().toRoundedPixels(
                        InsertionSpacingCalculator.calculateAppendPositionSs(line, note)));
                    note.setUpper(Score.defaultUpperNote(note));
                    line.addElement(note);
                }
            } else if (where == Where.TEMPO_CHANGE) {
                var tc = tempoReader.endElement10(qName);

                if (tc != null) {
                    if (tempoReader.getPos10() == 0) {
                        tempo = tc;
                    } else {
                        var firstElementInLine = 0;

                        for (var l = 0; l < parsedLines.size(); l++) {
                            var line = parsedLines.get(l);

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
            } else if (where == Where.COMPOSITION) {
                if (qName.equals(lastTag)) {
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
                var lastLine = parsedLines.get(parsedLines.size() - 1);

                for (var i = 0; i < lastLine.elementCount(); i++) {
                    if (lastLine.getElement(i).getType().isGraceNote()) {
                        lastLine.getElement(i).setUpper(true);
                    }
                }
            }
        }

        public void endElement12(String qName) {
            if (qName.equals(XML_LINES)) {
                where = Where.COMPOSITION;
            } else if (qName.equals(XML_VIEW)) {
                where = Where.COMPOSITION;
            } else if (where == Where.LINES) {
                var l = lineReader.endElement11(qName);

                if (l != null) {
                    parsedLines.add(l);
                }
            } else if (where == Where.TEMPO) {
                var t = tempoReader.endElement11(qName);

                if (t != null) {
                    tempo = t;
                    where = Where.COMPOSITION;
                }
            } else if (where == Where.COMPOSITION) {
                if (qName.equals(lastTag)) {
                    var str = value.toString();
                    var useDouble = majorVersion >= 2 && minorVersion >= 1;

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
                            useDouble ? Double.parseDouble(str) : Integer.parseInt(str);
                        case XML_INFO_STARTY -> attributionStartYSs =
                            useDouble ? Double.parseDouble(str) : Integer.parseInt(str);
                        case XML_ROW_HEIGHT -> rowHeightAdjustmentSs =
                            useDouble ? Double.parseDouble(str) : Integer.parseInt(str);
                        case XML_LINE_WIDTH -> lineWidthSs =
                            useDouble ? Double.parseDouble(str) : Integer.parseInt(str);
                        case XML_DYNAMIC_LAYOUT -> hasBeenDynamicallyLaidOut =
                            Boolean.parseBoolean(str);
                    }
                }
            } else if (where == Where.VIEW) {
                viewReader.endElement11(qName);
            }

            value.delete(0, value.length());
            lastTag = null;
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (where == Where.LINES) {
                lineReader.characters(ch, start, length);
            } else if (where == Where.VIEW) {
                viewReader.characters(ch, start, length);
            } else if (where == Where.NOTES) {
                noteReader.characters(ch, start, length);
            } else if (where == Where.TEMPO_CHANGE) {
                tempoReader.characters(ch, start, length);
            } else if (where == Where.TEMPO) {
                tempoReader.characters(ch, start, length);
            } else if ((where == Where.COMPOSITION) && (lastTag != null)) {
                value.append(ch, start, length);
            }
        }

        public Composition getComposition() {
            // Determine format version for migration
            int formatVersion = majorVersion >= 2 ? 2 : 1;

            // Migrate from legacy format (IntervalSets, inline Note attachments)
            // to new format (RangeElements, Attachment objects).
            FormatMigrator.migrate(parsedLines, formatVersion);

            // After migration, format version is always 2
            formatVersion = 2;

            // For pre-v2.1 files, convert pixel-based positions to staff-space units.
            // v2.1+ files already store values in staff-space units.
            if (majorVersion < 2 || (majorVersion == 2 && minorVersion < 1)) {
                var pps = ScaleContext.DEFAULT_PIXELS_PER_STAFF_SPACE;

                // Composition-level pixel-to-ss conversion
                topPaddingSs /= pps;
                lineWidthSs /= pps;
                rowHeightAdjustmentSs /= pps;
                attributionStartYSs /= pps;

                // Line-level pixel-to-ss conversion
                FormatMigrator.migratePixelsToStaffSpace(parsedLines);
            }

            // Extract fonts from ViewReader (null for v1.0 files without View section)
            Font titleFont = viewReader != null ? viewReader.getTitleFont() : null;
            Font lyricsFont = viewReader != null ? viewReader.getLyricsFont() : null;
            Font attributionFont = viewReader != null ? viewReader.getAttributionFont() : null;
            Font annotationFont = viewReader != null ? viewReader.getAnnotationFont() : null;

            // Legacy fallback: if topPadding wasn't set in file, calculate initial value.
            // Layout calculation will recalculate this properly, but this provides
            // a reasonable default for any code that accesses topPadding before layout.
            if (topPaddingSs == 0) {
                var tf = titleFont != null ? titleFont : defaultFontFromPrefs("titleFont", "titleFontSize");
                var af = attributionFont != null ? attributionFont : defaultFontFromPrefs("attributionFont", "attributionFontSize");
                topPaddingSs = ((2 * tf.getSize()) +
                    (Utils.lineCount(attribution) * af.getSize())) -
                    ScaleContext.getInstance().toRoundedPixels(2.0);
            }

            var data = new CompositionData(
                tempo,
                number,
                title,
                place,
                month,
                day,
                year,
                lyrics,
                underLyrics,
                banglaLyrics,
                translatedLyrics,
                attribution,
                footnotes,
                unofficialTranslation,
                defaultKeyAccidentalCount,
                defaultKeyType,
                titleFont,
                lyricsFont,
                attributionFont,
                annotationFont,
                topPaddingSs,
                attributionStartYSs,
                rowHeightAdjustmentSs,
                lineWidthSs,
                parsedLines,
                hasBeenDynamicallyLaidOut,
                formatVersion
            );

            // Use the loading constructor to avoid the wasted work of the
            // no-arg constructor (default line, attributionStartY calculation).
            return new Composition(data);
        }

        private static Font defaultFontFromPrefs(String nameKey, String sizeKey) {
            var prefs = songscribe.prefs.Prefs.getInstance();
            return songscribe.util.MyFontUtils.createFont(
                prefs.getString(nameKey), prefs.getInt(sizeKey));
        }

        private enum Where {
            COMPOSITION,
            LINES,
            VIEW,
            NOTES,
            TEMPO,
            TEMPO_CHANGE,
        }
    }
}
