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
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;

class MusicXmlRoundTripTest extends UnitTest {

    // -- helpers --

    private static String writeToString(Song song) throws Exception {
        var sw = new StringWriter();
        var pw = new PrintWriter(sw);
        MusicXmlWriter.writeSong(song, pw);
        pw.flush();
        return sw.toString();
    }

    private static Song parse(String xml) throws Exception {
        return MusicXmlReader.read(new InputSource(new StringReader(xml)));
    }

    public static Song roundTrip(Song song) throws Exception {
        return parse(writeToString(song));
    }

    /**
     * Extracts barline and repeat {@link ElementType}s from the given line, in order.
     * Elements where neither {@code isBarLine()} nor {@code isRepeat()} is true are excluded.
     */
    private static List<ElementType> barlineTypesOf(Line line) {
        var result = new ArrayList<ElementType>();

        for (StaffElement element : line.getElements()) {
            var type = element.getType();

            if (type.isBarLine() || type.isRepeat()) {
                result.add(type);
            }
        }

        return result;
    }

    /**
     * Compares the populated fields of {@code expected} and {@code actual} that the
     * MusicXML round-trip preserves: key signature, line count, and per-line barline sequence.
     */
    private static void assertPopulatedSubsetEquals(Song expected, Song actual) {
        assertThat(actual.getDefaultKeyAccidentalCount())
            .as("default key accidental count")
            .isEqualTo(expected.getDefaultKeyAccidentalCount());
        assertThat(actual.getDefaultKeyType())
            .as("default key type")
            .isEqualTo(expected.getDefaultKeyType());
        assertThat(actual.lineCount())
            .as("line count")
            .isEqualTo(expected.lineCount());

        var lineCount = expected.lineCount();

        for (int i = 0; i < lineCount; i++) {
            var expectedBarlines = barlineTypesOf(expected.getLine(i));
            var actualBarlines   = barlineTypesOf(actual.getLine(i));
            assertThat(actualBarlines)
                .as("barline types for line %d", i)
                .isEqualTo(expectedBarlines);
        }
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
    private interface LineBuilder {
        void build(Line line);
    }

    private static Song buildSong(LineBuilder... builders) {
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

    // -- tests --

    @Test
    void testDefaultSongRoundTripsLosslessly() throws Exception {
        var song = new Song();
        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testEmptySongWriterOutputIsSchemaValid() throws Exception {
        var song = new Song();
        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("MusicXmlWriter output for new Song() must be schema-valid")
            .doesNotThrowAnyException();
    }

    // -- Phase 4 tests --

    @Test
    @SuppressWarnings("NullAway")
    void testBarlineStyleMappingBijectionForDirectTypes() {
        var directTypes = List.of(
            ElementType.SINGLE_BARLINE,
            ElementType.DOUBLE_BARLINE,
            ElementType.FINAL_DOUBLE_BARLINE,
            ElementType.REPEAT_LEFT,
            ElementType.REPEAT_RIGHT
        );

        for (var type : directTypes) {
            var entry = BarlineStyleMapping.forElementType(type);
            assertThat(entry)
                .as("forward map entry for %s", type)
                .isNotNull();

            var roundTripped = BarlineStyleMapping.forBarStyle(entry.barStyle(), entry.repeatDirection());
            assertThat(roundTripped)
                .as("reverse map result for %s via barStyle=%s repeatDirection=%s",
                    type, entry.barStyle(), entry.repeatDirection())
                .isEqualTo(type);
        }

        assertThat(BarlineStyleMapping.forElementType(ElementType.REPEAT_LEFT_RIGHT))
            .as("REPEAT_LEFT_RIGHT must not be in the forward map")
            .isNull();
    }

    @Test
    void testMultiLineWithAssortedBarlinesRoundTrips() throws Exception {
        var song = buildSong(
            line -> line.addElement(ElementType.SINGLE_BARLINE.newInstance()),
            line -> line.addElement(ElementType.DOUBLE_BARLINE.newInstance()),
            line -> line.addElement(ElementType.FINAL_DOUBLE_BARLINE.newInstance())
        );

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testMultiLineWithAssortedBarlinesWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> line.addElement(ElementType.SINGLE_BARLINE.newInstance()),
            line -> line.addElement(ElementType.DOUBLE_BARLINE.newInstance()),
            line -> line.addElement(ElementType.FINAL_DOUBLE_BARLINE.newInstance())
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("multi-line barline song must be schema-valid")
            .doesNotThrowAnyException();
    }

    @Test
    void testRepeatsRoundTrip() throws Exception {
        // Line 1: REPEAT_LEFT_RIGHT verifies that the writer's straddling-pair
        // decomposition is correctly recomposed by the reader into a single element.
        // Line 2: REPEAT_LEFT followed by REPEAT_RIGHT — REPEAT_RIGHT ends up as
        // the terminal of the last line, so the reader's pending-hold logic flushes
        // it cleanly at </part> without any cross-line bleed.
        var song = buildSong(
            line -> line.addElement(ElementType.REPEAT_LEFT_RIGHT.newInstance()),
            line -> {
                line.addElement(ElementType.REPEAT_LEFT.newInstance());
                line.addElement(ElementType.REPEAT_RIGHT.newInstance());
            }
        );

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testRepeatsWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> line.addElement(ElementType.REPEAT_LEFT_RIGHT.newInstance()),
            line -> {
                line.addElement(ElementType.REPEAT_LEFT.newInstance());
                line.addElement(ElementType.REPEAT_RIGHT.newInstance());
            }
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("repeat barline song must be schema-valid")
            .doesNotThrowAnyException();
    }

    @Test
    void testLineBreakWithNoBarlineRoundTrips() throws Exception {
        var song = buildSong(
            line -> {},
            line -> line.addElement(ElementType.SINGLE_BARLINE.newInstance()),
            line -> {}
        );

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testLineBreakWithNoBarlineWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> {},
            line -> line.addElement(ElementType.SINGLE_BARLINE.newInstance()),
            line -> {}
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("song with empty lines must be schema-valid")
            .doesNotThrowAnyException();
    }

    @Test
    void testRepeatRightAtLineEndDoesNotBleedIntoNextLine() throws Exception {
        // Regression: line 1 ends with REPEAT_RIGHT (held as pendingRepeatRight by
        // the reader), and line 2 also contains repeats. Before the fix, the held
        // REPEAT_RIGHT was not flushed when the new-system measure started line 2,
        // so it bled across the line boundary — landing on line 2 (or merging with
        // line 2's REPEAT_LEFT into a spurious REPEAT_LEFT_RIGHT). The barline must
        // stay on line 1.
        var song = buildSong(
            line -> line.addElement(ElementType.REPEAT_RIGHT.newInstance()),
            line -> {
                line.addElement(ElementType.REPEAT_LEFT.newInstance());
                line.addElement(ElementType.REPEAT_RIGHT.newInstance());
            }
        );

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    // TU2/TU3 — forBarStyle: unknown style, unknown style+direction, and fallthrough
    @Test
    void testForBarStyleHandlesUnknownAndFallthrough() {
        // "dashed" is not in either reverse map — must return null regardless of direction.
        assertThat(BarlineStyleMapping.forBarStyle("dashed", null))
            .as("unknown bar-style with no direction must return null")
            .isNull();

        // "dotted" is also unknown; a non-null direction doesn't rescue it.
        assertThat(BarlineStyleMapping.forBarStyle("dotted", BarlineStyleMapping.REPEAT_FORWARD))
            .as("unknown bar-style with a direction must return null")
            .isNull();

        // BAR_STYLE_REGULAR is in REVERSE_MAP_NO_REPEAT only; when a non-null
        // direction is supplied the with-repeat map is checked first (and misses),
        // then the lookup falls through to the no-repeat map → SINGLE_BARLINE.
        assertThat(BarlineStyleMapping.forBarStyle(BarlineStyleMapping.BAR_STYLE_REGULAR, BarlineStyleMapping.REPEAT_FORWARD))
            .as("regular bar-style with a direction must fall through to SINGLE_BARLINE")
            .isEqualTo(ElementType.SINGLE_BARLINE);
    }

    // TU2 — reader skip: an unrecognised bar-style produces no barline element
    @Test
    void testUnknownBarStyleIsSilentlySkipped() throws Exception {
        // Hand-crafted minimal MusicXML 4.0 document: one line whose single measure
        // carries a "dashed" right barline that the mapping does not recognise.
        // The reader must silently discard it; the line's barline list must be empty.
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <print new-system=\"yes\"/>\n" +
            "      <barline location=\"right\"><bar-style>dashed</bar-style></barline>\n" +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";

        var song = parse(xml);
        assertThat(song.lineCount())
            .as("one line expected")
            .isEqualTo(1);
        assertThat(barlineTypesOf(song.getLine(0)))
            .as("unknown bar-style must produce no barline elements")
            .isEmpty();
    }

    // TU4 — mid-line multi-barline: [SINGLE_BARLINE, DOUBLE_BARLINE] on one line.
    // Writer: SINGLE_BARLINE is not the last element, so the peek-ahead opens a new
    // measure for DOUBLE_BARLINE. Both measures belong to the same line.
    // Reader: measure 1 (new-system) → SINGLE_BARLINE; measure 2 → DOUBLE_BARLINE;
    // both land on line 0.  Expected per-line sequences: line 0 = [SINGLE_BARLINE, DOUBLE_BARLINE].
    @Test
    void testMidLineTwoBarlineRoundTrips() throws Exception {
        var song = buildSong(
            line -> {
                line.addElement(ElementType.SINGLE_BARLINE.newInstance());
                line.addElement(ElementType.DOUBLE_BARLINE.newInstance());
            }
        );

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testMidLineTwoBarlineWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> {
                line.addElement(ElementType.SINGLE_BARLINE.newInstance());
                line.addElement(ElementType.DOUBLE_BARLINE.newInstance());
            }
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("mid-line two-barline song must be schema-valid")
            .doesNotThrowAnyException();
    }

    // TU5 — held repeat + non-left follow: [REPEAT_RIGHT, SINGLE_BARLINE] on one line.
    // Writer: REPEAT_RIGHT is not the last element → opens a new measure for SINGLE_BARLINE.
    // Reader: measure 1 → REPEAT_RIGHT (held); measure 2 barline is SINGLE_BARLINE
    // (not REPEAT_LEFT on the left), so processBarline's else-branch fires: flushes the
    // held REPEAT_RIGHT, then appends SINGLE_BARLINE.
    // The song model's auto-maintenance then re-positions REPEAT_RIGHT as the terminal
    // element, so the round-tripped line is [SINGLE_BARLINE, REPEAT_RIGHT].
    @Test
    void testHeldRepeatFollowedByNonLeftRoundTrips() throws Exception {
        var song = buildSong(
            line -> {
                line.addElement(ElementType.REPEAT_RIGHT.newInstance());
                line.addElement(ElementType.SINGLE_BARLINE.newInstance());
            }
        );

        var song2 = roundTrip(song);
        // The round-trip goes through the model's auto-maintenance, which keeps
        // REPEAT_RIGHT as the terminal element. Both elements must survive the
        // round-trip; the exact order reflects that invariant.
        final var expectedBarlines = List.of(ElementType.SINGLE_BARLINE, ElementType.REPEAT_RIGHT);
        assertThat(barlineTypesOf(song2.getLine(0)))
            .as("barline types for line 0 after round-trip")
            .isEqualTo(expectedBarlines);
    }

    @Test
    void testHeldRepeatFollowedByNonLeftWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> {
                line.addElement(ElementType.REPEAT_RIGHT.newInstance());
                line.addElement(ElementType.SINGLE_BARLINE.newInstance());
            }
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("held repeat + non-left song must be schema-valid")
            .doesNotThrowAnyException();
    }

    // TU6 — key signature round-trips
    @Test
    void testKeySignatureWithSharpsRoundTrips() throws Exception {
        final int sharpCount = 3;
        var song = buildSong(line -> {});
        song.withoutMutationTracking(() -> {
            song.setDefaultKeyType(KeyType.SHARPS);
            song.setDefaultKeyAccidentalCount(sharpCount);
        });

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testKeySignatureWithFlatsRoundTrips() throws Exception {
        final int flatCount = 2;
        var song = buildSong(line -> {});
        song.withoutMutationTracking(() -> {
            song.setDefaultKeyType(KeyType.FLATS);
            song.setDefaultKeyAccidentalCount(flatCount);
        });

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    // TU1 — regression twin: schema-valid counterpart to testRepeatRightAtLineEndDoesNotBleedIntoNextLine
    @Test
    void testRepeatRightAtLineEndDoesNotBleedIntoNextLineWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> line.addElement(ElementType.REPEAT_RIGHT.newInstance()),
            line -> {
                line.addElement(ElementType.REPEAT_LEFT.newInstance());
                line.addElement(ElementType.REPEAT_RIGHT.newInstance());
            }
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("repeat-right bleed regression song must be schema-valid")
            .doesNotThrowAnyException();
    }
}
