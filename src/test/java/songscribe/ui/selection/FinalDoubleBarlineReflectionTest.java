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

package songscribe.ui.selection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.MainFrameMockTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.ui.action.Actions;
import songscribe.ui.action.ElementTypeAction;
import songscribe.ui.action.UIAction;

/**
 * Reflection of the song's auto-maintained terminal onto the <em>real</em>
 * {@code Actions.FINAL_DOUBLE_BARLINE_ACTION}, as opposed to the standalone action instances
 * {@link ReflectionIntegrationTest} builds.
 * <p>
 * The distinction is the whole point: the final double barline is now a full member of
 * {@code Actions.BARLINE_ACTIONS} and therefore of {@code Actions.NON_DURATION_ACTION_GROUP}, so
 * checking it does not merely light a menu item — it takes the user's armed barline pen out of the
 * group. That side effect exists only when the actions are the ones the group actually holds, and
 * it is what the edit-entry ordering in {@code ScoreViewController.modeDidChange} has to undo.
 */
class FinalDoubleBarlineReflectionTest extends MainFrameMockTest {

    /** The pen the user is assumed to have armed before clicking the terminal. */
    private static final ElementType USER_PEN_TYPE = ElementType.SINGLE_BARLINE;

    private Song song;
    private ElementTypeAction userPen;

    @BeforeEach
    void setUp() {
        song = new Song();
        userPen = ReflectionTestHelper.barlineActionFor(USER_PEN_TYPE);
    }

    @Test
    void testSelectingTheTerminalChecksTheFinalDoubleBarlineEntry() {
        armBarlinePen();

        reflectTerminal();

        assertThat(Actions.FINAL_DOUBLE_BARLINE_ACTION.isSelected())
            .as("the entry standing for the terminal's type is checked")
            .isTrue();
    }

    /**
     * The consequence of group membership. A reflected check that left the pen armed would
     * leave two barline entries lit at once, and the group's notion of what is selected would
     * still name the pen — so nothing downstream, including the preview element, would notice
     * the terminal at all.
     */
    @Test
    void testSelectingTheTerminalUnchecksTheArmedBarlinePen() {
        armBarlinePen();

        reflectTerminal();

        assertThat(userPen.isSelected())
            .as("the previously armed pen is unchecked")
            .isFalse();
        assertThat(Actions.NON_DURATION_ACTION_GROUP.getSelected())
            .as("the group's one checked entry moved to the final double barline")
            .isSameAs(Actions.FINAL_DOUBLE_BARLINE_ACTION);
    }

    /** Arms {@link #userPen} the way clicking its toolbar button would. */
    private void armBarlinePen() {
        Actions.NON_DURATION_ACTION_GROUP.setSelected(userPen, true);

        assertThat(userPen.isSelected())
            .as("pre-condition: a barline pen is armed before the terminal is selected")
            .isTrue();
    }

    /**
     * Selects the terminal of the song's only line and runs a reflection pass over the real
     * barline entries.
     */
    private void reflectTerminal() {
        var line = lineEndingInTerminal();
        var coordinator = ReflectionTestHelper.createCoordinatorForLine(
            line, List.<UIAction.Reflectable>of(Actions.BARLINE_ACTIONS));

        ReflectionTestHelper.selectNote(coordinator, line.elementCount() - 1);
        coordinator.getActionReflector().triggerReflection();
    }

    /**
     * The song's only line, one note followed by the auto-maintained terminal. The terminal
     * exists only as the last element of a song's last line, so a detached line cannot stand in.
     */
    private Line lineEndingInTerminal() {
        var line = song.getLine(0);
        song.withoutMutationTracking(() -> line.addElement(ElementType.CROTCHET.newInstance()));

        return line;
    }
}
