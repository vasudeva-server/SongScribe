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

package songscribe.ui.action;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.ElementType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The position policy for a mid-line key change — where the insertion marker appears and where it
 * hides. See {@code docs/key-changes.md}.
 */
class KeyChangeActionTest extends UnitTest {

    /**
     * One position the policy is asked about.
     *
     * @param description what the row is a case of; doubles as the test's display name
     * @param types the line's elements, in order
     * @param index the insertion index the predicate is asked about
     * @param accepted whether a key change may be written there
     */
    private record PositionCase(String description, List<ElementType> types, int index, boolean accepted) {}

    private KeyChangeAction action;

    @BeforeEach
    void setUpAction() {
        action = KeyChangeAction.createAction(MockEnvHelper.setupMockEnv().frame());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reachCases")
    void testAcceptsOnlyAnIndexThatReachesANote(PositionCase testCase) {
        assertAcceptance(testCase);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("existingKeyChangeCases")
    void testRefusesEveryIndexTouchingAKeyChangeAlreadyOnTheLine(PositionCase testCase) {
        assertAcceptance(testCase);
    }

    private void assertAcceptance(PositionCase testCase) {
        var line = lineWith(testCase.types().toArray(ElementType[]::new));

        assertThat(action.acceptsInsertionIndex(line, testCase.index()))
            .isEqualTo(testCase.accepted());
    }

    static Stream<PositionCase> reachCases() {
        var threeNotes = List.of(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET);

        return Stream.of(
            new PositionCase(
                "index 0 is refused, since a key change is never a line's first element",
                threeNotes, 0, false),
            new PositionCase(
                "the last note's index is accepted, since the change governs that note",
                threeNotes, 2, true),
            new PositionCase(
                "the index past the last note is refused, since the change governs nothing",
                threeNotes, 3, false),
            new PositionCase(
                "the index before a trailing barline is refused, since no note follows",
                List.of(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.SINGLE_BARLINE),
                2, false),
            new PositionCase(
                "the index before a trailing rest is refused, since a key affects no rest",
                List.of(ElementType.CROTCHET, ElementType.CROTCHET, ElementType.CROTCHET_REST),
                2, false),
            new PositionCase(
                "a line holding no note offers no position at all",
                List.of(ElementType.SINGLE_BARLINE, ElementType.SINGLE_BARLINE), 1, false)
        );
    }

    static Stream<PositionCase> existingKeyChangeCases() {
        // A key change standing at index 2, behind the barline at index 1.
        var withKeyChange = List.of(
            ElementType.CROTCHET, ElementType.SINGLE_BARLINE, ElementType.KEY_CHANGE,
            ElementType.CROTCHET, ElementType.CROTCHET);

        return Stream.of(
            new PositionCase(
                "the index before the barline the key change sits behind is refused",
                withKeyChange, 1, false),
            // The third index of that neighborhood — the gap inside the pair, index 2 — is not
            // this predicate's to refuse. Line.canInsertElementAt refuses it for every operation
            // before any client predicate is asked, and LineTest asserts it there.
            new PositionCase(
                "the index immediately after the key change is refused",
                withKeyChange, 3, false),
            new PositionCase(
                "an index clear of the key change is accepted",
                withKeyChange, 4, true)
        );
    }
}
