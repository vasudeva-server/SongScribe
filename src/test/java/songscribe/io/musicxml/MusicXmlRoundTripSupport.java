/*
    SongScribe song notation program
    Copyright (C) Sri Chinmoy Centres International

    This file is part of SongScribe.

    SongScribe is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 3 of the License, or
    (at your option) any later version.

    SongScribe is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package songscribe.io.musicxml;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.io.XmlFixtures.closeTag;
import static songscribe.io.XmlFixtures.element;
import static songscribe.io.XmlFixtures.emptyTag;
import static songscribe.io.XmlFixtures.openTag;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import org.xml.sax.InputSource;

import songscribe.Constants;
import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Span;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.font.DocumentFontsHolder;
import songscribe.io.SongLoadResult;

/**
 * Shared plumbing for the MusicXML round-trip test classes: the write/read
 * cycle, song construction, and the cross-cutting span-assertion helpers used by
 * more than one concern-specific test file.
 */
abstract class MusicXmlRoundTripSupport extends UnitTest {

    /**
     * A non-zero X offset (in pixels) that survives the px → ss → tenths → ss → px
     * round-trip without rounding loss.  Any integer number of pixels is exact at the
     * default scale (8 px per staff space), so 2 staff spaces = 16 px is a clean choice.
     */
    protected static final int X_OFFSET_PX = ScaleContext.ssToRoundedPx(2.0);

    /**
     * Staff position of C4 — six steps below the B4 origin (staffPosition 0) in the
     * descending-pitch lattice.
     */
    protected static final int C4_STAFF_POSITION = 6;

    /**
     * Minimal SongScribe provenance block. Every hand-built {@code <score-partwise>}
     * fixture read through {@link MusicXmlReader} must carry a {@code <software>} tag
     * that starts with {@link Constants#PACKAGE_NAME}, or the reader's provenance gate
     * refuses it at {@code endDocument}. Fixtures insert this immediately after the
     * root element so future fixtures inherit the tag instead of silently breaking.
     */
    protected static final String SOFTWARE_IDENTIFICATION =
        "  " + openTag(MusicXmlTags.IDENTIFICATION) + '\n' +
        "    " + openTag(MusicXmlTags.ENCODING) + '\n' +
        "      " + element(MusicXmlTags.SOFTWARE, Constants.PACKAGE_NAME) + '\n' +
        "    " + closeTag(MusicXmlTags.ENCODING) + '\n' +
        "  " + closeTag(MusicXmlTags.IDENTIFICATION) + '\n';

    /** The single part these fixtures declare and then fill. */
    private static final String PART_ID = "P1";

    // -- helpers --

    /**
     * Wraps {@code measureBody} in a minimal {@code <score-partwise>} document with a
     * SongScribe provenance tag, a new-system {@code <print>} (so the reader starts a
     * line), and standard attributes. Shared by the hand-built reader edge-case and
     * round-trip fixtures.
     *
     * <p>Element and attribute names come from {@link MusicXmlTags} wherever the codec
     * has a constant for them, so renaming one there cannot leave this fixture quietly
     * emitting an element the reader no longer recognises. The few names spelled out
     * below ({@code divisions}, {@code time}, {@code clef}, {@code part-name}) are
     * literals in the writer too, so there is no constant for them to drift from.
     */
    protected static String scoreWithMeasureBody(String measureBody) {
        return
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            openTag(MusicXmlTags.SCORE_PARTWISE, MusicXmlTags.ATTR_VERSION, MusicXmlTags.VERSION_VALUE) + '\n' +
            SOFTWARE_IDENTIFICATION +
            "  " + openTag(MusicXmlTags.PART_LIST) + '\n' +
            "    " + openTag(MusicXmlTags.SCORE_PART, MusicXmlTags.ATTR_ID, PART_ID) +
                "<part-name></part-name>" + closeTag(MusicXmlTags.SCORE_PART) + '\n' +
            "  " + closeTag(MusicXmlTags.PART_LIST) + '\n' +
            "  " + openTag(MusicXmlTags.PART, MusicXmlTags.ATTR_ID, PART_ID) + '\n' +
            "    " + openTag(MusicXmlTags.MEASURE, MusicXmlTags.ATTR_NUMBER, "1") + '\n' +
            "      " + emptyTag(MusicXmlTags.PRINT, MusicXmlTags.ATTR_NEW_SYSTEM, MusicXmlTags.YES) + '\n' +
            "      " + openTag(MusicXmlTags.ATTRIBUTES) + '\n' +
            "        <divisions>" + MusicXmlUnits.DIVISIONS + "</divisions>\n" +
            "        " + element(MusicXmlTags.KEY, element(MusicXmlTags.FIFTHS, "0")) + '\n' +
            "        <time print-object=\"no\"><senza-misura/></time>\n" +
            "        <clef><sign>G</sign><line>2</line></clef>\n" +
            "      " + closeTag(MusicXmlTags.ATTRIBUTES) + '\n' +
            measureBody +
            "    " + closeTag(MusicXmlTags.MEASURE) + '\n' +
            "  " + closeTag(MusicXmlTags.PART) + '\n' +
            closeTag(MusicXmlTags.SCORE_PARTWISE) + '\n';
    }

