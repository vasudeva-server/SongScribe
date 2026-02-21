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

import org.jetbrains.annotations.Nullable;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import songscribe.music.Composition;
import songscribe.music.KeyType;
import songscribe.music.Line;
import songscribe.ui.component.IMainFrame;
import songscribe.ui.component.Score;
import songscribe.ui.layout2.InsertionSpacingCalculator;

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

        if (!c.getNumber().isEmpty()) {
            XML.writeValue(pw, XML_NUMBER, c.getNumber());
        }

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
                Double.toString(c.getTopPadding())
            );
        }

        XML.writeValue(
            pw,
            XML_INFO_STARTY,
            Double.toString(c.getAttributionStartY())
        );

        if (c.getRowHeightAdjustment() != 0) {
            XML.writeValue(
                pw,
                XML_ROW_HEIGHT,
                Double.toString(c.getRowHeightAdjustment())
            );
        }

        // Line width in staff-space units
        XML.writeValue(pw, XML_LINE_WIDTH, Double.toString(c.getLineWidth()));

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
        private NoteIO.NoteReader noteReader = null;
        private TempoIO.TempoReader tempoReader = null;
        private LineIO.LineReader lineReader = null;
        private ViewIO.ViewReader viewReader = null;
        private Composition composition = null;
        private int majorVersion = 0, minorVersion = 0;
        private final IMainFrame mainFrame;

        public DocumentReader(IMainFrame mainFrame) {
            this.mainFrame = mainFrame;
        }

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
                        composition = new Composition(mainFrame);
                        composition.setTopPadding(0, false);
                        composition.removeLine(0);
                        where = Where.COMPOSITION;

                        if ((majorVersion == 1) && (minorVersion == 0)) {
                            noteReader = new NoteIO.NoteReader();
                            tempoReader = new TempoIO.TempoReader();
                        } else if ((majorVersion == 1) && (minorVersion == 1)) {
                            lineReader = new LineIO.LineReader();
                            viewReader = new ViewIO.ViewReader(
                                mainFrame.getProfileManager()
                            );
                        } else if ((majorVersion == 1) && (minorVersion == 2)) {
                            lineReader = new LineIO.LineReader();
                            viewReader = new ViewIO.ViewReader(
                                mainFrame.getProfileManager()
                            );
                        } else if ((majorVersion == 1) && (minorVersion == 3)) {
                            lineReader = new LineIO.LineReader();
                            viewReader = new ViewIO.ViewReader(
                                mainFrame.getProfileManager()
                            );
                        } else if ((majorVersion == 1) && (minorVersion == 4)) {
                            lineReader = new LineIO.LineReader();
                            viewReader = new ViewIO.ViewReader(
                                mainFrame.getProfileManager()
                            );
                        } else if ((majorVersion == 2) && (minorVersion == 0)) {
                            lineReader = new LineIO.LineReader();
                            viewReader = new ViewIO.ViewReader(
                                mainFrame.getProfileManager()
                            );
                        } else if ((majorVersion == 2) && (minorVersion == 1)) {
                            lineReader = new LineIO.LineReader();
                            viewReader = new ViewIO.ViewReader(
                                mainFrame.getProfileManager()
                            );
                        } else if ((majorVersion == 2) && (minorVersion == 2)) {
                            lineReader = new LineIO.LineReader();
                            viewReader = new ViewIO.ViewReader(
                                mainFrame.getProfileManager()
                            );
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
                } else if ((majorVersion == 1) && (minorVersion == 1)) {
                    startElement11(uri, localName, qName, attributes);
                } else if ((majorVersion == 1) && (minorVersion == 2)) {
                    startElement12(uri, localName, qName, attributes);
                } else if ((majorVersion == 1) && (minorVersion == 3)) {
                    startElement13(uri, localName, qName, attributes);
                } else if ((majorVersion == 1) && (minorVersion == 4)) {
                    startElement14(uri, localName, qName, attributes);
                } else if ((majorVersion == 2) && (minorVersion == 0)) {
                    startElement20(uri, localName, qName, attributes);
                } else if ((majorVersion == 2) && (minorVersion == 1)) {
                    startElement21(uri, localName, qName, attributes);
                } else if ((majorVersion == 2) && (minorVersion == 2)) {
                    startElement21(uri, localName, qName, attributes);
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
                    mainFrame
                        .getScore()
                        .drawWidthIfWiderLine(
                            composition.getLine(composition.lineCount() - 1),
                            true
                        );
                    composition.addLine(new Line());
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

        public void startElement12(
            String uri,
            String localName,
            String qName,
            Attributes attributes
        ) {
            // No changes
            startElement11(uri, localName, qName, attributes);
        }

        public void startElement13(
            String uri,
            String localName,
            String qName,
            Attributes attributes
        ) {
            // No changes from 1.2
            startElement12(uri, localName, qName, attributes);
        }

        public void startElement14(
            String uri,
            String localName,
            String qName,
            Attributes attributes
        ) {
            // No changes from 1.3
            startElement13(uri, localName, qName, attributes);
        }

        public void startElement20(
            String uri,
            String localName,
            String qName,
            Attributes attributes
        ) {
            // No changes from 1.4
            startElement14(uri, localName, qName, attributes);
        }

        public void startElement21(
            String uri,
            String localName,
            String qName,
            Attributes attributes
        ) {
            // No changes from 2.0
            startElement20(uri, localName, qName, attributes);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            if ((majorVersion == 1) && (minorVersion == 0)) {
                endElement10(qName);
            } else if ((majorVersion == 1) && (minorVersion == 1)) {
                endElement11(qName);
            } else if ((majorVersion == 1) && (minorVersion == 2)) {
                endElement12(qName);
            } else if ((majorVersion == 1) && (minorVersion == 3)) {
                endElement13(qName);
            } else if ((majorVersion == 1) && (minorVersion == 4)) {
                endElement14(qName);
            } else if ((majorVersion == 2) && (minorVersion == 0)) {
                endElement20(qName);
            } else if ((majorVersion == 2) && (minorVersion == 1)) {
                endElement21(qName);
            } else if ((majorVersion == 2) && (minorVersion == 2)) {
                endElement21(qName);
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
                    if (composition.lineCount() == 0) {
                        composition.addLine(new Line());
                    }

                    var line = composition.getLine(composition.lineCount() - 1);
                    note.setXPosSs((int) Math.round(
                        InsertionSpacingCalculator.calculateAppendPositionSs(line, note)));
                    note.setUpper(Score.defaultUpperNote(note));
                    line.addNote(note);
                }
            } else if (where == Where.TEMPO_CHANGE) {
                var tc = tempoReader.endElement10(qName);

                if (tc != null) {
                    if (tempoReader.getPos10() == 0) {
                        composition.setTempo(tc);
                    } else {
                        var firstNoteInLine = 0;

                        for (var l = 0; l < composition.lineCount(); l++) {
                            var line = composition.getLine(l);

                            if (
                                tempoReader.getPos10() <
                                    (firstNoteInLine + line.noteCount())
                            ) {
                                line
                                    .getNote(
                                        tempoReader.getPos10() - firstNoteInLine
                                    )
                                    .setTempoChange(tc);
                                break;
                            }

                            firstNoteInLine += line.noteCount() + 1;
                        }
                    }
                }
            } else if (where == Where.COMPOSITION) {
                if (qName.equals(XML_COMPOSITION)) {
                    composition.setModified(true);
                } else if (qName.equals(lastTag)) {
                    var str = value.toString();

                    switch (lastTag) {
                        case XML_KEYS -> composition.setDefaultKeyAccidentalCount(
                            Integer.parseInt(str)
                        );
                        case XML_KEYTYPE -> composition.setDefaultKeyType(
                            KeyType.valueOf(str)
                        );
                        case XML_NUMBER -> composition.setNumber(str);
                        case XML_TITLE -> composition.setTitle(
                            str.isEmpty() ? "Untitled" : str
                        );
                        case XML_LYRICS -> composition.setLyrics(str);
                        case XML_INFO -> composition.setAttribution(str);
                        case XML_FOOTNOTES -> composition.setFootnotes(str);
                    }
                }
            }

            value.delete(0, value.length());
            lastTag = null;
        }

        public void endElement11(String qName) {
            // No change except end the end of the line reading we set
            // the quaver notes to upper position.
            endElement12(qName);

            if ((where == Where.LINES) && (composition.lineCount() > 0)) {
                var lastLine = composition.getLine(composition.lineCount() - 1);

                for (var i = 0; i < lastLine.noteCount(); i++) {
                    if (lastLine.getNote(i).getNoteType().isGraceNote()) {
                        lastLine.getNote(i).setUpper(true);
                    }
                }
            }
        }

        public void endElement12(String qName) {
            if (qName.equals(XML_LINES)) {
                where = Where.COMPOSITION;
            } else if (qName.equals(XML_VIEW)) {
                viewReader.setAttributes(composition);
                where = Where.COMPOSITION;
            } else if (where == Where.LINES) {
                var l = lineReader.endElement11(qName);

                if (l != null) {
                    composition.addLine(l);
                }
            } else if (where == Where.TEMPO) {
                var t = tempoReader.endElement11(qName);

                if (t != null) {
                    composition.setTempo(t);
                    where = Where.COMPOSITION;
                }
            } else if (where == Where.COMPOSITION) {
                if (qName.equals(lastTag)) {
                    var str = value.toString();

                    switch (lastTag) {
                        case XML_KEYS -> composition.setDefaultKeyAccidentalCount(
                            Integer.parseInt(str)
                        );
                        case XML_KEYTYPE -> composition.setDefaultKeyType(
                            KeyType.valueOf(str)
                        );
                        case XML_NUMBER -> composition.setNumber(str);
                        case XML_TITLE -> composition.setTitle(
                            str.isEmpty() ? "Untitled" : str
                        );
                        case XML_PLACE -> composition.setPlace(str);
                        case XML_YEAR -> composition.setYear(str);
                        case XML_MONTH -> composition.setMonth(
                            Integer.parseInt(str)
                        );
                        case XML_DAY -> composition.setDay(
                            Integer.parseInt(str)
                        );
                        case XML_LYRICS -> composition.setLyrics(str);
                        case XML_UNDERLYRICS -> composition.setUnderLyrics(str);
                        case XML_BANGLA_LYRICS -> composition.setBanglaLyrics(
                            str
                        );
                        case XML_TRANSLATED_LYRICS -> composition.setTranslatedLyrics(
                            str
                        );
                        case XML_FOOTNOTES -> composition.setFootnotes(str);
                        case XML_INFO -> composition.setAttribution(str);
                        case XML_TOP_SPACE -> composition.setTopPadding(
                            Integer.parseInt(str),
                            false
                        );
                        case XML_INFO_STARTY -> composition.setAttributionStartY(
                            Integer.parseInt(str)
                        );
                        case XML_ROW_HEIGHT -> composition.setRowHeightAdjustment(
                            Integer.parseInt(str)
                        );
                        case XML_LINE_WIDTH -> {
                            // The line width is stored as logical pixels in the UI resolution
                            composition.setLineWidth(Integer.parseInt(str));
                        }
                    }
                }
            } else if (where == Where.VIEW) {
                viewReader.endElement11(qName);
            }

            value.delete(0, value.length());
            lastTag = null;
        }

        public void endElement13(String qName) {
            if (qName.equals(XML_LINES)) {
                where = Where.COMPOSITION;
            } else if (qName.equals(XML_VIEW)) {
                viewReader.setAttributes(composition);
                where = Where.COMPOSITION;
            } else if (where == Where.LINES) {
                var l = lineReader.endElement11(qName);

                if (l != null) {
                    composition.addLine(l);
                }
            } else if (where == Where.TEMPO) {
                var t = tempoReader.endElement11(qName);

                if (t != null) {
                    composition.setTempo(t);
                    where = Where.COMPOSITION;
                }
            } else if (where == Where.COMPOSITION) {
                if (qName.equals(lastTag)) {
                    var str = value.toString();

                    switch (lastTag) {
                        case XML_KEYS -> composition.setDefaultKeyAccidentalCount(
                            Integer.parseInt(str)
                        );
                        case XML_KEYTYPE -> composition.setDefaultKeyType(
                            KeyType.valueOf(str)
                        );
                        case XML_NUMBER -> composition.setNumber(str);
                        case XML_TITLE -> composition.setTitle(
                            str.isEmpty() ? "Untitled" : str
                        );
                        case XML_PLACE -> composition.setPlace(str);
                        case XML_YEAR -> composition.setYear(str);
                        case XML_MONTH -> composition.setMonth(
                            Integer.parseInt(str)
                        );
                        case XML_DAY -> composition.setDay(
                            Integer.parseInt(str)
                        );
                        case XML_LYRICS -> composition.setLyrics(str);
                        case XML_UNDERLYRICS -> composition.setUnderLyrics(str);
                        case XML_BANGLA_LYRICS -> composition.setBanglaLyrics(
                            str
                        );
                        case XML_TRANSLATED_LYRICS -> composition.setTranslatedLyrics(
                            str
                        );
                        case XML_UNOFFICIAL_TRANSLATION -> composition.setUnofficialTranslation(
                            Boolean.parseBoolean(str)
                        );
                        case XML_FOOTNOTES -> composition.setFootnotes(str);
                        case XML_INFO -> composition.setAttribution(str);
                        case XML_TOP_SPACE -> composition.setTopPadding(
                            Integer.parseInt(str),
                            false
                        );
                        case XML_INFO_STARTY -> composition.setAttributionStartY(
                            Integer.parseInt(str)
                        );
                        case XML_ROW_HEIGHT -> composition.setRowHeightAdjustment(
                            Integer.parseInt(str)
                        );
                        case XML_LINE_WIDTH -> {
                            // The line width is stored as logical pixels in the UI resolution
                            composition.setLineWidth(Integer.parseInt(str));
                        }
                    }
                }
            } else if (where == Where.VIEW) {
                viewReader.endElement11(qName);
            }

            value.delete(0, value.length());
            lastTag = null;
        }

        public void endElement14(String qName) {
            // No changes from 1.3
            endElement13(qName);
        }

        public void endElement20(String qName) {
            if (qName.equals(XML_LINES)) {
                where = Where.COMPOSITION;
            } else if (qName.equals(XML_VIEW)) {
                viewReader.setAttributes(composition);
                where = Where.COMPOSITION;
            } else if (where == Where.LINES) {
                var l = lineReader.endElement11(qName);

                if (l != null) {
                    composition.addLine(l);
                }
            } else if (where == Where.TEMPO) {
                var t = tempoReader.endElement11(qName);

                if (t != null) {
                    composition.setTempo(t);
                    where = Where.COMPOSITION;
                }
            } else if (where == Where.COMPOSITION) {
                if (qName.equals(lastTag)) {
                    var str = value.toString();

                    switch (lastTag) {
                        case XML_KEYS -> composition.setDefaultKeyAccidentalCount(
                            Integer.parseInt(str)
                        );
                        case XML_KEYTYPE -> composition.setDefaultKeyType(
                            KeyType.valueOf(str)
                        );
                        case XML_NUMBER -> composition.setNumber(str);
                        case XML_TITLE -> composition.setTitle(
                            str.isEmpty() ? "Untitled" : str
                        );
                        case XML_PLACE -> composition.setPlace(str);
                        case XML_YEAR -> composition.setYear(str);
                        case XML_MONTH -> composition.setMonth(
                            Integer.parseInt(str)
                        );
                        case XML_DAY -> composition.setDay(
                            Integer.parseInt(str)
                        );
                        case XML_LYRICS -> composition.setLyrics(str);
                        case XML_UNDERLYRICS -> composition.setUnderLyrics(str);
                        case XML_BANGLA_LYRICS -> composition.setBanglaLyrics(
                            str
                        );
                        case XML_TRANSLATED_LYRICS -> composition.setTranslatedLyrics(
                            str
                        );
                        case XML_UNOFFICIAL_TRANSLATION -> composition.setUnofficialTranslation(
                            Boolean.parseBoolean(str)
                        );
                        case XML_FOOTNOTES -> composition.setFootnotes(str);
                        case XML_INFO -> composition.setAttribution(str);
                        case XML_TOP_SPACE -> composition.setTopPadding(
                            Integer.parseInt(str),
                            false
                        );
                        case XML_INFO_STARTY -> composition.setAttributionStartY(
                            Integer.parseInt(str)
                        );
                        case XML_ROW_HEIGHT -> composition.setRowHeightAdjustment(
                            Integer.parseInt(str)
                        );
                        case XML_LINE_WIDTH -> {
                            // The line width is stored as logical pixels in the UI resolution
                            composition.setLineWidth(Integer.parseInt(str));
                        }
                        case XML_DYNAMIC_LAYOUT -> composition.setHasBeenDynamicallyLaidOut(
                            Boolean.parseBoolean(str)
                        );
                    }
                }
            } else if (where == Where.VIEW) {
                viewReader.endElement11(qName);
            }

            value.delete(0, value.length());
            lastTag = null;
        }

        public void endElement21(String qName) {
            if (qName.equals(XML_LINES)) {
                where = Where.COMPOSITION;
            } else if (qName.equals(XML_VIEW)) {
                viewReader.setAttributes(composition);
                where = Where.COMPOSITION;
            } else if (where == Where.LINES) {
                var l = lineReader.endElement11(qName);

                if (l != null) {
                    composition.addLine(l);
                }
            } else if (where == Where.TEMPO) {
                var t = tempoReader.endElement11(qName);

                if (t != null) {
                    composition.setTempo(t);
                    where = Where.COMPOSITION;
                }
            } else if (where == Where.COMPOSITION) {
                if (qName.equals(lastTag)) {
                    var str = value.toString();

                    switch (lastTag) {
                        case XML_KEYS -> composition.setDefaultKeyAccidentalCount(
                            Integer.parseInt(str)
                        );
                        case XML_KEYTYPE -> composition.setDefaultKeyType(
                            KeyType.valueOf(str)
                        );
                        case XML_NUMBER -> composition.setNumber(str);
                        case XML_TITLE -> composition.setTitle(
                            str.isEmpty() ? "Untitled" : str
                        );
                        case XML_PLACE -> composition.setPlace(str);
                        case XML_YEAR -> composition.setYear(str);
                        case XML_MONTH -> composition.setMonth(
                            Integer.parseInt(str)
                        );
                        case XML_DAY -> composition.setDay(
                            Integer.parseInt(str)
                        );
                        case XML_LYRICS -> composition.setLyrics(str);
                        case XML_UNDERLYRICS -> composition.setUnderLyrics(str);
                        case XML_BANGLA_LYRICS -> composition.setBanglaLyrics(
                            str
                        );
                        case XML_TRANSLATED_LYRICS -> composition.setTranslatedLyrics(
                            str
                        );
                        case XML_UNOFFICIAL_TRANSLATION -> composition.setUnofficialTranslation(
                            Boolean.parseBoolean(str)
                        );
                        case XML_FOOTNOTES -> composition.setFootnotes(str);
                        case XML_INFO -> composition.setAttribution(str);
                        case XML_TOP_SPACE -> composition.setTopPadding(
                            Double.parseDouble(str),
                            false
                        );
                        case XML_INFO_STARTY -> composition.setAttributionStartY(
                            Double.parseDouble(str)
                        );
                        case XML_ROW_HEIGHT -> composition.setRowHeightAdjustment(
                            Double.parseDouble(str)
                        );
                        case XML_LINE_WIDTH -> composition.setLineWidth(
                            Double.parseDouble(str)
                        );
                        case XML_DYNAMIC_LAYOUT -> composition.setHasBeenDynamicallyLaidOut(
                            Boolean.parseBoolean(str)
                        );
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
            // Legacy fallback: if topPadding wasn't set in file, calculate initial value.
            // Layout calculation will recalculate this properly, but this provides
            // a reasonable default for any code that accesses topPadding before layout.
            if (composition.getTopPadding() == 0) {
                //noinspection deprecation
                composition.recalcTopPadding();
            }

            // For legacy files (pre-2.0), xPos values were absolute positions.
            // Reset them to 0 since layout will recalculate positions dynamically.
            if (!composition.hasBeenDynamicallyLaidOut()) {
                for (var line : composition.getLines()) {
                    for (var i = 0; i < line.noteCount(); i++) {
                        // line.getNote(i).setXPosSs(0);
                    }
                }
            }

            // Mark the composition's format version so FormatMigrator skips
            // migrations that have already been applied in v2.x files.
            if (majorVersion >= 2) {
                composition.setFormatVersion(2);
            }

            // Migrate from legacy format (IntervalSets, inline Note attachments)
            // to new format (RangeElements, Attachment objects).
            // This populates the new data structures from the legacy data.
            FormatMigrator.migrate(composition);

            // For pre-v2.1 files, convert pixel-based positions to staff-space units.
            // v2.1+ files already store values in staff-space units.
            if (majorVersion < 2 || (majorVersion == 2 && minorVersion < 1)) {
                FormatMigrator.migratePixelsToStaffSpace(composition);
            }

            // v2.1 → v2.2: No migration needed. stemDirectionAuto defaults to true,
            // so absence of <stemDirectionAuto/> in existing v2.1 files is correct.
            // Re-saving a v2.1 file stamps it as v2.2. See FormatMigrator Javadoc.

            return composition;
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
