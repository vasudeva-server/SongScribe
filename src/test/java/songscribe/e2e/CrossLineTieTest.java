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

import module java.desktop;

import org.assertj.swing.edt.GuiActionRunner;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import songscribe.dom.ScaleContext;
import songscribe.dom.StaffElement;
import songscribe.dom.Tie;
import songscribe.hit.HitTarget;
import songscribe.layout.HorizontalSpacingCalculator;
import songscribe.layout.LayoutResult;
import songscribe.layout.OpenSide;
import songscribe.ui.action.Actions;

/**
 * The whole-application gate for a tie whose two notes sit in different lines (issue #493,
 * phase 13 task 4). Every rule this exercises is already unit-tested in isolation; what only
 * the real Swing pipeline can show is that the wiring between those rules holds.
 *
 * <p>Deliberately four tests, one per piece of wiring that has no unit-level equivalent: real
 * clicks reaching the enable predicate, a real repaint laying out a half on each line, real
 * hit geometry resolving a click on either half to the same tie, and a line-header click
 * deleting a line the tie reaches into. What each of those does to the <em>model</em> — the
 * toggle, the insertion and deletion sweeps, the pitch-shift group — is covered by
 * {@code CrossLineTieToggleTest}, {@code TieInvalidationTest},
 * {@code CrossLineTieDeletionTest}, {@code CrossLineTieLineStructureTest} and
 * {@code NoteDragHandlerTest.CrossLineTieDrag}, which prove the same rules in milliseconds.
 * Restating them here buys nothing and costs an approval-gated run, so please do not.
 *
 * <p>The failure being guarded against is a <b>half-tie</b>: one line holding a tie the other
 * has dropped, which draws as an arc running off the edge to nothing. Every assertion here
 * therefore checks <i>both</i> lines, never just the one the action was aimed at.
 *
 * <p>The fixture is two lines of crotchets — a deliberately full first line of
 * {@link #ANCHOR_LINE_ELEMENT_COUNT}, since a tie reaches the next line precisely when there is
 * no room left on this one, and a short second line. Line 0's last note and line 1's first note
 * share a pitch, which is exactly the condition {@code RangeQueries.boundaryTieAt} requires
 * before the toggle is offered.
 */
class CrossLineTieTest extends E2ETest {

    /** The line holding the tie's anchor — the earlier of the two endpoints. */
    private static final int ANCHOR_LINE = 0;

    /** The line holding the tie's end — the later of the two endpoints. */
    private static final int END_LINE = 1;

    /**
     * Element count of {@link #ANCHOR_LINE} as the fixture loads it. The line is full — 28
     * crotchets is where natural spacing runs out at the fixture's line width — because that
     * is the only shape this feature occurs in: a tie reaches the next line precisely when
     * there is no room left on this one. A short line would still exercise the same code, but
     * it would not exercise it at the spacing the anchor is really laid out at.
     */
    private static final int ANCHOR_LINE_ELEMENT_COUNT = 28;

    /** Index of the anchor: the last element of {@link #ANCHOR_LINE} in the fixture. */
    private static final int ANCHOR_INDEX = ANCHOR_LINE_ELEMENT_COUNT - 1;

    /** Index of the end note: the first element of {@link #END_LINE}. */
    private static final int END_INDEX = 0;

    /**
     * Element count of {@link #END_LINE} as the fixture loads it: four crotchets plus the
     * song's terminal barline.
     */
    private static final int END_LINE_ELEMENT_COUNT = 5;

    /** Staff position of both endpoint notes (G4) in the fixture. */
    private static final int BOUNDARY_STAFF_POSITION = 2;

    /**
     * Half the header width, in staff spaces, is inside the region
     * {@code HitRegionBuilder.addStaffLine} registers for a whole-line selection — the only
     * place a click selects a line rather than what is drawn on it.
     */
    private static final double HEADER_CLICK_FRACTION = 0.5;