    // The Song-only overloads default to DocumentFonts.defaultFonts() so the
    // Phase 1–6 tests that predate fonts plumbing compile and run unchanged; the
    // font-aware overloads carry the DocumentFonts through the write/read seam and
    // surface it back via SongLoadResult.Success.

    protected static String writeToString(Song song) throws Exception {
        return writeToString(song, DocumentFonts.defaultFonts());
    }

    protected static String writeToString(Song song, DocumentFontsHolder fonts) throws Exception {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        MusicXmlWriter.writeSong(song, fonts, pw);
        pw.flush();
        return sw.toString();
    }

    protected static Song parse(String xml) throws Exception {
        return parseResult(xml).song();
    }

    protected static SongLoadResult.Success parseResult(String xml) throws Exception {
        return MusicXmlReader.read(new InputSource(new StringReader(xml)));
    }

    public static Song roundTrip(Song song) throws Exception {
        return roundTrip(song, DocumentFonts.defaultFonts()).song();
    }

    public static SongLoadResult.Success roundTrip(Song song, DocumentFontsHolder fonts) throws Exception {
        return parseResult(writeToString(song, fonts));
    }

    /**
     * Builds a song whose lines are populated by the given {@link LineBuilder}s.
     * The default initial line that {@link Song#Song()} installs is replaced by
     * the caller-supplied lines. Each builder's elements are added to its line
     * before that line is inserted into the song, so none of the builders run
     * with their line as the song's last line; the terminal-slot auto-maintenance
     * therefore does not reorder elements during construction.
     */
    @FunctionalInterface
    protected interface LineBuilder {
        void build(Line line);
    }

    protected static Song buildSong(LineBuilder... builders) {
        var song = new Song();

        song.withoutMutationTracking(() -> {
            song.removeLine(0);

            for (var builder : builders) {
                var line = new Line(song);
                builder.build(line);
                song.addLine(line);
            }
        });

        return song;
    }

    // -------------------------------------------------------------------------
    // Range-Spans: assertSpanEquals helpers
    //
    // These helpers compare a reloaded span to expected values field-by-field
    // without adding equals()/hashCode() to span classes or StaffElement, which
    // would break identity-based hash collections and layout caches.
    //
    // The base overload checks anchor and end element indices.  Per-type
    // overloads add the type-specific fields (Tuplet.grade/verticalPositionSs,
    // Trill.yPositionSs).  The context string names the case in failure output.
    // -------------------------------------------------------------------------

    protected static void assertSpanEquals(
            Span actual, int expectedAnchor, int expectedEnd, String context) {
        assertThat(actual.getAnchorElementIndex())
            .as("%s: anchor index", context)
            .isEqualTo(expectedAnchor);
        assertThat(actual.getEndElementIndex())
            .as("%s: end index", context)
            .isEqualTo(expectedEnd);
    }

    protected static void assertSpanEquals(
            Span actual, int expectedAnchor, int expectedEnd) {
        assertSpanEquals(actual, expectedAnchor, expectedEnd, "span");
    }

    /**
     * Asserts anchor/end indices plus the {@link Tuplet}-specific fields
     * {@code grade} and {@code verticalPositionSs}. Used by Range-Spans 6b.
     */
    protected static void assertSpanEquals(
            Tuplet actual, int expectedAnchor, int expectedEnd,
            int expectedGrade, int expectedVerticalPositionSs) {
        assertSpanEquals(actual, expectedAnchor, expectedEnd, "tuplet");
        assertThat(actual.getGrade())
            .as("tuplet: grade")
            .isEqualTo(expectedGrade);
        assertThat(actual.getVerticalPositionSs())
            .as("tuplet: verticalPositionSs")
            .isEqualTo(expectedVerticalPositionSs);
    }

    /**
     * Asserts anchor/end indices plus the {@link Trill}-specific field
     * {@code yPositionSs}. Used by Range-Spans 6b.
     */
    protected static void assertSpanEquals(
            Trill actual, int expectedAnchor, int expectedEnd, int expectedYPositionSs) {
        assertSpanEquals(actual, expectedAnchor, expectedEnd, "trill");
        assertThat(actual.getYPositionSs())
            .as("trill: yPositionSs")
            .isEqualTo(expectedYPositionSs);
    }
}
