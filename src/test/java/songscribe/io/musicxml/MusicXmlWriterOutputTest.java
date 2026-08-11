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
import static org.mockito.Mockito.mock;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.quaver;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.C4_STAFF_POSITION;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.FIXED_CLOCK_YEAR;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.X_OFFSET_PX;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.build;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.assertj.core.data.Offset;
import org.audiveris.proxymusic.FontStyle;
import org.audiveris.proxymusic.FontWeight;
import org.audiveris.proxymusic.FormattedText;
import org.audiveris.proxymusic.LeftCenterRight;
import org.audiveris.proxymusic.Note;
import org.audiveris.proxymusic.OverUnder;
import org.audiveris.proxymusic.ScorePartwise;
import org.audiveris.proxymusic.Slide;
import org.audiveris.proxymusic.StartStop;
import org.audiveris.proxymusic.StartStopContinue;
import org.audiveris.proxymusic.Syllabic;
import org.audiveris.proxymusic.TextElementData;
import org.audiveris.proxymusic.Tied;
import org.audiveris.proxymusic.TiedType;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import songscribe.Constants;
import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Crescendo;
import songscribe.dom.Diminuendo;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
import songscribe.dom.Lyric;
import songscribe.dom.ScaleContext;
import songscribe.dom.Song;
import songscribe.dom.SongMetadata;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;
import songscribe.font.DocumentFonts;
import songscribe.font.FontKey;
import songscribe.layout.LayoutResult;
import songscribe.layout.LineLayoutProvider;
import songscribe.layout.SlideGeometry;
import songscribe.util.DateUtils;

/**
 * Writer-output fidelity cases: assertions that the {@code Song → MusicXML → Song} round-trip
 * cannot catch (the reader ignores the property under test, or the schema is silent about it),
 * plus the {@code *WriterOutputIsSchemaValid} schema checks for populated notes.
 *
 * <p>Where a case only needs the shape {@link ScorePartwiseBuilder} produces, it asserts
 * directly against the {@link ScorePartwise} graph {@code ScorePartwiseBuilder.build} returns —
 * clearer, and not coupled to how {@code MusicXmlSerializer} happens to format the marshalled
 * text. Schema validity inherently needs the marshalled document, so those gates still go
 * through {@link MusicXmlWriter#writeSong} and {@link MusicXmlSchemaValidator}.
 */
class MusicXmlWriterOutputTest extends UnitTest {

    // MusicXML coordinate unit: 1 staff space = 10 tenths.

    /** The lattice origin: B4 lives at staffPosition 0. */
    private static final int B4_STAFF_POSITION = 0;

    /** The octave both pitch-spelling fixture notes (B4 and C4) belong to. */
    private static final int STAFF_POSITION_OCTAVE = 4;

    // Staff positions for the slide-endpoint output tests. Equal positions give a horizontal
    // glissando (a zero Y term); unequal ones give a diagonal, so both the X and the Y term of
    // the emitted endpoint are exercised.
    //
    // The level fixture puts an accidental on its target so the two notes differ in pitch while
    // sharing a staff position: geometry keys off the staff position, so the line stays level,
    // but a same-pitch glissando is not a state the model may hold and would carry no geometry.
    private static final int LEVEL_GLISSANDO_STAFF_POSITION = 0;
    private static final int DIAGONAL_GLISSANDO_SOURCE_STAFF_POSITION = 4;
    private static final int DIAGONAL_GLISSANDO_TARGET_STAFF_POSITION = -4;

    /** Grade of the triplet tuplet in the all-span schema case. */
    private static final int ALL_SPAN_TUPLET_GRADE = 3;

    /** A triplet occupies the time of two notes of its written value, here undotted crotchets. */
    private static final int ALL_SPAN_TUPLET_NORMAL_NOTES = 2;

    private static final int NO_DOTS = 0;

    // -- Phase 7: credit-matrix song fields, shared by the credit shape tests
    //    and the schema-valid gate --
    private static final String CREDIT_TEST_NUMBER      = "5";
    private static final String CREDIT_TEST_TITLE       = "My Song Title";
    private static final String CREDIT_TEST_SUBTITLE    = "My Subtitle";
    private static final String CREDIT_TEST_COMPOSER    = "A Composer";
    private static final String CREDIT_TEST_LYRICIST    = "A Lyricist";
    private static final String CREDIT_TEST_PLACE       = "New York";
    private static final String COMPOSITION_YEAR        = "1987";
    private static final int COMPOSITION_MONTH          = 12;
    private static final int COMPOSITION_DAY             = 3;
    private static final String LYRICS_YEAR             = "1988";
    private static final int LYRICS_MONTH               = 6;
    private static final int LYRICS_DAY                 = 15;
    private static final double ATTRIBUTION_Y_OFFSET_SS = 3.5;
    private static final String CREDIT_TEST_UNDERLYRICS   = "Under lyrics text";
    private static final String CREDIT_TEST_BANGLA_LYRICS = "Bangla lyrics text";
    private static final String CREDIT_TEST_TRANSLATION   = "Translated lyrics text";
    private static final String CREDIT_TEST_FOOTNOTES     = "Footnote text";

    /** Every {@code <note>} in {@code score}, in document order, across every part and measure. */
    private static List<Note> allNotes(ScorePartwise score) {
        var notes = new ArrayList<Note>();

        for (var part : score.getPart()) {
            for (var measure : part.getMeasure()) {
                for (var item : measure.getNoteOrBackupOrForward()) {
                    if (item instanceof Note note) {
                        notes.add(note);
                    }
                }
            }
        }

        return notes;
    }

    /** The {@code <slide>} of the given {@code type} in {@code note}'s notations, or {@code null}. */
    private static @Nullable Slide findSlide(Note note, StartStop type) {
        for (var notations : note.getNotations()) {
            for (var member : notations.getTiedOrSlurOrTuplet()) {
                if (member instanceof Slide slide && slide.getType() == type) {
                    return slide;
                }
            }
        }

        return null;
    }

