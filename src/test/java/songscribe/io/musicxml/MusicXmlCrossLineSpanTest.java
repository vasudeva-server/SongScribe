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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.ElementType;
import songscribe.dom.LineRange;
import songscribe.dom.ReportedChange;
import songscribe.dom.Span;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.io.XmlFixtures.attr;
import static songscribe.io.XmlFixtures.closeTag;
import static songscribe.io.XmlFixtures.element;
import static songscribe.io.XmlFixtures.emptyTag;
import static songscribe.io.XmlFixtures.openTag;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.EIGHTH_DURATION;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.MIDDLE_C_PITCH;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.middleCEighth;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.note;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parseResult;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;

/**
 * A mark the reader cannot keep is dropped, and the rest of the document is read. Each dropped
 * mark makes one report entry, under its kind's heading, with the reason it was dropped and the
 * elements it sat on, and the notes it sat on stay.
 *
 * <p>A span that opens on one line and closes on the next cannot be represented — a line holds
 * its own spans. The span kinds the reader pairs across the whole part are the ones that can
 * state it: a beam, a tuplet and a trill. Each of those rows is also read with its notes on one
 * line, which proves the span is dropped for the line break and not for anything else in the
 * markup.
 */
class MusicXmlCrossLineSpanTest extends UnitTest {

    /**
     * One span kind and the notes that carry it from its start marker to its stop marker.
     *
     * <p>The note count is the kind's own business rather than a fixture convention: a beam and a
     * trill are stated by two notes, while a triplet's 3:2 ratio is only satisfied by three, so a
     * two-note triplet is refused for its ratio and would prove nothing about line breaks.
     * A cross-line triplet has three notes too, so the line break is its only defect.
     *
     * @param description the case, as the test's display name
     * @param notes       the span's notes in order, the first carrying its start marker and the
     *                    last its stop marker
     * @param spanType    the span the notes build when they all sit on one line
     * @param headingKey  the report heading the dropped span is reported under
     */
    private record CrossLineCase(
        String description, List<String> notes, Class<? extends Span<?>> spanType, String headingKey
    ) {

        /** The notes with {@code separator} between the last note and the ones before it. */
        String markupSplitBeforeLastNote(String separator) {
            var upToLast = String.join("", notes.subList(0, notes.size() - 1));
            return upToLast + separator + notes.getLast();
        }
    }

    private static final String TRIPLET_ACTUAL_NOTES = "3";
    private static final String TRIPLET_NORMAL_NOTES = "2";

    /** An {@code <actual-notes>} that no tuplet grade holds, one below the lowest and one above the highest. */
    private static final String TUPLET_ACTUAL_NOTES_BELOW_GRADES = "1";
    private static final String TUPLET_ACTUAL_NOTES_ABOVE_GRADES = "8";

    private static final String NO_NORMAL_NOTES = "0";

    private static final String NEW_SYSTEM =
        "      " + emptyTag(MusicXmlTags.PRINT, MusicXmlTags.ATTR_NEW_SYSTEM, MusicXmlTags.YES) + '\n';

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final LineRange FIRST_LINE_RANGE = new LineRange(FIRST_LINE, FIRST_LINE);

    /** The lines a span's notes land on when {@link #NEW_SYSTEM} splits them. */
    private static final int CROSS_LINE_COUNT = 2;

