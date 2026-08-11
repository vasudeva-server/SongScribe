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
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatLeftRight;
import static songscribe.dom.StaffElementFactory.repeatRight;
import static songscribe.dom.StaffElementFactory.singleBarline;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.assertSpanEquals;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.buildSong;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.roundTrip;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.writeToString;

import java.awt.Component;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Annotation;
import songscribe.dom.AnnotationAttachment;
import songscribe.dom.Duration;
import songscribe.dom.ElementType;
import songscribe.dom.Ending;
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
class MusicXmlAnnotationRoundTripTest extends UnitTest {

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

    // Element positions. The fixtures below are all short lines of the same shape
    // — the element under test sits at one of the first three positions — so they
    // index by position and name the element they expect in the assertion itself,
    // rather than each fixture minting its own constant for the same two values.
    private static final int FIRST_ELEMENT_INDEX = 0;
    private static final int SECOND_ELEMENT_INDEX = 1;
    private static final int THIRD_ELEMENT_INDEX = 2;

    // Anchor / split / end indices in the REPEAT_LEFT_RIGHT-ended ending fixture.
    private static final int ENDING_ANCHOR_INDEX = 0;
    private static final int ENDING_SPLIT_INDEX = 2;
    private static final int ENDING_END_INDEX = 4;

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
                var note = crotchet();
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
            var note0 = crotchet();
            var note1 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            note1.addAttachment(new TempoChangeAttachment(note1, perNoteTempo));
            attachAnnotation(note1, annotationCase);
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);
        var note1 = line.getElement(SECOND_ELEMENT_INDEX);

        var tempoAttachment = note1.findAttachment(TempoChangeAttachment.class);
        assertThat(tempoAttachment).as("tempo attachment present").isNotNull();

        var tempo = tempoAttachment.getTempo();
        assertThat(tempo.getVisibleTempo()).as("tempo bpm").isEqualTo(PER_NOTE_TEMPO_BPM);
        assertThat(tempo.getTempoType()).as("tempo type").isEqualTo(Duration.MINIM);

        assertAnnotationEquals(note1, annotationCase, "note carrying both tempo and annotation");

        var note0 = line.getElement(FIRST_ELEMENT_INDEX);
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
            var note0 = crotchet();
            var note1 = crotchet();
            line.addElement(note0);
            line.addElement(note1);
            attachAnnotation(note0, first);
            attachAnnotation(note1, second);
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertAnnotationEquals(line.getElement(FIRST_ELEMENT_INDEX), first, "first annotation on note 0");
        assertAnnotationEquals(line.getElement(SECOND_ELEMENT_INDEX), second, "second annotation on note 1");
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
        assertThat(song.getLine(0).getElement(FIRST_ELEMENT_INDEX).findAttachment(AnnotationAttachment.class))
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
                var note = crotchet();
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

    // -------------------------------------------------------------------------
    // Terminal barline annotation (issue #713)
    // -------------------------------------------------------------------------

    @Test
    void testAnnotationOnFinalDoubleBarlineTerminalRoundTrips() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_ABOVE_CENTER, Component.CENTER_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_CENTER_SS);

        var song = buildSong(line -> {
            var note = crotchet();
            line.addElement(note);
            var terminal = finalDoubleBarline();
            line.addElement(terminal);
            attachAnnotation(terminal, annotationCase);
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);
        var terminal = line.getElement(line.elementCount() - 1);

        assertThat(terminal.getType()).as("terminal type").isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        assertAnnotationEquals(terminal, annotationCase, "annotation on FINAL_DOUBLE_BARLINE terminal");
    }

    @Test
    void testAnnotationOnRepeatRightTerminalRoundTrips() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_BELOW_LEFT, Component.LEFT_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_LEFT_SS);

        var song = buildSong(line -> {
            var note = crotchet();
            line.addElement(note);
            var terminal = repeatRight();
            line.addElement(terminal);
            attachAnnotation(terminal, annotationCase);
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);
        var terminal = line.getElement(line.elementCount() - 1);

        assertThat(terminal.getType()).as("terminal type").isEqualTo(ElementType.REPEAT_RIGHT);
        assertAnnotationEquals(terminal, annotationCase, "annotation on REPEAT_RIGHT terminal");
    }

    /**
     * The writer emits a barline's annotation for any barline, not only the one occupying the
     * terminal slot. Without this an annotation on an ordinary mid-line barline could be
     * dropped on save and nobody would notice, because the two tests above only ever exercise
     * a barline that happens to be the song's terminal.
     */
    @Test
    void testAnnotationOnOrdinaryBarlineRoundTrips() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_ABOVE_LEFT, Component.LEFT_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_LEFT_SS);

        var song = buildSong(line -> {
            line.addElement(crotchet());
            var barline = singleBarline();
            line.addElement(barline);
            line.addElement(crotchet());
            attachAnnotation(barline, annotationCase);
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);
        var barline = line.getElement(SECOND_ELEMENT_INDEX);

        assertThat(barline.getType())
            .as("the annotated element is still the mid-line barline, not the terminal")
            .isEqualTo(ElementType.SINGLE_BARLINE);
        assertAnnotationEquals(barline, annotationCase, "annotation on an ordinary barline");
    }

    // -------------------------------------------------------------------------
    // REPEAT_LEFT / REPEAT_LEFT_RIGHT annotations (issue #734)
    // -------------------------------------------------------------------------

    @Test
    void testAnnotationOnRepeatLeftRoundTrips() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_ABOVE_LEFT, Component.LEFT_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_LEFT_SS);

        var song = buildSong(line -> {
            line.addElement(crotchet());
            var element = repeatLeft();
            line.addElement(element);
            attachAnnotation(element, annotationCase);
            line.addElement(crotchet());
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);
        var element = line.getElement(SECOND_ELEMENT_INDEX);

        assertThat(element.getType()).as("element type").isEqualTo(ElementType.REPEAT_LEFT);
        assertAnnotationEquals(element, annotationCase, "annotation on REPEAT_LEFT");
    }

    @Test
    void testAnnotationOnRepeatLeftRightRoundTrips() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_BELOW_RIGHT, Component.RIGHT_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_RIGHT_SS);

        var song = buildSong(line -> {
            line.addElement(crotchet());
            var element = repeatLeftRight();
            line.addElement(element);
            attachAnnotation(element, annotationCase);
            line.addElement(crotchet());
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);
        var element = line.getElement(SECOND_ELEMENT_INDEX);

        assertThat(element.getType()).as("element type").isEqualTo(ElementType.REPEAT_LEFT_RIGHT);
        assertAnnotationEquals(element, annotationCase, "annotation on REPEAT_LEFT_RIGHT");
    }

    /**
     * A REPEAT_LEFT that is its line's <em>first</em> element, which the mid-line tests above
     * cannot produce: the writer has just opened the line's measure, so it closes that empty
     * measure with an invisible right barline before the forward-left barline, and the
     * annotation direction has to precede both (a forward-left barline must be its measure's
     * first child).
     *
     * <p>Despite spanning a line break, this does <em>not</em> exercise an annotation held
     * across one: line 1 parks nothing, and the annotation direction is read after the reader
     * has already switched to line 2. No file the writer can produce leaves an annotation
     * pending across a line break.
     */
    @Test
    void testAnnotationOnLineStartingRepeatLeftRoundTrips() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_ABOVE_CENTER, Component.CENTER_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_CENTER_SS);

        var song = buildSong(
            line -> line.addElement(crotchet()),
            line -> {
                var element = repeatLeft();
                line.addElement(element);
                attachAnnotation(element, annotationCase);
                line.addElement(crotchet());
            }
        );

        var reloaded = roundTrip(song);
        var lineOne = reloaded.getLine(0);
        var lineTwo = reloaded.getLine(1);
        var element = lineTwo.getElement(FIRST_ELEMENT_INDEX);

        assertThat(element.getType()).as("element type").isEqualTo(ElementType.REPEAT_LEFT);
        assertAnnotationEquals(element, annotationCase, "annotation on line-starting REPEAT_LEFT");

        assertThat(lineOne.getElement(FIRST_ELEMENT_INDEX).findAttachment(AnnotationAttachment.class))
            .as("line 1's note carries no annotation")
            .isNull();
    }

    @Test
    void testRepeatAnnotationDirectionsValidateAgainstSchema() throws Exception {
        var repeatLeftCase = new AnnotationCase(
            TEXT_ABOVE_LEFT, Component.LEFT_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_LEFT_SS);
        var repeatLeftRightCase = new AnnotationCase(
            TEXT_BELOW_RIGHT, Component.RIGHT_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_RIGHT_SS);

        var song = buildSong(line -> {
            line.addElement(crotchet());
            var repeatLeftElement = repeatLeft();
            line.addElement(repeatLeftElement);
            attachAnnotation(repeatLeftElement, repeatLeftCase);
            line.addElement(crotchet());
            var repeatLeftRightElement = repeatLeftRight();
            line.addElement(repeatLeftRightElement);
            attachAnnotation(repeatLeftRightElement, repeatLeftRightCase);
            line.addElement(crotchet());
        });

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(xml))
            .as("annotated REPEAT_LEFT and REPEAT_LEFT_RIGHT output validates against the MusicXML 4.0 schema")
            .doesNotThrowAnyException();
    }

    /**
     * The line-start shape has its own measure layout the mid-line schema test never produces:
     * the annotation direction and the invisible right barline that closes the just-opened
     * measure are that measure's only children. A measure holding nothing but a direction and
     * a barline is the shape most likely to trip the schema's content model, so it is
     * validated separately from the mid-line one.
     */
    @Test
    void testLineStartingRepeatLeftAnnotationValidatesAgainstSchema() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_ABOVE_CENTER, Component.CENTER_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_CENTER_SS);

        var song = buildSong(
            line -> line.addElement(crotchet()),
            line -> {
                var element = repeatLeft();
                line.addElement(element);
                attachAnnotation(element, annotationCase);
                line.addElement(crotchet());
            }
        );

        var xml = writeToString(song);
        var validator = new MusicXmlSchemaValidator();

        assertThatCode(() -> validator.validate(xml))
            .as("an annotated line-starting REPEAT_LEFT validates against the MusicXML 4.0 schema")
            .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Deferred REPEAT_RIGHT hold does not steal another element's annotation (issue #734)
    // -------------------------------------------------------------------------

    @Test
    void testAnnotationOnNoteAfterRepeatRightStaysOnTheNote() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_BELOW_CENTER, Component.CENTER_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_CENTER_SS);

        var song = buildSong(line -> {
            line.addElement(crotchet());
            line.addElement(repeatRight());
            var note = crotchet();
            line.addElement(note);
            attachAnnotation(note, annotationCase);
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        var strayRepeatRight = line.getElement(SECOND_ELEMENT_INDEX);
        assertThat(strayRepeatRight.getType()).as("stray element type").isEqualTo(ElementType.REPEAT_RIGHT);
        assertThat(strayRepeatRight.findAttachment(AnnotationAttachment.class))
            .as("stray REPEAT_RIGHT carries no annotation")
            .isNull();

        var note = line.getElement(THIRD_ELEMENT_INDEX);
        assertThat(note.getType()).as("note type").isEqualTo(ElementType.CROTCHET);
        assertAnnotationEquals(note, annotationCase, "annotation on note after a stray REPEAT_RIGHT");
    }

    @Test
    void testAnnotatedRepeatRightAndAnnotatedNextNoteBothSurvive() throws Exception {
        var repeatRightCase = new AnnotationCase(
            TEXT_ABOVE_LEFT, Component.LEFT_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_LEFT_SS);
        var noteCase = new AnnotationCase(
            TEXT_BELOW_RIGHT, Component.RIGHT_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_RIGHT_SS);

        var song = buildSong(line -> {
            line.addElement(crotchet());
            var repeatRightElement = repeatRight();
            line.addElement(repeatRightElement);
            attachAnnotation(repeatRightElement, repeatRightCase);
            var note = crotchet();
            line.addElement(note);
            attachAnnotation(note, noteCase);
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        var repeatRightElement = line.getElement(SECOND_ELEMENT_INDEX);
        assertThat(repeatRightElement.getType()).as("REPEAT_RIGHT type").isEqualTo(ElementType.REPEAT_RIGHT);
        assertAnnotationEquals(repeatRightElement, repeatRightCase, "annotation on annotated REPEAT_RIGHT");

        var note = line.getElement(THIRD_ELEMENT_INDEX);
        assertThat(note.getType()).as("note type").isEqualTo(ElementType.CROTCHET);
        assertAnnotationEquals(note, noteCase, "annotation on the note following the annotated REPEAT_RIGHT");
    }

    @Test
    void testStrayRepeatRightBeforeAnnotatedRepeatLeftDoesNotStealIt() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_ABOVE_CENTER, Component.CENTER_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_CENTER_SS);

        var song = buildSong(line -> {
            line.addElement(crotchet());
            line.addElement(repeatRight());
            var element = repeatLeft();
            line.addElement(element);
            attachAnnotation(element, annotationCase);
            line.addElement(crotchet());
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertThat(line.getElement(SECOND_ELEMENT_INDEX).findAttachment(AnnotationAttachment.class))
            .as("stray REPEAT_RIGHT carries no annotation")
            .isNull();

        var element = line.getElement(THIRD_ELEMENT_INDEX);
        assertThat(element.getType()).as("element type").isEqualTo(ElementType.REPEAT_LEFT);
        assertAnnotationEquals(element, annotationCase, "annotation on REPEAT_LEFT after a stray REPEAT_RIGHT");
    }

    @Test
    void testStrayRepeatRightBeforeAnnotatedRepeatLeftRightDoesNotStealIt() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_BELOW_LEFT, Component.LEFT_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_LEFT_SS);

        var song = buildSong(line -> {
            line.addElement(crotchet());
            line.addElement(repeatRight());
            var element = repeatLeftRight();
            line.addElement(element);
            attachAnnotation(element, annotationCase);
            line.addElement(crotchet());
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);

        assertThat(line.getElement(SECOND_ELEMENT_INDEX).findAttachment(AnnotationAttachment.class))
            .as("stray REPEAT_RIGHT carries no annotation")
            .isNull();

        var element = line.getElement(THIRD_ELEMENT_INDEX);
        assertThat(element.getType()).as("element type").isEqualTo(ElementType.REPEAT_LEFT_RIGHT);
        assertAnnotationEquals(element, annotationCase, "annotation on REPEAT_LEFT_RIGHT after a stray REPEAT_RIGHT");
    }

    /**
     * The third of the three places a held REPEAT_RIGHT is released: the line-break handler.
     * A REPEAT_RIGHT ending a line is still held when the next line's new-system measure
     * starts, so its annotation has to survive the flush that lands the barline back on the
     * line it belongs to. A drop here would only ever show up in a multi-line song.
     */
    @Test
    void testAnnotationOnRepeatRightAtLineEndSurvivesTheLineBreak() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_ABOVE_RIGHT, Component.RIGHT_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_RIGHT_SS);

        var song = buildSong(
            line -> {
                line.addElement(crotchet());
                var element = repeatRight();
                line.addElement(element);
                attachAnnotation(element, annotationCase);
            },
            line -> line.addElement(crotchet())
        );

        var reloaded = roundTrip(song);
        var lineOne = reloaded.getLine(0);
        var lineTwo = reloaded.getLine(1);
        var element = lineOne.getElement(lineOne.elementCount() - 1);

        assertThat(element.getType()).as("element type").isEqualTo(ElementType.REPEAT_RIGHT);
        assertAnnotationEquals(element, annotationCase, "annotation on a line-ending REPEAT_RIGHT");

        assertThat(lineTwo.getElement(FIRST_ELEMENT_INDEX).findAttachment(AnnotationAttachment.class))
            .as("the annotation does not bleed onto line 2's first note")
            .isNull();
    }

    /**
     * The second of the three release points: a held REPEAT_RIGHT followed by a barline that
     * is not a left-forward repeat is flushed by {@code processBarline}'s own branch, not by
     * the note path or the line break. The unannotated shape of this is covered by
     * {@code MusicXmlBarlineRoundTripTest.testHeldRepeatFollowedByNonLeftRoundTrips}; this
     * adds the annotation the flush has to carry with it.
     */
    @Test
    void testAnnotationOnRepeatRightFlushedByFollowingBarlineStaysOnIt() throws Exception {
        var annotationCase = new AnnotationCase(
            TEXT_BELOW_CENTER, Component.CENTER_ALIGNMENT, Annotation.Placement.BELOW, Y_OFFSET_BELOW_CENTER_SS);

        var song = buildSong(line -> {
            line.addElement(crotchet());
            var element = repeatRight();
            line.addElement(element);
            attachAnnotation(element, annotationCase);
            line.addElement(singleBarline());
            line.addElement(crotchet());
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);
        var element = line.getElement(SECOND_ELEMENT_INDEX);

        assertThat(element.getType()).as("element type").isEqualTo(ElementType.REPEAT_RIGHT);
        assertAnnotationEquals(element, annotationCase, "annotation on a REPEAT_RIGHT flushed by the next barline");

        var barline = line.getElement(THIRD_ELEMENT_INDEX);
        assertThat(barline.getType()).as("following element type").isEqualTo(ElementType.SINGLE_BARLINE);
        assertThat(barline.findAttachment(AnnotationAttachment.class))
            .as("the barline that triggered the flush does not take the annotation")
            .isNull();
    }

    @Test
    void testAnnotatedRepeatLeftRightEndingEndKeepsHeldMarkerAndAnnotation() throws Exception {
        // Line layout:
        //   REPEAT_LEFT(0) | C(1) | REPEAT_RIGHT(2) | C(3) | REPEAT_LEFT_RIGHT(4, annotated) | C(5)
        // Ending: anchor=REPEAT_LEFT, split=REPEAT_RIGHT, end=REPEAT_LEFT_RIGHT.
        //
        // The ending *ends* on the combined barline, so its <ending number="2" type="stop">
        // rides on the backward-right half — the half the reader parks. Both the marker and
        // the annotation are then held across the merge in BarlineParser.processBarline and
        // released together by attachHeldRepeatRight. This is the only shape where a parked
        // marker the resolver acts on has to survive the merge: a split's number="1" stop is
        // deliberately discarded (the split is recomputed from element types at load time),
        // so a fixture split on the combined barline would still pass with the held markers
        // thrown away entirely. Drop them here and no Ending is rebuilt at all.
        var annotationCase = new AnnotationCase(
            TEXT_ABOVE_RIGHT, Component.RIGHT_ALIGNMENT, Annotation.Placement.ABOVE, Y_OFFSET_ABOVE_RIGHT_SS);

        var song = buildSong(line -> {
            var anchorElement = repeatLeft();
            var splitElement = repeatRight();
            var endElement = repeatLeftRight();
            line.addElement(anchorElement);
            line.addElement(crotchet());
            line.addElement(splitElement);
            line.addElement(crotchet());
            line.addElement(endElement);
            attachAnnotation(endElement, annotationCase);
            line.addElement(crotchet());
            line.addSpan(new Ending(anchorElement, endElement));
        });

        var reloaded = roundTrip(song);
        var line = reloaded.getLine(0);
        var endings = line.findEndings();

        assertThat(endings).as("ending count").hasSize(1);
        var ending = endings.getFirst();
        assertSpanEquals(ending, ENDING_ANCHOR_INDEX, ENDING_END_INDEX, "annotated REPEAT_LEFT_RIGHT-ended ending");
        assertThat(ending.getSplitIndex(line))
            .as("split index: REPEAT_RIGHT must be at index " + ENDING_SPLIT_INDEX)
            .isEqualTo(ENDING_SPLIT_INDEX);

        var endElement = line.getElement(ENDING_END_INDEX);
        assertThat(endElement.getType()).as("end element type").isEqualTo(ElementType.REPEAT_LEFT_RIGHT);
        assertAnnotationEquals(endElement, annotationCase, "annotation on the ending-ending REPEAT_LEFT_RIGHT");
    }
}
