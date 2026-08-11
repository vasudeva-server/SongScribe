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
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.graceQuaver;
import static songscribe.dom.StaffElementFactory.minim;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.X_OFFSET_PX;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import songscribe.UnitTest;
import songscribe.dom.Articulation;
import songscribe.dom.ArticulationType;
import songscribe.dom.DynamicAttachment;
import songscribe.dom.ElementType;
import songscribe.dom.FermataAttachment;
import songscribe.dom.KeyType;
import songscribe.dom.StaffElement;

class MusicXmlNoteRoundTripTest extends UnitTest {

    /**
     * Staffposition for F4 (the first sharp in the key-of-one-sharp / G-major key).
     * B4 = staffPosition 0 increasing downward: A4=1, G4=2, F4=3.
     */
    private static final int F4_STAFF_POSITION = 3;

    /**
     * The number of sharps in G major (one sharp = F#).
     * Used in the {@code <alter>} divergence tests.
     */
    private static final int G_MAJOR_SHARP_COUNT = 1;

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

            assertThat(actualFermata.getAlignment())
                .as("%s: fermata alignment", context)
                .isEqualTo(expectedFermata.getAlignment());
            assertThat(actualFermata.getOwnerElement())
                .as("%s: fermata owner element", context)
                .isEqualTo(actual);
        }

        var expectedDynamic = expected.findAttachment(DynamicAttachment.class);
        var actualDynamic = actual.findAttachment(DynamicAttachment.class);

        if (expectedDynamic == null) {
            assertThat(actualDynamic).as("%s: dynamics", context).isNull();
        } else {
            assertThat(actualDynamic).as("%s: dynamics", context).isNotNull();

            assertThat(actualDynamic.getType())
                .as("%s: dynamics type", context)
                .isEqualTo(expectedDynamic.getType());
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
            var grace = graceQuaver();
            grace.setUpper(true);
            grace.setStemDirectionAuto(false);
            line.addElement(grace);
            line.addElement(crotchet());
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
            var finalDotCount = dotCount;
            var song = buildSong(line -> {
                var note = minim();
                note.setDotCount(finalDotCount);
                line.addElement(note);
            });

            var song2 = roundTrip(song);
            var expected = song.getLine(0).getElement(0);
            var actual = song2.getLine(0).getElement(0);
            assertNoteEquals(expected, actual);
        }
    }

    /**
     * {@code forAccidental} is non-null by contract, and the writer relies on it: it no
     * longer has a skip branch for a missing token. Adding an accidental to the enum
     * without a token now fails to compile, because that method is an exhaustive switch —
     * this test guards the weaker mistake the compiler cannot see, a token that is present
     * but blank, which would write an empty {@code <accidental>} and reload as none.
     */
    @Test
    void testEveryAccidentalHasAMusicXmlToken() {
        for (var accidental : StaffElement.Accidental.values()) {
            assertThat(AccidentalMapping.forAccidental(accidental))
                .as("%s must have a MusicXML <accidental> token", accidental)
                .isNotBlank();
        }
    }

    @Test
    void testAllAccidentalsRoundTrip() throws Exception {
        var accidentals = List.of(
            StaffElement.Accidental.NATURAL,
            StaffElement.Accidental.FLAT,
            StaffElement.Accidental.SHARP,
            StaffElement.Accidental.DOUBLE_FLAT,
            StaffElement.Accidental.DOUBLE_SHARP
        );

        for (var accidental : accidentals) {
            var song = buildSong(line -> {
                var note = crotchet();
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
            var note = crotchet();
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
            var note = crotchet();
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
            var note = crotchet();
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
        var song = buildSong(line -> line.addElement(crotchet()));
        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    @Test
    void testNoteXOffsetRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = crotchet();
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
            var note = crotchet();
            line.addElement(note);
            note.addAttachment(new FermataAttachment(note));
        });

        var song2 = roundTrip(song);
        assertNoteEquals(
            song.getLine(0).getElement(0),
            song2.getLine(0).getElement(0)
        );
    }

    /**
     * Every dynamic mark, not a representative one: the writer picks the {@code <dynamics>}
     * child element by name and the reader picks the model constant back by that same name, so
     * a wrong pair — {@code MEZZO_PIANO} written as {@code <p/>}, say — is a swap that only a
     * per-constant round-trip sees. The corpus fixpoint cannot: it compares a document against
     * the document the reader's own output produced, and a symmetric swap reproduces itself.
     */
    @Test
    void testAllDynamicTypesRoundTrip() throws Exception {
        for (var dynamicType : DynamicAttachment.DynamicType.values()) {
            var song = buildSong(line -> {
                var note = crotchet();
                line.addElement(note);
                note.addAttachment(new DynamicAttachment(note, dynamicType));
            });

            var song2 = roundTrip(song);
            assertNoteEquals(
                song.getLine(0).getElement(0),
                song2.getLine(0).getElement(0),
                "dynamic " + dynamicType
            );
        }
    }

    @Test
    void testAccentArticulationRoundTrips() throws Exception {
        var song = buildSong(line -> {
            var note = crotchet();
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
            var note = crotchet();
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
            var startNote = crotchet();
            startNote.setGlissando();
            line.addElement(startNote);
            line.addElement(crotchet());
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
            var note = crotchet();
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
            line.addElement(crotchet());
            line.addElement(breathMark());
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
            var note = crotchet();
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
            var note = crotchet();
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
            var note = crotchet();
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
            var note = crotchet();
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
}
