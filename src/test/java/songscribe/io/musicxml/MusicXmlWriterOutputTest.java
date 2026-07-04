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

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilderFactory;

import org.assertj.core.data.Offset;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.ElementType;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.layout.Ending;
import songscribe.Constants;

/**
 * Writer-output fidelity cases: assertions on the emitted MusicXML that the
 * {@code Song → MusicXML → Song} round-trip cannot catch (the reader ignores the
 * attribute or element under test), plus the {@code *WriterOutputIsSchemaValid}
 * schema checks for populated notes.
 */
class MusicXmlWriterOutputTest extends MusicXmlRoundTripSupport {

    // MusicXML coordinate unit: 1 staff space = 10 tenths.

    /** The lattice origin: B4 lives at staffPosition 0. */
    private static final int B4_STAFF_POSITION = 0;

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

    /** Grade of the triplet tuplet in the all-span schema case. */
    private static final int ALL_SPAN_TUPLET_GRADE = 3;

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

    /**
     * Returns the untrimmed text content of the first {@code <childTag>} descendant
     * of {@code lyric}, or {@code null} if none is present. Unlike
     * {@link #firstElementText}, this does not trim: the compound-word marker
     * (U+2011) appended to {@code <text>} is not whitespace, but trimming is
     * avoided here on principle since a lyric's exact text content is the point
     * under test.
     */
    private static @Nullable String lyricChildText(Element lyric, String childTag) {
        var nodes = lyric.getElementsByTagName(childTag);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
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

    // -- Phase 7c: all-span schema validation --

    /**
     * A song populated with all six line-level range spans must emit MusicXML
     * that validates against the 4.0 schema. This exercises the {@code <note>},
     * {@code <notations>}, {@code <direction>}, and {@code <barline>} child-order
     * rules together — every span feeds a different one of those positions:
     * <ul>
     *   <li>{@code Beam} → note-level {@code <beam>}</li>
     *   <li>{@code Tie} → note-level {@code <tie>} + {@code <notations><tied></li>
     *   <li>{@code Tuplet} → note-level {@code <time-modification>} + {@code <notations><tuplet></li>
     *   <li>{@code Trill} → {@code <notations><ornaments></li>
     *   <li>{@code Crescendo}/{@code Diminuendo} → measure-level {@code <direction><wedge></li>
     *   <li>{@code Ending} → {@code <barline><ending></li>
     * </ul>
     *
     * <p>Line layout (indices):
     * <pre>
     *   REPEAT_LEFT(0) Q(1) Q(2) C(3) C(4) REPEAT_RIGHT(5) C(6) C(7) C(8) FINAL(9)
     * </pre>
     * Beam[1,2] and Crescendo[1,2] share the two quavers; Tie[3,4] and Trill[3,4]
     * share the two crotchets; Tuplet[6,8] and Diminuendo[6,8] share the triplet;
     * the Ending spans REPEAT_LEFT(0)→FINAL(9) split at REPEAT_RIGHT(5). The two
     * hairpins never overlap (the first closes before the second opens).
     */
    @Test
    void testAllSpansWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(line -> {
            var endingAnchor = ElementType.REPEAT_LEFT.newInstance();
            var quaver0 = ElementType.QUAVER.newInstance();
            var quaver1 = ElementType.QUAVER.newInstance();
            var crotchet0 = ElementType.CROTCHET.newInstance();
            var crotchet1 = ElementType.CROTCHET.newInstance();
            var split = ElementType.REPEAT_RIGHT.newInstance();
            var triplet0 = ElementType.CROTCHET.newInstance();
            var triplet1 = ElementType.CROTCHET.newInstance();
            var triplet2 = ElementType.CROTCHET.newInstance();
            var endingEnd = ElementType.FINAL_DOUBLE_BARLINE.newInstance();

            line.addElement(endingAnchor);
            line.addElement(quaver0);
            line.addElement(quaver1);
            line.addElement(crotchet0);
            line.addElement(crotchet1);
            line.addElement(split);
            line.addElement(triplet0);
            line.addElement(triplet1);
            line.addElement(triplet2);
            line.addElement(endingEnd);

            line.addBeaming(new Beam(quaver0, quaver1));
            line.addCrescendo(new Crescendo(quaver0, quaver1));
            line.addTie(new Tie(crotchet0, crotchet1));
            line.addRangeElement(new Trill(crotchet0, crotchet1));
            line.addTuplet(new Tuplet(triplet0, triplet2, ALL_SPAN_TUPLET_GRADE));
            line.addDiminuendo(new Diminuendo(triplet0, triplet2));
            line.addRangeElement(new Ending(endingAnchor, endingEnd));
        });

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("a song with all six span types must be schema-valid")
            .doesNotThrowAnyException();
    }

