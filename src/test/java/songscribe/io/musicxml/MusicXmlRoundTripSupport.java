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

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.RangeElement;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

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

    // -- helpers --

    protected static String writeToString(Song song) throws Exception {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        MusicXmlWriter.writeSong(song, pw);
        pw.flush();
        return sw.toString();
    }

    protected static Song parse(String xml) throws Exception {
        return MusicXmlReader.read(new InputSource(new StringReader(xml)));
    }

    public static Song roundTrip(Song song) throws Exception {
        return parse(writeToString(song));
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
            // new Song() installs a default base tempo but does not mirror it onto
            // a first element. Clear it so the built song is in canonical post-load
            // form (firstElement.hasTempo ⟺ song.tempo != null): tempo-free unless a
            // test materializes one on the first element. Otherwise the tempo writer
            // would emit this unmirrored default and reload it as a spurious
            // first-note attachment.
            song.setTempo(null);
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
    // Range-Spans: assertRangeElementEquals helpers
    //
    // These helpers compare a reloaded span to expected values field-by-field
    // without adding equals()/hashCode() to span classes or StaffElement, which
    // would break identity-based hash collections and layout caches.
    //
    // The base overload checks anchor and end element indices.  Per-type
    // overloads add the type-specific fields (Tuplet.grade/verticalPositionSs,
    // Trill.yPositionSs).  The context string names the case in failure output.
    // -------------------------------------------------------------------------

    protected static void assertRangeElementEquals(
            RangeElement actual, int expectedAnchor, int expectedEnd, String context) {
        assertThat(actual.getAnchorElementIndex())
            .as("%s: anchor index", context)
            .isEqualTo(expectedAnchor);
        assertThat(actual.getEndElementIndex())
            .as("%s: end index", context)
            .isEqualTo(expectedEnd);
    }

    protected static void assertRangeElementEquals(
            RangeElement actual, int expectedAnchor, int expectedEnd) {
        assertRangeElementEquals(actual, expectedAnchor, expectedEnd, "span");
    }

    /**
     * Asserts anchor/end indices plus the {@link Tuplet}-specific fields
     * {@code grade} and {@code verticalPositionSs}. Used by Range-Spans 6b.
     */
    protected static void assertRangeElementEquals(
            Tuplet actual, int expectedAnchor, int expectedEnd,
            int expectedGrade, int expectedVerticalPositionSs) {
        assertRangeElementEquals((RangeElement) actual, expectedAnchor, expectedEnd, "tuplet");
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
    protected static void assertRangeElementEquals(
            Trill actual, int expectedAnchor, int expectedEnd, int expectedYPositionSs) {
        assertRangeElementEquals((RangeElement) actual, expectedAnchor, expectedEnd, "trill");
        assertThat(actual.getYPositionSs())
            .as("trill: yPositionSs")
            .isEqualTo(expectedYPositionSs);
    }
}