    @ParameterizedTest(name = "{0}")
    @MethodSource("crossLineCases")
    void testASpanOpeningOnOneLineAndClosingOnTheNextIsDropped(CrossLineCase testCase) throws Exception {
        var result = parseResult(scoreWithMeasureBody(testCase.markupSplitBeforeLastNote(NEW_SYSTEM)));

        assertThat(result.song().getLines())
            .as("a line holds its own spans, so a span across two lines has no line to live on")
            .hasSize(CROSS_LINE_COUNT)
            .allSatisfy(line -> assertThat(line.spanRegistry().getSpans()).isEmpty());
        assertThat(result.report().changes()).containsExactly(new ReportedChange(
            testCase.headingKey(),
            Strings.get(Strings.REPORT_REASON_DAMAGED_LINE_BREAK),
            new LineRange(FIRST_LINE, SECOND_LINE)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("crossLineCases")
    void testTheSameSpanOpeningAndClosingOnOneLineIsRead(CrossLineCase testCase) throws Exception {
        var song = parse(scoreWithMeasureBody(testCase.markupSplitBeforeLastNote("")));

        assertThat(song.getLine(FIRST_LINE).spanRegistry().getSpans())
            .as("the same markup with no line break between the notes builds the span")
            .singleElement()
            .isInstanceOf(testCase.spanType());
    }

    static Stream<CrossLineCase> crossLineCases() {
        return Stream.of(
            new CrossLineCase("a beam", List.of(
                middleCEighth(beam(MusicXmlTags.BEAM_BEGIN)),
                middleCEighth(beam(MusicXmlTags.BEAM_END))), Beam.class, Strings.REPORT_BEAMS_REMOVED),
            new CrossLineCase("a tuplet", List.of(
                middleCEighth(tripletRatio(), tupletBracket(MusicXmlTags.TYPE_START)),
                middleCEighth(tripletRatio()),
                middleCEighth(tripletRatio(), tupletBracket(MusicXmlTags.TYPE_STOP))),
                Tuplet.class, Strings.REPORT_TUPLETS_REMOVED),
            new CrossLineCase("a trill", List.of(
                middleCEighth(trill(MusicXmlTags.TYPE_START)),
                middleCEighth(trill(MusicXmlTags.TYPE_STOP))), Trill.class, Strings.REPORT_TRILLS_REMOVED)
        );
    }

    /**
     * One document with a mark the reader drops, and what the load reports.
     *
     * @param description the mark, as the test's display name
     * @param markup      the measure body holding the mark
     * @param noteCount   the notes {@code markup} writes, each of which stays
     * @param expected    the one entry the dropped mark makes
     */
    private record DroppedMarkCase(String description, String markup, int noteCount, ReportedChange expected) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("droppedMarkCases")
    void testADroppedMarkMakesOneReportEntryAndKeepsItsNotes(DroppedMarkCase testCase) throws Exception {
        var result = parseResult(scoreWithMeasureBody(testCase.markup()));

        assertThat(result.report().changes()).containsExactly(testCase.expected());
        assertThat(result.song().getLines().stream()
            .flatMap(line -> line.getElements().stream())
            .filter(element -> isPlayedElement(element.getType().kind()))
            .count()).isEqualTo(testCase.noteCount());
    }

    private static boolean isPlayedElement(ElementType.Kind kind) {
        return kind == ElementType.Kind.NOTE || kind == ElementType.Kind.GRACE_NOTE || kind == ElementType.Kind.REST;
    }

    static Stream<DroppedMarkCase> droppedMarkCases() {
        return Stream.of(
            new DroppedMarkCase("a beam start no end pairs with",
                middleCEighth(beam(MusicXmlTags.BEAM_BEGIN)) + middleCEighth(), 2,
                dropped(Strings.REPORT_BEAMS_REMOVED, Strings.REPORT_REASON_DAMAGED_NO_END)),
            new DroppedMarkCase("a beam end no start pairs with",
                middleCEighth() + middleCEighth(beam(MusicXmlTags.BEAM_END)), 2,
                dropped(Strings.REPORT_BEAMS_REMOVED, Strings.REPORT_REASON_DAMAGED_NO_START)),
            new DroppedMarkCase("a trill start no end pairs with",
                middleCEighth(trill(MusicXmlTags.TYPE_START)) + middleCEighth(), 2,
                dropped(Strings.REPORT_TRILLS_REMOVED, Strings.REPORT_REASON_DAMAGED_NO_END)),
            new DroppedMarkCase("a trill end no start pairs with",
                middleCEighth() + middleCEighth(trill(MusicXmlTags.TYPE_STOP)), 2,
                dropped(Strings.REPORT_TRILLS_REMOVED, Strings.REPORT_REASON_DAMAGED_NO_START)),
            new DroppedMarkCase("a hairpin still open when the part ends",
                wedge(MusicXmlTags.WEDGE_CRESCENDO) + middleCEighth() + middleCEighth(), 2,
                dropped(Strings.REPORT_HAIRPINS_REMOVED, Strings.REPORT_REASON_DAMAGED_NO_END)),
            new DroppedMarkCase("an ending still open when the part ends",
                endingStart() + middleCEighth() + middleCEighth(), 2,
                dropped(Strings.REPORT_ENDINGS_REMOVED, Strings.REPORT_REASON_DAMAGED_NO_END)),
            new DroppedMarkCase("a tuplet whose actual-notes is below every grade",
                threeNoteTuplet(timeModification(TUPLET_ACTUAL_NOTES_BELOW_GRADES, TRIPLET_NORMAL_NOTES)), 3,
                dropped(Strings.REPORT_TUPLETS_REMOVED, Strings.REPORT_REASON_DAMAGED_TUPLET_NUMBER)),
            new DroppedMarkCase("a tuplet whose actual-notes is above every grade",
                threeNoteTuplet(timeModification(TUPLET_ACTUAL_NOTES_ABOVE_GRADES, TRIPLET_NORMAL_NOTES)), 3,
                dropped(Strings.REPORT_TUPLETS_REMOVED, Strings.REPORT_REASON_DAMAGED_TUPLET_NUMBER)),
            new DroppedMarkCase("a tuplet with a normal-type and no positive normal-notes",
                threeNoteTuplet(timeModification(TRIPLET_ACTUAL_NOTES, NO_NORMAL_NOTES)), 3,
                dropped(Strings.REPORT_TUPLETS_REMOVED, Strings.REPORT_REASON_DAMAGED_NORMAL_NOTES)),
            new DroppedMarkCase("a tie on a rest",
                eighthRest(TIED_START) + middleCEighth(), 2,
                dropped(Strings.REPORT_TIES_REMOVED, Strings.REPORT_REASON_DAMAGED_WRONG_ELEMENT)),
            new DroppedMarkCase("a tie on a grace note",
                note(element(MusicXmlTags.GRACE, ""), MIDDLE_C_PITCH,
                    element(MusicXmlTags.NOTE_TYPE, NoteTypeMapping.TYPE_EIGHTH), TIED_START) + middleCEighth(), 2,
                dropped(Strings.REPORT_TIES_REMOVED, Strings.REPORT_REASON_DAMAGED_WRONG_ELEMENT)),
            new DroppedMarkCase("a trill starting on a rest",
                eighthRest(trill(MusicXmlTags.TYPE_START)) + middleCEighth(trill(MusicXmlTags.TYPE_STOP)), 2,
                dropped(Strings.REPORT_TRILLS_REMOVED, Strings.REPORT_REASON_TRILL_ENDPOINT_UNUSABLE)),
            new DroppedMarkCase("a trill covering a rest",
                middleCEighth(trill(MusicXmlTags.TYPE_START)) + eighthRest()
                    + middleCEighth(trill(MusicXmlTags.TYPE_STOP)), 3,
                dropped(Strings.REPORT_TRILLS_REMOVED, Strings.REPORT_REASON_TRILL_REST_OR_BREATH_MARK)),
            new DroppedMarkCase("a beam mark on a rest",
                eighthRest(beam(MusicXmlTags.BEAM_BEGIN)) + middleCEighth(beam(MusicXmlTags.BEAM_END)), 2,
                new ReportedChange(
                    Strings.REPORT_BEAMS_REMOVED,
                    Strings.get(Strings.REPORT_REASON_DAMAGED_WRONG_ELEMENT),
                    FIRST_LINE_RANGE))
        );
    }

    /** The entry for a mark dropped from the first line. */
    private static ReportedChange dropped(String headingKey, String reasonKey) {
        return new ReportedChange(headingKey, Strings.get(reasonKey), FIRST_LINE_RANGE);
    }

    /** Three eighths that open and close one tuplet bracket, each carrying {@code ratio}. */
    private static String threeNoteTuplet(String ratio) {
        return middleCEighth(ratio, tupletBracket(MusicXmlTags.TYPE_START))
            + middleCEighth(ratio)
            + middleCEighth(ratio, tupletBracket(MusicXmlTags.TYPE_STOP));
    }

    /** An eighth rest carrying {@code markers} after its {@code <type>}. */
    private static String eighthRest(String... markers) {
        var children = new ArrayList<>(List.of(
            element(MusicXmlTags.REST, ""),
            element(MusicXmlTags.DURATION, EIGHTH_DURATION),
            element(MusicXmlTags.NOTE_TYPE, NoteTypeMapping.TYPE_EIGHTH)));
        children.addAll(List.of(markers));

        return note(children.toArray(String[]::new));
    }

    /** A {@code <direction>} holding a wedge of {@code wedgeType}, which binds to the next note. */
    private static String wedge(String wedgeType) {
        return element(MusicXmlTags.DIRECTION,
            element(MusicXmlTags.DIRECTION_TYPE, emptyTag(MusicXmlTags.WEDGE, MusicXmlTags.ATTR_TYPE, wedgeType)))
            + '\n';
    }

    /** The invisible left barline that carries an ending's start, which binds to the next note. */
    private static String endingStart() {
        return openTag(MusicXmlTags.BARLINE, MusicXmlTags.ATTR_LOCATION, BarlineStyleMapping.LOCATION_LEFT)
            + element(MusicXmlTags.BAR_STYLE, BarlineStyleMapping.BAR_STYLE_NONE)
            + '<' + MusicXmlTags.ENDING + ' ' + attr(MusicXmlTags.ATTR_NUMBER, MusicXmlTags.NUMBER_1)
            + ' ' + attr(MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_START) + "/>"
            + closeTag(MusicXmlTags.BARLINE) + '\n';
    }

    /** The {@code <tied type="start">} marker, inside {@code <notations>}. */
    private static final String TIED_START =
        element(MusicXmlTags.NOTATIONS, emptyTag(MusicXmlTags.TIED, MusicXmlTags.ATTR_TYPE, MusicXmlTags.TYPE_START));

    /** The primary {@code <beam number="1">} marker with {@code beamValue}. */
    private static String beam(String beamValue) {
        return openTag(MusicXmlTags.BEAM, MusicXmlTags.ATTR_NUMBER, MusicXmlTags.NUMBER_1)
            + beamValue + closeTag(MusicXmlTags.BEAM);
    }

    /** A triplet's {@code <time-modification>}, which every note under the bracket carries. */
    private static String tripletRatio() {
        return timeModification(TRIPLET_ACTUAL_NOTES, TRIPLET_NORMAL_NOTES);
    }

    /** A {@code <time-modification>} stating {@code actualNotes} in the time of {@code normalNotes} eighths. */
    private static String timeModification(String actualNotes, String normalNotes) {
        return element(MusicXmlTags.TIME_MOD,
            element(MusicXmlTags.ACTUAL_NOTES, actualNotes)
                + element(MusicXmlTags.NORMAL_NOTES, normalNotes)
                + element(MusicXmlTags.NORMAL_TYPE, NoteTypeMapping.TYPE_EIGHTH));
    }

    /** The {@code <tuplet>} bracket marker of {@code type}, which only the outer notes carry. */
    private static String tupletBracket(String type) {
        return element(MusicXmlTags.NOTATIONS, emptyTag(MusicXmlTags.TUPLET, MusicXmlTags.ATTR_TYPE, type));
    }

    /** The {@code <wavy-line>} marker of {@code type}, inside {@code <ornaments>}. */
    private static String trill(String type) {
        return element(MusicXmlTags.NOTATIONS,
            element(MusicXmlTags.ORNAMENTS,
                emptyTag(MusicXmlTags.WAVY_LINE, MusicXmlTags.ATTR_TYPE, type)));
    }
}