    /**
     * The two notes the tie joins, captured when it is created. Assertions compare against
     * these rather than against a fixed index, because which notes the tie joins is the thing
     * being asserted and an edit can move either of them to a different index without
     * changing that.
     */
    private @Nullable StaffElement tiedAnchor = null;
    private @Nullable StaffElement tiedEnd = null;

    @BeforeEach
    void loadCrossLineTieFixture() throws Exception {
        loadFixture("cross-line-tie");
        performLayout(ANCHOR_LINE);
        performLayout(END_LINE);
        deselectSelection();

        // A misread fixture would leave every test below asserting against the wrong
        // elements, so pin its shape rather than assume it.
        assertAll(
            () -> assertThat(song().lineCount()).as("fixture line count").isEqualTo(2),
            () -> assertThat(song().getLine(ANCHOR_LINE).elementCount())
                .as("fixture anchor line element count").isEqualTo(ANCHOR_LINE_ELEMENT_COUNT),
            () -> assertThat(song().getLine(END_LINE).elementCount())
                .as("fixture end line element count").isEqualTo(END_LINE_ELEMENT_COUNT),
            () -> assertThat(anchorNote().getStaffPosition())
                .as("fixture anchor staff position").isEqualTo(BOUNDARY_STAFF_POSITION),
            () -> assertThat(endNote().getStaffPosition())
                .as("fixture end staff position").isEqualTo(BOUNDARY_STAFF_POSITION)
        );
    }

    // -- Creating the tie --

    @Test
    void testToggleTieIsEnabledOnlyAtAMatchingLineBoundary() {
        enterSelectMode();
        clickAt(noteScreenPosition(ANCHOR_LINE, ANCHOR_INDEX));

        assertActionEnabled(true, "last note of the anchor line, pitch-matched across the break");

        clickAt(noteScreenPosition(ANCHOR_LINE, ANCHOR_INDEX - 1));

        assertActionEnabled(false, "a note that is not at a line boundary");

        clickAt(noteScreenPosition(END_LINE, END_INDEX));

        assertActionEnabled(true, "first note of the end line, pitch-matched across the break");
    }

    @Test
    void testBothLinesLayOutTheirOwnHalfOfTheArc() {
        createTieFrom(ANCHOR_LINE, ANCHOR_INDEX);

        var tie = crossLineTie();
        var anchorHalf = tieLayoutOn(ANCHOR_LINE, tie);
        var endHalf = tieLayoutOn(END_LINE, tie);

        // Geometry values are asserted by CrossLineTieLayoutTest; what this adds is that a
        // repaint driven by a real toggle produces a half on each line at all, and that each
        // half runs off the edge that faces the other line rather than the edge that faces away.
        assertAll(
            () -> assertThat(anchorHalf.openSide())
                .as("the anchor line's half runs off its right edge")
                .isEqualTo(OpenSide.END),
            () -> assertThat(endHalf.openSide())
                .as("the end line's half enters from its left edge")
                .isEqualTo(OpenSide.START)
        );
    }

    // -- Selecting the tie --

    @Test
    void testClickingEitherHalfSelectsTheSameTieAndBothHalvesReportSelected() {
        createTieFrom(ANCHOR_LINE, ANCHOR_INDEX);

        var tie = crossLineTie();

        enterSelectMode();
        clickAt(tieHalfClickPoint(ANCHOR_LINE, tie));
        assertBothHalvesReportSelected(tie, "clicked the anchor line's half");

        clickAt(tieHalfClickPoint(END_LINE, tie));
        assertBothHalvesReportSelected(tie, "clicked the end line's half");
    }

    // -- Deleting a whole line --

