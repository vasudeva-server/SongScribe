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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.SongData;

class SongIsEmptyTest extends UnitTest {

    // Branch 1: lines list is empty → isEmpty() returns true.
    @Test
    void testNoLinesReturnsTrue() {
        var song = new Song();
        var data = new SongData(
            null, "", "", "", 0, 0, "", "", "", "", "", "", false,
            Song.DEFAULT_KEY_ACCIDENTAL_COUNT, Song.DEFAULT_KEY_TYPE,
            0.0, 0.0, 0.0, List.of(), false, 1
        );
        song.withoutMutationTracking(() -> song.loadFrom(data));

        assertThat(song.isEmpty()).isTrue();
    }

    // Branch 2: all lines are empty (no elements at all) → isEmpty() returns true.
    @Test
    void testAllEmptyLinesReturnsTrue() {
        var song = new Song();
        var emptyLine = new Line(song);
        var data = new SongData(
            null, "", "", "", 0, 0, "", "", "", "", "", "", false,
            Song.DEFAULT_KEY_ACCIDENTAL_COUNT, Song.DEFAULT_KEY_TYPE,
            0.0, 0.0, 0.0, List.of(emptyLine), false, 1
        );
        song.withoutMutationTracking(() -> song.loadFrom(data));

        assertThat(song.isEmpty()).isTrue();
    }

    // Branch 3: any line has at least one element → isEmpty() returns false.
    @Test
    void testAnyNonEmptyLineReturnsFalse() {
        var song = new Song();
        song.withoutMutationTracking(() ->
            song.getLine(0).addElement(new StaffElement(ElementType.CROTCHET))
        );

        assertThat(song.isEmpty()).isFalse();
    }
}
