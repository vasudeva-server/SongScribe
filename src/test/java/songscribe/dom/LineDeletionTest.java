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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.StaffElementFactory.crotchet;
import static songscribe.dom.StaffElementFactory.keyChange;
import static songscribe.dom.StaffElementFactory.singleBarline;

/**
 * What {@link Line#deleteRange} covers, and the order {@link Line#deleteRanges} takes several
 * ranges in.
 *
 * <p>The deletion an edit performs, as against the {@link Line#removeRange} primitive beneath it.
 * It is handed the range {@link StaffElementRun#effectiveRange} resolved, so what these cases
 * check is that naming either half of a pair covers both. Every fixture here is built around the
 * one pair a key edit creates and destroys — a mid-line key change and the barline it stands
 * behind — because that is the pair whose two halves mean nothing apart.
 */
class LineDeletionTest extends UnitTest {

    /** The key the fixture line runs in, and the one both its key changes restate. */
    private static final Key LINE_KEY = Key.NO_ACCIDENTALS;

    private static final int FIRST_BARLINE_INDEX = 1;
    private static final int FIRST_KEY_CHANGE_INDEX = 2;
    private static final int MIDDLE_NOTE_INDEX = 3;
    private static final int SECOND_BARLINE_INDEX = 4;
    private static final int SECOND_KEY_CHANGE_INDEX = 5;
    private static final int LAST_NOTE_INDEX = 6;

    /**
     * One deletion, and what the fixture line is left holding.
     *
     * @param description the case, which doubles as the parameterized display name
     * @param begin       the first index the caller names, before widening
     * @param end         the last index the caller names, before widening
     * @param surviving   the fixture indices of the elements that must remain, in order
     */
    private record DeletionCase(String description, int begin, int end, List<Integer> surviving) {

        @Override
        public String toString() {
            return description;
        }
    }

    /**
     * A line in {@link #LINE_KEY} holding {@code note, barline, key change, note, barline,
     * key change, note} — two complete pairs with a note either side of each.
     *
     * @return the elements in the order they stand on the line, so a case can name survivors by
     *     the index they were built at
     */
    private static List<StaffElement> fixtureElements() {
        return List.of(
            crotchet(), singleBarline(), keyChange(LINE_KEY),
            crotchet(), singleBarline(), keyChange(LINE_KEY),
            crotchet());
    }

    private static Line lineHolding(List<StaffElement> elements) {
        var line = detachedLine();

        line.setKey(LINE_KEY);

        for (var element : elements) {
            line.addElement(element);
        }

        return line;
    }

    /** The elements standing on {@code line}, in order, for an identity comparison. */
    private static List<StaffElement> elementsOf(Line line) {
        var standing = new ArrayList<StaffElement>();

        for (var index = 0; index < line.effectiveElementCount(); index++) {
            standing.add(line.getElement(index));
        }

        return standing;
    }

    private static List<StaffElement> at(List<StaffElement> elements, List<Integer> indices) {
        return indices.stream().map(elements::get).toList();
    }

    static Stream<DeletionCase> deletionCases() {
        return Stream.of(
            new DeletionCase(
                "naming the key change takes the barline it stands behind",
                FIRST_KEY_CHANGE_INDEX, FIRST_KEY_CHANGE_INDEX,
                List.of(0, MIDDLE_NOTE_INDEX, SECOND_BARLINE_INDEX, SECOND_KEY_CHANGE_INDEX,
                    LAST_NOTE_INDEX)),
            new DeletionCase(
                "naming the barline takes the key change standing behind it",
                FIRST_BARLINE_INDEX, FIRST_BARLINE_INDEX,
                List.of(0, MIDDLE_NOTE_INDEX, SECOND_BARLINE_INDEX, SECOND_KEY_CHANGE_INDEX,
                    LAST_NOTE_INDEX)),
            new DeletionCase(
                "a range naming a note on its own reaches no pair and takes only that note",
                MIDDLE_NOTE_INDEX, MIDDLE_NOTE_INDEX,
                List.of(0, FIRST_BARLINE_INDEX, FIRST_KEY_CHANGE_INDEX, SECOND_BARLINE_INDEX,
                    SECOND_KEY_CHANGE_INDEX, LAST_NOTE_INDEX)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("deletionCases")
    void testDeleteRangeCoversTheWidenedRangeAndNothingElse(DeletionCase testCase) {
        var elements = fixtureElements();
        var line = lineHolding(elements);

        line.deleteRange(line.effectiveRange(testCase.begin(), testCase.end()));

        assertThat(elementsOf(line))
            .containsExactlyElementsOf(at(elements, testCase.surviving()));
    }

    @Test
    void testDeleteRangesRemovesTheElementsEveryRangeNamed() {
        var elements = fixtureElements();
        var line = lineHolding(elements);

        line.deleteRanges(List.of(
            new StaffElementRun.EffectiveRange(FIRST_BARLINE_INDEX, FIRST_KEY_CHANGE_INDEX),
            new StaffElementRun.EffectiveRange(SECOND_BARLINE_INDEX, SECOND_KEY_CHANGE_INDEX)));

        assertThat(elementsOf(line))
            .as("taken in ascending order the first removal would shift the second range onto the "
                + "wrong elements, so both pairs surviving intact is the ordering's whole claim")
            .containsExactlyElementsOf(at(elements, List.of(0, MIDDLE_NOTE_INDEX, LAST_NOTE_INDEX)));
    }
}