    @Test
    void testDeletingTheEndLineRemovesTheTieFromTheSurvivingLine() {
        createTieFrom(ANCHOR_LINE, ANCHOR_INDEX);

        enterSelectMode();
        clickAt(lineHeaderPoint(END_LINE));

        assertThat(scoreView().getSelectionCoordinator().getSelectedLine())
            .as("clicking the header selects the whole line").isEqualTo(END_LINE);

        robot.pressAndReleaseKey(KeyEvent.VK_DELETE);
        performLayout(ANCHOR_LINE);

        assertAll(
            () -> assertThat(song().lineCount()).as("the end line was deleted").isEqualTo(1),
            () -> assertThat(song().getLine(ANCHOR_LINE).findTies())
                .as("the surviving line must not keep half a tie").isEmpty()
        );

        undo();
        layOutBothLines();

        assertThat(song().lineCount()).as("undo restored the end line").isEqualTo(2);
        assertOneTieHeldByBothLines("undo of the deleted end line");
    }

    // -- Model helpers --

    private StaffElement anchorNote() {
        return song().getLine(ANCHOR_LINE).getElement(ANCHOR_INDEX);
    }

    private StaffElement endNote() {
        return song().getLine(END_LINE).getElement(END_INDEX);
    }

    /** The one tie the anchor line holds. Fails if the toggle never created it. */
    private Tie crossLineTie() {
        var ties = song().getLine(ANCHOR_LINE).findTies();
        assertThat(ties).as("the anchor line must hold exactly one tie").hasSize(1);

        return ties.getFirst();
    }

    // -- Interaction helpers --

    /**
     * Selects the note at {@code index} in {@code lineIndex} and toggles the tie on, going
     * through the toolbar button rather than calling the operation directly, so the enable
     * predicate and the action wiring are part of every test that needs a tie.
     */
    private void createTieFrom(int lineIndex, int index) {
        enterSelectMode();
        clickAt(noteScreenPosition(lineIndex, index));

        assertActionEnabled(true, "toggle tie before creating the tie");

        triggerAction(Actions.TOGGLE_TIE_ACTION);
        layOutBothLines();

        var tie = crossLineTie();
        tiedAnchor = tie.getAnchorElement();
        tiedEnd = tie.getEndElement();

        assertAll(
            () -> assertThat(tiedAnchor).as("the new tie's anchor is the anchor line's last note")
                .isSameAs(anchorNote()),
            () -> assertThat(tiedEnd).as("the new tie's end is the end line's first note")
                .isSameAs(endNote())
        );
    }

    private void undo() {
        clickMenuItem(Actions.UNDO_ACTION);
        layOutBothLines();
    }

    private void layOutBothLines() {
        performLayout(ANCHOR_LINE);

        if (song().lineCount() > END_LINE) {
            performLayout(END_LINE);
        }
    }

    // -- Coordinate helpers --

    /**
     * A point inside the region {@code HitRegionBuilder.addStaffLine} registers: the line's
     * header, to the left of the music. Clicking the staff among the notes selects the notes.
     */
    private Point lineHeaderPoint(int lineIndex) {
        var result = GuiActionRunner.execute(() -> {
            var lineComponent = scoreView().getLineComponent(lineIndex);
            assertThat(lineComponent).isNotNull();

            var headerRightEdgeSs =
                HorizontalSpacingCalculator.calculateHeaderRightEdgeSs(lineComponent.getLine());
            var locationOnScreen = lineComponent.getLocationOnScreen();

            return new Point(
                locationOnScreen.x + ScaleContext.ssToRoundedPx(headerRightEdgeSs * HEADER_CLICK_FRACTION),
                locationOnScreen.y + ScaleContext.ssToRoundedPx(lineComponent.getMiddleLineYSs())
            );
        });
        assertThat(result).isNotNull();

        return result;
    }

