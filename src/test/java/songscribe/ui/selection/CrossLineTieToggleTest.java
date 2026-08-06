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
import static org.mockito.Mockito.when;
import static songscribe.dom.StaffElementFactory.createNote;
import static songscribe.dom.StaffElementFactory.crotchetRest;
import static songscribe.dom.StaffElementFactory.finalDoubleBarline;
import static songscribe.dom.StaffElementFactory.singleBarline;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.ui.MusicEditOperations;

/**
 * Unit tests for the cross-line tie toggle (#493): enabling the tie command when a single
 * element sits at a line boundary and its adjacent line offers a matching pitched note, and
 * the toggle itself adding/removing that tie through {@link MusicEditOperations}.
 * <p>
 * A mocked {@link SelectionCoordinator} stands in for the real one — only {@code getRange()}
 * is ever called from {@link MusicEditOperations#canToggleTie()}/{@link
 * MusicEditOperations#toggleTie()} — so each fixture needs no {@code ScoreView} and never
 * touches the message bus, matching how {@code RangeQueriesTest} builds ranges directly rather
 * than through a live coordinator.
 */
class CrossLineTieToggleTest extends UnitTest {

    private static final int STAFF_POSITION_A = 0;
    private static final int STAFF_POSITION_B = 2;

    // song.getLine(0) always starts with one pre-existing terminal element (the auto-maintained
    // final barline). Line.addElement(StaffElement) inserts before that terminal only while the
    // receiving line is still the song's last line, so secondLine is attached first — leaving
    // firstLine's terminal in place — before firstLineLast is appended after it, landing at
    // index 1, which is also firstLine's last index. secondLine starts empty, so its boundary
    // note — added first, with a filler after it — lands at index 0.
    private static final int FIRST_LINE_BOUNDARY_INDEX = 1;
    private static final int SECOND_LINE_BOUNDARY_INDEX = 0;

    private record Fixture(Song song, Line firstLine, Line secondLine,
                            SelectionCoordinator coordinator, MusicEditOperations operations) {}

    /**
     * Builds a two-line song: {@code firstLine} is {@code [<existing terminal>, firstLineLast]}
     * and {@code secondLine} is {@code [secondLineFirst, filler]}, so the two given elements sit
     * exactly at the line boundary between them.
     */
    private static Fixture twoLineFixture(StaffElement firstLineLast, StaffElement secondLineFirst) {
        var song = new Song();
        var firstLine = song.getLine(0);
        var secondLine = new Line(song);

        song.withoutMutationTracking(() -> {
            song.addLine(secondLine);
            firstLine.addElement(firstLineLast);
            secondLine.addElement(secondLineFirst);
            secondLine.addElement(createNote(STAFF_POSITION_A, true));
        });

        var coordinator = mock(SelectionCoordinator.class);
        var operations = new MusicEditOperations(song, coordinator);
        return new Fixture(song, firstLine, secondLine, coordinator, operations);
    }

    /** Stubs the coordinator to report a single-element selection at {@code index} in {@code line}. */
    private static void selectSingle(SelectionCoordinator coordinator, Line line, int index) {
        when(coordinator.getRange()).thenReturn(new Selection.Range(line, index, index, index));
    }

    @Test
    void testCanToggleTieEnabledForMatchingBoundaryPairInBothDirections() {
        var fixture = twoLineFixture(
            createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));

        selectSingle(fixture.coordinator(), fixture.firstLine(), FIRST_LINE_BOUNDARY_INDEX);
        assertThat(fixture.operations().canToggleTie())
            .as("enabled selecting the first line's last element").isTrue();

