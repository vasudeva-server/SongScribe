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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import javax.swing.JOptionPane;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.hit.HitTarget;
import songscribe.message.mutation.ElementDeletion;
import songscribe.message.mutation.ElementInsertion;
import songscribe.message.mutation.ElementRangeDeletion;
import songscribe.message.mutation.LineDeletion;
import songscribe.message.mutation.LineInsertion;
import songscribe.message.mutation.Mutation;
import songscribe.message.notification.SongDidChangeNotification;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;
import songscribe.ui.component.ScoreView;
import songscribe.ui.component.score.PitchShifter;
import songscribe.undo.UndoTestSupport;

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

    /**
     * A notification carrying {@code mutations} in the order given. The song attached to it is
     * never consulted by {@link SelectionCoordinator#revalidateElementSelection}, so a fresh,
     * unrelated one is enough.
     */
    private static SongDidChangeNotification notificationFor(Mutation... mutations) {
        return new SongDidChangeNotification(List.of(mutations), new Song());
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

        coordinator.revalidateElementSelection(notificationFor());

        assertThat(coordinator.getRange()).isNull();
    }

    @Test
    void testRevalidateElementSelectionIsANoOpForATargetSelection() {
        var coordinator = coordinatorOn(crotchetLine(2));
        coordinator.select(new HitTarget.StaffLine());

        coordinator.revalidateElementSelection(notificationFor());

        assertThat(coordinator.isLineSelected()).isTrue();
    }

    @Test
    void testRevalidateElementSelectionKeepsARangeStillWithinTheLine() {
        var line = crotchetLine(3);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 1);

        var insertedElement = ElementType.CROTCHET.newInstance();
        var insertIndex = 2;
        line.addElement(insertIndex, insertedElement);

        coordinator.revalidateElementSelection(
            notificationFor(new ElementInsertion(line, insertIndex, insertedElement)));

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).as("begin").isEqualTo(0);
                assertThat(range.end()).as("end").isEqualTo(1);
            });
    }

    /**
     * The old bounds-check-and-clear behavior dropped the whole selection the moment its tail
     * ran past the end of the line. The splice instead follows the surviving elements, so a
     * deletion of the range's own tail shrinks the range rather than clearing it.
     */
    @Test
    void testRevalidateElementSelectionShrinksARangeWhenItsTailIsDeleted() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 1);

        var deletedIndex = 1;
        var deletedElement = line.getElement(deletedIndex);
        line.removeElement(deletedIndex);

        coordinator.revalidateElementSelection(
            notificationFor(new ElementDeletion(line, deletedIndex, deletedElement)));

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).as("begin").isEqualTo(0);
                assertThat(range.end()).as("end").isEqualTo(0);
            });
        assertThat(coordinator.getActiveLineIndex())
            .as("the line stays active — only the range shrank")
            .isEqualTo(LINE_0);
    }

    /**
     * The crash this guards against: every range query walks the selected span, so a range
     * whose end can no longer be indexed on its line throws before anything else can notice it
     * is stale. Splicing a range-deletion of the selected tail keeps {@code end} inside the
     * shrunk line rather than past it.
     */
    @Test
    void testRevalidatedSelectionMakesTheTupletQuerySafeOnAShrunkLine() {
        var line = crotchetLine(3);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 2);

        var deleteFrom = 1;
        var deleteTo = 2;
        var deletedElements = List.of(line.getElement(deleteFrom), line.getElement(deleteTo));
        line.removeElement(deleteTo);
        line.removeElement(deleteFrom);

        coordinator.revalidateElementSelection(
            notificationFor(new ElementRangeDeletion(line, deleteFrom, deleteTo, deletedElements)));

        var range = coordinator.getRange();

        assertThat(range).as("the range survives, spliced to what's left").isNotNull();
        assertThat(range.end())
            .as("safe to index — this is what the crash guarded against")
            .isLessThan(line.elementCount());
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

        coordinator.revalidateElementSelection(notificationFor());

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> assertThat(range.end()).isEqualTo(TERMINAL_ELEMENT_INDEX));
    }

    @Test
    void testRevalidateElementSelectionLeavesTheRangeUntouchedForAMutationOnAnotherLine() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 1);

        var otherLineIndex = 1;
        var otherLine = crotchetLine(2);
        coordinator.registerLine(otherLineIndex, otherLine);

        var insertedElement = ElementType.CROTCHET.newInstance();

        coordinator.revalidateElementSelection(
            notificationFor(new ElementInsertion(otherLine, 0, insertedElement)));

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).as("begin").isEqualTo(0);
                assertThat(range.end()).as("end").isEqualTo(1);
            });
    }

    @Test
    void testRevalidateElementSelectionClearsTheSelectionAndDeactivatesTheLineOnDeletionOfItsOwnLine() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 1);

        coordinator.revalidateElementSelection(notificationFor(new LineDeletion(LINE_0, line)));

        assertThat(coordinator.getRange()).isNull();
        assertThat(coordinator.getActiveLineIndex())
            .as("the line itself is gone, so it must be deactivated too")
            .isEqualTo(-1);
    }

    /**
     * The fold clears the selection outright on a {@link LineDeletion}, but only after checking
     * that the deleted line is the range's own. Drop that check — the tempting simplification,
     * since a deleted line is obviously gone — and deleting any line at all would wipe a
     * selection sitting on a line that is still there, and deactivate it besides.
     */
    @Test
    void testRevalidateElementSelectionLeavesTheRangeUntouchedForDeletionOfAnotherLine() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 1);

        var otherLineIndex = 1;
        var otherLine = crotchetLine(2);
        coordinator.registerLine(otherLineIndex, otherLine);

        coordinator.revalidateElementSelection(
            notificationFor(new LineDeletion(otherLineIndex, otherLine)));

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).as("begin").isEqualTo(0);
                assertThat(range.end()).as("end").isEqualTo(1);
            });
        assertThat(coordinator.getActiveLineIndex())
            .as("another line's deletion must not deactivate this one")
            .isEqualTo(LINE_0);
    }

    @Test
    void testRevalidateElementSelectionLeavesTheRangeUntouchedForLineInsertionOfAnotherLine() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 1);

        var insertedLineIndex = 1;
        var insertedLine = crotchetLine(1);

        coordinator.revalidateElementSelection(
            notificationFor(new LineInsertion(insertedLineIndex, insertedLine)));

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).as("begin").isEqualTo(0);
                assertThat(range.end()).as("end").isEqualTo(1);
            });
    }

    @Test
    void testRevalidateElementSelectionClearsTheRangeWhenEveryElementInItIsDeleted() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 1);

        var deleteFrom = 0;
        var deleteTo = 1;
        var deletedElements = List.of(line.getElement(deleteFrom), line.getElement(deleteTo));
        line.removeElement(deleteTo);
        line.removeElement(deleteFrom);

        coordinator.revalidateElementSelection(
            notificationFor(new ElementRangeDeletion(line, deleteFrom, deleteTo, deletedElements)));

        assertThat(coordinator.getRange()).isNull();
        assertThat(coordinator.getActiveLineIndex())
            .as("only the range became unusable — the line stays active")
            .isEqualTo(LINE_0);
    }

    /**
     * The fold walks the batch front-to-back because each record's indices describe the state
     * the record before it left behind. Reversing that walk is not a harmless reordering: on
     * this batch it leaves {@code begin} one too high, so the range names a note the user never
     * selected and drops one they did. A one-mutation batch cannot tell the two orders apart,
     * which is why every other test in this group leaves the order unpinned.
     */
    @Test
    void testRevalidateElementSelectionFoldsTheBatchInRecordedOrder() {
        final var lineElementCount = 5;
        final var rangeBegin = 2;
        final var removedAheadCount = 2;
        final var rangeEnd = lineElementCount - 1;

        var line = crotchetLine(lineElementCount);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(rangeBegin, rangeEnd);

        var selectedElements = List.copyOf(line.getElements(rangeBegin, rangeEnd));

        // Two removals ahead of the range, in the order they run. The second names index 0 of
        // the list the first one left behind, not of the original list — which is precisely
        // why the fold has to replay them in this order and no other.
        var firstRemovedIndex = 1;
        var firstRemoved = line.getElement(firstRemovedIndex);
        line.removeElement(firstRemovedIndex);

        var secondRemovedIndex = 0;
        var secondRemoved = line.getElement(secondRemovedIndex);
        line.removeElement(secondRemovedIndex);

        coordinator.revalidateElementSelection(notificationFor(
            new ElementDeletion(line, firstRemovedIndex, firstRemoved),
            new ElementDeletion(line, secondRemovedIndex, secondRemoved)));

        var range = coordinator.getRange();

        assertThat(range).as("expected the range to survive the fold").isNotNull();
        assertThat(range.begin())
            .as("folding the batch back-to-front instead would leave begin one too high")
            .isEqualTo(rangeBegin - removedAheadCount);
        assertThat(range.end()).as("end").isEqualTo(rangeEnd - removedAheadCount);
        assertThat(line.getElements(range.begin(), range.end()))
            .as("the range must keep naming the same elements it named before the batch")
            .isEqualTo(selectedElements);
    }

    /**
     * The backstop: a structural change made without going through the mutation-tracked API
     * never reaches the fold, so nothing in it can shrink the range. It is the bounds check
     * after the fold — not the fold itself — that catches this and clears the range.
     */
    @Test
    void testRevalidateElementSelectionBackstopClearsARangeLeftUnindexableByAnUntrackedChange() {
        var line = crotchetLine(2);
        var coordinator = coordinatorOn(line);
        coordinator.selectRange(0, 1);

        // The song behind detachedLine reports mutation tracking permanently suspended, so
        // this removal records nothing on its own — no wrapper needed to make it untracked.
        line.removeElement(1);

        coordinator.revalidateElementSelection(notificationFor());

        assertThat(coordinator.getRange()).isNull();
        assertThat(coordinator.getActiveLineIndex())
            .as("the backstop clears the range only — the line itself stays active")
            .isEqualTo(LINE_0);
    }

    // -------------------------------------------------------------------------
    // revalidateElementSelection driven by a real pitch shift
    // -------------------------------------------------------------------------

    /**
     * The specific thing the old arrow-key repair threw away: it re-synced a selection after a
     * grace-note collapse by calling {@code selectRange(begin, end)}, which always anchors at
     * {@code begin} — discarding an anchor the user had dragged to the far end of the range.
     * The splice tracks the anchor through the removal instead, so a shift-extend that follows
     * still grows from the note the user actually anchored on.
     */
    @Test
    void testPitchShiftThatCollapsesAGraceNoteIntoItsHostPreservesTheAnchor() {
        var song = new Song();
        var line = song.getLine(LINE_0);

        // The grace note and its host already share a staff position, so shifting them both by
        // the same delta — which a uniform pitch shift always does — leaves them still equal,
        // and the commit's grace/host collision check removes the grace note.
        var sharedPositionSp = 4;
        var extraPositionSp = 9;
        var raiseOnePosition = -1;

        // Indices of the three elements once added to the (initially empty) line, before the
        // shift removes the grace note.
        var extraIndex = 2;

        // Only the terminal barline the song auto-maintains survives the collapse alongside
        // the host and the extra note.
        var elementCountAfterCollapse = 3;

        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setStaffPosition(sharedPositionSp);
        var host = ElementType.CROTCHET.newInstance();
        host.setStaffPosition(sharedPositionSp);
        var extra = ElementType.CROTCHET.newInstance();
        extra.setStaffPosition(extraPositionSp);

        song.withoutMutationTracking(() -> {
            line.addElement(grace);
            line.addElement(host);
            line.addElement(extra);
        });

        var coordinator = coordinatorOn(line);
        coordinator.selectSingleElement(LINE_0, extraIndex);
        coordinator.extendSelectionTo(0);

        assertThat(coordinator.getRange())
            .as("fixture sanity: the anchor starts at the far end, not at begin")
            .isNotNull()
            .satisfies(range -> assertThat(range.anchor()).isEqualTo(extraIndex));

        // Off the bus before the real shift below posts a real SongDidChangeNotification —
        // this test drives revalidateElementSelection directly afterward, and the production
        // wiring this coordinator would otherwise trigger (ActionReflector's toolbar reflection)
        // has no toolbar to reflect onto here.
        coordinator.unsubscribeForTest();

        var batch = new ArrayList<Mutation>();

        // Prefs is mocked only to silence the audio feedback the shift plays, and
        // OptionDialogs to silence the restatement prompt — neither is relevant here.
        try (var optionDialogs = mockStatic(OptionDialogs.class);
             var prefs = mockStatic(Prefs.class)) {

            prefs.when(() -> Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)).thenReturn(false);
            optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
                any(), any(), any(), anyInt(), anyInt())).thenReturn(JOptionPane.NO_OPTION);

            batch.addAll(UndoTestSupport.captureBatch(song,
                () -> PitchShifter.shiftPitch(null, line, 0, extraIndex, raiseOnePosition)));
        }

        assertThat(line.elementCount())
            .as("the grace note actually collapsed away, or this test proves nothing — the "
                + "survivors are the host, the extra note and the song-owned terminal barline")
            .isEqualTo(elementCountAfterCollapse);

        coordinator.revalidateElementSelection(new SongDidChangeNotification(batch, song));

        assertThat(coordinator.getRange())
            .isNotNull()
            .satisfies(range -> {
                assertThat(range.begin()).as("begin follows the surviving host").isEqualTo(0);
                assertThat(range.end()).as("end follows the surviving extra note").isEqualTo(1);
                assertThat(range.anchor())
                    .as("the anchor stayed on the note it started on, not reset to begin")
                    .isEqualTo(1);
            });
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
