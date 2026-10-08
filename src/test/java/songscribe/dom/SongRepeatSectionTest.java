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

package songscribe.dom;

import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.doubleBarline;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.repeatLeft;
import static songscribe.dom.StaffElementFactory.repeatLeftRight;
import static songscribe.dom.StaffElementFactory.repeatRight;

/**
 * {@link Song#repeatSectionStart}: the first element of the repeat section open just before a
 * queried location, found by walking backward across lines from an opener to a closer or to the
 * song start.
 */
class SongRepeatSectionTest extends UnitTest {

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;
    private static final int THIRD_LINE = 2;

    /** The element index right after an opener, in a line built {@code <opener> <note> <note>}. */
    private static final int AFTER_OPENER_INDEX = 1;

    /** The last note's index in a line built {@code <opener> <note> <note>}. */
    private static final int THIRD_ELEMENT_INDEX = 2;

    /** The note's index in a line built {@code <opener> <note> <closer> <note>}. */
    private static final int FOURTH_ELEMENT_INDEX = 3;

    /**
     * A song, a queried location in it, and the location {@link Song#repeatSectionStart} must
     * answer.
     *
     * @param description the case, as the test's display name
     * @param song        the song to query
     * @param query       the location to ask about
     * @param expected    the expected answer, or {@code null} when no section is open
     */
    private record RepeatSectionCase(
        String description, Song song, ElementLocation query, @Nullable ElementLocation expected
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("repeatSectionCases")
    void testRepeatSectionStartFindsTheOpenerOrStopsAtACloser(RepeatSectionCase testCase) {
        assertThat(testCase.song().repeatSectionStart(testCase.query())).isEqualTo(testCase.expected());
    }

    private static Song oneLineSong(SongFactory.LineBuilder builder) {
        return SongFactory.buildSong(line -> {
            line.setKey(Key.NO_ACCIDENTALS);
            builder.build(line);
        });
    }

    static Stream<RepeatSectionCase> repeatSectionCases() {
        return Stream.of(
            new RepeatSectionCase("a |: opener in the query's own line",
                oneLineSong(line -> {
                    line.addElement(repeatLeft());
                    line.addElement(crotchet());
                    line.addElement(crotchet());
                }),
                new ElementLocation(FIRST_LINE, THIRD_ELEMENT_INDEX), new ElementLocation(FIRST_LINE, AFTER_OPENER_INDEX)),

            new RepeatSectionCase("a :|: opener in the query's own line",
                oneLineSong(line -> {
                    line.addElement(repeatLeftRight());
                    line.addElement(crotchet());
                    line.addElement(crotchet());
                }),
                new ElementLocation(FIRST_LINE, THIRD_ELEMENT_INDEX), new ElementLocation(FIRST_LINE, AFTER_OPENER_INDEX)),

            new RepeatSectionCase("an opener on an earlier line",
                SongFactory.buildSong(
                    line -> {
                        line.setKey(Key.NO_ACCIDENTALS);
                        line.addElement(repeatLeft());
                        line.addElement(crotchet());
                    },
                    line -> {
                        line.addElement(crotchet());
                        line.addElement(crotchet());
                    }),
                new ElementLocation(SECOND_LINE, 1), new ElementLocation(FIRST_LINE, 1)),

            new RepeatSectionCase("no opener answers the song's first element",
                oneLineSong(line -> {
                    line.addElement(crotchet());
                    line.addElement(crotchet());
                }),
                new ElementLocation(FIRST_LINE, 1), new ElementLocation(FIRST_LINE, 0)),

            new RepeatSectionCase("a leading empty line before the first element is skipped",
                SongFactory.buildSong(
                    line -> line.setKey(Key.NO_ACCIDENTALS),
                    line -> {
                        line.addElement(crotchet());
                        line.addElement(crotchet());
                    }),
                new ElementLocation(SECOND_LINE, 1), new ElementLocation(SECOND_LINE, 0)),

            new RepeatSectionCase("an opener that is the last element of its line",
                SongFactory.buildSong(
                    line -> {
                        line.setKey(Key.NO_ACCIDENTALS);
                        line.addElement(crotchet());
                        line.addElement(repeatLeft());
                    },
                    line -> {
                        line.addElement(crotchet());
                        line.addElement(crotchet());
                    }),
                new ElementLocation(SECOND_LINE, 1), new ElementLocation(SECOND_LINE, 0)),

            new RepeatSectionCase("a stop at a :|",
                oneLineSong(line -> {
                    line.addElement(repeatLeft());
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                    line.addElement(crotchet());
                }),
                new ElementLocation(FIRST_LINE, FOURTH_ELEMENT_INDEX), null),

            new RepeatSectionCase("a stop at a ||",
                oneLineSong(line -> {
                    line.addElement(repeatLeft());
                    line.addElement(crotchet());
                    line.addElement(doubleBarline());
                    line.addElement(crotchet());
                }),
                new ElementLocation(FIRST_LINE, FOURTH_ELEMENT_INDEX), null),

            new RepeatSectionCase("a stop at a final barline",
                oneLineSong(line -> {
                    line.addElement(repeatLeft());
                    line.addElement(crotchet());
                    line.addElement(finalDoubleBarline());
                    line.addElement(crotchet());
                }),
                new ElementLocation(FIRST_LINE, FOURTH_ELEMENT_INDEX), null),

            new RepeatSectionCase("a query on a :| asks about the section that :| ends",
                oneLineSong(line -> {
                    line.addElement(repeatLeft());
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                }),
                new ElementLocation(FIRST_LINE, THIRD_ELEMENT_INDEX), new ElementLocation(FIRST_LINE, AFTER_OPENER_INDEX)),

            new RepeatSectionCase("a query on a |: does not count that opener",
                oneLineSong(line -> {
                    line.addElement(crotchet());
                    line.addElement(repeatLeft());
                    line.addElement(crotchet());
                }),
                new ElementLocation(FIRST_LINE, AFTER_OPENER_INDEX), new ElementLocation(FIRST_LINE, 0)),

            new RepeatSectionCase("a query directly after an opener answers its own location",
                oneLineSong(line -> {
                    line.addElement(repeatLeft());
                    line.addElement(crotchet());
                    line.addElement(crotchet());
                }),
                new ElementLocation(FIRST_LINE, AFTER_OPENER_INDEX), new ElementLocation(FIRST_LINE, AFTER_OPENER_INDEX)),

            new RepeatSectionCase("a query at the first element answers the song start",
                oneLineSong(line -> {
                    line.addElement(crotchet());
                    line.addElement(crotchet());
                }),
                new ElementLocation(FIRST_LINE, 0), new ElementLocation(FIRST_LINE, 0)),

            new RepeatSectionCase("an opener across an empty middle line",
                SongFactory.buildSong(
                    line -> {
                        line.setKey(Key.NO_ACCIDENTALS);
                        line.addElement(repeatLeft());
                        line.addElement(crotchet());
                    },
                    line -> {},
                    line -> {
                        line.addElement(crotchet());
                        line.addElement(crotchet());
                    }),
                new ElementLocation(THIRD_LINE, 1), new ElementLocation(FIRST_LINE, 1)),

            new RepeatSectionCase("a :| after a || has no open section and plays on",
                oneLineSong(line -> {
                    line.addElement(crotchet());
                    line.addElement(doubleBarline());
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                }),
                new ElementLocation(FIRST_LINE, FOURTH_ELEMENT_INDEX), null),

            new RepeatSectionCase("a :| after a closed section has no open section and plays on",
                oneLineSong(line -> {
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                    line.addElement(crotchet());
                    line.addElement(repeatRight());
                }),
                new ElementLocation(FIRST_LINE, FOURTH_ELEMENT_INDEX), null)
        );
    }
}
