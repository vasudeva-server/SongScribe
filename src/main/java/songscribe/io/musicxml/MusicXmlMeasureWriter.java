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
import java.util.List;

import org.jspecify.annotations.Nullable;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.io.XML;
import songscribe.io.musicxml.MusicXmlSpanIndex.EndingMarker;
import songscribe.io.musicxml.MusicXmlSpanIndex.IndexSpanMarkers;

final class MusicXmlMeasureWriter {

    private MusicXmlMeasureWriter() {}

    /**
     * Closes the current measure, increments the measure counter, opens a new
     * measure, and writes the forward-repeat left barline into it. Returns the
     * updated measure number. {@code forwardLeftEndings} are folded onto the
     * forward-left {@code <barline>} (an ending anchor or volta-2 start).
     */
    static int openForwardRepeatMeasure(PrintWriter pw, int measureNumber, List<EndingMarker> forwardLeftEndings) {
        closeMeasure(pw);
        measureNumber++;
        openMeasure(pw, measureNumber);
        writeForwardRepeatLeftBarline(pw, forwardLeftEndings);
        return measureNumber;
    }

    /**
     * Writes {@code <measure number="N">} at the current indent and pushes one
     * level so subsequent measure-body content is indented correctly.
     */
    static void openMeasure(PrintWriter pw, int measureNumber) {
        XML.writeBeginTag(pw, MusicXmlTags.MEASURE, MusicXmlTags.ATTR_NUMBER, Integer.toString(measureNumber));
        XML.indent();
    }