    /** The orientation of the {@code <tied>} of the given {@code type} in {@code note}, or {@code null}. */
    private static @Nullable OverUnder tiedOrientation(Note note, TiedType type) {
        for (var notations : note.getNotations()) {
            for (var member : notations.getTiedOrSlurOrTuplet()) {
                if (member instanceof Tied tied && tied.getType() == type) {
                    return tied.getOrientation();
                }
            }
        }

        return null;
    }

    /**
     * The orientation of every {@code <tied>} of the given {@code type} across {@code notes}, in
     * document order. Unlike {@link #tiedOrientation}, this does not stop at the first match, so
     * it can distinguish an interior chain note's two independent tie references from its
     * neighbors'.
     */
    private static List<OverUnder> tiedOrientations(List<Note> notes, TiedType type) {
        var orientations = new ArrayList<OverUnder>();

        for (var note : notes) {
            var orientation = tiedOrientation(note, type);

            if (orientation != null) {
                orientations.add(orientation);
            }
        }

        return orientations;
    }

    /** The {@link Syllabic} choice-list member of {@code lyric}, or {@code null} if absent. */
    private static @Nullable Syllabic syllabicOf(org.audiveris.proxymusic.Lyric lyric) {
        for (var item : lyric.getElisionAndSyllabicAndText()) {
            if (item instanceof Syllabic syllabic) {
                return syllabic;
            }
        }

        return null;
    }

    /** The text of the {@link TextElementData} choice-list member of {@code lyric}, or {@code null}. */
    private static @Nullable String textOf(org.audiveris.proxymusic.Lyric lyric) {
        for (var item : lyric.getElisionAndSyllabicAndText()) {
            if (item instanceof TextElementData text) {
                return text.getValue();
            }
        }

        return null;
    }

    /**
     * The {@code <credit-words>} of the {@code <credit>} whose {@code <credit-type>} equals
     * {@code creditType}, or {@code null} when no such credit is present.
     *
     * <p>{@code Credit.getCreditTypeOrLinkOrBookmark()} is a heterogeneous
     * {@code List<JAXBElement<?>>} — see {@link HeaderBuilder#addCredit} — so this unwraps by
     * the value's own type rather than by the {@code JAXBElement}'s element name.
     */
    private static @Nullable FormattedText creditWordsForType(ScorePartwise score, String creditType) {
        for (var credit : score.getCredit()) {
            String type = null;
            FormattedText words = null;

            for (var item : credit.getCreditTypeOrLinkOrBookmark()) {
                if (item.getValue() instanceof String typeValue) {
                    type = typeValue;
                } else if (item.getValue() instanceof FormattedText wordsValue) {
                    words = wordsValue;
                }
            }

            if (creditType.equals(type)) {
                return words;
            }
        }

        return null;
    }

    /**
     * The {@code <credit-words>} of the {@code <credit>} whose {@code <credit-type>} equals
     * {@code creditType}. Fails the test with a descriptive message when no such credit is
     * present, so call sites can dereference the result without a separate null check.
     */
    private static FormattedText requireCreditWordsForType(ScorePartwise score, String creditType) {
        var words = creditWordsForType(score, creditType);

        assertThat(words).as("%s credit must be present".formatted(creditType)).isNotNull();

        return words;
    }

    /** The set of {@code <credit-type>} text values present in {@code score}. */
    private static Set<String> creditTypesPresent(ScorePartwise score) {
        var types = new HashSet<String>();

        for (var credit : score.getCredit()) {
            for (var item : credit.getCreditTypeOrLinkOrBookmark()) {
                if (item.getValue() instanceof String type) {
                    types.add(type);
                }
            }
        }

        return types;
    }

    /**
     * The text of the {@code <miscellaneous-field>} whose {@code name} attribute equals
     * {@code name}, or {@code null} when no such field is present.
     */
    private static @Nullable String miscFieldValue(ScorePartwise score, String name) {
        for (var field : score.getIdentification().getMiscellaneous().getMiscellaneousField()) {
            if (name.equals(field.getName())) {
                return field.getValue();
            }
        }

        return null;
    }

    /**
     * Builds a song with every credit-affecting field populated: title, number,
     * subtitle, distinct composer/lyricist, {@code isArrangement()=true},
     * distinct composition/lyrics dates, place, a non-zero attribution Y offset,
     * and all four score-below text blocks. Shared by the credit shape tests and
     * the schema-valid gate.
     */
    private static Song buildCreditMatrixSong() {
        var song = buildSong(line -> line.addElement(crotchet()));

        song.setMetadata(new SongMetadata(
            CREDIT_TEST_TITLE,
            CREDIT_TEST_NUMBER,
            CREDIT_TEST_PLACE,
            COMPOSITION_YEAR, COMPOSITION_MONTH, COMPOSITION_DAY,
            CREDIT_TEST_COMPOSER,
            CREDIT_TEST_LYRICIST,
            Song.LyricsSource.LYRICIST,
            true,
            false,
            CREDIT_TEST_SUBTITLE,
            LYRICS_YEAR, LYRICS_MONTH, LYRICS_DAY
        ));

        song.getAttributionElement().setUserYOffsetSs(ATTRIBUTION_Y_OFFSET_SS);

        song.setUnderLyrics(CREDIT_TEST_UNDERLYRICS);
        song.setBanglaLyrics(CREDIT_TEST_BANGLA_LYRICS);
        song.setTranslatedLyrics(CREDIT_TEST_TRANSLATION);
        song.setFootnotes(CREDIT_TEST_FOOTNOTES);

        return song;
    }

