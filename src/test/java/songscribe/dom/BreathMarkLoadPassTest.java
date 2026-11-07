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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import songscribe.Strings;
import songscribe.UnitTest;

import songscribe.dom.SongFactory.LineBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.breathMark;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What {@link BreathMarkLoadPass#run} promises about a freshly parsed song: every breath mark
 * that does not follow a duration element is removed, a run of consecutive breath marks keeps at
 * most its first, and each line that loses a breath mark is reported once, in line order.
 */
class BreathMarkLoadPassTest extends UnitTest {

    /** The elements the pass leaves behind, the song's own final barline included. */
    private static final int REMAINING_ELEMENT_COUNT = 8;
    private static final int REMAINING_BREATH_MARK_AFTER_NOTE_INDEX = 3;
    private static final int REMAINING_FIRST_OF_CONSECUTIVE_PAIR_INDEX = 5;
    private static final int FIRST_LOSING_LINE_INDEX = 0;
    private static final int SECOND_LOSING_LINE_INDEX = 2;

    /** A line index of the song under test that holds the breath marks, after zero, one or two lines. */
    @ParameterizedTest(name = "line {0}")
    @ValueSource(ints = {0, 1, 2})
    void testEveryBreathMarkNotFollowingADurationElementIsRemovedAndItsLineReportedOnce(int lineIndex)
        throws StrandedKeyChangeException {
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
            line.addElement(finalDoubleBarline());
        };

        var loaded = SongFactory.loadSong(builders);
        var song = loaded.song();

        assertThat(loaded.report().changes()).containsExactly(removedOn(lineIndex));

        var line = song.getLine(lineIndex);
        assertThat(line.elementCount()).isEqualTo(REMAINING_ELEMENT_COUNT);
        assertThat(line.getElement(REMAINING_BREATH_MARK_AFTER_NOTE_INDEX).getType().isBreathMark()).isTrue();
        assertThat(line.getElement(REMAINING_FIRST_OF_CONSECUTIVE_PAIR_INDEX).getType().isBreathMark()).isTrue();
    }

    @Test
    void testEachLineThatLosesABreathMarkIsReportedOnceInLineOrder() throws StrandedKeyChangeException {
        LineBuilder losesTwo = line -> {
            line.addElement(breathMark());       // leads the line — removed
            line.addElement(crotchet());
            line.addElement(singleBarline());
            line.addElement(breathMark());       // follows a barline — removed
            line.addElement(crotchet());
        };
        LineBuilder losesNone = line -> {
            line.addElement(crotchet());
            line.addElement(breathMark());       // follows a note — kept
        };
        LineBuilder losesOne = line -> {
            line.addElement(crotchet());
            line.addElement(singleBarline());
            line.addElement(breathMark());       // follows a barline — removed
            line.addElement(crotchet());
            line.addElement(finalDoubleBarline());
        };

        var loaded = SongFactory.loadSong(losesTwo, losesNone, losesOne);

        assertThat(loaded.report().changes()).containsExactly(
            removedOn(FIRST_LOSING_LINE_INDEX),
            removedOn(SECOND_LOSING_LINE_INDEX)
        );
    }

    /** The entry the pass records for line {@code lineIndex} when it loses a breath mark. */
    private static ReportedChange removedOn(int lineIndex) {
        return new ReportedChange(
            ChangeHeading.BREATH_MARKS_REMOVED,
            Strings.get(Strings.REPORT_REASON_BREATH_MARK_HOST),
            new LineRange(lineIndex, lineIndex));
    }
}
