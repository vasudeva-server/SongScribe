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

package songscribe.dom;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.dom.AccidentalChain.Restamp;
import songscribe.dom.StaffElementRun.ElementRange;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link ProjectedEdit}'s promise, stated on its class Javadoc: an edit's preview and its result
 * cannot disagree about a note. A projected walk, taken before anything commits, is compared
 * against the same edit committed for real.
 */
class ProjectedEditTest extends UnitTest {

    private static final int F = 3;
    private static final int OTHER = 4;

    @Test
    void testAProjectedWalkAgreesWithWhatActuallyLandsWhenTheEditCommits() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f " + OTHER + " " + F);
        var line = song.getLine(0);
        var keyChange = StaffElementFactory.keyChange(Key.ONE_SHARP);

        var projected = ProjectedEdit.replacing(line, ElementRange.single(1), List.of(keyChange)).walk();
        var predicted = projected.stream().collect(Collectors.toMap(Restamp::note, Restamp::state));

        song.withModification(() -> line.setElement(1, keyChange));

        assertThat(predicted)
            .as("every note the projection restamped holds exactly the state it predicted, now that the edit has landed")
            .isNotEmpty()
            .allSatisfy((note, state) -> assertThat(note.chainedAccidental()).isEqualTo(state));
    }
}
