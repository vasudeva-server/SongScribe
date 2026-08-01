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
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.hit.HitTarget;
import songscribe.ui.component.ScoreView;

/**
 * Unit tests for the index-range shape of the coordinator's one selection: how it is set,
 * extended, cleared and revalidated, and what the range-shaped queries answer when the
 * selection is not a range at all.
 * <p>
 * The range's own arithmetic — what it covers, what it can be beamed or tied into — is pure
 * and lives in {@link RangeQueriesTest}.
 */
class SelectionCoordinatorRangeTest extends UnitTest {

    private static final int LINE_0 = 0;

    /** Index of the terminal barline appended after the two notes of a two-note song line. */
    private static final int TERMINAL_ELEMENT_INDEX = 2;

    /**
     * A coordinator with {@code line} registered at index 0 and activated, so a range can be
     * selected on it.
     */
    private static SelectionCoordinator coordinatorOn(Line line) {
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));
        coordinator.registerLine(LINE_0, line);
        coordinator.activateLine(LINE_0);
        return coordinator;
    }

    /** A detached line holding {@code count} crotchets. */
    private static Line crotchetLine(int count) {
        var line = detachedLine();

        for (var i = 0; i < count; i++) {
            line.addElement(ElementType.CROTCHET.newInstance());
        }

        return line;
    }

    // -------------------------------------------------------------------------
    // Nothing selected
    // -------------------------------------------------------------------------

    /**
     * The range-shaped queries all answer "nothing" rather than throwing when the selection is
     * absent. Since an empty range can no longer be constructed, this is the only shape those
     * answers now have.
     */
    @Test
    void testRangeQueriesReportNothingWhenNothingIsSelected() {
        var coordinator = coordinatorOn(crotchetLine(2));

        assertThat(coordinator.getRange()).as("getRange").isNull();
        assertThat(coordinator.getSelection()).as("getSelection").isNull();
        assertThat(coordinator.getSelectionSize()).as("getSelectionSize").isZero();
        assertThat(coordinator.getSingleSelectedElement()).as("getSingleSelectedElement").isNull();
        assertThat(coordinator.isElementSelected(0, LINE_0)).as("isElementSelected(0)").isFalse();
    }

    /**
     * With nothing selected, nothing is selected — including index 0. The fixture leads with a
     * breath mark because that is the input that exposes a missing guard: a range extended onto
     * a trailing breath mark would report it as selected if the empty case were not answered
     * first. Insertion forbids a breath mark at index 0, so this line is not reachable through
     * the UI; it is here to keep the query correct on its own terms rather than by way of an
     * invariant enforced in another subsystem.
     */
    @Test
    void testIsElementSelectedIsFalseForALeadingBreathMarkWithNoSelection() {
        var coordinator = coordinatorOn(lineWith(ElementType.BREATH_MARK, ElementType.CROTCHET));

        assertThat(coordinator.isElementSelected(0, LINE_0)).isFalse();
    }

    @Test
    void testIsElementSelectedIsFalseForANegativeIndex() {
        var coordinator = coordinatorOn(crotchetLine(2));
        coordinator.selectRange(0, 1);

        assertThat(coordinator.isElementSelected(-1, LINE_0)).isFalse();
    }

    // -------------------------------------------------------------------------
    // selectSingleElement / selectRange
    // -------------------------------------------------------------------------

    @Test
    void testSelectSingleElementCollapsesTheSelectionOntoOneElementAndAnchorsIt() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);

        assertThat(coordinator.selectSingleElement(LINE_0, 1)).isTrue();

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.line()).isSameAs(line);
                assertThat(range.begin()).isEqualTo(1);
                assertThat(range.end()).isEqualTo(1);
                assertThat(range.anchor()).isEqualTo(1);
            });
    }

    @Test
    void testSelectSingleElementReportsFailureWhenNoLineIsRegisteredAtThatIndex() {
        var coordinator = new SelectionCoordinator(mock(ScoreView.class));

        assertThat(coordinator.selectSingleElement(LINE_0, 0)).isFalse();
        assertThat(coordinator.getRange()).isNull();
    }

    @Test
    void testSelectRangeAnchorsAtBeginByDefault() {
        var coordinator = coordinatorOn(crotchetLine(3));

        coordinator.selectRange(1, 2);

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> assertThat(range.anchor()).isEqualTo(1));
    }

    @Test
    void testSelectRangeWithABeginOfMinusOneClearsTheSelection() {
        var coordinator = coordinatorOn(crotchetLine(2));
        coordinator.selectRange(0, 1);

        coordinator.selectRange(-1, -1, -1);

        assertThat(coordinator.getRange()).isNull();
        assertThat(coordinator.getActiveLineIndex())
            .as("the line stays active — only what was selected on it went away")
            .isEqualTo(LINE_0);
    }

    // -------------------------------------------------------------------------
    // extendSelectionTo
    // -------------------------------------------------------------------------

    @Test
    void testExtendSelectionToWithAnchorBeforeIndexSetsBeginToAnchorEndToIndex() {
        var coordinator = coordinatorOn(crotchetLine(3));
        coordinator.selectSingleElement(LINE_0, 0);

        coordinator.extendSelectionTo(2);

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).isEqualTo(0);
                assertThat(range.end()).isEqualTo(2);
                assertThat(range.anchor()).as("the anchor stays put").isEqualTo(0);
            });
    }

    @Test
    void testExtendSelectionToWithAnchorAfterIndexSetsBeginToIndexEndToAnchor() {
        // Reversed drag: anchor at index 2, extending back to index 0.
        var coordinator = coordinatorOn(crotchetLine(3));
        coordinator.selectSingleElement(LINE_0, 2);

        coordinator.extendSelectionTo(0);

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).isEqualTo(0);
                assertThat(range.end()).isEqualTo(2);
                assertThat(range.anchor()).isEqualTo(2);
            });
    }

    /**
     * A target selection carries no anchor, so there is nothing to extend from. This is the
     * case the old per-line state expressed as "anchor is -1".
     */
    @Test
    void testExtendSelectionToIsANoOpWhenTheSelectionIsATarget() {
        var coordinator = coordinatorOn(crotchetLine(2));
        coordinator.select(new HitTarget.StaffLine());

        coordinator.extendSelectionTo(1);

        assertThat(coordinator.getRange()).isNull();
        assertThat(coordinator.isLineSelected())
            .as("the target survives an extension it cannot answer")
            .isTrue();
    }

    @Test
    void testExtendSelectionToIsANoOpWhenNothingIsSelected() {
        var coordinator = coordinatorOn(crotchetLine(2));

        coordinator.extendSelectionTo(1);

        assertThat(coordinator.getRange()).isNull();
    }

    // -------------------------------------------------------------------------
    // clearSelection / clearActiveSelection
    // -------------------------------------------------------------------------

    @Test
    void testClearSelectionDropsTheRangeAndDeactivatesTheLine() {
        var coordinator = coordinatorOn(crotchetLine(2));
        coordinator.selectRange(0, 1);

        coordinator.clearSelection();

        assertThat(coordinator.getRange()).isNull();
        assertThat(coordinator.getActiveLineIndex()).isEqualTo(-1);
    }

    @Test
    void testClearActiveSelectionDropsTheRangeButKeepsTheLineActive() {
        var coordinator = coordinatorOn(crotchetLine(2));
        coordinator.selectRange(0, 1);

        coordinator.clearActiveSelection();

        assertThat(coordinator.getRange()).isNull();
        assertThat(coordinator.getActiveLineIndex()).isEqualTo(LINE_0);
    }

    // -------------------------------------------------------------------------
    // The two shapes exclude each other
    // -------------------------------------------------------------------------

    /**
     * The point of the unification: neither code path says "clear the other one". One field
     * holds one selection, so setting either shape is the whole of dropping the other.
     */
    @Test
    void testARangeAndATargetCannotBothBeSelected() {
        var coordinator = coordinatorOn(crotchetLine(2));

        coordinator.selectRange(0, 1);
        assertThat(coordinator.getRange()).as("range set").isNotNull();
        assertThat(coordinator.getSelectedTarget()).as("no target while a range is set").isNull();

        coordinator.select(new HitTarget.StaffLine());
        assertThat(coordinator.getSelectedTarget()).as("target set").isNotNull();
        assertThat(coordinator.getRange()).as("the range is gone").isNull();

        coordinator.selectRange(0, 1);
        assertThat(coordinator.getRange()).as("range set again").isNotNull();
        assertThat(coordinator.getSelectedTarget()).as("the target is gone").isNull();
    }

    // -------------------------------------------------------------------------
    // revalidateElementSelection
    // -------------------------------------------------------------------------

    @Test
    void testRevalidateElementSelectionIsANoOpWhenNothingIsSelected() {
        var coordinator = coordinatorOn(crotchetLine(2));

        assertThat(coordinator.revalidateElementSelection()).isFalse();
    }

    @Test
    void testRevalidateElementSelectionIsANoOpForATargetSelection() {
        var coordinator = coordinatorOn(crotchetLine(2));
        coordinator.select(new HitTarget.StaffLine());

        assertThat(coordinator.revalidateElementSelection()).isFalse();
        assertThat(coordinator.isLineSelected()).isTrue();
    }

    @Test
    void testRevalidateElementSelectionKeepsARangeStillWithinTheLine() {
        var coordinator = coordinatorOn(crotchetLine(2));
        coordinator.selectRange(0, 1);

        assertThat(coordinator.revalidateElementSelection()).isFalse();
        assertThat(coordinator.getRange()).isNotNull();
    }

    @Test
    void testRevalidateElementSelectionClearsARangeRunningPastTheEndOfTheLine() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 1);

        // Simulates undoing an insertion: the element the range reached is gone.
        line.removeElement(1);

        assertThat(coordinator.revalidateElementSelection()).isTrue();
        assertThat(coordinator.getRange()).isNull();
        assertThat(coordinator.getActiveLineIndex())
            .as("the line stays active — only the range became unusable")
            .isEqualTo(LINE_0);
    }

    /**
     * The crash this guards against: every range query walks the selected span, so a range
     * left pointing past the end of a shrunk line throws before anything else can notice it is
     * stale.
     */
    @Test
    void testRevalidatedSelectionMakesTheTupletQuerySafeOnAShrunkLine() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 1);

        line.removeElement(1);
        coordinator.revalidateElementSelection();

        var range = coordinator.getRange();

        assertThat(range).as("the stale range was dropped, so there is nothing to query").isNull();
    }

    /**
     * A range reaching the song's auto-maintained terminal is usable — the terminal is a real,
     * indexable element — so an unrelated edit must not clear it.
     */
    @Test
    void testRevalidateElementSelectionKeepsARangeReachingTheSongOwnedTerminal() {
        var song = new Song();
        var line = song.getLine(LINE_0);

        song.withoutMutationTracking(() -> {
            line.addElement(0, ElementType.CROTCHET.newInstance());
            line.addElement(1, ElementType.CROTCHET.newInstance());
        });

        assertThat(line.effectiveElementCount())
            .as("the fixture must make the two counts differ, or this test proves nothing")
            .isLessThan(line.elementCount());

        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, TERMINAL_ELEMENT_INDEX);

        assertThat(coordinator.revalidateElementSelection()).isFalse();
        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> assertThat(range.end()).isEqualTo(TERMINAL_ELEMENT_INDEX));
    }

    // -------------------------------------------------------------------------
    // selectAll
    // -------------------------------------------------------------------------

    @Test
    void testSelectAllExcludesTheAutoMaintainedFinalBarlineOnTheLastLine() {
        var song = new Song();
        var line = song.getLine(LINE_0);

        song.withoutMutationTracking(() -> {
            line.addElement(0, ElementType.CROTCHET.newInstance());
            line.addElement(1, ElementType.CROTCHET.newInstance());
        });

        // Line now holds: [quarter, quarter, FINAL_DOUBLE_BARLINE]
        assertThat(line.elementCount()).isEqualTo(3);
        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

        var coordinator = coordinatorOn(line);
        coordinator.selectAll();

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).isEqualTo(0);
                assertThat(range.end()).isEqualTo(1);
            });
    }

    @Test
    void testSelectAllExcludesTheAutoMaintainedRightRepeatTerminalOnTheLastLine() {
        var song = new Song();
        var line = song.getLine(LINE_0);
        song.replaceTerminal(ElementType.REPEAT_RIGHT);

        song.withoutMutationTracking(() -> {
            line.addElement(0, ElementType.CROTCHET.newInstance());
            line.addElement(1, ElementType.CROTCHET.newInstance());
        });

        assertThat(line.elementCount()).isEqualTo(3);
        assertThat(line.getElement(2).getType()).isEqualTo(ElementType.REPEAT_RIGHT);

        var coordinator = coordinatorOn(line);
        coordinator.selectAll();

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).isEqualTo(0);
                assertThat(range.end()).isEqualTo(1);
            });
    }

    @Test
    void testSelectAllOnALineWithOnlyTheFinalBarlineSelectsNothing() {
        var song = new Song();
        var line = song.getLine(LINE_0);

        // Default song seeds the first (and only) line with just the final barline.
        assertThat(line.elementCount()).isEqualTo(1);
        assertThat(line.getElement(0).getType()).isEqualTo(ElementType.FINAL_DOUBLE_BARLINE);

        var coordinator = coordinatorOn(line);
        coordinator.selectAll();

        assertThat(coordinator.getRange()).isNull();
    }

    /**
     * An empty line has nothing to swap a whole-line selection for, so that selection stands.
     */
    @Test
    void testSelectAllLeavesALineSelectionAloneOnAnEmptyLine() {
        var song = new Song();
        var coordinator = coordinatorOn(song.getLine(LINE_0));
        coordinator.select(new HitTarget.StaffLine());

        coordinator.selectAll();

        assertThat(coordinator.isLineSelected()).isTrue();
    }

    @Test
    void testSelectAllSwapsAWholeLineSelectionForItsElements() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);
        coordinator.select(new HitTarget.StaffLine());

        coordinator.selectAll();

        assertThat(coordinator.isLineSelected())
            .as("the line selection is gone, because both shapes are one field")
            .isFalse();
        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).isEqualTo(0);
                assertThat(range.end()).isEqualTo(1);
            });
    }

    @Test
    void testSelectAllSelectsEveryElement() {
        var coordinator = coordinatorOn(crotchetLine(2));

        coordinator.selectAll();

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).isEqualTo(0);
                assertThat(range.end()).isEqualTo(1);
                assertThat(range.anchor()).isEqualTo(0);
            });
    }

    @Test
    void testSelectAllOnALineWithExactlyOneElementSelectsThatElement() {
        // The boundary between "nothing to select" and "something to select": one element
        // collapses the range to a single index rather than tripping the empty-line guard.
        var coordinator = coordinatorOn(crotchetLine(1));

        coordinator.selectAll();

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).isEqualTo(0);
                assertThat(range.end()).isEqualTo(0);
            });
    }

    @Test
    void testSelectAllOnANonLastLineIncludesAllElements() {
        var song = new Song();
        var firstLine = song.getLine(LINE_0);
        song.addLine(1, new Line(song));

        // firstLine is no longer the last line — its final barline has been transferred away.
        song.withoutMutationTracking(() -> {
            firstLine.addElement(0, ElementType.CROTCHET.newInstance());
            firstLine.addElement(1, ElementType.CROTCHET.newInstance());
        });

        var coordinator = coordinatorOn(firstLine);
        coordinator.selectAll();

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).isEqualTo(0);
                assertThat(range.end()).isEqualTo(firstLine.elementCount() - 1);
            });
    }
}
