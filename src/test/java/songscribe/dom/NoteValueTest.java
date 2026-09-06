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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import songscribe.UnitTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NoteValue}'s promise that {@link NoteValue#note()} and {@link NoteValue#rest()} are the
 * two inverses of {@link ElementType#noteValue()}: a caller walks from a value to either of its
 * types and back without losing which value it started from.
 */
class NoteValueTest extends UnitTest {

    @ParameterizedTest
    @EnumSource(NoteValue.class)
    void testTheNoteAndTheRestOfAValueBothCarryThatValue(NoteValue value) {
        assertThat(value.note().noteValue())
            .as("the note of %s carries it", value)
            .isSameAs(value);
        assertThat(value.rest().noteValue())
            .as("the rest of %s carries it", value)
            .isSameAs(value);
    }
}
