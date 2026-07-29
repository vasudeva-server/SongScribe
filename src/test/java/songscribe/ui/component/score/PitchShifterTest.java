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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

import javax.swing.JOptionPane;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.ElementType;
import songscribe.dom.Line;
import songscribe.dom.Song;
import songscribe.dom.StaffElement;
import songscribe.prefs.Prefs;
import songscribe.prefs.PrefsKey;
import songscribe.ui.OptionDialogs;

/**
 * Tests for {@link PitchShifter}: the group builder shared by both the mouse-drag and arrow-key
 * pitch-shift paths, and the arrow-key entry point's restatement prompt.
 *
 * <p>A grace note shifts exactly like a pitched note, so the builder must include it — this is the
 * behavior the arrow-key path relied on with no fallback of its own.
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

    /**
     * The arrow-key pitch shift asks about restatements before it mutates anything, so Cancel is
     * simply "nothing happened" — and the note must still be sitting where it was.
     */
    @SuppressWarnings("PackageVisibleInnerClass")
    @Nested
    class RestatementPrompt {

        private static final int ORIGINAL_POSITION_SP = 4;
        private static final int RAISE_ONE_POSITION = -1;

        private Line line = detachedLine();

        @BeforeEach
        void setUpLineWithARestatement() {
            var song = new Song();
            line = song.getLine(0);

            song.withoutMutationTracking(() -> {
                line.addElement(sharpNote());

                // The restatement: a second sharp at the same staff position, later in the song.
                line.addElement(sharpNote());
            });
        }

        private static StaffElement sharpNote() {
            var note = ElementType.CROTCHET.newInstance();
            note.setStaffPosition(ORIGINAL_POSITION_SP);
            note.setAccidental(StaffElement.Accidental.SHARP);
            return note;
        }

        private void shiftAnswering(int answer) {
            // Prefs is mocked only to silence the audio feedback the shift plays, which is
            // irrelevant here and would reach for a MIDI device.
            try (var optionDialogs = mockStatic(OptionDialogs.class);
                 var prefs = mockStatic(Prefs.class)) {

                prefs.when(() -> Prefs.getBoolean(PrefsKey.PLAY_SELECTED_NOTE)).thenReturn(false);
                optionDialogs.when(() -> OptionDialogs.showConfirmDialog(
                    any(), any(), any(), anyInt(), anyInt())).thenReturn(answer);

                PitchShifter.shiftPitch(null, line, 0, 0, RAISE_ONE_POSITION);
            }
        }

        @Test
        void testCancelLeavesTheNoteExactlyWhereItWas() {
            shiftAnswering(JOptionPane.CANCEL_OPTION);

            assertThat(line.getElement(0).getStaffPosition())
                .as("the shift never happened")
                .isEqualTo(ORIGINAL_POSITION_SP);
            assertThat(line.getElement(0).getAccidental())
                .as("and the note kept the accidental a shift would have taken")
                .isEqualTo(StaffElement.Accidental.SHARP);
            assertThat(line.getElement(1).getAccidental())
                .isEqualTo(StaffElement.Accidental.SHARP);
        }

        @Test
        void testYesShiftsTheNoteAndClearsTheRestatement() {
            shiftAnswering(JOptionPane.YES_OPTION);

            assertThat(line.getElement(0).getStaffPosition())
                .isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);
            assertThat(line.getElement(1).getAccidental())
                .as("the accepted restatement is gone")
                .isNull();
        }

        @Test
        void testNoShiftsTheNoteAndLeavesTheRestatementAlone() {
            shiftAnswering(JOptionPane.NO_OPTION);

            assertThat(line.getElement(0).getStaffPosition())
                .isEqualTo(ORIGINAL_POSITION_SP + RAISE_ONE_POSITION);
            assertThat(line.getElement(1).getAccidental())
                .isEqualTo(StaffElement.Accidental.SHARP);
        }
    }
}