    /**
     * Task 1a — Grace-note writer output: {@code <grace>} is present on the built note with no
     * {@code steal-time-following} and no {@code <duration>} child. Round-trip cannot catch this:
     * the reader derives grace from {@code <grace>}/{@code <type>} and never compares a steal
     * value.
     */
    @Test
    void testGraceNoteOutputHasNoStealTimeFollowingAndNoDuration() {
        var song = buildSong(line -> {
            line.addElement(graceQuaver());
            line.addElement(crotchet());
        });

        // The first note in document order is the grace note.
        var graceNote = allNotes(build(song)).get(0);

        assertThat(graceNote.getGrace())
            .as("<grace> must be present on the grace note")
            .isNotNull();
        assertThat(graceNote.getGrace().getStealTimeFollowing())
            .as("<grace> must not carry steal-time-following")
            .isNull();
        assertThat(graceNote.getDuration())
            .as("<duration> must be absent in a grace note")
            .isNull();
    }

    /**
     * Task 1b — X-offset writer output: the user offset is written as
     * {@code relative-x} (not merged into {@code default-x}), and
     * {@code default-x + relative-x} reproduces the laid-out X in tenths.
     * Round-trip cannot catch this: the reader ignores {@code default-x}.
     */
    @Test
    void testNoteXOffsetIsWrittenAsRelativeXAndDefaultXIsBase() {
        var song = buildSong(line -> {
            var note = crotchet();
            note.setXOffsetPx(X_OFFSET_PX);
            line.addElement(note);
        });

        var note = allNotes(build(song)).get(0);
        var expectedOffsetTenths = ScaleContext.pxToSs(X_OFFSET_PX) * MusicXmlTags.TENTHS_PER_STAFF_SPACE;

        // The user offset must land in relative-x; default-x carries the base layout X.
        assertThat(note.getRelativeX())
            .as("<note> must carry relative-x when xOffsetPx is non-zero")
            .isNotNull();
        assertThat(note.getDefaultX())
            .as("<note> must always carry default-x")
            .isNotNull();

        var defaultX = note.getDefaultX().doubleValue();
        var relativeX = note.getRelativeX().doubleValue();

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
     * Slide-endpoint writer output: the {@code <slide>}s the builder attaches carry
     * {@code default-x} / {@code default-y} taken from the glissando's laid-out endpoints —
     * the start of the drawn line on the start slide, its end on the stop slide. Round-trip
     * cannot catch this: the reader ignores slide coordinate attributes.
     * <p>
     * Nothing renders the song here, so this also pins the builder's own layout fallback: before
     * this path existed the coordinates were dropped whenever the line had not been painted.
     */
    @Test
    void testGlissandoSlideEndpointsInOutput() {
        var song = buildGlissandoSong(
            LEVEL_GLISSANDO_STAFF_POSITION,
            LEVEL_GLISSANDO_STAFF_POSITION,
            StaffElement.Accidental.SHARP);
        var notes = allNotes(build(song));
        var endpoints = laidOutGlissandoEndpoints(song);

        assertThat(endpoints.endYSs())
            .as("the fixture must be level, so the zero Y term is genuinely under test")
            .isEqualTo(endpoints.startYSs());

        assertSlideEndpoint(notes.get(0), StartStop.START, endpoints.startXSs(), endpoints.startYSs());
        assertSlideEndpoint(notes.get(1), StartStop.STOP, endpoints.endXSs(), endpoints.endYSs());
    }

    /**
     * A glissando from a grace note into its host note must emit both slide
     * ends: {@code <slide type="start">} on the grace note and
     * {@code <slide type="stop">} on the host. The builder previously nulled
     * out the start side for any grace note, so only the stop was written.
     */
    @Test
    void testGraceNoteGlissandoWritesBothSlideStartAndStop() {
        var song = buildSong(line -> {
            var graceNote = graceQuaver();
            graceNote.setGlissando();
            line.addElement(graceNote);
            line.addElement(crotchet());
        });

        var notes = allNotes(build(song));

        assertThat(findSlide(notes.get(0), StartStop.START))
            .as("grace note must emit <slide type=\"start\">")
            .isNotNull();
        assertThat(findSlide(notes.get(1), StartStop.STOP))
            .as("host note must emit <slide type=\"stop\">")
            .isNotNull();
    }

    /**
     * A glissando too short to draw ({@link SlideGeometry#computeEndpoints} rejected it) must
     * still be emitted, just without {@code default-x}/{@code default-y} — the fallback branch
     * {@link ScorePartwiseBuilder#addSlide} kept for exactly this case.
     * <p>
     * The real spacing engine reserves at least {@code NoteGeometry.MIN_GLISSANDO_RESERVATION_SS}
     * between any two glissando-connected notes (refs #443), so a too-short glissando cannot be
     * reproduced by laying a song out normally — that reservation exists precisely to prevent it.
     * A {@link LayoutResult} that stores no geometry for the glissando note is exactly the state
     * {@code LayoutEngine.calculateSlides} leaves behind when {@code computeEndpoints} rejects it
     * (see the {@code SlideLayout} javadoc), so a bare mock reproduces that state directly.
     */
    @Test
    void testTooShortGlissandoEmitsNoCoordinatesFallback() {
        var song = buildGlissandoSong(
            LEVEL_GLISSANDO_STAFF_POSITION,
            LEVEL_GLISSANDO_STAFF_POSITION,
            StaffElement.Accidental.SHARP);
        var noGlissandoGeometry = mock(LayoutResult.class);
        var notes = allNotes(build(song, (line, lineIndex) -> noGlissandoGeometry));

        var startSlide = findSlide(notes.get(0), StartStop.START);

        assertThat(startSlide).as("a too-short glissando is still emitted").isNotNull();
        assertThat(startSlide.getDefaultX()).as("no default-x for a glissando too short to draw").isNull();
        assertThat(startSlide.getDefaultY()).as("no default-y for a glissando too short to draw").isNull();
    }

    // Phase 4 spot-check: a crotchet (quarter note) on B4 produces schema-valid output.
    @Test
    void testSingleNoteWriterOutputIsSchemaValid() throws Exception {
        var song = buildSong(
            line -> line.addElement(crotchet())
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
            line -> line.addElement(crotchetRest())
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
                line.addElement(graceQuaver());
                line.addElement(crotchet());
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
            var note = crotchet();
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
            var note = crotchet();
            note.setAccidental(StaffElement.Accidental.SHARP);
            line.addElement(note);
        });

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("song with a note carrying a sharp accidental must be schema-valid")
            .doesNotThrowAnyException();
    }