    /**
     * Pops one indent level and writes the {@code </measure>} closing tag.
     */
    static void closeMeasure(PrintWriter pw) {
        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.MEASURE);
    }

    // -------------------------------------------------------------------------
    // Attribute block
    // -------------------------------------------------------------------------

    static void writeAttributes(Song song, PrintWriter pw) {
        XML.writeBeginTag(pw, MusicXmlTags.ATTRIBUTES);
        XML.indent();

        XML.writeValue(pw, "divisions", Integer.toString(MusicXmlUnits.DIVISIONS));

        // <key> with inline child <fifths> — measure 1 carries the song default.
        var fifths = KeySignatureMapping.toFifths(song.getDefaultKeyType(), song.getDefaultKeyAccidentalCount());
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

    /**
     * Emits a key-only {@code <attributes>} block carrying just
     * {@code <key><fifths>}, used at a later line whose key differs from the
     * running key signature.
     */
    static void writeKeyOnlyAttributes(PrintWriter pw, int fifths) {
        XML.writeBeginTag(pw, MusicXmlTags.ATTRIBUTES);
        XML.indent();

        XML.printIndent(pw);
        pw.println("<key><fifths>" + fifths + "</fifths></key>");

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.ATTRIBUTES);
    }

    /**
     * The signed-fifths encoding of {@code line}'s effective key: its own key when
     * set, otherwise the song default (matching how every line is materialized
     * from the default on load).
     */
    static int effectiveKeyFifths(Song song, Line line) {
        var lineKeyType = line.getKeyType();

        if (lineKeyType == null) {
            return KeySignatureMapping.toFifths(song.getDefaultKeyType(), song.getDefaultKeyAccidentalCount());
        }

        return KeySignatureMapping.toFifths(lineKeyType, line.getKeyAccidentalCount());
    }

    // -------------------------------------------------------------------------
    // System-break marker
    // -------------------------------------------------------------------------

    static void writePrintNewSystem(PrintWriter pw) {
        XML.writeEmptyTag(pw, MusicXmlTags.PRINT, MusicXmlTags.ATTR_NEW_SYSTEM, MusicXmlTags.YES);
    }

    // -------------------------------------------------------------------------
    // Barline helpers
    // -------------------------------------------------------------------------

    /**
     * Emits a forward-repeat left barline (heavy-light style, forward direction),
     * folding {@code endings} onto it.
     */
    private static void writeForwardRepeatLeftBarline(PrintWriter pw, List<EndingMarker> endings) {
        writeBarlineFor(pw, ElementType.REPEAT_LEFT, endings);
    }

    /**
     * Emits a backward-repeat right barline (light-heavy style, backward direction),
     * folding {@code endings} onto it.
     */
    static void writeBackwardRepeatRightBarline(PrintWriter pw, List<EndingMarker> endings) {
        writeBarlineFor(pw, ElementType.REPEAT_RIGHT, endings);
    }

    /**
     * Looks up the {@link BarlineStyleMapping.BarlineEntry} for the given
     * {@link ElementType} and delegates to {@link #writeBarline(PrintWriter, BarlineStyleMapping.BarlineEntry, List)}.
     * The type must have a forward-map entry; types without one (e.g.
     * {@code REPEAT_LEFT_RIGHT}) are handled by their own callers before this
     * method is reached.
     */
    private static void writeBarlineFor(PrintWriter pw, ElementType type, List<EndingMarker> endings) {
        var entry = BarlineStyleMapping.forElementType(type);

        if (entry == null) {
            return;
        }

        writeBarline(pw, entry, endings);
    }

    /**
     * Emits a {@code <barline>} using the location stored in the
     * {@link BarlineStyleMapping.BarlineEntry}, folding {@code endings} onto it.
     */
    static void writeBarline(PrintWriter pw, BarlineStyleMapping.BarlineEntry entry, List<EndingMarker> endings) {
        writeBarline(pw, entry.location(), entry.barStyle(), entry.repeatDirection(), endings);
    }

    /** Emits {@code <barline location="right"><bar-style>none</bar-style></barline>}. */
    static void writeInvisibleRightBarline(PrintWriter pw) {
        writeInvisibleRightBarline(pw, List.of());
    }

    /**
     * Emits {@code <barline location="right"><bar-style>none</bar-style>…</barline>}
     * carrying {@code endings}. Used to host a note-terminated volta's
     * {@code <ending ... type="discontinue">} after the boundary note, where no
     * real right barline element exists.
     */
    static void writeInvisibleRightBarline(PrintWriter pw, List<EndingMarker> endings) {
        writeBarline(pw, BarlineStyleMapping.LOCATION_RIGHT, BarlineStyleMapping.BAR_STYLE_NONE, null, endings);
    }

    /**
     * Emits {@code <barline location="left"><bar-style>none</bar-style>…</barline>}
     * carrying {@code endings}. Used to host a volta-2 {@code <ending number="2"
     * type="start">} at the start of the measure following a REPEAT_RIGHT split,
     * where no real left barline element exists.
     */
    static void writeInvisibleLeftBarline(PrintWriter pw, List<EndingMarker> endings) {
        writeBarline(pw, BarlineStyleMapping.LOCATION_LEFT, BarlineStyleMapping.BAR_STYLE_NONE, null, endings);
    }

    /**
     * Emits a note-anchored ending's {@code <ending number="1" type="start">}
     * (issue #306) on an invisible left barline immediately before the current
     * element, when the anchor is a content element with no barline to host it.
     * A no-op when the element carries no ending-start markers.
     */
    static void writeNoteAnchoredEndingStart(PrintWriter pw, IndexSpanMarkers markers) {
        var endingStartMarkers = markers.endingLeftBarlineMarkers();

        if (!endingStartMarkers.isEmpty()) {
            writeInvisibleLeftBarline(pw, endingStartMarkers);
        }
    }

    /**
     * Emits a note-terminated ending's {@code <ending ... type="discontinue">}
     * (issue #306) on an invisible right barline immediately after
     * {@code element}, when the end is a content element with no barline to host
     * it. When {@code element} is the line's last element, the markers are instead
     * returned so the caller can fold them onto the end-of-line invisible right
     * barline, avoiding a redundant second one. Returns {@code lastNoteEndingMarkers}
     * unchanged when the element carries no ending-end markers or is not the last.
     */
    static List<EndingMarker> writeOrDeferNoteEndingEnd(
        PrintWriter pw,
        IndexSpanMarkers markers,
        StaffElement element,
        @Nullable StaffElement lastElement,
        List<EndingMarker> lastNoteEndingMarkers
    ) {
        var endingEndMarkers = markers.endingRightBarlineMarkers();

        if (endingEndMarkers.isEmpty()) {
            return lastNoteEndingMarkers;
        }

        if (element == lastElement) {
            return endingEndMarkers;
        }

        writeInvisibleRightBarline(pw, endingEndMarkers);
        return lastNoteEndingMarkers;
    }

    /**
     * Emits a full {@code <barline>} element with {@code <bar-style>}, any
     * {@code <ending>} children (which precede {@code <repeat>} per schema), and,
     * when non-null, a {@code <repeat direction="..."/>} child.
     */
    private static void writeBarline(PrintWriter pw, String location, String barStyle,
            @Nullable String repeatDirection, List<EndingMarker> endings) {
        XML.writeBeginTag(pw, MusicXmlTags.BARLINE, MusicXmlTags.ATTR_LOCATION, location);
        XML.indent();

        XML.writeValue(pw, MusicXmlTags.BAR_STYLE, barStyle);

        // <ending> precedes <repeat> inside <barline> per the MusicXML schema.
        for (var ending : endings) {
            XML.writeEmptyTag(pw, MusicXmlTags.ENDING,
                MusicXmlTags.ATTR_NUMBER, ending.number(),
                MusicXmlTags.ATTR_TYPE, ending.type()
            );
        }

        if (repeatDirection != null) {
            XML.writeEmptyTag(pw, MusicXmlTags.REPEAT, MusicXmlTags.ATTR_DIRECTION, repeatDirection);
        }

        XML.dedent();
        XML.writeEndTag(pw, MusicXmlTags.BARLINE);
    }
}
