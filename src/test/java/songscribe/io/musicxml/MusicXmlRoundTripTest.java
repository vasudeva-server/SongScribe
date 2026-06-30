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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.assertj.core.data.Offset;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.KeyType;
import songscribe.dom.Line;
import songscribe.dom.ScaleContext;
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

    // -- Phase 6b: note round-trip helpers and tests --

    /**
     * Staffposition for F4 (the first sharp in the key-of-one-sharp / G-major key).
     * B4 = staffPosition 0 increasing downward: A4=1, G4=2, F4=3.
     */
    private static final int F4_STAFF_POSITION = 3;

    /**
     * A non-zero X offset (in pixels) that survives the px → ss → tenths → ss → px
     * round-trip without rounding loss.  Any integer number of pixels is exact at the
     * default scale (8 px per staff space), so 2 staff spaces = 16 px is a clean choice.
     */
    private static final int X_OFFSET_PX = ScaleContext.ssToRoundedPx(2.0);

    /**
     * The number of sharps in G major (one sharp = F#).
     * Used in the {@code <alter>} divergence tests.
     */
    private static final int G_MAJOR_SHARP_COUNT = 1;

    /** The lattice origin: B4 lives at staffPosition 0. */
    private static final int B4_STAFF_POSITION = 0;

    /**
     * Staff position of C4 — six steps below the B4 origin (staffPosition 0) in the
     * descending-pitch lattice.
     */
    private static final int C4_STAFF_POSITION = 6;

    // Glissando cached geometry used by the slide-endpoint output test.
    // A horizontal slide: starts at (5, −2) staff-space units, runs 10 ss at 0°.
    private static final double SLIDE_START_X_SS = 5.0;
    private static final double SLIDE_START_Y_SS = -2.0;
    private static final double SLIDE_LENGTH_SS  = 10.0;
    private static final double SLIDE_COS        = 1.0;
    private static final double SLIDE_SIN        = 0.0;

    // A diagonal slide (3-4-5 triangle) so the endpoint test exercises BOTH the
    // cos (X) and sin (Y) terms — the horizontal case above zeroes the Y term.
    private static final double DIAGONAL_SLIDE_COS = 0.6;
    private static final double DIAGONAL_SLIDE_SIN = 0.8;

    /**
     * Compares the MusicXML-round-trip-preserved fields of {@code expected} and
     * {@code actual} note elements, asserting field-by-field equality.
     * Does <em>not</em> add {@code equals()}/{@code hashCode()} to
     * {@link StaffElement}: that would break identity-based hash collections and
     * recurse via the {@code line}/parent back-references.
     * <p>
     * Breath-mark is a separate line element (not a per-note field), so it is
     * checked at the line level in the dedicated breath-mark test.
     */
    private static void assertNoteEquals(StaffElement expected, StaffElement actual) {
        assertNoteEquals(expected, actual, "note");
    }

    /**
     * As {@link #assertNoteEquals(StaffElement, StaffElement)}, but every field
     * assertion is prefixed with {@code context} so a failure inside a loop names
     * the offending case (e.g. the note type or accidental under test).
     */
    private static void assertNoteEquals(StaffElement expected, StaffElement actual, String context) {
        assertThat(actual.getType())
            .as("%s: type", context)
            .isEqualTo(expected.getType());
        assertThat(actual.getStaffPosition())
            .as("%s: staffPosition", context)
            .isEqualTo(expected.getStaffPosition());
        assertThat(actual.getDotCount())
            .as("%s: dotCount", context)
            .isEqualTo(expected.getDotCount());
        assertThat(actual.getAccidental())
            .as("%s: accidental", context)
            .isEqualTo(expected.getAccidental());
        assertThat(actual.isAccidentalInParentheses())
            .as("%s: accidentalInParentheses", context)
            .isEqualTo(expected.isAccidentalInParentheses());
        assertThat(actual.isUpper())
            .as("%s: upper", context)
            .isEqualTo(expected.isUpper());
        assertThat(actual.isStemDirectionAuto())
            .as("%s: stemDirectionAuto", context)
            .isEqualTo(expected.isStemDirectionAuto());
        assertThat(actual.getXOffsetPx())
            .as("%s: xOffsetPx", context)
            .isEqualTo(expected.getXOffsetPx());
        assertThat(actual.hasGlissando())
            .as("%s: glissando", context)
            .isEqualTo(expected.hasGlissando());
        assertThat(actual.hasFall())
            .as("%s: fall", context)
            .isEqualTo(expected.hasFall());

        for (var articulationType : ArticulationType.values()) {
            assertThat(actual.hasArticulation(articulationType))
                .as("%s: hasArticulation(%s)", context, articulationType)
                .isEqualTo(expected.hasArticulation(articulationType));
        }

        // Counts guard against a write/read bug that duplicates an articulation or
        // attachment: presence-only checks would pass while the model is corrupt.
        assertThat(actual.getArticulations().size())
            .as("%s: articulation count", context)
            .isEqualTo(expected.getArticulations().size());
        assertThat(actual.getAttachments().size())
            .as("%s: attachment count", context)
            .isEqualTo(expected.getAttachments().size());

        var expectedFermata = expected.findAttachment(FermataAttachment.class);
        var actualFermata = actual.findAttachment(FermataAttachment.class);

        if (expectedFermata == null) {
            assertThat(actualFermata).as("%s: fermata", context).isNull();
        } else {
            assertThat(actualFermata).as("%s: fermata", context).isNotNull();
        }

        var expectedDynamic = expected.findAttachment(DynamicAttachment.class);
        var actualDynamic = actual.findAttachment(DynamicAttachment.class);

        if (expectedDynamic == null) {
            assertThat(actualDynamic).as("%s: dynamics", context).isNull();
        } else {
            assertThat(actualDynamic).as("%s: dynamics", context).isNotNull();

            if (actualDynamic != null) {
                assertThat(actualDynamic.getType())
                    .as("%s: dynamics type", context)
                    .isEqualTo(expectedDynamic.getType());
            }
        }
    }

    /**
     * Parses the emitted MusicXML string and returns the {@code <alter>} value from
     * the first {@code <pitch>} element, or {@code 0} when no {@code <alter>} element
     * is present (MusicXML omits {@code <alter>} for natural/unaltered pitches).
     */
    private static int extractAlterFromFirstNote(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new InputSource(new StringReader(xml)));
        var alterNodes = doc.getElementsByTagName("alter");

        if (alterNodes.getLength() == 0) {
            return 0;
        }

        return Integer.parseInt(alterNodes.item(0).getTextContent().trim());
    }

    // -- Task 1: durations, rests, grace, dot counts, accidentals, stem, X offset --

    @Test
    void testAllNoteDurationsRoundTrip() throws Exception {
        var noteTypes = List.of(
            ElementType.SEMIBREVE,
            ElementType.MINIM,
            ElementType.CROTCHET,
            ElementType.QUAVER,
            ElementType.SEMIQUAVER,
            ElementType.DEMI_SEMIQUAVER
        );

        for (var noteType : noteTypes) {
            var song = buildSong(line -> line.addElement(noteType.newInstance()));
            var song2 = roundTrip(song);
            var expected = song.getLine(0).getElement(0);
            var actual = song2.getLine(0).getElement(0);
            assertNoteEquals(expected, actual, "duration " + noteType);
        }
    }

    @Test
    void testAllRestTypesRoundTrip() throws Exception {
        var restTypes = List.of(
            ElementType.SEMIBREVE_REST,
            ElementType.MINIM_REST,
            ElementType.CROTCHET_REST,
            ElementType.QUAVER_REST,
            ElementType.SEMIQUAVER_REST,
            ElementType.DEMI_SEMIQUAVER_REST
        );

        for (var restType : restTypes) {
            var song = buildSong(line -> line.addElement(restType.newInstance()));
            var song2 = roundTrip(song);
            var expected = song.getLine(0).getElement(0);
            var actual = song2.getLine(0).getElement(0);
            assertNoteEquals(expected, actual, "rest " + restType);
        }
    }

    @Test
    void testGraceNoteRoundTrips() throws Exception {
        // Grace notes always emit <stem>up</stem> regardless of the model's stemDirectionAuto.
        // Set upper=true, stemDirectionAuto=false on the expected note so assertNoteEquals passes
        // when compared to the round-tripped note (which gets stemDirectionAuto=false from the reader).
        var song = buildSong(line -> {
            var grace = ElementType.GRACE_QUAVER.newInstance();
            grace.setUpper(true);
            grace.setStemDirectionAuto(false);
            line.addElement(grace);
            line.addElement(ElementType.CROTCHET.newInstance());
        });

        var song2 = roundTrip(song);

        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
        assertNoteEquals(
            song.getLine(0).getElement(1),
            song2.getLine(0).getElement(1)
        );
    }

    @Test
    void testDotCountsRoundTrip() throws Exception {
        for (var dotCount = 0; dotCount <= NoteTypeMapping.MAX_DOT_COUNT; dotCount++) {
            final var finalDotCount = dotCount;
            var song = buildSong(line -> {
                var note = ElementType.MINIM.newInstance();
                note.setDotCount(finalDotCount);
                line.addElement(note);
            });

            var song2 = roundTrip(song);
            var expected = song.getLine(0).getElement(0);
            var actual = song2.getLine(0).getElement(0);
            assertNoteEquals(expected, actual);
        }
    }

    @Test
    void testAllAccidentalsRoundTrip() throws Exception {
        // DOUBLE_NATURAL has no MusicXML mapping and is intentionally excluded.
        var accidentals = List.of(
            StaffElement.Accidental.NATURAL,
            StaffElement.Accidental.FLAT,
            StaffElement.Accidental.SHARP,
            StaffElement.Accidental.DOUBLE_FLAT,
            StaffElement.Accidental.DOUBLE_SHARP,
            StaffElement.Accidental.NATURAL_FLAT,
            StaffElement.Accidental.NATURAL_SHARP
        );

        for (var accidental : accidentals) {
            var song = buildSong(line -> {
                var note = ElementType.CROTCHET.newInstance();
                note.setAccidental(accidental);
                line.addElement(note);
            });

            var song2 = roundTrip(song);
            var expected = song.getLine(0).getElement(0);
            var actual = song2.getLine(0).getElement(0);
            assertNoteEquals(expected, actual, "accidental " + accidental);
        }
    }

    @Test
    void testParenthesizedAccidentalRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(StaffElement.Accidental.SHARP);
            note.setAccidentalInParentheses(true);
            line.addElement(note);
        });

        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    @Test
    void testManualStemUpRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(true);
            note.setStemDirectionAuto(false);
            line.addElement(note);
        });

        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    @Test
    void testManualStemDownRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setUpper(false);
            note.setStemDirectionAuto(false);
            line.addElement(note);
        });

        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    @Test
    void testAutoStemDirectionRoundTrips() throws Exception {
        // Default new instance has stemDirectionAuto=true; verify it survives round-trip.
        var song = buildSong(line -> line.addElement(ElementType.CROTCHET.newInstance()));
        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    @Test
    void testNoteXOffsetRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setXOffsetPx(X_OFFSET_PX);
            line.addElement(note);
        });

        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    // -- Task 2: fermata, dynamics, accent, staccato, glissando, fall, breath-mark --

    @Test
    void testFermataRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            line.addElement(note);
            note.addAttachment(new FermataAttachment(note));
        });

        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    @Test
    void testDynamicsRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            line.addElement(note);
            note.addAttachment(new DynamicAttachment(note, DynamicAttachment.DynamicType.FORTE));
        });

        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    @Test
    void testAccentArticulationRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            line.addElement(note);
            note.addArticulation(new Articulation(note, ArticulationType.ACCENT));
        });

        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    @Test
    void testStaccatoArticulationRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            line.addElement(note);
            note.addArticulation(new Articulation(note, ArticulationType.STACCATO));
        });

        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    @Test
    void testGlissandoRoundTrips() throws Exception {
        // The glissando is stored on the start note; the stop note carries nothing.
        var song = buildSong(line -> {
            var startNote = ElementType.CROTCHET.newInstance();
            startNote.setGlissando();
            line.addElement(startNote);
            line.addElement(ElementType.CROTCHET.newInstance());
        });

        var song2 = roundTrip(song);

        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
        assertNoteEquals(
            song.getLine(0).getElement(1),
            song2.getLine(0).getElement(1)
        );
    }

    @Test
    void testFallRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setFall();
            line.addElement(note);
        });

        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    @Test
    void testBreathMarkRoundTrips() throws Exception {
        // BREATH_MARK is serialized as <breath-mark/> inside the preceding note's
        // <notations> and skipped as a standalone element by the writer.
        // The reader appends a new BREATH_MARK element after the note.
        var song = buildSong(line -> {
            line.addElement(ElementType.CROTCHET.newInstance());
            line.addElement(ElementType.BREATH_MARK.newInstance());
        });

        var song2 = roundTrip(song);
        var line2 = song2.getLine(0);

        assertThat(line2.getElement(0).getType())
            .as("first element type after breath-mark round-trip")
            .isEqualTo(ElementType.CROTCHET);
        assertThat(line2.getElement(1).getType())
            .as("second element type after breath-mark round-trip")
            .isEqualTo(ElementType.BREATH_MARK);
    }

    // -- Task 3: <alter> / <accidental> divergence paths --

    /**
     * Case (a): a note whose pitch is altered by the key signature, with no explicit
     * accidental glyph.  The writer must emit the correct {@code <alter>} value for
     * external-renderer sounding fidelity; the reader correctly ignores {@code <alter>}
     * and recovers pitch from {@code <step>/<octave>} alone.
     * <p>
     * G major (1 sharp = F#): a note at staffPosition {@value F4_STAFF_POSITION} (F4)
     * with no explicit accidental should carry {@code <alter>1</alter>} in the output.
     */
    @Test
    void testNoteAlteredByKeyHasCorrectAlterInOutput() throws Exception {
        var song = buildSong(line -> {
            line.setKeyType(KeyType.SHARPS);
            line.setKeyAccidentalCount(G_MAJOR_SHARP_COUNT);
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(F4_STAFF_POSITION);
            // No explicit accidental — the key makes it F#.
            line.addElement(note);
        });

        var xml = writeToString(song);
        assertThat(extractAlterFromFirstNote(xml))
            .as("<alter> value for F4 in G major (no explicit accidental)")
            .isEqualTo(1);
    }

    /**
     * Case (a) round-trip: staffPosition and the displayed accidental (none) survive.
     * Pitch sounding-accuracy is covered by the writer-output assertion above;
     * the reader recovers pitch from step/octave, not from {@code <alter>}.
     */
    @Test
    void testNoteAlteredByKeyRoundTrips() throws Exception {
        var song = buildSong(line -> {
            line.setKeyType(KeyType.SHARPS);
            line.setKeyAccidentalCount(G_MAJOR_SHARP_COUNT);
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(F4_STAFF_POSITION);
            line.addElement(note);
        });

        var song2 = roundTrip(song);
        var expected = song.getLine(0).getElement(0);
        var actual = song2.getLine(0).getElement(0);

        assertThat(actual.getStaffPosition())
            .as("staffPosition after round-trip (key-altered note)")
            .isEqualTo(F4_STAFF_POSITION);
        assertThat(actual.getAccidental())
            .as("accidental after round-trip (key-altered note has no glyph)")
            .isNull();
        assertThat(actual.getType())
            .as("type after round-trip")
            .isEqualTo(expected.getType());
    }

    /**
     * Case (b): a cautionary natural — the note has an explicit NATURAL accidental
     * glyph displayed even though the sounding pitch is natural (alter = 0).
     * G major (1 sharp = F#): a note at staffPosition {@value F4_STAFF_POSITION} (F4)
     * with an explicit NATURAL accidental sounds at alter=0 (F natural), not F#.
     * The writer must emit {@code <alter>0</alter>} (or omit {@code <alter>}, since
     * MusicXML omits the element for 0) and {@code <accidental>natural</accidental>}.
     */
    @Test
    void testCautionaryNaturalHasZeroAlterInOutput() throws Exception {
        var song = buildSong(line -> {
            line.setKeyType(KeyType.SHARPS);
            line.setKeyAccidentalCount(G_MAJOR_SHARP_COUNT);
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(F4_STAFF_POSITION);
            note.setAccidental(StaffElement.Accidental.NATURAL);
            line.addElement(note);
        });

        var xml = writeToString(song);
        // The writer omits <alter> when it is 0 (natural), so absence == 0.
        assertThat(extractAlterFromFirstNote(xml))
            .as("<alter> value for cautionary natural (F natural overrides the key F#)")
            .isEqualTo(0);
    }

    /**
     * Case (b) round-trip: staffPosition, the displayed NATURAL accidental glyph,
     * and note type all survive the write → read cycle.
     */
    @Test
    void testCautionaryNaturalRoundTrips() throws Exception {
        var song = buildSong(line -> {
            line.setKeyType(KeyType.SHARPS);
            line.setKeyAccidentalCount(G_MAJOR_SHARP_COUNT);
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(F4_STAFF_POSITION);
            note.setAccidental(StaffElement.Accidental.NATURAL);
            line.addElement(note);
        });

        var song2 = roundTrip(song);
        var expected = song.getLine(0).getElement(0);
        var actual = song2.getLine(0).getElement(0);

        assertThat(actual.getStaffPosition())
            .as("staffPosition after round-trip (cautionary natural)")
            .isEqualTo(F4_STAFF_POSITION);
        assertThat(actual.getAccidental())
            .as("displayed accidental after round-trip (cautionary natural must survive)")
            .isEqualTo(StaffElement.Accidental.NATURAL);
        assertThat(actual.getType())
            .as("type after round-trip")
            .isEqualTo(expected.getType());
    }

    // -- Phase 6c: writer-output edge cases and reader isolation --

    /**
     * Parses {@code xml} via DOM and returns the named attribute value from the first
     * {@code <slide>} element whose {@code type} attribute matches {@code slideType},
     * or {@code null} when no matching slide or no such attribute is found.
     */
    private static @Nullable String slideAttribute(String xml, String slideType, String attrName) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new InputSource(new StringReader(xml)));
        var slides = doc.getElementsByTagName("slide");

        for (var i = 0; i < slides.getLength(); i++) {
            var slide = (Element) slides.item(i);

            if (slideType.equals(slide.getAttribute("type"))) {
                var value = slide.getAttribute(attrName);
                return value.isEmpty() ? null : value;
            }
        }

        return null;
    }

    /**
     * Task 1a — Grace-note writer output: {@code <grace>} is present in the emitted
     * {@code <note>} with no {@code steal-time-following} attribute and no
     * {@code <duration>} child. Round-trip cannot catch this: the reader derives grace
     * from {@code <grace>}/{@code <type>} and never compares a steal value.
     */
    @Test
    void testGraceNoteOutputHasNoStealTimeFollowingAndNoDuration() throws Exception {
        var song = buildSong(line -> {
            line.addElement(ElementType.GRACE_QUAVER.newInstance());
            line.addElement(ElementType.CROTCHET.newInstance());
        });

        var xml = writeToString(song);
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new InputSource(new StringReader(xml)));

        // The first <note> in document order is the grace note.
        var notes = doc.getElementsByTagName("note");
        assertThat(notes.getLength()).as("must have at least one note element").isGreaterThan(0);

        var graceNote = (Element) notes.item(0);
        var graceElements = graceNote.getElementsByTagName("grace");
        assertThat(graceElements.getLength())
            .as("<grace> element must be present in the grace note")
            .isEqualTo(1);

        var graceElement = (Element) graceElements.item(0);
        assertThat(graceElement.hasAttribute("steal-time-following"))
            .as("<grace> must not carry steal-time-following")
            .isFalse();

        assertThat(graceNote.getElementsByTagName("duration").getLength())
            .as("<duration> must be absent in a grace note")
            .isEqualTo(0);
    }

    /**
     * Task 1b — X-offset writer output: the user offset is written as
     * {@code relative-x} (not merged into {@code default-x}), and
     * {@code default-x + relative-x} reproduces the laid-out X in tenths.
     * Round-trip cannot catch this: the reader ignores {@code default-x}.
     */
    @Test
    void testNoteXOffsetIsWrittenAsRelativeXAndDefaultXIsBase() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setXOffsetPx(X_OFFSET_PX);
            line.addElement(note);
        });

        var xml = writeToString(song);
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new InputSource(new StringReader(xml)));

        var notes = doc.getElementsByTagName("note");
        var noteElement = (Element) notes.item(0);

        // The user offset must land in relative-x; default-x carries the base layout X.
        assertThat(noteElement.hasAttribute("relative-x"))
            .as("<note> must carry relative-x when xOffsetPx is non-zero")
            .isTrue();

        var defaultX = Double.parseDouble(noteElement.getAttribute("default-x"));
        var relativeX = Double.parseDouble(noteElement.getAttribute("relative-x"));
        var expectedOffsetTenths = ScaleContext.pxToSs(X_OFFSET_PX) * MusicXmlTags.TENTHS_PER_STAFF_SPACE;

        // relative-x encodes the user-set offset in tenths.
        assertThat(relativeX)
            .as("relative-x must equal the user offset in tenths")
            .isCloseTo(expectedOffsetTenths, Offset.offset(0.01));

        // default-x is the base layout position; for an unlaid-out note getXSs() == 0.
        assertThat(defaultX)
            .as("default-x must equal the base layout position (0 before layout)")
            .isCloseTo(0.0, Offset.offset(0.01));

        // An external renderer sums both to produce the visual position.
        assertThat(defaultX + relativeX)
            .as("default-x + relative-x must reproduce the full laid-out X")
            .isCloseTo(expectedOffsetTenths, Offset.offset(0.01));
    }

    /**
     * Task 1c — Slide-endpoint writer output: when a glissando has cached render
     * geometry, the emitted {@code <slide>} elements carry {@code default-x} /
     * {@code default-y} matching the computed start and end points. Round-trip
     * cannot catch this: the reader ignores slide coordinate attributes.
     */
    @Test
    @SuppressWarnings("NullAway")
    void testGlissandoSlideEndpointsInOutput() throws Exception {
        var song = buildSong(line -> {
            var startNote = ElementType.CROTCHET.newInstance();
            startNote.setGlissando();

            // Populate the transient cached geometry the writer uses for default-x/y.
            var glissando = startNote.getGlissando();
            glissando.cachedStartX      = SLIDE_START_X_SS;
            glissando.cachedStartY      = SLIDE_START_Y_SS;
            glissando.cachedLength      = SLIDE_LENGTH_SS;
            glissando.cachedCos         = SLIDE_COS;
            glissando.cachedSin         = SLIDE_SIN;
            glissando.hasCachedGeometry = true;

            line.addElement(startNote);
            line.addElement(ElementType.CROTCHET.newInstance());
        });

        var xml = writeToString(song);

        var expectedStartXTenths = SLIDE_START_X_SS * MusicXmlTags.TENTHS_PER_STAFF_SPACE;
        var expectedStartYTenths = SLIDE_START_Y_SS * MusicXmlTags.TENTHS_PER_STAFF_SPACE;
        var expectedStopXTenths  = (SLIDE_START_X_SS + SLIDE_LENGTH_SS * SLIDE_COS) * MusicXmlTags.TENTHS_PER_STAFF_SPACE;
        var expectedStopYTenths  = (SLIDE_START_Y_SS + SLIDE_LENGTH_SS * SLIDE_SIN) * MusicXmlTags.TENTHS_PER_STAFF_SPACE;

        // Start slide: default-x/y at the beginning of the glissando line.
        var startDefaultX = slideAttribute(xml, "start", "default-x");
        var startDefaultY = slideAttribute(xml, "start", "default-y");
        assertThat(startDefaultX).as("slide[start] must carry default-x").isNotNull();
        assertThat(startDefaultY).as("slide[start] must carry default-y").isNotNull();
        assertThat(Double.parseDouble(startDefaultX))
            .as("slide[start] default-x must match the computed start X")
            .isCloseTo(expectedStartXTenths, Offset.offset(0.01));
        assertThat(Double.parseDouble(startDefaultY))
            .as("slide[start] default-y must match the computed start Y")
            .isCloseTo(expectedStartYTenths, Offset.offset(0.01));

        // Stop slide: default-x/y at the end of the glissando line.
        var stopDefaultX = slideAttribute(xml, "stop", "default-x");
        var stopDefaultY = slideAttribute(xml, "stop", "default-y");
        assertThat(stopDefaultX).as("slide[stop] must carry default-x").isNotNull();
        assertThat(stopDefaultY).as("slide[stop] must carry default-y").isNotNull();
        assertThat(Double.parseDouble(stopDefaultX))
            .as("slide[stop] default-x must match the computed end X")
            .isCloseTo(expectedStopXTenths, Offset.offset(0.01));
        assertThat(Double.parseDouble(stopDefaultY))
            .as("slide[stop] default-y must match the computed end Y")
            .isCloseTo(expectedStopYTenths, Offset.offset(0.01));
    }

    /**
     * Task 2 — Dangling {@code <slide type="start">} reader handling: when no matching
     * {@code type="stop"} arrives (e.g. a truncated file), the reader drops the pending
     * start at part-end, the note has no glissando, and parsing completes without error.
     */
    @Test
    void testDanglingSlideStartProducesNoGlissando() throws Exception {
        // Hand-crafted MusicXML: one note with a slide start, no matching stop.
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <print new-system=\"yes\"/>\n" +
            "      <attributes>\n" +
            "        <divisions>480</divisions>\n" +
            "        <key><fifths>0</fifths></key>\n" +
            "        <time print-object=\"no\"><senza-misura/></time>\n" +
            "        <clef><sign>G</sign><line>2</line></clef>\n" +
            "      </attributes>\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><slide type=\"start\" line-type=\"solid\"/></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n" +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";

        var song = parse(xml);

        assertThat(song.lineCount()).as("must have one line").isEqualTo(1);
        assertThat(song.getLine(0).getElements()).as("line must have exactly one note").hasSize(1);
        assertThat(song.getLine(0).getElement(0).hasGlissando())
            .as("dangling slide start must leave the note without a glissando")
            .isFalse();
    }

    /**
     * Task 3 — {@code characters()} isolation: the reader accumulates text
     * unconditionally and clears the buffer at every {@code startElement}, so text
     * from one leaf element does not bleed into the next.
     * <p>
     * Scenario: {@code <step>C</step><alter>99</alter><octave>4</octave>} in a
     * compact, whitespace-free encoding. Without the clear-on-{@code startElement}
     * reset, the "99" from {@code <alter>} would remain in the buffer when
     * {@code <octave>} fires {@code characters("4")}, accumulating "994"; then
     * {@code parseInt("994")} would yield 994 instead of 4, and the staff position
     * would be wrong (or the parse would throw). With the reset, {@code <octave>}
     * starts with an empty buffer, so octave == 4 and staffPosition == C4.
     */
    @Test
    void testCharactersAccumulationDoesNotBleedAcrossLeafElements() throws Exception {
        // Compact encoding: no whitespace between adjacent pitch child elements,
        // so any buffer-bleed would concatenate directly (no trim salvation).
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <print new-system=\"yes\"/>\n" +
            "      <attributes>\n" +
            "        <divisions>480</divisions>\n" +
            "        <key><fifths>0</fifths></key>\n" +
            "        <time print-object=\"no\"><senza-misura/></time>\n" +
            "        <clef><sign>G</sign><line>2</line></clef>\n" +
            "      </attributes>\n" +
            "      <note>" +
                "<pitch><step>C</step><alter>99</alter><octave>4</octave></pitch>" +
                "<duration>480</duration>" +
                "<type>quarter</type>" +
            "</note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n" +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";

        var song = parse(xml);

        assertThat(song.lineCount()).as("must have one line").isEqualTo(1);
        assertThat(song.getLine(0).getElements()).as("line must have exactly one note").hasSize(1);

        var note = song.getLine(0).getElement(0);
        assertThat(note.getType())
            .as("note type must be CROTCHET")
            .isEqualTo(ElementType.CROTCHET);
        assertThat(note.getStaffPosition())
            .as("staff position must be C4 (%d) — not contaminated by <alter>99</alter> text", C4_STAFF_POSITION)
            .isEqualTo(C4_STAFF_POSITION);
    }

    // Phase 4 spot-check: a crotchet (quarter note) on B4 produces schema-valid output.
    @Test
    void testSingleNoteWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> line.addElement(ElementType.CROTCHET.newInstance())
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("single-note song must be schema-valid")
            .doesNotThrowAnyException();
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

    // -- Phase 6a: populated note output schema validation --

    @Test
    void testRestWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> line.addElement(ElementType.CROTCHET_REST.newInstance())
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("song with a quarter rest must be schema-valid")
            .doesNotThrowAnyException();
    }

    @Test
    void testGraceNoteWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> {
                line.addElement(ElementType.GRACE_QUAVER.newInstance());
                line.addElement(ElementType.CROTCHET.newInstance());
            }
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("song with a grace note followed by a quarter note must be schema-valid")
            .doesNotThrowAnyException();
    }

    @Test
    void testDottedNoteWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setDotCount(1);
            line.addElement(note);
        });

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("song with a dotted quarter note must be schema-valid")
            .doesNotThrowAnyException();
    }

    @Test
    void testNoteWithAccidentalWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(StaffElement.Accidental.SHARP);
            line.addElement(note);
        });

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("song with a note carrying a sharp accidental must be schema-valid")
            .doesNotThrowAnyException();
    }

    // -- Hand-crafted-document helpers --

    /**
     * Wraps {@code measureBody} in a minimal score-partwise document with a
     * new-system {@code <print>} (so the reader starts a line) and standard
     * attributes. Used by the reader edge-case and error-path tests below.
     */
    private static String scoreWithMeasureBody(String measureBody) {
        return
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <print new-system=\"yes\"/>\n" +
            "      <attributes>\n" +
            "        <divisions>480</divisions>\n" +
            "        <key><fifths>0</fifths></key>\n" +
            "        <time print-object=\"no\"><senza-misura/></time>\n" +
            "        <clef><sign>G</sign><line>2</line></clef>\n" +
            "      </attributes>\n" +
            measureBody +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";
    }

    /**
     * Returns the text content of the first {@code <childTag>} element in document
     * order, or {@code null} if none is present.
     */
    private static @Nullable String firstElementText(String xml, String childTag) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new InputSource(new StringReader(xml)));
        var nodes = doc.getElementsByTagName(childTag);

        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent().trim();
    }

    // -- Direct DOM pitch-spelling anchors (round-trip alone can't catch a uniform
    //    lattice shift, since both directions would shift together) --

    @Test
    void testWriterEmitsB4ForOriginStaffPosition() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(B4_STAFF_POSITION);
            line.addElement(note);
        });

        var xml = writeToString(song);
        assertThat(firstElementText(xml, "step")).as("<step> for the B4 origin").isEqualTo("B");
        assertThat(firstElementText(xml, "octave")).as("<octave> for the B4 origin").isEqualTo("4");
    }

    @Test
    void testWriterEmitsC4ForC4StaffPosition() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(C4_STAFF_POSITION);
            line.addElement(note);
        });

        var xml = writeToString(song);
        assertThat(firstElementText(xml, "step")).as("<step> for C4").isEqualTo("C");
        assertThat(firstElementText(xml, "octave")).as("<octave> for C4").isEqualTo("4");
    }

    // -- DOUBLE_NATURAL: no MusicXML mapping → silently skipped on write --

    @Test
    void testDoubleNaturalAccidentalIsSkippedOnWriteAndRoundTripsToNull() throws Exception {
        var song = buildSong(line -> {
            var note = ElementType.CROTCHET.newInstance();
            note.setAccidental(StaffElement.Accidental.DOUBLE_NATURAL);
            line.addElement(note);
        });

        var xml = writeToString(song);

        var factory = DocumentBuilderFactory.newInstance();
        var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        assertThat(doc.getElementsByTagName("accidental").getLength())
            .as("DOUBLE_NATURAL must emit no <accidental> element")
            .isEqualTo(0);

        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("a DOUBLE_NATURAL song must remain schema-valid")
            .doesNotThrowAnyException();

        var song2 = parse(xml);
        assertThat(song2.getLine(0).getElement(0).getAccidental())
            .as("a skipped accidental round-trips back to null")
            .isNull();
    }

    // -- Reader tolerance: stray/unknown tokens are ignored, not fatal --

    @Test
    void testStraySlideStopIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><slide type=\"stop\" line-type=\"solid\"/></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.getLine(0).getElements()).as("exactly one note").hasSize(1);
        assertThat(song.getLine(0).getElement(0).hasGlissando())
            .as("a slide stop with no pending start must leave the note without a glissando")
            .isFalse();
    }

    @Test
    void testUnknownAccidentalTokenIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <accidental>bogus</accidental>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.getLine(0).getElement(0).getAccidental())
            .as("an unrecognised <accidental> token must leave the note with no accidental")
            .isNull();
    }

    @Test
    void testUnknownDynamicSymbolIsIgnored() throws Exception {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "        <notations><dynamics><xyz/></dynamics></notations>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        var song = parse(xml);
        assertThat(song.getLine(0).getElement(0).findAttachment(DynamicAttachment.class))
            .as("an unrecognised <dynamics> symbol must produce no DynamicAttachment")
            .isNull();
    }

    // -- Reader error paths: malformed documents must throw, not silently corrupt --

    @Test
    void testNoteMissingTypeThrows() {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("type");
    }

    @Test
    void testUnrecognisedTypeTokenThrows() {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>bogus</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("Unrecognised");
    }

    @Test
    void testMalformedOctaveThrows() {
        var xml = scoreWithMeasureBody(
            "      <note>\n" +
            "        <pitch><step>B</step><octave>xyz</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("octave");
    }

    @Test
    void testMalformedRelativeXThrows() {
        var xml = scoreWithMeasureBody(
            "      <note relative-x=\"abc\">\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "      <barline location=\"right\"><bar-style>none</bar-style></barline>\n"
        );

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("relative-x");
    }

    @Test
    void testNoteBeforeAnyLineThrows() {
        // A <note> in a measure that never opened a line (no <print new-system>):
        // currentLine is null when </note> assembles the note.
        var xml =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<score-partwise version=\"4.0\">\n" +
            "  <part-list>\n" +
            "    <score-part id=\"P1\"><part-name></part-name></score-part>\n" +
            "  </part-list>\n" +
            "  <part id=\"P1\">\n" +
            "    <measure number=\"1\">\n" +
            "      <note>\n" +
            "        <pitch><step>B</step><octave>4</octave></pitch>\n" +
            "        <duration>480</duration>\n" +
            "        <type>quarter</type>\n" +
            "      </note>\n" +
            "    </measure>\n" +
            "  </part>\n" +
            "</score-partwise>\n";

        assertThatThrownBy(() -> parse(xml))
            .isInstanceOf(SAXException.class)
            .hasMessageContaining("before any line");
    }

    // -- Diagonal glissando: exercises BOTH the cos (X) and sin (Y) endpoint terms --

    @Test
    @SuppressWarnings("NullAway")
    void testDiagonalGlissandoSlideEndpointsInOutput() throws Exception {
        var song = buildSong(line -> {
            var startNote = ElementType.CROTCHET.newInstance();
            startNote.setGlissando();

            var glissando = startNote.getGlissando();
            glissando.cachedStartX      = SLIDE_START_X_SS;
            glissando.cachedStartY      = SLIDE_START_Y_SS;
            glissando.cachedLength      = SLIDE_LENGTH_SS;
            glissando.cachedCos         = DIAGONAL_SLIDE_COS;
            glissando.cachedSin         = DIAGONAL_SLIDE_SIN;
            glissando.hasCachedGeometry = true;

            line.addElement(startNote);
            line.addElement(ElementType.CROTCHET.newInstance());
        });

        var xml = writeToString(song);

        var expectedStopXTenths = (SLIDE_START_X_SS + SLIDE_LENGTH_SS * DIAGONAL_SLIDE_COS) * MusicXmlTags.TENTHS_PER_STAFF_SPACE;
        var expectedStopYTenths = (SLIDE_START_Y_SS + SLIDE_LENGTH_SS * DIAGONAL_SLIDE_SIN) * MusicXmlTags.TENTHS_PER_STAFF_SPACE;

        var stopDefaultX = slideAttribute(xml, "stop", "default-x");
        var stopDefaultY = slideAttribute(xml, "stop", "default-y");
        assertThat(stopDefaultX).as("diagonal slide[stop] must carry default-x").isNotNull();
        assertThat(stopDefaultY).as("diagonal slide[stop] must carry default-y").isNotNull();
        assertThat(Double.parseDouble(stopDefaultX))
            .as("diagonal slide[stop] default-x must include the cos term")
            .isCloseTo(expectedStopXTenths, Offset.offset(0.01));
        assertThat(Double.parseDouble(stopDefaultY))
            .as("diagonal slide[stop] default-y must include the sin term (non-zero here)")
            .isCloseTo(expectedStopYTenths, Offset.offset(0.01));
    }
}
