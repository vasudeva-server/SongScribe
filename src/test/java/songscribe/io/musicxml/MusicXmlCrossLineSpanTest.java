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

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Beam;
import songscribe.dom.Span;
import songscribe.dom.Trill;
import songscribe.dom.Tuplet;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.io.XmlFixtures.closeTag;
import static songscribe.io.XmlFixtures.element;
import static songscribe.io.XmlFixtures.emptyTag;
import static songscribe.io.XmlFixtures.openTag;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.middleCEighth;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.parse;
import static songscribe.io.musicxml.MusicXmlRoundTripSupport.scoreWithMeasureBody;

/**
 * A span that opens on one line and closes on the next cannot be represented — a line holds its
 * own spans — so the reader drops it and reads the rest of the document. The span
 * kinds the reader pairs across the whole part are the ones that can state it: a beam, a tuplet
 * and a trill.
 *
 * <p>Each row is also read with its notes on one line, which proves the span is dropped for the
 * line break and not for anything else in the markup.
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
     */
    private record CrossLineCase(String description, List<String> notes, Class<? extends Span<?>> spanType) {

        /** The notes with {@code separator} between the last note and the ones before it. */
        String markupSplitBeforeLastNote(String separator) {
            var upToLast = String.join("", notes.subList(0, notes.size() - 1));
            return upToLast + separator + notes.getLast();
        }
    }

    private static final String TRIPLET_ACTUAL_NOTES = "3";
    private static final String TRIPLET_NORMAL_NOTES = "2";

    private static final String NEW_SYSTEM =
        "      " + emptyTag(MusicXmlTags.PRINT, MusicXmlTags.ATTR_NEW_SYSTEM, MusicXmlTags.YES) + '\n';

    private static final int FIRST_LINE = 0;

    /** The lines a span's notes land on when {@link #NEW_SYSTEM} splits them. */
    private static final int CROSS_LINE_COUNT = 2;

    @ParameterizedTest(name = "{0}")
    @MethodSource("crossLineCases")
    void testASpanOpeningOnOneLineAndClosingOnTheNextIsDropped(CrossLineCase testCase) throws Exception {
        var song = parse(scoreWithMeasureBody(testCase.markupSplitBeforeLastNote(NEW_SYSTEM)));

        assertThat(song.getLines())
            .as("a line holds its own spans, so a span across two lines has no line to live on")
            .hasSize(CROSS_LINE_COUNT)
            .allSatisfy(line -> assertThat(line.spanRegistry().getSpans()).isEmpty());
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
                middleCEighth(beam(MusicXmlTags.BEAM_END))), Beam.class),
            new CrossLineCase("a tuplet", List.of(
                middleCEighth(tripletRatio(), tupletBracket(MusicXmlTags.TYPE_START)),
                middleCEighth(tripletRatio()),
                middleCEighth(tripletRatio(), tupletBracket(MusicXmlTags.TYPE_STOP))), Tuplet.class),
            new CrossLineCase("a trill", List.of(
                middleCEighth(trill(MusicXmlTags.TYPE_START)),
                middleCEighth(trill(MusicXmlTags.TYPE_STOP))), Trill.class)
        );
    }

    /** The primary {@code <beam number="1">} marker with {@code beamValue}. */
    private static String beam(String beamValue) {
        return openTag(MusicXmlTags.BEAM, MusicXmlTags.ATTR_NUMBER, MusicXmlTags.NUMBER_1)
            + beamValue + closeTag(MusicXmlTags.BEAM);
    }

    /** A triplet's {@code <time-modification>}, which every note under the bracket carries. */
    private static String tripletRatio() {
        return element(MusicXmlTags.TIME_MOD,
            element(MusicXmlTags.ACTUAL_NOTES, TRIPLET_ACTUAL_NOTES)
                + element(MusicXmlTags.NORMAL_NOTES, TRIPLET_NORMAL_NOTES)
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