    /**
     * The center of one half's hit region. {@code HitRegionBuilder.tieBoundsSs} registers the
     * bounding box of the arc's control polygon, so the box center is always inside it — and,
     * for either half of a cross-line tie, lies over staff between the notehead and the open
     * edge rather than over a notehead that would win the hit test.
     */
    private Point tieHalfClickPoint(int lineIndex, Tie tie) {
        var tieLayout = tieLayoutOn(lineIndex, tie);

        var result = GuiActionRunner.execute(() -> {
            var lineComponent = scoreView().getLineComponent(lineIndex);
            assertThat(lineComponent).isNotNull();

            var centerXSs = midExtent(
                tieLayout.startXSs(), tieLayout.cp1XSs(), tieLayout.cp2XSs(), tieLayout.endXSs());
            var centerYSs = midExtent(
                tieLayout.startYSs(), tieLayout.cp1YSs(), tieLayout.cp2YSs(), tieLayout.endYSs());

            // Layout Y is measured from the staff midline; a click is measured from the top of
            // the component.
            var locationOnScreen = lineComponent.getLocationOnScreen();

            return new Point(
                locationOnScreen.x + ScaleContext.ssToRoundedPx(centerXSs),
                locationOnScreen.y + ScaleContext.ssToRoundedPx(centerYSs + lineComponent.getMiddleLineYSs())
            );
        });
        assertThat(result).isNotNull();

        return result;
    }

    /** The midpoint of the range the given values span. */
    private static double midExtent(double... values) {
        var min = values[0];
        var max = values[0];

        for (var value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        return (min + max) / 2;
    }

    private LayoutResult.TieLayout tieLayoutOn(int lineIndex, Tie tie) {
        var tieLayout = GuiActionRunner.execute(() -> {
            var lineComponent = scoreView().getLineComponent(lineIndex);
            assertThat(lineComponent).isNotNull();

            var layoutResult = lineComponent.getLayoutResult();
            assertThat(layoutResult).isNotNull();

            return layoutResult.getTieLayout(tie);
        });
        assertThat(tieLayout).as("line %d must lay out its half of the tie", lineIndex).isNotNull();

        return tieLayout;
    }

    // -- Assertion helpers --

    private void assertActionEnabled(boolean expected, String label) {
        var isEnabled = GuiActionRunner.execute(Actions.TOGGLE_TIE_ACTION::isEnabled);
        assertThat(isEnabled).as("%s: toggle tie enabled", label).isEqualTo(expected);
    }

    /**
     * The gate this whole class exists for: one {@link Tie} object, present in both lines'
     * span lists, with the same two endpoint elements. Anything less is a half-tie.
     */
    private void assertOneTieHeldByBothLines(String label) {
        var tiesOnAnchorLine = song().getLine(ANCHOR_LINE).findTies();
        var tiesOnEndLine = song().getLine(END_LINE).findTies();

        assertAll(
            () -> assertThat(tiesOnAnchorLine).as("%s: anchor line's spans", label).hasSize(1),
            () -> assertThat(tiesOnEndLine).as("%s: end line's spans", label).hasSize(1)
        );

        var tie = tiesOnAnchorLine.getFirst();

        assertAll(
            () -> assertThat(tie)
                .as("%s: both lines must hold the same Tie, not two copies", label)
                .isSameAs(tiesOnEndLine.getFirst()),
            () -> assertThat(tie.getAnchorElement())
                .as("%s: anchor element", label).isSameAs(tiedAnchor),
            () -> assertThat(tie.getEndElement())
                .as("%s: end element", label).isSameAs(tiedEnd)
        );
    }

    /**
     * Both halves draw selected. Each line repaints with its own line index, and
     * {@code TieRenderer} colors its half by asking {@code isSelected(target, thatIndex)}, so
     * asking for both indices is asking whether both halves would draw as selected.
     */
    private void assertBothHalvesReportSelected(Tie tie, String label) {
        var coordinator = scoreView().getSelectionCoordinator();
        var target = new HitTarget.Tie(tie);

        assertAll(
            () -> assertThat(coordinator.getSelectedTarget())
                .as("%s: the whole tie is the selection", label).isEqualTo(target),
            () -> assertThat(coordinator.isSelected(target, ANCHOR_LINE))
                .as("%s: the anchor line's half draws selected", label).isTrue(),
            () -> assertThat(coordinator.isSelected(target, END_LINE))
                .as("%s: the end line's half draws selected", label).isTrue()
        );
    }
}
