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

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import songscribe.Strings;
import songscribe.UnitTest;

import songscribe.dom.SongFactory.LineBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What {@link BreathMarkLoadPass#run} promises about a freshly parsed song: every breath mark
 * that does not follow a duration element is removed, a run of consecutive breath marks keeps at
 * most its first, each removal is reported against the position it stood at once earlier
 * removals have closed the line up, and the pass leaves no mutation behind for undo to see.
 */
class BreathMarkLoadPassTest extends UnitTest {

    /** The index each removed breath mark is reported at, once earlier removals have closed the line up. */
    private static final int LEADING_BREATH_MARK_REPORTED_INDEX = 0;
    private static final int BREATH_MARK_AFTER_BARLINE_REPORTED_INDEX = 2;
    private static final int SECOND_OF_CONSECUTIVE_PAIR_REPORTED_INDEX = 6;

    /** The elements the pass leaves behind, in the order they end up in. */
    private static final int REMAINING_ELEMENT_COUNT = 7;
    private static final int REMAINING_BREATH_MARK_AFTER_NOTE_INDEX = 3;
    private static final int REMAINING_FIRST_OF_CONSECUTIVE_PAIR_INDEX = 5;

    /** A line index of the song under test that holds the breath marks, after zero, one or two lines. */
    @ParameterizedTest(name = "line {0}")
    @ValueSource(ints = {0, 1, 2})
    void testRunDropsEveryBreathMarkNotFollowingADurationElementAndEmitsNoMutation(int lineIndex) {
        var builders = new LineBuilder[lineIndex + 1];

        for (var i = 0; i < lineIndex; i++) {
            builders[i] = line -> line.addElement(crotchet());
        }

        builders[lineIndex] = line -> {
            line.addElement(breathMark());       // leads the line — removed
            line.addElement(crotchet());
            line.addElement(singleBarline());
            line.addElement(breathMark());       // follows a barline — removed
            line.addElement(crotchet());
            line.addElement(breathMark());       // follows a note — kept
            line.addElement(crotchet());
            line.addElement(breathMark());       // first of a consecutive pair, follows a note — kept
            line.addElement(breathMark());       // second of the pair, follows a breath mark — removed
            line.addElement(crotchet());
        };

        var song = SongFactory.buildSong(builders);
        var report = new ParsedSong(song, List.of(), List.of(), List.of(), List.of()).settle();

        assertThat(report.changes()).containsExactly(
            removedAt(lineIndex, LEADING_BREATH_MARK_REPORTED_INDEX),
            removedAt(lineIndex, BREATH_MARK_AFTER_BARLINE_REPORTED_INDEX),
            removedAt(lineIndex, SECOND_OF_CONSECUTIVE_PAIR_REPORTED_INDEX)
        );

        var line = song.getLine(lineIndex);
        assertThat(line.elementCount()).isEqualTo(REMAINING_ELEMENT_COUNT);
        assertThat(line.getElement(REMAINING_BREATH_MARK_AFTER_NOTE_INDEX).getType().isBreathMark()).isTrue();
        assertThat(line.getElement(REMAINING_FIRST_OF_CONSECUTIVE_PAIR_INDEX).getType().isBreathMark()).isTrue();

        assertThat(song.isModified()).isFalse();
    }

    /** The entry the pass records for the breath mark that stood at {@code index} of line {@code lineIndex}. */
    private static ReportedChange removedAt(int lineIndex, int index) {
        return new ReportedChange(
            Strings.DIALOG_REPORT_BREATH_MARKS_REMOVED,
            Strings.get(Strings.DIALOG_REPORT_REASON_BREATH_MARK_HOST),
            ElementLocationRange.single(new ElementLocation(lineIndex, index)));
    }
}
