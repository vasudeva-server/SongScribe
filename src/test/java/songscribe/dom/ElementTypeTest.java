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
 * {@link ElementType}'s promise that {@link ElementType#toRest()} and {@link ElementType#toNote()}
 * are inverses over the pitched notes and the rests, and the identity everywhere else.
 *
 * <p>The failure this guards is a type that has a value and is neither a pitched note nor a rest.
 * A grace note is one: the pair must convert it to itself rather than to the note or rest of its
 * value. The rows are every constant, so a type added later reaches this class on its own.
 */
class ElementTypeTest extends UnitTest {

    @ParameterizedTest
    @EnumSource(ElementType.class)
    void testToRestAndToNoteInvertEachOtherOverNotesAndRestsAndFixEveryOtherType(ElementType type) {
        if (type.isPitchedNote()) {
            var rest = type.toRest();

            assertThat(rest.isRest())
                .as("%s converts to a rest", type)
                .isTrue();
            assertThat(rest.toNote())
                .as("the rest of %s converts back to it", type)
                .isSameAs(type);
        } else if (type.isRest()) {
            var note = type.toNote();

            assertThat(note.isPitchedNote())
                .as("%s converts to a pitched note", type)
                .isTrue();
            assertThat(note.toRest())
                .as("the note of %s converts back to it", type)
                .isSameAs(type);
        } else {
            assertThat(type.toRest())
                .as("%s has no rest counterpart", type)
                .isSameAs(type);
            assertThat(type.toNote())
                .as("%s has no note counterpart", type)
                .isSameAs(type);
        }
    }
}
