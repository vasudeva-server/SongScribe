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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.loadCompactSong;

/**
 * {@link TerminalLoadPass#run}, as {@link SongLoad#run} runs it: a final double bar line stands
 * only as the song's last element, each one that does not is removed and reported on its line, and
 * the song's last element is a valid terminal afterwards.
 */
class TerminalLoadPassTest extends UnitTest {

    private static final Key STARTING_KEY = Key.NO_ACCIDENTALS;

    /**
     * A song as a reader leaves it, and where the pass reports its removals.
     *
     * @param description the case, as the test's display name
     * @param notations   one compact notation per line, in song order; {@code F} is a final double
     *                    bar line
     * @param removedOn   the index of the line of each final double bar line the pass removes, in
     *                    document order
     */
    private record TerminalCase(String description, String[] notations, List<Integer> removedOn) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("terminalCases")
    void testEveryFinalDoubleBarLineThatIsNotLastIsRemovedAndReportedAndTheLastElementIsATerminal(
        TerminalCase testCase
    ) throws StrandedKeyChangeException {
        var loaded = loadCompactSong(STARTING_KEY, testCase.notations());
        var song = loaded.song();

        assertThat(loaded.report().changes()).containsExactlyElementsOf(testCase.removedOn().stream()
            .map(lineIndex -> new ReportedChange(
                ChangeHeading.FINAL_BARLINES_REMOVED,
                Strings.get(Strings.REPORT_REASON_FINAL_BARLINE_NOT_LAST),
                new LineRange(lineIndex, lineIndex)))
            .toList());

        var finalBarLineCount = song.getLines().stream()
            .flatMap(line -> line.getElements().stream())
            .filter(element -> element.getType() == ElementType.FINAL_DOUBLE_BARLINE)
            .count();
        var lastLine = song.getLines().getLast();

        assertThat(finalBarLineCount).isEqualTo(1);
        assertThat(lastLine.getElement(lastLine.elementCount() - 1).getType().isValidSongTerminal()).isTrue();
    }

    static Stream<TerminalCase> terminalCases() {
        return Stream.of(
            new TerminalCase("a final bar line ending a line that is not last",
                new String[] {"3 3 F", "3 3"}, List.of(0)),
            new TerminalCase("a final bar line in the middle of the last line",
                new String[] {"3 F 3"}, List.of(0)),
            new TerminalCase("one on each of two lines, reported in document order",
                new String[] {"3 F", "3 F", "3"}, List.of(0, 1)),
            new TerminalCase("two on one line, one entry each",
                new String[] {"3 F 3 F 3"}, List.of(0, 0)),
            new TerminalCase("a final bar line that is the last element stays and nothing is reported",
                new String[] {"3 3", "3 F"}, List.of()));
    }

    @Test
    void testABarLineEndingTheSongBecomesItsFinalBarLineAndAnEndingOnItStays() throws StrandedKeyChangeException {
        var loaded = loadCompactSong(
            song -> {
                var line = song.getLine(0);

                return new PendingMarks(
                    List.of(),
                    List.of(new PendingEnding(
                        line.getElement(ENDING_ANCHOR_INDEX), line.getElement(CLOSING_BAR_INDEX),
                        UserOffset.NONE)),
                    List.of(), List.of(), List.of(), List.of());
            },
            STARTING_KEY, ENDING_ON_CLOSING_BAR);
        var line = loaded.song().getLine(0);

        assertThat(line.elementCount()).as("the bar line is replaced, not followed by a terminal")
            .isEqualTo(CLOSING_BAR_INDEX + 1);
        assertThat(line.getElement(CLOSING_BAR_INDEX).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);
        assertThat(line.spanRegistry().findSpans(Ending.class)).singleElement()
            .extracting(Ending::getEndElement).isSameAs(line.getElement(CLOSING_BAR_INDEX));
        assertThat(loaded.report().isEmpty()).isTrue();
    }

    /** Two notes, a bar line, two notes, a right repeat, two notes and a closing bar line. */
    private static final String ENDING_ON_CLOSING_BAR = "3 3 | 3 3 :| 3 3 |";

    /** The first bar line of {@link #ENDING_ON_CLOSING_BAR}. */
    private static final int ENDING_ANCHOR_INDEX = 2;

    /** The closing bar line of {@link #ENDING_ON_CLOSING_BAR}, the song's last element. */
    private static final int CLOSING_BAR_INDEX = 8;

}
