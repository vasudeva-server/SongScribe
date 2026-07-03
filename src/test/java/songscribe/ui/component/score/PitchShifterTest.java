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

package songscribe.ui.component.score;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;

/**
 * Tests for {@link PitchShifter#buildPitchShiftGroup}, the single group builder
 * shared by both the mouse-drag and arrow-key pitch-shift paths. A grace note
 * shifts exactly like a pitched note, so the builder must include it — this is
 * the behavior the arrow-key path relied on with no fallback of its own.
 */
class PitchShifterTest extends UnitTest {

    private static final int GRACE_POSITION_SP = 3;
    private static final int PITCHED_POSITION_SP = 5;

    private static Line lineWithGraceAt(int graceStaffPosition) {
        var line = detachedLine();
        var grace = ElementType.GRACE_QUAVER.newInstance();
        grace.setStaffPosition(graceStaffPosition);
        line.addElement(grace);
        return line;
    }

    @Test
    void testGraceNoteOnlyRangeYieldsNonEmptyGroup() {
        // A selection sitting solely on a grace note (the arrow-key case that used to
        // silently no-op because buildPitchShiftGroup filtered grace notes out).
        var line = lineWithGraceAt(GRACE_POSITION_SP);

        var group = PitchShifter.buildPitchShiftGroup(line, 0, 0);

        assertThat(group).hasSize(1);
        assertThat(group.getFirst().index()).isEqualTo(0);
        assertThat(group.getFirst().originalStaffPositionSp()).isEqualTo(GRACE_POSITION_SP);
    }

    @Test
    void testGraceNoteIncludedAlongsidePitchedNotes() {
        // [grace@3, crotchet@5] — both notes belong to the group.
        var line = lineWithGraceAt(GRACE_POSITION_SP);
        var pitched = ElementType.CROTCHET.newInstance();
        pitched.setStaffPosition(PITCHED_POSITION_SP);
        line.addElement(pitched);

        var group = PitchShifter.buildPitchShiftGroup(line, 0, 1);

        assertThat(group).hasSize(2);
        assertThat(group).extracting(PitchShifter.PitchShiftEntry::index)
            .containsExactly(0, 1);
    }
}
