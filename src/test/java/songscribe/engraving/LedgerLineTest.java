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

package songscribe.engraving;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which ledger lines a note gets, and where each one sits relative to the note.
 *
 * <p>The offsets are the only thing that says whether a high note reads as the pitch it is. The
 * whole staff-position range is enumerated rather than sampled: the sequence changes shape at
 * every position — it lengthens by one line every two positions, and a note sitting in a space is
 * offset half a staff space from the lines it needs — so no representative position stands in for
 * its neighbours.
 */
class LedgerLineTest extends UnitTest {

    /**
     * One note position and the ledger lines it needs.
     *
     * @param staffPositionSp the note's staff position, in half staff spaces, Y-down from the
     *                        middle line
     * @param expectedYSs     the Y offset of each ledger line from the note, in the order drawn:
     *                        from the line nearest the staff outward, in staff spaces
     */
    private record LedgerLineCase(int staffPositionSp, List<Double> expectedYSs) {

        @Override
        public String toString() {
            return "staff position " + staffPositionSp;
        }
    }

    private static LedgerLineCase noLines(int staffPositionSp) {
        return new LedgerLineCase(staffPositionSp, List.of());
    }

    /**
     * Every staff position a note may take. The eleven inside the staff and its two nearest
     * neighbours either side need no ledger line at all; the rest need one more line for every
     * two positions further out.
     */
    static Stream<LedgerLineCase> ledgerLineCases() {
        return Stream.of(
            new LedgerLineCase(-10, List.of(0.0, 1.0, 2.0)),
            new LedgerLineCase(-9, List.of(0.5, 1.5)),
            new LedgerLineCase(-8, List.of(0.0, 1.0)),
            new LedgerLineCase(-7, List.of(0.5)),
            new LedgerLineCase(-6, List.of(0.0)),
            noLines(-5),
            noLines(-4),
            noLines(-3),
            noLines(-2),
            noLines(-1),
            noLines(0),
            noLines(1),
            noLines(2),
            noLines(3),
            noLines(4),
            noLines(5),
            new LedgerLineCase(6, List.of(0.0)),
            new LedgerLineCase(7, List.of(-0.5)),
            new LedgerLineCase(8, List.of(0.0, -1.0)),
            new LedgerLineCase(9, List.of(-0.5, -1.5)),
            new LedgerLineCase(10, List.of(0.0, -1.0, -2.0)),
            new LedgerLineCase(11, List.of(-0.5, -1.5, -2.5)),
            new LedgerLineCase(12, List.of(0.0, -1.0, -2.0, -3.0)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ledgerLineCases")
    void testANoteGetsOneLedgerLinePerStaffLineBetweenItAndTheStaff(LedgerLineCase testCase) {
        var offsetsSs = new ArrayList<Double>();

        LedgerLine.forEachOffsetSs(testCase.staffPositionSp(), offsetsSs::add);

        assertThat(offsetsSs).containsExactlyElementsOf(testCase.expectedYSs());
    }

    @Test
    void testCasesCoverEveryStaffPositionANoteMayTake() {
        assertThat(ledgerLineCases().map(LedgerLineCase::staffPositionSp))
            .containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(StaffPosition.MIN_SP, StaffPosition.MAX_SP)
                    .boxed()
                    .toList());
    }
}
