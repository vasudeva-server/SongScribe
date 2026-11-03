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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.Strings;
import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.loadCompactSong;

/**
 * {@link TieLoadPass#run}, as {@link SongLoad#run} runs it: a pending tie on an element no tie can
 * anchor on is reported as damaged, a tie {@link Tie#judge} finds broken is removed and reported
 * with its reason on its lines, and a tie that holds stays.
 */
class TieLoadPassTest extends UnitTest {

    private static final int FIRST_LINE = 0;
    private static final int SECOND_LINE = 1;

    private static final Key STARTING_KEY = Key.NO_ACCIDENTALS;

    private static final String TIED = "3";
    private static final String OTHER = "4";

    /**
     * Where a pending tie's anchor stands.
     *
     * @param lineIndex    the anchor's line
     * @param elementIndex the anchor's index on that line
     */
    private record Anchor(int lineIndex, int elementIndex) {}

    /**
     * A song and the pending ties a reader found in it.
     *
     * @param description the case, as the test's display name
     * @param notations   one compact notation per line, in song order
     * @param anchors     the anchor of each pending tie, in the order the reader found them
     * @param expected    the entries the pass records, in order
     * @param kept        the anchors that are tied to the next note afterwards
     */
    private record TieCase(
        String description, String[] notations, List<Anchor> anchors, List<ReportedChange> expected,
        List<Anchor> kept
    ) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("tieCases")
    void testAPendingTieIsAddedOnlyWhileItHoldsAndEveryOtherIsReported(TieCase testCase)
        throws StrandedKeyChangeException {
        var loaded = loadCompactSong(
            song -> new PendingMarks(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                testCase.anchors().stream()
                    .map(anchor -> new PendingTie(song.getLine(anchor.lineIndex()).getElement(anchor.elementIndex())))
                    .toList()),
            STARTING_KEY, testCase.notations());
        var song = loaded.song();

        assertThat(loaded.report().changes()).containsExactlyElementsOf(testCase.expected());

        var tiedAnchors = testCase.anchors().stream()
            .filter(anchor -> song.getLine(anchor.lineIndex()).getElement(anchor.elementIndex())
                instanceof NoteElement note && note.isTiedToNext())
            .toList();

        assertThat(tiedAnchors).containsExactlyElementsOf(testCase.kept());
    }

    static Stream<TieCase> tieCases() {
        var firstNote = new Anchor(FIRST_LINE, 0);
        var secondNote = new Anchor(FIRST_LINE, 1);
        var thirdElement = new Anchor(FIRST_LINE, 2);

        return Stream.of(
            new TieCase("a tie between two notes at one pitch holds",
                new String[] {TIED + " " + TIED}, List.of(firstNote), List.of(), List.of(firstNote)),

            new TieCase("a tie on a rest is reported as damaged and not added",
                new String[] {"R " + TIED}, List.of(firstNote),
                List.of(removed(Strings.REPORT_REASON_DAMAGED_WRONG_ELEMENT, FIRST_LINE, FIRST_LINE)),
                List.of()),

            new TieCase("a tie to a note at another pitch is removed as not held",
                new String[] {TIED + " " + OTHER}, List.of(firstNote),
                List.of(removed(Strings.REPORT_REASON_TIE_NOT_HELD, FIRST_LINE, FIRST_LINE)),
                List.of()),

            new TieCase("a tie with no note to end on is removed as having no following note",
                new String[] {TIED + " R"}, List.of(firstNote),
                List.of(removed(Strings.REPORT_REASON_TIE_NO_FOLLOWING_NOTE, FIRST_LINE, FIRST_LINE)),
                List.of()),

            new TieCase("a tie across a line break to another pitch is removed, naming both lines",
                new String[] {TIED, OTHER}, List.of(firstNote),
                List.of(removed(Strings.REPORT_REASON_TIE_NOT_HELD, FIRST_LINE, SECOND_LINE)),
                List.of()),

            new TieCase("a tie across a line break to a note at its pitch holds",
                new String[] {TIED, TIED}, List.of(firstNote), List.of(), List.of(firstNote)),

            new TieCase("damaged and broken ties are reported in the order the reader found them",
                new String[] {TIED + " " + OTHER + " R"}, List.of(firstNote, thirdElement),
                List.of(
                    removed(Strings.REPORT_REASON_TIE_NOT_HELD, FIRST_LINE, FIRST_LINE),
                    removed(Strings.REPORT_REASON_DAMAGED_WRONG_ELEMENT, FIRST_LINE, FIRST_LINE)),
                List.of()),

            new TieCase("a tie that holds stays beside one that is removed",
                new String[] {TIED + " " + TIED + " " + OTHER}, List.of(firstNote, secondNote),
                List.of(removed(Strings.REPORT_REASON_TIE_NOT_HELD, FIRST_LINE, FIRST_LINE)),
                List.of(firstNote)));
    }

    private static ReportedChange removed(String reasonKey, int firstLine, int lastLine) {
        return new ReportedChange(
            ChangeHeading.TIES_REMOVED, Strings.get(reasonKey), new LineRange(firstLine, lastLine));
    }
}
