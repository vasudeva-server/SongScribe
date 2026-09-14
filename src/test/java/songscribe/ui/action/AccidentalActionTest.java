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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;
import songscribe.dom.Accidental;
import songscribe.dom.ChainedAccidental.ChainType;
import songscribe.dom.Key;
import songscribe.dom.Line;
import songscribe.dom.NoteElement;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link AccidentalAction#matchesElement} and {@link AccidentalAction#canToggle} against the
 * table in {@link AccidentalAction}'s class Javadoc, for every {@link ChainType} the note the
 * action reads can be in, crossed with every {@link Accidental} the action can be.
 */
class AccidentalActionTest extends UnitTest {

    private static final int F = 3;
    private static final int OTHER = 4;

    /** The key's alteration for every note this fixture builds — {@code k} in the class Javadoc. */
    private static final Accidental KEY_ALTERATION = Accidental.NATURAL;

    /** One note's chain state, ready to ask both {@code matchesElement} and {@code canToggle} about. */
    private record StateFixture(ChainType type, Line line, NoteElement note, int index, Accidental sounding) {
        @Override
        public String toString() {
            return type.toString();
        }
    }

    private record ActionCase(StateFixture state, Accidental actionAccidental) {
        @Override
        public String toString() {
            return state.type() + " x " + actionAccidental;
        }
    }

    /**
     * One line carrying a note in each of the five chain states, built fresh so no test mutates a
     * shared line another test then reads.
     */
    private static List<StateFixture> states() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + " " + F + "f " + OTHER + " " + F + " " + F + " " + F + "n");
        var line = song.getLine(0);

        return List.of(
            new StateFixture(ChainType.IN_KEY, line, (NoteElement) line.getElement(0), 0, Accidental.NATURAL),
            new StateFixture(ChainType.ANCHOR, line, (NoteElement) line.getElement(1), 1, Accidental.FLAT),
            new StateFixture(ChainType.REPETITION, line, (NoteElement) line.getElement(3), 3, Accidental.FLAT),
            new StateFixture(ChainType.IMPLICIT, line, (NoteElement) line.getElement(4), 4, Accidental.FLAT),
            new StateFixture(ChainType.CANCEL, line, (NoteElement) line.getElement(5), 5, Accidental.NATURAL));
    }

    /** The action that writes {@code accidental}, built against a fresh mock environment. */
    private static AccidentalAction actionFor(Accidental accidental) {
        var frame = MockEnvHelper.setupMockEnv().frame();

        return switch (accidental) {
            case FLAT -> AccidentalAction.createFlatAction(frame);
            case DOUBLE_FLAT -> AccidentalAction.createDoubleFlatAction(frame);
            case NATURAL -> AccidentalAction.createNaturalAction(frame);
            case SHARP -> AccidentalAction.createSharpAction(frame);
            case DOUBLE_SHARP -> AccidentalAction.createDoubleSharpAction(frame);
        };
    }

    static Stream<ActionCase> cases() {
        return states().stream()
            .flatMap(state -> Stream.of(Accidental.values()).map(accidental -> new ActionCase(state, accidental)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testMatchesElementMatchesTheTable(ActionCase testCase) {
        var action = actionFor(testCase.actionAccidental());
        var state = testCase.state();

        assertThat(action.matchesElement(state.note())).isEqualTo(expectedMatches(state, testCase.actionAccidental()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void testCanToggleMatchesTheTable(ActionCase testCase) {
        var action = actionFor(testCase.actionAccidental());
        var state = testCase.state();

        assertThat(action.canToggle(state.line(), state.index()))
            .isEqualTo(expectedCanToggle(state, testCase.actionAccidental()));
    }

    /** {@code false} for {@link ChainType#IN_KEY}; {@code a == s} otherwise. */
    private static boolean expectedMatches(StateFixture state, Accidental actionAccidental) {
        return (state.type() != ChainType.IN_KEY) && (actionAccidental == state.sounding());
    }

    /** {@code a != k} for {@link ChainType#IN_KEY} and {@link ChainType#ANCHOR}; {@code a != s} otherwise. */
    private static boolean expectedCanToggle(StateFixture state, Accidental actionAccidental) {
        return switch (state.type()) {
            case IN_KEY, ANCHOR -> actionAccidental != KEY_ALTERATION;
            case REPETITION, IMPLICIT, CANCEL -> actionAccidental != state.sounding();
        };
    }
}
