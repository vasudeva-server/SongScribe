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

import java.awt.Component;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.StaffElement;
import songscribe.dom.Tempo;
import songscribe.dom.TempoChangeAttachment;

/**
 * Round-trip coverage for annotation {@code <direction placement="above|below">}
 * emission and parsing (Phase 9). Each annotation is emitted as a single
 * {@code <words>} direction immediately before its note and recovered from the
 * {@code placement} + {@code halign} + {@code relative-y} signal, binding to the
 * next note as an {@link AnnotationAttachment}.
 */
class MusicXmlAnnotationRoundTripTest extends MusicXmlRoundTripSupport {

    // Distinct annotation texts so a note / alignment / placement mix-up is caught.
    private static final String TEXT_ABOVE_LEFT = "dolce";
    private static final String TEXT_ABOVE_CENTER = "espressivo";
    private static final String TEXT_ABOVE_RIGHT = "cresc.";
    private static final String TEXT_BELOW_LEFT = "pizz.";
    private static final String TEXT_BELOW_CENTER = "arco";
    private static final String TEXT_BELOW_RIGHT = "sotto voce";

    // Non-zero user Y offsets (staff spaces), mixing signs so the sign round-trips.
    // Integer-valued so the ss → tenths → ss conversion is exact.
    private static final double Y_OFFSET_ABOVE_LEFT_SS = 2.0;
    private static final double Y_OFFSET_ABOVE_CENTER_SS = 3.0;
    private static final double Y_OFFSET_ABOVE_RIGHT_SS = 4.0;
    private static final double Y_OFFSET_BELOW_LEFT_SS = -2.0;
    private static final double Y_OFFSET_BELOW_CENTER_SS = -3.0;
    private static final double Y_OFFSET_BELOW_RIGHT_SS = -4.0;

    // Per-note tempo (BPM) for the tempo-plus-annotation coexistence case.
    private static final int PER_NOTE_TEMPO_BPM = 92;
    private static final String NO_TEMPO_DESCRIPTION = "";

    // Element indices in the consecutive-notes fixture.
    private static final int FIRST_NOTE_INDEX = 0;
    private static final int SECOND_NOTE_INDEX = 1;

    /**
     * One annotation's round-trippable fields: text, horizontal alignment,
     * placement and user Y offset.
     */
    private record AnnotationCase(
        String text, float alignment, Annotation.Placement placement, double offsetSs) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void attachAnnotation(StaffElement element, AnnotationCase annotationCase) {
        var annotation = new Annotation(annotationCase.text());
        annotation.setXAlignment(annotationCase.alignment());
        annotation.setPlacement(annotationCase.placement());
        annotation.setUserYOffsetSs(annotationCase.offsetSs());
        element.addAttachment(new AnnotationAttachment(element, annotation));
    }

    private static void assertAnnotationEquals(
            StaffElement element, AnnotationCase expected, String context) {
        var attachment = element.findAttachment(AnnotationAttachment.class);
        assertThat(attachment).as("%s: annotation attachment present", context).isNotNull();

        var annotation = attachment.getAnnotation();
        assertThat(annotation.getAnnotation()).as("%s: text", context).isEqualTo(expected.text());
        assertThat(annotation.getXAlignment()).as("%s: xAlignment", context).isEqualTo(expected.alignment());
        assertThat(annotation.getPlacement()).as("%s: placement", context).isEqualTo(expected.placement());
        assertThat(annotation.getUserYOffsetSs()).as("%s: userYOffsetSs", context).isEqualTo(expected.offsetSs());
    }

    /**
     * The six annotations covering both placements crossed with each of
     * left/center/right alignment, each with a non-zero user Y offset.
     */
    private static List<AnnotationCase> alignmentMatrix() {
        return List.of(
            new AnnotationCase(TEXT_ABOVE_LEFT, Component.LEFT_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_LEFT_SS),
            new AnnotationCase(TEXT_ABOVE_CENTER, Component.CENTER_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_CENTER_SS),
            new AnnotationCase(TEXT_ABOVE_RIGHT, Component.RIGHT_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_RIGHT_SS),
            new AnnotationCase(TEXT_BELOW_LEFT, Component.LEFT_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_LEFT_SS),
            new AnnotationCase(TEXT_BELOW_CENTER, Component.CENTER_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_CENTER_SS),
            new AnnotationCase(TEXT_BELOW_RIGHT, Component.RIGHT_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_RIGHT_SS)
        );
    }

    // -------------------------------------------------------------------------
    // Round-trip tests
    // -------------------------------------------------------------------------

