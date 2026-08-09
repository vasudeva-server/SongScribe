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

package songscribe.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.dom.ElementType;
import songscribe.ui.action.Actions;
import songscribe.ui.action.UIAction;

/**
 * E2E coverage for the barline popup panel in {@code BarlineToolbar} (issue #737).
 *
 * <p>What only the real Swing pipeline can show is that an action reached through the panel still
 * drives the same machinery it did as a top-level toolbar button: the popup has to open, the click
 * has to land on the button inside it, and the action has to fire before the popup dismisses
 * itself. Everything downstream of the action — the insertion itself, terminal replacement — is
 * unit-tested elsewhere; what is untested until here is that the panel wires up to any of it.
 *
 * <p>The panel's three entries split into two mechanisms. The double and single barlines arm the
 * insertion pen, so they land where the next click lands. The final double barline touches only
 * the song's auto-maintained terminal, routing through {@code Song.replaceTerminal}, and is
 * enabled only while that terminal is selected — hence its own test rather than a third case of
 * the insertion one.
 *
 * <p>The line comes from disk rather than from real clicks because building it up note-by-note
 * would exercise far more of the insertion pipeline than the panel wiring this test is after.
 * Only the barline work — the thing under test — goes through the real click pipeline.
 */
class BarlinePanelTest extends E2ETest {

    private static final int LINE_INDEX = 0;

    /** Notes in the {@code accidental-context} fixture, excluding the song terminal. */
    private static final int FIXTURE_ELEMENT_COUNT = 4;

    /** A barline appended past the last note lands after every fixture element. */
    private static final int APPENDED_BARLINE_INDEX = FIXTURE_ELEMENT_COUNT;
    private static final int ELEMENT_COUNT_AFTER_APPEND = FIXTURE_ELEMENT_COUNT + 1;

    /** Barlines are unpitched, so the staff position of the insertion click is immaterial. */
    private static final int APPEND_STAFF_POSITION = 0;

    /** {@code Actions.BARLINE_ACTIONS} is {double, single, final double}. */
    private static final int DOUBLE_BARLINE_ACTION_INDEX = 0;
    private static final int SINGLE_BARLINE_ACTION_INDEX = 1;

    /** {@code Actions.REPEAT_ACTIONS} is {left, right, left-right}. */
    private static final int RIGHT_REPEAT_ACTION_INDEX = 1;

    /**
     * Loads the fixture fresh for each test, since every test mutates the line.
     */
    @BeforeEach
    void loadLine() {
        resetSong();
        loadFixture("accidental-context");
        deselectSelection();
        performLayout(LINE_INDEX);
    }

    @Test
    void testDoubleBarlineFromThePanelIsInsertedAtTheClickPoint() {
        assertPanelBarlineIsInsertedAtTheClickPoint(
            Actions.BARLINE_ACTIONS[DOUBLE_BARLINE_ACTION_INDEX], ElementType.DOUBLE_BARLINE);
    }

    @Test
    void testSingleBarlineFromThePanelIsInsertedAtTheClickPoint() {
        assertPanelBarlineIsInsertedAtTheClickPoint(
            Actions.BARLINE_ACTIONS[SINGLE_BARLINE_ACTION_INDEX], ElementType.SINGLE_BARLINE);
    }

    /**
     * Arms {@code action} from the barline panel, clicks past the last note, and asserts that a
     * barline of {@code expectedType} landed there.
     */
    private void assertPanelBarlineIsInsertedAtTheClickPoint(UIAction action, ElementType expectedType) {
        // A fixture that failed to load would otherwise let the count assertion below pass by
        // accident on whatever line happened to be there.
        assertThat(song().getLine(LINE_INDEX).effectiveElementCount())
            .as("fixture loaded").isEqualTo(FIXTURE_ELEMENT_COUNT);

        clickToolbarButton(action);
        clickAt(insertionPoint(LINE_INDEX, APPEND_STAFF_POSITION));
        performLayout(LINE_INDEX);

        var modified = song().getLine(LINE_INDEX);

        assertAll(
            () -> assertThat(modified.effectiveElementCount())
                .as("the barline added one element").isEqualTo(ELEMENT_COUNT_AFTER_APPEND),
            () -> assertThat(modified.getElement(APPENDED_BARLINE_INDEX).getType())
                .as("the barline landed at the click point").isEqualTo(expectedType)
        );
    }

    /**
     * The final double barline retypes the terminal rather than inserting anything, so the fixture
     * terminal — already a final double — is first driven to the only other valid terminal type,
     * through the same terminal-retyping path, so that the retype under test is observable.
     */
    @Test
    void testFinalDoubleBarlineFromThePanelRetypesTheSelectedTerminal() {
        enterSelectMode();
        selectTerminal();
        clickToolbarButton(Actions.REPEAT_ACTIONS[RIGHT_REPEAT_ACTION_INDEX]);
        performLayout(LINE_INDEX);

        assertThat(song().currentTerminalType())
            .as("terminal starts as something the final double barline would change")
            .isEqualTo(ElementType.REPEAT_RIGHT);

        // Reselect: the terminal element was replaced, and the panel entry is enabled only while
        // the terminal is the selection.
        selectTerminal();
        clickToolbarButton(Actions.FINAL_DOUBLE_BARLINE_ACTION);
        performLayout(LINE_INDEX);

        var line = song().getLine(LINE_INDEX);

        assertAll(
            () -> assertThat(song().currentTerminalType())
                .as("the terminal was retyped").isEqualTo(ElementType.FINAL_DOUBLE_BARLINE),
            // Retyping must not insert: Song.replaceTerminal swaps the element in place.
            () -> assertThat(line.effectiveElementCount())
                .as("nothing was inserted").isEqualTo(FIXTURE_ELEMENT_COUNT)
        );
    }

    /**
     * Clicks the song's auto-maintained terminal, the last element of the line.
     */
    private void selectTerminal() {
        clickAt(noteScreenPosition(LINE_INDEX, song().getLine(LINE_INDEX).elementCount() - 1));
    }
}
