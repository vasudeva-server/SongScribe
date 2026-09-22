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

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import songscribe.UnitTest;
import songscribe.message.mutation.ElementField;
import songscribe.message.mutation.ElementModification;

import static org.assertj.core.api.Assertions.assertThat;
import static songscribe.dom.SongFactory.compactSong;

/**
 * {@link ChainEdit#of}'s promise that a mutation stops meaning an edit once it is the walk's own
 * restamp — the one case the method answers null for that a caller could mistake for a bug: an
 * {@link ElementModification} the walk itself just posted, which must not send
 * {@link Song#applyChange} back through the walk a second time.
 */
class ChainEditTest extends UnitTest {

    private static final int F = 3;

    @Test
    void testOfAnswersNullForTheWalksOwnRestamp() {
        var song = compactSong(Key.NO_ACCIDENTALS, F + "f");
        var line = song.getLine(0);
        var note = (NoteElement) line.getElement(0);
        var restamp = new ElementModification(line, 0, EnumSet.of(ElementField.ACCIDENTAL), note, note);

        assertThat(ChainEdit.of(restamp)).isNull();
    }
}
