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
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatLeftRight;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.SOFTWARE_IDENTIFICATION;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.Song;

class MusicXmlBarlineRoundTripTest extends UnitTest {

    /**
     * Extracts barline and repeat {@link ElementType}s from the given line, in order.
     * Elements where neither {@code isBarLine()} nor {@code isRepeat()} is true are excluded.
     */
    private static List<ElementType> barlineTypesOf(Line line) {
        var result = new ArrayList<ElementType>();

        for (var element : line.getElements()) {
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

        for (var i = 0; i < lineCount; i++) {
            var expectedBarlines = barlineTypesOf(expected.getLine(i));
            var actualBarlines   = barlineTypesOf(actual.getLine(i));
            assertThat(actualBarlines)
                .as("barline types for line %d", i)
                .isEqualTo(expectedBarlines);
        }
    }

    // -- tests --

    @Test
    void testDefaultSongPopulatedSubsetRoundTrips() throws Exception {
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
    void testBarlineStyleMappingRoundTripsForDirectTypes() {
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
            line -> line.addElement(singleBarline()),
            line -> line.addElement(doubleBarline()),
            line -> line.addElement(finalDoubleBarline())
        );

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testMultiLineWithAssortedBarlinesWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> line.addElement(singleBarline()),
            line -> line.addElement(doubleBarline()),
            line -> line.addElement(finalDoubleBarline())
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
            line -> line.addElement(repeatLeftRight()),
            line -> {
                line.addElement(repeatLeft());
                line.addElement(repeatRight());
            }
        );

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testRepeatsWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> line.addElement(repeatLeftRight()),
            line -> {
                line.addElement(repeatLeft());
                line.addElement(repeatRight());
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
        // First line is a bare line break with no barline (the case under test); the
        // last line must end in a terminal for the song to be valid music.
        var song = buildSong(
            line -> {},
            line -> line.addElement(singleBarline()),
            line -> line.addElement(finalDoubleBarline())
        );

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testLineBreakWithNoBarlineWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> {},
            line -> line.addElement(singleBarline()),
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
        // Regression: line 1 ends with REPEAT_RIGHT (held as heldRepeatRight by
        // the reader), and line 2 also contains repeats. Before the fix, the held
        // REPEAT_RIGHT was not flushed when the new-system measure started line 2,
        // so it bled across the line boundary — landing on line 2 (or merging with
        // line 2's REPEAT_LEFT into a spurious REPEAT_LEFT_RIGHT). The barline must
        // stay on line 1.
        var song = buildSong(
            line -> line.addElement(repeatRight()),
            line -> {
                line.addElement(repeatLeft());
                line.addElement(repeatRight());
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
        // Hand-crafted minimal MusicXML 4.0 document: one line whose first measure
        // carries a "dashed" right barline that the mapping does not recognise, then a
        // second measure closing on a real terminal so the song is valid music.
        // The reader must silently discard the dashed barline, so the only barline that
        // survives is the closing FINAL_DOUBLE_BARLINE.
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            SOFTWARE_IDENTIFICATION +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <print new-system=\"yes\"/>\n" +
            "      <barline location=\"right\"><bar-style>dashed</bar-style></barline>\n" +
            "    </measure>\n" +
            "    <measure number=\"2\">\n" +
            "      <barline location=\"right\"><bar-style>light-heavy</bar-style></barline>\n" +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";

        var song = parse(xml);
        assertThat(song.lineCount())
            .as("one line expected")
            .isEqualTo(1);
        assertThat(barlineTypesOf(song.getLine(0)))
            .as("unknown bar-style is skipped; only the closing terminal survives")
            .containsExactly(ElementType.FINAL_DOUBLE_BARLINE);
    }

    // TU4 — mid-line multi-barline: [SINGLE_BARLINE, DOUBLE_BARLINE, FINAL] on one line.
    // The two mid-line barlines are the case under test; the line closes with a
    // terminal so it is valid music (DOUBLE_BARLINE is not a valid terminal).
    // Writer: SINGLE_BARLINE and DOUBLE_BARLINE are not the last element, so each
    // peek-ahead opens a new measure; FINAL closes the last measure. All belong to line 0.
    // Reader: measure 1 (new-system) → SINGLE_BARLINE; measure 2 → DOUBLE_BARLINE;
    // measure 3 → FINAL; all land on line 0.
    @Test
    void testMidLineTwoBarlineRoundTrips() throws Exception {
        var song = buildSong(
            line -> {
                line.addElement(singleBarline());
                line.addElement(doubleBarline());
                line.addElement(finalDoubleBarline());
            }
        );

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testMidLineTwoBarlineWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> {
                line.addElement(singleBarline());
                line.addElement(doubleBarline());
            }
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("mid-line two-barline song must be schema-valid")
            .doesNotThrowAnyException();
    }

    // TU5 — held repeat + non-left follow: [REPEAT_RIGHT, SINGLE_BARLINE, FINAL] on one line.
    // A valid last line ends in a terminal, so the line closes with FINAL_DOUBLE_BARLINE;
    // the mid-line REPEAT_RIGHT (held by the reader) is followed by a non-REPEAT_LEFT
    // barline, which is the case under test.
    // Writer: REPEAT_RIGHT and SINGLE_BARLINE are not the last element → each opens a
    // new measure; FINAL closes the last measure.
    // Reader: measure 1 → REPEAT_RIGHT (held); measure 2 barline is SINGLE_BARLINE
    // (not REPEAT_LEFT on the left), so processBarline's else-branch fires: flushes the
    // held REPEAT_RIGHT, then appends SINGLE_BARLINE; measure 3 → FINAL.
    // The reader builds each line detached from the song, so the elements keep their
    // exact document order — the round-tripped line is [REPEAT_RIGHT, SINGLE_BARLINE, FINAL].
    @Test
    void testHeldRepeatFollowedByNonLeftRoundTrips() throws Exception {
        var song = buildSong(
            line -> {
                line.addElement(repeatRight());
                line.addElement(singleBarline());
                line.addElement(finalDoubleBarline());
            }
        );

        var song2 = roundTrip(song);
        // The reader reconstructs elements in document order, so a mid-line
        // REPEAT_RIGHT is not pulled to the terminal slot: the order is preserved.
        var expectedBarlines = List.of(
            ElementType.REPEAT_RIGHT, ElementType.SINGLE_BARLINE, ElementType.FINAL_DOUBLE_BARLINE);
        assertThat(barlineTypesOf(song2.getLine(0)))
            .as("barline types for line 0 after round-trip")
            .isEqualTo(expectedBarlines);
    }

    @Test
    void testHeldRepeatFollowedByNonLeftWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> {
                line.addElement(repeatRight());
                line.addElement(singleBarline());
            }
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("held repeat + non-left song must be schema-valid")
            .doesNotThrowAnyException();
    }

    /**
     * Only a forward repeat on the <em>left</em> side is the second half of a straddling
     * REPEAT_LEFT_RIGHT pair. This covers the other side of that position test, which our own
     * writer never produces: a foreign file may omit the {@code location} attribute, which
     * MusicXML defines as {@code "right"}, so the forward repeat arrives at a non-left
     * position while a REPEAT_RIGHT is held. Merging it anyway would collapse two repeat
     * barlines into one and silently drop a repeat sign.
     *
     * <p>The two barlines share a measure because the reader resolves each {@code </barline>}
     * as it closes, independently of measure boundaries — the held REPEAT_RIGHT reaches the
     * forward repeat identically either way.
     */
    @Test
    void testHeldRepeatFollowedByNonLeftForwardRepeatDoesNotMerge() throws Exception {
        var xml = scoreWithMeasureBody(
            """
                      <barline location="right"><bar-style>light-heavy</bar-style>\
            <repeat direction="backward"/></barline>
                      <barline><bar-style>heavy-light</bar-style>\
            <repeat direction="forward"/></barline>
            """
        );

        var song = parse(xml);

        // The trailing FINAL_DOUBLE_BARLINE is the song's auto-maintained terminal, added
        // because the parsed line does not end in one. Merging the pair would leave
        // [REPEAT_LEFT_RIGHT, FINAL_DOUBLE_BARLINE] here.
        assertThat(barlineTypesOf(song.getLine(0)))
            .as("a forward repeat at a non-left position stays a separate element")
            .containsExactly(
                ElementType.REPEAT_RIGHT, ElementType.REPEAT_LEFT, ElementType.FINAL_DOUBLE_BARLINE);
    }

    // TU6 — key signature round-trips
    @Test
    void testKeySignatureWithSharpsRoundTrips() throws Exception {
        final var sharpCount = 3;
        var song = buildSong(line -> line.addElement(finalDoubleBarline()));
        song.withoutMutationTracking(() -> {
            song.setDefaultKeyType(KeyType.SHARPS);
            song.setDefaultKeyAccidentalCount(sharpCount);
        });

        var song2 = roundTrip(song);
        assertPopulatedSubsetEquals(song, song2);
    }

    @Test
    void testKeySignatureWithFlatsRoundTrips() throws Exception {
        final var flatCount = 2;
        var song = buildSong(line -> line.addElement(finalDoubleBarline()));
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
            line -> line.addElement(repeatRight()),
            line -> {
                line.addElement(repeatLeft());
                line.addElement(repeatRight());
            }
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("repeat-right bleed regression song must be schema-valid")
            .doesNotThrowAnyException();
    }
}
