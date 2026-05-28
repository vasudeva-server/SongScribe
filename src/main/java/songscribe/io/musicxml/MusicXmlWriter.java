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

import java.io.PrintWriter;
import org.jspecify.annotations.Nullable;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.io.XML;

public final class MusicXmlWriter {

    // Provisional value; final value justified in Phase 3.
    private static final int DIVISIONS = 480;

    // Measure numbering starts at 1 (MusicXML spec requires positive integers).
    private static final int FIRST_MEASURE_NUMBER = 1;

    private MusicXmlWriter() {}

    public static void writeSong(Song song, PrintWriter pw) {
        pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");

        XML.resetIndent();
        XML.writeBeginTag(pw, MusicXmlTags.SCORE_PARTWISE, MusicXmlTags.ATTR_VERSION, MusicXmlTags.VERSION_VALUE);
        XML.indent();

        XML.writeBeginTag(pw, MusicXmlTags.PART_LIST);
        XML.indent();

        // <score-part> and its child are emitted inline on one line.
        XML.printIndent(pw);
        pw.println("<" + MusicXmlTags.SCORE_PART + " " + MusicXmlTags.ATTR_ID + "=\"" + MusicXmlTags.PART_ID + "\"><part-name></part-name></" + MusicXmlTags.SCORE_PART + ">");

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PART_LIST);

        XML.writeBeginTag(pw, MusicXmlTags.PART, MusicXmlTags.ATTR_ID, MusicXmlTags.PART_ID);
        XML.indent();