        selectSingle(fixture.coordinator(), fixture.secondLine(), SECOND_LINE_BOUNDARY_INDEX);
        assertThat(fixture.operations().canToggleTie())
            .as("enabled selecting the second line's first element").isTrue();
    }

    @Test
    void testCanToggleTieDisabledWhenPitchesDiffer() {
        var fixture = twoLineFixture(
            createNote(STAFF_POSITION_A, true), createNote(STAFF_POSITION_B, true));

        selectSingle(fixture.coordinator(), fixture.firstLine(), FIRST_LINE_BOUNDARY_INDEX);
        assertThat(fixture.operations().canToggleTie())
            .as("disabled when the boundary pitches differ").isFalse();
    }

    @Test
    void testCanToggleTieDisabledWhenAnchorSideIsUnpitched() {
        var fixture = twoLineFixture(crotchetRest(), createNote(STAFF_POSITION_B, true));

        selectSingle(fixture.coordinator(), fixture.firstLine(), FIRST_LINE_BOUNDARY_INDEX);
        assertThat(fixture.operations().canToggleTie())
            .as("disabled when the anchor-side element is unpitched").isFalse();
    }

    @Test
    void testCanToggleTieDisabledWhenEndSideIsUnpitched() {
        var fixture = twoLineFixture(createNote(STAFF_POSITION_B, true), crotchetRest());

        selectSingle(fixture.coordinator(), fixture.secondLine(), SECOND_LINE_BOUNDARY_INDEX);
        assertThat(fixture.operations().canToggleTie())
            .as("disabled when the end-side element is unpitched").isFalse();
    }

    @Test
    void testCanToggleTieDisabledWhenSelectedElementIsNotAtABoundary() {
        // A middle note between the pre-existing terminal and the true boundary note: even
        // though a matching, adjacent second line exists, this element sits at neither edge
        // of firstLine, so it must never be offered a cross-line partner.
        var fixture = twoLineFixture(
            createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));
        var middleIndex = FIRST_LINE_BOUNDARY_INDEX;

        fixture.song().withoutMutationTracking(
            () -> fixture.firstLine().addElement(middleIndex, createNote(STAFF_POSITION_B, true)));

        selectSingle(fixture.coordinator(), fixture.firstLine(), middleIndex);
        assertThat(fixture.operations().canToggleTie())
            .as("disabled for a middle element, which is at neither edge of the line").isFalse();
    }

    @Test
    void testCanToggleTieDisabledAtTheFirstLinesStart() {
        var fixture = twoLineFixture(
            createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));
        var firstLineStartIndex = 0;

        selectSingle(fixture.coordinator(), fixture.firstLine(), firstLineStartIndex);
        assertThat(fixture.operations().canToggleTie())
            .as("disabled at the very first line's start: no earlier line exists").isFalse();
    }

    @Test
    void testCanToggleTieDisabledAtTheLastLinesEnd() {
        var fixture = twoLineFixture(
            createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));
        var lastLineEndIndex = 1;

        selectSingle(fixture.coordinator(), fixture.secondLine(), lastLineEndIndex);
        assertThat(fixture.operations().canToggleTie())
            .as("disabled at the very last line's end: no later line exists").isFalse();
    }

    /**
     * A line closed by a barline is the ordinary case, not a corner one: the toggle looks past
     * every element a tie may straddle to the line's last <em>note</em>, on both sides of the
     * break and in both directions of selection.
     */
    @Test
    void testCanToggleTieAcrossABarlineClosingTheFirstLine() {
        var fixture = twoLineFixture(
            createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));

        // firstLine: [..., note, barline] and secondLine: [barline, note, ...] — a separator on
        // each side of the break, so neither endpoint is its line's edge element any more.
        fixture.song().withoutMutationTracking(() -> {
            fixture.firstLine().addElement(FIRST_LINE_BOUNDARY_INDEX + 1, singleBarline());
            fixture.secondLine().addElement(SECOND_LINE_BOUNDARY_INDEX, singleBarline());
        });

        selectSingle(fixture.coordinator(), fixture.firstLine(), FIRST_LINE_BOUNDARY_INDEX);
        assertThat(fixture.operations().canToggleTie())
            .as("enabled selecting the first line's last note, behind its closing barline")
            .isTrue();

        selectSingle(fixture.coordinator(), fixture.secondLine(), SECOND_LINE_BOUNDARY_INDEX + 1);
        assertThat(fixture.operations().canToggleTie())
            .as("and selecting the second line's first note, behind its opening barline")
            .isTrue();
    }

    @Test
    void testToggleTiesTheNotesThemselvesNotTheSeparatorsBetweenThem() {
        var fixture = twoLineFixture(
            createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));
        var anchor = fixture.firstLine().getElement(FIRST_LINE_BOUNDARY_INDEX);
        var end = fixture.secondLine().getElement(SECOND_LINE_BOUNDARY_INDEX);

        fixture.song().withoutMutationTracking(() -> {
            fixture.firstLine().addElement(FIRST_LINE_BOUNDARY_INDEX + 1, singleBarline());
            fixture.secondLine().addElement(SECOND_LINE_BOUNDARY_INDEX, singleBarline());
        });

        selectSingle(fixture.coordinator(), fixture.firstLine(), FIRST_LINE_BOUNDARY_INDEX);
        fixture.operations().toggleTie();

        var tie = fixture.firstLine().findTieAt(FIRST_LINE_BOUNDARY_INDEX);

        assertThat(tie).as("tie created from the first line's last note").isNotNull();
        assertThat(tie).as("the same tie object spans both lines")
            .isSameAs(fixture.secondLine().findTieAt(SECOND_LINE_BOUNDARY_INDEX + 1));
        assertThat(tie.getAnchorElement()).isSameAs(anchor);
        assertThat(tie.getEndElement()).isSameAs(end);
    }

    /**
     * The final double barline ends the piece, so nothing may sound across it — it is the one
     * non-duration element the walk to the line's edge refuses to pass.
     */
    @Test
    void testCanToggleTieDisabledAcrossAFinalDoubleBarline() {
        var fixture = twoLineFixture(
            createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));

        fixture.song().withoutMutationTracking(() -> fixture.firstLine()
            .addElement(FIRST_LINE_BOUNDARY_INDEX + 1, finalDoubleBarline()));

        selectSingle(fixture.coordinator(), fixture.firstLine(), FIRST_LINE_BOUNDARY_INDEX);
        assertThat(fixture.operations().canToggleTie())
            .as("nothing sounds across the end of the piece").isFalse();
    }

    /**
     * A separator is passed over; a note is not. The note before the last one is still a middle
     * element, and widening the walk must not have made every note on the line a candidate.
     */
    @Test
    void testCanToggleTieDisabledForTheNoteBeforeTheLastNote() {
        var fixture = twoLineFixture(
            createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));

        fixture.song().withoutMutationTracking(() -> {
            fixture.firstLine().addElement(FIRST_LINE_BOUNDARY_INDEX, createNote(STAFF_POSITION_B, true));
            fixture.firstLine().addElement(FIRST_LINE_BOUNDARY_INDEX + 2, singleBarline());
        });

        selectSingle(fixture.coordinator(), fixture.firstLine(), FIRST_LINE_BOUNDARY_INDEX);
        assertThat(fixture.operations().canToggleTie())
            .as("a note stops the walk, so the one behind it is not at the boundary").isFalse();
    }

    @Test
    void testToggleCreatesTheTieInBothLinesAndTogglingAgainRemovesItFromBoth() {
        var fixture = twoLineFixture(
            createNote(STAFF_POSITION_B, true), createNote(STAFF_POSITION_B, true));

        selectSingle(fixture.coordinator(), fixture.firstLine(), FIRST_LINE_BOUNDARY_INDEX);

        assertThat(fixture.firstLine().findTieAt(FIRST_LINE_BOUNDARY_INDEX))
            .as("no tie before the first toggle").isNull();
        assertThat(fixture.secondLine().findTieAt(SECOND_LINE_BOUNDARY_INDEX))
            .as("no tie before the first toggle").isNull();

        fixture.operations().toggleTie();

        var tieInFirstLine = fixture.firstLine().findTieAt(FIRST_LINE_BOUNDARY_INDEX);
        var tieInSecondLine = fixture.secondLine().findTieAt(SECOND_LINE_BOUNDARY_INDEX);

        assertThat(tieInFirstLine).as("tie created in the first line").isNotNull();
        assertThat(tieInSecondLine).as("tie created in the second line").isNotNull();
        assertThat(tieInFirstLine).as("the same tie object spans both lines").isSameAs(tieInSecondLine);

        fixture.operations().toggleTie();

        assertThat(fixture.firstLine().findTieAt(FIRST_LINE_BOUNDARY_INDEX))
            .as("tie removed from the first line").isNull();
        assertThat(fixture.secondLine().findTieAt(SECOND_LINE_BOUNDARY_INDEX))
            .as("tie removed from the second line").isNull();
    }
}
