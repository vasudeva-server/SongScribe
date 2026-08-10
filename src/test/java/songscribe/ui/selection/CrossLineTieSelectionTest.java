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
import static songscribe.dom.StaffElementFactory.crotchet;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.Tie;
import songscribe.hit.HitTarget;
import songscribe.ui.component.ScoreView;

/**
 * Tests for selecting a cross-line tie (#493's phase 10) — one {@link Tie} object present in
 * both lines' {@code spans} lists, half drawn on each.
 * <p>
 * {@link SelectionCoordinator} names the target's identity and the line the click landed on
 * separately: {@code select} stores the {@link HitTarget}, and a prior {@code activateLine}
 * call records which line is active. Because a {@link Tie} is a record wrapping the shared
 * {@link songscribe.dom.Tie} object, both lines construct an equal {@link HitTarget.Tie} no
 * matter which line's click created it — see {@link #testClickingEitherHalfSelectsTheSameTieObject}.
 */
class CrossLineTieSelectionTest extends UnitTest {

    private static final int FIRST_LINE_INDEX = 0;
    private static final int SECOND_LINE_INDEX = 1;

    /**
     * A tie whose anchor is the last element of {@code firstLine} and whose end is the first
     * element of {@code secondLine} — the shape a cross-line tie always has (#493) — with a
     * coordinator that has both lines registered at their song indices.
     */
    private record CrossLineTieFixture(
        SelectionCoordinator coordinator,
        Line firstLine,
        Line secondLine,
        Tie tie
    ) {

        static CrossLineTieFixture create() {
            var song = new Song();
            var firstLine = song.getLine(0);
            var secondLine = new Line(song);
            var anchor = crotchet();
            var end = crotchet();
            var tie = new Tie(anchor, end);

            song.withoutMutationTracking(() -> {
                firstLine.addElement(anchor);
                song.addLine(secondLine);
                secondLine.addElement(end);
                firstLine.addTie(tie);
            });

            var coordinator = new SelectionCoordinator(mock(ScoreView.class));
            coordinator.registerLine(FIRST_LINE_INDEX, firstLine);
            coordinator.registerLine(SECOND_LINE_INDEX, secondLine);

            return new CrossLineTieFixture(coordinator, firstLine, secondLine, tie);
        }
    }

    /**
     * A click resolves to a {@link HitTarget} built from whichever line's hit region caught
     * it, and {@code select} stores that target as-is. For a cross-line tie both halves wrap
     * the same {@link Tie} object, so the target a click on the first line's half produces is
     * {@code equal} to the target a click on the second line's half produces — there is only
     * ever one tie to select, regardless of which half was clicked.
     */
    @Test
    void testClickingEitherHalfSelectsTheSameTieObject() {
        var fixture = CrossLineTieFixture.create();
        var target = new HitTarget.Tie(fixture.tie());

        fixture.coordinator().activateLine(FIRST_LINE_INDEX);
        fixture.coordinator().select(target);

        assertThat(fixture.coordinator().getSelectedTarget())
            .as("selecting via the anchor half's line")
            .isEqualTo(target);

        fixture.coordinator().activateLine(SECOND_LINE_INDEX);
        fixture.coordinator().select(target);

        assertThat(fixture.coordinator().getSelectedTarget())
            .as("selecting via the end half's line")
            .isEqualTo(target);
    }

    /**
     * Both lines draw their own half of the tie, each asking
     * {@link SelectionCoordinator#isSelected} with its own line index (see
     * {@code LineInvariants#colorFor}). A cross-line tie selected from either half must report
     * selected to both queries, or only the clicked half would render highlighted.
     */
    @Test
    void testSelectionReportsAsPresentOnBothLines() {
        var fixture = CrossLineTieFixture.create();
        var target = new HitTarget.Tie(fixture.tie());

        fixture.coordinator().activateLine(SECOND_LINE_INDEX);
        fixture.coordinator().select(target);

        assertThat(fixture.coordinator().isSelected(target, FIRST_LINE_INDEX))
            .as("the anchor half, drawn by the first line")
            .isTrue();
        assertThat(fixture.coordinator().isSelected(target, SECOND_LINE_INDEX))
            .as("the end half, drawn by the second line — the line the click landed on")
            .isTrue();
    }

    /**
     * {@link SelectionCoordinator#revalidateDecorationSelection} clears the selection when its
     * target no longer hangs off the active line. A cross-line tie is present in both lines'
     * {@code spans} lists (#493 phase 1), so a mutation on the line that is not active must not
     * clear it — the tie's own line, the active one, still holds it.
     */
    @Test
    void testSelectionSurvivesAMutationOnTheOtherLine() {
        var fixture = CrossLineTieFixture.create();
        var target = new HitTarget.Tie(fixture.tie());

        fixture.coordinator().activateLine(FIRST_LINE_INDEX);
        fixture.coordinator().select(target);

        // A mutation on the second line — not the active line the tie was selected from.
        fixture.secondLine().getSong().withoutMutationTracking(
            () -> fixture.secondLine().addElement(crotchet()));

        assertThat(fixture.coordinator().revalidateDecorationSelection())
            .as("the selection was cleared as stale")
            .isFalse();
        assertThat(fixture.coordinator().getSelectedTarget())
            .isEqualTo(target);
    }
}