    // -- Phase 6, Task 2b: lyric writer output --

    /**
     * Builds one line with six notes, each carrying a distinct representative
     * {@code <lyric>} form: a plain syllable, a compound-word boundary, a melisma
     * start, a text-less STOP carrier, a text-less CONTINUE carrier, and a note
     * with two verses. Shared by the raw-output shape test and the schema-valid
     * gate so both exercise the same matrix of forms.
     */
    private static Song buildLyricMatrixSong() {
        return buildSong(line -> {
            var plainSyllable = ElementType.CROTCHET.newInstance();
            plainSyllable.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Ky", Lyric.Extend.NONE);
            line.addElement(plainSyllable);

            var compoundSyllable = ElementType.CROTCHET.newInstance();
            compoundSyllable.setLyricForVerse(1, Lyric.Syllabic.BEGIN, true, "self", Lyric.Extend.NONE);
            line.addElement(compoundSyllable);

            var extenderStart = ElementType.CROTCHET.newInstance();
            extenderStart.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "oh", Lyric.Extend.START);
            line.addElement(extenderStart);

            var stopCarrier = ElementType.CROTCHET.newInstance();
            stopCarrier.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
            line.addElement(stopCarrier);

            var continueCarrier = ElementType.CROTCHET.newInstance();
            continueCarrier.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            line.addElement(continueCarrier);

            var multiVerse = ElementType.CROTCHET.newInstance();
            multiVerse.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "one", Lyric.Extend.NONE);
            multiVerse.setLyricForVerse(2, Lyric.Syllabic.SINGLE, false, "two", Lyric.Extend.NONE);
            line.addElement(multiVerse);
        });
    }

    /**
     * Task 2b — Raw lyric output shapes: asserts the emitted {@code <lyric>}
     * element structure for the six representative forms in
     * {@link #buildLyricMatrixSong()}. The round-trip tests confirm the
     * reconstructed {@link Lyric} values; this test instead pins the exact emitted
     * element shape — the carrier's absent {@code <syllabic>}/{@code <text>}, or the
     * raw U+2011 marker character inside {@code <text>} — which a value-level
     * round-trip cannot observe.
     */
    @Test
    void testLyricWriterOutputShapes() throws Exception {
        var xml = writeToString(buildLyricMatrixSong());
        var factory = DocumentBuilderFactory.newInstance();
        var builder = factory.newDocumentBuilder();
        var doc = builder.parse(new InputSource(new StringReader(xml)));
        var notes = doc.getElementsByTagName("note");

        // Note 0: plain syllable — <syllabic>begin</syllabic><text>Ky</text>, no <extend>.
        var plainLyrics = ((Element) notes.item(0)).getElementsByTagName("lyric");
        assertThat(plainLyrics.getLength()).as("plain-syllable note must carry exactly one <lyric>").isEqualTo(1);

        var plainLyric = (Element) plainLyrics.item(0);
        assertThat(plainLyric.getAttribute("number")).as("plain syllable: verse number").isEqualTo("1");
        assertThat(lyricChildText(plainLyric, "syllabic")).as("plain syllable: <syllabic>").isEqualTo("begin");
        assertThat(lyricChildText(plainLyric, "text")).as("plain syllable: <text>").isEqualTo("Ky");
        assertThat(plainLyric.getElementsByTagName("extend").getLength())
            .as("plain syllable: no <extend>")
            .isEqualTo(0);

        // Note 1: compound-word boundary — <text> carries the U+2011 marker.
        var compoundLyric = (Element) ((Element) notes.item(1)).getElementsByTagName("lyric").item(0);
        assertThat(lyricChildText(compoundLyric, "syllabic")).as("compound syllable: <syllabic>").isEqualTo("begin");
        assertThat(lyricChildText(compoundLyric, "text"))
            .as("compound syllable: <text> carries the compound-word marker")
            .isEqualTo("self" + Constants.NON_BREAKING_HYPHEN);

        // Note 2: extender start — <extend type="start"/> after <syllabic>/<text>.
        var extenderLyric = (Element) ((Element) notes.item(2)).getElementsByTagName("lyric").item(0);
        assertThat(lyricChildText(extenderLyric, "syllabic")).as("extender start: <syllabic>").isEqualTo("single");
        assertThat(lyricChildText(extenderLyric, "text")).as("extender start: <text>").isEqualTo("oh");
        var extenderStartTag = (Element) extenderLyric.getElementsByTagName("extend").item(0);
        assertThat(extenderStartTag.getAttribute("type")).as("extender start: <extend type>").isEqualTo("start");

        // Note 3: text-less STOP carrier — no <syllabic>/<text>, only <extend type="stop"/>.
        var stopLyric = (Element) ((Element) notes.item(3)).getElementsByTagName("lyric").item(0);
        assertThat(stopLyric.getElementsByTagName("syllabic").getLength())
            .as("STOP carrier: no <syllabic>")
            .isEqualTo(0);
        assertThat(stopLyric.getElementsByTagName("text").getLength())
            .as("STOP carrier: no <text>")
            .isEqualTo(0);
        var stopExtend = (Element) stopLyric.getElementsByTagName("extend").item(0);
        assertThat(stopExtend.getAttribute("type")).as("STOP carrier: <extend type>").isEqualTo("stop");

        // Note 4: text-less CONTINUE carrier — same shape, type="continue".
        var continueLyric = (Element) ((Element) notes.item(4)).getElementsByTagName("lyric").item(0);
        assertThat(continueLyric.getElementsByTagName("syllabic").getLength())
            .as("CONTINUE carrier: no <syllabic>")
            .isEqualTo(0);
        assertThat(continueLyric.getElementsByTagName("text").getLength())
            .as("CONTINUE carrier: no <text>")
            .isEqualTo(0);
        var continueExtend = (Element) continueLyric.getElementsByTagName("extend").item(0);
        assertThat(continueExtend.getAttribute("type")).as("CONTINUE carrier: <extend type>").isEqualTo("continue");

        // Note 5: multi-verse — two <lyric> children, number="1" then number="2".
        var multiVerseLyrics = ((Element) notes.item(5)).getElementsByTagName("lyric");
        assertThat(multiVerseLyrics.getLength()).as("multi-verse note: two <lyric> children").isEqualTo(2);
        assertThat(((Element) multiVerseLyrics.item(0)).getAttribute("number"))
            .as("multi-verse note: first <lyric number>")
            .isEqualTo("1");
        assertThat(((Element) multiVerseLyrics.item(1)).getAttribute("number"))
            .as("multi-verse note: second <lyric number>")
            .isEqualTo("2");
    }

    /**
     * A note with no lyrics must emit no {@code <lyric>} child at all. This guards
     * the writer's empty-list early return; a value-level round-trip cannot observe
     * it, since an absent {@code <lyric>} and an empty lyric list reload identically.
     */
    @Test
    void testNoteWithoutLyricsEmitsNoLyricElement() throws Exception {
        var song = buildSong(line -> line.addElement(ElementType.CROTCHET.newInstance()));
        var xml = writeToString(song);
        var doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new InputSource(new StringReader(xml)));

        assertThat(doc.getElementsByTagName("lyric").getLength())
            .as("a lyric-free note must emit no <lyric> element")
            .isZero();
    }

    /**
     * Task 2b — Schema-valid gate for lyric-bearing output: {@code roundTrip()}
     * does not auto-validate, so schema conformance (the {@code <lyric>} content
     * model, and its position after {@code <notations>} within {@code <note>}) needs
     * its own assertion, covering the same matrix of forms as
     * {@link #testLyricWriterOutputShapes()}.
     */
    @Test
    void testLyricsWriterOutputIsSchemaValid() throws Exception {
        var xml = writeToString(buildLyricMatrixSong());
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("a song with lyric-bearing notes (plain, compound, extender, carriers, multi-verse) must be schema-valid")
            .doesNotThrowAnyException();
    }
}