    // -- Direct pitch-spelling anchors (round-trip alone can't catch a uniform lattice shift,
    //    since both directions would shift together) --

    @Test
    void testWriterEmitsB4ForOriginStaffPosition() {
        var song = buildSong(line -> {
            var note = crotchet();
            note.setStaffPosition(B4_STAFF_POSITION);
            line.addElement(note);
        });

        var pitch = allNotes(build(song)).get(0).getPitch();

        assertThat(pitch.getStep().value()).as("<step> for the B4 origin").isEqualTo("B");
        assertThat(pitch.getOctave()).as("<octave> for the B4 origin").isEqualTo(STAFF_POSITION_OCTAVE);
    }

    @Test
    void testWriterEmitsC4ForC4StaffPosition() {
        var song = buildSong(line -> {
            var note = crotchet();
            note.setStaffPosition(C4_STAFF_POSITION);
            line.addElement(note);
        });

        var pitch = allNotes(build(song)).get(0).getPitch();

        assertThat(pitch.getStep().value()).as("<step> for C4").isEqualTo("C");
        assertThat(pitch.getOctave()).as("<octave> for C4").isEqualTo(STAFF_POSITION_OCTAVE);
    }

    // -- Diagonal glissando: exercises BOTH the X and Y endpoint terms --

    @Test
    void testDiagonalGlissandoSlideEndpointsInOutput() {
        var song = buildGlissandoSong(
            DIAGONAL_GLISSANDO_SOURCE_STAFF_POSITION,
            DIAGONAL_GLISSANDO_TARGET_STAFF_POSITION,
            null);
        var notes = allNotes(build(song));
        var endpoints = laidOutGlissandoEndpoints(song);

        assertThat(endpoints.endYSs())
            .as("the fixture must be diagonal, so the Y term is genuinely under test")
            .isNotEqualTo(endpoints.startYSs());

        assertSlideEndpoint(notes.get(0), StartStop.START, endpoints.startXSs(), endpoints.startYSs());
        assertSlideEndpoint(notes.get(1), StartStop.STOP, endpoints.endXSs(), endpoints.endYSs());
    }

    /**
     * A one-line song whose first note carries a glissando to the second.
     *
     * @param targetAccidental accidental for the target note, or null for none — the level
     *                         fixture needs one to keep the two notes at different pitches
     *                         while they share a staff position
     */
    private static Song buildGlissandoSong(
        int sourceStaffPosition,
        int targetStaffPosition,
        StaffElement.@Nullable Accidental targetAccidental) {

        return buildSong(line -> {
            var startNote = crotchet();
            startNote.setStaffPosition(sourceStaffPosition);
            startNote.setGlissando();

            var endNote = crotchet();
            endNote.setStaffPosition(targetStaffPosition);
            endNote.setAccidental(targetAccidental);

            line.addElement(startNote);
            line.addElement(endNote);
        });
    }

    /**
     * Lays {@code song}'s only line out the same way {@link #build(Song)} does and returns the
     * glissando endpoints the builder therefore had to emit. Deriving them independently keeps
     * the assertions about the emitted coordinates honest — only the builder's own mapping
     * (which endpoint goes on which slide, staff spaces to tenths) is under test here.
     */
    private static SlideGeometry.Endpoints laidOutGlissandoEndpoints(Song song) {
        var line = song.getLines().getFirst();
        var layoutResult = LineLayoutProvider
            .headless(song, DocumentFonts.defaultFonts())
            .layoutFor(line, 0);
        assertThat(layoutResult).as("the builder's layout fallback must produce a layout").isNotNull();

        var slideLayout = layoutResult.getSlideLayout(line.getElement(0));
        assertThat(slideLayout).as("the laid-out line must carry the glissando's geometry").isNotNull();

        var endpoints = slideLayout.glissando();
        assertThat(endpoints).as("the glissando must be long enough to draw").isNotNull();

        return endpoints;
    }