    @Test
    void testAboveAndBelowAlignmentMatrixRoundTrips() throws Exception {
        var cases = alignmentMatrix();

        var song = buildSong(line -> {
            for (var annotationCase : cases) {
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                attachAnnotation(note, annotationCase);
            }
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        for (var i = 0; i < cases.size(); i++) {
            assertAnnotationEquals(line.getElement(i), cases.get(i), "matrix case " + i);
        }
    }

    @Test
    void testNoteWithBothTempoAndAnnotationKeepsBothDistinct() throws Exception {
        var perNoteTempo = new Tempo(PER_NOTE_TEMPO_BPM, Duration.MINIM, NO_TEMPO_DESCRIPTION, true);
        var annotationCase = new AnnotationCase(
            TEXT_ABOVE_CENTER, Component.CENTER_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_CENTER_SS);

        // The tempo + annotation ride on the second note (not the first element),
        // so the writer emits a per-note tempo direction, not the song base tempo.
        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            note1.addAttachment(new TempoChangeAttachment(note1, perNoteTempo));
            attachAnnotation(note1, annotationCase);
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);
        var note1 = line.getElement(SECOND_NOTE_INDEX);

        var tempoAttachment = note1.findAttachment(TempoChangeAttachment.class);
        assertThat(tempoAttachment).as("tempo attachment present").isNotNull();

        var tempo = tempoAttachment.getTempo();
        assertThat(tempo.getVisibleTempo()).as("tempo bpm").isEqualTo(PER_NOTE_TEMPO_BPM);
        assertThat(tempo.getTempoType()).as("tempo type").isEqualTo(Duration.MINIM);

        assertAnnotationEquals(note1, annotationCase, "note carrying both tempo and annotation");

        var note0 = line.getElement(FIRST_NOTE_INDEX);
        assertThat(note0.findAttachment(AnnotationAttachment.class))
            .as("plain note carries no annotation").isNull();
        assertThat(note0.findAttachment(TempoChangeAttachment.class))
            .as("plain note carries no tempo").isNull();
    }

    @Test
    void testTwoAnnotationsOnConsecutiveNotesStayInOrder() throws Exception {
        var first = new AnnotationCase(
            TEXT_ABOVE_LEFT, Component.LEFT_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_LEFT_SS);
        var second = new AnnotationCase(
            TEXT_BELOW_RIGHT, Component.RIGHT_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_RIGHT_SS);

        var song = buildSong(line -> {
            var note0 = ElementType.CROTCHET.newInstance();
            var note1 = ElementType.CROTCHET.newInstance();
            line.addElement(note0);
            line.addElement(note1);
            attachAnnotation(note0, first);
            attachAnnotation(note1, second);
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertAnnotationEquals(line.getElement(FIRST_NOTE_INDEX), first, "first annotation on note 0");
        assertAnnotationEquals(line.getElement(SECOND_NOTE_INDEX), second, "second annotation on note 1");
    }

    @Test
    void testWordsOnlyDirectionWithoutPlacementIsNotAnnotation() throws Exception {
        // A words-only <direction> carrying no placement (and no <metronome>) is
        // left to the metronome resolver, which drops it — it is never promoted to
        // a phantom annotation.
        var xml = scoreWithMeasureBody(
            """
                      <direction><direction-type>\
                <words>not an annotation</words>\
                </direction-type></direction>
                      <note>
                        <pitch><step>B</step><octave>4</octave></pitch>
                        <duration>480</duration>
                        <type>quarter</type>
                      </note>
                      <barline location="right"><bar-style>light-heavy</bar-style></barline>
                """
        );

        var song = parse(xml);

        assertThat(song.lineCount()).as("parsing completes with one line").isEqualTo(1);
        assertThat(song.getLine(0).getElement(FIRST_NOTE_INDEX).findAttachment(AnnotationAttachment.class))
            .as("a words-only <direction> with no placement must not become an annotation")
            .isNull();
    }

    // -------------------------------------------------------------------------
    // Schema validation
    // -------------------------------------------------------------------------

    @Test
    void testAnnotationDirectionsValidateAgainstSchema() throws Exception {
        var cases = alignmentMatrix();

        var song = buildSong(line -> {
            for (var annotationCase : cases) {
                var note = ElementType.CROTCHET.newInstance();
                line.addElement(note);
                attachAnnotation(note, annotationCase);
            }
        });

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(xml))
            .as("annotation direction output validates against the MusicXML 4.0 schema")
            .doesNotThrowAnyException();
    }
}