        if (song.lineCount() == 0) {
            writeEmptySongMeasure(song, pw);
        } else {
            writeLineDrivenMeasures(song, pw);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.PART);

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.SCORE_PARTWISE);
    }

    /**
     * Empty-song fallback: a single attributes-only measure with no
     * {@code <print>} and no {@code <barline>}, matching Phase 1 behavior.
     */
    private static void writeEmptySongMeasure(Song song, PrintWriter pw) {
        openMeasure(pw, FIRST_MEASURE_NUMBER);
        writeAttributes(song, pw);
        closeMeasure(pw);
    }

    /**
     * Line-driven emission: each {@link Line} contributes one or more
     * {@code <measure>}s, segmented at every barline/repeat element and at
     * every line break.
     */
    private static void writeLineDrivenMeasures(Song song, PrintWriter pw) {
        int measureNumber = 0;

        for (Line line : song.getLines()) {
            // Open the line-starting measure. Every such measure carries a system-
            // break marker so the reader has one uniform rule:
            // new-system="yes" always starts a new line.
            measureNumber++;
            openMeasure(pw, measureNumber);
            writePrintNewSystem(pw);
            if (measureNumber == FIRST_MEASURE_NUMBER) {
                writeAttributes(song, pw);
            }

            // measureOpen tracks whether the current measure tag is still open.
            // A measure is open after we write its opening tag and closed after
            // we write its closing tag.
            boolean measureOpen = true;

            var elements = line.getElements();
            var lastElement = elements.isEmpty() ? null : elements.getLast();

            for (StaffElement element : elements) {
                ElementType type = element.getType();

                if (type == ElementType.REPEAT_LEFT) {
                    // REPEAT_LEFT opens a new measure (the forward-repeat barline
                    // is a left barline, not a right barline). Close the current
                    // measure with an invisible right barline to preserve the
                    // line boundary, then open the new measure.
                    writeInvisibleRightBarline(pw);
                    measureNumber = openForwardRepeatMeasure(pw, measureNumber);

                } else if (type == ElementType.REPEAT_LEFT_RIGHT) {
                    // REPEAT_LEFT_RIGHT straddles a measure boundary:
                    // - a backward-repeat right barline closes the current measure,
                    // - a forward-repeat left barline opens the next one.
                    // The reader reconstructs the REPEAT_LEFT_RIGHT from this pair.
                    writeBackwardRepeatRightBarline(pw);
                    measureNumber = openForwardRepeatMeasure(pw, measureNumber);

                } else if (type.isBarLine() || type.isRepeat()) {
                    // All other barline/repeat types close the current measure
                    // with a right barline. If this is not the last element on the
                    // line, a new measure is opened immediately for subsequent
                    // elements. If it is the last element, the outer end-of-line
                    // check will not emit a spurious empty measure.
                    var entry = BarlineStyleMapping.forElementType(type);
                    // entry is non-null here: REPEAT_LEFT and REPEAT_LEFT_RIGHT
                    // are handled in the branches above; all remaining barline/
                    // repeat types have forward-map entries.
                    if (entry == null) {
                        continue;
                    }
                    writeBarline(pw, entry);
                    closeMeasure(pw);
                    measureOpen = false;

                    // Peek ahead: if this barline is not the last element on the
                    // line, there are more elements to place, so open the next
                    // measure now. If it is the last element, measureOpen stays
                    // false and the end-of-line block below is skipped — no
                    // spurious empty measure is emitted.
                    if (element != lastElement) {
                        measureNumber++;
                        openMeasure(pw, measureNumber);
                        measureOpen = true;
                    }
                }
                // Non-barline elements (notes, rests, etc.) carry no XML in this
                // structural phase — note content arrives in Phase 3.
            }

            // If the current measure is still open at end of line, the line break
            // ends it. An invisible right barline marks the break so the reader can
            // reconstruct the line boundary without inserting a barline StaffElement.
            // If measureOpen is false, the last element was a real barline that
            // already closed its measure — no spurious empty measure is emitted.
            if (measureOpen) {
                writeInvisibleRightBarline(pw);
                closeMeasure(pw);
            }
        }
    }

    /**
     * Closes the current measure, increments the measure counter, opens a new
     * measure, and writes the forward-repeat left barline into it. Returns the
     * updated measure number.
     */
    private static int openForwardRepeatMeasure(PrintWriter pw, int measureNumber) {
        closeMeasure(pw);
        measureNumber++;
        openMeasure(pw, measureNumber);
        writeForwardRepeatLeftBarline(pw);
        return measureNumber;
    }

    /**
     * Writes {@code <measure number="N">} at the current indent and pushes one
     * level so subsequent measure-body content is indented correctly.
     */
    private static void openMeasure(PrintWriter pw, int measureNumber) {
        XML.writeBeginTag(pw, MusicXmlTags.MEASURE, MusicXmlTags.ATTR_NUMBER, Integer.toString(measureNumber));
        XML.indent();
    }

    /**
     * Pops one indent level and writes the {@code </measure>} closing tag.
     */
    private static void closeMeasure(PrintWriter pw) {
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.MEASURE);
    }

    // -------------------------------------------------------------------------
    // Attribute block
    // -------------------------------------------------------------------------

    private static void writeAttributes(Song song, PrintWriter pw) {
        XML.writeBeginTag(pw, MusicXmlTags.ATTRIBUTES);
        XML.indent();

        XML.writeValue(pw, "divisions", Integer.toString(DIVISIONS));

        // <key> with inline child <fifths>
        // Encode as signed fifths: negative for flats (MusicXML convention).
        int fifths = song.getDefaultKeyType() == KeyType.FLATS
            ? -song.getDefaultKeyAccidentalCount()
            : song.getDefaultKeyAccidentalCount();
        XML.printIndent(pw);
        pw.println("<key><fifths>" + fifths + "</fifths></key>");

        // <time print-object="no"> with inline self-closing child <senza-misura/>
        XML.printIndent(pw);
        pw.println("<time print-object=\"no\"><senza-misura/></time>");

        // <clef> with inline children
        XML.printIndent(pw);
        pw.println("<clef><sign>G</sign><line>2</line></clef>");

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.ATTRIBUTES);
    }

    // -------------------------------------------------------------------------
    // System-break marker
    // -------------------------------------------------------------------------

    private static void writePrintNewSystem(PrintWriter pw) {
        XML.writeEmptyTag(pw, MusicXmlTags.PRINT, MusicXmlTags.ATTR_NEW_SYSTEM, MusicXmlTags.YES);
    }

    // -------------------------------------------------------------------------
    // Barline helpers
    // -------------------------------------------------------------------------

    /**
     * Emits a forward-repeat left barline (heavy-light style, forward direction).
     */
    private static void writeForwardRepeatLeftBarline(PrintWriter pw) {
        writeBarlineFor(pw, ElementType.REPEAT_LEFT);
    }

    /**
     * Emits a backward-repeat right barline (light-heavy style, backward direction).
     */
    private static void writeBackwardRepeatRightBarline(PrintWriter pw) {
        writeBarlineFor(pw, ElementType.REPEAT_RIGHT);
    }

    /**
     * Looks up the {@link BarlineStyleMapping.BarlineEntry} for the given
     * {@link ElementType} and delegates to {@link #writeBarline(PrintWriter, BarlineStyleMapping.BarlineEntry)}.
     * The type must have a forward-map entry; types without one (e.g.
     * {@code REPEAT_LEFT_RIGHT}) are handled by their own callers before this
     * method is reached.
     */
    private static void writeBarlineFor(PrintWriter pw, ElementType type) {
        var entry = BarlineStyleMapping.forElementType(type);
        if (entry == null) {
            return;
        }
        writeBarline(pw, entry);
    }

    /**
     * Emits a {@code <barline>} using the location stored in the
     * {@link BarlineStyleMapping.BarlineEntry}.
     */
    private static void writeBarline(PrintWriter pw, BarlineStyleMapping.BarlineEntry entry) {
        writeBarline(pw, entry.location(), entry.barStyle(), entry.repeatDirection());
    }

    /** Emits {@code <barline location="right"><bar-style>none</bar-style></barline>}. */
    private static void writeInvisibleRightBarline(PrintWriter pw) {
        writeBarline(pw, BarlineStyleMapping.LOCATION_RIGHT, BarlineStyleMapping.BAR_STYLE_NONE, null);
    }

    /**
     * Emits a full {@code <barline>} element with {@code <bar-style>} and,
     * when non-null, a {@code <repeat direction="..."/>} child.
     */
    private static void writeBarline(PrintWriter pw, String location, String barStyle, @Nullable String repeatDirection) {
        XML.writeBeginTag(pw, MusicXmlTags.BARLINE, MusicXmlTags.ATTR_LOCATION, location);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.BAR_STYLE, barStyle);

        if (repeatDirection != null) {
            XML.writeEmptyTag(pw, MusicXmlTags.REPEAT, MusicXmlTags.ATTR_DIRECTION, repeatDirection);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.BARLINE);
    }
}