    /**
     * Asserts that {@code note}'s {@code <slide>} of the given type carries
     * {@code default-x}/{@code default-y} equal to the expected staff-space point, converted to
     * tenths.
     */
    private static void assertSlideEndpoint(
        Note note, StartStop slideType, double expectedXSs, double expectedYSs) {
        var slide = findSlide(note, slideType);

        assertThat(slide).as("slide[%s] must be present", slideType).isNotNull();
        assertThat(slide.getDefaultX()).as("slide[%s] must carry default-x", slideType).isNotNull();
        assertThat(slide.getDefaultY()).as("slide[%s] must carry default-y", slideType).isNotNull();
        assertThat(slide.getDefaultX().doubleValue())
            .as("slide[%s] default-x must match the laid-out X", slideType)
            .isCloseTo(expectedXSs * MusicXmlTags.TENTHS_PER_STAFF_SPACE, Offset.offset(0.01));
        assertThat(slide.getDefaultY().doubleValue())
            .as("slide[%s] default-y must match the laid-out Y", slideType)
            .isCloseTo(expectedYSs * MusicXmlTags.TENTHS_PER_STAFF_SPACE, Offset.offset(0.01));
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
            var endingAnchor = repeatLeft();
            var quaver0 = quaver();
            var quaver1 = quaver();
            var crotchet0 = crotchet();
            var crotchet1 = crotchet();
            var split = repeatRight();
            var triplet0 = crotchet();
            var triplet1 = crotchet();
            var triplet2 = crotchet();
            var endingEnd = finalDoubleBarline();

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
            line.addSpan(new Trill(crotchet0, crotchet1));
            line.addTuplet(new Tuplet(triplet0, triplet2, ALL_SPAN_TUPLET_GRADE, ALL_SPAN_TUPLET_NORMAL_NOTES,
                ElementType.CROTCHET, NO_DOTS));
            line.addDiminuendo(new Diminuendo(triplet0, triplet2));
            line.addSpan(new Ending(endingAnchor, endingEnd));
        });

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("a song with all six span types must be schema-valid")
            .doesNotThrowAnyException();
    }

    // -- Phase 5, Task 4: <tied orientation> writer output --

    /**
     * Builds a two-crotchet tied song with the given explicit stem directions on each note.
     * The builder never runs auto-stem layout, so the notes' write-forward {@code direction}
     * fields are read as set here.
     */
    private static Song buildTiedSong(StaffElement.Direction startDirection, StaffElement.Direction endDirection) {
        return buildSong(line -> {
            var crotchet0 = crotchet();
            crotchet0.setDirection(startDirection);
            var crotchet1 = crotchet();
            crotchet1.setDirection(endDirection);

            line.addElement(crotchet0);
            line.addElement(crotchet1);
            line.addTie(new Tie(crotchet0, crotchet1));
        });
    }

    @Test
    void testTiedOrientationIsUnderWhenBothStemsUp() {
        var notes = allNotes(build(buildTiedSong(StaffElement.Direction.UP, StaffElement.Direction.UP)));

        assertThat(tiedOrientation(notes.get(0), TiedType.START))
            .as("both-up tie: <tied type=\"start\"> orientation")
            .isEqualTo(OverUnder.UNDER);
        assertThat(tiedOrientation(notes.get(1), TiedType.STOP))
            .as("both-up tie: <tied type=\"stop\"> orientation")
            .isEqualTo(OverUnder.UNDER);
    }

    @Test
    void testTiedOrientationIsOverWhenBothStemsDown() {
        var notes = allNotes(build(buildTiedSong(StaffElement.Direction.DOWN, StaffElement.Direction.DOWN)));

        assertThat(tiedOrientation(notes.get(0), TiedType.START))
            .as("both-down tie: <tied type=\"start\"> orientation")
            .isEqualTo(OverUnder.OVER);
        assertThat(tiedOrientation(notes.get(1), TiedType.STOP))
            .as("both-down tie: <tied type=\"stop\"> orientation")
            .isEqualTo(OverUnder.OVER);
    }

    @Test
    void testTiedOrientationIsOverWhenStemsConflict() {
        var notes = allNotes(build(buildTiedSong(StaffElement.Direction.UP, StaffElement.Direction.DOWN)));

        assertThat(tiedOrientation(notes.get(0), TiedType.START))
            .as("conflicting-stem tie: <tied type=\"start\"> orientation")
            .isEqualTo(OverUnder.OVER);
        assertThat(tiedOrientation(notes.get(1), TiedType.STOP))
            .as("conflicting-stem tie: <tied type=\"stop\"> orientation")
            .isEqualTo(OverUnder.OVER);
    }

    /**
     * Builds a three-crotchet tie chain (two independent ties sharing the middle note as end of
     * one and anchor of the other) with the given explicit stem direction on each note, mirroring
     * {@link #buildTiedSong} for the two-note case. Tie ranges never coalesce, so the interior
     * note keeps independent {@code tieStop}/{@code tieStart} references this test exercises.
     */
    private static Song buildTiedChainSong(
        StaffElement.Direction note0Direction,
        StaffElement.Direction note1Direction,
        StaffElement.Direction note2Direction) {

        return buildSong(line -> {
            var crotchet0 = crotchet();
            crotchet0.setDirection(note0Direction);
            var crotchet1 = crotchet();
            crotchet1.setDirection(note1Direction);
            var crotchet2 = crotchet();
            crotchet2.setDirection(note2Direction);

            line.addElement(crotchet0);
            line.addElement(crotchet1);
            line.addElement(crotchet2);

            line.addTie(new Tie(crotchet0, crotchet1));
            line.addTie(new Tie(crotchet1, crotchet2));
        });
    }

    @Test
    void testTiedOrientationsDifferOnSharedInteriorChainNote() {
        // Chain: note0 --(tieA)-- note1 --(tieB)-- note2. note1 carries both a tieStop (from tieA)
        // and a tieStart (from tieB) as independent @Nullable Tie references, so a both-up left tie
        // and a stems-conflict right tie must resolve to different orientations on the same note.
        var notes = allNotes(build(buildTiedChainSong(
            StaffElement.Direction.UP, StaffElement.Direction.UP, StaffElement.Direction.DOWN)));

        var startOrientations = tiedOrientations(notes, TiedType.START);
        var stopOrientations = tiedOrientations(notes, TiedType.STOP);

        assertThat(startOrientations)
            .as("tieA <tied type=\"start\"> (note0, both-up) then tieB <tied type=\"start\"> "
                + "(note1, stems conflict)")
            .containsExactly(OverUnder.UNDER, OverUnder.OVER);
        assertThat(stopOrientations)
            .as("tieA <tied type=\"stop\"> (note1, both-up) then tieB <tied type=\"stop\"> "
                + "(note2, stems conflict)")
            .containsExactly(OverUnder.UNDER, OverUnder.OVER);
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
            var plainSyllable = crotchet();
            plainSyllable.setLyricForVerse(1, Lyric.Syllabic.BEGIN, false, "Ky", Lyric.Extend.NONE);
            line.addElement(plainSyllable);

            var compoundSyllable = crotchet();
            compoundSyllable.setLyricForVerse(1, Lyric.Syllabic.BEGIN, true, "self", Lyric.Extend.NONE);
            line.addElement(compoundSyllable);

            var extenderStart = crotchet();
            extenderStart.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "oh", Lyric.Extend.START);
            line.addElement(extenderStart);

            var stopCarrier = crotchet();
            stopCarrier.setLyricForVerse(1, null, false, null, Lyric.Extend.STOP);
            line.addElement(stopCarrier);

            var continueCarrier = crotchet();
            continueCarrier.setLyricForVerse(1, null, false, null, Lyric.Extend.CONTINUE);
            line.addElement(continueCarrier);

            var multiVerse = crotchet();
            multiVerse.setLyricForVerse(1, Lyric.Syllabic.SINGLE, false, "one", Lyric.Extend.NONE);
            multiVerse.setLyricForVerse(2, Lyric.Syllabic.SINGLE, false, "two", Lyric.Extend.NONE);
            line.addElement(multiVerse);
        });
    }

    /**
     * Task 2b — Raw lyric output shapes: asserts the built {@code Lyric} graph shape for the six
     * representative forms in {@link #buildLyricMatrixSong()}. The round-trip tests confirm the
     * reconstructed {@link Lyric} values; this test instead pins the exact emitted shape — the
     * carrier's absent syllabic/text choice members, or the raw U+2011 marker character inside
     * the text — which a value-level round-trip cannot observe.
     */
    @Test
    void testLyricWriterOutputShapes() {
        var notes = allNotes(build(buildLyricMatrixSong()));

        // Note 0: plain syllable — Syllabic.BEGIN then text "Ky", no <extend>.
        var plainLyrics = notes.get(0).getLyric();
        assertThat(plainLyrics).as("plain-syllable note must carry exactly one <lyric>").hasSize(1);

        var plainLyric = plainLyrics.getFirst();
        assertThat(plainLyric.getNumber()).as("plain syllable: verse number").isEqualTo("1");
        assertThat(syllabicOf(plainLyric)).as("plain syllable: <syllabic>").isEqualTo(Syllabic.BEGIN);
        assertThat(textOf(plainLyric)).as("plain syllable: <text>").isEqualTo("Ky");
        assertThat(plainLyric.getExtend()).as("plain syllable: no <extend>").isNull();

        // Note 1: compound-word boundary — text carries the U+2011 marker.
        var compoundLyric = notes.get(1).getLyric().getFirst();
        assertThat(syllabicOf(compoundLyric)).as("compound syllable: <syllabic>").isEqualTo(Syllabic.BEGIN);
        assertThat(textOf(compoundLyric))
            .as("compound syllable: <text> carries the compound-word marker")
            .isEqualTo("self" + Constants.NON_BREAKING_HYPHEN);

        // Note 2: extender start — <extend type="start"/> alongside syllabic/text.
        var extenderLyric = notes.get(2).getLyric().getFirst();
        assertThat(syllabicOf(extenderLyric)).as("extender start: <syllabic>").isEqualTo(Syllabic.SINGLE);
        assertThat(textOf(extenderLyric)).as("extender start: <text>").isEqualTo("oh");
        assertThat(extenderLyric.getExtend()).as("extender start: <extend> must be present").isNotNull();
        assertThat(extenderLyric.getExtend().getType())
            .as("extender start: <extend type>")
            .isEqualTo(StartStopContinue.START);

        // Note 3: text-less STOP carrier — no syllabic/text choice members, only <extend type="stop"/>.
        var stopLyric = notes.get(3).getLyric().getFirst();
        assertThat(syllabicOf(stopLyric)).as("STOP carrier: no <syllabic>").isNull();
        assertThat(textOf(stopLyric)).as("STOP carrier: no <text>").isNull();
        assertThat(stopLyric.getExtend()).as("STOP carrier: <extend> must be present").isNotNull();
        assertThat(stopLyric.getExtend().getType()).as("STOP carrier: <extend type>").isEqualTo(StartStopContinue.STOP);

        // Note 4: text-less CONTINUE carrier — same shape, type="continue".
        var continueLyric = notes.get(4).getLyric().getFirst();
        assertThat(syllabicOf(continueLyric)).as("CONTINUE carrier: no <syllabic>").isNull();
        assertThat(textOf(continueLyric)).as("CONTINUE carrier: no <text>").isNull();
        assertThat(continueLyric.getExtend()).as("CONTINUE carrier: <extend> must be present").isNotNull();
        assertThat(continueLyric.getExtend().getType())
            .as("CONTINUE carrier: <extend type>")
            .isEqualTo(StartStopContinue.CONTINUE);

        // Note 5: multi-verse — two <lyric> children, number="1" then number="2".
        var multiVerseLyrics = notes.get(5).getLyric();
        assertThat(multiVerseLyrics).as("multi-verse note: two <lyric> children").hasSize(2);
        assertThat(multiVerseLyrics.get(0).getNumber())
            .as("multi-verse note: first <lyric number>")
            .isEqualTo("1");
        assertThat(multiVerseLyrics.get(1).getNumber())
            .as("multi-verse note: second <lyric number>")
            .isEqualTo("2");
    }

    /**
     * A note with no lyrics must emit no {@code <lyric>} child at all. This guards
     * the builder's empty-list early return; a value-level round-trip cannot observe
     * it, since an absent {@code <lyric>} and an empty lyric list reload identically.
     */
    @Test
    void testNoteWithoutLyricsEmitsNoLyricElement() {
        var song = buildSong(line -> line.addElement(crotchet()));
        var note = allNotes(build(song)).get(0);

        assertThat(note.getLyric())
            .as("a lyric-free note must emit no <lyric> element")
            .isEmpty();
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

    // -- Phase 7: Credits writer output --

    /**
     * Task 2 — title/subtitle credit shapes: {@code title} carries the numbered
     * title, {@code justify="center"}, and the {@code TITLE} role font;
     * {@code subtitle} carries the bare subtitle text, the {@code SUBTITLE} role
     * font, and no {@code justify}.
     */
    @Test
    void testCreditTitleAndSubtitleWriterOutputShapes() {
        var song = buildCreditMatrixSong();
        var score = build(song);

        var titleWords = requireCreditWordsForType(score, MusicXmlTags.CREDIT_TITLE);
        assertThat(titleWords.getValue())
            .as("title credit text must be the numbered title")
            .isEqualTo(song.getNumberedTitle());
        assertThat(titleWords.getJustify())
            .as("title credit justify")
            .isEqualTo(LeftCenterRight.CENTER);

        var titleFont = DocumentFonts.defaultFonts().getFont(FontKey.TITLE);
        assertThat(titleWords.getFontFamily())
            .as("title credit font-family carries the PostScript name")
            .isEqualTo(titleFont.getPSName());
        assertThat(titleWords.getFontSize())
            .as("title credit font-size")
            .isEqualTo(String.valueOf(titleFont.getSize()));
        assertThat(titleWords.getFontWeight())
            .as("title credit font-weight")
            .isEqualTo(titleFont.isBold() ? FontWeight.BOLD : FontWeight.NORMAL);
        assertThat(titleWords.getFontStyle())
            .as("title credit font-style")
            .isEqualTo(titleFont.isItalic() ? FontStyle.ITALIC : FontStyle.NORMAL);

        var subtitleWords = requireCreditWordsForType(score, MusicXmlTags.CREDIT_SUBTITLE);
        assertThat(subtitleWords.getValue())
            .as("subtitle credit text")
            .isEqualTo(CREDIT_TEST_SUBTITLE);
        assertThat(subtitleWords.getJustify())
            .as("subtitle credit must not carry justify")
            .isNull();

        var subtitleFont = DocumentFonts.defaultFonts().getFont(FontKey.SUBTITLE);
        assertThat(subtitleWords.getFontFamily())
            .as("subtitle credit font-family carries the PostScript name")
            .isEqualTo(subtitleFont.getPSName());
    }

    /**
     * Task 3 — attribution-role credit shapes: composer, lyricist, arranger
     * (isArrangement()=true), composition date, lyrics date (distinct from
     * composition date), rights, and place all emit an {@code ATTRIBUTION}-font
     * credit carrying the same {@code relative-y} (the attribution Y offset, in
     * tenths).
     */
    @Test
    void testCreditAttributionWriterOutputShapes() {
        var song = buildCreditMatrixSong();
        var score = build(song);

        var expectedRelativeYTenths = ATTRIBUTION_Y_OFFSET_SS * MusicXmlTags.TENTHS_PER_STAFF_SPACE;
        var attributionCreditTypes = List.of(
            MusicXmlTags.CREDIT_COMPOSER, MusicXmlTags.CREDIT_LYRICIST, MusicXmlTags.CREDIT_ARRANGER,
            MusicXmlTags.CREDIT_COMPOSITION_DATE, MusicXmlTags.CREDIT_LYRICS_DATE,
            MusicXmlTags.CREDIT_RIGHTS, MusicXmlTags.CREDIT_PLACE
        );

        var attributionFont = DocumentFonts.defaultFonts().getFont(FontKey.ATTRIBUTION);

        for (var creditType : attributionCreditTypes) {
            var words = requireCreditWordsForType(score, creditType);
            assertThat(words.getRelativeY()).as("%s credit must carry relative-y", creditType).isNotNull();
            assertThat(words.getRelativeY().doubleValue())
                .as("%s credit relative-y", creditType)
                .isCloseTo(expectedRelativeYTenths, Offset.offset(0.01));
            assertThat(words.getFontFamily())
                .as("%s credit font-family carries the PostScript name", creditType)
                .isEqualTo(attributionFont.getPSName());
        }

        assertThat(requireCreditWordsForType(score, MusicXmlTags.CREDIT_COMPOSER).getValue())
            .as("composer credit text")
            .isEqualTo(CREDIT_TEST_COMPOSER);
        assertThat(requireCreditWordsForType(score, MusicXmlTags.CREDIT_LYRICIST).getValue())
            .as("lyricist credit text")
            .isEqualTo(CREDIT_TEST_LYRICIST);
        assertThat(requireCreditWordsForType(score, MusicXmlTags.CREDIT_ARRANGER).getValue())
            .as("arranger credit text")
            .isEqualTo(Song.SRI_CHINMOY);
        assertThat(requireCreditWordsForType(score, MusicXmlTags.CREDIT_COMPOSITION_DATE).getValue())
            .as("composition date credit text")
            .isEqualTo(DateUtils.toIsoDate(COMPOSITION_YEAR, COMPOSITION_MONTH, COMPOSITION_DAY));
        assertThat(requireCreditWordsForType(score, MusicXmlTags.CREDIT_LYRICS_DATE).getValue())
            .as("lyrics date credit text")
            .isEqualTo(DateUtils.toIsoDate(LYRICS_YEAR, LYRICS_MONTH, LYRICS_DAY));
        assertThat(requireCreditWordsForType(score, MusicXmlTags.CREDIT_RIGHTS).getValue())
            .as("rights credit text")
            .isEqualTo(String.format(MusicXmlTags.COPYRIGHT, FIXED_CLOCK_YEAR));
        assertThat(requireCreditWordsForType(score, MusicXmlTags.CREDIT_PLACE).getValue())
            .as("place credit text")
            .isEqualTo(CREDIT_TEST_PLACE);
    }

    /**
     * Task 4 — score-below credit shapes: underlyrics/translation carry the
     * {@code LYRICS} font, bangla-lyrics carries the {@code BANGLA} font plus
     * {@code xml:lang="bn"}, and footnotes carries the {@code FOOTNOTE} font.
     */
    @Test
    void testCreditScoreBelowWriterOutputShapes() {
        var song = buildCreditMatrixSong();
        var score = build(song);

        var underLyricsWords = requireCreditWordsForType(score, MusicXmlTags.CREDIT_UNDERLYRICS);
        assertThat(underLyricsWords.getValue())
            .as("underlyrics credit text")
            .isEqualTo(CREDIT_TEST_UNDERLYRICS);
        assertThat(underLyricsWords.getFontFamily())
            .as("underlyrics credit font-family carries the PostScript name")
            .isEqualTo(DocumentFonts.defaultFonts().getFont(FontKey.LYRICS).getPSName());

        var banglaWords = requireCreditWordsForType(score, MusicXmlTags.CREDIT_BANGLA_LYRICS);
        assertThat(banglaWords.getValue())
            .as("bangla-lyrics credit text")
            .isEqualTo(CREDIT_TEST_BANGLA_LYRICS);
        assertThat(banglaWords.getLang())
            .as("bangla-lyrics credit xml:lang")
            .isEqualTo(MusicXmlTags.CREDIT_LANGUAGE_BANGLA);
        assertThat(banglaWords.getFontFamily())
            .as("bangla-lyrics credit font-family carries the PostScript name")
            .isEqualTo(DocumentFonts.defaultFonts().getFont(FontKey.BANGLA).getPSName());

        var translationWords = requireCreditWordsForType(score, MusicXmlTags.CREDIT_TRANSLATION);
        assertThat(translationWords.getValue())
            .as("translation credit text")
            .isEqualTo(CREDIT_TEST_TRANSLATION);

        var footnotesWords = requireCreditWordsForType(score, MusicXmlTags.CREDIT_FOOTNOTES);
        assertThat(footnotesWords.getValue())
            .as("footnotes credit text")
            .isEqualTo(CREDIT_TEST_FOOTNOTES);
        assertThat(footnotesWords.getFontFamily())
            .as("footnotes credit font-family carries the PostScript name")
            .isEqualTo(DocumentFonts.defaultFonts().getFont(FontKey.FOOTNOTE).getPSName());
    }

    /**
     * A song with blank subtitle/place/dates, {@code isArrangement()=false}, and
     * blank score-below fields emits only the four credits that are never blank:
     * title (always non-empty), composer/lyricist (coerced to Sri Chinmoy when
     * blank), and rights (a fixed format string).
     */
    @Test
    void testBlankCreditFieldsEmitNoCredit() {
        var song = buildSong(line -> line.addElement(crotchet()));
        var score = build(song);

        assertThat(creditTypesPresent(score))
            .as("credit-types present for a song with blank subtitle/place/dates/arrangement/score-below fields")
            .containsExactlyInAnyOrder(
                MusicXmlTags.CREDIT_TITLE,
                MusicXmlTags.CREDIT_COMPOSER,
                MusicXmlTags.CREDIT_LYRICIST,
                MusicXmlTags.CREDIT_RIGHTS
            );
    }

    /**
     * A lyrics date equal to the composition date is redundant, so the builder
     * emits neither the {@code lyrics-date} miscellaneous-field nor the
     * {@code lyrics date} credit — only the composition date is written. Guards
     * the dedup branches in {@code HeaderBuilder.HeaderText} / {@code buildMiscellaneous}
     * / {@code buildCredits}, which no distinct-date test exercises.
     */
    @Test
    void testLyricsDateEqualToCompositionDateIsOmitted() {
        var song = buildSong(line -> line.addElement(crotchet()));

        song.setMetadata(new SongMetadata(
            CREDIT_TEST_TITLE,
            CREDIT_TEST_NUMBER,
            CREDIT_TEST_PLACE,
            COMPOSITION_YEAR, COMPOSITION_MONTH, COMPOSITION_DAY,
            CREDIT_TEST_COMPOSER,
            CREDIT_TEST_LYRICIST,
            Song.LyricsSource.LYRICIST,
            false,
            false,
            CREDIT_TEST_SUBTITLE,
            // Lyrics date deliberately equal to the composition date.
            COMPOSITION_YEAR, COMPOSITION_MONTH, COMPOSITION_DAY
        ));

        var score = build(song);
        var expectedCompositionDate = DateUtils.toIsoDate(COMPOSITION_YEAR, COMPOSITION_MONTH, COMPOSITION_DAY);

        // The composition date is present as both a misc-field and a credit.
        assertThat(miscFieldValue(score, MusicXmlTags.MISC_COMPOSITION_DATE))
            .as("composition-date misc-field must be present")
            .isEqualTo(expectedCompositionDate);
        assertThat(creditTypesPresent(score))
            .as("composition date credit must be present")
            .contains(MusicXmlTags.CREDIT_COMPOSITION_DATE);

        // The redundant lyrics date is omitted from both.
        assertThat(miscFieldValue(score, MusicXmlTags.MISC_LYRICS_DATE))
            .as("a lyrics-date equal to the composition date must not be emitted as a misc-field")
            .isNull();
        assertThat(creditTypesPresent(score))
            .as("a lyrics date equal to the composition date must not be emitted as a credit")
            .doesNotContain(MusicXmlTags.CREDIT_LYRICS_DATE);
    }

    /**
     * Task 5 — schema-valid gate: a song with every credit field populated
     * (title/subtitle/attribution roles/score-below blocks) emits schema-valid
     * MusicXML.
     */
    @Test
    void testCreditsWriterOutputIsSchemaValid() throws Exception {
        var xml = writeToString(buildCreditMatrixSong());
        var validator = new MusicXmlSchemaValidator();
        assertThatCode(() -> validator.validate(xml))
            .as("a song with every credit field populated must be schema-valid")
            .doesNotThrowAnyException();
    }

    // A width read from a file or replayed from an undo record is never range-checked,
    // so the builder's guard has to cover a negative as well as zero.
    private static final double NEGATIVE_LINE_WIDTH_SS = -1.0;

    /**
     * A song whose line width was never initialized, or which picked up a bad width from
     * a file or an undo replay, must not persist an unusable staff: nothing on the read
     * side rejects one, so the document would reopen with a staff no content can fit and
     * every line clipped and drawn in red instead of showing the song. The
     * builder substitutes {@link Song#FALLBACK_LINE_WIDTH_SS} for any width that is not
     * positive — a guard that only caught zero would let a negative through.
     */
    @ParameterizedTest
    @ValueSource(doubles = { 0, NEGATIVE_LINE_WIDTH_SS })
    void testNonPositiveLineWidthIsWrittenAsTheFallbackWidth(double lineWidthSs) {
        var song = buildSong(line -> line.addElement(crotchet()));
        song.setLineWidthSs(lineWidthSs);

        var pageWidth = build(song).getDefaults().getPageLayout().getPageWidth();
        var expectedPageWidth = MusicXmlUnits.ssAsTenths(Song.FALLBACK_LINE_WIDTH_SS);

        assertThat(pageWidth)
            .as("a line width of %s must be written as the fallback width", lineWidthSs)
            .isEqualByComparingTo(expectedPageWidth);
    }
}
